package com.example.magneticclock.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import android.app.ActivityOptions
import android.os.Build
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
    isPowerSaveMode: Boolean,
    onSettingsChanged: (AppSettings) -> Unit,
    onSettingsClick: () -> Unit,
    onDoubleTap: () -> Unit,
    onSwipeDown: () -> Unit,
    onSwipeUp: () -> Unit,
    onMockMove: () -> Unit,
    onStartTrip: () -> Unit,
    onPowerOff: () -> Unit,
    onPowerSaveToggle: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
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
            .offset { offset },
        contentAlignment = Alignment.Center,
    ) {
        // Фоновий шар для жестів (налаштування, голос, свайпи)
        // Він займає весь екран, але значки сповіщень будуть ПОВЕРХ нього
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { onDoubleTap() }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        if (dragAmount.y > 50) {
                            onSwipeDown()
                            change.consume()
                        } else if (dragAmount.y < -50) {
                            onSwipeUp()
                            change.consume()
                        }
                    }
                }
        )

        // Top Start: Weather
        if (settings.showWeather && weather != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(24.dp)
                    .clickable {
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                data = android.net.Uri.parse("https://www.google.com/search?q=weather")
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    }, 
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                PhoneTempInfo(phoneTemp, isPowerSaveMode, onPowerSaveToggle)
            }
        }

        // Main Layouts
        when (settings.layoutIndex) {
            1 -> SpeedFocusLayout(settings, currentTime, speed, tripStartTime, tripDistance, isTripActive, contentColor, secondaryColor, onMockMove, onStartTrip, onPowerOff, onSettingsClick, onSettingsChanged)
            2 -> BigDigitalLayout(settings, currentTime, speed, contentColor, onMockMove, onPowerOff, onSettingsClick, onSettingsChanged)
            3 -> MinimalistLayout(settings, currentTime, speed, contentColor, secondaryColor, onMockMove, onPowerOff, onSettingsClick, onSettingsChanged)
            else -> ClassicLayout(settings, currentTime, speed, tripStartTime, tripDistance, isTripActive, contentColor, secondaryColor, onMockMove, onPowerOff, onSettingsClick, onSettingsChanged)
        }
    }
}

@Composable
fun PhoneTempInfo(temp: Float, isPowerSaveMode: Boolean, onPowerSaveToggle: () -> Unit) {
    val isDangerous = temp >= 40f
    val tempColor = if (isDangerous) Color.Red else Color(0xFF00E676)
    
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Thermostat, contentDescription = null, tint = tempColor, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(4.dp))
        Text("${temp.toInt()}°C", color = tempColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        
        if (isDangerous) {
            Spacer(Modifier.width(12.dp))
            IconButton(
                onClick = onPowerSaveToggle,
                modifier = Modifier
                    .size(32.dp)
                    .background(if (isPowerSaveMode) Color.Green else Color.Red, CircleShape)
            ) {
                Icon(
                    imageVector = if (isPowerSaveMode) Icons.Default.BatteryChargingFull else Icons.Default.BatterySaver,
                    contentDescription = "Енергозбереження",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun ClassicLayout(settings: AppSettings, currentTime: Date, speed: Float, tripStartTime: Long, tripDistance: Double, isTripActive: Boolean, contentColor: Color, secondaryColor: Color, onMockMove: () -> Unit, onPowerOff: () -> Unit, onSettingsClick: () -> Unit, onSettingsChanged: (AppSettings) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (settings.showUnreadNotificationIcons) NotificationIconsRow(settings)
        TimeRow(settings, currentTime, contentColor)
        DateText(secondaryColor, currentTime)

        if (settings.showSpeed) {
            Spacer(Modifier.height(16.dp))
            SpeedRow(settings, speed, contentColor)
            if (isTripActive) TripStats(tripStartTime, tripDistance, contentColor)
        }

        Spacer(Modifier.height(32.dp))
        ControlButtonsRow(settings, contentColor, onMockMove, onPowerOff, onSettingsClick, onSettingsChanged)
        
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
fun SpeedFocusLayout(settings: AppSettings, currentTime: Date, speed: Float, tripStartTime: Long, tripDistance: Double, isTripActive: Boolean, contentColor: Color, secondaryColor: Color, onMockMove: () -> Unit, onStartTrip: () -> Unit, onPowerOff: () -> Unit, onSettingsClick: () -> Unit, onSettingsChanged: (AppSettings) -> Unit) {
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
        ControlButtonsRow(settings, contentColor, onMockMove, onPowerOff, onSettingsClick, onSettingsChanged)
    }
}

@Composable
fun BigDigitalLayout(settings: AppSettings, currentTime: Date, speed: Float, contentColor: Color, onMockMove: () -> Unit, onPowerOff: () -> Unit, onSettingsClick: () -> Unit, onSettingsChanged: (AppSettings) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        val hour = SimpleDateFormat("HH", Locale.getDefault()).format(currentTime)
        val min = SimpleDateFormat("mm", Locale.getDefault()).format(currentTime)
        Text(text = hour, color = if (settings.isOnePlusStyle && hour.startsWith("1")) Color.Red else contentColor, fontSize = settings.clockSizeSp.sp, fontWeight = FontWeight.Black, fontFamily = getFontFamily(settings.clockFont, settings.customClockFontPath))
        Text(text = min, color = if (settings.isOnePlusStyle && min.startsWith("1")) Color.Red else contentColor, fontSize = settings.clockSizeSp.sp, fontWeight = FontWeight.Black, fontFamily = getFontFamily(settings.clockFont, settings.customClockFontPath))
        if (settings.showSpeed) Text("${speed.toInt()} km/h", color = Color(0xFF00E676), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        ControlButtonsRow(settings, contentColor, onMockMove, onPowerOff, onSettingsClick, onSettingsChanged)
    }
}

@Composable
fun MinimalistLayout(settings: AppSettings, currentTime: Date, speed: Float, contentColor: Color, secondaryColor: Color, onMockMove: () -> Unit, onPowerOff: () -> Unit, onSettingsClick: () -> Unit, onSettingsChanged: (AppSettings) -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp)) {
        Column(Modifier.align(Alignment.BottomStart)) {
            Text(text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(currentTime), color = contentColor, fontSize = (settings.clockSizeSp * 0.7).sp, fontWeight = FontWeight.Light, fontFamily = getFontFamily(settings.clockFont, settings.customClockFontPath))
            if (settings.showSpeed) Text(text = "${speed.toInt()} km/h", color = secondaryColor, fontSize = 24.sp)
        }
        Box(Modifier.align(Alignment.BottomEnd)) {
            ControlButtonsRow(settings, contentColor.copy(alpha = 0.5f), onMockMove, onPowerOff, onSettingsClick, onSettingsChanged)
        }
    }
}

@Composable
fun ControlButtonsRow(settings: AppSettings, contentColor: Color, onMockMove: () -> Unit, onPowerOff: () -> Unit, onSettingsClick: () -> Unit, onSettingsChanged: (AppSettings) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onSettingsChanged(settings.copy(isAutoBrightness = !settings.isAutoBrightness)) }) {
            Icon(Icons.Default.BrightnessAuto, contentDescription = null, tint = if (settings.isAutoBrightness) Color.Green else contentColor, modifier = Modifier.size(settings.controlButtonSizeSp.dp))
        }
        Spacer(Modifier.width(24.dp))
        // Settings Gear Button
        IconButton(onClick = onSettingsClick) {
            Icon(Icons.Default.Settings, contentDescription = "Налаштування", tint = contentColor, modifier = Modifier.size(settings.controlButtonSizeSp.dp))
        }
        Spacer(Modifier.width(24.dp))
        // Mock Movement Button
        IconButton(onClick = onMockMove) { 
            Icon(Icons.Default.DirectionsCar, contentDescription = "Mock Move", tint = if (com.example.magneticclock.data.TripManager.isTripActive && com.example.magneticclock.data.TripManager.currentSpeedKmH > 0) Color.Green else contentColor, modifier = Modifier.size(settings.controlButtonSizeSp.dp)) 
        }
        Spacer(Modifier.width(24.dp))
        IconButton(onClick = onPowerOff) { Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = Color.Red, modifier = Modifier.size(settings.controlButtonSizeSp.dp)) }
    }
}

@Composable
fun BrightnessSliderOnly(settings: AppSettings, contentColor: Color, onSettingsChanged: (AppSettings) -> Unit) {
    Slider(value = settings.brightness, onValueChange = { onSettingsChanged(settings.copy(brightness = it)) }, modifier = Modifier.width(200.dp), colors = SliderDefaults.colors(thumbColor = contentColor, activeTrackColor = contentColor))
}

@Composable
fun NotificationIconsRow(settings: AppSettings) {
    val context = LocalContext.current
    val activeNotifications = NotificationService.notificationList
    
    // Фільтруємо власне сповіщення програми та показуємо всі інші (включаючи беззвучні)
    val filteredNotifications = remember(activeNotifications.size) {
        activeNotifications.filter { it.packageName != context.packageName }
    }

    if (filteredNotifications.isNotEmpty()) {
        LazyRow(modifier = Modifier.padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(filteredNotifications) { sbn ->
                NotificationIcon(context, sbn, settings.notificationIconSizeSp)
            }
        }
    }
}

@Composable
fun NotificationIcon(context: android.content.Context, sbn: android.service.notification.StatusBarNotification, iconSize: Int) {
    val packageName = sbn.packageName
    val iconId = sbn.id
    
    val icon = remember(packageName, iconId, sbn.postTime) {
        try {
            // 1. Спробуємо отримати "Великий значок" (це зазвичай аватар або іконка події)
            val largeIcon = sbn.notification.getLargeIcon()?.loadDrawable(context)
            if (largeIcon != null) {
                return@remember largeIcon.toBitmap().asImageBitmap()
            }
            
            // 2. Якщо великого немає, спробуємо "Маленький значок" (той, що в статус-барі)
            val smallIcon = sbn.notification.smallIcon?.loadDrawable(context)
            if (smallIcon != null) {
                return@remember smallIcon.toBitmap().asImageBitmap()
            }
            
            // 3. Крайній випадок - іконка самої програми
            context.packageManager.getApplicationIcon(packageName).toBitmap().asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
    
    icon?.let {
        androidx.compose.foundation.Image(
            bitmap = it, 
            contentDescription = null, 
            modifier = Modifier
                .size(iconSize.dp) 
                .clickable {
                    val packageName = sbn.packageName
                    val contentIntent = sbn.notification.contentIntent
                    android.util.Log.d("MagneticClock", "Клік по іконці: $packageName")
                    
                    try {
                        val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            ActivityOptions.makeBasic()
                                .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                                .toBundle()
                        } else null

                        if (contentIntent != null) {
                            // 1. Відправляємо наказ відкрити чат/програму через IntentSender
                            (context as? android.app.Activity)?.startIntentSender(
                                contentIntent.intentSender,
                                null,
                                0, 0, 0,
                                options
                            )
                            
                            // 2. МИТТЄВО згортаємо годинник, щоб побачити результат
                            (context as? android.app.Activity)?.moveTaskToBack(true)
                            
                            android.util.Log.i("MagneticClock", "IntentSender відправлено, годинник згорнуто")
                        } else {
                            // Якщо немає прямого посилання (contentIntent), просто відкриваємо програму
                            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                            launchIntent?.let { intent -> 
                                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent, options) 
                                (context as? android.app.Activity)?.moveTaskToBack(true)
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MagneticClock", "Критична помилка при відкритті: ${e.message}")
                    }
                }
        )
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
