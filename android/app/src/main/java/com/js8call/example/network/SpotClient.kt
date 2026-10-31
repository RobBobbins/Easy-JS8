package com.js8call.example.network

import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.js8call.example.api.ApiMessage
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * JS8NET spotting, JS8Call 3.0.3 JS8_Network/SpotClient.cpp: RX.LOCAL, RX.SPOT and RX.DIRECTED as
 * JSON over UDP, queued and sent once a minute. One host lookup: if it fails, nothing is sent.
 */
class SpotClient(
    private val name: String,
    private val port: Int,
    private val version: String
) {
    private val lock = Any()
    private val queue = ArrayDeque<ApiMessage>()
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var socket: DatagramSocket? = null
    private var host: InetAddress? = null
    private var valid = true
    private var sent = 0
    private var call = ""
    private var grid = ""
    private var info = ""

    private val sendTimer = object : Runnable {
        override fun run() {
            flush()
            handler?.postDelayed(this, SEND_INTERVAL_MS)
        }
    }

    fun start() {
        if (thread != null) return
        val t = HandlerThread("SpotClient").also { it.start() }
        thread = t
        handler = Handler(t.looper).also { h ->
            h.post {
                try {
                    host = InetAddress.getByName(name)
                    socket = DatagramSocket()
                    h.postDelayed(sendTimer, SEND_INTERVAL_MS)
                } catch (e: Exception) {
                    Log.w(TAG, "Host lookup failed: ${e.message}")
                    synchronized(lock) {
                        valid = false
                        queue.clear()
                    }
                }
            }
        }
    }

    fun stop() {
        thread?.quitSafely()
        thread = null
        handler = null
        socket?.close()
        socket = null
        synchronized(lock) {
            queue.clear()
            valid = true
            sent = 0
            call = ""
            grid = ""
            info = ""
        }
    }

    /** Local information goes to the network on a change, or once every 15 minutes. */
    fun setLocalStation(callsign: String, grid: String, info: String) {
        synchronized(lock) {
            val changed = callsign != call || grid != this.grid || info != this.info
            call = callsign
            this.grid = grid
            this.info = info
            if (valid && (changed || sent % 15 == 0)) {
                queue.addLast(ApiMessage("RX.LOCAL", "", mapOf(
                    "CALLSIGN" to callsign, "GRID" to grid, "INFO" to info, "VERSION" to version
                )))
            }
        }
    }

    fun enqueueCmd(cmd: String, from: String, to: String, relayPath: String, text: String, grid: String,
                   extra: String, submode: Int, dial: Long, offset: Int, snr: Int) {
        synchronized(lock) {
            if (!valid) return
            queue.addLast(ApiMessage("RX.DIRECTED", "", mapOf(
                "BY" to by(), "CMD" to cmd, "FROM" to from, "TO" to to, "PATH" to relayPath, "TEXT" to text,
                "GRID" to grid, "EXTRA" to extra, "FREQ" to dial + offset, "DIAL" to dial, "OFFSET" to offset,
                "SNR" to snr, "SPEED" to submode
            )))
        }
    }

    fun enqueueSpot(callsign: String, grid: String, submode: Int, dial: Long, offset: Int, snr: Int) {
        synchronized(lock) {
            if (!valid) return
            queue.addLast(ApiMessage("RX.SPOT", "", mapOf(
                "BY" to by(), "CALLSIGN" to callsign, "GRID" to grid, "FREQ" to dial + offset, "DIAL" to dial,
                "OFFSET" to offset, "SNR" to snr, "SPEED" to submode
            )))
        }
    }

    private fun by() = mapOf("CALLSIGN" to call, "GRID" to grid)

    private fun flush() {
        val messages = synchronized(lock) {
            sent++
            queue.toList().also { queue.clear() }
        }
        val address = host ?: return
        val out = socket ?: return
        for (message in messages) {
            val bytes = message.toJson().toByteArray()
            try {
                out.send(DatagramPacket(bytes, bytes.size, address, port))
            } catch (e: Exception) {
                Log.w(TAG, "Send failed: ${e.message}")
            }
        }
    }

    private companion object {
        const val TAG = "SpotClient"
        const val SEND_INTERVAL_MS = 60_000L
    }
}
