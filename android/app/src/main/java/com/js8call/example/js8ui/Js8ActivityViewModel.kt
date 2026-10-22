package com.js8call.example.js8ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.preference.PreferenceManager
import com.js8call.example.model.DecodedMessage

/** Holds the desktop-style activity (Band / Call Activity, Message Window) for the whole app session. */
class Js8ActivityViewModel(app: Application) : AndroidViewModel(app) {
    val activity = Js8Activity()
    private val _version = MutableLiveData(0)
    /** Bumped whenever something visible changed. */
    val version: LiveData<Int> = _version

    /** Operator's TX offset; the Message Window follows frames near it. */
    var myOffset = 1500

    private fun cfg() = Js8Activity.Config(PreferenceManager.getDefaultSharedPreferences(getApplication()))

    fun onFrame(m: DecodedMessage) {
        if (activity.onFrame(m, myOffset, cfg())) changed()
    }

    /** A message from the Typing Box, kept there (locked) until its last part has gone out. */
    data class TypedTx(val text: String, val onAir: Boolean = false, val charsSent: Int = 0,
                       /** Not typed: a heartbeat or reply put in the empty Typing Box while it is sent. */
                       val auto: Boolean = false)

    /** The Typing Box has nothing in it (kept by Js8MainFragment): only then is an automatic message shown there. */
    var typingBoxEmpty = true

    private val _typed = MutableLiveData<TypedTx?>(null)
    /** Typing Box message waiting or being sent; null = the box is free. */
    val typed: LiveData<TypedTx?> = _typed
    /** The typed message finished (or was halted): the Typing Box still has to be emptied. */
    var typedClearPending = false

    /** Send pressed: the Typing Box keeps [text] until it has been sent. */
    fun onTypedQueued(text: String) {
        _typed.value = TypedTx(text)
    }

    /** The queue handed [text] to the engine; if it is the typed message, its parts now count. */
    fun onSentToEngine(text: String) {
        val t = _typed.value ?: return
        if (!t.onAir && t.text == text) _typed.value = t.copy(onAir = true)
    }

    /** A part of any of our transmissions started going out (heartbeats and replies too). */
    fun onTxFrame(text: String, index: Int, count: Int, charsSent: Int, offset: Int, fullText: String = "") {
        activity.writeTxFrame(text, offset, first = index == 0, last = index == count - 1, cfg = cfg())
        // Every transmission shows in the Typing Box (Rob, 2026-10-21); what you typed is never replaced
        if (_typed.value == null && typingBoxEmpty && fullText.isNotEmpty()) {
            _typed.value = TypedTx(fullText, onAir = true, charsSent = charsSent, auto = true)
        } else {
            _typed.value?.let { if (it.onAir) _typed.value = it.copy(charsSent = charsSent) }
        }
        changed()
    }

    /** FT8 signal heard in the JS8 audio (Settings > View > Show FT8 decodes in the Message Window). */
    fun onFt8(slotUtcMs: Long, offset: Int, text: String, callDe: String) {
        activity.writeFt8(slotUtcMs, offset, text, callDe)
        changed()
    }

    /** Transmission finished or failed: the typed message (if it was on air) leaves the box. */
    fun onTxEnded() {
        if (_typed.value?.onAir == true) clearTyped()
    }

    /** Halt, or the message is done: free and empty the Typing Box (PC resetMessageUI). */
    fun clearTyped() {
        typedClearPending = true
        _typed.value = null
    }

    fun changed() { _version.value = (_version.value ?: 0) + 1 }
}
