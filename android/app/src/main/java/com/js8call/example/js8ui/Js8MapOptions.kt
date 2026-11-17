package com.js8call.example.js8ui

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.CompoundButtonCompat
import androidx.preference.PreferenceManager
import com.js8call.example.R

/**
 * Map options box (Rob, 2026-11-17): the gear above + opens the five Station > Layout > Map settings over the map.
 * Each change is saved at once and the map redraws; a tap outside the box closes it.
 */
object Js8MapOptions {

    fun show(anchor: View, map: View) {
        val ctx = anchor.context
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val dp = ctx.resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()
        val font = ResourcesCompat.getFont(ctx, R.font.selawik)
        val textPx = ctx.resources.getDimension(R.dimen.js8_text)
        val black = ContextCompat.getColor(ctx, R.color.js8_frame_text)

        fun label(t: String, bold: Boolean = false) = TextView(ctx).apply {
            text = t; typeface = if (bold) Typeface.create(font, Typeface.BOLD) else font
            setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx); setTextColor(black)
            setPadding(0, px(6), 0, px(2))
        }

        fun choice(key: String, title: String, default: String, opts: List<Pair<String, String>>) = TextView(ctx).apply {
            fun current() = prefs.all[key]?.toString() ?: default
            fun show() { text = "${opts.firstOrNull { it.second == current() }?.first ?: current()}  ▾" }
            setBackgroundResource(R.drawable.js8_cfg_field)
            typeface = font; setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx); setTextColor(black)
            setPadding(px(8), 0, px(8), 0); gravity = Gravity.CENTER_VERTICAL; isSingleLine = true
            minHeight = px(36)
            show()
            setOnClickListener {
                AlertDialog.Builder(ctx)
                    .setTitle(title.trimEnd(':'))
                    .setSingleChoiceItems(opts.map { it.first }.toTypedArray(), opts.indexOfFirst { it.second == current() }) { d, which ->
                        d.dismiss()
                        prefs.edit().putString(key, opts[which].second).apply()
                        show(); map.invalidate()
                    }.show()
            }
        }

        fun check(key: String, title: String, default: Boolean) = CheckBox(ctx).apply {
            text = title; typeface = font; setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx); setTextColor(black)
            CompoundButtonCompat.setButtonTintList(this,
                android.content.res.ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.js8_cfg_group_border)))
            isChecked = prefs.all[key]?.toString()?.toBooleanStrictOrNull() ?: default
            setOnCheckedChangeListener { _, c -> prefs.edit().putBoolean(key, c).apply(); map.invalidate() }
        }

        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(12), px(6), px(12), px(8))
            addView(label("Map", bold = true))
            addView(label("Lines:"))
            addView(choice(Js8MapView.PREF_LINES, "Lines:", "arrows", Js8MapView.LINE_CHOICES),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(label("Remove stations from map after:"))
            addView(choice(Js8MapView.PREF_AGING, "Remove stations from map after:", "0", Js8MapView.AGING_CHOICES),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(check(Js8MapView.PREF_CALLS, "Show callsigns", true))
            addView(check(Js8MapView.PREF_STATES, "Show state and province lines", true))
            addView(check(Js8MapView.PREF_GRIDS, "Show grid squares", false))
        }
        val box = ScrollView(ctx).apply {
            setBackgroundResource(R.drawable.js8_cfg_field)
            addView(col)
        }

        // Left of the buttons, top at the map's top
        val mapAt = IntArray(2).also { map.getLocationInWindow(it) }
        val anchorAt = IntArray(2).also { anchor.getLocationInWindow(it) }
        val width = px(300)
        val maxH = map.height - px(16)
        box.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(maxH, View.MeasureSpec.AT_MOST))
        PopupWindow(box, width, box.measuredHeight.coerceAtMost(maxH), true).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = px(6).toFloat()
            showAtLocation(anchor, Gravity.TOP or Gravity.START, anchorAt[0] - width - px(8), mapAt[1] + px(8))
        }
    }
}
