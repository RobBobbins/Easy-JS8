package com.js8call.example.js8ui

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.CheckBox
import android.widget.TextView
import com.js8call.example.R
import com.js8call.example.ui.WaterfallView

/**
 * Waterfall Controls > Control tab (JS8_UI/WideGraph.cpp): Offset, QSY + Center, and the filter
 * (Enable Filter, Sync, Center / Width boxes and knobs). Values are kept between starts with the
 * PC's defaults: center 1500, filter on, filter center 1500, width 2000 (Rob, 2026-10-20).
 */
class Js8WaterfallControls(
    private val root: View,
    private val prefs: SharedPreferences,
    private val waterfall: WaterfallView,
    private val offset: () -> Int,
    /** Offset box ∧ ∨: the main window applies it (and refuses while a message waits). */
    private val setOffset: (Int) -> Unit,
    /** QSY: retune so the offset lands on this center. */
    private val qsy: (Int) -> Unit
) {
    companion object {
        const val KEY_CENTER = "js8cfg_wf_center"
        const val KEY_FILTER_ENABLED = "js8cfg_filter_enabled"
        const val KEY_FILTER_CENTER = "js8cfg_filter_center"
        const val KEY_FILTER_WIDTH = "js8cfg_filter_width"

        // WideGraph.ui ranges
        private const val CENTER_MIN = 500
        private const val CENTER_MAX = 5000
        private const val FILTER_CENTER_MIN = 120
        private const val FILTER_CENTER_MAX = 4880
        private const val FILTER_WIDTH_MIN = 120
        private const val FILTER_WIDTH_MAX = 5000
    }

    private val ui = Handler(Looper.getMainLooper())
    private fun <T : View> v(id: Int): T? = root.findViewById(id)

    private var center = prefs.getInt(KEY_CENTER, 1500).coerceIn(CENTER_MIN, CENTER_MAX)
    private var filterOn = prefs.getBoolean(KEY_FILTER_ENABLED, true)
    private var filterCenter = prefs.getInt(KEY_FILTER_CENTER, 1500).coerceIn(FILTER_CENTER_MIN, FILTER_CENTER_MAX)
    private var filterWidth = prefs.getInt(KEY_FILTER_WIDTH, 2000).coerceIn(FILTER_WIDTH_MIN, FILTER_WIDTH_MAX)

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
        renderAll()
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
