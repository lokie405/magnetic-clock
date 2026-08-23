package com.example.magneticclock.data

import java.util.UUID

data class TripEntry(
    val id: String = UUID.randomUUID().toString(),
    val date: String, // yyyy-MM-dd
    val startTime: Long,
    val endTime: Long,
    val distance: Double,
    val startAddress: String,
    val startLatLng: String, // "lat,lng"
    val endAddress: String,
    val endLatLng: String
)
