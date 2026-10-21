package com.js8call.example.api

import android.util.Log
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface

/**
 * WSJT-X UDP protocol client (JS8Call 3.0.3 JS8_UDP/WSJTXMessageClient.cpp, NetworkMessage.h):
 * binary QDataStream messages, big endian, magic 0xadbccbda. Sends Heartbeat every 15 s,
 * Status, Decode, QSOLogged, LoggedADIF and Close; reads Reply, Clear, Close, Replay, HaltTx,
 * FreeText and Location when Accept UDP Requests is checked.
 *
 * All calls run on the API network thread.
 */
class WsjtxClient(
    private val id: String,
    private val version: String,
    private val revision: String,
    private val listener: Listener
) {
    interface Listener {
        fun onFreeText(text: String, send: Boolean)
        fun onLocation(location: String)
        fun onError(message: String)
    }

    private var socket: MulticastSocket? = null
    private var server: InetAddress? = null
    private var serverPort = 0
    private var ttlValue = 1
    private var interfaces: List<NetworkInterface> = emptyList()
    private var schema = 2 // 2 before negotiation, not 1 which is broken
    private var lastMessage: ByteArray? = null
    private val pending = ArrayDeque<ByteArray>()
    @Volatile var enabled = false
    @Volatile private var closed = false

    /** set_server: DNS lookup if needed, then start. */
    fun setServer(name: String, interfaceNames: List<String>) {
        interfaces = interfaceNames.mapNotNull { runCatching { NetworkInterface.getByName(it) }.getOrNull() }
        server = if (name.isBlank()) null else try {
            InetAddress.getByName(name.trim())
        } catch (e: Exception) {
            listener.onError("UDP server DNS lookup failed: ${e.message}")
            return
        }
        start()
    }

    fun setServerPort(port: Int) { serverPort = port }

    fun setTtl(value: Int) {
        ttlValue = value
        runCatching { socket?.timeToLive = ttlValue }
    }

    private fun start() {
        val s = server
        if (s == null) {
            pending.clear()
            return
        }
        if (isBroadcast(s)) {
            listener.onError("IPv4 broadcast not supported, please specify the loop-back address, a server host address, or multicast group address")
            pending.clear()
            return
        }
        if (socket == null) {
            try {
                socket = MulticastSocket(InetSocketAddress(0)).apply { timeToLive = ttlValue }
                startReader(socket!!)
            } catch (e: Exception) {
                listener.onError("WSJT-X socket: ${e.message}")
                return
            }
        }
        heartbeat()
        while (pending.isNotEmpty()) send(pending.removeFirst(), queueIfPending = true, allowDuplicates = false)
    }

    private fun isBroadcast(a: InetAddress): Boolean {
        if (a !is Inet4Address) return false
        if (a.hostAddress == "255.255.255.255") return true
        return runCatching {
            NetworkInterface.getNetworkInterfaces().toList().any { nif ->
                nif.interfaceAddresses.any { it.broadcast == a }
            }
        }.getOrDefault(false)
    }

    private fun startReader(s: MulticastSocket) {
        Thread({
            val buf = ByteArray(65536)
            while (!closed && !s.isClosed) {
                try {
                    val p = DatagramPacket(buf, buf.size)
                    s.receive(p)
                    parse(p.data.copyOf(p.length))
                } catch (e: Exception) {
                    if (!s.isClosed) Log.w(TAG, "WSJT-X receive: ${e.message}")
                }
            }
        }, "Js8Api-wsjtx-rx").apply { isDaemon = true }.start()
    }

    /** parse_message: the server's schema is taken once; requests only while enabled. */
    private fun parse(msg: ByteArray) {
        try {
            val r = QIn(msg)
            if (r.u32() != MAGIC) return
            val inSchema = r.u32().toInt()
            if (inSchema > SCHEMA_NUMBER) return
            val type = r.u32().toInt()
            r.utf8() // id
            if (schema < inSchema) schema = inSchema
            if (!enabled) return
            when (type) {
                FREE_TEXT -> {
                    val text = r.utf8()
                    val send = if (r.hasMore()) r.bool() else true
                    listener.onFreeText(text, send)
                }
                LOCATION -> listener.onLocation(r.utf8())
                CLOSE, REPLAY -> lastMessage = null
                // Reply, Clear, HaltTx: read but not acted on, like the PC (WSJTXMessageMapper TODOs)
                else -> {}
            }
        } catch (e: Exception) {
            listener.onError("WSJTXMessageClient exception: ${e.message}")
        }
    }

    private fun builder(type: Int): QOut = QOut().apply { u32(MAGIC); u32(schema.toLong()); u32(type.toLong()); utf8(id) }

    fun heartbeat() {
        if (serverPort == 0 || server == null) return
        val out = builder(HEARTBEAT).apply { u32(SCHEMA_NUMBER.toLong()); utf8(version); utf8(revision) }
        send(out.bytes(), queueIfPending = false, allowDuplicates = true)
    }

    fun close() {
        if (serverPort != 0 && server != null) send(builder(CLOSE).bytes(), queueIfPending = false, allowDuplicates = false)
        closed = true
        socket?.close()
        socket = null
    }

    fun statusUpdate(
        freq: Long, mode: String, dxCall: String, report: String, txMode: String, txEnabled: Boolean,
        transmitting: Boolean, decoding: Boolean, rxDf: Long, txDf: Long, deCall: String, deGrid: String,
        dxGrid: String, watchdog: Boolean, subMode: String, fastMode: Boolean, specialOp: Int,
        tolerance: Long, trPeriod: Long, configName: String, txMessage: String
    ) {
        if (serverPort == 0 || server == null) return
        val out = builder(STATUS).apply {
            u64(freq); utf8(mode); utf8(dxCall); utf8(report); utf8(txMode); bool(txEnabled); bool(transmitting)
            bool(decoding); u32(rxDf); u32(txDf); utf8(deCall); utf8(deGrid); utf8(dxGrid); bool(watchdog)
            utf8(subMode); bool(fastMode); u8(specialOp); u32(tolerance); u32(trPeriod); utf8(configName); utf8(txMessage)
        }
        send(out.bytes())
    }

    fun decode(isNew: Boolean, msOfDay: Long, snr: Int, deltaTime: Float, deltaFrequency: Long, mode: String,
               message: String, lowConfidence: Boolean, offAir: Boolean) {
        if (serverPort == 0 || server == null) return
        val out = builder(DECODE).apply {
            bool(isNew); u32(msOfDay); i32(snr); f64(deltaTime.toDouble()); u32(deltaFrequency); utf8(mode); utf8(message)
            bool(lowConfidence); bool(offAir)
        }
        send(out.bytes())
    }

    fun qsoLogged(timeOffMs: Long, dxCall: String, dxGrid: String, dialHz: Long, mode: String, reportSent: String,
                  reportReceived: String, txPower: String, comments: String, name: String, timeOnMs: Long,
                  operatorCall: String, myCall: String, myGrid: String, exchangeSent: String, exchangeRcvd: String,
                  propMode: String) {
        if (serverPort == 0 || server == null) return
        val out = builder(QSO_LOGGED).apply {
            dateTime(timeOffMs); utf8(dxCall); utf8(dxGrid); u64(dialHz); utf8(mode); utf8(reportSent); utf8(reportReceived)
            utf8(txPower); utf8(comments); utf8(name); dateTime(timeOnMs); utf8(operatorCall); utf8(myCall); utf8(myGrid)
            utf8(exchangeSent); utf8(exchangeRcvd); utf8(propMode)
        }
        send(out.bytes())
    }

    fun loggedAdif(record: String) {
        if (serverPort == 0 || server == null) return
        val adif = "\n<adif_ver:5>3.1.0\n<programid:6>WSJT-X\n<EOH>\n$record <EOR>"
        send(builder(LOGGED_ADIF).apply { utf8(adif) }.bytes())
    }

    /** send_message: duplicates skipped (except heartbeats); held until the server is known. */
    private fun send(message: ByteArray, queueIfPending: Boolean = true, allowDuplicates: Boolean = false) {
        if (serverPort == 0) return
        val s = server
        val sock = socket
        if (s == null || sock == null) {
            if (queueIfPending) pending.addLast(message)
            return
        }
        if (!allowDuplicates && lastMessage?.contentEquals(message) == true) return
        try {
            if (s.isMulticastAddress) {
                interfaces.forEach { nif ->
                    sock.networkInterface = nif
                    sock.send(DatagramPacket(message, message.size, s, serverPort))
                }
            } else {
                sock.send(DatagramPacket(message, message.size, s, serverPort))
            }
            lastMessage = message
        } catch (e: Exception) {
            Log.w(TAG, "WSJT-X send: ${e.message}")
        }
    }

    /** QDataStream writer (big endian; doubles 64-bit; QByteArray = length + bytes). */
    private class QOut {
        private val buf = ByteArrayOutputStream()
        private val d = DataOutputStream(buf)
        fun u32(v: Long) = d.writeInt(v.toInt())
        fun i32(v: Int) = d.writeInt(v)
        fun u64(v: Long) = d.writeLong(v)
        fun u8(v: Int) = d.writeByte(v)
        fun bool(v: Boolean) = d.writeByte(if (v) 1 else 0)
        fun f64(v: Double) = d.writeDouble(v)
        fun utf8(s: String) { val b = s.toByteArray(Charsets.UTF_8); d.writeInt(b.size); d.write(b) }
        /** QDateTime, UTC: Julian day (qint64), ms since midnight (quint32), timespec 1. */
        fun dateTime(epochMs: Long) {
            d.writeLong(Math.floorDiv(epochMs, 86_400_000L) + 2_440_588L)
            d.writeInt(Math.floorMod(epochMs, 86_400_000L).toInt())
            d.writeByte(1)
        }
        fun bytes(): ByteArray = buf.toByteArray()
    }

    private class QIn(bytes: ByteArray) {
        private val raw = ByteArrayInputStream(bytes)
        private val d = DataInputStream(raw)
        fun u32(): Long = d.readInt().toLong() and 0xffffffffL
        fun bool(): Boolean = d.readByte().toInt() != 0
        fun hasMore() = raw.available() > 0
        fun utf8(): String {
            val n = d.readInt()
            if (n == -1 || n == 0) return ""
            val b = ByteArray(n)
            d.readFully(b)
            return String(b, Charsets.UTF_8)
        }
    }

    companion object {
        private const val TAG = "Js8Api"
        const val MAGIC = 0xadbccbdaL
        const val SCHEMA_NUMBER = 3
        const val HEARTBEAT = 0
        const val STATUS = 1
        const val DECODE = 2
        const val CLEAR = 3
        const val REPLY = 4
        const val QSO_LOGGED = 5
        const val CLOSE = 6
        const val REPLAY = 7
        const val HALT_TX = 8
        const val FREE_TEXT = 9
        const val LOCATION = 11
        const val LOGGED_ADIF = 12
    }
}
