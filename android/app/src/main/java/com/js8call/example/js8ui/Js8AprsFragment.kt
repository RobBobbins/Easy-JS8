package com.js8call.example.js8ui

import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.Spanned
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.js8call.example.R
import java.util.Locale

/**
 * Saved > APRS EMAIL / APRS TXT / APRS GRID / APRS MSG (Rob, 2026-10-19). Easy JS8 only: the PC
 * has no APRS forms, you type the @APRSIS commands yourself (JS8Call User Guide, "@JS8NET and
 * @APRSIS"). OK builds the line, puts it in the Typing Box and returns; it does not send.
 *
 * APRS rules (APRS Protocol Reference 1.2, chapter 14): addressee padded to 9 characters,
 * message text at most 67 characters, no { in the text, message number { + up to 5 characters.
 */
class Js8AprsFragment : Fragment() {

    companion object {
        const val ARG_FORM = "form"
        const val FORM_EMAIL = "email"
        const val FORM_TXT = "txt"
        const val FORM_GRID = "grid"
        const val FORM_MSG = "msg"
        /** Fragment result: the line for the Typing Box, under RESULT_TEXT. */
        const val RESULT_KEY = "js8_aprs"
        const val RESULT_TEXT = "text"
        /** Last message number used (1–99), shared by EMAIL, TXT and MSG. */
        const val NUMBER_KEY = "js8cfg_aprs_msg_number"
        private const val MAX_TEXT = 67

        /** Next message number: 01 to 99, then 01 again. */
        fun nextNumber(last: Int) = last.coerceIn(0, 99) % 99 + 1

        /** APRS addressee field: 9 characters, padded with spaces. */
        fun addressee(name: String) = name.padEnd(9).take(9)
    }

    private val form get() = arguments?.getString(ARG_FORM) ?: FORM_MSG
    private val dp get() = resources.displayMetrics.density
    private val font by lazy { ResourcesCompat.getFont(requireContext(), R.font.selawik) }
    private val textPx get() = resources.getDimension(R.dimen.js8_text)
    private fun color(id: Int) = ContextCompat.getColor(requireContext(), id)
    private fun px(v: Int) = (v * dp).toInt()
    private fun prefs() = PreferenceManager.getDefaultSharedPreferences(requireContext())

    /** Email address / phone number / callsign / grid. */
    private lateinit var toBox: EditText
    private var messageBox: EditText? = null
    private var countLabel: TextView? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val title = when (form) {
            FORM_EMAIL -> "APRS EMAIL"
            FORM_TXT -> "APRS TXT"
            FORM_GRID -> "APRS GRID"
            else -> "APRS MSG"
        }
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(color(R.color.js8_frame_bg))
        }
        // ← Back at the top, like the Settings and Station windows (works like Cancel)
        root.addView(label("← Back", size = 1.15f, bold = true).apply {
            setPadding(px(12), 0, px(12), 0)
            minHeight = px(40)
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener { findNavController().popBackStack() }
        })

        val page = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(12), px(4), px(12), px(12))
        }
        page.addView(label(title, size = 1.15f, bold = true).apply { setPadding(0, 0, 0, px(8)) })

        when (form) {
            FORM_EMAIL -> toBox = box(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
            FORM_TXT -> toBox = box(InputType.TYPE_CLASS_PHONE).apply {
                filters = arrayOf(digitsOnly(), InputFilter.LengthFilter(15))
            }
            FORM_GRID -> toBox = box(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS).apply {
                filters = arrayOf(InputFilter.AllCaps(), InputFilter.LengthFilter(12))
                if (savedInstanceState == null) setText(prefs().getString("grid", "").orEmpty().trim().uppercase(Locale.US))
            }
            else -> toBox = box(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS).apply {
                filters = arrayOf(InputFilter.AllCaps(), InputFilter.LengthFilter(9))
            }
        }
        toBox.id = R.id.js8_aprs_to
        page.addView(field(when (form) {
            FORM_EMAIL -> "To (email address):"
            FORM_TXT -> "Phone number:"
            FORM_GRID -> "Grid:"
            else -> "To callsign:"
        }, toBox))

        if (form != FORM_GRID) {
            val msg = box(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS).apply {
                id = R.id.js8_aprs_message
                filters = arrayOf(InputFilter.AllCaps(), messageFilter())
            }
            messageBox = msg
            page.addView(field("Message:", msg))
            countLabel = label("").apply { gravity = Gravity.END }
            page.addView(countLabel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            val watcher = object : TextWatcher {
                override fun afterTextChanged(s: Editable?) = renderCount()
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            }
            toBox.addTextChangedListener(watcher)
            msg.addTextChangedListener(watcher)
        }

        root.addView(ScrollView(requireContext()).apply { addView(page) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // OK / Cancel pinned to the bottom, right-aligned, like the Station window
        val bar = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(px(10), px(8), px(10), px(8))
        }
        bar.addView(button("OK") { onOk() }, LinearLayout.LayoutParams(px(64), px(30)))
        bar.addView(button("Cancel") { findNavController().popBackStack() },
            LinearLayout.LayoutParams(px(64), px(30)).apply { marginStart = px(8) })
        root.addView(bar)
        return root
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        renderCount()
    }

    // ---- Building the line ------------------------------------------------------------------

    private fun to() = toBox.text.toString().trim().uppercase(Locale.US)
    private fun message() = messageBox?.text?.toString()?.trim().orEmpty()

    /** The part of the APRS message text before the typed message: "BOB@MAIL.COM ", "@5195551212 ". */
    private fun textPrefix(to: String = to()) = when (form) {
        FORM_EMAIL -> if (to.isEmpty()) "" else "$to "
        FORM_TXT -> if (to.isEmpty()) "" else "@$to "
        else -> ""
    }

    /** Characters of APRS message text (the 67 limit includes the email address or phone number). */
    private fun textLength() = textPrefix().length + (messageBox?.text?.length ?: 0)

    private fun renderCount() {
        val count = countLabel ?: return
        val n = textLength()
        count.text = "$n/$MAX_TEXT"
        count.setTextColor(if (n > MAX_TEXT) 0xFFD00000.toInt() else color(R.color.js8_frame_text))
    }

    private fun onOk() {
        val to = to()
        val message = message()
        val problem = when (form) {
            FORM_EMAIL -> if (!Regex("[^@\\s{]+@[^@\\s{]+\\.[^@\\s{]+").matches(to)) "Enter an email address" else null
            FORM_TXT -> if (to.length < 7) "Enter a phone number" else null
            FORM_GRID -> if (!Js8Grid.isValid(to)) "The grid you provided is not valid" else null
            else -> if (!Regex("[A-Z0-9]{1,6}(-[A-Z0-9]{1,2})?").matches(to)) "Enter a callsign (e.g. VA3XYZ or VA3XYZ-9)" else null
        } ?: when {
            form != FORM_GRID && message.isEmpty() -> "Enter a message"
            form != FORM_GRID && textLength() > MAX_TEXT -> "The message is too long: ${textLength()}/$MAX_TEXT characters"
            else -> null
        }
        if (problem != null) {
            AlertDialog.Builder(requireContext()).setMessage(problem).setPositiveButton(android.R.string.ok, null).show()
            return
        }

        val line = if (form == FORM_GRID) {
            "@APRSIS GRID $to"
        } else {
            val prefs = prefs()
            val number = nextNumber(prefs.getInt(NUMBER_KEY, 0))
            prefs.edit().putInt(NUMBER_KEY, number).apply()
            val destination = when (form) {
                FORM_EMAIL -> "EMAIL-2"
                FORM_TXT -> "SMSGTE"
                else -> to
            }
            "@APRSIS CMD :${addressee(destination)}:${textPrefix(to)}$message{" + String.format(Locale.US, "%02d", number)
        }
        parentFragmentManager.setFragmentResult(RESULT_KEY, bundleOf(RESULT_TEXT to line))
        findNavController().popBackStack()
    }

    // ---- Filters ----------------------------------------------------------------------------

    /** Message box: no { (it starts the message number), and at most 67 characters of APRS text. */
    private fun messageFilter() = InputFilter { source, start, end, dest, dstart, dend ->
        val typed = source.subSequence(start, end).toString().replace("{", "")
        val room = MAX_TEXT - textPrefix().length - (dest.length - (dend - dstart))
        when {
            room <= 0 -> ""
            typed.length > room -> typed.take(room)
            typed.length != end - start -> typed
            else -> null
        }
    }

    private fun digitsOnly() = InputFilter { source, start, end, _: Spanned, _, _ ->
        val digits = source.subSequence(start, end).filter { it.isDigit() }
        if (digits.length == end - start) null else digits
    }

    // ---- Widgets (same look as the Settings window) ----------------------------------------

    private fun label(text: String, size: Float = 1f, bold: Boolean = false) = TextView(requireContext()).apply {
        this.text = text
        typeface = if (bold) android.graphics.Typeface.create(font, android.graphics.Typeface.BOLD) else font
        setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx * size)
        setTextColor(color(R.color.js8_frame_text))
        includeFontPadding = false
    }

    private fun box(type: Int) = EditText(requireContext()).apply {
        setBackgroundResource(R.drawable.js8_cfg_field)
        typeface = font
        setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx)
        setTextColor(color(R.color.js8_frame_text))
        setHintTextColor(color(R.color.js8_cfg_placeholder))
        setPadding(px(8), 0, px(8), 0)
        gravity = Gravity.CENTER_VERTICAL
        isSingleLine = true
        inputType = type
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
    }

    /** Label above its box, like the Station window upright. */
    private fun field(text: String, control: View) = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, px(4), 0, px(4))
        addView(label(text))
        addView(control, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(34)).apply { topMargin = px(4) })
    }

    private fun button(text: String, onClick: () -> Unit) = AppCompatButton(requireContext(), null, 0).apply {
        this.text = text
        setBackgroundResource(R.drawable.js8_btn_blue)
        setTextColor(ContextCompat.getColorStateList(requireContext(), R.color.js8_btn_text))
        typeface = font
        setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx)
        isAllCaps = false
        gravity = Gravity.CENTER
        includeFontPadding = false
        minHeight = 0; minimumHeight = 0; minWidth = 0; minimumWidth = 0
        setPadding(px(10), 0, px(10), 0)
        stateListAnimator = null
        setOnClickListener { onClick() }
    }
}
