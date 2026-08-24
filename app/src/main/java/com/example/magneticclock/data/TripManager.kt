package com.example.magneticclock.data

import android.content.Context
import android.location.Location
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.SimpleDateFormat
import java.util.*

object TripManager {
    var tripStartTime by mutableLongStateOf(0L)
    var tripDistance by mutableDoubleStateOf(0.0)
    var isTripActive by mutableStateOf(false) // Means phone is on magnet and trip is ready
    
    private var lastLocation: Location? = null
    var currentSpeedKmH by mutableStateOf(0f)

    private var startLat: Double = 0.0
    private var startLng: Double = 0.0
    private var endLat: Double = 0.0
    private var endLng: Double = 0.0

    // Continuation Logic
    var lastFinalizedTime by mutableLongStateOf(0L)
    var isResumeWindowActive by mutableStateOf(false)

    fun updateLocation(location: Location) {
        val speed = location.speed * 3.6f
        currentSpeedKmH = speed

        // Trigger start of counting only when moving
        if (speed > 2.0f && isTripActive && tripStartTime == 0L) {
            tripStartTime = System.currentTimeMillis()
            startLat = location.latitude
            startLng = location.longitude
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
            
            if (speed > 2.0f && isResumeWindowActive) {
                isResumeWindowActive = false
            }
        }
        lastLocation = location
    }

    fun onClockOpened(dwellMinutes: Int) {
        if (!isTripActive) {
            val elapsedMs = if (lastFinalizedTime > 0) System.currentTimeMillis() - lastFinalizedTime else Long.MAX_VALUE
            if (elapsedMs < dwellMinutes * 60 * 1000L) {
                isResumeWindowActive = true
            } else {
                // Prepare trip immediately on magnet contact, but don't count time yet
                tripStartTime = 0L
                tripDistance = 0.0
                isTripActive = true
                isResumeWindowActive = false
            }
        }
    }

    fun onMagnetRemoved(context: Context) {
        if (isTripActive) {
            if (tripStartTime > 0L) {
                finalizeAndSave(context)
            } else {
                resetTrip() // Just reset if trip never actually started moving
            }
        }
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
            isResumeWindowActive = false
            JournalManager.deleteLastTrip(context)
        }
    }

    fun dismissResume() {
        isResumeWindowActive = false
        if (!isTripActive) {
            tripStartTime = 0L
            tripDistance = 0.0
            isTripActive = true
        }
    }

    private fun finalizeAndSave(context: Context) {
        if (!isTripActive || tripStartTime == 0L) return

        val endTime = System.currentTimeMillis()
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(tripStartTime))
        
        val startAddr = JournalManager.getAddress(context, startLat, startLng)
        val endAddr = JournalManager.getAddress(context, endLat, endLng)

        val entry = TripEntry(
            date = dateStr,
            startTime = tripStartTime,
            endTime = endTime,
            distance = tripDistance,
            startAddress = startAddr,
            startLatLng = "$startLat,$startLng",
            endAddress = endAddr,
            endLatLng = "$endLat,$endLng"
        )

        JournalManager.saveTrip(context, entry)
        lastFinalizedTime = System.currentTimeMillis()
        resetTrip()
    }

    fun resetTrip() {
        tripStartTime = 0L
        tripDistance = 0.0
        isTripActive = false
        lastLocation = null
    }
}
