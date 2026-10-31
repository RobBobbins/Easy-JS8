package com.js8call.example.js8ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.widget.NestedScrollView
import com.js8call.example.R
import kotlin.math.roundToInt

/**
 * The line between two windows is its handle (Rob, 2026-10-22: "not corners, the line"): a touch
 * strip [STRIP_DP] wide along the whole line, a thin bar drawn in the gap. A one-finger drag on it
 * reports the finger's movement in screen px; [onEnd] when the finger lifts.
 * [sideways] = an upright line, dragged left / right; otherwise a level line, dragged up / down.
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class Js8LineHandle(context: Context, private val sideways: Boolean, private val barPx: Float) : View(context) {
    var onMove: (Float) -> Unit = {}
    var onEnd: () -> Unit = {}
    private val bar = Paint().apply { color = Color.parseColor("#8A8A8A") }
    private var last = 0f

    init {
        // Above its neighbours, so a touch in the strip reaches the handle first
        translationZ = 1f
        setOnTouchListener { _, ev ->
            val pos = if (sideways) ev.rawX else ev.rawY
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    last = pos
                    parent?.requestDisallowInterceptTouchEvent(true) // no page scroll / swipe meanwhile
                }
                MotionEvent.ACTION_MOVE -> { onMove(pos - last); last = pos }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> onEnd()
            }
            true
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (sideways) {
            val l = (width - barPx) / 2f
            canvas.drawRect(l, 0f, l + barPx, height.toFloat(), bar)
        } else {
            val t = (height - barPx) / 2f
            canvas.drawRect(0f, t, width.toFloat(), t + barPx, bar)
        }
    }

    companion object {
        const val STRIP_DP = 12f
        const val BAR_DP = 3f
    }
}

/**
 * Upright and Swipe: the lines between windows can be dragged (Rob, 2026-10-22, like PC mode).
 * Sizes are kept in the settings; Settings > View > Reset Window Sizes puts them back.
 */
object Js8LineDividers {
    // Upright (dp)
    private const val UP_CALL = "js8cfg_up_split_call"   // Call Activity width
    private const val UP_LISTS = "js8cfg_up_split_lists" // Band / Call Activity height
    private const val UP_TEXT = "js8cfg_up_split_text"   // Message Window height
    private const val UP_WF = "js8cfg_up_split_wf"       // waterfall height
    // Swipe Activity screen (layout weights)
    private const val SW_BAND = "js8cfg_sw_split_band"
    private const val SW_MID = "js8cfg_sw_split_mid"
    private const val SW_CALL = "js8cfg_sw_split_call"
    private const val SW_TEXT = "js8cfg_sw_split_text"
    private const val SW_TYPING = "js8cfg_sw_split_typing"

    private val KEYS = listOf(UP_CALL, UP_LISTS, UP_TEXT, UP_WF, SW_BAND, SW_MID, SW_CALL, SW_TEXT, SW_TYPING)

    /** Settings > View > Reset Window Sizes: upright and Swipe back to the layout's sizes. */
    fun reset(prefs: SharedPreferences) = prefs.edit().apply { KEYS.forEach { remove(it) } }.apply()

    /** Upright page (fragment_js8_main): four lines. */
    fun setUpUpright(root: View, prefs: SharedPreferences) {
        val scroll = root.findViewById<NestedScrollView?>(R.id.js8_scroll) ?: return
        val page = scroll.getChildAt(0) as? LinearLayout ?: return
        val band = root.findViewById<View>(R.id.js8_band_activity)
        val call = root.findViewById<View>(R.id.js8_call_activity)
        val lists = band.parent as LinearLayout
        val text = root.findViewById<View>(R.id.js8_message_window)
        val typing = root.findViewById<View>(R.id.js8_typing_box)
        val wf = childOf(page, root.findViewById(R.id.js8_waterfall)) ?: return
        val d = root.resources.displayMetrics.density
        fun px(dp: Float) = dp * d

        // Saved sizes
        prefs.getFloat(UP_CALL, -1f).takeIf { it > 0 }?.let { setWidth(call, px(it).roundToInt()) }
        prefs.getFloat(UP_LISTS, -1f).takeIf { it > 0 }?.let { setHeight(lists, px(it).roundToInt()) }
        prefs.getFloat(UP_TEXT, -1f).takeIf { it > 0 }?.let { setHeight(text, px(it).roundToInt()) }
        prefs.getFloat(UP_WF, -1f).takeIf { it > 0 }?.let { setHeight(wf, px(it).roundToInt()) }

        // Band | Call: sideways, Call Activity's width (its left edge follows the finger)
        insertBetween(lists, band, call, sideways = true) { dx ->
            val w = (call.width - dx).coerceIn(px(70f), lists.width - px(100f))
            setWidth(call, w.roundToInt())
        }.onEnd = { prefs.edit().putFloat(UP_CALL, call.width / d).apply() }
        // Under the lists: their height
        insertBetween(page, lists, text, sideways = false) { dy ->
            setHeight(lists, (lists.height + dy).coerceIn(px(80f), px(1200f)).roundToInt())
        }.onEnd = { prefs.edit().putFloat(UP_LISTS, lists.height / d).apply() }
        // Under the Message Window: its height
        insertBetween(page, text, typing, sideways = false) { dy ->
            setHeight(text, (text.height + dy).coerceIn(px(40f), px(1200f)).roundToInt())
        }.onEnd = { prefs.edit().putFloat(UP_TEXT, text.height / d).apply() }
        // Above the waterfall: its top edge follows the finger, the page scrolls so its bottom stays put
        val above = page.getChildAt(page.indexOfChild(wf) - 1)
        insertBetween(page, above, wf, sideways = false) { dy ->
            val h = (wf.height - dy).coerceIn(px(80f), px(1200f)).roundToInt()
            val change = h - wf.height
            setHeight(wf, h)
            scroll.scrollBy(0, change)
        }.onEnd = { prefs.edit().putFloat(UP_WF, wf.height / d).apply() }
    }

    /** Swipe Activity screen (fragment_js8_swipe): three lines, Band | middle | Call and Message Window / Typing Box. */
    fun setUpSwipe(root: View, prefs: SharedPreferences) {
        val band = root.findViewById<View>(R.id.js8_band_activity)
        val call = root.findViewById<View>(R.id.js8_call_activity)
        val text = root.findViewById<View>(R.id.js8_message_window)
        val typing = root.findViewById<View>(R.id.js8_typing_box)
        val row = band.parent as? LinearLayout ?: return
        val mid = text.parent as? LinearLayout ?: return
        if (row.indexOfChild(mid) < 0) return

        prefs.getFloat(SW_BAND, -1f).takeIf { it > 0 }?.let { setWeight(band, it) }
        prefs.getFloat(SW_MID, -1f).takeIf { it > 0 }?.let { setWeight(mid, it) }
        prefs.getFloat(SW_CALL, -1f).takeIf { it > 0 }?.let { setWeight(call, it) }
        prefs.getFloat(SW_TEXT, -1f).takeIf { it > 0 }?.let { setWeight(text, it) }
        prefs.getFloat(SW_TYPING, -1f).takeIf { it > 0 }?.let { setWeight(typing, it) }
        val d = root.resources.displayMetrics.density
        fun saveRow() = prefs.edit().putFloat(SW_BAND, weight(band)).putFloat(SW_MID, weight(mid))
            .putFloat(SW_CALL, weight(call)).apply()

        // Width moves from one side of the line to the other (weights are shares of the row)
        fun trade(a: View, b: View, deltaPx: Float, along: Int) {
            val total = weight(a) + weight(b)
            val size = (if (along == 0) a.width + b.width else a.height + b.height).toFloat()
            if (size <= 0f) return
            val min = 60f * d
            val aSize = ((if (along == 0) a.width else a.height) + deltaPx).coerceIn(min, size - min)
            setWeight(a, total * aSize / size)
            setWeight(b, total - weight(a))
        }
        insertBetween(row, band, mid, sideways = true) { dx -> trade(band, mid, dx, 0) }.onEnd = { saveRow() }
        insertBetween(row, mid, call, sideways = true) { dx -> trade(mid, call, dx, 0) }.onEnd = { saveRow() }
        insertBetween(mid, text, typing, sideways = false) { dy -> trade(text, typing, dy, 1) }.onEnd = {
            prefs.edit().putFloat(SW_TEXT, weight(text)).putFloat(SW_TYPING, weight(typing)).apply()
        }
    }

    /**
     * Puts a handle in the gap between [a] and [b] (children of [parent], [b] right after [a]):
     * the strip is centred on the gap and overlaps both windows, which keep their places.
     */
    private fun insertBetween(parent: LinearLayout, a: View, b: View, sideways: Boolean, onMove: (Float) -> Unit): Js8LineHandle {
        val d = parent.resources.displayMetrics.density
        val strip = (Js8LineHandle.STRIP_DP * d).roundToInt()
        val blp = b.layoutParams as LinearLayout.LayoutParams
        val gap = if (sideways) blp.marginStart else blp.topMargin
        val handle = Js8LineHandle(parent.context, sideways, minOf(Js8LineHandle.BAR_DP * d, gap.toFloat().coerceAtLeast(1f)))
        handle.onMove = onMove
        val before = (strip - gap) / 2          // strip reaches this far into a
        val after = strip - gap - before        // and this far into b
        val lp = if (sideways) LinearLayout.LayoutParams(strip, ViewGroup.LayoutParams.MATCH_PARENT).apply { marginStart = -before }
            else LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, strip).apply { topMargin = -before }
        lp.weight = 0f
        parent.addView(handle, parent.indexOfChild(b), lp)
        if (sideways) { blp.marginStart = -after; blp.leftMargin = -after } else blp.topMargin = -after
        b.layoutParams = blp
        return handle
    }

    private fun childOf(page: ViewGroup, v: View?): View? {
        var cur: View? = v
        while (cur != null && cur.parent !== page) cur = cur.parent as? View
        return cur
    }

    private fun setWidth(v: View, w: Int) { v.layoutParams = v.layoutParams.apply { width = w } }
    private fun setHeight(v: View, h: Int) { v.layoutParams = v.layoutParams.apply { height = h } }
    private fun weight(v: View) = (v.layoutParams as LinearLayout.LayoutParams).weight
    private fun setWeight(v: View, w: Float) { v.layoutParams = (v.layoutParams as LinearLayout.LayoutParams).apply { weight = w } }
}
