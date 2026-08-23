package com.example.magneticclock.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {

    private object Keys {
        val BRIGHTNESS = floatPreferencesKey("brightness")
        val IS_AUTO_BRIGHTNESS = booleanPreferencesKey("is_auto_brightness")
        val ACTIVATION_THRESHOLD = floatPreferencesKey("activation_threshold")
        val DEACTIVATION_THRESHOLD = floatPreferencesKey("deactivation_threshold")
        val TRIGGER_DELAY_ACTIVATION = longPreferencesKey("trigger_delay_activation")
        val TRIGGER_DELAY_DEACTIVATION = longPreferencesKey("trigger_delay_deactivation")
        val IS_MONITORING_ENABLED = booleanPreferencesKey("is_monitoring_enabled")
        val CLOCK_FONT = stringPreferencesKey("clock_font")
        val DATE_FONT = stringPreferencesKey("date_font")
        val BATTERY_FONT = stringPreferencesKey("battery_font")
        val CLOCK_SIZE = intPreferencesKey("clock_size")
        val DATE_SIZE = intPreferencesKey("date_size")
        val BATTERY_SIZE = intPreferencesKey("battery_size")
        val IS_ONEPLUS_STYLE = booleanPreferencesKey("is_oneplus_style")
        val IS_DARK_MODE = booleanPreferencesKey("is_dark_mode")
        val ACTIVATION_VIBRATION = intPreferencesKey("activation_vibration")
        val DEACTIVATION_VIBRATION = intPreferencesKey("deactivation_vibration")
        val CUSTOM_CLOCK_FONT = stringPreferencesKey("custom_clock_font")
        val CUSTOM_DATE_FONT = stringPreferencesKey("custom_date_font")
        val CUSTOM_BATTERY_FONT = stringPreferencesKey("custom_battery_font")
        val CUSTOM_FONTS = stringSetPreferencesKey("custom_fonts")
        val SHOW_MAGNETIC_FIELD = booleanPreferencesKey("show_magnetic_field")
        val SHOW_WEATHER = booleanPreferencesKey("show_weather")
        val SHOW_SPEED = booleanPreferencesKey("show_speed")
        val SPEED_SIZE = intPreferencesKey("speed_size")
        val SHOW_PHONE_TEMPERATURE = booleanPreferencesKey("show_phone_temperature")
        val USE_PROXIMITY_SENSOR = booleanPreferencesKey("use_proximity_sensor")
        val LAYOUT_INDEX = intPreferencesKey("layout_index")
        val SHOW_TRIP_TIME = booleanPreferencesKey("show_trip_time")
        val SHOW_TRIP_DISTANCE = booleanPreferencesKey("show_trip_distance")
        val TRIP_LOG_DWELL_MINUTES = intPreferencesKey("trip_log_dwell_minutes")
        val SETTINGS_RETURN_DELAY = floatPreferencesKey("settings_return_delay")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { preferences ->
        AppSettings(
            brightness = preferences[Keys.BRIGHTNESS] ?: 0.5f,
            isAutoBrightness = preferences[Keys.IS_AUTO_BRIGHTNESS] ?: true,
            activationThreshold = preferences[Keys.ACTIVATION_THRESHOLD] ?: 60f,
            deactivationThreshold = preferences[Keys.DEACTIVATION_THRESHOLD] ?: 50f,
            triggerDelayActivationMs = preferences[Keys.TRIGGER_DELAY_ACTIVATION] ?: 1000L,
            triggerDelayDeactivationMs = preferences[Keys.TRIGGER_DELAY_DEACTIVATION] ?: 1000L,
            isMonitoringEnabled = preferences[Keys.IS_MONITORING_ENABLED] ?: true,
            clockFont = preferences[Keys.CLOCK_FONT] ?: "Default",
            dateFont = preferences[Keys.DATE_FONT] ?: "Default",
            batteryFont = preferences[Keys.BATTERY_FONT] ?: "Default",
            clockSizeSp = preferences[Keys.CLOCK_SIZE] ?: 64,
            dateSizeSp = preferences[Keys.DATE_SIZE] ?: 24,
            batterySizeSp = preferences[Keys.BATTERY_SIZE] ?: 18,
            isOnePlusStyle = preferences[Keys.IS_ONEPLUS_STYLE] ?: false,
            isDarkMode = preferences[Keys.IS_DARK_MODE] ?: true,
            activationVibrationIntensity = preferences[Keys.ACTIVATION_VIBRATION] ?: 0,
            deactivationVibrationIntensity = preferences[Keys.DEACTIVATION_VIBRATION] ?: 0,
            customClockFontPath = preferences[Keys.CUSTOM_CLOCK_FONT],
            customDateFontPath = preferences[Keys.CUSTOM_DATE_FONT],
            customBatteryFontPath = preferences[Keys.CUSTOM_BATTERY_FONT],
            customFonts = preferences[Keys.CUSTOM_FONTS] ?: emptySet(),
            showMagneticField = preferences[Keys.SHOW_MAGNETIC_FIELD] ?: false,
            showWeather = preferences[Keys.SHOW_WEATHER] ?: false,
            showSpeed = preferences[Keys.SHOW_SPEED] ?: false,
            speedSizeSp = preferences[Keys.SPEED_SIZE] ?: 24,
            showPhoneTemperature = preferences[Keys.SHOW_PHONE_TEMPERATURE] ?: false,
            useProximitySensor = preferences[Keys.USE_PROXIMITY_SENSOR] ?: true,
            layoutIndex = preferences[Keys.LAYOUT_INDEX] ?: 0,
            showTripTime = preferences[Keys.SHOW_TRIP_TIME] ?: false,
            showTripDistance = preferences[Keys.SHOW_TRIP_DISTANCE] ?: false,
            tripLogDwellMinutes = preferences[Keys.TRIP_LOG_DWELL_MINUTES] ?: 15,
            settingsReturnDelaySeconds = preferences[Keys.SETTINGS_RETURN_DELAY] ?: 5.0f,
        )
    }

    suspend fun updateSettings(settings: AppSettings) {
        context.dataStore.edit { preferences ->
            preferences[Keys.BRIGHTNESS] = settings.brightness
            preferences[Keys.IS_AUTO_BRIGHTNESS] = settings.isAutoBrightness
            preferences[Keys.ACTIVATION_THRESHOLD] = settings.activationThreshold
            preferences[Keys.DEACTIVATION_THRESHOLD] = settings.deactivationThreshold
            preferences[Keys.TRIGGER_DELAY_ACTIVATION] = settings.triggerDelayActivationMs
            preferences[Keys.TRIGGER_DELAY_DEACTIVATION] = settings.triggerDelayDeactivationMs
            preferences[Keys.IS_MONITORING_ENABLED] = settings.isMonitoringEnabled
            preferences[Keys.CLOCK_FONT] = settings.clockFont
            preferences[Keys.DATE_FONT] = settings.dateFont
            preferences[Keys.BATTERY_FONT] = settings.batteryFont
            preferences[Keys.CLOCK_SIZE] = settings.clockSizeSp
            preferences[Keys.DATE_SIZE] = settings.dateSizeSp
            preferences[Keys.BATTERY_SIZE] = settings.batterySizeSp
            preferences[Keys.IS_ONEPLUS_STYLE] = settings.isOnePlusStyle
            preferences[Keys.IS_DARK_MODE] = settings.isDarkMode
            preferences[Keys.ACTIVATION_VIBRATION] = settings.activationVibrationIntensity
            preferences[Keys.DEACTIVATION_VIBRATION] = settings.deactivationVibrationIntensity
            settings.customClockFontPath?.let { preferences[Keys.CUSTOM_CLOCK_FONT] = it } 
                ?: preferences.remove(Keys.CUSTOM_CLOCK_FONT)
            settings.customDateFontPath?.let { preferences[Keys.CUSTOM_DATE_FONT] = it } 
                ?: preferences.remove(Keys.CUSTOM_DATE_FONT)
            settings.customBatteryFontPath?.let { preferences[Keys.CUSTOM_BATTERY_FONT] = it } 
                ?: preferences.remove(Keys.CUSTOM_BATTERY_FONT)
            preferences[Keys.CUSTOM_FONTS] = settings.customFonts
            preferences[Keys.SHOW_MAGNETIC_FIELD] = settings.showMagneticField
            preferences[Keys.SHOW_WEATHER] = settings.showWeather
            preferences[Keys.SHOW_SPEED] = settings.showSpeed
            preferences[Keys.SPEED_SIZE] = settings.speedSizeSp
            preferences[Keys.SHOW_PHONE_TEMPERATURE] = settings.showPhoneTemperature
            preferences[Keys.USE_PROXIMITY_SENSOR] = settings.useProximitySensor
            preferences[Keys.LAYOUT_INDEX] = settings.layoutIndex
            preferences[Keys.SHOW_TRIP_TIME] = settings.showTripTime
            preferences[Keys.SHOW_TRIP_DISTANCE] = settings.showTripDistance
            preferences[Keys.TRIP_LOG_DWELL_MINUTES] = settings.tripLogDwellMinutes
            preferences[Keys.SETTINGS_RETURN_DELAY] = settings.settingsReturnDelaySeconds
        }
    }
}
