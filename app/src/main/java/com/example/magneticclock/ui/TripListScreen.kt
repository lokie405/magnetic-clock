package com.example.magneticclock.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.magneticclock.data.TripEntry
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripListScreen(
    trips: List<TripEntry>,
    onBack: () -> Unit
) {
    val groupedTrips = remember(trips) {
        trips.groupBy { it.date }.toSortedMap(compareByDescending { it })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Журнал поїздок") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (trips.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Поїздок ще немає", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                groupedTrips.forEach { (date, dayTrips) ->
                    item(key = date) {
                        DateHeader(date = date, dayTrips = dayTrips)
                    }
                }
            }
        }
    }
}

@Composable
fun DateHeader(date: String, dayTrips: List<TripEntry>) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatDisplayDate(date),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }
            
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    dayTrips.sortedByDescending { it.startTime }.forEachIndexed { index, trip ->
                        TripItem(index = dayTrips.size - index, trip = trip)
                        if (index < dayTrips.size - 1) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = Color.Gray.copy(alpha = 0.2f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TripItem(index: Int, trip: TripEntry) {
    val duration = trip.endTime - trip.startTime
    val min = (duration / 60000)
    val sec = (duration % 60000) / 1000
    val durationStr = if (min > 0) "${min}хв, ${sec}с" else "${sec}с"

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            text = "$index поїздка ($durationStr / ${"%.2f".format(trip.distance)}км)",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(4.dp))
        AddressRow(label = "Початок", address = trip.startAddress)
        AddressRow(label = "Кінець", address = trip.endAddress)
    }
}

@Composable
fun AddressRow(label: String, address: String) {
    Column(modifier = Modifier.padding(start = 8.dp, top = 4.dp)) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.labelMedium,
            color = Color.Gray
        )
        Text(
            text = address,
            style = MaterialTheme.typography.bodySmall,
            lineHeight = 16.sp
        )
    }
}

fun formatDisplayDate(dateStr: String): String {
    return try {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr)
        SimpleDateFormat("d MMMM yyyy", Locale("uk", "UA")).format(date!!)
    } catch (e: Exception) {
        dateStr
    }
}
