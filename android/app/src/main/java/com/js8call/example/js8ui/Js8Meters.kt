package com.js8call.example.js8ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.js8call.example.R

private fun View.dp(v: Float) = v * resources.displayMetrics.density

/** Audio input level meter under the CAT button: 0–90 dB scale with a green bar (desktop SignalMeter). */
class Js8LevelMeterView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    View(context, attrs) {

    /** Current level in dB, 0..90. */
    private var levelDb = 0f
    /** PC SignalMeter: the last 10 readings; the highest is the peak hold mark. */
    private val readings = ArrayDeque<Float>()
    private var peak = 0f

    fun setLevel(value: Float) {
        levelDb = value.coerceIn(0f, 90f)
        readings.addLast(levelDb)
        if (readings.size > 10) readings.removeFirst()
        peak = readings.maxOrNull() ?: 0f
        invalidate()
    }

    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = resources.getDimension(R.dimen.js8_text)
        typeface = ResourcesCompat.getFont(context, R.font.selawik)
        textAlign = Paint.Align.RIGHT
    }
    private val line = Paint().apply { color = Color.WHITE; strokeWidth = dp(1f) }
    private val green = ContextCompat.getColor(context, R.color.js8_green)
    private val bar = Paint()
    private val peakMark = android.graphics.Path()

    override fun onDraw(canvas: Canvas) {
        // Laid out for an 87 dp column; a narrower one (upright, 58 dp) scales the scale, bar and
        // label positions with it, and the text a little (85 %)
        val s = (width / dp(87f)).coerceAtMost(1f)
        text.textSize = resources.getDimension(R.dimen.js8_text) * (if (s < 1f) 0.85f else 1f)
        val top = dp(12f)
        val bottom = height - dp(30f)
        val axisX = dp(50f) * s
        val span = bottom - top
        canvas.drawLine(axisX, top, axisX, bottom, line)
        for (db in 0..90 step 10) {
            val y = bottom - span * db / 90f
            val major = db % 20 == 10
            canvas.drawLine(axisX - dp(if (major) 6f else 3f) * s, y, axisX, y, line)
            if (major) canvas.drawText("$db", axisX - dp(8f) * s, y + text.textSize * 0.35f, text)
        }
        canvas.drawText("${levelDb.toInt()} dB", axisX + dp(18f) * s, height - dp(8f), text)
        // PC Meter::paintEvent: red above 85, yellow when the peak is below 15, otherwise green
        bar.color = when {
            levelDb > 85f -> Color.RED
            peak < 15f -> Color.YELLOW
            else -> green
        }
        val left = axisX + dp(6f) * s
        val right = axisX + dp(22f) * s
        if (levelDb > 0f) canvas.drawRect(left, bottom - span * levelDb / 90f, right, bottom, bar)
        if (peak > 0f) {
            val y = bottom - span * peak / 90f
            peakMark.reset()
            peakMark.moveTo(right, y - dp(4f))
            peakMark.lineTo(right, y + dp(4f))
            peakMark.lineTo(left, y)
            peakMark.close()
            canvas.drawPath(peakMark, line)
        }
    }
}

/** Frequency ruler above the waterfall: 100 Hz ticks, labels every 100 Hz, colored band strip at the bottom. */
class Js8RulerView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    View(context, attrs) {

    var startHz = 0f
        set(value) { field = value; invalidate() }
    var hzPerPixel = 3000f / 1000f
        set(value) { field = value; invalidate() }
    var dialHz = 0L
        set(value) { field = value; invalidate() }
    /** Filter band, shaded outside as on the waterfall; null = filter off. */
    var filterHz: ClosedFloatingPointRange<Float>? = null
        set(value) { field = value; invalidate() }
    var filterOpacity = 0
        set(value) { field = value; invalidate() }
    /** Tap: the frequency under the finger (on the PC the scale is part of the waterfall). */
    var onOffsetChanged: ((Float) -> Unit)? = null

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> return true
            android.view.MotionEvent.ACTION_UP -> {
                val x = event.x.coerceIn(0f, width.toFloat())
                onOffsetChanged?.invoke(kotlin.math.floor(startHz + x * hzPerPixel))
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean = super.performClick()

    private val bg = Paint().apply { color = ContextCompat.getColor(context, R.color.js8_ruler_bg) }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.js8_ruler_text)
        textSize = resources.getDimension(R.dimen.js8_text)
        typeface = ResourcesCompat.getFont(context, R.font.selawik)
        textAlign = Paint.Align.CENTER
    }
    private val tick = Paint().apply { color = Color.BLACK; strokeWidth = dp(1f) }
    private val gray = Paint().apply { color = android.graphics.Color.rgb(149, 165, 166) }
    private val yellow = Paint().apply { color = ContextCompat.getColor(context, R.color.js8_ruler_band_yellow) }
    private val green = Paint().apply { color = ContextCompat.getColor(context, R.color.js8_ruler_band_green) }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val strip = dp(4f)
        canvas.drawRect(0f, 0f, w, h, bg)
        val endHz = startHz + w * hzPerPixel
        // PC JS8 sub-bands: gray 0-4000 Hz, yellow 500-3000 Hz, green 1000-2500 Hz
        fun band(fromHz: Float, toHz: Float, paint: Paint) = canvas.drawRect(
            ((fromHz - startHz) / hzPerPixel).coerceIn(0f, w), h - strip, ((toHz - startHz) / hzPerPixel).coerceIn(0f, w), h, paint)
        band(0f, 4000f, gray)
        band(500f, 3000f, yellow)
        band(1000f, 2500f, green)
        // Label every 100 Hz like the desktop when there is room (about 60 dp per label),
        // otherwise every 200/250/500/1000 Hz; ticks every quarter of a label step.
        val labelStep = listOf(100, 200, 250, 500, 1000).first { it / hzPerPixel >= dp(60f) || it == 1000 }
        val tickStep = labelStep / 4f
        var hz = kotlin.math.ceil(startHz / tickStep) * tickStep
        while (hz <= endHz) {
            val x = (hz - startHz) / hzPerPixel
            val isLabel = hz.toInt() % labelStep == 0
            val tickLen = if (isLabel) dp(9f) else if ((hz / tickStep).toInt() % 2 == 0) dp(6f) else dp(3f)
            canvas.drawLine(x, h - strip - tickLen, x, h - strip, tick)
            val label = "${hz.toInt()}"
            val half = text.measureText(label) / 2f
            if (isLabel && x - half >= 0f && x + half <= w) canvas.drawText(label, x, h - strip - dp(12f), text)
            hz += tickStep
        }
        // CPlotter::drawMetrics: orange WSPR mark on 30 m, clamped to the edge it lies beyond
        if (dialHz in 10_130_000L..10_150_000L) {
            val from = ((10_140_100L - dialHz) - startHz) / hzPerPixel
            val x1 = from.coerceIn(0f, w)
            val x2 = (from + 200f / hzPerPixel).coerceIn(0f, w)
            canvas.drawRect(x1, h - strip, x2, h, wsprBar)
            canvas.drawText("WSPR", (x1 + x2) / 2f, h - strip - dp(1f), wsprText)
        }
        // CPlotter::paintEvent: the filter pixmaps start at the top of the scale
        filterHz?.let { f ->
            filterShade.color = Color.argb((255 * filterOpacity / 100).coerceIn(0, 255), 0, 0, 0)
            val a = ((f.start - startHz) / hzPerPixel).coerceIn(0f, w)
            val b = ((f.endInclusive - startHz) / hzPerPixel).coerceIn(0f, w)
            if (a > 0f) canvas.drawRect(0f, 0f, a, h, filterShade)
            if (b < w) canvas.drawRect(b, 0f, w, h, filterShade)
        }
    }

    private val wsprBar = Paint().apply { color = Color.rgb(230, 126, 34) }
    private val wsprText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(230, 126, 34)
        textSize = resources.getDimension(R.dimen.js8_text)
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }
    private val filterShade = Paint()
}

/** Cycle progress bar at the end of the Status Bar: white track, #A5CDFF fill, "n/15" text. */
class Js8CycleProgressView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    View(context, attrs) {

    var seconds = 0
        set(value) { field = value; invalidate() }
    var period = 15
        set(value) { field = value; invalidate() }

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.js8_slider_fill) }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = resources.getDimension(R.dimen.js8_text) * 0.85f
        typeface = ResourcesCompat.getFont(context, R.font.selawik)
        textAlign = Paint.Align.CENTER
    }

    private val trackPath = android.graphics.Path()

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        // PC progress_bar_stylesheet: rounded ends on the white track and the blue fill (5 px radius on 14 px)
        val r = h * 5f / 14f
        trackPath.reset()
        trackPath.addRoundRect(0f, 0f, w, h, r, r, android.graphics.Path.Direction.CW)
        canvas.drawPath(trackPath, track)
        val fw = w * seconds / period.coerceAtLeast(1)
        if (fw > 0f) {
            canvas.save()
            canvas.clipPath(trackPath)
            canvas.drawRoundRect(0f, 0f, fw, h, r, r, fill)
            canvas.restore()
        }
        canvas.drawText("$seconds/$period", w / 2f, h / 2f + text.textSize * 0.35f, text)
    }
}

private const val MAX = 450

/** Vertical gain slider right of the Waterfall Controls: green value box on top, blue track, ticks both sides. */
class Js8GainSliderView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    View(context, attrs) {

    /** PC outAttenuation, 0..450 (tenths of a dB). */
    var value = 0
        set(v) { field = v.coerceIn(0, MAX); invalidate() }
    var onValueChanged: ((Int) -> Unit)? = null

    private val trackTop get() = dp(30f)
    private val trackBottom get() = height - dp(10f)

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                value = ((event.y - trackTop) / (trackBottom - trackTop) * MAX).toInt()
                onValueChanged?.invoke(value)
                return true
            }
            android.view.MotionEvent.ACTION_UP -> { performClick(); return true }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean = super.performClick()

    private val green = Paint().apply { color = ContextCompat.getColor(context, R.color.js8_green) }
    private val blue = Paint().apply { color = ContextCompat.getColor(context, R.color.js8_blue) }
    private val tick = Paint().apply { color = ContextCompat.getColor(context, R.color.js8_frame_text); strokeWidth = dp(1f) }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = resources.getDimension(R.dimen.js8_text)
        typeface = ResourcesCompat.getFont(context, R.font.selawik)
        textAlign = Paint.Align.CENTER
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val box = dp(20f)
        val cx = w / 2f
        canvas.drawRect(cx - dp(21f), 0f, cx + dp(21f), box, green)
        canvas.drawText("$value", cx, box / 2f + text.textSize * 0.35f, text)
        val top = box + dp(10f)
        val bottom = height - dp(10f)
        canvas.drawRect(cx - dp(3f), top, cx + dp(3f), bottom, blue)
        val handle = top + (bottom - top) * value / MAX
        canvas.drawRect(cx - dp(10f), handle - dp(3f), cx + dp(10f), handle + dp(3f), green)
        val n = 9
        for (i in 0..n) {
            val y = top + (bottom - top) * i / n
            canvas.drawLine(dp(2f), y, dp(9f), y, tick)
            canvas.drawLine(w - dp(9f), y, w - dp(2f), y, tick)
        }
    }
}
