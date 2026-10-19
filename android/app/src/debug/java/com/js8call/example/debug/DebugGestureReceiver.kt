package com.js8call.example.debug

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import java.lang.ref.WeakReference

/**
 * Debug builds only: lets adb run touch gestures the emulator's `input` command cannot
 * (two-finger pinch, fast double-tap) through the app's normal touch dispatch.
 *
 *   adb shell am broadcast -n com.easyjs8.app/com.js8call.example.debug.DebugGestureReceiver \
 *       --es gesture pinch --ef x 1100 --ef y 500 --ef factor 2.5
 *   ... --es gesture doubletap --ef x 1100 --ef y 500
 *   ... --es gesture drag --ef x 1100 --ef y 500 --ef dx -400 --ef dy 0 --ei fingers 2
 * Coordinates are window pixels.
 */
class DebugGestureReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val activity = ResumedActivityTracker.current
        android.util.Log.i("DebugGesture", "gesture=${intent.getStringExtra("gesture")} activity=$activity")
        if (activity == null) return
        val x = intent.getFloatExtra("x", 500f)
        val y = intent.getFloatExtra("y", 500f)
        val events = when (intent.getStringExtra("gesture")) {
            "pinch" -> pinch(x, y, intent.getFloatExtra("factor", 2f))
            "doubletap" -> tap(x, y, 0) + tap(x, y, 120)
            "drag" -> drag(x, y, intent.getFloatExtra("dx", 0f), intent.getFloatExtra("dy", 0f),
                intent.getIntExtra("fingers", 1))
            else -> emptyList()
        }
        val handler = Handler(Looper.getMainLooper())
        val root = activity.window.decorView
        events.forEach { (delay, ev) ->
            handler.postDelayed({
                val handled = root.dispatchTouchEvent(ev)
                android.util.Log.i("DebugGesture", "${MotionEvent.actionToString(ev.action)} n=${ev.pointerCount} handled=$handled")
                ev.recycle()
            }, delay)
        }
    }

    private var downTime = 0L
    // Start span above ScaleGestureDetector minimum span (about 27 mm), like real fingers
    private val intent0Radius = 300f

    private fun ev(t: Long, action: Int, pts: List<Pair<Float, Float>>): MotionEvent {
        val props = Array(pts.size) { i -> MotionEvent.PointerProperties().apply { id = i; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        val coords = Array(pts.size) { i -> MotionEvent.PointerCoords().apply { x = pts[i].first; y = pts[i].second; pressure = 1f; size = 1f } }
        return MotionEvent.obtain(downTime, downTime + t, action, pts.size, props, coords, 0, 0, 1f, 1f, 0, 0,
            android.view.InputDevice.SOURCE_TOUCHSCREEN, 0)
    }

    private fun tap(x: Float, y: Float, start: Long): List<Pair<Long, MotionEvent>> {
        downTime = SystemClock.uptimeMillis() + start
        return listOf(start to ev(0, MotionEvent.ACTION_DOWN, listOf(x to y)), start + 40 to ev(40, MotionEvent.ACTION_UP, listOf(x to y)))
    }

    private fun pinch(cx: Float, cy: Float, factor: Float): List<Pair<Long, MotionEvent>> {
        downTime = SystemClock.uptimeMillis()
        val r0 = intent0Radius
        val r1 = r0 * factor
        val out = mutableListOf<Pair<Long, MotionEvent>>()
        fun pts(r: Float) = listOf((cx - r) to cy, (cx + r) to cy)
        out += 0L to ev(0, MotionEvent.ACTION_DOWN, pts(r0).take(1))
        out += 10L to ev(10, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), pts(r0))
        for (i in 1..20) {
            val t = 10L + i * 16
            out += t to ev(t, MotionEvent.ACTION_MOVE, pts(r0 + (r1 - r0) * i / 20f))
        }
        out += 350L to ev(350, MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), pts(r1))
        out += 360L to ev(360, MotionEvent.ACTION_UP, pts(r1).take(1))
        return out
    }

    private fun drag(x: Float, y: Float, dx: Float, dy: Float, fingers: Int): List<Pair<Long, MotionEvent>> {
        downTime = SystemClock.uptimeMillis()
        val gap = 120f
        fun pts(f: Float) = (0 until fingers).map { (x + dx * f + it * gap) to (y + dy * f) }
        val out = mutableListOf<Pair<Long, MotionEvent>>()
        out += 0L to ev(0, MotionEvent.ACTION_DOWN, pts(0f).take(1))
        if (fingers > 1) out += 10L to ev(10, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), pts(0f))
        for (i in 1..20) {
            val t = 10L + i * 16
            out += t to ev(t, MotionEvent.ACTION_MOVE, pts(i / 20f))
        }
        if (fingers > 1) out += 350L to ev(350, MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), pts(1f))
        out += 360L to ev(360, MotionEvent.ACTION_UP, pts(1f).take(1))
        return out
    }
}

/** Debug builds only: remembers the resumed activity for [DebugGestureReceiver]. Started by the manifest. */
class ResumedActivityTracker : ContentProvider() {
    companion object {
        private var ref: WeakReference<Activity>? = null
        val current: Activity? get() = ref?.get()
    }

    override fun onCreate(): Boolean {
        (context?.applicationContext as? Application)?.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(a: Activity) { ref = WeakReference(a) }
                override fun onActivityCreated(a: Activity, b: Bundle?) {}
                override fun onActivityStarted(a: Activity) {}
                override fun onActivityPaused(a: Activity) {}
                override fun onActivityStopped(a: Activity) {}
                override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
                override fun onActivityDestroyed(a: Activity) {}
            })
        return true
    }

    override fun query(u: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?) = null
    override fun getType(u: Uri): String? = null
    override fun insert(u: Uri, v: ContentValues?): Uri? = null
    override fun delete(u: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(u: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
}
