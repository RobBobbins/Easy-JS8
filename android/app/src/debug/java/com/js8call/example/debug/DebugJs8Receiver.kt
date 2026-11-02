package com.js8call.example.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.js8call.example.service.JS8EngineService

/** Easy JS8 receive test: adb plays raw 12 kHz audio into the decoder, or sends one raw frame to read its tones. */
class DebugJs8Receiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val forward = Intent(context, JS8EngineService::class.java).apply {
            action = if (intent.hasExtra("frame")) JS8EngineService.ACTION_DEBUG_TX_FRAME else JS8EngineService.ACTION_DEBUG_JS8_PCM
            intent.extras?.let { putExtras(it) }
        }
        try {
            context.startService(forward)
        } catch (e: IllegalStateException) {
            Log.w("DebugJs8Receiver", "JS8 test dropped: bring the app to the front")
        }
    }
}
