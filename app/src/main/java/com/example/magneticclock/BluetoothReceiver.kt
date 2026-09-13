package com.example.magneticclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothAdapter
import android.os.Build
import com.example.magneticclock.data.DeviceFilter
import com.example.magneticclock.data.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BluetoothReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        android.util.Log.d("MagneticClock", "BluetoothReceiver: action=$action")

        val wakeUpActions = listOf(
            BluetoothDevice.ACTION_ACL_CONNECTED,
            "android.bluetooth.adapter.action.CONNECTION_STATE_CHANGED",
            BluetoothAdapter.ACTION_STATE_CHANGED,
            BluetoothDevice.ACTION_BOND_STATE_CHANGED
        )

        if (action in wakeUpActions) {
            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
            
            // Якщо є пристрій, перевіряємо чи він наш ПЕРЕД запуском сервісу
            if (device != null) {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val settings = SettingsManager(context).getSettingsOnce()
                        if (DeviceFilter.isTargetDevice(context, device, settings)) {
                            com.example.magneticclock.data.AppLogger.w("BT_DEV: Спрацював системний фільтр для ${device.address}. Запускаємо сервіс.")
                            startServiceSafely(context, device)
                        } else {
                            //com.example.magneticclock.data.AppLogger.d("BT_DEV: Системний фільтр відхилив ${device.address}")
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            } else if (action == BluetoothAdapter.ACTION_STATE_CHANGED || action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                // Для загальних змін стану запускаємо сервіс на коротку перевірку без фільтру пристрою
                startServiceSafely(context, null)
            }
        }
    }

    private fun startServiceSafely(context: Context, device: BluetoothDevice?) {
        val serviceIntent = Intent(context, MagneticSensorService::class.java)
        if (device != null) {
            serviceIntent.putExtra("target_device", device)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
