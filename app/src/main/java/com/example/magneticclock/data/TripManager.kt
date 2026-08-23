package com.example.magneticclock.data

import android.content.Context
import android.location.Location
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

object TripManager {
    var tripStartTime by mutableLongStateOf(0L)
    var tripDistance by mutableDoubleStateOf(0.0)
    var isTripStarted by mutableStateOf(false)
    
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

        // Start trip if moving and not started yet
        if (speed > 2.0f && !isTripStarted) {
            tripStartTime = System.currentTimeMillis()
            tripDistance = 0.0
            isTripStarted = true
            isResumeWindowActive = false
            startLat = location.latitude
            startLng = location.longitude
        }

        // Accumulate distance if trip is active
        if (isTripStarted) {
            lastLocation?.let { prev ->
                val distanceMeters = location.distanceTo(prev)
                if (distanceMeters > 0) {
                    tripDistance += (distanceMeters / 1000.0)
                }
            }
            endLat = location.latitude
            endLng = location.longitude
            
            // If movement restarts during resume window, finalize the old one and start new (implicit in speed > 2)
            if (speed > 2.0f && isResumeWindowActive) {
                isResumeWindowActive = false
            }
        }
        lastLocation = location
    }

    fun onClockOpened(dwellMinutes: Int) {
        if (!isTripStarted && lastFinalizedTime > 0) {
            val elapsedMs = System.currentTimeMillis() - lastFinalizedTime
            if (elapsedMs < dwellMinutes * 60 * 1000L) {
                isResumeWindowActive = true
            }
        }
    }

    fun onMagnetRemoved(context: Context) {
        if (isTripStarted && currentSpeedKmH < 1.0f) {
            finalizeAndSave(context)
        }
    }

    fun resumeLastTrip(context: Context) {
        val lastTrip = JournalManager.getLastTrip(context)
        if (lastTrip != null) {
            tripStartTime = lastTrip.startTime
            tripDistance = lastTrip.distance
            
            // Re-parse coordinates from "lat,lng" string
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

            isTripStarted = true
            isResumeWindowActive = false
            
            // Remove it from journal as it's now back in active state
            JournalManager.deleteLastTrip(context)
        }
    }

    fun dismissResume() {
        isResumeWindowActive = false
    }

    private fun finalizeAndSave(context: Context) {
        if (!isTripStarted) return

        val endTime = System.currentTimeMillis()
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(tripStartTime))
        
        // We do geocoding in foreground or background?
        // To be safe and immediate, we save coordinates, geocoding can happen on view or via simple call
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
        isTripStarted = false
        lastLocation = null
    }
}
