package com.example.magneticclock

import android.content.*
import android.os.Build
import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.content.ContextCompat
import com.example.magneticclock.data.AppSettings
import com.example.magneticclock.data.JournalManager
import com.example.magneticclock.data.SettingsManager
import com.example.magneticclock.ui.JournalScreen
import com.example.magneticclock.ui.SettingsScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var settingsManager: SettingsManager
    private var currentMagnitude = mutableFloatStateOf(0f)
    private var currentScreen = mutableStateOf("settings") // "settings" or "journal"
    
    private var lastInteractionTime = mutableLongStateOf(System.currentTimeMillis())
    private var isFromClock = mutableStateOf(false)

    private fun updateInteractionTime() {
        lastInteractionTime.longValue = System.currentTimeMillis()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        isFromClock.value = intent.getBooleanExtra("from_clock", false)
        if (isFromClock.value) {
            updateInteractionTime()
        }
    }

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
        
        isFromClock.value = intent.getBooleanExtra("from_clock", false)

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
            
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { _ -> }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val permissions = mutableListOf<String>()
                    if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
                    }
                    if (permissions.isNotEmpty()) {
                        permissionLauncher.launch(permissions.toTypedArray())
                    }
                }
            }

            if (isFromClock.value) {
                LaunchedEffect(lastInteractionTime.longValue) {
                    delay((settingsState.value.settingsReturnDelaySeconds * 1000).toLong())
                    finish()
                }
            }

            // Initialize Mock Data once
            LaunchedEffect(Unit) {
                if (JournalManager.loadTrips(this@MainActivity).isEmpty()) {
                    JournalManager.generateMockData(this@MainActivity)
                }
            }

            MaterialTheme(
                colorScheme = if (settingsState.value.isDarkMode) darkColorScheme() else lightColorScheme(),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent(PointerEventPass.Initial)
                                    updateInteractionTime()
                                }
                            }
                        }
                ) {
                    if (currentScreen.value == "journal") {
                        JournalScreen(onBack = { 
                            currentScreen.value = "settings"
                            updateInteractionTime()
                        })
                    } else {
                        SettingsScreen(
                            settings = settingsState.value,
                            magnitude = currentMagnitude.floatValue,
                            onSettingsChanged = { newSettings ->
                                updateInteractionTime()
                                scope.launch { settingsManager.updateSettings(newSettings) }
                            },
                            onPreviewClick = {
                                if (isFromClock.value) finish()
                                else {
                                    val intent = Intent(this@MainActivity, ClockActivity::class.java)
                                    startActivity(intent)
                                }
                            },
                            onViewJournal = {
                                updateInteractionTime()
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
