package com.example.magneticclock.data

import android.content.Context
import android.location.Location
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

object TripManager {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    var tripStartTime by mutableLongStateOf(0L)
    var tripDistance by mutableDoubleStateOf(0.0)
    var isTripActive by mutableStateOf(false) // True if we should be recording (driveCar active)
    
    private var lastLocation: Location? = null
    var currentSpeedKmH by mutableStateOf(0f)
    
    private var startLat: Double = 0.0
    private var startLng: Double = 0.0
    private var endLat: Double = 0.0
    private var endLng: Double = 0.0
    private var routePoints = mutableListOf<String>()
    private var lastRoutePointTime: Long = 0

    // Simulation fields
    private var isSimulating = false
    private var simulationJob: Job? = null

    /**
     * Called by Service whenever location changes.
     */
    fun updateLocation(location: Location) {
        val speed = location.speed * 3.6f
        currentSpeedKmH = speed

        // Requirement: driveCar starts when speed >= 2 km/h 
        // AND (implicitly) we are inCar and Magnet is on (service ensures updateLocation only called then)
        if (speed >= 2.0f && tripStartTime == 0L) {
            startTrip(location.latitude, location.longitude)
        }

        // Accumulate distance if trip is active
        if (tripStartTime > 0L) {
            lastLocation?.let { prev ->
                val distanceMeters = location.distanceTo(prev)
                if (distanceMeters > 0) {
                    tripDistance += (distanceMeters / 1000.0)
                }
            }
            endLat = location.latitude
            endLng = location.longitude

            // Record route point every 30 seconds or 100 meters
            val now = System.currentTimeMillis()
            val lastPoint = lastLocation
            if (now - lastRoutePointTime > 30000 || (lastPoint != null && location.distanceTo(lastPoint) > 100)) {
                routePoints.add("${location.latitude},${location.longitude}")
                lastRoutePointTime = now
            }
        }
        lastLocation = location
    }

    private fun startTrip(lat: Double, lng: Double) {
        Log.i("TripManager", "Drive started (driveCar). Speed: $currentSpeedKmH")
        tripStartTime = System.currentTimeMillis()
        startLat = lat
        startLng = lng
        isTripActive = true
        routePoints.clear()
        routePoints.add("$lat,$lng")
        lastRoutePointTime = tripStartTime
    }

    /**
     * Called by Service when inCar ends (driveEnd).
     */
    fun onBluetoothDisconnected(context: Context) {
        Log.d("TripManager", "driveEnd: inCar finished. Saving if driveCar was active.")
        if (tripStartTime > 0L) {
            finalizeAndSave(context)
        } else {
            resetTrip()
        }
    }

    fun onClockOpened() {
        // We could prepare something here, but driveCar starts by speed
    }

    fun onMagnetRemoved(context: Context) {
        // Requirement says driveCar ends when inCar ends, not magnet removal.
        // However, if the user removes the phone, speed will eventually drop to 0, 
        // but we keep the trip active until inCar (BT) is lost.
    }

    private fun finalizeAndSave(context: Context) {
        val sTime = tripStartTime
        val dist = tripDistance
        val sLat = startLat
        val sLng = startLng
        val eLat = endLat
        val eLng = endLng
        val finalRoute = routePoints.toList()
        
        Log.i("TripManager", "Finalizing trip: $dist km with ${finalRoute.size} points")

        val appContext = context.applicationContext
        scope.launch(Dispatchers.IO) {
            try {
                val endTime = System.currentTimeMillis()
                val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(sTime))
                
                // Get addresses
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
                    endLatLng = "$eLat,$eLng",
                    route = finalRoute
                )

                JournalManager.saveTrip(appContext, entry)
            } catch (e: Exception) {
                Log.e("TripManager", "Error saving trip: ${e.message}")
            }
        }
        
        resetTrip()
    }

    fun resetTrip() {
        stopSpeedSimulation()
        tripStartTime = 0L
        tripDistance = 0.0
        isTripActive = false
        lastLocation = null
        currentSpeedKmH = 0f
        routePoints.clear()
        lastRoutePointTime = 0
    }

    fun toggleSpeedSimulation() {
        if (isSimulating) {
            stopSpeedSimulation()
        } else {
            startSpeedSimulation()
        }
    }

    private fun startSpeedSimulation() {
        isSimulating = true
        currentSpeedKmH = 50f
        
        if (tripStartTime == 0L) {
            // Координати Рівного для тестів (центр)
            startTrip(50.6199, 26.2516)
        }

        simulationJob?.cancel()
        simulationJob = scope.launch {
            while (isSimulating) {
                delay(1000)
                tripDistance += (13.88 / 1000.0)
                
                // Симулюємо рух у бік Квасилова для зміни адреси
                endLat = 50.6199 - (tripDistance / 111.0) 
                endLng = 26.2516 + (tripDistance / 70.0)
                
                // Додаємо точки маршруту в симуляції
                routePoints.add("$endLat,$endLng")
            }
        }
        Log.d("TripManager", "Speed simulation started: 50 km/h (Rivne)")
    }

    private fun stopSpeedSimulation() {
        isSimulating = false
        simulationJob?.cancel()
        simulationJob = null
        currentSpeedKmH = 0f
        Log.d("TripManager", "Speed simulation stopped")
    }
}
