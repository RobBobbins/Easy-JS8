package com.js8call.example.js8ui

import android.content.Context
import android.content.SharedPreferences
import com.js8call.core.BluetoothSerialPortCatalog
import com.js8call.core.HamlibRigCatalog
import com.js8call.core.UsbSerialPortCatalog

/**
 * Every setting of the JS8Call 3.0.3 Settings window (JS8_UI/Configuration.ui), in its tab order,
 * with the JS8Call default (JS8_UI/Configuration.cpp). Settings JS8Android already had keep
 * JS8Android's stored key, so the engine reads them as before. The rest are stored under
 * "js8cfg_*" keys.
 *
 * PC-only settings are left out on purpose (Rob, 2026-10-19): API / WSJT-X / N3FJP / N1MM,
 * Save Directory, PTT command, Application stylesheet / font, Check for software updates.
 */
object Js8SettingsSchema {

    /** Values held while the window is open; written on OK. */
    class Draft(val prefs: SharedPreferences) {
        val values = mutableMapOf<String, String>()
        fun get(f: Field): String = values[f.key] ?: f.load(prefs)
        fun set(f: Field, v: String) { values[f.key] = v }
        fun bool(f: Field) = get(f) == "true"
    }

    sealed class Item
    data class Group(val title: String?, val items: List<Item>, val visible: (Draft) -> Boolean = { true }) : Item()
    /** Radio buttons in their own titled box (Data Bits, PTT Method, ...). */
    data class RadioGroup(
        val field: Field, val title: String, val options: List<Pair<String, String>>,
        /** Values shown greyed out. */
        val disabled: Set<String> = emptySet(),
        /** One under another sideways too (long labels). */
        val vertical: Boolean = false
    ) : Item()
    data class Note(val text: String) : Item()
    data class ButtonRow(val buttons: List<Pair<String, String>>) : Item() // label to action id
    data class Special(val id: String) : Item() // drawn by the fragment (tables, lists, diagnostics)

    open class Field(
        val key: String,
        val label: String,
        val kind: Kind,
        val default: String = "",
        val hint: String = "",
        val min: Double = 0.0,
        val max: Double = 0.0,
        val step: Double = 1.0,
        val prefix: String = "",
        val suffix: String = "",
        val special: String? = null,
        val decimals: Int = 0,
        val upper: Boolean = false,
        /** Most characters a text box takes (0 = no limit). */
        val maxLength: Int = 0,
        /** Text box shows the PC's warning mark while empty (callsign, grid). */
        val required: Boolean = false,
        val enabled: Boolean = true,
        /** Greyed out while this is false for the unsaved values (e.g. ACK needs AUTO and HB). */
        val active: (Draft) -> Boolean = { true },
        val choices: ((Context, Draft) -> List<Pair<String, String>>)? = null,
        val visible: (Draft) -> Boolean = { true },
        /** Reads the stored value as a string. Default: the key's own stored value. */
        val load: (SharedPreferences) -> String = { p -> readAny(p, key, default) },
        /** Writes the value. Default: string or boolean under the key. */
        val save: (SharedPreferences.Editor, String, Context) -> Unit = { e, v, _ ->
            if (kind == Kind.CHECK) e.putBoolean(key, v == "true") else e.putString(key, v)
        }
    ) : Item()

    enum class Kind { TEXT, CHECK, SPIN, CHOICE, RADIO, AUDIO_INPUT }

    data class Section(val tab: String, val subTab: String?, val items: List<Item>)

    private fun readAny(p: SharedPreferences, key: String, default: String): String =
        when (val v = p.all[key]) {
            null -> default
            is Boolean -> v.toString()
            else -> v.toString()
        }

    // ---- shorthand ---------------------------------------------------------------------------

    private fun text(
        key: String, label: String, default: String = "", hint: String = "", upper: Boolean = false,
        maxLength: Int = 0, required: Boolean = false
    ) = Field(key, label, Kind.TEXT, default, hint = hint, upper = upper, maxLength = maxLength, required = required)

    private fun check(key: String, label: String, default: Boolean) =
        Field(key, label, Kind.CHECK, default.toString())

    private fun spin(
        key: String, label: String, default: Number, min: Double, max: Double, step: Double = 1.0,
        prefix: String = "", suffix: String = "", special: String? = null, decimals: Int = 0
    ) = Field(key, label, Kind.SPIN, if (decimals == 0) default.toInt().toString() else default.toString(),
        min = min, max = max, step = step, prefix = prefix, suffix = suffix, special = special, decimals = decimals)

    private fun choice(key: String, label: String, default: String, options: List<Pair<String, String>>) =
        Field(key, label, Kind.CHOICE, default, choices = { _, _ -> options })

    private fun radio(key: String, default: String) = Field(key, "", Kind.RADIO, default)

    private fun same(vararg v: String) = v.map { it to it }

    // ---- Radio: one "Rig" list = JS8Android rig types + every Hamlib model ---------------------

    private const val RIG = "js8cfg_rig"

    private fun rigChoices(context: Context): List<Pair<String, String>> {
        val base = listOf(
            "None" to "none",
            "Hamlib NET rigctl / FLRig (network)" to "network",
            "QRP Labs QMX (direct USB)" to "qmx_serial",
            "truSDX (direct USB)" to "trusdx_serial",
            "BTECH UV-Pro (Bluetooth)" to "btech_uvpro",
            "RTS PTT only (USB serial)" to "rts_ptt"
        )
        val models = try { HamlibRigCatalog.listRigModels() } catch (_: Throwable) { emptyList() }
        return base + models.map { it.label to "hamlib:${it.id}" }
    }

    private fun loadRig(p: SharedPreferences): String {
        if (!p.getBoolean("rig_control_enabled", false)) return "none"
        return when (val t = p.getString("rig_type", "none") ?: "none") {
            "hamlib_usb" -> "hamlib:${p.getString("rig_hamlib_model", "0")}"
            else -> t
        }
    }

    private fun saveRig(e: SharedPreferences.Editor, v: String) {
        e.putBoolean("rig_control_enabled", v != "none")
        if (v.startsWith("hamlib:")) {
            e.putString("rig_type", "hamlib_usb")
            e.putString("rig_hamlib_model", v.removePrefix("hamlib:"))
        } else {
            e.putString("rig_type", v)
        }
    }

    private fun rigIs(d: Draft, vararg types: String): Boolean {
        val v = d.values[RIG] ?: loadRig(d.prefs)
        return types.any { t -> if (t == "hamlib") v.startsWith("hamlib:") else v == t }
    }

    private fun portChoices(context: Context): List<Pair<String, String>> {
        val usb = try { UsbSerialPortCatalog.listPorts(context) } catch (_: Throwable) { emptyList() }
        val bt = try { BluetoothSerialPortCatalog.listPorts(context) } catch (_: Throwable) { emptyList() }
        val list = mutableListOf("Automatic" to "auto")
        usb.forEach { list += "USB ${it.label}" to "android-usb:${it.deviceId}:${it.portIndex}" }
        bt.forEach { list += it.label to "android-bt:${it.address}:${it.portIndex}" }
        return list
    }

    // ---- Sections ---------------------------------------------------------------------------

    /**
     * The PC Mode menu speeds (mainwindow.ui menuModeJS8), stored as the engine's tx_submode.
     * Also listed in Menu > Mode. JS8 60 is greyed out: the engine cannot send it.
     */
    const val TX_SUBMODE_KEY = "tx_submode"
    val SPEEDS = listOf(
        "JS8 Normal (~16 wpm, best for most conditions)" to "0",
        "JS8 Fast (~24 wpm, reduced decoder sensitivity)" to "1",
        "JS8 40 (~40 wpm, only use in good conditions)" to "2",
        "JS8 60 (~60 wpm, fast but experimental mode)" to "8",
        "JS8 Slow (~8 wpm, best decoder sensitivity)" to "4"
    )
    val SPEEDS_DISABLED = setOf("8")

    /** Station > SWL (Listening only): no callsign needed, nothing is transmitted (Easy JS8 only). */
    const val SWL_KEY = "js8cfg_swl"

    /**
     * Settings window (header Settings button): settings for operating the program
     * (Rob, 2026-10-19). The PC has these in its Mode menu.
     */
    fun settingsSections(): List<Section> = listOf(
        // The rest of the PC Mode menu (Rob, 2026-10-19). Same keys the engine reads.
        Section("Operation", null, listOf(
            check(MULTI_KEY, "Enable Simultaneous Decoding of All Speeds (MULTI)", true),
            check(AUTOREPLY_KEY, "Enable Autoreply (AUTO)", false),
            check(HB_KEY, "Enable Heartbeat Networking (HB)", false),
            // PC prepareHeartbeatMode: enabled only with HB and AUTO
            Field(HB_ACK_KEY, "Enable Heartbeat Acknowledgments (ACK)", Kind.CHECK, "false",
                active = { d -> d.bool(Field(AUTOREPLY_KEY, "", Kind.CHECK, "false")) && d.bool(Field(HB_KEY, "", Kind.CHECK, "false")) })
        )),
        Section("Mode", null, listOf(
            RadioGroup(
                Field(TX_SUBMODE_KEY, "", Kind.RADIO, "0",
                    load = { p -> Js8Activity.txSubmode(p.getInt(TX_SUBMODE_KEY, 0)).toString() },
                    save = { e, v, _ -> e.putInt(TX_SUBMODE_KEY, v.toIntOrNull() ?: 0) }), // engine reads an Int
                "Speed", SPEEDS, disabled = SPEEDS_DISABLED, vertical = true
            )
        )),
        // Moved off the main screen (Rob, 2026-10-19). The engine follows last_frequency (dial, CAT).
        Section("Frequency", null, listOf(
            Field(FREQUENCY_KEY, "Frequency:", Kind.CHOICE, "14078000", choices = { c, d -> frequencyChoices(c, d) }),
            text(CUSTOM_FREQUENCY_KEY, "Custom frequency (MHz):")
        )),
        Section("Offset", null, listOf(
            spin(TX_OFFSET_KEY, "Offset:", 1500, 0.0, 5000.0, suffix = " Hz") // PC offsetSpinBox: 0-5000 Hz
        )),
        // From Menu > View (Rob, 2026-10-19). The Clear buttons act at once, not on OK.
        Section("View", null, listOf(
            check("js8cfg_show_hb_acks", "Show Band Heartbeats and ACKs", false),
            // Band Activity columns, upright (Rob, 2026-10-19); same setting as press-and-hold on the header
            check("js8_table_${BAND_TABLE_UPRIGHT}_col_offset", "Show Offset in Band Activity", false),
            check("js8_table_${BAND_TABLE_UPRIGHT}_col_snr", "Show SNR in Band Activity", false),
            // FT8 signals heard on the JS8 frequency (Rob, 2026-10-19); off = the FT8 decoder doesn't run
            check("js8cfg_show_ft8", "Show FT8 decodes in the Message Window", true),
            ButtonRow(listOf("Clear Band Activity" to "clear_band")),
            ButtonRow(listOf("Clear Call Activity" to "clear_call")),
            ButtonRow(listOf("Clear RX Activity" to "clear_rx")),
            ButtonRow(listOf("Clear All Activity" to "clear_all"))
        ))
    )

    /** Js8TableView key of the upright Band Activity (its columns are js8_table_<key>_col_<column>). */
    const val BAND_TABLE_UPRIGHT = "band_upright"
    const val MULTI_KEY = "js8cfg_multi"
    /** Live AUTO switch: the engine's autoreply_enabled. */
    const val AUTOREPLY_KEY = "autoreply_enabled"
    /** Station > Turn autoreply on at startup: AUTO is set from it when the app starts. */
    const val AUTOREPLY_STARTUP_KEY = "js8cfg_autoreply_startup"
    const val HB_KEY = "js8cfg_hb"
    const val HB_ACK_KEY = "js8cfg_hb_ack"
    /** Keys whose change can grey or un-grey another setting (ACK). */
    val ACTIVE_TRIGGERS = setOf(AUTOREPLY_KEY, HB_KEY)

    const val FREQUENCY_KEY = "last_frequency"
    const val CUSTOM_FREQUENCY_KEY = "custom_frequency_mhz"
    /** TX offset in Hz, kept between starts like the PC. Waterfall taps write it too. */
    const val TX_OFFSET_KEY = "js8cfg_tx_offset"

    /**
     * The band list (a QMX shows only its band profile) plus "Custom - n MHz" when a custom
     * frequency is typed (JS8Android MonitorFragment.loadFrequencies).
     */
    private fun frequencyChoices(context: Context, d: Draft): List<Pair<String, String>> {
        val entries = context.resources.getStringArray(com.js8call.example.R.array.js8_frequency_entries)
        val values = context.resources.getStringArray(com.js8call.example.R.array.js8_frequency_values)
        val qmx = d.prefs.getString("rig_type", "none") == "qmx_serial"
        val qmxBands = when (d.prefs.getString("qmx_band_profile", "low")) {
            "high" -> setOf("20m", "17m", "15m", "12m", "11m", "10m")
            "mixed" -> setOf("60m", "40m", "30m", "20m", "17m", "15m")
            else -> setOf("80m", "60m", "40m", "30m", "20m")
        }
        val list = entries.indices.map { entries[it] to values[it] }
            .filter { !qmx || qmxBands.any { band -> it.first.startsWith(band) } }
            .toMutableList()
        val customMhz = (d.values[CUSTOM_FREQUENCY_KEY] ?: d.prefs.getString(CUSTOM_FREQUENCY_KEY, "")).orEmpty().trim()
        val customHz = customMhz.toDoubleOrNull()?.takeIf { it > 0 }?.let { (it * 1_000_000.0).toLong() }
        if (customHz != null) list += "Custom - ${customMhz}MHz" to customHz.toString()
        return list
    }

    /** Station window (Menu > File > Station…): the PC's File > Settings window. */
    fun stationSections(): List<Section> = listOf(
        Section("General", "Station", listOf(
            Group("Station Details", listOf(
                text("callsign", "My Callsign:", upper = true, required = true),
                check(SWL_KEY, "SWL (Listening only)", false),
                text("grid", "My Maidenhead Grid Locator:", upper = true, maxLength = 12, required = true), // PC ExtendedValidator: up to 12
                Special("grid_gps"), // Acquire GPS · grid found · Set (Easy JS8 only)
                text("my_groups", "Callsign Groups (comma separated):", hint = "@GROUP1, ...", upper = true),
                check("js8cfg_avoid_allcall", "Do not participate in the @ALLCALL group", false)
            )),
            Group("Station Messages", listOf(
                text("js8cfg_cq_message", "CQ Message:", "CQ CQ CQ <MYGRID4>", upper = true),
                text("js8cfg_reply_message", "Reply Message:", "HW CPY?", upper = true),
                text("my_info", "Station Info (Rig, Antenna, Location, etc):", upper = true),
                text("my_status", "Station Status (Weather, Idle Time, Version, etc):", "IDLE <MYIDLE> VERSION <MYVERSION>", upper = true)
            ))
        )),
        Section("General", "Behavior", listOf(
            Group("Behavior", listOf(
                check("js8cfg_miles", "Display distance in miles", false),
                check("js8cfg_monitor_off", "Receiver (RX) off at startup", false),
                check("js8cfg_transmit_off", "Transmitter (TX) off at startup", false),
                check("js8cfg_monitor_last_used", "Monitor returns to last used frequency", false),
                check("js8cfg_tx_qsy", "Allow Tx frequency changes while transmitting", false),
                check("js8cfg_avoid_forced_identify", "Allow sending standard messages without callsign", false),
                check("js8cfg_transmit_directed", "Immediately transmit CQ, Reply, Info, Saved, and Directed messages from the menu", true),
                check("log_messages_to_file", "Write log files (ALL.TXT, DIRECTED.TXT, etc) of decoded text", false),
                check("js8cfg_spellcheck", "Suggest alternative word choices for more efficient message transmission", false),
                check("js8cfg_reset_activity", "Reset the Band Activity, Call Activity, and RX history at startup", false)
            )),
            Group("Aging", listOf(
                spin("js8cfg_callsign_aging", "Remove callsigns from call activity after:", 0, 0.0, 1440.0, suffix = " minutes", special = "Disabled"),
                spin("js8cfg_activity_aging", "Remove messages from band activity after:", 2, 0.0, 1440.0, suffix = " minutes", special = "Disabled")
            )),
            Group("Customization", listOf(
                text("js8cfg_eot", "End of Transmission Character Indicator:", "♢"),
                text("js8cfg_mfi", "Missing Frame Character Indicator:", "……")
            )),
            Group("Heartbeat (HB)", listOf(
                // JS8Android default kept at Disabled (0) so a new install never transmits on its own
                spin("heartbeat_interval", "Heartbeat interval:", 0, 0.0, 1440.0, step = 5.0, prefix = "every ", suffix = " minutes", special = "Disabled")
            ))
        )),
        Section("General", "Networking & Autoreply", listOf(
            Group("Heartbeat Network", listOf(
                check("js8cfg_heartbeat_anywhere", "Allow heartbeat transmissions outside of heartbeat sub-channel (500Hz - 1000Hz)", false),
                check("js8cfg_heartbeat_qso_pause", "Pause heartbeat transmissions while in a QSO (i.e., callsign is selected)", true),
                check("js8cfg_heartbeat_ack_snr", "Send SNR report when acknowledging heartbeats", false),
                text("js8cfg_hb_blacklist", "Never acknowledge heartbeats from these callsigns (comma separated):", upper = true),
                check("js8cfg_hb_rate_limit", "Suppress ACK to stations that send heartbeats at less than 55 minute intervals", false)
            )),
            Group("Autoreply", listOf(
                // Only how AUTO starts (PC autoreply_on_at_startup); the live switch is Settings > AUTO
                check(AUTOREPLY_STARTUP_KEY, "Turn autoreply on at startup", false),
                // PC: "Disable relay" checked == JS8Android relay_enabled false
                Field("relay_enabled", "Disable message relay (>) when autoreply is enabled", Kind.CHECK, "true",
                    load = { p -> (!p.getBoolean("relay_enabled", false)).toString() },
                    save = { e, v, _ -> e.putBoolean("relay_enabled", v != "true") }),
                text("js8cfg_auto_whitelist", "Only autoreply to these callsigns (comma separated):", upper = true),
                text("js8cfg_auto_blacklist", "Never autoreply to these callsigns (comma separated):", upper = true),
                spin("js8cfg_tx_watchdog", "Idle timeout - disable autoreply after:", 60, 0.0, 1440.0, suffix = " minutes of inactivity", special = "Disabled"),
                check("js8cfg_autoreply_confirmation", "Ask for confirmation before sending autoreply transmissions", true)
            )),
            Group("Callsign Filtering", listOf(
                text("js8cfg_rx_blocklist", "Hide messages from these callsigns in the RX pane (comma separated):", upper = true)
            ))
        )),
        Section("Radio", null, listOf(
            Group(null, listOf(
                Field(RIG, "Rig:", Kind.CHOICE, "none", choices = { c, _ -> rigChoices(c) },
                    load = { p -> loadRig(p) }, save = { e, v, _ -> saveRig(e, v) }),
                spin("js8cfg_cat_poll_interval", "Poll Interval:", 1, 1.0, 99.0, suffix = " s"),
                ButtonRow(listOf("Test CAT" to "test_cat", "Test PTT" to "test_ptt"))
            ))
        )),
        Section("Radio", "CAT Control", listOf(
            Group(null, listOf(
                Field("rig_hamlib_usb_port", "Port:", Kind.CHOICE, "auto", choices = { c, _ -> portChoices(c) },
                    visible = { d -> rigIs(d, "hamlib", "rts_ptt") }),
                Field("rigctld_host", "Network Server:", Kind.TEXT, "localhost", visible = { d -> rigIs(d, "network") }),
                Field("rigctld_port", "Network Port:", Kind.TEXT, "4532", visible = { d -> rigIs(d, "network") }),
                Field("qmx_band_profile", "QMX Bands:", Kind.CHOICE, "low",
                    choices = { _, _ -> listOf("80/60/40/30/20m" to "low", "20/17/15/12/11/10m" to "high", "60/40/30/20/17/15m" to "mixed") },
                    visible = { d -> rigIs(d, "qmx_serial") }),
                Field("qmx_cat_baud", "QMX Baud Rate:", Kind.TEXT, "115200", visible = { d -> rigIs(d, "qmx_serial") })
            )),
            Group("Parameters", listOf(
                choice("rig_serial_baud", "Baud Rate:", "9600", same("1200", "2400", "4800", "9600", "19200", "38400", "57600", "115200")),
                RadioGroup(Field("js8cfg_data_bits", "", Kind.RADIO, "default",
                    load = { p -> p.getString("js8cfg_data_bits", null) ?: if (p.getString("rig_serial_data_bits", "8") == "7") "7" else "default" },
                    save = { e, v, _ -> e.putString("js8cfg_data_bits", v); e.putString("rig_serial_data_bits", if (v == "7") "7" else "8") }),
                    "Data Bits", listOf("Default" to "default", "Seven" to "7", "Eight" to "8")),
                RadioGroup(Field("js8cfg_stop_bits", "", Kind.RADIO, "default",
                    load = { p -> p.getString("js8cfg_stop_bits", null) ?: if (p.getString("rig_serial_stop_bits", "1") == "2") "2" else "default" },
                    save = { e, v, _ -> e.putString("js8cfg_stop_bits", v); e.putString("rig_serial_stop_bits", if (v == "2") "2" else "1") }),
                    "Stop Bits", listOf("Default" to "default", "One" to "1", "Two" to "2")),
                RadioGroup(radio("js8cfg_handshake", "default"), "Handshake",
                    listOf("Default" to "default", "None" to "none", "XON/XOFF" to "xonxoff", "Hardware" to "hardware")),
                // JS8Android needs parity for some rigs; the PC window has no parity setting
                RadioGroup(radio("rig_serial_parity", "none"), "Parity", listOf("None" to "none", "Odd" to "odd", "Even" to "even")),
                Group("Force Control Lines", listOf(
                    choice("js8cfg_force_dtr", "DTR:", "", listOf("" to "", "High" to "high", "Low" to "low")),
                    choice("js8cfg_force_rts", "RTS:", "", listOf("" to "", "High" to "high", "Low" to "low"))
                ))
            ), visible = { d -> rigIs(d, "hamlib", "rts_ptt") })
        )),
        Section("Radio", "Rig Options", listOf(
            RadioGroup(radio("js8cfg_ptt_method", "vox"), "PTT Method",
                listOf("VOX" to "vox", "DTR" to "dtr", "CAT" to "cat", "RTS" to "rts")),
            RadioGroup(radio("transmit_mode", "none"), "Mode", listOf("USB" to "usb", "None" to "none", "Data/Pkt" to "usb_data")),
            RadioGroup(radio("js8cfg_tx_audio_source", "rear"), "Transmit Audio Source", listOf("Rear/Data" to "rear", "Front/Mic" to "front")),
            RadioGroup(radio("js8cfg_split", "none"), "Split Operation", listOf("Fake It" to "emulate", "Rig" to "rig", "None" to "none")),
            Group("Advanced", listOf(
                spin("js8cfg_tx_delay", "Tx delay:", 0.2, 0.1, 0.5, step = 0.1, suffix = "  s", decimals = 1),
                check("js8cfg_hold_ptt", "Hold PTT between frames while there are more to be transmitted", false)
            ))
        )),
        Section("Audio", null, listOf(
            Group("Modulation Soundcard", listOf(
                Field("last_audio_device_id", "Input:", Kind.AUDIO_INPUT, "-1"),
                choice("js8cfg_input_channel", "Input channel:", "Mono", same("Mono", "Left", "Right", "Both")),
                Field("js8cfg_output_device", "Output:", Kind.CHOICE, "default",
                    choices = { _, _ -> listOf("Phone's current audio output" to "default") }, enabled = false),
                choice("js8cfg_output_channel", "Output channel:", "Mono", same("Mono", "Left", "Right", "Both"))
            )),
            Group("Notification Soundcard", listOf(
                Field("js8cfg_notification_output", "Output:", Kind.CHOICE, "default",
                    choices = { _, _ -> listOf("Phone's notification sound" to "default") }, enabled = false)
            )),
            Group("Remember power settings by band", listOf(
                check("js8cfg_pwr_band_tx", "Transmit", false),
                check("js8cfg_pwr_band_tune", "Tune", false)
            ))
        )),
        Section("Reporting", null, listOf(
            Group("Logging", listOf(
                text("js8cfg_op_call", "Operator Callsign (if different than Station Callsign):", upper = true)
            )),
            Group("Network Services", listOf(
                check("psk_reporter", "Enable spotting to reporting networks (JS8NET, PSKReporter, etc)", false),
                text("js8cfg_spot_blacklist", "Never send spotting reports from these callsigns (comma separated):", upper = true),
                check("aprs_is", "Enable spotting @APRSIS messages to the APRS-IS network", false),
                check("js8cfg_aprs_relay", "Enable relaying inbound APRS messages to Heard List stations", false),
                text("js8cfg_aprs_server", "APRS Server:", "rotate.aprs2.net"),
                spin("js8cfg_aprs_port", "APRS Server Port:", 14580, 0.0, 65534.0)
            ))
        )),
        Section("Frequencies", null, listOf(
            Group("Frequency Calibration", listOf(
                spin("js8cfg_cal_slope", "Slope:", 0.0, -999.9999, 999.9999, step = 0.1, suffix = " ppm", decimals = 4),
                spin("js8cfg_cal_intercept", "Intercept:", 0.0, -99999.99, 99999.99, step = 0.1, suffix = " Hz", decimals = 2)
            )),
            Group("Frequencies", listOf(
                Special("frequencies_table") // Custom frequency (MHz) moved to Settings > Frequency
            )),
            Group("Frequency Schedule", listOf(
                check("js8cfg_auto_switch_bands", "Automatically switch bands / frequencies at specific times of day", false),
                Special("schedule_table")
            ))
        )),
        Section("Saved Messages", null, listOf(Special("saved_messages"))),
        Section("Notifications", null, listOf(
            check("js8cfg_notifications", "Enable Notifications", false),
            Special("notifications_table")
        )),
        Section("UI", "Band & Call Activity", listOf(Special("ui_band_call"))),
        Section("UI", "Messages", listOf(Special("ui_messages"))),
        Section("UI", "Composition", listOf(Special("ui_composition"))),
        Section("Diagnostics", null, listOf(
            Group("System, Device, and Application Diagnostics", listOf(
                ButtonRow(listOf("Run Diagnostics" to "diag_run", "Copy to Clipboard" to "diag_copy")),
                Special("diagnostics_text")
            ))
        )),
        // Settings JS8Android has that the PC window does not
        Section("Phone", null, listOf(
            Group("Easy JS8", listOf(
                choice(Js8MainFragment.PREF_LANDSCAPE_LAYOUT, "Sideways layout:", "pc", listOf("PC" to "pc", "Swipe" to "swipe")),
                check("tx_boost_enabled", "Transmit audio boost", false),
                check("keep_screen_on", "Keep screen on", false),
                check("persist_decodes", "Keep decoded messages after restart", false),
                check("time_sync_auto", "Automatic time sync", false)
            ))
        ))
    )

    /** Saved Messages default list (Configuration.cpp: "TNX 73 GL"). */
    const val SAVED_MESSAGES_KEY = "js8cfg_saved_messages"
    const val SAVED_MESSAGES_DEFAULT = "TNX 73 GL"

    /** Notification events (Configuration.cpp notifyRows). */
    val NOTIFY_EVENTS = listOf(
        "cq" to "CQ Message Received", "hb" to "HB Message Received", "ack" to "ACK Message Received",
        "directed" to "Directed Message Received", "inbox" to "Inbox Message Received",
        "call_new" to "New Callsign Heard", "call_old" to "Worked Callsign Heard"
    )
}
