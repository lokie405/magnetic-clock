package com.example.magneticclock.data

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import java.text.SimpleDateFormat
import java.util.*

object AppLogger {
    private const val TAG = "MagneticClockLog"
    private const val MAX_LOG_SIZE = 400

    // Елементи зберігаються у звичайному потокобезпечному списку в пам'яті для Compose
    val logList = mutableStateListOf<String>()

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    @Synchronized
    fun d(message: String) {
        addLog("DEBUG", message)
        Log.d(TAG, message)
    }

    @Synchronized
    fun i(message: String) {
        addLog("INFO", message)
        Log.i(TAG, message)
    }

    @Synchronized
    fun w(message: String) {
        addLog("WARN", message)
        Log.w(TAG, message)
    }

    @Synchronized
    fun e(message: String, throwable: Throwable? = null) {
        val msg = if (throwable != null) "$message \n${Log.getStackTraceString(throwable)}" else message
        addLog("ERROR", msg)
        Log.e(TAG, msg)
    }

    private fun addLog(level: String, message: String) {
        val timestamp = timeFormat.format(Date())
        val logLine = "[$timestamp] $level: $message"
        
        // Виконуємо операції з інтерфейсним списком на головному потоці Android
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        handler.post {
            if (logList.size >= MAX_LOG_SIZE) {
                logList.removeAt(0)
            }
            logList.add(logLine)
        }
    }

    @Synchronized
    fun clear() {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        handler.post {
            logList.clear()
            logList.add("[${timeFormat.format(Date())}] INFO: Журнал очищено користувачем")
        }
    }
}
