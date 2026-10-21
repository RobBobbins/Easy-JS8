package com.js8call.example.service

/**
 * The HB button's timer (PC m_hb_loop): on only while the button is lit, never kept after a
 * restart (like the PC). The engine service runs it; the main screen shows the countdown.
 */
object HeartbeatTimer {
    /** The timer is running (HB button lit). */
    @Volatile var on = false
    /** When the next timed heartbeat goes out (ms since 1970), 0 = none planned. */
    @Volatile var nextMs = 0L
}

/**
 * The CQ button's timer (PC m_cq_loop): on only while CQ is lit, not kept after a restart. When it
 * is due the engine service calls [fire] (the main screen: PC sendCQ(true)); without a main
 * screen the service queues the CQ message itself.
 */
object CqTimer {
    @Volatile var on = false
    @Volatile var nextMs = 0L
    /** Sends the CQ; false when it could not (no main screen). Called on the main thread. */
    @Volatile var fire: (() -> Boolean)? = null
    /** PC CQInterval: minutes, 0 = On demand. */
    const val KEY_INTERVAL = "js8cfg_cq_interval"
}
