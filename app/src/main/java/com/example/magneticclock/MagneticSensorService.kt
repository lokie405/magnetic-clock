package com.example.magneticclock

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
import android.os.*
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.magneticclock.data.AppSettings
import com.example.magneticclock.data.SettingsManager
import kotlinx.coroutines.*
import kotlin.math.sqrt

class MagneticSensorService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var magneticSensor: Sensor? = null
    private lateinit var settingsManager: SettingsManager
    private var currentSettings = AppSettings()
    private var vibrator: Vibrator? = null
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var activationStartTime: Long = 0
    private var deactivationStartTime: Long = 0
    private var isClockActive = false
    private var isBluetoothConnected = false

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
                    com.example.magneticclock.data.TripManager.onClockOpened(currentSettings.tripLogDwellMinutes)
                }
            }
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            if (action == BluetoothDevice.ACTION_ACL_CONNECTED || action == BluetoothDevice.ACTION_ACL_DISCONNECTED) {
                checkBluetoothStatus()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
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
                val wasBluetoothTriggerEnabled = currentSettings.useBluetoothTrigger
                val wasMonitoringEnabled = currentSettings.isMonitoringEnabled
                val oldDeviceName = currentSettings.bluetoothTriggerDeviceName
                
                currentSettings = newSettings
                
                if (newSettings.isMonitoringEnabled != wasMonitoringEnabled || 
                    newSettings.useBluetoothTrigger != wasBluetoothTriggerEnabled ||
                    newSettings.bluetoothTriggerDeviceName != oldDeviceName) {
                    
                    // Reset trigger state
                    activationStartTime = 0
                    deactivationStartTime = 0
                    
                    updateMonitoringState()
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
        if (!currentSettings.useBluetoothTrigger) {
            isBluetoothConnected = true
            updateMonitoringState()
            return
        }

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        
        if (adapter == null || !adapter.isEnabled) {
            isBluetoothConnected = false
            updateMonitoringState()
            return
        }

        // Check already connected devices via profile proxy
        adapter.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                var found = false
                try {
                    val devices = proxy?.connectedDevices
                    devices?.forEach { device ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                            ActivityCompat.checkSelfPermission(this@MagneticSensorService, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                            // Can't check name, assume not found
                        } else {
                            if (device.name == currentSettings.bluetoothTriggerDeviceName) {
                                found = true
                            }
                        }
                    }
                } catch (e: SecurityException) {
                    // Permission issue
                } finally {
                    adapter.closeProfileProxy(profile, proxy)
                }
                
                isBluetoothConnected = found
                updateMonitoringState()
            }

            override fun onServiceDisconnected(profile: Int) {}
        }, BluetoothProfile.A2DP)
    }

    private fun updateMonitoringState() {
        val shouldMonitor = currentSettings.isMonitoringEnabled && 
                           (!currentSettings.useBluetoothTrigger || isBluetoothConnected)
        
        if (shouldMonitor) {
            registerSensor()
        } else {
            isClockActive = false
            unregisterSensor()
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
        val text = if (currentSettings.useBluetoothTrigger && !isBluetoothConnected) {
            "Waiting for Bluetooth connection (${currentSettings.bluetoothTriggerDeviceName})..."
        } else {
            "Monitoring magnetic field..."
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
        val intent = Intent(this, ClockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
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
