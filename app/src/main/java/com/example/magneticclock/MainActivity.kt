package com.example.magneticclock

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import com.example.magneticclock.data.AppSettings
import com.example.magneticclock.data.JournalManager
import com.example.magneticclock.data.SettingsManager
import com.example.magneticclock.data.TripEntry
import com.example.magneticclock.ui.SettingsScreen
import com.example.magneticclock.ui.TripListScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var settingsManager: SettingsManager
    private var currentMagnitude = mutableFloatStateOf(0f)
    private var isInCarStatus = mutableStateOf(false)
    private var currentScreen = mutableStateOf("settings") // "settings" or "journal"
    
    private var lastInteractionTime = mutableLongStateOf(System.currentTimeMillis())
    private var isFromClock = mutableStateOf(false)

    private fun updateInteractionTime() {
        lastInteractionTime.longValue = System.currentTimeMillis()
    }

    override fun onResume() {
        super.onResume()
        updateInteractionTime()
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
                "IN_CAR_STATUS_UPDATE" -> {
                    isInCarStatus.value = intent.getBooleanExtra("is_in_car", false)
                }
                "CLOSE_CLOCK_ACTIVITY" -> {
                    finishAffinity()
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
            addAction("IN_CAR_STATUS_UPDATE")
            addAction("CLOSE_CLOCK_ACTIVITY")
        }
        
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        // Request initial status from service
        sendBroadcast(Intent("REQUEST_IN_CAR_STATUS").apply { setPackage(packageName) })

        setContent {
            val scope = rememberCoroutineScope()
            val settingsState by settingsManager.settingsFlow.collectAsState(initial = AppSettings())
            
            var trips by remember { mutableStateOf<List<TripEntry>>(emptyList()) }
            
            fun loadTrips() {
                trips = JournalManager.loadTrips(this@MainActivity)
            }
            
            LaunchedEffect(Unit) {
                loadTrips()
            }
            
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { _ -> }

            LaunchedEffect(Unit) {
                val permissions = mutableListOf<String>()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
                }
                if (permissions.isNotEmpty()) {
                    permissionLauncher.launch(permissions.toTypedArray())
                }
            }

            if (isFromClock.value && currentScreen.value != "journal") {
                val lifecycleOwner = LocalLifecycleOwner.current
                LaunchedEffect(lastInteractionTime.longValue, currentScreen.value) {
                    delay((settingsState.settingsReturnDelaySeconds * 1000).toLong())
                    if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        finish()
                    }
                }
            }

            MaterialTheme(
                colorScheme = if (settingsState.isDarkMode) darkColorScheme() else lightColorScheme(),
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
                    when (currentScreen.value) {
                        "journal" -> {
                            BackHandler { currentScreen.value = "settings"; updateInteractionTime() }
                            TripListScreen(
                                trips = trips,
                                onDeleteTrip = { id -> 
                                    JournalManager.deleteTrip(this@MainActivity, id)
                                    loadTrips()
                                },
                                onAddressClick = { latLng ->
                                    try {
                                        val gmmIntentUri = Uri.parse("geo:$latLng?q=$latLng")
                                        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                                        mapIntent.setPackage("com.google.android.apps.maps")
                                        startActivity(mapIntent)
                                    } catch (e: Exception) {
                                        // Fallback if Maps not installed
                                        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=$latLng"))
                                        startActivity(webIntent)
                                    }
                                },
                                onRouteClick = { route ->
                                    try {
                                        // Будуємо посилання для Google Maps з точками маршруту
                                        // Оскільки посилання обмежене, беремо максимум 20 рівномірних точок
                                        val step = if (route.size > 20) route.size / 20 else 1
                                        val sampledRoute = route.filterIndexed { index, _ -> index % step == 0 }
                                        
                                        val origin = route.first()
                                        val destination = route.last()
                                        val waypoints = if (sampledRoute.size > 2) {
                                            sampledRoute.subList(1, sampledRoute.size - 1).joinToString("|")
                                        } else ""
                                        
                                        val uriString = if (waypoints.isNotEmpty()) {
                                            "https://www.google.com/maps/dir/?api=1&origin=$origin&destination=$destination&waypoints=$waypoints&travelmode=driving"
                                        } else {
                                            "https://www.google.com/maps/dir/?api=1&origin=$origin&destination=$destination&travelmode=driving"
                                        }
                                        
                                        val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString))
                                        mapIntent.setPackage("com.google.android.apps.maps")
                                        startActivity(mapIntent)
                                    } catch (e: Exception) {
                                        // Fallback to browser
                                    }
                                },
                                onBack = { currentScreen.value = "settings"; updateInteractionTime() }
                            )
                        }
                        else -> {
                            SettingsScreen(
                                settings = settingsState,
                                magnitude = currentMagnitude.floatValue,
                                isInCar = isInCarStatus.value,
                                onSettingsChanged = { newSettings ->
                                    updateInteractionTime()
                                    scope.launch { settingsManager.updateSettings(newSettings) }
                                },
                                onPreviewClick = {
                                    if (isFromClock.value) finish()
                                    else {
                                        startActivity(Intent(this@MainActivity, ClockActivity::class.java))
                                    }
                                },
                                onViewJournal = {
                                    updateInteractionTime()
                                    currentScreen.value = "journal"
                                },
                                onOpenOverlaySettings = {
                                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                                    startActivity(intent)
                                },
                                onOpenBatterySettings = {
                                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    startActivity(intent)
                                }
                            )
                        }
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
