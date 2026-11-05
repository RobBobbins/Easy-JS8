package com.js8call.example.js8ui

/**
 * JS8's callsign rule (JS8_Main/Varicode.cpp isValidCallsign; same as the engine's
 * core/src/protocol/varicode.cpp is_valid_callsign), for the Settings OK check.
 * isValid leaves out the PC's first rule, that a base call (@ALLCALL, ...) is valid: of those only <....>
 * is not also a valid compound call.
 */
object Js8Callsign {
    private val base = Regex("""\b(([0-9A-Z])?([0-9A-Z])([0-9])([A-Z])?([A-Z])?([A-Z])?)([/][P])?\b""")
    private val compound = Regex("""(?:[@]?|\b)([A-Z0-9/@][A-Z0-9/]{0,2}[/]?[A-Z0-9/]{0,3}[/]?[A-Z0-9/]{0,3})\b""")
    private val letterDigit = Regex("[0-9][A-Z]|[A-Z][0-9]")

    /** PC basecalls: the calls and groups with their own codes (Varicode.cpp 218-285). */
    private val basecalls = setOf(
        "<....>", "@ALLCALL", "@JS8NET", "@DX/NA", "@DX/SA", "@DX/EU", "@DX/AS", "@DX/AF", "@DX/OC", "@DX/AN",
        "@REGION/1", "@REGION/2", "@REGION/3", "@GROUP/0", "@GROUP/1", "@GROUP/2", "@GROUP/3", "@GROUP/4",
        "@GROUP/5", "@GROUP/6", "@GROUP/7", "@GROUP/8", "@GROUP/9", "@COMMAND", "@CONTROL", "@NET", "@NTS",
        "@RESERVE/0", "@RESERVE/1", "@RESERVE/2", "@RESERVE/3", "@RESERVE/4", "@APRSIS", "@RAGCHEW", "@JS8",
        "@EMCOMM", "@ARES", "@MARS", "@AMRRON", "@RACES", "@RAYNET", "@RADAR", "@SKYWARN", "@CQ", "@HB", "@QSO",
        "@QSOPARTY", "@CONTEST", "@FIELDDAY", "@SOTA", "@IOTA", "@POTA", "@QRP", "@QRO"
    )

    /** PC match(...).capturedLength() == length: the first match found is the whole text. */
    private fun whole(r: Regex, call: String) = r.find(call)?.value?.length == call.length

    /** Varicode::isCompoundCallsign (Varicode.cpp 1326-1347): an @GROUP or a call with a prefix or suffix, not a plain call. */
    fun isCompound(call: String): Boolean {
        if (call in basecalls && !call.startsWith("@")) return false
        if (whole(base, call)) return false
        if (!whole(compound, call)) return false
        return isValidCompound(call)
    }

    /**
     * isValidCompoundCallsign (Varicode.cpp 1258-1291): 9 characters at most without the /; with a / the part
     * before it is not a base call; a group; or a word with a letter-digit pair, so plain words are not calls.
     */
    private fun isValidCompound(call: String): Boolean {
        if (call.length - call.count { it == '/' } > 9) return false
        val slash = call.indexOf('/')
        if (slash != -1) return call.substring(0, slash) !in basecalls
        if (call.startsWith("@")) return true
        return call.length > 2 && letterDigit.containsMatchIn(call)
    }

    fun isValid(callsign: String): Boolean {
        if (whole(base, callsign)) return callsign.length > 2 && letterDigit.containsMatchIn(callsign)
        if (!whole(compound, callsign)) return false
        return isValidCompound(callsign)
    }
}
