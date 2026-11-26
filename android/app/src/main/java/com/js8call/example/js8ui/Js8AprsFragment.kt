package com.js8call.example.js8ui

import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.js8call.example.R
import java.util.Locale

/**
 * Saved > APRS EMAIL / APRS TXT / APRS GRID / APRS MSG (Rob, 2026-10-19). Easy JS8 only: the PC
 * has no APRS forms, you type the @APRSIS commands yourself (JS8Call User Guide, "@JS8NET and
 * @APRSIS"). Insert builds the line, puts it in the Typing Box and returns; it does not send.
 *
 * Android look like the Station and Settings windows (Rob, 2026-11-26): ← and the title in the top
 * bar, Insert at its right end (greyed until the form is valid), outlined boxes, problems in red
 * under the box while typing, the 67 count as the Message box's counter. No Cancel: ← leaves.
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
    private fun px(v: Int) = (v * dp).toInt()
    private fun prefs() = PreferenceManager.getDefaultSharedPreferences(requireContext())
    /** The app theme's Material colours (light and dark), as the Station window uses. */
    private fun attr(a: Int) = MaterialColors.getColor(requireContext(), a, Color.GRAY)
    private val onSurface get() = attr(com.google.android.material.R.attr.colorOnSurface)

    /** Email address / phone number / callsign / grid. */
    private lateinit var toBox: EditText
    private lateinit var toLayout: TextInputLayout
    private var messageBox: EditText? = null
    private var messageLayout: TextInputLayout? = null
    private var insertButton: MaterialButton? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val title = when (form) {
            FORM_EMAIL -> "APRS EMAIL"
            FORM_TXT -> "APRS TXT"
            FORM_GRID -> "APRS GRID"
            else -> "APRS MSG"
        }
        val sideways = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(attr(com.google.android.material.R.attr.colorSurface))
        }
        root.addView(topBar(title, sideways), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val page = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(16), px(8), px(16), px(16))
        }

        val (toName, toType) = when (form) {
            FORM_EMAIL -> "To (email address)" to (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
            FORM_TXT -> "Phone number" to InputType.TYPE_CLASS_PHONE
            FORM_GRID -> "Grid" to (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS)
            else -> "To callsign" to (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS)
        }
        toLayout = outlined(toName)
        toBox = box(toLayout, toType).apply {
            id = R.id.js8_aprs_to
            when (form) {
                FORM_TXT -> filters = arrayOf(digitsOnly(), InputFilter.LengthFilter(15))
                FORM_GRID -> {
                    filters = arrayOf(InputFilter.AllCaps(), InputFilter.LengthFilter(12))
                    if (savedInstanceState == null) setText(prefs().getString("grid", "").orEmpty().trim().uppercase(Locale.US))
                }
                FORM_MSG -> filters = arrayOf(InputFilter.AllCaps(), InputFilter.LengthFilter(9))
            }
        }
        page.addView(toLayout, fieldParams())

        if (form != FORM_GRID) {
            val layout = outlined("Message").apply {
                // The 67 counts the email address or phone number too (textPrefix), so the counter is ours
                isCounterEnabled = true
                counterMaxLength = MAX_TEXT
                setLengthCounter { textLength() }
            }
            messageLayout = layout
            messageBox = box(layout, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS).apply {
                id = R.id.js8_aprs_message
                filters = arrayOf(InputFilter.AllCaps(), messageFilter())
            }
            page.addView(layout, fieldParams())
        }

        val watcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = check()
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        }
        toBox.addTextChangedListener(watcher)
        messageBox?.addTextChangedListener(watcher)

        root.addView(ScrollView(requireContext()).apply { addView(page) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        check()
    }

    // ---- Top bar ----------------------------------------------------------------------------

    /** ← and the title like the Station window; Insert at the right end (Rob, 2026-11-26). */
    private fun topBar(title: String, sideways: Boolean): View = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = px(if (sideways) 48 else 56)
        setPadding(px(4), 0, px(8), 0)
        addView(ImageView(requireContext()).apply {
            setImageResource(R.drawable.js8_pref_back)
            imageTintList = ColorStateList.valueOf(onSurface)
            contentDescription = "Back"
            setPadding(px(12), px(12), px(12), px(12))
            setBackgroundResource(themeDrawable(android.R.attr.selectableItemBackgroundBorderless))
            setOnClickListener { findNavController().popBackStack() }
        }, LinearLayout.LayoutParams(px(48), px(48)))
        addView(TextView(requireContext()).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTextColor(onSurface)
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(px(12), 0, px(12), 0)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(MaterialButton(requireContext(), null, androidx.appcompat.R.attr.borderlessButtonStyle).apply {
            id = R.id.js8_aprs_insert
            text = "Insert"
            isEnabled = false
            setOnClickListener { onInsert() }
            insertButton = this
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun themeDrawable(a: Int): Int {
        val tv = TypedValue()
        requireContext().theme.resolveAttribute(a, tv, true)
        return tv.resourceId
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

    /** The To box's problem, or null. */
    private fun toProblem(to: String = to()) = when (form) {
        FORM_EMAIL -> if (!Regex("[^@\\s{]+@[^@\\s{]+\\.[^@\\s{]+").matches(to)) "Enter an email address" else null
        FORM_TXT -> if (to.length < 7) "Enter a phone number" else null
        FORM_GRID -> if (!Js8Grid.isValid(to)) "The grid you provided is not valid" else null
        else -> if (!Regex("[A-Z0-9]{1,6}(-[A-Z0-9]{1,2})?").matches(to)) "Enter a callsign (e.g. VA3XYZ or VA3XYZ-9)" else null
    }

    /** The Message box's problem, or null. */
    private fun messageProblem() = when {
        form == FORM_GRID -> null
        message().isEmpty() -> "Enter a message"
        textLength() > MAX_TEXT -> "The message is too long: ${textLength()}/$MAX_TEXT characters"
        else -> null
    }

    /**
     * Red text under a box once something is typed in it (an empty form opens without red);
     * Insert greyed until both boxes are right.
     */
    private fun check() {
        if (!::toLayout.isInitialized) return
        val toBad = toProblem()
        val msgBad = messageProblem()
        toLayout.error = if (toBox.text.isNullOrEmpty()) null else toBad
        messageLayout?.let { layout ->
            layout.error = if (messageBox?.text.isNullOrEmpty() && textLength() <= MAX_TEXT) null else msgBad
            // The To box changes the count too: switching the counter off and on redraws it
            layout.isCounterEnabled = false
            layout.isCounterEnabled = true
        }
        insertButton?.isEnabled = toBad == null && msgBad == null
    }

    private fun onInsert() {
        check()
        if (toProblem() != null || messageProblem() != null) return
        val to = to()
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
            "@APRSIS CMD :${addressee(destination)}:${textPrefix(to)}${message()}{" + String.format(Locale.US, "%02d", number)
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

    // ---- Widgets (Android outlined boxes, as the Station window's popups) -------------------

    private fun outlined(name: String) = TextInputLayout(requireContext()).apply {
        boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        hint = name
    }

    private fun box(layout: TextInputLayout, type: Int) = TextInputEditText(layout.context).apply {
        isSingleLine = true
        inputType = type
        // Android 8.0+ only (the app runs on 7.0 since 2026-10-22)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        layout.addView(this, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun fieldParams() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        .apply { topMargin = px(8) }
}
