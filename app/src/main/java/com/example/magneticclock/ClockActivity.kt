package com.example.magneticclock

import android.content.*
import androidx.core.content.ContextCompat
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.example.magneticclock.data.AppSettings
import com.example.magneticclock.data.SettingsManager
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
    private var currentSpeed = mutableFloatStateOf(0f)
    private var phoneTemperature = mutableFloatStateOf(0f)
    private var batteryLevel = mutableIntStateOf(0)

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if ((level != -1) && (scale != -1)) {
                    batteryLevel.intValue = ((level * 100) / scale.toFloat()).toInt()
                }
                
                // Temperature is in tenths of a degree Celsius
                val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
                phoneTemperature.floatValue = temp / 10f
            }
        }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            // speed is in m/s, convert to km/h
            currentSpeed.floatValue = location.speed * 3.6f
        }
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "CLOSE_CLOCK_ACTIVITY" -> {
                    // Exit to home screen
                    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(homeIntent)
                    finish()
                }
                "MAGNETIC_FIELD_UPDATE" -> {
                    currentMagnitude.floatValue = intent.getFloatExtra("magnitude", 0f)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Window flags for AOD behavior
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
        )
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        settingsManager = SettingsManager(this)
        weatherManager = WeatherManager(this)

        sendBroadcast(Intent("CLOCK_OPENED").apply { setPackage(packageName) })
        
        val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, batteryFilter)
        
        ContextCompat.registerReceiver(
            this,
            closeReceiver,
            IntentFilter().apply {
                addAction("CLOSE_CLOCK_ACTIVITY")
                addAction("MAGNETIC_FIELD_UPDATE")
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        setContent {
            val scope = rememberCoroutineScope()
            val settingsState by settingsManager.settingsFlow.collectAsState(initial = AppSettings())
            
            // Apply brightness settings to the window
            LaunchedEffect(settingsState.isAutoBrightness, settingsState.brightness) {
                val lp = window.attributes
                if (settingsState.isAutoBrightness) {
                    lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                } else {
                    // screenBrightness takes 0.0 to 1.0
                    lp.screenBrightness = settingsState.brightness.coerceIn(0.01f, 1.0f)
                }
                window.attributes = lp
            }

            // Automatic screen on/off based on battery level
            LaunchedEffect(batteryLevel.intValue) {
                if (batteryLevel.intValue > 20) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            LaunchedEffect(settingsState.showWeather) {
                if (settingsState.showWeather) {
                    while (true) {
                        weatherData.value = weatherManager.fetchWeather()
                        delay(30.minutes)
                    }
                }
            }

            LaunchedEffect(settingsState.showSpeed) {
                val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
                if (settingsState.showSpeed) {
                    try {
                        locationManager.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER,
                            1000L,
                            1f,
                            locationListener,
                        )
                    } catch (e: SecurityException) {
                        // Permission not granted
                    }
                } else {
                    locationManager.removeUpdates(locationListener)
                    currentSpeed.floatValue = 0f
                }
            }

            ClockScreen(
                settings = settingsState,
                batteryLevel = batteryLevel.intValue,
                magnitude = currentMagnitude.floatValue,
                weather = weatherData.value,
                speed = currentSpeed.floatValue,
                phoneTemp = phoneTemperature.floatValue,
                onSettingsChanged = { newSettings ->
                    scope.launch { settingsManager.updateSettings(newSettings) }
                },
                onHotspotToggle = { toggleHotspot() },
                onDoubleTap = {
                    val intent = Intent(Intent.ACTION_VOICE_COMMAND).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    try {
                        startActivity(intent)
                    } catch (_: Exception) {
                        // Voice command not supported or no assistant found
                    }
                },
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

    private fun toggleHotspot() {
        val intents = listOf(
            // Try very specific hotspot settings activities for different manufacturers
            Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.Settings\$WifiApSettingsActivity")),
            Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.wifi.tether.WifiTetherSettings")),
            Intent("android.settings.WIFI_AP_SETTINGS"),
            // Fallback to Tethering settings
            Intent().apply {
                component = ComponentName("com.android.settings", "com.android.settings.Settings\$TetherSettingsActivity")
            },
            // Final fallbacks
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                return
            } catch (_: Exception) {
                // Continue to next intent
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        locationManager.removeUpdates(locationListener)
        unregisterReceiver(closeReceiver)
        unregisterReceiver(batteryReceiver)
    }
}
