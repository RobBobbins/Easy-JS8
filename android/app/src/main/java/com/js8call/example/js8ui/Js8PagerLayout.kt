package com.js8call.example.js8ui

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import kotlin.math.roundToInt

/**
 * Swipe layout pages. Every child is one full-size screen, placed side by side; the five buttons
 * under them call [showPage]. All pages stay in one view tree, so the screen code finds every view
 * by id in every layout.
 */
class Js8PagerLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    ViewGroup(context, attrs) {

    var onPageChanged: ((Int) -> Unit)? = null
    var page = 0
        private set

    private var offset = 0f // pixels scrolled, 0 .. (n-1)*width
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
}
