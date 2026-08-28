package com.example.magneticclock.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun LinePicker(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    unit: String,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "${if (value % 1 == 0f) value.toInt().toString() else "%.1f".format(value)} $unit",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
        }
        Slider(
            value = value,
            onValueChange = { 
                // Snap to steps
                val stepSize = (valueRange.endInclusive - valueRange.start) / (steps + 1)
                val snapped = (it / stepSize).roundToInt() * stepSize
                onValueChange(snapped.coerceIn(valueRange))
            },
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(valueRange.start.toInt().toString(), fontSize = 10.sp, color = androidx.compose.ui.graphics.Color.Gray)
            Text(valueRange.endInclusive.toInt().toString(), fontSize = 10.sp, color = androidx.compose.ui.graphics.Color.Gray)
        }
    }
}
