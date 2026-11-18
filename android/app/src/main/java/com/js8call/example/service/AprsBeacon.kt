package com.js8call.example.service

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.js8call.example.js8ui.Js8Grid
import kotlin.math.abs

/**
 * APRS beacon (Easy JS8 only, Rob 2026-11-17; the PC has none): "@APRSIS GRID <grid>" sent over the air on a
 * schedule, Station > General > APRS beacon. Every beacon carries a new GPS fix, 10-character grid; the fix
 * also updates My Maidenhead Grid Locator.
 *
 * Fixed interval: the fix starts before beacon time by as long as the last fix took. At beacon time the
 * beacon waits up to 30 seconds for the fix; no new fix = that beacon is skipped.
 *
 * SmartBeaconing (HamHUD / Direwolf rules, speeds in km/h): the GPS runs the whole time. Beacon rate scales
 * with speed between the stopped rate and the highway rate; a turn of more than
 * turn angle + turn slope / speed in mph sends one too, no sooner than the least time between turn beacons.
 */
class AprsBeacon(private val context: Context, private val send: (grid: String) -> Unit) {

    companion object {
        private const val TAG = "AprsBeacon"
        const val PREF_BEACON = "js8cfg_aprs_beacon"           // "0" off, minutes, or "smart"
        const val PREF_FAST_SPEED = "js8cfg_sb_fast_speed"     // km/h
        const val PREF_FAST_RATE = "js8cfg_sb_fast_rate"       // seconds
        const val PREF_SLOW_SPEED = "js8cfg_sb_slow_speed"     // km/h
        const val PREF_SLOW_RATE = "js8cfg_sb_slow_rate"       // seconds
        const val PREF_TURN_ANGLE = "js8cfg_sb_turn_angle"     // degrees
        const val PREF_TURN_SLOPE = "js8cfg_sb_turn_slope"     // degrees x mph
        const val PREF_TURN_TIME = "js8cfg_sb_turn_time"       // seconds
        const val SMART = "smart"
        val KEYS = setOf(PREF_BEACON, PREF_FAST_SPEED, PREF_FAST_RATE, PREF_SLOW_SPEED, PREF_SLOW_RATE,
            PREF_TURN_ANGLE, PREF_TURN_SLOPE, PREF_TURN_TIME)

        val BEACON_CHOICES = listOf("Off" to "0", "2 minutes" to "2", "5 minutes" to "5", "10 minutes" to "10",
            "15 minutes" to "15", "30 minutes" to "30", "1 hour" to "60", "SmartBeaconing" to SMART)
        val FAST_RATE_CHOICES = listOf("1 minute" to "60", "2 minutes" to "120", "5 minutes" to "300",
            "10 minutes" to "600", "15 minutes" to "900", "30 minutes" to "1800")
        val SLOW_RATE_CHOICES = listOf("5 minutes" to "300", "10 minutes" to "600", "15 minutes" to "900",
            "30 minutes" to "1800", "1 hour" to "3600")
        val TURN_TIME_CHOICES = listOf("30 seconds" to "30", "1 minute" to "60", "2 minutes" to "120",
            "5 minutes" to "300")

        /** The beacon waits this long past beacon time for its GPS fix, then is skipped. */
        private const val FIX_WAIT_MS = 30_000L
        private const val KMH_PER_MPH = 1.609344

        // ---- Status window (Station > APRS Beacon, Rob 2026-11-17) ------------------------------
        private const val HISTORY_MAX = 200
        private val history = ArrayDeque<String>()
        private val utc = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        private fun now() = synchronized(utc) { utc.format(java.util.Date()) }

        /** One line in the status window, with its UTC time; the last 200 are kept while the app runs. */
        fun note(text: String) {
            Log.i(TAG, text)
            val line = "${now()}  $text"
            synchronized(history) {
                history.addLast(line)
                while (history.size > HISTORY_MAX) history.removeFirst()
            }
        }

        fun lines(): List<String> = synchronized(history) { history.toList() }

        // What the beacon is doing now, for the window's last line (counts down live)
        @Volatile private var liveOn = false
        @Volatile private var liveNoPermission = false
        @Volatile private var liveSmart = false
        @Volatile private var liveNextAt = 0L     // elapsedRealtime: next beacon (fixed) or next by rate (smart)
        @Volatile private var liveLeadMs = 0L
        @Volatile private var liveFixing = false
        @Volatile private var liveWaitUntil = 0L  // elapsedRealtime: end of the 30 s wait for the fix
        @Volatile private var liveMoving = ""     // SmartBeaconing: "42 km/h, heading 270°"
        /** Set by the engine service while a beacon waits for the transmitter; null = not waiting. */
        @Volatile var liveTxWait: String? = null

        private fun mmss(ms: Long): String {
            val s = ((ms + 999) / 1000).coerceAtLeast(0)
            return "%d:%02d".format(s / 60, s % 60)
        }

        /** The window's live last line, with the current UTC time. */
        fun liveLine(): String {
            val t = SystemClock.elapsedRealtime()
            val text = when {
                liveNoPermission -> "No location permission: beacon not started"
                !liveOn -> "APRS beacon off"
                liveTxWait != null -> "Waiting for the transmitter: $liveTxWait"
                liveWaitUntil > t -> "Waiting for GPS fix: ${(liveWaitUntil - t + 999) / 1000} s left"
                liveSmart && liveMoving.isEmpty() -> "SmartBeaconing: waiting for the first GPS fix"
                liveSmart -> "SmartBeaconing: $liveMoving, next by rate in ${mmss(liveNextAt - t)}"
                liveFixing -> "Getting GPS fix… beacon in ${mmss(liveNextAt - t)}"
                else -> "Next beacon in ${mmss(liveNextAt - t)} (GPS fix starts ${mmss(liveLeadMs)} before)"
            }
            return "${now()}  $text"
        }

        fun hasPermission(context: Context) =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        /** Beacon on and location allowed: the engine's notification service also asks for location. */
        fun wanted(context: Context) =
            PreferenceManager.getDefaultSharedPreferences(context).getString(PREF_BEACON, "0") != "0" && hasPermission(context)
    }

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val handler = Handler(Looper.getMainLooper())
    private val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var listener: LocationListener? = null

    // Fixed interval
    private var intervalMs = 0L
    private var nextBeaconAt = 0L      // elapsedRealtime
    private var lastFixMs = 0L         // how long the last fix took
    private var fixRequestedAt = 0L
    private var fix: Location? = null  // the fix for the coming beacon
    private var waiting = false

    // SmartBeaconing
    private var lastBeaconAt = 0L
    private var lastCourse: Float? = null
    private var prevFix: Location? = null

    private fun num(key: String, default: Double) = prefs.all[key]?.toString()?.toDoubleOrNull() ?: default

    /** Starts the beacon for the current settings (Off = stopped). */
    fun restart() {
        val wasOn = liveOn
        stop()
        val mode = prefs.getString(PREF_BEACON, "0") ?: "0"
        if (mode == "0") { if (wasOn) note("APRS beacon off"); return }
        if (!hasPermission(context)) {
            liveNoPermission = true
            note("No location permission: APRS beacon not started")
            return
        }
        liveOn = true
        if (mode == SMART) {
            liveSmart = true
            listen(1000L) { onSmartFix(it) }
            note("SmartBeaconing started")
        } else {
            intervalMs = (mode.toLongOrNull() ?: return) * 60_000L
            nextBeaconAt = SystemClock.elapsedRealtime() // first beacon when turned on
            note("Beacon every ${if (intervalMs >= 3_600_000L) "hour" else "${intervalMs / 60_000} minutes"} started")
            scheduleCycle()
        }
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        unlisten()
        fix = null; waiting = false; prevFix = null; lastCourse = null; lastBeaconAt = 0L
        liveOn = false; liveNoPermission = false; liveSmart = false; liveFixing = false; liveWaitUntil = 0L; liveMoving = ""
    }

    @SuppressLint("MissingPermission")
    private fun listen(everyMs: Long, onFix: (Location) -> Unit) {
        unlisten()
        val l = object : LocationListener {
            override fun onLocationChanged(location: Location) = onFix(location)
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }
        listener = l
        runCatching { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, everyMs, 0f, l, Looper.getMainLooper()) }
            .onFailure { note("GPS not available: ${it.message}") }
    }

    private fun unlisten() {
        listener?.let { runCatching { lm.removeUpdates(it) } }
        listener = null
    }

    private fun gridOf(location: Location): String {
        val grid = Js8Grid.fromLatLon(location.latitude, location.longitude, 10)
        if (prefs.getString("grid", "") != grid) prefs.edit().putString("grid", grid).apply() // My Maidenhead Grid Locator
        return grid
    }

    // ---- Fixed interval --------------------------------------------------------------------

    private val startFix = Runnable {
        fix = null
        fixRequestedAt = SystemClock.elapsedRealtime()
        liveFixing = true
        val before = (nextBeaconAt - fixRequestedAt) / 1000
        note(if (before > 0) "GPS fix started ($before s before beacon time)" else "GPS fix started")
        listen(0L) { location ->
            if (fix != null) return@listen
            lastFixMs = SystemClock.elapsedRealtime() - fixRequestedAt
            fix = location
            liveFixing = false
            unlisten()
            note("GPS fix in ${(lastFixMs + 500) / 1000} s: ${gridOf(location)}")
            if (waiting) sendFixed()
        }
    }

    private val beaconDue = Runnable {
        if (fix != null) sendFixed()
        else {
            waiting = true
            liveWaitUntil = SystemClock.elapsedRealtime() + FIX_WAIT_MS
            note("Beacon time: waiting up to 30 seconds for the GPS fix")
            handler.postDelayed(skip, FIX_WAIT_MS)
        }
    }

    private val skip = Runnable {
        note("Beacon skipped: no new GPS fix")
        finishCycle()
    }

    /** Fix start, then beacon time, for the next beacon. */
    private fun scheduleCycle() {
        val now = SystemClock.elapsedRealtime()
        val lead = lastFixMs.coerceAtMost(intervalMs - 1000L)
        handler.postAtTime(startFix, (nextBeaconAt - lead).coerceAtLeast(now))
        handler.postAtTime(beaconDue, nextBeaconAt.coerceAtLeast(now))
        liveNextAt = nextBeaconAt; liveLeadMs = lead
        Log.i(TAG, "Next beacon in ${(nextBeaconAt - now) / 1000} s, GPS fix starts ${lead / 1000} s before")
    }

    private fun sendFixed() {
        val f = fix ?: return
        val grid = gridOf(f)
        note("Beacon: @APRSIS GRID $grid")
        send(grid)
        finishCycle()
    }

    private fun finishCycle() {
        handler.removeCallbacks(skip)
        handler.removeCallbacks(startFix)
        handler.removeCallbacks(beaconDue)
        unlisten()
        waiting = false
        fix = null
        liveFixing = false; liveWaitUntil = 0L
        val now = SystemClock.elapsedRealtime()
        do nextBeaconAt += intervalMs while (nextBeaconAt <= now)
        scheduleCycle()
    }

    // ---- SmartBeaconing --------------------------------------------------------------------

    private fun onSmartFix(location: Location) {
        val now = SystemClock.elapsedRealtime()
        val prev = prevFix
        // Speed and course from the GPS, or from the last fix when the GPS gives none
        val kmh = when {
            location.hasSpeed() -> location.speed * 3.6
            prev != null && location.time > prev.time -> prev.distanceTo(location) / ((location.time - prev.time) / 1000.0) * 3.6
            else -> 0.0
        }
        val course: Float? = when {
            location.hasBearing() && kmh > 0.5 -> location.bearing
            prev != null && prev.distanceTo(location) > 5f -> (prev.bearingTo(location) + 360f) % 360f
            else -> null
        }
        prevFix = location
        val grid = gridOf(location)
        val fastSpeed = num(PREF_FAST_SPEED, 100.0); val slowSpeed = num(PREF_SLOW_SPEED, 5.0)
        val fastRate = num(PREF_FAST_RATE, 300.0); val slowRate = num(PREF_SLOW_RATE, 1800.0)
        val rate = when {
            kmh <= slowSpeed -> slowRate
            kmh >= fastSpeed -> fastRate
            else -> (fastRate * fastSpeed / kmh).coerceAtMost(slowRate)
        }
        liveMoving = "${kmh.toInt()} km/h" + (course?.let { ", heading ${it.toInt()}°" } ?: "")

        if (lastBeaconAt == 0L) {
            smartSend(grid, now, course, "first GPS fix")
            liveNextAt = now + (rate * 1000).toLong()
            return
        }
        liveNextAt = lastBeaconAt + (rate * 1000).toLong()
        val elapsed = (now - lastBeaconAt) / 1000.0
        if (elapsed >= rate) {
            smartSend(grid, now, course, "rate ${rate.toInt()} s at ${kmh.toInt()} km/h")
            liveNextAt = now + (rate * 1000).toLong()
            return
        }
        // Corner pegging
        val last = lastCourse
        if (course != null && last != null && kmh > slowSpeed) {
            var turn = abs(course - last) % 360f
            if (turn > 180f) turn = 360f - turn
            val threshold = num(PREF_TURN_ANGLE, 30.0) + num(PREF_TURN_SLOPE, 255.0) / (kmh / KMH_PER_MPH)
            if (turn > threshold && elapsed >= num(PREF_TURN_TIME, 120.0)) {
                smartSend(grid, now, course, "turn ${turn.toInt()}° > ${threshold.toInt()}° at ${kmh.toInt()} km/h")
                liveNextAt = now + (rate * 1000).toLong()
            }
        }
        if (lastCourse == null && course != null) lastCourse = course
    }

    private fun smartSend(grid: String, now: Long, course: Float?, why: String) {
        lastBeaconAt = now
        if (course != null) lastCourse = course
        note("Beacon ($why): @APRSIS GRID $grid")
        send(grid)
    }
}
