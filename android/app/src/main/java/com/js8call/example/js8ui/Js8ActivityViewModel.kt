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

    fun onTransmit(text: String, directed: String?, offset: Int) {
        val body = if (!directed.isNullOrBlank() && !text.uppercase().startsWith(directed.uppercase())) "$directed $text" else text
        activity.writeTx(body, offset, cfg())
        changed()
    }

    fun changed() { _version.value = (_version.value ?: 0) + 1 }
}
