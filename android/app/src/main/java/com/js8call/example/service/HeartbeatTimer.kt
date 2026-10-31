package com.js8call.example.service

import android.content.SharedPreferences

/**
 * The HB button's timer (PC m_hb_loop): on while the button is lit. Kept after a restart with its
 * countdown (Rob, 2026-10-22: "all settings remembered"; the PC ends it with the program). The
 * engine service runs it; the main screen shows the countdown.
 */
object HeartbeatTimer {
    /** Set by the engine service; every change is saved there. */
    @Volatile var prefs: SharedPreferences? = null
    /** The timer is running (HB button lit). */
    @Volatile var on = false
        set(v) { field = v; prefs?.edit()?.putBoolean(KEY_ON, v)?.apply() }
    /** When the next timed heartbeat goes out (ms since 1970), 0 = none planned. */
    @Volatile var nextMs = 0L
        set(v) { field = v; prefs?.edit()?.putLong(KEY_NEXT, v)?.apply() }

    const val KEY_ON = "js8cfg_hb_timer_on"
    const val KEY_NEXT = "js8cfg_hb_timer_next"

    /** Engine service start: the saved state back (without saving it again). */
    fun load(p: SharedPreferences) {
        prefs = null
        on = p.getBoolean(KEY_ON, false)
        nextMs = p.getLong(KEY_NEXT, 0L)
        prefs = p
    }
}

/**
 * The CQ button's timer (PC m_cq_loop): on while CQ is lit, kept after a restart like the HB timer.
 * When it is due the engine service calls [fire] (the main screen: PC sendCQ(true)); without a main
 * screen the service queues the CQ message itself.
 */
object CqTimer {
    @Volatile var prefs: SharedPreferences? = null
    @Volatile var on = false
        set(v) { field = v; prefs?.edit()?.putBoolean(KEY_ON, v)?.apply() }
    @Volatile var nextMs = 0L
        set(v) { field = v; prefs?.edit()?.putLong(KEY_NEXT, v)?.apply() }
    /** Sends the CQ; false when it could not (no main screen). Called on the main thread. */
    @Volatile var fire: (() -> Boolean)? = null
    /** PC CQInterval: minutes, 0 = On demand. */
    const val KEY_INTERVAL = "js8cfg_cq_interval"
    const val KEY_ON = "js8cfg_cq_timer_on"
    const val KEY_NEXT = "js8cfg_cq_timer_next"

    fun load(p: SharedPreferences) {
        prefs = null
        on = p.getBoolean(KEY_ON, false)
        nextMs = p.getLong(KEY_NEXT, 0L)
        prefs = p
    }
}
