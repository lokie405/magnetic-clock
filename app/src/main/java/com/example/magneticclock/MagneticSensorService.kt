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
    private var isBTDevicePhysicallyConnected = false // Physical BT connection status (instant)
    private var isMagnetActive = false
    private var isClockShowing = false
    private var isClockModeTriggered = false // Стає true після першого магніту в сесії BT
    
    // Trigger Timers
    private var magnetActivationStartTime: Long = 0
    private var magnetDeactivationStartTime: Long = 0
    private var btDeactivationJob: Job? = null
    
    private var wakeLock: PowerManager.WakeLock? = null

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            TripManager.updateLocation(location)
        }
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP_SERVICE") {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
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
                    sendBroadcast(Intent("IN_CAR_STATUS_UPDATE").apply {
                        setPackage(packageName)
                        putExtra("is_in_car", isInCar)
                    })
                }
            }
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            Log.d("MagneticClock", "Bluetooth Broadcast received: $action")
            
            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent?.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION") intent?.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            }
            
            var name = try {
                if (ActivityCompat.checkSelfPermission(context!!, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    device?.name
                } else null
            } catch (e: SecurityException) { null }

            // Спроба отримати ім'я з екстри, якщо getName() повернув null (буває на заблокованому екрані)
            if (name == null) {
                name = intent?.getStringExtra(BluetoothDevice.EXTRA_NAME)
            }

            when (action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    Log.i("MagneticClock", "ACL Connected: $name (address: ${device?.address})")
                    // Даємо системі трохи часу на оновлення кешу імен
                    serviceScope.launch {
                        delay(1000L)
                        if (isTargetDevice(device)) {
                            updateInCarState(true)
                        } else {
                            checkBluetoothStatus()
                        }
                    }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    Log.i("MagneticClock", "ACL Disconnected: $name")
                    checkBluetoothStatus()
                }
                BluetoothDevice.ACTION_NAME_CHANGED -> {
                    Log.i("MagneticClock", "Bluetooth Name Changed: $name")
                    checkBluetoothStatus()
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent?.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    if (state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF) {
                        updateInCarState(false)
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("MagneticClock", "Service onCreate()")
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        magneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED) 
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
            
        settingsManager = SettingsManager(this)
        
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION") getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        serviceScope.launch {
            settingsManager.settingsFlow.collect { newSettings ->
                val wasEnabled = currentSettings.isMonitoringEnabled
                val oldTarget = currentSettings.bluetoothTriggerDeviceName
                currentSettings = newSettings
                
                if (newSettings.isMonitoringEnabled != wasEnabled || oldTarget != newSettings.bluetoothTriggerDeviceName) {
                    if (newSettings.isMonitoringEnabled) {
                        checkBluetoothStatus()
                    } else {
                        performFullStop()
                    }
                }
                updateSensorRegistration()
            }
        }

        ContextCompat.registerReceiver(this, controlReceiver, IntentFilter().apply {
            addAction("CLOCK_CLOSED_MANUALLY")
            addAction("CLOCK_OPENED")
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
        
        val startNotification = createMonitoringNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, startNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, startNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(1, startNotification)
        }
        
        checkBluetoothStatus()
    }

    private fun updateSensorRegistration() {
        // Датчик орієнтується на режим isInCar, який має затримку при вимкненні
        if (currentSettings.isMonitoringEnabled && isInCar) {
            registerSensor()
        } else {
            unregisterSensor()
        }
    }

    private fun checkBluetoothStatus() {
        if (!currentSettings.isMonitoringEnabled) return

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        
        if (adapter == null || !adapter.isEnabled) {
            updateInCarState(false)
            return
        }

        var foundInA2DP = false
        var foundInHeadset = false
        var checksCompleted = 0

        val profileListener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                try {
                    val devices = proxy?.connectedDevices
                    val hasTarget = devices?.any { device ->
                        isTargetDevice(device)
                    } ?: false
                    
                    if (profile == BluetoothProfile.A2DP) foundInA2DP = hasTarget
                    if (profile == BluetoothProfile.HEADSET) foundInHeadset = hasTarget
                    
                    Log.d("MagneticClock", "Profile $profile check: hasTarget=$hasTarget")
                } catch (e: Exception) {
                    Log.e("MagneticClock", "Error in profile $profile: ${e.message}")
                } finally {
                    checksCompleted++
                    adapter.closeProfileProxy(profile, proxy)
                    
                    // Коли обидва профілі перевірено
                    if (checksCompleted >= 2) {
                        updateInCarState(foundInA2DP || foundInHeadset)
                    }
                }
            }
            override fun onServiceDisconnected(profile: Int) {}
        }

        adapter.getProfileProxy(this, profileListener, BluetoothProfile.A2DP)
        adapter.getProfileProxy(this, profileListener, BluetoothProfile.HEADSET)
        
        // Fallback: миттєва перевірка через менеджер (на випадок якщо Proxy затримається)
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                val a2dpDevices = bluetoothManager.getConnectedDevices(BluetoothProfile.A2DP)
                val hsDevices = bluetoothManager.getConnectedDevices(BluetoothProfile.HEADSET)
                if (a2dpDevices.any { isTargetDevice(it) } || hsDevices.any { isTargetDevice(it) }) {
                    updateInCarState(true)
                }
            }
        } catch (e: Exception) {}
    }

    private fun isTargetDevice(device: BluetoothDevice?): Boolean {
        if (device == null) return false
        
        // 1. Спробуємо отримати ім'я напряму
        var name = try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                device.name
            } else null
        } catch (e: SecurityException) { null }

        // 2. Якщо ім'я null (що часто буває на заблокованому екрані), 
        // шукаємо його в списку спарених пристроїв за адресою
        if (name == null) {
            try {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                    name = bluetoothManager.adapter.bondedDevices.find { it.address == device.address }?.name
                }
            } catch (e: Exception) {}
        }

        Log.d("MagneticClock", "Checking device: $name (address: ${device.address})")
        return isTargetName(name)
    }

    private fun isTargetName(name: String?): Boolean {
        if (name == null) return false
        
        val cleanName = name.lowercase().trim()
        
        // 1. Перевірка Ford Focus 3
        if (cleanName.contains("ford") || cleanName.contains("focus") || cleanName.contains("sync")) {
            Log.i("MagneticClock", "Target detected: Ford Focus 3 system ($name)")
            return true
        }
        
        // 2. Перевірка Hawit TW929 Pro
        if (cleanName.contains("hawit") || cleanName.contains("tw929")) {
            Log.i("MagneticClock", "Target detected: Hawit headphones ($name)")
            return true
        }

        // 3. Перевірка імені з налаштувань
        val targetSettings = currentSettings.bluetoothTriggerDeviceName.lowercase().trim()
        if (targetSettings.isNotEmpty() && cleanName.contains(targetSettings)) {
            return true
        }

        return false
    }

    private fun updateInCarState(connected: Boolean) {
        Log.d("MagneticClock", "updateInCarState: connected=$connected, current isInCar=$isInCar")
        
        isBTDevicePhysicallyConnected = connected
        
        sendBroadcast(Intent("IN_CAR_STATUS_UPDATE").apply {
            setPackage(packageName)
            putExtra("is_in_car", connected)
        })

        if (connected) {
            btDeactivationJob?.cancel()
            btDeactivationJob = null
            
            if (!isInCar) {
                isInCar = true
                isClockModeTriggered = false // Скидаємо при новому підключенні
                Log.i("MagneticClock", ">>> inCar mode: STARTED <<<")
                onInCarStarted()
            } else {
                updateSensorRegistration()
            }
        } else {
            // При розриві НЕ вимикаємо датчик миттєво, чекаємо таймер
            if (isInCar && btDeactivationJob == null) {
                Log.d("MagneticClock", "inCar lost, starting delay: ${currentSettings.inCarDeactivationDelayMs}ms")
                btDeactivationJob = serviceScope.launch {
                    updateNotification()
                    delay(currentSettings.inCarDeactivationDelayMs.milliseconds)
                    if (isActive) {
                        isInCar = false
                        Log.i("MagneticClock", ">>> inCar mode: ENDED <<<")
                        onInCarEnded()
                        btDeactivationJob = null
                    }
                }
            }
        }
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
        Log.d("MagneticClock", "registerSensor() called")
        magneticSensor?.let {
            // NORMAL delay is more stable for background/locked screen on many devices
            val registered = sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            Log.i("MagneticClock", "Magnetic sensor registration: $registered")
        } ?: Log.e("MagneticClock", "CRITICAL: Magnetic sensor not found on this device!")
    }

    private fun unregisterSensor() {
        Log.i("MagneticClock", "!!! STOPPING SENSOR: unregisterListener called !!!")
        sensorManager.unregisterListener(this)
        // Clear UI magnitude
        sendBroadcast(Intent("MAGNETIC_FIELD_UPDATE").apply {
            setPackage(packageName)
            putExtra("magnitude", 0f)
        })
    }

    override fun onSensorChanged(event: SensorEvent?) {
        // Датчик працює, поки активний режим isInCar (враховуючи затримку)
        if (event == null || !isInCar || !currentSettings.isMonitoringEnabled) {
            if (!isInCar || !currentSettings.isMonitoringEnabled) unregisterSensor() 
            return
        }
        
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitude = sqrt((x * x) + (y * y) + (z * z))

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
                } else if (System.currentTimeMillis() - magnetActivationStartTime >= currentSettings.triggerDelayActivationMs) {
                    isMagnetActive = true
                    isClockModeTriggered = true // Фіксуємо активацію
                    magnetActivationStartTime = 0
                    vibrate(currentSettings.activationVibrationIntensity)
                    startClockActivity()
                    updateNotification()
                }
            } else {
                magnetActivationStartTime = 0
            }
        } else {
            if (magnitude <= currentSettings.deactivationThreshold) {
                magnetActivationStartTime = 0
                if (magnetDeactivationStartTime == 0L) {
                    magnetDeactivationStartTime = System.currentTimeMillis()
                } else if (System.currentTimeMillis() - magnetDeactivationStartTime >= currentSettings.triggerDelayDeactivationMs) {
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
        val intent = Intent(this, ClockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            putExtra("show_on_lockscreen", true)
        }

        // Пробуджуємо екран
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE, "MagneticClock:WakeLock")
            wakeLock.acquire(5000)
        } catch (e: Exception) {}

        // Оновлюємо ЄДИНЕ сповіщення, додаючи FullScreenIntent для пробудження
        isClockModeTriggered = true
        updateNotification()
        
        // Додаткове сповіщення для гарантованого запуску на заблокованому екрані
        // Використовуємо скасування перед надсиланням для "свіжості"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(1001)
        
        serviceScope.launch {
            delay(100L)
            manager.notify(1001, createMonitoringNotification())
        }
        
        try { startActivity(intent) } catch (e: Exception) {
            Log.e("MagneticClock", "StartActivity failed: ${e.message}")
        }
    }

    private fun updateNotification() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (isInCar) {
            val notification = createMonitoringNotification()
            
            var type = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1, notification, type)
            } else {
                startForeground(1, notification)
            }
            
            // Якщо активовано магніт, оновлюємо сповіщення з високою важливістю
            if (isMagnetActive) {
                manager.notify(1, notification)
            }
        } else {
            // Вимикаємо сповіщення, коли не в авто
            stopForeground(STOP_FOREGROUND_REMOVE)
            manager.cancel(1)
        }
    }

    private fun createMonitoringNotification(): Notification {
        // Тепер сповіщення завжди веде до годинника, згідно з вашим запитом
        val targetIntent = Intent(this, ClockActivity::class.java).apply { 
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP) 
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 1, targetIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Текст сповіщення
        val text = when {
            !currentSettings.isMonitoringEnabled -> "Програма вимкнена"
            btDeactivationJob != null -> "Bluetooth втрачено. Вимкнення через ${currentSettings.inCarDeactivationDelayMs / 1000}с..."
            !isInCar -> "Очікування Bluetooth (${currentSettings.bluetoothTriggerDeviceName})..."
            isClockModeTriggered -> "Повернутись до годинника"
            else -> "В авто. Очікування магніту..."
        }

        // Канал та пріоритет залежать від того, чи потрібно зараз розбудити екран
        val channelId = if (isMagnetActive) "clock_trigger" else "magnetic_monitor"
        val priority = if (isMagnetActive) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_MIN

        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Magnetic Clock")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(priority)
            .setVisibility(if (isMagnetActive) NotificationCompat.VISIBILITY_PUBLIC else NotificationCompat.VISIBILITY_SECRET)
            .setContentIntent(pendingIntent)

        // КРИТИЧНО: FullScreenIntent для запуску на заблокованому екрані
        if (isMagnetActive) {
            builder.setFullScreenIntent(pendingIntent, true)
            builder.setCategory(NotificationCompat.CATEGORY_ALARM)
        }

        if (currentSettings.showShadeNotification && isInCar && !isClockModeTriggered) {
            val stopIntent = Intent(this@MagneticSensorService, MagneticSensorService::class.java).apply { action = "STOP_SERVICE" }
            val stopPendingIntent = PendingIntent.getService(this@MagneticSensorService, 3, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(0, "Зупинити", stopPendingIntent)
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
