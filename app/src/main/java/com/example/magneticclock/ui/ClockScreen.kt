package com.example.magneticclock.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.magneticclock.NotificationService
import com.example.magneticclock.data.AppSettings
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
    bluetoothConnected: Boolean,
    connectedDeviceName: String,
    onSettingsChanged: (AppSettings) -> Unit,
    onSettingsClick: () -> Unit,
    onHotspotToggle: () -> Unit,
    onDoubleTap: () -> Unit,
    onSwipeDown: () -> Unit,
    onMockMove: () -> Unit,
    onStartTrip: () -> Unit,
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
                offset = androidx.compose.ui.unit.IntOffset(x = random.nextInt(7) - 3, y = random.nextInt(7) - 3)
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
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    if (dragAmount.y > 50) {
                        onSwipeDown()
                        change.consume()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // Top Start: Weather
        if (settings.showWeather && weather != null) {
            Row(modifier = Modifier.align(Alignment.TopStart).padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = getWeatherIcon(weather.weatherCode), contentDescription = null, tint = getWeatherColor(weather.weatherCode), modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "${weather.temperature.toInt()}°C", color = contentColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Top Center: Bluetooth & Magnetic Field
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 34.dp, start = 24.dp, end = 24.dp), 
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (settings.showConnectedDeviceName) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = if (bluetoothConnected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth, contentDescription = null, tint = if (bluetoothConnected) Color.Green else Color.Gray, modifier = Modifier.size(20.dp))
                    if (bluetoothConnected && connectedDeviceName.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = connectedDeviceName, color = contentColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (settings.showMagneticField) {
                Text(text = "${magnitude.toInt()} µT", color = secondaryColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            }
        }

        // Top End: Battery
        val batteryColor = if (batteryLevel > 20) Color.Green else Color.Red
        Row(modifier = Modifier.align(Alignment.TopEnd).padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "$batteryLevel%", color = batteryColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(8.dp))
            Icon(imageVector = Icons.Default.BatteryFull, contentDescription = null, tint = batteryColor, modifier = Modifier.size(24.dp))
        }

        // Bottom Start: Phone Temp
        if (phoneTemp > 0) {
            Box(modifier = Modifier.align(Alignment.BottomStart).padding(24.dp)) {
                PhoneTempInfo(phoneTemp)
            }
        }

        // Main Layouts
        when (settings.layoutIndex) {
            1 -> SpeedFocusLayout(settings, currentTime, speed, tripStartTime, tripDistance, isTripActive, contentColor, secondaryColor, onHotspotToggle, onMockMove, onStartTrip, onPowerOff, onSettingsChanged)
            2 -> BigDigitalLayout(settings, currentTime, speed, contentColor, onHotspotToggle, onMockMove, onPowerOff, onSettingsChanged)
            3 -> MinimalistLayout(settings, currentTime, speed, contentColor, secondaryColor, onHotspotToggle, onMockMove, onPowerOff, onSettingsChanged)
            else -> ClassicLayout(settings, currentTime, speed, tripStartTime, tripDistance, isTripActive, contentColor, secondaryColor, onHotspotToggle, onMockMove, onPowerOff, onSettingsChanged)
        }
    }
}

@Composable
fun PhoneTempInfo(temp: Float) {
    val tempColor = if (temp < 40f) Color(0xFF00E676) else Color.Red
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Thermostat, contentDescription = null, tint = tempColor, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(4.dp))
        Text("${temp.toInt()}°C", color = tempColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ClassicLayout(settings: AppSettings, currentTime: Date, speed: Float, tripStartTime: Long, tripDistance: Double, isTripActive: Boolean, contentColor: Color, secondaryColor: Color, onHotspotToggle: () -> Unit, onMockMove: () -> Unit, onPowerOff: () -> Unit, onSettingsChanged: (AppSettings) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (settings.showUnreadNotificationIcons) NotificationIconsRow()
        TimeRow(settings, currentTime, contentColor)
        DateText(secondaryColor, currentTime)

        if (settings.showSpeed) {
            Spacer(Modifier.height(16.dp))
            SpeedRow(settings, speed, contentColor)
            if (isTripActive) TripStats(tripStartTime, tripDistance, contentColor)
        }

        Spacer(Modifier.height(32.dp))
        ControlButtonsRow(settings, contentColor, onHotspotToggle, onMockMove, onPowerOff, onSettingsChanged)
        
        AnimatedVisibility(visible = !settings.isAutoBrightness) {
            Column {
                Spacer(Modifier.height(16.dp))
                BrightnessSliderOnly(settings, contentColor, onSettingsChanged)
            }
        }
    }
}

@Composable
fun TripStats(startTime: Long, distance: Double, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        val duration = System.currentTimeMillis() - startTime
        val min = (duration / 60000)
        val sec = (duration % 60000) / 1000
        val timeStr = if (startTime > 0) "${"%02d".format(min)}:${"%02d".format(sec)}" else "00:00"
        
        Icon(Icons.Default.History, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(timeStr, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(16.dp))
        Icon(Icons.Default.Route, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text("${"%.1f".format(distance)} км", color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SpeedFocusLayout(settings: AppSettings, currentTime: Date, speed: Float, tripStartTime: Long, tripDistance: Double, isTripActive: Boolean, contentColor: Color, secondaryColor: Color, onHotspotToggle: () -> Unit, onMockMove: () -> Unit, onStartTrip: () -> Unit, onPowerOff: () -> Unit, onSettingsChanged: (AppSettings) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(currentTime), color = contentColor, fontSize = (settings.clockSizeSp * 0.5).sp, fontWeight = FontWeight.Bold, fontFamily = getFontFamily(settings.clockFont, settings.customClockFontPath))
        
        if (settings.showSpeed) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(text = "${speed.toInt()}", color = Color(0xFF00E676), fontSize = (settings.clockSizeSp * 0.8).sp, fontWeight = FontWeight.Black)
                Text(text = " km/h", color = secondaryColor, fontSize = 24.sp, modifier = Modifier.padding(bottom = 16.dp))
            }
            if (isTripActive) TripStats(tripStartTime, tripDistance, secondaryColor)
        }
        
        Spacer(Modifier.height(32.dp))
        ControlButtonsRow(settings, contentColor, onHotspotToggle, onMockMove, onPowerOff, onSettingsChanged)
    }
}

@Composable
fun BigDigitalLayout(settings: AppSettings, currentTime: Date, speed: Float, contentColor: Color, onHotspotToggle: () -> Unit, onMockMove: () -> Unit, onPowerOff: () -> Unit, onSettingsChanged: (AppSettings) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        val hour = SimpleDateFormat("HH", Locale.getDefault()).format(currentTime)
        val min = SimpleDateFormat("mm", Locale.getDefault()).format(currentTime)
        Text(text = hour, color = if (settings.isOnePlusStyle && hour.startsWith("1")) Color.Red else contentColor, fontSize = settings.clockSizeSp.sp, fontWeight = FontWeight.Black, fontFamily = getFontFamily(settings.clockFont, settings.customClockFontPath))
        Text(text = min, color = if (settings.isOnePlusStyle && min.startsWith("1")) Color.Red else contentColor, fontSize = settings.clockSizeSp.sp, fontWeight = FontWeight.Black, fontFamily = getFontFamily(settings.clockFont, settings.customClockFontPath))
        if (settings.showSpeed) Text("${speed.toInt()} km/h", color = Color(0xFF00E676), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        ControlButtonsRow(settings, contentColor, onHotspotToggle, onMockMove, onPowerOff, onSettingsChanged)
    }
}

@Composable
fun MinimalistLayout(settings: AppSettings, currentTime: Date, speed: Float, contentColor: Color, secondaryColor: Color, onHotspotToggle: () -> Unit, onMockMove: () -> Unit, onPowerOff: () -> Unit, onSettingsChanged: (AppSettings) -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp)) {
        Column(Modifier.align(Alignment.BottomStart)) {
            Text(text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(currentTime), color = contentColor, fontSize = (settings.clockSizeSp * 0.7).sp, fontWeight = FontWeight.Light, fontFamily = getFontFamily(settings.clockFont, settings.customClockFontPath))
            if (settings.showSpeed) Text(text = "${speed.toInt()} km/h", color = secondaryColor, fontSize = 24.sp)
        }
        Box(Modifier.align(Alignment.BottomEnd)) {
            ControlButtonsRow(settings, contentColor.copy(alpha = 0.5f), onHotspotToggle, onMockMove, onPowerOff, onSettingsChanged)
        }
    }
}

@Composable
fun ControlButtonsRow(settings: AppSettings, contentColor: Color, onHotspotToggle: () -> Unit, onMockMove: () -> Unit, onPowerOff: () -> Unit, onSettingsChanged: (AppSettings) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onSettingsChanged(settings.copy(isAutoBrightness = !settings.isAutoBrightness)) }) {
            Icon(Icons.Default.BrightnessAuto, contentDescription = null, tint = if (settings.isAutoBrightness) Color.Green else contentColor)
        }
        Spacer(Modifier.width(24.dp))
        IconButton(onClick = onHotspotToggle) { Icon(Icons.Default.WifiTethering, contentDescription = null, tint = contentColor) }
        Spacer(Modifier.width(24.dp))
        // Mock Movement Button
        IconButton(onClick = onMockMove) { 
            Icon(Icons.Default.DirectionsCar, contentDescription = "Mock Move", tint = if (com.example.magneticclock.data.TripManager.isTripActive && com.example.magneticclock.data.TripManager.currentSpeedKmH > 0) Color.Green else contentColor) 
        }
        Spacer(Modifier.width(24.dp))
        IconButton(onClick = onPowerOff) { Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = Color.Red) }
    }
}

@Composable
fun BrightnessSliderOnly(settings: AppSettings, contentColor: Color, onSettingsChanged: (AppSettings) -> Unit) {
    Slider(value = settings.brightness, onValueChange = { onSettingsChanged(settings.copy(brightness = it)) }, modifier = Modifier.width(200.dp), colors = SliderDefaults.colors(thumbColor = contentColor, activeTrackColor = contentColor))
}

@Composable
fun NotificationIconsRow() {
    val context = LocalContext.current
    val activeNotifications = NotificationService.notificationList
    if (activeNotifications.isNotEmpty()) {
        LazyRow(modifier = Modifier.padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(activeNotifications.distinctBy { it.packageName }) { sbn ->
                NotificationIcon(context, sbn.packageName)
            }
        }
    }
}

@Composable
fun NotificationIcon(context: android.content.Context, packageName: String) {
    val icon = remember(packageName) {
        try {
            context.packageManager.getApplicationIcon(packageName).toBitmap().asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
    icon?.let {
        androidx.compose.foundation.Image(bitmap = it, contentDescription = null, modifier = Modifier.size(24.dp))
    }
}

@Composable
fun TimeRow(settings: AppSettings, currentTime: Date, contentColor: Color) {
    val hour = SimpleDateFormat("HH", Locale.getDefault()).format(currentTime)
    val min = SimpleDateFormat("mm", Locale.getDefault()).format(currentTime)
    Row(verticalAlignment = Alignment.CenterVertically) {
        TimePart(hour, settings, contentColor)
        Text(":", color = contentColor, fontSize = settings.clockSizeSp.sp, fontWeight = FontWeight.Bold)
        TimePart(min, settings, contentColor)
    }
}

@Composable
fun TimePart(part: String, settings: AppSettings, contentColor: Color) {
    Row {
        part.forEachIndexed { i, c ->
            val color = if (settings.isOnePlusStyle && i == 0 && c == '1') Color.Red else contentColor
            Text(c.toString(), color = color, fontSize = settings.clockSizeSp.sp, fontWeight = FontWeight.Bold, fontFamily = getFontFamily(settings.clockFont, settings.customClockFontPath))
        }
    }
}

@Composable
fun DateText(color: Color, date: Date) {
    val dateFormat = SimpleDateFormat("EEEE, d MMMM", Locale("uk", "UA"))
    Text(text = dateFormat.format(date), color = color, fontSize = 24.sp, fontWeight = FontWeight.Bold)
}

@Composable
fun SpeedRow(settings: AppSettings, speed: Float, contentColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Speed, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(8.dp))
        Text("${speed.toInt()} km/h", color = contentColor, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

private fun getWeatherIcon(code: Int) = when (code) {
    0 -> Icons.Default.WbSunny
    1, 2, 3, 45, 48 -> Icons.Default.WbCloudy
    else -> Icons.Default.WaterDrop
}

private fun getWeatherColor(code: Int) = when (code) {
    0 -> Color(0xFFFFD600)
    1, 2, 3 -> Color.Gray
    else -> Color(0xFF29B6F6)
}
