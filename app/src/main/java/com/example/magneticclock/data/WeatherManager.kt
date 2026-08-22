package com.example.magneticclock.data

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class WeatherData(
    val temperature: Float,
    val weatherCode: Int
)

class WeatherManager(private val context: Context) {

    suspend fun fetchWeather(): WeatherData? = withContext(Dispatchers.IO) {
        try {
            val location = getLastKnownLocation() ?: return@withContext null
            val urlString = "https://api.open-meteo.com/v1/forecast?latitude=${location.latitude}&longitude=${location.longitude}&current_weather=true"
            
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val currentWeather = json.getJSONObject("current_weather")
                
                return@withContext WeatherData(
                    temperature = currentWeather.getDouble("temperature").toFloat(),
                    weatherCode = currentWeather.getInt("weathercode")
                )
            }
        } catch (e: Exception) {
            Log.e("WeatherManager", "Error fetching weather", e)
        }
        return@withContext null
    }

    private fun getLastKnownLocation(): Location? {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return try {
            val providers = locationManager.getProviders(true)
            var bestLocation: Location? = null
            for (provider in providers) {
                val l = locationManager.getLastKnownLocation(provider) ?: continue
                if (bestLocation == null || l.accuracy < bestLocation.accuracy) {
                    bestLocation = l
                }
            }
            bestLocation
        } catch (e: SecurityException) {
            null
        }
    }
}
