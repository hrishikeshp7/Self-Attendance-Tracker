package com.attendance.tracker.update

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun UpdateSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var update by remember { mutableStateOf<UpdateInfo?>(null) }

    fun run(block: suspend () -> Unit) {
        scope.launch {
            busy = true
            try { block() } catch (e: Exception) {
                status = "Failed: ${e.message ?: e.javaClass.simpleName}"
            } finally { busy = false; progress = null }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val info = update
        if (info == null) {
            OutlinedButton(
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    status = "Checking…"
                    run {
                        update = checkForUpdate(context)
                        status = if (update == null) "You're on the latest version." else ""
                    }
                }
            ) { Text("Check for updates") }
        } else {
            Text("Update available: v${info.version}", style = MaterialTheme.typography.titleMedium)
            Button(
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    status = "Downloading…"
                    run {
                        downloadAndInstall(context, info) { progress = it }
                        status = "Opening installer…"
                    }
                }
            ) { Text("Download & install") }
        }
        progress?.let { LinearProgressIndicator(progress = it, modifier = Modifier.fillMaxWidth()) }
        if (status.isNotEmpty()) {
            Text(status, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
    }
}
