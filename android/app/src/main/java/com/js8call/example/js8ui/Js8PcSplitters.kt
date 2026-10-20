package com.js8call.example.js8ui

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.js8call.example.R
import kotlin.math.roundToInt

/**
 * PC mode: the PC window's splitters (mainwindow.ui mainSplitter, textHorizontalSplitter,
 * textVerticalSplitter) as three grab spots (Rob, 2026-10-20):
 * - left / right corner where the upright lines meet the line under the Message Window: sideways
 *   moves that upright line, up / down moves the Message Window / Typing Box line;
 * - the gap between STATUS and Saved: up / down moves the buttons row and the waterfall's top edge;
 * - the top of the line between the waterfall and Waterfall Controls (WideGraph.ui splitter):
 *   sideways trades width between them.
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

        // Generated layout (gen_pc_layout.py), PC dp
        private const val LEFT = 15f
        private const val RIGHT = 1062f
        private const val TOP = 140f
        private const val WATERFALL_BOTTOM = 672f
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

        /** Grab spot size, PC dp (about 22 dp on the phone when the window is fitted). */
        private const val GRAB = 44f
    }

    private val density = window.resources.displayMetrics.density
    private fun v(id: Int): View = window.findViewById(id)

    private var band = prefs.getFloat(KEY_BAND, BAND_RIGHT)
    private var call = prefs.getFloat(KEY_CALL, CALL_LEFT)
    private var text = prefs.getFloat(KEY_TEXT, TEXT_BOTTOM)
    private var main = prefs.getFloat(KEY_MAIN, MAIN_BOTTOM)
    private var wf = prefs.getFloat(KEY_WF, CONTROLS_LEFT)

    // Marks (PC dp, Rob 2026-10-20): square on the corners, wide on the up / down spot, tall on
    // the sideways spot
    private val leftCorner = grabSpot(10f, 10f) { dx, dy -> band += dx; text += dy }
    private val rightCorner = grabSpot(10f, 10f) { dx, dy -> call += dx; text += dy }
    private val mainSpot = grabSpot(16f, 8f) { _, dy -> main += dy; text += dy }
    private val wfSpot = grabSpot(8f, 16f) { dx, _ -> wf += dx }

    init {
        window.addView(leftCorner)
        window.addView(rightCorner)
        window.addView(mainSpot)
        window.addView(wfSpot)
        apply()
    }

    /** View > Reset Window Sizes (PC actionReset_Window_Sizes). */
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
        val midLeft = band + GAP_BAND
        val midRight = call - GAP_CALL
        place(v(R.id.js8_band_activity), LEFT, TOP, band - LEFT, main - TOP)
        place(v(R.id.js8_message_window), midLeft, TOP, midRight - midLeft, text - TOP)
        place(v(R.id.js8_typing_box), midLeft, text + GAP_TEXT, midRight - midLeft, main - text - GAP_TEXT)
        place(v(R.id.js8_call_activity), call, TOP, RIGHT - call, main - TOP)
        val buttonsTop = main + BUTTONS_GAP
        top(v(R.id.js8_pc_buttons), buttonsTop)
        val wfTop = buttonsTop + BUTTONS_H + WATERFALL_GAP
        listOf(R.id.js8_pc_waterfall, R.id.js8_pc_waterfall_controls, R.id.js8_gain_slider).forEach {
            val view = v(it)
            val lp = view.layoutParams as ViewGroup.MarginLayoutParams
            lp.topMargin = px(wfTop)
            lp.height = px(WATERFALL_BOTTOM - wfTop)
            view.layoutParams = lp
        }
        place(v(R.id.js8_pc_waterfall), LEFT, wfTop, wf - GAP_WF - LEFT, WATERFALL_BOTTOM - wfTop)
        place(v(R.id.js8_pc_waterfall_controls), wf, wfTop, CONTROLS_RIGHT - wf, WATERFALL_BOTTOM - wfTop)
        // Grab spots centred on the corners, in the STATUS / Saved gap and at the top of the
        // waterfall / controls line (level with the frequency scale and the tabs)
        spot(leftCorner, band + GAP_BAND / 2, text + GAP_TEXT / 2, GRAB, GRAB)
        spot(rightCorner, call - GAP_CALL / 2, text + GAP_TEXT / 2, GRAB, GRAB)
        spot(mainSpot, (STATUS_RIGHT + SAVED_LEFT) / 2, buttonsTop + BUTTONS_H / 2, SAVED_LEFT - STATUS_RIGHT - 4f, BUTTONS_H + 16f)
        spot(wfSpot, wf - GAP_WF / 2, wfTop + 14f, 24f, GRAB)
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

    /** Small raised mark in the middle of a grab spot: light top / left edge, dark bottom / right. */
    private class GrabMark(context: android.content.Context, private val markW: Float, private val markH: Float) : View(context) {
        private val d = context.resources.displayMetrics.density
        private val face = android.graphics.Paint().apply { color = android.graphics.Color.parseColor("#5A5A5A") }
        private val light = android.graphics.Paint().apply { color = android.graphics.Color.parseColor("#8A8A8A") }
        private val dark = android.graphics.Paint().apply { color = android.graphics.Color.parseColor("#2A2A2A") }

        override fun onDraw(canvas: android.graphics.Canvas) {
            val w = markW * d
            val h = markH * d
            val l = (width - w) / 2f
            val t = (height - h) / 2f
            val e = 1.5f * d // bevel edge
            canvas.drawRect(l, t, l + w, t + h, dark)
            canvas.drawRect(l, t, l + w - e, t + h - e, light)
            canvas.drawRect(l + e, t + e, l + w - e, t + h - e, face)
        }
    }

    /** A grab spot with its mark; a one-finger drag on it moves splitters (PC dp). */
    @SuppressLint("ClickableViewAccessibility")
    private fun grabSpot(markW: Float, markH: Float, onMove: (Float, Float) -> Unit) = GrabMark(window.context, markW, markH).apply {
        layoutParams = FrameLayout.LayoutParams(1, 1)
        var lastX = 0f
        var lastY = 0f
        setOnTouchListener { _, ev ->
            // The PC window is shown scaled (pinch zoom): finger pixels -> PC dp
            val toDp = 1f / (window.scaleX * density)
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = ev.rawX; lastY = ev.rawY
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> {
                    onMove((ev.rawX - lastX) * toDp, (ev.rawY - lastY) * toDp)
                    lastX = ev.rawX; lastY = ev.rawY
                    apply()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> save()
            }
            true
        }
    }
}
