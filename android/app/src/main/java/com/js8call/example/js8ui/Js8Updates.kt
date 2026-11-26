package com.js8call.example.js8ui

import android.app.Activity
import android.app.AlertDialog
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.util.Linkify
import android.util.Log
import android.widget.TextView
import androidx.preference.PreferenceManager
import com.js8call.example.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * JS8Call 3.0.3 JS8_Mainwindow/checkVersion.cpp, for Easy JS8's own GitHub releases: reads
 * version.txt from the latest release. A failed read says nothing, as on the PC.
 */
object Js8Updates {
    private const val TAG = "Js8Updates"
    private const val RELEASES_URL = "https://github.com/RobBobbins/Easy-JS8/releases"
    private const val VERSION_URL = "$RELEASES_URL/latest/download/version.txt"
    private const val CLOSE_AFTER_MS = 60_000L

    fun check(activity: Activity, alertOnUpToDate: Boolean) {
        // Installed from Google Play: Play does the updating. Play policy allows no updates from elsewhere, and
        // Google re-signs Play copies, so a GitHub APK can't install over them (Rob, 2026-11-25)
        if (fromPlay(activity)) {
            Log.d(TAG, "Checking for Updates skipped: installed from Google Play")
            if (alertOnUpToDate) show(activity, "Updates", "Google Play updates Easy JS8.")
            return
        }
        // debug_update_url: debug builds test against a local server
        val url = PreferenceManager.getDefaultSharedPreferences(activity).getString("debug_update_url", null)
            ?.takeIf { BuildConfig.DEBUG } ?: VERSION_URL
        thread(name = "Js8Updates") {
            val content = try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                try {
                    connection.inputStream.bufferedReader().readText().trim()
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                Log.d(TAG, "Checking for Updates Error: ${e.message}")
                return@thread
            }
            Handler(Looper.getMainLooper()).post {
                if (activity.isFinishing || activity.isDestroyed) return@post
                val current = BuildConfig.VERSION_NAME
                if (isNewer(content, current)) {
                    show(activity, "New Updates Available",
                        "A new version ($content) of Easy JS8 is now available. Please see the GitHub Releases for more details:\n$RELEASES_URL")
                } else if (alertOnUpToDate) {
                    show(activity, "No Updates Available", "Your version ($current) of Easy JS8 is up-to-date.")
                }
            }
        }
    }

    /** True when the Google Play Store installed this copy (an internal or closed test too). */
    private fun fromPlay(activity: Activity): Boolean = try {
        val pm = activity.packageManager
        val installer = if (Build.VERSION.SDK_INT >= 30) {
            pm.getInstallSourceInfo(activity.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION") pm.getInstallerPackageName(activity.packageName)
        }
        installer == "com.android.vending"
    } catch (e: Exception) {
        false
    }

    /** QVersionNumber: compared number by number, the leading numbers of each. */
    internal fun isNewer(network: String, current: String): Boolean {
        fun numbers(v: String) = Regex("^\\d+(\\.\\d+)*").find(v)?.value?.split(".")?.map { it.toIntOrNull() ?: 0 }.orEmpty()
        val n = numbers(network)
        val c = numbers(current)
        for (i in 0 until maxOf(n.size, c.size)) {
            val a = n.getOrElse(i) { 0 }
            val b = c.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    /** SelfDestructMessageBox: closes itself after 60 seconds. */
    private fun show(activity: Activity, title: String, text: String) {
        val dialog = AlertDialog.Builder(activity).setTitle(title).setMessage(text)
            .setPositiveButton(android.R.string.ok, null).show()
        dialog.findViewById<TextView>(android.R.id.message)?.let { Linkify.addLinks(it, Linkify.WEB_URLS) }
        Handler(Looper.getMainLooper()).postDelayed({
            if (dialog.isShowing && !activity.isFinishing && !activity.isDestroyed) dialog.dismiss()
        }, CLOSE_AFTER_MS)
    }
}
