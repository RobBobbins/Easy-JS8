package com.js8call.example.js8ui

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Swipe layout pages. Every child is one full-size screen, placed side by side; swipe left/right
 * or call [showPage]. All pages stay in one view tree, so the screen code finds every view by id
 * in every layout.
 *
 * A horizontal drag goes to the page underneath when something there can still scroll that way
 * (Band Activity columns, status bar); otherwise it turns the page.
 */
class Js8PagerLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    ViewGroup(context, attrs) {

    var onPageChanged: ((Int) -> Unit)? = null
    var page = 0
        private set

    private var offset = 0f // pixels scrolled, 0 .. (n-1)*width
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var dragging = false
    private var velocity: VelocityTracker? = null
    private var animator: ValueAnimator? = null

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        val cw = MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY)
        val ch = MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
        for (i in 0 until childCount) getChildAt(i).measure(cw, ch)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = r - l
        for (i in 0 until childCount) getChildAt(i).layout(i * w, 0, (i + 1) * w, b - t)
        offset = page * w.toFloat()
        applyOffset()
    }

    private fun applyOffset() {
        scrollTo(offset.roundToInt(), 0)
    }

    fun showPage(index: Int, animate: Boolean = true) {
        val target = index.coerceIn(0, (childCount - 1).coerceAtLeast(0))
        val changed = target != page
        page = target
        animator?.cancel()
        val end = target * width.toFloat()
        if (!animate || width == 0) {
            offset = end; applyOffset()
        } else {
            animator = ValueAnimator.ofFloat(offset, end).apply {
                duration = 220
                interpolator = DecelerateInterpolator()
                addUpdateListener { offset = it.animatedValue as Float; applyOffset() }
                start()
            }
        }
        if (changed) onPageChanged?.invoke(page)
    }

    /** True if some view under (x, y) on the current page can scroll horizontally in direction dir. */
    private fun canChildScroll(v: View, x: Float, y: Float, dir: Int): Boolean {
        if (v is ViewGroup) {
            for (i in v.childCount - 1 downTo 0) {
                val c = v.getChildAt(i)
                if (c.visibility != View.VISIBLE) continue
                val cx = x + v.scrollX - c.left
                val cy = y + v.scrollY - c.top
                if (cx >= 0 && cy >= 0 && cx < c.width && cy < c.height && canChildScroll(c, cx, cy, dir)) return true
            }
        }
        return v.canScrollHorizontally(dir)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x; downY = ev.y; lastX = ev.x; dragging = false
                animator?.cancel()
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (!dragging && abs(dx) > slop && abs(dx) > abs(dy) * 1.5f) {
                    val pageView = getChildAt(page) ?: return false
                    val dir = if (dx < 0) 1 else -1
                    if (!canChildScroll(pageView, ev.x, ev.y, dir)) {
                        dragging = true
                        lastX = ev.x
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                }
            }
        }
        return dragging
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (velocity == null) velocity = VelocityTracker.obtain()
        velocity?.addMovement(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> { lastX = ev.x; return true }
            MotionEvent.ACTION_MOVE -> {
                val max = (childCount - 1) * width.toFloat()
                offset = (offset - (ev.x - lastX)).coerceIn(0f, max)
                lastX = ev.x
                applyOffset()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocity?.computeCurrentVelocity(1000)
                val vx = velocity?.xVelocity ?: 0f
                velocity?.recycle(); velocity = null
                dragging = false
                val fling = 1000 * resources.displayMetrics.density / 2
                val target = when {
                    vx < -fling -> page + 1
                    vx > fling -> page - 1
                    else -> (offset / width).roundToInt()
                }
                showPage(target)
            }
        }
        return true
    }
}
