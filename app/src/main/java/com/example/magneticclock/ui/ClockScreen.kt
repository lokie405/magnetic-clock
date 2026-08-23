package com.example.magneticclock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.WifiTethering
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.core.graphics.drawable.toBitmap
import com.example.magneticclock.NotificationService
import com.example.magneticclock.R
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
    onSettingsChanged: (AppSettings) -> Unit,
    onHotspotToggle: () -> Unit,
    onDoubleTap: () -> Unit,
    onPowerOff: () -> Unit,
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
            
            val currentMin = now.get(Calendar.MINUTE)
            if (currentMin != lastMinute) {
                // Burn-in protection: shift by -3 to 3 pixels only once per minute
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
                    onTap = { onClose() },
                    onDoubleTap = { onDoubleTap() }
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (settings.showPhoneTemperature) {
            val tempColor = when {
                phoneTemp < 38f -> Color(0xFF00E676) // Green
                phoneTemp < 45f -> Color(0xFFFFD600) // Yellow
                else -> Color.Red
            }
            
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
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

        if ((settings.showWeather) && (weather != null)) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
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

        if (settings.showMagneticField) {
            Text(
                text = "${"%.1f".format(magnitude)} µT",
                color = secondaryColor,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Notification Icons
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

            // Time
            val hourStr = SimpleDateFormat("HH", Locale.getDefault()).format(currentTime)
            val minuteStr = SimpleDateFormat("mm", Locale.getDefault()).format(currentTime)
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Hour
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
                
                // Minute
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

            Spacer(modifier = Modifier.height(16.dp))

            // Date
            val dateFormat = SimpleDateFormat("EEEE, d MMMM", Locale.forLanguageTag("uk-UA"))
            Text(
                text = dateFormat.format(currentTime),
                style = TextStyle(
                    color = secondaryColor,
                    fontSize = settings.dateSizeSp.sp,
                    fontFamily = getFontFamily(settings.dateFont, settings.customDateFontPath),
                ),
            )

            if (settings.showSpeed) {
                Spacer(modifier = Modifier.height(8.dp))
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

            Spacer(modifier = Modifier.height(32.dp))

            // Battery and Hotspot
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val batteryColor = when {
                    batteryLevel > 60 -> if (settings.isDarkMode) Color.Green else Color(0xFF388E3C)
                    batteryLevel > 20 -> if (settings.isDarkMode) Color.Yellow else Color(0xFFFBC02D)
                    else -> Color.Red
                }

                Text(
                    text = "$batteryLevel%",
                    style = TextStyle(
                        color = batteryColor,
                        fontSize = settings.batterySizeSp.sp,
                        fontFamily = getFontFamily(settings.batteryFont, settings.customBatteryFontPath),
                    ),
                )
                
                Spacer(modifier = Modifier.width(24.dp))
                
                Icon(
                    imageVector = Icons.Default.WifiTethering,
                    contentDescription = "Hotspot",
                    tint = contentColor,
                    modifier = Modifier
                        .size(settings.batterySizeSp.dp * 1.5f)
                        .clickable { onHotspotToggle() },
                )

                Spacer(modifier = Modifier.width(24.dp))

                IconButton(onClick = onPowerOff) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = "Power Off",
                        tint = Color.Red,
                        modifier = Modifier.size(settings.batterySizeSp.dp * 1.5f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Brightness Control
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!settings.isAutoBrightness) {
                    Icon(
                        imageVector = Icons.Default.BrightnessLow,
                        contentDescription = "Brightness",
                        tint = contentColor,
                        modifier = Modifier.size(20.dp),
                    )
                    Slider(
                        value = settings.brightness,
                        onValueChange = { onSettingsChanged(settings.copy(brightness = it)) },
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = contentColor,
                            activeTrackColor = contentColor,
                        ),
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                IconButton(
                    onClick = {
                        val newAuto = !settings.isAutoBrightness
                        onSettingsChanged(settings.copy(isAutoBrightness = newAuto))
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.BrightnessAuto,
                        contentDescription = "Toggle Auto Brightness",
                        tint = if (settings.isAutoBrightness) Color.Green else contentColor,
                    )
                }
            }
        }
    }
}

private fun getWeatherIcon(code: Int): ImageVector {
    return when (code) {
        0 -> Icons.Default.WbSunny // Clear sky
        1, 2, 3 -> Icons.Default.WbCloudy // Mainly clear, partly cloudy, and overcast
        45, 48 -> Icons.Default.WbCloudy // Fog
        51, 53, 55, 61, 63, 65, 80, 81, 82 -> Icons.Default.WaterDrop // Rain
        71, 73, 75, 77, 85, 86 -> Icons.Default.WbCloudy // Snow (using cloudy as simple)
        95, 96, 99 -> Icons.Default.Thunderstorm // Thunderstorm
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
        95, 96, 99 -> Color(0xFFFFAB00) // Amber for Thunderstorm
        else -> Color.Gray
    }
}
