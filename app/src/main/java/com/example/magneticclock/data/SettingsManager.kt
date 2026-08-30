package com.example.magneticclock.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {

    private object Keys {
        val IS_MONITORING_ENABLED = booleanPreferencesKey("is_monitoring_enabled")
        val IS_DARK_MODE = booleanPreferencesKey("is_dark_mode")
        val SHOW_SHADE_NOTIFICATION = booleanPreferencesKey("show_shade_notification")
        
        val ACTIVATION_THRESHOLD = floatPreferencesKey("activation_threshold")
        val DEACTIVATION_THRESHOLD = floatPreferencesKey("deactivation_threshold")
        
        val TRIGGER_DELAY_ACTIVATION = longPreferencesKey("trigger_delay_activation")
        val TRIGGER_DELAY_DEACTIVATION = longPreferencesKey("trigger_delay_deactivation")
        val IN_CAR_DEACTIVATION_DELAY = longPreferencesKey("in_car_deactivation_delay")
        val SETTINGS_RETURN_DELAY = floatPreferencesKey("settings_return_delay")
        
        val CLOCK_SIZE = intPreferencesKey("clock_size")
        val NOTIFICATION_ICON_SIZE = intPreferencesKey("notification_icon_size")
        val CONTROL_BUTTON_SIZE = intPreferencesKey("control_button_size")
        val CLOCK_FONT = stringPreferencesKey("clock_font")
        val DATE_FONT = stringPreferencesKey("date_font")
        val BATTERY_FONT = stringPreferencesKey("battery_font")
        val CUSTOM_FONTS = stringSetPreferencesKey("custom_fonts")
        
        val SHOW_WEATHER = booleanPreferencesKey("show_weather")
        val SHOW_SPEED = booleanPreferencesKey("show_speed")
        val SHOW_MAGNETIC_FIELD = booleanPreferencesKey("show_magnetic_field")
        val SHOW_CONNECTED_DEVICE_NAME = booleanPreferencesKey("show_connected_device_name")
        val SHOW_UNREAD_NOTIFICATION_ICONS = booleanPreferencesKey("show_unread_notification_icons")
        
        val BRIGHTNESS = floatPreferencesKey("brightness")
        val IS_AUTO_BRIGHTNESS = booleanPreferencesKey("is_auto_brightness")
        val BLUETOOTH_TRIGGER_DEVICE_NAME = stringPreferencesKey("bluetooth_trigger_device_name")
        val INCLUDE_HAVIT = booleanPreferencesKey("include_havit")
        
        val LAYOUT_INDEX = intPreferencesKey("layout_index")
        val IS_ONEPLUS_STYLE = booleanPreferencesKey("is_oneplus_style")
        val SHOW_TRIP_TIME = booleanPreferencesKey("show_trip_time")
        val SHOW_TRIP_DISTANCE = booleanPreferencesKey("show_trip_distance")
        
        val CUSTOM_CLOCK_FONT = stringPreferencesKey("custom_clock_font")
        val CUSTOM_DATE_FONT = stringPreferencesKey("custom_date_font")
        val CUSTOM_BATTERY_FONT = stringPreferencesKey("custom_battery_font")
        
        val ACTIVATION_VIBRATION = intPreferencesKey("activation_vibration")
        val DEACTIVATION_VIBRATION = intPreferencesKey("deactivation_vibration")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { preferences ->
        AppSettings(
            isMonitoringEnabled = preferences[Keys.IS_MONITORING_ENABLED] ?: true,
            isDarkMode = preferences[Keys.IS_DARK_MODE] ?: true,
            showShadeNotification = preferences[Keys.SHOW_SHADE_NOTIFICATION] ?: true,
            activationThreshold = preferences[Keys.ACTIVATION_THRESHOLD] ?: 60f,
            deactivationThreshold = preferences[Keys.DEACTIVATION_THRESHOLD] ?: 50f,
            triggerDelayActivationMs = preferences[Keys.TRIGGER_DELAY_ACTIVATION] ?: 1000L,
            triggerDelayDeactivationMs = preferences[Keys.TRIGGER_DELAY_DEACTIVATION] ?: 1000L,
            inCarDeactivationDelayMs = preferences[Keys.IN_CAR_DEACTIVATION_DELAY] ?: 2000L,
            settingsReturnDelaySeconds = preferences[Keys.SETTINGS_RETURN_DELAY] ?: 5.0f,
            clockSizeSp = preferences[Keys.CLOCK_SIZE] ?: 180,
            notificationIconSizeSp = preferences[Keys.NOTIFICATION_ICON_SIZE] ?: 44,
            controlButtonSizeSp = preferences[Keys.CONTROL_BUTTON_SIZE] ?: 24,
            clockFont = preferences[Keys.CLOCK_FONT] ?: "Default",
            dateFont = preferences[Keys.DATE_FONT] ?: "Default",
            batteryFont = preferences[Keys.BATTERY_FONT] ?: "Default",
            customFonts = preferences[Keys.CUSTOM_FONTS] ?: emptySet(),
            showWeather = preferences[Keys.SHOW_WEATHER] ?: true,
            showSpeed = preferences[Keys.SHOW_SPEED] ?: true,
            showMagneticField = preferences[Keys.SHOW_MAGNETIC_FIELD] ?: false,
            showConnectedDeviceName = preferences[Keys.SHOW_CONNECTED_DEVICE_NAME] ?: true,
            showUnreadNotificationIcons = preferences[Keys.SHOW_UNREAD_NOTIFICATION_ICONS] ?: false,
            brightness = preferences[Keys.BRIGHTNESS] ?: 0.5f,
            isAutoBrightness = preferences[Keys.IS_AUTO_BRIGHTNESS] ?: true,
            bluetoothTriggerDeviceName = preferences[Keys.BLUETOOTH_TRIGGER_DEVICE_NAME] ?: "Ford Focus 3",
            includeHavit = preferences[Keys.INCLUDE_HAVIT] ?: true,
            layoutIndex = preferences[Keys.LAYOUT_INDEX] ?: 0,
            isOnePlusStyle = preferences[Keys.IS_ONEPLUS_STYLE] ?: false,
            showTripTime = preferences[Keys.SHOW_TRIP_TIME] ?: true,
            showTripDistance = preferences[Keys.SHOW_TRIP_DISTANCE] ?: true,
            customClockFontPath = preferences[Keys.CUSTOM_CLOCK_FONT],
            customDateFontPath = preferences[Keys.CUSTOM_DATE_FONT],
            customBatteryFontPath = preferences[Keys.CUSTOM_BATTERY_FONT],
            activationVibrationIntensity = preferences[Keys.ACTIVATION_VIBRATION] ?: 0,
            deactivationVibrationIntensity = preferences[Keys.DEACTIVATION_VIBRATION] ?: 0
        )
    }

    suspend fun updateSettings(settings: AppSettings) {
        context.dataStore.edit { preferences ->
            preferences[Keys.IS_MONITORING_ENABLED] = settings.isMonitoringEnabled
            preferences[Keys.IS_DARK_MODE] = settings.isDarkMode
            preferences[Keys.SHOW_SHADE_NOTIFICATION] = settings.showShadeNotification
            preferences[Keys.ACTIVATION_THRESHOLD] = settings.activationThreshold
            preferences[Keys.DEACTIVATION_THRESHOLD] = settings.deactivationThreshold
            preferences[Keys.TRIGGER_DELAY_ACTIVATION] = settings.triggerDelayActivationMs
            preferences[Keys.TRIGGER_DELAY_DEACTIVATION] = settings.triggerDelayDeactivationMs
            preferences[Keys.IN_CAR_DEACTIVATION_DELAY] = settings.inCarDeactivationDelayMs
            preferences[Keys.SETTINGS_RETURN_DELAY] = settings.settingsReturnDelaySeconds
            preferences[Keys.CLOCK_SIZE] = settings.clockSizeSp
            preferences[Keys.NOTIFICATION_ICON_SIZE] = settings.notificationIconSizeSp
            preferences[Keys.CONTROL_BUTTON_SIZE] = settings.controlButtonSizeSp
            preferences[Keys.CLOCK_FONT] = settings.clockFont
            preferences[Keys.DATE_FONT] = settings.dateFont
            preferences[Keys.BATTERY_FONT] = settings.batteryFont
            preferences[Keys.CUSTOM_FONTS] = settings.customFonts
            preferences[Keys.SHOW_WEATHER] = settings.showWeather
            preferences[Keys.SHOW_SPEED] = settings.showSpeed
            preferences[Keys.SHOW_MAGNETIC_FIELD] = settings.showMagneticField
            preferences[Keys.SHOW_CONNECTED_DEVICE_NAME] = settings.showConnectedDeviceName
            preferences[Keys.SHOW_UNREAD_NOTIFICATION_ICONS] = settings.showUnreadNotificationIcons
            preferences[Keys.BRIGHTNESS] = settings.brightness
            preferences[Keys.IS_AUTO_BRIGHTNESS] = settings.isAutoBrightness
            preferences[Keys.BLUETOOTH_TRIGGER_DEVICE_NAME] = settings.bluetoothTriggerDeviceName
            preferences[Keys.INCLUDE_HAVIT] = settings.includeHavit
            preferences[Keys.LAYOUT_INDEX] = settings.layoutIndex
            preferences[Keys.IS_ONEPLUS_STYLE] = settings.isOnePlusStyle
            preferences[Keys.SHOW_TRIP_TIME] = settings.showTripTime
            preferences[Keys.SHOW_TRIP_DISTANCE] = settings.showTripDistance
            
            settings.customClockFontPath?.let { preferences[Keys.CUSTOM_CLOCK_FONT] = it } ?: preferences.remove(Keys.CUSTOM_CLOCK_FONT)
            settings.customDateFontPath?.let { preferences[Keys.CUSTOM_DATE_FONT] = it } ?: preferences.remove(Keys.CUSTOM_DATE_FONT)
            settings.customBatteryFontPath?.let { preferences[Keys.CUSTOM_BATTERY_FONT] = it } ?: preferences.remove(Keys.CUSTOM_BATTERY_FONT)
            
            preferences[Keys.ACTIVATION_VIBRATION] = settings.activationVibrationIntensity
            preferences[Keys.DEACTIVATION_VIBRATION] = settings.deactivationVibrationIntensity
        }
    }
}
