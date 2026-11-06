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
import kotlin.math.roundToLong
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
        const val USER_GUIDE_URL = "https://js8call-improved.github.io/JS8Call-improved/d6/d14/md_docs_2user__guide_2JS8Call__User__Guide.html"
        /** PC on_actionCopyright_Notice_triggered. */
        const val COPYRIGHT_NOTICE = "If you make fair use of any part of this program under terms of the GNU " +
            "General Public License, you must display the following copyright notice prominently in your derivative work:\n\n" +
            "\"The algorithms, source code, look-and-feel of WSJT-X and related programs, and protocol specifications for " +
            "the modes FSK441, FT8, JT4, JT6M, JT9, JT65, JTMS, QRA64, ISCAT, MSK144 are Copyright (C) 2001-2018 by one or " +
            "more of the following authors: Joseph Taylor, K1JT; Bill Somerville, G4WJS; Steven Franke, K9AN; Nico Palermo, " +
            "IV3NWV; Greg Beam, KI7MT; Michael Black, W9MDB; Edson Pereira, PY2SDR; Philip Karn, KA9Q; and other members of " +
            "the WSJT Development Group.\n\nFurther, the source code of JS8Call contains material Copyright (C) 2018-2019 " +
            "by Jordan Sherer, KN4CRD.\""
        private const val ID_SUGGEST_FIRST = 9100
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
        private const val ID_SAVED_CURRENT = 998
        private const val ID_SAVED_FIRST = 1000
        private const val ID_DIRECTED_FIRST = 2000
        private const val ID_RELAY_FIRST = 3000
        private const val ID_RESTORE = 5
        private const val ID_CUT = 33
        private const val ID_COPY = 34
        private const val ID_PASTE = 35

        const val PREF_LANDSCAPE_LAYOUT = "landscape_layout"
        private const val KEY_FULLSCREEN = "js8cfg_fullscreen"
        private const val PRESS_ANCHOR = "press_anchor"
        /** Message Window menu height: 6 rows of 36 dp (ThemeOverlay.Js8PopupMenu), 2 group dividers, padding. */
        private const val MESSAGE_MENU_DP = 6 * 36 + 2 * 8 + 16
        /** Swipe page kept across rotation / returning from the Menu page. */
        private var swipePage = 1
        /** checkStartupWarnings runs once per app start, not on each rotation. */
        private var startupChecked = false
        /** RX / TX set from Receiver / Transmitter off at startup once per app start, not on each rotation. */
        private var startupRxTxDone = false

        /** MainActivity closing (Back on the main screen, PC closeEvent): Android keeps the process, the next open is a new start. */
        fun appClosed() {
            startupRxTxDone = false
            startupChecked = false // AUTO from Turn autoreply on at startup again, as a PC restart
        }
        /** When each callsign was first selected: the QSO start time for Log QSO (m_callSelectedTime). */
        private val callSelectedTime = mutableMapOf<String, Long>()
    }

    private lateinit var monitorVm: MonitorViewModel
    private var typingPressOffset = 0
    private lateinit var decodeVm: DecodeViewModel
    private lateinit var transmitVm: TransmitViewModel
    private lateinit var activityVm: Js8ActivityViewModel

    // Not in the upright layout: Frequency and Offset are in the Settings window there
    private var freqDisplay: TextView? = null
    private var offsetSlider: SeekBar? = null
    private var offsetValue: TextView? = null
    private lateinit var callsign: TextView
    private lateinit var clock: TextView
    /** Time drift beside the callsign, and its invisible twin that keeps the callsign centred. */
    private var clockDrift: TextView? = null
    private var clockDriftPad: TextView? = null
    private lateinit var btnRx: View
    private lateinit var btnTx: View
    private lateinit var bandActivity: Js8TableView
    private lateinit var callActivity: Js8TableView
    /** Upright and Swipe only; PC mode opens the Message Inbox screen instead (Rob, 2026-10-20). */
    private var inbox: Js8TableView? = null
    private lateinit var messageWindow: TextView
    private lateinit var typingBox: EditText
    private var messageWindowPx = 0f
    private var typingBoxPx = 0f
    private var txTextScale = 1f // Transmitted: Font against Received: Font
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
    private val saveMessageWindow = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            requireContext().contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(messageWindow.text.toString()) }
        } catch (e: Exception) {
            android.app.AlertDialog.Builder(requireContext()).setTitle("Save Failed")
                .setMessage("Error writing the file: ${e.message}").setPositiveButton("OK", null).show()
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
            // PC updateClockUI: the time drift after the time
            val drift = monitorVm.status.value?.timeDriftMs ?: 0L
            val driftText = if (drift == 0L) "" else "${if (drift > 0) "+" else ""}${drift}ms"
            val driftView = clockDrift
            if (driftView != null) {
                // every layout: the drift right of the callsign (Rob, 2026-11-03)
                clock.text = clockFormat.format(Date(now))
                if (driftView.text.toString() != driftText) {
                    driftView.text = driftText
                    clockDriftPad?.text = driftText
                    driftView.visibility = if (drift == 0L) View.GONE else View.VISIBLE
                    clockDriftPad?.visibility = if (drift == 0L) View.GONE else View.INVISIBLE
                }
            } else {
                clock.text = clockFormat.format(Date(now)) + if (drift == 0L) "" else " ($driftText)"
            }
            progress.seconds = ((now / 1000) % progress.period).toInt()
            if (activityVm.activity.processIdle(now, Js8Activity.Config(prefs()))) activityDirty = true
            // PC updateTimer: lists redrawn at most once a second, when something changed;
            // ages ("now", "15s", "2m") move on every 5 seconds even without new decodes
            if (activityDirty || (now / 1000) % 5 == 0L) renderActivity()
            renderHbLabel(now)
            renderNextBeacon(now)
            renderCqLabel(now)
            renderSendText(now)
            renderCallsign()
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
        clockDrift = view.findViewById(R.id.js8_clock_drift)
        clockDriftPad = view.findViewById(R.id.js8_clock_drift_pad)
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
        messageWindowPx = messageWindow.textSize
        // PC dialFreqUpButton / dialFreqDownButton (sideways header; upright has no arrows)
        view.findViewById<View?>(R.id.js8_freq_up)?.setOnClickListener { stepDial(250) }
        view.findViewById<View?>(R.id.js8_freq_down)?.setOnClickListener { stepDial(-250) }
        typingBoxPx = typingBox.textSize
        statusState = view.findViewById(R.id.js8_status_state)
        statusFreq = view.findViewById(R.id.js8_status_freq)
        statusAutoReply = view.findViewById(R.id.js8_status_autoreply)
        progress = view.findViewById(R.id.js8_status_progress)
        // Tablets (Rob, 2026-11-05): upright header buttons half the width they stretch to,
        // centred under the clock. The 36 dp is the three 12 dp gaps between the four columns.
        if (resources.configuration.smallestScreenWidthDp >= 600) {
            view.findViewById<View?>(R.id.js8_header_buttons_row)?.let { row ->
                val header = row.parent as View
                val gaps = (36 * resources.displayMetrics.density).toInt()
                header.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                    val inner = header.width - header.paddingLeft - header.paddingRight
                    val w = (inner - gaps) / 2 + gaps
                    if (w > 0 && row.layoutParams.width != w) row.layoutParams = row.layoutParams.apply { width = w }
                }
            }
        }
        // Upright: Receiving | wpm box | cycle progress bar, which takes what is left and ends at
        // the page's right edge (Rob, 2026-10-19 / 2026-10-22)
        // Tablets (Rob, 2026-11-05): when Saved is wider than Receiving's 110 dp, Receiving takes
        // Saved's width and the progress bar Halt's, still ending at the right edge; the wpm box
        // fills the middle. Phones keep 110 dp | 150 dp | what is left.
        view.findViewById<View?>(R.id.js8_status_scroll)?.let { scroll ->
            val state = view.findViewById<View>(R.id.js8_status_state)
            val wpm = view.findViewById<View>(R.id.js8_status_wpm)
            val saved = view.findViewById<View>(R.id.js8_btn_saved)
            val halt = view.findViewById<View>(R.id.js8_btn_halt)
            val stateW0 = state.layoutParams.width
            val wpmW0 = wpm.layoutParams.width
            scroll.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                val gap = (progress.layoutParams as ViewGroup.MarginLayoutParams).marginStart
                val wpmGap = (wpm.layoutParams as ViewGroup.MarginLayoutParams).marginStart
                val inner = scroll.width - scroll.paddingLeft - scroll.paddingRight
                val wide = saved.width > stateW0
                val stateW = if (wide) saved.width else stateW0
                val wpmW = if (wide) inner - stateW - wpmGap - gap - halt.width else wpmW0
                val w = if (wide) halt.width else inner - stateW - gap - wpmW - wpmGap
                if (state.layoutParams.width != stateW) state.layoutParams = state.layoutParams.apply { width = stateW }
                if (wpmW > 0 && wpm.layoutParams.width != wpmW) wpm.layoutParams = wpm.layoutParams.apply { width = wpmW }
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
        // Upright and Swipe: the lines between windows can be dragged (Rob, 2026-10-22)
        when {
            isUprightLayout() -> Js8LineDividers.setUpUpright(view, prefs())
            view.findViewById<View?>(R.id.js8_pager) != null -> Js8LineDividers.setUpSwipe(view, prefs())
        }
        setUpTables()
        renderConfigLabel()
        view.findViewById<Js8GainSliderView?>(R.id.js8_gain_slider)?.let { s ->
            s.value = prefs().getString(JS8EngineService.PREF_OUT_ATTENUATION, null)?.toIntOrNull() ?: 0
            s.onValueChanged = { v -> prefs().edit().putString(JS8EngineService.PREF_OUT_ATTENUATION, v.toString()).apply() }
            // PC: Remember power settings by band and TUNE move the slider (mw 4252, 5180, 5204)
            powerListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
                if (key == JS8EngineService.PREF_OUT_ATTENUATION) s.value = p.getString(key, null)?.toIntOrNull() ?: 0
            }.also { prefs().registerOnSharedPreferenceChangeListener(it) }
        }
        // PC onRigStateChanged when the rig first comes online: RX on unless "Receiver (RX) off at startup",
        // TX on unless "Transmitter (TX) off at startup" (Rob, 2026-10-21: RX was off after every fresh start)
        val firstStart = !startupRxTxDone
        if (firstStart) {
            startupRxTxDone = true
            prefs().edit().putBoolean("js8cfg_tx_enabled", !boolPref("js8cfg_transmit_off", false)).apply()
            if (boolPref("js8cfg_check_updates", true)) Js8Updates.check(requireActivity(), false) // PC checkStartupWarnings
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
            // (UI_Constructor: setChecked(Configuration::autoreply_on_at_startup)): ticked on, unticked off, as the PC
            // (Rob, 2026-11-03: "just do what the program does"; was unticked = AUTO as left, 2026-10-22).
            // A new install starts ticked (PC AutoreplyOnAtStartup default true). Before the split that checkbox
            // was the live switch itself, so an older install without the new key keeps its old setting.
            val p = prefs()
            val e = p.edit()
            val onAtStartup = if (p.contains(Js8SettingsSchema.AUTOREPLY_STARTUP_KEY)) p.getBoolean(Js8SettingsSchema.AUTOREPLY_STARTUP_KEY, true)
                else (if (p.contains(Js8SettingsSchema.AUTOREPLY_KEY)) p.getBoolean(Js8SettingsSchema.AUTOREPLY_KEY, false) else true)
                    .also { e.putBoolean(Js8SettingsSchema.AUTOREPLY_STARTUP_KEY, it) }
            val startAuto = onAtStartup &&
                p.getString("callsign", "").orEmpty().isNotBlank() && p.getString("grid", "").orEmpty().isNotBlank()
            e.putBoolean(Js8SettingsSchema.AUTOREPLY_KEY, startAuto).apply()
            // UI_Constructor::checkStartupWarnings: no callsign or grid opens Station
            view.post { if (isAdded) ensureCallsignSet(alert = false) }
        }
    }

    override fun onDestroyView() {
        if (Js8Api.controller === apiController) Js8Api.controller = null
        powerListener?.let { prefs().unregisterOnSharedPreferenceChangeListener(it) }
        powerListener = null
        if (com.js8call.example.service.CqTimer.fire === cqFire) com.js8call.example.service.CqTimer.fire = null
        super.onDestroyView()
    }

    /** Moves the Power slider when the service changes the power. */
    private var powerListener: android.content.SharedPreferences.OnSharedPreferenceChangeListener? = null

    private val cqFire: () -> Boolean = { view != null && !listenOnly() && sendCq(repeat = true) }

    /** What the API commands do on this screen (PC networkMessage on the main window). */
    private val apiController = object : Js8Api.Controller {
        override fun typingText(): String = if (view != null) typingBox.text.toString() else ""

        // PC addMessageText(text, clear = true): nothing while a message waits or is sent
        override fun setTypingText(text: String) {
            if (view == null || activityVm.typed.value != null) return
            activityVm.typedClearPending = false // the box was emptied already; this text stays
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
        renderCallsign()
        statusAutoReply.text = if (prefs.getBoolean("autoreply_enabled", false)) "Auto Reply: On" else "Auto Reply: Off"
        dialHz = prefs.getString("last_frequency", "14078000")?.toLongOrNull() ?: 14_078_000L
        renderFrequency()
        renderSpeed()
        renderTransmitEnabled()
        requireView().findViewById<View>(R.id.js8_btn_spot).isActivated = boolPref("psk_reporter", true)
        activityVm.changed() // groups and @ALLCALL in Call Activity
    }

    /** Settings > Colors font sizes. 10 pt, the default on those buttons, is the size the screens were laid out for. */
    private fun applyUiFonts(prefs: android.content.SharedPreferences) {
        fun scale(key: String) = (prefs.getString("js8cfg_ui_$key", null)?.toFloatOrNull() ?: 10f) / 10f
        bandActivity.setTextScale(scale("table_font"))
        callActivity.setTextScale(scale("table_font"))
        messageWindow.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, messageWindowPx * scale("rx_font"))
        typingBox.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, typingBoxPx * scale("compose_font"))
        txTextScale = scale("tx_font") / scale("rx_font")
        // PC font dialog: family and style
        fun face(key: String) = Js8SettingsFragment.typefaceFor(requireContext(), prefs.getString("js8cfg_ui_${key}_family", null), prefs.getString("js8cfg_ui_${key}_style", null))
        face("table_font")?.let { bandActivity.setFace(it); callActivity.setFace(it) }
        face("rx_font")?.let { messageWindow.typeface = it }
        face("compose_font")?.let { typingBox.typeface = it }
    }

    /** Station > UI colors; a color never picked keeps the screen's own. */
    private fun applyUiColors(prefs: android.content.SharedPreferences) {
        fun color(key: String) = prefs.getString("js8cfg_ui_$key", null)?.let { runCatching { Color.parseColor(it) }.getOrNull() }
        listOf(bandActivity, callActivity).forEach { it.setColors(color("table_bg"), color("table_fg"), color("selected_bg")) }
        color("rx_bg")?.let { messageWindow.setBackgroundColor(it) }
        color("compose_bg")?.let { typingBox.setBackgroundColor(it) }
        color("compose_fg")?.let { typingBox.setTextColor(it) }
        Js8TypingSpans.apply(typingBox.text, prefs, selectedCall)
    }

    override fun onResume() {
        super.onResume()
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(rigErrorReceiver, android.content.IntentFilter(JS8EngineService.ACTION_RIG_ERROR))
        showRigError()
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        renderCallsign()
        statusAutoReply.text = if (prefs.getBoolean("autoreply_enabled", false)) "Auto Reply: On" else "Auto Reply: Off"
        dialHz = prefs.getString("last_frequency", "14078000")?.toLongOrNull() ?: 14_078_000L
        renderFrequency()
        renderSpeed()
        renderTransmitEnabled() // Station > SWL (Listening only) may have changed
        splitters?.refresh()
        applyPanels()
        // Offset from Settings (or saved last time)
        val savedOffset = prefs.getString(Js8SettingsSchema.TX_OFFSET_KEY, null)?.toFloatOrNull()
        if (savedOffset != null && savedOffset != monitorVm.getTxOffset()) {
            offsetSlider?.progress = savedOffset.toInt()
            applyOffset(savedOffset)
        }
        waterfall.txOffsetHz = monitorVm.getTxOffset() // a rebuilt screen starts on 1500 otherwise
        renderOffsetBox()
        applyUiFonts(prefs)
        applyUiColors(prefs)
        renderConfigLabel()
        applyFullscreen()
        ui.post(tick)
    }

    private val rigErrorReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: Intent) = showRigError()
    }

    /** PC m_rigErrorMessageBox: Retry, Configurations..., OK (Settings, radio page), Cancel (closes the program). */
    private fun showRigError() {
        val reason = JS8EngineService.pendingRigError ?: return
        JS8EngineService.pendingRigError = null
        val context = requireContext()
        val pad = (16 * resources.displayMetrics.density).toInt()
        val config = android.widget.Button(context).apply { text = "Configurations..."; isAllCaps = false }
        // PC: the reason is the box's detailed text, under Show Details... (UI_Constructor 97-98, mainwindow 5499)
        val details = TextView(context).apply { text = reason; visibility = View.GONE; setTextIsSelectable(true) }
        val show = android.widget.Button(context).apply {
            text = "Show Details..."; isAllCaps = false
            setOnClickListener {
                val open = details.visibility != View.VISIBLE
                details.visibility = if (open) View.VISIBLE else View.GONE
                text = if (open) "Hide Details..." else "Show Details..."
            }
        }
        val box = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(pad, 0, pad, 0)
            addView(config); addView(show); addView(details)
        }
        val dialog = android.app.AlertDialog.Builder(context).setTitle("Rig Control Error")
            .setMessage("Do you want to reconfigure the radio interface?")
            .setView(box)
            .setPositiveButton("OK") { _, _ -> openStation("Radio") }
            .setNeutralButton("Retry") { _, _ ->
                context.startService(Intent(context, JS8EngineService::class.java).setAction(JS8EngineService.ACTION_RIG_RETRY))
            }
            .setNegativeButton("Cancel") { _, _ ->
                context.stopService(Intent(context, JS8EngineService::class.java))
                requireActivity().finishAndRemoveTask()
            }
            .create()
        config.setOnClickListener { dialog.dismiss(); Js8Configurations.showMenu(this, popupMenu(view?.findViewById(R.id.js8_menubar_configurations) ?: requireView())) }
        dialog.setCanceledOnTouchOutside(false)
        dialog.show()
    }

    override fun onPause() {
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(rigErrorReceiver)
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
        // Both lists start with the last heard on top (PC Sort By "Last heard timestamp (recent first)",
        // Rob 2026-10-22); a header tapped is kept after a restart
        bandActivity.defaultSortKey = "timestamp"
        callActivity.defaultSortKey = "timestamp"
        // buildBandActivitySortByMenu, buildCallActivitySortByMenu
        val heardSpeed = listOf(
            Triple("Last heard timestamp (oldest first)", "timestamp", false),
            Triple("Last heard timestamp (recent first)", "timestamp", true),
            Triple("SNR (weakest first)", "snr", true),
            Triple("SNR (strongest first)", "snr", false),
            Triple("Mode Speed (slowest first)", "submode", true),
            Triple("Mode Speed (fastest first)", "submode", false))
        bandActivity.sortChoices = listOf(Triple("Frequency offset", "offset", true)) + heardSpeed
        callActivity.sortChoices = listOf(
            Triple("Callsign", "callsign", true),
            Triple("Callsigns Replied (recent first)", "ackTimestamp", true),
            Triple("Frequency offset", "offset", true),
            Triple("Distance (closest first)", "distance", true),
            Triple("Distance (farthest first)", "distance", false),
            Triple("Azimuth", "azimuth", true)) + heardSpeed
        bandActivity.setColumns(if (upright) Js8SettingsSchema.BAND_TABLE_UPRIGHT else "band", listOf(
            Js8TableView.Column("offset", "Offset", 48, defaultVisible = !upright, numeric = true, menuLabel = "Frequency Offset"),
            Js8TableView.Column("timestamp", "Age", 36, numeric = true, menuLabel = "Last heard timestamp"),
            Js8TableView.Column("snr", "SNR", 36, defaultVisible = !upright, numeric = true),
            Js8TableView.Column("tdrift", "Time Delta", 70, defaultVisible = false, numeric = true),
            Js8TableView.Column("submode", "Speed", 76, defaultVisible = false, menuLabel = "Mode Speed"),
            Js8TableView.Column("message", "Message(s)", 300, stretch = true)
        ))
        // PC buildShowColumnsMenu (mw 4669-4694): no Message(s) entry
        bandActivity.menuOrder = listOf("offset", "timestamp", "snr", "tdrift", "submode")
        callActivity.setColumns("call", listOf(
            // PC displayCallActivity: shown only while a row has an icon, not in Show Columns
            Js8TableView.Column("icon", "★", 22, onlyWithValues = true),
            Js8TableView.Column("callsign", "Callsigns", 100, menuLabel = "Callsign"),
            Js8TableView.Column("timestamp", "Age", 36, numeric = true, menuLabel = "Last heard timestamp"),
            Js8TableView.Column("snr", "SNR", 36, numeric = true),
            Js8TableView.Column("offset", "Offset", 48, numeric = true, menuLabel = "Frequency Offset"),
            Js8TableView.Column("tdrift", "Time Delta", 70, defaultVisible = false, numeric = true),
            Js8TableView.Column("submode", "Speed", 76, defaultVisible = false, menuLabel = "Mode Speed"),
            Js8TableView.Column("grid", "Grid", 56, defaultVisible = false, menuLabel = "Grid Locator"),
            Js8TableView.Column("distance", "Distance", 64, defaultVisible = false, numeric = true),
            Js8TableView.Column("azimuth", "°", 60, menuLabel = "Azimuth", defaultVisible = false, numeric = true),
            Js8TableView.Column("log", "✓", 24, menuLabel = "Worked Before"),
            Js8TableView.Column("logName", "Name", 70, menuLabel = "Logged Name"),
            Js8TableView.Column("logComment", "Comment", 120, stretch = true, menuLabel = "Logged Comment")
        ))
        callActivity.menuOrder = listOf("callsign", "offset", "timestamp", "snr", "tdrift", "submode", "grid", "distance",
            "azimuth", "log", "logName", "logComment")
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
        // PC: the menu opens with no row under the finger too; only what needs no callsign
        callActivity.onEmptyLongClick = { anchor -> showCallMenu(anchor, null) }
        bandActivity.onEmptyLongClick = { anchor -> showBandMenu(anchor, null) }
        callActivity.onRowDoubleClick = { row ->
            callActivity.selectedRowId = row.id
            selectedCall = row.id
            renderSelection()
            addMessageText(row.id, send = false)  // PC addMessageText(call): a space before if needed, none after
        }
        bandActivity.onRowClick = { row ->
            callActivity.selectedRowId = null
            selectedCall = if (bandActivity.selectedRowId == null) null
                else row.id.removePrefix("o").toIntOrNull()?.let { activityVm.activity.callAtOffset(it) }
            renderSelection()
        }
        bandActivity.onRowLongClick = { row, anchor -> showBandMenu(anchor, row.id) }
        // Double-click: move the TX offset there and print its text in the Message Window
        bandActivity.onRowDoubleClick = { row ->
            val off = row.id.removePrefix("o").toIntOrNull()
            if (off != null) {
                if (offsetChangeAllowed()) { // PC changeFreq
                    offsetSlider?.progress = off
                    applyOffset(off.toFloat())
                }
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
            Js8Inbox.showMessageMenu(inbox!!, row, anchor) { ids ->
                viewLifecycleOwner.lifecycleScope.launch { ids.forEach { repository.deleteMessage(it.toLong()) } }
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

    /** PC splitWords: commas only (Configuration.cpp 2932-2943). */
    private fun words(key: String) = prefs().getString(key, "").orEmpty().uppercase()
        .split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    /** displayBandActivity + displayCallActivity + the Message Window. */
    /**
     * Typing Box while a typed message waits or is sent (PC TransmitTextEdit): locked, the text
     * already sent crossed out; emptied and unlocked when the message is done or halted.
     */
    private fun renderTyped(t: Js8ActivityViewModel.TypedTx?) {
        // PC typeahead: an edit that came too late for the part that started goes with the next one
        if (t != null && typeaheadFrames != null && typeaheadFrames != t.framesStarted && typeaheadOpen(t)) sendTypeahead(t)
        // A heartbeat or reply going out while the box was empty: show its text there
        if (t != null && t.auto && typingBox.text.toString() != t.text) {
            typingBox.filters = arrayOf(pcTextFilter)
            typingBox.setText(t.text)
        }
        val e = typingBox.text
        e.getSpans(0, e.length, android.text.style.StrikethroughSpan::class.java).forEach { e.removeSpan(it) }
        if (t == null) {
            typingBox.filters = arrayOf(pcTextFilter)
            typingBox.isCursorVisible = true
            typingBox.showSoftInputOnFocus = true
            if (activityVm.typedClearPending) {
                activityVm.typedClearPending = false
                e.clear()
            }
        } else {
            // Locked: typed keys and pastes change nothing. While a typed message is sent the text
            // after the crossed-out part stays open, until its last part (PC TransmitTextEdit)
            val open = typeaheadOpen(t)
            val sent = t.charsSent
            typingBox.filters = arrayOf(android.text.InputFilter { source, start, end, dest, dstart, dend ->
                if (open && dstart >= sent) source.subSequence(start, end) else dest.subSequence(dstart, dend)
            }, pcTextFilter)
            typingBox.isCursorVisible = open
            typingBox.showSoftInputOnFocus = open
            val n = t.charsSent.coerceAtMost(e.length)
            if (n > 0) e.setSpan(android.text.style.StrikethroughSpan(), 0, n, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        renderButtons()
    }

    /** PC TransmitTextEdit on_textContentsChanged: typed, pasted and inserted text, upper case and sendable only. */
    private val pcTextFilter = android.text.InputFilter { source, start, end, _, _, _ ->
        val s = source.subSequence(start, end).toString()
        val n = com.js8call.core.JS8Engine.normalizeText(s)
        if (n == s) null else n
    }

    private var typeaheadFrames: Int? = null
    private var uprightCallWidth: Int? = null

    private fun typeaheadOpen(t: Js8ActivityViewModel.TypedTx) = t.onAir && !t.auto && t.framesStarted > 0 && !t.onLastFrame

    /** PC prepareNextMessageFrame: what is typed past the sent text replaces the parts not started. */
    private fun sendTypeahead(t: Js8ActivityViewModel.TypedTx) {
        typeaheadFrames = t.framesStarted
        requireContext().startService(android.content.Intent(requireContext(), JS8EngineService::class.java)
            .setAction(JS8EngineService.ACTION_TX_TYPEAHEAD)
            .putExtra(JS8EngineService.EXTRA_TYPEAHEAD_TEXT, typingBox.text.toString().drop(t.charsSent))
            .putExtra(JS8EngineService.EXTRA_TYPEAHEAD_FRAMES, t.framesStarted))
    }

    /** Settings > View and the View menu: the panels of the upright and Pages windows (PC actionShow_*). */
    private fun applyPanels() {
        val root = view ?: return
        fun show(id: Int, on: Boolean) { root.findViewById<View?>(id)?.visibility = if (on) View.VISIBLE else View.GONE }
        val p = prefs()
        fun panel(i: Int) = p.getBoolean(Js8PcSplitters.PANELS[i].first, true)
        if (root.findViewById<View?>(R.id.js8_pc_window) != null) return // PC, FT8Call: Js8PcSplitters
        val band = panel(1)
        val call = panel(2)
        show(R.id.js8_freq_view, panel(0))
        show(R.id.js8_callclock_view, panel(0))
        show(R.id.js8_band_activity, band)
        show(R.id.js8_call_activity, call)
        show(R.id.js8_lists_row, band || call)
        val row = root.findViewById<android.widget.LinearLayout?>(R.id.js8_lists_row)
        if (row != null) {
            // A divider goes with the panel beside it
            (1 until row.childCount - 1).map { row.getChildAt(it) }.filter { it !is android.widget.LinearLayout && it !is Js8TableView }.forEach { line ->
                val i = row.indexOfChild(line)
                line.visibility = if (row.getChildAt(i - 1).visibility == View.VISIBLE && row.getChildAt(i + 1).visibility == View.VISIBLE) View.VISIBLE else View.GONE
            }
            // Upright: Call Activity has its own width; alone it takes the row
            val alone = row.findViewById<View>(R.id.js8_call_activity)
            (alone.layoutParams as? android.widget.LinearLayout.LayoutParams)?.takeIf { row.findViewById<View?>(R.id.js8_message_window) == null }?.let { lp ->
                if (uprightCallWidth == null) uprightCallWidth = lp.width
                lp.width = if (band) uprightCallWidth ?: lp.width else 0
                lp.weight = if (band) 0f else 1f
                alone.layoutParams = lp
            }
        }
        show(R.id.js8_waterfall_view, panel(3))
        show(R.id.js8_wf_controls_view, panel(4))
        show(R.id.js8_wf_controls_scroll, panel(4))
        show(R.id.js8_statusbar_view, panel(5))
        show(R.id.js8_status_scroll, panel(5))
    }

    /** Current speed (Settings > Mode, Call Activity > Jump to ... speed): offset dial width, status bar label, cycle. */
    private fun renderSpeed() {
        val submode = Js8Activity.txSubmode(prefs().getInt(Js8SettingsSchema.TX_SUBMODE_KEY, 0))
        waterfall.bandwidthHz = Js8Activity.bandwidthHz(submode)
        wfControls?.setFilterWidthMin(Js8Activity.bandwidthHz(submode).toInt() + 2 * Js8Activity.rxThresholdHz(submode))
        view?.findViewById<TextView?>(R.id.js8_status_mode)?.text = Js8Activity.modeLabel(submode)
        progress.period = Js8Activity.periodSeconds(submode)
        waterfall.setCyclePeriod(progress.period)
        requestTextStats()
        renderHb()
        renderModeButton()
    }

    /** PC updateModeButtonText: the speed and what is on, on the header's mode button. */
    private fun renderModeButton() {
        val button = view?.findViewById<TextView?>(R.id.js8_btn_mode) ?: return
        val submode = Js8Activity.txSubmode(prefs().getInt(Js8SettingsSchema.TX_SUBMODE_KEY, 0))
        var text = Js8Activity.modeName(submode)
        if (boolPref(Js8SettingsSchema.MULTI_KEY, true)) text += "+MULTI"
        if (boolPref(Js8SettingsSchema.AUTOREPLY_KEY, false)) text += if (boolPref(Js8SettingsSchema.AUTOREPLY_CONFIRM_KEY, true)) "+AUTO+CONF" else "+AUTO"
        if (hbShown()) text += if (wantHbReplies()) "+HB+ACK" else "+HB"
        if (button.text.toString() != text) button.text = text
    }

    /** PC menuModeJS8. Each item takes effect at once, and Settings shows the same values. */
    private fun showModeMenu(anchor: View) {
        val speedGroup = 1; val multiGroup = 2; val optionGroup = 3
        val idMulti = 101; val idAuto = 102; val idHb = 103; val idAck = 104
        val state = transmitVm.txState.value
        // on_menuModeJS8_aboutToShow: no speed change while a message is queued or being sent
        val canChangeSpeed = state != TransmitState.TRANSMITTING && state != TransmitState.QUEUED &&
            transmitVm.queue.value.isNullOrEmpty()
        val submode = Js8Activity.txSubmode(prefs().getInt(Js8SettingsSchema.TX_SUBMODE_KEY, 0))
        val auto = boolPref(Js8SettingsSchema.AUTOREPLY_KEY, false)
        val hb = boolPref(Js8SettingsSchema.HB_KEY, false)
        val menu = android.widget.PopupMenu(android.view.ContextThemeWrapper(requireContext(), R.style.ThemeOverlay_Js8ModeMenu), anchor)
        androidx.core.view.MenuCompat.setGroupDividerEnabled(menu.menu, true)
        Js8SettingsSchema.SPEEDS.forEachIndexed { i, (label, value) ->
            menu.menu.add(speedGroup, value.toInt(), i, label).apply {
                isChecked = value.toInt() == submode
                isEnabled = canChangeSpeed
            }
        }
        menu.menu.setGroupCheckable(speedGroup, true, true)
        fun option(group: Int, id: Int, label: String, checked: Boolean) =
            menu.menu.add(group, id, id, label).apply { isCheckable = true; isChecked = checked }
        option(multiGroup, idMulti, Js8SettingsSchema.MULTI_LABEL, boolPref(Js8SettingsSchema.MULTI_KEY, true))
        option(optionGroup, idAuto, Js8SettingsSchema.autoreplyLabel(boolPref(Js8SettingsSchema.AUTOREPLY_CONFIRM_KEY, true)), auto)
        // prepareHeartbeatMode: HB needs a speed that sends heartbeats, ACK also needs HB and AUTO
        option(optionGroup, idHb, Js8SettingsSchema.HB_LABEL, hb).isEnabled =
            submode in setOf(Js8Activity.SUBMODE_NORMAL, Js8Activity.SUBMODE_FAST, Js8Activity.SUBMODE_SLOW)
        option(optionGroup, idAck, Js8SettingsSchema.HB_ACK_LABEL, boolPref(Js8SettingsSchema.HB_ACK_KEY, false)).isEnabled = hbShown() && auto
        menu.setOnMenuItemClickListener { item ->
            val key = when (item.itemId) {
                idMulti -> Js8SettingsSchema.MULTI_KEY
                idAuto -> Js8SettingsSchema.AUTOREPLY_KEY
                idHb -> Js8SettingsSchema.HB_KEY
                idAck -> Js8SettingsSchema.HB_ACK_KEY
                else -> null
            }
            if (key == null) jumpToSpeed(item.itemId)
            else {
                prefs().edit().putBoolean(key, !item.isChecked).apply()
                refreshFromPrefs()
            }
            true
        }
        menu.show()
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
            txFrameEstimate = 0
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
                    com.js8call.core.JS8Engine.messageFrameTexts(text, myCall, myGrid, directed, submode,
                        myCall.isNotBlank() && !boolPref("js8cfg_avoid_forced_identify", false))
                }.getOrDefault(emptyList())
            }
            // A newer count may be on its way; only the latest text is shown
            if (typingBox.text.toString().trim() != text) return@launch
            val count = parts.size
            txFrameEstimate = count
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

    /** PC buildColumnLabelMap: "%1 Hz", "%1 dB" and "%1 ms" lose the unit with Minimal Column Labels. */
    private fun unit(value: String, unit: String) = if (bandActivity.minimalLabels()) value else "$value $unit"

    private fun renderActivity() {
        if (view == null) return
        activityDirty = false
        listsDrawn = true
        val a = activityVm.activity
        val cfg = Js8Activity.Config(prefs())
        val now = System.currentTimeMillis()
        val showHb = boolPref("js8cfg_show_hb_acks", true) // PC default on
        val cqColor = colorPref("js8cfg_ui_cq_bg", "#66FF66")           // colorCQ
        val myCallColor = colorPref("js8cfg_ui_directed_bg", "#FF6666") // colorMyCall
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
                    "offset" to unit(off.toString(), "Hz"),
                    "timestamp" to Js8Activity.since(last.utcMs, now),
                    "snr" to if (snr.isEmpty()) "" else unit(snr, "dB"),
                    "tdrift" to unit((last.tdrift * 1000).toInt().toString(), "ms"),
                    "submode" to Js8Activity.submodeLetter(last.submode),
                    "message" to text
                ),
                sortValues = mapOf("offset" to off, "timestamp" to -last.utcMs, "snr" to last.snr, "tdrift" to last.tdrift, "submode" to speedSort(last.submode)),
                bg = bg, center = setOf("timestamp", "submode")
            )
        }
        bandActivity.setRows(bandRows)

        // Call Activity: @ALLCALL, my groups, then heard callsigns
        val grid = Js8Api.myGrid(prefs()) // PC m_config.my_grid(): the API's grid while allowed
        val miles = boolPref("js8cfg_miles", false)
        // PC: no units in the distance and ° cells while the column labels show; the header says km or mi
        val distanceUnits = !callActivity.labelsShown()
        callActivity.setHeaderLabel("distance", if (miles) "mi" else "km")
        val blocked = cfg.blocklist
        val calls = a.callActivity.values.filter { d ->
            d.call == selectedCall || d.call in unreadFrom || agingCalls() == 0 || (now - d.utcMs) / 60_000 < agingCalls()
        }
        // PC displayCallActivity: the count is the calls inside Callsign aging (mw 3036-3055)
        val count = a.callActivity.values.count { d -> d.call.isNotBlank() && (agingCalls() == 0 || (now - d.utcMs) / 60_000 < agingCalls()) }
        callActivity.setHeaderLabel("callsign", if (count == 0) "Callsigns" else "Callsigns ($count)")
        val groupRows = buildList {
            if (!cfg.avoidAllcall) add(Js8TableView.Row("@ALLCALL", mapOf("callsign" to "@ALLCALL"), pin = 1))
            // ⚑ on a group with unread messages (createGroupCallsignTableRows)
            cfg.groups.sorted().forEach { add(Js8TableView.Row(it, mapOf("icon" to if (it in unreadTo) "⚑" else "", "callsign" to it),
                center = setOf("icon"), pin = 2)) }
        }
        val callRows = calls.sortedBy { it.call }.map { d ->
            val hasMessage = d.call in unreadFrom
            val hasCq = d.cqMs > 0 && (now - d.cqMs) / 60_000 < 5
            val hasAck = d.ackMs > 0
            val isBlocked = d.call in blocked || Js8Activity.baseCall(d.call) in blocked
            val icon = when {
                isBlocked -> "✕"; hasMessage -> "⚑"; hasAck -> "★"; hasCq -> "☎"; d.through.isNotEmpty() -> "⚟"; else -> ""
            }
            // Worked before / Name / Comment from the log; its grid when none was heard (displayCallActivity)
            val worked = com.js8call.example.data.Js8LogBook.workedBefore(requireContext(), d.call)
            val logged = if (worked) com.js8call.example.data.Js8LogBook.details(requireContext(), d.call) else null
            if (d.grid.isEmpty() && !logged?.grid.isNullOrEmpty()) d.grid = logged!!.grid.trim()
            val vec = Js8Activity.vector(grid, d.grid)
            val dist = vec?.first?.let { km -> Js8Activity.distanceText(km, miles, distanceUnits, grid, d.grid) }.orEmpty()
            val az = vec?.second?.let { "${it.roundToInt()}${if (distanceUnits) "°" else ""}" }.orEmpty()
            val snr = Js8Activity.formatSnr(d.snr)
            Js8TableView.Row(
                id = d.call,
                values = mapOf(
                    "icon" to icon, "callsign" to d.call,
                    "timestamp" to Js8Activity.since(d.utcMs, now),
                    "snr" to if (snr.isEmpty()) "" else unit(snr, "dB"),
                    "offset" to unit(d.offset.toString(), "Hz"),
                    "tdrift" to unit((d.tdrift * 1000).toInt().toString(), "ms"),
                    "submode" to Js8Activity.submodeLetter(d.submode),
                    "grid" to d.grid.take(4), "distance" to dist, "azimuth" to az,
                    "log" to if (worked) "✓" else "", "logName" to logged?.name.orEmpty(),
                    "logComment" to logged?.comment.orEmpty()
                ),
                sortValues = mapOf("timestamp" to -d.utcMs, "snr" to d.snr, "offset" to d.offset,
                    "ackTimestamp" to -d.ackMs, "submode" to speedSort(d.submode),
                    "distance" to (vec?.first ?: Double.MAX_VALUE), "azimuth" to (vec?.second ?: 999.0)),
                bold = hasMessage,
                bg = when {
                    d.call in primaryWords -> primary
                    d.call in secondaryWords -> secondary
                    hasCq -> cqColor
                    else -> null
                },
                colors = if (isBlocked) mapOf("icon" to Color.RED) else emptyMap(),
                center = setOf("icon", "timestamp", "submode", "log"),
                pin = if (hasMessage) 3 else 0 // messages pinned to the top (displayCallActivity)
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
            sb.append(clockFormat.format(Date(line.utcMs)).substring(12)).append(" - ")
            if (line.offset != Js8Activity.NOTICE_OFFSET) sb.append("(").append(line.offset.toString()).append(") - ")
            val textStart = sb.length
            sb.append(line.text.toString().trimEnd())
            sb.setSpan(android.text.style.ForegroundColorSpan(if (line.isTx) txFg else rxFg), start, sb.length, 0)
            if (line.isTx && txTextScale != 1f) sb.setSpan(android.text.style.RelativeSizeSpan(txTextScale), start, sb.length, 0)
            if (line.offset == Js8Activity.NOTICE_OFFSET) sb.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), start, sb.length, 0)
            // FT8 transmitting callsign in bold
            line.bold?.let { b ->
                if (textStart + b.last < sb.length) {
                    sb.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), textStart + b.first, textStart + b.last + 1, 0)
                }
            }
        }
        // Only when the text changed: setting it throws the box back to the top (the bounce Rob saw
        // every 5 seconds). The jump to the newest line happens before the box is drawn.
        val shown = "$rxFg/$txFg/$txTextScale/$sb"
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
    private var unreadTo: Set<String> = emptySet()

    private fun observeInbox() {
        repository.getAllMessages().observe(viewLifecycleOwner) { all ->
            inboxMessages = Js8Inbox.incoming(all)
            val my = prefs().getString("callsign", "").orEmpty().uppercase()
            inbox?.setRows(Js8Inbox.rows(inboxMessages, my))
            // Call Activity flag: senders of unread messages to my callsign (PC refreshInboxCounts, mw 6279-6293);
            // held messages are for others, group messages flag the group
            val toMe = inboxMessages.filter { !it.isRead && !Js8Inbox.isHeld(it) && !it.conversationId.startsWith("@") }
            unreadFrom = toMe.map { it.senderCallsign ?: it.conversationId }.toSet()
            unreadTo = inboxMessages.filter { !it.isRead }.map { it.conversationId }.filter { it.startsWith("@") }.toSet()
            // PC refreshInboxCounts: a sender not in Call Activity is added from its oldest unread message, with
            // its SNR, offset and time, and the ★ time (mw 6296-6314). The store keeps no time delta or speed.
            unreadFrom.filter { it.isNotEmpty() && !it.startsWith("@") && it !in activityVm.activity.callActivity }.forEach { from ->
                val m = toMe.filter { (it.senderCallsign ?: it.conversationId) == from }.minBy { it.id }
                activityVm.activity.callActivity[from] = Js8Activity.CallDetail(from, snr = m.snr ?: -99,
                    offset = m.frequency?.toInt() ?: -1, utcMs = m.timestamp, ackMs = m.timestamp)
            }
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
            if (transmitVm.txState.value == TransmitState.TRANSMITTING) return@setOnClickListener // PC on_monitorButton_clicked
            val running = monitorVm.status.value?.state.let { it == EngineState.RUNNING || it == EngineState.STARTING }
            if (running) stopMonitoring() else startMonitoring()
        }
        // TX: transmitter enabled. When off, queued messages wait (MainActivity checks the setting).
        btnTx.isActivated = boolPref("js8cfg_tx_enabled", true)
        btnTx.setOnClickListener {
            val on = !boolPref("js8cfg_tx_enabled", true)
            prefs().edit().putBoolean("js8cfg_tx_enabled", on).apply()
            it.isActivated = on
            if (on) processTxQueue() else halt() // PC on_monitorTxButton_toggled
            renderButtons()
        }
        // SPOT: spotting to reporting networks (Settings > Reporting)
        val spot = requireView().findViewById<View>(R.id.js8_btn_spot)
        spot.isActivated = boolPref("psk_reporter", true)
        spot.setOnClickListener {
            val on = !boolPref("psk_reporter", true)
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
        // CAT: PC on_readFreq_clicked, read the rig again
        requireView().findViewById<View>(R.id.js8_btn_cat).setOnClickListener {
            if (transmitVm.txState.value == TransmitState.TRANSMITTING) return@setOnClickListener
            requireContext().startService(Intent(requireContext(), JS8EngineService::class.java).setAction(JS8EngineService.ACTION_READ_RIG))
        }
        // PC labCallsign press opens Settings; click on the frequency buildFrequencyMenu
        callsign.setOnClickListener { openStation(page = Js8SettingsFragment.GENERAL_PAGE) }
        freqDisplay?.setOnClickListener { showFrequencyMenu(it) }
        // Settings button (Rob, 2026-10-31)
        requireView().findViewById<View>(R.id.js8_btn_settings).setOnClickListener { openSettings() }
        // Mode button: UI_Constructor modeButton press pops up menuModeJS8
        requireView().findViewById<View>(R.id.js8_btn_mode).setOnClickListener { showModeMenu(it) }
        // PC mode menu bar: the same windows as upright, no separate Menu page (Rob, 2026-10-20:
        // every menu has the same items in every layout). File = the PC's File > Settings (Station);
        // Configurations, Mode and Control items are in the Settings window; Help > About is in Station.
        fun menubar(id: Int, action: (View) -> Unit) = requireView().findViewById<View?>(id)?.setOnClickListener { action(it) }
        menubar(R.id.js8_menubar_file) { anchor ->
            val menu = popupMenu(anchor)
            menu.menu.add(0, 1, 1, "Station…")
            menu.menu.add(0, 2, 2, "Exit")
            menu.setOnMenuItemClickListener { item ->
                if (item.itemId == 1) openStation() else {
                    requireContext().stopService(Intent(requireContext(), JS8EngineService::class.java))
                    requireActivity().finishAndRemoveTask()
                }
                true
            }
            menu.show()
        }
        menubar(R.id.js8_menubar_mode) { showModeMenu(it) }
        menubar(R.id.js8_menubar_control) { showControlMenu(it) }
        menubar(R.id.js8_menubar_configurations) { anchor -> Js8Configurations.showMenu(this, popupMenu(anchor)) }
        // View: PC mainwindow.ui menuWindow, its order and lines; Settings… first (Rob, 2026-10-20).
        // No Show Tooltips: a phone has no hover
        menubar(R.id.js8_menubar_view) { anchor ->
            val menu = popupMenu(anchor)
            val m = menu.menu
            androidx.core.view.MenuCompat.setGroupDividerEnabled(m, true)
            // PC View > Show ...: the panels (PC mode and FT8Call: on the line-divided window)
            fun panel(group: Int, order: Int, i: Int) = Js8PcSplitters.PANELS[i].let { (key, name) ->
                m.add(group, 10 + i, order, name).apply { isCheckable = true; isChecked = prefs().getBoolean(key, true) }
            }
            m.add(0, 2, 0, "Settings…")
            panel(1, 1, 0)
            panel(2, 2, 1)
            // Same setting as Settings > View
            m.add(2, 16, 3, "Show Band Heartbeats and ACKs").apply { isCheckable = true; isChecked = boolPref("js8cfg_show_hb_acks", true) }
            m.add(2, 5, 4, "Show Band Activity Columns...")
            m.add(2, 4, 5, "Sort Band Activity By...")
            panel(3, 6, 2)
            m.add(3, 7, 7, "Show Call Activity Columns...")
            m.add(3, 6, 8, "Sort Call Activity By...")
            panel(4, 9, 3)
            panel(4, 10, 4)
            m.add(4, 8, 11, "Show Time Drift Controls...")
            m.add(5, 9, 12, "Show Fullscreen").apply { isCheckable = true; isChecked = boolPref(KEY_FULLSCREEN, false) }
            panel(5, 13, 5)
            m.add(6, 1, 14, "Show Message Inbox...")
            m.add(7, 3, 15, "Reset Frame Sizes") // PC View > Reset Window Sizes: the dividers
            menu.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    in 10..15 -> {
                        val key = Js8PcSplitters.PANELS[item.itemId - 10].first
                        prefs().edit().putBoolean(key, !prefs().getBoolean(key, true)).apply()
                        splitters?.refresh()
                        applyPanels()
                    }
                    16 -> {
                        prefs().edit().putBoolean("js8cfg_show_hb_acks", !item.isChecked).apply()
                        requestRender()
                    }
                    1 -> openInbox()
                    2 -> openSettings()
                    4 -> bandActivity.showSortMenu()
                    5 -> bandActivity.showColumnChooser()
                    6 -> callActivity.showSortMenu()
                    7 -> callActivity.showColumnChooser()
                    8 -> wfControls?.showTiming()
                    9 -> {
                        prefs().edit().putBoolean(KEY_FULLSCREEN, !item.isChecked).apply()
                        applyFullscreen()
                    }
                    else -> splitters?.reset()
                }
                true
            }
            menu.show()
        }
        menubar(R.id.js8_menubar_log) { showLogMenu(it) }
        menubar(R.id.js8_menubar_help) { anchor ->
            val menu = popupMenu(anchor)
            menu.menu.add(0, 1, 1, "Copyright notice")
            menu.menu.add(0, 4, 2, "Check for Updates")
            menu.menu.add(0, 2, 3, "About")
            menu.menu.add(0, 3, 4, "JS8Call User Guide")
            menu.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> AlertDialog.Builder(requireContext()).setMessage(COPYRIGHT_NOTICE)
                        .setPositiveButton(android.R.string.ok, null).show()
                    2 -> openStation(page = Js8SettingsFragment.ABOUT_PAGE)
                    4 -> Js8Updates.check(requireActivity(), true)
                    else -> startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(USER_GUIDE_URL)))
                }
                true
            }
            menu.show()
        }
    }

    /** PC menu_Log. Erase ... are the same as Settings > Log. */
    private fun showLogMenu(anchor: View) {
        val menu = popupMenu(anchor)
        menu.menu.add(0, 1, 1, "Add Log Entry...")
        menu.menu.add(0, 2, 2, "Open Log Directory...")
        menu.menu.add(0, 3, 3, "Erase ALL.TXT")
        menu.menu.add(0, 4, 4, "Erase js8call_log.adi")
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> openLog(selectedCall)
                2 -> openLogDirectory()
                3 -> confirmErase("Are you sure you want to erase file ALL.TXT?") {
                    requireContext().startService(Intent(requireContext(), JS8EngineService::class.java).setAction(JS8EngineService.ACTION_ERASE_LOG))
                }
                else -> confirmErase("Are you sure you want to erase file js8call_log.adi?") {
                    com.js8call.example.data.Js8LogBook.adifFile(requireContext()).delete()
                    com.js8call.example.data.Js8LogBook.forget()
                }
            }
            true
        }
        menu.show()
    }

    private fun confirmErase(message: String, erase: () -> Unit) {
        AlertDialog.Builder(requireContext()).setTitle("Confirm Erase").setMessage(message)
            .setPositiveButton("Yes") { _, _ -> erase() }.setNegativeButton("No", null).show()
    }

    /** PC on_actionOpen_log_directory_triggered: the Download/JS8CallLogs folder in the phone's Files app. */
    private fun openLogDirectory() {
        val uri = android.provider.DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Download/JS8CallLogs")
        try {
            startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, android.provider.DocumentsContract.Document.MIME_TYPE_DIR)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (e: android.content.ActivityNotFoundException) {
            showMessage("The log files are in Download/JS8CallLogs.")
        }
    }

    /** PC Control menu (mainwindow.ui menuControl). */
    private fun showControlMenu(anchor: View) {
        val v = requireView()
        val menu = popupMenu(anchor)
        val m = menu.menu
        androidx.core.view.MenuCompat.setGroupDividerEnabled(m, true)
        fun toggle(id: Int, label: String, button: View) =
            m.add(1, id, id, label).apply { isCheckable = true; isChecked = button.isActivated; isEnabled = button.isEnabled }
        toggle(1, "Enable Receiver (RX)", btnRx)
        toggle(2, "Enable Transmitter (TX)", btnTx)
        toggle(3, "Enable Reporting (SPOT)", v.findViewById(R.id.js8_btn_spot))
        toggle(4, "Enable Tuning Tone (TUNE)", v.findViewById(R.id.js8_btn_tune))
        // PC mainwindow.ui menuControl; Set Frequency..., Send Heartbeat... and Send CQ Message... open
        // the frequency, heartbeat and CQ menus (on_menuControl_aboutToShow)
        val freqClick = fillFrequencyMenu(m.addSubMenu(2, 7, 5, "Set Frequency..."), 1000)
        m.add(2, 8, 6, "Set Offset...")
        // PC Set Focus...: the area takes the focus (UC 813-827); here it also comes into view
        val focus = m.addSubMenu(2, 14, 6, "Set Focus...")
        val focusTargets = listOf("Message Receive Area" to messageWindow, "Message Reply Area" to typingBox,
            "Band Activity Table" to bandActivity, "Call Activity Table" to callActivity)
        focusTargets.forEachIndexed { i, (name, _) -> focus.add(0, 15 + i, i, name) }
        val hbClick = fillHbMenu(m.addSubMenu(3, 5, 7, "Send Heartbeat..."), 2000)
        val cqClick = fillCqMenu(m.addSubMenu(3, 6, 8, "Send CQ Message..."), 3000)
        val clear = m.addSubMenu(4, 9, 9, "Clear Activity...")
        androidx.core.view.MenuCompat.setGroupDividerEnabled(clear, true) // PC: a line under Clear All Activity
        clear.add(0, 10, 0, "Clear All Activity")
        clear.add(1, 11, 1, "Clear Band Activity")
        clear.add(1, 12, 2, "Clear RX Activity")
        clear.add(1, 13, 3, "Clear Call Activity")
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> btnRx.performClick()
                2 -> btnTx.performClick()
                3 -> v.findViewById<View>(R.id.js8_btn_spot).performClick()
                4 -> v.findViewById<View>(R.id.js8_btn_tune).performClick()
                8 -> askOffset()
                in 15..18 -> setFocus(focusTargets[item.itemId - 15].second)
                10 -> clearAllActivity() // PC on_actionClear_All_Activity_triggered: at once, no question
                11 -> { activityVm.activity.clearBand(); clearSelection(); activityVm.changed() }
                12 -> clearRxActivity()
                13 -> { activityVm.activity.clearCalls(); clearSelection(); activityVm.changed() }
                else -> freqClick(item.itemId) || hbClick(item.itemId) || cqClick(item.itemId)
            }
            true
        }
        menu.show()
    }

    private fun showFrequencyMenu(anchor: View) = showMenu(anchor) { fillFrequencyMenu(it, 0) }

    /**
     * PC buildFrequencyMenu: Set a Custom Frequency..., then the Frequencies table, lowest first.
     * Item ids start at [base]; the result runs the item with that id and says whether it was one.
     */
    private fun fillFrequencyMenu(m: android.view.Menu, base: Int): (Int) -> Boolean {
        val items = Js8SettingsSchema.frequencyItems(requireContext(), prefs().getString(Js8SettingsSchema.FREQUENCIES_KEY, null)).sortedBy { it.hz }
        androidx.core.view.MenuCompat.setGroupDividerEnabled(m, true) // PC: a line after the first item
        m.add(0, base, 0, "Set a Custom Frequency...")
        items.forEachIndexed { i, f ->
            val band = com.js8call.example.util.BandUtils.bandLabelForFrequencyHz(f.hz)
            val description = if (f.description.isEmpty()) "" else " - ${f.description}"
            // PC "%1:%2%2%3 MHz%4": the spaces twice, Radio::pretty_frequency_MHz_string "14.078 000"
            val pad = " ".repeat((5 - band.length).coerceAtLeast(0))
            val mhz = String.format(Locale.US, "%.6f", f.hz / 1e6).let { it.dropLast(3) + " " + it.takeLast(3) }
            m.add(1, base + i + 1, i + 1, "$band:$pad$pad$mhz MHz$description")
        }
        return { id ->
            when (id) {
                base -> { askFrequency(); true }
                in base + 1..base + items.size -> { setDial(items[id - base - 1].hz); true }
                else -> false
            }
        }
    }

    /** PC Set a Custom Frequency / Set Frequency...: "Frequency in MHz:". */
    private fun askFrequency() {
        val box = EditText(requireContext()).apply {
            isSingleLine = true
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(String.format(Locale.US, "%.6f", dialHz / 1e6))
            selectAll()
        }
        AlertDialog.Builder(requireContext()).setTitle("Set a Custom Frequency").setMessage("Frequency in MHz:").setView(box)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                box.text.toString().toDoubleOrNull()?.takeIf { it > 0 }?.let { setDial((it * 1e6).roundToLong()) }
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    /** PC Set Offset...: "Offset in Hz:". */
    private fun askOffset() {
        val box = EditText(requireContext()).apply {
            isSingleLine = true
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(monitorVm.getTxOffset().toInt().toString())
            selectAll()
        }
        AlertDialog.Builder(requireContext()).setTitle("Set Frequency Offset").setMessage("Offset in Hz:").setView(box)
            .setPositiveButton(android.R.string.ok) { _, _ -> box.text.toString().toIntOrNull()?.let { jumpToOffset(it.coerceIn(0, 5000)) } }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun setDial(hz: Long) {
        dialHz = hz
        prefs().edit().putString(Js8SettingsSchema.FREQUENCY_KEY, dialHz.toString()).apply()
        renderFrequency()
    }

    /** PC config_label: the configuration's name in the Status Bar unless it is Default. */
    private fun renderConfigLabel() {
        val mode = view?.findViewById<TextView?>(R.id.js8_status_mode) ?: return
        val row = mode.parent as? ViewGroup ?: return
        val name = Js8Configurations.currentName(requireContext())
        var label = row.findViewWithTag<TextView?>("js8_config_label")
        if (name == Js8Configurations.DEFAULT) {
            label?.let { row.removeView(it) }
            return
        }
        if (label == null) {
            val lp = mode.layoutParams as ViewGroup.MarginLayoutParams
            label = TextView(mode.context).apply {
                tag = "js8_config_label"
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, mode.textSize)
                typeface = mode.typeface
                setTextColor(mode.currentTextColor)
                gravity = mode.gravity
                minimumWidth = (80 * resources.displayMetrics.density).toInt()
                background = mode.background?.constantState?.newDrawable()
                setPadding(mode.paddingLeft, mode.paddingTop, mode.paddingRight, mode.paddingBottom)
                isSingleLine = true
            }
            row.addView(label, row.indexOfChild(mode), android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, lp.height).apply { marginStart = lp.marginStart })
        }
        label.text = name
    }

    /** PC View > Show Fullscreen: the phone's status and navigation bars hide. */
    private fun applyFullscreen() {
        val window = requireActivity().window
        val bars = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        if (boolPref(KEY_FULLSCREEN, false)) {
            bars.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            bars.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        } else bars.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
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
        ruler.dialHz = dialHz
        waterfall.setBandText(com.js8call.example.util.BandUtils.bandLabelForFrequencyHz(dialHz))
        // Upright: the band and the mode right of the callsign, no band outside the bands (Rob, 2026-11-05)
        val upright = resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
        view?.findViewById<TextView?>(R.id.js8_callsign_band)?.let { band ->
            val name = com.js8call.example.util.BandUtils.bandOrNull(dialHz)
            if (!upright || name == null) {
                band.visibility = View.GONE
            } else {
                if (band.text.toString() != name) band.text = name
                // about three letters between them
                (band.layoutParams as ViewGroup.MarginLayoutParams).marginStart = (band.textSize * 1.8f).toInt()
                band.visibility = View.VISIBLE
            }
        }
        view?.findViewById<TextView?>(R.id.js8_callsign_mode)?.let { mode ->
            (mode.layoutParams as ViewGroup.MarginLayoutParams).marginStart = (mode.textSize * 1.8f).toInt()
            mode.visibility = if (upright) View.VISIBLE else View.GONE
        }
        // FT8Call layout: the band above the frequency
        view?.findViewById<TextView?>(R.id.js8_band_label)?.text =
            com.js8call.example.util.BandUtils.bandLabelForFrequencyHz(dialHz)
        val offset = monitorVm.getTxOffset().toLong()
        statusFreq.text = "Freq: ${formatMhz(dialHz + offset)}"
        // PC clearActivity on a band change: the Typing Box empties and unlocks, the last sent message is
        // forgotten (mw 1279-1285, 2970-2990, 3001-3014)
        if (activityVm.bandChanged(com.js8call.example.util.BandUtils.bandLabelForFrequencyHz(dialHz))) {
            activityVm.clearTyped()
            typingBox.text.clear()
            activityVm.lastMessage = ""
        }
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
    private fun addMessageText(text: String, send: Boolean, clear: Boolean = false) {
        // PC: nothing is added while a message waits or is being sent
        if (activityVm.typed.value != null) return
        if (clear) typingBox.setText("")
        val box = typingBox.text
        val start = typingBox.selectionStart.coerceAtLeast(0)
        val end = typingBox.selectionEnd.coerceAtLeast(0)
        box.delete(minOf(start, end), maxOf(start, end))
        val at = typingBox.selectionStart.coerceAtLeast(0)
        val space = if (at > 0 && !box[at - 1].isWhitespace()) " " else ""
        box.insert(at, space + text)
        val whole = box.toString()
        val placeholder = whole.indexOf('[')
        if (clear && placeholder >= 0 && whole.indexOf(']', placeholder) > placeholder) {
            typingBox.setSelection(placeholder, whole.indexOf(']', placeholder) + 1)
        }
        if (send && boolPref("js8cfg_transmit_directed", true) && !whole.contains('[')) sendTyped()
    }

    /** PC buildQueryMenu: what each Directed item sends at once. */
    private fun directedSends(call: String, text: String): Boolean {
        val reply = "$call ${macro(prefs().getString("js8cfg_reply_message", "HW CPY?").orEmpty())}"
        return !text.endsWith(" ") && !text.contains('[') && !text.contains(" QUERY") && text != reply
    }

    /** PC buildQueryMenu: SNR needs them heard, INFO and GRID a value; @ALLCALL and @HB grey only these eight (115-245). */
    private fun directedEnabled(call: String, text: String): Boolean {
        val allCall = call == "@ALLCALL" || call == "@HB"
        return when {
            text.startsWith("$call SNR ") -> call in activityVm.activity.callActivity
            text == "$call INFO " -> false
            text.startsWith("$call GRID ") && !text.endsWith("?") -> prefs().getString("grid", "").orEmpty().isNotBlank()
            text in listOf("$call SNR?", "$call INFO?", "$call GRID?", "$call STATUS?", "$call HEARING?", "$call>[MESSAGE]",
                "$call MSG [MESSAGE]", "$call MSG TO:[CALLSIGN] [MESSAGE]") -> !allCall
            else -> true
        }
    }

    /** PC buildQueryMenu separators: after the first item, after GRID, after QUERY MSG (32, 111, 300). */
    private fun directedGroup(i: Int) = when {
        i == 0 -> 0
        i <= 4 -> 1
        i <= 15 -> 2
        else -> 3
    }

    private fun addDirected(call: String, text: String) = addMessageText(text, send = directedSends(call, text), clear = true)

    private fun sendTyped() {
        val text = typingBox.text.toString().trim()
        if (text.isEmpty()) return
        if (listenOnly()) return
        if (!ensureCallsignSet(alert = true)) return
        // PC ensureKeyNotStuck
        if (text.length > 5 && text.replace(text.substring(0, 1), "").isBlank()) {
            AlertDialog.Builder(requireContext()).setTitle("Message Not Sent")
                .setMessage("It appears you may have a stuck key (more than 5 repeated characters). Please check your keyboard and try again.")
                .setPositiveButton(android.R.string.ok, null).show()
            return
        }
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
        } else openStation(page = Js8SettingsFragment.GENERAL_PAGE) // straight to Station Details (Rob, 2026-10-22)
        return false
    }

    /** Station > SWL (Listening only): every transmit button greyed out, nothing sent. */
    private fun listenOnly() = boolPref(Js8SettingsSchema.SWL_KEY, false)

    /**
     * PC guiUpdate labCallsign (mw 2766-2771): the Station callsign, "CALL - GRID" while **Allow setting station
     * information (Grid, Info, Status, etc) from the APIs** is ticked and a grid is set (the API's grid if one
     * came in). No callsign: empty.
     */
    private fun renderCallsign() {
        val p = prefs()
        val call = p.getString("callsign", "").orEmpty()
        val grid = Js8Api.myGrid(p)
        val text = if (Js8Api.bool(p, Js8Api.KEY_DYNAMIC_INFO, false) && grid.isNotEmpty()) "$call - $grid" else call
        if (callsign.text.toString() != text) callsign.text = text
    }

    /** PC Control > Set Focus...: the area comes into view and takes the focus; the Typing Box opens the keyboard. */
    private fun setFocus(target: View) {
        target.requestRectangleOnScreen(android.graphics.Rect(0, 0, target.width, target.height))
        target.requestFocus()
        if (target === typingBox && typingBox.showSoftInputOnFocus)
            requireContext().getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                ?.showSoftInput(typingBox, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }

    /** Settings window (header Settings button). */
    private fun openSettings() = navigateFromMain(R.id.navigation_js8_settings)

    /** Inbox in this layout (upright, Swipe), or the Message Inbox screen (PC mode). */
    private fun openInbox(call: String? = null) {
        val table = inbox
        // PC messagePanel_->setCall: from Call Activity the list shows that call
        if (table != null && call == null) table.requestRectangleOnScreen(android.graphics.Rect(0, 0, table.width, table.height))
        else navigateFromMain(R.id.navigation_js8_inbox, android.os.Bundle().apply { putString(Js8InboxFragment.ARG_CALL, call) })
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
        renderButtons()
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
        typingBox.setOnLongClickListener { showTypingMenu(it); true }
        messageWindow.setOnLongClickListener { pressAnchor(MESSAGE_MENU_DP) { a -> showMessageWindowMenu(a) }; true }
        // Up and down drags scroll the box, not the page it sits in; sideways drags stay with the Swipe tabs.
        // Upright: every drag that starts in the box stays with the box, from the first touch (Rob, 2026-10-31)
        var downX = 0f
        var downY = 0f
        messageWindow.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    downX = ev.x
                    downY = ev.y
                    pressRawX = ev.rawX
                    pressRawY = ev.rawY
                    if (isUprightLayout()) v.parent?.requestDisallowInterceptTouchEvent(true)
                }
                android.view.MotionEvent.ACTION_MOVE ->
                    if ((v.canScrollVertically(1) || v.canScrollVertically(-1)) &&
                        kotlin.math.abs(ev.y - downY) > kotlin.math.abs(ev.x - downX)) {
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                    }
            }
            false
        }
        view.findViewById<View>(R.id.js8_btn_saved).setOnClickListener { showSavedMenu(it) }
        view.findViewById<View>(R.id.js8_btn_directed).setOnClickListener { showDirectedMenu(it) }
        view.findViewById<View>(R.id.js8_btn_deselect).setOnClickListener { clearSelection() }
        view.findViewById<View>(R.id.js8_btn_send).setOnClickListener { sendTyped() }
        // Halt: stop the transmission now and drop what is queued (on_stopTxButton_clicked)
        view.findViewById<View>(R.id.js8_btn_halt).setOnClickListener { halt() }
        view.findViewById<View?>(R.id.js8_inbox_reply)?.setOnClickListener { inboxReply() }
        // Where the Typing Box was pressed: the word its menu suggests for (PC cursorForPosition)
        typingBox.setOnTouchListener { _, e ->
            if (e.action == android.view.MotionEvent.ACTION_DOWN) typingPressOffset = typingBox.getOffsetForPosition(e.x, e.y)
            false
        }
        typingBox.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                activityVm.typingBoxEmpty = s.isNullOrBlank()
                JS8EngineService.typingBoxEmpty = s.isNullOrBlank() || activityVm.typed.value?.auto == true
                if (activityVm.typed.value?.auto == true) return // shown by the app, not typed
                activityVm.typed.value?.takeIf { typeaheadOpen(it) }?.let { sendTypeahead(it) }
                Js8Macros.touch()
                requestTextStats()
                s?.let { Js8TypingSpans.apply(it, prefs(), selectedCall) }
                renderButtons()
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
    }

    /** PC on_stopTxButton_clicked. */
    private fun halt() {
        transmitVm.clearQueue()
        requireContext().startService(Intent(requireContext(), JS8EngineService::class.java).apply {
            action = JS8EngineService.ACTION_HALT_TX
        })
        activityVm.clearTyped() // resetMessage: the Typing Box empties
        requireView().findViewById<View>(R.id.js8_btn_tune).isActivated = false
        if (com.js8call.example.service.HeartbeatTimer.on) setHbTimer(false)
        if (com.js8call.example.service.CqTimer.on) setCqTimer(false)
    }

    private fun clearSelection() {
        selectedCall?.let { callSelectedTime.remove(it) } // PC clearCallsignSelected
        callActivity.selectedRowId = null
        bandActivity.selectedRowId = null
        selectedCall = null
        renderSelection()
    }

    /** Saved button: the Saved Messages list (Settings > Saved Messages), macros expanded. */
    /** Popup menus with the lists' text size and shorter rows (ThemeOverlay.Js8PopupMenu). */
    private fun popupMenu(anchor: View) =
        android.widget.PopupMenu(android.view.ContextThemeWrapper(requireContext(), R.style.ThemeOverlay_Js8PopupMenu), anchor)

    /** Where the finger last went down on the Message Window, screen pixels. */
    private var pressRawX = 0f
    private var pressRawY = 0f

    /**
     * A menu opened from where the finger is, as the Band Activity row menus are: a 1 px view at the press
     * point, raised only as far as the menu ([menuDp] tall) needs to fit below it, removed when the menu
     * closes. Anchored to the whole Message Window, sideways the menu had two rows of room (Rob, 2026-11-03).
     */
    private fun pressAnchor(menuDp: Int, show: (View) -> Unit) {
        val content = requireActivity().findViewById<android.widget.FrameLayout>(android.R.id.content)
        val at = IntArray(2).also { content.getLocationOnScreen(it) }
        val need = menuDp * resources.displayMetrics.density
        val y = minOf(pressRawY - at[1], content.height - need).coerceAtLeast(0f)
        val a = View(requireContext()).apply { tag = PRESS_ANCHOR }
        content.addView(a, android.widget.FrameLayout.LayoutParams(1, 1).apply {
            leftMargin = (pressRawX - at[0]).toInt().coerceAtLeast(0)
            topMargin = y.toInt()
        })
        a.post { if (isAdded) show(a) else content.removeView(a) }
    }

    private fun savedMessages() = prefs().getString(Js8SettingsSchema.SAVED_MESSAGES_KEY, Js8SettingsSchema.SAVED_MESSAGES_DEFAULT)
        .orEmpty().split('\n').filter { it.isNotBlank() }

    private fun showSavedMenu(anchor: View) {
        val saved = savedMessages()
        val menu = popupMenu(anchor)
        saved.forEachIndexed { i, m -> menu.menu.add(0, i, i, macro(m)) } // PC replaceMacros(macro, values, false)
        menu.menu.add(0, 9999, 9999, "Edit Saved Messages")
        menu.menu.add(0, 9998, 9999, "Save Current Message").isEnabled = typingBox.text.isNotBlank()
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
                item.itemId == 9999 -> openStation(page = Js8SettingsSchema.SAVED_PAGE) // Saved Messages are in Station
                item.itemId == 9998 -> saveCurrentMessage()
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
        androidx.core.view.MenuCompat.setGroupDividerEnabled(menu.menu, true)
        items.forEachIndexed { i, (label, text) -> menu.menu.add(directedGroup(i), i, i, label).isEnabled = directedEnabled(call, text) }
        menu.setOnMenuItemClickListener { item ->
            addDirected(call, items[item.itemId].second)
            true
        }
        menu.show()
    }

    /** buildQueryMenu items for [call]: menu label to Typing Box text. */
    private fun directedItems(call: String): List<Pair<String, String>> {
        val grid = prefs().getString("grid", "").orEmpty()
        val snr = activityVm.activity.callActivity[call]?.snr?.let { Js8Activity.formatSnr(it) }.orEmpty()
        val items = listOf(
            "Send a directed message to selected callsign" to "$call ",
            "Reply - Send reply message to selected callsign" to
                "$call ${macro(prefs().getString("js8cfg_reply_message", "HW CPY?").orEmpty())}",
            "SNR - Send a signal report to the selected callsign" to "$call SNR $snr",
            "INFO - Send my station information" to "$call INFO ${macro(prefs().getString("my_info", "").orEmpty())}",
            "GRID $grid - Send my current station Maidenhead grid locator" to "$call GRID $grid",
            "SNR? - What is my signal report?" to "$call SNR?",
            "INFO? - What is your station information?" to "$call INFO?",
            "GRID? - What is your current grid locator?" to "$call GRID?",
            "STATUS? - What is your station status message?" to "$call STATUS?",
            "HEARING? - What are the stations are you hearing? (Top 4 ranked by most recently heard)" to "$call HEARING?",
            ">[MESSAGE] - Please relay this message to its destination" to "$call>[MESSAGE]",
            "MSG [MESSAGE] - Please store this message in your inbox" to "$call MSG [MESSAGE]",
            "MSG TO:[CALLSIGN] [MESSAGE] - Please store this message at your station for later retreival by [CALLSIGN]" to
                "$call MSG TO:[CALLSIGN] [MESSAGE]",
            "QUERY CALL [CALLSIGN]? - Please acknowledge you can communicate directly with [CALLSIGN]" to
                "$call QUERY CALL [CALLSIGN]?",
            "QUERY MSGS - Do you have any messages for me?" to "$call QUERY MSGS",
            "QUERY MSG [ID] - Please deliver the complete message identified by ID" to "$call QUERY MSG [ID]",
            "AGN? - Please repeat your last transmission" to "$call AGN?",
            "QSL? - Did you receive my last transmission?" to "$call QSL?",
            "QSL - I confirm I received your last transmission" to "$call QSL",
            "YES - I confirm your last inquiry" to "$call YES",
            "NO - I do not confirm your last inquiry" to "$call NO",
            "HW CPY? - How do you copy?" to "$call HW CPY?",
            "RR - Roger. Received. I copy." to "$call RR",
            "FB - Fine Business" to "$call FB",
            "73 - I send my best regards" to "$call 73",
            "SK - End of contact" to "$call SK",
            "DIT DIT - End of contact / Two bits" to "$call DIT DIT"
        )
        return items
    }

    /**
     * Press and hold a callsign in Call Activity: the PC's right-click menu
     * (UI_Constructor.cpp, tableWidgetCalls customContextMenuRequested). Rob, 2026-10-19.
     */
    private fun showCallMenu(anchor: View, row: String?) {
        // The row is selected first; the menu acts on it (PC callsignSelected). Pressed below the rows
        // (PC rowAt -1): the selection stays as it is, a Band Activity row's call or none, and the
        // items that need a call are greyed without one (UC 1290-1296)
        if (row != null) {
            bandActivity.selectedRowId = null
            callActivity.selectedRowId = row
            selectedCall = row
            renderSelection()
        }
        val call = selectedCall

        val a = activityVm.activity
        val isAllCall = call == "@ALLCALL"
        val isGroup = call?.startsWith("@") == true
        val detail = call?.let { a.callActivity[it] }
        val mySubmode = Js8Activity.txSubmode(prefs().getInt(Js8SettingsSchema.TX_SUBMODE_KEY, 0))
        val saved = savedMessages()
        val directed = call?.let { directedItems(it) }.orEmpty()
        // Messages from them, or held for them (Inbox::fetchForCall)
        val hasInbox = call != null && inboxMessages.any {
            (if (Js8Inbox.isHeld(it)) it.conversationId else it.senderCallsign ?: it.conversationId).equals(call, ignoreCase = true)
        }

        val popup = popupMenu(anchor)
        val m = popup.menu
        androidx.core.view.MenuCompat.setGroupDividerEnabled(m, true)
        // PC generateCallDetail is the row's tooltip; a phone has no hover, so it heads the menu
        if (call != null) {
            a.hearing[call]?.takeIf { it.isNotEmpty() }?.let { m.add(0, 0, 0, "HEARING: ${it.joinToString(", ")}").isEnabled = false }
            a.heardBy[call]?.takeIf { it.isNotEmpty() }?.let { m.add(0, 0, 0, "HEARD BY: ${it.joinToString(", ")}").isEnabled = false }
        }
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
        m.add(2, ID_LOG, 10, "Log...").isEnabled = call != null && !isAllCall
        m.add(2, ID_INBOX, 11, "Show Message Inbox...").isEnabled = call != null && !isAllCall && hasInbox
        m.add(2, ID_STORE, 12, "Store Message...").isEnabled = call != null && !isAllCall
        val savedMenu = m.addSubMenu(3, ID_SAVED, 20, "Saved Messages...")
        saved.forEachIndexed { i, text -> savedMenu.add(0, ID_SAVED_FIRST + i, i, macro(text)) }
        savedMenu.add(0, ID_SAVED_EDIT, saved.size, "Edit Saved Messages")
        savedMenu.add(0, ID_SAVED_CURRENT, saved.size + 1, "Save Current Message").isEnabled = typingBox.text.isNotBlank()
        val directedMenu = m.addSubMenu(3, ID_DIRECTED, 21, "Directed to ${call.orEmpty()}...")
        directedMenu.item.isEnabled = call != null
        androidx.core.view.MenuCompat.setGroupDividerEnabled(directedMenu, true)
        if (call != null) directed.forEachIndexed { i, (label, text) ->
            directedMenu.add(directedGroup(i), ID_DIRECTED_FIRST + i, i, label).isEnabled = directedEnabled(call, text)
        }
        m.add(3, ID_RELAY, 22, "Relay via ${call.orEmpty()}...").isEnabled = call != null && !isAllCall
        m.add(3, ID_DESELECT, 23, "Deselect ${call.orEmpty()}").isEnabled = call != null
        m.add(4, ID_ADD, 30, "Add New Station or Group...")
        m.add(4, ID_REMOVE, 31, if (isGroup) "Remove This Group" else "Remove This Station").isEnabled = call != null
        m.add(4, ID_BLOCK, 32, "Block This Station").isEnabled = call != null && !isGroup
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
                id == ID_INBOX -> openInbox(call)
                id == ID_LOG -> openLog(call)
                id == ID_STORE -> call?.let { storeMessage(it) }
                id == ID_SAVED_EDIT -> openStation(page = Js8SettingsSchema.SAVED_PAGE)
                id == ID_SAVED_CURRENT -> saveCurrentMessage()
                id in ID_SAVED_FIRST until ID_SAVED_FIRST + saved.size ->
                    addMessageText(macro(saved[id - ID_SAVED_FIRST]), send = true)
                id in ID_DIRECTED_FIRST until ID_DIRECTED_FIRST + directed.size ->
                    call?.let { addDirected(it, directed[id - ID_DIRECTED_FIRST].second) }
                // PC buildRelayAction -> prependMessageText("CALL>")
                id == ID_RELAY -> call?.let { relayVia(it) }
                id == ID_DESELECT -> clearSelection()
                id == ID_ADD -> askAddStation()
                id == ID_REMOVE -> call?.let { removeStation(it) }
                id == ID_BLOCK -> call?.let { blockStation(it) }
                id == ID_CLEAR_LIST -> { a.clearCalls(); clearSelection(); activityVm.changed() }
                id == ID_CLEAR_ALL -> askClearAll()
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
        popup.show()
    }

    private fun saveCurrentMessage() {
        prefs().edit().putString(Js8SettingsSchema.SAVED_MESSAGES_KEY,
            (savedMessages() + typingBox.text.toString().trim()).joinToString("\n")).apply()
    }

    /** Saved Messages... and Directed to CALL... submenus; their actions go in [actions] by item id. */
    private fun addSavedAndDirected(m: android.view.Menu, call: String?, actions: MutableMap<Int, () -> Unit>) {
        val saved = savedMessages()
        val savedMenu = m.addSubMenu(3, ID_SAVED, 20, "Saved Messages...")
        saved.forEachIndexed { i, text ->
            savedMenu.add(0, ID_SAVED_FIRST + i, i, macro(text))
            actions[ID_SAVED_FIRST + i] = { addMessageText(macro(text), send = true) }
        }
        savedMenu.add(0, ID_SAVED_EDIT, saved.size, "Edit Saved Messages")
        actions[ID_SAVED_EDIT] = { openStation(page = Js8SettingsSchema.SAVED_PAGE) }
        savedMenu.add(0, ID_SAVED_CURRENT, saved.size + 1, "Save Current Message").isEnabled = typingBox.text.isNotBlank()
        actions[ID_SAVED_CURRENT] = { saveCurrentMessage() }
        val directedMenu = m.addSubMenu(3, ID_DIRECTED, 21, "Directed to ${call.orEmpty()}...")
        directedMenu.item.isEnabled = call != null
        androidx.core.view.MenuCompat.setGroupDividerEnabled(directedMenu, true)
        if (call != null) directedItems(call).forEachIndexed { i, (label, text) ->
            directedMenu.add(directedGroup(i), ID_DIRECTED_FIRST + i, i, label).isEnabled = directedEnabled(call, text)
            actions[ID_DIRECTED_FIRST + i] = { addDirected(call, text) }
        }
    }

    private fun relayVia(call: String) {
        if (activityVm.typed.value == null) typingBox.text.insert(0, "$call>") // PC prependMessageText
    }

    /** PC Clear All Lists: asks first. */
    private fun askClearAll() {
        AlertDialog.Builder(requireContext()).setTitle("Clear All Activity")
            .setMessage("Are you sure you would like to clear all activity?")
            .setPositiveButton("Yes") { _, _ -> clearAllActivity() }
            .setNegativeButton("No", null).show()
    }

    /** PC clearRXActivity: the Typing Box empties and unlocks, so there's always a way out. */
    private fun clearRxActivity() {
        activityVm.activity.clearRx()
        activityVm.clearTyped()
        typingBox.text.clear()
        activityVm.changed()
    }

    private fun clearAllActivity() {
        val a = activityVm.activity
        a.clearBand(); a.clearCalls()
        clearRxActivity()
        activityVm.lastMessage = ""
        clearSelection()
        activityVm.changed()
    }

    /** Press and hold a Band Activity row: the PC's right-click menu (UI_Constructor.cpp, tableWidgetRXAll). */
    private fun showBandMenu(anchor: View, rowId: String?) {
        val offset = rowId?.let { it.removePrefix("o").toIntOrNull() ?: return }
        val a = activityVm.activity
        // Pressed below the rows (PC rowAt -1): the selection stays as it is, a Call Activity row or none,
        // no Jump items, Remove Activity greyed (UC 1018-1022)
        if (offset != null) {
            callActivity.selectedRowId = null
            bandActivity.selectedRowId = rowId
            selectedCall = a.callAtOffset(offset)
            renderSelection()
        }
        val call = selectedCall
        val isAllCall = call == "@ALLCALL"
        val last = offset?.let { a.bandActivity[it]?.lastOrNull() }
        val mySubmode = Js8Activity.txSubmode(prefs().getInt(Js8SettingsSchema.TX_SUBMODE_KEY, 0))

        val popup = popupMenu(anchor)
        val m = popup.menu
        androidx.core.view.MenuCompat.setGroupDividerEnabled(m, true)
        val actions = HashMap<Int, () -> Unit>()
        if (offset != null) {
            m.add(1, ID_JUMP_OFFSET, 1, "Jump to ${offset}Hz")
            actions[ID_JUMP_OFFSET] = { jumpToOffset(offset) }
        }
        if (offset != null && wfControls?.filterEnabled == true) {
            m.add(1, ID_FILTER_CENTER, 1, "Center filter at ${offset}Hz")
            actions[ID_FILTER_CENTER] = { wfControls?.setFilterCenter(offset) }
        }
        if (last != null) {
            if (last.submode != mySubmode && Js8Activity.txSubmode(last.submode) == last.submode) {
                m.add(1, ID_JUMP_SPEED, 2, "Jump to ${speedName(last.submode)} speed")
                actions[ID_JUMP_SPEED] = { jumpToSpeed(last.submode) }
            }
            val drift = -(last.tdrift * 1000).toInt()
            m.add(1, ID_JUMP_DRIFT, 3, "Jump to $drift ms time drift")
            actions[ID_JUMP_DRIFT] = {
                requireContext().startService(Intent(requireContext(), JS8EngineService::class.java).apply {
                    action = JS8EngineService.ACTION_SET_TIME_DRIFT
                    putExtra(JS8EngineService.EXTRA_TIME_DRIFT_MS, drift.toLong())
                })
            }
        }
        m.add(2, ID_LOG, 10, "Log...").isEnabled = call != null && !isAllCall
        actions[ID_LOG] = { openLog(call) }
        addSavedAndDirected(m, call, actions)
        m.add(3, ID_RELAY, 22, "Relay via ${call.orEmpty()}...").isEnabled = call != null
        actions[ID_RELAY] = { call?.let { relayVia(it) } }
        m.add(3, ID_DESELECT, 23, "Deselect ${call.orEmpty()}").isEnabled = call != null
        actions[ID_DESELECT] = { clearSelection() }
        m.add(4, ID_REMOVE, 30, "Remove Activity").isEnabled = offset != null
        actions[ID_REMOVE] = { offset?.let { a.bandActivity.remove(it) }; bandActivity.selectedRowId = null; activityVm.changed() }
        m.add(5, ID_CLEAR_LIST, 40, "Clear")
        actions[ID_CLEAR_LIST] = { a.clearBand(); clearSelection(); activityVm.changed() }
        m.add(5, ID_CLEAR_ALL, 41, "Clear All Lists")
        actions[ID_CLEAR_ALL] = { askClearAll() }
        popup.setOnMenuItemClickListener { item -> actions[item.itemId]?.invoke(); actions.containsKey(item.itemId) }
        popup.show()
    }

    /** Press and hold the Typing Box: the PC's right-click menu (UI_Constructor.cpp, extFreeTextMsgEdit). */
    private fun showTypingMenu(anchor: View) {
        val a = activityVm.activity
        val call = selectedCall
        val free = activityVm.typed.value == null
        val popup = popupMenu(anchor)
        val m = popup.menu
        androidx.core.view.MenuCompat.setGroupDividerEnabled(m, true)
        val actions = HashMap<Int, () -> Unit>()
        addSuggestions(m, actions, free)
        m.add(1, ID_RESTORE, 1, "Restore Previous Message").isEnabled = activityVm.lastMessage.isNotEmpty()
        actions[ID_RESTORE] = { addMessageText(activityVm.lastMessage.trimEnd(), send = false, clear = true) }
        addSavedAndDirected(m, call, actions)
        // PC buildRelayMenu: Call Activity without the stations aged out
        val now = System.currentTimeMillis()
        val relays = a.callActivity.values.filter { agingCalls() == 0 || (now - it.utcMs) / 60000 < agingCalls() }.map { it.call }
        val relayMenu = m.addSubMenu(3, ID_RELAY, 22, "Relay via...")
        relayMenu.item.isEnabled = typingBox.text.isNotEmpty() && relays.isNotEmpty()
        relays.forEachIndexed { i, c ->
            relayMenu.add(0, ID_RELAY_FIRST + i, i, c)
            actions[ID_RELAY_FIRST + i] = { relayVia(c) }
        }
        fun edit(id: Int, order: Int, label: String, enabled: Boolean, command: Int) {
            m.add(4, id, order, label).isEnabled = enabled
            actions[id] = { typingBox.onTextContextMenuItem(command) }
        }
        edit(ID_CUT, 30, "Cut", free && typingBox.hasSelection(), android.R.id.cut)
        edit(ID_COPY, 31, "Copy", typingBox.hasSelection(), android.R.id.copy)
        edit(ID_PASTE, 32, "Paste", free, android.R.id.paste)
        m.add(5, ID_CLEAR_LIST, 40, "Clear")
        actions[ID_CLEAR_LIST] = {
            // PC resetMessage: a message on its way is stopped too
            if (free) typingBox.setText("") else view?.findViewById<View>(R.id.js8_btn_halt)?.performClick()
            activityVm.lastMessage = ""
        }
        m.add(5, ID_CLEAR_ALL, 41, "Clear All Lists")
        actions[ID_CLEAR_ALL] = { askClearAll() }
        popup.setOnMenuItemClickListener { item -> actions[item.itemId]?.invoke(); actions.containsKey(item.itemId) }
        popup.show()
    }

    /** PC buildSuggestionsMenu: alternatives for the underlined word that was pressed. */
    private fun addSuggestions(m: android.view.Menu, actions: MutableMap<Int, () -> Unit>, free: Boolean) {
        if (!boolPref("js8cfg_spellcheck", true) || !free) return
        val text = typingBox.text
        val word = Js8TypingSpans.wordAt(text.toString(), typingPressOffset) ?: return
        val upper = text.substring(word.first, word.last + 1).uppercase().removePrefix("@")
        if (com.js8call.core.JS8Engine.jscCorrect(upper)) return
        val found = com.js8call.core.JS8Engine.jscSuggestions(upper, 5)
        if (found.isEmpty()) m.add(0, ID_SUGGEST_FIRST, 0, "No Suggestions").isEnabled = false
        found.forEachIndexed { i, s ->
            m.add(0, ID_SUGGEST_FIRST + i, 0, s)
            actions[ID_SUGGEST_FIRST + i] = { text.replace(word.first, word.last + 1, s) }
        }
    }

    /** Press and hold the Message Window: the PC's right-click menu (UI_Constructor.cpp, textEditRX). */
    private fun showMessageWindowMenu(anchor: View) {
        val popup = popupMenu(anchor)
        val m = popup.menu
        androidx.core.view.MenuCompat.setGroupDividerEnabled(m, true)
        // PC buildEditMenu: the window is read-only, so Cut and Paste stay grey; Copy needs a selection
        m.add(1, 5, 0, "Cut").isEnabled = false
        m.add(1, 1, 1, "Copy").isEnabled = messageWindow.hasSelection()
        m.add(1, 6, 1, "Paste").isEnabled = false
        m.add(2, 2, 2, "Clear")
        m.add(2, 3, 3, "Clear All Lists")
        m.add(3, 4, 4, "Save As...")
        popup.setOnDismissListener { if (anchor.tag == PRESS_ANCHOR) (anchor.parent as? ViewGroup)?.removeView(anchor) }
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> (requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                    .setPrimaryClip(android.content.ClipData.newPlainText("Message Window",
                        messageWindow.text.substring(messageWindow.selectionStart, messageWindow.selectionEnd)))
                2 -> clearRxActivity()
                3 -> askClearAll()
                else -> saveMessageWindow.launch("js8call-" +
                    java.text.SimpleDateFormat("yyyyMMdd", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                        .format(java.util.Date()) + ".txt")
            }
            true
        }
        popup.show()
    }

    /** Press and hold the waterfall: the PC's right-click menu (WideGraph.cpp). */
    private fun showWaterfallMenu(hz: Int) {
        val controls = wfControls
        val popup = popupMenu(waterfall)
        val m = popup.menu
        androidx.core.view.MenuCompat.setGroupDividerEnabled(m, true)
        val actions = HashMap<Int, () -> Unit>()
        m.add(1, 1, 1, "Set Offset to $hz Hz")
        actions[1] = { jumpToOffset(hz) }
        if (controls != null) {
            if (controls.filterEnabled) {
                m.add(2, 2, 2, "Disable Filter")
                actions[2] = { controls.tickFilter(false) }
            }
            m.add(2, 3, 3, "Set Filter Center to $hz Hz")
            actions[3] = { controls.setFilterCenter(hz); controls.tickFilter(true) }
            val widthMenu = m.addSubMenu(2, 4, 4, "Set Filter Width to...")
            listOf(25, 50, 75, 100, 250, 500, 750, 1000, 1500, 2000).filter { it >= controls.filterWidthMin }
                .forEachIndexed { i, width ->
                    widthMenu.add(0, 10 + i, i, "$width Hz")
                    actions[10 + i] = { controls.setFilterWidth(width); controls.tickFilter(true) }
                }
        }
        popup.setOnMenuItemClickListener { item -> actions[item.itemId]?.invoke(); actions.containsKey(item.itemId) }
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

    private fun stepDial(hz: Long) {
        dialHz += hz
        prefs().edit().putString(Js8SettingsSchema.FREQUENCY_KEY, dialHz.toString()).apply()
        renderFrequency()
    }

    /**
     * Waterfall Controls > QSY (UI_Constructor::qsy): the dial moves by offset - center, so the
     * signal at the offset is heard at center, and the offset becomes center. The dial goes to
     * Settings > Frequency (last_frequency), which retunes the radio when rig control is on.
     */
    private fun qsy(center: Int) {
        if (!offsetChangeAllowed()) return
        val delta = monitorVm.getTxOffset().toLong() - center
        if (delta == 0L) return
        dialHz += delta
        prefs().edit().putString(Js8SettingsSchema.FREQUENCY_KEY, dialHz.toString()).apply()
        jumpToOffset(center)
        activityVm.activity.shiftBand(delta.toInt())
        activityVm.changed()
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
            // PC: text selected in the Message Window fills Comments
            putString(Js8LogFragment.ARG_MESSAGE_WINDOW, messageWindow.text.toString().takeLast(8000))
            if (messageWindow.hasSelection()) putString(Js8LogFragment.ARG_COMMENTS,
                messageWindow.text.substring(messageWindow.selectionStart, messageWindow.selectionEnd))
        }
        navigateFromMain(R.id.navigation_js8_log, args)
    }

    /** Call Activity > Store Message...: held for [call] to collect (MessageReplyDialog, addCommandToStorage). */
    private fun storeMessage(call: String) {
        val box = EditText(requireContext()).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
            // PC MessageReplyDialog: upper case, newlines and printable 7-bit characters only
            filters = arrayOf(android.text.InputFilter { source, start, end, _, _, _ ->
                source.subSequence(start, end).toString().uppercase(Locale.US).filter { it == '\n' || it.code in 32..126 }
            })
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
                    com.js8call.example.data.Js8Mailbox.store(requireContext(), call, my, text, relayPath = my, dialHz = dialHz)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ---- HB button (PC hbMacroButton) ----------------------------------------------------------

    private fun hbInterval() = intPref("heartbeat_interval", 30)

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

    /** presentlyWantHBReplies: AUTO + ACK, nothing waiting to send, and no QSO under way when HB pauses for QSOs. */
    private fun wantHbReplies() = boolPref(Js8SettingsSchema.AUTOREPLY_KEY, false) && boolPref(Js8SettingsSchema.HB_ACK_KEY, false) &&
        activityVm.typed.value == null && (!boolPref("js8cfg_heartbeat_qso_pause", true) || selectedCall == null)

    /** updateHBButtonDisplay: "HB" / "HB + ACK", "(seconds)" to the next one while the timer runs. */
    private fun renderHbLabel(now: Long) {
        val hb = view?.findViewById<TextView?>(R.id.js8_btn_hb) ?: return
        if (hb.visibility != View.VISIBLE) return
        val timer = com.js8call.example.service.HeartbeatTimer
        val ack = wantHbReplies()
        // Upright: only "HB" or the seconds, the small button can't hold "HB + ACK" (Rob, 2026-10-22)
        val base = if (ack && !isUprightLayout()) "HB + ACK" else "HB"
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
    private fun showHbMenu(anchor: View) = showMenu(anchor) { fillHbMenu(it, 0) }

    private fun fillHbMenu(m: android.view.Menu, base: Int) = fillRepeatMenu(m, base, "Heartbeat", listOf(10, 15, 30, 60), hbInterval(),
        com.js8call.example.service.HeartbeatTimer.on, "Send Heartbeat Now",
        toggle = { setHbTimer(!com.js8call.example.service.HeartbeatTimer.on) },
        setInterval = { min -> prefs().edit().putString("heartbeat_interval", min.toString()).apply(); setHbTimer(min > 0) },
        sendNow = { sendHbNow() })

    /** buildCQMenu + buildRepeatMenu(isLowInterval = true): the CQ repeat times 1 / 5 / 10 / 15 (PC CQInterval). */
    private fun showCqMenu(anchor: View) = showMenu(anchor) { fillCqMenu(it, 0) }

    private fun fillCqMenu(m: android.view.Menu, base: Int) = fillRepeatMenu(m, base, "CQ", listOf(1, 5, 10, 15), cqInterval(),
        com.js8call.example.service.CqTimer.on, "Send CQ Now",
        toggle = { setCqTimer(!com.js8call.example.service.CqTimer.on) },
        setInterval = { min -> prefs().edit().putInt(com.js8call.example.service.CqTimer.KEY_INTERVAL, min).apply(); setCqTimer(min > 0) },
        sendNow = { sendCq(repeat = false) })

    /**
     * The PC's HB / CQ button menu: Start / Stop <name> Timer (with a repeat time), On demand /
     * the repeat times / Repeat every N minutes (Custom Interval), the one in use ticked, and
     * Send ... Now. A repeat time starts the timer, On demand stops it.
     */
    private fun fillRepeatMenu(m: android.view.Menu, base: Int, name: String, minutes: List<Int>, interval: Int, on: Boolean,
                               nowText: String, toggle: () -> Unit, setInterval: (Int) -> Unit, sendNow: () -> Unit): (Int) -> Boolean {
        androidx.core.view.MenuCompat.setGroupDividerEnabled(m, true)
        if (interval > 0) m.add(1, base + 1, 1, if (on) "Stop $name Timer" else "Start $name Timer")
        val repeats = listOf(0 to "On demand / do not repeat") +
            minutes.map { it to if (it == 1) "Repeat every 1 minute" else "Repeat every $it minutes" }
        val custom = interval !in repeats.map { it.first }
        repeats.forEachIndexed { i, (min, text) -> m.add(2, base + 100 + min, 10 + i, text).apply { isCheckable = true; isChecked = min == interval } }
        m.add(2, base + 99, 20, if (custom) "Repeat every $interval minutes (Custom Interval)" else "Repeat every N minutes (Custom Interval)")
            .apply { isCheckable = true; isChecked = custom }
        m.setGroupCheckable(2, true, true)
        m.add(3, base + 2, 30, nowText)
        val ids = setOf(base + 1, base + 2, base + 99) + repeats.map { base + 100 + it.first }
        return { id ->
            when (id) {
                !in ids -> false
                base + 1 -> { toggle(); true }
                base + 2 -> { sendNow(); true }
                base + 99 -> { askMinutes(interval, setInterval); true }
                else -> { setInterval(id - base - 100); true }
            }
        }
    }

    /** A popup filled by [fill]; a tap runs the click function [fill] returns. */
    private fun showMenu(anchor: View, fill: (android.view.Menu) -> (Int) -> Boolean) {
        val menu = popupMenu(anchor)
        val click = fill(menu.menu)
        menu.setOnMenuItemClickListener { click(it.itemId) }
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
                        val groups = p.getString("my_groups", "").orEmpty().split(',').map { it.trim().uppercase(Locale.US) }
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
                val groups = p.getString("my_groups", "").orEmpty().split(',').map { it.trim() }
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
        val list = p.getString("js8cfg_rx_blocklist", "").orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (list.none { it.equals(call, ignoreCase = true) }) {
            p.edit().putString("js8cfg_rx_blocklist", (list + call).joinToString(",")).apply()
        }
        activityVm.changed()
    }

    private fun showMessage(text: String) {
        AlertDialog.Builder(requireContext()).setMessage(text).setPositiveButton(android.R.string.ok, null).show()
    }

    private var timersSelection: String? = null
    private var hbPaused = false

    /** PC callsignSelectedChanged: a selection stops the HB and CQ timers, deselecting restores HB. */
    private fun selectionTimers() {
        if (selectedCall == timersSelection) return
        timersSelection = selectedCall
        if (selectedCall == null) {
            if (hbPaused) {
                hbPaused = false
                setHbTimer(true)
            }
            return
        }
        if (!boolPref("js8cfg_heartbeat_qso_pause", true)) return
        if (com.js8call.example.service.HeartbeatTimer.on) {
            setHbTimer(false)
            hbPaused = true
        }
        if (com.js8call.example.service.CqTimer.on) setCqTimer(false)
    }

    /** REPLY, SNR, INFO, Directed, Deselect are enabled only while a callsign is selected (desktop behaviour). */
    private fun renderSelection() {
        Js8TypingSpans.apply(typingBox.text, prefs(), selectedCall)
        Js8Api.setSelectedCall(selectedCall) // API: STATION.STATUS SELECTED, RX.GET_CALL_SELECTED
        selectionTimers()
        val heard = selectedCall?.let { activityVm.activity.callActivity[it] }
        Js8Macros.selected = heard?.let { Js8Macros.Selected(selectedCall.orEmpty(), it.snr, it.tdrift) }
        selectedCall?.let { if (it !in callSelectedTime) callSelectedTime[it] = System.currentTimeMillis() }
        requestTextStats() // the directed callsign changes the parts (PC m_txTextDirtyLastSelectedCall)
        renderModeButton() // +HB+ACK follows the selection (presentlyWantHBReplies)
        renderButtons()
    }

    /** PC JS8Submode order: Slow sorts as the slowest (displayBandActivity compareSubmode). */
    private fun speedSort(submode: Int) = if (submode == 4) -4 else submode

    /** PC updateButtonDisplay and updateTextDisplay. */
    private fun renderButtons() {
        val v = view ?: return
        val has = selectedCall != null
        val swl = listenOnly()
        val busy = activityVm.typed.value != null || transmitVm.isBusy()
        fun on(id: Int, enabled: Boolean) { v.findViewById<View?>(id)?.isEnabled = enabled && !swl }
        listOf(R.id.js8_btn_reply, R.id.js8_btn_snr, R.id.js8_btn_directed).forEach { on(it, has && !busy) }
        on(R.id.js8_btn_info, !busy && prefs().getString("my_info", "").orEmpty().isNotBlank())
        on(R.id.js8_btn_status, !busy && prefs().getString("my_status", "").orEmpty().isNotBlank())
        on(R.id.js8_btn_saved, !busy)
        on(R.id.js8_btn_hb, !busy)
        on(R.id.js8_btn_cq, !busy)
        v.findViewById<View?>(R.id.js8_btn_deselect)?.isEnabled = has && !busy
        // PC: Send is grey while any message is queued or going out, a heartbeat or reply too (isMessageQueuedForTransmit)
        on(R.id.js8_btn_send, !busy && typingBox.text.isNotBlank() && boolPref("js8cfg_tx_enabled", true))
        // PC updateButtonDisplay, updateTxButtonDisplay, callsignSelectedChanged
        val call = selectedCall
        v.findViewById<TextView?>(R.id.js8_btn_directed)?.let { b ->
            // The upright button is too narrow for the PC's "Directed to CALL"
            val full = if (call == null) "Directed" else "Directed to $call"
            b.text = if (b.paint.measureText(full) <= b.width - b.paddingLeft - b.paddingRight) full else "Directed"
        }
        renderSendText(System.currentTimeMillis())
        typingBox.hint = if (call == null) "TYPE YOUR OUTGOING MESSAGES HERE."
            else "TYPE YOUR OUTGOING DIRECTED MESSAGE TO $call HERE."
    }

    /** PC m_txFrameCountEstimate: parts the Typing Box text will take. */
    private var txFrameEstimate = 0

    /** PC State::timed: "Send (45s)", "Send (1m 30s)", "Send (2m)". */
    private fun timed(state: String, seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return when {
            m > 0 && s > 0 -> "$state (${m}m ${s}s)"
            m > 0 -> "$state (${m}m)"
            else -> "$state (${s}s)"
        }
    }

    /** PC updateTxButtonDisplay. A button too narrow for the time shows the word alone. */
    private fun renderSendText(now: Long) {
        val b = view?.findViewById<TextView?>(R.id.js8_btn_send) ?: return
        val t = progress.period
        val second = ((now / 1000) % t).toInt()
        val left = activityVm.txFramesLeft
        val text = when {
            view?.findViewById<View>(R.id.js8_btn_tune)?.isActivated == true -> "Tuning"
            transmitVm.txState.value == TransmitState.TRANSMITTING ->
                if (left >= 0) timed("Sending", (left + 1) * t - (second + 1) % t) else "Sending"
            // PC: Send takes the first part off the queue at once, each part's end the next one (startTx, stopTx)
            transmitVm.isBusy() -> when {
                left >= 0 -> timed("Ready", (left + 1) * t - (second + 1) % t) // between parts
                activityVm.txFrameCount > 0 -> timed("Ready", activityVm.txFrameCount * t) // before the first
                else -> "Ready"
            }
            txFrameEstimate > 0 -> timed("Send", txFrameEstimate * t)
            else -> "Send"
        }
        val fits = b.width == 0 || b.paint.measureText(text) <= b.width - b.paddingLeft - b.paddingRight
        val shown = if (fits) text else text.substringBefore(" (")
        if (b.text.toString() != shown) b.text = shown
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
        waterfall.onHold = { showWaterfallMenu(it) }
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
        ruler.filterHz = if (waterfall.filterEnabled && waterfall.filterWidthHz > 0f)
            (waterfall.filterCenterHz - waterfall.filterWidthHz / 2f)..(waterfall.filterCenterHz + waterfall.filterWidthHz / 2f) else null
        ruler.filterOpacity = waterfall.filterOpacityPercent
    }

    // ---- Engine -------------------------------------------------------------------------

    private fun observe() {
        // New decodes are gathered and drawn by the clock tick (PC: m_rxDisplayDirty)
        activityVm.version.observe(viewLifecycleOwner) {
            requestRender()
            view?.findViewById<TextView?>(R.id.js8_status_blank)?.text =
                activityVm.lastTx.takeIf { it.isNotEmpty() }?.let { "Last Tx: $it" }.orEmpty()
        }
        activityVm.typed.observe(viewLifecycleOwner) { renderTyped(it); renderModeButton() }
        observeInbox()
        monitorVm.status.observe(viewLifecycleOwner) { status ->
            val running = status.state == EngineState.RUNNING || status.state == EngineState.STARTING
            btnRx.isActivated = running
            // PC dataSink m_px: 10 log10 of the mean square of the raw samples, shown as is (+90 pinned it at 90);
            // m_pxmax, the loudest sample, turns the bar red
            levelMeter.setLevel(if (running) status.powerDb else 0f, if (running) status.peakDb else 0f)
            renderStatus()
            offsetSlider?.let { if (it.progress != status.txOffsetHz.toInt()) it.progress = status.txOffsetHz.toInt() }
            offsetValue?.text = "${status.txOffsetHz.toInt()} Hz" // FT8Call has no slider to update it
            renderOffsetBox()
            wfControls?.setTimingState(status.timeDriftMs, status.autoDriftOn)
            monitorVm.getWaterfallRenderer().timeDriftMs = status.timeDriftMs
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
        monitorVm.rigConnected.observe(viewLifecycleOwner) { connected -> renderCat(connected) }
    }

    /**
     * PC readFreq button: green and disabled while the rig is fine, red and live after a failure,
     * orange and live while the rig opens (rigOpen), "CAT/S" in split.
     */
    private fun renderCat(connected: Boolean) {
        val cat = requireView().findViewById<TextView>(R.id.js8_btn_cat)
        val hasRig = prefs().getString("rig_type", "none") != "none"
        val ok = connected || !hasRig
        val opening = !ok && monitorVm.rigOpening
        cat.isActivated = ok
        cat.isSelected = !ok
        cat.isEnabled = !ok
        val background = if (opening) R.drawable.js8_shape_orange else R.drawable.js8_btn_toggle_green
        if (cat.tag != background) { cat.tag = background; cat.setBackgroundResource(background) }
        // PC: what the rig says; before a poll has read it, or with a rig that can't tell, the setting
        val split = connected && (JS8EngineService.rigSplit ?: prefs().getString("js8cfg_split", "none").orEmpty().let { it != "none" && it.isNotEmpty() })
        cat.text = if (split) "CAT/S" else "CAT"
    }

    private fun renderStatus() {
        val state = monitorVm.status.value?.state ?: EngineState.STOPPED
        val transmitting = state == EngineState.RUNNING && transmitVm.txState.value == TransmitState.TRANSMITTING
        btnTx.isSelected = transmitting
        renderButtons()
        when {
            transmitting -> {
                val tuning = requireView().findViewById<View>(R.id.js8_btn_tune).isActivated
                statusState.text = if (tuning) "Tx: TUNE" else activityVm.frameOnAirText()?.let { "Tx: $it".take(40).trim() } ?: "Transmitting"
                statusState.setBackgroundResource(R.drawable.js8_status_red)
                statusState.setTextColor(Color.BLACK)
            }
            // PC styles.h IdleTimeout: black, white text
            state == EngineState.RUNNING && JS8EngineService.idleTimedOutMinutes != 0L -> {
                statusState.text = "Idle timeout"; statusState.setBackgroundResource(R.drawable.js8_status_black)
                statusState.setTextColor(Color.WHITE)
            }
            state == EngineState.RUNNING || state == EngineState.STARTING -> {
                statusState.text = if (Js8Api.State.decoding) "Decoding" else "Receiving"; statusState.setBackgroundResource(R.drawable.js8_status_green)
                statusState.setTextColor(Color.BLACK)
            }
            // PC guiUpdate, RX off: an empty label with no colour; after an idle timeout it stays as it was
            JS8EngineService.idleTimedOutMinutes != 0L -> Unit
            else -> { statusState.text = ""; statusState.background = null; statusState.setTextColor(Color.BLACK) }
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
