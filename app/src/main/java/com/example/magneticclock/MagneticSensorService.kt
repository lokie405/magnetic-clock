package com.example.magneticclock

import android.annotation.SuppressLint
import android.app.*
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.*
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.magneticclock.data.AppSettings
import com.example.magneticclock.data.SettingsManager
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
    private var activationStartTime: Long = 0
    private var deactivationStartTime: Long = 0
    private var isClockActive = false
    private var isBluetoothConnected = false
    private var btDeactivationJob: Job? = null

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            com.example.magneticclock.data.TripManager.updateLocation(location)
        }
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "CLOCK_CLOSED_MANUALLY" -> {
                    isClockActive = false
                    activationStartTime = 0
                    deactivationStartTime = 0
                    com.example.magneticclock.data.TripManager.onMagnetRemoved(this@MagneticSensorService)
                }
                "CLOCK_OPENED" -> {
                    isClockActive = true
                    activationStartTime = 0
                    deactivationStartTime = 0
                    com.example.magneticclock.data.TripManager.onClockOpened()
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
                @Suppress("DEPRECATION")
                intent?.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            }

            if (action == BluetoothDevice.ACTION_ACL_CONNECTED || action == BluetoothDevice.ACTION_ACL_DISCONNECTED) {
                // Check if the affected device is our target
                val deviceName = try { 
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        ActivityCompat.checkSelfPermission(context!!, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        null
                    } else {
                        device?.name
                    }
                } catch (e: SecurityException) { null }

                if (deviceName != null && (deviceName.trim().contains(currentSettings.bluetoothTriggerDeviceName.trim(), ignoreCase = true) || 
                    deviceName.trim().contains("havit", ignoreCase = true))) {
                    
                    if (action == BluetoothDevice.ACTION_ACL_CONNECTED) {
                        isBluetoothConnected = true
                        updateMonitoringState()
                    } else {
                        // For disconnection, double check with profile proxy to be sure no other target device is connected
                        checkBluetoothStatus()
                    }
                } else {
                    // If we don't know the device name yet, check all connected devices
                    checkBluetoothStatus()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        // Switch to UNCALIBRATED for more stable trigger behavior
        magneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED) 
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
            
        settingsManager = SettingsManager(this)
        
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        serviceScope.launch {
            settingsManager.settingsFlow.collect { newSettings ->
                val wasMonitoringEnabled = currentSettings.isMonitoringEnabled
                val oldDeviceName = currentSettings.bluetoothTriggerDeviceName
                
                currentSettings = newSettings
                
                if (newSettings.isMonitoringEnabled != wasMonitoringEnabled || 
                    newSettings.bluetoothTriggerDeviceName != oldDeviceName) {
                    
                    // Reset trigger state
                    activationStartTime = 0
                    deactivationStartTime = 0
                    
                    checkBluetoothStatus()
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction("CLOCK_CLOSED_MANUALLY")
            addAction("CLOCK_OPENED")
        }
        
        ContextCompat.registerReceiver(
            this,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        val btFilter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        registerReceiver(bluetoothReceiver, btFilter)

        createNotificationChannel()
        startForeground(1, createNotification())
        
        checkBluetoothStatus()
    }

    private fun checkBluetoothStatus() {
        android.util.Log.d("MagneticClock", "Checking Bluetooth status...")
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        
        if (adapter == null || !adapter.isEnabled) {
            android.util.Log.d("MagneticClock", "Bluetooth adapter is null or disabled")
            isBluetoothConnected = false
            updateMonitoringState()
            return
        }

        var targetFound = false
        
        // 1. Immediate check using BluetoothManager for already connected devices
        val profiles = intArrayOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET)
        for (profile in profiles) {
            try {
                val connectedDevices = bluetoothManager.getConnectedDevices(profile)
                connectedDevices.forEach { device ->
                    val name = try { 
                        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                            device.name 
                        } else null
                    } catch (e: SecurityException) { null }

                    android.util.Log.d("MagneticClock", "Connected device via manager (profile $profile): $name")
                    if (name != null && (name.trim().contains(currentSettings.bluetoothTriggerDeviceName.trim(), ignoreCase = true) || 
                        name.trim().contains("havit", ignoreCase = true))) {
                        android.util.Log.i("MagneticClock", "Target device matched! ($name)")
                        targetFound = true
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("MagneticClock", "Could not check profile $profile: ${e.message}")
            }
        }

        if (targetFound) {
            isBluetoothConnected = true
            updateMonitoringState()
            return
        }

        // 2. Asynchronous check via Profile Proxy as secondary check
        val profileListener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                try {
                    val devices = proxy?.connectedDevices
                    devices?.forEach { device ->
                        val name = try {
                            if (ActivityCompat.checkSelfPermission(this@MagneticSensorService, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                                device.name
                            } else null
                        } catch (e: SecurityException) { null }

                        if (name != null && (name.trim().contains(currentSettings.bluetoothTriggerDeviceName.trim(), ignoreCase = true) || 
                            name.trim().contains("havit", ignoreCase = true))) {
                            targetFound = true
                        }
                    }
                } catch (e: Exception) {
                } finally {
                    if (targetFound) {
                        isBluetoothConnected = true
                        updateMonitoringState()
                    }
                    adapter.closeProfileProxy(profile, proxy)
                }
                
                if (profile == BluetoothProfile.HEADSET && !targetFound) {
                    isBluetoothConnected = false
                    updateMonitoringState()
                }
            }
            override fun onServiceDisconnected(profile: Int) {}
        }

        adapter.getProfileProxy(this, profileListener, BluetoothProfile.A2DP)
        adapter.getProfileProxy(this, profileListener, BluetoothProfile.HEADSET)
    }

    @SuppressLint("MissingPermission")
    private fun updateMonitoringState() {
        val btTargetMet = isBluetoothConnected
        val monitoringEnabled = currentSettings.isMonitoringEnabled
        
        if (monitoringEnabled && btTargetMet) {
            // Bluetooth is active - cancel any pending deactivation
            if (btDeactivationJob != null) {
                android.util.Log.d("MagneticClock", "Bluetooth restored, cancelling deactivation timer")
                btDeactivationJob?.cancel()
                btDeactivationJob = null
            }
            registerSensor()
            
            // Start location updates when BT is connected
            try {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    1f,
                    locationListener
                )
            } catch (e: SecurityException) {
                android.util.Log.e("MagneticClock", "Location permission missing in Service")
            }
            
            notifyNotification()
        } else if (!monitoringEnabled) {
            // Global monitoring off - deactivate immediately
            btDeactivationJob?.cancel()
            btDeactivationJob = null
            performDeactivation()
        } else {
            // Bluetooth lost (btTargetMet is false) - start delay timer
            if (btDeactivationJob == null) {
                android.util.Log.d("MagneticClock", "Bluetooth lost, starting deactivation timer (${currentSettings.triggerDelayDeactivationMs}ms)")
                btDeactivationJob = serviceScope.launch {
                    notifyNotification()
                    delay(currentSettings.triggerDelayDeactivationMs.milliseconds)
                    android.util.Log.i("MagneticClock", "Deactivation timer finished, finalising trip")
                    performDeactivation()
                    btDeactivationJob = null
                }
            }
        }
    }

    private fun performDeactivation() {
        // Vibrate on deactivation
        vibrate(currentSettings.deactivationVibrationIntensity)

        // Stop location updates
        locationManager.removeUpdates(locationListener)
        com.example.magneticclock.data.TripManager.currentSpeedKmH = 0f
        
        // ALWAYS try to finalize trip when BT is officially considered lost
        com.example.magneticclock.data.TripManager.onBluetoothDisconnected(this)
        
        isClockActive = false
        unregisterSensor()
        
        // Send 0 magnitude to clear UI
        val intent = Intent("MAGNETIC_FIELD_UPDATE").apply {
            setPackage(packageName)
            putExtra("magnitude", 0f)
        }
        sendBroadcast(intent)
        notifyNotification()
    }

    private fun notifyNotification() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || 
            ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(1, createNotification())
        }
    }

    private fun registerSensor() {
        magneticSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    private fun unregisterSensor() {
        sensorManager.unregisterListener(this)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "magnetic_monitor",
                "Magnetic Field Monitor",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val text = when {
            btDeactivationJob != null -> "Bluetooth lost. Deactivating in ${currentSettings.triggerDelayDeactivationMs / 1000}s..."
            !isBluetoothConnected -> "Waiting for Bluetooth connection (${currentSettings.bluetoothTriggerDeviceName})..."
            else -> "Monitoring magnetic field..."
        }

        return NotificationCompat.Builder(this, "magnetic_monitor")
            .setContentTitle("Magnetic Clock")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        
        if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED || 
            event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val magnitude = sqrt((x * x) + (y * y) + (z * z))

            val intent = Intent("MAGNETIC_FIELD_UPDATE").apply {
                setPackage(packageName)
                putExtra("magnitude", magnitude)
            }
            sendBroadcast(intent)

            checkTrigger(magnitude)
        }
    }

    private fun checkTrigger(magnitude: Float) {
        if (!isClockActive) {
            if (magnitude >= currentSettings.activationThreshold) {
                deactivationStartTime = 0
                if (activationStartTime == 0L) {
                    activationStartTime = System.currentTimeMillis()
                } else if (System.currentTimeMillis() - activationStartTime >= currentSettings.triggerDelayActivationMs) {
                    vibrate(currentSettings.activationVibrationIntensity)
                    startClockActivity()
                    isClockActive = true
                    activationStartTime = 0
                }
            } else {
                activationStartTime = 0
            }
        } else {
            // Logic for Deactivation
            if (magnitude <= currentSettings.deactivationThreshold) {
                activationStartTime = 0
                if (deactivationStartTime == 0L) {
                    deactivationStartTime = System.currentTimeMillis()
                } else if (System.currentTimeMillis() - deactivationStartTime >= currentSettings.triggerDelayDeactivationMs) {
                    vibrate(currentSettings.deactivationVibrationIntensity)
                    isClockActive = false
                    deactivationStartTime = 0
                    
                    com.example.magneticclock.data.TripManager.onMagnetRemoved(this)
                    
                    sendBroadcast(Intent("CLOSE_CLOCK_ACTIVITY").apply { setPackage(packageName) })
                }
            } else {
                deactivationStartTime = 0
            }
        }
    }

    private fun vibrate(intensity: Int) {
        if (intensity > 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(100, intensity))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(100)
            }
        }
    }

    private fun startClockActivity() {
        // Vibrate on activation
        vibrate(currentSettings.activationVibrationIntensity)

        val intent = Intent(this, ClockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }

        // For Android 10+ background activity starts, we use a Full Screen Intent Notification
        val pendingIntent = PendingIntent.getActivity(
            this, 
            0, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "clock_trigger"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Clock Trigger",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Magnetic Clock")
            .setContentText("Launching clock...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)
            .build()

        // Briefly wake up the screen
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
            "MagneticClock:WakeLock"
        )
        wakeLock.acquire(3000)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(2, notification)
        
        // Fallback for unlocked state
        try {
            startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("MagneticClock", "Background startActivity failed: ${e.message}")
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        unregisterSensor()
        unregisterReceiver(receiver)
        unregisterReceiver(bluetoothReceiver)
        serviceScope.cancel()
    }
}
