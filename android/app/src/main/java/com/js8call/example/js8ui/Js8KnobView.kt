package com.js8call.example.js8ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The PC's QDial (WideGraph filter Center / Width knobs): a raised grey knob with a small dot.
 * Like QDial without wrapping, the dot runs over 300 degrees, from 7:30 (minimum) clockwise to
 * 4:30 (maximum). Drag around the knob to turn it.
 */
class Js8KnobView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    var minimum = 0
    var maximum = 100
    var value = 0
        set(v) { field = v.coerceIn(minimum, maximum); invalidate() }
    /** Called while the knob is turned by hand. */
    var onValueChanged: ((Int) -> Unit)? = null

    private val d = resources.displayMetrics.density
    private val face = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.parseColor("#2A2A2A") }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#9A9A9A") }
    private val dotRim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.parseColor("#6A6A6A") }

    /** Degrees clockwise from 12 o'clock: -150 at the minimum, +150 at the maximum. */
    private fun angle() = -150f + 300f * (value - minimum) / (maximum - minimum).coerceAtLeast(1)

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f - 2 * d
        // Lit from the top left, like the PC's knob
        face.shader = RadialGradient(cx - r * 0.35f, cy - r * 0.35f, r * 1.6f,
            Color.parseColor("#E2E2E2"), Color.parseColor("#9C9C9C"), Shader.TileMode.CLAMP)
        rim.strokeWidth = 1f * d
        canvas.drawCircle(cx, cy, r, face)
        canvas.drawCircle(cx, cy, r, rim)
        val a = Math.toRadians(angle().toDouble())
        val dr = r * 0.62f
        val dx = cx + (dr * sin(a)).toFloat()
        val dy = cy - (dr * cos(a)).toFloat()
        dotRim.strokeWidth = 1f * d
        canvas.drawCircle(dx, dy, r * 0.17f, dot)
        canvas.drawCircle(dx, dy, r * 0.17f, dotRim)
    }

    /** Greyed while Enable Filter is off (PC setFilterEnabled). */
    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        alpha = if (enabled) 1f else 0.45f
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> parent?.requestDisallowInterceptTouchEvent(true)
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                // Angle of the finger around the centre, clockwise from 12 o'clock
                var deg = Math.toDegrees(atan2((ev.x - width / 2f).toDouble(), (height / 2f - ev.y).toDouble())).toFloat()
                // The 60 degrees gap at the bottom: snap to the nearer end
                if (deg > 150f) deg = 150f
                if (deg < -150f) deg = -150f
                val v = minimum + ((deg + 150f) / 300f * (maximum - minimum)).toInt()
                if (v != value) {
                    value = v
                    onValueChanged?.invoke(value)
                }
            }
        }
        return true
    }
}
