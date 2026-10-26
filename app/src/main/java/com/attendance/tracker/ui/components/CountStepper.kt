package com.attendance.tracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun CountStepper(
    label: String,
    color: Color,
    value: Int,
    modifier: Modifier = Modifier,
    onChange: (Int) -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = color, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        // One slim tinted pill: "−  2  +". Whole 36dp halves are the tap targets, no outlines.
        Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.14f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = 36.dp)
                        .clickable(enabled = value > 0, role = Role.Button) { onChange(value - 1) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "−",
                        color = color.copy(alpha = if (value > 0) 1f else 0.3f),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Text(
                    text = value.toString(),
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(min = 24.dp)
                )
                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = 36.dp)
                        .clickable(role = Role.Button) { onChange(value + 1) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("+", color = color, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
