package com.example.magneticclock

import android.content.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

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
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        setContent {
            val scope = rememberCoroutineScope()
            val settingsState = settingsManager.settingsFlow.collectAsState(initial = AppSettings())
            
            MaterialTheme(
                colorScheme = if (settingsState.value.isDarkMode) darkColorScheme() else lightColorScheme()
            ) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    SettingsScreen(
                        settings = settingsState.value,
                        magnitude = currentMagnitude.value,
                        onSettingsChanged = { newSettings ->
                            scope.launch { settingsManager.updateSettings(newSettings) }
                        },
                        onPreviewClick = {
                            val intent = Intent(this, ClockActivity::class.java)
                            startActivity(intent)
                        }
                    )
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
    onPreviewClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val fontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val path = copyFileToInternalStorage(context, it)
            // For simplicity, let's assume we pick for Clock font here. 
            // In a real app, you'd pass which element you're picking for.
            onSettingsChanged(settings.copy(customClockFontPath = path))
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
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Dark Mode", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Switch(checked = settings.isDarkMode, onCheckedChange = {
                        onSettingsChanged(settings.copy(isDarkMode = it))
                    })
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Current Magnetic Field: ${"%.2f".format(magnitude)} µT", 
                             fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Activation: ${settings.activationThreshold} µT | Deactivation: ${settings.deactivationThreshold} µT",
                             fontSize = 12.sp)
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
                Text("Trigger Delay", fontWeight = FontWeight.Bold)
                Text("${settings.triggerDurationMs / 1000f} seconds")
                Slider(
                    value = settings.triggerDurationMs.toFloat(),
                    onValueChange = { 
                        val rounded = (it / 500).toInt() * 500L
                        onSettingsChanged(settings.copy(triggerDurationMs = rounded)) 
                    },
                    valueRange = 0f..5000f,
                    steps = 9 // 0, 0.5, 1, 1.5, 2, 2.5, 3, 3.5, 4, 4.5, 5
                )
            }

            item {
                Text("Vibration Power", fontWeight = FontWeight.Bold)
                
                Text("Activation Vibration: ${if (settings.activationVibrationIntensity == 0) "Off" else settings.activationVibrationIntensity}")
                Slider(
                    value = settings.activationVibrationIntensity.toFloat(),
                    onValueChange = { onSettingsChanged(settings.copy(activationVibrationIntensity = it.toInt())) },
                    valueRange = 0f..255f
                )
                
                Spacer(Modifier.height(8.dp))
                
                Text("Deactivation Vibration: ${if (settings.deactivationVibrationIntensity == 0) "Off" else settings.deactivationVibrationIntensity}")
                Slider(
                    value = settings.deactivationVibrationIntensity.toFloat(),
                    onValueChange = { onSettingsChanged(settings.copy(deactivationVibrationIntensity = it.toInt())) },
                    valueRange = 0f..255f
                )
            }

            item {
                Text("Fonts", fontWeight = FontWeight.Bold)
                FontSelector("Clock Font", settings.clockFont, settings.customClockFontPath) { name, path ->
                    if (path != null) {
                        fontPickerLauncher.launch("*/*")
                    } else {
                        onSettingsChanged(settings.copy(clockFont = name, customClockFontPath = null))
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
                    Switch(checked = settings.isOnePlusStyle, onCheckedChange = {
                        onSettingsChanged(settings.copy(isOnePlusStyle = it))
                    })
                }
            }
            
            item {
                Text("Brightness Settings", fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Auto-Brightness")
                    Spacer(Modifier.weight(1f))
                    Switch(checked = settings.isAutoBrightness, onCheckedChange = {
                        onSettingsChanged(settings.copy(isAutoBrightness = it))
                        if (!it) requestWriteSettingsPermission(context)
                    })
                }
                if (!settings.isAutoBrightness) {
                    Slider(
                        value = settings.brightness,
                        onValueChange = { onSettingsChanged(settings.copy(brightness = it)) },
                        valueRange = 0f..1f
                    )
                }
            }
        }
    }
}

@Composable
fun ThresholdControl(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Column {
        Text("$label: ${value.toInt()} µT", fontSize = 14.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onValueChange((value - 1).coerceAtLeast(0f)) }) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease")
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = 0f..1000f,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { onValueChange((value + 1).coerceAtMost(1000f)) }) {
                Icon(Icons.Default.Add, contentDescription = "Increase")
            }
        }
    }
}

@Composable
fun FontSelector(
    label: String, 
    selectedFont: String, 
    customPath: String?, 
    onFontSelected: (String, String?) -> Unit
) {
    Column {
        Text(label, fontSize = 14.sp)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(availableFonts) { font ->
                Button(
                    onClick = { onFontSelected(font, null) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (font == selectedFont && customPath == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(font, fontSize = 12.sp)
                }
            }
            item {
                Button(
                    onClick = { onFontSelected("Custom", "") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (customPath != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(if (customPath != null) "Custom Font Loaded" else "Load Custom Font", fontSize = 12.sp)
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
            valueRange = 10f..200f
        )
    }
}

fun copyFileToInternalStorage(context: Context, uri: Uri): String? {
    val returnCursor = context.contentResolver.query(uri, null, null, null, null)
    val nameIndex = returnCursor?.getColumnIndex(OpenableColumns.DISPLAY_NAME)
    returnCursor?.moveToFirst()
    val name = returnCursor?.getString(nameIndex ?: 0) ?: "custom_font.ttf"
    returnCursor?.close()

    val file = File(context.filesDir, name)
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

fun requestWriteSettingsPermission(context: Context) {
    if (!Settings.System.canWrite(context)) {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
        intent.data = Uri.parse("package:" + context.packageName)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
