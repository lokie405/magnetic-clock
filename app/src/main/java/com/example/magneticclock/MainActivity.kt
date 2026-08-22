package com.example.magneticclock

import android.content.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.Manifest
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.magneticclock.data.AppSettings
import com.example.magneticclock.data.SettingsManager
import com.example.magneticclock.ui.availableFonts
import com.example.magneticclock.ui.getFontFamily
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private lateinit var settingsManager: SettingsManager
    private var currentMagnitude = mutableStateOf(0f)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "MAGNETIC_FIELD_UPDATE" -> {
                    currentMagnitude.value = intent.getFloatExtra("magnitude", 0f)
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
            
            MaterialTheme(
                colorScheme = if (settingsState.value.isDarkMode) darkColorScheme() else lightColorScheme(),
            ) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    SettingsScreen(
                        settings = settingsState.value,
                        magnitude = currentMagnitude.value,
                        onSettingsChanged = { newSettings ->
                            scope.launch { settingsManager.updateSettings(newSettings) }
                        },
                    ) {
                        val intent = Intent(this, ClockActivity::class.java)
                        startActivity(intent)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    magnitude: Float,
    onSettingsChanged: (AppSettings) -> Unit,
    onPreviewClick: () -> Unit,
) {
    val context = LocalContext.current
    
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (!granted) {
            // Optional: Show message that weather needs location
        }
    }

    val fontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        uri?.let {
            val path = copyFileToInternalStorage(context, it)
            path?.let { p ->
                val newCustomFonts = settings.customFonts + p
                onSettingsChanged(
                    settings.copy(
                        customClockFontPath = p,
                        customFonts = newCustomFonts,
                    ),
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Magnetic Clock Settings") },
                actions = {
                    IconButton(onClick = onPreviewClick) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Preview Clock")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("Service Monitoring", fontWeight = FontWeight.Bold)
                            Text(
                                text = if (settings.isMonitoringEnabled) "Active" else "Inactive", 
                                fontSize = 12.sp, 
                                color = if (settings.isMonitoringEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Switch(
                            checked = settings.isMonitoringEnabled,
                            onCheckedChange = {
                                onSettingsChanged(settings.copy(isMonitoringEnabled = it))
                            },
                        )
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Dark Mode", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = settings.isDarkMode,
                        onCheckedChange = {
                            onSettingsChanged(settings.copy(isDarkMode = it))
                        },
                    )
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Current Magnetic Field: ${"%.2f".format(magnitude)} µT", 
                            fontSize = 18.sp, 
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Activation: ${settings.activationThreshold} µT | Deactivation: ${settings.deactivationThreshold} µT",
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            item {
                Text("Trigger Thresholds (µT)", fontWeight = FontWeight.Bold)
                ThresholdControl("Activation", settings.activationThreshold) {
                    onSettingsChanged(settings.copy(activationThreshold = it))
                }
                Spacer(Modifier.height(8.dp))
                ThresholdControl("Deactivation", settings.deactivationThreshold) {
                    onSettingsChanged(settings.copy(deactivationThreshold = it))
                }
            }

            item {
                Text("Trigger Delays (Seconds)", fontWeight = FontWeight.Bold)
                
                Text("Activation: ${settings.triggerDelayActivationMs / 1000f}s")
                Slider(
                    value = settings.triggerDelayActivationMs.toFloat(),
                    onValueChange = { 
                        val rounded = (it / 500).toInt() * 500L
                        onSettingsChanged(settings.copy(triggerDelayActivationMs = rounded)) 
                    },
                    valueRange = 0f..5000f,
                    steps = 9,
                )
                
                Spacer(Modifier.height(8.dp))
                
                Text("Deactivation: ${settings.triggerDelayDeactivationMs / 1000f}s")
                Slider(
                    value = settings.triggerDelayDeactivationMs.toFloat(),
                    onValueChange = { 
                        val rounded = (it / 500).toInt() * 500L
                        onSettingsChanged(settings.copy(triggerDelayDeactivationMs = rounded)) 
                    },
                    valueRange = 0f..5000f,
                    steps = 9,
                )
            }

            item {
                Text("Notifications", fontWeight = FontWeight.Bold)
                Button(
                    onClick = {
                        val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Grant Notification Access")
                }
            }
            
            item {
                Text("Vibration Power", fontWeight = FontWeight.Bold)
                
                Text("Activation Vibration: ${if (settings.activationVibrationIntensity == 0) "Off" else settings.activationVibrationIntensity}")
                Slider(
                    value = settings.activationVibrationIntensity.toFloat(),
                    onValueChange = { onSettingsChanged(settings.copy(activationVibrationIntensity = it.toInt())) },
                    valueRange = 0f..255f,
                )
                
                Spacer(Modifier.height(8.dp))
                
                Text("Deactivation Vibration: ${if (settings.deactivationVibrationIntensity == 0) "Off" else settings.deactivationVibrationIntensity}")
                Slider(
                    value = settings.deactivationVibrationIntensity.toFloat(),
                    onValueChange = { onSettingsChanged(settings.copy(deactivationVibrationIntensity = it.toInt())) },
                    valueRange = 0f..255f,
                )
            }

            item {
                Text("Fonts", fontWeight = FontWeight.Bold)
                FontSelector(
                    label = "Clock Font",
                    selectedFont = settings.clockFont,
                    customPath = settings.customClockFontPath,
                    customFonts = settings.customFonts,
                    onFontSelected = { name, path ->
                        if (name == "ADD_NEW") {
                            fontPickerLauncher.launch("*/*")
                        } else {
                            onSettingsChanged(settings.copy(clockFont = name, customClockFontPath = path))
                        }
                    },
                ) { path ->
                    val newCustomFonts = settings.customFonts - path
                    val isSelected = settings.customClockFontPath == path
                    onSettingsChanged(
                        settings.copy(
                            customFonts = newCustomFonts,
                            clockFont = if (isSelected) "Default" else settings.clockFont,
                            customClockFontPath = if (isSelected) null else settings.customClockFontPath,
                        )
                    )
                    // Delete file from storage
                    try {
                        File(path).delete()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            item {
                Text("Sizes", fontWeight = FontWeight.Bold)
                SizeSlider("Clock Size", settings.clockSizeSp) { onSettingsChanged(settings.copy(clockSizeSp = it)) }
                SizeSlider("Date Size", settings.dateSizeSp) { onSettingsChanged(settings.copy(dateSizeSp = it)) }
                SizeSlider("Battery Size", settings.batterySizeSp) { onSettingsChanged(settings.copy(batterySizeSp = it)) }
            }
            
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("OnePlus Style (Red '1' in Hour & Min)")
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = settings.isOnePlusStyle,
                        onCheckedChange = {
                            onSettingsChanged(settings.copy(isOnePlusStyle = it))
                        },
                    )
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Show Magnetic Field in Clock")
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = settings.showMagneticField,
                        onCheckedChange = {
                            onSettingsChanged(settings.copy(showMagneticField = it))
                        },
                    )
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Show Weather in Clock")
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = settings.showWeather,
                        onCheckedChange = {
                            if (it) {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                            onSettingsChanged(settings.copy(showWeather = it))
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun ThresholdControl(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Column {
        Text(
            text = "$label: ${value.toInt()} µT", 
            fontSize = 14.sp,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onValueChange((value - 5).coerceAtLeast(0f)) }) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease")
            }
            Slider(
                value = value,
                onValueChange = { onValueChange((it / 5).toInt() * 5f) },
                valueRange = 0f..500f,
                steps = 99,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onValueChange((value + 5).coerceAtMost(500f)) }) {
                Icon(Icons.Default.Add, contentDescription = "Increase")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FontSelector(
    label: String,
    selectedFont: String,
    customPath: String?,
    customFonts: Set<String>,
    onFontSelected: (String, String?) -> Unit,
    onDeleteFont: (String) -> Unit,
) {
    val currentTime = remember { Calendar.getInstance().time }
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val timeStr = timeFormat.format(currentTime)

    Column {
        Text(label, fontSize = 14.sp)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            // Built-in Fonts
            items(availableFonts) { font ->
                val isSelected = (font == selectedFont) && (customPath == null)
                Surface(
                    onClick = { onFontSelected(font, null) },
                    shape = MaterialTheme.shapes.medium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.height(60.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = timeStr,
                            fontSize = 18.sp,
                            fontFamily = getFontFamily(font, null),
                        )
                        Text(font, fontSize = 10.sp)
                    }
                }
            }
            
            // Custom Fonts
            items(customFonts.toList()) { path ->
                val file = File(path)
                val fileName = file.name
                val isSelected = (customPath == path)
                var showDeleteConfirm by remember { mutableStateOf(value = false) }

                if (showDeleteConfirm) {
                    AlertDialog(
                        onDismissRequest = { showDeleteConfirm = false },
                        title = { Text("Видалити шрифт?") },
                        text = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Ви впевнені, що хочете видалити цей шрифт із програми?")
                                Spacer(modifier = Modifier.height(16.dp))
                                // Preview of the font being deleted
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.height(60.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        Text(
                                            text = timeStr,
                                            fontSize = 18.sp,
                                            fontFamily = getFontFamily("Custom", path),
                                        )
                                        Text(
                                            text = fileName.take(10),
                                            fontSize = 10.sp,
                                        )
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                onDeleteFont(path)
                                showDeleteConfirm = false
                            }) {
                                Text("Видалити", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteConfirm = false }) {
                                Text("Скасувати")
                            }
                        }
                    )
                }
                
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .height(60.dp)
                        .combinedClickable(
                            onClick = { onFontSelected("Custom", path) },
                            onLongClick = { showDeleteConfirm = true }
                        ),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = timeStr,
                            fontSize = 18.sp,
                            fontFamily = getFontFamily("Custom", path),
                        )
                        Text(
                            text = fileName.take(10),
                            fontSize = 10.sp,
                        )
                    }
                }
            }

            // Add New Button
            item {
                Surface(
                    onClick = { onFontSelected("ADD_NEW", null) },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.height(60.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Font")
                        Spacer(Modifier.width(4.dp))
                        Text("Load Font", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun SizeSlider(label: String, value: Int, onValueChange: (Int) -> Unit) {
    Column {
        Text("$label: $value sp", fontSize = 14.sp)
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 10f..200f,
        )
    }
}

fun copyFileToInternalStorage(context: Context, uri: Uri): String? {
    val returnCursor = context.contentResolver.query(uri, null, null, null, null)
    val nameIndex = returnCursor?.getColumnIndex(OpenableColumns.DISPLAY_NAME)
    returnCursor?.moveToFirst()
    var name = returnCursor?.getString(nameIndex ?: 0) ?: "custom_font.ttf"
    returnCursor?.close()

    val fontsDir = File(context.filesDir, "fonts")
    if (!fontsDir.exists()) fontsDir.mkdirs()

    var file = File(fontsDir, name)
    if (file.exists()) {
        // If file exists, prepend timestamp to make it unique
        name = "${System.currentTimeMillis()}_$name"
        file = File(fontsDir, name)
    }

    try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val outputStream = FileOutputStream(file)
        var read: Int
        val maxBufferSize = 1 * 1024 * 1024
        val bytesAvailable = inputStream?.available() ?: 0
        val bufferSize = bytesAvailable.coerceAtMost(maxBufferSize)
        val buffers = ByteArray(bufferSize)
        while (inputStream?.read(buffers).also { read = it ?: -1 } != -1) {
            outputStream.write(buffers, 0, read)
        }
        inputStream?.close()
        outputStream.close()
        return file.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}
