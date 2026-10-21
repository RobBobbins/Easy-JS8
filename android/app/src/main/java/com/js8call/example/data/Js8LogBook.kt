package com.js8call.example.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The PC's log (JS8Call 3.0.3 JS8_Logbook/ADIF.cpp, LogBook.cpp, JS8_UI/LogQSO.cpp accept):
 * js8call_log.adi (ADIF, one record per line) and js8call.log (comma separated), both in the
 * app's own files folder (the PC's AppLocalDataLocation). Read back for Call Activity's
 * ✓ (worked before), Name and Comment columns.
 */
object Js8LogBook {
    const val ADIF_FILE = "js8call_log.adi"
    const val CSV_FILE = "js8call.log"

    data class Qso(val call: String, val band: String, val mode: String, val submode: String,
                   val grid: String, val date: String, val name: String, val comment: String)

    @Volatile private var loaded: Map<String, List<Qso>>? = null

    fun adifFile(context: Context) = File(context.filesDir, ADIF_FILE)
    fun csvFile(context: Context) = File(context.filesDir, CSV_FILE)

    private val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    private val timeFormat = SimpleDateFormat("HHmmss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

    /** ADIF::QSOToADIF. */
    fun qsoToAdif(
        call: String, grid: String, mode: String, submode: String, rptSent: String, rptRcvd: String,
        onMs: Long, offMs: Long, band: String, comments: String, name: String, dialMhz: String,
        myCall: String, myGrid: String, operatorCall: String, additional: Map<String, String>
    ): String {
        fun f(k: String, v: String) = " <$k:${v.length}>$v"
        val sb = StringBuilder("<call:${call.length}>$call")
        sb.append(f("gridsquare", grid)).append(f("mode", mode))
        if (submode.isNotEmpty()) sb.append(f("submode", submode))
        sb.append(f("rst_sent", rptSent)).append(f("rst_rcvd", rptRcvd))
        sb.append(" <qso_date:8>").append(dateFormat.format(Date(onMs)))
        sb.append(" <time_on:6>").append(timeFormat.format(Date(onMs)))
        sb.append(" <qso_date_off:8>").append(dateFormat.format(Date(offMs)))
        sb.append(" <time_off:6>").append(timeFormat.format(Date(offMs)))
        sb.append(f("band", band)).append(f("freq", dialMhz))
        sb.append(f("station_callsign", myCall)).append(f("my_gridsquare", myGrid))
        if (comments.isNotEmpty()) sb.append(f("comment", comments))
        if (name.isNotEmpty()) sb.append(f("name", name))
        if (operatorCall.isNotEmpty()) sb.append(f("operator", operatorCall))
        for ((key, value) in additional) {
            val k = key.uppercase(Locale.US)
            if (k in ADIF_FIELDS) sb.append(" <$k:${value.length}>$value")
            else sb.append(" <APP_JS8CALL_$k:${value.length}>$value")
        }
        return sb.toString()
    }

    /** ADIF::addQSOToFile: a new file starts with the PC's header line. */
    fun addToAdif(context: Context, record: String): Boolean = try {
        val f = adifFile(context)
        val header = if (!f.exists() || f.length() == 0L) "JS8Call ADIF Export<eoh>\n" else ""
        f.appendText("$header$record <eor>\n", Charsets.ISO_8859_1)
        true
    } catch (e: Exception) { false }

    /** LogQSO::accept: one comma separated line in js8call.log. */
    fun addToCsv(context: Context, items: List<String>): Boolean = try {
        csvFile(context).appendText(items.joinToString(",") + "\n", Charsets.UTF_8)
        true
    } catch (e: Exception) { false }

    /** LogBook::addAsWorked. */
    fun addAsWorked(context: Context, q: Qso) {
        val map = load(context).toMutableMap()
        map[q.call] = (map[q.call] ?: emptyList()) + q
        loaded = map
    }

    /** LogBook::hasWorkedBefore(call, ""): any band. */
    fun workedBefore(context: Context, call: String) = load(context).containsKey(call)

    /** LogBook::findCallDetails: the first non-empty grid, date, name and comment. */
    fun details(context: Context, call: String): Qso? {
        val qsos = load(context)[call] ?: return null
        fun first(sel: (Qso) -> String) = qsos.map(sel).firstOrNull { it.isNotEmpty() }.orEmpty()
        return Qso(call, "", "", "", first { it.grid }, first { it.date }, first { it.name }, first { it.comment })
    }

    /** Erase js8call_log.adi (and forget the calls in it). */
    fun forget() { loaded = null }

    /** ADIF::load: optional header up to <EOH>, records up to <EOR>. */
    @Synchronized
    fun load(context: Context): Map<String, List<Qso>> {
        loaded?.let { return it }
        val map = mutableMapOf<String, MutableList<Qso>>()
        val f = adifFile(context)
        if (f.exists()) {
            var text = runCatching { f.readText(Charsets.ISO_8859_1) }.getOrDefault("")
            if (!text.startsWith("<")) {
                val eoh = text.indexOf("<EOH>", ignoreCase = true)
                if (eoh >= 0) text = text.substring(eoh + 5)
            }
            text.split(Regex("<EOR>", RegexOption.IGNORE_CASE)).forEach { raw ->
                val record = raw.substring(raw.indexOf('<').coerceAtLeast(0))
                val call = field(record, "CALL")
                if (call.isNotEmpty()) {
                    map.getOrPut(call) { mutableListOf() } += Qso(call, field(record, "BAND"), field(record, "MODE"),
                        field(record, "SUBMODE"), field(record, "GRIDSQUARE"), field(record, "QSO_DATE"),
                        field(record, "NAME"), field(record, "COMMENT"))
                }
            }
        }
        loaded = map
        return map
    }

    /** ADIF::extractField: <NAME:length> or <NAME:length:type>. */
    private fun field(record: String, name: String): String {
        val at = record.indexOf("<$name:", ignoreCase = true)
        if (at < 0) return ""
        val close = record.indexOf('>', at)
        if (close < 0) return ""
        val spec = record.substring(at + name.length + 2, close)
        val length = spec.substringBefore(':').toIntOrNull() ?: return ""
        if (length <= 0) return ""
        return record.substring(close + 1, (close + 1 + length).coerceAtMost(record.length))
    }

    /** ADIF 3.1.0 field names (ADIF.cpp ADIF_FIELDS), for Additional Fields. */
    val ADIF_FIELDS = listOf(
        "APP", "ADDRESS", "ADDRESS_INTL", "AGE", "A_INDEX", "ANT_AZ", "ANT_EL", "ANT_PATH", "ARRL_SECT",
        "AWARD_SUBMITTED", "AWARD_GRANTED", "BAND", "BAND_RX", "CALL", "CHECK", "CLASS", "CLUBLOG_QSO_UPLOAD_DATE",
        "CLUBLOG_QSO_UPLOAD_STATUS", "CNTY", "COMMENT", "COMMENT_INTL", "CONT", "CONTACTED_OP", "CONTEST_ID",
        "COUNTRY", "COUNTRY_INTL", "CQZ", "CREDIT_SUBMITTED", "CREDIT_GRANTED", "DARC_DOK", "DISTANCE", "DXCC",
        "EMAIL", "EQ_CALL", "EQSL_QSLRDATE", "EQSL_QSLSDATE", "EQSL_QSL_RCVD", "EQSL_QSL_SENT", "FISTS", "FISTS_CC",
        "FORCE_INIT", "FREQ", "FREQ_RX", "GRIDSQUARE", "GUEST_OP", "HRDLOG_QSO_UPLOAD_DATE", "HRDLOG_QSO_UPLOAD_STATUS",
        "IOTA", "IOTA_ISLAND_ID", "ITUZ", "K_INDEX", "LAT", "LON", "LOTW_QSLRDATE", "LOTW_QSLSDATE", "LOTW_QSL_RCVD",
        "LOTW_QSL_SENT", "MAX_BURSTS", "MODE", "MS_SHOWER", "MY_ANTENNA", "MY_ANTENNA_INTL", "MY_CITY", "MY_CITY_INTL",
        "MY_CNTY", "MY_COUNTRY", "MY_COUNTRY_INTL", "MY_CQ_ZONE", "MY_DXCC", "MY_FISTS", "MY_GRIDSQUARE", "MY_IOTA",
        "MY_IOTA_ISLAND_ID", "MY_ITU_ZONE", "MY_LAT", "MY_LON", "MY_NAME", "MY_NAME_INTL", "MY_POSTAL_CODE",
        "MY_POSTAL_CODE_INTL", "MY_RIG", "MY_RIG_INTL", "MY_SIG", "MY_SIG_INTL", "MY_SIG_INFO", "MY_SIG_INFO_INTL",
        "MY_SOTA_REF", "MY_STATE", "MY_STREET", "MY_STREET_INTL", "MY_USACA_COUNTIES", "MY_VUCC_GRIDS", "NAME",
        "NAME_INTL", "NOTES", "NOTES_INTL", "NR_BURSTS", "NR_PINGS", "OPERATOR", "OWNER_CALLSIGN", "PFX", "PRECEDENCE",
        "PROP_MODE", "PUBLIC_KEY", "QRZCOM_QSO_UPLOAD_DATE", "QRZCOM_QSO_UPLOAD_STATUS", "QSLMSG", "QSLMSG_INTL",
        "QSLRDATE", "QSLSDATE", "QSL_RCVD", "QSL_RCVD_VIA", "QSL_SENT", "QSL_SENT_VIA", "QSL_VIA", "QSO_COMPLETE",
        "QSO_DATE", "QSO_DATE_OFF", "QSO_RANDOM", "QTH", "QTH_INTL", "REGION", "RIG", "RIG_INTL", "RST_RCVD", "RST_SENT",
        "RX_PWR", "SAT_MODE", "SAT_NAME", "SFI", "SIG", "SIG_INTL", "SIG_INFO", "SIG_INFO_INTL", "SILENT_KEY", "SKCC",
        "SOTA_REF", "SRX", "SRX_STRING", "STATE", "STATION_CALLSIGN", "STX", "STX_STRING", "SUBMODE", "SWL", "TEN_TEN",
        "TIME_OFF", "TIME_ON", "TX_PWR", "UKSMG", "USACA_COUNTIES", "VUCC_GRIDS", "WEB"
    )
}
