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
    /**
     * The MediaStore numbers of the log files this install made. Android hides a file an uninstalled copy
     * made, so a search by name made a new "ALL (n).TXT" on every start (S20, 2026-11-06). Kept apart from
     * the settings, which a configuration switch clears; the PC keeps one ALL.TXT for every configuration.
     */
    private val fileIds = context.applicationContext.getSharedPreferences("js8_log_files", Context.MODE_PRIVATE)
    private val executor = Executors.newSingleThreadExecutor()
    private val dayFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    /**
     * PC m_RxLog: a date and frequency line goes before the next decode line. Set at start, by Erase ALL.TXT
     * and when the period time goes back (the UTC day turns); cleared at the end of every decode run, so it
     * is written only inside that run (UI_Constructor.cpp 43, mw 2104-2105, 2188, 4227, 7032-7038).
     */
    @Volatile
    private var rxLog = true
    private var nutc0 = 999999
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
            rxLog = true
            logTarget = null
            for (name in listOf(LOG_NAME)) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        findMediaStoreEntry(name)?.let { resolver.delete(it, null, null) }
                        fileIds.edit().remove(name).apply()
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

    /** A decode run starts; [nutc] is its period time, hhmmss (PC decodeStart, mw 2097-2105). */
    fun decodeStarted(nutc: Int) {
        if (nutc < nutc0) rxLog = true
        nutc0 = nutc
    }

    /** PC decodeDone: no date line after this decode run. */
    fun decodeDone() {
        rxLog = false
    }

    /**
     * PC writeAllTxt(date + " " + DecodedText::string() + " " + message): the date now, then the period
     * time [nutc] with no space before the SNR, the 12-character [frame], 4 spaces before the text, UTC
     * (processDecodeEvent.cpp 241-245, DecodedText.cpp 326). [dialHz] is the dial for the date line.
     */
    fun logRx(utcMs: Long, nutc: Int, dialHz: Long, frame: String, text: String, snr: Int, dt: Float, offset: Int, modeChar: String, bits: Int) {
        if (!enabled) return
        val line = String.format(Locale.US, "%s %02d:%02d:%02d%3d %4.1f %4d %s  %s         %d    %s",
            dayFormatter.format(Date(utcMs)), nutc / 10000, nutc % 10000 / 100, nutc % 100, snr, dt, offset, modeChar, frame, bits, text)
        val header = if (rxLog) directedTimeFormatter.format(Date(utcMs)) + "  ${mhz(dialHz)} MHz  JS8" else null
        rxLog = false
        enqueue(line, header)
    }

    /**
     * PC write_transmit_entry: the start of the period, "Transmitting x MHz  JS8:  " and the text of the
     * frame going out, as it is (mw 2667-2670, 6994-7009).
     */
    fun logTx(utcMs: Long, dialHz: Long, periodSec: Int, frameText: String) {
        if (!enabled) return
        val start = utcMs - (utcMs / 1000 % periodSec) * 1000
        enqueue(directedTimeFormatter.format(Date(start)) + "  Transmitting ${mhz(dialHz)} MHz  JS8:  $frameText", null)
    }

    /** PC write_frequency_entry: a new dial frequency, below 30 MHz only. */
    fun logFrequency(utcMs: Long, dialHz: Long) {
        if (!enabled || dialHz >= 30_000_000L || dialHz == lastDial) return
        lastDial = dialHz
        enqueue(directedTimeFormatter.format(Date(utcMs)) + "  ${mhz(dialHz)} MHz  JS8", null)
    }

    /** qSetRealNumberPrecision(12): no trailing zeros. */
    private fun mhz(dialHz: Long) = java.math.BigDecimal(dialHz).movePointLeft(6).stripTrailingZeros().toPlainString()

    /** PC writeMsgTxt: DIRECTED.TXT, the drifted UTC time [utcMs] (mw 7062), dial MHz, offset, SNR and text, tab separated. */
    fun logDirected(utcMs: Long, dialHz: Long, offset: Int, snr: String, text: String) {
        if (!enabled) return
        val line = directedTimeFormatter.format(Date(utcMs)) + "\t" + String.format(Locale.US, "%.6f", dialHz / 1_000_000.0) +
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

    private fun enqueue(line: String, header: String?) {
        if (!enabled) return
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

    /** The file saved under [displayName] while it still exists, else the search by name. */
    private fun findMediaStoreEntry(displayName: String): Uri? {
        val saved = fileIds.getLong(displayName, -1L)
        if (saved >= 0) {
            val uri = Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, saved.toString())
            val exists = try {
                resolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)?.use { it.moveToFirst() } == true
            } catch (e: Exception) {
                false
            }
            if (exists) return uri
            fileIds.edit().remove(displayName).apply()
        }
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
                fileIds.edit().putLong(displayName, id).apply()
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
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)?.also { uri ->
                uri.lastPathSegment?.toLongOrNull()?.let { fileIds.edit().putLong(displayName, it).apply() }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed creating log entry", e)
            null
        }
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
