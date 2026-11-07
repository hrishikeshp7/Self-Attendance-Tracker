package com.attendance.tracker.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.attendance.tracker.share.SharedTimetable
import com.attendance.tracker.share.TimetableShareManager
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportTimetableDialog(
    initialTimetable: SharedTimetable?,
    onDismiss: () -> Unit,
    onConfirmImport: (timetable: SharedTimetable, replaceSchedule: Boolean) -> Unit
) {
    val context = LocalContext.current
    var inputCode by remember { mutableStateOf("") }
    var activeTimetable by remember(initialTimetable) { mutableStateOf(initialTimetable) }
    var replaceSchedule by remember { mutableStateOf(false) }
    var parseError by remember { mutableStateOf<String?>(null) }

    fun tryParseCode() {
        parseError = null
        val parsed = TimetableShareManager.decode(inputCode.trim())
        if (parsed != null) {
            activeTimetable = parsed
        } else {
            parseError = "Invalid timetable link or code. Please check and try again."
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.School,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (activeTimetable != null) "Import Timetable" else "Enter Shared Link",
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val current = activeTimetable
                if (current == null) {
                    Text(
                        text = "Paste the timetable link or code shared by your classmate or Class Rep.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = inputCode,
                        onValueChange = {
                            inputCode = it
                            parseError = null
                        },
                        label = { Text("Paste link or code") },
                        placeholder = { Text("attendance://import?tt=...") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                    parseError?.let {
                        Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        onClick = { tryParseCode() },
                        enabled = inputCode.isNotBlank(),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Preview Timetable")
                    }
                } else {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = current.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${current.subjects.size} subjects · ${current.lectures.size} weekly lectures",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }

                    Text("Subjects in this batch:", style = MaterialTheme.typography.labelLarge)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        current.subjects.forEach { sub ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = sub.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${sub.requiredAttendance}% req.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Text("Import options:", style = MaterialTheme.typography.labelLarge)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = !replaceSchedule,
                            onClick = { replaceSchedule = false }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text("Merge with my schedule", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Keeps existing subjects & attendance; adds new ones",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = replaceSchedule,
                            onClick = { replaceSchedule = true }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text("Replace my schedule", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Replaces all existing schedule entries with this timetable",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            val current = activeTimetable
            if (current != null) {
                Button(
                    onClick = {
                        onConfirmImport(current, replaceSchedule)
                    }
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import Timetable")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
