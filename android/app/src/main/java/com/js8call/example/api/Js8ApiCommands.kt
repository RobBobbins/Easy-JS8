package com.js8call.example.api

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.js8call.example.data.MessageEntity
import com.js8call.example.data.MessageRepository
import com.js8call.example.js8ui.Js8Activity
import com.js8call.example.service.JS8EngineService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The API commands (JS8Call 3.0.3 JS8_Mainwindow/networkMessage.cpp), same names, replies and
 * fields. Runs on the main thread. Where the PC changes a button or menu, Easy JS8 changes the
 * same setting the button or the Settings window does.
 */
class Js8ApiCommands(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val api = Js8Api
    private val utcFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    private val clockFormat = SimpleDateFormat("HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

    private fun prefs() = api.prefs()
    private fun bool(key: String, def: Boolean) = api.bool(prefs(), key, def)
    private fun reply(type: String, value: String, params: Map<String, Any?>) = api.sendNetworkMessage(type, value, params)

    private fun service(action: String, extras: Intent.() -> Unit = {}) {
        try {
            context.startService(Intent(context, JS8EngineService::class.java).apply { this.action = action; extras() })
        } catch (e: IllegalStateException) {
            Log.w("Js8Api", "$action dropped: the app is in the background")
        }
    }

    private fun setPref(key: String, value: Boolean) = prefs().edit().putBoolean(key, value).apply()
    private fun refresh() = api.controller?.refresh()

    fun handle(message: ApiMessage) {
        val type = message.type
        if (type == "PING") return
        val id = message.id
        val params = message.params

        when (type) {
            // ---- RIG --------------------------------------------------------------------------
            "RIG.GET_PTT" -> {
                val ptt = Js8Api.State.transmitting
                reply("RIG.PTT_STATUS", "", mapOf("_ID" to id, "PTT" to ptt, "MESSAGE" to if (ptt) Js8Api.State.currentMessage else ""))
                return
            }
            "RIG.SET_TUNE" -> {
                val on = ApiMessage.toBool(message.value)
                val now = api.controller?.setTune(on) ?: run {
                    service(JS8EngineService.ACTION_TUNE) {
                        putExtra(JS8EngineService.EXTRA_TUNE_ON, on)
                        putExtra(JS8EngineService.EXTRA_TX_FREQ_HZ, api.offsetHz().toDouble())
                    }
                    Js8Api.State.tuning = on
                    on
                }
                reply("RIG.SET_TUNE", "", mapOf("_ID" to id, "value" to now))
                return
            }
            "RIG.TX_HALT" -> {
                api.controller?.halt() ?: service(JS8EngineService.ACTION_HALT_TX)
                // the PC answers with the TX button (monitorTxButton)
                reply("RIG.TX_HALT", "", mapOf("_ID" to id, "value" to bool("js8cfg_tx_enabled", true)))
                return
            }
            "RIG.GET_FREQ" -> {
                val dial = api.dialHz()
                val offset = api.offsetHz()
                api.statusUpdateWsjtxOnly()
                if (!api.jsonConflicts()) {
                    reply("RIG.FREQ", "", mapOf("_ID" to id, "FREQ" to dial + offset, "DIAL" to dial, "OFFSET" to offset))
                }
                return
            }
            "RIG.SET_FREQ" -> {
                ApiMessage.toIntOrNull(params["DIAL"])?.let { f ->
                    // setRig: the dial (Settings > Frequency) and the radio
                    if (f > 0) {
                        prefs().edit().putString("last_frequency", f.toString()).apply()
                        service(JS8EngineService.ACTION_SET_FREQUENCY) { putExtra(JS8EngineService.EXTRA_FREQUENCY_HZ, f.toLong()) }
                        refresh()
                    }
                }
                ApiMessage.toIntOrNull(params["OFFSET"])?.let { f ->
                    api.controller?.setOffset(f) ?: run {
                        prefs().edit().putString("js8cfg_tx_offset", f.toString()).apply()
                        service(JS8EngineService.ACTION_SET_TX_OFFSET) { putExtra(JS8EngineService.EXTRA_TX_OFFSET_HZ, f.toFloat()) }
                    }
                }
                // no reply, like the PC
            }

            // ---- STATION ----------------------------------------------------------------------
            "STATION.GET_CALLSIGN" -> { reply("STATION.CALLSIGN", api.myCall(), mapOf("_ID" to id)); return }
            "STATION.GET_GRID" -> { reply("STATION.GRID", api.myGrid(prefs()), mapOf("_ID" to id)); return }
            "STATION.SET_GRID" -> {
                api.setDynamicGrid(message.value)
                reply("STATION.GRID", api.myGrid(prefs()), mapOf("_ID" to id))
                return
            }
            "STATION.GET_INFO" -> { reply("STATION.INFO", api.myInfo(prefs()), mapOf("_ID" to id)); return }
            "STATION.SET_INFO" -> {
                api.setDynamicInfo(message.value)
                reply("STATION.INFO", api.myInfo(prefs()), mapOf("_ID" to id))
                return
            }
            "STATION.GET_STATUS" -> { reply("STATION.STATUS", api.myStatus(prefs()), mapOf("_ID" to id)); return }
            "STATION.SET_STATUS" -> {
                api.setDynamicStatus(message.value)
                reply("STATION.STATUS", api.myStatus(prefs()), mapOf("_ID" to id))
                return
            }
            "STATION.VERSION" -> { reply("STATION.VERSION", "", mapOf("_ID" to id, "VERSION" to Js8Api.VERSION)); return }
            "STATION.GET_OS" -> {
                reply("STATION.GET_OS", "", mapOf(
                    "OS_NAME" to "Android ${Build.VERSION.RELEASE}", "OS_KERNEL" to "linux",
                    "OS_KERNEL_VERSION" to (System.getProperty("os.version") ?: ""), "_ID" to id
                ))
                return
            }
            "STATION.GET_SPOT" -> { reply("STATION.SPOT", "", mapOf("value" to bool("psk_reporter", false), "_ID" to id)); return }
            "STATION.SET_SPOT" -> {
                setPref("psk_reporter", ApiMessage.toBool(message.value))
                refresh()
                reply("STATION.SPOT", "", mapOf("value" to bool("psk_reporter", false), "_ID" to id))
                return
            }
            "STATION.GET_CONFIG" -> {
                val speed = api.speed()
                reply("STATION.CONFIG", "", mapOf(
                    "_ID" to id,
                    "AUTO_REPLY" to bool("autoreply_enabled", false),
                    "JS8HB" to bool("js8cfg_hb", false),
                    "HBACK" to bool("js8cfg_hb_ack", false),
                    "MULTI_DECODER" to bool("js8cfg_multi", true),
                    "HB_INTERVAL" to hbInterval(),
                    "HB_TIMER_ACTIVE" to hbTimerActive(),
                    "MONITOR" to Js8Api.State.monitoring,
                    "TX_ENABLED" to bool("js8cfg_tx_enabled", true),
                    "SPEED" to speed,
                    "CAN_HB" to (speed == Js8Activity.SUBMODE_FAST || speed == Js8Activity.SUBMODE_NORMAL || speed == Js8Activity.SUBMODE_SLOW),
                    "AUTOREPLY_CONFIRMATION" to bool("js8cfg_autoreply_confirmation", true),
                    "MY_GROUPS" to groups(),
                    "AVOID_ALLCALL" to bool("js8cfg_avoid_allcall", false)
                ))
                return
            }
            "STATION.SET_AUTO_REPLY" -> {
                setPref("autoreply_enabled", ApiMessage.toBool(message.value)); refresh()
                reply("STATION.SET_AUTO_REPLY", "", mapOf("_ID" to id, "AUTO_REPLY" to bool("autoreply_enabled", false)))
                return
            }
            "STATION.SET_JS8HB" -> {
                setPref("js8cfg_hb", ApiMessage.toBool(message.value)); refresh()
                reply("STATION.SET_JS8HB", "", mapOf("_ID" to id, "JS8HB" to bool("js8cfg_hb", false)))
                return
            }
            "STATION.SET_HBACK" -> {
                setPref("js8cfg_hb_ack", ApiMessage.toBool(message.value)); refresh()
                reply("STATION.SET_HBACK", "", mapOf("_ID" to id, "HBACK" to bool("js8cfg_hb_ack", false)))
                return
            }
            "STATION.SET_MULTI_DECODER" -> {
                setPref("js8cfg_multi", ApiMessage.toBool(message.value)); refresh()
                reply("STATION.SET_MULTI_DECODER", "", mapOf("_ID" to id, "MULTI_DECODER" to bool("js8cfg_multi", true)))
                return
            }
            "STATION.SET_HB_INTERVAL" -> {
                // Station > Heartbeat interval (minutes); the engine service reschedules on the change
                val interval = ApiMessage.toIntOrNull(params["INTERVAL"]) ?: 0
                prefs().edit().putString("heartbeat_interval", interval.toString()).apply()
                reply("STATION.SET_HB_INTERVAL", "", mapOf("_ID" to id, "HB_INTERVAL" to hbInterval()))
                return
            }
            "STATION.SET_HB_TIMER" -> {
                // The HB button's timer (hbMacroButton->setChecked)
                val start = ApiMessage.toBool(message.value)
                com.js8call.example.service.HeartbeatTimer.on = start
                service(JS8EngineService.ACTION_HB_TIMER) { putExtra(JS8EngineService.EXTRA_HB_TIMER_ON, start) }
                refresh()
                reply("STATION.SET_HB_TIMER", "", mapOf("_ID" to id, "HB_TIMER_ACTIVE" to hbTimerActive()))
                return
            }
            "STATION.SEND_HB" -> {
                service(JS8EngineService.ACTION_SEND_HB)
                reply("STATION.SEND_HB", "", mapOf("_ID" to id))
                return
            }
            "STATION.SET_AUTOREPLY_CONFIRMATION" -> {
                val checked = ApiMessage.toBool(message.value)
                setPref("js8cfg_autoreply_confirmation", checked)
                reply("STATION.AUTOREPLY_CONFIRMATION", "", mapOf("_ID" to id, "AUTOREPLY_CONFIRMATION" to checked))
                return
            }
            // Easy JS8 sends autoreplies without asking, so there is never one waiting
            "STATION.AUTOREPLY_CONFIRM_RESPONSE" -> return
            "STATION.SET_GROUPS" -> {
                val newGroups = mutableListOf<String>()
                (params["GROUPS"] as? List<*>)?.forEach { v ->
                    val g = v?.toString()?.trim().orEmpty()
                    if (g.isEmpty() || !g.startsWith("@")) return@forEach
                    if (g.uppercase(Locale.US) in setOf("@APRSIS", "@JS8NET")) {
                        reply("STATION.SET_GROUPS", "", mapOf("_ID" to id, "ERROR" to "Group not allowed: $g"))
                        return
                    }
                    if (!Regex("@[A-Za-z0-9/]+").matches(g)) {
                        reply("STATION.SET_GROUPS", "", mapOf("_ID" to id, "ERROR" to "Invalid group name: $g"))
                        return
                    }
                    newGroups += g.uppercase(Locale.US)
                }
                prefs().edit().putString("my_groups", newGroups.joinToString(",")).apply()
                refresh()
                reply("STATION.SET_GROUPS", "", mapOf("_ID" to id, "GROUPS" to groups()))
                return
            }
            "STATION.SET_AVOID_ALLCALL" -> {
                setPref("js8cfg_avoid_allcall", ApiMessage.toBool(message.value)); refresh()
                reply("STATION.SET_AVOID_ALLCALL", "", mapOf("_ID" to id, "AVOID_ALLCALL" to bool("js8cfg_avoid_allcall", false)))
                return
            }

            // ---- RX ---------------------------------------------------------------------------
            "RX.GET_CALL_ACTIVITY" -> {
                val now = System.currentTimeMillis()
                val aging = api.int(prefs(), "js8cfg_callsign_aging", 0)
                val calls = linkedMapOf<String, Any?>("_ID" to id)
                api.activity?.callActivity?.values?.toList()?.forEach { cd ->
                    if (aging > 0 && (now - cd.utcMs) / 60_000 >= aging) return@forEach
                    calls[cd.call] = mapOf("SNR" to cd.snr, "GRID" to cd.grid, "UTC" to cd.utcMs)
                }
                reply("RX.CALL_ACTIVITY", "", calls)
                return
            }
            "RX.GET_CALL_SELECTED" -> { reply("RX.CALL_SELECTED", Js8Api.State.selectedCall.orEmpty(), mapOf("_ID" to id)); return }
            "RX.GET_BAND_ACTIVITY" -> {
                val dial = api.dialHz()
                val offsets = linkedMapOf<String, Any?>("_ID" to id)
                api.activity?.bandActivity?.toList()?.forEach { (offset, list) ->
                    val d = list.lastOrNull() ?: return@forEach
                    offsets[offset.toString()] = mapOf(
                        "FREQ" to dial + d.offset, "DIAL" to dial, "OFFSET" to d.offset,
                        "TEXT" to d.text, "SNR" to d.snr, "UTC" to d.utcMs
                    )
                }
                reply("RX.BAND_ACTIVITY", "", offsets)
                return
            }
            "RX.GET_TEXT" -> { reply("RX.TEXT", rxText().takeLast(1024), mapOf("_ID" to id)); return }
            "RX.GET_FREE_OFFSETS" -> { freeOffsets(id, params); return }
            "RX.GET_FILTER" -> { reply("RX.FILTER", "", filterState(id)); return }
            "RX.SET_FILTER" -> {
                val center = ApiMessage.toIntOrNull(params["CENTER"])
                val width = ApiMessage.toIntOrNull(params["WIDTH"])
                setFilter(center, width, null)
                reply("RX.FILTER", "", filterState(id))
                return
            }
            "RX.SET_FILTER_ENABLED" -> {
                setFilter(null, null, ApiMessage.toBool(message.value))
                reply("RX.SET_FILTER_ENABLED", "", mapOf("_ID" to id, "ENABLED" to filterEnabled()))
                return
            }

            // ---- TX ---------------------------------------------------------------------------
            "TX.GET_TEXT" -> { reply("TX.TEXT", api.controller?.typingText().orEmpty().takeLast(1024), mapOf("_ID" to id)); return }
            "TX.SET_TEXT" -> {
                api.controller?.setTypingText(message.value)
                reply("TX.TEXT", api.controller?.typingText().orEmpty().takeLast(1024), mapOf("_ID" to id))
                return
            }
            "TX.SEND_MESSAGE" -> {
                val text = message.value
                if (text.isNotEmpty()) {
                    api.host?.sendMessage(text) ?: LocalBroadcastManager.getInstance(context).sendBroadcast(
                        Intent(JS8EngineService.ACTION_QUEUE_TX).putExtra(JS8EngineService.EXTRA_QUEUE_TX_TEXT, text))
                    return
                }
            }
            "TX.GET_QUEUE_DEPTH" -> {
                var depth = api.host?.queueDepth() ?: 0
                if (Js8Api.State.transmitting && depth == 0) depth = 1
                reply("TX.QUEUE_DEPTH", "", mapOf("_ID" to id, "DEPTH" to depth))
                return
            }

            // ---- MODE -------------------------------------------------------------------------
            "MODE.GET_SPEED" -> { reply("MODE.SPEED", "", mapOf("_ID" to id, "SPEED" to api.speed())); return }
            "MODE.SET_SPEED" -> {
                // Normal 0, Fast 1, 40 2, Slow 4; JS8 60 (8) cannot be sent here, so it is left alone
                val speed = if (params.containsKey("SPEED")) ApiMessage.toIntOrNull(params["SPEED"]) else null
                if (speed != null && speed in setOf(0, 1, 2, 4)) {
                    prefs().edit().putInt("tx_submode", speed).apply()
                    refresh()
                }
                reply("MODE.SET_SPEED", "", mapOf("_ID" to id, "SPEED" to api.speed()))
                return
            }

            // ---- INBOX ------------------------------------------------------------------------
            "INBOX.GET_MESSAGES" -> { inboxMessages(id, params["CALLSIGN"]?.toString().orEmpty()); return }
            "INBOX.STORE_MESSAGE" -> {
                val call = params["CALLSIGN"]?.toString().orEmpty()
                val text = params["TEXT"]?.toString().orEmpty()
                if (call.isEmpty() || text.isEmpty()) return
                // Held for them to collect, from us (addCommandToStorage STORE)
                val my = api.myCall()
                scope.launch {
                    val mid = try { com.js8call.example.data.Js8Mailbox.store(context, call, my, text, my) } catch (e: Exception) { -1L }
                    reply("INBOX.MESSAGE", "", mapOf("_ID" to id, "ID" to mid))
                }
                return
            }

            // ---- WINDOW -----------------------------------------------------------------------
            "WINDOW.RAISE" -> { api.host?.raise(); return }
        }
        Log.d("Js8Api", "Unable to process networkMessage: $type")
    }

    private fun hbInterval() = api.int(prefs(), "heartbeat_interval", 0)
    private fun hbTimerActive() = com.js8call.example.service.HeartbeatTimer.on

    private fun groups(): List<String> = prefs().getString("my_groups", "").orEmpty().split(',', ' ')
        .map { it.trim().uppercase(Locale.US) }.filter { it.startsWith("@") }

    // ---- Message Window text (textEditRX) ---------------------------------------------------------

    private fun rxText(): String = api.activity?.rxLines?.toList()?.joinToString("\n") { line ->
        "${clockFormat.format(Date(line.utcMs))} - (${line.offset}) - ${line.text.toString().trimEnd()}"
    }.orEmpty()

    // ---- Filter (WideGraph) ------------------------------------------------------------------------

    private fun filterEnabled() = runCatching { prefs().getBoolean("js8cfg_filter_enabled", true) }.getOrDefault(true)
    private fun filterCenter() = runCatching { prefs().getInt("js8cfg_filter_center", 1500) }.getOrDefault(1500)
    private fun filterWidth() = runCatching { prefs().getInt("js8cfg_filter_width", 2000) }.getOrDefault(2000)

    private fun filterState(id: Long) = mapOf("_ID" to id, "CENTER" to filterCenter(), "WIDTH" to filterWidth(), "ENABLED" to filterEnabled())

    private fun setFilter(center: Int?, width: Int?, enabled: Boolean?) {
        val c = api.controller
        if (c != null) { c.setFilter(center, width, enabled); return }
        // WideGraph.ui ranges: center 120-4880, width 120-5000
        prefs().edit().apply {
            center?.let { putInt("js8cfg_filter_center", it.coerceIn(120, 4880)) }
            width?.let { putInt("js8cfg_filter_width", it.coerceIn(120, 5000)) }
            enabled?.let { putBoolean("js8cfg_filter_enabled", it) }
        }.apply()
    }

    // ---- RX.GET_FREE_OFFSETS ------------------------------------------------------------------------

    private fun freeOffsets(id: Long, params: Map<String, Any?>) {
        val speed = ApiMessage.toIntOrNull(params["SPEED"]) ?: api.speed()
        val low = ApiMessage.toIntOrNull(params["LOW"]) ?: 500
        val high = ApiMessage.toIntOrNull(params["HIGH"]) ?: 2500
        val bw = Js8Activity.bandwidthHz(speed).toInt()
        val now = System.currentTimeMillis()
        // Occupied spans: each recent signal's bandwidth plus the one asked for; older than 30 s = clear
        val occupied = api.activity?.bandActivity?.toList().orEmpty().mapNotNull { (occ, list) ->
            val last = list.lastOrNull() ?: return@mapNotNull null
            if ((now - last.utcMs) / 1000 >= 30) return@mapNotNull null
            val half = bw / 2 + Js8Activity.bandwidthHz(last.submode).toInt() / 2
            (occ - half) to (occ + half)
        }.sortedWith(compareBy({ it.first }, { it.second }))
        val merged = mutableListOf<Pair<Int, Int>>()
        for (iv in occupied) {
            if (merged.isEmpty() || merged.last().second < iv.first) merged += iv
            else merged[merged.size - 1] = merged.last().first to maxOf(merged.last().second, iv.second)
        }
        val free = mutableListOf<Map<String, Int>>()
        var cursor = low
        for ((start, end) in merged) {
            if (end <= cursor) continue
            if (start > cursor) {
                val gapHigh = minOf(start, high)
                val width = gapHigh - cursor
                if (cursor < high && width >= bw) free += mapOf("LOW" to cursor, "HIGH" to gapHigh, "WIDTH" to width)
            }
            cursor = maxOf(cursor, end)
            if (cursor >= high) break
        }
        if (cursor < high && high - cursor >= bw) free += mapOf("LOW" to cursor, "HIGH" to high, "WIDTH" to high - cursor)
        reply("RX.FREE_OFFSETS", "", mapOf("_ID" to id, "FREE" to free, "SPEED" to speed, "BANDWIDTH" to bw, "LOW" to low, "HIGH" to high))
    }

    // ---- INBOX.GET_MESSAGES -------------------------------------------------------------------------

    /**
     * Messages from CALLSIGN and held for it (all with none), newest first, as the PC's inbox records:
     * type STORE, READ or UNREAD, params UTC / TO / FROM / PATH / FREQ / DIAL / OFFSET / CMD / SNR / TEXT.
     */
    private fun inboxMessages(id: Long, callsign: String) {
        val call = callsign.ifEmpty { "%" }
        val myCall = api.myCall()
        val dial = api.dialHz()
        scope.launch {
            val all = try { MessageRepository.getInstance(context).getAllMessagesSync() } catch (e: Exception) { return@launch }
            // Inbox::fetchForCall: STORE to the callsign, READ / UNREAD from it
            val list = com.js8call.example.js8ui.Js8Inbox.incoming(all)
                .filter { m ->
                    val held = m.direction == MessageEntity.DIRECTION_HELD
                    call == "%" || (if (held) m.conversationId else m.senderCallsign ?: m.conversationId).equals(call, ignoreCase = true)
                }
                .map { m ->
                    val held = m.direction == MessageEntity.DIRECTION_HELD
                    val from = m.senderCallsign ?: m.conversationId
                    val offset = m.frequency?.toInt() ?: 0
                    mapOf(
                        "type" to if (held) "STORE" else if (m.isRead) "READ" else "UNREAD",
                        "value" to "",
                        "params" to mapOf(
                            "UTC" to utcFormat.format(Date(m.timestamp)),
                            "TO" to if (held || m.conversationId.startsWith("@")) m.conversationId else myCall,
                            "FROM" to from, "PATH" to (m.relayPath ?: from), "FREQ" to dial + offset, "DIAL" to dial,
                            "OFFSET" to offset, "CMD" to " MSG ", "SNR" to (m.snr ?: 0), "TEXT" to m.text,
                            "_ID" to (m.timestamp - ApiMessage.EPOCH).toString()
                        )
                    )
                }
            reply("INBOX.MESSAGES", "", mapOf("_ID" to id, "MESSAGES" to list))
        }
    }
}
