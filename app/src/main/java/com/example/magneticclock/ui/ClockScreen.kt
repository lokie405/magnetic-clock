package com.example.magneticclock.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.magneticclock.NotificationService
import com.example.magneticclock.R
import com.example.magneticclock.data.AppSettings
import com.example.magneticclock.data.TripManager
import com.example.magneticclock.data.WeatherData
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

@Composable
fun ClockScreen(
    settings: AppSettings,
    batteryLevel: Int,
    magnitude: Float,
    weather: WeatherData?,
    speed: Float,
    phoneTemp: Float,
    tripStartTime: Long,
    tripDistance: Double,
    isTripActive: Boolean,
    isResumeWindowActive: Boolean,
    bluetoothConnected: Boolean,
    connectedDeviceName: String,
    onSettingsChanged: (AppSettings) -> Unit,
    onResumeTrip: () -> Unit,
    onDismissResume: () -> Unit,
    onSettingsClick: () -> Unit,
    onHotspotToggle: () -> Unit,
    onDoubleTap: () -> Unit,
    onPowerOff: () -> Unit,
    onClose: () -> Unit,
) {
    var currentTime by remember { mutableStateOf(Calendar.getInstance().time) }
    var offset by remember { mutableStateOf(androidx.compose.ui.unit.IntOffset(0, 0)) }
    
    LaunchedEffect(Unit) {
        var lastMinute = -1
        while (true) {
            val now = Calendar.getInstance()
            currentTime = now.time
            
            val currentMin = now[Calendar.MINUTE]
            if (currentMin != lastMinute) {
                val random = Random()
                offset = androidx.compose.ui.unit.IntOffset(
                    x = random.nextInt(7) - 3,
                    y = random.nextInt(7) - 3
                )
                lastMinute = currentMin
            }
            delay(1.seconds)
        }
    }

    val backgroundColor = if (settings.isDarkMode) Color.Black else Color.White
    val contentColor = if (settings.isDarkMode) Color.White else Color.Black
    val secondaryColor = if (settings.isDarkMode) Color.Gray else Color.DarkGray

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .offset { offset }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onSettingsClick() },
                    onDoubleTap = { onDoubleTap() }
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        // OVERLAYS (Status Bar Info)
        // Top Start: Weather
        if ((settings.showWeather) && (weather != null)) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = getWeatherIcon(weather.weatherCode),
                    contentDescription = "Weather",
                    tint = getWeatherColor(weather.weatherCode),
                    modifier = Modifier.size(32.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${weather.temperature.toInt()}°C",
                    color = contentColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Top Center: Bluetooth Status
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (bluetoothConnected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                contentDescription = "Bluetooth",
                tint = if (bluetoothConnected) Color.Green else Color.Gray,
                modifier = Modifier.size(24.dp)
            )
            if (bluetoothConnected && connectedDeviceName.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = connectedDeviceName,
                    color = contentColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Top Center (Lower): Magnetic Field
        if (settings.showMagneticField) {
            Text(
                text = "${"%.1f".format(magnitude)} µT",
                color = secondaryColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 60.dp),
            )
        }

        // Top End: Battery
        val batteryColor = when {
            batteryLevel > 60 -> if (settings.isDarkMode) Color.Green else Color(0xFF388E3C)
            batteryLevel > 20 -> if (settings.isDarkMode) Color.Yellow else Color(0xFFFBC02D)
            else -> Color.Red
        }
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$batteryLevel%",
                color = batteryColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.BatteryFull,
                contentDescription = "Battery",
                tint = batteryColor,
                modifier = Modifier.size(24.dp)
            )
        }

        // Trip Resume Panel
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 120.dp, end = 24.dp)
        ) {
            AnimatedVisibility(
                visible = isResumeWindowActive,
                enter = slideInHorizontally { it } + fadeIn(),
                exit = slideOutHorizontally { it } + fadeOut()
            ) {
                TripResumePanel(
                    onConfirm = onResumeTrip,
                    onDeny = onDismissResume,
                    contentColor = contentColor,
                    secondaryColor = secondaryColor
                )
            }
        }

        // Bottom Start: Phone Temp
        if (settings.showPhoneTemperature) {
            val tempColor = when {
                phoneTemp < 38f -> Color(0xFF00E676) // Green
                phoneTemp < 45f -> Color(0xFFFFD600) // Yellow
                else -> Color.Red
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Thermostat,
                    contentDescription = "Phone Temperature",
                    tint = tempColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${"%.1f".format(phoneTemp)}°C",
                    color = tempColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Main Content Area (Layouts)
        when (settings.layoutIndex) {
            1 -> SpeedFocusLayout(
                settings = settings,
                currentTime = currentTime,
                speed = speed,
                tripStartTime = tripStartTime,
                tripDistance = tripDistance,
                isTripActive = isTripActive,
                contentColor = contentColor,
                secondaryColor = secondaryColor,
                onHotspotToggle = onHotspotToggle,
                onPowerOff = onPowerOff,
                onSettingsChanged = onSettingsChanged
            )
            2 -> BigDigitalLayout(
                settings = settings,
                currentTime = currentTime,
                speed = speed,
                contentColor = contentColor,
                onHotspotToggle = onHotspotToggle,
                onPowerOff = onPowerOff,
                onSettingsChanged = onSettingsChanged
            )
            3 -> MinimalistLayout(
                settings = settings,
                batteryLevel = batteryLevel,
                currentTime = currentTime,
                speed = speed,
                contentColor = contentColor,
                secondaryColor = secondaryColor,
                onHotspotToggle = onHotspotToggle,
                onPowerOff = onPowerOff,
                onSettingsChanged = onSettingsChanged
            )
            else -> ClassicLayout(
                settings = settings,
                currentTime = currentTime,
                speed = speed,
                tripStartTime = tripStartTime,
                tripDistance = tripDistance,
                isTripActive = isTripActive,
                contentColor = contentColor,
                secondaryColor = secondaryColor,
                onHotspotToggle = onHotspotToggle,
                onPowerOff = onPowerOff,
                onSettingsChanged = onSettingsChanged
            )
        }
    }
}

@Composable
fun TripResumePanel(
    onConfirm: () -> Unit,
    onDeny: () -> Unit,
    contentColor: Color,
    secondaryColor: Color
) {
    var timeLeft by remember { mutableStateOf(30) }
    
    LaunchedEffect(Unit) {
        while (timeLeft > 0) {
            delay(1.seconds)
            timeLeft--
        }
        onDeny()
    }

    Card(
        modifier = Modifier.width(180.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.85f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, contentColor.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Продовжити поїздку?",
                color = contentColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                "Скасування через $timeLeft сек",
                color = secondaryColor,
                fontSize = 9.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TextButton(
                    onClick = onDeny,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Ні", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Так", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ClassicLayout(
    settings: AppSettings,
    currentTime: Date,
    speed: Float,
    tripStartTime: Long,
    tripDistance: Double,
    isTripActive: Boolean,
    contentColor: Color,
    secondaryColor: Color,
    onHotspotToggle: () -> Unit,
    onPowerOff: () -> Unit,
    onSettingsChanged: (AppSettings) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        NotificationIconsRow()
        TimeRow(settings, currentTime, contentColor)
        Spacer(modifier = Modifier.height(16.dp))
        DateText(settings, currentTime, secondaryColor)

        if (settings.showSpeed) {
            Spacer(modifier = Modifier.height(8.dp))
            SpeedRow(settings, speed, contentColor)
            
            // Trip Stats
            if (settings.showTripTime || settings.showTripDistance) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (settings.showTripTime && isTripActive) {
                        val timeStr = if (tripStartTime > 0) {
                            val durationMs = System.currentTimeMillis() - tripStartTime
                            val hours = (durationMs / (1000 * 60 * 60))
                            val minutes = (durationMs / (1000 * 60)) % 60
                            val seconds = (durationMs / 1000) % 60
                            if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
                            else "%02d:%02d".format(minutes, seconds)
                        } else {
                            "00:00"
                        }
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.History, contentDescription = null, tint = contentColor, modifier = Modifier.size(settings.speedSizeSp.dp * 0.8f))
                            Spacer(Modifier.width(4.dp))
                            Text(text = timeStr, color = contentColor, fontSize = settings.speedSizeSp.sp, fontWeight = FontWeight.Black)
                        }
                    }
                    
                    if (settings.showTripTime && settings.showTripDistance && isTripActive) {
                        Spacer(Modifier.width(20.dp))
                    }
                    
                    if (settings.showTripDistance && isTripActive) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Route, contentDescription = null, tint = contentColor, modifier = Modifier.size(settings.speedSizeSp.dp * 0.8f))
                            Spacer(Modifier.width(4.dp))
                            Text(text = "${"%.1f".format(tripDistance)} km", color = contentColor, fontSize = settings.speedSizeSp.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        ControlButtonsRow(settings, contentColor, onHotspotToggle, onPowerOff, onSettingsChanged)
        
        AnimatedVisibility(
            visible = !settings.isAutoBrightness,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                Spacer(modifier = Modifier.height(16.dp))
                BrightnessSliderOnly(settings, contentColor, onSettingsChanged)
            }
        }
    }
}

@Composable
fun SpeedFocusLayout(
    settings: AppSettings,
    currentTime: Date,
    speed: Float,
    tripStartTime: Long,
    tripDistance: Double,
    isTripActive: Boolean,
    contentColor: Color,
    secondaryColor: Color,
    onHotspotToggle: () -> Unit,
    onPowerOff: () -> Unit,
    onSettingsChanged: (AppSettings) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        Text(
            text = timeFormat.format(currentTime),
            color = contentColor,
            fontSize = (settings.clockSizeSp * 0.6).sp,
            fontWeight = FontWeight.Bold,
            fontFamily = getFontFamily(settings.clockFont, settings.customClockFontPath)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        if (settings.showSpeed) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${speed.toInt()}",
                    color = Color(0xFF00E676),
                    fontSize = (settings.speedSizeSp * 2.5).sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = getFontFamily(settings.clockFont, settings.customClockFontPath)
                )
                Text(
                    text = " km/h",
                    color = secondaryColor,
                    fontSize = (settings.speedSizeSp * 0.8).sp,
                    modifier = Modifier.padding(bottom = (settings.speedSizeSp * 0.4).dp)
                )
            }

            // Trip Stats
            if (settings.showTripTime || settings.showTripDistance) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (settings.showTripTime && isTripActive) {
                        val timeStr = if (tripStartTime > 0) {
                            val durationMs = System.currentTimeMillis() - tripStartTime
                            val hours = (durationMs / (1000 * 60 * 60))
                            val minutes = (durationMs / (1000 * 60)) % 60
                            val seconds = (durationMs / 1000) % 60
                            if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
                            else "%02d:%02d".format(minutes, seconds)
                        } else {
                            "00:00"
                        }
                        
                        Text(text = timeStr, color = secondaryColor, fontSize = settings.speedSizeSp.sp, fontWeight = FontWeight.Bold)
                    }
                    
                    if (settings.showTripTime && settings.showTripDistance && isTripActive) {
                        Text(text = "  •  ", color = secondaryColor, fontSize = settings.speedSizeSp.sp)
                    }
                    
                    if (settings.showTripDistance && isTripActive) {
                        Text(text = "${"%.1f".format(tripDistance)} km", color = secondaryColor, fontSize = settings.speedSizeSp.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        ControlButtonsRow(settings, contentColor, onHotspotToggle, onPowerOff, onSettingsChanged)

        AnimatedVisibility(
            visible = !settings.isAutoBrightness,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                Spacer(modifier = Modifier.height(16.dp))
                BrightnessSliderOnly(settings, contentColor, onSettingsChanged)
            }
        }
    }
}

@Composable
fun BigDigitalLayout(
    settings: AppSettings,
    currentTime: Date,
    speed: Float,
    contentColor: Color,
    onHotspotToggle: () -> Unit,
    onPowerOff: () -> Unit,
    onSettingsChanged: (AppSettings) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val hourStr = SimpleDateFormat("HH", Locale.getDefault()).format(currentTime)
        val minStr = SimpleDateFormat("mm", Locale.getDefault()).format(currentTime)
        
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = hourStr,
                color = if (settings.isOnePlusStyle && hourStr.startsWith("1")) Color.Red else contentColor,
                fontSize = (settings.clockSizeSp * 1.5).sp,
                fontWeight = FontWeight.Black,
                fontFamily = getFontFamily(settings.clockFont, settings.customClockFontPath),
                lineHeight = (settings.clockSizeSp * 1.2).sp
            )
            Text(
                text = minStr,
                color = if (settings.isOnePlusStyle && minStr.startsWith("1")) Color.Red else contentColor,
                fontSize = (settings.clockSizeSp * 1.5).sp,
                fontWeight = FontWeight.Black,
                fontFamily = getFontFamily(settings.clockFont, settings.customClockFontPath),
                lineHeight = (settings.clockSizeSp * 1.2).sp
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (settings.showSpeed) {
            Text("${speed.toInt()} km/h", color = Color(0xFF00E676), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
        }

        ControlButtonsRow(settings, contentColor, onHotspotToggle, onPowerOff, onSettingsChanged)

        AnimatedVisibility(
            visible = !settings.isAutoBrightness,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                Spacer(modifier = Modifier.height(16.dp))
                BrightnessSliderOnly(settings, contentColor, onSettingsChanged)
            }
        }
    }
}

@Composable
fun MinimalistLayout(
    settings: AppSettings,
    batteryLevel: Int,
    currentTime: Date,
    speed: Float,
    contentColor: Color,
    secondaryColor: Color,
    onHotspotToggle: () -> Unit,
    onPowerOff: () -> Unit,
    onSettingsChanged: (AppSettings) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().padding(48.dp)) {
        Column(
            modifier = Modifier.align(Alignment.BottomStart),
            horizontalAlignment = Alignment.Start,
        ) {
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            Text(
                text = timeFormat.format(currentTime),
                color = contentColor,
                fontSize = settings.clockSizeSp.sp,
                fontWeight = FontWeight.Light,
                fontFamily = getFontFamily(settings.clockFont, settings.customClockFontPath)
            )
            
            if (settings.showSpeed) {
                Text(
                    text = "${speed.toInt()} km/h",
                    color = secondaryColor,
                    fontSize = settings.speedSizeSp.sp,
                    fontWeight = FontWeight.Normal
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(if (batteryLevel > 20) Color.Green else Color.Red, shape = androidx.compose.foundation.shape.CircleShape)
            )
        }

        Box(modifier = Modifier.align(Alignment.BottomEnd)) {
            Column(horizontalAlignment = Alignment.End) {
                ControlButtonsRow(settings, contentColor.copy(alpha = 0.5f), onHotspotToggle, onPowerOff, onSettingsChanged)
                
                AnimatedVisibility(
                    visible = !settings.isAutoBrightness,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))
                        BrightnessSliderOnly(settings, contentColor.copy(alpha = 0.5f), onSettingsChanged)
                    }
                }
            }
        }
    }
}

@Composable
fun ControlButtonsRow(
    settings: AppSettings,
    contentColor: Color,
    onHotspotToggle: () -> Unit,
    onPowerOff: () -> Unit,
    onSettingsChanged: (AppSettings) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Auto-Brightness Toggle
        IconButton(
            onClick = {
                onSettingsChanged(settings.copy(isAutoBrightness = !settings.isAutoBrightness))
            },
        ) {
            Icon(
                imageVector = Icons.Default.BrightnessAuto,
                contentDescription = "Toggle Auto Brightness",
                tint = if (settings.isAutoBrightness) Color.Green else contentColor,
                modifier = Modifier.size(settings.batterySizeSp.dp * 1.5f),
            )
        }

        Spacer(modifier = Modifier.width(40.dp))
        
        // Hotspot Toggle
        IconButton(onClick = onHotspotToggle) {
            Icon(
                imageVector = Icons.Default.WifiTethering,
                contentDescription = "Hotspot",
                tint = contentColor,
                modifier = Modifier.size(settings.batterySizeSp.dp * 1.5f),
            )
        }

        Spacer(modifier = Modifier.width(40.dp))

        // Power Off
        IconButton(onClick = onPowerOff) {
            Icon(
                imageVector = Icons.Default.PowerSettingsNew,
                contentDescription = "Power Off",
                tint = Color.Red,
                modifier = Modifier.size(settings.batterySizeSp.dp * 1.5f),
            )
        }
    }
}

@Composable
fun BrightnessSliderOnly(
    settings: AppSettings,
    contentColor: Color,
    onSettingsChanged: (AppSettings) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(0.8f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Slider(
            value = settings.brightness,
            onValueChange = { onSettingsChanged(settings.copy(brightness = it)) },
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            colors = SliderDefaults.colors(
                thumbColor = contentColor,
                activeTrackColor = contentColor,
            ),
        )
        Icon(
            imageVector = Icons.Default.BrightnessLow,
            contentDescription = "Brightness",
            tint = contentColor,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
fun NotificationIconsRow() {
    val context = LocalContext.current
    val activeNotifications = NotificationService.notificationList
    if (activeNotifications.isNotEmpty()) {
        LazyRow(
            modifier = Modifier.padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(activeNotifications.distinctBy { it.packageName }) { sbn ->
                val icon = try {
                    context.packageManager.getApplicationIcon(sbn.packageName).toBitmap().asImageBitmap()
                } catch (_: Exception) {
                    null
                }
                icon?.let {
                    androidx.compose.foundation.Image(
                        bitmap = it,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun TimeRow(settings: AppSettings, currentTime: Date, contentColor: Color) {
    val hourStr = SimpleDateFormat("HH", Locale.getDefault()).format(currentTime)
    val minuteStr = SimpleDateFormat("mm", Locale.getDefault()).format(currentTime)
    
    Row(verticalAlignment = Alignment.CenterVertically) {
        hourStr.forEachIndexed { index, char ->
            val color = if (settings.isOnePlusStyle && (index == 0) && (char == '1')) Color.Red else contentColor
            Text(
                text = char.toString(),
                style = TextStyle(
                    color = color,
                    fontSize = settings.clockSizeSp.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = getFontFamily(settings.clockFont, settings.customClockFontPath),
                ),
            )
        }
        
        Text(
            text = ":",
            style = TextStyle(
                color = contentColor,
                fontSize = settings.clockSizeSp.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        
        minuteStr.forEachIndexed { index, char ->
            val color = if (settings.isOnePlusStyle && (index == 0) && (char == '1')) Color.Red else contentColor
            Text(
                text = char.toString(),
                style = TextStyle(
                    color = color,
                    fontSize = settings.clockSizeSp.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = getFontFamily(settings.clockFont, settings.customClockFontPath),
                ),
            )
        }
    }
}

@Composable
fun DateText(settings: AppSettings, currentTime: Date, secondaryColor: Color) {
    val dateFormat = SimpleDateFormat("EEEE, d MMMM", Locale.forLanguageTag("uk-UA"))
    Text(
        text = dateFormat.format(currentTime),
        style = TextStyle(
            color = secondaryColor,
            fontSize = settings.dateSizeSp.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = getFontFamily(settings.dateFont, settings.customDateFontPath),
        ),
    )
}

@Composable
fun SpeedRow(settings: AppSettings, speed: Float, contentColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.Speed,
            contentDescription = "Speed",
            tint = Color(0xFF00E676), // Bright Green
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "${speed.toInt()} km/h",
            color = contentColor,
            fontSize = settings.speedSizeSp.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = getFontFamily(settings.clockFont, settings.customClockFontPath)
        )
    }
}

private fun getWeatherIcon(code: Int): ImageVector {
    return when (code) {
        0 -> Icons.Default.WbSunny // Clear sky
        1, 2, 3 -> Icons.Default.WbCloudy // Mainly clear, partly cloudy, and overcast
        45, 48 -> Icons.Default.WbCloudy // Fog
        51, 53, 55, 61, 63, 65, 80, 81, 82 -> Icons.Default.WaterDrop // Rain
        71, 73, 75, 77, 85, 86 -> Icons.Default.WbCloudy // Snow (using cloudy as simple)
        95, 96 -> Icons.Default.Thunderstorm // Thunderstorm
        else -> Icons.Default.WbCloudy
    }
}

private fun getWeatherColor(code: Int): Color {
    return when (code) {
        0 -> Color(0xFFFFD600) // Bright Yellow for Sun
        1, 2, 3 -> Color.Gray // Gray for clouds
        45, 48 -> Color.LightGray // Light Gray for fog
        51, 53, 55, 61, 63, 65, 80, 81, 82 -> Color(0xFF29B6F6) // Blue for Rain
        71, 73, 75, 77, 85, 86 -> Color.White // White for Snow
        95, 96 -> Color(0xFFFFAB00) // Amber for Thunderstorm
        else -> Color.Gray
    }
}
