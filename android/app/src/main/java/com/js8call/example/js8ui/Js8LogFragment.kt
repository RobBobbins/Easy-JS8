package com.js8call.example.js8ui

import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.js8call.example.api.Js8Api
import com.js8call.example.data.Js8LogBook
import com.js8call.example.util.BandUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Log QSO (JS8Call 3.0.3 JS8_UI/LogQSO.ui / LogQSO.cpp, UI_Constructor::on_logQSOButton_clicked
 * and acceptQSO): LOG button, PC mode Log menu, Call Activity > Log.... Add to Log writes
 * js8call_log.adi and js8call.log, marks the call worked, and sends the contact to LOG.QSO,
 * N1MM, N3FJP and WSJT-X (Js8Api.logQso).
 *
 * Opened again for the same callsign before it was logged, the page keeps what was typed
 * (the PC keeps its window as it was).
 */
class Js8LogFragment : Fragment() {

    companion object {
        const val ARG_CALL = "call"
        const val ARG_GRID = "grid"
        const val ARG_RPT_SENT = "rpt_sent"
        const val ARG_START_MS = "start_ms"
        const val ARG_FREQ_HZ = "freq_hz"
        /** Fragment result after Add to Log: the main window clears its selection (PC clearCallsignSelected). */
        const val RESULT_KEY = "js8_log_accepted"

        private const val KEY_RETAIN = "js8cfg_log_retain_comments"   // LogQSO/SaveComments
        private const val KEY_COMMENTS = "js8cfg_log_comments"        // LogQSO/LogComments
        private const val KEY_FIELDS = "js8cfg_log_additional_fields" // LogQSO/AdditionalFields

        /** What was typed, kept while the call stays the same and it is not logged yet. */
        private var draft: Draft? = null
    }

    private class Draft(
        var call: String, var op: String, var start: String, var end: String, var mode: String, var band: String,
        var sent: String, var rcvd: String, var grid: String, var name: String, var comments: String,
        var retain: Boolean, val fields: MutableList<Pair<String, String>>, var freqHz: Long
    )

    private val utc = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC"); isLenient = false }
    private lateinit var ui: Js8Page
    private lateinit var d: Draft
    private lateinit var fieldsBox: LinearLayout
    private lateinit var fieldsGroup: View
    private val boxes = mutableMapOf<String, EditText>()
    private val fieldRows = mutableListOf<Pair<EditText, EditText>>()

    private fun prefs() = PreferenceManager.getDefaultSharedPreferences(requireContext())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        ui = Js8Page(requireContext())
        d = draftFor(arguments ?: Bundle())

        val page = ui.column()
        fun text(key: String, label: String, value: String, caps: Boolean = true, max: Int = 0): EditText {
            val b = ui.box(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
                (if (caps) InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS else 0)).apply {
                setText(value)
                val f = mutableListOf<InputFilter>()
                if (caps) f += InputFilter.AllCaps()
                if (max > 0) f += InputFilter.LengthFilter(max)
                filters = f.toTypedArray()
            }
            boxes[key] = b
            page.addView(ui.field(label, b))
            return b
        }
        text("op", "Operator Callsign:", d.op)
        text("call", "Contact Callsign:", d.call)
        val start = ui.box(InputType.TYPE_CLASS_DATETIME).apply { setText(d.start) }
        boxes["start"] = start
        page.addView(ui.field("Start:", ui.withButton(start, "Now") { start.setText(utc.format(Date())) }))
        val end = ui.box(InputType.TYPE_CLASS_DATETIME).apply { setText(d.end) }
        boxes["end"] = end
        page.addView(ui.field("End:", ui.withButton(end, "Now") { end.setText(utc.format(Date())) }))
        text("mode", "Mode:", d.mode)
        text("band", "Band:", d.band, caps = false)
        text("sent", "Signal Report Sent:", d.sent)
        text("rcvd", "Signal Report Received:", d.rcvd)
        text("grid", "Grid:", d.grid, max = 8) // Maidenhead::StandardValidator: up to 8
        text("name", "Name:", d.name, caps = false)
        text("comments", "Comments:", d.comments, caps = false)
        val retain = ui.checkBox("Retain Comments", d.retain)
        retain.setOnCheckedChangeListener { _, c -> d.retain = c }
        page.addView(retain)

        // Additional Fields (hidden until there is one) and Add New Field
        fieldsBox = ui.column()
        fieldsGroup = ui.groupBox("Additional Fields", fieldsBox)
        page.addView(fieldsGroup)
        d.fields.forEach { (k, v) -> addFieldRow(k, v) }
        fieldsGroup.visibility = if (fieldRows.isEmpty()) View.GONE else View.VISIBLE
        page.addView(LinearLayout(requireContext()).apply {
            addView(ui.button("Add New Field") { addFieldRow("", "").first.requestFocus() },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ui.px(30)))
            setPadding(0, ui.px(6), 0, ui.px(6))
        })

        return ui.frame("Log QSO", onBack = { leave() }, content = page,
            buttons = listOf("Add to Log" to { addToLog() }, "Cancel" to { leave() }))
    }

    /** initLogQSO: new values unless the same callsign is still waiting to be logged. */
    private fun draftFor(args: Bundle): Draft {
        val call = args.getString(ARG_CALL).orEmpty()
        draft?.let { if (it.call.isNotEmpty() && it.call == call) return it }
        val p = prefs()
        val myCall = p.getString("callsign", "").orEmpty().trim().uppercase(Locale.US)
        val op = p.getString("js8cfg_op_call", "").orEmpty().trim().uppercase(Locale.US).ifEmpty { myCall }
        val now = System.currentTimeMillis()
        val startMs = args.getLong(ARG_START_MS, now).let { if (it <= 0) now else it }
        val freq = args.getLong(ARG_FREQ_HZ, Js8Api.dialHz())
        val retain = p.getBoolean(KEY_RETAIN, false)
        // AdditionalFields: the field names used last time, values empty
        val keys = p.getString(KEY_FIELDS, "").orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        return Draft(
            call = call, op = op, start = utc.format(Date(startMs)), end = utc.format(Date(maxOf(now, startMs))),
            mode = "JS8", band = BandUtils.bandLabelForFrequencyHz(freq), sent = args.getString(ARG_RPT_SENT).orEmpty(),
            rcvd = "", grid = args.getString(ARG_GRID).orEmpty(), name = "",
            comments = if (retain) p.getString(KEY_COMMENTS, "").orEmpty() else "",
            retain = retain, fields = keys.map { it to "" }.toMutableList(), freqHz = freq
        ).also { draft = it }
    }

    /** createAdditionalField: an ADIF field name (type it or pick from the list) and its value. */
    private fun addFieldRow(key: String, value: String): Pair<EditText, EditText> {
        val keyBox = ui.box(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS).apply {
            setText(key)
            filters = arrayOf(InputFilter.AllCaps())
        }
        val valueBox = ui.box(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS).apply { setText(value) }
        val pick = ui.withButton(keyBox, "▾") {
            val names = Js8LogBook.ADIF_FIELDS
            AlertDialog.Builder(requireContext()).setTitle("Field")
                .setSingleChoiceItems(names.toTypedArray(), names.indexOf(keyBox.text.toString())) { dlg, i ->
                    dlg.dismiss(); keyBox.setText(names[i]); valueBox.requestFocus()
                }.show()
        }
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, ui.px(4), 0, ui.px(4))
            addView(pick, LinearLayout.LayoutParams(0, ui.px(34), 1f))
            addView(valueBox, LinearLayout.LayoutParams(0, ui.px(34), 1f).apply { marginStart = ui.px(8) })
        }
        fieldsBox.addView(row)
        fieldRows += keyBox to valueBox
        fieldsGroup.visibility = View.VISIBLE
        return keyBox to valueBox
    }

    private fun value(key: String) = boxes[key]?.text?.toString().orEmpty().trim()

    /** Keeps what was typed (Cancel, ← Back, phone back) for when the page opens again. */
    private fun saveDraft() {
        d.op = value("op"); d.call = value("call"); d.start = value("start"); d.end = value("end")
        d.mode = value("mode"); d.band = value("band"); d.sent = value("sent"); d.rcvd = value("rcvd")
        d.grid = value("grid"); d.name = value("name"); d.comments = value("comments")
        d.fields.clear()
        fieldRows.forEach { (k, v) -> d.fields += k.text.toString().trim() to v.text.toString() }
    }

    /** storeSettings (hideEvent): Retain Comments, the comments, the additional field names. */
    private fun storeSettings() {
        prefs().edit()
            .putBoolean(KEY_RETAIN, d.retain)
            .putString(KEY_FIELDS, d.fields.map { it.first }.filter { it.isNotEmpty() }.distinct().joinToString(","))
            .apply()
    }

    private fun leave() {
        saveDraft()
        storeSettings()
        findNavController().popBackStack()
    }

    override fun onPause() {
        if (::d.isInitialized && view != null) saveDraft()
        super.onPause()
    }

    /** LogQSO::accept, then UI_Constructor::acceptQSO. */
    private fun addToLog() {
        saveDraft()
        val ctx = requireContext()
        val onMs = runCatching { utc.parse(d.start)!!.time }.getOrNull()
        val offMs = runCatching { utc.parse(d.end)!!.time }.getOrNull()
        if (onMs == null || offMs == null) {
            message(null, "Enter Start and End as yyyy-MM-dd HH:mm:ss (UTC)")
            return
        }
        val call = d.call.uppercase(Locale.US)
        val grid = d.grid.uppercase(Locale.US)
        var mode = d.mode.uppercase(Locale.US)
        var submode = ""
        if (mode == "JS8") { mode = "MFSK"; submode = "JS8" }
        val dialMhz = String.format(Locale.US, "%.6f", d.freqHz / 1e6)
        val p = prefs()
        val myCall = p.getString("callsign", "").orEmpty().trim().uppercase(Locale.US)
        val myGrid = Js8Api.myGrid(p)
        // collectAdditionalFields: rows without a field name are left out
        val additional = linkedMapOf<String, String>()
        d.fields.forEach { (k, v) -> if (k.isNotEmpty()) additional[k] = v }

        val adif = Js8LogBook.qsoToAdif(call, grid, mode, submode, d.sent, d.rcvd, onMs, offMs, d.band, d.comments,
            d.name, dialMhz, myCall, myGrid, d.op, additional)
        if (!Js8LogBook.addToAdif(ctx, adif)) {
            message("Log file error", "Cannot open \"${Js8LogBook.adifFile(ctx).absolutePath}\"")
        }
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val line = listOf(date.format(Date(onMs)), time.format(Date(onMs)), date.format(Date(offMs)), time.format(Date(offMs)),
            call, grid, dialMhz, if (mode == "MFSK") "JS8" else mode, d.sent, d.rcvd, d.comments, d.name) + additional.values
        if (!Js8LogBook.addToCsv(ctx, line)) {
            message("Log file error", "Cannot open \"${Js8LogBook.csvFile(ctx).absolutePath}\" for append")
        }
        Js8LogBook.addAsWorked(ctx, Js8LogBook.Qso(call, d.band, mode, submode, grid,
            SimpleDateFormat("yyyyMMdd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(onMs)),
            d.name, d.comments))
        Js8Api.logQso(Js8Api.Qso(
            call = call, grid = grid, dialHz = d.freqHz, mode = mode, submode = submode, rptSent = d.sent,
            rptReceived = d.rcvd, name = d.name, comments = d.comments, timeOnMs = onMs, timeOffMs = offMs,
            operatorCall = d.op, myCall = myCall, myGrid = myGrid, adif = adif, extra = additional
        )) { title, text -> context?.let { AlertDialog.Builder(it).setTitle(title).setMessage(text).setPositiveButton(android.R.string.ok, null).show() } }

        // m_comments is kept for Retain Comments; the call is cleared (the next LOG starts fresh)
        p.edit().putString(KEY_COMMENTS, d.comments).apply()
        storeSettings()
        draft = null
        parentFragmentManager.setFragmentResult(RESULT_KEY, bundleOf(ARG_CALL to call))
        findNavController().popBackStack()
    }

    private fun message(title: String?, text: String) {
        AlertDialog.Builder(requireContext()).apply { title?.let { setTitle(it) } }
            .setMessage(text).setPositiveButton(android.R.string.ok, null).show()
    }
}
