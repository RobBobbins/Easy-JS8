package com.js8call.example.network

import android.util.Log
import android.os.Handler
import android.os.HandlerThread
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.nio.charset.Charset
import java.util.Locale
import kotlin.random.Random

class PskReporterClient(
    private val programId: String,
    private val host: String = HOST,
    /** PC DriftingDateTime: the export time uses the drifted clock (PSKReporter.cpp 572-573). */
    private val nowMs: () -> Long = System::currentTimeMillis
) {
    /** PC pskReporter sendError: the status bar message. */
    var onUnavailable: (() -> Unit)? = null

    private data class Spot(
        val call: String,
        val grid: String,
        val snr: Int,
        val frequencyHz: Long,
        val mode: String,
        val timestampSeconds: Long
    )

    private val lock = Any()
    private val spots = mutableListOf<Spot>()
    private val callsCache = HashMap<String, Long>()

    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private var socket: DatagramSocket? = null
    private var started = false

    private var rxCall = ""
    private var rxGrid = ""
    private var rxAntenna = ""

    private var observationId: Int = Random.nextInt()
    private var sequenceNumber: Int = 0
    private var sendDescriptors: Int = 0
    private var flushCounter: Int = 0
    /** PC report_timer_: one interval, 120 to 125 s, chosen when it starts. */
    private var reportIntervalSec = MIN_SEND_INTERVAL_SECONDS

    // PC payload_, tx_data_ and tx_residue_: the packet being filled. It stays between report times until it
    // goes; spots that did not fit wait in the residue for the next packet.
    private val payload = PacketWriter()
    private var payloadHeader: MessageHeader? = null
    private val txData = PacketWriter()
    private var txResidue = ByteArray(0)

    private val reportRunnable = object : Runnable {
        override fun run() {
            sendReport(false)
            scheduleReport()
        }
    }

    private val descriptorRunnable = object : Runnable {
        override fun run() {
            sendDescriptors = 3
            scheduleDescriptorRefresh()
        }
    }

    fun start() {
        if (started) return
        started = true
        handlerThread = HandlerThread("PskReporter").apply { start() }
        handler = Handler(handlerThread!!.looper)
        handler?.post { ensureSocket() }
        reportIntervalSec = MIN_SEND_INTERVAL_SECONDS + Random.nextInt(JITTER_MAX_SECONDS + 1)
        scheduleReport()
        scheduleDescriptorRefresh()
    }

    fun stop(flush: Boolean, discardPending: Boolean = false) {
        if (discardPending) {
            synchronized(lock) {
                spots.clear()
                callsCache.clear()
            }
        }
        val localHandler = handler
        if (localHandler == null) {
            started = false
            return
        }
        localHandler.post {
            if (flush) {
                sendReport(true)
            }
            closeSocket()
        }
        handlerThread?.quitSafely()
        handlerThread = null
        handler = null
        started = false
    }

    fun setLocalStation(call: String, grid: String, antenna: String) {
        val normalizedCall = call.trim().uppercase(Locale.US)
        val normalizedGrid = grid.trim().uppercase(Locale.US)
        val normalizedAntenna = antenna.trim()
        handler?.post {
            rxCall = normalizedCall
            rxGrid = normalizedGrid
            rxAntenna = normalizedAntenna
        }
    }

    fun addSpot(call: String, grid: String, snr: Int, frequencyHz: Long, mode: String, timestampSeconds: Long) {
        if (!started) return
        val normalizedCall = call.trim().uppercase(Locale.US)
        if (normalizedCall.isBlank()) return
        val normalizedGrid = grid.trim().uppercase(Locale.US)
        val normalizedMode = mode.trim().uppercase(Locale.US)
        // PC bands()->find: one of the 30 bands, empty outside them (PSKReporter.cpp 693-694)
        val band = bandKey(frequencyHz)
        val cacheKey = "${normalizedCall}_$band"
        val now = System.currentTimeMillis() / 1000
        val expiration = now - CACHE_TIMEOUT_SECONDS

        synchronized(lock) {
            val previous = callsCache[cacheKey]
            val expired = previous == null || previous < expiration
            // PC eclipse_active: within 6 hours of an eclipse every spot goes, past the 1-hour limit
            if (expired || eclipseActive(timestampSeconds)) {
                spots.add(Spot(normalizedCall, normalizedGrid, snr, frequencyHz, normalizedMode, timestampSeconds))
                callsCache[cacheKey] = now
            } else {
                for (i in spots.indices.reversed()) {
                    val existing = spots[i]
                    if (existing.call == normalizedCall && bandKey(existing.frequencyHz) == band) {
                        spots[i] = Spot(normalizedCall, normalizedGrid, snr, frequencyHz, normalizedMode, timestampSeconds)
                        callsCache[cacheKey] = now
                        break
                    }
                }
            }

            val iterator = callsCache.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (now - entry.value > CACHE_TIMEOUT_SECONDS * 2) {
                    iterator.remove()
                }
            }
        }
    }

    private fun scheduleReport() {
        handler?.postDelayed(reportRunnable, reportIntervalSec * 1000L)
    }

    private fun scheduleDescriptorRefresh() {
        handler?.postDelayed(descriptorRunnable, DESCRIPTOR_REFRESH_MS)
    }

    private fun ensureSocket() {
        if (socket != null) return
        try {
            val udpSocket = DatagramSocket()
            udpSocket.connect(InetSocketAddress(host, PORT))
            socket = udpSocket
            sendDescriptors = 3
            Log.d(TAG, "PSKReporter socket opened to $host:$PORT")
        } catch (e: Exception) {
            Log.w(TAG, "PSKReporter socket error", e)
            onUnavailable?.invoke()
            socket = null
        }
    }

    private fun closeSocket() {
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        socket = null
    }

    /** PC build_preamble: the header with the next sequence number, the descriptors while due, the receiver record. */
    private fun buildPreamble() {
        payload.writeShort(IPFIX_VERSION)
        val lengthOffset = payload.position
        payload.writeShort(0)
        val exportTimeOffset = payload.position
        payload.writeInt(0)
        payload.writeInt(++sequenceNumber) // PC ++sequence_number_: 1 per packet, from 1 (448)
        payload.writeInt(observationId)
        if (sendDescriptors > 0) {
            sendDescriptors -= 1
            payload.writeBytes(buildSenderInfoDescriptor())
            payload.writeBytes(buildReceiverInfoDescriptor())
        }
        payload.writeBytes(buildReceiverRecord())
        payloadHeader = MessageHeader(lengthOffset, exportTimeOffset)
    }

    /** PC flushing(): every 4th report time sends whatever is waiting, even no spots. */
    private fun flushing() = ++flushCounter % FLUSH_INTERVAL == 0

    /**
     * PC send_report (PSKReporter.cpp 492-594): the spots go into the packet. It is sent when it would pass
     * 10000 bytes (the last spot waits for the next packet), when the spots run out above 508 bytes, or when
     * flushing: every 4th report time and at the end, then with only the station record if there are no spots.
     * Otherwise it waits for the next report time.
     */
    private fun sendReport(sendResidue: Boolean) {
        ensureSocket()
        val udpSocket = socket ?: return
        fun spotsLeft() = synchronized(lock) { spots.isNotEmpty() }
        if (payload.size == 0) buildPreamble()
        var flush = flushing() || sendResidue
        while (spotsLeft() || flush) {
            if (payload.size == 0) buildPreamble()
            if (txData.size == 0 && (spotsLeft() || txResidue.isNotEmpty())) {
                txData.writeShort(TEMPLATE_ID_SENDER)
                txData.writeShort(0)
            }
            if (txResidue.isNotEmpty()) {
                txData.writeBytes(txResidue)
                txResidue = ByteArray(0)
            }
            while (spotsLeft() || flush) {
                var txDataSize = txData.size
                synchronized(lock) { spots.removeFirstOrNull() }?.let { writeSpot(txData, it) }
                val len = estimatedMessageLength(payload.size, txData.size)
                val drained = !spotsLeft()
                if (len > MAX_PAYLOAD_LENGTH || (drained && len > MIN_PAYLOAD_LENGTH) || (flush && drained)) {
                    if (txData.size > 0) {
                        if (len <= MAX_PAYLOAD_LENGTH) txDataSize = txData.size
                        val tx = PacketWriter()
                        tx.writeBytes(txData.toByteArray().copyOf(txDataSize))
                        tx.padTo4()
                        tx.setShort(2, tx.size)
                        payload.writeBytes(tx.toByteArray())
                    }
                    val header = payloadHeader ?: return
                    payload.padTo4()
                    payload.setShort(header.lengthOffset, payload.size)
                    payload.setInt(header.exportTimeOffset, (nowMs() / 1000).toInt())
                    send(udpSocket, payload.toByteArray())
                    flush = false
                    payload.clear()
                    val all = txData.toByteArray()
                    txResidue = all.copyOfRange(txDataSize, all.size)
                    txData.clear()
                    break
                }
            }
        }
    }

    /** PC handle_socket_error: an error drops the spots waiting to go (PSKReporter.cpp 385-397). */
    private fun send(udpSocket: DatagramSocket, bytes: ByteArray) {
        try {
            udpSocket.send(DatagramPacket(bytes, bytes.size))
            Log.d(TAG, "PSKReporter sent ${bytes.size} bytes")
        } catch (e: Exception) {
            Log.w(TAG, "PSKReporter send failed", e)
            synchronized(lock) { spots.clear() }
            onUnavailable?.invoke()
        }
    }

    private fun buildSenderInfoDescriptor(): ByteArray {
        val writer = PacketWriter()
        writer.writeShort(2)
        val lengthOffset = writer.position
        writer.writeShort(0)
        writer.writeShort(TEMPLATE_ID_SENDER)
        writer.writeShort(7)
        writer.writeShort(0x8000 + 1)
        writer.writeShort(0xFFFF)
        writer.writeInt(ENTERPRISE_NUMBER)
        writer.writeShort(0x8000 + 5)
        writer.writeShort(5)
        writer.writeInt(ENTERPRISE_NUMBER)
        writer.writeShort(0x8000 + 6)
        writer.writeShort(1)
        writer.writeInt(ENTERPRISE_NUMBER)
        writer.writeShort(0x8000 + 10)
        writer.writeShort(0xFFFF)
        writer.writeInt(ENTERPRISE_NUMBER)
        writer.writeShort(0x8000 + 3)
        writer.writeShort(0xFFFF)
        writer.writeInt(ENTERPRISE_NUMBER)
        writer.writeShort(0x8000 + 11)
        writer.writeShort(1)
        writer.writeInt(ENTERPRISE_NUMBER)
        writer.writeShort(150)
        writer.writeShort(4)
        writer.padTo4()
        writer.setShort(lengthOffset, writer.size)
        return writer.toByteArray()
    }

    private fun buildReceiverInfoDescriptor(): ByteArray {
        val writer = PacketWriter()
        writer.writeShort(3)
        val lengthOffset = writer.position
        writer.writeShort(0)
        writer.writeShort(TEMPLATE_ID_RECEIVER)
        writer.writeShort(4)
        writer.writeShort(0)
        writer.writeShort(0x8000 + 2)
        writer.writeShort(0xFFFF)
        writer.writeInt(ENTERPRISE_NUMBER)
        writer.writeShort(0x8000 + 4)
        writer.writeShort(0xFFFF)
        writer.writeInt(ENTERPRISE_NUMBER)
        writer.writeShort(0x8000 + 8)
        writer.writeShort(0xFFFF)
        writer.writeInt(ENTERPRISE_NUMBER)
        writer.writeShort(0x8000 + 9)
        writer.writeShort(0xFFFF)
        writer.writeInt(ENTERPRISE_NUMBER)
        writer.padTo4()
        writer.setShort(lengthOffset, writer.size)
        return writer.toByteArray()
    }

    private fun buildReceiverRecord(): ByteArray {
        val writer = PacketWriter()
        writer.writeShort(TEMPLATE_ID_RECEIVER)
        val lengthOffset = writer.position
        writer.writeShort(0)
        writeUtfString(writer, rxCall)
        writeUtfString(writer, rxGrid)
        writeUtfString(writer, programId)
        writeUtfString(writer, rxAntenna)
        writer.padTo4()
        writer.setShort(lengthOffset, writer.size)
        return writer.toByteArray()
    }

    private fun writeSpot(writer: PacketWriter, spot: Spot) {
        writeUtfString(writer, spot.call)
        writeFrequency(writer, spot.frequencyHz)
        writer.writeByte(spot.snr)
        writeUtfString(writer, spot.mode)
        writeUtfString(writer, spot.grid)
        writer.writeByte(REPORTER_SOURCE_AUTOMATIC)
        writer.writeInt(spot.timestampSeconds.toInt())
    }

    private fun writeFrequency(writer: PacketWriter, frequencyHz: Long) {
        writer.writeByte((frequencyHz shr 32).toInt())
        writer.writeByte((frequencyHz shr 24).toInt())
        writer.writeByte((frequencyHz shr 16).toInt())
        writer.writeByte((frequencyHz shr 8).toInt())
        writer.writeByte(frequencyHz.toInt())
    }

    private fun writeUtfString(writer: PacketWriter, value: String) {
        val bytes = value.toByteArray(Charset.forName("UTF-8"))
        val trimmed = if (bytes.size <= MAX_STRING_LENGTH) bytes else safeTruncate(bytes)
        writer.writeByte(trimmed.size)
        writer.writeBytes(trimmed)
    }

    private fun safeTruncate(bytes: ByteArray): ByteArray {
        var idx = MAX_STRING_LENGTH
        while (idx > 0) {
            val byte = bytes[idx].toInt() and 0xFF
            if ((byte and 0xC0) != 0x80) {
                return bytes.copyOf(idx)
            }
            idx -= 1
        }
        return ByteArray(0)
    }

    private fun estimatedMessageLength(baseSize: Int, txDataSize: Int): Int {
        var len = baseSize + txDataSize
        len += padBytes(txDataSize)
        len += padBytes(len)
        return len
    }

    private fun padBytes(size: Int): Int {
        val rem = size % 4
        return if (rem == 0) 0 else 4 - rem
    }

    private fun bandKey(frequencyHz: Long) = com.js8call.example.util.BandUtils.bandOrNull(frequencyHz).orEmpty()

    private fun eclipseActive(timestampSeconds: Long) = ECLIPSE_SECONDS.any { kotlin.math.abs(it - timestampSeconds) <= 6 * 3600L }

    private class PacketWriter(initialSize: Int = 1024) {
        private var buffer = ByteArray(initialSize)
        var position: Int = 0
            set(value) {
                field = value
            }

        val size: Int
            get() = position

        fun writeByte(value: Int) {
            ensure(1)
            buffer[position++] = value.toByte()
        }

        fun writeShort(value: Int) {
            ensure(2)
            buffer[position++] = ((value shr 8) and 0xFF).toByte()
            buffer[position++] = (value and 0xFF).toByte()
        }

        fun writeInt(value: Int) {
            ensure(4)
            buffer[position++] = ((value shr 24) and 0xFF).toByte()
            buffer[position++] = ((value shr 16) and 0xFF).toByte()
            buffer[position++] = ((value shr 8) and 0xFF).toByte()
            buffer[position++] = (value and 0xFF).toByte()
        }

        fun writeBytes(data: ByteArray) {
            ensure(data.size)
            data.copyInto(buffer, position, 0, data.size)
            position += data.size
        }

        fun padTo4() {
            val pad = (4 - (position % 4)) % 4
            repeat(pad) { writeByte(0) }
        }

        fun setShort(offset: Int, value: Int) {
            buffer[offset] = ((value shr 8) and 0xFF).toByte()
            buffer[offset + 1] = (value and 0xFF).toByte()
        }

        fun setInt(offset: Int, value: Int) {
            buffer[offset] = ((value shr 24) and 0xFF).toByte()
            buffer[offset + 1] = ((value shr 16) and 0xFF).toByte()
            buffer[offset + 2] = ((value shr 8) and 0xFF).toByte()
            buffer[offset + 3] = (value and 0xFF).toByte()
        }

        fun toByteArray(): ByteArray {
            return buffer.copyOf(position)
        }

        fun clear() {
            position = 0
        }

        private fun ensure(extra: Int) {
            if (position + extra <= buffer.size) return
            var newSize = buffer.size * 2
            while (newSize < position + extra) {
                newSize *= 2
            }
            buffer = buffer.copyOf(newSize)
        }
    }

    private data class MessageHeader(val lengthOffset: Int, val exportTimeOffset: Int)

    companion object {
        private const val TAG = "PskReporter"
        const val HOST = "report.pskreporter.info"
        private const val PORT = 4739
        private const val IPFIX_VERSION = 10
        private const val TEMPLATE_ID_SENDER = 0x50e3
        private const val TEMPLATE_ID_RECEIVER = 0x50e2
        private const val ENTERPRISE_NUMBER = 30351
        private const val REPORTER_SOURCE_AUTOMATIC = 1
        // PC PSKReporter.cpp 50-52: every 120-125 s; every 4th time sends what it has
        private const val MIN_SEND_INTERVAL_SECONDS = 120
        private const val JITTER_MAX_SECONDS = 5
        private const val FLUSH_INTERVAL = 4
        private const val MAX_STRING_LENGTH = 254
        private const val CACHE_TIMEOUT_SECONDS = 3600L
        private const val MIN_PAYLOAD_LENGTH = 508
        private const val MAX_PAYLOAD_LENGTH = 10000
        private const val DESCRIPTOR_REFRESH_MS = 60L * 60L * 1000L

        /** PC JS8_Include/eclipse.txt (eclipse.gsfc.nasa.gov SEdecade2021), UTC. */
        private val ECLIPSE_SECONDS = listOf(
            "2024-10-02T18:46:13Z", "2025-03-29T10:48:36Z", "2025-09-21T19:43:04Z", "2026-02-17T12:13:05Z",
            "2026-08-12T17:47:05Z", "2027-02-06T16:00:47Z", "2027-08-02T10:04:49Z", "2028-01-26T15:08:58Z",
            "2028-07-22T02:56:39Z", "2029-01-14T17:13:47Z", "2029-06-12T04:06:13Z", "2029-07-11T15:37:18Z",
            "2029-12-05T15:03:57Z", "2030-06-01T06:29:13Z", "2030-11-25T06:51:37Z"
        ).map { iso ->
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.parse(iso)!!.time / 1000
        }
    }
}
