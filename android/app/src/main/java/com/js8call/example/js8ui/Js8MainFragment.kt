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
import com.js8call.example.api.Js8Api
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
        // Call Activity press-and-hold menu
        private const val ID_JUMP_OFFSET = 1
        private const val ID_JUMP_SPEED = 2
        private const val ID_JUMP_DRIFT = 3
        private const val ID_FILTER_CENTER = 4
        private const val ID_LOG = 10
        private const val ID_INBOX = 11
        private const val ID_STORE = 12
        private const val ID_SAVED = 20
        private const val ID_DIRECTED = 21
        private const val ID_RELAY = 22
        private const val ID_DESELECT = 23
        private const val ID_ADD = 30
        private const val ID_REMOVE = 31
        private const val ID_BLOCK = 32
        private const val ID_CLEAR_LIST = 40
        private const val ID_CLEAR_ALL = 41
        private const val ID_SAVED_EDIT = 999
        private const val ID_SAVED_FIRST = 1000
        private const val ID_DIRECTED_FIRST = 2000

        const val PREF_LANDSCAPE_LAYOUT = "landscape_layout"
        /** Swipe page kept across rotation / returning from the Menu page. */
        private var swipePage = 1
        /** checkStartupWarnings runs once per app start, not on each rotation. */
        private var startupChecked = false
        /** RX / TX set from Receiver / Transmitter off at startup once per app start, not on each rotation. */
        private var startupRxTxDone = false
        /** When each callsign was first selected: the QSO start time for Log QSO (m_callSelectedTime). */
        private val callSelectedTime = mutableMapOf<String, Long>()
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
    /** Upright and Swipe only; PC mode opens the Message Inbox screen instead (Rob, 2026-10-20). */
    private var inbox: Js8TableView? = null
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
    /** Waterfall Controls > Control tab (PC and Swipe; upright hides its Offset box). */
    private var wfControls: Js8WaterfallControls? = null

    // Waterfall Controls > Display > Adjust... > Import... / Export... (WF.cpp import_palette /
    // export_palette): a .pal file chosen with the phone's file picker
    private var paletteImported: ((List<Int>) -> Unit)? = null
    private var paletteToExport: String? = null
    private val importPaletteFile = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            val text = requireContext().contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            paletteImported?.invoke(com.js8call.example.ui.WaterfallPalettes.parse(text))
        } catch (e: Exception) {
            android.app.AlertDialog.Builder(requireContext()).setTitle("Import Failed")
                .setMessage("Error opening waterfall palette file: ${e.message}").setPositiveButton("OK", null).show()
        }
    }
    private val exportPaletteFile = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val text = paletteToExport ?: return@registerForActivityResult
        if (uri == null) return@registerForActivityResult
        try {
            requireContext().contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(text) }
        } catch (e: Exception) {
            android.app.AlertDialog.Builder(requireContext()).setTitle("Export Failed")
                .setMessage("Error writing waterfall palette file: ${e.message}").setPositiveButton("OK", null).show()
        }
    }
    /** PC mode: the draggable dividers (null in the other layouts). */
    private var splitters: Js8PcSplitters? = null
    /** Something changed since the lists were last drawn (PC m_rxDisplayDirty). */
    private var activityDirty = false
    /** Lists drawn at least once since this screen's views were made. */
    private var listsDrawn = false
    /** Message Window text (and colors) last put in the box; unchanged text is not set again. */
    private var messageWindowShown: String? = null

    private val ui = Handler(Looper.getMainLooper())
    private var clockFormat = SimpleDateFormat("yyyy MMM dd HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val tick = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            clock.text = clockFormat.format(Date(now))
            progress.seconds = ((now / 1000) % progress.period).toInt()
            // PC updateTimer: lists redrawn at most once a second, when something changed;
            // ages ("now", "15s", "2m") move on every 5 seconds even without new decodes
            if (activityDirty || (now / 1000) % 5 == 0L) renderActivity()
            renderHbLabel(now)
            renderNextBeacon(now)
            renderCqLabel(now)
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
            sideways == "ft8call" -> R.layout.fragment_js8_ft8call
            else -> R.layout.fragment_js8_pc
        }
        return inflater.inflate(layout, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listsDrawn = false // new views: draw the lists as soon as the data arrives
        messageWindowShown = null
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
        // FT8Call layout: date over time in the black header, Message Window text bold italic (FT8Call v0.4.2)
        val ft8call = isFt8CallLayout(view)
        clockFormat = SimpleDateFormat(if (ft8call) "yyyy MMM dd\nHH:mm:ss" else "yyyy MMM dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        if (ft8call) messageWindow.setTypeface(messageWindow.typeface, android.graphics.Typeface.BOLD_ITALIC)
        typingBox = view.findViewById(R.id.js8_typing_box)
        statusState = view.findViewById(R.id.js8_status_state)
        statusFreq = view.findViewById(R.id.js8_status_freq)
        statusAutoReply = view.findViewById(R.id.js8_status_autoreply)
        progress = view.findViewById(R.id.js8_status_progress)
        // Upright: Receiving | wpm box | cycle progress bar, which takes what is left and ends at
        // the page's right edge (Rob, 2026-10-19 / 2026-10-22)
        view.findViewById<View?>(R.id.js8_status_scroll)?.let { scroll ->
            scroll.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                val state = view.findViewById<View>(R.id.js8_status_state)
                val wpm = view.findViewById<View>(R.id.js8_status_wpm)
                val gap = (progress.layoutParams as ViewGroup.MarginLayoutParams).marginStart
                val wpmGap = (wpm.layoutParams as ViewGroup.MarginLayoutParams).marginStart
                val w = scroll.width - scroll.paddingLeft - scroll.paddingRight - state.width - gap -
                    wpm.layoutParams.width - wpmGap
                if (w > 0 && progress.layoutParams.width != w) {
                    progress.layoutParams = progress.layoutParams.apply { width = w }
                }
            }
        }
        waterfall = view.findViewById(R.id.js8_waterfall)
        ruler = view.findViewById(R.id.js8_ruler)
        levelMeter = view.findViewById(R.id.js8_level_meter)
        // Upright: CAT + level meter column 33 % narrower, 87 -> 58 dp (Rob, 2026-10-20)
        if (resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE) {
            (levelMeter.parent as? View)?.let { col ->
                col.layoutParams = col.layoutParams.apply { width = (58 * resources.displayMetrics.density).toInt() }
            }
        }

        setUpSwipe(view)
        splitters = view.findViewById<android.widget.FrameLayout?>(R.id.js8_pc_window)?.let { Js8PcSplitters(it, prefs()) }
        setUpTables()
        // PC onRigStateChanged when the rig first comes online: RX on unless "Receiver (RX) off at startup",
        // TX on unless "Transmitter (TX) off at startup" (Rob, 2026-10-21: RX was off after every fresh start)
        val firstStart = !startupRxTxDone
        if (firstStart) {
            startupRxTxDone = true
            prefs().edit().putBoolean("js8cfg_tx_enabled", !boolPref("js8cfg_transmit_off", false)).apply()
        }
        setUpHeader()
        setUpButtons(view)
        if (firstStart && !boolPref("js8cfg_monitor_off", false)) view.post {
            val running = monitorVm.status.value?.state.let { it == EngineState.RUNNING || it == EngineState.STARTING }
            if (isAdded && !running) startMonitoring()
        }
        // FT8Call layout: equal buttons and the divider spot in the STATUS / Saved gap from the start
        if (ft8call) layoutPcButtons(hbShown())
        // Saved > APRS page OK and Message Inbox screen Reply: the line goes in the Typing Box, not sent
        parentFragmentManager.setFragmentResultListener(Js8AprsFragment.RESULT_KEY, viewLifecycleOwner) { _, result ->
            result.getString(Js8AprsFragment.RESULT_TEXT)?.let { addMessageText(it, send = false) }
        }
        // Add to Log: the selection is cleared (acceptQSO -> clearCallsignSelected) and the ✓ shows
        parentFragmentManager.setFragmentResultListener(Js8LogFragment.RESULT_KEY, viewLifecycleOwner) { _, _ ->
            clearSelection()
            activityVm.changed()
        }
        setUpWaterfall()
        wfControls = Js8WaterfallControls(view, prefs(), waterfall,
            offset = { monitorVm.getTxOffset().toInt() },
            setOffset = { hz -> jumpToOffset(hz) },
            qsy = { center -> qsy(center) },
            period = { progress.period },
            setDrift = { ms ->
                requireContext().startService(Intent(requireContext(), JS8EngineService::class.java).apply {
                    action = JS8EngineService.ACTION_SET_TIME_DRIFT
                    putExtra(JS8EngineService.EXTRA_TIME_DRIFT_MS, ms)
                })
            },
            setAutoDrift = { on, stopAfter ->
                requireContext().startService(Intent(requireContext(), JS8EngineService::class.java).apply {
                    action = JS8EngineService.ACTION_SET_AUTO_DRIFT
                    putExtra(JS8EngineService.EXTRA_AUTO_DRIFT_ON, on)
                    putExtra(JS8EngineService.EXTRA_AUTO_DRIFT_STOP_AFTER, stopAfter)
                })
            },
            importPalette = { onLoaded -> paletteImported = onLoaded; importPaletteFile.launch(arrayOf("*/*")) },
            exportPalette = { text -> paletteToExport = text; exportPaletteFile.launch("palette.pal") })
        observe()
        // Station > Reporting API commands that use this screen (Typing Box, TUNE, Halt, offset, filter)
        Js8Api.controller = apiController
        // The CQ timer sends through this screen (sendCQ(true))
        com.js8call.example.service.CqTimer.fire = cqFire
        Js8Api.setSelectedCall(selectedCall)
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

    override fun onDestroyView() {
        if (Js8Api.controller === apiController) Js8Api.controller = null
        if (com.js8call.example.service.CqTimer.fire === cqFire) com.js8call.example.service.CqTimer.fire = null
        super.onDestroyView()
    }

    private val cqFire: () -> Boolean = { view != null && !listenOnly() && sendCq(repeat = true) }

    /** What the API commands do on this screen (PC networkMessage on the main window). */
    private val apiController = object : Js8Api.Controller {
        override fun typingText(): String = if (view != null) typingBox.text.toString() else ""

        // PC addMessageText(text, clear = true): nothing while a message waits or is sent
        override fun setTypingText(text: String) {
            if (view == null || activityVm.typed.value != null) return
            typingBox.setText(text)
            typingBox.setSelection(typingBox.text.length)
        }

        override fun setTune(on: Boolean): Boolean {
            val tune = view?.findViewById<View>(R.id.js8_btn_tune) ?: return false
            if (tune.isEnabled && tune.isActivated != on) tune.performClick()
            return tune.isActivated
        }

        override fun halt() {
            view?.findViewById<View>(R.id.js8_btn_halt)?.performClick()
        }

        override fun setOffset(hz: Int) {
            if (view != null) jumpToOffset(hz)
        }

        override fun setFilter(center: Int?, width: Int?, enabled: Boolean?) {
            wfControls?.setFilter(center, width, enabled)
        }

        override fun refresh() = refreshFromPrefs()
    }

    /** Settings changed by the API: header, status bar, TX / SPOT buttons and the lists follow. */
    private fun refreshFromPrefs() {
        if (view == null) return
        val prefs = prefs()
        callsign.text = prefs.getString("callsign", "").orEmpty().ifBlank { "NOCALL" }
        statusAutoReply.text = if (prefs.getBoolean("autoreply_enabled", false)) "Auto Reply: On" else "Auto Reply: Off"
        dialHz = prefs.getString("last_frequency", "14078000")?.toLongOrNull() ?: 14_078_000L
        renderFrequency()
        renderSpeed()
        renderTransmitEnabled()
        requireView().findViewById<View>(R.id.js8_btn_spot).isActivated = boolPref("psk_reporter", false)
        activityVm.changed() // groups and @ALLCALL in Call Activity
    }

    override fun onResume() {
        super.onResume()
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        callsign.text = prefs.getString("callsign", "").orEmpty().ifBlank { "NOCALL" }
        statusAutoReply.text = if (prefs.getBoolean("autoreply_enabled", false)) "Auto Reply: On" else "Auto Reply: Off"
        dialHz = prefs.getString("last_frequency", "14078000")?.toLongOrNull() ?: 14_078_000L
        renderFrequency()
        renderSpeed()
        renderTransmitEnabled() // Station > SWL (Listening only) may have changed
        // Offset from Settings (or saved last time)
        val savedOffset = prefs.getString(Js8SettingsSchema.TX_OFFSET_KEY, null)?.toFloatOrNull()
        if (savedOffset != null && savedOffset != monitorVm.getTxOffset()) {
            offsetSlider?.progress = savedOffset.toInt()
            applyOffset(savedOffset)
        }
        waterfall.txOffsetHz = monitorVm.getTxOffset() // a rebuilt screen starts on 1500 otherwise
        renderOffsetBox()
        ui.post(tick)
    }

    override fun onPause() {
        ui.removeCallbacks(tick)
        ui.removeCallbacks(textStats)
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
        // Upright keeps its own Band Activity columns: Offset and SNR hidden at first, shown again from
        // Settings > View (Rob, 2026-10-19). Sideways keeps the PC defaults.
        val upright = resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
        bandActivity.setColumns(if (upright) Js8SettingsSchema.BAND_TABLE_UPRIGHT else "band", listOf(
            Js8TableView.Column("offset", "Offset", 48, defaultVisible = !upright, numeric = true, menuLabel = "Frequency Offset"),
            Js8TableView.Column("timestamp", "Age", 36, numeric = true, menuLabel = "Last heard timestamp"),
            Js8TableView.Column("snr", "SNR", 36, defaultVisible = !upright, numeric = true),
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
        inbox?.let { Js8Inbox.setColumns(it) }
        // Call Activity click selects a callsign; Band Activity click selects the newest call at that offset
        // (UI_Constructor::callsignSelected). Only one table holds a selection at a time.
        callActivity.onRowClick = { row ->
            bandActivity.selectedRowId = null
            selectedCall = if (callActivity.selectedRowId == null) null else row.id
            renderSelection()
        }
        // Double-click: put the callsign in the Typing Box (on_tableWidgetCalls_cellDoubleClicked)
        // Press and hold: the PC's right-click menu for that callsign
        callActivity.onRowLongClick = { row, anchor -> showCallMenu(anchor, row.id) }
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
        inbox?.onRowClick = { row -> showInboxMessage(row.id) }
        // Press and hold: Delete (MessagePanel)
        inbox?.onRowLongClick = { row, anchor ->
            Js8Inbox.showMessageMenu(anchor) {
                viewLifecycleOwner.lifecycleScope.launch { repository.deleteMessage(row.id.toLong()) }
                requireView().findViewById<TextView?>(R.id.js8_inbox_message)?.text = ""
            }
        }
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
    /**
     * Typing Box while a typed message waits or is sent (PC TransmitTextEdit): locked, the text
     * already sent crossed out; emptied and unlocked when the message is done or halted.
     */
    private fun renderTyped(t: Js8ActivityViewModel.TypedTx?) {
        // A heartbeat or reply going out while the box was empty: show its text there
        if (t != null && t.auto && typingBox.text.toString() != t.text) {
            typingBox.filters = emptyArray()
            typingBox.setText(t.text)
        }
        val e = typingBox.text
        e.getSpans(0, e.length, android.text.style.StrikethroughSpan::class.java).forEach { e.removeSpan(it) }
        if (t == null) {
            typingBox.filters = emptyArray()
            typingBox.isCursorVisible = true
            typingBox.showSoftInputOnFocus = true
            if (activityVm.typedClearPending) {
                activityVm.typedClearPending = false
                e.clear()
            }
        } else {
            // Locked: typed keys and pastes change nothing
            typingBox.filters = arrayOf(android.text.InputFilter { _, _, _, dest, dstart, dend -> dest.subSequence(dstart, dend) })
            typingBox.isCursorVisible = false
            typingBox.showSoftInputOnFocus = false
            val n = t.charsSent.coerceAtMost(e.length)
            if (n > 0) e.setSpan(android.text.style.StrikethroughSpan(), 0, n, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        view?.findViewById<View>(R.id.js8_btn_send)?.isEnabled = !listenOnly() && e.isNotBlank() && t == null
    }

    /** Current speed (Settings > Mode, Call Activity > Jump to ... speed): offset dial width, status bar label, cycle. */
    private fun renderSpeed() {
        val submode = Js8Activity.txSubmode(prefs().getInt(Js8SettingsSchema.TX_SUBMODE_KEY, 0))
        waterfall.bandwidthHz = Js8Activity.bandwidthHz(submode)
        view?.findViewById<TextView?>(R.id.js8_status_mode)?.text = Js8Activity.modeLabel(submode)
        progress.period = Js8Activity.periodSeconds(submode)
        waterfall.setCyclePeriod(progress.period)
        requestTextStats()
        renderHb()
    }

    // ---- Status Bar wpm box (PC mode) ---------------------------------------------------

    /** Typing Box text, selection or speed changed: count again 100 ms later (PC m_txTextDirtyDebounce). */
    private val textStats = Runnable { refreshTextStats() }

    private fun requestTextStats() {
        ui.removeCallbacks(textStats)
        ui.postDelayed(textStats, 100)
    }

    /**
     * UI_Constructor::refreshTextDisplay + updateTextStatsDisplay: build the message's parts the
     * way it would be sent and show words / characters per minute; hidden while the box is empty.
     */
    private fun refreshTextStats() {
        val label = view?.findViewById<TextView?>(R.id.js8_status_wpm) ?: return
        // Upright keeps the box's space while hidden, so the progress bar keeps its width
        val hidden = if (view?.findViewById<View?>(R.id.js8_status_scroll) != null) View.INVISIBLE else View.GONE
        val text = typingBox.text.toString().trim()
        if (text.isEmpty()) {
            label.visibility = hidden
            label.text = ""
            return
        }
        val p = prefs()
        val myCall = p.getString("callsign", "").orEmpty().trim().uppercase(Locale.US)
        val myGrid = p.getString("grid", "").orEmpty().trim().uppercase(Locale.US).take(4)
        // Same directed callsign and identify rule as sending (sendTyped, JS8EngineService)
        val directed = selectedCall?.takeIf { !text.uppercase().startsWith(it.uppercase()) }.orEmpty()
        val submode = p.getInt(Js8SettingsSchema.TX_SUBMODE_KEY, 0)
        val fpm = 60.0 / Js8Activity.periodSeconds(Js8Activity.txSubmode(submode))
        viewLifecycleOwner.lifecycleScope.launch {
            val parts = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                runCatching {
                    com.js8call.core.JS8Engine.messageFrameTexts(text, myCall, myGrid, directed, submode, myCall.isNotBlank())
                }.getOrDefault(emptyList())
            }
            // A newer count may be on its way; only the latest text is shown
            if (typingBox.text.toString().trim() != text) return@launch
            val count = parts.size
            if (count == 0) {
                label.visibility = hidden
                label.text = ""
                return@launch
            }
            val sent = parts.joinToString("")
            val words = sent.split(" ").count { it.isNotEmpty() }
            val minutes = count / fpm
            label.text = String.format(Locale.US, "%.1fwpm / %.1fcpm", words / minutes, sent.length / minutes)
            label.visibility = View.VISIBLE
        }
    }

    /** Lists need redrawing; the first time after the screen opens it happens at once. */
    private fun requestRender() {
        if (!listsDrawn) renderActivity() else activityDirty = true
    }

    private fun renderActivity() {
        if (view == null) return
        activityDirty = false
        listsDrawn = true
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
            // Worked before / Name / Comment from the log; its grid when none was heard (displayCallActivity)
            val worked = com.js8call.example.data.Js8LogBook.workedBefore(requireContext(), d.call)
            val logged = if (worked) com.js8call.example.data.Js8LogBook.details(requireContext(), d.call) else null
            if (d.grid.isEmpty() && !logged?.grid.isNullOrEmpty()) d.grid = logged!!.grid.trim()
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
                    "log" to if (worked) "✓" else "", "logName" to logged?.name.orEmpty(),
                    "logComment" to logged?.comment.orEmpty()
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
                .append(") - ")
            val textStart = sb.length
            sb.append(line.text.toString().trimEnd())
            sb.setSpan(android.text.style.ForegroundColorSpan(if (line.isTx) txFg else rxFg), start, sb.length, 0)
            // FT8 transmitting callsign in bold
            line.bold?.let { b ->
                if (textStart + b.last < sb.length) {
                    sb.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), textStart + b.first, textStart + b.last + 1, 0)
                }
            }
        }
        // Only when the text changed: setting it throws the box back to the top (the bounce Rob saw
        // every 5 seconds). The jump to the newest line happens before the box is drawn.
        val shown = "$rxFg/$txFg/$sb"
        if (shown == messageWindowShown) return
        messageWindowShown = shown
        messageWindow.text = sb
        messageWindow.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                messageWindow.viewTreeObserver.removeOnPreDrawListener(this)
                val layout = messageWindow.layout ?: return true
                val y = layout.height - messageWindow.height + messageWindow.paddingTop + messageWindow.paddingBottom
                if (y <= 0 || messageWindow.scrollY == y) return true
                messageWindow.scrollTo(0, y)
                return false // skip this frame; the next one is drawn already scrolled
            }
        })
    }

    // ---- Inbox (JS8Android message store: MSG messages received) -----------------------

    private val repository by lazy { com.js8call.example.data.MessageRepository.getInstance(requireContext()) }
    private var inboxMessages: List<com.js8call.example.data.MessageEntity> = emptyList()
    private var unreadFrom: Set<String> = emptySet()

    private fun observeInbox() {
        repository.getAllMessages().observe(viewLifecycleOwner) { all ->
            inboxMessages = Js8Inbox.incoming(all)
            val my = prefs().getString("callsign", "").orEmpty().uppercase()
            inbox?.setRows(Js8Inbox.rows(inboxMessages, my))
            // Call Activity flag: stations with unread messages (displayCallActivity hasMessage)
            unreadFrom = inboxMessages.filter { !it.isRead }.map { it.senderCallsign ?: it.conversationId }.toSet()
            requestRender()
        }
    }

    private fun showInboxMessage(id: String) {
        val m = inboxMessages.firstOrNull { it.id.toString() == id } ?: return
        requireView().findViewById<TextView?>(R.id.js8_inbox_message)?.text = m.text
        if (!m.isRead) viewLifecycleOwner.lifecycleScope.launch { repository.markMessageAsRead(m.id) }
    }

    /** Inbox Reply: "FROM MSG [MESSAGE]" into the Typing Box (MessagePanel::on_replyPushButton_clicked). */
    private fun inboxReply() {
        val id = inbox?.selectedRowId ?: return
        val m = inboxMessages.firstOrNull { it.id.toString() == id } ?: return
        val typed = requireView().findViewById<EditText>(R.id.js8_inbox_reply_text).text.toString()
        addMessageText(Js8Inbox.replyText(m, typed), send = false)
    }

    // ---- Header -------------------------------------------------------------------------

    private fun setUpHeader() {
        offsetSlider?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, value: Int, fromUser: Boolean) {
                // Locked like the waterfall while a message waits or is sent (UI_Constructor::changeFreq)
                if (fromUser && !offsetChangeAllowed()) {
                    sb.progress = monitorVm.getTxOffset().toInt()
                    return
                }
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
        // LOG: the Log QSO page for the selected callsign (on_logQSOButton_clicked)
        requireView().findViewById<View>(R.id.js8_btn_log).setOnClickListener { openLog(selectedCall) }
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
        // PC mode menu bar: the same windows as upright, no separate Menu page (Rob, 2026-10-20:
        // every menu has the same items in every layout). File = the PC's File > Settings (Station);
        // Configurations, Mode and Control items are in the Settings window; Help > About is in Station.
        fun menubar(id: Int, action: (View) -> Unit) = requireView().findViewById<View?>(id)?.setOnClickListener { action(it) }
        menubar(R.id.js8_menubar_file) { openStation() }
        listOf(R.id.js8_menubar_configurations, R.id.js8_menubar_mode, R.id.js8_menubar_control)
            .forEach { menubar(it) { openSettings() } }
        // View: Inbox (PC View > Show Message Inbox...) and Settings… (Rob, 2026-10-20)
        menubar(R.id.js8_menubar_view) { anchor ->
            val menu = popupMenu(anchor)
            menu.menu.add(0, 1, 1, "Inbox")
            menu.menu.add(0, 2, 2, "Settings…")
            menu.menu.add(0, 3, 3, "Reset Window Sizes") // PC View > Reset Window Sizes: the dividers
            menu.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> openInbox()
                    2 -> openSettings()
                    else -> splitters?.reset()
                }
                true
            }
            menu.show()
        }
        menubar(R.id.js8_menubar_log) { openLog(selectedCall) }
        menubar(R.id.js8_menubar_help) { openStation(page = Js8SettingsFragment.ABOUT_PAGE) }
    }

    private fun applyOffset(hz: Float) {
        monitorVm.setTxOffset(hz)
        transmitVm.setTxOffset(hz)
        waterfall.txOffsetHz = hz
        // Saved, so Settings > Offset shows it and the app starts on it (PC keeps the offset too)
        prefs().edit().putString(Js8SettingsSchema.TX_OFFSET_KEY, hz.toInt().toString()).apply()
        renderOffsetBox()
        renderFrequency()
        requireContext().startService(Intent(requireContext(), JS8EngineService::class.java).apply {
            action = JS8EngineService.ACTION_SET_TX_OFFSET
            putExtra(JS8EngineService.EXTRA_TX_OFFSET_HZ, hz)
        })
    }

    /** Waterfall Controls > Offset box: always the current offset (it kept the layout's 1500 at startup). */
    private fun renderOffsetBox() {
        view?.findViewById<TextView?>(R.id.js8_wf_offset)?.text = "Offset: ${monitorVm.getTxOffset().toInt()} Hz"
    }

    private fun renderFrequency() {
        freqDisplay?.text = formatMhz(dialHz)
        // FT8Call layout: the band above the frequency
        view?.findViewById<TextView?>(R.id.js8_band_label)?.text =
            com.js8call.example.util.BandUtils.bandLabelForFrequencyHz(dialHz)
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
        // PC: nothing is added while a message waits or is being sent
        if (activityVm.typed.value != null) return
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
        // PC: the message stays in the Typing Box until its last part has been sent
        activityVm.onTypedQueued(text)
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

    /** Inbox in this layout (upright, Swipe), or the Message Inbox screen (PC mode). */
    private fun openInbox() {
        val table = inbox
        if (table != null) table.requestRectangleOnScreen(android.graphics.Rect(0, 0, table.width, table.height))
        else navigateFromMain(R.id.navigation_js8_inbox)
    }

    /** Station window: callsign, grid and the rest of the PC's File > Settings. */
    internal fun openStation(page: String? = null) = navigateFromMain(R.id.navigation_js8_station,
        page?.let { android.os.Bundle().apply { putString(Js8SettingsFragment.ARG_PAGE, it) } })

    private fun navigateFromMain(destination: Int, args: android.os.Bundle? = null) {
        if (!isAdded) return
        val nav = findNavController()
        if (nav.currentDestination?.id == R.id.navigation_js8_main) nav.navigate(destination, args)
    }

    /** Transmit buttons follow SWL (Listening only); REPLY, SNR, INFO, Directed also need a selection. */
    private fun renderTransmitEnabled() {
        val v = view ?: return
        val swl = listenOnly()
        listOf(R.id.js8_btn_cq, R.id.js8_btn_status, R.id.js8_btn_saved, R.id.js8_btn_tune, R.id.js8_btn_hb)
            .forEach { v.findViewById<View>(it).isEnabled = !swl }
        v.findViewById<View>(R.id.js8_btn_send).isEnabled = !swl && typingBox.text.isNotBlank() && activityVm.typed.value == null
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
        // HB (PC hbMacroButton): tap = one heartbeat, or start / stop the timer when a repeat
        // time is set; press and hold = the PC's heartbeat menu (buildHeartbeatMenu)
        view.findViewById<View>(R.id.js8_btn_hb).apply {
            setOnClickListener { onHbClicked() }
            setOnLongClickListener { showHbMenu(it); true }
        }
        // CQ: clears the selection, CQ Message (sendCQ)
        // Tap = one CQ, or the CQ timer on / off with a repeat time; press and hold = buildCQMenu
        view.findViewById<View>(R.id.js8_btn_cq).apply {
            setOnClickListener { onCqClicked() }
            setOnLongClickListener { showCqMenu(it); true }
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
            activityVm.clearTyped() // PC on_stopTxButton_clicked -> resetMessage: the Typing Box empties
            view.findViewById<View>(R.id.js8_btn_tune).isActivated = false
        }
        view.findViewById<View?>(R.id.js8_inbox_reply)?.setOnClickListener { inboxReply() }
        typingBox.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                activityVm.typingBoxEmpty = s.isNullOrBlank()
                if (activityVm.typed.value?.auto == true) return // shown by the app, not typed
                Js8Macros.touch()
                requestTextStats()
                view.findViewById<View>(R.id.js8_btn_send).isEnabled =
                    !s.isNullOrBlank() && !listenOnly() && activityVm.typed.value == null
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
    /** Popup menus with the lists' text size and shorter rows (ThemeOverlay.Js8PopupMenu). */
    private fun popupMenu(anchor: View) =
        android.widget.PopupMenu(android.view.ContextThemeWrapper(requireContext(), R.style.ThemeOverlay_Js8PopupMenu), anchor)

    private fun savedMessages() = prefs().getString(Js8SettingsSchema.SAVED_MESSAGES_KEY, Js8SettingsSchema.SAVED_MESSAGES_DEFAULT)
        .orEmpty().split('\n').filter { it.isNotBlank() }

    private fun showSavedMenu(anchor: View) {
        val saved = savedMessages()
        val menu = popupMenu(anchor)
        saved.forEachIndexed { i, m -> menu.menu.add(0, i, i, m) }
        menu.menu.add(0, 9999, 9999, "Edit Saved Messages…")
        // APRS pages, in every layout: menus have the same items everywhere (Rob, 2026-10-20)
        val aprs = listOf(
            "APRS EMAIL" to Js8AprsFragment.FORM_EMAIL,
            "APRS TXT" to Js8AprsFragment.FORM_TXT,
            "APRS GRID" to Js8AprsFragment.FORM_GRID,
            "APRS MSG" to Js8AprsFragment.FORM_MSG
        )
        aprs.forEachIndexed { i, (label, _) -> menu.menu.add(0, 10000 + i, 10000 + i, label) }
        menu.setOnMenuItemClickListener { item ->
            when {
                item.itemId == 9999 -> openStation() // Saved Messages are in Station
                // APRS GRID sends at once like a saved message, with the grid from Station (Rob, 2026-10-21)
                aprs.getOrNull(item.itemId - 10000)?.second == Js8AprsFragment.FORM_GRID -> sendAprsGrid()
                item.itemId >= 10000 -> openAprs(aprs[item.itemId - 10000].second)
                else -> addMessageText(macro(saved[item.itemId]), send = true)
            }
            true
        }
        menu.show()
    }

    /** Saved > APRS GRID: "@APRSIS GRID" + My Maidenhead Grid Locator, sent like a saved message. */
    private fun sendAprsGrid() {
        val grid = prefs().getString("grid", "").orEmpty().trim().uppercase(Locale.US)
        if (!Js8Grid.isValid(grid)) {
            AlertDialog.Builder(requireContext())
                .setMessage("Set your grid in Station first (General > My Maidenhead Grid Locator).")
                .setPositiveButton(android.R.string.ok, null).show()
            return
        }
        addMessageText("@APRSIS GRID $grid", send = true)
    }

    /** Saved > APRS page; its OK puts the line in the Typing Box without sending (Rob, 2026-10-19). */
    private fun openAprs(form: String) {
        if (!isAdded) return
        val nav = findNavController()
        if (nav.currentDestination?.id == R.id.navigation_js8_main) {
            nav.navigate(R.id.navigation_js8_aprs, android.os.Bundle().apply { putString(Js8AprsFragment.ARG_FORM, form) })
        }
    }

    /** Directed button: the query menu (JS8_Mainwindow/buildQueryMenu.cpp). */
    private fun showDirectedMenu(anchor: View) {
        val call = selectedCall ?: return
        val items = directedItems(call)
        val menu = popupMenu(anchor)
        items.forEachIndexed { i, (label, _) -> menu.menu.add(0, i, i, label) }
        menu.setOnMenuItemClickListener { item ->
            addMessageText(items[item.itemId].second, send = true)
            true
        }
        menu.show()
    }

    /** buildQueryMenu items for [call]: menu label to Typing Box text. */
    private fun directedItems(call: String): List<Pair<String, String>> {
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
        return items
    }

    /**
     * Press and hold a callsign in Call Activity: the PC's right-click menu
     * (UI_Constructor.cpp, tableWidgetCalls customContextMenuRequested). Rob, 2026-10-19.
     */
    private fun showCallMenu(anchor: View, call: String) {
        // The row is selected first; the menu acts on it (PC callsignSelected)
        bandActivity.selectedRowId = null
        callActivity.selectedRowId = call
        selectedCall = call
        renderSelection()

        val a = activityVm.activity
        val isAllCall = call == "@ALLCALL"
        val isGroup = call.startsWith("@")
        val detail = a.callActivity[call]
        val mySubmode = Js8Activity.txSubmode(prefs().getInt(Js8SettingsSchema.TX_SUBMODE_KEY, 0))
        val saved = savedMessages()
        val directed = directedItems(call)
        // Messages from them, or held for them (Inbox::fetchForCall)
        val hasInbox = inboxMessages.any {
            (if (Js8Inbox.isHeld(it)) it.conversationId else it.senderCallsign ?: it.conversationId).equals(call, ignoreCase = true)
        }

        val popup = popupMenu(anchor)
        val m = popup.menu
        androidx.core.view.MenuCompat.setGroupDividerEnabled(m, true)
        // Jump to their offset / speed / time drift
        if (!isAllCall && detail != null && detail.offset > 0) {
            m.add(1, ID_JUMP_OFFSET, 1, "Jump to ${detail.offset}Hz")
            // PC: shown while Enable Filter is on
            if (wfControls?.filterEnabled == true) m.add(1, ID_FILTER_CENTER, 1, "Center filter at ${detail.offset}Hz")
            if (detail.submode != mySubmode && Js8Activity.txSubmode(detail.submode) == detail.submode) {
                m.add(1, ID_JUMP_SPEED, 2, "Jump to ${speedName(detail.submode)} speed")
            }
            m.add(1, ID_JUMP_DRIFT, 3, "Jump to ${-(detail.tdrift * 1000).toInt()} ms time drift")
        }
        m.add(2, ID_LOG, 10, "Log...").isEnabled = !isGroup
        m.add(2, ID_INBOX, 11, "Show Message Inbox...").isEnabled = !isAllCall && hasInbox
        m.add(2, ID_STORE, 12, "Store Message...")
        val savedMenu = m.addSubMenu(3, ID_SAVED, 20, "Saved Messages...")
        saved.forEachIndexed { i, text -> savedMenu.add(0, ID_SAVED_FIRST + i, i, text) }
        savedMenu.add(0, ID_SAVED_EDIT, saved.size, "Edit Saved Messages\u2026")
        val directedMenu = m.addSubMenu(3, ID_DIRECTED, 21, "Directed to $call...")
        directed.forEachIndexed { i, (label, _) -> directedMenu.add(0, ID_DIRECTED_FIRST + i, i, label) }
        m.add(3, ID_RELAY, 22, "Relay via $call...").isEnabled = !isAllCall
        m.add(3, ID_DESELECT, 23, "Deselect $call")
        m.add(4, ID_ADD, 30, "Add New Station or Group...")
        m.add(4, ID_REMOVE, 31, if (isGroup) "Remove This Group" else "Remove This Station")
        m.add(4, ID_BLOCK, 32, "Block This Station").isEnabled = !isGroup
        m.add(5, ID_CLEAR_LIST, 40, "Clear Entire List")
        m.add(5, ID_CLEAR_ALL, 41, "Clear All Lists")

        popup.setOnMenuItemClickListener { item ->
            val id = item.itemId
            when {
                id == ID_JUMP_OFFSET -> detail?.let { jumpToOffset(it.offset) }
                id == ID_FILTER_CENTER -> detail?.let { wfControls?.setFilterCenter(it.offset) }
                id == ID_JUMP_SPEED -> detail?.let { jumpToSpeed(it.submode) }
                id == ID_JUMP_DRIFT -> detail?.let {
                    requireContext().startService(Intent(requireContext(), JS8EngineService::class.java).apply {
                        action = JS8EngineService.ACTION_SET_TIME_DRIFT
                        putExtra(JS8EngineService.EXTRA_TIME_DRIFT_MS, -(it.tdrift * 1000).toLong())
                    })
                }
                id == ID_INBOX -> openInbox()
                id == ID_LOG -> openLog(call)
                id == ID_STORE -> storeMessage(call)
                id == ID_SAVED_EDIT -> openStation()
                id in ID_SAVED_FIRST until ID_SAVED_FIRST + saved.size ->
                    addMessageText(macro(saved[id - ID_SAVED_FIRST]), send = true)
                id in ID_DIRECTED_FIRST until ID_DIRECTED_FIRST + directed.size ->
                    addMessageText(directed[id - ID_DIRECTED_FIRST].second, send = true)
                // PC buildRelayAction -> prependMessageText("CALL>")
                id == ID_RELAY -> if (activityVm.typed.value == null) typingBox.text.insert(0, "$call>")
                id == ID_DESELECT -> clearSelection()
                id == ID_ADD -> askAddStation()
                id == ID_REMOVE -> removeStation(call)
                id == ID_BLOCK -> blockStation(call)
                id == ID_CLEAR_LIST -> { a.clearCalls(); clearSelection(); activityVm.changed() }
                id == ID_CLEAR_ALL -> { a.clearBand(); a.clearCalls(); a.clearRx(); clearSelection(); activityVm.changed() }
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
        popup.show()
    }

    /** PC JS8::Submode::name for the Jump to ... speed item. */
    private fun speedName(submode: Int) = when (submode) {
        Js8Activity.SUBMODE_FAST -> "Fast"
        Js8Activity.SUBMODE_TURBO -> "JS8 40"
        Js8Activity.SUBMODE_SLOW -> "Slow"
        Js8Activity.SUBMODE_ULTRA -> "JS8 60"
        else -> "Normal"
    }

    /** PC changeFreq: same rule as tapping the waterfall. */
    private fun jumpToOffset(hz: Int) {
        if (!offsetChangeAllowed()) return
        offsetSlider?.progress = hz
        applyOffset(hz.toFloat())
    }

    /**
     * Waterfall Controls > QSY (UI_Constructor::qsy): the dial moves by offset - center, so the
     * signal at the offset is heard at center, and the offset becomes center. The dial goes to
     * Settings > Frequency (last_frequency), which retunes the radio when rig control is on.
     * Not done: the PC also moves the Band Activity rows by the same amount.
     */
    private fun qsy(center: Int) {
        if (!offsetChangeAllowed()) return
        val delta = monitorVm.getTxOffset().toLong() - center
        if (delta == 0L) return
        dialHz += delta
        prefs().edit().putString(Js8SettingsSchema.FREQUENCY_KEY, dialHz.toString()).apply()
        jumpToOffset(center)
        renderFrequency()
    }

    /**
     * UI_Constructor::on_logQSOButton_clicked: the Log QSO page for [call] (none for a group),
     * its grid from Call Activity, report sent = their SNR, start = when it was first selected,
     * frequency = dial + offset.
     */
    private fun openLog(call: String?) {
        val c = call?.takeIf { !it.startsWith("@") }.orEmpty()
        val d = activityVm.activity.callActivity[c]
        val args = android.os.Bundle().apply {
            putString(Js8LogFragment.ARG_CALL, c)
            putString(Js8LogFragment.ARG_GRID, d?.grid?.trim().orEmpty())
            putString(Js8LogFragment.ARG_RPT_SENT, d?.snr?.takeIf { it > -50 }?.let { Js8Activity.formatSnr(it) }.orEmpty())
            putLong(Js8LogFragment.ARG_START_MS, callSelectedTime[c] ?: 0L)
            putLong(Js8LogFragment.ARG_FREQ_HZ, dialHz + monitorVm.getTxOffset().toLong())
        }
        navigateFromMain(R.id.navigation_js8_log, args)
    }

    /** Call Activity > Store Message...: held for [call] to collect (MessageReplyDialog, addCommandToStorage). */
    private fun storeMessage(call: String) {
        val box = EditText(requireContext()).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            filters = arrayOf(android.text.InputFilter.AllCaps())
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Message")
            .setMessage("Store this message locally for $call:")
            .setView(box)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val text = box.text.toString().trim()
                if (text.isEmpty()) return@setPositiveButton
                val my = prefs().getString("callsign", "").orEmpty().trim().uppercase(Locale.US)
                viewLifecycleOwner.lifecycleScope.launch {
                    com.js8call.example.data.Js8Mailbox.store(requireContext(), call, my, text, relayPath = my)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ---- HB button (PC hbMacroButton) ----------------------------------------------------------

    private fun hbInterval() = intPref("heartbeat_interval", 0)

    /** prepareHeartbeatMode: shown with Settings > HB on and a speed that sends heartbeats. */
    private fun hbShown(): Boolean {
        val speed = Js8Activity.txSubmode(prefs().getInt(Js8SettingsSchema.TX_SUBMODE_KEY, 0))
        return boolPref(Js8SettingsSchema.HB_KEY, false) &&
            speed in setOf(Js8Activity.SUBMODE_NORMAL, Js8Activity.SUBMODE_FAST, Js8Activity.SUBMODE_SLOW)
    }

    /** PC mode: the row's buttons as laid out (restored when HB hides). */
    private var pcButtonsOriginal: List<Pair<Int, Int>>? = null
    private val pcButtonIds = listOf(R.id.js8_btn_hb, R.id.js8_btn_cq, R.id.js8_btn_reply, R.id.js8_btn_snr, R.id.js8_btn_info,
        R.id.js8_btn_status, R.id.js8_btn_saved, R.id.js8_btn_directed, R.id.js8_btn_deselect, R.id.js8_btn_send, R.id.js8_btn_halt)

    private fun renderHb() {
        val v = view ?: return
        val hb = v.findViewById<View?>(R.id.js8_btn_hb) ?: return
        val shown = hbShown()
        val changed = (hb.visibility == View.VISIBLE) != shown
        hb.visibility = if (shown) View.VISIBLE else View.GONE
        if (changed) {
            layoutPcButtons(shown)
            layoutUprightButtons(shown)
            fitStatusText(shown)
        }
        renderHbLabel(System.currentTimeMillis())
    }

    /**
     * PC mode row (1062 PC dp): with HB the 11 buttons share it, each ~86 dp, the STATUS / Saved
     * gap kept (Rob's capture of the PC).
     */
    private fun layoutPcButtons(withHb: Boolean) {
        val v = view ?: return
        if (v.findViewById<View?>(R.id.js8_pc_buttons) == null) return
        val buttons = pcButtonIds.map { v.findViewById<View>(it) }
        val original = pcButtonsOriginal ?: buttons.map { b ->
            val lp = b.layoutParams as ViewGroup.MarginLayoutParams
            lp.marginStart to lp.width
        }.also { pcButtonsOriginal = it }
        val dp = resources.displayMetrics.density
        if (isFt8CallLayout(v)) {
            // FT8Call layout: every shown button the same width across the row (FT8Call v0.4.2),
            // the STATUS / Saved gap kept for the divider's grab spot (gen_pc_layout.py, same numbers)
            val shown = if (withHb) buttons else buttons.drop(1)
            val gap = 6f
            val splitGap = 31f
            val w = (1047f - (shown.size - 2) * gap - splitGap) / shown.size
            val status = shown.indexOf(v.findViewById<View>(R.id.js8_btn_status))
            shown.forEachIndexed { i, b ->
                val lp = b.layoutParams as ViewGroup.MarginLayoutParams
                lp.marginStart = ((15f + i * (w + gap) + (if (i > status) splitGap - gap else 0f)) * dp).toInt()
                lp.width = (w * dp).toInt()
                b.layoutParams = lp
            }
            splitters?.setButtonGap(15f + status * (w + gap) + w, 15f + (status + 1) * (w + gap) + splitGap - gap)
            return
        }
        // The divider's grab spot follows the STATUS / Saved gap
        val wHb = (1062f - 15f - 9 * 8f - 31f) / 11f
        if (withHb) splitters?.setButtonGap(15f + 5 * (wHb + 8f) + wHb, 15f + 6 * (wHb + 8f) + 23f)
        else splitters?.setButtonGap(null, null)
        buttons.forEachIndexed { i, b ->
            val lp = b.layoutParams as ViewGroup.MarginLayoutParams
            if (withHb) {
                val w = (1062f - 15f - 9 * 8f - 31f) / 11f
                val x = 15f + i * (w + 8f) + (if (i >= 6) 31f - 8f else 0f)
                lp.marginStart = (x * dp).toInt()
                lp.width = (w * dp).toInt()
            } else {
                lp.marginStart = original[i].first
                lp.width = original[i].second
            }
            b.layoutParams = lp
        }
    }

    /**
     * Upright top row with HB (Rob, 2026-10-20): HB, CQ, SNR, INFO small (~47 dp), REPLY ~59 dp,
     * STATUS ~71 dp so its text fits; without HB all five the same, as before.
     */
    private fun layoutUprightButtons(withHb: Boolean) {
        if (!isUprightLayout()) return
        val v = view ?: return
        val weights = listOf(R.id.js8_btn_hb to 0.8f, R.id.js8_btn_cq to 0.8f, R.id.js8_btn_reply to 1f,
            R.id.js8_btn_snr to 0.8f, R.id.js8_btn_info to 0.8f, R.id.js8_btn_status to 1.2f)
        weights.forEach { (id, w) ->
            val b = v.findViewById<View>(id)
            val lp = b.layoutParams as android.widget.LinearLayout.LayoutParams
            lp.weight = if (withHb) w else 1f
            b.layoutParams = lp
        }
    }

    /** The FT8Call layout (fragment_js8_ft8call): PC mode's window, FT8Call's look. */
    private fun isFt8CallLayout(v: View? = view) = v?.findViewById<View?>(R.id.js8_pc_window)?.tag == "ft8call"

    /** FT8Call layout header: "Next Beacon: 247 s" while the HB timer runs, empty when it is off. */
    private fun renderNextBeacon(now: Long) {
        val label = view?.findViewById<TextView?>(R.id.js8_next_beacon) ?: return
        val timer = com.js8call.example.service.HeartbeatTimer
        val text = if (timer.on && timer.nextMs > 0) {
            val secs = Math.round((timer.nextMs - now) / 1000.0)
            if (secs > 0) "Next Beacon: $secs s" else "Next Beacon: now"
        } else ""
        if (label.text.toString() != text) label.text = text
    }

    /** The upright page (fragment_js8_main), not PC mode or Swipe. */
    private fun isUprightLayout() = view?.findViewById<View?>(R.id.js8_pc_buttons) == null &&
        view?.findViewById<View?>(R.id.js8_pager) == null

    /** STATUS text size from the layout (restored when HB hides). */
    private var statusTextPx = 0f

    /** Swipe: eleven buttons share the row with HB, so STATUS shrinks to fit (upright widens it instead). */
    private fun fitStatusText(withHb: Boolean) {
        if (view?.findViewById<View?>(R.id.js8_pager) == null) return // PC mode ~86 dp, upright: STATUS is wider
        val status = view?.findViewById<TextView?>(R.id.js8_btn_status) ?: return
        if (statusTextPx == 0f) statusTextPx = status.textSize
        if (withHb) {
            status.maxLines = 1
            androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(status,
                (statusTextPx * 0.6f).toInt().coerceAtLeast(1), statusTextPx.toInt(), 1, android.util.TypedValue.COMPLEX_UNIT_PX)
        } else {
            androidx.core.widget.TextViewCompat.setAutoSizeTextTypeWithDefaults(status, androidx.core.widget.TextViewCompat.AUTO_SIZE_TEXT_TYPE_NONE)
            status.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, statusTextPx)
        }
    }

    /** updateHBButtonDisplay: "HB" / "HB + ACK", "(seconds)" to the next one while the timer runs. */
    private fun renderHbLabel(now: Long) {
        val hb = view?.findViewById<TextView?>(R.id.js8_btn_hb) ?: return
        if (hb.visibility != View.VISIBLE) return
        val timer = com.js8call.example.service.HeartbeatTimer
        // presentlyWantHBReplies: AUTO + ACK, and no QSO under way when HB pauses for QSOs
        val ack = boolPref(Js8SettingsSchema.AUTOREPLY_KEY, false) && boolPref(Js8SettingsSchema.HB_ACK_KEY, false) &&
            (!boolPref("js8cfg_heartbeat_qso_pause", true) || selectedCall == null)
        val base = if (ack) "HB + ACK" else "HB"
        val text = if (timer.on) {
            val secs = Math.round((timer.nextMs - now) / 1000.0)
            val count = if (timer.nextMs > 0 && secs > 0) "$secs" else "now"
            // Upright: only the seconds while counting, the small button can't hold more (Rob, 2026-10-20)
            if (isUprightLayout()) count else "$base ($count)"
        } else base
        if (hb.text.toString() != text) hb.text = text
        hb.isActivated = timer.on
    }

    /** on_hbMacroButton_toggled: no repeat time = one heartbeat now, otherwise the timer on / off. */
    private fun onHbClicked() {
        if (hbInterval() <= 0) {
            setHbTimer(false)
            sendHbNow()
        } else {
            setHbTimer(!com.js8call.example.service.HeartbeatTimer.on)
        }
    }

    private fun setHbTimer(on: Boolean) {
        // starting the timer deselects unless heartbeats may go out during a QSO
        if (on && boolPref("js8cfg_heartbeat_qso_pause", true)) clearSelection()
        com.js8call.example.service.HeartbeatTimer.on = on
        requireContext().startService(Intent(requireContext(), JS8EngineService::class.java).apply {
            action = JS8EngineService.ACTION_HB_TIMER
            putExtra(JS8EngineService.EXTRA_HB_TIMER_ON, on)
        })
        renderHbLabel(System.currentTimeMillis())
    }

    private fun sendHbNow() {
        if (listenOnly() || !ensureCallsignSet(alert = true)) return
        requireContext().startService(Intent(requireContext(), JS8EngineService::class.java).apply {
            action = JS8EngineService.ACTION_SEND_HB
        })
    }

    /**
     * buildHeartbeatMenu + buildRepeatMenu(isLowInterval = false): the HB repeat times 10 / 15 / 30 / 60.
     * The time is Station > Heartbeat interval (one setting).
     */
    private fun showHbMenu(anchor: View) = showRepeatMenu(anchor, "Heartbeat", listOf(10, 15, 30, 60), hbInterval(),
        com.js8call.example.service.HeartbeatTimer.on, "Send Heartbeat Now",
        toggle = { setHbTimer(!com.js8call.example.service.HeartbeatTimer.on) },
        setInterval = { min -> prefs().edit().putString("heartbeat_interval", min.toString()).apply(); setHbTimer(min > 0) },
        sendNow = { sendHbNow() })

    /** buildCQMenu + buildRepeatMenu(isLowInterval = true): the CQ repeat times 1 / 5 / 10 / 15 (PC CQInterval). */
    private fun showCqMenu(anchor: View) = showRepeatMenu(anchor, "CQ", listOf(1, 5, 10, 15), cqInterval(),
        com.js8call.example.service.CqTimer.on, "Send CQ Now",
        toggle = { setCqTimer(!com.js8call.example.service.CqTimer.on) },
        setInterval = { min -> prefs().edit().putInt(com.js8call.example.service.CqTimer.KEY_INTERVAL, min).apply(); setCqTimer(min > 0) },
        sendNow = { sendCq(repeat = false) })

    /**
     * The PC's HB / CQ button menu: Start / Stop <name> Timer (with a repeat time), On demand /
     * the repeat times / Repeat every N minutes (Custom Interval), the one in use ticked, and
     * Send ... Now. A repeat time starts the timer, On demand stops it.
     */
    private fun showRepeatMenu(anchor: View, name: String, minutes: List<Int>, interval: Int, on: Boolean, nowText: String,
                               toggle: () -> Unit, setInterval: (Int) -> Unit, sendNow: () -> Unit) {
        val menu = popupMenu(anchor)
        val m = menu.menu
        androidx.core.view.MenuCompat.setGroupDividerEnabled(m, true)
        if (interval > 0) m.add(1, 1, 1, if (on) "Stop $name Timer" else "Start $name Timer")
        val repeats = listOf(0 to "On demand / do not repeat") +
            minutes.map { it to if (it == 1) "Repeat every 1 minute" else "Repeat every $it minutes" }
        val custom = interval !in repeats.map { it.first }
        repeats.forEachIndexed { i, (min, text) -> m.add(2, 100 + min, 10 + i, text).apply { isCheckable = true; isChecked = min == interval } }
        m.add(2, 99, 20, if (custom) "Repeat every $interval minutes (Custom Interval)" else "Repeat every N minutes (Custom Interval)")
            .apply { isCheckable = true; isChecked = custom }
        m.setGroupCheckable(2, true, true)
        m.add(3, 2, 30, nowText)
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> toggle()
                2 -> sendNow()
                99 -> askMinutes(interval, setInterval)
                else -> setInterval(item.itemId - 100)
            }
            true
        }
        menu.show()
    }

    /** Repeat every N minutes (Custom Interval): QInputDialog "Minutes", 1-1440. */
    private fun askMinutes(current: Int, onPick: (Int) -> Unit) {
        val box = EditText(requireContext()).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(current.takeIf { it > 0 }?.toString() ?: "1")
            selectAll()
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Repeat every N minutes")
            .setMessage("Minutes")
            .setView(box)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val n = box.text.toString().toIntOrNull() ?: return@setPositiveButton
                onPick(n.coerceIn(1, 1440))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ---- CQ button (PC cqMacroButton) ----------------------------------------------------------

    private fun cqInterval() = intPref(com.js8call.example.service.CqTimer.KEY_INTERVAL, 0)

    /** on_cqMacroButton_toggled: no repeat time = one CQ now, otherwise the CQ timer on / off. */
    private fun onCqClicked() {
        if (cqInterval() <= 0) sendCq(repeat = false)
        else setCqTimer(!com.js8call.example.service.CqTimer.on)
    }

    /** Starting the CQ timer always deselects (PC clearCallsignSelected). */
    private fun setCqTimer(on: Boolean) {
        if (on) clearSelection()
        com.js8call.example.service.CqTimer.on = on
        requireContext().startService(Intent(requireContext(), JS8EngineService::class.java).apply {
            action = JS8EngineService.ACTION_CQ_TIMER
            putExtra(JS8EngineService.EXTRA_HB_TIMER_ON, on)
        })
        renderCqLabel(System.currentTimeMillis())
    }

    /**
     * sendCQ: a one-off CQ stops the CQ and HB timers; the CQ Message goes in the Typing Box and is
     * sent (from the timer always, by hand when "Immediately transmit ..." is on). False when
     * nothing could be put in the Typing Box (a message is waiting or being sent).
     */
    private fun sendCq(repeat: Boolean): Boolean {
        if (!repeat) {
            if (com.js8call.example.service.CqTimer.on) setCqTimer(false)
            if (com.js8call.example.service.HeartbeatTimer.on) setHbTimer(false)
        }
        if (activityVm.typed.value != null) return repeat // the PC adds nothing while a message waits
        clearSelection()
        val text = macro(prefs().getString("js8cfg_cq_message", "CQ CQ CQ <MYGRID4>").orEmpty().ifBlank { "CQ CQ CQ <MYGRID4>" })
        addMessageText(text, send = !repeat)
        if (repeat) sendTyped()
        return true
    }

    /** updateCQButtonDisplay: "CQ (seconds)" to the next one while the timer runs. */
    private fun renderCqLabel(now: Long) {
        val cq = view?.findViewById<TextView?>(R.id.js8_btn_cq) ?: return
        val timer = com.js8call.example.service.CqTimer
        val text = if (timer.on) {
            val secs = Math.round((timer.nextMs - now) / 1000.0)
            val count = if (timer.nextMs > 0 && secs > 0) "$secs" else "now"
            if (isUprightLayout()) count else "CQ ($count)" // upright: only the seconds (Rob)
        } else "CQ"
        if (cq.text.toString() != text) cq.text = text
        cq.isActivated = timer.on
    }

    /** PC setSubmode: becomes the Speed in Settings too. */
    private fun jumpToSpeed(submode: Int) {
        prefs().edit().putInt(Js8SettingsSchema.TX_SUBMODE_KEY, submode).apply()
        renderSpeed()
    }

    /** PC addStation: a callsign joins Call Activity, an @GROUP your groups, @ALLCALL comes back. */
    private fun askAddStation() {
        val box = EditText(requireContext()).apply {
            isSingleLine = true
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            filters = arrayOf(android.text.InputFilter.AllCaps())
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Add New Station or Group")
            .setMessage("Station or Group Callsign:")
            .setView(box)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val call = box.text.toString().trim().uppercase(Locale.US)
                if (call.isEmpty()) return@setPositiveButton
                val p = prefs()
                when {
                    call == "@ALLCALL" -> p.edit().putBoolean("js8cfg_avoid_allcall", false).apply()
                    call.startsWith("@") -> {
                        if (!Regex("@[A-Z0-9/]+").matches(call)) { showMessage("$call is not a valid group"); return@setPositiveButton }
                        val groups = p.getString("my_groups", "").orEmpty().split(',', ' ').map { it.trim().uppercase(Locale.US) }
                            .filter { it.isNotEmpty() }
                        if (call !in groups) p.edit().putString("my_groups", (groups + call).joinToString(",")).apply()
                    }
                    Js8Callsign.isValid(call) -> activityVm.activity.callActivity
                        .getOrPut(call) { Js8Activity.CallDetail(call, offset = -1, utcMs = System.currentTimeMillis()) }
                    else -> { showMessage("$call is not a valid callsign or group"); return@setPositiveButton }
                }
                activityVm.changed()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** PC removeStation: @ALLCALL hidden, a group leaves your groups, a station leaves the list. */
    private fun removeStation(call: String) {
        val p = prefs()
        when {
            call == "@ALLCALL" -> p.edit().putBoolean("js8cfg_avoid_allcall", true).apply()
            call.startsWith("@") -> {
                val groups = p.getString("my_groups", "").orEmpty().split(',', ' ').map { it.trim() }
                    .filter { it.isNotEmpty() && !it.equals(call, ignoreCase = true) }
                p.edit().putString("my_groups", groups.joinToString(",")).apply()
            }
            else -> activityVm.activity.callActivity.remove(call)
        }
        clearSelection()
        activityVm.changed()
    }

    /** PC blockStation: onto the RX block list (Station > Networking & Autoreply). */
    private fun blockStation(call: String) {
        val p = prefs()
        val list = p.getString("js8cfg_rx_blocklist", "").orEmpty().split(',', ' ').map { it.trim() }.filter { it.isNotEmpty() }
        if (list.none { it.equals(call, ignoreCase = true) }) {
            p.edit().putString("js8cfg_rx_blocklist", (list + call).joinToString(",")).apply()
        }
        activityVm.changed()
    }

    private fun showMessage(text: String) {
        AlertDialog.Builder(requireContext()).setMessage(text).setPositiveButton(android.R.string.ok, null).show()
    }

    /** REPLY, SNR, INFO, Directed, Deselect are enabled only while a callsign is selected (desktop behaviour). */
    private fun renderSelection() {
        Js8Api.setSelectedCall(selectedCall) // API: STATION.STATUS SELECTED, RX.GET_CALL_SELECTED
        selectedCall?.let { if (it !in callSelectedTime) callSelectedTime[it] = System.currentTimeMillis() }
        requestTextStats() // the directed callsign changes the parts (PC m_txTextDirtyLastSelectedCall)
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
        waterfall.onDisplayChanged = { syncRuler() }
        // Display Decode Attempts (UI_Constructor::processDecodeEvent): yellow tick when a decode
        // starts; red bracket for a decode; candidates within 2 s by sync quality
        val attempts = object : com.js8call.example.service.DecodeAttempts.Listener {
            override fun onDecodeStarted() {
                if (view != null) waterfall.drawHorizontalLine(Color.YELLOW, 0, 5)
            }

            override fun onSyncState(kind: Int, mode: Int, freq: Float, dt: Float, candidate: Int) {
                if (view == null) return
                val color = when {
                    kind == 1 -> Color.RED
                    kotlin.math.abs(dt * 1000) > 2000 -> return
                    candidate < 10 -> Color.rgb(0, 128, 128) // Qt::darkCyan
                    candidate <= 15 -> Color.CYAN
                    candidate <= 21 -> Color.WHITE
                    else -> return
                }
                waterfall.drawDecodeLine(color, freq, freq + Js8Activity.bandwidthHz(mode))
            }
        }
        com.js8call.example.service.DecodeAttempts.listener = attempts
        viewLifecycleOwner.lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onDestroy(owner: androidx.lifecycle.LifecycleOwner) {
                if (com.js8call.example.service.DecodeAttempts.listener === attempts) com.js8call.example.service.DecodeAttempts.listener = null
            }
        })
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

    /** Frequency scale = waterfall range (Waterfall Controls > Display > Start, Bins Per Pixel). */
    private fun syncRuler() {
        if (waterfall.width <= 0) return
        ruler.startHz = waterfall.startHz()
        ruler.hzPerPixel = waterfall.hzPerPixel()
    }

    // ---- Engine -------------------------------------------------------------------------

    private fun observe() {
        // New decodes are gathered and drawn by the clock tick (PC: m_rxDisplayDirty)
        activityVm.version.observe(viewLifecycleOwner) { requestRender() }
        activityVm.typed.observe(viewLifecycleOwner) { renderTyped(it) }
        observeInbox()
        monitorVm.status.observe(viewLifecycleOwner) { status ->
            val running = status.state == EngineState.RUNNING || status.state == EngineState.STARTING
            btnRx.isActivated = running
            levelMeter.levelDb = if (running) (status.powerDb + 90f).coerceIn(0f, 90f) else 0f
            renderStatus()
            offsetSlider?.let { if (it.progress != status.txOffsetHz.toInt()) it.progress = status.txOffsetHz.toInt() }
            renderOffsetBox()
            wfControls?.setTimingState(status.timeDriftMs, status.autoDriftOn)
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
        // PC updateButtonDisplay: HB greyed while transmitting (and with SWL)
        view?.findViewById<View?>(R.id.js8_btn_hb)?.isEnabled = !transmitting && !listenOnly()
        view?.findViewById<View?>(R.id.js8_btn_cq)?.isEnabled = !transmitting && !listenOnly()
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
