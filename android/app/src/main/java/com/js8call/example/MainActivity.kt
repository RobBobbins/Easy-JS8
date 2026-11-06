package com.js8call.example

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceManager
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.snackbar.Snackbar
import com.js8call.example.api.Js8Api
import com.js8call.example.model.EngineState
import com.js8call.example.service.JS8EngineService
import com.js8call.example.ui.DecodeViewModel
import com.js8call.example.ui.MessagesViewModel
import com.js8call.example.ui.MonitorViewModel
import com.js8call.example.ui.TransmitViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNav: NavigationBarView
    private var onMonitorTab = true
    private lateinit var decodeViewModel: DecodeViewModel
    private lateinit var monitorViewModel: MonitorViewModel
    private var spectrumBroadcastCount: Long = 0
    private lateinit var messagesViewModel: MessagesViewModel
    private lateinit var transmitViewModel: TransmitViewModel
    private lateinit var js8ActivityViewModel: com.js8call.example.js8ui.Js8ActivityViewModel

    private val decodeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                JS8EngineService.ACTION_DECODE -> {
                    val utc = intent.getIntExtra(JS8EngineService.EXTRA_UTC, 0)
                    val snr = intent.getIntExtra(JS8EngineService.EXTRA_SNR, 0)
                    val dt = intent.getFloatExtra(JS8EngineService.EXTRA_DT, 0f)
                    val freq = intent.getFloatExtra(JS8EngineService.EXTRA_FREQ, 0f)
                    val text = intent.getStringExtra(JS8EngineService.EXTRA_TEXT) ?: ""
                    val type = intent.getIntExtra(JS8EngineService.EXTRA_TYPE, 0)
                    val quality = intent.getFloatExtra(JS8EngineService.EXTRA_QUALITY, 0f)
                    val mode = intent.getIntExtra(JS8EngineService.EXTRA_MODE, 0)
                    val driftMs = intent.getIntExtra(JS8EngineService.EXTRA_DRIFT_MS, 0)
                    decodeViewModel.addDecode(utc, snr, dt, freq, text, type, quality, mode, driftMs)
                    // Easy JS8: desktop Band / Call Activity and Message Window
                    js8ActivityViewModel.myOffset = monitorViewModel.getTxOffset().toInt()
                    js8ActivityViewModel.onFrame(com.js8call.example.model.DecodedMessage(
                        utc, snr, dt, freq, text, type, quality, mode, driftMs))
                    monitorViewModel.updateSnr(snr)
                }
                JS8EngineService.ACTION_APRS_NOTICE ->
                    js8ActivityViewModel.writeNotice(intent.getStringExtra(JS8EngineService.EXTRA_TEXT) ?: "")
                JS8EngineService.ACTION_QUEUE_TX -> {
                    val text = intent.getStringExtra(JS8EngineService.EXTRA_QUEUE_TX_TEXT) ?: return
                    val directed = intent.getStringExtra(JS8EngineService.EXTRA_QUEUE_TX_DIRECTED)
                    val priority = intent.getIntExtra(JS8EngineService.EXTRA_QUEUE_TX_PRIORITY, 0)
                    transmitViewModel.queueMessage(text, directed, priority, clearComposed = false)
                    // Trigger queue processing
                    processNextTxIfIdle()
                }
                JS8EngineService.ACTION_TX_STATE -> {
                    val state = intent.getStringExtra(JS8EngineService.EXTRA_TX_STATE)
                    when (state) {
                        JS8EngineService.TX_STATE_FINISHED, JS8EngineService.TX_STATE_FAILED -> {
                            // Easy JS8: the typed message leaves the Typing Box once it is done
                            js8ActivityViewModel.onTxEnded()
                            // Update ViewModel state first
                            if (state == JS8EngineService.TX_STATE_FINISHED) {
                                transmitViewModel.transmissionComplete()
                            } else {
                                transmitViewModel.transmissionFailed()
                            }
                            // Process next item in queue after TX completes
                            processNextTxIfIdle()
                        }
                        JS8EngineService.TX_STATE_QUEUED -> {
                            js8ActivityViewModel.onTxQueued(intent.getIntExtra(JS8EngineService.EXTRA_TX_FRAME_COUNT, 0))
                            transmitViewModel.setQueued()
                        }
                        JS8EngineService.TX_STATE_STARTED -> {
                            transmitViewModel.startTransmitting()
                        }
                    }
                }
                JS8EngineService.ACTION_FT8_DECODED -> {
                    // Easy JS8: FT8 heard in the JS8 audio, one line in the Message Window
                    js8ActivityViewModel.onFt8(
                        intent.getLongExtra(JS8EngineService.EXTRA_FT8_UTC_MS, System.currentTimeMillis()),
                        intent.getFloatExtra(JS8EngineService.EXTRA_FT8_FREQ, 0f).toInt(),
                        intent.getStringExtra(JS8EngineService.EXTRA_FT8_TEXT).orEmpty(),
                        intent.getStringExtra(JS8EngineService.EXTRA_FT8_CALL_DE).orEmpty()
                    )
                }
                JS8EngineService.ACTION_TX_FRAME -> {
                    // Easy JS8: each part of our transmission in the Message Window as it goes out
                    js8ActivityViewModel.onTxFrame(
                        intent.getStringExtra(JS8EngineService.EXTRA_TX_FRAME_TEXT).orEmpty(),
                        intent.getIntExtra(JS8EngineService.EXTRA_TX_FRAME_INDEX, 0),
                        intent.getIntExtra(JS8EngineService.EXTRA_TX_FRAME_COUNT, 1),
                        intent.getIntExtra(JS8EngineService.EXTRA_TX_FRAME_CHARS_SENT, 0),
                        intent.getDoubleExtra(JS8EngineService.EXTRA_TX_OFFSET_HZ, 0.0).toInt(),
                        intent.getStringExtra(JS8EngineService.EXTRA_TX_FRAME_FULL_TEXT).orEmpty(),
                        intent.getBooleanExtra(JS8EngineService.EXTRA_TX_FRAME_IS_DATA, false)
                    )
                }
                ACTION_PROCESS_TX_QUEUE -> {
                    android.util.Log.d("MainActivity", "Received ACTION_PROCESS_TX_QUEUE")
                    processNextTxIfIdle()
                }
            }
        }
    }

    private val monitorReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                JS8EngineService.ACTION_ENGINE_STATE -> {
                    val state = when (intent.getStringExtra(JS8EngineService.EXTRA_STATE)) {
                        JS8EngineService.STATE_RUNNING -> EngineState.RUNNING
                        JS8EngineService.STATE_STOPPED -> EngineState.STOPPED
                        JS8EngineService.STATE_STARTING -> EngineState.STARTING
                        JS8EngineService.STATE_ERROR -> EngineState.ERROR
                        else -> EngineState.ERROR
                    }
                    monitorViewModel.updateState(state)
                    if (state == EngineState.STOPPED || state == EngineState.ERROR) {
                        transmitViewModel.engineStopped()
                    }
                    if (state == EngineState.RUNNING) {
                        processNextTxIfIdle()
                    }
                }
                JS8EngineService.ACTION_SPECTRUM -> {
                    val bins = intent.getFloatArrayExtra(JS8EngineService.EXTRA_BINS)
                    val binHz = intent.getFloatExtra(JS8EngineService.EXTRA_BIN_HZ, 0f)
                    val powerDb = intent.getFloatExtra(JS8EngineService.EXTRA_POWER_DB, 0f)
                    val peakDb = intent.getFloatExtra(JS8EngineService.EXTRA_PEAK_DB, 0f)
                    if (bins != null) {
                        spectrumBroadcastCount += 1
                        if (spectrumBroadcastCount % 20L == 0L) {
                            android.util.Log.i(
                                "JS8MainActivity",
                                "Spectrum broadcasts: count=$spectrumBroadcastCount bins=${bins.size} binHz=$binHz power=$powerDb peak=$peakDb"
                            )
                        }
                        monitorViewModel.updateSpectrum(bins, binHz, powerDb, peakDb)
                    }
                }
                JS8EngineService.ACTION_AUDIO_DEVICE -> {
                    val deviceName = intent.getStringExtra(JS8EngineService.EXTRA_AUDIO_DEVICE) ?: "Unknown"
                    monitorViewModel.updateAudioDevice(deviceName)
                }
                JS8EngineService.ACTION_ERROR -> {
                    val message = intent.getStringExtra(JS8EngineService.EXTRA_ERROR_MESSAGE) ?: "Unknown error"
                    monitorViewModel.onError(message)
                }
                JS8EngineService.ACTION_AUDIO_DEVICE_LOST -> {
                    val device = intent.getStringExtra(JS8EngineService.EXTRA_AUDIO_DEVICE) ?: "Audio device"
                    val message = getString(R.string.monitor_audio_device_lost, device)
                    monitorViewModel.onError(message)
                    if (!onMonitorTab) Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                }
                JS8EngineService.ACTION_RADIO_FREQUENCY -> {
                    val frequencyHz = intent.getLongExtra(JS8EngineService.EXTRA_RADIO_FREQUENCY_HZ, 0L)
                    if (frequencyHz > 0) {
                        monitorViewModel.updateFrequency(frequencyHz)
                    }
                }
                JS8EngineService.ACTION_TIME_DRIFT -> {
                    val driftMs = intent.getLongExtra(JS8EngineService.EXTRA_TIME_DRIFT_MS, 0L)
                    monitorViewModel.updateTimeDrift(driftMs)
                }
                JS8EngineService.ACTION_AUTO_DRIFT ->
                    monitorViewModel.updateAutoDrift(intent.getBooleanExtra(JS8EngineService.EXTRA_AUTO_DRIFT_ON, false))
                JS8EngineService.ACTION_RIG_STATUS -> {
                    val connected = intent.getBooleanExtra(JS8EngineService.EXTRA_RIG_CONNECTED, false)
                    monitorViewModel.updateRigConnected(connected, intent.getBooleanExtra(JS8EngineService.EXTRA_RIG_OPENING, false))
                }
            }
        }
    }

    // ---- Scheduled Frequency Change (PC tryBandHop's SelfDestructMessageBox), over every screen --

    private var hopDialog: androidx.appcompat.app.AlertDialog? = null
    private var hopCountdown: Runnable? = null
    private val hopUi = android.os.Handler(android.os.Looper.getMainLooper())

    private val hopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = showScheduledHop()
    }

    private fun answerScheduledHop(ok: Boolean) {
        startService(Intent(this, JS8EngineService::class.java).apply {
            action = JS8EngineService.ACTION_SCHEDULE_HOP_ANSWER
            putExtra(JS8EngineService.EXTRA_SCHEDULE_HOP_OK, ok)
        })
    }

    private fun closeHopDialog() {
        hopCountdown?.let { hopUi.removeCallbacks(it) }
        hopCountdown = null
        hopDialog?.dismiss()
        hopDialog = null
    }

    /** The change the service is counting down, if any; also after a rotation or a return to the app. */
    private fun showScheduledHop() {
        closeHopDialog()
        val hz = JS8EngineService.pendingHopHz
        if (hz == 0L) return
        val mhz = String.format(java.util.Locale.US, "%.6f", hz / 1_000_000.0)
        fun left() = ((JS8EngineService.pendingHopEndMs - System.currentTimeMillis()) / 1000).toInt()
        fun text() = "A scheduled frequency change has arrived. The rig frequency will be changed to $mhz MHz in ${left()} second(s)."
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Scheduled Frequency Change").setMessage(text())
            .setPositiveButton(android.R.string.ok) { _, _ -> closeHopDialog(); answerScheduledHop(true) }
            .setNegativeButton(android.R.string.cancel) { _, _ -> closeHopDialog(); answerScheduledHop(false) }
            .setCancelable(false).show()
        hopDialog = dialog
        hopCountdown = object : Runnable {
            override fun run() {
                if (left() <= 0 || JS8EngineService.pendingHopHz == 0L) { closeHopDialog(); return } // the service changes it on its own 30 second timer
                dialog.setMessage(text())
                hopUi.postDelayed(this, 1000)
            }
        }.also { hopUi.postDelayed(it, 1000) }
    }

    // ---- Idle Timeout (PC tx_watchdog) ------------------------------------------------------------

    private var idleDialog: androidx.appcompat.app.AlertDialog? = null

    private val idleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            transmitViewModel.clearQueue() // PC resetMessageTransmitQueue
            if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) showIdleTimeout() // else onStart shows it
        }
    }

    private fun showIdleTimeout() {
        val minutes = JS8EngineService.idleTimedOutMinutes
        if (minutes == 0L || idleDialog != null) return
        idleDialog = androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Idle Timeout")
            .setMessage("You have been idle for more than $minutes minutes.")
            .setPositiveButton(android.R.string.ok) { _, _ ->
                idleDialog = null
                startService(Intent(this, JS8EngineService::class.java).apply { action = JS8EngineService.ACTION_IDLE_OK })
            }
            .setCancelable(false).show()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        JS8EngineService.lastUserMs = System.currentTimeMillis()
    }

    // ---- Autoreply Confirmation Required (PC confirmThenEnqueueMessage), over every screen -------

    private var confirmDialog: androidx.appcompat.app.AlertDialog? = null
    private var confirmShownId = -1

    private val confirmReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = showAutoReplyConfirm()
    }

    private fun answerAutoReply(id: Int, accepted: Boolean) {
        startService(Intent(this, JS8EngineService::class.java).apply {
            action = JS8EngineService.ACTION_AUTOREPLY_CONFIRM_ANSWER
            putExtra(JS8EngineService.EXTRA_CONFIRM_ID, id)
            putExtra(JS8EngineService.EXTRA_CONFIRM_ACCEPTED, accepted)
        })
    }

    private fun closeConfirmDialog() {
        confirmDialog?.dismiss()
        confirmDialog = null
        confirmShownId = -1
    }

    /** The oldest autoreply waiting; the next one shows when it is answered or runs out. */
    private fun showAutoReplyConfirm() {
        val c = JS8EngineService.autoReplyConfirms.firstOrNull()
        if (c?.id == confirmShownId) return
        closeConfirmDialog()
        if (c == null) return
        confirmShownId = c.id
        confirmDialog = androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Autoreply Confirmation Required")
            .setMessage("A transmission is queued for autoreply:\n\n${c.text}\n\nWould you like to send this transmission?")
            .setPositiveButton("Yes") { _, _ -> answerAutoReply(c.id, true) }
            .setNegativeButton("No") { _, _ -> answerAutoReply(c.id, false) }
            .setCancelable(false).show()
    }

    companion object {
        /** PC PriorityNormal for TX.SEND_MESSAGE: the service's PRIORITY_NORMAL, a queued reply's. */
        const val API_PRIORITY_NORMAL = 100
        private const val INTRO_HOLD_MS = 1000L
        private const val INTRO_LAND_MS = 300L
        private const val INTRO_LAND_FADE_MS = 150L
        private const val INTRO_NO_SPLASH_MS = 1000L
        /** Logo box (left, top, right, bottom) as fractions of Rob's intro images and of the r2 icon. */
        private val INTRO_LOGO_PORT = floatArrayOf(0.3991f, 0.4162f, 0.6231f, 0.5286f)
        private val INTRO_LOGO_LAND = floatArrayOf(0.4534f, 0.3185f, 0.5568f, 0.5620f)
        private val ICON_LOGO = floatArrayOf(0.2963f, 0.2616f, 0.7037f, 0.7083f)
        private const val INTRO_FADE_OUT_MS = 500L
        private const val REQUEST_RECORD_AUDIO = 1
        private const val REQUEST_BLUETOOTH_CONNECT = 3
        private const val REQUEST_LOCATION = 2
        private const val PREF_KEEP_SCREEN_ON = "keep_screen_on"
        const val ACTION_PROCESS_TX_QUEUE = "com.js8call.example.ACTION_PROCESS_TX_QUEUE"
    }

    private val prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == PREF_KEEP_SCREEN_ON) {
            applyKeepScreenOn(prefs.getBoolean(PREF_KEEP_SCREEN_ON, false))
        }
    }

    /** False until [setUpMain] has run; the intro draws first at app start. */
    private var mainReady = false

    /** Runs [block] once the frame now being prepared has been drawn. */
    private fun afterNextFrame(block: () -> Unit) {
        android.view.Choreographer.getInstance().postFrameCallback { hopUi.post(block) }
    }

    /**
     * Easy JS8 intro: Rob's image (res/drawable-port-nodpi and -land-nodpi js8_intro.png), cropped to fill the
     * screen. It is the app's first frame, so Android's start screen goes as early as it can; on Android 12+ its
     * logo moves onto the intro logo ([landSplashLogo]). The app is set up behind the intro, which holds for
     * [INTRO_HOLD_MS] once the main screen is drawn, then fades into it. A tap skips the hold.
     */
    private fun showIntro() {
        val intro = android.widget.ImageView(this).apply {
            setImageResource(R.drawable.js8_intro)
            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(0xFF04061A.toInt())
            isClickable = true
        }
        setContentView(intro)
        var setUpStarted = false
        fun startSetUp() {
            if (setUpStarted) return
            setUpStarted = true
            setUpBehindIntro(intro)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Set-up blocks the main thread, so it waits for the move to end
            var splashSeen = false
            splashScreen.setOnExitAnimationListener { splash ->
                splashSeen = true
                landSplashLogo(splash, intro) { startSetUp() }
            }
            // A start with no start screen
            afterNextFrame { intro.postDelayed({ if (!splashSeen) startSetUp() }, INTRO_NO_SPLASH_MS) }
        } else {
            startSetUp()
        }
    }

    /**
     * Android's start screen logo shrinks and moves onto the intro logo over [INTRO_LAND_MS], then the start
     * screen fades out over [INTRO_LAND_FADE_MS]; both screens are the same navy.
     */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.S)
    private fun landSplashLogo(splash: android.window.SplashScreenView, intro: android.widget.ImageView, done: () -> Unit) {
        val icon = splash.iconView
        val image = intro.drawable
        if (icon == null || image == null || intro.width == 0 || icon.width == 0) {
            splash.remove()
            done()
            return
        }
        // Intro logo on the screen: the image is centre-cropped
        val f = if (intro.width > intro.height) INTRO_LOGO_LAND else INTRO_LOGO_PORT
        val iw = image.intrinsicWidth
        val ih = image.intrinsicHeight
        val s = maxOf(intro.width / iw.toFloat(), intro.height / ih.toFloat())
        val at = IntArray(2).also { intro.getLocationOnScreen(it) }
        val left = at[0] + (intro.width - iw * s) / 2
        val top = at[1] + (intro.height - ih * s) / 2
        val tW = (f[2] - f[0]) * iw * s
        val tH = (f[3] - f[1]) * ih * s
        val tCx = left + (f[0] + f[2]) / 2 * iw * s
        val tCy = top + (f[1] + f[3]) / 2 * ih * s
        // Start-screen logo: the icon view shows the middle 72 of the icon's 108 dp, so the r2 image is drawn 1.5
        // times the view's size, a quarter of the view past each edge (measured on SM-X210: 146 px logo, 240 px view)
        val iconAt = IntArray(2).also { icon.getLocationOnScreen(it) }
        val cW = icon.width * 1.5f
        val cH = icon.height * 1.5f
        val lW = (ICON_LOGO[2] - ICON_LOGO[0]) * cW
        val lH = (ICON_LOGO[3] - ICON_LOGO[1]) * cH
        val lCx = (ICON_LOGO[0] + ICON_LOGO[2]) / 2 * cW - icon.width * 0.25f
        val lCy = (ICON_LOGO[1] + ICON_LOGO[3]) / 2 * cH - icon.height * 0.25f
        val k = (tW / lW + tH / lH) / 2
        android.util.Log.d("Js8Intro", "icon at ${iconAt[0]},${iconAt[1]} ${icon.width}x${icon.height}, intro logo ${tW}x$tH at $tCx,$tCy, scale $k")
        icon.pivotX = 0f
        icon.pivotY = 0f
        icon.animate().scaleX(k).scaleY(k)
            .translationX(tCx - iconAt[0] - k * lCx).translationY(tCy - iconAt[1] - k * lCy)
            .setDuration(INTRO_LAND_MS)
            .withEndAction {
                splash.animate().alpha(0f).setDuration(INTRO_LAND_FADE_MS)
                    .withEndAction { splash.remove(); done() }.start()
            }.start()
    }

    /** The intro waiting for [onStart]: the app was put away before its set-up ran. */
    private var introWaiting: android.widget.ImageView? = null

    /** Adds the main screen under the intro and sets the app up, then holds and fades the intro. */
    private fun setUpBehindIntro(intro: android.widget.ImageView) {
        afterNextFrame {
            if (isDestroyed) return@afterNextFrame
            // The screens can't be added once Android has saved the app's state (locked phone, Home pressed)
            if (supportFragmentManager.isStateSaved) {
                introWaiting = intro
                return@afterNextFrame
            }
            val root = findViewById<android.view.ViewGroup>(android.R.id.content)
            root.addView(layoutInflater.inflate(R.layout.activity_main, root, false), 0)
            setUpMain()
            afterNextFrame {
                val fadeOut = Runnable {
                    intro.setOnClickListener(null)
                    intro.animate().alpha(0f).setDuration(INTRO_FADE_OUT_MS)
                        .withEndAction { root.removeView(intro) }.start()
                }
                intro.postDelayed(fadeOut, INTRO_HOLD_MS)
                intro.setOnClickListener {
                    intro.removeCallbacks(fadeOut)
                    fadeOut.run()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            showIntro()
        } else {
            setContentView(R.layout.activity_main)
            setUpMain()
        }
    }

    /** The screens, view models, receivers and permission checks; behind the intro at app start. */
    private fun setUpMain() {
        mainReady = true

        // Set up bottom navigation
        bottomNav = findViewById(R.id.bottom_navigation)
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        bottomNav.setupWithNavController(navController)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            // Easy JS8 desktop-copy screens have no bottom tab bar
            bottomNav.visibility = if (destination.id == R.id.navigation_js8_main ||
                destination.id == R.id.navigation_js8_menu ||
                destination.id == R.id.navigation_js8_settings ||
                destination.id == R.id.navigation_js8_station ||
                destination.id == R.id.navigation_js8_aprs ||
                destination.id == R.id.navigation_js8_log ||
                destination.id == R.id.navigation_js8_export ||
                destination.id == R.id.navigation_js8_inbox) android.view.View.GONE else android.view.View.VISIBLE
            onMonitorTab = destination.id == R.id.navigation_monitor
            if (destination.id == R.id.navigation_conversation) {
                bottomNav.menu.findItem(R.id.navigation_messages).isChecked = true
            }
        }

        decodeViewModel = ViewModelProvider(this)[DecodeViewModel::class.java]
        monitorViewModel = ViewModelProvider(this)[MonitorViewModel::class.java]
        // A new screen while the engine already runs: the one-time RUNNING went out before it was here (S20, 2026-11-06)
        if (Js8Api.State.monitoring && monitorViewModel.status.value?.state == EngineState.STOPPED) {
            monitorViewModel.updateState(EngineState.RUNNING)
        }
        messagesViewModel = ViewModelProvider(this)[MessagesViewModel::class.java]
        transmitViewModel = ViewModelProvider(this)[TransmitViewModel::class.java]
        js8ActivityViewModel = ViewModelProvider(this)[com.js8call.example.js8ui.Js8ActivityViewModel::class.java]
        decodeViewModel.loadPersistedDecodesIfEnabled()

        // PC isMessageQueuedForTransmit, for the service's Scheduled Frequency Change check
        transmitViewModel.txState.observe(this) {
            JS8EngineService.messageQueued = it != com.js8call.example.model.TransmitState.IDLE
        }

        // Easy JS8: Station > Reporting API (and WSJT-X, N3FJP, N1MM)
        Js8Api.init(this)
        Js8Api.activity = js8ActivityViewModel.activity
        Js8Api.host = apiHost
        JS8EngineService.callHeardMs = { call -> js8ActivityViewModel.activity.callActivity[call]?.utcMs }
        JS8EngineService.callActivityList = { js8ActivityViewModel.activity.callActivity.values.map { it.copy() } }

        // N51: decodes, replies, the TX queue and the rig keep going with the screen off, as the PC does with its monitor
        // off. Registered until onDestroy, not onStop.
        val filter = IntentFilter().apply {
            addAction(JS8EngineService.ACTION_DECODE)
            addAction(JS8EngineService.ACTION_QUEUE_TX)
            addAction(JS8EngineService.ACTION_APRS_NOTICE)
            addAction(JS8EngineService.ACTION_TX_STATE)
            addAction(JS8EngineService.ACTION_TX_FRAME)
            addAction(JS8EngineService.ACTION_FT8_DECODED)
            addAction(ACTION_PROCESS_TX_QUEUE)
        }
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(decodeReceiver, filter)

        val monitorFilter = IntentFilter().apply {
            addAction(JS8EngineService.ACTION_ENGINE_STATE)
            addAction(JS8EngineService.ACTION_SPECTRUM)
            addAction(JS8EngineService.ACTION_AUDIO_DEVICE)
            addAction(JS8EngineService.ACTION_ERROR)
            addAction(JS8EngineService.ACTION_AUDIO_DEVICE_LOST)
            addAction(JS8EngineService.ACTION_RADIO_FREQUENCY)
            addAction(JS8EngineService.ACTION_TIME_DRIFT)
            addAction(JS8EngineService.ACTION_AUTO_DRIFT)
            addAction(JS8EngineService.ACTION_RIG_STATUS)
        }
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(monitorReceiver, monitorFilter)
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(idleReceiver, IntentFilter(JS8EngineService.ACTION_IDLE_TIMEOUT))
        txQueueUi.postDelayed(txQueueTicker, 1000)

        // Observe unread count for badge on Messages tab
        messagesViewModel.totalUnreadCount.observe(this) { count ->
            val badge = bottomNav.getOrCreateBadge(R.id.navigation_messages)
            if (count > 0) {
                badge.isVisible = true
                badge.number = count
            } else {
                badge.isVisible = false
            }
        }

        // Check permissions
        checkPermissions()

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        applyKeepScreenOn(prefs.getBoolean(PREF_KEEP_SCREEN_ON, false))
    }

    override fun onStart() {
        super.onStart()
        introWaiting?.let { introWaiting = null; setUpBehindIntro(it) }
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        applyKeepScreenOn(prefs.getBoolean(PREF_KEEP_SCREEN_ON, false))
        // These two only show a box; onStart shows the one waiting
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(hopReceiver, IntentFilter(JS8EngineService.ACTION_SCHEDULE_HOP))
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(confirmReceiver, IntentFilter(JS8EngineService.ACTION_AUTOREPLY_CONFIRM))
        showScheduledHop()
        showAutoReplyConfirm()
        showIdleTimeout()
    }

    override fun onStop() {
        PreferenceManager.getDefaultSharedPreferences(this)
            .unregisterOnSharedPreferenceChangeListener(prefsListener)
        LocalBroadcastManager.getInstance(this)
            .unregisterReceiver(hopReceiver)
        LocalBroadcastManager.getInstance(this)
            .unregisterReceiver(confirmReceiver)
        closeHopDialog()
        closeConfirmDialog()
        idleDialog?.dismiss()
        idleDialog = null
        if (mainReady) decodeViewModel.persistDecodesOnStop()
        if (isFinishing && !isChangingConfigurations) {
            Js8Api.closing() // PC closeEvent: STATION.CLOSING
            stopEngineService()
            com.js8call.example.js8ui.Js8MainFragment.appClosed()
        }
        super.onStop()
    }

    override fun onDestroy() {
        txQueueUi.removeCallbacks(txQueueTicker)
        LocalBroadcastManager.getInstance(this)
            .unregisterReceiver(decodeReceiver)
        LocalBroadcastManager.getInstance(this)
            .unregisterReceiver(monitorReceiver)
        LocalBroadcastManager.getInstance(this)
            .unregisterReceiver(idleReceiver)
        if (Js8Api.host === apiHost) Js8Api.host = null
        JS8EngineService.callHeardMs = null
        JS8EngineService.callActivityList = null
        super.onDestroy()
    }

    /** API commands that use the transmit queue (TX.SEND_MESSAGE, TX.GET_QUEUE_DEPTH, WINDOW.RAISE). */
    private val apiHost = object : Js8Api.Host {
        override fun sendMessage(text: String) {
            // PC enqueueMessage(PriorityNormal, ...): like a queued reply, it waits for an empty Typing Box and goes
            // out only with AUTO on; otherwise it is put in the Typing Box (N53)
            transmitViewModel.queueMessage(text, null, API_PRIORITY_NORMAL, clearComposed = false)
            processNextTxIfIdle()
        }

        override fun queueDepth(): Int = transmitViewModel.queue.value?.size ?: 0

        override fun raise() {
            startActivity(Intent(this@MainActivity, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    // PC processTxQueue runs once a second (mainwindow.cpp 2800): a reply waiting for the Typing Box goes when it empties
    private val txQueueUi = android.os.Handler(android.os.Looper.getMainLooper())
    private val txQueueTicker = object : Runnable {
        override fun run() {
            if (!transmitViewModel.queue.value.isNullOrEmpty()) processNextTxIfIdle()
            txQueueUi.postDelayed(this, 1000)
        }
    }

    /**
     * Process the next message in the TX queue if not currently transmitting.
     */
    private fun processNextTxIfIdle() {
        // Don't send if already transmitting
        val state = transmitViewModel.txState.value
        android.util.Log.d("MainActivity", "processNextTxIfIdle: state=$state")
        if (state == com.js8call.example.model.TransmitState.TRANSMITTING) {
            android.util.Log.d("MainActivity", "processNextTxIfIdle: already transmitting, skipping")
            return
        }
        // Easy JS8: between the parts of a message the state is QUEUED; wait for it to finish
        if (transmitViewModel.isBusy()) {
            android.util.Log.d("MainActivity", "processNextTxIfIdle: message still going out, waiting")
            return
        }
        
        // Easy JS8 TX button: transmitter disabled, keep the queue until it is enabled again
        if (!androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean("js8cfg_tx_enabled", true)) {
            android.util.Log.d("MainActivity", "processNextTxIfIdle: TX disabled")
            return
        }

        // PC processTxQueue: a queued reply or heartbeat ACK (priority above 0) waits for an empty Typing Box
        // (mainwindow.cpp 6628-6631). The message sent from the Typing Box and CQ go as before.
        val boxFree = js8ActivityViewModel.typingBoxFree()
        val nextMessage = transmitViewModel.getNextMessage { boxFree || it.priority == 0 }
        android.util.Log.d("MainActivity", "processNextTxIfIdle: nextMessage=$nextMessage")
        if (nextMessage == null) {
            android.util.Log.d("MainActivity", "processNextTxIfIdle: no message in queue")
            return
        }

        // PC: it goes out with AUTO on, or when it is high priority, a heartbeat or an ACK; otherwise it is put
        // in the Typing Box for you to send (mainwindow.cpp 6650-6664, N1)
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        val text = nextMessage.text
        val sendsNow = nextMessage.priority >= 1000 || text.contains(" HEARTBEAT ") || text.contains(" HB ") ||
            text.contains(" ACK ") || prefs.getBoolean("autoreply_enabled", false)
        if (nextMessage.priority > 0 && !sendsNow) {
            val controller = Js8Api.controller
            if (controller == null || js8ActivityViewModel.typed.value != null) return // no main screen yet: it waits
            transmitViewModel.removeFromQueue(nextMessage)
            val my = prefs.getString("callsign", "").orEmpty().trim().uppercase()
            controller.setTypingText(if (my.isNotEmpty() && text.startsWith("$my: ")) text.removePrefix("$my: ") else text)
            return
        }

        // Easy JS8: the Message Window shows each part as it goes out (ACTION_TX_FRAME), not here
        js8ActivityViewModel.onSentToEngine(nextMessage.text)
        transmitViewModel.markSent(nextMessage)

        // Send to service for transmission
        android.util.Log.d("MainActivity", "processNextTxIfIdle: sending to service: text='${nextMessage.text}' directed=${nextMessage.directed}")
        val txIntent = Intent(this, JS8EngineService::class.java).apply {
            action = JS8EngineService.ACTION_TRANSMIT_MESSAGE
            putExtra(JS8EngineService.EXTRA_TX_TEXT, nextMessage.text)
            nextMessage.directed?.let { putExtra(JS8EngineService.EXTRA_TX_DIRECTED, it) }
            putExtra(JS8EngineService.EXTRA_TX_FREQ_HZ, transmitViewModel.getTxOffset().toDouble())
        }
        startService(txIntent)
    }

    private fun checkPermissions() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted
            }
            shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> {
                // Show explanation
                Snackbar.make(
                    findViewById(android.R.id.content),
                    R.string.permission_audio_message,
                    Snackbar.LENGTH_LONG
                ).setAction("Grant") {
                    requestAudioPermission()
                }.show()
            }
            else -> {
                // Request permission
                requestAudioPermission()
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasConnect = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
            val hasScan = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
            when {
                hasConnect && hasScan -> {
                    // Permission already granted
                }
                shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_CONNECT) ||
                    shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_SCAN) -> {
                    Snackbar.make(
                        findViewById(android.R.id.content),
                        "Bluetooth permission needed for device access",
                        Snackbar.LENGTH_LONG
                    ).setAction("Grant") {
                        requestBluetoothPermission()
                    }.show()
                }
                else -> {
                    requestBluetoothPermission()
                }
            }
        }
    }

    private fun requestAudioPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            REQUEST_RECORD_AUDIO
        )
    }

    private fun requestBluetoothPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            ),
            REQUEST_BLUETOOTH_CONNECT
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            REQUEST_RECORD_AUDIO -> {
                if (grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED
                ) {
                    // Permission granted
                    Snackbar.make(
                        findViewById(android.R.id.content),
                        "Microphone permission granted",
                        Snackbar.LENGTH_SHORT
                    ).show()
                } else {
                    // Permission denied
                    Snackbar.make(
                        findViewById(android.R.id.content),
                        R.string.permission_audio_denied,
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }
            REQUEST_BLUETOOTH_CONNECT -> {
                val granted = grantResults.isNotEmpty() &&
                    grantResults.all { it == PackageManager.PERMISSION_GRANTED }
                if (granted) {
                    Snackbar.make(
                        findViewById(android.R.id.content),
                        "Bluetooth permission granted",
                        Snackbar.LENGTH_SHORT
                    ).show()
                } else {
                    Snackbar.make(
                        findViewById(android.R.id.content),
                        "Bluetooth permission denied; Bluetooth devices may not work",
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun applyKeepScreenOn(enabled: Boolean) {
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun stopEngineService() {
        val intent = Intent(this, JS8EngineService::class.java).apply {
            action = JS8EngineService.ACTION_STOP
        }
        startService(intent)
    }
}
