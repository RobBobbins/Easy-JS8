package com.js8call.example.js8ui

/**
 * JS8's callsign rule (JS8_Main/Varicode.cpp isValidCallsign; same as the engine's
 * core/src/protocol/varicode.cpp is_valid_callsign), for the Settings OK check.
 * Group calls (@...) are refused by the caller, so the base-call list (@ALLCALL, ...) is not needed.
 */
object Js8Callsign {
    private val base = Regex("""\b(([0-9A-Z])?([0-9A-Z])([0-9])([A-Z])?([A-Z])?([A-Z])?)([/][P])?\b""")
    private val compound = Regex("""(?:[@]?|\b)([A-Z0-9/@][A-Z0-9/]{0,2}[/]?[A-Z0-9/]{0,3}[/]?[A-Z0-9/]{0,3})\b""")
    private val letterDigit = Regex("[0-9][A-Z]|[A-Z][0-9]")

    fun isValid(callsign: String): Boolean {
        if (base.matches(callsign)) return callsign.length > 2 && letterDigit.containsMatchIn(callsign)
        if (!compound.matches(callsign)) return false
        // compound calls cannot be > 9 characters after removing the /
        if (callsign.length - callsign.count { it == '/' } > 9) return false
        if ('/' in callsign || callsign.startsWith("@")) return true
        return callsign.length > 2 && letterDigit.containsMatchIn(callsign)
    }
}
