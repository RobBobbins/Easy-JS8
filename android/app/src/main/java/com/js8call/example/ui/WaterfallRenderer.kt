package com.js8call.example.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Offscreen renderer that maintains waterfall bitmap state across view lifecycles.
 *
 * Draws like the PC's CPlotter / WideGraph (JS8Call 3.0.3): one PC pixel (one dp here) covers
 * Bins Per Pixel x 1500/2048 Hz from Start; each line is 10*log10 of the power, flattened when
 * Flatten is on, then turned into a palette colour with the Gain / Zero scaler. Lines are added
 * at Scroll Speed (fps) dp per second, repeating the last spectrum until a new one arrives.
 */
class WaterfallRenderer {

    companion object {
        /** CPlotter.cpp FFT_BIN_WIDTH: the PC's bin, which Bins Per Pixel counts. */
        const val PC_BIN_HZ = 1500f / 2048f
        private const val TICK_MS = 33L
    }

    /** Waterfall Controls > Display values (WideGraph defaults). */
    data class Display(
        val startHz: Int = 500,
        val binsPerPixel: Int = 2,
        val fps: Int = 4,
        val gain: Int = 0,
        val zero: Int = 0,
        val flatten: Boolean = true,
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

    private var latest: FloatArray? = null
    private var latestBinHz = 0f
    private var latestAtMs = 0L
    private var rowsOwed = 0f
    private var lastTickMs = 0L
    private var running = false

    private var columns = FloatArray(0)
    private var row = IntArray(0)

    private val drawHandler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            val now = SystemClock.uptimeMillis()
            // Stop scrolling once spectra stop coming (RX off)
            if (latest == null || now - latestAtMs > 1500) {
                running = false
                return
            }
            // WideGraph m_drawTimer: fps PC pixels a second, each repeating the latest line
            rowsOwed += (now - lastTickMs) * display.fps * display.density / 1000f
            lastTickMs = now
            val rows = rowsOwed.toInt()
            if (rows > 0 && canDraw()) {
                rowsOwed -= rows
                drawRows(rows.coerceAtMost(height))
            }
            drawHandler.postDelayed(this, TICK_MS)
        }
    }

    private var invalidateCallback: (() -> Unit)? = null

    fun setInvalidateCallback(callback: (() -> Unit)?) {
        invalidateCallback = callback
    }

    fun setDisplay(value: Display) {
        display = value
    }

    fun getDisplay(): Display = display

    /** Hz at the left edge (Start). */
    fun startHz(): Float = display.startHz.toFloat()

    /** Hz across one screen pixel. */
    fun hzPerPixel(): Float = display.binsPerPixel * PC_BIN_HZ / display.density.coerceAtLeast(0.1f)

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

    fun updateSpectrum(bins: FloatArray, binHz: Float) {
        if (bins.isEmpty() || binHz <= 0f) return
        latest = bins.copyOf()
        latestBinHz = binHz
        latestAtMs = SystemClock.uptimeMillis()
        if (!running) {
            running = true
            lastTickMs = latestAtMs
            rowsOwed = 1f
            drawHandler.post(tick)
        }
    }

    fun clear() {
        stopDrawing()
        latest = null
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

    /**
     * WideGraph::dataSink + CPlotter::drawData: one value per PC pixel, 10*log10 of the power
     * over its Bins Per Pixel, flattened, then Scaler1D (zero + scale * value) into 0..254.
     */
    private fun lineColours(): IntArray {
        val bins = latest ?: return row
        val d = display
        val density = d.density.coerceAtLeast(0.1f)
        val cols = (width / density).toInt().coerceAtLeast(1)
        if (columns.size != cols) columns = FloatArray(cols)
        val colHz = d.binsPerPixel * PC_BIN_HZ
        // Phone bins are a different width; sum the power over the column's span, counted in PC bins
        val perPcBin = PC_BIN_HZ / latestBinHz
        var valid = 0
        for (c in 0 until cols) {
            val f0 = d.startHz + c * colHz
            val b0 = f0 / latestBinHz
            val b1 = (f0 + colHz) / latestBinHz
            if (b1 >= bins.size - 1) { columns[c] = Float.NaN; continue }
            var sum = 0f
            var b = b0
            while (b < b1) {
                val i = b.toInt()
                val next = minOf((i + 1).toFloat(), b1)
                val frac = b - i
                val v = bins[i] + frac * (bins[i + 1] - bins[i])
                sum += v * (next - b)
                b = next
            }
            val power = sum / perPcBin
            columns[c] = 10f * log10((d.binsPerPixel * power).coerceAtLeast(1e-20f))
            valid = c + 1
        }
        if (d.flatten && valid > 1) WaterfallFlatten.flatten(columns, valid)

        val scale = 10f * sqrt(d.binsPerPixel / 15f) * 10f.pow(0.015f * d.gain)
        if (row.size != width) row = IntArray(width)
        for (x in 0 until width) {
            val v = columns[(x / density).toInt().coerceAtMost(cols - 1)]
            val index = if (v.isNaN()) 0 else (d.zero + (scale * v).toInt()).coerceIn(0, 254)
            row[x] = d.colours[index]
        }
        return row
    }

    private fun drawRows(rows: Int) {
        val bitmap = waterfallBitmap ?: return
        val temp = tempBitmap ?: return
        val line = lineColours()
        if (line.size != width) return

        // Scroll down by [rows]
        val tempCanvas = Canvas(temp)
        tempCanvas.drawColor(Color.BLACK)
        tempCanvas.drawBitmap(bitmap, Rect(0, 0, width, height - rows), Rect(0, rows, width, height), null)
        waterfallCanvas?.drawBitmap(temp, 0f, 0f, null)
        for (y in 0 until rows) bitmap.setPixels(line, 0, width, 0, y, width, 1)

        invalidateCallback?.invoke()
    }
}
