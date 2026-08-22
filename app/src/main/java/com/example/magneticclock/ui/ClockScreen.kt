package com.example.magneticclock.ui

import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
    onSettingsChanged: (AppSettings) -> Unit,
    onHotspotToggle: () -> Unit,
    onPowerOff: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var currentTime by remember { mutableStateOf(Calendar.getInstance().time) }
    
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = Calendar.getInstance().time
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
            .clickable { onClose() },
        contentAlignment = Alignment.Center,
    ) {
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
                    Text(
                        text = "Auto-Brightness Active",
                        color = secondaryColor,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(end = 8.dp),
                    )
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
