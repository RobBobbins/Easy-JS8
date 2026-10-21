package com.js8call.example.js8ui

import android.content.SharedPreferences
import com.js8call.example.BuildConfig

/**
 * The PC's message macros (mainwindow.cpp buildMacroValues): <MYCALL>, <MYGRID4>, <MYGRID12>,
 * <MYINFO>, <MYSTATUS>, <MYCQ>, <MYREPLY>, <MYVERSION>, <MYIDLE>.
 */
object Js8Macros {
    /** Last time the operator did something (typed, sent). Used for <MYIDLE>. */
    @Volatile var lastActivityMs: Long = System.currentTimeMillis()

    fun touch() { lastActivityMs = System.currentTimeMillis() }

    private fun idle(): String {
        val m = (System.currentTimeMillis() - lastActivityMs) / 60_000
        return when {
            m < 60 -> "${m}M"
            m < 1440 -> "${m / 60}H"
            else -> "${m / 1440}D"
        }
    }

    fun expand(text: String, prefs: SharedPreferences): String {
        val grid = com.js8call.example.api.Js8Api.myGrid(prefs) // the API's grid while allowed (Configuration::my_grid)
        val base = mapOf(
            "<MYCALL>" to prefs.getString("callsign", "").orEmpty().trim().uppercase(),
            "<MYGRID4>" to grid.take(4),
            "<MYGRID12>" to grid.take(12),
            "<MYVERSION>" to "EASYJS8-${BuildConfig.VERSION_NAME}",
            "<MYIDLE>" to idle()
        )
        fun sub(s: String) = base.entries.fold(s) { acc, (k, v) -> acc.replace(k, v) }
        val values = base + mapOf(
            "<MYINFO>" to sub(com.js8call.example.api.Js8Api.myInfo(prefs)),
            "<MYSTATUS>" to sub(com.js8call.example.api.Js8Api.myStatus(prefs)),
            "<MYCQ>" to sub(prefs.getString("js8cfg_cq_message", "CQ CQ CQ <MYGRID4>").orEmpty()),
            "<MYREPLY>" to sub(prefs.getString("js8cfg_reply_message", "HW CPY?").orEmpty())
        )
        return values.entries.fold(text) { acc, (k, v) -> acc.replace(k, v) }.replace(Regex("\\s+"), " ").trim()
    }
}
