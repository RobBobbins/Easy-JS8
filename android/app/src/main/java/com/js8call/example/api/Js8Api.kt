package com.js8call.example.api

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import androidx.preference.PreferenceManager
import com.js8call.example.BuildConfig
import com.js8call.example.js8ui.Js8Activity
import com.js8call.example.util.BandUtils
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The PC's network services (JS8Call 3.0.3), Station > Reporting:
 *  - API: JSON to a UDP server (MessageClient) and a TCP server for clients (MessageServer),
 *    the commands of JS8_Mainwindow/networkMessage.cpp ([Js8ApiCommands]) and the events the PC
 *    sends (RX.ACTIVITY, RX.DIRECTED, RX.SPOT, RIG.PTT, TX.FRAME, STATION.STATUS, RIG.FREQ, ...).
 *  - WSJT-X UDP protocol ([WsjtxClient]).
 *  - Logged contacts to N3FJP (TCP) and N1MM (UDP), and LOG.QSO: [logQso], for the LOG window
 *    (not built yet, so nothing calls it outside the debug test).
 *
 * Runs in the app process for as long as it lives (started by MainActivity and the engine
 * service). Sockets are used on one network thread; commands are handled on the main thread.
 */
object Js8Api {
    private const val TAG = "Js8Api"

    // ---- Settings (Station > Reporting, PC Configuration.cpp defaults) ------------------------
    const val KEY_DYNAMIC_INFO = "js8cfg_api_dynamic_info"   // AutoGrid, off
    const val KEY_UDP_SERVER = "js8cfg_udp_server"           // 127.0.0.1
    const val KEY_UDP_PORT = "js8cfg_udp_port"               // 2242
    const val KEY_UDP_ENABLED = "js8cfg_udp_enabled"         // off
    const val KEY_UDP_ACCEPT = "js8cfg_udp_accept"           // off
    const val KEY_TCP_SERVER = "js8cfg_tcp_server"           // 127.0.0.1
    const val KEY_TCP_PORT = "js8cfg_tcp_port"               // 2442
    const val KEY_TCP_ENABLED = "js8cfg_tcp_enabled"         // off
    const val KEY_TCP_ACCEPT = "js8cfg_tcp_accept"           // off
    const val KEY_TCP_MAX = "js8cfg_tcp_max_connections"     // 1
    const val KEY_WSJTX_SERVER = "js8cfg_wsjtx_server"       // 127.0.0.1
    const val KEY_WSJTX_PORT = "js8cfg_wsjtx_port"           // 2237
    const val KEY_WSJTX_ENABLED = "js8cfg_wsjtx_enabled"     // off
    const val KEY_WSJTX_TTL = "js8cfg_wsjtx_ttl"             // 1
    const val KEY_WSJTX_ACCEPT = "js8cfg_wsjtx_accept"       // off
    const val KEY_WSJTX_INTERFACES = "js8cfg_wsjtx_interfaces" // comma separated names; empty = loopback
    const val KEY_N3FJP_ENABLED = "js8cfg_n3fjp_enabled"     // off
    const val KEY_N3FJP_SERVER = "js8cfg_n3fjp_server"       // 127.0.0.1
    const val KEY_N3FJP_PORT = "js8cfg_n3fjp_port"           // 1100
    const val KEY_N1MM_ENABLED = "js8cfg_n1mm_enabled"       // off
    const val KEY_N1MM_SERVER = "js8cfg_n1mm_server"         // 127.0.0.1
    const val KEY_N1MM_PORT = "js8cfg_n1mm_port"             // 2333

    private val KEYS = setOf(
        KEY_DYNAMIC_INFO, KEY_UDP_SERVER, KEY_UDP_PORT, KEY_UDP_ENABLED, KEY_UDP_ACCEPT, KEY_TCP_SERVER, KEY_TCP_PORT,
        KEY_TCP_ENABLED, KEY_TCP_ACCEPT, KEY_TCP_MAX, KEY_WSJTX_SERVER, KEY_WSJTX_PORT, KEY_WSJTX_ENABLED, KEY_WSJTX_TTL,
        KEY_WSJTX_ACCEPT, KEY_WSJTX_INTERFACES
    )

    /** Program name and version sent in PING, Heartbeat and STATION.VERSION. */
    const val APP_NAME = "Easy JS8"
    val VERSION: String get() = BuildConfig.VERSION_NAME

    // ---- What the commands need from the app --------------------------------------------------

    /** The main screen (Js8MainFragment) while its views exist. Called on the main thread. */
    interface Controller {
        /** TX.GET_TEXT: the Typing Box. */
        fun typingText(): String
        /** TX.SET_TEXT: PC addMessageText(text, clear = true); nothing while a message waits. */
        fun setTypingText(text: String)
        /** RIG.SET_TUNE: TUNE button; returns whether TUNE is on now. */
        fun setTune(on: Boolean): Boolean
        /** RIG.TX_HALT: Halt button. */
        fun halt()
        /** RIG.SET_FREQ OFFSET: PC changeFreq (same lock as a waterfall tap). */
        fun setOffset(hz: Int)
        /** RX.SET_FILTER / RX.SET_FILTER_ENABLED: Waterfall Controls > Control filter. */
        fun setFilter(center: Int?, width: Int?, enabled: Boolean?)
        /** Settings changed through the API: header, status bar and buttons follow. */
        fun refresh()
    }

    /** MainActivity: the transmit queue. Called on the main thread. */
    interface Host {
        /** TX.SEND_MESSAGE: PC enqueueMessage(PriorityNormal, text, -1) + processTxQueue. */
        fun sendMessage(text: String)
        /** TX.GET_QUEUE_DEPTH: messages waiting. */
        fun queueDepth(): Int
        /** WINDOW.RAISE. */
        fun raise()
    }

    @Volatile var controller: Controller? = null
    @Volatile var host: Host? = null
    /** Band / Call Activity and the Message Window (MainActivity's Js8ActivityViewModel). */
    @Volatile var activity: Js8Activity? = null

    /** Live state the engine service and the main screen keep up to date. */
    object State {
        @Volatile var selectedCall: String? = null
        @Volatile var transmitting = false
        @Volatile var currentMessage = ""
        @Volatile var decoding = false
        @Volatile var monitoring = false
        @Volatile var tuning = false
        /** Dial the radio reported (CAT); 0 = use Settings > Frequency. */
        @Volatile var radioDialHz = 0L
    }

    // ---- Dynamic station information (Allow setting station information ... from the APIs) ----

    @Volatile private var dynamicGrid = ""
    @Volatile private var dynamicInfo = ""
    @Volatile private var dynamicStatus = ""

    /** Configuration::my_grid: the API's grid while allowed (4 characters or more), else Station's. */
    fun myGrid(p: SharedPreferences): String {
        val saved = p.getString("grid", "").orEmpty().trim().uppercase(Locale.US)
        return if (bool(p, KEY_DYNAMIC_INFO, false) && dynamicGrid.length >= 4) dynamicGrid.uppercase(Locale.US) else saved
    }

    fun myInfo(p: SharedPreferences): String {
        val saved = p.getString("my_info", "").orEmpty()
        return if (bool(p, KEY_DYNAMIC_INFO, false) && dynamicInfo.isNotEmpty()) dynamicInfo else saved
    }

    fun myStatus(p: SharedPreferences): String {
        val saved = p.getString("my_status", "").orEmpty()
        return if (bool(p, KEY_DYNAMIC_INFO, false) && dynamicStatus.isNotEmpty()) dynamicStatus else saved
    }

    internal fun setDynamicGrid(v: String) { dynamicGrid = v.trim() }
    internal fun setDynamicInfo(v: String) { dynamicInfo = v.trim() }
    internal fun setDynamicStatus(v: String) { dynamicStatus = v.trim() }

    // ---- Start ----------------------------------------------------------------------------------

    private var app: Context? = null
    private val main = Handler(Looper.getMainLooper())
    private val netThread = HandlerThread("Js8Api").apply { start() }
    private val net = Handler(netThread.looper)
    private val udp = UdpClient()
    private val tcp = TcpServer()
    private var wsjtx: WsjtxClient? = null
    private var wsjtxConfig = ""
    private var dynamicWasOn = false
    private var lastBand = ""
    private lateinit var commands: Js8ApiCommands

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            in KEYS -> apply()
            "last_frequency" -> { bandCheck(); statusUpdate() }
            "tx_submode", "js8cfg_tx_offset" -> statusUpdate()
        }
    }

    /** MainActivity.onCreate and JS8EngineService.onCreate; only the first call does anything. */
    fun init(context: Context) {
        if (app != null) return
        val c = context.applicationContext
        app = c
        commands = Js8ApiCommands(c)
        val p = prefs()
        p.registerOnSharedPreferenceChangeListener(prefsListener)
        dynamicWasOn = bool(p, KEY_DYNAMIC_INFO, false)
        lastBand = BandUtils.bandLabelForFrequencyHz(dialHz())
        apply()
        net.postDelayed(pulse, PULSE_MS)
    }

    internal fun prefs(): SharedPreferences = PreferenceManager.getDefaultSharedPreferences(app!!)

    /** Settings may be stored as text (Station window) or numbers / booleans. */
    internal fun bool(p: SharedPreferences, key: String, def: Boolean) =
        p.all[key]?.toString()?.toBooleanStrictOrNull() ?: def

    internal fun int(p: SharedPreferences, key: String, def: Int) =
        p.all[key]?.toString()?.trim()?.toDoubleOrNull()?.toInt() ?: def

    internal fun str(p: SharedPreferences, key: String, def: String) = p.all[key]?.toString() ?: def

    private class Config(p: SharedPreferences) {
        val udpEnabled = bool(p, KEY_UDP_ENABLED, false)
        val udpServer = str(p, KEY_UDP_SERVER, "127.0.0.1").trim()
        val udpPort = int(p, KEY_UDP_PORT, 2242)
        val udpAccept = bool(p, KEY_UDP_ACCEPT, false)
        val tcpEnabled = bool(p, KEY_TCP_ENABLED, false)
        val tcpServer = str(p, KEY_TCP_SERVER, "127.0.0.1").trim()
        val tcpPort = int(p, KEY_TCP_PORT, 2442)
        val tcpAccept = bool(p, KEY_TCP_ACCEPT, false)
        val tcpMax = int(p, KEY_TCP_MAX, 1)
        val wsjtxEnabled = bool(p, KEY_WSJTX_ENABLED, false)
        val wsjtxServer = str(p, KEY_WSJTX_SERVER, "127.0.0.1").trim()
        val wsjtxPort = int(p, KEY_WSJTX_PORT, 2237)
        val wsjtxTtl = int(p, KEY_WSJTX_TTL, 1)
        val wsjtxAccept = bool(p, KEY_WSJTX_ACCEPT, false)
        val wsjtxInterfaces = str(p, KEY_WSJTX_INTERFACES, "").split(',').map { it.trim() }.filter { it.isNotEmpty() }

        /** WSJT-X on the same server and port as the UDP API: the PC stops the JSON client. */
        val jsonConflicts = wsjtxEnabled && wsjtxPort == udpPort && wsjtxServer == udpServer
    }

    @Volatile private var cfg: Config? = null
    private fun config(): Config = cfg ?: Config(prefs()).also { cfg = it }

    /** Settings changed (or first start): servers follow (PC prepareApi and the config signals). */
    private fun apply() {
        val p = prefs()
        val c = Config(p)
        cfg = c
        // Configuration.cpp: checking the box clears an old grid from the API
        val dynamicOn = bool(p, KEY_DYNAMIC_INFO, false)
        if (dynamicOn && !dynamicWasOn) dynamicGrid = ""
        dynamicWasOn = dynamicOn
        net.post {
            udp.port = if (c.jsonConflicts) 0 else c.udpPort
            udp.setServerName(if (c.udpEnabled) c.udpServer else "")
            if (c.tcpEnabled) {
                tcp.setMaxConnections(c.tcpMax)
                tcp.setServer(c.tcpServer, c.tcpPort)
                tcp.start()
            } else tcp.stop()
            val wcfg = "${c.wsjtxServer}|${c.wsjtxPort}|${c.wsjtxTtl}|${c.wsjtxInterfaces}"
            if (c.wsjtxEnabled) {
                val w = wsjtx ?: WsjtxClient(APP_NAME, VERSION, "", wsjtxListener).also { wsjtx = it; wsjtxConfig = "" }
                w.enabled = c.wsjtxAccept
                if (wcfg != wsjtxConfig) {
                    wsjtxConfig = wcfg
                    w.setServerPort(c.wsjtxPort)
                    w.setTtl(c.wsjtxTtl)
                    w.setServer(c.wsjtxServer, c.wsjtxInterfaces.ifEmpty { loopbackInterfaces() })
                }
            } else {
                wsjtx?.close()
                wsjtx = null
            }
        }
    }

    /** Configuration load_network_interfaces: with nothing chosen, the loopback interface. */
    private fun loopbackInterfaces(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList().filter { it.isLoopback && it.isUp }.map { it.name }
    }.getOrDefault(emptyList())

    /** Interfaces for Outgoing interfaces: up and able to multicast (load_network_interfaces). */
    fun multicastInterfaces(): List<Pair<String, String>> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && (it.supportsMulticast() || it.isLoopback) }
            .map { (it.displayName ?: it.name) to it.name }
    }.getOrDefault(emptyList())

    private val wsjtxListener = object : WsjtxClient.Listener {
        // WSJTXMessageMapper::handleFreeText / handleLocation: the PC sends these on as API
        // messages (it does not change its own Typing Box or grid)
        override fun onFreeText(text: String, send: Boolean) {
            main.post {
                sendNetworkMessage("TX.SET_TEXT", text)
                if (send) sendNetworkMessage("TX.SEND_MESSAGE", text)
            }
        }
        override fun onLocation(location: String) { main.post { sendNetworkMessage("STATION.SET_GRID", location) } }
        override fun onError(message: String) { Log.w(TAG, message) }
    }

    /** MessageClient ping (15 s) and the WSJT-X heartbeat (NetworkMessage::pulse, 15 s). */
    private const val PULSE_MS = 15_000L
    private val pulse = object : Runnable {
        override fun run() {
            udp.ping()
            wsjtx?.heartbeat()
            net.postDelayed(this, PULSE_MS)
        }
    }

    // ---- Sending (UI_Constructor::sendNetworkMessage) ------------------------------------------

    /** canSendNetworkMessage. */
    fun canSend(): Boolean = app != null && config().let { it.udpEnabled || it.tcpEnabled }

    fun sendNetworkMessage(type: String, value: String) {
        if (!canSend()) return
        dispatch(ApiMessage(type, value))
    }

    fun sendNetworkMessage(type: String, value: String, params: Map<String, Any?>) {
        if (!canSend()) return
        dispatch(ApiMessage(type, value, params))
    }

    private fun dispatch(m: ApiMessage) {
        val c = config()
        net.post {
            if (c.udpEnabled) udp.send(m)
            if (c.tcpEnabled) tcp.send(m)
        }
    }

    // ---- Incoming (udpNetworkMessage / tcpNetworkMessage) --------------------------------------

    private fun onUdpMessage(m: ApiMessage) = main.post {
        val c = config()
        if (c.udpEnabled && c.udpAccept) commands.handle(m)
    }

    private fun onTcpMessage(m: ApiMessage) = main.post {
        val c = config()
        if (c.tcpEnabled && c.tcpAccept) commands.handle(m)
    }

    // ---- Values the events and commands share --------------------------------------------------

    fun dialHz(): Long {
        val radio = State.radioDialHz
        if (radio > 0) return radio
        return prefs().getString("last_frequency", "14078000")?.trim()?.toLongOrNull() ?: 14_078_000L
    }

    fun offsetHz(): Int = int(prefs(), "js8cfg_tx_offset", 1500)

    fun speed(): Int = Js8Activity.txSubmode(runCatching { prefs().getInt("tx_submode", 0) }.getOrDefault(0))

    fun myCall(): String = prefs().getString("callsign", "").orEmpty().trim().uppercase(Locale.US)

    // ---- Events the PC sends --------------------------------------------------------------------

    /** Selection changed on the main screen (callsignSelected). */
    fun setSelectedCall(call: String?) {
        if (State.selectedCall == call) return
        State.selectedCall = call
        statusUpdate()
    }

    /**
     * UI_Constructor::statusUpdate: WSJT-X Status and STATION.STATUS (not when WSJT-X uses the
     * UDP API's server and port). Called on decode start / end, RX on / off, transmit start / end,
     * offset, dial and speed changes, and a new selection.
     */
    fun statusUpdate() {
        if (app == null) return
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post { statusUpdate() }; return }
        val c = config()
        val dial = dialHz()
        val offset = offsetHz()
        val selected = State.selectedCall.orEmpty()
        if (c.wsjtxEnabled) wsjtxStatus(dial, offset, selected, decoding = State.decoding)
        if (canSend() && !c.jsonConflicts) {
            sendNetworkMessage("STATION.STATUS", "", mapOf(
                "FREQ" to dial + offset, "DIAL" to dial, "OFFSET" to offset,
                "SPEED" to speed(), "SELECTED" to selected
            ))
        }
    }

    /** WSJT-X uses the UDP API's server and port: no JSON for STATION.STATUS / RIG.FREQ. */
    internal fun jsonConflicts() = config().jsonConflicts

    /** RIG.GET_FREQ: the WSJT-X Status only. */
    internal fun statusUpdateWsjtxOnly() {
        if (config().wsjtxEnabled) wsjtxStatus(dialHz(), offsetHz(), State.selectedCall.orEmpty(), decoding = State.decoding)
    }

    private fun wsjtxStatus(dial: Long, offset: Int, dxCall: String, decoding: Boolean) {
        val p = prefs()
        val dxGrid = activity?.callActivity?.get(dxCall)?.grid.orEmpty()
        val txMessage = if (State.transmitting) State.currentMessage else ""
        val myCall = myCall()
        val myGrid = myGrid(p)
        val transmitting = State.transmitting
        net.post {
            // WSJTXMessageMapper::sendStatusUpdate: mode and submode "JS8", tolerance and T/R period unset
            wsjtx?.statusUpdate(dial + offset, "JS8", dxCall, "", "JS8", true, transmitting, decoding,
                offset.toLong(), offset.toLong(), myCall, myGrid, dxGrid, false, "JS8", false, 0,
                0xffffffffL, 0xffffffffL, "", txMessage)
        }
    }

    /** displayDialFrequency: on a band change, WSJT-X Status and RIG.FREQ with the band name. */
    fun bandCheck() {
        if (app == null) return
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post { bandCheck() }; return }
        val dial = dialHz()
        val band = BandUtils.bandLabelForFrequencyHz(dial)
        if (band == lastBand) return
        lastBand = band
        val c = config()
        val offset = offsetHz()
        if (c.wsjtxEnabled) wsjtxStatus(dial, offset, State.selectedCall.orEmpty(), decoding = State.decoding || State.monitoring)
        if (!c.jsonConflicts) {
            sendNetworkMessage("RIG.FREQ", "", mapOf(
                "_ID" to -1, "BAND" to band, "FREQ" to dial + offset, "DIAL" to dial, "OFFSET" to offset
            ))
        }
    }

    /** processRxActivity: every frame heard. */
    fun rxActivity(text: String, offset: Int, snr: Int, speed: Int, tdrift: Float, utcMs: Long, bits: Int) {
        if (!canSend()) return
        val dial = dialHz()
        sendNetworkMessage("RX.ACTIVITY", text, mapOf(
            "_ID" to -1, "FREQ" to dial + offset, "DIAL" to dial, "OFFSET" to offset, "SNR" to snr,
            "SPEED" to speed, "TDRIFT" to tdrift, "UTC" to utcMs, "BITS" to bits
        ))
    }

    /**
     * processCommandActivity: a directed command heard (for anyone). text is "TO CMD EXTRA ..."
     * without the sender, with the end-of-transmission mark on the last frame.
     */
    fun rxDirected(from: String, to: String, cmd: String, grid: String, extra: String, text: String,
                   offset: Int, snr: Int, speed: Int, tdrift: Float, utcMs: Long) {
        if (!canSend()) return
        val dial = dialHz()
        sendNetworkMessage("RX.DIRECTED", text, mapOf(
            "_ID" to -1, "FROM" to from, "TO" to to, "CMD" to cmd, "GRID" to grid, "EXTRA" to extra,
            "TEXT" to text, "FREQ" to dial + offset, "DIAL" to dial, "OFFSET" to offset, "SNR" to snr,
            "SPEED" to speed, "TDRIFT" to tdrift, "UTC" to utcMs
        ))
    }

    /** processSpots: a station spotted to the reporting networks. */
    fun rxSpot(dial: Long, offset: Int, call: String, snr: Int, grid: String) {
        if (!canSend()) return
        sendNetworkMessage("RX.SPOT", "", mapOf(
            "_ID" to -1, "FREQ" to dial + offset, "DIAL" to dial, "OFFSET" to offset,
            "CALL" to call, "SNR" to snr, "GRID" to grid
        ))
    }

    /** emitPTT. */
    fun ptt(on: Boolean) {
        sendNetworkMessage("RIG.PTT", if (on) "on" else "off", mapOf(
            "_ID" to -1, "PTT" to on, "UTC" to System.currentTimeMillis()
        ))
    }

    /** emitTones: the tone numbers of the frame now going out. */
    fun txFrame(tones: IntArray) {
        if (!canSend()) return
        sendNetworkMessage("TX.FRAME", "", mapOf("_ID" to -1, "TONES" to tones.toList()))
    }

    /** processDecodeEvent: WSJT-X Decode for each frame (mode "JS8"). utc is hhmmss. */
    fun wsjtxDecode(utc: Int, snr: Int, dt: Float, offset: Int, text: String, lowConfidence: Boolean) {
        if (app == null || !config().wsjtxEnabled) return
        val ms = ((utc / 10000) * 3600L + ((utc / 100) % 100) * 60L + utc % 100) * 1000L
        net.post { wsjtx?.decode(true, ms, snr, dt, offset.toLong(), "JS8", text, lowConfidence, false) }
    }

    /** closeEvent: STATION.CLOSING, then the UDP client's CLOSE and the WSJT-X Close. */
    fun closing() {
        if (app == null) return
        sendNetworkMessage("STATION.CLOSING", "", mapOf("_ID" to -1, "REASON" to "User closed application"))
        net.post {
            udp.close()
            wsjtx?.close()
            wsjtx = null
            wsjtxConfig = ""
        }
    }

    // ---- Logged contacts (UI_Constructor::acceptQSO) --------------------------------------------

    data class Qso(
        val call: String, val grid: String, val dialHz: Long, val mode: String, val submode: String,
        val rptSent: String, val rptReceived: String, val name: String, val comments: String,
        val timeOnMs: Long, val timeOffMs: Long, val operatorCall: String, val myCall: String, val myGrid: String,
        val adif: String, val extra: Map<String, String> = emptyMap()
    )

    /**
     * Sends a logged contact to LOG.QSO (API), N1MM, N3FJP and WSJT-X, like the PC's acceptQSO.
     * [onError] gets the PC's message for a failed N1MM / N3FJP send (on the main thread).
     */
    fun logQso(q: Qso, onError: (title: String, text: String) -> Unit = { _, _ -> }) {
        val p = prefs()
        sendNetworkMessage("LOG.QSO", q.adif, mapOf(
            "_ID" to -1, "UTC.ON" to q.timeOnMs, "UTC.OFF" to q.timeOffMs, "CALL" to q.call, "GRID" to q.grid,
            "FREQ" to q.dialHz, "MODE" to q.mode, "SUBMODE" to q.submode, "RPT.SENT" to q.rptSent,
            "RPT.RECV" to q.rptReceived, "NAME" to q.name, "COMMENTS" to q.comments,
            "STATION.OP" to q.operatorCall, "STATION.CALL" to q.myCall, "STATION.GRID" to q.myGrid, "EXTRA" to q.extra
        ))
        val n1mmHost = str(p, KEY_N1MM_SERVER, "127.0.0.1").trim()
        val n1mmPort = int(p, KEY_N1MM_PORT, 2333)
        if (bool(p, KEY_N1MM_ENABLED, false) && n1mmHost.isNotEmpty() && n1mmPort != 0) {
            net.post {
                val ok = runCatching {
                    java.net.DatagramSocket().use { s ->
                        val b = (q.adif + " <eor>").toByteArray(Charsets.UTF_8)
                        s.send(DatagramPacket(b, b.size, InetAddress.getByName(n1mmHost), n1mmPort))
                    }
                }.isSuccess
                if (!ok) main.post { onError("Error sending log to N1MM", "Write returned \"-1\"") }
            }
        }
        val n3Host = str(p, KEY_N3FJP_SERVER, "127.0.0.1").trim()
        val n3Port = int(p, KEY_N3FJP_PORT, 1100)
        if (bool(p, KEY_N3FJP_ENABLED, false) && n3Host.isNotEmpty() && n3Port != 0) {
            val data = n3fjpCommand(q)
            net.post {
                val ok = n3fjp.send(n3Host, n3Port, data, 500)
                if (ok) {
                    net.postDelayed({
                        n3fjp.send(n3Host, n3Port, "<CMD><CHECKLOG></CMD>", 100)
                        n3fjp.send(n3Host, n3Port, "\r\n", 100)
                    }, 300)
                } else main.post { onError("Error sending log to N3FJP", "Write failed for \"$n3Host:$n3Port\"") }
            }
        }
        if (config().wsjtxEnabled) {
            net.post {
                // JS8Call has no separate time on: both are the time off
                wsjtx?.qsoLogged(q.timeOffMs, q.call, q.grid, q.dialHz, q.mode, q.rptSent, q.rptReceived, "", "", "",
                    q.timeOffMs, "", q.myCall, q.myGrid, "", "", "")
                wsjtx?.loggedAdif(q.adif)
            }
        }
    }

    /** The PC's N3FJP <ADDDIRECT> command. */
    private fun n3fjpCommand(q: Qso): String {
        val on = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = q.timeOnMs }
        val date = String.format(Locale.US, "%04d/%02d/%02d", on.get(java.util.Calendar.YEAR), on.get(java.util.Calendar.MONTH) + 1, on.get(java.util.Calendar.DAY_OF_MONTH))
        val time = String.format(Locale.US, "%d:%02d", on.get(java.util.Calendar.HOUR_OF_DAY), on.get(java.util.Calendar.MINUTE))
        val band = BandUtils.bandLabelForFrequencyHz(q.dialHz).replace("m", "")
        val mhz = String.format(Locale.US, "%.6f", q.dialHz / 1_000_000.0)
        var other = 0
        val additional = StringBuilder()
        for ((key, value) in q.extra) {
            val n3key = N3FJP_ADIF_MAP[key] ?: run { other++; N3FJP_ADIF_MAP["*$other"] } ?: break
            additional.append("<$n3key>$value</$n3key>")
        }
        return "<CMD><ADDDIRECT><EXCLUDEDUPES>TRUE</EXCLUDEDUPES><STAYOPEN>FALSE</STAYOPEN>" +
            "<fldDateStr>$date</fldDateStr><fldTimeOnStr>$time</fldTimeOnStr><fldCall>${q.call}</fldCall>" +
            "<fldGridR>${q.grid}</fldGridR><fldBand>$band</fldBand><fldFrequency>$mhz</fldFrequency>" +
            "<fldMode>JS8</fldMode><fldOperator>${q.operatorCall}</fldOperator><fldNameR>${q.name}</fldNameR>" +
            "<fldComments>${q.comments}</fldComments><fldRstS>${q.rptSent}</fldRstS><fldRstR>${q.rptReceived}</fldRstR>" +
            "$additional</CMD>"
    }

    /** JS8_Logbook/n3fjp.h: ADIF field -> N3FJP field (the first of a repeated key wins, like QMap). */
    private val N3FJP_ADIF_MAP = mapOf(
        "AGE" to "fldAge", "BAND" to "fldBand", "CALL" to "fldCall", "CHECK" to "fldCheck", "CLASS" to "fldClass",
        "COMMENT" to "fldComments", "COMMENT_INTL" to "fldComments", "CONTEST_ID" to "fldModeContest",
        "CONT" to "fldContinent", "DXCC" to "fldCountryDXCC", "COUNTRY" to "fldCountyR", "COUNTRY_INTL" to "fldCountyR",
        "MY_COUNTRY" to "fldCountyS", "MY_COUNTRY_INTL" to "fldCountyS", "CQZ" to "fldCQZone", "QSO_DATE" to "fldDateStr",
        "FISTS" to "fldFists", "FISTS_CC" to "fldFists", "FREQ" to "fldFrequency", "GRIDSQUARE" to "fldGridR",
        "MY_GRIDSQUARE" to "fldGridS", "IOTA" to "fldIOTA", "ITUZ" to "fldITUZone", "MODE" to "fldMode",
        "NAME" to "fldNameR", "NAME_INTL" to "fldNameR", "MY_NAME" to "fldNameS", "MY_NAME_INTL" to "fldNameS",
        "OPERATOR" to "fldOperator", "*1" to "fldOther1", "*2" to "fldOther2", "*3" to "fldOther3", "*4" to "fldOther4",
        "*5" to "fldOther5", "*6" to "fldOther6", "*7" to "fldOther7", "*8" to "fldOther8", "RX_PWR" to "fldPower",
        "TX_PWR" to "fldPower", "PRECEDENCE" to "fldPrecedence", "PFX" to "fldPrefix", "PROP_MODE" to "fldPropMode",
        "QSL_RCVD_VIA" to "fldQSLConfByR", "QSL_SENT_VIA" to "fldQSLConfByS", "QSL_RCVD" to "fldQSLR",
        "QSL_SENT" to "fldQSLS", "QTH" to "fldQTHGroup", "QTH_INTL" to "fldQTHGroup", "RST_RCVD" to "fldRstR",
        "RST_SENT" to "fldRstS", "SAT_NAME" to "fldSatName", "ARRL_SECT" to "fldSection", "SRX" to "fldSPCNum",
        "STX" to "fldSerialNoS", "SRX_STRING" to "fldSPC", "STATE" to "fldState", "STATION_CALLSIGN" to "fldStation",
        "TEN_TEN" to "fldTenTen", "TIME_OFF" to "fldTimeOffStr", "TIME_ON" to "fldTimeOnStr"
    )

    /** TCPClient: one connection kept to the N3FJP server; "\r\f" after each write like the PC. */
    private val n3fjp = object {
        private var socket: Socket? = null
        private var host = ""
        private var port = 0
        fun send(h: String, p: Int, data: String, timeoutMs: Int): Boolean = try {
            if (socket?.isConnected != true || socket?.isClosed == true || h != host || p != port) {
                runCatching { socket?.close() }
                host = h; port = p
                socket = Socket().apply { connect(InetSocketAddress(h, p), timeoutMs) }
            }
            socket!!.getOutputStream().apply { write((data + "\r\u000C").toByteArray(Charsets.ISO_8859_1)); flush() }
            true
        } catch (e: Exception) {
            Log.w(TAG, "N3FJP: ${e.message}")
            runCatching { socket?.close() }
            socket = null
            false
        }
    }

    // ---- UDP API client (JS8_Main/MessageClient.cpp) --------------------------------------------

    private class UdpClient {
        var port = 0
        private var socket: MulticastSocket? = null
        private var host: InetAddress? = null
        private var name = ""
        private val queue = ArrayDeque<ApiMessage>()
        private var lastDatagram: String? = null

        /** set_server_name: an empty name stops sending (messages wait while a name is looked up). */
        fun setServerName(n: String) {
            if (n == name && (host != null || n.isEmpty())) return
            name = n
            host = null
            if (n.isEmpty()) return
            val addr = try { InetAddress.getByName(n) } catch (e: Exception) {
                Log.w(TAG, "Host lookup failed: ${e.message}")
                queue.clear()
                return
            }
            host = addr
            try {
                socket?.close()
                socket = MulticastSocket(InetSocketAddress(0)).also { s ->
                    if (addr.isMulticastAddress) runCatching { s.joinGroup(addr) }
                    startReader(s)
                }
            } catch (e: Exception) {
                Log.w(TAG, "UDP socket: ${e.message}")
                return
            }
            ping()
            if (port != 0) while (queue.isNotEmpty()) sendNow(queue.removeFirst())
        }

        private fun startReader(s: MulticastSocket) {
            Thread({
                val buf = ByteArray(65536)
                while (!s.isClosed) {
                    try {
                        val p = DatagramPacket(buf, buf.size)
                        s.receive(p)
                        val text = String(p.data, 0, p.length, Charsets.UTF_8)
                        try { onUdpMessage(ApiMessage.fromJson(text)) } catch (e: Exception) {
                            Log.w(TAG, "MessageClient exception: ${e.message}")
                        }
                    } catch (e: Exception) {
                        if (!s.isClosed) Log.w(TAG, "UDP receive: ${e.message}")
                    }
                }
            }, "Js8Api-udp-rx").apply { isDaemon = true }.start()
        }

        fun ping() {
            if (port != 0 && host != null) {
                sendNow(ApiMessage("PING", "", mapOf(
                    "NAME" to APP_NAME, "VERSION" to VERSION, "UTC" to System.currentTimeMillis()
                )))
            }
        }

        fun send(m: ApiMessage) {
            if (port == 0) return
            if (host == null) { if (name.isNotEmpty() && queue.size < 500) queue.addLast(m) } else sendNow(m)
        }

        /** Not sent when it is exactly the datagram sent last. */
        private fun sendNow(m: ApiMessage) {
            if (port == 0) return
            val h = host ?: return
            val s = socket ?: return
            val json = m.toJson()
            if (json == lastDatagram) return
            try {
                val b = json.toByteArray(Charsets.UTF_8)
                s.send(DatagramPacket(b, b.size, h, port))
                lastDatagram = json
            } catch (e: Exception) {
                Log.w(TAG, "UDP send: ${e.message}")
            }
        }

        fun close() {
            if (port != 0 && host != null) sendNow(ApiMessage("CLOSE"))
        }
    }

    // ---- TCP API server (JS8_Main/MessageServer.cpp) ---------------------------------------------

    private class TcpServer {
        private var host = ""
        private var port = 0
        private var maxConnections = 1
        private var server: ServerSocket? = null
        private val clients = CopyOnWriteArrayList<Client>()

        fun setServer(h: String, p: Int) {
            val listening = server != null
            if (listening && (h != host || p != port)) stop()
            host = h
            port = p
            if (listening) start()
        }

        /** Only an address (QHostAddress::setAddress), a port above 0. */
        fun start() {
            if (server != null) return
            if (!isAddress(host)) { Log.d(TAG, "MessageServer address invalid: $host $port"); return }
            if (port <= 0) { Log.d(TAG, "MessageServer port invalid: $host $port"); return }
            val s = try { ServerSocket(port, 50, InetAddress.getByName(host)) } catch (e: Exception) {
                Log.w(TAG, "MessageServer listening: false $host $port (${e.message})")
                return
            }
            server = s
            Log.i(TAG, "MessageServer listening: true $host $port")
            Thread({
                while (!s.isClosed) {
                    val socket = try { s.accept() } catch (e: Exception) { break }
                    net.post { incoming(socket) }
                }
            }, "Js8Api-tcp-accept").apply { isDaemon = true }.start()
        }

        fun stop() {
            clients.forEach { it.close() }
            clients.clear()
            runCatching { server?.close() }
            server = null
        }

        fun setMaxConnections(n: Int) {
            maxConnections = n
            prune()
        }

        private fun active() = clients.count { it.connected }

        /** Keep only the newest connections (fifo). */
        private fun prune() {
            clients.removeAll { !it.connected }
            while (maxConnections > 0 && clients.size > maxConnections) clients.removeAt(0).close()
        }

        private fun incoming(socket: Socket) {
            val client = Client(socket)
            clients.removeAll { !it.connected }
            if (maxConnections > 0 && maxConnections <= active()) {
                client.send(ApiMessage("API.ERROR", "Connections Full"))
                client.close()
                return
            }
            clients.add(client)
            client.startReader()
        }

        /** Only to clients waiting for this reply (or every client for _ID <= 0). */
        fun send(m: ApiMessage) {
            clients.forEach { if (it.awaitingResponse(m.id)) it.send(m) }
        }

        private fun isAddress(h: String) =
            Regex("^\\d{1,3}(\\.\\d{1,3}){3}$").matches(h) || (h.contains(':') && Regex("^[0-9A-Fa-f:.]+$").matches(h))
    }

    private class Client(private val socket: Socket) {
        @Volatile var connected = true
        private val requests = ConcurrentHashMap<Long, ApiMessage>()
        private val out: OutputStream? = runCatching { socket.getOutputStream() }.getOrNull()

        fun awaitingResponse(id: Long) = id <= 0 || requests.containsKey(id)

        fun startReader() {
            Thread({
                try {
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                    while (true) {
                        val line = reader.readLine() ?: break
                        val msg = line.trim()
                        if (msg.isEmpty()) continue
                        try {
                            val m = ApiMessage.fromJson(msg)
                            requests[m.ensureId()] = m
                            onTcpMessage(m)
                        } catch (e: Exception) {
                            net.post { send(ApiMessage("API.ERROR", e.message ?: "illegal value")) }
                        }
                    }
                } catch (_: Exception) {
                }
                connected = false
                Log.d(TAG, "MessageServer client disconnected")
            }, "Js8Api-tcp-client").apply { isDaemon = true }.start()
        }

        fun send(m: ApiMessage) {
            if (!connected) return
            try {
                out?.write((m.toJson() + "\n").toByteArray(Charsets.UTF_8))
                out?.flush()
            } catch (e: Exception) {
                connected = false
            }
            requests.remove(m.id)
        }

        fun close() {
            connected = false
            runCatching { socket.close() }
        }
    }
}
