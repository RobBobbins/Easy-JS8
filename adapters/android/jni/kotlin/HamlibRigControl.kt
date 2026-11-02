package com.js8call.core

/**
 * JNI wrapper for Hamlib rig control on Android.
 */
class HamlibRigControl {
    companion object {
        init {
            System.loadLibrary("js8core-jni")
        }
    }

    @Volatile
    private var handle: Long = 0

    @Synchronized
    fun open(
        rigModel: Int,
        deviceId: Int,
        portIndex: Int,
        baudRate: Int,
        dataBits: Int,
        stopBits: Int,
        parity: String
    ): Boolean {
        close()
        val nativeHandle = nativeOpen(
            rigModel,
            deviceId,
            portIndex,
            baudRate,
            dataBits,
            stopBits,
            parity
        )
        if (nativeHandle == 0L) {
            return false
        }
        handle = nativeHandle
        return true
    }

    @Synchronized
    fun openSerialPath(
        rigModel: Int,
        serialPath: String,
        baudRate: Int,
        dataBits: Int,
        stopBits: Int,
        parity: String,
        handshake: String = "None",
        dtrState: String = "",
        rtsState: String = ""
    ): Boolean {
        close()
        val nativeHandle = nativeOpenWithPath(
            rigModel,
            serialPath,
            baudRate,
            dataBits,
            stopBits,
            parity,
            handshake,
            dtrState,
            rtsState
        )
        if (nativeHandle == 0L) {
            return false
        }
        handle = nativeHandle
        return true
    }

    @Synchronized
    fun close() {
        val active = handle
        if (active != 0L) {
            nativeClose(active)
            handle = 0
        }
    }

    @Synchronized
    fun setFrequency(frequencyHz: Long): Boolean {
        val active = handle
        if (active == 0L) return false
        return nativeSetFrequency(active, frequencyHz)
    }

    @Synchronized
    fun setSplit(txFrequencyHz: Long, enabled: Boolean): Boolean {
        val active = handle
        if (active == 0L) return false
        return nativeSetSplit(active, txFrequencyHz, enabled)
    }

    /** The rig's dial in Hz, 0 when it can't be read. */
    @Synchronized
    fun getFrequency(): Long {
        val active = handle
        if (active == 0L) return 0L
        return nativeGetFrequency(active)
    }

    /** PC HamlibTransceiver::poll: "vfo|split|txHz|mode|ptt", an empty field when the rig can't tell. */
    @Synchronized
    fun readState(): String {
        val active = handle
        if (active == 0L) return ""
        return nativeReadState(active)
    }

    /** Sets the mode of the transmit VFO while in split (PC rig_set_split_mode). */
    @Synchronized
    fun setSplitMode(mode: String): Boolean {
        val active = handle
        if (active == 0L) return false
        return nativeSetSplitMode(active, mode)
    }

    @Synchronized
    fun setPtt(enabled: Boolean, rearData: Boolean = false): Boolean {
        val active = handle
        if (active == 0L) return false
        return nativeSetPtt(active, enabled, rearData)
    }

    @Synchronized
    fun setMode(mode: String, passband: Int): Boolean {
        val active = handle
        if (active == 0L) return false
        return nativeSetMode(active, mode, passband)
    }

    fun isOpen(): Boolean = handle != 0L

    fun getLastError(): String {
        return nativeGetLastError()
    }

    private external fun nativeReadState(handle: Long): String
    private external fun nativeSetSplitMode(handle: Long, mode: String): Boolean
    private external fun nativeOpen(
        rigModel: Int,
        deviceId: Int,
        portIndex: Int,
        baudRate: Int,
        dataBits: Int,
        stopBits: Int,
        parity: String
    ): Long
    private external fun nativeOpenWithPath(
        rigModel: Int,
        serialPath: String,
        baudRate: Int,
        dataBits: Int,
        stopBits: Int,
        parity: String,
        handshake: String,
        dtrState: String,
        rtsState: String
    ): Long

    private external fun nativeClose(handle: Long)
    private external fun nativeSetFrequency(handle: Long, frequencyHz: Long): Boolean
    private external fun nativeGetFrequency(handle: Long): Long
    private external fun nativeSetPtt(handle: Long, enabled: Boolean, rearData: Boolean): Boolean
    private external fun nativeSetSplit(handle: Long, txFrequencyHz: Long, enabled: Boolean): Boolean
    private external fun nativeSetMode(handle: Long, mode: String, passband: Int): Boolean
    private external fun nativeGetLastError(): String
}
