package com.js8call.example.util

import java.util.Locale
import kotlin.math.floor

object BandUtils {
    enum class FallbackStyle {
        FLOOR_MHZ,
        ONE_DECIMAL_MHZ
    }

    fun bandLabelForFrequencyHz(
        frequencyHz: Long,
        fallbackStyle: FallbackStyle = FallbackStyle.FLOOR_MHZ
    ): String {
        val mhz = frequencyHz.toDouble() / 1_000_000.0
        return bandOrNull(frequencyHz) ?: when (fallbackStyle) {
            FallbackStyle.FLOOR_MHZ -> String.format(Locale.US, "%dmhz", floor(mhz).toInt())
            FallbackStyle.ONE_DECIMAL_MHZ -> String.format(Locale.US, "%.1fMHz", mhz)
        }
    }

    /** PC Radio::pretty_frequency_MHz_string: "7.078 000" with a no-break space before the last 3 digits. */
    fun prettyMhz(frequencyHz: Long): String =
        String.format(Locale.US, "%.6f", frequencyHz / 1_000_000.0).let { it.dropLast(3) + "\u00A0" + it.takeLast(3) }

    /** PC Bands::find: the band name, or null outside the 30 bands. */
    fun bandOrNull(frequencyHz: Long): String? {
        val mhz = frequencyHz.toDouble() / 1_000_000.0
        return when {
            // PC Bands.cpp
            mhz in 0.136..0.137 -> "2190m"
            mhz in 0.472..0.479 -> "630m"
            mhz in 0.501..0.504 -> "560m"
            mhz in 1.8..2.0 -> "160m"
            mhz in 3.5..4.0 -> "80m"
            mhz in 5.06..5.45 -> "60m"
            mhz in 7.0..7.3 -> "40m"
            mhz in 10.0..10.15 -> "30m"
            mhz in 14.0..14.35 -> "20m"
            mhz in 18.068..18.168 -> "17m"
            mhz in 21.0..21.45 -> "15m"
            mhz in 24.89..24.99 -> "12m"
            // CB JS8 is on 27.245 (Rob, 2026-11-05); not in the PC's table. The 40 CB channels.
            mhz in 26.965..27.405 -> "11m"
            mhz in 28.0..29.7 -> "10m"
            mhz in 50.0..54.0 -> "6m"
            mhz in 70.0..71.0 -> "4m"
            mhz in 144.0..148.0 -> "2m"
            mhz in 222.0..225.0 -> "1.25m"
            mhz in 420.0..450.0 -> "70cm"
            mhz in 902.0..928.0 -> "33cm"
            mhz in 1240.0..1300.0 -> "23cm"
            mhz in 2300.0..2450.0 -> "13cm"
            mhz in 3300.0..3500.0 -> "9cm"
            mhz in 5650.0..5925.0 -> "6cm"
            mhz in 10000.0..10500.0 -> "3cm"
            mhz in 24000.0..24250.0 -> "1.25cm"
            mhz in 47000.0..47200.0 -> "6mm"
            mhz in 75500.0..81000.0 -> "4mm"
            mhz in 119980.0..120020.0 -> "2.5mm"
            mhz in 142000.0..149000.0 -> "2mm"
            mhz in 241000.0..250000.0 -> "1mm"
            else -> null
        }
    }
}
