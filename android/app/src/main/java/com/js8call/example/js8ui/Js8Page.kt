package com.js8call.example.js8ui

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.CompoundButtonCompat
import com.js8call.example.R

/**
 * Widgets for the Easy JS8 pages (Log QSO, Export Options): the same look as the APRS pages and
 * the Settings window. ← Back at the top, the page title, a scrolling page, the buttons pinned
 * to the bottom right.
 */
class Js8Page(private val context: Context) {
    private val dp = context.resources.displayMetrics.density
    val font: Typeface? = ResourcesCompat.getFont(context, R.font.selawik)
    val textPx = context.resources.getDimension(R.dimen.js8_text)
    fun color(id: Int) = ContextCompat.getColor(context, id)
    fun px(v: Int) = (v * dp).toInt()

    /** Page frame: ← Back (works like Cancel), [title], [content] scrolling, [buttons] at the bottom. */
    fun frame(title: String, onBack: () -> Unit, content: LinearLayout, buttons: List<Pair<String, () -> Unit>>): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(color(R.color.js8_frame_bg))
        }
        root.addView(label("← Back", size = 1.15f, bold = true).apply {
            setPadding(px(12), 0, px(12), 0)
            minHeight = px(40)
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener { onBack() }
        })
        val page = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(12), px(4), px(12), px(12))
        }
        page.addView(label(title, size = 1.15f, bold = true).apply { setPadding(0, 0, 0, px(8)) })
        page.addView(content)
        root.addView(ScrollView(context).apply { addView(page) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(px(10), px(8), px(10), px(8))
        }
        buttons.forEachIndexed { i, (text, action) ->
            bar.addView(button(text, action), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(30)).apply {
                if (i > 0) marginStart = px(8)
            })
        }
        root.addView(bar)
        return root
    }

    fun column() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    fun label(text: String, size: Float = 1f, bold: Boolean = false) = TextView(context).apply {
        this.text = text
        typeface = if (bold) Typeface.create(font, Typeface.BOLD) else font
        setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx * size)
        setTextColor(color(R.color.js8_frame_text))
        includeFontPadding = false
    }

    fun box(type: Int) = EditText(context).apply {
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

    /** Label above its control, like the Station window upright. */
    fun field(text: String, control: View) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, px(4), 0, px(4))
        addView(label(text))
        addView(control, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(34)).apply { topMargin = px(4) })
    }

    /** Control with a button on its right (Start: [date time] [Now]). */
    fun withButton(control: View, buttonText: String, onClick: () -> Unit) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(control, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        addView(button(buttonText, onClick), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(30)).apply {
            marginStart = px(8)
        })
    }

    fun checkBox(text: String, checked: Boolean) = CheckBox(context).apply {
        this.text = text
        typeface = font
        setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx)
        setTextColor(color(R.color.js8_frame_text))
        CompoundButtonCompat.setButtonTintList(this, android.content.res.ColorStateList.valueOf(color(R.color.js8_cfg_group_border)))
        isChecked = checked
    }

    fun button(text: String, onClick: () -> Unit) = AppCompatButton(context, null, 0).apply {
        this.text = text
        setBackgroundResource(R.drawable.js8_btn_blue)
        setTextColor(ContextCompat.getColorStateList(context, R.color.js8_btn_text))
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

    /** Box with a thin border and its title on the top edge (Qt group box), like the Settings window. */
    fun groupBox(title: String, content: LinearLayout): View {
        val frame = android.widget.FrameLayout(context)
        content.setBackgroundResource(R.drawable.js8_cfg_group)
        content.setPadding(px(10), px(16), px(10), px(10))
        frame.addView(content, android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = px(9)
        })
        frame.addView(label(title).apply {
            setPadding(px(3), 0, px(3), 0)
            setBackgroundColor(color(R.color.js8_frame_bg))
        }, android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginStart = px(10)
        })
        frame.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = px(6); bottomMargin = px(8)
        }
        return frame
    }
}
