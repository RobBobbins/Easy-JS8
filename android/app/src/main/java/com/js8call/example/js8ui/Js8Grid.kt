package com.js8call.example.js8ui

import java.util.Locale

/** Maidenhead locator from latitude / longitude (same math as JS8Android SettingsFragment). */
object Js8Grid {
    /** 8 characters: field, square, subsquare, extended square (e.g. FN03HP46). */
    fun fromLatLon(latitude: Double, longitude: Double): String {
        var lon = -longitude
        var lat = latitude.coerceIn(-90.0, 90.0)
        if (lon < -180.0) lon += 360.0
        if (lon > 180.0) lon -= 360.0
        if (lon == 180.0) lon = 179.999999
        if (lat == 90.0) lat = 89.999999
        val lonMinutes = (180.0 - lon) * 60.0
        val latMinutes = (lat + 90.0) * 60.0
        val lonField = (lonMinutes / 1200.0).toInt().coerceIn(0, 17)
        val latField = (latMinutes / 600.0).toInt().coerceIn(0, 17)
        val lonRem = lonMinutes - lonField * 1200.0
        val latRem = latMinutes - latField * 600.0
        val lonSquare = (lonRem / 120.0).toInt().coerceIn(0, 9)
        val latSquare = (latRem / 60.0).toInt().coerceIn(0, 9)
        val lonSub = ((lonRem - lonSquare * 120.0) / 5.0).toInt().coerceIn(0, 23)
        val latSub = ((latRem - latSquare * 60.0) / 2.5).toInt().coerceIn(0, 23)
        val lonExt = ((lonRem - lonSquare * 120.0 - lonSub * 5.0) / 0.5).toInt().coerceIn(0, 9)
        val latExt = ((latRem - latSquare * 60.0 - latSub * 2.5) / 0.25).toInt().coerceIn(0, 9)
        return buildString {
            append('A' + lonField); append('A' + latField)
            append('0' + lonSquare); append('0' + latSquare)
            append('A' + lonSub); append('A' + latSub)
            append('0' + lonExt); append('0' + latExt)
        }.uppercase(Locale.US)
    }

    /**
     * The PC's grid check (JS8_Include/Maidenhead.h, ExtendedValidator): 4, 6, 8, 10 or 12
     * characters, pairs A-R, 0-9, A-X, 0-9, A-X, 0-9.
     */
    fun isValid(grid: String): Boolean {
        val g = grid.uppercase(Locale.US)
        if (g.length % 2 != 0 || g.length < 4 || g.length > 12) return false
        return g.withIndex().all { (i, c) ->
            when (i) {
                0, 1 -> c in 'A'..'R'
                4, 5, 8, 9 -> c in 'A'..'X'
                else -> c in '0'..'9'
            }
        }
    }
}
