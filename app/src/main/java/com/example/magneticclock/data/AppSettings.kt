package com.example.magneticclock.data

data class AppSettings(
    val isMonitoringEnabled: Boolean = true,
    val isDarkMode: Boolean = true,
    val showShadeNotification: Boolean = true,
    
    val activationThreshold: Float = 60f,
    val deactivationThreshold: Float = 50f,
    
    val triggerDelayActivationMs: Long = 1000L,
    val triggerDelayDeactivationMs: Long = 1000L,
    val inCarDeactivationDelayMs: Long = 2000L,
    val settingsReturnDelaySeconds: Float = 5.0f,
    
    val clockSizeSp: Int = 180,
    val notificationIconSizeSp: Int = 44,
    val controlButtonSizeSp: Int = 24,
    val clockFont: String = "Default",
    val dateFont: String = "Default",
    val batteryFont: String = "Default",
    val customFonts: Set<String> = emptySet(),
    
    val showWeather: Boolean = true,
    val showSpeed: Boolean = true,
    val showMagneticField: Boolean = false,
    val showConnectedDeviceName: Boolean = true,
    val showUnreadNotificationIcons: Boolean = false,
    
    val brightness: Float = 0.5f,
    val isAutoBrightness: Boolean = true,
    val bluetoothTriggerDeviceName: String = "Ford Focus 3",
    val includeHavit: Boolean = true,
    
    val layoutIndex: Int = 0,
    val isOnePlusStyle: Boolean = false,
    val showTripTime: Boolean = true,
    val showTripDistance: Boolean = true,
    
    val customClockFontPath: String? = null,
    val customDateFontPath: String? = null,
    val customBatteryFontPath: String? = null,
    
    val activationVibrationIntensity: Int = 0,
    val deactivationVibrationIntensity: Int = 0,
)
