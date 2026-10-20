package com.js8call.example.js8ui

import android.app.AlertDialog
import android.content.SharedPreferences
import android.content.res.Configuration
import android.view.View
import android.widget.CheckBox
import android.widget.SeekBar
import android.widget.TextView
import com.js8call.example.R
import com.js8call.example.ui.WaterfallPalettes
import com.js8call.example.ui.WaterfallRenderer
import com.js8call.example.ui.WaterfallView

/**
 * Waterfall Controls > Display tab, first part (JS8_UI/WideGraph.ui displayTab): Start, Bins Per
 * Pixel, Palette, Scroll Speed, Flatten, Waterfall Gain / Zero, Filter Opacity. Kept between
 * starts with the PC's defaults. Bins Per Pixel is kept apart for upright (6) and sideways (2,
 * the PC's) so the narrow upright waterfall starts with a useful range (Rob, 2026-10-20).
 */
class Js8WaterfallDisplay(
    private val root: View,
    private val prefs: SharedPreferences,
    private val waterfall: WaterfallView,
    stepper: (Int, Int, (Int) -> Unit) -> Unit
) {
    companion object {
        const val KEY_START = "js8cfg_wf_start"
        const val KEY_BPP_UPRIGHT = "js8cfg_wf_bpp_upright"
        const val KEY_BPP_SIDEWAYS = "js8cfg_wf_bpp_sideways"
        const val KEY_PALETTE = "js8cfg_wf_palette"
        const val KEY_FPS = "js8cfg_wf_fps"
        const val KEY_FLATTEN = "js8cfg_wf_flatten"
        const val KEY_GAIN = "js8cfg_wf_gain"
        const val KEY_ZERO = "js8cfg_wf_zero"
        const val KEY_OPACITY = "js8cfg_filter_opacity"

        // WideGraph.ui ranges
        private const val START_MAX = 5000
        private const val BPP_MAX = 1000
        private const val FPS_MAX = 100
    }

    private val context = root.context
    private fun <T : View> v(id: Int): T? = root.findViewById(id)

    private val upright = root.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
    private val bppKey = if (upright) KEY_BPP_UPRIGHT else KEY_BPP_SIDEWAYS

    private var start = prefs.getInt(KEY_START, 500).coerceIn(0, START_MAX)
    private var bpp = prefs.getInt(bppKey, if (upright) 6 else 2).coerceIn(1, BPP_MAX)
    private var palette = prefs.getString(KEY_PALETTE, WaterfallPalettes.DEFAULT) ?: WaterfallPalettes.DEFAULT
    private var colours = WaterfallPalettes.colours(context, palette)
    private var fps = prefs.getInt(KEY_FPS, 4).coerceIn(1, FPS_MAX)
    private var flatten = prefs.getBoolean(KEY_FLATTEN, true)
    private var gain = prefs.getInt(KEY_GAIN, 0).coerceIn(-50, 50)
    private var zero = prefs.getInt(KEY_ZERO, 0).coerceIn(-50, 50)
    private var opacity = prefs.getInt(KEY_OPACITY, 50).coerceIn(0, 100)

    init {
        // Start: 100 Hz a step (WideGraph.ui singleStep)
        stepper(R.id.js8_wf_start_up, R.id.js8_wf_start_down) { step ->
            start = (start + Integer.signum(step) * 100).coerceIn(0, START_MAX)
            prefs.edit().putInt(KEY_START, start).apply()
            apply()
        }
        stepper(R.id.js8_wf_bpp_up, R.id.js8_wf_bpp_down) { step ->
            bpp = (bpp + step).coerceIn(1, BPP_MAX)
            prefs.edit().putInt(bppKey, bpp).apply()
            apply()
        }
        stepper(R.id.js8_wf_fps_up, R.id.js8_wf_fps_down) { step ->
            fps = (fps + step).coerceIn(1, FPS_MAX)
            prefs.edit().putInt(KEY_FPS, fps).apply()
            apply()
        }
        stepper(R.id.js8_wf_opacity_up, R.id.js8_wf_opacity_down) { step ->
            opacity = (opacity + step).coerceIn(0, 100)
            prefs.edit().putInt(KEY_OPACITY, opacity).apply()
            apply()
        }
        v<TextView>(R.id.js8_wf_palette)?.setOnClickListener { pickPalette() }
        v<CheckBox>(R.id.js8_wf_flatten)?.apply {
            isChecked = flatten
            setOnCheckedChangeListener { _, on ->
                flatten = on
                prefs.edit().putBoolean(KEY_FLATTEN, on).apply()
                apply()
            }
        }
        slider(R.id.js8_wf_gain, gain) { gain = it; prefs.edit().putInt(KEY_GAIN, it).apply(); apply() }
        slider(R.id.js8_wf_zero, zero) { zero = it; prefs.edit().putInt(KEY_ZERO, it).apply(); apply() }
        apply()
    }

    /** -50..50 slider (QSlider gainSlider / zeroSlider). */
    private fun slider(id: Int, value: Int, onChange: (Int) -> Unit) {
        v<SeekBar>(id)?.apply {
            max = 100
            progress = value + 50
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) { if (fromUser) onChange(p - 50) }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }
    }

    private fun pickPalette() {
        val names = WaterfallPalettes.names(context)
        AlertDialog.Builder(context)
            .setTitle("Palette")
            .setSingleChoiceItems(names.toTypedArray(), names.indexOf(palette)) { d, which ->
                d.dismiss()
                palette = names[which]
                colours = WaterfallPalettes.colours(context, palette)
                prefs.edit().putString(KEY_PALETTE, palette).apply()
                apply()
            }.show()
    }

    private fun apply() {
        v<TextView>(R.id.js8_wf_start)?.text = "Start: $start Hz"
        v<TextView>(R.id.js8_wf_bpp)?.text = "Bins Per Pixel: $bpp"
        v<TextView>(R.id.js8_wf_palette)?.text = "$palette  ▾"
        v<TextView>(R.id.js8_wf_fps)?.text = "Scroll Speed: $fps fps"
        v<TextView>(R.id.js8_wf_opacity)?.text = "$opacity%"
        waterfall.filterOpacityPercent = opacity
        waterfall.setDisplay(WaterfallRenderer.Display(
            startHz = start, binsPerPixel = bpp, fps = fps, gain = gain, zero = zero,
            flatten = flatten, colours = colours, density = root.resources.displayMetrics.density
        ))
    }
}
