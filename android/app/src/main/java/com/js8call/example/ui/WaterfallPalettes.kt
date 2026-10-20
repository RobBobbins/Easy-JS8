package com.js8call.example.ui

import android.content.Context
import android.graphics.Color

/**
 * Waterfall Controls > Display > Palette: the PC's 29 palette files (JS8Call Palettes folder, .pal files
 * copied to assets/palettes), listed by name like the PC's combo box, and spread over 256
 * colours like WF::Palette::interpolate.
 */
object WaterfallPalettes {

    const val DEFAULT = "Default"
    private const val DIR = "palettes"
    private const val POINTS = 256

    /** Palette names (file names without .pal), in the PC's order. */
    fun names(context: Context): List<String> =
        (context.assets.list(DIR) ?: emptyArray())
            .filter { it.endsWith(".pal", ignoreCase = true) }
            .map { it.removeSuffix(".pal") }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)

    /** 256 colours for [name]; Default when the file is missing or unreadable. */
    fun colours(context: Context, name: String): IntArray = try {
        val points = context.assets.open("$DIR/$name.pal").bufferedReader().readLines()
            .mapNotNull { line ->
                val items = line.split(';')
                if (items.size != 3) return@mapNotNull null
                val (r, g, b) = items.map { it.trim().toIntOrNull() ?: return@mapNotNull null }
                Color.rgb(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
            }
        interpolate(points)
    } catch (e: Exception) {
        fallback()
    }

    /** Default.pal. */
    fun fallback(): IntArray = interpolate(listOf(
        Color.rgb(0, 0, 0), Color.rgb(0, 6, 136), Color.rgb(0, 19, 198), Color.rgb(0, 32, 239),
        Color.rgb(172, 167, 105), Color.rgb(194, 198, 49), Color.rgb(225, 228, 107),
        Color.rgb(255, 255, 0), Color.rgb(255, 51, 0)
    ))

    /** WF.cpp make_valid + Palette::interpolate. */
    private fun interpolate(given: List<Int>): IntArray {
        val colours = given.toMutableList()
        if (colours.size < 2) colours.add(0, Color.BLACK)
        if (colours.size == 1) colours.add(Color.WHITE)
        val interval = POINTS.toDouble() / (colours.size - 1)
        return IntArray(POINTS) { i ->
            var prior = (i / interval).toInt()
            if (prior >= colours.size - 1) prior--
            var next = prior + 1
            if (next >= colours.size) next--
            val inc = i - interval * prior
            fun mix(a: Int, b: Int) = (a + inc * (b - a) / interval).toInt().coerceIn(0, 255)
            val p = colours[prior]
            val n = colours[next]
            Color.rgb(mix(Color.red(p), Color.red(n)), mix(Color.green(p), Color.green(n)), mix(Color.blue(p), Color.blue(n)))
        }
    }
}
