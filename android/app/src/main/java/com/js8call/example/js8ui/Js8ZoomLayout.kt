package com.js8call.example.js8ui

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.FrameLayout
import kotlin.math.min

/**
 * Holds one child laid out at a fixed design size (the PC window, in dp) and shows it
 * scaled. The child is a real view tree: buttons, lists and text boxes keep working and
 * text is redrawn at each zoom level, so it stays sharp.
 *
 * - zoomable = true (PC mode): starts fitted to the screen; pinch to zoom, drag to move
 *   (one finger on non-interactive areas, or two fingers anywhere), double-tap toggles
 *   between fitted and 2x at the tapped point.
 * - zoomable = false (Swipe layout pages): child is scaled to fill the space, no gestures.
 */
class Js8ZoomLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    var designWidthDp = 1077f
    var designHeightDp = 714f
    var zoomable = true
    var maxZoomFactor = 4f

    private val density = resources.displayMetrics.density
    private var scale = 1f
    private var fitScale = 1f
    private var tx = 0f
    private var ty = 0f
    private var fitted = false

    private var lastFocusX = 0f
    private var lastFocusY = 0f
    private var multiTouch = false

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            zoomAround(scale * d.scaleFactor, d.focusX, d.focusY)
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            tx -= dx; ty -= dy; apply(); return true
        }
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (scale > fitScale * 1.05f) { fitted = false; fit() } else zoomAround(fitScale * 2f, e.x, e.y)
            return true
        }
    })

    init {
        clipToPadding = true
        clipChildren = true
    }

    // Children are moved with translation/scale, which does not clip to this view's edges.
    override fun dispatchDraw(canvas: android.graphics.Canvas) {
        canvas.save()
        canvas.clipRect(0, 0, width, height)
        super.dispatchDraw(canvas)
        canvas.restore()
    }

    private val child: View? get() = if (childCount > 0) getChildAt(0) else null

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        child?.measure(
            MeasureSpec.makeMeasureSpec((designWidthDp * density).toInt(), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec((designHeightDp * density).toInt(), MeasureSpec.EXACTLY)
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        child?.let {
            it.layout(0, 0, it.measuredWidth, it.measuredHeight)
            it.pivotX = 0f
            it.pivotY = 0f
        }
        val cw = designWidthDp * density
        val ch = designHeightDp * density
        fitScale = min(areaW() / cw, areaH() / ch)
        if (!zoomable) {
            // Fill: scale to fit, centered
            scale = fitScale
            tx = paddingLeft + (areaW() - cw * scale) / 2f
            ty = paddingTop + (areaH() - ch * scale) / 2f
            apply()
        } else if (!fitted || changed) {
            fitted = true
            fit()
        }
    }

    private fun areaW() = (width - paddingLeft - paddingRight).toFloat()
    private fun areaH() = (height - paddingTop - paddingBottom).toFloat()

    private fun fit() {
        val cw = designWidthDp * density
        val ch = designHeightDp * density
        scale = fitScale
        tx = paddingLeft + (areaW() - cw * scale) / 2f
        ty = paddingTop + (areaH() - ch * scale) / 2f
        apply()
    }

    private fun zoomAround(newScale: Float, fx: Float, fy: Float) {
        val s = newScale.coerceIn(fitScale, fitScale * maxZoomFactor)
        // keep the content point under the focus where it is
        tx = fx - (fx - tx) * (s / scale)
        ty = fy - (fy - ty) * (s / scale)
        scale = s
        apply()
    }

    private fun apply() {
        val c = child ?: return
        val cw = designWidthDp * density * scale
        val ch = designHeightDp * density * scale
        // clamp: content edges may not come inside the view; center when smaller
        val left = paddingLeft.toFloat()
        val top = paddingTop.toFloat()
        tx = if (cw <= areaW()) left + (areaW() - cw) / 2f else tx.coerceIn(left + areaW() - cw, left)
        ty = if (ch <= areaH()) top + (areaH() - ch) / 2f else ty.coerceIn(top + areaH() - ch, top)
        c.scaleX = scale
        c.scaleY = scale
        c.translationX = tx
        c.translationY = ty
    }

    // Two fingers: always ours (zoom/pan), even over views that block interception (text
    // boxes, lists): the child gets a CANCEL and the rest of the gesture stays here.
    // One finger: the child gets it first; if the child does not take it (background,
    // labels), onTouchEvent pans.
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (!zoomable) return super.dispatchTouchEvent(ev)
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) multiTouch = false
        if (!multiTouch && ev.pointerCount >= 2) {
            multiTouch = true
            val cancel = MotionEvent.obtain(ev).apply { action = MotionEvent.ACTION_CANCEL }
            super.dispatchTouchEvent(cancel)
            cancel.recycle()
        }
        if (multiTouch) {
            // two-finger pan: follow the midpoint of the fingers still down
            val lifted = if (ev.actionMasked == MotionEvent.ACTION_POINTER_UP) ev.actionIndex else -1
            var fx = 0f; var fy = 0f; var n = 0
            for (i in 0 until ev.pointerCount) if (i != lifted) { fx += ev.getX(i); fy += ev.getY(i); n++ }
            if (n > 0) { fx /= n; fy /= n }
            val action = ev.actionMasked
            if (action == MotionEvent.ACTION_MOVE && n > 0) {
                tx += fx - lastFocusX; ty += fy - lastFocusY; apply()
            }
            lastFocusX = fx; lastFocusY = fy
            scaleDetector.onTouchEvent(ev)
            if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) multiTouch = false
            return true
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!zoomable) return super.onTouchEvent(ev)
        scaleDetector.onTouchEvent(ev)
        if (!scaleDetector.isInProgress) gestureDetector.onTouchEvent(ev)
        return true
    }
}
