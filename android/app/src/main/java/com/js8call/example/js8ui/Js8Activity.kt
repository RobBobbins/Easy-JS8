package com.js8call.example.js8ui

import android.content.SharedPreferences
import com.js8call.example.model.DecodedMessage
import com.js8call.example.util.Js8Commands
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Port of the JS8Call 3.0.3 desktop activity logic:
 *  - Band Activity   (JS8_Mainwindow/processDecodeEvent.cpp, displayBandActivity.cpp)
 *  - Call Activity   (UI_Constructor::logCallActivity, processCommandActivity.cpp, displayCallActivity.cpp)
 *  - Message Window  (processRxActivity.cpp, displayTextForFreq / writeMessageTextToUI)
 *
 * Works on the decoded text JS8Android's engine delivers (one entry per frame, with the
 * First/Last/Data bits), so it follows the desktop rules at the text level.
 */
class Js8Activity {

    data class ActivityDetail(
        val offset: Int,
        val text: String,
        val snr: Int,
        val utcMs: Long,
        val tdrift: Float,
        val submode: Int,
        val bits: Int,
        val isDirected: Boolean,
        val isDataFrame: Boolean
    )

    data class CallDetail(
        val call: String,
        var grid: String = "",
        var snr: Int = -99,
        var offset: Int = 0,
        var utcMs: Long = 0,
        var tdrift: Float = 0f,
        var submode: Int = 0,
        var cqMs: Long = 0,
        var ackMs: Long = 0
    )

    /** One line of the Message Window. */
    class RxLine(val utcMs: Long, val offset: Int, val isTx: Boolean, val text: StringBuilder, var closed: Boolean) {
        var lastWasData = false
    }

    data class Directed(val from: String, val to: String, val cmd: String, val extra: String)

    val bandActivity = linkedMapOf<Int, MutableList<ActivityDetail>>()
    val callActivity = linkedMapOf<String, CallDetail>()
    val rxLines = mutableListOf<RxLine>()

    private val dupeCache = mutableMapOf<String, Long>()
    private val openRxLine = mutableMapOf<Int, RxLine>()   // offset bucket -> line still taking frames
    private val bufferedToMe = mutableMapOf<Int, Long>()   // offset -> time a directed header to me/group started

    companion object {
        const val SUBMODE_NORMAL = 0
        const val SUBMODE_FAST = 1
        const val SUBMODE_TURBO = 2
        const val SUBMODE_SLOW = 4
        const val SUBMODE_ULTRA = 8

        /** JS8Submode.cpp rxThreshold. */
        fun rxThreshold(submode: Int) = when (submode) {
            SUBMODE_FAST -> 16
            SUBMODE_TURBO -> 32
            SUBMODE_ULTRA -> 50
            else -> 10
        }

        fun periodSec(submode: Int) = when (submode) {
            SUBMODE_FAST -> 10
            SUBMODE_TURBO -> 6
            SUBMODE_SLOW -> 30
            SUBMODE_ULTRA -> 4
            else -> 15
        }

        /** Mode Speed column: N / F / S, or 40 / 60 (displayBandActivity.cpp). */
        fun submodeLetter(submode: Int) = when (submode) {
            SUBMODE_FAST -> "F"
            SUBMODE_TURBO -> "40"
            SUBMODE_SLOW -> "S"
            SUBMODE_ULTRA -> "60"
            else -> "N"
        }

        /** Speeds the engine can send; anything else goes out as Normal (getPreferredTxSubmode). */
        fun txSubmode(stored: Int) = if (stored in setOf(SUBMODE_NORMAL, SUBMODE_FAST, SUBMODE_TURBO, SUBMODE_SLOW)) stored else SUBMODE_NORMAL

        /** Status bar mode label (updateModeButtonText). */
        fun modeLabel(submode: Int) = when (submode) {
            SUBMODE_FAST -> "JS8 Fast"
            SUBMODE_TURBO -> "JS8 40"
            SUBMODE_SLOW -> "JS8 Slow"
            SUBMODE_ULTRA -> "JS8 60"
            else -> "JS8 Normal"
        }

        /** Cycle length in seconds (JS8A/B/C/E/I_TX_SECONDS; engine framePeriodMs). */
        fun periodSeconds(submode: Int) = when (submode) {
            SUBMODE_FAST -> 10
            SUBMODE_TURBO -> 6
            SUBMODE_SLOW -> 30
            SUBMODE_ULTRA -> 4
            else -> 15
        }

        /** JS8::Submode::bandwidth: 8 tones x 12000 / samples per symbol (JS8Submode.cpp). */
        fun bandwidthHz(submode: Int) = when (submode) {
            SUBMODE_FAST -> 80f
            SUBMODE_TURBO -> 160f
            SUBMODE_SLOW -> 25f
            SUBMODE_ULTRA -> 250f
            else -> 50f
        }

        /** UI_Constructor::since(). */
        fun since(utcMs: Long, now: Long = System.currentTimeMillis()): String {
            val delta = (now - utcMs) / 1000
            return when {
                delta >= 86400 -> "${delta / 86400}d"
                delta >= 3600 -> "${delta / 3600}h"
                delta >= 60 -> "${delta / 60}m"
                delta >= 15 -> "${delta - delta % 15}s"
                else -> "now"
            }
        }

        /** Varicode::formatSNR: +NN / -NN, empty when out of range. */
        fun formatSnr(snr: Int): String {
            if (snr < -60 || snr > 60) return ""
            return if (snr >= 0) String.format("+%02d", snr) else String.format("-%02d", -snr)
        }

        private val callsignRegex = Regex("^(?:[A-Z0-9]{1,4}/)?[A-Z0-9]{1,3}[0-9][A-Z0-9]{0,4}(?:/[A-Z0-9]{1,4})?$")
        private val gridRegex = Regex("^[A-R]{2}[0-9]{2}(?:[A-X]{2}(?:[0-9]{2})?)?$")

        fun isCallsign(t: String) = callsignRegex.matches(t.uppercase()) && t.any { it.isDigit() } && t.any { it.isLetter() }
        fun isGrid(t: String) = gridRegex.matches(t.uppercase())

        /** Base callsign without /P style prefixes or suffixes (Radio::base_callsign). */
        fun baseCall(call: String): String {
            val parts = call.uppercase().split('/')
            return parts.maxByOrNull { p -> if (p.any { it.isDigit() } && p.any { it.isLetter() }) p.length else -1 } ?: call
        }

        /** Grid -> (lat, lon) of the square center. */
        fun gridToLatLon(grid: String): Pair<Double, Double>? {
            val g = grid.trim().uppercase()
            if (g.length < 4 || !isGrid(g.take(if (g.length >= 6) 6 else 4))) return null
            var lon = (g[0] - 'A') * 20.0 - 180.0 + (g[2] - '0') * 2.0
            var lat = (g[1] - 'A') * 10.0 - 90.0 + (g[3] - '0') * 1.0
            if (g.length >= 6) {
                lon += (g[4] - 'A') * (5.0 / 60.0) + 2.5 / 60.0
                lat += (g[5] - 'A') * (2.5 / 60.0) + 1.25 / 60.0
            } else {
                lon += 1.0; lat += 0.5
            }
            return lat to lon
        }

        /** Great-circle distance (km) and bearing (degrees) between two grids, or null. */
        fun vector(from: String, to: String): Pair<Double, Double>? {
            val a = gridToLatLon(from) ?: return null
            val b = gridToLatLon(to) ?: return null
            val r = Math.PI / 180
            val la1 = a.first * r; val la2 = b.first * r
            val dLon = (b.second - a.second) * r
            val dLat = la2 - la1
            val h = sin(dLat / 2) * sin(dLat / 2) + cos(la1) * cos(la2) * sin(dLon / 2) * sin(dLon / 2)
            val km = 6371.0 * 2 * atan2(sqrt(h), sqrt(1 - h))
            val y = sin(dLon) * cos(la2)
            val x = cos(la1) * sin(la2) - sin(la1) * cos(la2) * cos(dLon)
            val az = (Math.toDegrees(atan2(y, x)) + 360) % 360
            return km to az
        }
    }

    // ---- Settings read on each frame ---------------------------------------------------------

    class Config(prefs: SharedPreferences) {
        val myCall = prefs.getString("callsign", "").orEmpty().trim().uppercase()
        val myBase = baseCall(myCall)
        val groups = prefs.getString("my_groups", "").orEmpty().split(',', ' ')
            .map { it.trim().uppercase() }.filter { it.startsWith("@") }.toSet()
        // stored as a boolean by Settings OK; read without assuming the type
        val avoidAllcall = prefs.all["js8cfg_avoid_allcall"]?.toString()?.toBoolean() ?: false
        val eot = prefs.getString("js8cfg_eot", "♢").orEmpty().ifEmpty { "♢" }
        val blocklist = prefs.getString("js8cfg_rx_blocklist", "").orEmpty().split(',', ' ')
            .map { it.trim().uppercase() }.filter { it.isNotEmpty() }.toSet()

        fun isToMe(to: String) = myCall.isNotEmpty() && (to == myCall || to == myBase)
        fun isMyCallIncluded(text: String): Boolean {
            if (myBase.isEmpty() || !text.uppercase().contains(myBase)) return false
            return text.uppercase().split(Regex("[^A-Z0-9/]+")).any { it == myBase || it == myCall }
        }
    }

    // ---- Frames --------------------------------------------------------------------------------

    /** Splits "FROM: TO CMD EXTRA" (the engine's parseDirectedCommand rules). */
    fun parseDirected(text: String): Directed? {
        val t = text.trim()
        val tokens = t.split(Regex("\\s+"))
        if (tokens.size < 2 || !tokens[0].endsWith(":")) return null
        val from = tokens[0].trimEnd(':')
        if (from.isEmpty()) return null
        val toToken = tokens[1]
        if (toToken.endsWith(">")) return Directed(from, toToken.trimEnd('>'), ">", tokens.drop(2).joinToString(" "))
        val remainder = tokens.drop(2).joinToString(" ")
        val match = if (remainder.isNotEmpty()) Js8Commands.matchAt(remainder) else null
        return if (match != null) Directed(from, toToken, match.command, match.payload)
        else if (isCallsign(toToken) || toToken.startsWith("@")) Directed(from, toToken, "", remainder)
        else null
    }

    /**
     * Handles one decoded frame. myOffset is the operator's TX offset (Message Window follows it).
     * Returns true when something visible changed.
     */
    fun onFrame(m: DecodedMessage, myOffset: Int, cfg: Config): Boolean {
        val now = m.timestamp
        val text = m.text
        if (text.isBlank()) return false

        // Duplicate frame within half a period (processDecodeEvent.cpp)
        val dupeKey = "${m.mode}:$text"
        dupeCache[dupeKey]?.let { if (now - it < periodSec(m.mode) * 500L) return false }
        dupeCache[dupeKey] = now
        if (dupeCache.size > 500) dupeCache.entries.removeIf { now - it.value > 120_000 }

        val offset = m.frequency.roundToInt()
        val range = rxThreshold(m.mode)
        val directed = parseDirected(text)
        val isFirst = (m.type and 1) != 0
        val isLast = (m.type and 2) != 0

        // Band Activity: follow a drifting signal to its new offset, keep the last 10 frames
        if (!bandActivity.containsKey(offset)) {
            bandActivity.keys.firstOrNull { abs(it - offset) <= range }?.let { prev ->
                bandActivity.remove(prev)?.let { bandActivity[offset] = it }
            }
        }
        val list = bandActivity.getOrPut(offset) { mutableListOf() }
        list += ActivityDetail(offset, text, m.snr, now, m.dt, m.mode, m.type, directed != null, m.isDataFrame())
        while (list.size > 10) list.removeAt(0)

        // Heartbeat / CQ (compound frames): call activity only
        val upper = text.uppercase()
        val isHeartbeat = upper.contains("@HB HEARTBEAT") || upper.contains(": HB ") || upper.contains("@ALLCALL HB")
        val isCq = directed != null && directed.to == "@ALLCALL" && directed.extra.uppercase().startsWith("CQ") ||
            directed != null && directed.to == "@ALLCALL" && directed.cmd.trim().uppercase() == "CQ"

        if (directed != null && directed.from != "<....>" && directed.to != "<....>") {
            val grid = (directed.extra + " " + text).split(Regex("\\s+")).lastOrNull { isGrid(it) && it.length >= 4 }.orEmpty()
            val toMe = cfg.isToMe(directed.to)
            logCall(CallDetail(
                call = directed.from, grid = grid, snr = m.snr, offset = offset, utcMs = now, tdrift = m.dt,
                submode = m.mode,
                cqMs = if (isCq) now else 0,
                ackMs = if (toMe || upper.contains(": ACK") || directed.cmd.trim() == "ACK") now else 0
            ))
            // GRID command: grids in the text
            if (directed.cmd.trim().uppercase() == "GRID") {
                directed.extra.split(Regex("\\s+")).filter { isGrid(it) }.forEach { g ->
                    callActivity[directed.from]?.grid = g
                }
            }
            val isAllCall = directed.to == "@ALLCALL" || directed.to == "@HB"
            val isGroup = directed.to in cfg.groups
            if (toMe || isGroup) bufferedToMe[offset] = now
            if (blocked(directed.from, cfg)) return true
            // Command display: to me, my groups, or a plain @ALLCALL message (processCommandActivity.cpp)
            val showAllCall = isAllCall && !cfg.avoidAllcall && directed.cmd.isBlank() && !isHeartbeat && !isCq
            if (toMe || isGroup || showAllCall) {
                writeRx(text, offset, now, isTx = false, newLine = true, isLast = isLast, cfg = cfg)
                return true
            }
        } else if (isFirst && !m.isDataFrame()) {
            // First frame "CALL: text" of a standard message: log the callsign (processRxActivity.cpp)
            val call = text.substringBefore(':', "").trim()
            if (call.isNotEmpty() && isCallsign(call)) {
                logCall(CallDetail(call, snr = m.snr, offset = offset, utcMs = now, tdrift = m.dt, submode = m.mode))
            }
        }

        // Message Window: frames near my offset, or continuing a message to me / my group
        if (isHeartbeat || text.contains("<....>")) return true
        val buffered = bufferedToMe.keys.firstOrNull { abs(it - offset) <= range && now - (bufferedToMe[it] ?: 0) < 5 * 60_000 }
        val near = abs(offset - myOffset) <= range
        if (!near && buffered == null) return true
        val sender = text.substringBefore(':', "").trim()
        if (sender.isNotEmpty() && blocked(sender, cfg)) return true
        writeRx(text, offset, now, isTx = false, newLine = isFirst, isLast = isLast, cfg = cfg, isData = m.isDataFrame())
        if (isLast && buffered != null) bufferedToMe.remove(buffered)
        return true
    }

    private fun blocked(call: String, cfg: Config) =
        cfg.blocklist.isNotEmpty() && (call.uppercase() in cfg.blocklist || baseCall(call) in cfg.blocklist)

    /** UI_Constructor::logCallActivity: update keeping grid / ack / cq times. */
    fun logCall(d: CallDetail) {
        if (d.call.isBlank() || d.call.contains(">") || d.call.startsWith("@")) return
        val old = callActivity[d.call]
        if (old != null) {
            if (d.grid.isEmpty()) d.grid = old.grid
            if (d.ackMs == 0L) d.ackMs = old.ackMs
            if (d.cqMs == 0L) d.cqMs = old.cqMs
        }
        callActivity[d.call] = d
    }

    /** displayTextForFreq + writeMessageTextToUI: continue the open line for this offset or start a new one. */
    fun writeRx(text: String, offset: Int, utcMs: Long, isTx: Boolean, newLine: Boolean, isLast: Boolean, cfg: Config,
                isData: Boolean = false) {
        val bucket = offset / 10 * 10
        var line = if (newLine) null else (openRxLine[offset] ?: openRxLine[bucket] ?: openRxLine[bucket + 10])
        if (line != null && line.closed) line = null
        val piece = if (isLast) text.trimEnd() + " " + cfg.eot + " " else text
        if (line == null) {
            // duplicate ACK / HEARTBEAT SNR lines are not repeated
            if ((text.contains(" ACK") || text.contains(" HEARTBEAT SNR ")) &&
                rxLines.any { it.text.toString().trim() == piece.trim() && abs(it.utcMs - utcMs) < 2000 }) return
            line = RxLine(utcMs, offset, isTx, StringBuilder(piece), isLast)
            rxLines += line
            while (rxLines.size > 500) rxLines.removeAt(0)
        } else {
            // data frames carry their own spaces; a header meeting its payload needs one put back
            val needsSpace = !line.lastWasData && line.text.isNotEmpty() && !line.text.last().isWhitespace() &&
                piece.isNotEmpty() && !piece.first().isWhitespace()
            if (needsSpace) line.text.append(' ')
            line.text.append(piece)
            if (isLast) line.closed = true
        }
        line.lastWasData = isData
        openRxLine.remove(offset); openRxLine.remove(bucket); openRxLine.remove(bucket + 10)
        if (isLast) openRxLine.clear() else {
            openRxLine[offset] = line; openRxLine[bucket] = line; openRxLine[bucket + 10] = line
        }
    }

    /** Our own transmission, shown in the Message Window in the TX color. */
    fun writeTx(text: String, offset: Int, cfg: Config) =
        writeRx("${cfg.myCall}: $text", offset, System.currentTimeMillis(), isTx = true, newLine = true, isLast = true, cfg = cfg)

    /** Band Activity row text for one offset, with the hide rules of displayBandActivity.cpp. */
    fun bandRowText(offset: Int, now: Long, agingMin: Int, showHb: Boolean, selected: Boolean, eot: String): Pair<String, ActivityDetail?> {
        val items = bandActivity[offset] ?: return "" to null
        val show = BooleanArray(items.size) { true }
        items.forEachIndexed { i, item ->
            var ok = true
            if (!selected && agingMin > 0 && (now - item.utcMs) / 60_000 >= agingMin) ok = false
            if (!showHb) {
                if (item.text.contains(" @HB ") || item.text.contains(" HEARTBEAT ")) {
                    ok = false
                    if (i > 0 && show[i - 1] && items[i - 1].text.endsWith(": ")) show[i - 1] = false
                }
                if ((i == 0 || !show[i - 1]) && item.text.contains(" MSG ID ")) ok = false
            }
            if (item.text.isEmpty()) ok = false
            show[i] = ok
        }
        val sb = StringBuilder()
        var last: ActivityDetail? = null
        var prev: ActivityDetail? = null
        items.forEachIndexed { i, item ->
            if (!show[i]) return@forEachIndexed
            var t = item.text
            if ((item.bits and 2) != 0) t = t.trimEnd() + " " + eot + " "
            if (prev != null && !prev!!.isDataFrame && sb.isNotEmpty() && !sb.last().isWhitespace() && t.isNotEmpty() && !t.first().isWhitespace()) sb.append(' ')
            sb.append(t)
            last = item; prev = item
        }
        return sb.toString().trimEnd() to last
    }

    /** UI_Constructor::callsignSelected for a Band Activity row: newest callsign heard within the offset range. */
    fun callAtOffset(offset: Int): String? {
        val sub = bandActivity[offset]?.lastOrNull()?.submode ?: SUBMODE_NORMAL
        val th = rxThreshold(sub)
        return callActivity.values.filter { abs(it.offset - offset) <= th }.maxByOrNull { it.utcMs }?.call
    }

    fun clearBand() = bandActivity.clear()
    fun clearCalls() = callActivity.clear()
    fun clearRx() { rxLines.clear(); openRxLine.clear() }
}
