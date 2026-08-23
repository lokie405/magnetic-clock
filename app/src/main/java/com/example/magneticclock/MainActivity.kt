package com.example.magneticclock

import android.content.*
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.example.magneticclock.data.AppSettings
import com.example.magneticclock.data.JournalManager
import com.example.magneticclock.data.SettingsManager
import com.example.magneticclock.ui.JournalScreen
import com.example.magneticclock.ui.SettingsScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var settingsManager: SettingsManager
    private var currentMagnitude = mutableFloatStateOf(0f)
    private var currentScreen = mutableStateOf("settings") // "settings" or "journal"

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "MAGNETIC_FIELD_UPDATE" -> {
                    currentMagnitude.floatValue = intent.getFloatExtra("magnitude", 0f)
                }
                "CLOSE_CLOCK_ACTIVITY" -> {
                    // Minimize the app to home screen
                    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(homeIntent)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = SettingsManager(this)

        val serviceIntent = Intent(this, MagneticSensorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        
        val filter = IntentFilter().apply {
            addAction("MAGNETIC_FIELD_UPDATE")
            addAction("CLOSE_CLOCK_ACTIVITY")
        }
        
        ContextCompat.registerReceiver(
            this, 
            receiver, 
            filter, 
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        setContent {
            val scope = rememberCoroutineScope()
            val settingsState = settingsManager.settingsFlow.collectAsState(initial = AppSettings())
            
            // Initialize Mock Data (Force recreation with new format)
            LaunchedEffect(Unit) {
                JournalManager.generateMockData(this@MainActivity)
            }

            MaterialTheme(
                colorScheme = if (settingsState.value.isDarkMode) darkColorScheme() else lightColorScheme(),
            ) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    if (currentScreen.value == "journal") {
                        JournalScreen(onBack = { currentScreen.value = "settings" })
                    } else {
                        SettingsScreen(
                            settings = settingsState.value,
                            magnitude = currentMagnitude.floatValue,
                            onSettingsChanged = { newSettings ->
                                scope.launch { settingsManager.updateSettings(newSettings) }
                            },
                            onPreviewClick = {
                                val intent = Intent(this@MainActivity, ClockActivity::class.java)
                                startActivity(intent)
                            },
                            onViewJournal = {
                                currentScreen.value = "journal"
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
    }
}
