package com.js8call.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRecorder
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import com.js8call.core.BluetoothSerialBridge
import com.js8call.core.BluetoothSerialPortCatalog
import com.js8call.core.HamlibRigControl
import com.js8call.core.JS8AudioHelper
import com.js8call.core.JS8Engine
import com.js8call.core.QmxDirectSerial
import com.js8call.core.TruSdxDirectSerial
import com.js8call.core.UsbSerialBridge
import com.js8call.core.UsbSerialPortCatalog
import com.js8call.example.MainActivity
import com.js8call.example.model.DecodedMessage
import com.js8call.example.ui.AudioDevices
import com.js8call.example.MessageLogWriter
import com.js8call.example.R
import com.js8call.example.BuildConfig
import com.js8call.example.api.Js8Api
import com.js8call.example.data.Js8LogBook
import com.js8call.example.data.Js8Mailbox
import com.js8call.example.data.MessageRepository
import com.js8call.example.network.PskReporterClient
import com.js8call.example.network.SpotClient
import com.js8call.example.util.CallsignValidator
import com.js8call.example.util.Js8Commands
import com.js8call.example.util.TxMessageClassifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

internal fun assembleMsgPayload(parts: List<String>): String = parts.joinToString(separator = "")

internal fun findClosestMsgBufferKey(
    keys: Iterable<Int>,
    frequency: Float,
    toleranceHz: Int = 10
): Int? {
    val rounded = Math.round(frequency)
    return keys
        .map { key -> key to Math.abs(key - rounded) }
        .filter { (_, distance) -> distance <= toleranceHz }
        .minByOrNull { (_, distance) -> distance }
        ?.first
}

/** PC hasExistingMessageBuffer(drift = true): the buffer at [key] moves to the offset of its newest frame. */
internal fun <T> MutableMap<Int, T>.followDrift(key: Int, frequency: Float): Int {
    val offset = Math.round(frequency)
    if (offset != key) remove(key)?.let { this[offset] = it }
    return offset
}

/**
 * Foreground service for running the JS8 engine in the background.
 *
 * This service manages the native engine lifecycle, audio capture,
 * and broadcasts decode events to the UI.
 */
class JS8EngineService : Service() {

    private val messageScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val messageRepository by lazy { MessageRepository.getInstance(applicationContext) }
    private var engine: JS8Engine? = null
    private var audioHelper: JS8AudioHelper? = null
    private var rigCtlClient: NetworkRig? = null
    private var rigCtlConnected: Boolean = false
    private var rigCtlErrorShown: Boolean = false
    private var rigControlMode: String = "none"
    private var hamlibRigControl: HamlibRigControl? = null
    private var hamlibRigConnected: Boolean = false
    /** Last poll of the rig (PC transceiver state). */
    private var rigState = NetworkRig.State()
    /** Split Operation Rig left split on at key-off; turned off when the setting moves away. */
    private var rigSplitLeftOn = false
    private var rtsPttConnected: Boolean = false
    private var trusdxConnected: Boolean = false
    private var qmxConnected: Boolean = false
    private var uvproConnected: Boolean = false
    private var rtsPttTransport: SerialTransport? = null
    private var usbSerialBridge: UsbSerialBridge? = null
    private var pttUsbBridge: UsbSerialBridge? = null // Station > Radio > PTT Port
    private var bluetoothSerialBridge: BluetoothSerialBridge? = null
    private var trusdxDirectSerial: TruSdxDirectSerial? = null
    private var trusdxSerialSession: TruSdxSerialSession? = null
    private var qmxDirectSerial: QmxDirectSerial? = null
    private var qmxCatSession: QmxCatSession? = null
    private var uvProSession: UvProSession? = null
    @Volatile private var trusdxInitInProgress: Boolean = false
    @Volatile private var engineStartGeneration: Int = 0
    @Volatile private var engineStartInProgress: Boolean = false
    @Volatile private var trusdxStartupWorkerActive: Boolean = false
    @Volatile private var qmxStartupWorkerActive: Boolean = false
    @Volatile private var uvproSessionGeneration: Int = 0
    @Volatile private var trusdxParserResyncs: Long = 0
    @Volatile private var trusdxRxFrames: Long = 0
    @Volatile private var trusdxRxSamples: Long = 0
    @Volatile private var trusdxTxFrames: Long = 0
    @Volatile private var trusdxTxSamples: Long = 0
    @Volatile private var trusdxTxDrops: Long = 0
    @Volatile private var trusdxTxSilentFrames: Long = 0
    @Volatile private var trusdxRxUnderruns: Long = 0
    @Volatile private var trusdxRxSubmitDrops: Long = 0
    @Volatile private var trusdxLastRxAudioNs: Long = 0L
    @Volatile private var trusdxLastRxRearmNs: Long = 0L
    @Volatile private var trusdxWatchdogToken: Int = 0
    @Volatile private var spectrumEventCount: Long = 0
    @Volatile private var trusdxRxRateWindowStartNs: Long = 0L
    @Volatile private var trusdxRxRateWindowSamples: Long = 0L
    @Volatile private var trusdxRxFrameDrops: Long = 0L
    @Volatile private var trusdxRxKeepaliveToken: Int = 0
    @Volatile private var trusdxRxKeepaliveCount: Long = 0L
    private val trusdxRxFrameQueue = LinkedBlockingDeque<ByteArray>(TRUSDX_RX_FRAME_QUEUE_MAX)
    @Volatile private var trusdxRxWorkerRunning = false
    @Volatile private var trusdxRxWorkerThread: Thread? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // Stop monitoring when selected audio device is lost
    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) {
            if (engine == null && !engineStartInProgress) return
            val lost = removed.firstOrNull { it.id == selectedAudioDeviceId } ?: return
            val name = AudioDevices.nameFor(lost) ?: "Audio device"
            Log.w(TAG, "Selected audio device removed: $name (ID: ${lost.id}); stopping engine")
            stopEngine()
            broadcastAudioDeviceLost(name)
            stopSelf()
        }
    }
    private val txHandlerThread = HandlerThread("Js8Tx")
    private lateinit var txHandler: Handler
    private lateinit var txMonitorHandler: Handler
    private var selectedAudioDeviceId: Int = -1  // -1 means use default
    private var selectedOutputDeviceId: Int = -1  // -1 means use default
    private var currentTxOffsetHz: Float = 1500f
    private var txMonitorActive = false
    private var txMonitorWasAudioActive = false
    @Volatile private var testPttOn = false
    private val testPttRelease = Runnable { testPtt(false) }
    private val pttStateLock = Any()
    @Volatile private var txPttGeneration = 0
    @Volatile private var rigPttAsserted = false
    private var rigPttDesired = false
    private var rigPttDesiredGeneration = 0
    private var rigPttCommandPending = false
    private var rigPttFailureCount = 0
    private var rigPttCompletion: ((Boolean) -> Unit)? = null
    private var pttShuttingDown = false
    @Volatile private var txPttFailed = false
    @Volatile private var txSessionActive = false
    @Volatile private var txAudioActive = false
    @Volatile private var trusdxTxIntentActive = false
    private var scoRoutingActive = false
    private var previousAudioMode = AudioManager.MODE_NORMAL
    private var scoRestartAttempts = 0
    private var scoSilenceCheckToken = 0
    private var scoStartToken = 0
    private var scoSourceIndex = 0
    private val scoSourceCandidates = intArrayOf(
        MediaRecorder.AudioSource.VOICE_RECOGNITION,
        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
        MediaRecorder.AudioSource.MIC
    )
    private var callsignWarningShown = false
    private var lastTxMessage: String = ""
    private var lastTxDirected: String = ""
    private var lastTxSubmode: Int = SUBMODE_NORMAL
    private var lastTxFrequencyHz: Double = DEFAULT_AUDIO_FREQUENCY_HZ
    private var messageLogger: MessageLogWriter? = null
    private val heartbeatRegex = Regex("^\\s*([^:]+):\\s+@HB\\s+HEARTBEAT\\b", RegexOption.IGNORE_CASE)
    private val cqRegex = Regex("^\\s*[^:]+:\\s+(@ALLCALL\\s+)?CQ\\b", RegexOption.IGNORE_CASE)
    private val notifiedCallsigns = mutableSetOf<String>() // PC m_callActivity: new entries notify once
    private val allCallReplies = mutableMapOf<String, Long>()
    private val recentOffsets = mutableMapOf<Int, Long>()
    private val relayBuffers = mutableMapOf<Int, RelayBuffer>()
    private val relayLock = Any()
    private val relayTargetRegex = Regex("^\\s*([A-Z0-9/]+)([> ])", RegexOption.IGNORE_CASE)
    private val relayPathRegex = Regex("\\s(?:\\*DE\\*|VIA)\\s([A-Z0-9/]+)", RegexOption.IGNORE_CASE)
    private val gridRegex = Regex("\\b[A-R]{2}[0-9]{2}([A-X]{2})?\\b", RegexOption.IGNORE_CASE)
    private val txMonitorRunnable = object : Runnable {
        override fun run() {
            val generation = txPttGeneration
            val activeEngine = engine
            if (activeEngine == null) {
                txMonitorActive = false
                return
            }
            val sessionActive = activeEngine.isTransmitting()
            val audioActive = activeEngine.isTransmittingAudio()
            val millisecondsUntilAudio = activeEngine.txMillisecondsUntilAudio()
            txSessionActive = sessionActive
            txAudioActive = audioActive
            if (!sessionActive) {
                txMonitorActive = false
                txMonitorWasAudioActive = false
                endTuning()
                apiTransmitting(false, activeEngine)
                trusdxTxIntentActive = false
                if (isRigControlConnected()) {
                    requestRigPtt(false, generation) { released ->
                        if (!isCurrentTxGeneration(generation)) return@requestRigPtt
                        if (released) {
                            broadcastTxState(TX_STATE_FINISHED)
                        } else {
                            broadcastError("Failed to release PTT after transmission")
                            broadcastTxState(TX_STATE_FAILED)
                        }
                    }
                } else {
                    if (isCurrentTxGeneration(generation)) {
                        broadcastTxState(TX_STATE_FINISHED)
                    }
                }
                return
            }

            val rigRequired = isRigPttRequired()
            val rigConnected = isRigControlConnected()
            if (rigRequired && !rigConnected) {
                failTransmitForPtt(generation, "Rig control is not connected")
                return
            }
            val pttLeadMs = pttLeadMs()
            if (rigConnected && !audioActive && millisecondsUntilAudio in 0..pttLeadMs) {
                requestRigPtt(true, generation)
            }

            if (audioActive && rigConnected && !isRigPttReady(generation)) {
                activeEngine.setTransmitReady(false)
                txMonitorHandler.postDelayed(this, TX_PREKEY_MONITOR_INTERVAL_MS)
                return
            }

            if (audioActive && !txMonitorWasAudioActive) {
                txMonitorWasAudioActive = true
                logTxStarted(activeEngine)
                broadcastTxState(TX_STATE_STARTED)
                broadcastTxFrame(activeEngine)
                apiTransmitting(true, activeEngine)
            } else if (!audioActive && txMonitorWasAudioActive) {
                txMonitorWasAudioActive = false
                apiTransmitting(false, activeEngine)
                // PC stopTx2: Hold PTT between frames leaves the rig keyed while more are to come
                if (rigConnected && !PreferenceManager.getDefaultSharedPreferences(this@JS8EngineService).getBoolean("js8cfg_hold_ptt", false)) {
                    requestRigPtt(false, generation)
                }
                broadcastTxState(TX_STATE_QUEUED)
            }

            val nextCheckMs = if (millisecondsUntilAudio.toLong() > pttLeadMs + TX_MONITOR_INTERVAL_MS) {
                TX_MONITOR_INTERVAL_MS
            } else {
                TX_PREKEY_MONITOR_INTERVAL_MS
            }
            txMonitorHandler.postDelayed(this, nextCheckMs)
        }
    }

    private val heartbeatHandler = Handler(Looper.getMainLooper())
    private var nextHeartbeatTime = 0L
    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            // Easy JS8: on the transmit thread like typed messages, so a stuck engine call can't
            // freeze the screen (2026-10-22: a stuck Halt froze the app when the HB timer fired)
            txHandler.post { checkHeartbeat() }
        }
    }

    private var pskReporterClient: PskReporterClient? = null
    private var spotClient: SpotClient? = null
    private var pskReporterEnabled = false
    private var currentDialHz: Long = 0
        set(value) {
            if (value != field) secBandChanged = driftedNowMs() / 1000 // PC m_secBandChanged (mw 5446-5449)
            field = value
        }
    /** PC m_secBandChanged: when the dial last changed, seconds. Spots wait 4/5 of a period after it. */
    private var secBandChanged = 0L
    /** PC m_rxCallQueue: heard calls waiting to be spotted to PSK Reporter, JS8NET and RX.SPOT (processSpots). */
    private val pendingSpots = ArrayDeque<() -> Unit>()
    private var splitXitHz = 0 // PC m_XIT for the transmission in progress
    @Volatile private var catReadPending = false // CAT button: the next FA answer sets the dial
    private var pwrBandSetOk = true // PC m_PwrBandSetOK
    private var pwrLastBand = "" // PC m_lastBand
    private var tuneOn = false // PC m_tune
    private var bandHopped = true // PC m_bandHopped
    private var bandHoppedHz = 0L // PC m_bandHoppedFreq
    private var bandScheduleSeen = ""
    @Volatile private var rigFailed = false
    /** PC rigOpen: the CAT button is orange from opening the rig until it answers or fails. */
    @Volatile private var rigOpening = false
    private var rigFirstError = true // PC m_first_error
    @Volatile private var splitHeld = "none" // split mode applied at key-on, until key-off
    private var currentCallsign: String = ""
    private var currentGrid: String = ""

    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        // PC prepareHeartbeatMode: HB off (or a speed without heartbeats) stops the HB timer
        if (key == PREF_HB || key == PREF_TX_SUBMODE) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            if (!prefs.getBoolean(PREF_HB, false) || getPreferredTxSubmode() !in setOf(SUBMODE_NORMAL, SUBMODE_FAST, SUBMODE_SLOW)) {
                HeartbeatTimer.on = false
            }
        }
        if (key == PREF_HEARTBEAT_INTERVAL || key == PREF_HB || key == PREF_TX_SUBMODE) {
            scheduleHeartbeat(true)
        }
        if (key == PREF_MULTI || key == PREF_TX_SUBMODE) {
            engine?.setSubmodes(configuredRxSubmodes())
        }
        if (key == PREF_PSK_REPORTER || key == "callsign" || key == "grid") {
            updatePskReporterState()
        }
        if (key in APRS_PREFS) updateAprsState()
        if (key == "last_frequency") {
            updateDialFromPrefs()
            dialChanged()
        }
        if (key in DECODE_RANGE_PREFS) applyDecodeRange()
        if (key == PREF_OUT_ATTENUATION) onOutAttenuationChanged()
        if (key == "js8cfg_output_channel") applyTxOutput()
        if (key == "js8cfg_input_channel") restartAudioCapture()
        if (key == "js8cfg_output_device") updateOutputDeviceForInput(selectedAudioDeviceId)
        if (key == PREF_LOG_MESSAGES) {
            updateMessageLogging()
        }
        if (key == PREF_SHOW_FT8) {
            applyFt8Setting()
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (!settingsLoaded) {
            settingsLoaded = true
            raiseIdleTimeout()
        }
        createNotificationChannel()
        createRigTransports()
        txHandlerThread.start()
        txHandler = Handler(txHandlerThread.looper)
        heartbeatHandler.post(bandHopRunnable)
        heartbeatHandler.post(idleRunnable)
        heartbeatHandler.post(msgBufferRunnable)
        txMonitorHandler = Handler(Looper.getMainLooper())
        (getSystemService(AUDIO_SERVICE) as AudioManager)
            .registerAudioDeviceCallback(audioDeviceCallback, mainHandler)
        
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
        messageLogger = MessageLogWriter(applicationContext)
        updateMessageLogging()
        // HB / CQ timers and Automatic Time Drift as they were left (Rob, 2026-10-22: "all settings remembered")
        HeartbeatTimer.load(prefs)
        CqTimer.load(prefs)
        autoDriftOn = prefs.getBoolean(KEY_AUTO_DRIFT_ON, false)
        autoDriftStopAfter = prefs.getInt(KEY_AUTO_DRIFT_STOP_AFTER, 0)
        autoDriftDecodesLeft = prefs.getInt(KEY_AUTO_DRIFT_LEFT, autoDriftStopAfter)
        resumeTimers()
        initPskReporter()
        // Easy JS8: Station > Reporting API / WSJT-X / N3FJP / N1MM
        Js8Api.init(this)

        Log.i(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                Log.i(TAG, "Starting engine")
                // Get preferred device ID from intent
                if (intent.hasExtra(EXTRA_AUDIO_DEVICE_ID)) {
                    selectedAudioDeviceId = intent.getIntExtra(EXTRA_AUDIO_DEVICE_ID, -1)
                    Log.i(TAG, "Start requested with device ID: $selectedAudioDeviceId")
                }
                updateDialFromPrefs()
                monitorLastUsed()
                startPowerMemory()
                updatePskReporterState()
                startForegroundService()
                startEngine()
            }
            ACTION_STOP -> {
                Log.i(TAG, "Stopping engine")
                stopEngine()
                stopSelf()
            }
            ACTION_SWITCH_AUDIO_DEVICE -> {
                val deviceId = intent.getIntExtra(EXTRA_AUDIO_DEVICE_ID, -1)
                Log.i(TAG, "Switching audio device to ID: $deviceId")
                switchAudioDevice(deviceId)
            }
            ACTION_SET_FREQUENCY -> {
                val frequencyHz = intent.getLongExtra(EXTRA_FREQUENCY_HZ, 0L)
                Log.i(TAG, "Setting frequency to $frequencyHz Hz")
                if (frequencyHz > 0) {
                    currentDialHz = frequencyHz
                }
                setFrequency(frequencyHz)
            }
            ACTION_SET_TX_OFFSET -> {
                val offsetHz = intent.getFloatExtra(EXTRA_TX_OFFSET_HZ, 1500f)
                Log.i(TAG, "Setting TX offset to $offsetHz Hz")
                currentTxOffsetHz = offsetHz
            }
            ACTION_TRANSMIT_MESSAGE -> {
                val txIntent = Intent(intent)
                txHandler.post { handleTransmitMessage(txIntent) }
            }
            ACTION_TIME_SYNC_ONCE -> {
                Log.i(TAG, "One-shot time sync armed; waiting for next decode")
                timeSyncOncePending = true
            }
            ACTION_SET_TIME_DRIFT -> {
                val driftMs = intent.getLongExtra(EXTRA_TIME_DRIFT_MS, 0L)
                Log.i(TAG, "Setting time drift to $driftMs ms")
                timeSyncOncePending = false
                driftMmaMs = driftMs
                driftMmaN = if (driftMs == 0L) 0 else 1
                applyTimeDrift(driftMs)
            }
            ACTION_SET_SYNC_STATS -> {
                syncStatsOn = intent.getBooleanExtra(EXTRA_SYNC_STATS_ON, false)
                engine?.setSyncStats(syncStatsOn)
            }
            ACTION_SET_AUTO_DRIFT -> setAutoDrift(
                intent.getBooleanExtra(EXTRA_AUTO_DRIFT_ON, false),
                intent.getIntExtra(EXTRA_AUTO_DRIFT_STOP_AFTER, 0)
            )
            ACTION_DEBUG_INJECT_DECODE -> {
                if (BuildConfig.DEBUG) injectDecode(intent)
            }
            // Debug builds: raw 12 kHz 16-bit audio in the app's files folder, played in at the next
            // slot start (period = seconds); or one raw frame sent, its tones in the log
            ACTION_DEBUG_JS8_PCM -> {
                if (BuildConfig.DEBUG) {
                    val bytes = runCatching { java.io.File(filesDir, intent.getStringExtra("file").orEmpty()).readBytes() }.getOrNull()
                    if (bytes != null) {
                        val buf = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                        val samples = ShortArray(bytes.size / 2) { buf.short }
                        val periodMs = intent.getIntExtra("period", 15) * 1000L
                        mainHandler.postDelayed({ engine?.debugMixRx(samples); Log.i(TAG, "JS8 test: ${samples.size} samples in") },
                            periodMs - driftedNowMs() % periodMs)
                    }
                }
            }
            ACTION_DEBUG_TX_FRAME -> {
                if (BuildConfig.DEBUG) {
                    val active = engine
                    active?.transmitFrame(intent.getStringExtra("frame").orEmpty(), intent.getIntExtra("bits", 3), intent.getIntExtra("submode", 0), 1000.0)
                    txHandler.postDelayed({
                        Log.i(TAG, "JS8 test tones: ${active?.txFrameTones()?.joinToString("")}")
                        active?.stopTransmit()
                    }, 1500)
                }
            }
            // Debug builds: FT8 monitor test with a 15 s 12 kHz 16-bit WAV in the app's files folder
            ACTION_DEBUG_FT8_WAV -> {
                if (BuildConfig.DEBUG) {
                    val name = intent.getStringExtra("file") ?: "ft8.wav"
                    val bytes = runCatching { java.io.File(filesDir, name).readBytes() }.getOrNull()
                    if (bytes == null || bytes.size <= 44) {
                        Log.w(TAG, "FT8 test: no $name in files")
                    } else {
                        val buf = java.nio.ByteBuffer.wrap(bytes, 44, bytes.size - 44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                        val samples = ShortArray((bytes.size - 44) / 2) { buf.short }
                        Log.i(TAG, "FT8 test: decoding $name (${samples.size} samples)")
                        engine?.debugFt8Recording(samples)
                    }
                }
            }
            // HB button, Send Heartbeat Now, API STATION.SEND_HB: a heartbeat now (PC sendHB)
            ACTION_SEND_HB -> sendHeartbeat()
            ACTION_ERASE_LOG -> messageLogger?.erase()
            ACTION_SCHEDULE_HOP_ANSWER -> finishScheduledHop(intent.getBooleanExtra(EXTRA_SCHEDULE_HOP_OK, false))
            ACTION_IDLE_OK -> finishIdleTimeout()
            ACTION_AUTOREPLY_CONFIRM_ANSWER -> finishAutoReplyConfirm(intent.getIntExtra(EXTRA_CONFIRM_ID, -1),
                intent.getBooleanExtra(EXTRA_CONFIRM_ACCEPTED, false), intent.getBooleanExtra(EXTRA_CONFIRM_FROM_API, false))
            // PC manualBandHop: Switch to Frequency Now
            ACTION_SCHEDULE_HOP_NOW -> {
                val hz = intent.getLongExtra(EXTRA_FREQUENCY_HZ, 0L)
                if (hz > 0L && !isTransmitActive()) {
                    bandHopped = true
                    bandHoppedHz = hz
                    applyBandHop(hz)
                }
            }
            // CQ button timer on / off (PC cqMacroButton toggled)
            ACTION_CQ_TIMER -> {
                CqTimer.on = intent.getBooleanExtra(EXTRA_HB_TIMER_ON, false)
                Log.i(TAG, "CQ timer ${if (CqTimer.on) "started" else "stopped"}")
                scheduleCq(true)
                // PC: one or the other, starting the CQ loop cancels the HB loop (Rob, 2026-10-22)
                if (CqTimer.on && HeartbeatTimer.on) {
                    HeartbeatTimer.on = false
                    Log.i(TAG, "HB timer stopped (CQ timer started)")
                    scheduleHeartbeat(true)
                }
            }
            // HB button timer on / off (PC hbMacroButton toggled), API STATION.SET_HB_TIMER
            ACTION_HB_TIMER -> {
                HeartbeatTimer.on = intent.getBooleanExtra(EXTRA_HB_TIMER_ON, false)
                Log.i(TAG, "HB timer ${if (HeartbeatTimer.on) "started" else "stopped"}")
                scheduleHeartbeat(true)
                // PC: one or the other, starting the HB loop cancels the CQ loop (Rob, 2026-10-22)
                if (HeartbeatTimer.on && CqTimer.on) {
                    CqTimer.on = false
                    Log.i(TAG, "CQ timer stopped (HB timer started)")
                    scheduleCq(true)
                }
            }
            // Easy JS8 Halt button: stop the transmission now. The TX monitor sees the
            // session end, releases PTT and reports TX_STATE_FINISHED as usual.
            ACTION_TX_TYPEAHEAD -> {
                val text = intent.getStringExtra(EXTRA_TYPEAHEAD_TEXT).orEmpty()
                val frames = intent.getIntExtra(EXTRA_TYPEAHEAD_FRAMES, -1)
                txHandler.post { replaceUnsentText(text, frames) }
            }
            ACTION_HALT_TX -> {
                Log.i(TAG, "Halt requested")
                endTuning()
                txHandler.post { engine?.stopTransmit() }
            }
            // Easy JS8 TUNE button: steady tone at the TX offset until TUNE is pressed again.
            ACTION_TUNE -> {
                val on = intent.getBooleanExtra(EXTRA_TUNE_ON, false)
                val freq = intent.getDoubleExtra(EXTRA_TX_FREQ_HZ, currentTxOffsetHz.toDouble())
                if (on) startTuning() else endTuning()
                txHandler.post { handleTune(on, freq) }
            }
            ACTION_READ_RIG -> readRig()
            ACTION_RIG_RETRY -> rigOpen()
            // Station > Radio (PC on_test_CAT_push_button_clicked / on_test_PTT_push_button_clicked)
            ACTION_TEST_CAT -> testCat()
            ACTION_TEST_PTT -> testPtt(intent.getBooleanExtra(EXTRA_RIG_TEST_ON, false))
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        pendingHopHz = 0L
        idleTimedOutMinutes = 0L
        autoReplyConfirms.clear()
        Log.i(TAG, "Service destroyed")
        (getSystemService(AUDIO_SERVICE) as AudioManager)
            .unregisterAudioDeviceCallback(audioDeviceCallback)
        
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
        // The timers stay on with their countdowns for the next start (Rob, 2026-10-22; the PC ends them)
        heartbeatHandler.removeCallbacksAndMessages(null)
        cqHandler.removeCallbacksAndMessages(null)
        messageLogger?.shutdown()
        aprsClient.shutdown()
        spotClient?.stop()

        stopEngine()
        txHandlerThread.quitSafely()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.i(TAG, "Task removed; stopping engine")
        Js8Api.closing() // PC closeEvent: swiped away counts as closing
        stopEngine()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    private fun startForegroundService() {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, JS8EngineService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_media_pause,
                getString(R.string.notification_action_stop),
                stopPendingIntent
            )
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Build the rig transports. Called again on stop, once the old ones have
     * been handed to the teardown, so a restart never reaches a closing link.
     */
    private fun createRigTransports() {
        usbSerialBridge = UsbSerialBridge(applicationContext)
        bluetoothSerialBridge = BluetoothSerialBridge(applicationContext)
        trusdxDirectSerial = TruSdxDirectSerial(applicationContext)
        qmxDirectSerial = QmxDirectSerial(applicationContext)
        hamlibRigControl = HamlibRigControl()
        trusdxSerialSession = trusdxDirectSerial?.let { direct ->
            TruSdxSerialSession(
                direct,
                object : TruSdxSerialSession.Listener {
                    override fun onCatMessage(message: String) {
                        if (isTruSdxDiagnosticsEnabled()) {
                            Log.v(TAG, "TruSDX CAT <= $message")
                        }
                        if (catReadPending && message.startsWith("FA")) {
                            message.substring(2).trimEnd(';').toLongOrNull()?.let { hz -> mainHandler.post { onRigRead(hz) } }
                        }
                    }

                    override fun onAudioFrame(samplesU8: ByteArray) {
                        handleTruSdxAudioFrame(samplesU8)
                    }

                    override fun onParserResync(reason: String) {
                        trusdxParserResyncs += 1
                        if (isTruSdxDiagnosticsEnabled()) {
                            Log.w(TAG, "TruSDX parser resync ($reason), count=$trusdxParserResyncs")
                        }
                    }

                    override fun onIoError(message: String) {
                        Log.w(TAG, "TruSDX I/O error: $message")
                        trusdxConnected = false
                        broadcastError("TruSDX serial link lost")
                    }

                }
            )
        }
        qmxCatSession = qmxDirectSerial?.let { direct ->
            QmxCatSession(direct, object : QmxCatSession.Listener {
                override fun onFrequency(frequencyHz: Long) {
                    if (splitHeld != "none") return // the radio is on the TX dial for now
                    if (catReadPending) {
                        mainHandler.post { onRigRead(frequencyHz) }
                        return
                    }
                    val dialHz = removeCalibration(frequencyHz)
                    currentDialHz = dialHz
                    mainHandler.post { broadcastRadioFrequency(dialHz) }
                }

                override fun onMessage(message: String) {
                    Log.d(TAG, "QMX CAT <= $message")
                }

                override fun onIoError(message: String) {
                    Log.w(TAG, "QMX CAT I/O error: $message")
                    qmxConnected = false
                    mainHandler.post { broadcastError("QMX CAT link lost") }
                }
            })
        }
        uvProSession = createUvProSession()
    }

    private fun createUvProSession(): UvProSession {
        // Callbacks from a replaced session must not touch the current one.
        val generation = ++uvproSessionGeneration
        return UvProSession(applicationContext, object : UvProSession.Listener {
            override fun onRxAudio(samples: ShortArray, sampleRateHz: Int) {
                if (generation != uvproSessionGeneration) return
                engine?.submitAudioRaw(samples, samples.size, sampleRateHz, System.nanoTime())
            }

            override fun onTxTimeout() {
                mainHandler.post {
                    if (generation != uvproSessionGeneration) return@post
                    failTransmitForPtt(txPttGeneration, "UV-Pro transmit timed out; PTT released")
                }
            }

            override fun onIoError(message: String) {
                if (generation != uvproSessionGeneration) return
                Log.w(TAG, "UV-Pro I/O error: $message")
                uvproConnected = false
                mainHandler.post { broadcastError("UV-Pro Bluetooth link lost") }
            }
        })
    }

    private fun startEngine(resumeGeneration: Int? = null) {
        val generation = if (resumeGeneration == null) {
            if (engineStartInProgress || engine != null) {
                Log.w(TAG, "Ignoring duplicate engine start request")
                // A screen opened while the engine runs missed the one-time RUNNING: tell it again (S20, 2026-11-06)
                if (engine != null && Js8Api.State.monitoring) {
                    LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_ENGINE_STATE).putExtra(EXTRA_STATE, STATE_RUNNING))
                }
                return
            }
            if (trusdxStartupWorkerActive) {
                Log.w(TAG, "Ignoring engine start while TruSDX cleanup is still active")
                broadcastError("TruSDX serial initialization is still stopping. Please try again.")
                broadcastEngineState(STATE_STOPPED)
                return
            }
            if (qmxStartupWorkerActive) {
                Log.w(TAG, "Ignoring engine start while QMX cleanup is still active")
                broadcastError("QMX CAT initialization is still stopping. Please try again.")
                broadcastEngineState(STATE_STOPPED)
                return
            }
            if (uvproWorkersActive.get() > 0) {
                Log.w(TAG, "Ignoring engine start while UV-Pro cleanup is still active")
                broadcastError("UV-Pro Bluetooth is still disconnecting. Please try again.")
                broadcastEngineState(STATE_STOPPED)
                return
            }

            engineStartInProgress = true
            val nextGeneration = ++engineStartGeneration
            try {
                initializeRigControl(nextGeneration)
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing rig control", e)
                failEngineStart(nextGeneration, "Error initializing rig control: ${e.message}")
                return
            }

            if (rigControlMode == "trusdx_serial" || rigControlMode == "qmx_serial" || rigControlMode == "btech_uvpro") {
                return
            }
            nextGeneration
        } else {
            if (!isCurrentEngineStart(resumeGeneration)) {
                Log.i(TAG, "Ignoring stale engine start continuation generation=$resumeGeneration")
                return
            }
            resumeGeneration
        }

        try {
            // Create callback handler that marshals to main thread
            val callbackHandler = object : JS8Engine.CallbackHandler {
                override fun onDecoded(
                    utc: Int, snr: Int, dt: Float, freq: Float,
                    text: String, type: Int, quality: Float, mode: Int, driftMs: Int
                ) {
                    Log.d(TAG, "Decoded: $text (SNR: $snr dB)")
                    if (isDuplicateFrame(text, mode)) return
                    logRxDecode(text, snr, dt, freq, mode, type)

                    // Broadcast on main thread
                    mainHandler.post {
                        if (autoDriftOn && (mode == 0 || mode == 4)) applyAutoDrift(driftMs)
                        else maybeApplyTimeSync(driftMs)
                        handleDecode(utc, snr, dt, freq, text, type, quality, mode, driftMs)
                        maybeReportToPskReporter(utc, snr, freq, text)
                    }
                }

                override fun onSpectrum(
                    bins: FloatArray, binHz: Float,
                    powerDb: Float, peakDb: Float
                ) {
                    spectrumEventCount += 1
                    if (rigControlMode == "trusdx_serial" && spectrumEventCount % 20L == 0L) {
                        Log.i(TAG, "Spectrum events: count=$spectrumEventCount bins=${bins.size} binHz=$binHz power=$powerDb peak=$peakDb")
                    }
                    // Broadcast spectrum data (main thread)
                    mainHandler.post {
                        broadcastSpectrum(bins, binHz, powerDb, peakDb)
                    }
                }

                override fun onDecodedFrame(frame: String) {
                    lastDecodedFrame = frame
                }

                override fun onDecodeStarted(submodes: Int) {
                    Log.d(TAG, "Decode started: submodes=$submodes")
                    decodeDialHz = currentDialHz
                    decodePeriodUtc = periodUtc(submodes)
                    messageLogger?.decodeStarted(decodePeriodUtc)
                    mainHandler.post {
                        Js8Api.State.decoding = true
                        Js8Api.statusUpdate() // PC decodeBusy(true)
                        broadcastDecodeStarted(submodes)
                        if (syncStatsOn) DecodeAttempts.listener?.onDecodeStarted()
                    }
                }

                override fun onSyncState(kind: Int, mode: Int, freq: Float, dt: Float, candidate: Int) {
                    if (!syncStatsOn) return
                    mainHandler.post { DecodeAttempts.listener?.onSyncState(kind, mode, freq, dt, candidate) }
                }

                override fun onDecodeFinished(count: Int) {
                    Log.d(TAG, "Decode finished: count=$count")
                    messageLogger?.decodeDone()
                    mainHandler.post {
                        Js8Api.State.decoding = false
                        Js8Api.statusUpdate() // PC decodeBusy(false)
                        broadcastDecodeFinished(count)
                    }
                }

                // Easy JS8: FT8 heard in the JS8 audio, shown in the Message Window
                override fun onFt8Decoded(slotUtcMs: Long, freq: Float, snr: Int, text: String, callDe: String) {
                    Log.i(TAG, "FT8: $text (${freq.toInt()} Hz, $snr dB)")
                    mainHandler.post {
                        LocalBroadcastManager.getInstance(this@JS8EngineService).sendBroadcast(Intent(ACTION_FT8_DECODED).apply {
                            putExtra(EXTRA_FT8_UTC_MS, slotUtcMs)
                            putExtra(EXTRA_FT8_FREQ, freq)
                            putExtra(EXTRA_FT8_SNR, snr)
                            putExtra(EXTRA_FT8_TEXT, text)
                            putExtra(EXTRA_FT8_CALL_DE, callDe)
                        })
                    }
                }

                override fun onError(message: String) {
                    Log.e(TAG, "Engine error: $message")
                    mainHandler.post {
                        broadcastError(message)
                    }
                }

                override fun onLog(level: Int, message: String) {
                    val levelStr = when (level) {
                        0 -> "TRACE"
                        1 -> "DEBUG"
                        2 -> "INFO"
                        3 -> "WARN"
                        4 -> "ERROR"
                        else -> "LOG"
                    }
                    Log.d(TAG, "[$levelStr] $message")
                }

                override fun onTxAudio(samples: ShortArray, sampleRateHz: Int) {
                    handleTruSdxTxAudio(samples, sampleRateHz)
                    handleUvProTxAudio(samples, sampleRateHz)
                }
            }

            // Create engine. Its decode timetable covers only the speeds it starts with, and
            // setSubmodes can only narrow that, so start with all four and narrow to MULTI.
            engine = JS8Engine.create(
                sampleRateHz = 12000,
                submodes = RX_BITS_ALL,
                callbackHandler = callbackHandler,
                enableTxAudioTap = rigControlMode == "trusdx_serial" || rigControlMode == "btech_uvpro",
                useQmxUsbAudio = rigControlMode == "qmx_serial"
            )
            engine?.setSubmodes(configuredRxSubmodes())

            if (rigControlMode == "qmx_serial") {
                selectedOutputDeviceId = findOutputDeviceId(selectedAudioDeviceId)
                engine?.setOutputDevice(selectedOutputDeviceId)
                Log.i(
                    TAG,
                    "QMX USB audio route: input=$selectedAudioDeviceId output=$selectedOutputDeviceId (${getOutputDeviceName(selectedOutputDeviceId)})"
                )
            }

            // Start engine
            if (engine?.start() == true) {
                Log.i(TAG, "Engine started successfully")
                spectrumEventCount = 0

                applyTxBoostSetting()
                applyTxOutput()
                applyFt8Setting()
                applyTimeDriftSetting()
                engine?.setAutoSync(autoDriftOn)
                // The screen shows Start / Stop Automatic Time Drift as kept from the last run
                LocalBroadcastManager.getInstance(this)
                    .sendBroadcast(Intent(ACTION_AUTO_DRIFT).putExtra(EXTRA_AUTO_DRIFT_ON, autoDriftOn))
                engine?.setSyncStats(syncStatsOn)
                applyDecodeRange()

                if (rigControlMode == "trusdx_serial") {
                    Log.i(TAG, "TruSDX mode active: skipping microphone capture")
                    val label = if (selectedAudioDeviceId == TRUSDX_AUDIO_SPEAKER_ID) {
                        TRUSDX_AUDIO_SPEAKER_NAME
                    } else {
                        TRUSDX_AUDIO_SERIAL_NAME
                    }
                    broadcastAudioDevice(label)
                    broadcastEngineState(STATE_RUNNING)
                    broadcastProcessTxQueue()
                    startTruSdxRxWorker()
                    scheduleTruSdxRxKeepAlive()
                } else if (rigControlMode == "btech_uvpro") {
                    Log.i(TAG, "UV-Pro mode active: skipping microphone capture")
                    broadcastAudioDevice(UVPRO_AUDIO_NAME)
                    broadcastEngineState(STATE_RUNNING)
                    broadcastProcessTxQueue()
                } else {
                    // Start audio capture with selected device (if any)
                    scoRestartAttempts = 0
                    scoSourceIndex = 0
                    scoSilenceCheckToken++
                    scoStartToken++
                    if (isScoInputDevice(selectedAudioDeviceId)) {
                        startAudioCaptureWithScoWait(engine!!, selectedAudioDeviceId)
                    } else {
                        startAudioCapture(engine!!, selectedAudioDeviceId)
                    }
                }
                engineStartInProgress = false
            } else {
                Log.e(TAG, "Failed to start engine")
                engine?.close()
                engine = null
                failEngineStart(generation, "Failed to start engine")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error starting engine", e)
            engine?.close()
            engine = null
            failEngineStart(generation, "Error starting engine: ${e.message}")
        }
    }

    private fun isCurrentEngineStart(generation: Int): Boolean {
        return engineStartInProgress && engineStartGeneration == generation
    }

    private fun failEngineStart(generation: Int, message: String) {
        if (!isCurrentEngineStart(generation)) return
        rigOpening = false
        if (rigControlMode == "trusdx_serial" && trusdxSerialSession?.isConnected() == true) {
            trusdxStartupWorkerActive = true
            Thread {
                try {
                    trusdxSerialSession?.stop()
                } finally {
                    trusdxStartupWorkerActive = false
                }
            }.start()
        }
        if (rigControlMode == "qmx_serial" && qmxCatSession?.isConnected() == true) {
            qmxStartupWorkerActive = true
            Thread {
                try {
                    qmxCatSession?.stop()
                } finally {
                    qmxStartupWorkerActive = false
                }
            }.start()
        }
        if (rigControlMode == "btech_uvpro") {
            // A fresh session for the next start, so this stop cannot close it.
            val failedSession = uvProSession
            uvProSession = createUvProSession()
            uvproConnected = false
            uvproWorkersActive.incrementAndGet()
            Thread {
                try {
                    failedSession?.stop()
                } finally {
                    uvproWorkersActive.decrementAndGet()
                }
            }.start()
        }
        engineStartInProgress = false
        trusdxInitInProgress = false
        trusdxConnected = false
        rigCtlErrorShown = true
        broadcastError(message)
        broadcastEngineState(STATE_ERROR)
    }

    private fun initializeRigControl(generation: Int) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val rigControlEnabled = prefs.getBoolean("rig_control_enabled", false)
        val rigType = prefs.getString("rig_type", "none")

        if (!rigControlEnabled || rigType == "none") {
            Log.i(TAG, "Rig control not enabled")
            rigControlMode = "none"
            rtsPttConnected = false
            trusdxConnected = false
            qmxConnected = false
            rtsPttTransport = null
            return
        }

        rigControlMode = rigType ?: "none"
        rigOpening = true
        broadcastRigStatus(false)
        rtsPttConnected = false
        trusdxConnected = false
        qmxConnected = false
        rtsPttTransport = null
        when (rigType) {
            "hamlib_usb" -> { initializeHamlibUsbControl(); openPttPort() }
            "rts_ptt" -> initializeRtsPttControl()
            "trusdx_serial" -> initializeTruSdxControl(generation)
            "qmx_serial" -> initializeQmxControl(generation)
            "btech_uvpro" -> initializeUvProControl(generation)
            else -> Log.w(TAG, "Unknown rig type: $rigType")
        }
    }

    private fun initializeUvProControl(generation: Int) {
        Log.i(TAG, "Initializing UV-Pro Bluetooth audio")
        uvproConnected = false
        val session = uvProSession
        uvproWorkersActive.incrementAndGet()
        Thread {
            val opened = try {
                session?.start() == true
            } catch (error: Throwable) {
                Log.e(TAG, "UV-Pro start failed", error)
                false
            }
            mainHandler.post {
                uvproWorkersActive.decrementAndGet()
                if (!isCurrentEngineStart(generation)) {
                    if (opened) session?.stop()
                } else if (opened) {
                    uvproConnected = true
                    rigCtlErrorShown = false
                    startEngine(generation)
                } else {
                    failEngineStart(generation, session?.getLastError() ?: "Failed to connect to the UV-Pro.")
                }
            }
        }.start()
    }

    private fun initializeTruSdxControl(generation: Int) {
        Log.i(TAG, "Initializing TruSDX serial control")
        trusdxInitInProgress = true
        trusdxConnected = false
        trusdxParserResyncs = 0
        trusdxRxFrames = 0
        trusdxRxSamples = 0
        trusdxTxFrames = 0
        trusdxTxSamples = 0
        trusdxTxDrops = 0
        trusdxTxSilentFrames = 0
        trusdxRxUnderruns = 0
        trusdxRxSubmitDrops = 0
        trusdxLastRxAudioNs = 0L
        trusdxLastRxRearmNs = 0L
        trusdxTxIntentActive = false
        trusdxRxRateWindowStartNs = 0L
        trusdxRxRateWindowSamples = 0L
        trusdxRxFrameDrops = 0L
        trusdxRxKeepaliveToken++
        trusdxRxKeepaliveCount = 0L
        clearTruSdxRxFrameQueue()

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val selectedPort = prefs.getString("rig_hamlib_usb_port", "auto")?.trim().orEmpty()
        val selection = resolveSerialSelection(selectedPort, prefs)
        if (selection == null) {
            Log.w(TAG, "Invalid serial port selection: $selectedPort")
            failEngineStart(generation, "Invalid serial port selection for TruSDX control.")
            return
        }

        if (selection.transport != SerialTransport.USB) {
            Log.w(TAG, "TruSDX only supports USB serial transport")
            failEngineStart(generation, "TruSDX mode supports USB serial only.")
            return
        }

        openTruSdxSerialUsb(selection, generation)
    }

    private fun openTruSdxSerialUsb(selection: SerialSelection, generation: Int) {
        var deviceId = selection.usbDeviceId
        var portIndex = selection.portIndex
        if (deviceId == null) {
            val ports = try {
                UsbSerialPortCatalog.listPorts(this)
            } catch (_: Throwable) {
                emptyList()
            }
            val firstPort = ports.firstOrNull()
            if (firstPort != null) {
                deviceId = firstPort.deviceId
                portIndex = firstPort.portIndex
                Log.i(TAG, "Auto-selected USB serial port for TruSDX: ${firstPort.label}")
            }
        }

        val usbDevice = if (deviceId != null) {
            UsbPermissionHelper.findUsbDeviceById(this, deviceId)
        } else {
            null
        }

        if (usbDevice == null) {
            Log.w(TAG, "No USB serial device found for TruSDX")
            failEngineStart(generation, "No USB serial device found for TruSDX control.")
            return
        }

        Log.i(TAG, "TruSDX USB device selected: ${usbDevice.deviceName} (id=${usbDevice.deviceId}) port=$portIndex")

        if (!UsbPermissionHelper.hasPermission(this, usbDevice)) {
            Log.i(TAG, "Requesting USB permission for TruSDX device...")
            UsbPermissionHelper.requestPermission(this, usbDevice) { granted ->
                if (!isCurrentEngineStart(generation)) {
                    Log.i(TAG, "Ignoring stale TruSDX permission result generation=$generation")
                    return@requestPermission
                }
                if (granted) {
                    Log.i(TAG, "USB permission granted for TruSDX device")
                    openTruSdxSerialUsbInternal(usbDevice.deviceId, portIndex, generation)
                } else {
                    Log.w(TAG, "USB permission denied for TruSDX device")
                    failEngineStart(generation, "USB permission denied. Please grant USB access in Settings.")
                }
            }
        } else {
            Log.i(TAG, "USB permission already granted for TruSDX device")
            openTruSdxSerialUsbInternal(usbDevice.deviceId, portIndex, generation)
        }
    }

    private fun openTruSdxSerialUsbInternal(deviceId: Int, portIndex: Int, generation: Int) {
        trusdxStartupWorkerActive = true
        Thread {
            try {
                if (!isCurrentEngineStart(generation)) {
                    trusdxStartupWorkerActive = false
                    return@Thread
                }
                val session = trusdxSerialSession
                val opened = session?.start(deviceId, portIndex) == true
                if (opened) {
                    session?.setSpeakerEnabled(selectedAudioDeviceId == TRUSDX_AUDIO_SPEAKER_ID)
                }
                val initialized = opened && session?.initializeRigState() == true
                if (!isCurrentEngineStart(generation)) {
                    if (opened) session?.stop()
                    trusdxStartupWorkerActive = false
                    return@Thread
                }
                mainHandler.post {
                    if (!isCurrentEngineStart(generation)) {
                        if (opened) session?.stop()
                        trusdxStartupWorkerActive = false
                        return@post
                    }
                    if (initialized) {
                        trusdxConnected = true
                        rigCtlErrorShown = false
                        trusdxInitInProgress = false
                        trusdxStartupWorkerActive = false
                        Log.i(TAG, "TruSDX serial control ready device=$deviceId port=$portIndex")
                        if (currentDialHz > 0L) {
                            Thread {
                                val ok = session?.setFrequency(applyCalibration(currentDialHz)) == true
                                Log.i(TAG, "TruSDX initial frequency apply: hz=$currentDialHz ok=$ok")
                            }.start()
                        }
                        startEngine(generation)
                    } else {
                        session?.stop()
                        trusdxStartupWorkerActive = false
                        trusdxConnected = false
                        Log.w(TAG, "Failed to initialize TruSDX serial control")
                        failEngineStart(generation, "Failed to initialize TruSDX serial control.")
                    }
                }
            } catch (error: Throwable) {
                Log.e(TAG, "TruSDX serial initialization failed", error)
                trusdxStartupWorkerActive = false
                mainHandler.post {
                    failEngineStart(generation, "Failed to initialize TruSDX serial control: ${error.message}")
                }
            }
        }.start()
    }

    private fun initializeQmxControl(generation: Int) {
        Log.i(TAG, "Initializing QMX CAT control")
        qmxConnected = false
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val selectedPort = prefs.getString("rig_hamlib_usb_port", "auto")?.trim().orEmpty()
        val selection = resolveSerialSelection(selectedPort, prefs)
        if (selection?.transport != SerialTransport.USB) {
            failEngineStart(generation, "QMX mode supports USB CAT only.")
            return
        }

        var deviceId = selection.usbDeviceId
        var portIndex = selection.portIndex
        if (deviceId == null) {
            val firstPort = try { UsbSerialPortCatalog.listPorts(this).firstOrNull() } catch (_: Throwable) { null }
            deviceId = firstPort?.deviceId
            portIndex = firstPort?.portIndex ?: 0
        }
        val usbDevice = deviceId?.let { UsbPermissionHelper.findUsbDeviceById(this, it) }
        if (usbDevice == null) {
            failEngineStart(generation, "No USB serial device found for QMX CAT control.")
            return
        }

        val open = {
            qmxStartupWorkerActive = true
            Thread {
                try {
                    val baudRate = prefs.getString("qmx_cat_baud", "115200")?.toIntOrNull() ?: 115200
                    val session = qmxCatSession
                    val opened = session?.start(usbDevice.deviceId, portIndex, baudRate) == true
                    val initialized = opened && session?.initialize() == true
                    mainHandler.post {
                        if (!isCurrentEngineStart(generation)) {
                            if (opened) session?.stop()
                        } else if (initialized) {
                            qmxConnected = true
                            rigCtlErrorShown = false
                            if (currentDialHz > 0L) session?.setFrequency(applyCalibration(currentDialHz))
                            startEngine(generation)
                        } else {
                            session?.stop()
                            failEngineStart(generation, "Failed to initialize QMX CAT control.")
                        }
                        qmxStartupWorkerActive = false
                    }
                } catch (error: Throwable) {
                    qmxStartupWorkerActive = false
                    mainHandler.post {
                        failEngineStart(generation, "Failed to initialize QMX CAT control: ${error.message}")
                    }
                }
            }.start()
        }
        if (UsbPermissionHelper.hasPermission(this, usbDevice)) {
            open()
        } else {
            UsbPermissionHelper.requestPermission(this, usbDevice) { granted ->
                if (!isCurrentEngineStart(generation)) return@requestPermission
                if (granted) open() else failEngineStart(generation, "USB permission denied for QMX CAT control.")
            }
        }
    }

    /** Settings > View > Show FT8 decodes in the Message Window (off when first installed). */
    private fun applyFt8Setting() {
        val on = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(PREF_SHOW_FT8, false)
        engine?.setFt8Enabled(on)
        Log.i(TAG, "FT8 monitor: ${if (on) "on" else "off"}")
    }

    private fun applyTxBoostSetting() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        // The generic boost uses soft limiting, which corrupts QMX's narrow FSK tones.
        // QMX receives the engine's full-scale USB waveform without it.
        val txBoostEnabled = rigControlMode != "qmx_serial" && prefs.getBoolean("tx_boost_enabled", false)
        engine?.setTxBoostEnabled(txBoostEnabled)
        Log.i(TAG, "TX boost: ${if (txBoostEnabled) "enabled (+10 dB)" else "disabled"}${if (rigControlMode == "qmx_serial") " for QMX clean USB audio" else ""}")
    }

    /**
     * Speeds the decoder listens for. The engine wants bits 1 << SubmodeId (A Normal, B Fast,
     * C JS8 40, E Slow), not the varicode speed numbers: the old "NORMAL or FAST or SLOW or TURBO"
     * gave 7 = Normal, Fast, JS8 40 and never Slow.
     * Settings > MULTI (PC SubModeMultiDecode, default on): all four; off: only the TX speed.
     */
    private fun configuredRxSubmodes(): Int {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (prefs.getBoolean(PREF_MULTI, true)) return RX_BITS_ALL
        return when (getPreferredTxSubmode()) {
            SUBMODE_FAST -> RX_BIT_FAST
            SUBMODE_TURBO -> RX_BIT_TURBO
            SUBMODE_SLOW -> RX_BIT_SLOW
            SUBMODE_ULTRA -> RX_BIT_ULTRA
            else -> RX_BIT_NORMAL
        }
    }

    private fun initializeHamlibUsbControl() {
        Log.i(TAG, "Initializing serial rig control (Hamlib)")
        hamlibRigConnected = false

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val rigModelStr = prefs.getString("rig_hamlib_model", "0")?.trim().orEmpty()
        val selectedPort = prefs.getString("rig_hamlib_usb_port", "auto")?.trim().orEmpty()

        val rigModel = rigModelStr.toIntOrNull() ?: 0
        Log.i(TAG, "Hamlib rig model selected: $rigModel")
        if (rigModel <= 0) {
            Log.w(TAG, "Hamlib rig model not selected")
            broadcastError("Select a Hamlib rig model before enabling serial control.")
            rigCtlErrorShown = true
            return
        }

        // PC Capabilities::network: the Hamlib model is reached by host and port, no serial port
        if (com.js8call.core.HamlibRigCatalog.model(rigModelStr)?.network == true) {
            openHamlibNetwork(rigModel, prefs)
            return
        }

        val selection = resolveSerialSelection(selectedPort, prefs)
        if (selection == null) {
            Log.w(TAG, "Invalid serial port selection: $selectedPort")
            broadcastError("Invalid serial port selection for Hamlib control.")
            rigCtlErrorShown = true
            return
        }

        when (selection.transport) {
            SerialTransport.USB -> openHamlibSerialUsb(rigModel, selection)
            SerialTransport.BLUETOOTH -> openHamlibSerialBluetooth(rigModel, selection)
        }
    }

    /** PC HamlibTransceiver, RIG_PORT_NETWORK: rig_pathname = "host:port"; blank is Hamlib's default for the rig. */
    private fun openHamlibNetwork(rigModel: Int, prefs: SharedPreferences) {
        val host = prefs.getString("rigctld_host", "").orEmpty().trim()
        val port = prefs.getString("rigctld_port", "").orEmpty().trim()
        val path = if (host.isEmpty()) "" else if (port.isEmpty()) host else "$host:$port"
        Thread {
            val ok = hamlibRigControl?.openSerialPath(rigModel, path, 0, 0, 0, "None") == true
            mainHandler.post {
                hamlibRigConnected = ok
                rigCtlErrorShown = false
                if (ok) {
                    Log.i(TAG, "Hamlib network rig opened (model=$rigModel path=$path)")
                    rigOnline()
                } else {
                    rigFailure("Hamlib could not open the rig at ${path.ifEmpty { "its default address" }}: ${hamlibRigControl?.getLastError().orEmpty()}")
                    rigCtlErrorShown = true
                }
            }
        }.start()
    }

    private fun initializeRtsPttControl() {
        Log.i(TAG, "Initializing RTS PTT control")
        rtsPttConnected = false
        rtsPttTransport = null

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val selectedPort = prefs.getString("rig_hamlib_usb_port", "auto")?.trim().orEmpty()

        val selection = resolveSerialSelection(selectedPort, prefs)
        if (selection == null) {
            Log.w(TAG, "Invalid serial port selection: $selectedPort")
            broadcastError("Invalid serial port selection for RTS PTT control.")
            rigCtlErrorShown = true
            return
        }

        when (selection.transport) {
            SerialTransport.USB -> openRtsSerialUsb(selection)
            SerialTransport.BLUETOOTH -> openRtsSerialBluetooth(selection)
        }
    }

    private fun openRtsSerialUsb(selection: SerialSelection) {
        val params = readSerialParams()

        usbSerialBridge?.registerNative()

        var deviceId = selection.usbDeviceId
        var portIndex = selection.portIndex
        if (deviceId == null) {
            val ports = try {
                UsbSerialPortCatalog.listPorts(this)
            } catch (_: Throwable) {
                emptyList()
            }
            val firstPort = ports.firstOrNull()
            if (firstPort != null) {
                deviceId = firstPort.deviceId
                portIndex = firstPort.portIndex
                Log.i(TAG, "Auto-selected USB serial port for RTS PTT: ${firstPort.label}")
            }
        }

        val usbDevice = if (deviceId != null) {
            UsbPermissionHelper.findUsbDeviceById(this, deviceId)
        } else {
            null
        }

        if (usbDevice == null) {
            if (selection.path == "auto") {
                val btPorts = try {
                    BluetoothSerialPortCatalog.listPorts(this)
                } catch (_: Throwable) {
                    emptyList()
                }
                val btPort = btPorts.firstOrNull()
                if (btPort != null) {
                    val btSelection = SerialSelection(
                        transport = SerialTransport.BLUETOOTH,
                        path = "android-bt:${btPort.address}:${btPort.portIndex}",
                        usbDeviceId = null,
                        btAddress = btPort.address,
                        portIndex = btPort.portIndex
                    )
                    Log.i(TAG, "Auto-selected Bluetooth serial port for RTS PTT: ${btPort.label}")
                    openRtsSerialBluetooth(btSelection)
                    return
                }
            }
            Log.w(TAG, "No USB serial device found for RTS PTT")
            broadcastError("No USB serial device found for RTS PTT control.")
            rigCtlErrorShown = true
            return
        }

        Log.i(TAG, "RTS PTT USB device selected: ${usbDevice.deviceName} (id=${usbDevice.deviceId}) port=$portIndex")

        if (!UsbPermissionHelper.hasPermission(this, usbDevice)) {
            Log.i(TAG, "Requesting USB permission for RTS PTT device...")
            UsbPermissionHelper.requestPermission(this, usbDevice) { granted ->
                if (granted) {
                    Log.i(TAG, "USB permission granted for RTS PTT device")
                    openRtsSerialUsbInternal(usbDevice.deviceId, portIndex, params)
                } else {
                    Log.w(TAG, "USB permission denied for RTS PTT device")
                    broadcastError("USB permission denied. Please grant USB access in Settings.")
                    rigCtlErrorShown = true
                }
            }
        } else {
            Log.i(TAG, "USB permission already granted for RTS PTT device")
            openRtsSerialUsbInternal(usbDevice.deviceId, portIndex, params)
        }
    }

    private fun openRtsSerialUsbInternal(deviceId: Int, portIndex: Int, params: SerialParams) {
        Thread {
            val ok = usbSerialBridge?.open(
                deviceId,
                portIndex,
                params.baudRate,
                params.dataBits.takeIf { it > 0 } ?: 8,
                params.stopBits.takeIf { it > 0 } ?: 1,
                params.parityValue
            ) == true

            mainHandler.post {
                rtsPttConnected = ok
                rigCtlErrorShown = false
                if (ok) {
                    rtsPttTransport = SerialTransport.USB
                    usbSerialBridge?.setRts(false)
                    applyForcedLines(params)
                    Log.i(TAG, "RTS PTT USB serial opened device=$deviceId port=$portIndex")
                } else {
                    Log.w(TAG, "RTS PTT USB serial open failed for device=$deviceId port=$portIndex")
                    broadcastError("Failed to open USB serial port for RTS PTT control.")
                    rigCtlErrorShown = true
                }
            }
        }.start()
    }

    private fun openRtsSerialBluetooth(selection: SerialSelection) {
        val params = readSerialParams()

        bluetoothSerialBridge?.registerNative()

        val address = selection.btAddress
        if (address == null) {
            Log.w(TAG, "Bluetooth address missing for RTS PTT")
            broadcastError("No Bluetooth serial device selected for RTS PTT control.")
            rigCtlErrorShown = true
            return
        }

        if (BluetoothSerialPortCatalog.findBondedDevice(this, address) == null) {
            Log.w(TAG, "Bluetooth device not paired or unavailable: $address")
            broadcastError("Bluetooth serial device not available. Check pairing and settings.")
            rigCtlErrorShown = true
            return
        }

        Thread {
            val ok = bluetoothSerialBridge?.open(
                address,
                selection.portIndex,
                params.baudRate,
                params.dataBits.takeIf { it > 0 } ?: 8,
                params.stopBits.takeIf { it > 0 } ?: 1,
                params.parityValue
            ) == true

            mainHandler.post {
                rtsPttConnected = ok
                rigCtlErrorShown = false
                if (ok) {
                    rtsPttTransport = SerialTransport.BLUETOOTH
                    bluetoothSerialBridge?.setRts(false)
                    applyForcedLines(params)
                    Log.i(TAG, "RTS PTT Bluetooth serial opened addr=$address port=${selection.portIndex}")
                } else {
                    val detail = bluetoothSerialBridge?.getLastError().orEmpty()
                    if (detail.isNotBlank()) {
                        Log.w(TAG, "RTS PTT Bluetooth open failed for addr=$address port=${selection.portIndex}: $detail")
                        broadcastError("Failed to open Bluetooth serial port for RTS PTT control: $detail")
                    } else {
                        Log.w(TAG, "RTS PTT Bluetooth open failed for addr=$address port=${selection.portIndex}")
                        broadcastError("Failed to open Bluetooth serial port for RTS PTT control.")
                    }
                    rigCtlErrorShown = true
                }
            }
        }.start()
    }

    private fun openHamlibSerialUsb(rigModel: Int, selection: SerialSelection) {
        val params = readSerialParams()

        usbSerialBridge?.registerNative()

        var deviceId = selection.usbDeviceId
        var portIndex = selection.portIndex
        if (deviceId == null) {
            val ports = try {
                UsbSerialPortCatalog.listPorts(this)
            } catch (_: Throwable) {
                emptyList()
            }
            val firstPort = ports.firstOrNull()
            if (firstPort != null) {
                deviceId = firstPort.deviceId
                portIndex = firstPort.portIndex
                Log.i(TAG, "Auto-selected USB serial port: ${firstPort.label}")
            }
        }

        val usbDevice = if (deviceId != null) {
            UsbPermissionHelper.findUsbDeviceById(this, deviceId)
        } else {
            null
        }

        if (usbDevice == null) {
            if (selection.path == "auto") {
                val btPorts = try {
                    BluetoothSerialPortCatalog.listPorts(this)
                } catch (_: Throwable) {
                    emptyList()
                }
                val btPort = btPorts.firstOrNull()
                if (btPort != null) {
                    val btSelection = SerialSelection(
                        transport = SerialTransport.BLUETOOTH,
                        path = "android-bt:${btPort.address}:${btPort.portIndex}",
                        usbDeviceId = null,
                        btAddress = btPort.address,
                        portIndex = btPort.portIndex
                    )
                    Log.i(TAG, "Auto-selected Bluetooth serial port: ${btPort.label}")
                    openHamlibSerialBluetooth(rigModel, btSelection)
                    return
                }
            }
            Log.w(TAG, "No USB serial device found for Hamlib")
            broadcastError("No USB serial device found for Hamlib control.")
            rigCtlErrorShown = true
            return
        }

        Log.i(TAG, "Hamlib USB device selected: ${usbDevice.deviceName} (id=${usbDevice.deviceId}) port=$portIndex")

        if (!UsbPermissionHelper.hasPermission(this, usbDevice)) {
            Log.i(TAG, "Requesting USB permission for Hamlib device...")
            UsbPermissionHelper.requestPermission(this, usbDevice) { granted ->
                if (granted) {
                    Log.i(TAG, "USB permission granted for Hamlib device")
                    openHamlibSerialUsbInternal(rigModel, usbDevice.deviceId, portIndex, params)
                } else {
                    Log.w(TAG, "USB permission denied for Hamlib device")
                    broadcastError("USB permission denied. Please grant USB access in Settings.")
                    rigCtlErrorShown = true
                }
            }
        } else {
            Log.i(TAG, "USB permission already granted for Hamlib device")
            openHamlibSerialUsbInternal(rigModel, usbDevice.deviceId, portIndex, params)
        }
    }

    private fun openHamlibSerialUsbInternal(
        rigModel: Int,
        deviceId: Int,
        portIndex: Int,
        params: SerialParams
    ) {
        Thread {
            val preopenOk = usbSerialBridge?.open(
                deviceId,
                portIndex,
                params.baudRate,
                params.dataBits.takeIf { it > 0 } ?: 8,
                params.stopBits.takeIf { it > 0 } ?: 1,
                params.parityValue
            ) == true

            if (!preopenOk) {
                mainHandler.post {
                    hamlibRigConnected = false
                    rigCtlErrorShown = true
                    Log.w(TAG, "Hamlib USB pre-open failed for device=$deviceId port=$portIndex")
                    broadcastError("Failed to open USB serial port for Hamlib control.")
                }
                return@Thread
            }

            val resolvedPortIndex = usbSerialBridge?.getActivePortIndex() ?: portIndex
            val serialPath = "android-usb:$deviceId:$resolvedPortIndex"

            val ok = hamlibRigControl?.openSerialPath(
                rigModel,
                serialPath,
                params.baudRate,
                params.dataBits,
                params.stopBits,
                params.parity,
                params.handshake,
                params.dtrState,
                params.rtsState
            ) == true

            mainHandler.post {
                hamlibRigConnected = ok
                rigCtlErrorShown = false
                if (ok) {
                    Log.i(TAG, "Hamlib rig opened (model=$rigModel path=$serialPath)")
                    rigOnline()
                } else {
                    val detail = hamlibRigControl?.getLastError().orEmpty()
                    if (detail.isNotBlank()) {
                        Log.w(TAG, "Hamlib rig open failed: $detail")
                        rigFailure("Failed to open Hamlib rig: $detail")
                    } else {
                        Log.w(TAG, "Hamlib rig open failed")
                        rigFailure("Failed to open Hamlib rig. Check serial connection and settings.")
                    }
                    rigCtlErrorShown = true
                }
            }
        }.start()
    }

    private fun openHamlibSerialBluetooth(rigModel: Int, selection: SerialSelection) {
        val params = readSerialParams()

        bluetoothSerialBridge?.registerNative()

        val address = selection.btAddress
        if (address == null) {
            Log.w(TAG, "Bluetooth address missing for Hamlib")
            broadcastError("No Bluetooth serial device selected for Hamlib control.")
            rigCtlErrorShown = true
            return
        }

        if (BluetoothSerialPortCatalog.findBondedDevice(this, address) == null) {
            Log.w(TAG, "Bluetooth device not paired or unavailable: $address")
            broadcastError("Bluetooth serial device not available. Check pairing and settings.")
            rigCtlErrorShown = true
            return
        }

        Thread {
            val preopenOk = bluetoothSerialBridge?.open(
                address,
                selection.portIndex,
                params.baudRate,
                params.dataBits.takeIf { it > 0 } ?: 8,
                params.stopBits.takeIf { it > 0 } ?: 1,
                params.parityValue
            ) == true

            if (!preopenOk) {
                val detail = bluetoothSerialBridge?.getLastError().orEmpty()
                mainHandler.post {
                    hamlibRigConnected = false
                    rigCtlErrorShown = true
                    if (detail.isNotBlank()) {
                        Log.w(TAG, "Hamlib Bluetooth pre-open failed for addr=$address port=${selection.portIndex}: $detail")
                        broadcastError("Failed to open Bluetooth serial port for Hamlib control: $detail")
                    } else {
                        Log.w(TAG, "Hamlib Bluetooth pre-open failed for addr=$address port=${selection.portIndex}")
                        broadcastError("Failed to open Bluetooth serial port for Hamlib control.")
                    }
                }
                return@Thread
            }

            Log.i(TAG, "Hamlib Bluetooth pre-open ok for addr=$address port=${selection.portIndex}")
            val serialPath = selection.path
            Log.i(TAG, "Hamlib opening Bluetooth rig via path=$serialPath")
            val ok = hamlibRigControl?.openSerialPath(
                rigModel,
                serialPath,
                params.baudRate,
                params.dataBits,
                params.stopBits,
                params.parity,
                params.handshake,
                params.dtrState,
                params.rtsState
            ) == true

            mainHandler.post {
                hamlibRigConnected = ok
                rigCtlErrorShown = false
                if (ok) {
                    Log.i(TAG, "Hamlib rig opened (model=$rigModel path=$serialPath)")
                    rigOnline()
                } else {
                    val detail = hamlibRigControl?.getLastError().orEmpty()
                    if (detail.isNotBlank()) {
                        Log.w(TAG, "Hamlib rig open failed: $detail")
                        rigFailure("Failed to open Hamlib rig: $detail")
                    } else {
                        Log.w(TAG, "Hamlib rig open failed")
                        rigFailure("Failed to open Hamlib rig. Check serial connection and settings.")
                    }
                    rigCtlErrorShown = true
                }
            }
        }.start()
    }

    /** RTS PTT rig: Force Control Lines on the line that isn't keying PTT (Hamlib rigs: set_conf at open). */
    private fun applyForcedLines(params: SerialParams) {
        val dtrKeys = pttMethod() == "dtr"
        if (!dtrKeys && params.dtrState.isNotEmpty()) setSerialLine(usbSerialBridge, bluetoothSerialBridge, true, params.dtrState == "ON")
        if (dtrKeys && params.rtsState.isNotEmpty()) setSerialLine(usbSerialBridge, bluetoothSerialBridge, false, params.rtsState == "ON")
    }

    private data class SerialParams(
        val baudRate: Int,
        val dataBits: Int,
        val stopBits: Int,
        val parity: String,
        val parityValue: Int,
        // PC HamlibTransceiver set_conf values; "" = leave as it is
        val handshake: String,
        val dtrState: String,
        val rtsState: String
    )

    private fun readSerialParams(): SerialParams {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val baudRate = prefs.getString("rig_serial_baud", "4800")?.toIntOrNull() ?: 9600
        // PC HamlibTransceiver: Default leaves Hamlib's own value (0 here)
        val dataBits = prefs.getString("js8cfg_data_bits", "default")?.toIntOrNull() ?: 0
        val stopBits = prefs.getString("js8cfg_stop_bits", "default")?.toIntOrNull() ?: 0
        val parity = prefs.getString("rig_serial_parity", "none") ?: "none"
        val parityValue = when (parity.lowercase(Locale.US)) {
            "odd" -> 1
            "even" -> 2
            "mark" -> 3
            "space" -> 4
            else -> 0
        }
        val handshake = when (prefs.getString("js8cfg_handshake", "default")) {
            "none" -> "None"
            "xonxoff" -> "XONXOFF"
            "hardware" -> "Hardware"
            else -> ""
        }
        fun line(key: String) = when (prefs.getString(key, "")) {
            "high" -> "ON"
            "low" -> "OFF"
            else -> ""
        }
        return SerialParams(baudRate, dataBits, stopBits, parity, parityValue, handshake, line("js8cfg_force_dtr"), line("js8cfg_force_rts"))
    }

    private fun resolveSerialSelection(
        selection: String,
        prefs: android.content.SharedPreferences
    ): SerialSelection? {
        val trimmed = selection.trim()
        if (trimmed.startsWith("android-bt:")) {
            val remainder = trimmed.removePrefix("android-bt:")
            val parts = remainder.split(':', limit = 2)
            val address = BluetoothSerialPortCatalog.normalizeAddress(parts.firstOrNull().orEmpty())
                ?: return null
            val portIndex = parts.getOrNull(1)?.toIntOrNull() ?: 0
            return SerialSelection(
                transport = SerialTransport.BLUETOOTH,
                path = "android-bt:$address:$portIndex",
                usbDeviceId = null,
                btAddress = address,
                portIndex = portIndex
            )
        }

        if (trimmed.startsWith("android-usb:")) {
            val remainder = trimmed.removePrefix("android-usb:")
            val parts = remainder.split(':', limit = 2)
            val deviceId = parts.firstOrNull()?.toIntOrNull() ?: return null
            val portIndex = parts.getOrNull(1)?.toIntOrNull() ?: 0
            return SerialSelection(
                transport = SerialTransport.USB,
                path = "android-usb:$deviceId:$portIndex",
                usbDeviceId = deviceId,
                btAddress = null,
                portIndex = portIndex
            )
        }

        if (trimmed.isNotEmpty() && trimmed != "auto") {
            val parts = trimmed.split(':', limit = 2)
            val deviceId = parts.firstOrNull()?.toIntOrNull() ?: return null
            val portIndex = parts.getOrNull(1)?.toIntOrNull() ?: 0
            return SerialSelection(
                transport = SerialTransport.USB,
                path = "android-usb:$deviceId:$portIndex",
                usbDeviceId = deviceId,
                btAddress = null,
                portIndex = portIndex
            )
        }

        val deviceId = prefs.getString("rig_usb_device_id", "")?.toIntOrNull()
        val portIndex = prefs.getString("rig_usb_port_index", "0")?.toIntOrNull() ?: 0
        return if (deviceId != null) {
            SerialSelection(
                transport = SerialTransport.USB,
                path = "android-usb:$deviceId:$portIndex",
                usbDeviceId = deviceId,
                btAddress = null,
                portIndex = portIndex
            )
        } else {
            SerialSelection(
                transport = SerialTransport.USB,
                path = "auto",
                usbDeviceId = null,
                btAddress = null,
                portIndex = 0
            )
        }
    }

    private enum class SerialTransport {
        USB,
        BLUETOOTH
    }

    private data class SerialSelection(
        val transport: SerialTransport,
        val path: String,
        val usbDeviceId: Int?,
        val btAddress: String?,
        val portIndex: Int
    )

    private fun stopEngine() {
        try {
            engineStartGeneration++
            engineStartInProgress = false
            trusdxInitInProgress = false
            synchronized(pttStateLock) {
                txPttGeneration++
                rigPttDesired = rigPttAsserted
                rigPttDesiredGeneration = txPttGeneration
                rigPttFailureCount = 0
                rigPttCompletion = null
                pttShuttingDown = true
                txPttFailed = false
            }
            engine?.setTransmitReady(false)
            scoSilenceCheckToken++
            scoStartToken++
            audioHelper?.stopCapture()
            audioHelper?.close()
            audioHelper = null
            stopTxMonitor()
            disableScoRouting()

            txHandler.removeCallbacksAndMessages(null)
            synchronized(pttStateLock) {
                rigPttDesired = false
                rigPttDesiredGeneration = txPttGeneration
                rigPttCommandPending = false
                rigPttCompletion = null
            }
            // Rig teardown belongs on the TX handler, not here. With the radio
            // gone a CAT command blocks for hamlib's full timeout, and every
            // HamlibRigControl method shares one monitor, so close() waits
            // behind it. That was nine seconds on the main thread.
            val shutdownMode = rigControlMode
            val shutdownTransport = rtsPttTransport
            val shutdownHamlib = hamlibRigControl
            val shutdownUsb = usbSerialBridge
            val shutdownBluetooth = bluetoothSerialBridge
            val shutdownTruSdx = trusdxSerialSession
            val shutdownQmx = qmxCatSession
            val shutdownUvPro = uvProSession
            val shutdownNetwork = rigCtlClient
            val shutdownPttMethod = pttMethod()
            val shutdownDialHz = currentDialHz
            val shouldReleasePtt = isRigControlConnected()
            if (shutdownMode == "btech_uvpro") uvproWorkersActive.incrementAndGet()

            // Unregistering clears one native global whichever bridge asks, so
            // it stays here: deferred, it could wipe a restart's registration.
            shutdownUsb?.unregisterNative()
            shutdownBluetooth?.unregisterNative()
            pttUsbBridge?.close()
            pttUsbBridge = null

            createRigTransports()
            rigCtlClient = null
            rigCtlConnected = false
            rigCtlErrorShown = false
            hamlibRigConnected = false
            rtsPttConnected = false
            trusdxConnected = false
            qmxConnected = false
            uvproConnected = false
            trusdxWatchdogToken++
            trusdxTxIntentActive = false
            stopTruSdxRxWorker()
            clearTruSdxRxFrameQueue()
            trusdxRxKeepaliveToken++
            trusdxRxKeepaliveCount = 0L
            rtsPttTransport = null
            rigControlMode = "none"
            if (isTruSdxDiagnosticsEnabled() &&
                (trusdxRxFrames > 0 || trusdxTxFrames > 0 || trusdxParserResyncs > 0 || trusdxTxDrops > 0 || trusdxRxUnderruns > 0 || trusdxRxFrameDrops > 0)
            ) {
                Log.i(
                    TAG,
                    "TruSDX diagnostics: rxFrames=$trusdxRxFrames rxSamples=$trusdxRxSamples rxFrameDrops=$trusdxRxFrameDrops rxSubmitDrops=$trusdxRxSubmitDrops rxUnderruns=$trusdxRxUnderruns txFrames=$trusdxTxFrames txSamples=$trusdxTxSamples txSilent=$trusdxTxSilentFrames txDrops=$trusdxTxDrops parserResyncs=$trusdxParserResyncs"
                )
            }

            txHandler.post {
                if (shouldReleasePtt) {
                    // Captured references, not setRigPtt: the fields already
                    // hold the transports the next start will use.
                    val released = when (shutdownMode) {
                        "network" -> shutdownPttMethod == "vox" || shutdownNetwork?.setPtt(false) == true
                        "hamlib_usb" -> when (shutdownPttMethod) {
                            "vox" -> true
                            "dtr", "rts" -> setSerialLine(shutdownUsb, shutdownBluetooth, shutdownPttMethod == "dtr", false)
                            else -> shutdownHamlib?.setPtt(false) == true
                        }
                        "rts_ptt" -> if (shutdownPttMethod == "dtr") setSerialLine(shutdownUsb, shutdownBluetooth, true, false) else when (shutdownTransport) {
                            SerialTransport.USB -> shutdownUsb?.setRts(false) == true
                            SerialTransport.BLUETOOTH -> shutdownBluetooth?.setRts(false) == true
                            else -> false
                        }
                        "trusdx_serial" -> shutdownTruSdx?.setPtt(false) == true
                        "qmx_serial" -> shutdownQmx?.setPtt(false) == true
                        "btech_uvpro" -> shutdownUvPro?.setPtt(false) == true
                        else -> false
                    }
                    if (released) {
                        synchronized(pttStateLock) { rigPttAsserted = false }
                    } else {
                        Log.e(TAG, "Unable to confirm PTT release during shutdown")
                    }
                    clearSplit(shutdownMode, shutdownDialHz, shutdownNetwork, shutdownHamlib, shutdownTruSdx, shutdownQmx)
                }
                shutdownHamlib?.close()
                shutdownTruSdx?.stop()
                shutdownQmx?.stop()
                try {
                    shutdownUvPro?.stop()
                } finally {
                    if (shutdownMode == "btech_uvpro") uvproWorkersActive.decrementAndGet()
                }
                shutdownUsb?.close()
                shutdownBluetooth?.close()
                shutdownNetwork?.disconnect()
                Log.i(TAG, "Rig control torn down")
            }

            pskReporterClient?.stop(flush = true)
            pskReporterEnabled = false

            engine?.stop()
            engine?.close()
            engine = null

            broadcastEngineState(STATE_STOPPED)
            Log.i(TAG, "Engine stopped")

        } catch (e: Exception) {
            Log.e(TAG, "Error stopping engine", e)
        }
    }

    private fun broadcastEngineState(state: String) {
        updateAprsState()
        val intent = Intent(ACTION_ENGINE_STATE).apply {
            putExtra(EXTRA_STATE, state)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        // API: RX on / off (PC monitor -> statusUpdate)
        val monitoring = state == STATE_RUNNING
        if (Js8Api.State.monitoring != monitoring) {
            Js8Api.State.monitoring = monitoring
            Js8Api.statusUpdate()
        }

        // The connected flags are assigned in too many places to broadcast from
        // each one, so poll while the engine runs. Every poll sends, not only
        // changes: local broadcasts are not sticky, and a UI that attaches
        // late or missed one while stopped has no other way to catch up
        if (state == STATE_RUNNING) {
            startRigStatusPolling()
        } else {
            stopRigStatusPolling()
        }
    }

    private val rigStatusHandler = Handler(Looper.getMainLooper())
    private val rigStatusRunnable = object : Runnable {
        override fun run() {
            broadcastRigStatus(isRigControlConnected())
            readRig() // PC Poll Interval: the rig's frequency is read on this timer
            rigStatusHandler.postDelayed(this, numberPref("js8cfg_cat_poll_interval", 1.0).toLong().coerceAtLeast(1L) * 1000L)
        }
    }

    private fun startRigStatusPolling() {
        rigStatusHandler.removeCallbacks(rigStatusRunnable)
        rigStatusHandler.post(rigStatusRunnable)
    }

    private fun stopRigStatusPolling() {
        rigStatusHandler.removeCallbacks(rigStatusRunnable)
        rigOpening = false
        broadcastRigStatus(false)
    }

    private fun broadcastRigStatus(connected: Boolean) {
        if (connected) rigOpening = false
        val intent = Intent(ACTION_RIG_STATUS).apply {
            putExtra(EXTRA_RIG_CONNECTED, connected)
            putExtra(EXTRA_RIG_OPENING, rigOpening)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    // Shared with injected decodes
    /** Directed-message parser shared with the main screen (Js8Activity.parseDirected). */
    private val apiDirectedParser = com.js8call.example.js8ui.Js8Activity()

    /**
     * API events for one frame (PC processDecodeEvent / processRxActivity / processCommandActivity):
     * WSJT-X Decode, RX.ACTIVITY, and RX.DIRECTED for a directed command heard (for anyone).
     * WSJT-X gets the decode's own [dt] (pDE 265), RX.ACTIVITY the Time Delta [tdrift].
     */
    private fun apiDecode(utc: Int, snr: Int, dt: Float, tdrift: Float, freq: Float, text: String, type: Int, quality: Float, mode: Int) {
        val now = driftedNowMs()
        val offset = freq.toInt()
        // DecodedText::isLowConfidence: quality below 0.17 (the PC marks those with '?')
        Js8Api.wsjtxDecode(utc, snr, dt, offset, text, quality < 0.17f)
        if (!Js8Api.canSend()) return
        Js8Api.rxActivity(text, offset, snr, mode, tdrift, now, type, decodeDial())
    }

    private class DirectedBuffer(
        val d: com.js8call.example.js8ui.Js8Activity.Directed, val parts: MutableList<String>, val offset: Int, val snr: Int, val mode: Int,
        val dt: Float, val startMs: Long
    ) {
        /** Time of its newest frame (PC processBufferedActivity takes the latest of cmd and msgs). */
        var lastUpdated = startMs
    }

    /**
     * PC processBufferedActivity (18-40): closed 60 s after its newest frame if a text frame came (parts[0] is
     * the command frame's own text), dropped at 90 s. Checked every second, not only when a frame arrives.
     */
    private fun cleanupDirectedBuffers(now: Long) {
        directedBuffers.entries.toList().forEach { (key, b) ->
            val age = now - b.lastUpdated
            when {
                age > BUFFER_CLOSE_MS && b.parts.size > 1 -> { directedBuffers.remove(key); finishDirected(b) }
                age > BUFFER_DROP_MS -> directedBuffers.remove(key)
            }
        }
    }

    private val directedBuffers = mutableMapOf<Int, DirectedBuffer>()

    /**
     * PC processBufferedActivity then processCommandActivity: a buffered command waits for its last
     * frame and its checksum, the others go out at once. Feeds RX.DIRECTED and DIRECTED.TXT.
     */
    private fun directedActivity(snr: Int, dt: Float, freq: Float, text: String, type: Int, mode: Int) {
        val now = System.currentTimeMillis()
        val last = (type and 0x2) != 0
        cleanupDirectedBuffers(now)
        val open = findClosestMsgBufferKey(directedBuffers.keys, freq, rxThresholdHz(mode))
        if (open != null && (type and FRAME_FIRST) == 0) {
            val key = directedBuffers.followDrift(open, freq)
            val b = directedBuffers.getValue(key)
            b.parts.add(text)
            b.lastUpdated = now
            if (last) { directedBuffers.remove(key); finishDirected(b) }
            return
        }
        val d = apiDirectedParser.parseDirected(text) ?: return
        if (d.from == "<....>" || d.to == "<....>") return
        // Varicode::isCommandAllowed, CQ and heartbeats included; free text to a call (PC command " ") from a directed frame
        val commands = com.js8call.example.util.Js8Commands
        val freeText = d.cmd.isEmpty() && !DecodedMessage.isDataFrame(type)
        if (!freeText && !commands.COMMANDS.containsKey(d.cmd.uppercase())) return
        val cmdIndex = text.indexOf(" ${d.cmd} ", ignoreCase = true)
        val first = if (cmdIndex >= 0) text.substring(cmdIndex + d.cmd.length + 2) else d.extra
        if (commands.isBuffered(d.cmd)) {
            val b = DirectedBuffer(d, mutableListOf(first), freq.toInt(), snr, mode, dt, now)
            if (last) finishDirected(b) else directedBuffers[Math.round(freq)] = b
            return
        }
        // PC isCommandBuffered: every other command with a space in its name waits for the last frame too, all but "?".
        // CQ and heartbeat frames go at once (processDecodeEvent.cpp 371-420). The number after SNR stays its own word.
        if (!last && d.cmd != "?" && commands.COMMANDS[d.cmd.uppercase()] != -1) {
            directedBuffers[Math.round(freq)] =
                DirectedBuffer(d, mutableListOf(if (first.isEmpty()) "" else "$first "), freq.toInt(), snr, mode, dt, now)
            return
        }
        emitDirected(d, text.trim().substringAfter(':').trim(), text, last, freq.toInt(), snr, mode, dt, now)
    }

    /** A whole buffered command: dropped on a bad checksum, as the PC does. */
    private fun finishDirected(b: DirectedBuffer) {
        val commands = com.js8call.example.util.Js8Commands
        var message = assembleMsgPayload(b.parts).trimEnd()
        if (commands.checksumBits(b.d.cmd) == 16) {
            val (valid, body) = validateRelayChecksum(message)
            if (!valid) return
            message = body
        }
        // PC "%1%2".arg(to).arg(cmd), the command names with their leading space: two spaces after the call for
        // free text, none before ">" and "?" ("K1ABC> HELLO", processCommandActivity.cpp 131-141, Varicode.cpp 50-118)
        val head = when (b.d.cmd) {
            "" -> "${b.d.to} "
            ">", "?" -> "${b.d.to}${b.d.cmd}"
            else -> "${b.d.to} ${b.d.cmd.uppercase()}"
        }
        val base = listOf(head, message.trim()).filter { it.isNotEmpty() }.joinToString(" ")
        emitDirected(b.d, base, "$base ${b.d.extra}", true, b.offset, b.snr, b.mode, b.dt, driftedNowMs())
    }

    /** "TO CMD EXTRA" without the sender; the last frame ends with the end-of-transmission mark. */
    private fun emitDirected(d: com.js8call.example.js8ui.Js8Activity.Directed, text: String, gridText: String, last: Boolean,
                             offset: Int, snr: Int, mode: Int, dt: Float, now: Long) {
        val eot = PreferenceManager.getDefaultSharedPreferences(this).getString("js8cfg_eot", "♢").orEmpty().ifEmpty { "♢" }
        val base = if (last) "${text.trimEnd()} $eot " else text
        val snrText = com.js8call.example.js8ui.Js8Activity.formatSnr(snr)
        messageLogger?.logDirected(driftedNowMs(), currentDialHz, offset, snrText, "${d.from}: $base")
        if (!Js8Api.canSend()) return
        val grid = (d.extra + " " + gridText).split(Regex("\\s+")).lastOrNull { com.js8call.example.js8ui.Js8Activity.isGrid(it) && it.length >= 4 }.orEmpty()
        val cmd = if (d.cmd == ">" || d.cmd == "?") d.cmd else " ${d.cmd.uppercase()}" // the PC's names carry a leading space
        Js8Api.rxDirected(d.from, d.to, cmd, grid, d.extra, base, offset, snr, mode, dt, now, decodeDial())
    }

    /** API: PTT on / off (PC emitPTT), the frame's tones (emitTones) and a status update. */
    private fun apiTransmitting(on: Boolean, activeEngine: JS8Engine) {
        if (Js8Api.State.transmitting == on) return
        Js8Api.State.transmitting = on
        Js8Api.ptt(on)
        if (on) activeEngine.txFrameTones().takeIf { it.isNotEmpty() }?.let {
            Js8Api.txFrame(it)
            if (BuildConfig.DEBUG) Log.i(TAG, "JS8 test tones: ${it.joinToString("")}")
        }
        Js8Api.statusUpdate()
    }

    private fun handleDecode(
        utc: Int, snr: Int, dt: Float, freq: Float,
        text: String, type: Int, quality: Float, mode: Int, driftMs: Int
    ) {
        stopCqForMessage(text)
        synchronized(recentOffsets) { recentOffsets[Math.round(freq)] = System.currentTimeMillis() }
        notifyDecode(text)
        // PC processDecodeEvent: the Time Delta column, RX.ACTIVITY and RX.DIRECTED take the clock drift in place
        // of the decode's offset while auto sync runs on Normal or Slow (pDE 312-314, 370-372, 462-464; WG 369-376)
        val tdrift = if (autoDriftOn && (mode == SUBMODE_NORMAL || mode == SUBMODE_SLOW)) (engine?.timeDriftMs() ?: 0L) / 1000f else dt
        broadcastDecode(utc, snr, tdrift, freq, text, type, quality, mode, driftMs)
        apiDecode(utc, snr, dt, tdrift, freq, text, type, quality, mode)
        spotDecode(snr, freq, text, type, mode)
        directedActivity(snr, tdrift, freq, text, type, mode)
        handleRelayFrame(text, snr, mode, freq, type)
        maybeHandleIncomingMessage(text, snr, freq, type, mode)
        // PC processDecodeEvent: a command is answered when its last frame is in
        deferAutoReply(text, freq, type, mode)?.let { maybeHandleAutoReply(it, snr, mode) }
    }

    private val pendingAutoReplies = mutableMapOf<Int, Pair<String, Long>>()

    /** The command to answer now, if any: a one-frame command, or one whose data frames just ended. */
    private fun deferAutoReply(text: String, freq: Float, type: Int, mode: Int): String? {
        val now = System.currentTimeMillis()
        synchronized(pendingAutoReplies) {
            pendingAutoReplies.entries.removeIf { now - it.value.second > BUFFER_CLOSE_MS }
            if (!DecodedMessage.isDataFrame(type)) {
                if (isLastFrame(type)) return text
                pendingAutoReplies[Math.round(freq)] = text to now
                return null
            }
            val key = findClosestMsgBufferKey(pendingAutoReplies.keys, freq, rxThresholdHz(mode)) ?: return null
            if (!isLastFrame(type)) { pendingAutoReplies.followDrift(key, freq); return null }
            return pendingAutoReplies.remove(key)?.first
        }
    }

    /** PC tryNotify: only with Enable Notifications and that event's box ticked. Plays its Sound File, or the phone's notification sound. */
    private fun tryNotify(key: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (!prefs.getBoolean("js8cfg_notifications", false)) return
        if (prefs.getString("js8cfg_notify_$key", "false") != "true") return
        Log.i(TAG, "Notify: $key")
        val file = prefs.getString(com.js8call.example.js8ui.Js8SettingsSchema.NOTIFY_FILE_PREFIX + key, "").orEmpty()
        val sound = if (file.isEmpty()) RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) else android.net.Uri.parse(file)
        RingtoneManager.getRingtone(this, sound)?.play()
    }

    private fun notifyDecode(text: String) {
        if (text.trim().substringBefore(' ').endsWith(":")) {
            extractHeardCallsign(text)?.let { call ->
                if (notifiedCallsigns.add(call)) tryNotify(if (Js8LogBook.workedBefore(this, call)) "call_old" else "call_new")
            }
        }
        if (cqRegex.containsMatchIn(text)) {
            tryNotify("cq")
        } else if (parseHeartbeat(text) != null) {
            tryNotify("hb")
        } else {
            val callsign = getConfiguredCallsign() ?: return
            val directed = parseDirectedCommand(text) ?: return
            if (!isSelfCallsign(callsign, directed.to) && !isSubscribedGroup(directed.to)) return
            tryNotify("directed")
            if (directed.command.uppercase() == "ACK") tryNotify("ack")
        }
    }

    private fun injectDecode(intent: Intent) {
        val text = intent.getStringExtra(EXTRA_TEXT)
        if (text.isNullOrBlank()) {
            Log.w(TAG, "Injected decode has no text")
            return
        }
        val snr = intent.getIntExtra(EXTRA_SNR, -10)
        val freq = intent.getFloatExtra(EXTRA_FREQ, 1500f)
        val type = intent.getIntExtra(EXTRA_TYPE, 3)
        val mode = intent.getIntExtra(EXTRA_MODE, 0)
        val now = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val utc = now.get(Calendar.HOUR_OF_DAY) * 10000 +
            now.get(Calendar.MINUTE) * 100 + now.get(Calendar.SECOND)
        Log.i(TAG, "Injected decode: '$text' snr=$snr freq=$freq type=$type mode=$mode")
        handleDecode(utc, snr, 0f, freq, text, type, intent.getFloatExtra(EXTRA_QUALITY, 1f), mode, 0)
    }

    private fun broadcastDecode(
        utc: Int, snr: Int, dt: Float, freq: Float,
        text: String, type: Int, quality: Float, mode: Int, driftMs: Int
    ) {
        val intent = Intent(ACTION_DECODE).apply {
            putExtra(EXTRA_UTC, utc)
            putExtra(EXTRA_SNR, snr)
            putExtra(EXTRA_DT, dt)
            putExtra(EXTRA_FREQ, freq)
            putExtra(EXTRA_TEXT, text)
            putExtra(EXTRA_TYPE, type)
            putExtra(EXTRA_QUALITY, quality)
            putExtra(EXTRA_MODE, mode)
            putExtra(EXTRA_DRIFT_MS, driftMs)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private var timeSyncOncePending = false
    private var driftMmaMs = 0L
    private var driftMmaN = 0

    private fun maybeApplyTimeSync(suggestedDriftMs: Int) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val autoSync = prefs.getBoolean(PREF_TIME_SYNC_AUTO, false)
        if (!autoSync && !timeSyncOncePending) return

        if (timeSyncOncePending) {
            timeSyncOncePending = false
            driftMmaMs = suggestedDriftMs.toLong()
            driftMmaN = 1
            applyTimeDrift(suggestedDriftMs.toLong())
            return
        }

        if (driftMmaN == 0) {
            driftMmaN = 1
            driftMmaMs = engine?.timeDriftMs() ?: 0L
        }
        driftMmaMs = (((driftMmaN - 1) * driftMmaMs) + suggestedDriftMs) / driftMmaN
        if (driftMmaN < 60) driftMmaN++
        applyTimeDrift(driftMmaMs)
    }

    // Duplicate frames (processDecodeEvent.cpp m_messageDupeCache): the same frame in the same
    // speed within half a cycle is dropped before anything sees it. Automatic Time Drift decodes
    // every second, so one signal is decoded several times.
    private val frameDupeCache = mutableMapOf<String, Long>()

    private fun isDuplicateFrame(text: String, mode: Int): Boolean = synchronized(frameDupeCache) {
        val now = System.currentTimeMillis()
        val key = "$mode:$text"
        val last = frameDupeCache[key]
        if (last != null && now - last < com.js8call.example.js8ui.Js8Activity.periodSec(mode) * 500L) return true
        frameDupeCache[key] = now
        if (frameDupeCache.size > 500) frameDupeCache.entries.removeIf { now - it.value > 120_000 }
        false
    }

    // Waterfall Controls > Display > Display Decode Attempts (decoder syncStats)
    private var syncStatsOn = false

    // Waterfall Controls > Timing > Start Automatic Time Drift (WideGraph::on_autoDriftButton_toggled).
    // Kept after a restart with its count (Rob, 2026-10-22; the PC button is not kept).
    private var autoDriftOn = false
    private var autoDriftStopAfter = 0
    private var autoDriftDecodesLeft = 0

    /** PC decode(): filter limits, 0-5000 Hz with the filter off, offset tried first. */
    private fun applyDecodeRange() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val filter = runCatching { prefs.getBoolean("js8cfg_filter_enabled", true) }.getOrDefault(true)
        val center = runCatching { prefs.getInt("js8cfg_filter_center", 1500) }.getOrDefault(1500)
        val width = runCatching { prefs.getInt("js8cfg_filter_width", 2000) }.getOrDefault(2000)
        val low = if (filter) (center - width / 2).coerceIn(0, 5000) else 0
        val high = if (filter) (center + width / 2).coerceIn(0, 5000) else 5000
        val offset = prefs.getString("js8cfg_tx_offset", null)?.toFloatOrNull()?.toInt() ?: 1500
        engine?.setDecodeRange(low, high, offset)
    }

    private fun setAutoDrift(on: Boolean, stopAfter: Int) {
        autoDriftOn = on
        autoDriftStopAfter = if (on) stopAfter.coerceAtLeast(0) else 0
        autoDriftDecodesLeft = autoDriftStopAfter
        saveAutoDrift()
        engine?.setAutoSync(on)
        Log.i(TAG, "Automatic time drift ${if (on) "started (stop after $stopAfter)" else "stopped"}")
        LocalBroadcastManager.getInstance(this)
            .sendBroadcast(Intent(ACTION_AUTO_DRIFT).putExtra(EXTRA_AUTO_DRIFT_ON, on))
    }

    // Normal / Slow decode while Automatic Time Drift is on (processDecodeEvent.cpp driftQueue,
    // WideGraph::notifyDriftedSignalsDecoded): averaged over up to 60 decodes, then the count.
    private fun applyAutoDrift(suggestedDriftMs: Int) {
        if (driftMmaN == 0) {
            driftMmaN = 1
            driftMmaMs = engine?.timeDriftMs() ?: 0L
        }
        driftMmaMs = (((driftMmaN - 1) * driftMmaMs) + suggestedDriftMs) / driftMmaN
        if (driftMmaN < 60) driftMmaN++
        applyTimeDrift(driftMmaMs)
        if (autoDriftStopAfter > 0 && --autoDriftDecodesLeft <= 0) setAutoDrift(false, 0) else saveAutoDrift()
    }

    private fun applyTimeDrift(driftMs: Long) {
        engine?.setTimeDriftMs(driftMs)
        PreferenceManager.getDefaultSharedPreferences(this)
            .edit()
            .putLong(PREF_TIME_DRIFT_MS, driftMs)
            .apply()
        Log.i(TAG, "Time drift applied: $driftMs ms")
        broadcastTimeDrift(driftMs)
        // TX slots are computed against the drifted clock now; reschedule.
        scheduleHeartbeat(false)
    }

    private fun applyTimeDriftSetting() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val driftMs = prefs.getLong(PREF_TIME_DRIFT_MS, 0L)
        if (driftMs != 0L) {
            engine?.setTimeDriftMs(driftMs)
            Log.i(TAG, "Time drift restored: $driftMs ms")
        }
        broadcastTimeDrift(driftMs)
    }

    private fun broadcastTimeDrift(driftMs: Long) {
        val intent = Intent(ACTION_TIME_DRIFT).apply {
            putExtra(EXTRA_TIME_DRIFT_MS, driftMs)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun broadcastSpectrum(
        bins: FloatArray, binHz: Float,
        powerDb: Float, peakDb: Float
    ) {
        val intent = Intent(ACTION_SPECTRUM).apply {
            putExtra(EXTRA_BINS, bins)
            putExtra(EXTRA_BIN_HZ, binHz)
            putExtra(EXTRA_POWER_DB, powerDb)
            putExtra(EXTRA_PEAK_DB, peakDb)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun broadcastDecodeStarted(submodes: Int) {
        val intent = Intent(ACTION_DECODE_STARTED).apply {
            putExtra(EXTRA_SUBMODES, submodes)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun broadcastDecodeFinished(count: Int) {
        val intent = Intent(ACTION_DECODE_FINISHED).apply {
            putExtra(EXTRA_COUNT, count)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun broadcastError(message: String) {
        val intent = Intent(ACTION_ERROR).apply {
            putExtra(EXTRA_ERROR_MESSAGE, message)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun broadcastAudioDevice(deviceName: String) {
        val intent = Intent(ACTION_AUDIO_DEVICE).apply {
            putExtra(EXTRA_AUDIO_DEVICE, deviceName)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun broadcastAudioDeviceLost(deviceName: String) {
        val intent = Intent(ACTION_AUDIO_DEVICE_LOST).apply {
            putExtra(EXTRA_AUDIO_DEVICE, deviceName)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun broadcastRadioFrequency(frequencyHz: Long) {
        // API: the radio's dial; a new band sends RIG.FREQ (displayDialFrequency)
        if (frequencyHz > 0 && frequencyHz != Js8Api.State.radioDialHz) {
            Js8Api.State.radioDialHz = frequencyHz
            Js8Api.bandCheck()
            Js8Api.statusUpdate()
        }
        val intent = Intent(ACTION_RADIO_FREQUENCY).apply {
            putExtra(EXTRA_RADIO_FREQUENCY_HZ, frequencyHz)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun getActiveAudioDevice(): String {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        // For Android M (API 23) and above, use AudioDeviceInfo
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)

            // If a specific device is selected, find it
            if (selectedAudioDeviceId != -1) {
                for (device in devices) {
                    if (device.id == selectedAudioDeviceId) {
                        val deviceType = getDeviceName(device)
                        Log.i(TAG, "Using selected audio device: $deviceType (ID: ${device.id})")
                        return deviceType
                    }
                }
            }

            // Find the first active input device
            for (device in devices) {
                val deviceType = getDeviceName(device)
                // Return first valid device (Oboe typically uses default)
                Log.i(TAG, "Detected audio device: $deviceType")
                return deviceType
            }
        }

        // Fallback for older Android versions or if no device found
        return "Microphone"
    }

    private fun updateOutputDeviceForInput(inputDeviceId: Int) {
        val outputId = findOutputDeviceId(inputDeviceId)
        selectedOutputDeviceId = outputId
        engine?.setOutputDevice(outputId)
        if (outputId > 0) {
            Log.i(TAG, "Using output device: ${getOutputDeviceName(outputId)} (ID: $outputId)")
        } else {
            Log.i(TAG, "Using default output device")
        }
    }

    private fun findOutputDeviceId(inputDeviceId: Int): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return -1
        // Settings > Audio > Output:, kept while that device is plugged in
        PreferenceManager.getDefaultSharedPreferences(this).getString("js8cfg_output_device", "default")?.toIntOrNull()?.let { id ->
            val outputs = (getSystemService(AUDIO_SERVICE) as AudioManager).getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            if (outputs.any { it.id == id }) return id
        }
        if (inputDeviceId == -1) return -1

        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val inputDevice = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .firstOrNull { it.id == inputDeviceId } ?: return -1

        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val inputName = inputDevice.productName?.toString()?.takeIf { it.isNotBlank() }
        val inputFamily = deviceFamily(inputDevice.type)

        var output = outputs.firstOrNull { device ->
            device.type == inputDevice.type &&
                (inputName == null || device.productName?.toString() == inputName)
        }

        if (output == null && inputName != null) {
            output = outputs.firstOrNull { device ->
                device.productName?.toString() == inputName &&
                    (inputFamily.isEmpty() || deviceFamily(device.type) == inputFamily)
            }
        }

        if (output == null && inputName != null) {
            output = outputs.firstOrNull { device -> device.productName?.toString() == inputName }
        }

        if (output == null && inputFamily.isNotEmpty()) {
            output = outputs.firstOrNull { device -> deviceFamily(device.type) == inputFamily }
        }

        return output?.id ?: -1
    }

    private fun deviceFamily(type: Int): String {
        return when (type) {
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_ACCESSORY,
            AudioDeviceInfo.TYPE_USB_HEADSET -> "usb"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "bt_sco"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "bt_a2dp"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "wired"
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> "builtin"
            AudioDeviceInfo.TYPE_LINE_ANALOG,
            AudioDeviceInfo.TYPE_LINE_DIGITAL -> "line"
            else -> ""
        }
    }

    private fun getOutputDeviceName(deviceId: Int): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return "Default Output"
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val device = outputs.firstOrNull { it.id == deviceId } ?: return "Default Output"
        return getDeviceName(device)
    }

    private fun getDeviceName(device: AudioDeviceInfo): String {
        return AudioDevices.nameFor(device) ?: "Unknown Device"
    }

    /** Station > Audio > Input channel changed while receiving (PC reopens the input on OK). */
    private fun restartAudioCapture() {
        val activeEngine = engine ?: return
        val helper = audioHelper ?: return
        helper.stopCapture()
        helper.close()
        audioHelper = buildAudioHelper(activeEngine, selectedAudioDeviceId).also { it.startCapture() }
    }

    private fun switchAudioDevice(deviceId: Int) {
        try {
            if (engine != null && deviceId == selectedAudioDeviceId) {
                Log.i(TAG, "Audio device already selected (ID: $deviceId); ignoring switch request")
                return
            }
            // Store the selected device ID
            selectedAudioDeviceId = deviceId

            if (rigControlMode == "trusdx_serial") {
                val speakerEnabled = deviceId == TRUSDX_AUDIO_SPEAKER_ID
                val ok = trusdxSerialSession?.setSpeakerEnabled(speakerEnabled) == true
                if (!ok) {
                    broadcastError("Failed to update TruSDX speaker mode")
                }
                val label = if (speakerEnabled) TRUSDX_AUDIO_SPEAKER_NAME else TRUSDX_AUDIO_SERIAL_NAME
                broadcastAudioDevice(label)
                return
            }
            if (rigControlMode == "btech_uvpro") {
                broadcastAudioDevice(UVPRO_AUDIO_NAME)
                return
            }

            scoRestartAttempts = 0
            scoSourceIndex = 0
            scoSilenceCheckToken++
            scoStartToken++

            if (engine != null) {
                audioHelper?.stopCapture()
                audioHelper?.close()
                audioHelper = null
                if (isScoInputDevice(deviceId)) {
                    startAudioCaptureWithScoWait(engine!!, deviceId)
                } else {
                    startAudioCapture(engine!!, deviceId)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error switching audio device", e)
            broadcastError("Error switching audio device: ${e.message}")
        }
    }

    private fun scheduleScoSilenceCheck() {
        if (!isScoInputDevice(selectedAudioDeviceId)) return
        val token = ++scoSilenceCheckToken
        scheduleScoSilenceCheckInternal(token)
    }

    private fun scheduleScoSilenceCheckInternal(token: Int) {
        mainHandler.postDelayed({
            if (token != scoSilenceCheckToken) return@postDelayed
            val helper = audioHelper ?: return@postDelayed
            val maxAbs = helper.getLastAbsMax()
            if (maxAbs < SCO_SILENCE_THRESHOLD && scoRestartAttempts < SCO_MAX_RESTARTS) {
                scoRestartAttempts++
                scoSourceIndex = (scoSourceIndex + 1) % scoSourceCandidates.size
                Log.w(TAG, "SCO input appears silent (max=$maxAbs); restarting capture (attempt $scoRestartAttempts/$SCO_MAX_RESTARTS)")
                restartAudioCaptureForSco()
                return@postDelayed
            }
            scheduleScoSilenceCheckInternal(token)
        }, SCO_SILENCE_CHECK_DELAY_MS)
    }

    private fun restartAudioCaptureForSco() {
        val deviceId = selectedAudioDeviceId
        val activeEngine = engine ?: return
        scoStartToken++
        audioHelper?.stopCapture()
        audioHelper?.close()
        audioHelper = null
        startAudioCaptureWithScoWait(activeEngine, deviceId)
    }

    private fun startAudioCapture(activeEngine: JS8Engine, deviceId: Int) {
        applyInputRouting(deviceId)
        startAudioCaptureInternal(activeEngine, deviceId)
    }

    private fun startAudioCaptureWithScoWait(activeEngine: JS8Engine, deviceId: Int) {
        val token = scoStartToken
        applyInputRouting(deviceId)
        waitForScoActive(activeEngine, deviceId, token, 0)
    }

    private fun waitForScoActive(
        activeEngine: JS8Engine,
        deviceId: Int,
        token: Int,
        attempt: Int
    ) {
        if (token != scoStartToken) return
        val scoActive = isScoActive(deviceId)
        if (scoActive || attempt >= SCO_START_MAX_ATTEMPTS) {
            if (scoActive) {
                Log.i(TAG, "SCO audio active; starting capture")
            } else {
                Log.w(TAG, "SCO audio not active after $attempt checks; starting capture anyway")
            }
            startAudioCaptureInternal(activeEngine, deviceId)
            return
        }

        mainHandler.postDelayed({
            waitForScoActive(activeEngine, deviceId, token, attempt + 1)
        }, SCO_START_WAIT_INTERVAL_MS)
    }

    private fun startAudioCaptureInternal(activeEngine: JS8Engine, deviceId: Int) {
        audioHelper = buildAudioHelper(activeEngine, deviceId)
        if (audioHelper?.startCapture() == true) {
            Log.i(TAG, "Audio capture started with device ID: $deviceId")
            val deviceName = getActiveAudioDevice()
            broadcastAudioDevice(deviceName)
            updateOutputDeviceForInput(deviceId)
            broadcastEngineState(STATE_RUNNING)
            broadcastProcessTxQueue()
            scheduleScoSilenceCheck()
        } else {
            Log.e(TAG, "Failed to start audio capture")
            broadcastError("Failed to start audio capture")
            broadcastEngineState(STATE_ERROR) // no audio, no receiving: RX off (errors alone no longer change it)
        }
    }

    private fun handleTruSdxAudioFrame(samplesU8: ByteArray) {
        if (rigControlMode != "trusdx_serial") return
        if (samplesU8.isEmpty()) return

        trusdxRxFrames += 1
        trusdxRxSamples += samplesU8.size.toLong()
        val now = System.nanoTime()
        trusdxLastRxAudioNs = now
        trusdxRxRateWindowSamples += samplesU8.size.toLong()
        enqueueTruSdxRxFrame(samplesU8)

        if (trusdxRxFrames <= 10) {
            val queued = trusdxRxFrameQueue.size
            Log.i(TAG, "TruSDX RX frame #$trusdxRxFrames size=${samplesU8.size} queued=$queued")
        }
        if (trusdxRxRateWindowStartNs == 0L) {
            trusdxRxRateWindowStartNs = now
        } else {
            val windowNs = now - trusdxRxRateWindowStartNs
            if (windowNs >= 1_000_000_000L) {
                val observedRate = ((trusdxRxRateWindowSamples * 1_000_000_000L) / windowNs).toInt()
                if (isTruSdxDiagnosticsEnabled()) {
                    Log.i(TAG, "TruSDX RX stream: frames=$trusdxRxFrames samples=$trusdxRxSamples chunk=${samplesU8.size} rate=${observedRate}B/s")
                }
                trusdxRxRateWindowStartNs = now
                trusdxRxRateWindowSamples = 0L
            }
        }
    }

    private fun scheduleTruSdxRxKeepAlive() {
        val token = ++trusdxRxKeepaliveToken
        mainHandler.postDelayed(object : Runnable {
            override fun run() {
                if (token != trusdxRxKeepaliveToken) return
                if (rigControlMode != "trusdx_serial") return
                if (!trusdxConnected) {
                    mainHandler.postDelayed(this, TRUSDX_RX_KEEPALIVE_INTERVAL_MS)
                    return
                }
                if (!trusdxTxIntentActive && !isTransmitActive()) {
                    val ok = trusdxSerialSession?.sendRxKeepAlive() == true
                    trusdxRxKeepaliveCount += 1
                    if (!ok) {
                        Log.w(TAG, "TruSDX frequency poll failed")
                    } else if (isTruSdxDiagnosticsEnabled() && trusdxRxKeepaliveCount % 10L == 0L) {
                        Log.i(TAG, "TruSDX frequency poll count=$trusdxRxKeepaliveCount")
                    }
                }
                mainHandler.postDelayed(this, TRUSDX_RX_KEEPALIVE_INTERVAL_MS)
            }
        }, TRUSDX_RX_KEEPALIVE_INTERVAL_MS)
    }

    private fun enqueueTruSdxRxFrame(frame: ByteArray) {
        if (!trusdxRxFrameQueue.offerLast(frame)) {
            trusdxRxFrameQueue.pollFirst()
            trusdxRxFrameDrops += 1
            trusdxRxFrameQueue.offerLast(frame)
        }
    }

    private fun clearTruSdxRxFrameQueue() {
        trusdxRxFrameQueue.clear()
    }

    private fun startTruSdxRxWorker() {
        if (trusdxRxWorkerRunning) return
        trusdxRxWorkerRunning = true
        trusdxRxWorkerThread = Thread({
            while (trusdxRxWorkerRunning) {
                val frame = try {
                    trusdxRxFrameQueue.pollFirst(100L, TimeUnit.MILLISECONDS)
                } catch (_: InterruptedException) {
                    null
                } ?: continue

                val activeEngine = engine ?: continue
                val pcm = ShortArray(frame.size)
                var i = 0
                while (i < frame.size) {
                    // TruSDX RX audio arrives as unsigned 8-bit PCM on the serial stream.
                    val sampleU8 = frame[i].toInt() and 0xFF
                    pcm[i] = ((sampleU8 - 128) shl 8).toShort()
                    i++
                }

                val ok = activeEngine.submitAudioRaw(
                    samples = pcm,
                    numSamples = pcm.size,
                    inputSampleRateHz = TRUSDX_RX_SAMPLE_RATE_HZ,
                    timestampNs = System.nanoTime()
                )
                if (!ok) {
                    trusdxRxSubmitDrops += 1
                    if (trusdxRxSubmitDrops <= 10L) {
                        Log.w(TAG, "TruSDX submit drop #$trusdxRxSubmitDrops chunk=${pcm.size}")
                    }
                    if (isTruSdxDiagnosticsEnabled()) {
                        Log.w(TAG, "TruSDX audio submit failed for chunk size=${pcm.size}")
                    }
                }
            }
        }, "TruSdxRxWorker")
        trusdxRxWorkerThread?.isDaemon = true
        trusdxRxWorkerThread?.start()
    }

    private fun stopTruSdxRxWorker() {
        trusdxRxWorkerRunning = false
        val thread = trusdxRxWorkerThread
        trusdxRxWorkerThread = null
        thread?.interrupt()
        if (thread != null && thread.isAlive) {
            try {
                thread.join(300)
            } catch (_: InterruptedException) {
            }
        }
    }

    private fun scheduleTruSdxRxWatchdog() {
        val token = ++trusdxWatchdogToken
        mainHandler.postDelayed(object : Runnable {
            override fun run() {
                if (token != trusdxWatchdogToken) return
                if (rigControlMode != "trusdx_serial") return
                if (engine == null) return

                val now = System.nanoTime()
                val last = trusdxLastRxAudioNs
                val stalled = last == 0L || (now - last) > TRUSDX_RX_STALL_REARM_NS
                val rearmDue = trusdxLastRxRearmNs == 0L || (now - trusdxLastRxRearmNs) > TRUSDX_RX_REARM_COOLDOWN_NS
                if (stalled && trusdxConnected && rearmDue) {
                    val ok = trusdxSerialSession?.ensureRxStreaming() == true
                    trusdxLastRxRearmNs = now
                    Log.i(TAG, "TruSDX RX watchdog rearm: ok=$ok lastRxNs=$last")
                }

                mainHandler.postDelayed(this, TRUSDX_RX_WATCHDOG_INTERVAL_MS)
            }
        }, TRUSDX_RX_WATCHDOG_INTERVAL_MS)
    }

    private fun handleTruSdxTxAudio(samplesPcm16: ShortArray, sampleRateHz: Int) {
        if (rigControlMode != "trusdx_serial") return
        if (samplesPcm16.isEmpty()) return
        val serialTxActive = trusdxSerialSession?.isTxActive() == true
        var sumSquares = 0.0
        for (s in samplesPcm16) {
            val v = s.toDouble()
            sumSquares += v * v
        }
        val inRms = kotlin.math.sqrt(sumSquares / samplesPcm16.size)
        val txSamples = if (sampleRateHz == TRUSDX_TX_SAMPLE_RATE_HZ) {
            samplesPcm16
        } else {
            resamplePcmLinear(samplesPcm16, sampleRateHz, TRUSDX_TX_SAMPLE_RATE_HZ)
        }
        if (txSamples.isEmpty()) return

        val ok = trusdxSerialSession?.sendTxAudio(txSamples) == true
        if (!ok) {
            trusdxTxDrops += 1
            return
        }

        if (!serialTxActive) {
            return
        }

        trusdxTxFrames += 1
        trusdxTxSamples += txSamples.size.toLong()
        if (inRms < 1.0) {
            trusdxTxSilentFrames += 1
        }
        if (isTruSdxDiagnosticsEnabled() && (trusdxTxFrames % 200L == 0L)) {
            Log.i(TAG, "TruSDX TX frame #$trusdxTxFrames inRate=$sampleRateHz outSamples=${txSamples.size} inRms=${"%.1f".format(Locale.US, inRms)} silentFrames=$trusdxTxSilentFrames drops=$trusdxTxDrops")
        }
    }

    private fun handleUvProTxAudio(samplesPcm16: ShortArray, sampleRateHz: Int) {
        if (rigControlMode != "btech_uvpro" || samplesPcm16.isEmpty()) return
        val txSamples = if (sampleRateHz == UvProSession.SAMPLE_RATE_HZ) {
            samplesPcm16
        } else {
            resamplePcmLinear(samplesPcm16, sampleRateHz, UvProSession.SAMPLE_RATE_HZ)
        }
        uvProSession?.sendTxAudio(txSamples)
    }

    private fun resamplePcmLinear(samples: ShortArray, inputRateHz: Int, outputRateHz: Int): ShortArray {
        if (samples.isEmpty()) return ShortArray(0)
        if (inputRateHz <= 0 || outputRateHz <= 0) return samples
        if (inputRateHz == outputRateHz) return samples

        val ratio = outputRateHz.toDouble() / inputRateHz.toDouble()
        val outSize = kotlin.math.max(1, kotlin.math.round(samples.size * ratio).toInt())
        val out = ShortArray(outSize)
        val step = inputRateHz.toDouble() / outputRateHz.toDouble()
        var pos = 0.0
        var i = 0
        while (i < outSize) {
            val idx = pos.toInt().coerceIn(0, samples.size - 1)
            val next = (idx + 1).coerceAtMost(samples.size - 1)
            val frac = pos - idx.toDouble()
            val a = samples[idx].toInt()
            val b = samples[next].toInt()
            val value = (a + ((b - a) * frac)).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            out[i] = value.toShort()
            pos += step
            i++
        }
        return out
    }

    private fun broadcastProcessTxQueue() {
        val intent = Intent(MainActivity.ACTION_PROCESS_TX_QUEUE)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun isScoActive(deviceId: Int): Boolean {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val commDevice = audioManager.communicationDevice
            if (commDevice != null && commDevice.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                if (deviceId < 0 || commDevice.id == deviceId) {
                    return true
                }
            }
        }
        return audioManager.isBluetoothScoOn
    }

    private fun isScoInputDevice(deviceId: Int): Boolean {
        return findInputDevice(deviceId)?.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
    }

    private fun buildAudioHelper(engine: JS8Engine, deviceId: Int): JS8AudioHelper {
        val overrideSource = if (isScoInputDevice(deviceId)) {
            scoSourceCandidates[scoSourceIndex]
        } else {
            -1
        }
        return JS8AudioHelper(engine, 12000, deviceId, applicationContext, overrideSource,
            PreferenceManager.getDefaultSharedPreferences(this).getString("js8cfg_input_channel", "Mono") ?: "Mono")
    }

    private fun applyInputRouting(inputDeviceId: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val inputDevice = findInputDevice(inputDeviceId)
        if (inputDevice?.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
            enableScoRouting(inputDevice)
        } else {
            disableScoRouting()
        }
    }

    private fun findInputDevice(deviceId: Int): AudioDeviceInfo? {
        if (deviceId < 0 || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        return audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .firstOrNull { it.id == deviceId }
    }

    private fun enableScoRouting(device: AudioDeviceInfo) {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val priorMode = audioManager.mode

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "BLUETOOTH_CONNECT permission not granted; SCO routing unavailable")
                broadcastError("Bluetooth permission required for SCO routing")
                return
            }
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            if (!scoRoutingActive) {
                previousAudioMode = priorMode
            }
            val commDevice = findCommunicationDevice(device)
            if (commDevice == null) {
                Log.w(TAG, "No available communication device matches SCO input; falling back to SCO start")
                if (!startBluetoothScoLegacy(audioManager, priorMode)) {
                    broadcastError("Failed to start Bluetooth SCO routing")
                }
                return
            }
            val ok = audioManager.setCommunicationDevice(commDevice)
            if (ok) {
                scoRoutingActive = true
                Log.i(TAG, "SCO routing enabled via communication device: ${commDevice.productName} (ID: ${commDevice.id})")
                if (!audioManager.isBluetoothScoOn) {
                    if (startBluetoothScoLegacy(audioManager, priorMode)) {
                        Log.i(TAG, "SCO audio started after communication device routing")
                    } else {
                        Log.w(TAG, "Failed to start SCO audio after communication device routing")
                    }
                }
            } else {
                audioManager.mode = priorMode
                Log.w(TAG, "Failed to set communication device for SCO; falling back to SCO start")
                if (!startBluetoothScoLegacy(audioManager, priorMode)) {
                    broadcastError("Failed to start Bluetooth SCO routing")
                }
            }
        } else {
            if (!scoRoutingActive) {
                previousAudioMode = priorMode
            }
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            try {
                audioManager.startBluetoothSco()
                audioManager.isBluetoothScoOn = true
                scoRoutingActive = true
                Log.i(TAG, "SCO routing enabled via startBluetoothSco")
            } catch (e: SecurityException) {
                audioManager.mode = priorMode
                Log.w(TAG, "Failed to start Bluetooth SCO", e)
                broadcastError("Bluetooth permission required for SCO routing")
            }
        }
    }

    private fun startBluetoothScoLegacy(audioManager: AudioManager, priorMode: Int): Boolean {
        return try {
            audioManager.startBluetoothSco()
            audioManager.isBluetoothScoOn = true
            scoRoutingActive = true
            Log.i(TAG, "SCO routing enabled via legacy startBluetoothSco")
            true
        } catch (e: SecurityException) {
            audioManager.mode = priorMode
            Log.w(TAG, "Failed to start Bluetooth SCO (legacy)", e)
            false
        }
    }

    private fun findCommunicationDevice(inputDevice: AudioDeviceInfo): AudioDeviceInfo? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val candidates = audioManager.availableCommunicationDevices
        if (candidates.isEmpty()) return null
        candidates.firstOrNull { it.id == inputDevice.id }?.let { return it }
        val inputName = inputDevice.productName?.toString()
        candidates.firstOrNull {
            it.type == inputDevice.type &&
                (inputName == null || it.productName?.toString() == inputName)
        }?.let { return it }
        return candidates.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
    }

    private fun disableScoRouting() {
        if (!scoRoutingActive) return
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                audioManager.clearCommunicationDevice()
            } catch (e: SecurityException) {
                Log.w(TAG, "Failed to clear communication device", e)
            }
            if (audioManager.isBluetoothScoOn) {
                try {
                    audioManager.stopBluetoothSco()
                    audioManager.isBluetoothScoOn = false
                } catch (e: SecurityException) {
                    Log.w(TAG, "Failed to stop Bluetooth SCO", e)
                }
            }
        } else {
            try {
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
            } catch (e: SecurityException) {
                Log.w(TAG, "Failed to stop Bluetooth SCO", e)
            }
        }
        audioManager.mode = previousAudioMode
        scoRoutingActive = false
        Log.i(TAG, "SCO routing disabled")
    }

    private fun saveAutoDrift() = PreferenceManager.getDefaultSharedPreferences(this).edit()
        .putBoolean(KEY_AUTO_DRIFT_ON, autoDriftOn).putInt(KEY_AUTO_DRIFT_STOP_AFTER, autoDriftStopAfter)
        .putInt(KEY_AUTO_DRIFT_LEFT, autoDriftDecodesLeft).apply()

    /**
     * Service start: a running HB / CQ timer carries on with its countdown; if its time passed
     * while the app was closed, it counts a full interval from now.
     */
    private fun resumeTimers() {
        val now = System.currentTimeMillis() + (engine?.timeDriftMs() ?: 0L)
        val hbNext = HeartbeatTimer.nextMs
        if (HeartbeatTimer.on && hbNext > now) {
            nextHeartbeatTime = hbNext
            heartbeatHandler.removeCallbacks(heartbeatRunnable)
            heartbeatHandler.postDelayed(heartbeatRunnable, hbNext - now)
            Log.i(TAG, "HB timer resumed, next at ${java.util.Date(hbNext)}")
        } else scheduleHeartbeat(true)
        val cqNext = CqTimer.nextMs
        if (CqTimer.on && cqNext > now) {
            cqHandler.removeCallbacks(cqRunnable)
            cqHandler.postDelayed(cqRunnable, cqNext - now)
            Log.i(TAG, "CQ timer resumed, next at ${java.util.Date(cqNext)}")
        } else scheduleCq(true)
    }

    private fun checkHeartbeat() {
        // This function is now just the runnable target, actual scheduling is handled by postDelayed in scheduleHeartbeat
        sendHeartbeat()
    }

    private fun scheduleHeartbeat(first: Boolean) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val intervalStr = prefs.getString(PREF_HEARTBEAT_INTERVAL, "30") ?: "30"
        // Settings > HB (PC SubModeHB, default off) and the HB button's timer (PC m_hb_loop):
        // no timed heartbeats unless both are on
        val intervalMinutes = if (prefs.getBoolean(PREF_HB, false) && HeartbeatTimer.on) intervalStr.toIntOrNull() ?: 0 else 0

        if (intervalMinutes <= 0) {
            heartbeatHandler.removeCallbacks(heartbeatRunnable)
            HeartbeatTimer.nextMs = 0L
            Log.i(TAG, "Heartbeat disabled")
            return
        }

        // Drifted timeline, so slots match the engine's cycle boundaries.
        val now = System.currentTimeMillis() + (engine?.timeDriftMs() ?: 0L)
        val frameDuration = framePeriodMs(getPreferredTxSubmode())

        // PC TxLoop::onTxLoopPeriodChangeStart: the full interval from now, also when the timer is
        // started again or the interval / speed changes, no random extra cycle (Rob, 2026-10-22;
        // JS8Android sent the first one after 10 s and skipped a cycle 1 time in 4)
        val delay = intervalMinutes * 60 * 1000L

        var targetTime = now + delay
        
        // Align to next frame boundary (UTC epoch)
        // We want the trigger to happen slightly BEFORE the boundary (e.g. 2000ms)
        // so the engine has time to pick it up for the immediate slot.
        val remainder = targetTime % frameDuration
        val timeToBoundary = frameDuration - remainder
        targetTime += timeToBoundary
        
        // Schedule 2 seconds before the slot starts
        targetTime -= 2000L
        
        // Ensure strictly future
        if (targetTime <= now) {
            targetTime += frameDuration
        }
        
        nextHeartbeatTime = targetTime
        HeartbeatTimer.nextMs = targetTime
        
        Log.i(TAG, "Heartbeat scheduled for ${java.util.Date(nextHeartbeatTime)} (interval=$intervalMinutes min, frame=${frameDuration/1000}s)")
        
        heartbeatHandler.removeCallbacks(heartbeatRunnable)
        val waitMs = nextHeartbeatTime - now
        heartbeatHandler.postDelayed(heartbeatRunnable, waitMs)
    }

    private val cqHandler = Handler(Looper.getMainLooper())
    private val cqRunnable = Runnable {
        // m_cq_loop triggerTxNow -> sendCQ(true): the main screen puts it in the Typing Box and sends
        if (CqTimer.on) {
            val sent = CqTimer.fire?.invoke() ?: false
            if (!sent && !isListenOnly()) {
                val prefs = PreferenceManager.getDefaultSharedPreferences(this)
                val cq = prefs.getString("js8cfg_cq_message", "CQ CQ CQ <MYGRID4>").orEmpty().ifBlank { "CQ CQ CQ <MYGRID4>" }
                broadcastQueueTx(com.js8call.example.js8ui.Js8Macros.expand(cq, prefs), null)
            }
        }
        scheduleCq(false)
    }

    /** The CQ timer (PC TxLoop): first CQ at the next slot, then every CQ interval, on slot boundaries. */
    private fun scheduleCq(first: Boolean) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val minutes = prefs.all[CqTimer.KEY_INTERVAL]?.toString()?.toIntOrNull() ?: 0
        cqHandler.removeCallbacks(cqRunnable)
        if (!CqTimer.on || minutes <= 0) {
            CqTimer.nextMs = 0L
            return
        }
        val now = System.currentTimeMillis() + (engine?.timeDriftMs() ?: 0L)
        val frame = framePeriodMs(getPreferredTxSubmode())
        // PC TxLoop: the full interval from now, also when the timer is started again (Rob, 2026-10-22)
        var target = now + minutes * 60_000L
        target += frame - target % frame
        target -= 2000L
        if (target <= now) target += frame
        CqTimer.nextMs = target
        Log.i(TAG, "CQ scheduled for ${java.util.Date(target)} (interval=$minutes min)")
        cqHandler.postDelayed(cqRunnable, target - now)
    }

    private fun framePeriodMs(submode: Int): Long {
        return when (submode) {
            SUBMODE_SLOW -> 30000L
            SUBMODE_NORMAL -> 15000L
            SUBMODE_FAST -> 10000L
            SUBMODE_TURBO -> 6000L
            SUBMODE_ULTRA -> 4000L
            else -> 15000L
        }
    }

    /**
     * True for messages that belong in the heartbeat sub-band: heartbeats
     * themselves and heartbeat SNR acknowledgements.
     */
    private fun isHeartbeatTraffic(text: String): Boolean {
        if (TxMessageClassifier.isHeartbeatMessage(text)) return true
        return heartbeatAckTxRegex.containsMatchIn(text)
    }

    /** PC sendHeartbeat: my offset at or below 1000 Hz, else findFreeFreqOffset(500, 1000, 50). */
    private fun heartbeatOffsetHz(): Float {
        val mine = PreferenceManager.getDefaultSharedPreferences(this)
            .getString("js8cfg_tx_offset", null)?.toFloatOrNull() ?: 1500f
        if (mine <= HB_SUBBAND_HIGH_HZ) return mine
        val slots = (HB_SUBBAND_HIGH_HZ - HB_SUBBAND_LOW_HZ) / HB_SLOT_HZ
        repeat(slots) {
            val f = HB_SUBBAND_LOW_HZ + HB_SLOT_HZ * (0 until slots).random()
            if (isOffsetFree(f, mine)) return f.toFloat()
        }
        repeat(slots) {
            val f = (HB_SUBBAND_LOW_HZ until HB_SUBBAND_HIGH_HZ).random()
            if (isOffsetFree(f, mine)) return f.toFloat()
        }
        return HB_SUBBAND_LOW_HZ.toFloat()
    }

    /** PC isFreqOffsetFree: nothing heard within the slot in the last 30 seconds. */
    private fun isOffsetFree(f: Int, mine: Float): Boolean {
        if (f == mine.toInt()) return true
        val now = System.currentTimeMillis()
        return synchronized(recentOffsets) {
            recentOffsets.entries.removeIf { now - it.value >= 30_000 }
            recentOffsets.keys.none { Math.abs(it - f) < HB_SLOT_HZ }
        }
    }

    private fun sendHeartbeat() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        // PC enqueueMessage: waits for what is on the air, and for TX off
        // A heartbeat waits behind queued messages (PC priority 11, replies 100), and for an empty Typing Box
        // (PC processTxQueue, mainwindow.cpp 6628-6631)
        if (isTransmitActive() || messageQueued || !typingBoxEmpty || !prefs.getBoolean("js8cfg_tx_enabled", true)) {
            Log.i(TAG, "Heartbeat waiting for the transmitter")
            mainHandler.postDelayed({ sendHeartbeat() }, 1000L)
            return
        }
        if (isListenOnly()) return
        val callsign = getConfiguredCallsign() ?: return
        val grid = com.js8call.example.api.Js8Api.myGrid(prefs)

        val freq = if (prefs.getBoolean("js8cfg_heartbeat_anywhere", false)) {
            prefs.getString("js8cfg_tx_offset", null)?.toFloatOrNull() ?: 1500f
        } else {
            heartbeatOffsetHz()
        }
        if (refuseWsprBand(freq.toDouble())) return
        
        // Message format: CALL: @HB HEARTBEAT GRID
        // Note: JS8Call desktop constructs it as: CALL: @HB HEARTBEAT GRID
        // My buildTxMessage handles adding the "CALL: " part if we pass "@HB HEARTBEAT GRID" as text and directedCall as ""?
        // Wait, handleTransmitMessage uses:
        // text = payloadText
        // myCall = callsign
        // selectedCall = directed
        
        // Let's use transmitMessage directly
        
        val message = "@HB HEARTBEAT $grid"
        // If I pass selectedCall as empty, buildTxMessage logic:
        // if directedCall is empty, return text.
        // So I need to construct the full line myself?
        // nativeTransmitMessage expects "text" (payload) and "selectedCall".
        // The engine likely frames it.
        // Let's look at desktop:
        // lines.append(QString("%1: HEARTBEAT %2").arg(mycall).arg(mygrid));
        // So "CALL: HEARTBEAT GRID"
        
        // But wait, the standard heartbeat format is often directed to @HB?
        // Desktop varicode.cpp:
        // QRegularExpression heartbeat_re(R"(^\s*(?<callsign>[@](?:ALLCALL|HB)\s+)?(?<type>CQ CQ CQ|CQ DX|CQ QRP|CQ CONTEST|CQ FIELD|CQ FD|CQ CQ|CQ|HB|HEARTBEAT(?!\s+SNR))(?:\s(?<grid>[A-R]{2}[0-9]{2}))?\b)");
        
        // Let's stick to what the JS8Call guide says or what works.
        // Usually: "CALL: @HB HEARTBEAT GRID4"
        
        val payload = "@HB HEARTBEAT ${grid.take(4)}"
        
        Log.i(TAG, "Sending Heartbeat: $payload at ${freq}Hz")

        val activeEngine = engine
        if (activeEngine != null) {
            val submode = getPreferredTxSubmode()
            prepareEngineForTransmit(activeEngine)
             val ok = activeEngine.transmitMessage(
                text = payload,
                myCall = callsign,
                myGrid = grid.take(4), // PC: my_grid().left(4) before buildMessageFrames
                selectedCall = "", // Broadcast-ish
                submode = submode,
                audioFrequencyHz = splitAudioHz(freq.toDouble()),
                txDelaySec = configuredTxDelay(),
                forceIdentify = true, // Force ID to ensure callsign is sent
                forceData = false
            )
            
            if (ok) {
                updateLastTxMessage(payload, "", submode, freq.toDouble())
                broadcastTxState(TX_STATE_QUEUED)
                startTxMonitor()
            } else {
                Log.e(TAG, "Failed to send heartbeat")
            }
        }

        scheduleHeartbeat(false)
    }

    private fun handleTransmitMessage(intent: Intent) {
        // PC rule (Rob, 2026-10-20): sending leaves the heartbeat interval alone (JS8Android set
        // it to Disabled here); only a one-off CQ stops the HB timer (Js8MainFragment)
        val activeEngine = engine
        if (activeEngine == null) {
            broadcastError("Engine not running")
            return
        }

        val text = intent.getStringExtra(EXTRA_TX_TEXT)?.trim().orEmpty()
        if (text.isEmpty()) {
            broadcastError("Empty TX message")
            return
        }
        if (isListenOnly()) return

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val callsign = getConfiguredCallsign()
        if (callsign == null) {
            warnMissingCallsign()
            return
        }
        val grid = com.js8call.example.api.Js8Api.myGrid(prefs)

        val directed = intent.getStringExtra(EXTRA_TX_DIRECTED)?.trim().orEmpty()
        val submode = if (intent.hasExtra(EXTRA_TX_SUBMODE)) {
            intent.getIntExtra(EXTRA_TX_SUBMODE, SUBMODE_NORMAL)
        } else {
            prefs.getInt(PREF_TX_SUBMODE, SUBMODE_NORMAL)
        }
        // Heartbeat traffic belongs in the 500-1000 Hz sub-band, away from QSOs.
        // This covers manual HB sends and auto HB ACKs; the scheduled heartbeat
        // picks its own offset in sendHeartbeat().
        val requestedFrequencyHz = intent.getDoubleExtra(EXTRA_TX_FREQ_HZ, DEFAULT_AUDIO_FREQUENCY_HZ)
        val audioFrequencyHz = if (isHeartbeatTraffic(text) && !prefs.getBoolean("js8cfg_heartbeat_anywhere", false)) {
            heartbeatOffsetHz().toDouble()
        } else {
            requestedFrequencyHz
        }
        if (refuseWsprBand(audioFrequencyHz)) return
        val txDelaySec = intent.getDoubleExtra(EXTRA_TX_DELAY_S, configuredTxDelay())
        val forceIdentify = intent.getBooleanExtra(EXTRA_TX_FORCE_IDENTIFY, false)
        val forceData = intent.getBooleanExtra(EXTRA_TX_FORCE_DATA, false)
        val effectiveForceIdentify = (forceIdentify || callsign.isNotBlank()) && !avoidForcedIdentify()
        val payloadText = text.trim() // sent as typed, like the PC (the CQ button's CQ Message holds the grid)

        // Set transmit mode if configured (before queuing TX)
        val modeSet = setTransmitMode()
        if (!modeSet) {
            Log.w(TAG, "Failed to set transmit mode (rig control might not be connected)")
        }

        Log.i(
            TAG,
            "TX request: text='$payloadText', directed='${directed}', submode=$submode, freq=$audioFrequencyHz, delay=$txDelaySec, identify=$effectiveForceIdentify"
        )

        prepareEngineForTransmit(activeEngine)
        val ok = activeEngine.transmitMessage(
            text = payloadText,
            myCall = callsign,
            myGrid = grid.take(4), // PC: my_grid().left(4) before buildMessageFrames
            selectedCall = directed,
            submode = submode,
            audioFrequencyHz = splitAudioHz(audioFrequencyHz),
            txDelaySec = txDelaySec,
            forceIdentify = effectiveForceIdentify,
            forceData = forceData
        )

        if (ok) {
            Log.i(TAG, "TX request accepted")
            updateLastTxMessage(payloadText, directed, submode, audioFrequencyHz)
            broadcastTxState(TX_STATE_QUEUED)
            startTxMonitor()
        } else {
            Log.e(TAG, "TX request rejected")
            broadcastError("Failed to start transmit")
            broadcastTxState(TX_STATE_FAILED)
        }
    }

    private fun handleTune(on: Boolean, audioFrequencyHz: Double) {
        val activeEngine = engine
        if (!on) {
            activeEngine?.stopTransmit()
            return
        }
        if (refuseWsprBand(audioFrequencyHz)) {
            return
        }
        if (isListenOnly() || activeEngine == null || isTransmitActive()) {
            mainHandler.post { endTuning() } // no tone: back to the Tx power
            if (activeEngine == null) broadcastError("Start receiving (RX) before tuning")
            else if (!isListenOnly()) broadcastError("Already transmitting")
            return
        }
        setTransmitMode()
        prepareEngineForTransmit(activeEngine)
        if (activeEngine.startTune(splitAudioHz(audioFrequencyHz), getPreferredTxSubmode())) {
            Log.i(TAG, "Tune started at $audioFrequencyHz Hz")
            broadcastTxState(TX_STATE_QUEUED)
            startTxMonitor()
        } else {
            mainHandler.post { endTuning() }
            broadcastError("Failed to start tune")
        }
    }

    private fun setTransmitMode(): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (rigControlMode == "trusdx_serial") {
            Log.d(TAG, "Setting transmit mode for TruSDX: USB")
            return sendRigModeCommand(RIG_MODE_USB, 0)
        }
        if (rigControlMode == "qmx_serial") {
            Log.d(TAG, "Setting transmit mode for QMX: Digi")
            return qmxCatSession?.setDigiMode() == true
        }
        if (rigControlMode == "btech_uvpro") {
            // FM only; there is no mode to set.
            return true
        }
        val txMode = prefs.getString(PREF_TRANSMIT_MODE, "none") ?: "none"
        
        Log.d(TAG, "Setting transmit mode: $txMode")
        
        return when (txMode) {
            "none" -> true
            "usb" -> sendRigModeCommand(RIG_MODE_USB, 0)  // 0 = rig default passband
            "usb_data" -> sendRigModeCommand(RIG_MODE_PKTUSB, 0)
            else -> true
        }
    }
    
    private fun sendRigModeCommand(mode: String, passband: Int = 0): Boolean {
        return when (rigControlMode) {
            "hamlib_usb" -> hamlibRigControl?.setMode(mode, passband) == true
            "trusdx_serial" -> trusdxSerialSession?.setUsbMode() == true
            "qmx_serial" -> qmxCatSession?.setDigiMode() == true
            "rts_ptt" -> false
            "network" -> rigCtlClient?.setMode(mode) == true
            else -> false
        }
    }

    /**
     * The part (frame) of the message that just started going out, as text (PC
     * prepareNextMessageFrame: each frame shown in the Message Window as it is sent).
     */
    private fun broadcastTxFrame(activeEngine: JS8Engine) {
        val texts = activeEngine.txFrameTexts()
        val index = activeEngine.txFramesStarted() - 1
        if (texts.isEmpty() || index !in texts.indices) return
        val intent = Intent(ACTION_TX_FRAME).apply {
            putExtra(EXTRA_TX_FRAME_TEXT, texts[index])
            putExtra(EXTRA_TX_FRAME_INDEX, index)
            // Only a first part can be a header (CQ, directed); data parts join without a space
            putExtra(EXTRA_TX_FRAME_IS_DATA, index > 0 || activeEngine.txFirstFrameIsData())
            putExtra(EXTRA_TX_FRAME_COUNT, texts.size)
            // PC setCharsSent(m_totalTxMessage.length()): text of the parts sent so far, this one included,
            // without the "CALLSIGN: " the first part starts with, which is not in the Typing Box (Rob,
            // 2026-10-21: the strike-out ran 8 characters ahead and crossed out short messages at once)
            val added = Regex("^[A-Z0-9/]+: ").find(texts[0])?.value?.length ?: 0
            putExtra(EXTRA_TX_FRAME_CHARS_SENT, (texts.take(index + 1).sumOf { it.length } - added).coerceAtLeast(0))
            // The whole message as sent, without the "CALLSIGN: ": shown in the Typing Box for messages
            // that did not come from it (heartbeats, replies; Rob, 2026-10-21: "all messages do it")
            putExtra(EXTRA_TX_FRAME_FULL_TEXT, texts.joinToString("").drop(added).trim())
            putExtra(EXTRA_TX_OFFSET_HZ, lastTxFrequencyHz)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun broadcastTxState(state: String) {
        val intent = Intent(ACTION_TX_STATE).apply {
            putExtra(EXTRA_TX_STATE, state)
            // PC m_txFrameCount for the Send button's "Ready (30s)"
            if (state == TX_STATE_QUEUED) putExtra(EXTRA_TX_FRAME_COUNT, engine?.txFrameTexts()?.size ?: 0)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    /** PC prepareNextMessageFrame, typeahead: the Typing Box changed past what was sent. Runs on txHandler. */
    private fun replaceUnsentText(text: String, framesStarted: Int) {
        val activeEngine = engine ?: return
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val ok = activeEngine.replaceUnsentText(text.trimEnd().uppercase(), prefs.getString("callsign", "").orEmpty().trim().uppercase(),
            Js8Api.myGrid(prefs).take(4), Js8Api.State.selectedCall.orEmpty(), lastTxSubmode, framesStarted)
        Log.i(TAG, "Typeahead: \"$text\" after part $framesStarted: $ok")
    }

    private fun persistIncomingMessage(
        from: String,
        text: String,
        snr: Int,
        freq: Float,
        relayPath: String?,
        conversationId: String = from
    ) {
        messageScope.launch {
            try {
                messageRepository.insertIncomingMessage(conversationId, from, text, snr, freq, relayPath, currentDialHz.takeIf { it > 0L })
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to persist incoming MSG from $from", t)
                return@launch
            }

            mainHandler.post {
                tryNotify("inbox")
                val intent = Intent(ACTION_MESSAGE_RECEIVED).apply {
                    putExtra(EXTRA_MESSAGE_FROM, from)
                    putExtra(EXTRA_MESSAGE_TEXT, text)
                    putExtra(EXTRA_MESSAGE_SNR, snr)
                    putExtra(EXTRA_MESSAGE_FREQ, freq)
                    putExtra(EXTRA_MESSAGE_CONVERSATION_ID, conversationId)
                    relayPath?.let { putExtra(EXTRA_MESSAGE_RELAY_PATH, it) }
                }
                LocalBroadcastManager.getInstance(this@JS8EngineService).sendBroadcast(intent)
                showMessageNotification(from, text)
            }
        }
    }
    
    /**
     * Broadcast a request to queue a TX message.
     * The UI layer (TransmitViewModel) will handle adding it to the TX queue.
     */
    private fun broadcastQueueTx(text: String, directed: String?, priority: Int = 0) {
        val intent = Intent(ACTION_QUEUE_TX).apply {
            putExtra(EXTRA_QUEUE_TX_TEXT, text)
            directed?.let { putExtra(EXTRA_QUEUE_TX_DIRECTED, it) }
            putExtra(EXTRA_QUEUE_TX_PRIORITY, priority)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        Log.d(TAG, "Broadcast queue TX: text='$text' directed=$directed priority=$priority")
    }

    private fun numberPref(key: String, default: Double): Double =
        PreferenceManager.getDefaultSharedPreferences(this).all[key]?.toString()?.toDoubleOrNull() ?: default

    /** Station > Radio > Tx delay (PC m_TxDelay). */
    private fun configuredTxDelay() = numberPref("js8cfg_tx_delay", 0.2)

    /** PC: PTT goes on Tx delay before the period start; the audio follows the speed's start delay after it. */
    private fun pttLeadMs(): Int = (configuredTxDelay() * 1000.0).toInt() + when (getPreferredTxSubmode()) {
        SUBMODE_NORMAL, SUBMODE_SLOW -> 500
        SUBMODE_FAST -> 200
        else -> 100
    }

    /** PC processHeartbeatRateLimit: a second heartbeat within 55 minutes puts the station on the Never acknowledge list. */
    private fun processHeartbeatRateLimit(call: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (!prefs.getBoolean("js8cfg_hb_rate_limit", false)) return
        val times = prefs.getString(PREF_HB_TIMES, "").orEmpty().split(',').mapNotNull {
            val parts = it.split('=')
            parts.getOrNull(1)?.toLongOrNull()?.let { ms -> parts[0] to ms }
        }.toMap().toMutableMap()
        val now = System.currentTimeMillis()
        val last = times[call]
        val edit = prefs.edit()
        if (last != null && now - last < 55 * 60 * 1000L) {
            times.remove(call)
            val list = prefs.getString("js8cfg_hb_blacklist", "").orEmpty().trim().trimEnd(',')
            edit.putString("js8cfg_hb_blacklist", if (list.isEmpty()) call else "$list,$call")
        } else {
            times[call] = now
        }
        edit.putString(PREF_HB_TIMES, times.entries.joinToString(",") { "${it.key}=${it.value}" }).apply()
    }

    // ---- Idle timeout (PC tx_watchdog) ----------------------------------------------------------

    private var idleWasAuto = false
    private var idleWasHb = false
    private var idleWasCq = false

    private val idleRunnable = object : Runnable {
        override fun run() {
            checkIdleTimeout()
            heartbeatHandler.postDelayed(this, 60_000L)
        }
    }

    /** PC processActivity runs processBufferedActivity on the clock, not only when a frame arrives. */
    private val msgBufferRunnable = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            getConfiguredCallsign()?.let { cleanupMsgBuffers(now, it) }
            cleanupRelayBuffers(now)
            cleanupSpotCmdBuffers(now)
            cleanupDirectedBuffers(now)
            processSpots()
            heartbeatHandler.postDelayed(this, 1_000L)
        }
    }

    /**
     * PC read_settings: an Idle timeout of 1 to 4 minutes is raised to 5 when the settings load at start
     * (cpp 2410-2412); a value typed after that is used as it is until the next start (cpp 3351).
     */
    private fun raiseIdleTimeout() {
        val key = "js8cfg_tx_watchdog"
        val p = PreferenceManager.getDefaultSharedPreferences(this)
        val v = p.all[key] ?: return
        if (v.toString().toDoubleOrNull()?.toLong() !in 1L..4L) return
        p.edit().apply {
            when (v) {
                is Int -> putInt(key, 5)
                is Long -> putLong(key, 5L)
                is Float -> putFloat(key, 5f)
                else -> putString(key, "5")
            }
        }.apply()
    }

    private fun checkIdleTimeout() {
        val minutes = numberPref("js8cfg_tx_watchdog", 60.0).toLong()
        if (minutes <= 0 || idleTimedOutMinutes != 0L) return
        if (System.currentTimeMillis() - lastUserMs < minutes * 60_000L) return
        Log.i(TAG, "Idle timeout after $minutes minutes")
        idleTimedOutMinutes = minutes
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        idleWasAuto = prefs.getBoolean(PREF_AUTOREPLY_ENABLED, false)
        idleWasHb = HeartbeatTimer.on
        idleWasCq = CqTimer.on
        txHandler.post { engine?.stopTransmit() }
        prefs.edit().putBoolean(PREF_AUTOREPLY_ENABLED, false).apply()
        setTxTimers(hb = false, cq = false)
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_IDLE_TIMEOUT))
    }

    /** The Idle Timeout box's OK puts AUTO, HB and CQ back (PC msgBox finished). */
    private fun finishIdleTimeout() {
        if (idleTimedOutMinutes == 0L) return
        idleTimedOutMinutes = 0L
        lastUserMs = System.currentTimeMillis()
        PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean(PREF_AUTOREPLY_ENABLED, idleWasAuto).apply()
        setTxTimers(idleWasHb, idleWasCq)
    }

    private fun setTxTimers(hb: Boolean, cq: Boolean) {
        HeartbeatTimer.on = hb
        CqTimer.on = cq
        scheduleHeartbeat(true)
        scheduleCq(true)
    }

    /** PC confirmThenEnqueueMessage: with "Ask for confirmation ..." ticked, Yes / No first; no answer in 90 s = No. */
    private fun queueAutoReply(text: String, directed: String?, priority: Int = 0, onAccepted: (() -> Unit)? = null) {
        // PC processCommandActivity: no reply with text in the Typing Box or a message to me still arriving,
        // and none during an idle timeout (processCommandActivity.cpp 313-318). SWL never transmits.
        if (!typingBoxEmpty || hasOpenBufferToMe() || idleTimedOutMinutes != 0L || isListenOnly()) {
            Log.i(TAG, "Reply '$text' not queued: Typing Box empty $typingBoxEmpty, buffer to me ${hasOpenBufferToMe()}, " +
                "idle timeout $idleTimedOutMinutes, SWL ${isListenOnly()}")
            return
        }
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (!prefs.getBoolean("js8cfg_autoreply_confirmation", true)) {
            broadcastQueueTx(text, directed, priority)
            onAccepted?.invoke()
            return
        }
        mainHandler.post {
            val c = AutoReplyConfirm(nextConfirmId++, text, directed, priority, onAccepted)
            autoReplyConfirms.add(c)
            mainHandler.postDelayed({ finishAutoReplyConfirm(c.id, false, false) }, AUTOREPLY_CONFIRM_SECONDS * 1000L)
            LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_AUTOREPLY_CONFIRM))
            Js8Api.sendNetworkMessage("STATION.AUTOREPLY_CONFIRM_REQUEST", text, mapOf("_ID" to -1, "CONFIRM_ID" to c.id,
                "PRIORITY" to priority, "OFFSET" to Js8Api.offsetHz(), "TIMEOUT" to AUTOREPLY_CONFIRM_SECONDS))
        }
    }

    /** The box's Yes / No, the 90 seconds, or the API's STATION.AUTOREPLY_CONFIRM_RESPONSE. */
    private fun finishAutoReplyConfirm(id: Int, accepted: Boolean, fromApi: Boolean) {
        val c = autoReplyConfirms.firstOrNull { it.id == id } ?: return
        autoReplyConfirms.remove(c)
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_AUTOREPLY_CONFIRM))
        if (accepted) {
            broadcastQueueTx(c.text, c.directed, c.priority)
            c.onAccepted?.invoke()
        }
        when {
            fromApi -> Js8Api.sendNetworkMessage("STATION.AUTOREPLY_CONFIRMED", "", mapOf("_ID" to -1, "CONFIRM_ID" to id,
                "ACCEPTED" to accepted, "MESSAGE" to c.text))
            accepted -> Js8Api.sendNetworkMessage("STATION.AUTOREPLY_CONFIRM_ACCEPTED", c.text, mapOf("_ID" to -1, "CONFIRM_ID" to id,
                "PRIORITY" to c.priority, "OFFSET" to Js8Api.offsetHz()))
            else -> Js8Api.sendNetworkMessage("STATION.AUTOREPLY_CONFIRM_EXPIRED", "", mapOf("_ID" to -1, "CONFIRM_ID" to id,
                "MESSAGE" to c.text))
        }
    }

    private fun showMessageNotification(from: String, text: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        
        // Create message notification channel if it doesn't exist
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_MESSAGES,
                getString(R.string.notification_message_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_message_channel_desc)
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        // Create intent to open the app
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_messages", true)
            putExtra("callsign", from)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, from.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID_MESSAGES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.notification_new_message_title, from))
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID_MESSAGE_BASE + from.hashCode(), notification)
    }

    private fun startTxMonitor() {
        txMonitorHandler.removeCallbacks(txMonitorRunnable)
        txMonitorActive = true
        txMonitorWasAudioActive = false
        synchronized(pttStateLock) {
            txPttGeneration++
            rigPttDesired = rigPttAsserted
            rigPttDesiredGeneration = txPttGeneration
            rigPttFailureCount = 0
            rigPttCompletion = null
            pttShuttingDown = false
            txPttFailed = false
        }
        if (rigControlMode == "trusdx_serial") {
            trusdxTxIntentActive = true
        }
        txMonitorHandler.post(txMonitorRunnable)
    }

    private fun stopTxMonitor() {
        if (!txMonitorActive) return
        txMonitorActive = false
        trusdxTxIntentActive = false
        txMonitorHandler.removeCallbacks(txMonitorRunnable)
    }

    private fun isRigPttReady(generation: Int): Boolean {
        return synchronized(pttStateLock) {
            generation == txPttGeneration &&
                rigPttAsserted &&
                rigPttDesired &&
                !rigPttCommandPending
        }
    }

    private fun isCurrentTxGeneration(generation: Int): Boolean {
        return synchronized(pttStateLock) { generation == txPttGeneration }
    }

    private fun requestRigPtt(
        enabled: Boolean,
        generation: Int,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        if (!enabled) engine?.setTransmitReady(false)

        var completion: ((Boolean) -> Unit)? = null
        var scheduleCommand = false
        var openTransmitGate = false
        synchronized(pttStateLock) {
            if (generation != txPttGeneration || (txPttFailed && enabled)) return
            if (rigPttDesired != enabled || rigPttDesiredGeneration != generation) {
                rigPttDesired = enabled
                rigPttDesiredGeneration = generation
                rigPttFailureCount = 0
            }
            if (onComplete != null) rigPttCompletion = onComplete

            if (!rigPttCommandPending) {
                if (rigPttAsserted == rigPttDesired) {
                    completion = rigPttCompletion
                    rigPttCompletion = null
                    openTransmitGate = enabled && generation == txPttGeneration
                } else {
                    rigPttCommandPending = true
                    scheduleCommand = true
                }
            }
        }

        if (openTransmitGate) engine?.setTransmitReady(true)
        completion?.invoke(true)
        if (scheduleCommand) txHandler.post { runRigPttCommand() }
    }

    private fun runRigPttCommand() {
        val target: Boolean
        val generation: Int
        synchronized(pttStateLock) {
            target = rigPttDesired
            generation = rigPttDesiredGeneration
        }

        val success = if (target) applySplit() && setRigPtt(true) else setRigPtt(false)
        if (!target) clearSplit(rigControlMode, currentDialHz, rigCtlClient, hamlibRigControl, trusdxSerialSession, qmxCatSession)
        var scheduleNext = false
        var completion: ((Boolean) -> Unit)? = null
        var failEnable = false
        var transmitReady: Boolean? = null
        var completionResult = success
        synchronized(pttStateLock) {
            rigPttCommandPending = false
            if (success) {
                rigPttAsserted = target
                rigPttFailureCount = 0
                transmitReady = if (target) {
                    target == rigPttDesired && generation == txPttGeneration
                } else {
                    false
                }
            }

            if (pttShuttingDown) {
                transmitReady = false
                rigPttCompletion = null
                return@synchronized
            }

            val targetStillDesired = target == rigPttDesired &&
                generation == rigPttDesiredGeneration
            if (!success && targetStillDesired) {
                rigPttFailureCount++
                if (rigPttFailureCount <= PTT_COMMAND_RETRIES) {
                    rigPttCommandPending = true
                    scheduleNext = true
                } else {
                    completion = rigPttCompletion
                    rigPttCompletion = null
                    failEnable = target
                    rigPttDesired = rigPttAsserted
                }
            } else if (rigPttAsserted != rigPttDesired) {
                rigPttCommandPending = true
                scheduleNext = true
            } else if (rigPttDesiredGeneration == txPttGeneration) {
                completion = rigPttCompletion
                rigPttCompletion = null
                completionResult = true
            }
        }

        transmitReady?.let { engine?.setTransmitReady(it) }

        mainHandler.post {
            if (success) {
                Log.i(TAG, "PTT ${if (target) "enabled" else "released"} generation=$generation")
            } else {
                Log.w(TAG, "PTT ${if (target) "enable" else "release"} failed generation=$generation")
            }
            completion?.invoke(completionResult)
            if (failEnable) {
                failTransmitForPtt(generation, "Failed to enable PTT")
            }
        }
        if (scheduleNext) txHandler.post { runRigPttCommand() }
    }

    private fun failTransmitForPtt(generation: Int, message: String) {
        synchronized(pttStateLock) {
            if (generation != txPttGeneration || txPttFailed) return
            txPttFailed = true
            rigPttDesired = false
            rigPttDesiredGeneration = generation
            rigPttFailureCount = 0
            rigPttCompletion = null
            if (!rigPttCommandPending && rigPttAsserted) {
                rigPttCommandPending = true
                txHandler.post { runRigPttCommand() }
            }
        }

        Log.e(TAG, message)
        txMonitorActive = false
        txMonitorHandler.removeCallbacks(txMonitorRunnable)
        engine?.stopTransmit()
        txSessionActive = false
        txAudioActive = false
        txMonitorWasAudioActive = false
        trusdxTxIntentActive = false
        engine?.setTransmitReady(false)
        broadcastError(message)
        broadcastTxState(TX_STATE_FAILED)
    }

    /**
     * Handle incoming MSG commands - always runs regardless of autoreply setting.
     * This ensures messages are saved to inbox even if auto-ACK is disabled.
     * 
     * JS8Call MSG format can be:
     *   FROM: TO MSG payload    (with space, single frame)
     *   FROM: TO MSGpayload     (without space, bandwidth optimization)
     *   FROM: TO MSG            (multi-frame: command frame)
     *   payload...              (multi-frame: data frames follow)
     */
    private fun maybeHandleIncomingMessage(text: String, snr: Int, freq: Float, type: Int, submode: Int) {
        val callsign = getConfiguredCallsign()
        Log.d(TAG, "maybeHandleIncomingMessage: text='$text' type=$type callsign=$callsign")
        if (callsign == null) {
            Log.d(TAG, "maybeHandleIncomingMessage: no callsign configured, skipping")
            return
        }
        
        val now = System.currentTimeMillis()
        cleanupMsgBuffers(now, callsign)

        // PC processDecodeEvent: a first frame clears an old buffer at this offset
        if (type and FRAME_FIRST != 0) {
            synchronized(msgLock) {
                findClosestMsgBufferKey(msgBuffers.keys, freq, rxThresholdHz(submode))?.let { msgBuffers.remove(it) }
            }
        }

        // Try parsing as a directed command (MSG header frame)
        val directed = parseDirectedCommand(text)
        
        val command = directed?.command?.uppercase()
        // PC: @APRSIS GRID and CMD are spotted for everyone
        if (directed != null && command != null && command in APRS_SPOT_COMMANDS && directed.to.trim().uppercase() == "@APRSIS") {
            val from = directed.from.trim().uppercase()
            if (isLastFrame(type)) {
                handleAprsSpot(command, from, directed.payload, snr, freq)
                return
            }
            val key = findMatchingMsgBufferKey(freq, submode) ?: Math.round(freq)
            synchronized(msgLock) {
                msgBuffers[key] = MsgBuffer(
                    from = from, to = "@APRSIS", snr = snr, frequency = freq, lastUpdated = now,
                    parts = if (directed.payload.isNotBlank()) mutableListOf(directed.payload) else mutableListOf(),
                    command = command
                )
            }
            return
        }
        // PC processCommandActivity: these lists come before the inbox, mailbox and ACK
        if (directed != null && (command == Js8Commands.CMD_MSG || command in MAILBOX_COMMANDS) && isAutoreplyBlocked(directed.from)) return
        // PC processCommandActivity: @APRSIS MSG TO:<call> is an APRS message like MSG TO:
        val mailboxCommand = if (command == Js8Commands.CMD_MSG && directed?.to?.trim()?.uppercase() == "@APRSIS") Js8Commands.CMD_MSG_TO else command
        if (directed != null && mailboxCommand != null && mailboxCommand in MAILBOX_COMMANDS) {
            // Easy JS8 mailbox (PC processCommandActivity): MSG TO:, QUERY, QUERY MSGS, QUERY CALL
            val command = mailboxCommand
            val to = directed.to.trim().uppercase()
            val isForMe = isSelfCallsign(callsign, to)
            val isForMyGroup = isSubscribedGroup(to)
            val isAllCall = isAllCallTarget(to) && !PreferenceManager.getDefaultSharedPreferences(this).getBoolean("js8cfg_avoid_allcall", false)
            val accepted = when (command) {
                Js8Commands.CMD_MSG_TO -> isForMe || isForMyGroup || to == "@APRSIS"
                Js8Commands.CMD_QUERY -> isForMe || isForMyGroup
                else -> isForMe || isForMyGroup || isAllCall // QUERY MSGS
            }
            if (!accepted) return
            val payload = directed.payload
            if (isLastFrame(type)) {
                val checked = checkedMsgText(payload, to) ?: return
                handleMailboxCommand(command, directed.from.trim().uppercase(), to, checked, snr, freq)
                return
            }
            val key = findMatchingMsgBufferKey(freq, submode) ?: Math.round(freq)
            synchronized(msgLock) {
                msgBuffers[key] = MsgBuffer(
                    from = directed.from.trim().uppercase(), to = to, snr = snr, frequency = freq, lastUpdated = now,
                    parts = if (payload.isNotBlank()) mutableListOf(payload) else mutableListOf(),
                    command = command
                )
            }
            return
        }

        if (directed != null && command == Js8Commands.CMD_MSG) {
            // This is a MSG command frame
            val isForMe = isSelfCallsign(callsign, directed.to)
            val isForMyGroup = isSubscribedGroup(directed.to)
            
            if (!isForMe && !isForMyGroup) {
                Log.d(TAG, "maybeHandleIncomingMessage: MSG not for me ($callsign) or my groups, skipping")
                return
            }
            
            val initialPayload = directed.payload
            
            Log.d(TAG, "maybeHandleIncomingMessage: MSG command from=${directed.from} to=${directed.to} initialPayload='$initialPayload' isLastFrame=${isLastFrame(type)}")
            
            // If this is the last frame and we have payload, deliver immediately
            if (isLastFrame(type) && initialPayload.isNotBlank()) {
                val conversationId = if (isForMyGroup) directed.to else directed.from
                val cleanPayload = checkedMsgText(initialPayload, directed.to) ?: return
                val path = parseRelayPathCallsigns(directed.from, cleanPayload).joinToString(">")
                Log.i(TAG, "MSG received (single frame): from=$path to=${directed.to} text='$cleanPayload' conversationId=$conversationId")
                persistIncomingMessage(path, cleanPayload, snr, freq, null, conversationId)
                
                // Queue the ACK now that the full message is in; with AUTO off it goes to the Typing Box (PC, N1)
                Log.i(TAG, "Auto ACK for MSG from=${directed.from}")
                queueAutoReply("$callsign: $path ACK", null, priority = PRIORITY_NORMAL)
                return
            }
            
            // Buffer the MSG command and wait for data frames
            val key = findMatchingMsgBufferKey(freq, submode) ?: Math.round(freq)
            val buffer = MsgBuffer(
                from = directed.from.trim().uppercase(),
                to = directed.to.trim().uppercase(),
                snr = snr,
                frequency = freq,
                lastUpdated = now,
                parts = if (initialPayload.isNotBlank()) mutableListOf(initialPayload) else mutableListOf()
            )
            synchronized(msgLock) {
                msgBuffers[key] = buffer
            }
            Log.d(TAG, "maybeHandleIncomingMessage: buffered MSG command, waiting for data frames")
            
            // If last frame with no payload, it's an empty MSG (shouldn't happen normally)
            if (isLastFrame(type)) {
                synchronized(msgLock) {
                    msgBuffers.remove(key)
                }
                Log.d(TAG, "maybeHandleIncomingMessage: last frame but no payload, discarding")
            }
            return
        }
        
        // Not a directed command - check if it's a data frame for a buffered MSG
        if (!DecodedMessage.isDataFrame(type)) {
            return
        }
        
        val result = synchronized(msgLock) {
            val key = msgBuffers.followDrift(findMatchingMsgBufferKey(freq, submode) ?: return@synchronized null, freq)
            val buffer = msgBuffers[key] ?: return@synchronized null
            buffer.parts.add(text)
            buffer.lastUpdated = now
            Log.d(TAG, "maybeHandleIncomingMessage: added data frame to buffer, parts=${buffer.parts.size} isLastFrame=${isLastFrame(type)}")
            if (isLastFrame(type)) {
                msgBuffers.remove(key)
                buffer
            } else {
                null
            }
        }
        
        if (result != null) {
            processMsgBuffer(result, callsign)
        }
    }
    
    private data class MsgBuffer(
        val from: String,
        val to: String,
        val snr: Int,
        val frequency: Float,
        var lastUpdated: Long,
        val parts: MutableList<String> = mutableListOf(),
        /** MSG, or a mailbox command (MSG TO:, QUERY, QUERY MSGS). */
        val command: String = Js8Commands.CMD_MSG
    )
    
    private val msgBuffers = mutableMapOf<Int, MsgBuffer>()
    private val msgLock = Any()
    
    /** PC processBufferedActivity: older than 60 s = treated as finished, empty at 90 s = dropped. */
    private fun cleanupMsgBuffers(now: Long, myCallsign: String) {
        val closed = mutableListOf<MsgBuffer>()
        synchronized(msgLock) {
            msgBuffers.entries.removeIf { (_, buffer) ->
                val age = now - buffer.lastUpdated
                when {
                    age > BUFFER_CLOSE_MS && buffer.parts.isNotEmpty() -> { closed += buffer; true }
                    else -> age > BUFFER_DROP_MS
                }
            }
        }
        closed.forEach { processMsgBuffer(it, myCallsign) }
    }
    
    private fun findMatchingMsgBufferKey(freq: Float, submode: Int): Int? {
        synchronized(msgLock) {
            return findClosestMsgBufferKey(msgBuffers.keys, freq, rxThresholdHz(submode))
        }
    }

    /** PC hasExistingMessageBufferToMe. */
    private fun hasOpenBufferToMe(): Boolean {
        val my = getConfiguredCallsign() ?: return false
        return synchronized(msgLock) { msgBuffers.values.any { isSelfCallsign(my, it.to) } }
    }

    /** PC JS8::Submode::rxThreshold. */
    private fun rxThresholdHz(submode: Int): Int = when (submode) {
        SUBMODE_FAST -> 16
        SUBMODE_TURBO -> 32
        SUBMODE_ULTRA -> 50
        else -> 10
    }
    
    private fun processMsgBuffer(buffer: MsgBuffer, myCallsign: String) {
        if (buffer.command in APRS_SPOT_COMMANDS) {
            handleAprsSpot(buffer.command, buffer.from, assembleMsgPayload(buffer.parts), buffer.snr, buffer.frequency)
            return
        }
        if (buffer.command != Js8Commands.CMD_MSG) {
            val checked = checkedMsgText(assembleMsgPayload(buffer.parts), buffer.to) ?: return
            handleMailboxCommand(buffer.command, buffer.from, buffer.to, checked, buffer.snr, buffer.frequency)
            return
        }
        // Data frames split at arbitrary byte boundaries, not word boundaries.
        val cleanText = checkedMsgText(assembleMsgPayload(buffer.parts), buffer.to) ?: run {
            Log.d(TAG, "processMsgBuffer: failed checksum, discarding")
            return
        }

        if (cleanText.isBlank()) {
            Log.d(TAG, "processMsgBuffer: empty message after joining parts, discarding")
            return
        }
        
        val isForMyGroup = isSubscribedGroup(buffer.to)
        val conversationId = if (isForMyGroup) buffer.to else buffer.from
        
        val path = parseRelayPathCallsigns(buffer.from, cleanText).joinToString(">")
        Log.i(TAG, "MSG received (multi-frame): from=$path to=${buffer.to} text='$cleanText' conversationId=$conversationId")
        persistIncomingMessage(path, cleanText, buffer.snr, buffer.frequency, null, conversationId)
        
        // Queue the ACK now that the full message is in; with AUTO off it goes to the Typing Box (PC, N1)
        Log.i(TAG, "Auto ACK for MSG from=${buffer.from}")
        queueAutoReply("$myCallsign: $path ACK", null, priority = PRIORITY_NORMAL)
    }
    
    
    /**
     * PC processBufferedActivity: the text without its end mark and checksum, null when the checksum
     * fails. No text means no checksum, and @APRSIS MSG is sent without one (PC skipAprsChecksum).
     */
    private fun checkedMsgText(payload: String, to: String): String? {
        val text = payload.trim().replace(Regex("\\s*[▪■♢]+\\s*$"), "").trim()
        if (text.isEmpty() || to.trim().equals("@APRSIS", ignoreCase = true)) return text
        val (valid, body) = validateRelayChecksum(text)
        return if (valid) body.trim() else null
    }

    private val aprsRelayDedupe = mutableMapOf<String, Long>()
    private val aprsInboundDedupe = mutableMapOf<String, Long>()
    private val aprsClient by lazy {
        AprsIsClient { from, to, message, id -> mainHandler.post { onAprsMessage(from, to, message, id) } }
    }

    private fun prefOn(key: String) = PreferenceManager.getDefaultSharedPreferences(this).all[key]?.toString() == "true"

    /** PC spot_to_aprs: Enable spotting to reporting networks and Enable spotting @APRSIS messages. */
    private fun spotToAprs() = PreferenceManager.getDefaultSharedPreferences(this).let { it.getBoolean(PREF_PSK_REPORTER, true) && it.getBoolean("aprs_is", true) }

    /** PC spot_to_aprs_relay: also needs Enable relaying inbound APRS messages to Heard List stations. */
    private fun aprsRelayOn() = spotToAprs() && prefOn("js8cfg_aprs_relay")

    /** PC prepareSpotting, but only while RX is on: nothing can be transmitted with the engine off. */
    private fun updateAprsState() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val enabled = spotToAprs() && engine != null
        if (enabled) {
            aprsClient.setSkipPercent(0.25f)
            aprsClient.setServer(prefs.getString("js8cfg_aprs_server", "rotate.aprs2.net")?.trim().orEmpty(),
                numberPref("js8cfg_aprs_port", 14580.0).toInt())
        }
        aprsClient.setIncomingRelayEnabled(enabled && aprsRelayOn())
        aprsClient.setPaused(!enabled)
    }

    /** PC spotAprsGrid / spotAprsCmd: an assembled @APRSIS GRID or CMD frame goes up to APRS-IS. */
    private fun handleAprsSpot(command: String, from: String, payload: String, snr: Int, freq: Float) {
        val message = payload.trim().replace(Regex("\\s*[▪■]+\\s*$"), "").trim()
        if (!spotToAprs() || isOnCallList("js8cfg_spot_blacklist", from)) return
        val myCall = getConfiguredCallsign() ?: return
        val byCall = AprsIsClient.replaceCallsignSuffixWithSSID(myCall, com.js8call.example.js8ui.Js8Activity.baseCall(myCall))
        val fromCall = AprsIsClient.replaceCallsignSuffixWithSSID(from, com.js8call.example.js8ui.Js8Activity.baseCall(from))
        if (command == Js8Commands.CMD_APRS_GRID) {
            val mhz = String.format(Locale.US, "%.6f", (currentDialHz + Math.round(freq)) / 1_000_000.0)
            val comment = "${mhz}MHz ${formatSNR(snr)}dB"
            aprsGridRegex.findAll(message.uppercase(Locale.US)).map { it.value }.filter { it != "RR73" }.forEach { grid ->
                aprsClient.enqueueSpot(byCall, fromCall, grid, if ("/" in from) "$from $comment" else comment)
            }
            return
        }
        val (valid, text) = validateRelayChecksum(message)
        if (!valid) {
            Log.d(TAG, "APRS CMD failed checksum, discarded: $message")
            return
        }
        aprsClient.enqueueThirdParty(byCall, fromCall, text)
    }

    /** PC AprsInboundRelay::onMessageReceived. */
    private fun onAprsMessage(from: String, to: String, message: String, messageId: String) {
        if (engine == null || !aprsRelayOn() || isOnCallList("js8cfg_spot_blacklist", to)) return
        val heardMs = callHeardMs?.invoke(to) ?: return
        val now = System.currentTimeMillis()
        val aging = numberPref("js8cfg_callsign_aging", 0.0).toInt()
        if (aging > 0 && now - heardMs > aging * 60_000L) return
        if (message.length >= 4 && message.startsWith("ack", ignoreCase = true) &&
            message.drop(3).all { it.isLetterOrDigit() }) return
        if (messageId.isNotEmpty()) {
            aprsInboundDedupe.entries.removeIf { now - it.value > APRS_DEDUPE_MS }
            val key = "$from|$to|$messageId".uppercase(Locale.US)
            val duplicate = key in aprsInboundDedupe
            aprsClient.enqueueMessageAck(to, from, messageId)
            if (duplicate) return
            aprsInboundDedupe[key] = now
        }
        val intent = Intent(ACTION_APRS_NOTICE).putExtra(EXTRA_TEXT, "APRS-IS Relay: $from -> $to: $message")
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        broadcastQueueTx("@APRSIS MSG TO:$to $message DE $from".uppercase(Locale.US), null, priority = APRS_RELAY_PRIORITY)
    }

    /**
     * Easy JS8 mailbox: the PC's processCommandActivity for MSG TO: (hold a message for another
     * station, or an @APRSIS message for us), QUERY MSGS (YES MSG ID n / NO) and QUERY MSG n
     * (send the held message, mark it collected). Replies go out with AUTO on, like Easy JS8's
     * other automatic replies. Relayed requests (A>B) are answered to the sender only.
     */
    private fun handleMailboxCommand(command: String, from: String, to: String, text: String, snr: Int, freq: Float, replyPath: String = from) {
        val my = getConfiguredCallsign() ?: return
        if (isSelfCallsign(my, from)) return
        val ctx = this
        val isAllCall = isAllCallTarget(to)
        if (isAllCall && allCallRepliedRecently(from)) return
        val isGroupCall = isSubscribedGroup(to) || to == "@APRSIS"
        // PC: relayed, the requestor is the end of the path and the reply goes back along it
        val relayed = replyPath.contains(">")
        val who = if (relayed) replyPath.substringAfterLast(">") else from
        val calls = if (command == Js8Commands.CMD_QUERY_CALL) callActivityNow() else emptyList() // read here, on the main thread
        messageScope.launch {
            var reply: String? = null
            var afterSend: (() -> Unit)? = null
            when (command) {
                Js8Commands.CMD_MSG_TO -> {
                    if (isAllCall) return@launch
                    val segs = text.replace(Regex("^TO:\\s*", RegexOption.IGNORE_CASE), "").split(Regex("\\s+")).filter { it.isNotEmpty() }
                    if (segs.isEmpty()) return@launch
                    val toCall = segs.first().uppercase()
                    val body = segs.drop(1).joinToString(" ").trim()
                    if (to == "@APRSIS") {
                        // APRS relay: a message to us, from the DE <SENDER> at its end; once a minute
                        if (!isSelfCallsign(my, toCall) || body.isEmpty()) return@launch
                        val sender = Regex("\\s+DE\\s+(\\S+)\\s*$").find(body)?.groupValues?.get(1) ?: "APRS"
                        val key = "$toCall|$body|$sender".uppercase()
                        val now = System.currentTimeMillis()
                        synchronized(aprsRelayDedupe) {
                            aprsRelayDedupe.entries.removeIf { now - it.value > 60_000 }
                            if (aprsRelayDedupe.containsKey(key)) return@launch
                            aprsRelayDedupe[key] = now
                        }
                        mainHandler.post { persistIncomingMessage("APRS", body, snr, freq, "APRS") }
                        return@launch
                    }
                    // Station > Disable message relay (>) when autoreply is enabled (PC relay_off)
                    if (!isRelayEnabled() || body.isEmpty()) return@launch
                    val calls = if (relayed) replyPath.split(">") else parseRelayPathCallsigns(from, body)
                    val path = calls.joinToString(">")
                    Js8Mailbox.store(ctx, com.js8call.example.js8ui.Js8Activity.baseCall(toCall), from, body, path, snr, freq, currentDialHz.takeIf { it > 0L })
                    Log.i(TAG, "Mailbox: holding message from $from for $toCall: '$body'")
                    reply = "${if (calls.size > 1) path else from} ACK"
                }
                Js8Commands.CMD_QUERY_MSGS, "QUERY MSGS?" -> {
                    var mid = Js8Mailbox.nextIdFor(ctx, who)
                    var pending = Js8Mailbox.countFor(ctx, who)
                    if (isGroupCall) pending += Js8Mailbox.countGroupFor(ctx, to, from)
                    pending--
                    if (mid == -1L && isGroupCall) mid = Js8Mailbox.nextGroupIdFor(ctx, to, from)
                    reply = when {
                        mid != -1L && pending > 0 -> "$replyPath YES MSG ID $mid +$pending"
                        mid != -1L -> "$replyPath YES MSG ID $mid"
                        !isAllCall -> "$replyPath NO"
                        else -> null
                    }
                }
                Js8Commands.CMD_QUERY_CALL -> {
                    val who = Regex("[A-Z0-9/]*[0-9][A-Z0-9/]*").find(text.uppercase())?.value ?: return@launch
                    val heard = heardStation(who, calls) ?: return@launch
                    reply = "$replyPath YES ${formatSNR(heard.snr)} (${sinceText(heard.utcMs)})"
                }
                Js8Commands.CMD_QUERY -> {
                    if (isAllCall) return@launch
                    val segs = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
                    if (segs.size < 2 || !segs[0].equals("MSG", true)) return@launch
                    val mid = segs[1].toLongOrNull() ?: return@launch
                    val m = Js8Mailbox.get(ctx, mid) ?: return@launch
                    if (m.direction != com.js8call.example.data.MessageEntity.DIRECTION_HELD) return@launch
                    val heldTo = m.conversationId
                    val isGroupMsg = heldTo.startsWith("@")
                    if (!isGroupMsg && !heldTo.equals(who, true) &&
                        !heldTo.equals(com.js8call.example.js8ui.Js8Activity.baseCall(who), true)) return@launch
                    val body = m.text.trim()
                    if (body.isEmpty()) return@launch
                    var pending = Js8Mailbox.countFor(ctx, who)
                    if (isGroupCall) pending += Js8Mailbox.countGroupFor(ctx, to, from)
                    pending -= 2
                    var next = Js8Mailbox.lookaheadIdFor(ctx, who, mid)
                    if (next == -1L && isGroupMsg) next = Js8Mailbox.lookaheadGroupIdFor(ctx, to, from, mid)
                    val sender = m.senderCallsign.orEmpty()
                    reply = when {
                        next != -1L && pending > 0 -> "$replyPath MSG $body FROM $sender NEXT MSG ID $next +$pending"
                        next != -1L -> "$replyPath MSG $body FROM $sender NEXT MSG ID $next"
                        else -> "$replyPath MSG $body FROM $sender"
                    }
                    // Collected: no longer offered (the PC marks it once the reply has gone out)
                    afterSend = {
                        if (isGroupMsg) Js8Mailbox.markGroupDelivered(ctx, mid, from)
                        else messageScope.launch { Js8Mailbox.markDelivered(ctx, mid) }
                    }
                }
            }
            val r = reply ?: return@launch
            // PC: QUERY MSGS, QUERY CALL and replies to @ALLCALL need AUTO (processCommandActivity.cpp 984, 1054, 1122)
            val needsAuto = isAllCall || command == Js8Commands.CMD_QUERY_MSGS || command == "QUERY MSGS?" || command == Js8Commands.CMD_QUERY_CALL
            if (needsAuto && !isAutoreplyEnabled()) return@launch
            Log.i(TAG, "Mailbox reply to $from: '$r'")
            if (isAllCall) markAllCallReplied(from)
            mainHandler.post {
                queueAutoReply("$my: $r", null, priority = PRIORITY_NORMAL, onAccepted = afterSend)
            }
        }
    }

    private fun isSubscribedGroup(target: String): Boolean {
        if (!target.startsWith("@")) return false
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val groupsStr = prefs.getString("my_groups", "") ?: ""
        val groups = groupsStr.split(",").map { it.trim().uppercase() }.filter { it.isNotEmpty() }
        return groups.contains(target.uppercase())
    }

    /** PC processCommandActivity: replies are made with AUTO off too; the queue puts them in the Typing Box (N1). */
    private fun maybeHandleAutoReply(text: String, snr: Int, mode: Int) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val callsign = getConfiguredCallsign()
        if (callsign == null) {
            warnMissingCallsign()
            return
        }

        val heartbeat = parseHeartbeat(text)
        if (heartbeat != null) {
            // PC presentlyWantHBReplies: AUTO + HB + ACK, HB and ACK off by default
            fun skip(why: String) = Log.i(TAG, "HB ACK to ${heartbeat.from} skipped: $why")
            if (!isAutoreplyEnabled() || !prefs.getBoolean(PREF_HB, false) || !prefs.getBoolean(PREF_HB_ACK, false)) {
                skip("AUTO ${isAutoreplyEnabled()} HB ${prefs.getBoolean(PREF_HB, false)} ACK ${prefs.getBoolean(PREF_HB_ACK, false)}"); return
            }
            if (isSelfCallsign(callsign, heartbeat.from)) return
            // PC canCurrentModeSendHeartbeat, then no ACK with a buffer open or, when paused, a callsign selected
            if (getPreferredTxSubmode() !in setOf(SUBMODE_NORMAL, SUBMODE_FAST, SUBMODE_SLOW)) { skip("speed ${getPreferredTxSubmode()}"); return }
            synchronized(msgLock) { msgBuffers.values.map { "${it.from}>${it.to} ${it.command}" } }.let {
                if (it.isNotEmpty()) { skip("open buffers $it"); return }
            }
            if (prefs.getBoolean("js8cfg_heartbeat_qso_pause", true) && !Js8Api.State.selectedCall.isNullOrEmpty()) {
                skip("callsign selected ${Js8Api.State.selectedCall}"); return
            }
            processHeartbeatRateLimit(heartbeat.from.trim().uppercase())
            if (isAutoreplyBlocked(heartbeat.from) || isOnCallList("js8cfg_hb_blacklist", heartbeat.from)) { skip("callsign list"); return }
            if (allCallRepliedRecently(heartbeat.from)) { skip("replied in the last 15 minutes"); return }
            val snrText = formatSNR(snr)
            if (snrText.isEmpty()) { skip("SNR $snr"); return }
            val target = heartbeat.from.trim().uppercase()
            // A message held for them: "MSG ID n" (+ how many more) on the ACK (PC sendHeartbeatAck extra)
            messageScope.launch {
                val group = parseDirectedCommand(text)?.to?.trim()?.uppercase().orEmpty().takeIf { isSubscribedGroup(it) }
                var mid = Js8Mailbox.nextIdFor(this@JS8EngineService, target)
                var pending = Js8Mailbox.countFor(this@JS8EngineService, target)
                if (group != null) {
                    pending += Js8Mailbox.countGroupFor(this@JS8EngineService, group, target)
                    if (mid == -1L) mid = Js8Mailbox.nextGroupIdFor(this@JS8EngineService, group, target)
                }
                pending--
                val extra = when {
                    mid == -1L -> ""
                    pending > 0 -> " MSG ID $mid +$pending)" // the PC's own text, bracket included
                    else -> " MSG ID $mid"
                }
                val payload = "$callsign: $target HEARTBEAT SNR $snrText$extra"
                Log.i(TAG, "Auto HB ACK: from=$target snr=$snrText text='$payload'")
                markAllCallReplied(target)
                mainHandler.post { queueAutoReply(payload, null, priority = PRIORITY_HB) }
            }
            return
        }

        val directed = parseDirectedCommand(text) ?: return
        if (isAutoreplyBlocked(directed.from)) return
        if (!shouldReplyToDirected(callsign, directed)) return
        val cmdUpper = directed.command.uppercase()
        when {
            cmdUpper == "SNR?" || cmdUpper == "?" -> {
                val snrText = formatSNR(snr)
                if (snrText.isEmpty()) return
                Log.i(TAG, "Auto SNR reply: from=${directed.from} snr=$snrText")
                queueAutoReply("$callsign: ${directed.from} SNR $snrText", null, priority = PRIORITY_NORMAL)
            }
            cmdUpper == "INFO?" -> {
                // PC: skipped only when the stored text, trimmed, is empty; macros filled in after (pCA 340-356)
                val infoRaw = com.js8call.example.api.Js8Api.myInfo(prefs).trim()
                if (infoRaw.isEmpty()) return
                val info = com.js8call.example.js8ui.Js8Macros.expand(infoRaw, prefs)
                Log.i(TAG, "Auto INFO reply: from=${directed.from}")
                queueAutoReply("$callsign: ${directed.from} INFO $info", null, priority = PRIORITY_NORMAL)
            }
            cmdUpper == "STATUS?" -> {
                // PC: skipped only when the stored text, trimmed, is empty; macros filled in after (pCA 340-356)
                val statusRaw = com.js8call.example.api.Js8Api.myStatus(prefs).trim()
                if (statusRaw.isEmpty()) return
                val status = com.js8call.example.js8ui.Js8Macros.expand(statusRaw, prefs)
                Log.i(TAG, "Auto STATUS reply: from=${directed.from}")
                queueAutoReply("$callsign: ${directed.from} STATUS $status", null, priority = PRIORITY_NORMAL)
            }
            cmdUpper == "HEARING?" -> mainHandler.post { // after the decodes already sent to Call Activity (PC: decodes first, then commands)
                val heard = getRecentHeardCallsigns(setOf(directed.from.trim().uppercase(), callsign), HEARD_LIMIT)
                Log.i(TAG, "Auto HEARING reply: from=${directed.from}, count=${heard.size}")
                queueAutoReply("$callsign: ${listOf("${directed.from} HEARING").plus(heard).joinToString(" ")}", null, priority = PRIORITY_NORMAL)
            }
            cmdUpper == "GRID?" -> {
                val grid = com.js8call.example.api.Js8Api.myGrid(prefs)
                if (grid.isBlank()) return
                Log.i(TAG, "Auto GRID reply: from=${directed.from}")
                queueAutoReply("$callsign: ${directed.from} GRID $grid", null, priority = PRIORITY_NORMAL)
            }
            cmdUpper == "AGN?" -> {
                val message = lastTxMessage.trimEnd()
                if (message.isBlank()) return
                Log.i(TAG, "Auto AGN reply: from=${directed.from}")
                queueAutoReply(message, null, priority = PRIORITY_NORMAL)
            }
            // MSG auto-ACK is handled in maybeHandleIncomingMessage after full message is received
            else -> return
        }
    }

    private fun handleRelayFrame(text: String, snr: Int, mode: Int, freq: Float, type: Int) {
        if (!isRelayEnabled()) return
        val callsign = getConfiguredCallsign() ?: return
        val now = System.currentTimeMillis()
        cleanupRelayBuffers(now)

        val directed = parseDirectedCommand(text)
        if (directed != null && directed.command == ">") {
            if (isAutoreplyBlocked(directed.from)) return
            val target = directed.to.trim().uppercase()
            if (!isSelfCallsign(callsign, target) && !isSubscribedGroup(target)) return

            val key = findMatchingRelayBufferKey(freq, mode) ?: Math.round(freq)
            val buffer = RelayBuffer(
                from = directed.from.trim().uppercase(),
                to = target,
                snr = snr,
                submode = getPreferredTxSubmode(),
                frequency = freq,
                lastUpdated = now,
            )
            synchronized(relayLock) {
                relayBuffers[key] = buffer
            }
            Log.i(TAG, "Relay command buffered from=${directed.from} to=${directed.to} freq=$freq")

            if (directed.payload.isNotBlank()) {
                val (payload, hasEom) = normalizeRelayPayload(directed.payload)
                if (payload.isNotBlank()) {
                    buffer.parts.add(payload)
                    buffer.lastUpdated = now
                }
                if (hasEom || isLastFrame(type)) {
                    synchronized(relayLock) {
                        relayBuffers.remove(key)
                    }
                    processRelayBuffer(buffer)
                }
                return
            }

            return
        }

        if (!DecodedMessage.isDataFrame(type)) return

        val result = synchronized(relayLock) {
            val key = relayBuffers.followDrift(findMatchingRelayBufferKey(freq, mode) ?: return@synchronized null, freq)
            val buffer = relayBuffers[key] ?: return@synchronized null
            buffer.parts.add(text)
            buffer.lastUpdated = now
            if (isLastFrame(type)) {
                relayBuffers.remove(key)
                buffer
            } else {
                null
            }
        }

        if (result != null) {
            processRelayBuffer(result)
        }
    }

    private fun parseHeartbeat(text: String): Heartbeat? {
        val match = heartbeatRegex.find(text) ?: return null
        val from = match.groupValues.getOrNull(1)?.trim().orEmpty()
        if (from.isBlank()) return null
        return Heartbeat(from)
    }

    private data class Heartbeat(val from: String)

    private fun parseDirectedCommand(text: String): DirectedCommand? {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return null

        val tokens = trimmed.split(Regex("\\s+"))
        if (tokens.size < 2) return null

        var index = 0
        var from = ""
        var toToken = tokens[index]

        if (toToken.endsWith(":")) {
            from = toToken.trimEnd(':')
            index++
            if (index >= tokens.size) return null
            toToken = tokens[index]
        }

        var to = toToken
        val command: String
        val payload: String

        if (toToken.endsWith(">")) {
            to = toToken.trimEnd('>')
            command = ">"
            payload = tokens.drop(index + 1).joinToString(" ")
        } else {
            if (index + 1 >= tokens.size) return null
            val remainder = tokens.drop(index + 1).joinToString(" ")
            val match = Js8Commands.matchAt(remainder)
            if (match != null) {
                command = match.command
                payload = match.payload
            } else {
                // Unknown text keeps the single-token shape, so freetext frames
                // reach callers unchanged.
                command = tokens[index + 1]
                payload = tokens.drop(index + 2).joinToString(" ")
            }
        }

        if (to.isBlank() || command.isBlank()) return null
        if (from.isBlank() && command != ">") return null
        return DirectedCommand(from, to, command, payload)
    }

    private data class DirectedCommand(
        val from: String,
        val to: String,
        val command: String,
        val payload: String
    )

    private data class RelayBuffer(
        val from: String,
        val to: String,
        val snr: Int,
        val submode: Int,
        val frequency: Float,
        var lastUpdated: Long,
        val parts: MutableList<String> = mutableListOf()
    )

    private fun shouldReplyToDirected(myCall: String, command: DirectedCommand): Boolean {
        if (isSelfCallsign(myCall, command.from)) return false
        if (isSubscribedGroup(command.to)) return true
        if (command.to.startsWith("@")) return false
        if (!isSelfCallsign(myCall, command.to)) return false
        return true
    }

    private fun callList(key: String): Set<String> =
        PreferenceManager.getDefaultSharedPreferences(this).getString(key, "").orEmpty().split(',') // PC splitWords: commas only
            .map { it.trim().uppercase() }.filter { it.isNotEmpty() }.toSet()

    /** The call or its base call is on a Station callsign list (PC list.contains(call) || contains(base_callsign)). */
    private fun isOnCallList(key: String, call: String): Boolean {
        val list = callList(key)
        val upper = call.trim().uppercase()
        return upper in list || com.js8call.example.js8ui.Js8Activity.baseCall(upper) in list
    }

    /** Station > Only autoreply to / Never autoreply to these callsigns (PC auto_whitelist, auto_blacklist). */
    private fun isAutoreplyBlocked(from: String): Boolean =
        (callList("js8cfg_auto_whitelist").isNotEmpty() && !isOnCallList("js8cfg_auto_whitelist", from)) ||
            isOnCallList("js8cfg_auto_blacklist", from)

    private fun isAutoreplyEnabled(): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        return prefs.getBoolean(PREF_AUTOREPLY_ENABLED, false)
    }

    private fun isTruSdxDiagnosticsEnabled(): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        return prefs.getBoolean(PREF_TRUSDX_DIAGNOSTICS_ENABLED, false)
    }

    private fun isRelayEnabled(): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        return prefs.getBoolean(PREF_RELAY_ENABLED, false)
    }

    private fun getPreferredTxSubmode(): Int {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val submode = prefs.getInt(PREF_TX_SUBMODE, SUBMODE_NORMAL)
        return when (submode) {
            SUBMODE_NORMAL,
            SUBMODE_FAST,
            SUBMODE_TURBO,
            SUBMODE_SLOW,
            SUBMODE_ULTRA -> submode
            else -> SUBMODE_NORMAL
        }
    }

    private fun getConfiguredCallsign(): String? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val callsign = prefs.getString("callsign", "")?.trim().orEmpty().uppercase()
        if (callsign.isBlank()) return null
        callsignWarningShown = false
        return callsign
    }

    /** Station > SWL (Listening only): nothing is transmitted (Easy JS8 only). */
    private fun isListenOnly(): Boolean =
        PreferenceManager.getDefaultSharedPreferences(this).getBoolean(PREF_SWL, false)

    private fun warnMissingCallsign() {
        if (callsignWarningShown) return
        callsignWarningShown = true
        broadcastError(getString(R.string.error_callsign_required))
    }

    private fun initPskReporter() {
        if (pskReporterClient != null) return
        val programInfo = "EasyJS8-${BuildConfig.VERSION_NAME}"
        // Debug builds never reach the real PSK Reporter: test decodes would be false spots
        val pskHost = if (BuildConfig.DEBUG) {
            PreferenceManager.getDefaultSharedPreferences(this).getString("debug_psk_host", "10.0.2.2") ?: "10.0.2.2"
        } else {
            PskReporterClient.HOST
        }
        pskReporterClient = PskReporterClient(programInfo, pskHost, nowMs = { driftedNowMs() }).apply {
            // PC: 5 s on the status bar
            onUnavailable = { mainHandler.post { android.widget.Toast.makeText(this@JS8EngineService, "Spotting to PSK Reporter unavailable", android.widget.Toast.LENGTH_SHORT).show() } }
        }
        updateAprsState()
        updatePskReporterState()
    }

    private fun updatePskReporterState() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val enabled = prefs.getBoolean(PREF_PSK_REPORTER, true)
        val callsign = prefs.getString("callsign", "")?.trim().orEmpty().uppercase()
        val grid = com.js8call.example.api.Js8Api.myGrid(prefs)
        Log.d(TAG, "PSKReporter state: enabled=$enabled callsign='$callsign' grid='$grid'")
        currentCallsign = callsign
        currentGrid = grid
        // PC prepareSpotting: on with spotting alone, whatever the callsign and grid (mw 1111-1128). Turned off,
        // the waiting spots go first (pskReporterSendReport(true), mw 1044-1047).
        if (!enabled) {
            pskReporterClient?.stop(flush = true)
            pskReporterEnabled = false
            spotClient?.stop()
            return
        }
        pskReporterClient?.start()
        // PC pskSetLocal: the antenna field is the expanded Station Info
        pskReporterClient?.setLocalStation(callsign, grid, com.js8call.example.js8ui.Js8Macros.expand(Js8Api.myInfo(prefs), prefs))
        pskReporterEnabled = true
        // PC prepareSpotting / spotSetLocal. debug_spot_host: debug builds test against a local server
        val spotHost = prefs.getString("debug_spot_host", null)?.takeIf { BuildConfig.DEBUG } ?: SPOT_HOST
        val spot = spotClient ?: SpotClient(spotHost, SPOT_PORT, "${Js8Api.APP_NAME} ${Js8Api.VERSION}").also { spotClient = it }
        spot.start()
        spot.setLocalStation(callsign, grid, com.js8call.example.js8ui.Js8Macros.expand(Js8Api.myInfo(prefs), prefs))
    }

    /** One command heard for a group, waiting for its data frames (PC m_messageBuffer). */
    private class SpotCmdBuffer(
        val directed: com.js8call.example.js8ui.Js8Activity.Directed, val command: Int, val grid: String,
        val snr: Int, val offset: Int, val mode: Int, val parts: MutableList<String>, var lastUpdated: Long
    )

    private val spotCmdBuffers = mutableMapOf<Int, SpotCmdBuffer>()

    /** PC processBufferedActivity: closed 60 s after its newest frame if a data frame came, dropped at 90 s. Main thread. */
    private fun cleanupSpotCmdBuffers(now: Long) {
        spotCmdBuffers.entries.toList().forEach { (key, b) ->
            val age = now - b.lastUpdated
            when {
                age > BUFFER_CLOSE_MS && b.parts.size > 1 -> { spotCmdBuffers.remove(key); finishSpotCmd(b) } // parts[0] is the command's own text
                age > BUFFER_DROP_MS -> spotCmdBuffers.remove(key)
            }
        }
    }

    /** PC processSpots / spotReport and spotCmd: heard stations and group commands go to JS8NET. */
    private fun spotDecode(snr: Int, freq: Float, text: String, type: Int, mode: Int) {
        val spot = spotClient
        if (spot == null || !pskReporterEnabled) {
            spotCmdBuffers.clear()
            return
        }
        val dial = currentDialHz
        if (dial <= 0) return
        val offset = freq.toInt()
        val now = System.currentTimeMillis()
        cleanupSpotCmdBuffers(now)
        val trimmed = text.trim()
        val call = if (trimmed.substringBefore(' ').endsWith(":")) extractHeardCallsign(trimmed) else null
        if (call != null && !isSelfCallsign(currentCallsign, call) && !isOnCallList("js8cfg_spot_blacklist", call)) {
            // PC processSpots spotReport: held after a dial change, then sent with the dial it was heard on
            val heardDial = decodeDial()
            val grid = extractGrid(trimmed).orEmpty()
            pendingSpots.addLast { spotClient?.enqueueSpot(call, grid, mode, heardDial, offset, snr) }
        }
        val d = if ((type and 0x4) == 0) apiDirectedParser.parseDirected(text) else null
        if (d == null) {
            // PC hasExistingMessageBuffer: the speed's match range, and the buffer follows a drifting signal
            val key = spotCmdBuffers.followDrift(findClosestMsgBufferKey(spotCmdBuffers.keys, freq, rxThresholdHz(mode)) ?: return, freq)
            val buffer = spotCmdBuffers.getValue(key)
            buffer.parts.add(text)
            buffer.lastUpdated = now
            if (isLastFrame(type)) {
                spotCmdBuffers.remove(key)
                finishSpotCmd(buffer)
            }
            return
        }
        if (d.from == "<....>" || d.to == "<....>" || !d.to.startsWith("@")) return
        val command = Js8Commands.COMMANDS[d.cmd.uppercase()] ?: return
        val grid = (d.extra + " " + text).split(Regex("\\s+")).lastOrNull { com.js8call.example.js8ui.Js8Activity.isGrid(it) && it.length >= 4 }.orEmpty()
        if (command !in SPOT_BUFFERED_COMMANDS) {
            if (isOnCallList("js8cfg_spot_blacklist", d.from)) return
            // PC processDecodeEvent: only a CQ carries its text
            val cq = d.cmd.trim().equals("CQ", true) || d.extra.trim().startsWith("CQ", true)
            spot.enqueueCmd(d.cmd.uppercase(), d.from, d.to, "", if (cq) trimmed else "", grid, d.extra, mode, dial, offset, snr)
            return
        }
        val buffer = SpotCmdBuffer(d, command, grid, snr, offset, mode, mutableListOf(d.extra), now)
        if (isLastFrame(type)) finishSpotCmd(buffer) else spotCmdBuffers[offset] = buffer
    }

    /** PC processBufferedActivity: the parts joined, the checksum checked and taken off. */
    private fun finishSpotCmd(buffer: SpotCmdBuffer) {
        val d = buffer.directed
        var message = assembleMsgPayload(buffer.parts).replace(Regex("[\\s▪■♢]+$"), "")
        if (buffer.command != SPOT_NO_CHECKSUM_COMMAND) {
            val (valid, checked) = validateRelayChecksum(message)
            if (!valid) return
            message = checked
        }
        if (isOnCallList("js8cfg_spot_blacklist", d.from)) return
        spotClient?.enqueueCmd(d.cmd.uppercase(), d.from, d.to, "", message, buffer.grid, d.extra,
            buffer.mode, currentDialHz, buffer.offset, buffer.snr)
    }

    private fun updateDialFromPrefs() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val dial = prefs.getString("last_frequency", "")?.trim().orEmpty()
        currentDialHz = dial.toLongOrNull() ?: currentDialHz
    }

    /**
     * PC processSpots (mw 6529-6575): the heard calls wait until 4/5 of a period has passed since the dial
     * last changed, then go with the dial they were heard on. Main thread, every second.
     */
    private fun processSpots() {
        if (!pskReporterEnabled) {
            pendingSpots.clear()
            return
        }
        if (pendingSpots.isEmpty()) return
        val period = com.js8call.example.js8ui.Js8Activity.periodSec(getPreferredTxSubmode())
        if (driftedNowMs() / 1000 - secBandChanged <= (4 * period) / 5) return
        while (pendingSpots.isNotEmpty()) pendingSpots.removeFirst().invoke()
    }

    private fun maybeReportToPskReporter(utc: Int, snr: Int, freq: Float, text: String) {
        if (!pskReporterEnabled) return
        val dialHz = decodeDial() // PC d.dial: the dial when the decode started
        if (dialHz <= 0) {
            Log.d(TAG, "PSKReporter skip: dialHz=$dialHz")
            return
        }
        val call = extractHeardCallsign(text)
        if (call == null) {
            Log.d(TAG, "PSKReporter skip: no callsign in '$text'")
            return
        }
        if (currentCallsign.isNotBlank() && isSelfCallsign(currentCallsign, call)) {
            Log.d(TAG, "PSKReporter skip: self call=$call")
            return
        }
        val grid = extractGrid(text).orEmpty() // PC: every logged call, grid or not
        if (isOnCallList("js8cfg_spot_blacklist", call)) return // PC spot_blacklist
        val rfHz = dialHz + freq.toInt()
        if (rfHz <= 0) {
            Log.d(TAG, "PSKReporter skip: rfHz=$rfHz dialHz=$dialHz freq=$freq")
            return
        }
        val timestamp = decodeUtcToEpochSeconds(utc)
        pendingSpots.addLast {
            Log.d(TAG, "PSKReporter spot: call=$call grid=$grid snr=$snr rfHz=$rfHz ts=$timestamp")
            pskReporterClient?.addSpot(call, grid, snr, rfHz, "JS8", timestamp)
            Js8Api.rxSpot(dialHz, freq.toInt(), call, snr, grid) // PC processSpots: RX.SPOT
        }
    }

    private fun extractGrid(text: String): String? {
        val match = gridRegex.find(text) ?: return null
        return match.value.uppercase(Locale.US)
    }

    private fun decodeUtcToEpochSeconds(utc: Int): Long {
        val hours = utc / 10000
        val minutes = (utc / 100) % 100
        val seconds = utc % 100
        val nowMillis = System.currentTimeMillis()
        val nowUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = nowMillis
        }
        val candidate = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(Calendar.YEAR, nowUtc.get(Calendar.YEAR))
            set(Calendar.MONTH, nowUtc.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, nowUtc.get(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, hours)
            set(Calendar.MINUTE, minutes)
            set(Calendar.SECOND, seconds)
            set(Calendar.MILLISECOND, 0)
        }
        var candidateMillis = candidate.timeInMillis
        val diff = candidateMillis - nowMillis
        val twelveHoursMs = 12L * 60L * 60L * 1000L
        if (diff > twelveHoursMs) {
            candidateMillis -= 24L * 60L * 60L * 1000L
        } else if (diff < -twelveHoursMs) {
            candidateMillis += 24L * 60L * 60L * 1000L
        }
        return candidateMillis / 1000L
    }

    private fun isTransmitActive(): Boolean {
        return txSessionActive || txAudioActive
    }

    private fun isSelfCallsign(myCall: String, from: String): Boolean {
        return CallsignValidator.matches(myCall, from)
    }

    private fun isGroupTarget(target: String): Boolean {
        return target.contains("@")
    }


    private fun isLastFrame(type: Int): Boolean = (type and 0x2) != 0

    private fun formatSNR(snr: Int): String {
        if (snr < -60 || snr > 60) return ""
        val sign = if (snr >= 0) "+" else ""
        val width = if (snr < 0) 3 else 2
        return String.format("%s%0${width}d", sign, snr)
    }

    /** PC processBufferedActivity, the same rule as message buffers: closed 60 s after its newest frame, dropped at 90 s. */
    private fun cleanupRelayBuffers(now: Long) {
        val closed = mutableListOf<RelayBuffer>()
        synchronized(relayLock) {
            relayBuffers.entries.removeIf { (_, buffer) ->
                val age = now - buffer.lastUpdated
                when {
                    age > BUFFER_CLOSE_MS && buffer.parts.isNotEmpty() -> { closed += buffer; true }
                    else -> age > BUFFER_DROP_MS
                }
            }
        }
        closed.forEach { processRelayBuffer(it) }
    }

    private fun updateMessageLogging() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val enabled = prefs.getBoolean(PREF_LOG_MESSAGES, true)
        messageLogger?.setEnabled(enabled)
    }

    /** The engine thread's last decoded frame (onDecodedFrame), for the decode that follows it. */
    @Volatile private var lastDecodedFrame = ""
    /** PC dec_data.params.nutc of the decode run: its period time, hhmmss. */
    @Volatile private var decodePeriodUtc = 0

    /**
     * PC decodeStart (mw 2090-2101): the period time of the run's lowest speed number, taken 2 s after
     * that period's end and rounded down to it. The engine's bits are SubmodeId A, B, C, E, I: Normal,
     * Fast, JS8 40, Slow, JS8 60, the same order as the PC's speed numbers.
     */
    private fun periodUtc(submodes: Int): Int {
        val js8 = com.js8call.example.js8ui.Js8Activity
        val speed = when (Integer.numberOfTrailingZeros(submodes)) {
            1 -> js8.SUBMODE_FAST
            2 -> js8.SUBMODE_TURBO
            3 -> js8.SUBMODE_SLOW
            4 -> js8.SUBMODE_ULTRA
            else -> js8.SUBMODE_NORMAL
        }
        val period = js8.periodSec(speed)
        val t = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = driftedNowMs() + (2 - period) * 1000L
        }
        val sec = t.get(java.util.Calendar.SECOND)
        return t.get(java.util.Calendar.HOUR_OF_DAY) * 10000 + t.get(java.util.Calendar.MINUTE) * 100 + (sec - sec % period)
    }

    /** PC processDecodeEvent: valid frames to ALL.TXT; the date line uses the dial now (m_freqNominal). */
    private fun logRxDecode(text: String, snr: Int, dt: Float, freq: Float, mode: Int, type: Int) {
        val logger = messageLogger ?: return
        logger.logRx(driftedNowMs(), decodePeriodUtc, currentDialHz, lastDecodedFrame, text, snr, dt, freq.toInt(),
            com.js8call.example.js8ui.Js8Activity.allTxtLetter(mode), type)
    }

    /** PC DriftingDateTime::currentDateTimeUtc. */
    private fun driftedNowMs() = System.currentTimeMillis() + (engine?.timeDriftMs() ?: 0L)

    /** PC m_decoderBusyFreq: the dial when the decode started, not after a QSY during it. */
    private fun decodeDial() = decodeDialHz.takeIf { it > 0 } ?: currentDialHz

    /** PC write_transmit_entry at each key-up: the text of the frame going out (mw 2667-2670, 6994-7009). */
    private fun logTxStarted(activeEngine: JS8Engine) {
        val logger = messageLogger ?: return
        val frame = activeEngine.txFrameTexts().getOrNull(activeEngine.txFramesStarted() - 1) ?: return
        logger.logTx(driftedNowMs(), currentDialHz, com.js8call.example.js8ui.Js8Activity.periodSec(lastTxSubmode), frame)
    }

    /** PC hasExistingMessageBuffer: the speed's match range (JS8::Submode::rxThreshold). */
    private fun findMatchingRelayBufferKey(freq: Float, submode: Int): Int? {
        synchronized(relayLock) {
            return findClosestMsgBufferKey(relayBuffers.keys, freq, rxThresholdHz(submode))
        }
    }

    private fun processRelayBuffer(buffer: RelayBuffer) {
        if (buffer.parts.isEmpty()) return
        val combined = stripRelayEom(buffer.parts.joinToString(separator = "").trimEnd())
        if (combined.isBlank()) return

        // N52: a text starting with "@" is no callsign, so it isn't forwarded: ACKed or its command answered (PC pCA 420-434)
        val (valid, message) = validateRelayChecksum(combined)
        val payload = if (valid) {
            message
        } else {
            Log.w(TAG, "Relay payload failed checksum validation")
            return
        }

        if (payload.isBlank()) return

        // PC: a relay to a group is never forwarded; it is ACKed or its command answered (processCommandActivity.cpp 424)
        val forwardPayload = if (buffer.to.startsWith("@")) null else buildRelayForwardPayload(payload)
        if (forwardPayload != null) {
            val forwardText = if (buffer.from.isNotBlank()) {
                "$forwardPayload *DE* ${buffer.from}"
            } else {
                forwardPayload
            }
            sendRelayMessage(forwardText)
            return
        }

        val trimmed = payload.trimStart()
        if (trimmed.startsWith("ACK", ignoreCase = true)) {
            return
        }

        val relayPath = parseRelayPathCallsigns(buffer.from, payload).joinToString(">")
        if (relayPath.isBlank()) return

        if (handleRelayedMailbox(payload, buffer, relayPath)) return
        val handled = maybeHandleRelayedAutoreply(payload, relayPath, buffer.snr, buffer.submode)
        if (!handled) {
            sendRelayMessage("$relayPath ACK")
        }
    }

    /** PC processCommandActivity: MSG, MSG TO:, QUERY, QUERY MSGS and QUERY CALL inside a relay. */
    private fun handleRelayedMailbox(payload: String, buffer: RelayBuffer, relayPath: String): Boolean {
        val text = payload.replace(Regex("\\s*\\*DE\\*.*$"), "").trim()
        val words = text.split(Regex("\\s+"))
        val first = words.first().uppercase()
        val second = words.getOrNull(1).orEmpty().uppercase()
        val mailbox = { command: String, skip: Int ->
            handleMailboxCommand(command, buffer.from, buffer.to, words.drop(skip).joinToString(" "), buffer.snr, buffer.frequency, relayPath)
            true
        }
        return when {
            first == "MSG" && second == "TO:" -> mailbox(Js8Commands.CMD_MSG_TO, 2)
            first == "MSG" && second.startsWith("TO:") -> {
                handleMailboxCommand(Js8Commands.CMD_MSG_TO, buffer.from, buffer.to, words.drop(1).joinToString(" "), buffer.snr, buffer.frequency, relayPath)
                true
            }
            first == "MSG" -> {
                val body = words.drop(1).joinToString(" ")
                if (body.isEmpty()) return false
                persistIncomingMessage(relayPath, body, buffer.snr, buffer.frequency, null, relayPath)
                getConfiguredCallsign()?.let {
                    queueAutoReply("$it: $relayPath ACK", null, priority = PRIORITY_NORMAL)
                }
                true
            }
            first == "QUERY" && (second == "MSGS" || second == "MSGS?") -> mailbox(Js8Commands.CMD_QUERY_MSGS, 2)
            first == "QUERY" && second == "CALL" -> mailbox(Js8Commands.CMD_QUERY_CALL, 2)
            first == "QUERY" -> mailbox(Js8Commands.CMD_QUERY, 1)
            else -> false
        }
    }

    private fun buildRelayForwardPayload(message: String): String? {
        val trimmed = message.trimStart()
        val match = relayTargetRegex.find(trimmed) ?: return null
        val target = match.groupValues[1].trim().uppercase()
        if (!isCallsignLike(target) || isGroupTarget(target)) return null

        val separator = match.groupValues[2]
        if (separator == ">") return trimmed

        val replaceIndex = match.groupValues[1].length
        if (replaceIndex >= trimmed.length) return null
        val builder = StringBuilder(trimmed)
        builder.setCharAt(replaceIndex, '>')
        return builder.toString()
    }

    private fun isCallsignLike(token: String): Boolean {
        return CallsignValidator.isAmateurCallsign(token)
    }

    private fun parseRelayPathCallsigns(from: String, text: String): List<String> {
        val calls = mutableListOf<String>()
        for (match in relayPathRegex.findAll(text.uppercase())) {
            val call = match.groupValues.getOrNull(1)?.trim().orEmpty()
            if (call.isNotEmpty()) {
                calls.add(0, call)
            }
        }
        val base = from.trim().uppercase()
        if (base.isNotEmpty()) {
            calls.add(0, base)
        }
        return calls
    }

    private fun maybeHandleRelayedAutoreply(
        payload: String,
        relayPath: String,
        snr: Int,
        submode: Int
    ): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        val tokens = payload.trimStart().split(Regex("\\s+"), limit = 3)
        if (tokens.isEmpty()) return false
        val cmd = tokens[0].uppercase()

        return when (cmd) {
            "SNR?", "?" -> {
                val snrText = formatSNR(snr)
                if (snrText.isEmpty()) return false
                sendRelayMessage("$relayPath SNR $snrText")
            }
            "INFO?" -> {
                // PC: skipped only when the stored text, trimmed, is empty; macros filled in after (pCA 340-356)
                val infoRaw = com.js8call.example.api.Js8Api.myInfo(prefs).trim()
                if (infoRaw.isEmpty()) return false
                val info = com.js8call.example.js8ui.Js8Macros.expand(infoRaw, prefs)
                sendRelayMessage("$relayPath INFO $info")
            }
            "STATUS?" -> {
                // PC: skipped only when the stored text, trimmed, is empty; macros filled in after (pCA 340-356)
                val statusRaw = com.js8call.example.api.Js8Api.myStatus(prefs).trim()
                if (statusRaw.isEmpty()) return false
                val status = com.js8call.example.js8ui.Js8Macros.expand(statusRaw, prefs)
                sendRelayMessage("$relayPath STATUS $status")
            }
            "HEARING?" -> {
                val callsign = getConfiguredCallsign() ?: return false
                val heard = getRecentHeardCallsigns(setOf(relayPath, callsign), HEARD_LIMIT)
                sendRelayMessage(listOf("$relayPath HEARING").plus(heard).joinToString(" "))
            }
            "GRID?" -> {
                val grid = com.js8call.example.api.Js8Api.myGrid(prefs)
                if (grid.isBlank()) return false
                sendRelayMessage("$relayPath GRID $grid")
            }
            "AGN?" -> {
                val message = lastTxMessage.trimEnd()
                if (message.isBlank()) return false
                sendRelayMessage(message)
            }
            else -> false
        }
    }

    private fun validateRelayChecksum(message: String): Pair<Boolean, String> {
        val trimmed = message.trimStart()
        if (trimmed.length < 4) return false to trimmed
        val checksum = trimmed.takeLast(3).uppercase()
        val body = trimmed.dropLast(4)
        return checksum16Valid(checksum, body) to body
    }

    private fun normalizeRelayPayload(payload: String): Pair<String, Boolean> {
        var trimmed = payload.trimEnd()
        val hasEom = trimmed.endsWith(RELAY_EOM_MARKER)
        if (hasEom) {
            trimmed = trimmed.dropLast(1).trimEnd()
        }
        return trimmed to hasEom
    }

    private fun stripRelayEom(payload: String): String {
        var trimmed = payload.trimEnd()
        if (trimmed.endsWith(RELAY_EOM_MARKER)) {
            trimmed = trimmed.dropLast(1).trimEnd()
        }
        return trimmed
    }

    private fun checksum16Valid(checksum: String, input: String): Boolean {
        val crc = crc16Kermit(input.toByteArray(Charsets.US_ASCII))
        return pack16Bits(crc) == checksum
    }

    private fun crc16Kermit(data: ByteArray): Int {
        var crc = 0x0000
        for (byte in data) {
            var cur = byte.toInt() and 0xFF
            for (i in 0 until 8) {
                val mix = (crc xor cur) and 0x01
                crc = crc ushr 1
                if (mix != 0) {
                    crc = crc xor 0x8408
                }
                cur = cur ushr 1
            }
        }
        return crc and 0xFFFF
    }

    private fun pack16Bits(value: Int): String {
        val alphabet = CHECKSUM_ALPHABET
        val base = CHECKSUM_BASE
        val tmp1 = value / (base * base)
        val tmp2 = (value - (tmp1 * base * base)) / base
        val tmp3 = value % base
        return "${alphabet[tmp1]}${alphabet[tmp2]}${alphabet[tmp3]}"
    }

    /** PC processCommandActivity: a relay is a queued reply, sent after what is on the air. */
    private fun sendRelayMessage(text: String): Boolean {
        if (isListenOnly()) return false
        val callsign = getConfiguredCallsign() ?: return false
        val payload = text.trim()
        if (payload.isEmpty()) return false
        queueAutoReply("$callsign: $payload", null, priority = PRIORITY_NORMAL)
        return true
    }

    private fun sendAutoReply(
        text: String,
        directed: String?,
        submode: Int,
        requireDirected: Boolean = true,
        forceData: Boolean = false
    ) {
        val activeEngine = engine ?: return
        if (isListenOnly()) return
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val callsign = getConfiguredCallsign()
        if (callsign == null) {
            warnMissingCallsign()
            return
        }
        val grid = com.js8call.example.api.Js8Api.myGrid(prefs)

        val payloadText = text.trim()
        val directedCall = directed?.trim().orEmpty().uppercase()
        if (requireDirected && directedCall.isBlank()) return

        prepareEngineForTransmit(activeEngine)
        val ok = activeEngine.transmitMessage(
            text = payloadText,
            myCall = callsign,
            myGrid = grid.take(4), // PC: my_grid().left(4) before buildMessageFrames
            selectedCall = directedCall,
            submode = submode,
            audioFrequencyHz = splitAudioHz(currentTxOffsetHz.toDouble()),
            txDelaySec = configuredTxDelay(),
            forceIdentify = callsign.isNotBlank() && !avoidForcedIdentify(),
            forceData = forceData
        )

        if (ok) {
            Log.i(TAG, "Autoreply queued: to=$directedCall text='$payloadText'")
            updateLastTxMessage(payloadText, directedCall, submode, currentTxOffsetHz.toDouble())
            broadcastTxState(TX_STATE_QUEUED)
            startTxMonitor()
        } else {
            Log.e(TAG, "Autoreply rejected")
            broadcastError("Failed to start transmit")
            broadcastTxState(TX_STATE_FAILED)
        }
    }

    /** PC m_callActivity: a copy of the Call Activity list, so Clear and Remove This Station take a call out. Main thread only. */
    private fun callActivityNow(): List<com.js8call.example.js8ui.Js8Activity.CallDetail> = callActivityList?.invoke().orEmpty()

    /** PC call activity age: Remove callsigns from call activity after:, 0 = never. */
    private fun heardCurrent(utcMs: Long): Boolean {
        val aging = numberPref("js8cfg_callsign_aging", 0.0).toInt()
        return aging == 0 || (System.currentTimeMillis() - utcMs) / 60_000 < aging
    }

    /** PC HEARING?: the newest calls in Call Activity. Main thread only. */
    private fun getRecentHeardCallsigns(exclude: Set<String>, limit: Int): List<String> =
        callActivityNow().filter { it.call !in exclude && heardCurrent(it.utcMs) }
            .sortedByDescending { it.utcMs }.take(limit).map { it.call }

    /** PC QUERY CALL: the call or its base call in Call Activity. */
    private fun heardStation(call: String, calls: List<com.js8call.example.js8ui.Js8Activity.CallDetail>) =
        calls.firstOrNull { heardCurrent(it.utcMs) && (it.call == call || com.js8call.example.js8ui.Js8Activity.baseCall(it.call) == call) }

    /** PC processRxActivity / processCommandActivity: a message to me or my group ends the CQ timer. */
    private fun stopCqForMessage(text: String) {
        if (!CqTimer.on) return
        val my = getConfiguredCallsign() ?: return
        val d = parseDirectedCommand(text) ?: return
        if (isSelfCallsign(my, d.from)) return
        if (!isSelfCallsign(my, d.to) && !isSubscribedGroup(d.to)) return
        CqTimer.on = false
        scheduleCq(true)
    }

    /** PC refuseToSendIn30mWSPRBand: no other mode in the 30 m WSPR guard band. */
    private fun refuseWsprBand(offsetHz: Double): Boolean {
        if (currentDialHz + offsetHz.toLong() !in WSPR_GUARD_HZ) return false
        HeartbeatTimer.on = false
        scheduleHeartbeat(true)
        CqTimer.on = false
        scheduleCq(true)
        broadcastError("Please choose another Tx frequency. The app will not knowingly transmit another mode in the WSPR sub-band on 30m.")
        broadcastTxState(TX_STATE_FAILED)
        return true
    }

    /** PC since(). */
    private fun sinceText(time: Long): String {
        val delta = (System.currentTimeMillis() - time) / 1000
        return when {
            delta >= 86_400 -> "${delta / 86_400}d"
            delta >= 3_600 -> "${delta / 3_600}h"
            delta >= 60 -> "${delta / 60}m"
            delta >= 15 -> "${delta - delta % 15}s"
            else -> "now"
        }
    }

    /** PC isAllCallIncluded. */
    private fun isAllCallTarget(to: String) = to.contains("@ALLCALL") || to.contains("@HB")

    /** PC m_txAllcallCommandCache: one automatic reply to a station's @ALLCALL in 15 minutes. */
    private fun allCallRepliedRecently(from: String): Boolean {
        val call = from.trim().uppercase()
        val last = synchronized(allCallReplies) { allCallReplies[call] } ?: return false
        return System.currentTimeMillis() - last < ALLCALL_REPLY_GAP_MS
    }

    private fun markAllCallReplied(from: String) {
        synchronized(allCallReplies) { allCallReplies[from.trim().uppercase()] = System.currentTimeMillis() }
    }

    private fun extractHeardCallsign(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return null
        val firstToken = trimmed.split(Regex("\\s+"), limit = 2)[0]
        var token = firstToken.trimEnd(':').uppercase()
        if (token.contains(">")) {
            token = token.substringBefore(">").trimEnd(':')
        }
        if (token.startsWith("@")) return null
        if (token in HEARD_EXCLUDE_TOKENS) return null
        return token.takeIf(CallsignValidator::isAmateurCallsign)
    }

    private fun updateLastTxMessage(
        text: String,
        directedCall: String,
        submode: Int,
        frequencyHz: Double
    ) {
        val built = buildTxMessage(JS8Engine.normalizeText(text), directedCall) // the text as sent
        if (built.isNotBlank()) {
            lastTxMessage = built
            lastTxDirected = directedCall.trim().uppercase()
            lastTxSubmode = submode
            lastTxFrequencyHz = frequencyHz
            Js8Api.State.currentMessage = built // PC m_currentMessage (RIG.PTT_STATUS, WSJT-X Status)
        }
    }

    private fun buildTxMessage(text: String, directedCall: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ""
        val selected = directedCall.trim().uppercase()
        if (selected.isEmpty()) return trimmed
        if (trimmed.startsWith("`")) return trimmed

        if (TxMessageClassifier.isBaseMessage(trimmed)) return trimmed
        if (trimmed.startsWith(selected, ignoreCase = true)) return trimmed

        val sep = if (trimmed.startsWith(" ")) "" else " "
        return selected + sep + trimmed
    }

    private fun isRigControlConnected(): Boolean {
        return when (rigControlMode) {
            "network" -> rigCtlConnected
            "hamlib_usb" -> hamlibRigConnected
            "rts_ptt" -> rtsPttConnected
            "trusdx_serial" -> trusdxConnected
            "qmx_serial" -> qmxConnected
            "btech_uvpro" -> uvproConnected
            else -> false
        }
    }

    private fun isRigPttRequired(): Boolean = rigControlMode != "none"

    private fun prepareEngineForTransmit(activeEngine: JS8Engine) {
        activeEngine.setTransmitReady(!isRigPttRequired())
    }

    /** Station > Radio > PTT Method (PC default VOX). truSDX, QMX and UV-Pro key over their own link. */
    private fun pttMethod(): String =
        PreferenceManager.getDefaultSharedPreferences(this).getString("js8cfg_ptt_method", "vox") ?: "vox"

    private fun setRigPtt(enabled: Boolean): Boolean {
        val method = pttMethod()
        return when (rigControlMode) {
            "network" -> method == "vox" || rigCtlClient?.setPtt(enabled, rearAudioSource()) == true
            "hamlib_usb" -> when (method) {
                "vox" -> true // the radio keys itself from the audio
                "dtr", "rts" -> if (pttPortSelected()) {
                    (if (method == "dtr") pttUsbBridge?.setDtr(enabled) else pttUsbBridge?.setRts(enabled)) == true
                } else {
                    setSerialLine(usbSerialBridge, bluetoothSerialBridge, method == "dtr", enabled)
                }
                else -> hamlibRigControl?.setPtt(enabled, rearAudioSource()) == true
            }
            "rts_ptt" -> if (method == "dtr") setSerialLine(usbSerialBridge, bluetoothSerialBridge, true, enabled) else setRtsPtt(enabled)
            "trusdx_serial" -> trusdxSerialSession?.setPtt(enabled) == true
            "qmx_serial" -> qmxCatSession?.setPtt(enabled) == true
            "btech_uvpro" -> uvProSession?.setPtt(enabled) == true
            else -> false
        }
    }

    /** Station > Behavior > Allow sending standard messages without callsign (PC buildMessageFrames). */
    private fun avoidForcedIdentify() = PreferenceManager.getDefaultSharedPreferences(this)
        .all["js8cfg_avoid_forced_identify"]?.toString() == "true"

    /** Station > Radio > Transmit Audio Source (PC back_ptt_port_). */
    private fun rearAudioSource() = PreferenceManager.getDefaultSharedPreferences(this)
        .getString("js8cfg_tx_audio_source", "front") != "front"

    private fun pttPortSelected() = PreferenceManager.getDefaultSharedPreferences(this)
        .getString("js8cfg_ptt_port", "cat").orEmpty().let { it.isNotEmpty() && it != "cat" }

    /** Station > Radio > PTT Port: a second USB serial port just for the DTR / RTS line. */
    private fun openPttPort() {
        if (!pttPortSelected() || pttMethod() !in setOf("dtr", "rts")) return
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val selection = resolveSerialSelection(prefs.getString("js8cfg_ptt_port", "cat").orEmpty(), prefs)
        val deviceId = selection?.usbDeviceId
        val device = deviceId?.let { UsbPermissionHelper.findUsbDeviceById(this, it) }
        if (selection == null || deviceId == null || device == null) {
            broadcastError("PTT Port not found.")
            return
        }
        val open = {
            val params = readSerialParams()
            val bridge = UsbSerialBridge(applicationContext)
            Thread {
                val ok = bridge.open(deviceId, selection.portIndex, params.baudRate, params.dataBits.takeIf { it > 0 } ?: 8, params.stopBits.takeIf { it > 0 } ?: 1, params.parityValue)
                mainHandler.post {
                    if (ok) {
                        bridge.setRts(false)
                        bridge.setDtr(false)
                        pttUsbBridge = bridge
                    } else {
                        broadcastError("Failed to open PTT Port.")
                    }
                }
            }.start()
        }
        if (UsbPermissionHelper.hasPermission(this, device)) {
            open()
        } else {
            UsbPermissionHelper.requestPermission(this, device) { granted ->
                if (granted) open() else broadcastError("USB permission denied for PTT Port.")
            }
        }
    }

    /** DTR or RTS on the serial port the rig is open on (only one of the two bridges has a port). */
    private fun setSerialLine(usb: UsbSerialBridge?, bluetooth: BluetoothSerialBridge?, dtr: Boolean, enabled: Boolean): Boolean {
        val usbOk = if (dtr) usb?.setDtr(enabled) else usb?.setRts(enabled)
        if (usbOk == true) return true
        return (if (dtr) bluetooth?.setDtr(enabled) else bluetooth?.setRts(enabled)) == true
    }

    private fun broadcastRigTest(kind: String, ok: Boolean, on: Boolean, text: String) {
        val intent = Intent(ACTION_RIG_TEST).apply {
            putExtra(EXTRA_RIG_TEST_KIND, kind)
            putExtra(EXTRA_RIG_TEST_OK, ok)
            putExtra(EXTRA_RIG_TEST_ON, on)
            putExtra(EXTRA_RIG_TEST_TEXT, text)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    /** Test CAT: connect again with the saved Radio settings (the rig is only opened at engine start). */
    private fun testCat() {
        val wasRunning = engine != null || engineStartInProgress
        if (isTransmitActive()) {
            broadcastRigTest(RIG_TEST_CAT, false, false, "Can't test the rig while transmitting")
            return
        }
        if (testPttOn) testPtt(false)
        stopEngine()
        updateDialFromPrefs()
        startForegroundService()
        startEngine()
        val deadline = System.currentTimeMillis() + RIG_TEST_TIMEOUT_MS
        mainHandler.post(object : Runnable {
            override fun run() {
                val connected = isRigControlConnected()
                if (!connected && System.currentTimeMillis() < deadline) {
                    mainHandler.postDelayed(this, 250)
                    return
                }
                broadcastRigTest(RIG_TEST_CAT, connected, false, if (connected) "Rig connected" else "Rig did not connect")
                if (!wasRunning) { // RX was off: leave it off
                    stopEngine()
                    stopSelf()
                }
            }
        })
    }

    /** Test PTT: key the radio with no audio; released by the button, leaving the page, or after 30 s. */
    private fun testPtt(on: Boolean) {
        mainHandler.removeCallbacks(testPttRelease)
        val refused = when {
            !on -> null
            isListenOnly() -> "SWL (Listening only) is checked"
            isTransmitActive() -> "Can't test PTT while transmitting"
            !isRigControlConnected() -> "Rig not connected: press Test CAT first"
            else -> null
        }
        if (refused != null) {
            broadcastRigTest(RIG_TEST_PTT, false, false, refused)
            return
        }
        txHandler.post {
            val ok = setRigPtt(on)
            testPttOn = on && ok
            mainHandler.post {
                if (testPttOn) mainHandler.postDelayed(testPttRelease, RIG_TEST_PTT_MAX_MS)
                broadcastRigTest(RIG_TEST_PTT, ok, testPttOn, if (ok) "" else "PTT command failed")
            }
        }
    }

    private fun setRtsPtt(enabled: Boolean): Boolean {
        val transport = rtsPttTransport
        val result = when (transport) {
            SerialTransport.USB -> usbSerialBridge?.setRts(enabled) == true
            SerialTransport.BLUETOOTH -> bluetoothSerialBridge?.setRts(enabled) == true
            else -> false
        }
        if (!result) {
            Log.w(TAG, "RTS PTT toggle failed (transport=$transport)")
        }
        return result
    }

    private val bandHopRunnable = object : Runnable {
        override fun run() {
            tryBandHop()
            heartbeatHandler.postDelayed(this, BAND_HOP_CHECK_MS - System.currentTimeMillis() % BAND_HOP_CHECK_MS)
        }
    }
    private val bandHopTimeout = Runnable { finishScheduledHop(true) }

    /** PC tryBandHop: the latest schedule line whose window holds the time now, unless the operator has moved off it. */
    private fun tryBandHop() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (!prefs.getBoolean("js8cfg_auto_switch_bands", false)) return
        updateDialFromPrefs()
        if (bandHoppedHz != 0L && currentDialHz != bandHoppedHz) bandHopped = false
        val schedule = prefs.getString("js8cfg_schedule", "").orEmpty()
        if (schedule != bandScheduleSeen) { // PC band_schedule_changed
            bandScheduleSeen = schedule
            bandHopped = true
        }
        if (pendingHopHz != 0L || isTransmitActive() || messageQueued) return

        fun minutes(s: String) = s.take(2).toInt() * 60 + s.takeLast(2).toInt()
        val nowMs = System.currentTimeMillis() % 86_400_000L
        val now = nowMs / 60_000.0
        val hz = schedule.split('\n').filter { it.isNotBlank() }.map { it.split('|') }
            .sortedWith(compareBy({ minutes(it[1]) }, { minutes(it[2]) }))
            .lastOrNull {
                val at = minutes(it[1])
                val until = minutes(it[2])
                (at <= now && now <= until) || (until < at && (at <= now || now <= until))
            }?.get(0)?.toLongOrNull() ?: return

        val noOverride = bandHopped || hz != bandHoppedHz
        if (!noOverride || hz == currentDialHz) return
        bandHopped = false
        bandHoppedHz = hz
        pendingHopHz = hz
        pendingHopEndMs = System.currentTimeMillis() + BAND_HOP_COUNTDOWN_MS
        Log.i(TAG, "Scheduled frequency change to $hz Hz in 30 s")
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_SCHEDULE_HOP).putExtra(EXTRA_FREQUENCY_HZ, hz))
        heartbeatHandler.postDelayed(bandHopTimeout, BAND_HOP_COUNTDOWN_MS)
    }

    /** OK, or the 30 seconds ran out (the PC box clicks its default OK). Cancel stays put. */
    private fun finishScheduledHop(ok: Boolean) {
        heartbeatHandler.removeCallbacks(bandHopTimeout)
        val hz = pendingHopHz
        pendingHopHz = 0L
        if (hz == 0L || !ok) return
        bandHopped = true
        applyBandHop(hz)
    }

    private fun applyBandHop(hz: Long) {
        Log.i(TAG, "Frequency changed to $hz Hz by the schedule")
        PreferenceManager.getDefaultSharedPreferences(this).edit().putString("last_frequency", hz.toString()).apply()
        currentDialHz = hz
        setFrequency(hz)
        broadcastRadioFrequency(hz)
    }

    private fun setRigFrequency(frequencyHz: Long): Boolean = when (rigControlMode) {
        "network" -> rigCtlClient?.setFrequency(applyCalibration(frequencyHz)) == true
        "hamlib_usb" -> hamlibRigControl?.setFrequency(applyCalibration(frequencyHz)) == true
        "trusdx_serial" -> trusdxSerialSession?.setFrequency(applyCalibration(frequencyHz)) == true
        "qmx_serial" -> qmxCatSession?.setFrequency(applyCalibration(frequencyHz)) == true
        else -> false
    }

    /** PC Configuration apply_calibration: the frequency sent to the rig. */
    private fun applyCalibration(hz: Long): Long = Math.round(
        numberPref("js8cfg_cal_intercept", 0.0) + (1.0 + numberPref("js8cfg_cal_slope", 0.0) / 1e6) * hz)

    /** PC Configuration remove_calibration: the frequency read from the rig. */
    private fun removeCalibration(hz: Long): Long = Math.round(
        (hz - numberPref("js8cfg_cal_intercept", 0.0)) / (1.0 + numberPref("js8cfg_cal_slope", 0.0) / 1e6))

    /** PC on_readFreq_clicked (CAT button): read the rig again, unless transmitting. */
    private fun readRig() {
        if (rigFailed || isTransmitActive() || !isRigControlConnected()) return
        when (rigControlMode) {
            "qmx_serial" -> catReadPending = qmxCatSession?.readFrequency() == true
            "trusdx_serial" -> catReadPending = trusdxSerialSession?.readFrequency() == true
            "network", "hamlib_usb" -> Thread {
                val hz = if (rigControlMode == "network") rigCtlClient?.getFrequency() else hamlibRigControl?.getFrequency()
                val state = if ((hz ?: 0L) <= 0L) null else if (rigControlMode == "network") rigCtlClient?.readState() else hamlibState()
                mainHandler.post {
                    state?.let { onRigState(it) }
                    if ((hz ?: 0L) > 0L) onRigRead(hz ?: 0L)
                    else if (!rigFailed && isRigControlConnected() && !isTransmitActive()) rigFailure("The rig did not answer a frequency read")
                }
            }.start()
        }
    }

    private fun hamlibState(): NetworkRig.State {
        val p = hamlibRigControl?.readState().orEmpty().split('|')
        return NetworkRig.State(
            vfo = p.getOrNull(0)?.ifEmpty { null },
            split = p.getOrNull(1)?.ifEmpty { null }?.let { it == "1" },
            txHz = p.getOrNull(2)?.toLongOrNull(),
            mode = p.getOrNull(3)?.ifEmpty { null },
            ptt = p.getOrNull(4)?.ifEmpty { null }?.let { it == "1" }
        )
    }

    /** PC HamlibTransceiver::poll: what the rig says. The split shows on the CAT button (PC onRigStateChanged). */
    private fun onRigState(state: NetworkRig.State) {
        if (state != rigState) Log.i(TAG, "Rig state: $state")
        rigState = state
        val split = state.split ?: return
        if (split != rigSplit) {
            rigSplit = split
            broadcastRigStatus(isRigControlConnected())
        }
    }

    /** PC rigFailure: stop sending, one automatic retry, then the Rig Control Error box. */
    private fun rigFailure(reason: String) {
        rigFailed = true
        rigOpening = false
        startService(Intent(this, JS8EngineService::class.java).setAction(ACTION_HALT_TX))
        if (rigFirstError) {
            rigFirstError = false
            rigOpen()
            return
        }
        rigFirstError = true
        pendingRigError = reason
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_RIG_ERROR))
    }

    private fun rigOpen() {
        rigFailed = false
        initializeRigControl(engineStartGeneration)
    }

    /** A frequency read from the rig becomes the dial (PC onRigStateChanged). */
    private fun onRigRead(rigHz: Long) {
        catReadPending = false
        if (rigHz <= 0L || splitHeld != "none") return
        val hz = removeCalibration(rigHz)
        if (hz == currentDialHz) return
        currentDialHz = hz
        PreferenceManager.getDefaultSharedPreferences(this).edit().putString("last_frequency", hz.toString()).apply()
        broadcastRadioFrequency(hz)
        dialChanged()
    }

    /** PC onRigStateChanged: the dial moved. */
    private fun dialChanged() {
        // PC onRigStateChanged: frequency line in ALL.TXT, local station sent again to the spotting networks
        messageLogger?.logFrequency(driftedNowMs(), currentDialHz)
        if (pskReporterEnabled) updatePskReporterState()
        if (engine != null) { // PC: if (m_monitoring) m_lastMonitoredFrequency = m_freqNominal
            PreferenceManager.getDefaultSharedPreferences(this).edit().putLong(PREF_LAST_MONITORED, currentDialHz).apply()
        }
        bandChanged()
    }

    /** PC on_monitorButton_clicked: RX on with "Monitor returns to last used frequency" puts the dial back. */
    private fun monitorLastUsed() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val last = prefs.getLong(PREF_LAST_MONITORED, 0L)
        if (prefs.getBoolean("js8cfg_monitor_last_used", false) && last > 0L && last != currentDialHz) {
            prefs.edit().putString("last_frequency", last.toString()).apply()
            currentDialHz = last
            broadcastRadioFrequency(last)
        }
        if (currentDialHz > 0L) prefs.edit().putLong(PREF_LAST_MONITORED, currentDialHz).apply()
    }

    /** Rig just opened at RX on: PC setRig(m_lastMonitoredFrequency). truSDX and QMX tune at open anyway. */
    private fun rigOnline() {
        rigFirstError = true
        if (currentDialHz > 0L && PreferenceManager.getDefaultSharedPreferences(this).getBoolean("js8cfg_monitor_last_used", false)) {
            setFrequency(currentDialHz)
        }
    }

    // ---- Power (PC outAttenuation and Remember power settings by band) ------------------------

    private fun outAttenuation() = numberPref(PREF_OUT_ATTENUATION, 0.0).toInt().coerceIn(0, 450) // PC default 0

    /** PC on_outAttenuation_valueChanged: a / 10 dB (SoundOutput::setAttenuation), Output channel. */
    private fun applyTxOutput() {
        val channel = listOf("Mono", "Left", "Right", "Both").indexOf(
            PreferenceManager.getDefaultSharedPreferences(this).getString("js8cfg_output_channel", "Mono")).coerceAtLeast(0)
        engine?.setTxOutput(Math.pow(10.0, -outAttenuation() / 10.0 / 20.0).toFloat(), channel)
    }

    private fun onOutAttenuationChanged() {
        if (pwrBandSetOk) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            if (!tuneOn && prefs.getBoolean("js8cfg_pwr_band_tx", false)) rememberPower(PREF_PWR_TX_MEMORY, outAttenuation())
            if (tuneOn && prefs.getBoolean("js8cfg_pwr_band_tune", false)) rememberPower(PREF_PWR_TUNE_MEMORY, outAttenuation())
        }
        applyTxOutput()
    }

    /** "20m=100,40m=90" (PC QVariantHash m_pwrBandTxMemory / m_pwrBandTuneMemory). */
    private fun powerMemory(key: String): Map<String, Int> =
        PreferenceManager.getDefaultSharedPreferences(this).getString(key, "").orEmpty().split(',')
            .mapNotNull { e -> e.split('=').takeIf { it.size == 2 }?.let { p -> p[1].toIntOrNull()?.let { p[0] to it } } }.toMap()

    private fun rememberPower(key: String, attenuation: Int) {
        val text = (powerMemory(key) + (pwrLastBand to attenuation)).entries.joinToString(",") { "${it.key}=${it.value}" }
        PreferenceManager.getDefaultSharedPreferences(this).edit().putString(key, text).apply()
    }

    private fun setOutAttenuation(attenuation: Int) {
        PreferenceManager.getDefaultSharedPreferences(this).edit().putString(PREF_OUT_ATTENUATION, attenuation.toString()).apply()
    }

    /** Service starting: the Power slider may have moved while nothing listened. Same band = keep it. */
    private fun startPowerMemory() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        pwrLastBand = prefs.getString(PREF_PWR_LAST_BAND, "").orEmpty()
        if (pwrLastBand == bandName() && prefs.getBoolean("js8cfg_pwr_band_tx", false)) rememberPower(PREF_PWR_TX_MEMORY, outAttenuation())
        bandChanged()
    }

    private fun bandName() = com.js8call.example.util.BandUtils.bandLabelForFrequencyHz(currentDialHz)

    /** PC band_changed. */
    private fun bandChanged() {
        val band = bandName()
        if (currentDialHz <= 0L || band == pwrLastBand) return
        pwrLastBand = band
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.edit().putString(PREF_PWR_LAST_BAND, band).apply()
        if (prefs.getBoolean("js8cfg_pwr_band_tx", false) && !tuneOn) {
            val remembered = powerMemory(PREF_PWR_TX_MEMORY)[band]
            if (remembered != null) setOutAttenuation(remembered) else rememberPower(PREF_PWR_TX_MEMORY, outAttenuation())
        }
    }

    /** PC on_tuneButton_clicked, starting: remember the Tx power, go to the Tune power. */
    private fun startTuning() {
        if (tuneOn) return
        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("js8cfg_pwr_band_tune", false)) {
            rememberPower(PREF_PWR_TX_MEMORY, outAttenuation())
            pwrBandSetOk = false
            powerMemory(PREF_PWR_TUNE_MEMORY)[pwrLastBand]?.let { setOutAttenuation(it) }
            pwrBandSetOk = true
        }
        tuneOn = true
    }

    /** PC end_tuning: remember the Tune power, back to the Tx power. */
    private fun endTuning() {
        if (!tuneOn) return
        tuneOn = false
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (prefs.getBoolean("js8cfg_pwr_band_tune", false) || prefs.getBoolean("js8cfg_pwr_band_tx", false)) {
            rememberPower(PREF_PWR_TUNE_MEMORY, outAttenuation())
            pwrBandSetOk = false
            powerMemory(PREF_PWR_TX_MEMORY)[pwrLastBand]?.let { setOutAttenuation(it) }
            pwrBandSetOk = true
        }
    }

    /**
     * Station > Rig Options > Split Operation for this rig. Rig needs a split command, which
     * only the network and Hamlib links have; truSDX and QMX use Fake It. Radios the app can't
     * tune, or an unknown dial, stay on None (PC: split_mode() false with no rig).
     */
    private fun splitMode(): String {
        val mode = PreferenceManager.getDefaultSharedPreferences(this).getString("js8cfg_split", "none") ?: "none"
        if (currentDialHz <= 0L) return "none"
        return when (rigControlMode) {
            "network", "hamlib_usb" -> mode
            "trusdx_serial", "qmx_serial" -> if (mode == "none") "none" else "emulate"
            else -> "none"
        }
    }

    /** PC setXIT: audio stays in 1500-2000 Hz and the TX dial moves by the rest. */
    private fun splitAudioHz(offsetHz: Double): Double {
        splitXitHz = if (splitMode() == "none") 0 else (offsetHz.toInt() / 500) * 500 - 1500
        if (splitXitHz != 0) Log.i(TAG, "Split: offset $offsetHz audio ${offsetHz - splitXitHz} XIT $splitXitHz")
        return offsetHz - splitXitHz
    }

    /** Station > Radio > Mode as a rig mode, null for None (PC: leave the TX VFO's mode alone). */
    private fun txRigMode(): String? = when (PreferenceManager.getDefaultSharedPreferences(this).getString(PREF_TRANSMIT_MODE, "none")) {
        "usb" -> RIG_MODE_USB
        "usb_data" -> RIG_MODE_PKTUSB
        else -> null
    }

    /** Runs on txHandler just before key-on. */
    private fun applySplit(): Boolean {
        val mode = splitMode()
        val txHz = currentDialHz + splitXitHz
        val ok = when (mode) {
            "emulate" -> setRigFrequency(txHz) // PC EmulateSplitTransceiver
            "rig" -> when (rigControlMode) {
                "network" -> rigCtlClient?.setSplit(applyCalibration(txHz), true, txRigMode()) == true
                else -> (hamlibRigControl?.setSplit(applyCalibration(txHz), true) == true).also { on ->
                    if (on) txRigMode()?.let { hamlibRigControl?.setSplitMode(it) } // a rig without it stays as it is
                }
            }
            else -> {
                // Split Operation changed away from Rig: the PC sends tx_frequency 0 at that moment
                if (rigSplitLeftOn) when (rigControlMode) {
                    "network" -> rigCtlClient?.setSplit(0, false)
                    "hamlib_usb" -> hamlibRigControl?.setSplit(0, false)
                }
                rigSplitLeftOn = false
                true
            }
        }
        if (ok) splitHeld = mode
        if (ok && mode == "rig") rigSplitLeftOn = true
        return ok
    }

    /** Runs on txHandler after key-off. Takes references because shutdown has already cleared the fields. */
    private fun clearSplit(
        mode: String,
        dialHz: Long,
        network: NetworkRig?,
        hamlib: HamlibRigControl?,
        trusdx: TruSdxSerialSession?,
        qmx: QmxCatSession?
    ) {
        val held = splitHeld
        splitHeld = "none"
        when (held) {
            "emulate" -> when (mode) {
                "network" -> network?.setFrequency(applyCalibration(dialHz))
                "hamlib_usb" -> hamlib?.setFrequency(applyCalibration(dialHz))
                "trusdx_serial" -> trusdx?.setFrequency(applyCalibration(dialHz))
                "qmx_serial" -> qmx?.setFrequency(applyCalibration(dialHz))
            }
            "rig" -> {} // PC: split stays on the rig after key-off
        }
    }

    private fun setFrequency(frequencyHz: Long) {
        if (splitHeld == "emulate") return // key-off tunes to currentDialHz
        if (!isRigControlConnected()) {
            Log.d(TAG, "Cannot set frequency: rig control not connected")
            // A configured rig with no link is a failure to report; with rig
            // control off the pick only stores the dial value, so stay quiet
            if (!rigCtlErrorShown && rigControlMode != "none" && rigControlMode != "rts_ptt") {
                broadcastError("Rig control is not connected")
                rigCtlErrorShown = true
            }
            return
        }

        // Run on background thread
        Thread {
            val success = setRigFrequency(frequencyHz)

            mainHandler.post {
                if (success) {
                    Log.i(TAG, "Frequency set to $frequencyHz Hz")
                } else {
                    val detail = when (rigControlMode) {
                        "hamlib_usb" -> hamlibRigControl?.getLastError().orEmpty()
                        "rts_ptt" -> "RTS PTT does not support frequency control"
                        "trusdx_serial" -> "TruSDX serial CAT command failed"
                        "qmx_serial" -> "QMX CAT command failed"
                        "btech_uvpro" -> "UV-Pro frequency is set on the radio"
                        else -> ""
                    }
                    if (detail.isNotBlank()) {
                        Log.w(TAG, "Failed to set frequency to $frequencyHz Hz: $detail")
                    } else {
                        Log.w(TAG, "Failed to set frequency to $frequencyHz Hz")
                    }
                    if (!rigCtlErrorShown && rigControlMode != "rts_ptt" && rigControlMode != "btech_uvpro") {
                        if (detail.isNotBlank()) {
                            broadcastError("Rig control failed: $detail")
                        } else {
                            broadcastError("Rig control communication failed")
                        }
                        rigCtlErrorShown = true
                    }
                }
            }
        }.start()
    }

    companion object {
        // Shared by service instances: stopping monitoring destroys the service
        // while a UV-Pro worker may still hold the radio's sockets.
        private val uvproWorkersActive = AtomicInteger(0)

        /** PC read_settings runs once at start: the first service start in this process. */
        @Volatile private var settingsLoaded = false

        private const val TAG = "JS8EngineService"
        private const val PREF_AUTOREPLY_ENABLED = "autoreply_enabled"
        private const val PREF_RELAY_ENABLED = "relay_enabled"
        private const val PREF_TX_SUBMODE = "tx_submode"
        private const val PREF_MY_INFO = "my_info"
        private const val PREF_MY_STATUS = "my_status"
        private const val PREF_PSK_REPORTER = "psk_reporter"
        private const val SPOT_HOST = "spot.js8call.com"
        private const val SPOT_PORT = 50000
        // PC Varicode buffered_cmds / checksum_cmds: all checksummed except 15
        private val SPOT_BUFFERED_COMMANDS = setOf(5, 9, 10, 11, 12, 13, 15, 24)
        private const val SPOT_NO_CHECKSUM_COMMAND = 15
        private const val FRAME_FIRST = 1
        /** The main screen's Typing Box has nothing in it. */
        @Volatile var typingBoxEmpty = true
        private const val BUFFER_CLOSE_MS = 60_000L
        private const val BUFFER_DROP_MS = 90_000L
        private const val PREF_TRUSDX_DIAGNOSTICS_ENABLED = "trusdx_diagnostics_enabled"
        private const val HEARD_LIMIT = 4
        private val HEARD_EXCLUDE_TOKENS = setOf("CQ", "HB", "HEARTBEAT", "ALLCALL", "@ALLCALL")
        private const val RELAY_EOM_MARKER = "\u2662"
        private const val SUBMODE_NORMAL = 0
        private const val SUBMODE_FAST = 1
        private const val SUBMODE_TURBO = 2
        private const val SUBMODE_SLOW = 4
        private const val SUBMODE_ULTRA = 8
        // Decoder bits: 1 << SubmodeId (core/include/js8core/protocol/submode.hpp)
        private const val RX_BIT_NORMAL = 1
        private const val RX_BIT_FAST = 2
        private const val RX_BIT_TURBO = 4
        private const val RX_BIT_SLOW = 8
        private const val RX_BIT_ULTRA = 16
        private const val RX_BITS_ALL = RX_BIT_NORMAL or RX_BIT_FAST or RX_BIT_TURBO or RX_BIT_SLOW or RX_BIT_ULTRA

        // Heartbeat sub-band, matching the desktop app
        private const val HB_SUBBAND_LOW_HZ = 500
        private const val HB_SUBBAND_HIGH_HZ = 1000
        private const val HB_SLOT_HZ = 50
        private val WSPR_GUARD_HZ = 10_139_900L..10_140_320L

        // Outgoing HB ACK: "MYCALL: TARGET HEARTBEAT SNR +NN"
        private val heartbeatAckTxRegex =
            Regex("^\\s*[A-Z0-9/]+:\\s+\\S+\\s+HEARTBEAT\\s+SNR\\b", RegexOption.IGNORE_CASE)
        private const val CHECKSUM_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ+-./?"
        private const val CHECKSUM_BASE = 41

        // Actions
        const val ACTION_START = "com.js8call.example.ACTION_START"
        const val ACTION_STOP = "com.js8call.example.ACTION_STOP"
        const val ACTION_SWITCH_AUDIO_DEVICE = "com.js8call.example.ACTION_SWITCH_AUDIO_DEVICE"
        const val ACTION_SET_FREQUENCY = "com.js8call.example.ACTION_SET_FREQUENCY"
        const val ACTION_SET_TX_OFFSET = "com.js8call.example.ACTION_SET_TX_OFFSET"
        const val ACTION_ENGINE_STATE = "com.js8call.example.ACTION_ENGINE_STATE"
        const val ACTION_DECODE = "com.js8call.example.ACTION_DECODE"
        const val ACTION_SPECTRUM = "com.js8call.example.ACTION_SPECTRUM"
        const val ACTION_DECODE_STARTED = "com.js8call.example.ACTION_DECODE_STARTED"
        const val ACTION_DECODE_FINISHED = "com.js8call.example.ACTION_DECODE_FINISHED"
        const val ACTION_AUDIO_DEVICE = "com.js8call.example.ACTION_AUDIO_DEVICE"
        const val ACTION_AUDIO_DEVICE_LOST = "com.js8call.example.ACTION_AUDIO_DEVICE_LOST"
        const val ACTION_ERROR = "com.js8call.example.ACTION_ERROR"
        const val ACTION_TRANSMIT_MESSAGE = "com.js8call.example.ACTION_TRANSMIT_MESSAGE"
        const val ACTION_TX_STATE = "com.js8call.example.ACTION_TX_STATE"
        /** A part of the message being sent just started going out (text, index, count, chars sent, offset). */
        const val ACTION_TX_FRAME = "com.js8call.example.ACTION_TX_FRAME"
        /** FT8 signal decoded in the JS8 audio (slot UTC ms, freq, snr, text, transmitting call). */
        const val ACTION_FT8_DECODED = "com.js8call.example.ACTION_FT8_DECODED"
        const val EXTRA_FT8_UTC_MS = "ft8_utc_ms"
        const val EXTRA_FT8_FREQ = "ft8_freq"
        const val EXTRA_FT8_SNR = "ft8_snr"
        const val EXTRA_FT8_TEXT = "ft8_text"
        const val EXTRA_FT8_CALL_DE = "ft8_call_de"
        const val PREF_SHOW_FT8 = "js8cfg_show_ft8"
        /** Settings > Power: PC outAttenuation, 0-450, a / 10 dB. */
        const val PREF_OUT_ATTENUATION = "js8cfg_out_attenuation"
        private const val PREF_PWR_TX_MEMORY = "js8cfg_pwr_band_tx_memory"
        private const val PREF_PWR_TUNE_MEMORY = "js8cfg_pwr_band_tune_memory"
        private const val PREF_PWR_LAST_BAND = "js8cfg_pwr_last_band"
        private const val PREF_LAST_MONITORED = "js8cfg_last_monitored_hz"
        const val EXTRA_TX_FRAME_TEXT = "tx_frame_text"
        const val EXTRA_TX_FRAME_INDEX = "tx_frame_index"
        const val EXTRA_TX_FRAME_COUNT = "tx_frame_count"
        const val EXTRA_TX_FRAME_CHARS_SENT = "tx_frame_chars_sent"
        const val EXTRA_TX_FRAME_FULL_TEXT = "tx_frame_full_text"
        const val EXTRA_TX_FRAME_IS_DATA = "tx_frame_is_data"
        const val ACTION_RADIO_FREQUENCY = "com.js8call.example.ACTION_RADIO_FREQUENCY"
        const val ACTION_MESSAGE_RECEIVED = "com.js8call.example.ACTION_MESSAGE_RECEIVED"
        const val ACTION_QUEUE_TX = "com.js8call.example.ACTION_QUEUE_TX"
        const val ACTION_APRS_NOTICE = "com.js8call.example.ACTION_APRS_NOTICE"
        /** Set by MainActivity: last heard time of a call in Call Activity, read on the main thread. */
        @Volatile var callHeardMs: ((String) -> Long?)? = null
        /** Set by MainActivity: a copy of Call Activity, read on the main thread. */
        @Volatile var callActivityList: (() -> List<com.js8call.example.js8ui.Js8Activity.CallDetail>)? = null
        const val ACTION_TIME_SYNC_ONCE = "com.js8call.example.ACTION_TIME_SYNC_ONCE"
        const val ACTION_SET_TIME_DRIFT = "com.js8call.example.ACTION_SET_TIME_DRIFT"
        const val ACTION_TIME_DRIFT = "com.js8call.example.ACTION_TIME_DRIFT"
        /** Start / stop Automatic Time Drift (EXTRA_AUTO_DRIFT_ON, EXTRA_AUTO_DRIFT_STOP_AFTER; 0 = no stop). */
        const val ACTION_SET_AUTO_DRIFT = "com.js8call.example.ACTION_SET_AUTO_DRIFT"
        private const val KEY_AUTO_DRIFT_ON = "js8cfg_auto_drift_on"
        private const val KEY_AUTO_DRIFT_STOP_AFTER = "js8cfg_auto_drift_stop_after_run"
        private const val KEY_AUTO_DRIFT_LEFT = "js8cfg_auto_drift_left"
        /** Broadcast: Automatic Time Drift started or stopped (EXTRA_AUTO_DRIFT_ON). */
        const val ACTION_AUTO_DRIFT = "com.js8call.example.ACTION_AUTO_DRIFT"
        const val EXTRA_AUTO_DRIFT_ON = "auto_drift_on"
        const val EXTRA_AUTO_DRIFT_STOP_AFTER = "auto_drift_stop_after"
        /** Display Decode Attempts on / off (EXTRA_SYNC_STATS_ON). */
        const val ACTION_SET_SYNC_STATS = "com.js8call.example.ACTION_SET_SYNC_STATS"
        private val DECODE_RANGE_PREFS = setOf(
            "js8cfg_filter_enabled", "js8cfg_filter_center", "js8cfg_filter_width", "js8cfg_tx_offset"
        )
        const val EXTRA_SYNC_STATS_ON = "sync_stats_on"
        const val ACTION_RIG_STATUS = "com.js8call.example.ACTION_RIG_STATUS"
        const val ACTION_TEST_CAT = "com.js8call.example.ACTION_TEST_CAT"
        const val ACTION_READ_RIG = "com.js8call.example.ACTION_READ_RIG"
        const val ACTION_RIG_RETRY = "com.js8call.example.ACTION_RIG_RETRY"
        const val ACTION_RIG_ERROR = "com.js8call.example.ACTION_RIG_ERROR"
        const val ACTION_TEST_PTT = "com.js8call.example.ACTION_TEST_PTT"
        const val ACTION_RIG_TEST = "com.js8call.example.ACTION_RIG_TEST"
        const val EXTRA_RIG_TEST_KIND = "rig_test_kind"
        const val EXTRA_RIG_TEST_OK = "rig_test_ok"
        const val EXTRA_RIG_TEST_ON = "rig_test_on"
        const val EXTRA_RIG_TEST_TEXT = "rig_test_text"
        const val RIG_TEST_CAT = "cat"
        const val RIG_TEST_PTT = "ptt"
        private const val RIG_TEST_TIMEOUT_MS = 15_000L
        private const val RIG_TEST_PTT_MAX_MS = 30_000L
        const val ACTION_DEBUG_INJECT_DECODE = "com.js8call.example.ACTION_DEBUG_INJECT_DECODE"
        const val ACTION_DEBUG_FT8_WAV = "com.js8call.example.ACTION_DEBUG_FT8_WAV"
        const val ACTION_DEBUG_JS8_PCM = "com.js8call.example.ACTION_DEBUG_JS8_PCM"
        const val ACTION_DEBUG_TX_FRAME = "com.js8call.example.ACTION_DEBUG_TX_FRAME"
        /** What the rig last said about split, null until a poll could read it (CAT button: CAT/S). */
        @Volatile var rigSplit: Boolean? = null
        const val ACTION_TX_TYPEAHEAD = "com.js8call.example.ACTION_TX_TYPEAHEAD"
        const val EXTRA_TYPEAHEAD_TEXT = "typeahead_text"
        const val EXTRA_TYPEAHEAD_FRAMES = "typeahead_frames"
        const val ACTION_HALT_TX = "com.js8call.example.ACTION_HALT_TX"
        const val ACTION_TUNE = "com.js8call.example.ACTION_TUNE"
        const val ACTION_SEND_HB = "com.js8call.example.ACTION_SEND_HB"
        const val ACTION_ERASE_LOG = "com.js8call.example.ACTION_ERASE_LOG"
        const val ACTION_SCHEDULE_HOP = "com.js8call.example.ACTION_SCHEDULE_HOP"
        const val ACTION_SCHEDULE_HOP_ANSWER = "com.js8call.example.ACTION_SCHEDULE_HOP_ANSWER"
        const val ACTION_SCHEDULE_HOP_NOW = "com.js8call.example.ACTION_SCHEDULE_HOP_NOW"
        const val EXTRA_SCHEDULE_HOP_OK = "schedule_hop_ok"
        /** Waiting out the Scheduled Frequency Change countdown; 0 = none. */
        @Volatile var pendingHopHz = 0L
        @Volatile var pendingHopEndMs = 0L
        const val ACTION_IDLE_TIMEOUT = "com.js8call.example.ACTION_IDLE_TIMEOUT"
        const val ACTION_IDLE_OK = "com.js8call.example.ACTION_IDLE_OK"
        private const val PREF_HB_TIMES = "js8cfg_hb_rate_times"
        /** Last touch or key on any screen (PC eventFilter resetIdleTimer). */
        @Volatile var lastUserMs = System.currentTimeMillis()
        /** Non-zero: timed out after this many minutes, waiting for the Idle Timeout box's OK. */
        @Volatile var idleTimedOutMinutes = 0L
        const val ACTION_AUTOREPLY_CONFIRM = "com.js8call.example.ACTION_AUTOREPLY_CONFIRM"
        const val ACTION_AUTOREPLY_CONFIRM_ANSWER = "com.js8call.example.ACTION_AUTOREPLY_CONFIRM_ANSWER"
        const val EXTRA_CONFIRM_ID = "confirm_id"
        const val EXTRA_CONFIRM_ACCEPTED = "confirm_accepted"
        const val EXTRA_CONFIRM_FROM_API = "confirm_from_api"
        const val AUTOREPLY_CONFIRM_SECONDS = 90
        private var nextConfirmId = 1

        /** An autoreply waiting for its Yes / No (PC PendingConfirmation). */
        class AutoReplyConfirm(val id: Int, val text: String, val directed: String?, val priority: Int, val onAccepted: (() -> Unit)? = null) {
            val endMs = System.currentTimeMillis() + AUTOREPLY_CONFIRM_SECONDS * 1000L
        }
        val autoReplyConfirms = java.util.concurrent.CopyOnWriteArrayList<AutoReplyConfirm>()

        /** A message is queued or going out (the queue lives in TransmitViewModel). */
        @Volatile var messageQueued = false
        /** Dial when the running decode started (PC m_decoderBusyFreq). */
        @Volatile var decodeDialHz = 0L
        /** Reason waiting for the Rig Control Error box, kept until the main screen can show it. */
        @Volatile var pendingRigError: String? = null
        private const val BAND_HOP_CHECK_MS = 15_000L
        private const val BAND_HOP_COUNTDOWN_MS = 30_000L
        const val ACTION_HB_TIMER = "com.js8call.example.ACTION_HB_TIMER"
        /** CQ button timer; on / off in EXTRA_HB_TIMER_ON. */
        const val ACTION_CQ_TIMER = "com.js8call.example.ACTION_CQ_TIMER"
        const val EXTRA_HB_TIMER_ON = "hb_timer_on"
        /** Commands the mailbox answers (PC processCommandActivity). */
        private val APRS_PREFS = setOf(PREF_PSK_REPORTER, "aprs_is", "js8cfg_aprs_relay", "js8cfg_aprs_server", "js8cfg_aprs_port")
        private val APRS_SPOT_COMMANDS = setOf(Js8Commands.CMD_APRS_GRID, Js8Commands.CMD_APRS_CMD)
        private const val APRS_DEDUPE_MS = 120_000L
        private const val APRS_RELAY_PRIORITY = 1000
        private const val PRIORITY_NORMAL = 100
        private const val PRIORITY_HB = 11
        private val aprsGridRegex = Regex("\\b[A-X]{2}[0-9]{2}(?:[A-X]{2}(?:[0-9]{2})?)*\\b")
        private val MAILBOX_COMMANDS = setOf(Js8Commands.CMD_MSG_TO, Js8Commands.CMD_QUERY, Js8Commands.CMD_QUERY_MSGS, "QUERY MSGS?", Js8Commands.CMD_QUERY_CALL)
        private const val ALLCALL_REPLY_GAP_MS = 15 * 60 * 1000L
        const val EXTRA_TUNE_ON = "tune_on"

        // Engine states
        const val STATE_STOPPED = "stopped"
        const val STATE_STARTING = "starting"
        const val STATE_RUNNING = "running"
        const val STATE_ERROR = "error"

        // Extras
        const val EXTRA_STATE = "state"
        const val EXTRA_RIG_CONNECTED = "rig_connected"
        const val EXTRA_RIG_OPENING = "rig_opening"
        const val EXTRA_UTC = "utc"
        const val EXTRA_SNR = "snr"
        const val EXTRA_DT = "dt"
        const val EXTRA_FREQ = "freq"
        const val EXTRA_TEXT = "text"
        const val EXTRA_TYPE = "type"
        const val EXTRA_QUALITY = "quality"
        const val EXTRA_MODE = "mode"
        const val EXTRA_DRIFT_MS = "drift_ms"
        const val EXTRA_TIME_DRIFT_MS = "time_drift_ms"
        const val EXTRA_BINS = "bins"
        const val EXTRA_BIN_HZ = "bin_hz"
        const val EXTRA_POWER_DB = "power_db"
        const val EXTRA_PEAK_DB = "peak_db"
        const val EXTRA_SUBMODES = "submodes"
        const val EXTRA_COUNT = "count"
        const val EXTRA_AUDIO_DEVICE = "audio_device"
        const val EXTRA_AUDIO_DEVICE_ID = "audio_device_id"
        const val EXTRA_ERROR_MESSAGE = "error_message"
        const val EXTRA_FREQUENCY_HZ = "frequency_hz"
        const val EXTRA_TX_TEXT = "tx_text"
        const val EXTRA_TX_DIRECTED = "tx_directed"
        const val EXTRA_TX_SUBMODE = "tx_submode"
        const val EXTRA_TX_FREQ_HZ = "tx_freq_hz"
        const val EXTRA_TX_OFFSET_HZ = "tx_offset_hz"
        const val EXTRA_TX_DELAY_S = "tx_delay_s"
        const val EXTRA_TX_FORCE_IDENTIFY = "tx_force_identify"
        const val EXTRA_TX_FORCE_DATA = "tx_force_data"
        const val EXTRA_TX_STATE = "tx_state"
        const val EXTRA_RADIO_FREQUENCY_HZ = "radio_frequency_hz"
        const val EXTRA_MESSAGE_FROM = "message_from"
        const val EXTRA_MESSAGE_TEXT = "message_text"
        const val EXTRA_MESSAGE_SNR = "message_snr"
        const val EXTRA_MESSAGE_FREQ = "message_freq"
        const val EXTRA_MESSAGE_RELAY_PATH = "message_relay_path"
        const val EXTRA_MESSAGE_CONVERSATION_ID = "message_conversation_id"
        const val EXTRA_QUEUE_TX_TEXT = "queue_tx_text"
        const val EXTRA_QUEUE_TX_DIRECTED = "queue_tx_directed"
        const val EXTRA_QUEUE_TX_PRIORITY = "queue_tx_priority"
        const val PREF_TRANSMIT_MODE = "transmit_mode"
        const val PREF_HEARTBEAT_INTERVAL = "heartbeat_interval"
        const val PREF_TIME_SYNC_AUTO = "time_sync_auto"
        const val PREF_TIME_DRIFT_MS = "time_drift_ms"
        const val PREF_LOG_MESSAGES = "log_messages_to_file"
        /** Station > SWL (Listening only). */
        const val PREF_SWL = "js8cfg_swl"
        /** Settings > Operation: MULTI (default on), HB and ACK (default off), as the PC Mode menu. */
        const val PREF_MULTI = "js8cfg_multi"
        const val PREF_HB = "js8cfg_hb"
        const val PREF_HB_ACK = "js8cfg_hb_ack"
        const val RIG_MODE_USB = "USB"
        const val RIG_MODE_PKTUSB = "PKTUSB"

        const val TX_STATE_QUEUED = "queued"
        const val TX_STATE_STARTED = "started"
        const val TX_STATE_FINISHED = "finished"
        const val TX_STATE_FAILED = "failed"

        const val DEFAULT_AUDIO_FREQUENCY_HZ = 1500.0
        const val TRUSDX_RX_SAMPLE_RATE_HZ = 7812
        const val TRUSDX_TX_SAMPLE_RATE_HZ = 11520
        const val TRUSDX_AUDIO_SERIAL_ID = -2001
        const val TRUSDX_AUDIO_SPEAKER_ID = -2002
        const val TRUSDX_AUDIO_SERIAL_NAME = "TruSDX Serial"
        const val TRUSDX_AUDIO_SPEAKER_NAME = "TruSDX Speaker"
        const val UVPRO_AUDIO_ID = -2003
        const val UVPRO_AUDIO_NAME = "UV-Pro Bluetooth"
        private const val TRUSDX_RX_FRAME_QUEUE_MAX = 512
        private const val TRUSDX_RX_WATCHDOG_INTERVAL_MS = 1200L
        private const val TRUSDX_RX_STALL_REARM_NS = 2_000_000_000L
        private const val TRUSDX_RX_REARM_COOLDOWN_NS = 2_500_000_000L
        private const val TRUSDX_RX_KEEPALIVE_INTERVAL_MS = 2000L
        private const val PTT_COMMAND_RETRIES = 1
        private const val TX_PREKEY_MONITOR_INTERVAL_MS = 25L
        private const val TX_MONITOR_INTERVAL_MS = 250L
        private const val SCO_START_WAIT_INTERVAL_MS = 200L
        private const val SCO_START_MAX_ATTEMPTS = 10
        private const val SCO_SILENCE_CHECK_DELAY_MS = 2000L
        private const val SCO_SILENCE_THRESHOLD = 5
        private const val SCO_MAX_RESTARTS = 3

        private const val CHANNEL_ID = "js8call_service"
        private const val CHANNEL_ID_MESSAGES = "js8call_messages"
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_ID_MESSAGE_BASE = 1000
    }
}
