package com.js8call.example.service

import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Locale
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * PC APRSISClient (JS8Call 3.0.3): spots and third-party frames go up to APRS-IS; with the inbound
 * relay on, the connection stays up and text messages come back through [onMessage].
 * All state lives on one background thread; each connection has its own reading thread.
 */
class AprsIsClient(private val onMessage: (from: String, to: String, message: String, messageId: String) -> Unit) {

    private class Frame(val text: String, val queuedMs: Long)

    private val thread = HandlerThread("AprsIsClient").apply { start() }
    private val handler = Handler(thread.looper)

    private val queue = ArrayDeque<Frame>()
    private var host = ""
    private var port = 0
    private var paused = true
    private var relayEnabled = false
    private var skipPercent = 0f
    private var socket: Socket? = null

    private val timer = object : Runnable {
        override fun run() {
            if (!paused) processQueue(true)
            handler.postDelayed(this, TIMER_MS)
        }
    }

    init {
        handler.postDelayed(timer, TIMER_MS)
    }

    fun setServer(newHost: String, newPort: Int) = handler.post {
        if (newHost != host || newPort != port) {
            host = newHost
            port = newPort
            close()
        }
    }

    fun setSkipPercent(percent: Float) = handler.post { skipPercent = percent }

    fun setPaused(value: Boolean) = handler.post {
        paused = value
        if (!paused && relayEnabled) processQueue(false)
    }

    fun setIncomingRelayEnabled(enabled: Boolean) = handler.post {
        if (relayEnabled != enabled) {
            relayEnabled = enabled
            close() // the login line carries the message filter, so log in again
            if (enabled && !paused) processQueue(false)
        }
    }

    fun enqueueSpot(byCall: String, fromCall: String, grid: String, comment: String) {
        val (lat, lon) = grid2aprs(grid)
        enqueue("$fromCall>APJ8CL,qAS,$byCall:=$lat/${lon}G#JS8 ${comment.take(42)}\n")
    }

    fun enqueueThirdParty(byCall: String, fromCall: String, text: String) =
        enqueue("$fromCall>APJ8CL,qAS,$byCall:$text\n")

    fun enqueueMessageAck(fromCall: String, toCall: String, messageId: String) {
        if (fromCall.isEmpty() || toCall.isEmpty() || messageId.isEmpty()) return
        enqueue("$fromCall>APRS,TCPIP*::${toCall.take(9).padEnd(9)}:ack$messageId\n")
    }

    fun shutdown() {
        handler.removeCallbacksAndMessages(null)
        handler.post { close() }
        thread.quitSafely()
    }

    private fun enqueue(frame: String) = handler.post {
        queue.addLast(Frame(frame, System.currentTimeMillis()))
    }

    private fun processQueue(disconnect: Boolean) {
        if (!relayEnabled && queue.isEmpty()) return
        if (host.isEmpty() || port == 0) {
            queue.clear()
            return
        }
        val active = socket ?: connect() ?: return
        val delayed = ArrayList<Frame>()
        try {
            val out = active.getOutputStream()
            while (queue.isNotEmpty()) {
                val frame = queue.first()
                if (System.currentTimeMillis() - frame.queuedMs > PACKET_TIMEOUT_MS) {
                    queue.removeFirst()
                    continue
                }
                // random delay 25% of the time for throttling (a skip adds 60 seconds)
                if (skipPercent > 0 && (0 until 100).random() <= skipPercent * 100) {
                    delayed.add(queue.removeFirst())
                    continue
                }
                out.write(frame.text.toByteArray(Charsets.UTF_8))
                out.flush()
                Log.d(TAG, "Write: ${frame.text.trim()}")
                queue.removeFirst()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Write error: ${e.message}")
            queue.addAll(delayed)
            close()
            return
        }
        queue.addAll(delayed)
        if (disconnect && !relayEnabled) close()
    }

    private fun connect(): Socket? {
        return try {
            Log.i(TAG, "Connecting: $host:$port")
            val s = Socket()
            s.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            s.getOutputStream().write(loginFrame(if (relayEnabled) "filter t/m" else "").toByteArray(Charsets.UTF_8))
            s.getOutputStream().flush()
            socket = s
            Thread({ readLoop(s) }, "AprsIsClientRead").start()
            s
        } catch (e: Exception) {
            Log.w(TAG, "Connect error: ${e.message}")
            scheduleReconnect()
            null
        }
    }

    private fun readLoop(s: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
            while (true) {
                val line = reader.readLine()?.trim() ?: break
                if (line.isEmpty() || line.startsWith("#")) continue
                val match = MESSAGE_REGEX.find(line)
                if (match == null) {
                    Log.d(TAG, "No match: $line")
                    continue
                }
                val (message, messageId) = extractMessageId(match.groupValues[3])
                onMessage(match.groupValues[1], match.groupValues[2].trim(), message, messageId)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Read ended: ${e.message}")
        }
        handler.post {
            if (socket === s) {
                Log.i(TAG, "Disconnected")
                close()
                scheduleReconnect()
            }
        }
    }

    private fun scheduleReconnect() {
        if (!relayEnabled) return
        handler.postDelayed({
            if (relayEnabled && socket == null) processQueue(false)
        }, RECONNECT_MS)
    }

    private fun close() {
        val s = socket ?: return
        socket = null
        runCatching { s.close() }
    }

    companion object {
        private const val TAG = "AprsIsClient"
        private const val TIMER_MS = 60_000L
        private const val RECONNECT_MS = 5_000L
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val PACKET_TIMEOUT_MS = 300_000L
        private const val LOCAL_CALL = "APJ8CL"
        private val MESSAGE_REGEX = Regex("^([^>]+)>[^:]+::([A-Z0-9 ]{9}):(.*)$")

        internal fun hashCallsign(callsign: String): Int {
            val root = (callsign.substringBefore('-').uppercase(Locale.US) + "\u0000").toByteArray(Charsets.US_ASCII)
            var hash = 0x73E2
            var i = 0
            while (i + 1 < root.size) {
                hash = hash xor (root[i].toInt() shl 8)
                hash = hash xor root[i + 1].toInt()
                i += 2
            }
            return hash and 0x7FFF
        }

        internal fun loginFrame(filter: String) = "user $LOCAL_CALL pass ${hashCallsign(LOCAL_CALL)} ver JS8Call $filter\n"

        /** The text without its "{id" ending, and the id ("" when there is none). */
        internal fun extractMessageId(raw: String): Pair<String, String> {
            val trimmed = raw.trim()
            val brace = trimmed.lastIndexOf('{')
            if (brace < 0) return trimmed to ""
            val suffix = trimmed.substring(brace + 1).removeSuffix("}")
            if (suffix.isEmpty() || !suffix.all { it.isLetterOrDigit() }) return trimmed to ""
            return trimmed.substring(0, brace).trim() to suffix
        }

        internal fun grid2deg(locator: String): Pair<Float, Float> {
            val grid = locator.uppercase(Locale.US)
            var lat = -90f
            var lon = -90f
            val letters = Regex("([A-X])([A-X])").findAll(grid).toList()
            val digits = Regex("(\\d)(\\d)").findAll(grid).toList()
            val valx = IntArray(22)
            val valy = IntArray(22)
            var tot = 0
            letters.forEachIndexed { i, m ->
                if (i * 2 + 1 < 22) {
                    valx[i * 2] = m.groupValues[1][0] - 'A'
                    valy[i * 2] = m.groupValues[2][0] - 'A'
                    tot++
                }
            }
            digits.forEachIndexed { i, m ->
                if (i * 2 + 1 < 22) {
                    valx[i * 2 + 1] = m.groupValues[1].toInt()
                    valy[i * 2 + 1] = m.groupValues[2].toInt()
                    tot++
                }
            }
            for (i in 0 until tot) {
                val z = i - 1
                val scale = (10.0.pow(Math.floorDiv(-(z - 1), 2)) * 24.0.pow(Math.floorDiv(-z, 2))).toFloat()
                lon += scale * valx[i]
                lat += scale * valy[i]
            }
            return lat to lon * 2
        }

        internal fun grid2aprs(grid: String): Pair<String, String> {
            val (lat, lon) = grid2deg(grid)
            return aprsCoord(lat, 7, "N", "S") to aprsCoord(lon, 8, "E", "W")
        }

        private fun aprsCoord(value: Float, width: Int, positive: String, negative: String): String {
            val dir = if (value < 0) negative else positive
            val abs = kotlin.math.abs(value).toDouble()
            var deg = floor(abs)
            val minutes = (abs - deg) * 60
            var min = floor(minutes)
            var sec = ((minutes - min) * 60).roundToInt()
            if (sec == 60) {
                min += 1
                sec = 0
            }
            if (min == 60.0) {
                deg += 1
                min = 0.0
            }
            return String.format(Locale.US, "%0$width.2f%s", deg * 100 + min + sec / 60.0, dir)
        }

        internal fun replaceCallsignSuffixWithSSID(call: String, base: String): String {
            if (call == base) return call
            val ssid = Regex("/(\\d+)").find(call)?.groupValues?.get(1)
            return if (ssid != null) "$base-$ssid" else base
        }
    }
}
