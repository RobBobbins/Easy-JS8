package com.js8call.example.js8ui

import android.content.SharedPreferences
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.js8call.example.R
import kotlin.math.roundToInt

/**
 * PC mode: the PC window's splitters (mainwindow.ui mainSplitter, textHorizontalSplitter,
 * textVerticalSplitter, WideGraph.ui splitter). Each line between two windows is its handle
 * (Rob, 2026-10-22: "not corners, the line"; were grab spots on the corners since 2026-10-20):
 * - Band Activity | Message Window and Message Window | Call Activity: sideways;
 * - Message Window / Typing Box: up / down;
 * - the line under the lists: up / down moves the buttons row and the waterfall's top edge;
 * - waterfall | Waterfall Controls: sideways trades width between them.
 * Positions are in PC dp (the 1077 x 714 window), kept in the settings like the PC keeps its
 * window state; View > Reset Window Sizes puts them back.
 */
class Js8PcSplitters(private val window: FrameLayout, private val prefs: SharedPreferences) {

    companion object {
        private const val KEY_BAND = "js8cfg_pc_split_band"   // Band Activity right edge
        private const val KEY_CALL = "js8cfg_pc_split_call"   // Call Activity left edge
        private const val KEY_TEXT = "js8cfg_pc_split_text"   // Message Window bottom edge
        private const val KEY_MAIN = "js8cfg_pc_split_main"   // lists / text bottom edge
        private const val KEY_WF = "js8cfg_pc_split_wf"       // Waterfall Controls left edge

        private const val SHOW_FREQ = "js8cfg_show_frequency_clock"
        private const val SHOW_BAND = "js8cfg_show_band_activity"
        private const val SHOW_CALL = "js8cfg_show_call_activity"
        private const val SHOW_WATERFALL = "js8cfg_show_waterfall"
        private const val SHOW_CONTROLS = "js8cfg_show_waterfall_controls"
        private const val SHOW_STATUS = "js8cfg_show_statusbar"
        /** PC View menu actionShow_*: setting and menu text. */
        val PANELS = listOf(SHOW_FREQ to "Show Frequency & Clock", SHOW_BAND to "Show Band Activity", SHOW_CALL to "Show Call Activity",
            SHOW_WATERFALL to "Show Waterfall", SHOW_CONTROLS to "Show Waterfall Controls", SHOW_STATUS to "Show Statusbar")

        // Generated layout (gen_pc_layout.py), PC dp
        private const val LEFT = 15f
        private const val RIGHT = 1062f
        private const val TOP = 140f
        private const val WATERFALL_BOTTOM = 672f
        private const val TOP_NO_HEADER = 45f          // Show Frequency & Clock off: the menu bar's bottom
        private const val BOTTOM_NO_STATUSBAR = 704f   // Show Statusbar off: the status bar's bottom
        // First setting and Reset Window Sizes: measured on Rob's layout (2026-10-20). The generated
        // layout has the PC capture's 441 / 759 / 277 / 351.
        private const val BAND_RIGHT = 375f
        private const val CALL_LEFT = 759f
        private const val TEXT_BOTTOM = 374f
        private const val MAIN_BOTTOM = 441f
        // Waterfall Controls 340 dp wide at first: the Control tab filter row fits (Rob's capture)
        private const val CONTROLS_RIGHT = 997f
        private const val CONTROLS_LEFT = CONTROLS_RIGHT - 340f
        private const val GAP_WF = 8f        // waterfall | Waterfall Controls
        private const val CAT_W = 87f        // CAT button / level meter column left of the waterfall
        private const val GAP_BAND = 8f      // Band Activity | Message Window
        private const val GAP_CALL = 6f      // Message Window | Call Activity
        private const val GAP_TEXT = 7f      // Message Window / Typing Box
        private const val BUTTONS_GAP = 9f   // lists bottom to buttons top
        private const val BUTTONS_H = 25f
        private const val WATERFALL_GAP = 10f
        private const val STATUS_RIGHT = 500f // STATUS button right edge
        private const val SAVED_LEFT = 531f   // Saved button left edge

        // Smallest sizes. PC: the lists / text area at least 75 px (50 dp) tall, nothing collapses.
        private const val MIN_LIST_W = 100f
        private const val MIN_MID_W = 120f
        private const val MIN_TEXT_H = 30f
        private const val MIN_MAIN_H = 75f
        private const val MIN_WATERFALL_H = 40f
        // PC WideGraph.ui: waterfall at least 400 px (267 dp), controls at least 100 px (67 dp)
        private const val MIN_WATERFALL_W = 267f
        private const val MIN_CONTROLS_W = 67f

        /** Touch strip along each line, PC dp (about 12 dp on the phone when the window is fitted). */
        private const val STRIP = 24f
        /** Bar drawn along each line, PC dp. */
        private const val BAR = 4f
    }

    private val density = window.resources.displayMetrics.density
    private fun v(id: Int): View = window.findViewById(id)

    private var band = prefs.getFloat(KEY_BAND, BAND_RIGHT)
    private var call = prefs.getFloat(KEY_CALL, CALL_LEFT)
    private var text = prefs.getFloat(KEY_TEXT, TEXT_BOTTOM)
    private var main = prefs.getFloat(KEY_MAIN, MAIN_BOTTOM)
    private var wf = prefs.getFloat(KEY_WF, CONTROLS_LEFT)

    private val bandLine = line(true) { band += it }
    private val callLine = line(true) { call += it }
    private val textLine = line(false) { text += it }
    private val mainLine = line(false) { main += it; text += it }
    private val wfLine = line(true) { wf += it }

    init {
        listOf(bandLine, callLine, textLine, mainLine, wfLine).forEach { window.addView(it) }
        apply()
    }

    /** View > Reset Window Sizes (PC actionReset_Window_Sizes). */
    /** The STATUS / Saved gap moves right while the HB button shows (Js8MainFragment). */
    private var statusRight = STATUS_RIGHT
    private var savedLeft = SAVED_LEFT

    fun setButtonGap(statusRight: Float?, savedLeft: Float?) {
        this.statusRight = statusRight ?: STATUS_RIGHT
        this.savedLeft = savedLeft ?: SAVED_LEFT
        apply()
    }

    /** View > Show ... changed. */
    fun refresh() = apply()

    fun reset() {
        band = BAND_RIGHT; call = CALL_LEFT; text = TEXT_BOTTOM; main = MAIN_BOTTOM; wf = CONTROLS_LEFT
        prefs.edit().remove(KEY_BAND).remove(KEY_CALL).remove(KEY_TEXT).remove(KEY_MAIN).remove(KEY_WF).apply()
        apply()
    }

    private fun clamp() {
        main = main.coerceIn(TOP + MIN_MAIN_H, WATERFALL_BOTTOM - MIN_WATERFALL_H - WATERFALL_GAP - BUTTONS_H - BUTTONS_GAP)
        band = band.coerceIn(LEFT + MIN_LIST_W, RIGHT - MIN_LIST_W - GAP_CALL - MIN_MID_W - GAP_BAND)
        call = call.coerceIn(band + GAP_BAND + MIN_MID_W + GAP_CALL, RIGHT - MIN_LIST_W)
        text = text.coerceIn(TOP + MIN_TEXT_H, main - MIN_TEXT_H - GAP_TEXT)
        wf = wf.coerceIn(LEFT + CAT_W + MIN_WATERFALL_W + GAP_WF, CONTROLS_RIGHT - MIN_CONTROLS_W)
    }

    private fun apply() {
        clamp()
        fun shown(key: String) = prefs.getBoolean(key, true)
        fun show(view: View, on: Boolean) { view.visibility = if (on) View.VISIBLE else View.GONE }
        val showBand = shown(SHOW_BAND)
        val showCall = shown(SHOW_CALL)
        val showWaterfall = shown(SHOW_WATERFALL)
        val showControls = showWaterfall && shown(SHOW_CONTROLS)
        show(v(R.id.js8_pc_header), shown(SHOW_FREQ))
        show(v(R.id.js8_pc_statusbar), shown(SHOW_STATUS))
        show(v(R.id.js8_band_activity), showBand)
        show(v(R.id.js8_call_activity), showCall)
        show(v(R.id.js8_pc_waterfall), showWaterfall)
        show(v(R.id.js8_pc_waterfall_controls), showControls)
        show(v(R.id.js8_gain_slider), showWaterfall)
        show(bandLine, showBand)
        show(callLine, showCall)
        show(mainLine, showWaterfall)
        show(wfLine, showControls)
        // The saved splitter positions stay; a hidden panel's space goes to its neighbours (PC setSizes)
        val top = if (shown(SHOW_FREQ)) TOP else TOP_NO_HEADER
        val bottom = if (shown(SHOW_STATUS)) WATERFALL_BOTTOM else BOTTOM_NO_STATUSBAR
        val main = if (showWaterfall) this.main else bottom - BUTTONS_H - BUTTONS_GAP
        val text = this.text + (main - this.main)
        val wf = if (showControls) this.wf else CONTROLS_RIGHT + GAP_WF
        val midLeft = if (showBand) band + GAP_BAND else LEFT
        val midRight = if (showCall) call - GAP_CALL else RIGHT
        place(v(R.id.js8_band_activity), LEFT, top, band - LEFT, main - top)
        place(v(R.id.js8_message_window), midLeft, top, midRight - midLeft, text - top)
        place(v(R.id.js8_typing_box), midLeft, text + GAP_TEXT, midRight - midLeft, main - text - GAP_TEXT)
        place(v(R.id.js8_call_activity), call, top, RIGHT - call, main - top)
        val buttonsTop = main + BUTTONS_GAP
        top(v(R.id.js8_pc_buttons), buttonsTop)
        val wfTop = buttonsTop + BUTTONS_H + WATERFALL_GAP
        listOf(R.id.js8_pc_waterfall, R.id.js8_pc_waterfall_controls, R.id.js8_gain_slider).forEach {
            val view = v(it)
            val lp = view.layoutParams as ViewGroup.MarginLayoutParams
            lp.topMargin = px(wfTop)
            lp.height = px(bottom - wfTop)
            view.layoutParams = lp
        }
        place(v(R.id.js8_pc_waterfall), LEFT, wfTop, wf - GAP_WF - LEFT, bottom - wfTop)
        place(v(R.id.js8_pc_waterfall_controls), wf, wfTop, CONTROLS_RIGHT - wf, bottom - wfTop)
        // Touch strips centred on the gaps, along the whole line
        spot(bandLine, band + GAP_BAND / 2, (top + main) / 2, STRIP, main - top)
        spot(callLine, call - GAP_CALL / 2, (top + main) / 2, STRIP, main - top)
        spot(textLine, (midLeft + midRight) / 2, text + GAP_TEXT / 2, midRight - midLeft, STRIP)
        // Under the lists: the strip stops at the buttons' top edge, so the buttons keep their taps
        place(mainLine, LEFT, buttonsTop - STRIP, RIGHT - LEFT, STRIP)
        spot(wfLine, wf - GAP_WF / 2, (wfTop + bottom) / 2, STRIP, bottom - wfTop)
    }

    private fun save() = prefs.edit().putFloat(KEY_BAND, band).putFloat(KEY_CALL, call)
        .putFloat(KEY_TEXT, text).putFloat(KEY_MAIN, main).putFloat(KEY_WF, wf).apply()

    private fun px(dp: Float) = (dp * density).roundToInt()

    private fun place(view: View, x: Float, y: Float, w: Float, h: Float) {
        val lp = view.layoutParams as ViewGroup.MarginLayoutParams
        lp.marginStart = px(x); lp.leftMargin = px(x); lp.topMargin = px(y)
        lp.width = px(w); lp.height = px(h)
        view.layoutParams = lp
    }

    private fun top(view: View, y: Float) {
        val lp = view.layoutParams as ViewGroup.MarginLayoutParams
        lp.topMargin = px(y)
        view.layoutParams = lp
    }

    private fun spot(view: View, cx: Float, cy: Float, w: Float, h: Float) =
        place(view, cx - w / 2, cy - h / 2, w, h)

    /** A line's handle; a one-finger drag along it moves its splitter (finger px -> PC dp). */
    private fun line(sideways: Boolean, onMove: (Float) -> Unit) =
        Js8LineHandle(window.context, sideways, BAR * density).apply {
            layoutParams = FrameLayout.LayoutParams(1, 1)
            // The PC window is shown scaled (pinch zoom)
            this.onMove = { delta -> onMove(delta / (window.scaleX * density)); apply() }
            onEnd = { save() }
        }
}
