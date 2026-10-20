package com.js8call.example.js8ui

import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.js8call.example.R
import com.js8call.example.service.JS8EngineService
import com.js8call.example.ui.WaterfallPalettes
import com.js8call.example.ui.WaterfallRenderer
import com.js8call.example.ui.WaterfallView

/**
 * Waterfall Controls > Display tab (JS8_UI/WideGraph.ui displayTab): Size, Palette with the Palette
 * Designer (Adjust...), Scrolling & Averaging, Waterfall, Spectrum and Filter. Kept between
 * starts with the PC's defaults. Bins Per Pixel is kept apart for upright (6) and sideways (2,
 * the PC's) so the narrow upright waterfall starts with a useful range (Rob, 2026-10-20).
 */
class Js8WaterfallDisplay(
    private val root: View,
    private val prefs: SharedPreferences,
    private val waterfall: WaterfallView,
    stepper: (Int, Int, (Int) -> Unit) -> Unit,
    /** Palette Designer > Import...: pick a .pal file, hand back its colours. */
    private val importPalette: ((List<Int>) -> Unit) -> Unit,
    /** Palette Designer > Export...: save this text as a .pal file. */
    private val exportPalette: (String) -> Unit
) {
    companion object {
        const val KEY_START = "js8cfg_wf_start"
        const val KEY_BPP_UPRIGHT = "js8cfg_wf_bpp_upright"
        const val KEY_BPP_SIDEWAYS = "js8cfg_wf_bpp_sideways"
        const val KEY_PALETTE = "js8cfg_wf_palette"
        const val KEY_USER_PALETTE = "js8cfg_wf_user_palette"
        const val KEY_FPS = "js8cfg_wf_fps"
        const val KEY_AVG = "js8cfg_wf_avg"
        const val KEY_FLATTEN = "js8cfg_wf_flatten"
        const val KEY_SPECTRUM = "js8cfg_wf_spectrum"
        const val KEY_SMOOTH = "js8cfg_wf_smooth"
        const val KEY_GAIN = "js8cfg_wf_gain"
        const val KEY_ZERO = "js8cfg_wf_zero"
        const val KEY_DECODE_ATTEMPTS = "js8cfg_wf_decode_attempts"
        const val KEY_HEIGHT = "js8cfg_wf_spec_height"
        const val KEY_GAIN2D = "js8cfg_wf_spec_gain"
        const val KEY_ZERO2D = "js8cfg_wf_spec_zero"
        const val KEY_OPACITY = "js8cfg_filter_opacity"

        // WideGraph.ui ranges
        private const val START_MAX = 5000
        private const val BPP_MAX = 1000
        private const val FPS_MAX = 100
        private const val AVG_MAX = 50
        private const val SMOOTH_MAX = 7
        private val SPECTRA = listOf("Current", "Cumulative", "Linear Avg")
        /** WF.cpp points */
        private const val MAX_COLOURS = 256
    }

    private val context = root.context
    private fun <T : View> v(id: Int): T? = root.findViewById(id)

    private val upright = root.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
    private val bppKey = if (upright) KEY_BPP_UPRIGHT else KEY_BPP_SIDEWAYS

    private var start = prefs.getInt(KEY_START, 500).coerceIn(0, START_MAX)
    private var bpp = prefs.getInt(bppKey, if (upright) 6 else 2).coerceIn(1, BPP_MAX)
    private var palette = prefs.getString(KEY_PALETTE, WaterfallPalettes.DEFAULT) ?: WaterfallPalettes.DEFAULT
    private var userColours = WaterfallPalettes.parse(prefs.getString(KEY_USER_PALETTE, "").orEmpty())
    private var colours = paletteColours()
    private var fps = prefs.getInt(KEY_FPS, 4).coerceIn(1, FPS_MAX)
    private var avg = prefs.getInt(KEY_AVG, 1).coerceIn(1, AVG_MAX)
    private var flatten = prefs.getBoolean(KEY_FLATTEN, true)
    private var spectrum = prefs.getInt(KEY_SPECTRUM, WaterfallRenderer.SPECTRUM_CURRENT).coerceIn(0, 2)
    private var smooth = prefs.getInt(KEY_SMOOTH, 1).coerceIn(1, SMOOTH_MAX)
    private var gain = prefs.getInt(KEY_GAIN, 0).coerceIn(-50, 50)
    private var zero = prefs.getInt(KEY_ZERO, 0).coerceIn(-50, 50)
    private var decodeAttempts = prefs.getBoolean(KEY_DECODE_ATTEMPTS, false)
    private var height = prefs.getInt(KEY_HEIGHT, 0).coerceIn(0, 100)
    private var gain2d = prefs.getInt(KEY_GAIN2D, 0).coerceIn(-50, 50)
    private var zero2d = prefs.getInt(KEY_ZERO2D, 0).coerceIn(-50, 50)
    private var opacity = prefs.getInt(KEY_OPACITY, 50).coerceIn(0, 100)

    init {
        // Start: 100 Hz a step; Height: 5 % a step (WideGraph.ui singleStep)
        stepper(R.id.js8_wf_start_up, R.id.js8_wf_start_down) { step ->
            start = (start + Integer.signum(step) * 100).coerceIn(0, START_MAX); save(KEY_START, start)
        }
        stepper(R.id.js8_wf_bpp_up, R.id.js8_wf_bpp_down) { step ->
            bpp = (bpp + step).coerceIn(1, BPP_MAX); save(bppKey, bpp)
        }
        stepper(R.id.js8_wf_fps_up, R.id.js8_wf_fps_down) { step ->
            fps = (fps + step).coerceIn(1, FPS_MAX); save(KEY_FPS, fps)
        }
        stepper(R.id.js8_wf_avg_up, R.id.js8_wf_avg_down) { step ->
            avg = (avg + step).coerceIn(1, AVG_MAX); save(KEY_AVG, avg)
        }
        stepper(R.id.js8_wf_smooth_up, R.id.js8_wf_smooth_down) { step ->
            if (spectrum != WaterfallRenderer.SPECTRUM_LINEAR_AVG) return@stepper // greyed
            smooth = (smooth + Integer.signum(step)).coerceIn(1, SMOOTH_MAX); save(KEY_SMOOTH, smooth)
        }
        stepper(R.id.js8_wf_height_up, R.id.js8_wf_height_down) { step ->
            height = (height + Integer.signum(step) * 5).coerceIn(0, 100); save(KEY_HEIGHT, height)
        }
        stepper(R.id.js8_wf_opacity_up, R.id.js8_wf_opacity_down) { step ->
            opacity = (opacity + step).coerceIn(0, 100); save(KEY_OPACITY, opacity)
        }
        v<TextView>(R.id.js8_wf_palette)?.setOnClickListener { pickPalette() }
        v<View>(R.id.js8_wf_palette_adjust)?.setOnClickListener { PaletteDesigner(userColours).show() }
        v<TextView>(R.id.js8_wf_spectrum)?.setOnClickListener { pickSpectrum() }
        check(R.id.js8_wf_flatten, flatten) { flatten = it; prefs.edit().putBoolean(KEY_FLATTEN, it).apply(); apply() }
        check(R.id.js8_wf_decode_attempts, decodeAttempts) {
            decodeAttempts = it; prefs.edit().putBoolean(KEY_DECODE_ATTEMPTS, it).apply(); sendSyncStats()
        }
        slider(R.id.js8_wf_gain, gain) { gain = it; save(KEY_GAIN, it) }
        slider(R.id.js8_wf_zero, zero) { zero = it; save(KEY_ZERO, it) }
        slider(R.id.js8_wf_gain2d, gain2d) { gain2d = it; save(KEY_GAIN2D, it) }
        slider(R.id.js8_wf_zero2d, zero2d) { zero2d = it; save(KEY_ZERO2D, it) }
        sendSyncStats()
        apply()
    }

    private fun save(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
        apply()
    }

    private fun check(id: Int, value: Boolean, onChange: (Boolean) -> Unit) {
        v<CheckBox>(id)?.apply {
            isChecked = value
            setOnCheckedChangeListener { _, on -> onChange(on) }
        }
    }

    /** -50..50 slider (QSlider gainSlider / zeroSlider / gain2dSlider / zero2dSlider). */
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

    /** Display Decode Attempts: the decoder reports its tries only while checked (syncStats). */
    private fun sendSyncStats() {
        context.startService(Intent(context, JS8EngineService::class.java).apply {
            action = JS8EngineService.ACTION_SET_SYNC_STATS
            putExtra(JS8EngineService.EXTRA_SYNC_STATS_ON, decodeAttempts)
        })
    }

    private fun paletteColours(): IntArray =
        if (palette == WaterfallPalettes.USER_DEFINED) WaterfallPalettes.fromList(userColours)
        else WaterfallPalettes.colours(context, palette)

    private fun pickPalette() {
        // The PC's list: the palette files, then User Defined
        val names = WaterfallPalettes.names(context) + WaterfallPalettes.USER_DEFINED
        v<TextView>(R.id.js8_wf_palette)?.let { dropDown(it, names, names.indexOf(palette)) { i -> setPalette(names[i]) } }
    }

    /**
     * Combo box list (QComboBox): opens under [anchor], as wide as it, 8 entries tall and scrolls;
     * the entry in use is highlighted. A tap picks and closes; a tap anywhere else just closes.
     */
    private fun dropDown(anchor: TextView, entries: List<String>, selected: Int, onPick: (Int) -> Unit) {
        val dp = root.resources.displayMetrics.density
        val rowHeight = (30 * dp).toInt()
        val popup = android.widget.ListPopupWindow(context)
        popup.anchorView = anchor
        popup.width = anchor.width
        popup.height = if (entries.size > 8) rowHeight * 8 else ViewGroup.LayoutParams.WRAP_CONTENT
        popup.isModal = true
        popup.setBackgroundDrawable(androidx.core.content.ContextCompat.getDrawable(context, R.drawable.js8_spin_field))
        popup.setAdapter(object : android.widget.BaseAdapter() {
            override fun getCount() = entries.size
            override fun getItem(position: Int) = entries[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                ((convertView as? TextView) ?: TextView(context).apply {
                    layoutParams = android.widget.AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowHeight)
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding((8 * dp).toInt(), 0, (8 * dp).toInt(), 0)
                    setSingleLine()
                    typeface = anchor.typeface
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, anchor.textSize)
                }).apply {
                    text = entries[position]
                    val inUse = position == selected
                    setBackgroundColor(if (inUse) androidx.core.content.ContextCompat.getColor(context, R.color.js8_blue) else Color.TRANSPARENT)
                    setTextColor(if (inUse) Color.BLACK else anchor.currentTextColor)
                }
        })
        popup.setOnItemClickListener { _, _, position, _ ->
            popup.dismiss()
            onPick(position)
        }
        popup.show()
        if (selected >= 0) popup.listView?.setSelection((selected - 3).coerceAtLeast(0))
    }

    private fun setPalette(name: String) {
        palette = name
        colours = paletteColours()
        prefs.edit().putString(KEY_PALETTE, palette).apply()
        apply()
    }

    private fun pickSpectrum() {
        v<TextView>(R.id.js8_wf_spectrum)?.let {
            dropDown(it, SPECTRA, spectrum) { which -> spectrum = which; save(KEY_SPECTRUM, spectrum) }
        }
    }

    private fun apply() {
        v<TextView>(R.id.js8_wf_start)?.text = "Start: $start Hz"
        v<TextView>(R.id.js8_wf_bpp)?.text = "Bins Per Pixel: $bpp"
        v<TextView>(R.id.js8_wf_palette)?.text = "$palette  ▾"
        v<TextView>(R.id.js8_wf_fps)?.text = "Scroll Speed: $fps fps"
        v<TextView>(R.id.js8_wf_avg)?.text = "Avg N: $avg"
        v<TextView>(R.id.js8_wf_spectrum)?.text = "${SPECTRA[spectrum]}  ▾"
        v<TextView>(R.id.js8_wf_smooth)?.text = "Smooth:  $smooth"
        // on_spec2dComboBox_currentIndexChanged: Smooth only for Linear Avg
        val smoothOn = spectrum == WaterfallRenderer.SPECTRUM_LINEAR_AVG
        v<View>(R.id.js8_wf_smooth_box)?.let { it.isEnabled = smoothOn; it.alpha = if (smoothOn) 1f else 0.45f }
        v<TextView>(R.id.js8_wf_height)?.text = "$height %"
        v<TextView>(R.id.js8_wf_opacity)?.text = "$opacity%"
        waterfall.filterOpacityPercent = opacity
        waterfall.setDisplay(WaterfallRenderer.Display(
            startHz = start, binsPerPixel = bpp, fps = fps, avg = avg, gain = gain, zero = zero,
            flatten = flatten, spectrum = spectrum, smooth = smooth, percent2D = height,
            gain2D = gain2d, zero2D = zero2d, colours = colours, density = root.resources.displayMetrics.density
        ))
    }

    /** The app's blue button (Js8Button), for the Palette Designer and Select Color windows. */
    private fun button(text: String, onClick: () -> Unit): View {
        val dp = root.resources.displayMetrics.density
        return androidx.appcompat.widget.AppCompatButton(context, null, 0).apply {
            this.text = text
            setBackgroundResource(R.drawable.js8_btn_blue)
            setTextColor(androidx.core.content.ContextCompat.getColorStateList(context, R.color.js8_btn_text))
            typeface = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.selawik)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, root.resources.getDimension(R.dimen.js8_text))
            isAllCaps = false
            gravity = Gravity.CENTER
            includeFontPadding = false
            minHeight = 0; minimumHeight = 0; minWidth = 0; minimumWidth = 0
            setPadding((8 * dp).toInt(), 0, (8 * dp).toInt(), 0)
            stateListAnimator = null
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, root.resources.getDimensionPixelSize(R.dimen.js8_button_height))
                .apply { marginStart = (6 * dp).toInt() }
        }
    }

    // ---- Palette Designer (WF.cpp Designer, wf_palette_design_dialog.ui) ------------------

    /**
     * The colour list, weak signals at the top. Tap a colour to change it (the PC's double click);
     * press and hold for Delete / Insert ... / Insert after ... (the PC's right-click menu).
     * Import... / Export... read and write .pal files. OK makes it the User Defined palette.
     */
    private inner class PaletteDesigner(start: List<Int>) {
        private val list = start.toMutableList()
        private val dp = root.resources.displayMetrics.density
        private val rows = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            // The PC's menu also opens on empty space (Insert ... at the end)
            setOnLongClickListener { menu(this, null); true }
        }
        private val table = ScrollView(context).apply {
            setBackgroundResource(R.drawable.js8_spin_field)
            isFillViewport = true
            addView(rows, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }

        fun show() {
            render()
            lateinit var dialog: AlertDialog
            // wf_palette_design_dialog.ui: the table on the left, the button box as a column on the
            // right; a vertical QDialogButtonBox on Windows lists Import... / Export... (action
            // buttons), then OK, Cancel, from the top
            val buttons = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(button("Import...") {
                    importPalette { loaded ->
                        if (loaded.size > MAX_COLOURS) {
                            Toast.makeText(context, "Too many colours in palette.", Toast.LENGTH_LONG).show()
                        } else {
                            list.clear(); list.addAll(loaded); render()
                        }
                    }
                })
                addView(button("Export...") { exportPalette(WaterfallPalettes.format(list)) })
                addView(button("OK") {
                    userColours = list.toList()
                    prefs.edit().putString(KEY_USER_PALETTE, WaterfallPalettes.format(userColours)).apply()
                    setPalette(WaterfallPalettes.USER_DEFINED)
                    dialog.dismiss()
                })
                addView(button("Cancel") { dialog.dismiss() })
            }
            // Same width for every button in the column
            for (i in 0 until buttons.childCount) {
                buttons.getChildAt(i).layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    root.resources.getDimensionPixelSize(R.dimen.js8_button_height)).apply { if (i > 0) topMargin = (6 * dp).toInt() }
            }
            dialog = AlertDialog.Builder(context)
                .setTitle("Palette Designer")
                .setView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    // 219 x 209 (the PC's dialog), 9 dp margins
                    val m = (9 * dp).toInt()
                    setPadding(m, m, m, m)
                    addView(table, LinearLayout.LayoutParams(0, (191 * dp).toInt(), 1f))
                    addView(buttons, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        .apply { marginStart = (6 * dp).toInt() })
                })
                .create()
            dialog.show()
            dialog.window?.setLayout((219 * dp).toInt() + (48 * dp).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        /** One row per colour: the row number (QTableWidget's vertical header), then the colour. */
        private fun render() {
            rows.removeAllViews()
            val rowHeight = (24 * dp).toInt()
            list.forEachIndexed { i, colour ->
                rows.addView(LinearLayout(context).apply {
                    addView(TextView(context).apply {
                        text = "${i + 1}"
                        gravity = Gravity.CENTER
                        setBackgroundResource(R.drawable.js8_list_header_cell)
                        setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.js8_list_header_text))
                        setTextSize(TypedValue.COMPLEX_UNIT_PX, root.resources.getDimension(R.dimen.js8_text))
                    }, LinearLayout.LayoutParams((30 * dp).toInt(), ViewGroup.LayoutParams.MATCH_PARENT))
                    addView(View(context).apply { setBackgroundColor(colour) },
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
                    // QTableWidget grid line under each row
                    setBackgroundColor(androidx.core.content.ContextCompat.getColor(context, R.color.js8_list_header_border))
                    setPadding(0, 0, 0, dp.toInt().coerceAtLeast(1))
                    setOnClickListener { editColour(colour) { list[i] = it; render() } }
                    setOnLongClickListener { menu(this, i); true }
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowHeight))
            }
        }

        /** WF.cpp context_menu */
        private fun menu(anchor: View, row: Int?) {
            val popup = PopupMenu(android.view.ContextThemeWrapper(context, R.style.ThemeOverlay_Js8PopupMenu), anchor)
            if (row != null) popup.menu.add(0, 1, 0, "Delete")
            popup.menu.add(0, 2, 1, "Insert ...")
            popup.menu.add(0, 3, 2, "Insert after ...")
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> { list.removeAt(row!!); render() }
                    2 -> insert(row ?: list.size, Color.BLACK)
                    3 -> insert(if (row != null) row + 1 else list.size, Color.WHITE)
                }
                true
            }
            popup.show()
        }

        /** WF.cpp insert_new_item: starts from the colour above, or [default]. */
        private fun insert(row: Int, default: Int) {
            if (list.size >= MAX_COLOURS) {
                AlertDialog.Builder(context).setTitle("Palette Full")
                    .setMessage("A palette cannot exceed $MAX_COLOURS colours.")
                    .setPositiveButton("OK", null).show()
                return
            }
            editColour(if (row > 0) list[row - 1] else default) { list.add(row, it); render() }
        }
    }

    /**
     * Colour editor in place of the PC's QColorDialog: Red / Green / Blue sliders, the HTML code,
     * and the colour itself.
     */
    private fun editColour(start: Int, onPicked: (Int) -> Unit) {
        val dp = root.resources.displayMetrics.density
        var r = Color.red(start); var g = Color.green(start); var b = Color.blue(start)
        val swatch = View(context)
        val html = EditText(context).apply {
            setSingleLine(); setTextSize(TypedValue.COMPLEX_UNIT_PX, root.resources.getDimension(R.dimen.js8_text))
        }
        val bars = mutableListOf<SeekBar>()
        val values = mutableListOf<TextView>()
        var updating = false
        fun refresh(fromText: Boolean) {
            updating = true
            swatch.setBackgroundColor(Color.rgb(r, g, b))
            listOf(r, g, b).forEachIndexed { i, c -> bars[i].progress = c; values[i].text = "$c" }
            if (!fromText) html.setText(String.format("#%02x%02x%02x", r, g, b))
            updating = false
        }
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt(), 0)
            addView(swatch, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (48 * dp).toInt()))
            listOf("Red:", "Green:", "Blue:").forEachIndexed { i, label ->
                addView(LinearLayout(context).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(TextView(context).apply { text = label }, LinearLayout.LayoutParams((56 * dp).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT))
                    val bar = SeekBar(context).apply {
                        max = 255
                        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                                if (!fromUser || updating) return
                                when (i) { 0 -> r = p; 1 -> g = p; else -> b = p }
                                refresh(false)
                            }
                            override fun onStartTrackingTouch(sb: SeekBar) {}
                            override fun onStopTrackingTouch(sb: SeekBar) {}
                        })
                    }
                    bars.add(bar)
                    addView(bar, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    val value = TextView(context).apply { gravity = Gravity.END }
                    values.add(value)
                    addView(value, LinearLayout.LayoutParams((40 * dp).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT))
                })
            }
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(context).apply { text = "HTML:" }, LinearLayout.LayoutParams((56 * dp).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(html, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            })
        }
        html.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b2: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b2: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (updating) return
                val t = s?.toString()?.trim()?.removePrefix("#") ?: return
                if (t.length != 6) return
                val c = t.toIntOrNull(16) ?: return
                r = (c shr 16) and 0xff; g = (c shr 8) and 0xff; b = c and 0xff
                refresh(true)
            }
        })
        refresh(false)
        lateinit var dialog: AlertDialog
        box.setPadding(box.paddingLeft, box.paddingTop, box.paddingRight, (12 * dp).toInt())
        box.addView(LinearLayout(context).apply {
            gravity = Gravity.END
            addView(button("OK") { onPicked(Color.rgb(r, g, b)); dialog.dismiss() })
            addView(button("Cancel") { dialog.dismiss() })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = (10 * dp).toInt() })
        dialog = AlertDialog.Builder(context)
            .setTitle("Select Color")
            .setView(box)
            .create()
        dialog.show()
    }
}
