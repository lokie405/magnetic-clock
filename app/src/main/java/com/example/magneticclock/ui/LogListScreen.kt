package com.example.magneticclock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.magneticclock.data.AppLogger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogListScreen(
    onBack: () -> Unit
) {
    val logs = AppLogger.logList
    val listState = rememberLazyListState()

    // Автоматична прокрутка до найновіших логів внизу екрана
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Журнал діагностики (RAM)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    IconButton(onClick = {
                        try {
                            val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_SUBJECT, "Magnetic Clock Diagnostic Logs")
                                putExtra(android.content.Intent.EXTRA_TEXT, logs.joinToString("\n"))
                            }
                            val shareIntent = android.content.Intent.createChooser(sendIntent, "Експорт логів через...")
                            context.startActivity(shareIntent)
                        } catch (e: Exception) {
                            AppLogger.e("Помилка при експорті логів", e)
                        }
                    }) {
                        Text("Експорт", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 4.dp))
                    }
                    IconButton(onClick = { AppLogger.clear() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Очистити лог")
                    }
                }
            )
        }
    ) { padding ->
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Журнал порожній", color = Color.Gray)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.surface),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(logs) { logLine ->
                    val color = when {
                        logLine.contains("ERROR:") -> Color(0xFFEF5350)
                        logLine.contains("WARN:") || logLine.contains("BT_DEV:") -> Color(0xFFFFB74D) // Ключ BT_DEV підсвічує помаранчевим
                        logLine.contains("INFO:") -> Color(0xFF66BB6A)
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    Text(
                        text = logLine,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = color,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}
