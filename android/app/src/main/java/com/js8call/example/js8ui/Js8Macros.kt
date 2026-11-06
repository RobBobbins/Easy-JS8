package com.js8call.example.js8ui

import android.content.SharedPreferences
import com.js8call.example.BuildConfig

/**
 * The PC's message macros (mainwindow.cpp buildMacroValues): <MYCALL>, <MYGRID4>, <MYGRID12>,
 * <MYINFO>, <MYHB>, <MYSTATUS>, <MYCQ>, <MYREPLY>, <MYVERSION>, <MYIDLE>, <CALL>, <SNR>, <TDELTA>.
 */
object Js8Macros {
    /** Last time the operator did something (typed, sent). Used for <MYIDLE>. */
    @Volatile var lastActivityMs: Long = System.currentTimeMillis()

    class Selected(val call: String, val snr: Int, val tdriftSec: Float)
    /** The selected callsign when Call Activity has it. */
    @Volatile var selected: Selected? = null

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
            "<MYVERSION>" to "EASY JS8 ${BuildConfig.VERSION_NAME}", // PC sends the plain version (Rob, 2026-11-06)
            "<MYIDLE>" to idle()
        ) + selectedValues()
        fun sub(s: String) = base.entries.fold(s) { acc, (k, v) -> acc.replace(k, v) }
        val values = base + mapOf(
            "<MYINFO>" to sub(com.js8call.example.api.Js8Api.myInfo(prefs)),
            "<MYSTATUS>" to sub(com.js8call.example.api.Js8Api.myStatus(prefs)),
            "<MYHB>" to sub("HB <MYGRID4>"),  // PC hidden HB Message, its default (Configuration.cpp 2153)
            "<MYCQ>" to sub(prefs.getString("js8cfg_cq_message", "CQ CQ CQ <MYGRID4>").orEmpty()),
            "<MYREPLY>" to sub(prefs.getString("js8cfg_reply_message", "HW CPY?").orEmpty())
        )
        // PC replaceMacros: values upper-cased, what is left in <> removed, spaces kept as typed
        return values.entries.fold(text) { acc, (k, v) -> acc.replace(k, v.uppercase()) }
            .replace(Regex("[<][^>]+[>]"), "")
    }

    /** PC buildMacroValues: only with a selected callsign in Call Activity; <SNR> above -31. */
    private fun selectedValues(): Map<String, String> {
        val s = selected ?: return emptyMap()
        val values = mutableMapOf("<CALL>" to s.call, "<TDELTA>" to "${(1000 * s.tdriftSec).toInt()} ms")
        if (s.snr > -31) values["<SNR>"] = Js8Activity.formatSnr(s.snr)
        return values
    }
}
