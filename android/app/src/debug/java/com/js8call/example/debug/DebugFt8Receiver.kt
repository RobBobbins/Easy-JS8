package com.js8call.example.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.js8call.example.service.JS8EngineService

/** Easy JS8 FT8 monitor test: adb asks the service to decode a WAV from the app's files folder. */
class DebugFt8Receiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val forward = Intent(context, JS8EngineService::class.java).apply {
            action = JS8EngineService.ACTION_DEBUG_FT8_WAV
            intent.extras?.let { putExtras(it) }
        }
        try {
            context.startService(forward)
        } catch (e: IllegalStateException) {
            Log.w("DebugFt8Receiver", "FT8 test dropped: bring the app to the front")
        }
    }
}
