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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.magneticclock.data.AppSettings
import com.example.magneticclock.ui.components.LinePicker
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    magnitude: Float,
    isInCar: Boolean,
    onSettingsChanged: (AppSettings) -> Unit,
    onPreviewClick: () -> Unit,
    onViewJournal: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
) {
    val context = LocalContext.current
    
    val fontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        uri?.let {
            val path = copyFileToInternalStorage(context, it)
            path?.let { p ->
                val newCustomFonts = settings.customFonts + p
                onSettingsChanged(
                    settings.copy(
                        clockFont = "Custom",
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
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Налаштування")
                        Spacer(Modifier.weight(1f))
                        Icon(
                            imageVector = if (isInCar) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                            contentDescription = "Bluetooth Status",
                            tint = if (isInCar) Color(0xFF00E676) else Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (settings.isMonitoringEnabled) "${magnitude.toInt()} µT" else "Off",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (settings.isMonitoringEnabled) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                        Spacer(Modifier.width(16.dp))
                    }
                },
                actions = {
                    IconButton(onClick = onPreviewClick) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Preview")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Button(
                    onClick = onViewJournal,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                ) {
                    Icon(Icons.Default.History, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Журнал поїздок")
                }
            }

            // 1. Switch включити/виключити программу
            item {
                SettingSwitch(
                    label = "Активність програми",
                    checked = settings.isMonitoringEnabled,
                    icon = Icons.Default.PowerSettingsNew,
                    isMain = true
                ) { onSettingsChanged(settings.copy(isMonitoringEnabled = it)) }
            }

            // 2. Switch режим день/ніч
            item {
                SettingSwitch(
                    label = "Нічний режим",
                    checked = settings.isDarkMode,
                    icon = Icons.Default.DarkMode
                ) { onSettingsChanged(settings.copy(isDarkMode = it)) }
            }

            // 3. Перемикач для сповіщення в шторці
            item {
                SettingSwitch(
                    label = "Сповіщення в шторці",
                    checked = settings.showShadeNotification,
                    icon = Icons.Default.Notifications
                ) { onSettingsChanged(settings.copy(showShadeNotification = it)) }
            }

            // 4. Пороги спрацювання magnetActive та magnetDeactive (0-300 мТ, крок 10)
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
                    Column(Modifier.padding(16.dp)) {
                        LinePicker(
                            label = "Поріг активації",
                            value = settings.activationThreshold,
                            valueRange = 0f..300f,
                            steps = 29,
                            unit = "µT"
                        ) { onSettingsChanged(settings.copy(activationThreshold = it)) }
                        
                        LinePicker(
                            label = "Поріг деактивації",
                            value = settings.deactivationThreshold,
                            valueRange = 0f..300f,
                            steps = 29,
                            unit = "µT"
                        ) { onSettingsChanged(settings.copy(deactivationThreshold = it)) }
                    }
                }
            }

            // 5. Затримки спрацювання (0-10с, крок 0.5с)
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
                    Column(Modifier.padding(16.dp)) {
                        LinePicker(
                            label = "Затримка активації",
                            value = settings.triggerDelayActivationMs / 1000f,
                            valueRange = 0f..10f,
                            steps = 19,
                            unit = "с"
                        ) { onSettingsChanged(settings.copy(triggerDelayActivationMs = (it * 1000).toLong())) }
                        
                        LinePicker(
                            label = "Затримка деактивації",
                            value = settings.triggerDelayDeactivationMs / 1000f,
                            valueRange = 0f..10f,
                            steps = 19,
                            unit = "с"
                        ) { onSettingsChanged(settings.copy(triggerDelayDeactivationMs = (it * 1000).toLong())) }
                    }
                }
            }

            // 6. Затримка при деактивації inCar
            item {
                LinePicker(
                    label = "Затримка Bluetooth (inCar)",
                    value = settings.inCarDeactivationDelayMs / 1000f,
                    valueRange = 0f..10f,
                    steps = 19,
                    unit = "с"
                ) { onSettingsChanged(settings.copy(inCarDeactivationDelayMs = (it * 1000).toLong())) }
            }

            // 7. Затримка повернення до годинника
            item {
                LinePicker(
                    label = "Автоповернення до годинника",
                    value = settings.settingsReturnDelaySeconds,
                    valueRange = 1f..30f,
                    steps = 59,
                    unit = "с"
                ) { onSettingsChanged(settings.copy(settingsReturnDelaySeconds = it)) }
            }

            // 8. Розмір шрифта
            item {
                LinePicker(
                    label = "Розмір годинника",
                    value = settings.clockSizeSp.toFloat(),
                    valueRange = 50f..400f,
                    steps = 35,
                    unit = "sp"
                ) { onSettingsChanged(settings.copy(clockSizeSp = it.toInt())) }
            }

            // 9. Шрифти
            item {
                FontSelector(
                    label = "Шрифти годинника",
                    selectedFont = settings.clockFont,
                    customPath = settings.customClockFontPath,
                    customFonts = settings.customFonts,
                    onFontSelected = { name, path ->
                        if (name == "ADD_NEW") fontPickerLauncher.launch("*/*")
                        else onSettingsChanged(settings.copy(clockFont = name, customClockFontPath = path))
                    },
                    onDeleteFont = { path ->
                        val newFonts = settings.customFonts - path
                        val isCurrent = settings.customClockFontPath == path
                        onSettingsChanged(settings.copy(
                            customFonts = newFonts,
                            clockFont = if (isCurrent) "Default" else settings.clockFont,
                            customClockFontPath = if (isCurrent) null else settings.customClockFontPath
                        ))
                        try { File(path).delete() } catch (e: Exception) {}
                    }
                )
            }

            // 10-14. Switches
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingSwitch("OnePlus Стиль (Червона '1')", settings.isOnePlusStyle, Icons.Default.Brush) { onSettingsChanged(settings.copy(isOnePlusStyle = it)) }
                    SettingSwitch("Погода", settings.showWeather, Icons.Default.Cloud) { onSettingsChanged(settings.copy(showWeather = it)) }
                    SettingSwitch("Швидкість", settings.showSpeed, Icons.Default.Speed) { onSettingsChanged(settings.copy(showSpeed = it)) }
                    SettingSwitch("Значення магнітного поля", settings.showMagneticField, Icons.Default.Sensors) { onSettingsChanged(settings.copy(showMagneticField = it)) }
                    SettingSwitch("Назва Bluetooth пристрою", settings.showConnectedDeviceName, Icons.Default.Bluetooth) { onSettingsChanged(settings.copy(showConnectedDeviceName = it)) }
                    SettingSwitch("Значки сповіщень", settings.showUnreadNotificationIcons, Icons.Default.Mail) { onSettingsChanged(settings.copy(showUnreadNotificationIcons = it)) }
                }
            }
            
            item {
                Text("Вибір макету", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    val layouts = listOf("Класичний", "Швидкість", "Великі цифри", "Мінімалізм")
                    itemsIndexed(layouts) { index, name ->
                        val isSelected = settings.layoutIndex == index
                        Button(
                            onClick = { onSettingsChanged(settings.copy(layoutIndex = index)) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) { Text(name) }
                    }
                }
            }
            
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("Необхідні дозволи", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onOpenOverlaySettings, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
                        Text("Дозволити поверх інших вікон")
                    }
                    Button(onClick = onOpenBatterySettings, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
                        Text("Вимкнути оптимізацію батареї")
                    }
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
fun SettingSwitch(label: String, checked: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, isMain: Boolean = false, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.weight(1f))
        
        val colors = if (isMain) {
            SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF00E676), // Зелений при ON
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color.Red.copy(alpha = 0.5f),
                uncheckedBorderColor = Color.Red // Червоний при OFF
            )
        } else {
            SwitchDefaults.colors() // Стандартні кольори
        }

        Switch(
            checked = checked, 
            onCheckedChange = onCheckedChange,
            colors = colors
        )
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
    val timeStr = "10:38"

    Column {
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            items(availableFonts) { font ->
                val isSelected = (font == selectedFont) && (customPath == null)
                FontButton(text = timeStr, fontName = font, label = font, isSelected = isSelected) {
                    onFontSelected(font, null)
                }
            }
            
            items(customFonts.toList()) { path ->
                val fileName = File(path).name.take(8)
                val isSelected = (customPath == path)
                FontButton(
                    text = timeStr,
                    fontName = "Custom",
                    customPath = path,
                    label = fileName,
                    isSelected = isSelected,
                    onLongClick = { onDeleteFont(path) }
                ) {
                    onFontSelected("Custom", path)
                }
            }

            item {
                Surface(
                    onClick = { onFontSelected("ADD_NEW", null) },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(80.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.padding(24.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FontButton(
    text: String,
    fontName: String,
    customPath: String? = null,
    label: String,
    isSelected: Boolean,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.size(80.dp).combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = MaterialTheme.shapes.medium,
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(text = text, fontSize = 20.sp, fontFamily = getFontFamily(fontName, customPath))
            Text(text = label, fontSize = 9.sp)
        }
    }
}

fun copyFileToInternalStorage(context: Context, uri: Uri): String? {
    val returnCursor = context.contentResolver.query(uri, null, null, null, null)
    val nameIndex = returnCursor?.getColumnIndex(OpenableColumns.DISPLAY_NAME)
    returnCursor?.moveToFirst()
    var name = returnCursor?.getString(nameIndex ?: 0) ?: "font.ttf"
    returnCursor?.close()

    val fontsDir = File(context.filesDir, "fonts").apply { if (!exists()) mkdirs() }
    val file = File(fontsDir, "${System.currentTimeMillis()}_$name")

    try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output -> input.copyTo(output) }
        }
        return file.absolutePath
    } catch (e: Exception) { e.printStackTrace() }
    return null
}
