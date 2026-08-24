package com.example.magneticclock.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.magneticclock.data.JournalManager
import com.example.magneticclock.data.TripEntry
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var trips by remember { mutableStateOf(JournalManager.loadTrips(context)) }
    var tripToDelete by remember { mutableStateOf<TripEntry?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trip Journal") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val file = java.io.File(context.filesDir, "trips_history.txt")
                        if (file.exists()) {
                            // Simple way to share content
                            val text = file.readText()
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, text)
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, "Export Trip History")
                            context.startActivity(shareIntent)
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Export")
                    }
                }
            )
        }
    ) { padding ->
        if (trips.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No trips recorded yet", color = Color.Gray)
            }
        } else {
            val groupedTrips = trips.groupBy { it.date }.toSortedMap(compareByDescending { it })
            
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                groupedTrips.forEach { (date, dayTrips) ->
                    item {
                        DateGroupHeader(date, dayTrips, onDeleteRequest = { tripToDelete = it })
                    }
                }
            }
        }
    }

    if (tripToDelete != null) {
        AlertDialog(
            onDismissRequest = { tripToDelete = null },
            title = { Text("Delete Trip?") },
            text = { Text("Are you sure you want to delete this trip record?") },
            confirmButton = {
                TextButton(onClick = {
                    JournalManager.deleteTrip(context, tripToDelete!!.id)
                    trips = JournalManager.loadTrips(context)
                    tripToDelete = null
                }) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { tripToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DateGroupHeader(date: String, dayTrips: List<TripEntry>, onDeleteRequest: (TripEntry) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val sortedTrips = dayTrips.sortedBy { it.startTime }

    Column {
        Surface(
            onClick = { expanded = !expanded },
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.medium
        ) {
            Row(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                    contentDescription = null
                )
                Spacer(Modifier.width(12.dp))
                Text(date, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.weight(1f))
                Text("${dayTrips.size} trips", fontSize = 12.sp)
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(modifier = Modifier.padding(top = 8.dp, start = 8.dp)) {
                sortedTrips.forEachIndexed { index, trip ->
                    TripItem(index + 1, trip, onLongClick = { onDeleteRequest(trip) })
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TripItem(tripNumber: Int, trip: TripEntry, onLongClick: () -> Unit) {
    val durationMs = trip.endTime - trip.startTime
    val hours = durationMs / (1000 * 60 * 60)
    val minutes = (durationMs / (1000 * 60)) % 60
    val seconds = (durationMs / 1000) % 60
    val durationStr = if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)

    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    Card(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth()
            .combinedClickable(
                onClick = { },
                onLongClick = onLongClick
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = Color.Black,
                    shape = androidx.compose.foundation.shape.CircleShape,
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(tripNumber.toString(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(durationStr, fontWeight = FontWeight.Black, fontSize = 16.sp)
                Spacer(Modifier.width(16.dp))
                Text(
                    text = "${"%.1f".format(trip.distance)} km", 
                    color = MaterialTheme.colorScheme.primary, 
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp
                )
            }

            Spacer(Modifier.height(8.dp))

            // Start Info
            TripPointRow(
                time = timeFormat.format(Date(trip.startTime)),
                address = trip.startAddress,
                latLng = trip.startLatLng,
                icon = Icons.Default.PlayCircle,
                tint = Color.Green
            )

            Spacer(Modifier.height(4.dp))

            // End Info
            TripPointRow(
                time = timeFormat.format(Date(trip.endTime)),
                address = trip.endAddress,
                latLng = trip.endLatLng,
                icon = Icons.Default.StopCircle,
                tint = Color.Red
            )
        }
    }
}

@Composable
fun TripPointRow(time: String, address: String, latLng: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp).padding(top = 2.dp))
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row {
                Text(time, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    address, 
                    fontSize = 12.sp, 
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable {
                        val uri = "https://www.google.com/maps/search/?api=1&query=$latLng"
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                        context.startActivity(intent)
                    },
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
