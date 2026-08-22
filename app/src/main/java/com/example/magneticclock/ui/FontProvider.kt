package com.example.magneticclock.ui

import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.example.magneticclock.R
import java.io.File

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
    
    return try {
        val font = GoogleFont(fontName)
        FontFamily(
            Font(googleFont = font, fontProvider = provider)
        )
    } catch (e: Exception) {
        FontFamily.Default
    }
}

val availableFonts = listOf("Default", "Roboto", "Montserrat", "Playfair Display")
