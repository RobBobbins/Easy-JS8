package com.js8call.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

/**
 * Custom view that displays a scrolling waterfall visualization of spectrum data.
 * Emulates the JS8Call waterfall display algorithm.
 */
class WaterfallView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Offset dial as on the PC (CPlotter::drawDials): 2 px red outline, white alpha 75
    // Dense4Pattern (50% checker) fill, left edge at the offset, as wide as the mode.
    // In screen pixels like the PC's 2 px pen: a 50 Hz dial is only ~4 dp wide on a phone
    // (0-3000 Hz across the waterfall), so a 2 dp pen would fill it solid red.
    private val dialOutline = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 2f
        strokeJoin = Paint.Join.MITER
    }
    private val dialFill = Paint().apply {
        val checker = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply {
            setPixel(0, 0, Color.argb(75, 255, 255, 255))
            setPixel(1, 1, Color.argb(75, 255, 255, 255))
        }
        shader = BitmapShader(checker, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }
    private val srcRect = Rect()
    private val dstRect = Rect()

    private var renderer: WaterfallRenderer? = null

    // TX offset tracking (PC: no lower limit above 0 Hz)
    var txOffsetHz: Float = 1500f
        set(value) {
            field = value.coerceAtLeast(0f)
            postInvalidate()
        }
    /** Width of the offset dial: the bandwidth of the current mode (Normal 50 Hz). */
    var bandwidthHz: Float = 50f
        set(value) {
            field = value
            postInvalidate()
        }
    /** Filter (WideGraph Control tab, CPlotter::drawFilter): outside center +- width / 2 is darkened. */
    var filterEnabled = false
        set(value) { field = value; postInvalidate() }
    var filterCenterHz = 1500f
        set(value) { field = value; postInvalidate() }
    var filterWidthHz = 2000f
        set(value) { field = value; postInvalidate() }
    /** PC Display tab > Filter > Opacity, 50 % by default. */
    var filterOpacityPercent = 50
        set(value) { field = value; postInvalidate() }
    private val filterShade = Paint()
    private val filterEdge = Paint().apply { color = Color.YELLOW; strokeWidth = 1f }

    // Callback for a tap: the frequency under the finger. The owner applies it.
    var onOffsetChanged: ((Float) -> Unit)? = null

    /** Hz at the left edge (Waterfall Controls > Display > Start). */
    fun startHz(): Float = renderer?.startHz() ?: 0f

    /** Hz across one screen pixel (Bins Per Pixel). */
    fun hzPerPixel(): Float {
        if (width <= 0) return 0f
        return renderer?.hzPerPixel() ?: (3000f / width)
    }

    /** Waterfall Controls > Display: new values; the frequency scale follows via [onDisplayChanged]. */
    fun setDisplay(value: WaterfallRenderer.Display) {
        renderer?.setDisplay(value)
        onDisplayChanged?.invoke()
        invalidate()
    }

    fun waterfallDisplay(): WaterfallRenderer.Display? = renderer?.getDisplay()

    var onDisplayChanged: (() -> Unit)? = null

    /** Current speed's cycle, for the Cumulative spectrum. */
    fun setCyclePeriod(seconds: Int) {
        renderer?.setCyclePeriod(seconds)
    }

    /** Display Decode Attempts (UI_Constructor::processDecodeEvent). */
    fun drawDecodeLine(color: Int, fa: Float, fb: Float) {
        renderer?.drawDecodeLine(color, fa, fb)
    }

    fun drawHorizontalLine(color: Int, xDp: Int, widthDp: Int) {
        renderer?.drawHorizontalLine(color, xDp, widthDp)
    }

    init {
        isClickable = true
    }

    fun bindRenderer(renderer: WaterfallRenderer?) {
        this.renderer?.setInvalidateCallback(null)
        this.renderer = renderer
        this.renderer?.setInvalidateCallback { postInvalidateOnAnimation() }
        if (renderer != null && width > 0 && height > 0) {
            renderer.setSize(width, height)
        }
        postInvalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        renderer?.setSize(w, h)
    }

    fun clear() {
        renderer?.clear()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val bitmap = renderer?.getBitmap()
        if (bitmap == null) {
            canvas.drawColor(Color.BLACK)
        } else {
            // Draw the waterfall bitmap scaled to fill the view
            srcRect.set(0, 0, bitmap.width, bitmap.height)
            dstRect.set(0, 0, width, height)
            canvas.drawBitmap(bitmap, srcRect, dstRect, paint)
        }

        val hzPerPx = hzPerPixel()
        // Filter: black at the opacity outside the band, a yellow line at each edge
        if (filterEnabled && filterWidthHz > 0f && hzPerPx > 0f) {
            filterShade.color = Color.argb((255 * filterOpacityPercent / 100).coerceIn(0, 255), 0, 0, 0)
            val start = ((filterCenterHz - filterWidthHz / 2f - startHz()) / hzPerPx).coerceIn(0f, width.toFloat())
            val end = ((filterCenterHz + filterWidthHz / 2f - startHz()) / hzPerPx).coerceIn(0f, width.toFloat())
            if (start > 0f) {
                canvas.drawRect(0f, 0f, start, height.toFloat(), filterShade)
                canvas.drawLine(start, 1f, start, height.toFloat(), filterEdge)
            }
            if (end < width) {
                canvas.drawRect(end, 0f, width.toFloat(), height.toFloat(), filterShade)
                canvas.drawLine(end, 1f, end, height.toFloat(), filterEdge)
            }
        }

        // Offset dial (CPlotter::paintEvent / drawDials), also while RX is off
        if (hzPerPx > 0f) {
            val x = ((txOffsetHz - startHz()) / hzPerPx + 0.5f).toInt().coerceIn(0, width).toFloat()
            val w = (bandwidthHz / hzPerPx + 0.5f).toInt().toFloat()
            val inset = 1f // QRect(1, 1, width - 2, height - 2)
            canvas.drawRect(x + inset, inset, x + w - inset, height - inset, dialFill)
            canvas.drawRect(x + inset, inset, x + w - inset, height - inset, dialOutline)
        }
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        when (event.action) {
            android.view.MotionEvent.ACTION_DOWN -> {
                return true
            }
            android.view.MotionEvent.ACTION_UP -> {
                // CPlotter::mouseReleaseEvent: offset = whole Hz under the finger
                val hzPerPx = hzPerPixel()
                if (hzPerPx > 0f) {
                    val xPosition = event.x.coerceIn(0f, width.toFloat())
                    val newOffsetHz = kotlin.math.floor(startHz() + xPosition * hzPerPx)
                    onOffsetChanged?.invoke(newOffsetHz)
                    android.util.Log.d("WaterfallView", "Tap at x=$xPosition: offset $newOffsetHz Hz")
                }
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        renderer?.setInvalidateCallback { postInvalidateOnAnimation() }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        renderer?.setInvalidateCallback(null)
        renderer?.stopDrawing()
    }
}
