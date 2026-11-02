package com.js8call.example.service

/**
 * A rig reached over the network: Hamlib NET rigctl, DX Lab Suite Commander, Ham Radio Deluxe.
 * Modes are Hamlib's names: USB, PKTUSB.
 */
interface NetworkRig {
    /** What a poll read (PC transceiver state). null = the server could not say. */
    data class State(val vfo: String? = null, val split: Boolean? = null, val txHz: Long? = null,
                     val mode: String? = null, val ptt: Boolean? = null)

    fun connect(timeoutMs: Int = 5000): Boolean
    fun disconnect()
    fun setFrequency(frequencyHz: Long): Boolean
    /** The rig's dial in Hz, 0 when it can't be read. */
    fun getFrequency(): Long
    /** Split on with the TX VFO at txFrequencyHz, in [mode] when given, or split off. */
    fun setSplit(txFrequencyHz: Long, enabled: Boolean, mode: String? = null): Boolean
    fun setPtt(enabled: Boolean, rearData: Boolean = false): Boolean
    fun setMode(mode: String): Boolean
    fun readState(): State
}
