package com.example.magneticclock.data

data class AppSettings(
    val brightness: Float = 0.5f,
    val isAutoBrightness: Boolean = true,
    val activationThreshold: Float = 60f,
    val deactivationThreshold: Float = 50f,
    val triggerDurationMs: Long = 1000L,
    val clockFont: String = "Default",
    val dateFont: String = "Default",
    val batteryFont: String = "Default",
    val clockSizeSp: Int = 64,
    val dateSizeSp: Int = 24,
    val batterySizeSp: Int = 18,
    val isOnePlusStyle: Boolean = false,
    val isDarkMode: Boolean = true,
    val activationVibrationIntensity: Int = 0,
    val deactivationVibrationIntensity: Int = 0,
    val customClockFontPath: String? = null,
    val customDateFontPath: String? = null,
    val customBatteryFontPath: String? = null
)
