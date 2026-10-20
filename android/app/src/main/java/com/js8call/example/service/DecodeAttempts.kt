package com.js8call.example.service

/**
 * Waterfall Controls > Display > Display Decode Attempts: the engine service hands each decode
 * start and sync candidate to the screen, on the main thread, while the box is checked
 * (PC processDecodeEvent DecodeStarted / SyncState). In-process only.
 */
object DecodeAttempts {
    interface Listener {
        fun onDecodeStarted()
        /** [kind] 0 = candidate (quality [candidate]), 1 = decoded; [mode] = speed. */
        fun onSyncState(kind: Int, mode: Int, freq: Float, dt: Float, candidate: Int)
    }

    @Volatile
    var listener: Listener? = null
}
