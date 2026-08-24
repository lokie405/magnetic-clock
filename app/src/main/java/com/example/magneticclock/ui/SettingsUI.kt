package com.example.magneticclock.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.magneticclock.data.AppSettings
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    magnitude: Float,
    onSettingsChanged: (AppSettings) -> Unit,
    onPreviewClick: () -> Unit,
    onViewJournal: () -> Unit,
) {
    val context = LocalContext.current
    
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (!granted) {
            android.util.Log.d("MainActivity", "Location permission denied")
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
                Button(
                    onClick = onViewJournal,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    Icon(Icons.Default.ListAlt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("View Trip Journal")
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Sensors, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Text("Service Monitoring", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            }
                            Text(
                                text = if (settings.isMonitoringEnabled) "Active" else "Inactive", 
                                fontSize = 12.sp, 
                                color = if (settings.isMonitoringEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(start = 32.dp),
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
                    Icon(Icons.Default.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("Dark Mode", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ElectricBolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "Current Magnetic Field",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Text(
                            text = "${"%.2f".format(magnitude)} µT", 
                            fontSize = 24.sp, 
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(start = 32.dp, top = 4.dp)
                        )
                        Text(
                            text = "Activation: ${settings.activationThreshold} µT | Deactivation: ${settings.deactivationThreshold} µT",
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 32.dp)
                        )
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("Trigger Thresholds (µT)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(12.dp))
                ThresholdControl("Activation", settings.activationThreshold) {
                    onSettingsChanged(settings.copy(activationThreshold = it))
                }
                Spacer(Modifier.height(8.dp))
                ThresholdControl("Deactivation", settings.deactivationThreshold) {
                    onSettingsChanged(settings.copy(deactivationThreshold = it))
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("Trigger Delays (Seconds)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(12.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 32.dp)) {
                    Icon(Icons.Default.PlayCircle, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Activation: ${settings.triggerDelayActivationMs / 1000f}s", fontSize = 14.sp)
                }
                Slider(
                    value = settings.triggerDelayActivationMs.toFloat(),
                    onValueChange = { 
                        val rounded = (it / 500).toInt() * 500L
                        onSettingsChanged(settings.copy(triggerDelayActivationMs = rounded)) 
                    },
                    valueRange = 0f..5000f,
                    steps = 9,
                    modifier = Modifier.padding(start = 32.dp)
                )
                
                Spacer(Modifier.height(8.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 32.dp)) {
                    Icon(Icons.Default.StopCircle, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Deactivation: ${settings.triggerDelayDeactivationMs / 1000f}s", fontSize = 14.sp)
                }
                Slider(
                    value = settings.triggerDelayDeactivationMs.toFloat(),
                    onValueChange = { 
                        val rounded = (it / 500).toInt() * 500L
                        onSettingsChanged(settings.copy(triggerDelayDeactivationMs = rounded)) 
                    },
                    valueRange = 0f..5000f,
                    steps = 9,
                    modifier = Modifier.padding(start = 32.dp)
                )
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("Notifications", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        val intent = android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth().padding(start = 32.dp),
                ) {
                    Text("Grant Notification Access")
                }
            }
            
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Vibration, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("Vibration Power", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(12.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 32.dp)) {
                    Icon(Icons.Default.Vibration, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Activation Vibration: ${if (settings.activationVibrationIntensity == 0) "Off" else settings.activationVibrationIntensity}", fontSize = 14.sp)
                }
                Slider(
                    value = settings.activationVibrationIntensity.toFloat(),
                    onValueChange = { onSettingsChanged(settings.copy(activationVibrationIntensity = it.toInt())) },
                    valueRange = 0f..255f,
                    modifier = Modifier.padding(start = 32.dp)
                )
                
                Spacer(Modifier.height(8.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 32.dp)) {
                    Icon(Icons.Default.Vibration, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Deactivation Vibration: ${if (settings.deactivationVibrationIntensity == 0) "Off" else settings.deactivationVibrationIntensity}", fontSize = 14.sp)
                }
                Slider(
                    value = settings.deactivationVibrationIntensity.toFloat(),
                    onValueChange = { onSettingsChanged(settings.copy(deactivationVibrationIntensity = it.toInt())) },
                    valueRange = 0f..255f,
                    modifier = Modifier.padding(start = 32.dp)
                )
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FontDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("Fonts", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(8.dp))
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
                        ),
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Dashboard, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("Layout Selection", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(start = 32.dp, top = 12.dp, bottom = 12.dp),
                ) {
                    val layouts = listOf("Classic", "Speed Focus", "Big Digital", "Minimalist")
                    itemsIndexed(layouts) { index, title ->
                        val isSelected = settings.layoutIndex == index
                        Button(
                            onClick = { onSettingsChanged(settings.copy(layoutIndex = index)) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        ) {
                            val layoutIcon = when(index) {
                                1 -> Icons.Default.Speed
                                2 -> Icons.Default.ViewStream
                                3 -> Icons.Default.ViewHeadline
                                else -> Icons.Default.ViewCompact
                            }
                            Icon(layoutIcon, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(title)
                        }
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Straighten, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("Sizes", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(12.dp))
                SizeSlider("Clock Size", settings.clockSizeSp, Icons.Default.AccessTime) { onSettingsChanged(settings.copy(clockSizeSp = it)) }
                SizeSlider("Date Size", settings.dateSizeSp, Icons.Default.Today) { onSettingsChanged(settings.copy(dateSizeSp = it)) }
                SizeSlider("Battery Size", settings.batterySizeSp, Icons.Default.BatteryChargingFull) { onSettingsChanged(settings.copy(batterySizeSp = it)) }
                SizeSlider("Speed Size", settings.speedSizeSp, Icons.Default.Speed) { onSettingsChanged(settings.copy(speedSizeSp = it)) }
            }
            
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Brush, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("OnePlus Style (Red '1')", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
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
                    Icon(Icons.Default.Visibility, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("Show Magnetic Field", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
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
                    Icon(Icons.Default.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("Show Weather", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = settings.showWeather,
                        onCheckedChange = {
                            if (it) {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                                        android.Manifest.permission.ACCESS_COARSE_LOCATION,
                                    ),
                                )
                            }
                            onSettingsChanged(settings.copy(showWeather = it))
                        },
                    )
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("Show Speed", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = settings.showSpeed,
                        onCheckedChange = {
                            if (it) {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                                        android.Manifest.permission.ACCESS_COARSE_LOCATION,
                                    ),
                                )
                            }
                            onSettingsChanged(settings.copy(showSpeed = it))
                        },
                    )
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DeviceThermostat, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("Show Phone Temperature", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = settings.showPhoneTemperature,
                        onCheckedChange = {
                            onSettingsChanged(settings.copy(showPhoneTemperature = it))
                        },
                    )
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Bluetooth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Bluetooth Trigger", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("Only monitor magnetic field when connected to ${settings.bluetoothTriggerDeviceName}", fontSize = 12.sp)
                    }
                    Switch(
                        checked = settings.useBluetoothTrigger,
                        onCheckedChange = {
                            onSettingsChanged(settings.copy(useBluetoothTrigger = it))
                        },
                    )
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("Trip Finalization Dwell Time", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                Text("${settings.tripLogDwellMinutes} minutes", fontSize = 12.sp, modifier = Modifier.padding(start = 32.dp))
                Slider(
                    value = settings.tripLogDwellMinutes.toFloat(),
                    onValueChange = { 
                        val rounded = (it / 5).toInt() * 5
                        onSettingsChanged(settings.copy(tripLogDwellMinutes = rounded.coerceIn(5, 60))) 
                    },
                    valueRange = 5f..60f,
                    steps = 10,
                    modifier = Modifier.padding(start = 32.dp)
                )
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SettingsBackupRestore, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("Auto-Return to Clock Delay", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                Text("${"%.1f".format(settings.settingsReturnDelaySeconds)} seconds", fontSize = 12.sp, modifier = Modifier.padding(start = 32.dp))
                Slider(
                    value = settings.settingsReturnDelaySeconds,
                    onValueChange = { 
                        val rounded = (it * 2).toInt() / 2.0f
                        onSettingsChanged(settings.copy(settingsReturnDelaySeconds = rounded.coerceIn(1f, 10f))) 
                    },
                    valueRange = 1f..10f,
                    steps = 17,
                    modifier = Modifier.padding(start = 32.dp)
                )
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("Show Trip Time", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = settings.showTripTime,
                        onCheckedChange = {
                            onSettingsChanged(settings.copy(showTripTime = it))
                        },
                    )
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Route, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("Show Trip Distance", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = settings.showTripDistance,
                        onCheckedChange = {
                            onSettingsChanged(settings.copy(showTripDistance = it))
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun ThresholdControl(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.padding(start = 32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val icon = if (label == "Activation") Icons.Default.PlayCircle else Icons.Default.StopCircle
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$label: ${value.toInt()} µT", 
                fontSize = 14.sp,
            )
        }
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
                            TextButton(
                                onClick = {
                                    onDeleteFont(path)
                                    showDeleteConfirm = false
                                },
                            ) {
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
fun SizeSlider(label: String, value: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, onValueChange: (Int) -> Unit) {
    Column(modifier = Modifier.padding(start = 32.dp, top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("$label: $value sp", fontSize = 14.sp)
        }
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
