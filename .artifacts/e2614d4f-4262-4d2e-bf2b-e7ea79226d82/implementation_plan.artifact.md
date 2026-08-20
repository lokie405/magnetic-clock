# Implementation Plan - Magnetic Clock App

This project will create an Android application that monitors the magnetic field using the device's magnetometer. When a specific threshold is met for a set duration, it will display a customizable full-screen clock.

## User Review Required

> [!IMPORTANT]
> **Hotspot Toggle**: On modern Android versions (API 26+), apps cannot directly toggle the mobile hotspot for security reasons without system-level permissions. I will implement a button that opens the System Hotspot Settings for the user to toggle it easily.
> **Brightness**: Adjusting system brightness requires the `WRITE_SETTINGS` permission, which the user must grant manually in system settings. The app will guide the user to this setting.

## Proposed Changes

### 1. Dependencies and Configuration
#### [MODIFY] [build.gradle.kts](file:///C:/Users/user/AndroidStudioProjects/Lessons/MagneticClock/app/build.gradle.kts)
- Add DataStore for settings persistence.
- Add Google Fonts dependency for custom fonts.
- Add Lifecycle Service for background monitoring.

#### [MODIFY] [AndroidManifest.xml](file:///C:/Users/user/AndroidStudioProjects/Lessons/MagneticClock/app/src/main/AndroidManifest.xml)
- Add permissions: `WRITE_SETTINGS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `WAKE_LOCK`.
- Register the `MagneticSensorService`.
- Register `ClockActivity` as a full-screen activity.

---

### 2. Core Logic (Background Monitoring)
#### [NEW] [MagneticSensorService.kt](file:///C:/Users/user/AndroidStudioProjects/Lessons/MagneticClock/app/src/main/java/com/example/magneticclock/MagneticSensorService.kt)
- A Foreground Service that listens to `Sensor.TYPE_MAGNETIC_FIELD`.
- Implements the logic for trigger/release thresholds and duration timers.
- Launches the `ClockActivity` when triggered.

#### [NEW] [SettingsManager.kt](file:///C:/Users/user/AndroidStudioProjects/Lessons/MagneticClock/app/src/main/java/com/example/magneticclock/SettingsManager.kt)
- Uses DataStore to save and load all user preferences (colors, fonts, thresholds, brightness).

---

### 3. User Interface
#### [MODIFY] [MainActivity.kt](file:///C:/Users/user/AndroidStudioProjects/Lessons/MagneticClock/app/src/main/java/com/example/magneticclock/MainActivity.kt)
- Main settings screen:
    - Brightness slider + Auto-toggle.
    - Sensor thresholds (Trigger/Release/Time).
    - Color pickers (30 colors for BG, Clock, Date).
    - Font pickers (10-20 fonts with Ukrainian support).

#### [NEW] [ClockActivity.kt](file:///C:/Users/user/AndroidStudioProjects/Lessons/MagneticClock/app/src/main/java/com/example/magneticclock/ClockActivity.kt)
- The "Clock Screen" triggered by the sensor.
- Displays Time, Date, Battery Level.
- Button to open Hotspot settings.
- Applies the selected colors and fonts.

#### [NEW] [Resources.kt](file:///C:/Users/user/AndroidStudioProjects/Lessons/MagneticClock/app/src/main/java/com/example/magneticclock/Resources.kt)
- Pre-defined list of 30 colors and 20 fonts (Google Fonts with Cyrillic support).

## Verification Plan

### Automated Tests
- Unit tests for `MagneticSensorService` logic (triggering thresholds).

### Manual Verification
- Deploy to a physical device (Emulators don't usually simulate magnetic sensors well).
- Grant `WRITE_SETTINGS` permission.
- Move a magnet near the phone to trigger the clock.
- Verify brightness adjustment.
- Verify font and color changes on the clock screen.
