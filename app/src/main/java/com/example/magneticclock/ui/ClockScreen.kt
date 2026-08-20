package com.example.magneticclock.ui

import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.example.magneticclock.R
import com.example.magneticclock.data.AppSettings
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay

val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

fun getFontFamily(fontName: String, customPath: String?): FontFamily {
    if (!customPath.isNullOrEmpty()) {
        try {
            val file = File(customPath)
            if (file.exists()) {
                return FontFamily(Typeface.createFromFile(file))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    if (fontName == "Default") return FontFamily.Default
    
    val font = GoogleFont(fontName)
    return FontFamily(
        Font(googleFont = font, fontProvider = provider)
    )
}

val availableFonts = listOf("Default", "Roboto", "Montserrat", "Playfair Display")

@Composable
fun ClockScreen(
    settings: AppSettings,
    batteryLevel: Int,
    onHotspotToggle: () -> Unit,
    onClose: () -> Unit
) {
    var currentTime by remember { mutableStateOf(Calendar.getInstance().time) }
    
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = Calendar.getInstance().time
            delay(1000)
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
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Time
            val hourStr = SimpleDateFormat("HH", Locale.getDefault()).format(currentTime)
            val minuteStr = SimpleDateFormat("mm", Locale.getDefault()).format(currentTime)
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Hour
                hourStr.forEachIndexed { index, char ->
                    val color = if (settings.isOnePlusStyle && index == 0 && char == '1') Color.Red else contentColor
                    Text(
                        text = char.toString(),
                        style = TextStyle(
                            color = color,
                            fontSize = settings.clockSizeSp.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = getFontFamily(settings.clockFont, settings.customClockFontPath)
                        )
                    )
                }
                
                Text(
                    text = ":",
                    style = TextStyle(
                        color = contentColor,
                        fontSize = settings.clockSizeSp.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                
                // Minute
                minuteStr.forEachIndexed { index, char ->
                    val color = if (settings.isOnePlusStyle && index == 0 && char == '1') Color.Red else contentColor
                    Text(
                        text = char.toString(),
                        style = TextStyle(
                            color = color,
                            fontSize = settings.clockSizeSp.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = getFontFamily(settings.clockFont, settings.customClockFontPath)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Date
            val dateFormat = SimpleDateFormat("EEEE, d MMMM", Locale("uk"))
            Text(
                text = dateFormat.format(currentTime),
                style = TextStyle(
                    color = secondaryColor,
                    fontSize = settings.dateSizeSp.sp,
                    fontFamily = getFontFamily(settings.dateFont, settings.customDateFontPath)
                )
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Battery and Hotspot
            Row(
                verticalAlignment = Alignment.CenterVertically
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
                        fontFamily = getFontFamily(settings.batteryFont, settings.customBatteryFontPath)
                    )
                )
                
                Spacer(modifier = Modifier.width(24.dp))
                
                Icon(
                    imageVector = Icons.Default.WifiTethering,
                    contentDescription = "Hotspot",
                    tint = contentColor,
                    modifier = Modifier
                        .size(settings.batterySizeSp.dp * 1.5f)
                        .clickable { onHotspotToggle() }
                )
            }
        }
    }
}
