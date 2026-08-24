package com.example.magneticclock.data

import android.content.Context
import android.location.Geocoder
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object JournalManager {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private const val JSON_FILE = "trips_journal.json"
    private const val TEXT_FILE = "trips_history.txt"

    fun loadTrips(context: Context): List<TripEntry> {
        val file = File(context.filesDir, JSON_FILE)
        if (!file.exists()) return emptyList()
        return try {
            val json = file.readText()
            val type = object : TypeToken<List<TripEntry>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveTrip(context: Context, entry: TripEntry) {
        val trips = loadTrips(context).toMutableList()
        trips.add(entry)
        saveAll(context, trips)
    }

    fun deleteTrip(context: Context, id: String) {
        val trips = loadTrips(context).toMutableList()
        trips.removeAll { it.id == id }
        saveAll(context, trips)
    }

    fun getLastTrip(context: Context): TripEntry? {
        return loadTrips(context).lastOrNull()
    }

    fun deleteLastTrip(context: Context) {
        val trips = loadTrips(context).toMutableList()
        if (trips.isNotEmpty()) {
            trips.removeAt(trips.size - 1)
            saveAll(context, trips)
        }
    }

    private fun saveAll(context: Context, trips: List<TripEntry>) {
        val jsonFile = File(context.filesDir, JSON_FILE)
        val textFile = File(context.filesDir, TEXT_FILE)
        
        // Авто-очищення: зберігаємо поїздки лише за останні 30 днів
        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        val filteredTrips = trips.filter { it.startTime > thirtyDaysAgo }

        try {
            val json = gson.toJson(filteredTrips)
            jsonFile.writeText(json)
            
            // Генеруємо текстовий звіт для користувача
            val readableText = generateReadableHistory(filteredTrips)
            textFile.writeText(readableText)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun generateReadableHistory(trips: List<TripEntry>): String {
        val sb = StringBuilder()
        sb.append("MAGNETIC CLOCK - TRIP HISTORY\n")
        sb.append("=============================\n\n")

        val grouped = trips.groupBy { it.date }
        val sortedDates = grouped.keys.sortedDescending()

        for (date in sortedDates) {
            sb.append("DATE: $date\n")
            sb.append("-----------------------------\n")
            val dayTrips = grouped[date]?.sortedBy { it.startTime } ?: emptyList()
            dayTrips.forEachIndexed { index, trip ->
                val duration = trip.endTime - trip.startTime
                val hours = duration / (1000 * 60 * 60)
                val minutes = (duration / (1000 * 60)) % 60
                val seconds = (duration / 1000) % 60
                val durationStr = if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)

                val startTimeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(trip.startTime))
                val endTimeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(trip.endTime))

                sb.append("Trip #${index + 1} | Duration: $durationStr | Distance: ${"%.1f".format(trip.distance)} km\n")
                sb.append("  START: $startTimeStr | ${trip.startAddress}\n")
                sb.append("         Map: https://www.google.com/maps/search/?api=1&query=${trip.startLatLng}\n")
                sb.append("  END:   $endTimeStr | ${trip.endAddress}\n")
                sb.append("         Map: https://www.google.com/maps/search/?api=1&query=${trip.endLatLng}\n")
                sb.append("\n")
            }
            sb.append("\n")
        }
        return sb.toString()
    }

    fun getAddress(context: Context, lat: Double, lng: Double): String {
        if (lat == 0.0 && lng == 0.0) return "Невідома локація"
        return try {
            val geocoder = Geocoder(context, Locale("uk", "UA"))
            val addresses = @Suppress("DEPRECATION") geocoder.getFromLocation(lat, lng, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                
                val city = addr.locality ?: ""
                val street = addr.thoroughfare ?: ""
                val house = addr.subThoroughfare ?: addr.featureName ?: ""
                
                val formattedStreet = shortenStreetType(street)
                
                val parts = mutableListOf<String>()
                if (city.isNotEmpty()) parts.add(city)
                if (formattedStreet.isNotEmpty()) parts.add(formattedStreet)
                if (house.isNotEmpty() && house != street) parts.add(house)
                
                if (parts.isNotEmpty()) parts.joinToString(", ")
                else addr.getAddressLine(0) ?: "$lat, $lng"
            } else "$lat, $lng"
        } catch (e: Exception) {
            "$lat, $lng"
        }
    }

    private fun shortenStreetType(street: String): String {
        if (street.isEmpty()) return ""
        return street
            .replace("вулиця", "вул.")
            .replace("Вулиця", "вул.")
            .replace("проспект", "просп.")
            .replace("Проспект", "просп.")
            .replace("бульвар", "бульв.")
            .replace("Бульвар", "бульв.")
            .replace("площа", "пл.")
            .replace("Площа", "пл.")
            .replace("провулок", "пров.")
            .replace("Провулок", "пров.")
            .replace("шосе", "ш.")
            .replace("Шосе", "ш.")
    }

    fun generateMockData(context: Context) {
        val trips = mutableListOf<TripEntry>()
        val cal = Calendar.getInstance()
        val random = Random()

        // Створюємо записи за останні 10 днів (близько 15 поїздок)
        for (_i in 0 until 10) {
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
            // 1-2 поїздки на день
            val numTrips = random.nextInt(2) + 1
            for (_j in 0 until numTrips) {
                val startHour = 8 + random.nextInt(10)
                cal.set(Calendar.HOUR_OF_DAY, startHour)
                cal.set(Calendar.MINUTE, random.nextInt(60))
                val startTime = cal.timeInMillis
                
                // Тривалість від 15 до 90 хвилин
                val durationMs = (15 + random.nextInt(75)) * 60 * 1000L
                val endTime = startTime + durationMs
                
                // Дистанція від 3 до 45 км
                val distance = 3.0 + random.nextDouble() * 42.0
                
                trips.add(TripEntry(
                    date = dateStr,
                    startTime = startTime,
                    endTime = endTime,
                    distance = distance,
                    startAddress = "Київ, вул. Центральна, ${random.nextInt(100) + 1}",
                    startLatLng = "50.4501,30.5234",
                    endAddress = "Київ, просп. Перемоги, ${random.nextInt(120) + 1}",
                    endLatLng = "50.4580,30.4500"
                ))
            }
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        // Очищаємо старі файли перед записом нових
        File(context.filesDir, JSON_FILE).delete()
        File(context.filesDir, TEXT_FILE).delete()
        
        saveAll(context, trips)
    }
}
