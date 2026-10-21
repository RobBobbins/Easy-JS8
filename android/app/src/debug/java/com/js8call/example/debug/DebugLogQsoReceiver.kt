package com.js8call.example.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.js8call.example.api.Js8Api

/**
 * Easy JS8 API test: send a made-up logged contact to LOG.QSO, N1MM, N3FJP and WSJT-X
 * (the LOG window that will call Js8Api.logQso is not built yet).
 * adb shell am broadcast -n com.easyjs8.app/com.js8call.example.debug.DebugLogQsoReceiver --es call W1AW
 */
class DebugLogQsoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Js8Api.init(context)
        val call = intent.getStringExtra("call") ?: "W1AW"
        val now = System.currentTimeMillis()
        val dial = Js8Api.dialHz()
        val adif = "<call:${call.length}>$call <gridsquare:4>FN31 <mode:4>MFSK <submode:3>JS8 <rst_sent:3>-10 " +
            "<rst_rcvd:3>-12 <qso_date:8>20261020 <time_on:6>120000 <time_off:6>120500 <band:3>20m " +
            "<freq:9>${String.format(java.util.Locale.US, "%.6f", dial / 1e6)} <station_callsign:${Js8Api.myCall().length}>${Js8Api.myCall()}"
        Js8Api.logQso(Js8Api.Qso(
            call = call, grid = "FN31", dialHz = dial, mode = "MFSK", submode = "JS8", rptSent = "-10",
            rptReceived = "-12", name = "Test", comments = "Easy JS8 API test", timeOnMs = now - 300_000,
            timeOffMs = now, operatorCall = "", myCall = Js8Api.myCall(), myGrid = "", adif = adif,
            extra = mapOf("STATE" to "CT")
        )) { title, text -> Log.w("Js8Api", "$title: $text") }
        Log.i("Js8Api", "Debug LOG.QSO sent for $call")
    }
}
