package com.js8call.example.js8ui

import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.text.Editable
import android.text.Spanned
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.CharacterStyle
import android.text.style.ForegroundColorSpan
import android.text.style.UpdateAppearance
import com.js8call.core.JS8Engine
import com.js8call.example.util.Js8Commands

/**
 * Typing Box marks: the PC's pills (PillRenderer, DirectedMessageParser; Station > UI > Composition)
 * as colored backgrounds, and the red underline on words with a shorter alternative
 * (JSCChecker::checkRange; Station > General > Behavior > Suggest alternative word choices).
 */
object Js8TypingSpans {

    private class PillBackground(color: Int) : BackgroundColorSpan(color)
    private class PillForeground(color: Int) : ForegroundColorSpan(color)

    private class Misspelled : CharacterStyle(), UpdateAppearance {
        override fun updateDrawState(tp: TextPaint) {
            tp.isUnderlineText = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tp.underlineColor = Color.RED
        }
    }

    private val senderRegex = Regex("^([A-Z0-9/]+):\\s*")
    private val wordRegex = Regex("@?\\w+")

    fun apply(e: Editable, prefs: SharedPreferences, selectedCall: String?) {
        e.getSpans(0, e.length, PillBackground::class.java).forEach { e.removeSpan(it) }
        e.getSpans(0, e.length, PillForeground::class.java).forEach { e.removeSpan(it) }
        e.getSpans(0, e.length, Misspelled::class.java).forEach { e.removeSpan(it) }
        val text = e.toString()
        if (prefs.all["js8cfg_pills"]?.toString() != "false") {
            var start = 0
            text.split('\n').forEach { line ->
                pillLine(e, start, line.uppercase(), prefs, !selectedCall.isNullOrEmpty())
                start += line.length + 1
            }
        }
        if (prefs.all["js8cfg_spellcheck"]?.toString() != "false") { // PC Spellcheck default true
            wordRegex.findAll(text).forEach { m ->
                if (!JS8Engine.jscCorrect(m.value.uppercase().removePrefix("@"))) {
                    e.setSpan(Misspelled(), m.range.first, m.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }
    }

    /** The word at a text offset, or null (PC cursorForPosition, StartOfWord to EndOfWord). */
    fun wordAt(text: String, offset: Int): IntRange? =
        wordRegex.findAll(text).firstOrNull { offset >= it.range.first && offset <= it.range.last + 1 }?.range

    /** PC parseTokensUpper: [sender:] then a group or recipient (or relay chain), then a command. */
    private fun pillLine(e: Editable, lineStart: Int, upper: String, prefs: SharedPreferences, hasSelection: Boolean) {
        fun pill(start: Int, length: Int, kind: String, bg: String, fg: String) {
            val at = lineStart + start
            fun color(key: String, default: String) =
                runCatching { Color.parseColor(prefs.getString("js8cfg_ui_pill_${kind}_$key", null) ?: default) }.getOrDefault(Color.parseColor(default))
            e.setSpan(PillBackground(color("bg", bg)), at, at + length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            e.setSpan(PillForeground(color("fg", fg)), at, at + length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        var pos = 0
        senderRegex.find(upper)?.let { m ->
            val call = m.groupValues[1]
            if (Js8Activity.isCallsign(call)) {
                pill(0, call.length, "sender", "#7F8C8D", "#FFFFFF")
                pos = m.range.last + 1
            }
        }
        val content = upper.substring(pos)
        val first = content.substringBefore(' ')
        var hasTarget = true
        when {
            first.startsWith("@") && first.length > 1 -> pill(pos, first.length, "group", "#16A085", "#FFFFFF")
            first.contains('>') && first.split('>').filter { it.isNotEmpty() }.let { hops -> hops.isNotEmpty() && hops.all { Js8Activity.isCallsign(it) } } -> {
                var hop = pos
                first.split('>').forEach { part ->
                    if (part.isNotEmpty()) pill(hop, part.length, "recipient", "#2980B9", "#FFFFFF")
                    hop += part.length + 1
                }
            }
            Js8Activity.isCallsign(first) -> pill(pos, first.length, "recipient", "#2980B9", "#FFFFFF")
            else -> hasTarget = false
        }
        val after = if (hasTarget) pos + first.length else pos
        if (!hasTarget && !hasSelection) return // PC: a command needs a recipient, typed or selected
        val rest = upper.substring(after)
        val match = Js8Commands.matchAt(rest) ?: return
        val spaces = rest.length - rest.trimStart().length
        pill(after + spaces, match.command.length, "command", "#E67E22", "#FFFFFF")
    }
}
