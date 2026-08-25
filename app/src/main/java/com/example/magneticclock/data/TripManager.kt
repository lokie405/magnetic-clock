package com.example.magneticclock.data

import android.content.Context
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

object TripManager {
    private val scope = CoroutineScope(Dispatchers.Main)

    var tripStartTime by mutableLongStateOf(0L)
    var tripDistance by mutableDoubleStateOf(0.0)
    var isTripActive by mutableStateOf(false) // Means phone is on magnet and trip is ready
    
    private var lastLocation: Location? = null
    var currentSpeedKmH by mutableStateOf(0f)
    private var isMockingMovement = false
    private var mockJob: Job? = null

    private var startLat: Double = 0.0
    private var startLng: Double = 0.0
    private var endLat: Double = 0.0
    private var endLng: Double = 0.0

    // Continuation Logic
    var lastFinalizedTime by mutableLongStateOf(0L)

    fun updateLocation(location: Location) {
        if (isMockingMovement) return // Ignore real GPS if mocking

        val speed = location.speed * 3.6f
        currentSpeedKmH = speed

        // Trigger start of counting only when moving or manual start
        if (speed > 2.0f && isTripActive && tripStartTime == 0L) {
            startTrip(location.latitude, location.longitude)
        }

        // Accumulate distance if counting has started
        if (isTripActive && tripStartTime > 0L) {
            lastLocation?.let { prev ->
                val distanceMeters = location.distanceTo(prev)
                if (distanceMeters > 0) {
                    tripDistance += (distanceMeters / 1000.0)
                }
            }
            endLat = location.latitude
            endLng = location.longitude
        }
        lastLocation = location
    }

    fun startTrip(lat: Double, lng: Double) {
        if (tripStartTime == 0L) {
            Log.d("TripManager", "Starting trip manually with simulation")
            tripStartTime = System.currentTimeMillis()
            startLat = lat
            startLng = lng
            isTripActive = true
            
            // Start simulation
            isMockingMovement = true
            currentSpeedKmH = 10f
            mockJob?.cancel()
            mockJob = scope.launch {
                while (isMockingMovement && isTripActive) {
                    delay(1000)
                    // 10 km/h = 2.77 meters per second
                    tripDistance += (2.77 / 1000.0)
                }
            }
        }
    }

    fun onBluetoothDisconnected(context: Context) {
        Log.d("TripManager", "onBluetoothDisconnected called. isTripActive=$isTripActive, tripStartTime=$tripStartTime")
        if (isTripActive && tripStartTime > 0L) {
            finalizeAndSave(context)
        } else {
            resetTrip()
        }
        context.sendBroadcast(android.content.Intent("CLOSE_CLOCK_ACTIVITY").apply { setPackage(context.packageName) })
    }

    fun onClockOpened() {
        if (!isTripActive) {
            // Prepare trip immediately on magnet contact, but don't count time yet
            tripStartTime = 0L
            tripDistance = 0.0
            isTripActive = true
        }
    }

    fun onMagnetRemoved(context: Context) {
        // We no longer finalize on magnet removal, only on BT disconnect
    }

    fun resumeLastTrip(context: Context) {
        val lastTrip = JournalManager.getLastTrip(context)
        if (lastTrip != null) {
            tripStartTime = lastTrip.startTime
            tripDistance = lastTrip.distance
            
            val startParts = lastTrip.startLatLng.split(",")
            if (startParts.size == 2) {
                startLat = startParts[0].toDoubleOrNull() ?: 0.0
                startLng = startParts[1].toDoubleOrNull() ?: 0.0
            }
            
            val endParts = lastTrip.endLatLng.split(",")
            if (endParts.size == 2) {
                endLat = endParts[0].toDoubleOrNull() ?: 0.0
                endLng = endParts[1].toDoubleOrNull() ?: 0.0
            }

            isTripActive = true
            JournalManager.deleteLastTrip(context)
        }
    }

    fun dismissResume() {
        if (!isTripActive) {
            tripStartTime = 0L
            tripDistance = 0.0
            isTripActive = true
        }
    }

    private fun finalizeAndSave(context: Context) {
        if (!isTripActive || tripStartTime == 0L) {
            Log.d("TripManager", "finalizeAndSave skipped: isTripActive=$isTripActive, startTime=$tripStartTime")
            resetTrip()
            return
        }

        // Capture data before reset
        val sTime = tripStartTime
        val dist = tripDistance
        val sLat = startLat
        val sLng = startLng
        val eLat = endLat
        val eLng = endLng
        
        Log.i("TripManager", "Finalizing trip: distance=$dist km")

        val appContext = context.applicationContext
        scope.launch(Dispatchers.IO) {
            try {
                val endTime = System.currentTimeMillis()
                val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(sTime))
                
                val startAddr = JournalManager.getAddress(appContext, sLat, sLng)
                val endAddr = JournalManager.getAddress(appContext, eLat, eLng)

                val entry = TripEntry(
                    date = dateStr,
                    startTime = sTime,
                    endTime = endTime,
                    distance = dist,
                    startAddress = startAddr,
                    startLatLng = "$sLat,$sLng",
                    endAddress = endAddr,
                    endLatLng = "$eLat,$eLng"
                )

                JournalManager.saveTrip(appContext, entry)
                lastFinalizedTime = System.currentTimeMillis()
                
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(appContext, "Поїздку завершено та збережено", Toast.LENGTH_LONG).show()
                }
                Log.d("TripManager", "Trip saved successfully")
            } catch (e: Exception) {
                Log.e("TripManager", "Error saving trip: ${e.message}")
            }
        }
        
        resetTrip()
    }

    fun resetTrip() {
        isMockingMovement = false
        mockJob?.cancel()
        mockJob = null
        tripStartTime = 0L
        tripDistance = 0.0
        isTripActive = false
        lastLocation = null
    }
}
