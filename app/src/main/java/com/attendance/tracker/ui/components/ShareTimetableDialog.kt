package com.attendance.tracker.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.attendance.tracker.data.model.ScheduleEntry
import com.attendance.tracker.data.model.Subject
import com.attendance.tracker.share.TimetableShareManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareTimetableDialog(
    scheduleEntries: List<ScheduleEntry>,
    subjectsMap: Map<Long, Subject>,
    onDismiss: () -> Unit,
    onOpenImportManual: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var batchTitle by remember { mutableStateOf("Class Timetable") }

    val sharedTimetable = remember(batchTitle, scheduleEntries, subjectsMap) {
        TimetableShareManager.createSharedTimetable(batchTitle, scheduleEntries, subjectsMap)
    }

    val deepLink = remember(sharedTimetable) {
        TimetableShareManager.buildDeepLink(sharedTimetable)
    }

    val qrBitmap = remember(deepLink) {
        try {
            TimetableShareManager.generateQrBitmap(deepLink, sizePx = 540)
        } catch (_: Exception) {
            null
        }
    }

    val saveQrLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        if (uri != null && qrBitmap != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val card = TimetableShareManager.createQrCardBitmap(sharedTimetable, qrBitmap)
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        card.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
                    }
                    launch(Dispatchers.Main) {
                        Toast.makeText(context, "QR Card saved to storage!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    launch(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
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
                Text("Share Timetable QR", fontWeight = FontWeight.Bold)
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
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutlinedTextField(
                    value = batchTitle,
                    onValueChange = { batchTitle = it },
                    label = { Text("Batch / Section Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "${sharedTimetable.subjects.size} subjects · ${sharedTimetable.lectures.size} weekly lectures",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (qrBitmap != null) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        modifier = Modifier
                            .size(220.dp)
                            .padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "Timetable QR Code",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp)
                            )
                        }
                    }
                    Text(
                        text = "Share this QR image to WhatsApp or class groups. Anyone can scan or tap to import in 1 tap.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 1. Primary Action: Share QR Image Card (with deep link in caption)
                Button(
                    onClick = {
                        qrBitmap?.let { bitmap ->
                            TimetableShareManager.shareQrImage(context, sharedTimetable, bitmap)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share QR Code (Image + Link)")
                }

                // 2. Secondary Actions: Save QR Image or Copy Link
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val safeName = batchTitle.trim().replace(Regex("[^a-zA-Z0-9_]"), "_")
                            saveQrLauncher.launch("${safeName}_timetable_qr.png")
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save QR")
                    }

                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Timetable Link", deepLink)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Link copied to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy Link")
                    }
                }

                Divider()

                TextButton(
                    onClick = {
                        onDismiss()
                        onOpenImportManual()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Have a friend's link? Import here")
                }
            }
        },
        confirmButton = {}
    )
}
