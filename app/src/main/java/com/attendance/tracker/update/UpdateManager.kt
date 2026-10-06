package com.attendance.tracker.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** A GitHub release newer than the installed app. */
data class UpdateInfo(val version: String, val apkUrl: String, val notes: String)

private const val LATEST_URL =
    "https://api.github.com/repos/hrishikeshp7/Self-Attendance-Tracker/releases/latest"

fun currentVersionName(context: Context): String =
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"

/** "v1.2.28-20261006_124010" or "1.2.28" -> [1, 2, 28]. */
internal fun parseVersion(tag: String): List<Int> =
    tag.removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }

internal fun isNewer(remote: List<Int>, local: List<Int>): Boolean {
    for (i in 0 until maxOf(remote.size, local.size)) {
        val r = remote.getOrElse(i) { 0 }
        val l = local.getOrElse(i) { 0 }
        if (r != l) return r > l
    }
    return false
}

/** Removes any downloaded update APK; run after the app is replaced so it never lingers. */
fun clearUpdateCache(context: Context) {
    File(context.cacheDir, "updates").deleteRecursively()
}

/** Returns the latest release if newer than the installed version, else null. */
suspend fun checkForUpdate(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
    val conn = URL(LATEST_URL).openConnection() as HttpURLConnection
    try {
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        val json = JSONObject(conn.inputStream.bufferedReader().readText())
        val tag = json.getString("tag_name")
        if (!isNewer(parseVersion(tag), parseVersion(currentVersionName(context)))) return@withContext null
        val assets = json.getJSONArray("assets")
        val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { it.getString("name").endsWith(".apk") }
            ?: return@withContext null
        UpdateInfo(
            version = tag.removePrefix("v").substringBefore('-'),
            apkUrl = apk.getString("browser_download_url"),
            notes = json.optString("body")
        )
    } finally {
        conn.disconnect()
    }
}

/** Downloads the APK, reporting 0..1 progress, then opens the system installer. */
suspend fun downloadAndInstall(context: Context, info: UpdateInfo, onProgress: (Float) -> Unit) {
    val file = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
        val out = File(dir, "update-${info.version}.apk")
        val conn = URL(info.apkUrl).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(32 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress(done.toFloat() / total)
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        out
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    context.startActivity(
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
