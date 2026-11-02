package com.js8call.example

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors

class MessageLogWriter(private val context: Context) {

    private val resolver = context.applicationContext.contentResolver
    private val executor = Executors.newSingleThreadExecutor()
    private val dayFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    /** Day of the last line; the PC's m_RxLog writes a date and frequency line when the day turns. */
    private var lastDay = ""
    private var lastDial = 0L

    private val directedTimeFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    @Volatile
    private var enabled = false
    private var logTarget: LogTarget? = null

    fun setEnabled(isEnabled: Boolean) {
        enabled = isEnabled
    }

    fun shutdown() {
        enabled = false
        executor.shutdown()
    }

    /** Erase ALL.TXT. The next line starts a new file. */
    fun erase() {
        executor.execute {
            logTarget = null
            for (name in listOf(LOG_NAME)) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        findMediaStoreEntry(name)?.let { resolver.delete(it, null, null) }
                    } else {
                        File(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), LOG_DIR), name).delete()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed erasing $name", e)
                }
            }
            Log.i(TAG, "Log erased")
        }
    }

    /**
     * PC writeAllTxt: "yyyy-MM-dd hh:mm:ss snr dt offset mode  frame         bits   message", UTC.
     * The engine hands the app decoded text, not the 12-character frame, so the frame column holds
     * its first 12 characters.
     */
    fun logRx(utcMs: Long, dialHz: Long, text: String, snr: Int, dt: Float, offset: Int, modeChar: String, bits: Int) {
        if (!enabled) return
        val line = String.format(Locale.US, "%s %3d %4.1f %4d %s  %-12s         %d   %s",
            directedTimeFormatter.format(Date(utcMs)), snr, dt, offset, modeChar, text.take(12), bits, text)
        enqueue(line, utcMs, dialHz)
    }

    /** PC write_transmit_entry: the start of the period, "Transmitting x MHz  JS8:  message". */
    fun logTx(utcMs: Long, dialHz: Long, periodSec: Int, text: String) {
        if (!enabled) return
        val start = utcMs - (utcMs / 1000 % periodSec) * 1000
        enqueue(directedTimeFormatter.format(Date(start)) + "  Transmitting ${mhz(dialHz)} MHz  JS8:  ${sanitizeText(text)}", utcMs, dialHz)
    }

    /** PC write_frequency_entry: a new dial frequency, below 30 MHz only. */
    fun logFrequency(utcMs: Long, dialHz: Long) {
        if (!enabled || dialHz >= 30_000_000L || dialHz == lastDial) return
        lastDial = dialHz
        lastDay = dayFormatter.format(Date(utcMs))
        enqueue(directedTimeFormatter.format(Date(utcMs)) + "  ${mhz(dialHz)} MHz  JS8", utcMs, dialHz)
    }

    /** qSetRealNumberPrecision(12): no trailing zeros. */
    private fun mhz(dialHz: Long) = java.math.BigDecimal(dialHz).movePointLeft(6).stripTrailingZeros().toPlainString()

    /** PC writeMsgTxt: DIRECTED.TXT, UTC date, dial MHz, offset, SNR and text, tab separated. */
    fun logDirected(dialHz: Long, offset: Int, snr: String, text: String) {
        if (!enabled) return
        val line = directedTimeFormatter.format(Date()) + "\t" + String.format(Locale.US, "%.6f", dialHz / 1_000_000.0) +
            "\t$offset\t$snr\t$text\n"
        executor.execute {
            try {
                val bytes = line.toByteArray(Charsets.UTF_8)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val uri = findMediaStoreEntry(DIRECTED_NAME) ?: createMediaStoreEntry(DIRECTED_NAME)
                    uri?.let { writeToMediaStore(it, bytes) }
                } else {
                    (ensureLegacyFileTarget() as? LogTarget.FileTarget)?.let { writeToFile(File(it.file.parentFile, DIRECTED_NAME), bytes) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed writing DIRECTED.TXT", e)
            }
        }
    }

    private fun enqueue(line: String, utcMs: Long, dialHz: Long) {
        if (!enabled) return
        val day = dayFormatter.format(Date(utcMs))
        val header = if (day != lastDay) directedTimeFormatter.format(Date(utcMs)) + "  ${mhz(dialHz)} MHz  JS8" else null
        lastDay = day
        executor.execute {
            try {
                header?.let { appendLine(it) }
                appendLine(line)
            } catch (e: Exception) {
                Log.w(TAG, "Failed writing log line", e)
            }
        }
    }

    private fun appendLine(line: String) {
        if (!enabled) return
        val bytes = (line + "\n").toByteArray(Charsets.UTF_8)
        val target = ensureLogTarget() ?: return
        when (target) {
            is LogTarget.MediaStoreTarget -> writeToMediaStore(target.uri, bytes)
            is LogTarget.FileTarget -> writeToFile(target.file, bytes)
        }
    }

    private fun ensureLogTarget(): LogTarget? {
        val current = logTarget
        if (current != null) return current
        val created = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ensureMediaStoreTarget()
        } else {
            ensureLegacyFileTarget()
        }
        if (created != null) {
            logTarget = created
        }
        return created
    }

    private fun ensureMediaStoreTarget(): LogTarget? {
        val uri = findMediaStoreEntry(LOG_NAME) ?: createMediaStoreEntry(LOG_NAME)
        return uri?.let { LogTarget.MediaStoreTarget(it) }
    }

    private fun ensureLegacyFileTarget(): LogTarget? {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            Log.w(TAG, "Storage permission missing; cannot write log")
            return null
        }
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), LOG_DIR)
        if (!dir.exists() && !dir.mkdirs()) {
            Log.w(TAG, "Failed to create log directory: ${dir.absolutePath}")
            return null
        }
        val file = File(dir, LOG_NAME)
        return LogTarget.FileTarget(file)
    }

    private fun writeToMediaStore(uri: Uri, bytes: ByteArray) {
        val stream = resolver.openOutputStream(uri, "wa") ?: resolver.openOutputStream(uri)
        if (stream == null) {
            Log.w(TAG, "Failed opening log stream")
            return
        }
        stream.use { it.write(bytes) }
    }

    private fun writeToFile(file: File, bytes: ByteArray) {
        FileOutputStream(file, true).use { it.write(bytes) }
    }

    private fun findMediaStoreEntry(displayName: String): Uri? {
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?"
        val args = arrayOf(displayName, RELATIVE_PATH)
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.MediaColumns._ID),
            selection,
            args,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(0)
                return Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id.toString())
            }
        }
        return null
    }

    private fun createMediaStoreEntry(displayName: String): Uri? {
        return try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_PATH)
            }
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        } catch (e: Exception) {
            Log.w(TAG, "Failed creating log entry", e)
            null
        }
    }

    private fun sanitizeText(text: String): String {
        val normalized = text.replace(Regex("\\s+"), " ").trim()
        return normalized.replace("\"", "\\\"")
    }

    private sealed class LogTarget {
        data class MediaStoreTarget(val uri: Uri) : LogTarget()
        data class FileTarget(val file: File) : LogTarget()
    }

    private companion object {
        private const val TAG = "MessageLogWriter"
        private const val LOG_DIR = "JS8CallLogs"
        private const val LOG_NAME = "ALL.TXT"
        private const val DIRECTED_NAME = "DIRECTED.TXT"
        private val RELATIVE_PATH = Environment.DIRECTORY_DOWNLOADS + "/JS8CallLogs/"
    }
}
