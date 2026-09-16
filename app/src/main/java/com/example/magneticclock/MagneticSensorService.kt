package com.example.magneticclock

import android.Manifest
import android.app.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.*
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.magneticclock.data.AppSettings
import com.example.magneticclock.data.SettingsManager
import com.example.magneticclock.data.TripManager
import kotlinx.coroutines.*
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.milliseconds

class MagneticSensorService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var locationManager: LocationManager
    private var magneticSensor: Sensor? = null
    private lateinit var settingsManager: SettingsManager
    private var currentSettings = AppSettings()
    private var vibrator: Vibrator? = null
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // States
    private var isInCar = false // "In Car" mode (stays true during deactivation delay)
    private var isSensorRegistered = false
    private var isBTDevicePhysicallyConnected = false // Physical BT connection status (instant)
    private var isMagnetActive = false
    private var isClockShowing = false
    private var isClockModeTriggered = false // Стає true після першого магніту в сесії BT
    
    // Trigger Timers
    private var magnetActivationStartTime: Long = 0
    private var magnetDeactivationStartTime: Long = 0
    private var btDeactivationJob: Job? = null
    private var lastMagnitude = 0f
    private var lastNotifiedMagnitude = 0f
    private var isCheckingBluetooth = false
    
    private var wakeLock: PowerManager.WakeLock? = null
    private var intentDevice: BluetoothDevice? = null

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            TripManager.updateLocation(location)
        }
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            com.example.magneticclock.data.AppLogger.d("Сервіс перезапущено системою. Перевіряємо стан...")
            if (!isInCar) checkBluetoothStatus()
            return START_STICKY
        }

        val action = intent.action
        com.example.magneticclock.data.AppLogger.i("onStartCommand: action=$action")
        if (action == "STOP_SERVICE") {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        
        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("target_device", BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION") intent.getParcelableExtra("target_device")
        }

        if (device != null) {
            com.example.magneticclock.data.AppLogger.d("onStartCommand: Отримано пристрій ${device.address} з інтенту")
            intentDevice = device
        }

        if (device != null && isTargetDevice(device)) {
            com.example.magneticclock.data.AppLogger.i("Пристрій з інтенту підтверджено. Активація.")
            updateInCarState(true)
        } else if (!isInCar && !isCheckingBluetooth) {
            checkBluetoothStatus()
        }

        return START_STICKY
    }

    private fun notifyInCarStatus() {
        sendBroadcast(Intent("IN_CAR_STATUS_UPDATE").apply {
            setPackage(packageName)
            putExtra("is_in_car", isInCar)
        })
    }

    private val controlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("MagneticClock", "Control Broadcast: ${intent?.action}")
            when (intent?.action) {
                "CLOCK_CLOSED_MANUALLY" -> {
                    isClockShowing = false
                    updateNotification()
                }
                "CLOCK_OPENED" -> {
                    isClockShowing = true
                    updateNotification()
                }
                "REQUEST_IN_CAR_STATUS" -> {
                    notifyInCarStatus()
                }
            }
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent?.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION") intent?.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            }
            
            var name = intent?.getStringExtra(BluetoothDevice.EXTRA_NAME)
            if (name == null && device != null) {
                try {
                    if (ActivityCompat.checkSelfPermission(context!!, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        name = device.name
                    }
                } catch (e: Exception) { }
            }

            if (device != null && !com.example.magneticclock.data.DeviceFilter.isTargetDevice(this@MagneticSensorService, device, currentSettings)) {
                // Якщо це сторонній пристрій, повністю ігноруємо його і не засмічуємо журнал
                return
            }

            val stateStr = when(action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> "СТАН: Підключився (CONNECTED)"
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> "СТАН: Від'єднався (DISCONNECTED)"
                BluetoothDevice.ACTION_NAME_CHANGED -> "СТАН: Зміна імені"
                BluetoothAdapter.ACTION_STATE_CHANGED -> "СТАН: Зміна адаптера BT"
                else -> "СТАН: Активність"
            }
            
            com.example.magneticclock.data.AppLogger.w("BT_DEV: Назва: [${name ?: "Невідомо"}], MAC: [${device?.address ?: "Немає"}], $stateStr")

            when (action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    serviceScope.launch {
                        delay(1000L)
                        if (isTargetDevice(device)) {
                            com.example.magneticclock.data.AppLogger.i("ACL Connected підтверджено для цільового пристрою")
                            updateInCarState(true)
                        } else {
                            checkBluetoothStatus()
                        }
                    }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    com.example.magneticclock.data.AppLogger.w("ACL Disconnected: Зв'язок розірвано. Перевірка статусу...")
                    // Якщо від'єднався наш цільовий пристрій - негайно вимикаємо "В авто"
                    if (device != null && com.example.magneticclock.data.DeviceFilter.isTargetDevice(this@MagneticSensorService, device, currentSettings)) {
                        updateInCarState(false)
                    } else {
                        checkBluetoothStatus()
                    }
                }
                BluetoothDevice.ACTION_NAME_CHANGED -> {
                    checkBluetoothStatus()
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent?.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    if (state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF) {
                        com.example.magneticclock.data.AppLogger.w("Bluetooth вимкнено в системі")
                        updateInCarState(false)
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        com.example.magneticclock.data.AppLogger.i("Сервіс MagneticSensorService СТВОРЕНО (onCreate)")
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        magneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED) 
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
            
        settingsManager = SettingsManager(this)
        com.example.magneticclock.data.MusicPlayerManager.init(this)
        
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION") getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        serviceScope.launch {
            settingsManager.settingsFlow.collect { newSettings ->
                val wasEnabled = currentSettings.isMonitoringEnabled
                val wasMusicEnabled = currentSettings.isMusicEnabled
                val oldToken = currentSettings.telegramBotToken
                val oldTarget = currentSettings.bluetoothTriggerDeviceName
                
                // Оновлюємо поточний стейт ТІЛЬКИ ПІСЛЯ копіювання старих значень для порівняння
                currentSettings = newSettings
                
                intentDevice?.let { device ->
                    if (newSettings.isMonitoringEnabled && com.example.magneticclock.data.DeviceFilter.isTargetDevice(this@MagneticSensorService, device, currentSettings)) {
                        if (!isInCar) {
                            com.example.magneticclock.data.AppLogger.i("Пристрій підтверджено після завантаження налаштувань")
                            updateInCarState(true)
                        }
                    }
                }
                
                if (newSettings.isMonitoringEnabled != wasEnabled || oldTarget != newSettings.bluetoothTriggerDeviceName) {
                    if (newSettings.isMonitoringEnabled) {
                        if (!isInCar) checkBluetoothStatus()
                    } else {
                        performFullStop()
                    }
                }

                // Оновлюємо музику тільки при реальній зміні токена або включенні
                if (newSettings.isMusicEnabled && newSettings.telegramBotToken.isNotEmpty()) {
                    if (newSettings.telegramBotToken != oldToken || !wasMusicEnabled) {
                        com.example.magneticclock.data.MusicPlayerManager.fetchPlaylist(this@MagneticSensorService, newSettings.telegramBotToken, newSettings.telegramChannelId)
                    }
                }

                updateSensorRegistration()
            }
        }

        ContextCompat.registerReceiver(this, controlReceiver, IntentFilter().apply {
            addAction("CLOCK_CLOSED_MANUALLY")
            addAction("CLOCK_OPENED")
            addAction("REQUEST_IN_CAR_STATUS")
        }, ContextCompat.RECEIVER_NOT_EXPORTED)

        // Android 14+ requires flags for system broadcasts too
        val btFilter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothDevice.ACTION_NAME_CHANGED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            registerReceiver(bluetoothReceiver, btFilter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(bluetoothReceiver, btFilter)
        }

        createNotificationChannels()
        
        // Початковий запуск сервісу в режимі "тиші"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, createMonitoringNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, createMonitoringNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(1, createMonitoringNotification())
        }
        
        // Якщо ми не в авто, відразу ПОВНІСТЮ видаляємо сповіщення
        if (!isInCar) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    private fun updateSensorRegistration() {
        Log.d("MagneticClock", "updateSensorRegistration: enabled=${currentSettings.isMonitoringEnabled}, isInCar=$isInCar")
        // Датчик орієнтується на режим isInCar, який має затримку при вимкненні
        if (currentSettings.isMonitoringEnabled && isInCar) {
            registerSensor()
        } else {
            unregisterSensor()
        }
    }

    private fun checkBluetoothStatus() {
        if (!currentSettings.isMonitoringEnabled || isCheckingBluetooth) return

        isCheckingBluetooth = true
        // Прибрано updateNotification() тут, щоб не показувати шторку завчасно

        serviceScope.launch {
            val startTime = System.currentTimeMillis()
            var attempt = 0
            
            while (System.currentTimeMillis() - startTime < 30000) {
                if (isInCar) {
                    isCheckingBluetooth = false
                    return@launch
                }

                val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                val adapter = bluetoothManager.adapter
                
                if (adapter != null && adapter.isEnabled) {
                    try {
                        if (ActivityCompat.checkSelfPermission(this@MagneticSensorService, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                            // Опитуємо лише реально ПІДКЛЮЧЕНІ в системі пристрої
                            val a2dpDevices = bluetoothManager.getConnectedDevices(BluetoothProfile.A2DP)
                            val hsDevices = bluetoothManager.getConnectedDevices(BluetoothProfile.HEADSET)
                            
                            val target = a2dpDevices.find { com.example.magneticclock.data.DeviceFilter.isTargetDevice(this@MagneticSensorService, it, currentSettings) } 
                                ?: hsDevices.find { com.example.magneticclock.data.DeviceFilter.isTargetDevice(this@MagneticSensorService, it, currentSettings) }
                            
                            if (target != null) {
                                Log.i("MagneticClock", "Target device found in system connected list: ${target.address}")
                                updateInCarState(true)
                                isCheckingBluetooth = false
                                return@launch
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MagneticClock", "BT Check Error: ${e.message}")
                    }
                }
                
                attempt++
                Log.d("MagneticClock", "BT Check Attempt $attempt...")
                delay(3000L)
            }
            
            isCheckingBluetooth = false
            if (!isInCar && !isClockShowing) {
                com.example.magneticclock.data.AppLogger.w("Цільовий BT пристрій не знайдено за 30с. Зупинка сервісу.")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            } else if (!isInCar && isClockShowing) {
                com.example.magneticclock.data.AppLogger.d("BT не знайдено, але годинник відкритий. Залишаємось активними.")
            }
        }
    }

    private fun isTargetDevice(device: BluetoothDevice?): Boolean {
        if (device == null) return false
        return com.example.magneticclock.data.DeviceFilter.isTargetDevice(this, device, currentSettings)
    }

    private fun updateInCarState(connected: Boolean) {
        com.example.magneticclock.data.AppLogger.d("updateInCarState: Фізичне з'єднання=$connected, Поточний логічний режим=$isInCar")
        
        isBTDevicePhysicallyConnected = connected
        
        if (connected) {
            btDeactivationJob?.cancel()
            btDeactivationJob = null
            
            if (!isInCar) {
                isInCar = true
                isClockModeTriggered = false
                com.example.magneticclock.data.AppLogger.i(">>> РЕЖИМ 'В АВТО' АКТИВОВАНО <<<")
                
                // КРИТИЧНО: Спочатку повідомляємо UI, потім вмикаємо датчики
                notifyInCarStatus()
                onInCarStarted()
            } else {
                updateSensorRegistration()
            }
        } else {
            if (isInCar && btDeactivationJob == null) {
                com.example.magneticclock.data.AppLogger.w("Зв'язок BT перервано, запуск таймеру затримки: ${currentSettings.inCarDeactivationDelayMs}мс")
                btDeactivationJob = serviceScope.launch {
                    updateNotification()
                    delay(currentSettings.inCarDeactivationDelayMs.milliseconds)
                    if (isActive) {
                        isInCar = false
                        com.example.magneticclock.data.AppLogger.w(">>> РЕЖИМ 'В АВТО' ВИМКНЕНО ЗА ЗАТРИМКОЮ <<<")
                        
                        // КРИТИЧНО: Повідомляємо UI про вимкнення
                        notifyInCarStatus()
                        onInCarEnded()
                        btDeactivationJob = null
                        
                        delay(5000L)
                        if (!isBTDevicePhysicallyConnected) {
                            com.example.magneticclock.data.AppLogger.i("Повне фонове закриття сервісу.")
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        }
                    }
                }
            }
        }

        // Надсилаємо ЛОГІЧНИЙ стан (isInCar), щоб іконка залишалася зеленою під час затримки
        notifyInCarStatus()
        updateNotification()
    }

    private fun onInCarStarted() {
        Log.i("MagneticClock", "inCarStarted: Starting sensors and location")
        
        // Беремо WakeLock, щоб процесор не заснув при вимкненому екрані
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MagneticClock:SensorWakeLock").apply {
                acquire()
            }
            Log.d("MagneticClock", "Partial WakeLock acquired")
        } catch (e: Exception) { Log.e("MagneticClock", "WakeLock error: ${e.message}") }

        updateSensorRegistration()
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, locationListener)
        } catch (e: SecurityException) {
            Log.e("MagneticClock", "Location permission missing in Service")
        }
    }

    private fun onInCarEnded() {
        Log.i("MagneticClock", "inCarEnded: Cleaning up")
        
        isClockModeTriggered = false
        
        wakeLock?.let {
            if (it.isHeld) it.release()
            Log.d("MagneticClock", "WakeLock released")
        }
        wakeLock = null

        updateSensorRegistration()
        locationManager.removeUpdates(locationListener)
        TripManager.onBluetoothDisconnected(this)
        
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(1001)

        isMagnetActive = false
        isClockShowing = false
        magnetActivationStartTime = 0
        magnetDeactivationStartTime = 0
        
        sendBroadcast(Intent("CLOSE_CLOCK_ACTIVITY").apply { setPackage(packageName) })
        updateNotification()
    }

    private fun performFullStop() {
        if (TripManager.isTripActive) {
            TripManager.onBluetoothDisconnected(this)
        }
        onInCarEnded()
        isInCar = false
        updateNotification()
    }

    private fun registerSensor() {
        if (isSensorRegistered) return
        
        Log.d("MagneticClock", "registerSensor() called")
        magneticSensor?.let {
            val registered = sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            isSensorRegistered = registered
            com.example.magneticclock.data.AppLogger.d("Магнітний сканер: РЕЄСТРАЦІЯ (статус=$registered)")
        } ?: Log.e("MagneticClock", "CRITICAL: Magnetic sensor not found on this device!")
    }

    private fun unregisterSensor() {
        if (!isSensorRegistered) return
        
        com.example.magneticclock.data.AppLogger.w("Магнітний сканер: ВИМКНЕННЯ")
        sensorManager.unregisterListener(this)
        isSensorRegistered = false
        
        // Обов'язково скидаємо значення в 0
        sendBroadcast(Intent("MAGNETIC_FIELD_UPDATE").apply {
            setPackage(packageName)
            putExtra("magnitude", 0f)
        })
    }

    override fun onSensorChanged(event: SensorEvent?) {
        // Датчик працює ТІЛЬКИ поки активний режим isInCar (з урахуванням затримки)
        if (event == null || !isInCar || !currentSettings.isMonitoringEnabled) {
            if (isSensorRegistered) unregisterSensor() 
            return
        }
        
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitude = sqrt((x.toDouble() * x + y.toDouble() * y + z.toDouble() * z)).toFloat()
        
        lastMagnitude = magnitude

        // Логуємо раз на 5 секунд для дебагу
        if (System.currentTimeMillis() % 5000 < 200) {
            Log.v("MagneticClock", "Sensor active: magnitude=$magnitude, threshold=${currentSettings.activationThreshold}")
        }

        sendBroadcast(Intent("MAGNETIC_FIELD_UPDATE").apply {
            setPackage(packageName)
            putExtra("magnitude", magnitude)
        })

        processMagnetTrigger(magnitude)
    }

    private fun processMagnetTrigger(magnitude: Float) {
        if (!isMagnetActive) {
            if (magnitude >= currentSettings.activationThreshold) {
                magnetDeactivationStartTime = 0
                if (magnetActivationStartTime == 0L) {
                    magnetActivationStartTime = System.currentTimeMillis()
                    Log.d("MagneticClock", "Magnet threshold exceeded: $magnitude. Waiting for delay...")
                } else if (System.currentTimeMillis() - magnetActivationStartTime >= currentSettings.triggerDelayActivationMs) {
                    com.example.magneticclock.data.AppLogger.i("!!! МАГНІТ ВИЯВЛЕНО: Запуск годинника (Поле: $magnitude) !!!")
                    isMagnetActive = true
                    isClockModeTriggered = true
                    magnetActivationStartTime = 0
                    vibrate(currentSettings.activationVibrationIntensity)
                    startClockActivity()
                    updateNotification()
                }
            } else {
                if (magnetActivationStartTime != 0L) Log.d("MagneticClock", "Magnet lost before trigger: $magnitude")
                magnetActivationStartTime = 0
            }
        } else {
            if (magnitude <= currentSettings.deactivationThreshold) {
                magnetActivationStartTime = 0
                if (magnetDeactivationStartTime == 0L) {
                    magnetDeactivationStartTime = System.currentTimeMillis()
                } else if (System.currentTimeMillis() - magnetDeactivationStartTime >= currentSettings.triggerDelayDeactivationMs) {
                    com.example.magneticclock.data.AppLogger.w("!!! МАГНІТ ЗНЯТО: Закриття годинника (Поле: $magnitude) !!!")
                    isMagnetActive = false
                    magnetDeactivationStartTime = 0
                    vibrate(currentSettings.deactivationVibrationIntensity)
                    
                    if (isClockShowing) {
                        sendBroadcast(Intent("CLOSE_CLOCK_ACTIVITY").apply { setPackage(packageName) })
                    }
                }
            } else {
                magnetDeactivationStartTime = 0
            }
        }
    }

    private fun startClockActivity() {
        if (isClockShowing) {
            com.example.magneticclock.data.AppLogger.d("Спроба запуску ігнорується: Годинник вже на екрані")
            return
        }

        val intent = Intent(this, ClockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or 
                     Intent.FLAG_ACTIVITY_SINGLE_TOP or 
                     Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                     Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("show_on_lockscreen", true)
            putExtra("from_service", true)
        }

        // Пробуджуємо екран агресивно
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            val screenWakeLock = pm.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "MagneticClock:EmergencyWakeup"
            )
            screenWakeLock.acquire(5000L)
            // Не відпускаємо відразу, даємо час системі зорієнтуватися
        } catch (e: Exception) {
            Log.e("MagneticClock", "WakeLock error: ${e.message}")
        }

        isClockModeTriggered = true
        updateNotification()
        
        try { 
            startActivity(intent) 
            Log.i("MagneticClock", "startActivity executed")
        } catch (e: Exception) {
            Log.e("MagneticClock", "startActivity failed: ${e.message}")
        }
    }

    private fun updateNotification() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Сповіщення активне (відображається в шторці) ТІЛЬКИ коли є з'єднання з потрібними пристроями (isInCar)
        if (currentSettings.isMonitoringEnabled && isInCar) {
            val notification = createMonitoringNotification()
            startForegroundSafely(notification)
        } else {
            // Прибираємо будь-які повідомлення в шторці ПОВНІСТЮ
            stopForeground(STOP_FOREGROUND_REMOVE)
            manager.cancel(1)
        }
    }

    private fun startForegroundSafely(notification: Notification) {
        var type = 0
        val hasFineLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarseLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (hasFineLocation || hasCoarseLocation) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            }
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1, notification, type)
            } else {
                startForeground(1, notification)
            }
        } catch (e: SecurityException) {
            Log.e("MagneticClock", "SecurityException starting foreground with type flags: ${e.message}")
            // Fallback without location type
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(1, notification, 0)
                }
            } catch (ex: Exception) {
                Log.e("MagneticClock", "Failed fallback startForeground: ${ex.message}")
            }
        }
    }

    private fun createMonitoringNotification(): Notification {
        val targetIntent = Intent(this, ClockActivity::class.java).apply { 
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP) 
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 1, targetIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Використовуємо тихий канал для звичайного стану і гучний тільки для активації магнітом
        val channelId = if (isMagnetActive) "clock_trigger" else "magnetic_monitor"
        val priority = if (isMagnetActive) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW
        
        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Magnetic Clock")
            .setContentText("Повернутись до годинника")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true) // Робимо постійним, поки триває зв'язок з авто
            .setAutoCancel(false)
            .setPriority(priority)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)

        // Дозволяє пробивати заблокований екран при підключенні магніту
        if (isMagnetActive) {
            builder.setFullScreenIntent(pendingIntent, true)
            builder.setCategory(NotificationCompat.CATEGORY_ALARM)
        }

        return builder.build()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Знижуємо важливість, щоб сповіщення було тихим і ховалося
            val monitorChannel = NotificationChannel("magnetic_monitor", "Magnetic Field Monitor", NotificationManager.IMPORTANCE_LOW)
            
            // Канал для тригера має бути високої важливості
            val triggerChannel = NotificationChannel("clock_trigger", "Clock Trigger", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 100, 50, 100)
                setSound(null, null) // Тихий запуск, але з вібрацією для пробудження
            }
            
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(monitorChannel)
            manager.createNotificationChannel(triggerChannel)
        }
    }

    private fun vibrate(intensity: Int) {
        if (intensity > 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(100, intensity))
            } else {
                @Suppress("DEPRECATION") vibrator?.vibrate(100)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        unregisterSensor()
        unregisterReceiver(bluetoothReceiver)
        unregisterReceiver(controlReceiver)
        serviceScope.cancel()
    }
}
