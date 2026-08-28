package com.example.magneticclock

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.KeyguardManager
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.*
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.magneticclock.data.AppSettings
import com.example.magneticclock.data.SettingsManager
import com.example.magneticclock.data.TripManager
import com.example.magneticclock.data.WeatherData
import com.example.magneticclock.data.WeatherManager
import com.example.magneticclock.ui.ClockScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes

class ClockActivity : ComponentActivity() {

    private lateinit var settingsManager: SettingsManager
    private lateinit var weatherManager: WeatherManager
    private var currentMagnitude = mutableFloatStateOf(0f)
    private var weatherData = mutableStateOf<WeatherData?>(null)
    private var phoneTemperature = mutableFloatStateOf(0f)
    private var batteryLevel = mutableIntStateOf(100)
    private var bluetoothConnected = mutableStateOf(false)
    private var connectedDeviceName = mutableStateOf("")
    private var isPowerSaveShown = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if ((level != -1) && (scale != -1)) {
                    batteryLevel.intValue = ((level * 100) / scale.toFloat()).toInt()
                    checkBatteryWarning(batteryLevel.intValue)
                }
                val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
                phoneTemperature.floatValue = temp / 10f
            }
        }
    }

    private fun checkBatteryWarning(level: Int) {
        if (level < 20 && !isPowerSaveShown) {
            isPowerSaveShown = true
            AlertDialog.Builder(this)
                .setTitle("Низький заряд батареї")
                .setMessage("Заряд менше 20%. Бажаєте завершити роботу програми для енергозбереження?")
                .setPositiveButton("Так") { _, _ -> 
                    sendBroadcast(Intent("CLOCK_CLOSED_MANUALLY").apply { setPackage(packageName) })
                    finish()
                }
                .setNegativeButton("Ні", null)
                .show()
        } else if (level >= 20) {
            isPowerSaveShown = false
        }
    }

    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "CLOSE_CLOCK_ACTIVITY" -> finish()
                "MAGNETIC_FIELD_UPDATE" -> {
                    currentMagnitude.floatValue = intent.getFloatExtra("magnitude", 0f)
                }
                "IN_CAR_STATUS_UPDATE" -> {
                    // Тепер значок на годиннику синхронізується з логікою сервісу (враховуючи затримку)
                    bluetoothConnected.value = intent.getBooleanExtra("is_in_car", false)
                    if (!bluetoothConnected.value) {
                        connectedDeviceName.value = ""
                    }
                }
            }
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            // Залишаємо лише для отримання назви пристрою, колір значка тепер через IN_CAR_STATUS_UPDATE
            if (intent?.action == BluetoothDevice.ACTION_ACL_CONNECTED) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ActivityCompat.checkSelfPermission(this@ClockActivity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return
                
                val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                connectedDeviceName.value = device?.name ?: "Connected"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Покращені прапорці для OnePlus/Android 15
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        settingsManager = SettingsManager(this)
        weatherManager = WeatherManager(this)

        sendBroadcast(Intent("CLOCK_OPENED").apply { setPackage(packageName) })
        
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        registerReceiver(bluetoothReceiver, IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        })
        
        ContextCompat.registerReceiver(this, closeReceiver, IntentFilter().apply {
            addAction("CLOSE_CLOCK_ACTIVITY")
            addAction("MAGNETIC_FIELD_UPDATE")
            addAction("IN_CAR_STATUS_UPDATE")
        }, ContextCompat.RECEIVER_NOT_EXPORTED)

        checkInitialBluetoothStatus()

        setContent {
            val scope = rememberCoroutineScope()
            val settingsState by settingsManager.settingsFlow.collectAsState(initial = AppSettings())
            
            LaunchedEffect(settingsState.isAutoBrightness, settingsState.brightness) {
                val lp = window.attributes
                lp.screenBrightness = if (settingsState.isAutoBrightness) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE 
                                     else settingsState.brightness.coerceIn(0.01f, 1.0f)
                window.attributes = lp
            }

            LaunchedEffect(batteryLevel.intValue) {
                if (batteryLevel.intValue > 20) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }

            LaunchedEffect(settingsState.showWeather) {
                if (settingsState.showWeather) {
                    while (true) {
                        weatherData.value = weatherManager.fetchWeather()
                        delay(30.minutes)
                    }
                }
            }

            ClockScreen(
                settings = settingsState,
                batteryLevel = batteryLevel.intValue,
                magnitude = currentMagnitude.floatValue,
                weather = weatherData.value,
                speed = TripManager.currentSpeedKmH,
                phoneTemp = phoneTemperature.floatValue,
                tripStartTime = TripManager.tripStartTime,
                tripDistance = TripManager.tripDistance,
                isTripActive = TripManager.isTripActive,
                bluetoothConnected = bluetoothConnected.value,
                connectedDeviceName = connectedDeviceName.value,
                onSettingsChanged = { newSettings -> scope.launch { settingsManager.updateSettings(newSettings) } },
                onSettingsClick = {
                    val intent = Intent(this@ClockActivity, MainActivity::class.java).apply {
                        putExtra("from_clock", true)
                        addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    }
                    startActivity(intent)
                },
                onHotspotToggle = { toggleHotspot() },
                onDoubleTap = { launchVoiceAssistant() },
                onSwipeDown = { openNotificationShade() },
                onMockMove = { TripManager.toggleSpeedSimulation() },
                onStartTrip = { TripManager.resetTrip() /* Manual start handled by speed now */ },
                onPowerOff = {
                    scope.launch {
                        sendBroadcast(Intent("CLOCK_CLOSED_MANUALLY").apply { setPackage(packageName) })
                        settingsManager.updateSettings(settingsState.copy(isMonitoringEnabled = false))
                        finish()
                    }
                },
                onClose = { 
                    sendBroadcast(Intent("CLOCK_CLOSED_MANUALLY").apply { setPackage(packageName) })
                    finish() 
                },
            )
        }
    }

    private fun launchVoiceAssistant() {
        try {
            startActivity(Intent(Intent.ACTION_VOICE_COMMAND).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
        } catch (_: Exception) {}
    }

    @SuppressLint("WrongConstant")
    private fun openNotificationShade() {
        try {
            val statusBarService = getSystemService("statusbar")
            val statusBarManager = Class.forName("android.app.StatusBarManager")
            val expandMethod = statusBarManager.getMethod("expandNotificationsPanel")
            expandMethod.invoke(statusBarService)
        } catch (_: Exception) {}
    }

    private fun toggleHotspot() {
        val intents = listOf(
            // Пріоритет: Режим модема (Tethering)
            Intent("android.settings.TETHER_CONFIG_SETTINGS"),
            // Налаштування Wi-Fi Hotspot
            Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.Settings\$WifiApSettingsActivity")),
            // Загальні бездротові мережі
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
            // Загальні налаштування
            Intent(Settings.ACTION_SETTINGS)
        )

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                return
            } catch (_: Exception) {}
        }
    }

    @SuppressLint("MissingPermission")
    private fun checkInitialBluetoothStatus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return

        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter?.isEnabled == true) {
            adapter.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                    proxy?.connectedDevices?.firstOrNull()?.let {
                        bluetoothConnected.value = true
                        connectedDeviceName.value = it.name ?: "Connected"
                    }
                    adapter.closeProfileProxy(profile, proxy)
                }
                override fun onServiceDisconnected(profile: Int) {}
            }, BluetoothProfile.A2DP)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sendBroadcast(Intent("CLOCK_CLOSED_MANUALLY").apply { setPackage(packageName) })
        unregisterReceiver(closeReceiver)
        unregisterReceiver(batteryReceiver)
        unregisterReceiver(bluetoothReceiver)
    }
}
