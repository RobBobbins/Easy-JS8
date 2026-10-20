package com.js8call.example.js8ui

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.graphics.Typeface
import android.widget.CheckBox
import android.widget.TextView
import androidx.core.widget.TextViewCompat
import com.js8call.example.R
import com.js8call.example.ui.WaterfallView

/**
 * Waterfall Controls > Control tab (JS8_UI/WideGraph.cpp): Offset, QSY + Center, and the filter
 * (Enable Filter, Sync, Center / Width boxes and knobs). Values are kept between starts with the
 * PC's defaults: center 1500, filter on, filter center 1500, width 2000 (Rob, 2026-10-20).
 *
 * Timing tab: Time Drift box, Start Automatic Time Drift with Stop After n decode(s), and the four
 * Manual buttons. The drift itself lives in the engine service.
 */
class Js8WaterfallControls(
    private val root: View,
    private val prefs: SharedPreferences,
    private val waterfall: WaterfallView,
    private val offset: () -> Int,
    /** Offset box ∧ ∨: the main window applies it (and refuses while a message waits). */
    private val setOffset: (Int) -> Unit,
    /** QSY: retune so the offset lands on this center. */
    private val qsy: (Int) -> Unit,
    /** Current speed's cycle in seconds (WideGraph m_TRperiod). */
    private val period: () -> Int,
    /** Timing: new time drift in ms (WideGraph::want_new_drift). */
    private val setDrift: (Long) -> Unit,
    /** Timing: Start / Stop Automatic Time Drift; stop after n decodes, 0 = keep going. */
    private val setAutoDrift: (Boolean, Int) -> Unit
) {
    companion object {
        const val KEY_CENTER = "js8cfg_wf_center"
        const val KEY_FILTER_ENABLED = "js8cfg_filter_enabled"
        const val KEY_FILTER_CENTER = "js8cfg_filter_center"
        const val KEY_FILTER_WIDTH = "js8cfg_filter_width"
        // WideGraph settings StopAutoSyncOnDecode / StopAutoSyncAfter
        const val KEY_AUTO_DRIFT_STOP = "js8cfg_auto_drift_stop"
        const val KEY_AUTO_DRIFT_AFTER = "js8cfg_auto_drift_after"

        // WideGraph.ui ranges
        private const val CENTER_MIN = 500
        private const val CENTER_MAX = 5000
        private const val FILTER_CENTER_MIN = 120
        private const val FILTER_CENTER_MAX = 4880
        private const val FILTER_WIDTH_MIN = 120
        private const val FILTER_WIDTH_MAX = 5000
        private const val DRIFT_MAX = 30000
        private const val AUTO_AFTER_MAX = 1024
    }

    private val ui = Handler(Looper.getMainLooper())
    private fun <T : View> v(id: Int): T? = root.findViewById(id)

    private var center = prefs.getInt(KEY_CENTER, 1500).coerceIn(CENTER_MIN, CENTER_MAX)
    private var filterOn = prefs.getBoolean(KEY_FILTER_ENABLED, true)
    private var filterCenter = prefs.getInt(KEY_FILTER_CENTER, 1500).coerceIn(FILTER_CENTER_MIN, FILTER_CENTER_MAX)
    private var filterWidth = prefs.getInt(KEY_FILTER_WIDTH, 2000).coerceIn(FILTER_WIDTH_MIN, FILTER_WIDTH_MAX)

    private var autoStop = prefs.getBoolean(KEY_AUTO_DRIFT_STOP, true)
    private var autoAfter = prefs.getInt(KEY_AUTO_DRIFT_AFTER, 1).coerceIn(1, AUTO_AFTER_MAX)
    private var driftMs = 0L
    private var autoOn = false
    private val autoButtonFace: Typeface? = v<TextView>(R.id.js8_wf_auto_drift)?.typeface

    private val centerKnob = v<Js8KnobView>(R.id.js8_wf_filter_center_knob)?.apply {
        minimum = FILTER_CENTER_MIN; maximum = FILTER_CENTER_MAX
        onValueChanged = { setFilterCenter(it) }
    }
    private val widthKnob = v<Js8KnobView>(R.id.js8_wf_filter_width_knob)?.apply {
        minimum = FILTER_WIDTH_MIN; maximum = FILTER_WIDTH_MAX
        onValueChanged = { setFilterWidth(it) }
    }

    init {
        stepper(R.id.js8_wf_offset_up, R.id.js8_wf_offset_down) { step -> setOffset((offset() + step).coerceIn(0, 5000)) }
        stepper(R.id.js8_wf_center_up, R.id.js8_wf_center_down) { step -> setCenter(center + step) }
        stepper(R.id.js8_wf_filter_center_up, R.id.js8_wf_filter_center_down) { step -> if (filterOn) setFilterCenter(filterCenter + step) }
        stepper(R.id.js8_wf_filter_width_up, R.id.js8_wf_filter_width_down) { step -> if (filterOn) setFilterWidth(filterWidth + step) }
        // WideGraph::on_qsyPushButton_clicked: qsy(freq() - centerFreq())
        v<View>(R.id.js8_wf_qsy)?.setOnClickListener { qsy(center) }
        v<CheckBox>(R.id.js8_wf_filter_enable)?.apply {
            isChecked = filterOn
            setOnCheckedChangeListener { _, on -> setFilterEnabled(on) }
        }
        // on_filterCenterSyncButton_clicked: the filter center goes to the offset
        v<View>(R.id.js8_wf_filter_sync)?.setOnClickListener { setFilterCenter(offset()) }
        setUpTabs()
        setUpTiming()
        fitUpright()
        renderAll()
    }

    // ---- Upright sizes ------------------------------------------------------------------

    /**
     * Upright (Rob, 2026-10-20): buttons and fields only as wide as their text, centred; the
     * group frames stay full width. Fields are as wide as their longest value so they don't
     * change width as the value changes. Sideways keeps the PC's fill-the-panel widths.
     */
    private fun fitUpright() {
        if (root.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) return
        val dp = root.resources.displayMetrics.density
        fun wrap(id: Int) = v<View>(id)?.let { view ->
            (view.layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
                lp.width = ViewGroup.LayoutParams.WRAP_CONTENT
                lp.weight = 0f
                lp.gravity = Gravity.CENTER_HORIZONTAL
                view.layoutParams = lp
            }
        }
        fun button(id: Int) {
            wrap(id)
            v<TextView>(id)?.let { b ->
                val pad = (12 * dp).toInt()
                b.setPaddingRelative(pad, b.paddingTop, pad, b.paddingBottom)
            }
        }
        fun field(boxId: Int, textId: Int, longest: String) {
            wrap(boxId)
            wrap(textId)
            v<TextView>(textId)?.let { t ->
                t.setPaddingRelative(t.paddingStart, t.paddingTop, (8 * dp).toInt(), t.paddingBottom)
                t.minWidth = Math.ceil(t.paint.measureText(longest).toDouble()).toInt() + t.paddingStart + t.paddingEnd
            }
        }
        fun centerRow(id: Int) { v<LinearLayout>(id)?.gravity = Gravity.CENTER }

        // Control tab
        button(R.id.js8_wf_qsy)
        field(R.id.js8_wf_center_box, R.id.js8_wf_center, "Center: $CENTER_MAX Hz")
        centerRow(R.id.js8_wf_filter_row)
        wrap(R.id.js8_wf_filter_enable)
        button(R.id.js8_wf_filter_sync)
        centerRow(R.id.js8_wf_filter_boxes_row)
        wrap(R.id.js8_wf_filter_center_col)
        wrap(R.id.js8_wf_filter_width_col)
        field(R.id.js8_wf_filter_center_box, R.id.js8_wf_filter_center, "Center: $FILTER_CENTER_MAX Hz")
        field(R.id.js8_wf_filter_width_box, R.id.js8_wf_filter_width, "Width: $FILTER_WIDTH_MAX Hz")
        // Timing tab
        field(R.id.js8_wf_drift_box, R.id.js8_wf_drift, "Time Drift: -$DRIFT_MAX ms")
        button(R.id.js8_wf_auto_drift)
        v<TextView>(R.id.js8_wf_auto_drift)?.let { b ->
            // room for the bold Stop text, so the button keeps its width when pressed
            val bold = android.graphics.Paint(b.paint).apply { typeface = Typeface.create(b.typeface, Typeface.BOLD) }
            b.minWidth = Math.ceil(bold.measureText("Stop Automatic Time Drift").toDouble()).toInt() + b.paddingStart + b.paddingEnd
        }
        centerRow(R.id.js8_wf_auto_drift_row)
        field(R.id.js8_wf_auto_drift_count_box, R.id.js8_wf_auto_drift_count, "$AUTO_AFTER_MAX decode(s)")
        // Minute Start shrinks its text only sideways, where the panel can be narrow
        v<TextView>(R.id.js8_wf_drift_minute)?.let {
            TextViewCompat.setAutoSizeTextTypeWithDefaults(it, TextViewCompat.AUTO_SIZE_TEXT_TYPE_NONE)
            it.setTextSize(TypedValue.COMPLEX_UNIT_PX, root.resources.getDimension(R.dimen.js8_text))
        }
        listOf(R.id.js8_wf_drift_minute, R.id.js8_wf_drift_end, R.id.js8_wf_drift_start, R.id.js8_wf_drift_reset)
            .forEach { button(it) }
    }

    // ---- Tabs ---------------------------------------------------------------------------

    private fun setUpTabs() {
        v<View>(R.id.js8_wf_tab_control)?.setOnClickListener { showTab(R.id.js8_wf_tab_control) }
        v<View>(R.id.js8_wf_tab_timing)?.setOnClickListener { showTab(R.id.js8_wf_tab_timing) }
        // Display tab: not built yet
    }

    private fun showTab(tabId: Int) {
        listOf(R.id.js8_wf_tab_control, R.id.js8_wf_tab_display, R.id.js8_wf_tab_timing).forEach { id ->
            v<View>(id)?.setBackgroundResource(if (id == tabId) R.drawable.js8_tab_selected else 0)
        }
        v<View>(R.id.js8_wf_control_page)?.visibility = if (tabId == R.id.js8_wf_tab_control) View.VISIBLE else View.GONE
        v<View>(R.id.js8_wf_timing_page)?.visibility = if (tabId == R.id.js8_wf_tab_timing) View.VISIBLE else View.GONE
    }

    // ---- Timing tab (WideGraph.cpp) -----------------------------------------------------

    private fun setUpTiming() {
        // driftSpinBox: 100 ms steps, -30000 to 30000
        stepper(R.id.js8_wf_drift_up, R.id.js8_wf_drift_down) { step ->
            val next = (driftMs + Integer.signum(step) * 100L).coerceIn(-DRIFT_MAX.toLong(), DRIFT_MAX.toLong())
            if (next != driftMs) { driftMs = next; renderTiming(); setDrift(next) }
        }
        v<View>(R.id.js8_wf_auto_drift)?.setOnClickListener {
            setAutoDrift(!autoOn, if (autoStop) autoAfter else 0)
        }
        v<CheckBox>(R.id.js8_wf_auto_drift_stop)?.apply {
            isChecked = autoStop
            setOnCheckedChangeListener { _, on ->
                autoStop = on
                prefs.edit().putBoolean(KEY_AUTO_DRIFT_STOP, on).apply()
                renderTiming()
            }
        }
        stepper(R.id.js8_wf_auto_drift_count_up, R.id.js8_wf_auto_drift_count_down) { step ->
            if (autoOn) return@stepper // greyed while running
            autoAfter = (autoAfter + step).coerceIn(1, AUTO_AFTER_MAX)
            prefs.edit().putInt(KEY_AUTO_DRIFT_AFTER, autoAfter).apply()
            renderTiming()
        }
        // on_driftSyncMinuteButton_clicked: nearest minute start, from the computer clock
        v<View>(R.id.js8_wf_drift_minute)?.setOnClickListener {
            val sec = utcSecond()
            setDrift((if (sec < 30) -sec else 60 - sec) * 1000L)
        }
        // on_driftSyncEndButton_clicked: nearest cycle end (2 s before the next start)
        v<View>(R.id.js8_wf_drift_end)?.setOnClickListener {
            val (pos, neg) = cycleDistances()
            setDrift((if (Math.abs(neg) < pos) neg + 2 else pos - 2) * 1000L)
        }
        // on_driftSyncButton_clicked: nearest cycle start
        v<View>(R.id.js8_wf_drift_start)?.setOnClickListener {
            val (pos, neg) = cycleDistances()
            setDrift((if (Math.abs(neg) < pos) neg else pos) * 1000L)
        }
        v<View>(R.id.js8_wf_drift_reset)?.setOnClickListener { setDrift(0L) }
    }

    private fun utcSecond() = ((System.currentTimeMillis() / 1000) % 60).toInt()

    /** Seconds to the next cycle start, and since the last one (negative). */
    private fun cycleDistances(): Pair<Int, Int> {
        val p = period()
        val sec = utcSecond()
        return (p - sec % p) to (sec % p - p)
    }

    /** The engine service's drift and Automatic Time Drift state (MonitorStatus). */
    fun setTimingState(drift: Long, auto: Boolean) {
        if (drift == driftMs && auto == autoOn) return
        driftMs = drift
        autoOn = auto
        renderTiming()
    }

    private fun renderTiming() {
        v<TextView>(R.id.js8_wf_drift)?.text = "Time Drift: $driftMs ms"
        // on_autoDriftButton_toggled: Start / Stop; checked = bold (WideGraph.ui style sheet)
        v<TextView>(R.id.js8_wf_auto_drift)?.apply {
            text = if (autoOn) "Stop Automatic Time Drift" else "Start Automatic Time Drift"
            setTypeface(autoButtonFace, if (autoOn) Typeface.BOLD else Typeface.NORMAL)
        }
        v<TextView>(R.id.js8_wf_auto_drift_count)?.text = "$autoAfter decode(s)"
        // autoDriftStopSpinBox is disabled while running
        v<View>(R.id.js8_wf_auto_drift_count_box)?.let { it.isEnabled = !autoOn; it.alpha = if (autoOn) 0.45f else 1f }
    }

    private fun setCenter(hz: Int) {
        center = hz.coerceIn(CENTER_MIN, CENTER_MAX)
        prefs.edit().putInt(KEY_CENTER, center).apply()
        renderAll()
    }

    /** Call Activity > Center filter at <offset>Hz too. */
    fun setFilterCenter(hz: Int) {
        filterCenter = hz.coerceIn(FILTER_CENTER_MIN, FILTER_CENTER_MAX)
        prefs.edit().putInt(KEY_FILTER_CENTER, filterCenter).apply()
        renderAll()
    }

    private fun setFilterWidth(hz: Int) {
        filterWidth = hz.coerceIn(FILTER_WIDTH_MIN, FILTER_WIDTH_MAX)
        prefs.edit().putInt(KEY_FILTER_WIDTH, filterWidth).apply()
        renderAll()
    }

    private fun setFilterEnabled(on: Boolean) {
        filterOn = on
        prefs.edit().putBoolean(KEY_FILTER_ENABLED, on).apply()
        renderAll()
    }

    val filterEnabled get() = filterOn

    private fun renderAll() {
        v<TextView>(R.id.js8_wf_center)?.text = "Center: $center Hz"
        v<TextView>(R.id.js8_wf_filter_center)?.text = "Center: $filterCenter Hz"
        v<TextView>(R.id.js8_wf_filter_width)?.text = "Width: $filterWidth Hz"
        centerKnob?.value = filterCenter
        widthKnob?.value = filterWidth
        // WideGraph::setFilterEnabled: Sync, both boxes and both knobs follow the checkbox
        listOf(R.id.js8_wf_filter_sync, R.id.js8_wf_filter_center_box, R.id.js8_wf_filter_width_box,
            R.id.js8_wf_filter_center_knob, R.id.js8_wf_filter_width_knob).forEach { id ->
            v<View>(id)?.let { it.isEnabled = filterOn; if (it !is Js8KnobView) it.alpha = if (filterOn) 1f else 0.45f }
        }
        waterfall.filterEnabled = filterOn
        waterfall.filterCenterHz = filterCenter.toFloat()
        waterfall.filterWidthHz = filterWidth.toFloat()
        renderTiming()
    }

    /**
     * ∧ ∨ pair: a tap steps 1 Hz; holding repeats, 10 Hz steps after a second and 100 Hz after
     * three (the PC spin box repeats too).
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun stepper(upId: Int, downId: Int, onStep: (Int) -> Unit) {
        listOf(upId to 1, downId to -1).forEach { (id, sign) ->
            val view = v<View>(id) ?: return@forEach
            var repeats = 0
            val repeat = object : Runnable {
                override fun run() {
                    repeats++
                    onStep(sign * when { repeats > 30 -> 100; repeats > 10 -> 10; else -> 1 })
                    ui.postDelayed(this, 100)
                }
            }
            view.setOnTouchListener { _, ev ->
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        repeats = 0
                        onStep(sign)
                        ui.postDelayed(repeat, 400)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> ui.removeCallbacks(repeat)
                }
                true
            }
        }
    }
}
