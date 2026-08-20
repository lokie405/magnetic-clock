package com.example.magneticclock

import android.content.*
import androidx.core.content.ContextCompat
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.example.magneticclock.data.AppSettings
import com.example.magneticclock.data.SettingsManager
import com.example.magneticclock.ui.ClockScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ClockActivity : ComponentActivity() {

    private lateinit var settingsManager: SettingsManager
    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Exit to home screen
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Window flags for AOD behavior
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        settingsManager = SettingsManager(this)
        
        ContextCompat.registerReceiver(
            this,
            closeReceiver,
            IntentFilter("CLOSE_CLOCK_ACTIVITY"),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        setContent {
            val settingsState = remember { mutableStateOf(AppSettings()) }
            val batteryLevel = remember { mutableIntStateOf(0) }
            
            LaunchedEffect(Unit) {
                settingsState.value = settingsManager.settingsFlow.first()
                updateBatteryLevel { batteryLevel.intValue = it }
            }

            ClockScreen(
                settings = settingsState.value,
                batteryLevel = batteryLevel.intValue,
                onHotspotToggle = { toggleHotspot() },
                onClose = { 
                    sendBroadcast(Intent("CLOCK_CLOSED_MANUALLY").apply { setPackage(packageName) })
                    finish() 
                }
            )
        }
    }

    private fun updateBatteryLevel(onResult: (Int) -> Unit) {
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = registerReceiver(null, intentFilter)
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level != -1 && scale != -1) {
            onResult((level * 100 / scale.toFloat()).toInt())
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
            } catch (e: Exception) {
                // Continue to next intent
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(closeReceiver)
    }
}
