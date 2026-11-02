package com.js8call.example.service

import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Locale

/** DX Lab Suite Commander over TCP (PC DXLabSuiteCommanderTransceiver), default port 52002. */
class DxLabCommanderClient(private val host: String, private val port: Int) : NetworkRig {
    private var socket: Socket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    @Synchronized
    override fun connect(timeoutMs: Int): Boolean {
        disconnect()
        return try {
            val s = Socket()
            s.connect(InetSocketAddress(host, port), timeoutMs)
            s.soTimeout = 3000
            socket = s
            input = s.getInputStream()
            output = s.getOutputStream()
            // PC do_start: anything but a frequency here is an error
            reply("<command:10>CmdGetFreq<parameters:0>")?.startsWith("<CmdFreq:") == true
        } catch (e: Exception) {
            android.util.Log.e("DxLabCommander", "Failed to connect to $host:$port - ${e.message}")
            disconnect()
            false
        }
    }

    @Synchronized
    override fun disconnect() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        input = null
        output = null
    }

    private fun send(command: String): Boolean = try {
        output?.let { it.write(command.toByteArray(Charsets.ISO_8859_1)); it.flush(); true } ?: false
    } catch (_: Exception) {
        disconnect()
        false
    }

    private fun reply(command: String): String? {
        if (!send(command)) return null
        val buffer = ByteArray(1024)
        return try {
            val n = input?.read(buffer) ?: -1
            if (n <= 0) null.also { disconnect() } else String(buffer, 0, n, Charsets.ISO_8859_1)
        } catch (_: Exception) {
            disconnect()
            null
        }
    }

    /** "<command:10>NAME<parameters:n>params" with n = the length of params. */
    private fun command(name: String, params: String = "") = "<command:${name.length}>$name<parameters:${params.length}>$params"

    private fun param(tag: String, value: String) = "<$tag:${value.length}>$value"

    /** kHz with three decimals, width 10, thousands separated (PC frequency_to_string). */
    private fun text(hz: Long) = String.format(Locale.US, "%,10.3f", hz / 1e3 + 1e-4)

    /** The value after "<Name:n>", or null when the answer is something else. */
    private fun value(reply: String?, name: String): String? =
        reply?.takeIf { it.startsWith("<$name:") }?.substringAfter('>')?.trim()

    private fun hz(text: String?): Long? =
        text?.replace(",", "")?.replace("�", "")?.trim()?.toDoubleOrNull()?.let { ((it + 1e-4) * 1e3).toLong() }

    @Synchronized
    override fun setFrequency(frequencyHz: Long) = send(command("CmdSetFreq", param("xcvrfreq", text(frequencyHz))))

    @Synchronized
    override fun getFrequency(): Long = hz(value(reply(command("CmdGetFreq")), "CmdFreq")) ?: 0L

    @Synchronized
    override fun setSplit(txFrequencyHz: Long, enabled: Boolean, mode: String?): Boolean {
        if (!enabled) return send("<command:8>CmdSplit<parameters:8><1:3>off")
        val suppress = if (mode == null) param("SuppressModeChange", "Y") else ""
        return send(command("CmdQSXSplit", param("xcvrfreq", text(txFrequencyHz)) + param("SuppressDual", "Y") + suppress))
    }

    @Synchronized
    override fun setPtt(enabled: Boolean, rearData: Boolean): Boolean {
        if (!send(command(if (enabled) "CmdTX" else "CmdRX"))) return false
        // PC do_ptt: wait up to a second for the rig to follow
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < 1000) {
            val state = value(reply(command("CmdSendTx")), "CmdTX") ?: return false
            if ((state == "ON") == enabled) return true
            Thread.sleep(10)
        }
        return false
    }

    @Synchronized
    override fun setMode(mode: String) = send(command("CmdSetMode", param("1", if (mode == "PKTUSB") "DATA-U" else "USB")))

    @Synchronized
    override fun readState(): NetworkRig.State {
        val split = value(reply(command("CmdSendSplit")), "CmdSplit")?.let { it == "ON" }
        val tx = if (split == true) hz(value(reply(command("CmdGetTXFreq")), "CmdTXFreq")) else null
        return NetworkRig.State(split = split, txHz = tx, mode = value(reply(command("CmdSendMode")), "CmdMode")?.let { m ->
            when (m) {
                "DATA-U", "Data-U", "DIGU", "PKT-R" -> "PKTUSB"
                else -> m
            }
        })
    }
}
