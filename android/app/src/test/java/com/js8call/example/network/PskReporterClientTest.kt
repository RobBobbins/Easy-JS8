package com.js8call.example.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The PC PSKReporter.cpp rules (audit B N38-N40), read through the client's private parts. */
class PskReporterClientTest {
    private val client = PskReporterClient("JS8Android-test")

    private fun call(name: String, vararg args: Any): Any? {
        val types = args.map { if (it is Long) Long::class.javaPrimitiveType!! else it.javaClass }.toTypedArray()
        return client.javaClass.getDeclaredMethod(name, *types).apply { isAccessible = true }.invoke(client, *args)
    }

    private fun field(name: String) = client.javaClass.getDeclaredField(name).apply { isAccessible = true }

    /** The packet being built, as bytes; then emptied, as after a send. */
    private fun takePayload(): ByteArray {
        val payload = field("payload").get(client)!!
        val bytes = payload.javaClass.getDeclaredMethod("toByteArray").apply { isAccessible = true }.invoke(payload) as ByteArray
        payload.javaClass.getDeclaredMethod("clear").apply { isAccessible = true }.invoke(payload)
        return bytes
    }

    private fun sequenceIn(bytes: ByteArray) =
        (bytes[8].toInt() and 0xff shl 24) or (bytes[9].toInt() and 0xff shl 16) or (bytes[10].toInt() and 0xff shl 8) or (bytes[11].toInt() and 0xff)

    @Test
    fun sequenceNumberGoesUpOnePerPacketFromOne() {
        // PC build_preamble: ++sequence_number_ (PSKReporter.cpp 448)
        val seen = (1..3).map { call("buildPreamble"); sequenceIn(takePayload()) }
        assertEquals(listOf(1, 2, 3), seen)
    }

    @Test
    fun descriptorsGoInTheFirstThreePackets() {
        // PC send_descriptors_ = 3, one less per preamble
        field("sendDescriptors").setInt(client, 3)
        val sizes = (1..4).map { call("buildPreamble"); takePayload().size }
        assertEquals(0, field("sendDescriptors").getInt(client))
        assertEquals(sizes[0], sizes[2])
        assertTrue(sizes[3] < sizes[2])
    }

    @Test
    fun everyFourthReportTimeFlushes() {
        // PC flushing(): ++flush_counter_ % FLUSH_INTERVAL == 0, FLUSH_INTERVAL 4 (PSKReporter.cpp 52, 425-427)
        assertEquals(listOf(false, false, false, true, false, false, false, true), (1..8).map { call("flushing") })
    }

    @Test
    fun eclipseWindowIsSixHoursEachSide() {
        // JS8_Include/eclipse.txt 2027-02-06T16:00:47Z = 1801929647 s
        val eclipse = 1801929647L
        assertEquals(true, call("eclipseActive", eclipse))
        assertEquals(true, call("eclipseActive", eclipse - 6 * 3600))
        assertEquals(true, call("eclipseActive", eclipse + 6 * 3600))
        assertEquals(false, call("eclipseActive", eclipse + 6 * 3600 + 1))
        assertEquals(false, call("eclipseActive", eclipse - 7 * 3600))
    }

    @Test
    fun repeatLimitBandIsOneOfThe30PcBands() {
        // PC bands()->find: the band name, empty outside the 30 bands (PSKReporter.cpp 693-694)
        assertEquals("60m", call("bandKey", 5_357_000L))
        assertEquals("30m", call("bandKey", 10_050_000L))
        assertEquals("17m", call("bandKey", 18_104_000L))
        assertEquals("12m", call("bandKey", 24_922_000L))
        assertEquals("", call("bandKey", 6_000_000L))
    }
}
