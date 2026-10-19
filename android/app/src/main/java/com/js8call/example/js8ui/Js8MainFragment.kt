package com.js8call.example.js8ui

import android.Manifest
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.js8call.example.R
import com.js8call.example.model.DecodedMessage
import com.js8call.example.model.EngineState
import com.js8call.example.model.TransmitState
import com.js8call.example.service.JS8EngineService
import com.js8call.example.ui.AudioDevices
import com.js8call.example.ui.DecodeViewModel
import com.js8call.example.ui.MonitorViewModel
import com.js8call.example.ui.TransmitViewModel
import com.js8call.example.ui.WaterfallView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Easy JS8 main screen: copy of the JS8Call 3.0.3 desktop window.
 * Upright: vertical mode. Sideways: the "Sideways layout" setting picks PC mode or Swipe.
 * All three layouts use the same view ids, so the code below serves all of them.
 */
class Js8MainFragment : Fragment() {

    companion object {
        const val PREF_LANDSCAPE_LAYOUT = "landscape_layout"
        /** Swipe page kept across rotation / returning from the Menu page. */
        private var swipePage = 1
        /** checkStartupWarnings runs once per app start, not on each rotation. */
        private var startupChecked = false
    }

    private lateinit var monitorVm: MonitorViewModel
    private lateinit var decodeVm: DecodeViewModel
    private lateinit var transmitVm: TransmitViewModel
    private lateinit var activityVm: Js8ActivityViewModel

    // Not in the upright layout: Frequency and Offset are in the Settings window there
    private var freqDisplay: TextView? = null
    private var offsetSlider: SeekBar? = null
    private var offsetValue: TextView? = null
    private lateinit var callsign: TextView
    private lateinit var clock: TextView
    private lateinit var btnRx: View
    private lateinit var btnTx: View
    private lateinit var bandActivity: Js8TableView
    private lateinit var callActivity: Js8TableView
    private lateinit var inbox: Js8TableView
    private lateinit var messageWindow: TextView
    private lateinit var typingBox: EditText
    private lateinit var statusState: TextView
    private lateinit var statusFreq: TextView
    private lateinit var statusAutoReply: TextView
    private lateinit var progress: Js8CycleProgressView
    private lateinit var waterfall: WaterfallView
    private lateinit var ruler: Js8RulerView
    private lateinit var levelMeter: Js8LevelMeterView

    private var dialHz: Long = 14_078_000L
    private var selectedCall: String? = null

    private val ui = Handler(Looper.getMainLooper())
    private val clockFormat = SimpleDateFormat("yyyy MMM dd HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val tick = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            clock.text = clockFormat.format(Date(now))
            progress.seconds = ((now / 1000) % progress.period).toInt()
            // Ages ("now", "15s", "2m") move on even without new decodes
            if ((now / 1000) % 5 == 0L) renderActivity()
            ui.postDelayed(this, 1000 - now % 1000)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val sideways = PreferenceManager.getDefaultSharedPreferences(requireContext())
            .getString(PREF_LANDSCAPE_LAYOUT, "pc")
        val layout = when {
            !landscape -> R.layout.fragment_js8_main
            sideways == "swipe" -> R.layout.fragment_js8_swipe
            else -> R.layout.fragment_js8_pc
        }
        return inflater.inflate(layout, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        monitorVm = ViewModelProvider(requireActivity())[MonitorViewModel::class.java]
        decodeVm = ViewModelProvider(requireActivity())[DecodeViewModel::class.java]
        transmitVm = ViewModelProvider(requireActivity())[TransmitViewModel::class.java]
        activityVm = ViewModelProvider(requireActivity())[Js8ActivityViewModel::class.java]

        freqDisplay = view.findViewById(R.id.js8_freq_display)
        offsetSlider = view.findViewById(R.id.js8_offset_slider)
        offsetValue = view.findViewById(R.id.js8_offset_value)
        if (resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE) {
            // Upright: Offset lives in the Settings window, not under the waterfall
            view.findViewById<View?>(R.id.js8_wf_offset_title)?.visibility = View.GONE
            view.findViewById<View?>(R.id.js8_wf_offset_box)?.visibility = View.GONE
        }
        callsign = view.findViewById(R.id.js8_callsign)
        clock = view.findViewById(R.id.js8_clock)
        btnRx = view.findViewById(R.id.js8_btn_rx)
        btnTx = view.findViewById(R.id.js8_btn_tx)
        bandActivity = view.findViewById(R.id.js8_band_activity)
        callActivity = view.findViewById(R.id.js8_call_activity)
        inbox = view.findViewById(R.id.js8_inbox_table)
        messageWindow = view.findViewById(R.id.js8_message_window)
        typingBox = view.findViewById(R.id.js8_typing_box)
        statusState = view.findViewById(R.id.js8_status_state)
        statusFreq = view.findViewById(R.id.js8_status_freq)
        statusAutoReply = view.findViewById(R.id.js8_status_autoreply)
        progress = view.findViewById(R.id.js8_status_progress)
        waterfall = view.findViewById(R.id.js8_waterfall)
        ruler = view.findViewById(R.id.js8_ruler)
        levelMeter = view.findViewById(R.id.js8_level_meter)

        setUpSwipe(view)
        setUpTables()
        setUpHeader()
        setUpButtons(view)
        setUpWaterfall()
        observe()
        if (!startupChecked) {
            startupChecked = true
            // AUTO starts as Station > Turn autoreply on at startup, which needs callsign and grid
            // (Configuration::autoreply_on_at_startup). Before the split that checkbox was the live
            // switch itself, so an install without the new key keeps its old setting.
            val p = prefs()
            val e = p.edit()
            if (!p.contains(Js8SettingsSchema.AUTOREPLY_STARTUP_KEY)) {
                e.putBoolean(Js8SettingsSchema.AUTOREPLY_STARTUP_KEY, p.getBoolean(Js8SettingsSchema.AUTOREPLY_KEY, false))
            }
            val startAuto = (p.getBoolean(Js8SettingsSchema.AUTOREPLY_STARTUP_KEY, p.getBoolean(Js8SettingsSchema.AUTOREPLY_KEY, false))) &&
                p.getString("callsign", "").orEmpty().isNotBlank() && p.getString("grid", "").orEmpty().isNotBlank()
            e.putBoolean(Js8SettingsSchema.AUTOREPLY_KEY, startAuto).apply()
            // UI_Constructor::checkStartupWarnings: no callsign or grid opens Station
            view.post { if (isAdded) ensureCallsignSet(alert = false) }
        }
    }

    override fun onResume() {
        super.onResume()
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        callsign.text = prefs.getString("callsign", "").orEmpty().ifBlank { "NOCALL" }
        statusAutoReply.text = if (prefs.getBoolean("autoreply_enabled", false)) "Auto Reply: On" else "Auto Reply: Off"
        dialHz = prefs.getString("last_frequency", "14078000")?.toLongOrNull() ?: 14_078_000L
        renderFrequency()
        // Current speed (Settings > Mode, Menu > Mode): offset dial width, status bar label, cycle
        val submode = Js8Activity.txSubmode(prefs.getInt(Js8SettingsSchema.TX_SUBMODE_KEY, 0))
        waterfall.bandwidthHz = Js8Activity.bandwidthHz(submode)
        requireView().findViewById<TextView?>(R.id.js8_status_mode)?.text = Js8Activity.modeLabel(submode)
        progress.period = Js8Activity.periodSeconds(submode)
        renderTransmitEnabled() // Station > SWL (Listening only) may have changed
        // Offset from Settings (or saved last time)
        val savedOffset = prefs.getString(Js8SettingsSchema.TX_OFFSET_KEY, null)?.toFloatOrNull()
        if (savedOffset != null && savedOffset != monitorVm.getTxOffset()) {
            offsetSlider?.progress = savedOffset.toInt()
            applyOffset(savedOffset)
        }
        waterfall.txOffsetHz = monitorVm.getTxOffset() // a rebuilt screen starts on 1500 otherwise
        ui.post(tick)
    }

    override fun onPause() {
        ui.removeCallbacks(tick)
        super.onPause()
    }

    // ---- Swipe layout ---------------------------------------------------------------------

    private fun setUpSwipe(view: View) {
        view.findViewById<Js8ZoomLayout?>(R.id.js8_header_page)?.apply {
            zoomable = false
            designWidthDp = 620f
            designHeightDp = 196f
        }
        val pager = view.findViewById<Js8PagerLayout?>(R.id.js8_pager) ?: return
        val tabs = listOf(R.id.js8_tab_0, R.id.js8_tab_1, R.id.js8_tab_2, R.id.js8_tab_3, R.id.js8_tab_4)
            .map { view.findViewById<TextView>(it) }
        fun renderTabs(page: Int) = tabs.forEachIndexed { i, t ->
            if (i == page) t.setBackgroundResource(R.drawable.js8_shape_blue) else t.background = null
            t.setTextColor(if (i == page) Color.BLACK else ContextCompat.getColor(requireContext(), R.color.js8_frame_text))
        }
        tabs.forEachIndexed { i, t -> t.setOnClickListener { pager.showPage(i) } }
        pager.onPageChanged = { page ->
            renderTabs(page)
            swipePage = page
        }
        // Before the first drawing, so the page matches the lit tab from the start (onLayout places it)
        pager.showPage(swipePage, animate = false)
        renderTabs(swipePage)
    }

    // ---- Tables -------------------------------------------------------------------------

    private fun setUpTables() {
        // Columns and defaults: JS8_UI/mainwindow.cpp buildShowColumnsMenu()
        bandActivity.setColumns("band", listOf(
            Js8TableView.Column("offset", "Offset", 48, numeric = true, menuLabel = "Frequency Offset"),
            Js8TableView.Column("timestamp", "Age", 36, numeric = true, menuLabel = "Last heard timestamp"),
            Js8TableView.Column("snr", "SNR", 36, numeric = true),
            Js8TableView.Column("tdrift", "Time Delta", 70, defaultVisible = false, numeric = true),
            Js8TableView.Column("submode", "Mode Speed", 76, defaultVisible = false),
            Js8TableView.Column("message", "Message(s)", 300, stretch = true)
        ))
        callActivity.setColumns("call", listOf(
            Js8TableView.Column("icon", "", 22, menuLabel = "Message / ACK / CQ icon"),
            Js8TableView.Column("callsign", "Callsigns", 100),
            Js8TableView.Column("timestamp", "Age", 36, numeric = true, menuLabel = "Last heard timestamp"),
            Js8TableView.Column("snr", "SNR", 36, numeric = true),
            Js8TableView.Column("offset", "Offset", 48, numeric = true, menuLabel = "Frequency Offset"),
            Js8TableView.Column("tdrift", "Time Delta", 70, defaultVisible = false, numeric = true),
            Js8TableView.Column("submode", "Mode Speed", 76, defaultVisible = false),
            Js8TableView.Column("grid", "Grid", 56, defaultVisible = false, menuLabel = "Grid Locator"),
            Js8TableView.Column("distance", "Distance", 64, defaultVisible = false, numeric = true),
            Js8TableView.Column("azimuth", "Azimuth", 60, defaultVisible = false, numeric = true),
            Js8TableView.Column("log", "✓", 24, menuLabel = "Worked Before"),
            Js8TableView.Column("logName", "Name", 70, menuLabel = "Logged Name"),
            Js8TableView.Column("logComment", "Comment", 120, stretch = true, menuLabel = "Logged Comment")
        ))
        inbox.setColumns("inbox", listOf(
            Js8TableView.Column("flag", "⚑", 24),
            Js8TableView.Column("id", "ID", 40, numeric = true),
            Js8TableView.Column("date", "Date", 120),
            Js8TableView.Column("freq", "Frequency", 80),
            Js8TableView.Column("from", "From", 70),
            Js8TableView.Column("to", "To", 70),
            Js8TableView.Column("message", "Message", 200, stretch = true)
        ))
        // Call Activity click selects a callsign; Band Activity click selects the newest call at that offset
        // (UI_Constructor::callsignSelected). Only one table holds a selection at a time.
        callActivity.onRowClick = { row ->
            bandActivity.selectedRowId = null
            selectedCall = if (callActivity.selectedRowId == null) null else row.id
            renderSelection()
        }
        // Double-click: put the callsign in the Typing Box (on_tableWidgetCalls_cellDoubleClicked)
        callActivity.onRowDoubleClick = { row ->
            callActivity.selectedRowId = row.id
            selectedCall = row.id
            renderSelection()
            addMessageText(row.id + " ", send = false)
        }
        bandActivity.onRowClick = { row ->
            callActivity.selectedRowId = null
            selectedCall = if (bandActivity.selectedRowId == null) null
                else row.id.removePrefix("o").toIntOrNull()?.let { activityVm.activity.callAtOffset(it) }
            renderSelection()
        }
        // Double-click: move the TX offset there and print its text in the Message Window
        bandActivity.onRowDoubleClick = { row ->
            val off = row.id.removePrefix("o").toIntOrNull()
            if (off != null) {
                offsetSlider?.progress = off
                applyOffset(off.toFloat())
                val cfg = Js8Activity.Config(prefs())
                val (text, last) = activityVm.activity.bandRowText(off, System.currentTimeMillis(), agingActivity(), true, true, cfg.eot)
                if (text.isNotBlank() && last != null) {
                    activityVm.activity.writeRx(text, off, last.utcMs, isTx = false, newLine = true, isLast = true, cfg = cfg)
                    activityVm.changed()
                }
            }
        }
        inbox.onRowClick = { row -> showInboxMessage(row.id) }
    }

    private fun prefs() = PreferenceManager.getDefaultSharedPreferences(requireContext())

    private fun intPref(key: String, default: Int) = prefs().all[key]?.toString()?.toIntOrNull() ?: default
    private fun boolPref(key: String, default: Boolean) = prefs().all[key]?.toString()?.toBooleanStrictOrNull() ?: default
    private fun agingActivity() = intPref("js8cfg_activity_aging", 2)
    private fun agingCalls() = intPref("js8cfg_callsign_aging", 0)

    private fun colorPref(key: String, default: String) =
        runCatching { Color.parseColor(prefs().getString(key, null) ?: default) }.getOrDefault(Color.parseColor(default))

    private fun words(key: String) = prefs().getString(key, "").orEmpty().uppercase()
        .split(',', ' ').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    /** displayBandActivity + displayCallActivity + the Message Window. */
    private fun renderActivity() {
        if (view == null) return
        val a = activityVm.activity
        val cfg = Js8Activity.Config(prefs())
        val now = System.currentTimeMillis()
        val showHb = boolPref("js8cfg_show_hb_acks", false)
        val cqColor = Color.parseColor("#66FF66")       // colorCQ
        val myCallColor = Color.parseColor("#FF6666")   // colorMyCall
        val primary = colorPref("js8cfg_ui_primary_bg", "#F1C40F")
        val secondary = colorPref("js8cfg_ui_secondary_bg", "#FFFF66")
        val primaryWords = words("js8cfg_ui_primary_words")
        val secondaryWords = words("js8cfg_ui_secondary_words")
        val selectedOffset = bandActivity.selectedRowId?.removePrefix("o")?.toIntOrNull()

        // Band Activity
        val bandRows = a.bandActivity.keys.sorted().mapNotNull { off ->
            val (text, last) = a.bandRowText(off, now, agingActivity(), showHb, off == selectedOffset, cfg.eot)
            if (text.isBlank() || last == null) return@mapNotNull null
            val wordSet = text.uppercase().split(Regex("[:> ]+")).filter { it.isNotEmpty() }.toSet()
            val bg = when {
                (wordSet intersect primaryWords).isNotEmpty() -> primary
                (wordSet intersect secondaryWords).isNotEmpty() -> secondary
                "CQ" in wordSet -> cqColor
                cfg.isMyCallIncluded(text) -> myCallColor
                else -> null
            }
            val snr = Js8Activity.formatSnr(last.snr)
            Js8TableView.Row(
                id = "o$off",
                values = mapOf(
                    "offset" to "$off Hz",
                    "timestamp" to Js8Activity.since(last.utcMs, now),
                    "snr" to if (snr.isEmpty()) "" else "$snr dB",
                    "tdrift" to "${(last.tdrift * 1000).toInt()} ms",
                    "submode" to Js8Activity.submodeLetter(last.submode),
                    "message" to text
                ),
                sortValues = mapOf("offset" to off, "timestamp" to -last.utcMs, "snr" to last.snr, "tdrift" to last.tdrift),
                bg = bg, center = setOf("timestamp", "submode")
            )
        }
        bandActivity.setRows(bandRows)

        // Call Activity: @ALLCALL, my groups, then heard callsigns
        val grid = prefs().getString("grid", "").orEmpty()
        val miles = boolPref("js8cfg_miles", false)
        val blocked = cfg.blocklist
        val calls = a.callActivity.values.filter { d ->
            d.call == selectedCall || d.call in unreadFrom || agingCalls() == 0 || (now - d.utcMs) / 60_000 < agingCalls()
        }
        callActivity.setHeaderLabel("callsign", if (calls.isEmpty()) "Callsigns" else "Callsigns (${calls.size})")
        val groupRows = buildList {
            if (!cfg.avoidAllcall) add(Js8TableView.Row("@ALLCALL", mapOf("callsign" to "@ALLCALL")))
            cfg.groups.sorted().forEach { add(Js8TableView.Row(it, mapOf("callsign" to it))) }
        }
        val callRows = calls.sortedBy { it.call }.map { d ->
            val hasMessage = d.call in unreadFrom
            val hasCq = d.cqMs > 0 && (now - d.cqMs) / 60_000 < 5
            val hasAck = d.ackMs > 0
            val isBlocked = d.call in blocked || Js8Activity.baseCall(d.call) in blocked
            val icon = when {
                isBlocked -> "✕"; hasMessage -> "⚑"; hasAck -> "★"; hasCq -> "☎"; else -> ""
            }
            val vec = Js8Activity.vector(grid, d.grid)
            val dist = vec?.first?.let { km -> if (miles) "${(km * 0.621371).roundToInt()} mi" else "${km.roundToInt()} km" }.orEmpty()
            val az = vec?.second?.let { "${it.roundToInt()}°" }.orEmpty()
            val snr = Js8Activity.formatSnr(d.snr)
            Js8TableView.Row(
                id = d.call,
                values = mapOf(
                    "icon" to icon, "callsign" to d.call,
                    "timestamp" to Js8Activity.since(d.utcMs, now),
                    "snr" to if (snr.isEmpty()) "" else "$snr dB",
                    "offset" to "${d.offset} Hz",
                    "tdrift" to "${(d.tdrift * 1000).toInt()} ms",
                    "submode" to Js8Activity.submodeLetter(d.submode),
                    "grid" to d.grid.take(4), "distance" to dist, "azimuth" to az,
                    "log" to "", "logName" to "", "logComment" to ""
                ),
                sortValues = mapOf("timestamp" to -d.utcMs, "snr" to d.snr, "offset" to d.offset,
                    "distance" to (vec?.first ?: Double.MAX_VALUE), "azimuth" to (vec?.second ?: 999.0)),
                bold = hasMessage,
                bg = when {
                    d.call in primaryWords -> primary
                    d.call in secondaryWords -> secondary
                    hasCq -> cqColor
                    else -> null
                },
                colors = if (isBlocked) mapOf("icon" to Color.RED) else emptyMap(),
                center = setOf("icon", "timestamp", "submode", "log")
            )
        }
        // messages pinned to the top (displayCallActivity)
        callActivity.setRows(groupRows + callRows.sortedBy { if (it.bold) 0 else 1 })

        // Message Window: received lines in the RX color, our transmissions in the TX color
        val rxFg = colorPref("js8cfg_ui_rx_fg", "#000000")
        val txFg = colorPref("js8cfg_ui_tx_fg", "#FF0000")
        val sb = android.text.SpannableStringBuilder()
        a.rxLines.forEach { line ->
            if (sb.isNotEmpty()) sb.append('\n')
            val start = sb.length
            sb.append(clockFormat.format(Date(line.utcMs)).substring(12)).append(" - (").append(line.offset.toString())
                .append(") - ").append(line.text.toString().trimEnd())
            sb.setSpan(android.text.style.ForegroundColorSpan(if (line.isTx) txFg else rxFg), start, sb.length, 0)
        }
        messageWindow.text = sb
        messageWindow.post {
            val layout = messageWindow.layout ?: return@post
            val y = layout.height - messageWindow.height + messageWindow.paddingTop + messageWindow.paddingBottom
            if (y > 0) messageWindow.scrollTo(0, y)
        }
    }

    // ---- Inbox (JS8Android message store: MSG messages received) -----------------------

    private val repository by lazy { com.js8call.example.data.MessageRepository.getInstance(requireContext()) }
    private var inboxMessages: List<com.js8call.example.data.MessageEntity> = emptyList()
    private var unreadFrom: Set<String> = emptySet()
    private val inboxDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

    private fun observeInbox() {
        repository.getAllMessages().observe(viewLifecycleOwner) { all ->
            inboxMessages = all.filter { it.direction == com.js8call.example.data.MessageEntity.DIRECTION_INCOMING }
            val my = prefs().getString("callsign", "").orEmpty().uppercase()
            inbox.setRows(inboxMessages.map { m ->
                Js8TableView.Row(
                    id = m.id.toString(),
                    values = mapOf(
                        "flag" to if (m.isRead) "" else "⚑",
                        "id" to m.id.toString(),
                        "date" to inboxDate.format(Date(m.timestamp)),
                        "freq" to (m.frequency?.let { "${it.roundToInt()} Hz" } ?: ""),
                        "from" to (m.relayPath ?: m.senderCallsign ?: m.conversationId),
                        "to" to if (m.conversationId.startsWith("@")) m.conversationId else my,
                        "message" to m.text
                    ),
                    sortValues = mapOf("id" to m.id, "date" to m.timestamp),
                    bold = !m.isRead, center = setOf("flag")
                )
            })
            // Call Activity flag: stations with unread messages (displayCallActivity hasMessage)
            unreadFrom = inboxMessages.filter { !it.isRead }.map { it.senderCallsign ?: it.conversationId }.toSet()
            renderActivity()
        }
    }

    private fun showInboxMessage(id: String) {
        val m = inboxMessages.firstOrNull { it.id.toString() == id } ?: return
        requireView().findViewById<TextView>(R.id.js8_inbox_message).text = m.text
        if (!m.isRead) viewLifecycleOwner.lifecycleScope.launch { repository.markMessageAsRead(m.id) }
    }

    /** Inbox Reply: "FROM MSG [MESSAGE]" into the Typing Box (MessagePanel::on_replyPushButton_clicked). */
    private fun inboxReply() {
        val id = inbox.selectedRowId ?: return
        val m = inboxMessages.firstOrNull { it.id.toString() == id } ?: return
        val path = m.relayPath ?: m.senderCallsign ?: m.conversationId
        val typed = requireView().findViewById<EditText>(R.id.js8_inbox_reply_text).text.toString().trim()
        addMessageText("$path MSG ${typed.ifEmpty { "[MESSAGE]" }}", send = false)
    }

    // ---- Header -------------------------------------------------------------------------

    private fun setUpHeader() {
        offsetSlider?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, value: Int, fromUser: Boolean) {
                offsetValue?.text = "$value Hz"
                if (fromUser) applyOffset(value.toFloat())
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
        btnRx.setOnClickListener {
            val running = monitorVm.status.value?.state.let { it == EngineState.RUNNING || it == EngineState.STARTING }
            if (running) stopMonitoring() else startMonitoring()
        }
        // TX: transmitter enabled. When off, queued messages wait (MainActivity checks the setting).
        btnTx.isActivated = boolPref("js8cfg_tx_enabled", true)
        btnTx.setOnClickListener {
            val on = !boolPref("js8cfg_tx_enabled", true)
            prefs().edit().putBoolean("js8cfg_tx_enabled", on).apply()
            it.isActivated = on
            if (on) processTxQueue()
        }
        // SPOT: spotting to reporting networks (Settings > Reporting)
        val spot = requireView().findViewById<View>(R.id.js8_btn_spot)
        spot.isActivated = boolPref("psk_reporter", false)
        spot.setOnClickListener {
            val on = !boolPref("psk_reporter", false)
            prefs().edit().putBoolean("psk_reporter", on).apply()
            it.isActivated = on
        }
        requireView().findViewById<View>(R.id.js8_btn_log).setOnClickListener {
            android.widget.Toast.makeText(requireContext(), "Log QSO is not built yet", android.widget.Toast.LENGTH_SHORT).show()
        }
        // TUNE: steady tone at the TX offset until pressed again
        requireView().findViewById<View>(R.id.js8_btn_tune).setOnClickListener {
            val on = !it.isActivated
            it.isActivated = on
            requireContext().startService(Intent(requireContext(), JS8EngineService::class.java).apply {
                action = JS8EngineService.ACTION_TUNE
                putExtra(JS8EngineService.EXTRA_TUNE_ON, on)
                putExtra(JS8EngineService.EXTRA_TX_FREQ_HZ, monitorVm.getTxOffset().toDouble())
            })
        }
        // Settings button, where the PC has its mode button (Rob, 2026-10-19)
        requireView().findViewById<View>(R.id.js8_btn_mode).setOnClickListener { openSettings() }
        // Vertical and Swipe: the ⋮ button. PC mode: the menu bar items.
        val openMenu = View.OnClickListener { findNavController().navigate(R.id.navigation_js8_menu) }
        requireView().findViewById<View?>(R.id.js8_menu_button)?.setOnClickListener(openMenu)
        listOf(R.id.js8_menubar_file, R.id.js8_menubar_configurations, R.id.js8_menubar_mode, R.id.js8_menubar_log,
            R.id.js8_menubar_view, R.id.js8_menubar_control, R.id.js8_menubar_help)
            .forEach { requireView().findViewById<View?>(it)?.setOnClickListener(openMenu) }
    }

    private fun applyOffset(hz: Float) {
        monitorVm.setTxOffset(hz)
        transmitVm.setTxOffset(hz)
        waterfall.txOffsetHz = hz
        // Saved, so Settings > Offset shows it and the app starts on it (PC keeps the offset too)
        prefs().edit().putString(Js8SettingsSchema.TX_OFFSET_KEY, hz.toInt().toString()).apply()
        requireView().findViewById<TextView?>(R.id.js8_wf_offset)?.text = "Offset: ${hz.toInt()} Hz"
        renderFrequency()
        requireContext().startService(Intent(requireContext(), JS8EngineService::class.java).apply {
            action = JS8EngineService.ACTION_SET_TX_OFFSET
            putExtra(JS8EngineService.EXTRA_TX_OFFSET_HZ, hz)
        })
    }

    private fun renderFrequency() {
        freqDisplay?.text = formatMhz(dialHz)
        val offset = monitorVm.getTxOffset().toLong()
        statusFreq.text = "Freq: ${formatMhz(dialHz + offset)}"
    }

    /** 14078000 -> "14.078 000" (desktop dial format). */
    private fun formatMhz(hz: Long): String {
        val mhz = hz / 1_000_000
        val khz = (hz / 1000) % 1000
        val rest = hz % 1000
        return String.format(Locale.US, "%d.%03d %03d", mhz, khz, rest)
    }

    // ---- Buttons ------------------------------------------------------------------------

    /**
     * UI_Constructor::addMessageText: put text in the Typing Box. When send is true and
     * "Immediately transmit CQ, Reply, Info, Saved, and Directed messages" is on (PC default),
     * transmit it straight away (toggleTx).
     */
    private fun addMessageText(text: String, send: Boolean) {
        typingBox.setText(text)
        val placeholder = text.indexOf('[')
        if (placeholder >= 0 && text.indexOf(']', placeholder) > placeholder) {
            typingBox.setSelection(placeholder, text.indexOf(']', placeholder) + 1)
        } else typingBox.setSelection(text.length)
        if (send && boolPref("js8cfg_transmit_directed", true) && !text.contains('[')) sendTyped()
    }

    private fun sendTyped() {
        val text = typingBox.text.toString().trim()
        if (text.isEmpty()) return
        if (listenOnly()) return
        if (!ensureCallsignSet(alert = true)) return
        Js8Macros.touch()
        transmitVm.queueMessage(text, selectedCall?.takeIf { !text.uppercase().startsWith(it.uppercase()) })
        typingBox.text.clear()
        processTxQueue()
    }

    /**
     * UI_Constructor::ensureCallsignSet: with no callsign or grid, say so (when alert is true)
     * and open Station. Returns false when one is missing.
     * One message for both (Rob, 2026-10-19); the PC has one for each.
     * SWL (Listening only) needs neither.
     */
    private fun ensureCallsignSet(alert: Boolean): Boolean {
        if (listenOnly()) return true
        if (prefs().getString("callsign", "").orEmpty().isNotBlank() &&
            prefs().getString("grid", "").orEmpty().isNotBlank()) return true
        if (alert) {
            if (childFragmentManager.findFragmentByTag(Js8StationRequiredDialog.TAG) == null) {
                Js8StationRequiredDialog().show(childFragmentManager, Js8StationRequiredDialog.TAG)
            }
        } else openStation()
        return false
    }

    /** Station > SWL (Listening only): every transmit button greyed out, nothing sent. */
    private fun listenOnly() = boolPref(Js8SettingsSchema.SWL_KEY, false)

    /** Settings window (header Settings button). */
    private fun openSettings() = navigateFromMain(R.id.navigation_js8_settings)

    /** Station window: callsign, grid and the rest of the PC's File > Settings. */
    internal fun openStation() = navigateFromMain(R.id.navigation_js8_station)

    private fun navigateFromMain(destination: Int) {
        if (!isAdded) return
        val nav = findNavController()
        if (nav.currentDestination?.id == R.id.navigation_js8_main) nav.navigate(destination)
    }

    /** Transmit buttons follow SWL (Listening only); REPLY, SNR, INFO, Directed also need a selection. */
    private fun renderTransmitEnabled() {
        val v = view ?: return
        val swl = listenOnly()
        listOf(R.id.js8_btn_cq, R.id.js8_btn_status, R.id.js8_btn_saved, R.id.js8_btn_tune)
            .forEach { v.findViewById<View>(it).isEnabled = !swl }
        v.findViewById<View>(R.id.js8_btn_send).isEnabled = !swl && typingBox.text.isNotBlank()
        btnTx.isEnabled = !swl
        btnTx.isActivated = !swl && boolPref("js8cfg_tx_enabled", true)
        renderSelection()
    }

    /** MainActivity sends the next queued message (same trigger as JS8Android's Transmit screen). */
    private fun processTxQueue() {
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(requireContext())
            .sendBroadcast(Intent(com.js8call.example.MainActivity.ACTION_PROCESS_TX_QUEUE))
    }

    private fun macro(text: String) = Js8Macros.expand(text, prefs())

    private fun setUpButtons(view: View) {
        // CQ: clears the selection, CQ Message (sendCQ)
        view.findViewById<View>(R.id.js8_btn_cq).setOnClickListener {
            clearSelection()
            addMessageText(macro(prefs().getString("js8cfg_cq_message", "CQ CQ CQ <MYGRID4>").orEmpty()
                .ifBlank { "CQ CQ CQ <MYGRID4>" }), send = true)
        }
        // REPLY: "CALL <reply message>"
        view.findViewById<View>(R.id.js8_btn_reply).setOnClickListener {
            val call = selectedCall ?: return@setOnClickListener
            addMessageText("$call ${macro(prefs().getString("js8cfg_reply_message", "HW CPY?").orEmpty())}", send = true)
        }
        // SNR: "CALL SNR -12" from Call Activity
        view.findViewById<View>(R.id.js8_btn_snr).setOnClickListener {
            val call = selectedCall ?: return@setOnClickListener
            val d = activityVm.activity.callActivity[call] ?: return@setOnClickListener
            addMessageText("$call SNR ${Js8Activity.formatSnr(d.snr)}", send = true)
        }
        // INFO / STATUS: "INFO <station info>" / "STATUS <station status>"
        view.findViewById<View>(R.id.js8_btn_info).setOnClickListener {
            val info = prefs().getString("my_info", "").orEmpty()
            if (info.isNotBlank()) addMessageText("INFO ${macro(info)}", send = true)
        }
        view.findViewById<View>(R.id.js8_btn_status).setOnClickListener {
            val status = prefs().getString("my_status", "").orEmpty()
            if (status.isNotBlank()) addMessageText("STATUS ${macro(status)}", send = true)
        }
        view.findViewById<View>(R.id.js8_btn_saved).setOnClickListener { showSavedMenu(it) }
        view.findViewById<View>(R.id.js8_btn_directed).setOnClickListener { showDirectedMenu(it) }
        view.findViewById<View>(R.id.js8_btn_deselect).setOnClickListener { clearSelection() }
        view.findViewById<View>(R.id.js8_btn_send).setOnClickListener { sendTyped() }
        // Halt: stop the transmission now and drop what is queued (on_stopTxButton_clicked)
        view.findViewById<View>(R.id.js8_btn_halt).setOnClickListener {
            transmitVm.clearQueue()
            requireContext().startService(Intent(requireContext(), JS8EngineService::class.java).apply {
                action = JS8EngineService.ACTION_HALT_TX
            })
            view.findViewById<View>(R.id.js8_btn_tune).isActivated = false
        }
        view.findViewById<View>(R.id.js8_inbox_reply).setOnClickListener { inboxReply() }
        typingBox.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                Js8Macros.touch()
                view.findViewById<View>(R.id.js8_btn_send).isEnabled = !s.isNullOrBlank() && !listenOnly()
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
    }

    private fun clearSelection() {
        callActivity.selectedRowId = null
        bandActivity.selectedRowId = null
        selectedCall = null
        renderSelection()
    }

    /** Saved button: the Saved Messages list (Settings > Saved Messages), macros expanded. */
    private fun showSavedMenu(anchor: View) {
        val saved = prefs().getString(Js8SettingsSchema.SAVED_MESSAGES_KEY, Js8SettingsSchema.SAVED_MESSAGES_DEFAULT)
            .orEmpty().split('\n').filter { it.isNotBlank() }
        val menu = android.widget.PopupMenu(requireContext(), anchor)
        saved.forEachIndexed { i, m -> menu.menu.add(0, i, i, m) }
        menu.menu.add(0, 9999, 9999, "Edit Saved Messages…")
        menu.setOnMenuItemClickListener { item ->
            if (item.itemId == 9999) openStation() // Saved Messages are in Station
            else addMessageText(macro(saved[item.itemId]), send = true)
            true
        }
        menu.show()
    }

    /** Directed button: the query menu (JS8_Mainwindow/buildQueryMenu.cpp). */
    private fun showDirectedMenu(anchor: View) {
        val call = selectedCall ?: return
        val grid = prefs().getString("grid", "").orEmpty()
        val snr = activityVm.activity.callActivity[call]?.snr?.let { Js8Activity.formatSnr(it) }.orEmpty()
        val items = listOf(
            "$call Reply - Send reply message to selected callsign" to
                "$call ${macro(prefs().getString("js8cfg_reply_message", "HW CPY?").orEmpty())}",
            "$call SNR - Send a signal report to the selected callsign" to "$call SNR $snr",
            "$call INFO - Send my station information" to "$call INFO ${macro(prefs().getString("my_info", "").orEmpty())}",
            "$call GRID $grid - Send my current station Maidenhead grid locator" to "$call GRID $grid",
            "$call SNR? - What is my signal report?" to "$call SNR?",
            "$call INFO? - What is your station information?" to "$call INFO?",
            "$call GRID? - What is your current grid locator?" to "$call GRID?",
            "$call STATUS? - What is your station status message?" to "$call STATUS?",
            "$call HEARING? - What are the stations are you hearing? (Top 4 ranked by most recently heard)" to "$call HEARING?",
            "$call|[MESSAGE] - Please ACK and retransmit the following message" to "$call|[MESSAGE]",
            "$call>[MESSAGE] - Please relay this message to its destination" to "$call>[MESSAGE]",
            "$call MSG [MESSAGE] - Please store this message in your inbox" to "$call MSG [MESSAGE]",
            "$call MSG TO:[CALLSIGN] [MESSAGE] - Please store this message at your station for later retreival by [CALLSIGN]" to
                "$call MSG TO:[CALLSIGN] [MESSAGE]",
            "$call QUERY CALL [CALLSIGN]? - Please acknowledge you can communicate directly with [CALLSIGN]" to
                "$call QUERY CALL [CALLSIGN]?",
            "$call QUERY MSGS - Do you have any messages for me?" to "$call QUERY MSGS",
            "$call QUERY MSG [ID] - Please deliver the complete message identified by ID" to "$call QUERY MSG [ID]",
            "$call AGN? - Please repeat your last transmission" to "$call AGN?",
            "$call QSL? - Did you receive my last transmission?" to "$call QSL?",
            "$call QSL - I confirm I received your last transmission" to "$call QSL",
            "$call YES - I confirm your last inquiry" to "$call YES",
            "$call NO - I do not confirm your last inquiry" to "$call NO"
        )
        val menu = android.widget.PopupMenu(requireContext(), anchor)
        items.forEachIndexed { i, (label, _) -> menu.menu.add(0, i, i, label) }
        menu.setOnMenuItemClickListener { item ->
            addMessageText(items[item.itemId].second, send = true)
            true
        }
        menu.show()
    }

    /** REPLY, SNR, INFO, Directed, Deselect are enabled only while a callsign is selected (desktop behaviour). */
    private fun renderSelection() {
        val has = selectedCall != null
        val v = requireView()
        val swl = listenOnly()
        listOf(R.id.js8_btn_reply, R.id.js8_btn_snr, R.id.js8_btn_info, R.id.js8_btn_directed)
            .forEach { v.findViewById<View>(it).isEnabled = has && !swl }
        v.findViewById<View>(R.id.js8_btn_deselect).isEnabled = has
    }

    // ---- Waterfall ----------------------------------------------------------------------

    private fun setUpWaterfall() {
        val renderer = monitorVm.getWaterfallRenderer()
        waterfall.bindRenderer(renderer)
        // Tap on the waterfall or its frequency scale (CPlotter::mouseReleaseEvent -> changeFreq)
        val tapOffset: (Float) -> Unit = { hz ->
            if (offsetChangeAllowed()) {
                offsetSlider?.progress = hz.toInt()
                applyOffset(hz)
            }
        }
        waterfall.onOffsetChanged = tapOffset
        ruler.onOffsetChanged = tapOffset
        waterfall.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> syncRuler() }
    }

    /**
     * UI_Constructor::changeFreq: no offset change while a message is queued or being sent,
     * unless "Allow Tx frequency changes while transmitting" is checked.
     */
    private fun offsetChangeAllowed(): Boolean {
        val state = transmitVm.txState.value
        val queued = state == TransmitState.QUEUED || state == TransmitState.TRANSMITTING ||
            !transmitVm.queue.value.isNullOrEmpty()
        return !queued || boolPref("js8cfg_tx_qsy", false)
    }

    private fun syncRuler() {
        val r = monitorVm.getWaterfallRenderer()
        val binHz = r.getCurrentBinHz()
        val start = r.getDisplayStartBin()
        val end = r.getDisplayEndBin()
        if (binHz > 0 && end > start && waterfall.width > 0) {
            ruler.startHz = start * binHz
            ruler.hzPerPixel = (end - start) * binHz / waterfall.width
        } else if (waterfall.width > 0) {
            ruler.startHz = 0f
            ruler.hzPerPixel = 3000f / waterfall.width
        }
    }

    // ---- Engine -------------------------------------------------------------------------

    private fun observe() {
        activityVm.version.observe(viewLifecycleOwner) { renderActivity() }
        observeInbox()
        monitorVm.status.observe(viewLifecycleOwner) { status ->
            val running = status.state == EngineState.RUNNING || status.state == EngineState.STARTING
            btnRx.isActivated = running
            levelMeter.levelDb = if (running) (status.powerDb + 90f).coerceIn(0f, 90f) else 0f
            renderStatus()
            offsetSlider?.let { if (it.progress != status.txOffsetHz.toInt()) it.progress = status.txOffsetHz.toInt() }
        }
        monitorVm.spectrum.observe(viewLifecycleOwner) { syncRuler() }
        transmitVm.txState.observe(viewLifecycleOwner) { state ->
            renderStatus()
            // TUNE lights only while the tone is on
            if (state != TransmitState.TRANSMITTING && state != TransmitState.QUEUED) {
                requireView().findViewById<View>(R.id.js8_btn_tune).isActivated = false
            }
        }
        monitorVm.radioFrequency.observe(viewLifecycleOwner) { hz ->
            if (hz != null && hz > 0) { dialHz = hz; renderFrequency() }
        }
        monitorVm.rigConnected.observe(viewLifecycleOwner) { connected ->
            requireView().findViewById<View>(R.id.js8_btn_cat).isActivated = connected
        }
    }

    private fun renderStatus() {
        val state = monitorVm.status.value?.state ?: EngineState.STOPPED
        val transmitting = state == EngineState.RUNNING && transmitVm.txState.value == TransmitState.TRANSMITTING
        btnTx.isSelected = transmitting
        when {
            transmitting -> { statusState.text = "Transmitting"; statusState.setBackgroundResource(R.drawable.js8_status_red) }
            state == EngineState.RUNNING || state == EngineState.STARTING -> {
                statusState.text = "Receiving"; statusState.setBackgroundResource(R.drawable.js8_status_green)
            }
            else -> { statusState.text = "Idle"; statusState.setBackgroundResource(R.drawable.js8_status_blue) }
        }
    }

    private fun startMonitoring() {
        val ctx = requireContext()
        if (!AudioDevices.usesSerialAudio(ctx) &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            AlertDialog.Builder(ctx)
                .setMessage("Microphone permission is needed to receive.")
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 41)
                }
                .show()
            return
        }
        monitorVm.startMonitoring()
        ContextCompat.startForegroundService(ctx, Intent(ctx, JS8EngineService::class.java).apply {
            action = JS8EngineService.ACTION_START
            putExtra(JS8EngineService.EXTRA_AUDIO_DEVICE_ID, AudioDevices.selected(ctx).id)
        })
    }

    private fun stopMonitoring() {
        monitorVm.stopMonitoring()
        requireContext().startService(Intent(requireContext(), JS8EngineService::class.java).apply {
            action = JS8EngineService.ACTION_STOP
        })
    }
}
