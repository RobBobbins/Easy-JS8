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
        val isDataFrame: Boolean,
        val lowConfidence: Boolean = false,
        /** Dial at decode, for the API (PC ActivityDetail.dial); 0 = not known. */
        val dial: Long = 0
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
        var ackMs: Long = 0,
        /** PC CallDetail.through: heard only inside a relay through this station. */
        var through: String = ""
    )

    /** One line of the Message Window. */
    class RxLine(val utcMs: Long, val offset: Int, val isTx: Boolean, val text: StringBuilder, var closed: Boolean) {
        var lastWasData = false
        /** Characters of text shown in bold (FT8 transmitting callsign), or null. */
        var bold: IntRange? = null
    }

    data class Directed(val from: String, val to: String, val cmd: String, val extra: String)

    val bandActivity = linkedMapOf<Int, MutableList<ActivityDetail>>()
    val callActivity = linkedMapOf<String, CallDetail>()
    val rxLines = mutableListOf<RxLine>()
    /** PC m_heardGraphOutgoing / m_heardGraphIncoming (logHeardGraph). */
    val hearing = mutableMapOf<String, MutableSet<String>>()
    val heardBy = mutableMapOf<String, MutableSet<String>>()
    /** PC m_rxBlockedOffsets: continuation frames of a blocked station. */
    private val blockedOffsets = mutableSetOf<Int>()
    /** A GRID command or relay still arriving, by offset; their later frames finish it. */
    private class Pending(val from: String, val cmd: String, val text: StringBuilder)
    private val pending = mutableMapOf<Int, Pending>()

    private val dupeCache = mutableMapOf<String, Long>()
    private val openRxLine = mutableMapOf<Int, RxLine>()   // offset bucket -> line still taking frames
    private val bufferedToMe = mutableMapOf<Int, Long>()   // offset -> time a directed header to me/group started

    companion object {
        const val NOTICE_OFFSET = -1
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

        /** ALL.TXT mode letter (DecodedText.cpp submodeChar). */
        fun allTxtLetter(submode: Int) = when (submode) {
            SUBMODE_FAST -> "B"
            SUBMODE_TURBO -> "C"
            SUBMODE_SLOW -> "E"
            SUBMODE_ULTRA -> "I"
            else -> "A"
        }

        /** Speeds the engine can send; anything else goes out as Normal (getPreferredTxSubmode). */
        fun txSubmode(stored: Int) = if (stored in setOf(SUBMODE_NORMAL, SUBMODE_FAST, SUBMODE_TURBO, SUBMODE_SLOW, SUBMODE_ULTRA)) stored else SUBMODE_NORMAL

        /** Mode button text starts with this (JS8::Submode::name). */
        fun modeName(submode: Int) = when (submode) {
            SUBMODE_FAST -> "FAST"
            SUBMODE_TURBO -> "JS8 40"
            SUBMODE_SLOW -> "SLOW"
            SUBMODE_ULTRA -> "JS8 60"
            else -> "NORMAL"
        }

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
        /** JS8::Submode::rxThreshold. */
        fun rxThresholdHz(submode: Int) = when (submode) {
            SUBMODE_FAST -> 16
            SUBMODE_TURBO -> 32
            SUBMODE_ULTRA -> 50
            else -> 10
        }

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

        /** PC gridLat / gridLon: West longitude positive; a missing subsquare counts as MM, a missing extension as 44. */
        private fun coords(grid: String): Pair<Float, Float> {
            fun part(i: Int, at: Int, default: Char) = if (i + at < grid.length) grid[i + at] else default
            fun data(i: Int) = listOf(part(i, 0, 'A') - 'A', part(i, 2, '0') - '0', part(i, 4, 'M') - 'A', part(i, 6, '4') - '0')
            val la = data(1)
            val lo = data(0)
            val lat = (-90 + 10 * la[0]) + la[1] + (2.5f * (la[2] + 0.5f)) / 60f + (15 * (la[3] + 0.5f)) / 3600f
            val lon = (180 - 20 * lo[0]) - (2 * lo[1]) - (5 * (lo[2] + 0.5f)) / 60f - (30 * (lo[3] + 0.5f)) / 3600f
            return lat to lon
        }

        /** PC Geodesic azdist (Thomas 1970, Clarke 1866 ellipsoid): distance in km and azimuth in degrees, or null. */
        fun vector(from: String, to: String): Pair<Double, Double>? {
            gridToLatLon(from) ?: return null
            gridToLatLon(to) ?: return null
            val origin = from.trim().uppercase()
            val remote = to.trim().uppercase()
            if (origin == remote) return 0.0 to 0.0
            val (lat1, lon1) = coords(origin)
            val (lat2, lon2) = coords(remote)
            if (abs(lat1 - lat2) < 0.02f && abs(lon1 - lon2) < 0.02f) return 0.0 to 0.0
            val range = (lon1 - lon2 + 720f) % 360f
            if (abs(lat1 + lat2) < 0.02f && abs(range - 180f) < 0.02f) return 204000.0 to 0.0
            val AL = 6378206.4f; val BL = 6356583.8f
            val D2R = 0.01745329251994f; val TAU = 6.28318530718f
            val BOA = BL / AL; val F = 1f - BOA; val FF64 = F * F / 64f
            val P1R = lat1 * D2R; val P2R = lat2 * D2R
            val DLR = lon2 * D2R - lon1 * D2R
            val T1R = kotlin.math.atan(BOA * kotlin.math.tan(P1R))
            val T2R = kotlin.math.atan(BOA * kotlin.math.tan(P2R))
            val TM = (T1R + T2R) / 2f; val DTM = (T2R - T1R) / 2f
            val STM = sin(TM); val CTM = cos(TM); val SDTM = sin(DTM); val CDTM = cos(DTM)
            val KL = STM * CDTM; val KK = SDTM * CTM
            val SDLMR = sin(DLR / 2f)
            val L = SDTM * SDTM + SDLMR * SDLMR * (CDTM * CDTM - STM * STM)
            val CD = 1f - 2f * L
            val DL = kotlin.math.acos(CD)
            val SD = sin(DL)
            val T = DL / SD
            val U = 2f * KL * KL / (1f - L)
            val V = 2f * KK * KK / L
            val D = 4f * T * T
            val X = U + V
            val E = -2f * CD
            val Y = U - V
            val A = -D * E
            val dist = AL * SD * (T - (F / 4f) * (T * X - Y) +
                FF64 * (X * (A + (T - (A + E) / 2f) * X) + Y * (-2f * D + E * Y) + D * X * Y)) / 1000f
            val TDLPM = kotlin.math.tan((DLR + (-((E * (4f - X) + 2f * Y) *
                ((F / 2f) * T + FF64 * (32f * T + (A - 20f * T) * X - 2f * (D + 2f) * Y)) / 4f) * kotlin.math.tan(DLR))) / 2f)
            val HAPBR = atan2(SDTM, CTM * TDLPM)
            val HAMBR = atan2(CDTM, STM * TDLPM)
            var A1M2 = TAU + HAMBR - HAPBR
            while (A1M2 < 0f || A1M2 >= TAU) A1M2 += if (A1M2 < 0f) TAU else -TAU
            return dist.toDouble() to (360f - A1M2 / D2R).toDouble()
        }

        /** PC Distance::toString: "<N" when close and either grid has only 4 characters. */
        fun distanceText(km: Double, miles: Boolean, units: Boolean, a: String, b: String): String {
            val close = (a.trim().length < 6 || b.trim().length < 6) && km < 120.0
            var value = if (close) 120.0 else km
            if (miles) value /= 1.609344
            return (if (close) "<" else "") + Math.round(value) + if (units) (if (miles) " mi" else " km") else ""
        }

        /** Grid from a compound "CALL GRID" helper frame (DecodeViewModel), for the frame it joins. */
        val compoundGrids = java.util.concurrent.ConcurrentHashMap<String, String>()
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
        val mfi = prefs.getString("js8cfg_mfi", "……").orEmpty()
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
        list += ActivityDetail(offset, text, m.snr, now, m.dt, m.mode, m.type, directed != null, m.isDataFrame(), m.quality < 0.17f,
            com.js8call.example.service.JS8EngineService.decodeDialHz)
        while (list.size > 10) list.removeAt(0)

        // Heartbeat / CQ (compound frames): call activity only
        val upper = text.uppercase()
        val isHeartbeat = upper.contains("@HB HEARTBEAT") || upper.contains(": HB ") || upper.contains("@ALLCALL HB")
        val isCq = directed != null && directed.to == "@ALLCALL" && directed.extra.uppercase().startsWith("CQ") ||
            directed != null && directed.to == "@ALLCALL" && directed.cmd.trim().uppercase() == "CQ"

        finishPending(offset, range, text, isLast, m, cfg.myCall)
        if (directed != null && directed.from != "<....>" && directed.to != "<....>") {
            val grid = (directed.extra + " " + text).split(Regex("\\s+")).lastOrNull { isGrid(it) && it.length >= 4 }
                ?: compoundGrids.remove(directed.from.uppercase()).orEmpty()
            val toMe = cfg.isToMe(directed.to)
            logCall(CallDetail(
                call = directed.from, grid = grid, snr = m.snr, offset = offset, utcMs = now, tdrift = m.dt,
                submode = m.mode,
                cqMs = if (isCq) now else 0,
                ackMs = if (toMe || upper.contains(": ACK") || directed.cmd.trim() == "ACK") now else 0
            ))
            logHeard(directed.from, directed.to, cfg.myCall)
            // GRID command: grids in the text; a buffered one finishes in finishPending
            val command = directed.cmd.trim().uppercase()
            if (command == "GRID") {
                directed.extra.split(Regex("\\s+")).filter { isGrid(it) }.forEach { g ->
                    callActivity[directed.from]?.grid = g
                }
            }
            if (command == "HEARING") parseHeard(directed.from, directed.extra, cfg.myCall)
            if (!isLast && (command == "GRID" || command == "HEARING" || (command == ">" && toMeCmd(directed, cfg)))) {
                pending[offset] = Pending(directed.from, command, StringBuilder(directed.extra))
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
        val buffered = bufferedToMe.keys.firstOrNull { abs(it - offset) <= range && now - (bufferedToMe[it] ?: 0) < 90_000 }
        val near = abs(offset - myOffset) <= range
        if (!near && buffered == null) return true
        val sender = text.substringBefore(':', "").trim()
        if (sender.isNotEmpty() && blocked(sender, cfg)) {
            blockedOffsets += offset
            return true
        }
        // PC displayTextForFreq: continuation frames of a blocked station stay hidden too
        blockedOffsets.firstOrNull { abs(it - offset) <= range }?.let { blockedAt ->
            if (!isLast && !isFirst) return true
            blockedOffsets -= blockedAt
            if (!isFirst) return true
        }
        writeRx(text, offset, now, isTx = false, newLine = isFirst, isLast = isLast, cfg = cfg, isData = m.isDataFrame())
        if (isLast && buffered != null) bufferedToMe.remove(buffered)
        return true
    }

    private fun toMeCmd(d: Directed, cfg: Config) = cfg.isToMe(d.to)

    /** PC logHeardGraph. */
    private fun logHeard(from: String, to: String, my: String) {
        if (my.isEmpty()) return
        hearing.getOrPut(my) { mutableSetOf() } += from
        heardBy.getOrPut(from) { mutableSetOf() } += my
        if (to == "@ALLCALL") return
        hearing.getOrPut(from) { mutableSetOf() } += to
        heardBy.getOrPut(to) { mutableSetOf() } += from
    }

    private fun parseHeard(from: String, text: String, my: String) =
        text.split(Regex("\\s+")).filter { isCallsign(it) }.forEach { logHeard(from, it, my) }

    /** Later frames of a buffered GRID, HEARING or relay at this offset; the last frame finishes it. */
    private fun finishPending(offset: Int, range: Int, text: String, isLast: Boolean, m: DecodedMessage, my: String) {
        val key = pending.keys.firstOrNull { abs(it - offset) <= range } ?: return
        val p = pending[key] ?: return
        p.text.append(text)
        if (!isLast) return
        pending.remove(key)
        when (p.cmd) {
            "GRID" -> p.text.toString().split(Regex("\\s+")).lastOrNull { isGrid(it) }?.let { callActivity[p.from]?.grid = it }
            "HEARING" -> parseHeard(p.from, p.text.toString(), my)
            ">" -> Regex("\\s(?:\\*DE\\*|VIA)\\s([A-Z0-9/]+)").findAll(p.text.toString().uppercase()).forEach {
                // PC processCommandActivity: third-party calls join Call Activity, icon ⚟
                logCall(CallDetail(it.groupValues[1], snr = -64, offset = offset, utcMs = System.currentTimeMillis(),
                    tdrift = m.dt, submode = m.mode, through = p.from))
            }
        }
    }

    /** PC qsy: the rows move by the same amount as the dial. */
    fun shiftBand(hzDelta: Int) {
        val moved = bandActivity.entries.filter { it.value.isNotEmpty() }.associate { (off, list) ->
            off - hzDelta to list.apply { this[lastIndex] = last().copy(offset = off - hzDelta) }
        }
        bandActivity.clear()
        bandActivity.putAll(moved)
    }

    private fun blocked(call: String, cfg: Config) =
        cfg.blocklist.isNotEmpty() && (call.uppercase() in cfg.blocklist || baseCall(call) in cfg.blocklist)

    /** UI_Constructor::logCallActivity: update keeping grid / ack / cq times. */
    fun logCall(d: CallDetail) {
        if (d.call.isBlank() || d.call.contains(">") || d.call.startsWith("@")) return
        val old = callActivity[d.call]
        if (old != null) {
            if (d.through.isNotEmpty()) return // heard directly already
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

    /** PC writeNoticeTextToUI: a bold line with the time and no offset. */
    fun writeNotice(text: String) {
        val line = RxLine(System.currentTimeMillis(), NOTICE_OFFSET, isTx = false, text = StringBuilder(text), closed = true)
        rxLines += line
        while (rxLines.size > 500) rxLines.removeAt(0)
    }

    /**
     * FT8 signal heard in the JS8 audio: its own line, "FT8: CQ W1AW FN31", with the
     * transmitting callsign (the message's second callsign) in bold. Repeats within a slot are dropped.
     */
    fun writeFt8(slotUtcMs: Long, offset: Int, message: String, callDe: String) {
        val text = "FT8: $message"
        if (rxLines.any { it.utcMs == slotUtcMs && it.text.toString() == text }) return
        val line = RxLine(slotUtcMs, offset, isTx = false, text = StringBuilder(text), closed = true)
        if (callDe.isNotEmpty()) {
            // second callsign: search after the first word, so "W1AW W1AW" style repeats bold the right one
            val firstSpace = message.indexOf(' ')
            val at = if (firstSpace < 0) -1 else message.indexOf(callDe, firstSpace + 1)
            if (at >= 0) line.bold = (5 + at) until (5 + at + callDe.length)
        }
        rxLines += line
        while (rxLines.size > 500) rxLines.removeAt(0)
    }

    /**
     * One part of our own transmission, shown in the TX color as it starts going out
     * (PC prepareNextMessageFrame -> displayTextForFreq): the first part starts a line,
     * later parts join it, the last one ends it with the EOT mark.
     */
    fun writeTxFrame(text: String, offset: Int, first: Boolean, last: Boolean, cfg: Config, isData: Boolean = false) =
        writeRx(text, offset, System.currentTimeMillis(), isTx = true, newLine = first, isLast = last, cfg = cfg, isData = isData)

    /**
     * PC processIdleActivity: a message whose next frame didn't come within 1.5 periods gets the
     * Missing Frame Character Indicator, in Band Activity and on its open Message Window line.
     */
    fun processIdle(now: Long, cfg: Config): Boolean {
        if (cfg.mfi.isEmpty()) return false
        var changed = false
        bandActivity.forEach { (offset, list) ->
            val last = list.lastOrNull() ?: return@forEach
            if ((last.bits and 2) != 0 || last.text == cfg.mfi) return@forEach
            if ((now - last.utcMs) < periodSec(last.submode) * 1500L) return@forEach
            list += ActivityDetail(offset, cfg.mfi, last.snr, last.utcMs, last.tdrift, last.submode, 0, false, true)
            while (list.size > 10) list.removeAt(0)
            val bucket = offset / 10 * 10
            if (openRxLine[offset] ?: openRxLine[bucket] ?: openRxLine[bucket + 10] != null) {
                writeRx(cfg.mfi, offset, last.utcMs, isTx = false, newLine = false, isLast = false, cfg = cfg, isData = true)
            }
            changed = true
        }
        return changed
    }

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
            if (item.lowConfidence) t = "[$t]" // DecodedText::isLowConfidence
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
    fun clearCalls() { callActivity.clear(); hearing.clear(); heardBy.clear() }
    fun clearRx() { rxLines.clear(); openRxLine.clear() }

    /**
     * Band Activity, Call Activity and the Message Window as JSON, kept across restarts like the PC
     * (Rob, 2026-10-22: "all settings remembered"; PC: unless "Reset the Band Activity, Call Activity,
     * and RX history at startup").
     */
    fun toJson(): org.json.JSONObject {
        val band = org.json.JSONArray()
        bandActivity.forEach { (off, list) ->
            list.forEach { d ->
                band.put(org.json.JSONObject().put("offset", off).put("text", d.text).put("snr", d.snr).put("utc", d.utcMs)
                    .put("tdrift", d.tdrift.toDouble()).put("submode", d.submode).put("bits", d.bits)
                    .put("directed", d.isDirected).put("data", d.isDataFrame))
            }
        }
        val calls = org.json.JSONArray()
        callActivity.values.forEach { c ->
            calls.put(org.json.JSONObject().put("call", c.call).put("grid", c.grid).put("snr", c.snr).put("offset", c.offset)
                .put("utc", c.utcMs).put("tdrift", c.tdrift.toDouble()).put("submode", c.submode).put("cq", c.cqMs).put("ack", c.ackMs))
        }
        val rx = org.json.JSONArray()
        rxLines.forEach { l ->
            rx.put(org.json.JSONObject().put("utc", l.utcMs).put("offset", l.offset).put("tx", l.isTx).put("text", l.text.toString())
                .put("boldFirst", l.bold?.first ?: -1).put("boldLast", l.bold?.last ?: -1))
        }
        return org.json.JSONObject().put("band", band).put("calls", calls).put("rx", rx)
    }

    /** Puts back what [toJson] saved; Message Window lines come back finished (no more frames join them). */
    fun fromJson(o: org.json.JSONObject) {
        bandActivity.clear(); callActivity.clear(); clearRx()
        o.optJSONArray("band")?.let { arr ->
            for (i in 0 until arr.length()) {
                val j = arr.getJSONObject(i)
                val off = j.getInt("offset")
                bandActivity.getOrPut(off) { mutableListOf() }.add(ActivityDetail(off, j.getString("text"), j.getInt("snr"),
                    j.getLong("utc"), j.getDouble("tdrift").toFloat(), j.getInt("submode"), j.getInt("bits"),
                    j.getBoolean("directed"), j.getBoolean("data")))
            }
        }
        o.optJSONArray("calls")?.let { arr ->
            for (i in 0 until arr.length()) {
                val j = arr.getJSONObject(i)
                val c = j.getString("call")
                callActivity[c] = CallDetail(c, j.getString("grid"), j.getInt("snr"), j.getInt("offset"), j.getLong("utc"),
                    j.getDouble("tdrift").toFloat(), j.getInt("submode"), j.getLong("cq"), j.getLong("ack"))
            }
        }
        o.optJSONArray("rx")?.let { arr ->
            for (i in 0 until arr.length()) {
                val j = arr.getJSONObject(i)
                rxLines.add(RxLine(j.getLong("utc"), j.getInt("offset"), j.getBoolean("tx"), StringBuilder(j.getString("text")), true).apply {
                    val f = j.optInt("boldFirst", -1)
                    if (f >= 0) bold = f..j.optInt("boldLast", f)
                })
            }
        }
    }
}
