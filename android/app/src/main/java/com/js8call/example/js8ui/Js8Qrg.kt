package com.js8call.example.js8ui

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer

/**
 * The PC's frequency file (*.qrg, Configuration read_frequencies_file / save_frequencies): a
 * QDataStream, big-endian. Magic, version, list count, then per row frequency (quint64), mode
 * and region as enum key names (char*: length with the NUL, then the bytes) and, from
 * version 103, the description (QString: byte length, UTF-16).
 */
object Js8Qrg {
    class BadFile(message: String) : Exception(message)

    private const val MAGIC = 0xadbccbdbL
    private const val VERSION = 103
    private val REGIONS = mapOf("ALL" to "All", "R1" to "Region 1", "R2" to "Region 2", "R3" to "Region 3")
    private val MODES = mapOf("ALL" to "All", "JS8" to "JS8")

    fun read(bytes: ByteArray): List<Js8SettingsSchema.FrequencyItem> {
        val b = ByteBuffer.wrap(bytes)
        fun corrupt(): Nothing = throw BadFile("Contents corrupt")
        fun u32(): Long = if (b.remaining() < 4) corrupt() else b.int.toLong() and 0xffffffffL
        fun block(n: Long): ByteArray {
            if (n > b.remaining()) corrupt()
            return ByteArray(n.toInt()).also { b.get(it) }
        }
        fun key(names: Map<String, String>): String {
            val raw = block(u32())
            if (raw.isEmpty()) corrupt()
            return names[String(raw, 0, raw.size - 1, Charsets.ISO_8859_1)] ?: corrupt()
        }
        if (bytes.size < 4 || u32() != MAGIC) throw BadFile("Incorrect file magic")
        val version = u32()
        if (version > VERSION) throw BadFile("Version is too new")
        val count = u32()
        val list = ArrayList<Js8SettingsSchema.FrequencyItem>()
        for (i in 0 until count) {
            if (b.remaining() < 8) corrupt()
            val hz = b.long
            val mode = key(MODES)
            val region = key(REGIONS)
            val description = if (version == 102L) "" else {
                val n = u32()
                if (n == 0xffffffffL) "" else String(block(n), Charsets.UTF_16BE)
            }
            list += Js8SettingsSchema.FrequencyItem(hz, region, mode, description.replace('|', ' ').replace('\n', ' '))
        }
        if (b.hasRemaining()) corrupt()
        return list
    }

    fun write(items: List<Js8SettingsSchema.FrequencyItem>): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).apply {
            fun key(names: Map<String, String>, value: String) {
                val name = (names.entries.firstOrNull { it.value == value }?.key ?: "ALL").toByteArray(Charsets.ISO_8859_1)
                writeInt(name.size + 1)
                write(name)
                writeByte(0)
            }
            writeInt(MAGIC.toInt())
            writeInt(VERSION)
            writeInt(items.size)
            items.forEach { f ->
                writeLong(f.hz)
                key(MODES, f.mode)
                key(REGIONS, f.region)
                val text = f.description.toByteArray(Charsets.UTF_16BE)
                writeInt(text.size)
                write(text)
            }
        }
        return out.toByteArray()
    }
}
