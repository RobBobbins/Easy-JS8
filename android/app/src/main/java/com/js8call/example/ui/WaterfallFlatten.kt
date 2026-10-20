package com.js8call.example.ui

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * Waterfall Controls > Display > Flatten (JS8Call 3.0.3 JS8_Main/Flatten.cpp): levels one line of
 * dB values. At six Chebyshev nodes across the line it takes the 10th percentile of the values
 * within +-size/12, fits the 5th-degree polynomial through those six points, and subtracts it.
 * x is scaled to 0..1 for the solve; the polynomial is the same one.
 */
object WaterfallFlatten {

    private const val DEGREE = 5
    private const val SAMPLE = 10
    private const val COUNT = DEGREE + 1

    private val NODES = DoubleArray(COUNT) { i -> 0.5 * (1.0 - cos(PI / (2.0 * COUNT) * (2.0 * i + 1))) }

    fun flatten(data: FloatArray, size: Int) {
        if (size <= 0) return
        val arm = size / (2 * COUNT)
        val x = DoubleArray(COUNT)
        val y = DoubleArray(COUNT)
        for (i in 0 until COUNT) {
            val node = size * NODES[i]
            val base = node.roundToInt()
            val from = (base - arm).coerceIn(0, size)
            val to = (base + arm).coerceIn(0, size)
            val span = if (to > from) data.copyOfRange(from, to) else floatArrayOf(data[base.coerceIn(0, size - 1)])
            span.sort()
            x[i] = node / size
            y[i] = span[span.size * SAMPLE / 100].toDouble()
        }
        val c = solve(x, y) ?: return
        for (i in 0 until size) {
            val t = i.toDouble() / size
            var v = 0.0
            for (k in DEGREE downTo 0) v = v * t + c[k]
            data[i] -= v.toFloat()
        }
    }

    /** Vandermonde system V c = y, Gaussian elimination with partial pivoting. */
    private fun solve(x: DoubleArray, y: DoubleArray): DoubleArray? {
        val n = x.size
        val a = Array(n) { r -> DoubleArray(n + 1).also { row ->
            var p = 1.0
            for (col in 0 until n) { row[col] = p; p *= x[r] }
            row[n] = y[r]
        } }
        for (col in 0 until n) {
            val pivot = (col until n).maxByOrNull { abs(a[it][col]) } ?: return null
            if (abs(a[pivot][col]) < 1e-12) return null
            val tmp = a[col]; a[col] = a[pivot]; a[pivot] = tmp
            for (r in col + 1 until n) {
                val f = a[r][col] / a[col][col]
                for (k in col..n) a[r][k] -= f * a[col][k]
            }
        }
        val c = DoubleArray(n)
        for (r in n - 1 downTo 0) {
            var s = a[r][n]
            for (k in r + 1 until n) s -= a[r][k] * c[k]
            c[r] = s / a[r][r]
        }
        return c
    }
}
