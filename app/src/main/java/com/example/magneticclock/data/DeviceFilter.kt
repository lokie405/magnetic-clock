package com.example.magneticclock.data

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

object DeviceFilter {
    
    fun isTargetDevice(context: Context, device: BluetoothDevice, settings: AppSettings): Boolean {
        val address = device.address ?: ""
        
        // Отримуємо ім'я пристрою
        var name: String? = null
        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                name = device.name
            }
        } catch (e: Exception) { }
        
        val finalName = (name ?: "Невідомо").lowercase()

        // 1. Ford Focus 3 (MAC: 00:87:61:0A:69:36)
        if (settings.includeFordFocus) {
            if (address.equals("00:87:61:0A:69:36", ignoreCase = true) || 
                finalName.contains("ford") || finalName.contains("focus")) return true
        }
        
        // 2. SYNC (MAC: 00:21:CC:2E:3A:12)
        if (settings.includeSync) {
            if (address.equals("00:21:CC:2E:3A:12", ignoreCase = true) || 
                finalName.contains("sync")) return true
        }
        
        // 3. Навушники Hawit TW929 Pro (MAC: 70:BB:61:90:29:17)
        if (settings.includeHavit) {
            if (address.equals("70:BB:61:90:29:17", ignoreCase = true) || 
                finalName.contains("hawit") || finalName.contains("tw929")) return true
        }

        // 4. Кастомний пристрій з текстового поля
        val custom = settings.bluetoothTriggerDeviceName.trim()
        if (custom.isNotEmpty()) {
            if (custom.contains(":") && address.equals(custom, ignoreCase = true)) return true
            if (finalName.contains(custom.lowercase())) return true
        }

        // 5. Fallback: Тільки якщо НЕ увімкнено жодного іншого фільтру, дозволяємо авто-визначення за класом
        val anyExplicitEnabled = settings.includeFordFocus || settings.includeSync || settings.includeHavit || custom.isNotEmpty()
        
        if (!anyExplicitEnabled) {
            try {
                val bluetoothClass = device.bluetoothClass
                if (bluetoothClass != null) {
                    val deviceClass = bluetoothClass.deviceClass
                    // 1032 = AUDIO_VIDEO_CAR_AUDIO, 1056 = AUDIO_VIDEO_HANDSFREE
                    if (deviceClass == 1032 || deviceClass == 1056) return true
                }
            } catch (e: Exception) {}
        }

        return false
    }
}
