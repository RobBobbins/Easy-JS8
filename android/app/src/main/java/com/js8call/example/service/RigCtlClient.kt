package com.js8call.example.service

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket

class RigCtlClient(
    val host: String,
    val port: Int
) {
    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null

    @Synchronized
    fun ensureConnected(timeoutMs: Int = 3000): Boolean {
        val active = socket
        if (active != null && active.isConnected && !active.isClosed && reader != null && writer != null) {
            return true
        }
        return connect(timeoutMs)
    }

    @Synchronized
    fun connect(timeoutMs: Int = 5000): Boolean {
        disconnect()
        return try {
            val newSocket = Socket()
            newSocket.connect(InetSocketAddress(host, port), timeoutMs)
            newSocket.soTimeout = timeoutMs
            socket = newSocket
            reader = BufferedReader(InputStreamReader(newSocket.getInputStream()))
            writer = BufferedWriter(OutputStreamWriter(newSocket.getOutputStream()))
            true
        } catch (e: Exception) {
            android.util.Log.e("RigCtlClient", "Failed to connect to $host:$port - ${e.javaClass.simpleName}: ${e.message}")
            disconnect()
            false
        }
    }

    @Synchronized
    fun disconnect() {
        try {
            reader?.close()
        } catch (_: Exception) {
        }
        try {
            writer?.close()
        } catch (_: Exception) {
        }
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        reader = null
        writer = null
        socket = null
    }

    @Synchronized
    fun sendCommand(command: String): String? {
        if (!ensureConnected()) return null
        return try {
            val line = command.trimEnd()
            val out = writer ?: run {
                disconnect()
                return null
            }
            val input = reader ?: run {
                disconnect()
                return null
            }
            out.write(line)
            out.write("\n")
            out.flush()
            input.readLine()
        } catch (_: Exception) {
            disconnect()
            null
        }
    }

    fun setFrequency(frequencyHz: Long): Boolean {
        val response = sendCommand("F $frequencyHz") ?: return false
        return response.startsWith("RPRT 0")
    }

    /** The rig's dial in Hz, 0 when it can't be read. */
    fun getFrequency(): Long = sendCommand("f")?.trim()?.toDoubleOrNull()?.toLong() ?: 0L

    /** Split on with the TX VFO at txFrequencyHz, or split off. */
    fun setSplit(txFrequencyHz: Long, enabled: Boolean): Boolean {
        val vfo = sendCommand(if (enabled) "S 1 VFOB" else "S 0 VFOA") ?: return false
        if (!vfo.startsWith("RPRT 0") || !enabled) return vfo.startsWith("RPRT 0")
        val response = sendCommand("I $txFrequencyHz") ?: return false
        return response.startsWith("RPRT 0")
    }

    /** rearData: rigctl's PTT data (PC back_ptt_port_); a server that refuses it gets plain PTT. */
    fun setPtt(enabled: Boolean, rearData: Boolean = false): Boolean {
        if (enabled && rearData && sendCommand("T 3")?.startsWith("RPRT 0") == true) return true
        val response = sendCommand("T ${if (enabled) 1 else 0}") ?: return false
        return response.startsWith("RPRT 0")
    }
}
