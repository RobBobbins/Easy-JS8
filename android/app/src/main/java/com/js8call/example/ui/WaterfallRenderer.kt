package com.js8call.example.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Offscreen renderer that maintains waterfall bitmap state across view lifecycles.
 *
 * Draws like the PC's CPlotter / WideGraph / dataSink (JS8Call 3.0.3). Each engine spectrum is
 * first put on the PC's bin grid (1500/2048 Hz, 0-5000 Hz), so the PC's arithmetic applies as is:
 * - waterfall: one PC pixel (one dp) = Bins Per Pixel bins from Start; Avg N spectra averaged per
 *   line; 10*log10, Flatten, then the Gain / Zero scaler (Scaler1D) into the palette; Scroll Speed
 *   PC pixels a second (divided by Avg N), repeating the last line until a new one is ready;
 * - spectrum: the bottom Height % of the plot, Current (green, waterfall line minus its minimum),
 *   Cumulative (cyan, this cycle's average power in dB) or Linear Avg (yellow, flat1 + Smooth),
 *   placed with the spectrum Gain / Zero (Scaler2D);
 * - decode attempts: lines at the top of the waterfall (CPlotter::drawDecodeLine).
 */
class WaterfallRenderer {

    companion object {
        /** CPlotter.cpp FFT_BIN_WIDTH: the PC's bin, which Bins Per Pixel counts. */
        const val PC_BIN_HZ = 1500f / 2048f
        /** commons.h JS8_NSMAX, and dataSink iz = min(NSMAX, 5000 / df3). */
        private const val NSMAX = 6827
        private val IZ = min(NSMAX, (5000f / PC_BIN_HZ).toInt())
        private const val TICK_MS = 33L
        /** dataSink nch: Smooth 1..7 -> smoothing width. */
        private val NCH = intArrayOf(1, 2, 4, 9, 18, 36, 72)

        const val SPECTRUM_CURRENT = 0
        const val SPECTRUM_CUMULATIVE = 1
        const val SPECTRUM_LINEAR_AVG = 2
    }

    /** Waterfall Controls > Display values (WideGraph defaults). */
    data class Display(
        val startHz: Int = 500,
        val binsPerPixel: Int = 2,
        val fps: Int = 4,
        val avg: Int = 1,
        val gain: Int = 0,
        val zero: Int = 0,
        val flatten: Boolean = true,
        val spectrum: Int = SPECTRUM_CURRENT,
        val smooth: Int = 1,
        val percent2D: Int = 0,
        val gain2D: Int = 0,
        val zero2D: Int = 0,
        val colours: IntArray = WaterfallPalettes.fallback(),
        /** Screen pixels per dp (one PC pixel). */
        val density: Float = 1f
    )

    private var waterfallBitmap: Bitmap? = null
    private var waterfallCanvas: Canvas? = null
    private var tempBitmap: Bitmap? = null

    private var width = 0
    private var height = 0

    private var display = Display()

    // PC-grid spectra (dataSink s / ssum / specData)
    private val sNow = FloatArray(NSMAX)
    private val sAccum = FloatArray(NSMAX)
    private var sAccumRuns = 0
    private val sCommitted = FloatArray(NSMAX)
    private var haveCommitted = false
    private val ssum = FloatArray(NSMAX)
    private var ihsym = 0
    private val savg = FloatArray(NSMAX)
    private val slin = FloatArray(NSMAX)
    private var lastCycle = -1L
    private var cyclePeriodSec = 15

    // WF::Sink state since the last frame
    private var summaryReady = false
    private var currentReady = false

    private var latestAtMs = 0L
    private var rowsOwed = 0f
    private var lastTickMs = 0L
    private var running = false

    private var columns = FloatArray(0)
    private var row = IntArray(0)

    private val linePaint = Paint().apply { isAntiAlias = false; style = Paint.Style.STROKE }
    private val spectrumPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE }
    private val spectrumPath = Path()

    private val drawHandler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            val now = SystemClock.uptimeMillis()
            // Stop scrolling once spectra stop coming (RX off)
            if (!haveCommitted || now - latestAtMs > 1500) {
                running = false
                return
            }
            // WideGraph m_drawTimer: loopMs = 1000 / fps * Avg N, one PC pixel a frame
            val d = display
            rowsOwed += (now - lastTickMs) * d.fps * d.density / (1000f * d.avg.coerceAtLeast(1))
            lastTickMs = now
            val rows = rowsOwed.toInt()
            if (rows > 0 && canDraw()) {
                rowsOwed -= rows
                drawFrame(rows.coerceAtMost(waterfallHeight().coerceAtLeast(1)))
            }
            drawHandler.postDelayed(this, TICK_MS)
        }
    }

    private var invalidateCallback: (() -> Unit)? = null

    fun setInvalidateCallback(callback: (() -> Unit)?) {
        invalidateCallback = callback
    }

    fun setDisplay(value: Display) {
        val old = display
        display = value
        if (old.percent2D != value.percent2D) {
            // CPlotter::resize: new waterfall / spectrum split; clear what changed role
            val top = min(waterfallHeight(old), waterfallHeight(value))
            waterfallCanvas?.let { c ->
                Paint().apply { color = Color.BLACK }.let { p -> c.drawRect(0f, top.toFloat(), width.toFloat(), height.toFloat(), p) }
            }
            invalidateCallback?.invoke()
        }
    }

    fun getDisplay(): Display = display

    /** Current speed's cycle (Cumulative restarts each cycle, dataSink lastCycle). */
    fun setCyclePeriod(seconds: Int) {
        cyclePeriodSec = seconds.coerceAtLeast(1)
    }

    /** Hz at the left edge (Start). */
    fun startHz(): Float = display.startHz.toFloat()

    /** Hz across one screen pixel. */
    fun hzPerPixel(): Float = display.binsPerPixel * PC_BIN_HZ / display.density.coerceAtLeast(0.1f)

    private fun waterfallHeight(d: Display = display): Int = height - spectrumHeight(d)

    private fun spectrumHeight(d: Display = display): Int = (d.percent2D * height / 100f).toInt()

    fun setSize(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        if (w == width && h == height && waterfallBitmap != null && tempBitmap != null) return

        width = w
        height = h

        val oldWaterfall = waterfallBitmap
        val oldTemp = tempBitmap

        waterfallBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        tempBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        waterfallCanvas = Canvas(waterfallBitmap!!)
        waterfallCanvas?.drawColor(Color.BLACK)

        if (oldWaterfall != null && !oldWaterfall.isRecycled) {
            val srcRect = Rect(0, 0, oldWaterfall.width, oldWaterfall.height)
            val dstRect = Rect(0, 0, w, h)
            waterfallCanvas?.drawBitmap(oldWaterfall, srcRect, dstRect, null)
            oldWaterfall.recycle()
        }

        oldTemp?.recycle()
    }

    /** One engine spectrum (linear power per [binHz] bin): dataSink. */
    fun updateSpectrum(bins: FloatArray, binHz: Float) {
        if (bins.isEmpty() || binHz <= 0f) return

        // Onto the PC grid. Engine power is |X|^2 / n^2 of raw samples; the PC scales samples by
        // 0.1 and keeps s = 1000 x that (waterfall) and ssum = that (Cumulative).
        val ratio = PC_BIN_HZ / binHz
        for (i in 0 until IZ) {
            val pos = i * ratio
            val k = pos.toInt()
            sNow[i] = if (k + 1 < bins.size) {
                val v = bins[k] + (pos - k) * (bins[k + 1] - bins[k])
                10f * v
            } else 0f
        }

        // Cumulative restarts each cycle of the current speed
        val cycle = System.currentTimeMillis() / 1000 / cyclePeriodSec
        if (cycle != lastCycle) {
            ssum.fill(0f)
            ihsym = 0
            lastCycle = cycle
        }
        ihsym++
        for (i in 0 until IZ) {
            ssum[i] += sNow[i] / 1000f
            savg[i] = ssum[i] / ihsym
        }
        if (ihsym % 10 == 0) linearAverage()
        summaryReady = true

        // Avg N (WideGraph::dataSink m_waterfallNow)
        if (sAccumRuns == 0) sNow.copyInto(sAccum) else for (i in 0 until IZ) sAccum[i] += sNow[i]
        sAccumRuns++
        if (sAccumRuns >= display.avg.coerceAtLeast(1)) {
            for (i in 0 until IZ) sCommitted[i] = sAccum[i] / sAccumRuns
            sAccumRuns = 0
            haveCommitted = true
            currentReady = true
        }

        latestAtMs = SystemClock.uptimeMillis()
        if (!running && haveCommitted) {
            running = true
            lastTickMs = latestAtMs
            rowsOwed = 1f
            drawHandler.post(tick)
        }
    }

    fun clear() {
        stopDrawing()
        haveCommitted = false
        sAccumRuns = 0
        waterfallCanvas?.drawColor(Color.BLACK)
        invalidateCallback?.invoke()
    }

    fun stopDrawing() {
        running = false
        drawHandler.removeCallbacks(tick)
    }

    fun release() {
        clear()
        waterfallBitmap?.recycle()
        waterfallBitmap = null
        tempBitmap?.recycle()
        tempBitmap = null
        waterfallCanvas = null
    }

    fun getBitmap(): Bitmap? = waterfallBitmap

    private fun canDraw(): Boolean {
        return width > 0 && height > 0 && waterfallBitmap != null && tempBitmap != null && waterfallCanvas != null
    }

    private fun xFromFreq(f: Float): Float {
        val d = display
        val cols = width / d.density.coerceAtLeast(0.1f)
        return ((f - d.startHz) / (d.binsPerPixel * PC_BIN_HZ) + 0.5f).toInt().toFloat().coerceIn(0f, cols)
    }

    /** CPlotter::drawDecodeLine: bracket from [fa] to [fb] Hz across the top of the waterfall. */
    fun drawDecodeLine(color: Int, fa: Float, fb: Float) {
        val c = waterfallCanvas ?: return
        val dp = display.density
        val x1 = min(xFromFreq(fa), xFromFreq(fb)) * dp
        val x2 = max(xFromFreq(fa), xFromFreq(fb)) * dp
        linePaint.color = color
        linePaint.strokeWidth = dp
        c.drawLine(x1, 4 * dp, x2, 4 * dp, linePaint)
        c.drawLine(x1, 0f, x1, 9 * dp, linePaint)
        c.drawLine(x2, 0f, x2, 9 * dp, linePaint)
        invalidateCallback?.invoke()
    }

    /** CPlotter::drawHorizontalLine(color, x, width) on the top row. */
    fun drawHorizontalLine(color: Int, xDp: Int, widthDp: Int) {
        val c = waterfallCanvas ?: return
        val dp = display.density
        linePaint.color = color
        linePaint.strokeWidth = dp
        val end = if (widthDp <= 0) width.toFloat() else (xDp + widthDp) * dp
        c.drawLine(xDp * dp, 0f, end, 0f, linePaint)
        invalidateCallback?.invoke()
    }

    /** WideGraph::dataSink swide: one dB value per PC pixel; NaN past 5000 Hz. */
    private fun swide(): Int {
        val d = display
        val density = d.density.coerceAtLeast(0.1f)
        val cols = (width / density).toInt().coerceAtLeast(1)
        if (columns.size != cols) columns = FloatArray(cols)
        val bpp = d.binsPerPixel
        val start = (d.startHz / PC_BIN_HZ + 0.5f).toInt()
        val end = min(cols, (5000f / (bpp * PC_BIN_HZ)).toInt())
        for (c in 0 until cols) {
            val first = start + c * bpp
            if (c >= end || first + bpp > IZ) { columns[c] = Float.NaN; continue }
            var sum = 0f
            for (k in first until first + bpp) sum += sCommitted[k]
            columns[c] = 10f * log10((bpp * sum).coerceAtLeast(1e-20f))
        }
        var valid = 0
        while (valid < cols && !columns[valid].isNaN()) valid++
        // CPlotter::drawData: m_flatten over the visible width
        if (d.flatten && valid > 1) WaterfallFlatten.flatten(columns, valid)
        return valid
    }

    private fun drawFrame(rows: Int) {
        val bitmap = waterfallBitmap ?: return
        val temp = tempBitmap ?: return
        val d = display
        val density = d.density.coerceAtLeast(0.1f)
        val h1 = waterfallHeight()
        val valid = swide()
        val cols = columns.size

        if (h1 > 0) {
            // Scaler1D
            val scale = 10f * sqrt(d.binsPerPixel * d.avg / 15f) * 10f.pow(0.015f * d.gain)
            if (row.size != width) row = IntArray(width)
            for (x in 0 until width) {
                val v = columns[(x / density).toInt().coerceAtMost(cols - 1)]
                val index = if (v.isNaN()) 0 else (d.zero + (scale * v).toInt()).coerceIn(0, 254)
                row[x] = d.colours[index]
            }
            // Scroll the waterfall part down by [rows]
            val tempCanvas = Canvas(temp)
            tempCanvas.drawBitmap(bitmap, Rect(0, 0, width, h1 - rows), Rect(0, rows, width, h1), null)
            waterfallCanvas?.drawBitmap(temp, Rect(0, rows, width, h1), Rect(0, rows, width, h1), null)
            for (y in 0 until rows) bitmap.setPixels(row, 0, width, 0, y, width, 1)
        }

        // CPlotter::shouldDrawSpectrum
        val drawSpectrum = if (d.spectrum == SPECTRUM_CURRENT) currentReady else summaryReady
        if (drawSpectrum && spectrumHeight() > 0) drawSpectrum(valid)
        currentReady = false
        summaryReady = false

        invalidateCallback?.invoke()
    }

    private fun drawSpectrum(valid: Int) {
        val c = waterfallCanvas ?: return
        val d = display
        val dp = d.density.coerceAtLeast(0.1f)
        val top = waterfallHeight().toFloat()
        c.save()
        c.clipRect(0f, top, width.toFloat(), height.toFloat())
        c.drawColor(Color.BLACK)

        // Scaler2D, in PC pixels of the spectrum height
        val h2 = spectrumHeight() / dp
        val scaledGain = h2 / 70f * 10f.pow(0.02f * d.gain2D)
        val scaledZero = h2 * 0.9f - h2 / 70f * d.zero2D
        fun y(value: Float) = top + (scaledZero - scaledGain * value) * dp

        spectrumPath.reset()
        val cols = columns.size
        when (d.spectrum) {
            SPECTRUM_CURRENT -> {
                spectrumPaint.color = Color.GREEN
                var minV = Float.MAX_VALUE
                for (x in 0 until valid) minV = min(minV, columns[x])
                for (x in 0 until valid) addPoint(x * dp, y(columns[x] - minV))
            }
            SPECTRUM_CUMULATIVE -> {
                spectrumPaint.color = Color.CYAN
                addPoints(savg, cols, dp) { v -> y(30f + 10f * log10(v.coerceAtLeast(1e-20f))) }
            }
            else -> {
                spectrumPaint.color = Color.YELLOW
                addPoints(slin, cols, dp) { v -> y(v) }
            }
        }
        spectrumPaint.strokeWidth = dp
        c.drawPath(spectrumPath, spectrumPaint)
        c.restore()
    }

    private fun addPoint(x: Float, y: Float) {
        if (spectrumPath.isEmpty) spectrumPath.moveTo(x, y) else spectrumPath.lineTo(x, y)
    }

    /** CPlotter addPoints: each PC pixel averages its Bins Per Pixel bins of [data]. */
    private fun addPoints(data: FloatArray, cols: Int, dp: Float, value: (Float) -> Float) {
        val bpp = display.binsPerPixel
        val start = (display.startHz / PC_BIN_HZ + 0.5f).toInt()
        for (x in 0 until cols) {
            val first = start + x * bpp
            if (first + bpp > NSMAX) break
            var sum = 0f
            for (k in first until first + bpp) sum += data[k]
            addPoint(x * dp, value(sum / bpp))
        }
    }

    /** dataSink, every 10 spectra: slin = flat1(savg) [+ smo twice], scaled 0..50. */
    private fun linearAverage() {
        val mode4 = NCH[(display.smooth - 1).coerceIn(0, NCH.size - 1)]
        val nsmo = 4 * min(10 * mode4, 150)
        flat1(savg, IZ, nsmo, slin)
        if (mode4 >= 2) {
            val tmp = FloatArray(NSMAX)
            smo(slin, tmp, IZ, mode4)
            smo(tmp, slin, IZ, mode4)
        }
        for (i in 0 until 250) slin[i] = 0f
        val ia = (500.0 / PC_BIN_HZ).toInt()
        val ib = (2700.0 / PC_BIN_HZ).toInt()
        var smin = Float.MAX_VALUE
        for (i in ia until ib) smin = min(smin, slin[i])
        var smax = -Float.MAX_VALUE
        for (i in 0 until IZ) smax = max(smax, slin[i])
        val scale = if (smax > smin) 50f / (smax - smin) else 0f
        for (i in slin.indices) slin[i] = max(0f, scale * (slin[i] - smin))
    }

    /** dataSink.cpp flat1. */
    private fun flat1(s: FloatArray, iz: Int, nsmo: Int, out: FloatArray) {
        val nstep = 20
        val nh = nstep / 2
        val ia = nsmo / 2 + 1
        val ib = iz - nsmo / 2 - 1
        val x = FloatArray(8192)
        val rank = (0.5f * nsmo).roundToInt().coerceIn(0, nsmo - 1)
        val temp = FloatArray(nsmo)
        var i = ia
        while (i <= ib) {
            s.copyInto(temp, 0, i - nsmo / 2, i - nsmo / 2 + nsmo)
            temp.sort()
            x[i] = temp[rank]
            for (j in (i - nh) until (i + nh)) x[j] = x[i]
            i += nstep
        }
        for (j in 0 until ia) x[j] = x[ia]
        for (j in ib + 1 until iz) x[j] = x[ib]
        var xmax = -Float.MAX_VALUE
        for (j in iz / 10 until (9 * iz) / 10) xmax = max(xmax, x[j])
        val x0 = 0.001f * xmax
        for (j in 0 until iz) out[j] = s[j] / (x[j] + x0)
    }

    /** dataSink.cpp smo: b = running sum of a over nadd points, edges zero. */
    private fun smo(a: FloatArray, b: FloatArray, npts: Int, nadd: Int) {
        val nh = nadd / 2
        for (i in nh until npts - nh) {
            var sum = 0f
            for (j in -nh..nh) sum += a[i + j]
            b[i] = sum
        }
        for (i in 0 until nh) b[i] = 0f
        for (i in npts - nh until npts) b[i] = 0f
    }
}
