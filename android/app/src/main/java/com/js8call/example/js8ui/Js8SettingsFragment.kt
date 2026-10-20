package com.js8call.example.js8ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.CompoundButtonCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.js8call.example.BuildConfig
import com.js8call.example.R
import com.js8call.example.js8ui.Js8SettingsSchema.ButtonRow
import com.js8call.example.js8ui.Js8SettingsSchema.Field
import com.js8call.example.js8ui.Js8SettingsSchema.Group
import com.js8call.example.js8ui.Js8SettingsSchema.Item
import com.js8call.example.js8ui.Js8SettingsSchema.Kind
import com.js8call.example.js8ui.Js8SettingsSchema.Note
import com.js8call.example.js8ui.Js8SettingsSchema.RadioGroup as RadioItem
import com.js8call.example.js8ui.Js8SettingsSchema.Section
import com.js8call.example.js8ui.Js8SettingsSchema.Special
import com.js8call.example.ui.AudioDevices
import com.js8call.example.ui.MonitorViewModel
import java.util.Locale

/** Unsaved Settings values, kept while the phone rotates. Cleared by OK / Cancel. */
class Js8SettingsViewModel(app: Application) : AndroidViewModel(app) {
    val values = mutableMapOf<String, String>()
    var tab = 0
    var subTab = 0
    /** Station upright: the page shown (General, Radio, ...), or null for the list of pages. */
    var page: String? = null
    var diagnostics = ""

    /** Small box beside Acquire GPS: "", "Acquiring…", the grid found, or "No position". */
    val gpsText = MutableLiveData("")
    /** Grid found by Acquire GPS; Set copies it into My Maidenhead Grid Locator. */
    var gpsGrid: String? = null
        private set

    private val handler = Handler(Looper.getMainLooper())
    private val locationManager get() = getApplication<Application>().getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var listener: LocationListener? = null
    private val timeout = Runnable { finishGps(null) }

    /**
     * Asks the phone's GPS for a new position (GPS only, no Wi-Fi / cell position).
     * Shows the grid of the first fix, or "No position" after 2 minutes.
     * Returns false when the phone's location is off.
     */
    @SuppressLint("MissingPermission")
    fun acquireGps(): Boolean {
        stopGps()
        gpsGrid = null
        val lm = locationManager
        val gps = LocationManager.GPS_PROVIDER
        if (!runCatching { lm.isProviderEnabled(gps) }.getOrDefault(false)) { gpsText.value = "No position"; return false }
        // A GPS fix the phone got in the last 2 minutes is used at once
        val recent = runCatching { lm.getLastKnownLocation(gps) }.getOrNull()
        if (recent != null && System.currentTimeMillis() - recent.time <= GPS_TIMEOUT_MS) { finishGps(recent); return true }

        gpsText.value = "Acquiring…"
        val l = object : LocationListener {
            override fun onLocationChanged(location: Location) = finishGps(location)
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }
        listener = l
        try {
            lm.requestLocationUpdates(gps, 0L, 0f, l, Looper.getMainLooper())
        } catch (_: SecurityException) {
            finishGps(null); return true
        } catch (_: IllegalArgumentException) {
            finishGps(null); return true
        }
        handler.postDelayed(timeout, GPS_TIMEOUT_MS)
        return true
    }

    private fun finishGps(location: Location?) {
        stopGps()
        gpsGrid = location?.let { Js8Grid.fromLatLon(it.latitude, it.longitude) }
        gpsText.value = gpsGrid ?: "No position"
    }

    private fun stopGps() {
        handler.removeCallbacks(timeout)
        listener?.let { runCatching { locationManager.removeUpdates(it) } }
        listener = null
    }

    /** OK / Cancel: stop looking and empty the small box. */
    fun resetGps() {
        stopGps()
        gpsGrid = null
        gpsText.value = ""
    }

    override fun onCleared() = stopGps()

    companion object {
        private const val GPS_TIMEOUT_MS = 2 * 60 * 1000L
    }
}

/**
 * The JS8Call Settings window.
 * Upright: one list, sections one after another, label above its box.
 * Sideways: the PC window, tabs across the top, label beside its box.
 * OK and Cancel are pinned to the bottom in both.
 */
class Js8SettingsFragment : Fragment() {

    companion object {
        /** True when shown as screen 5 of the Swipe layout (OK / Cancel keep it open). */
        const val ARG_EMBEDDED = "embedded"
        /** "station" (default: Menu > File > Station…, Swipe screen 5) or "settings" (header Settings). */
        const val ARG_WINDOW = "window"
        /** Station upright: last page of the list, not a settings section. */
        private const val ABOUT_PAGE = "About"
    }

    private lateinit var vm: Js8SettingsViewModel
    private lateinit var draft: Js8SettingsSchema.Draft
    /** Station window: the PC's File > Settings. Otherwise the operating Settings window. */
    private val station get() = (arguments?.getString(ARG_WINDOW) ?: "station") == "station"
    private val sections by lazy {
        if (station) Js8SettingsSchema.stationSections() else Js8SettingsSchema.settingsSections()
    }
    private lateinit var content: FrameLayout
    private var sideways = false
    /** My Maidenhead Grid Locator box and the small box beside Acquire GPS, as last drawn. */
    private var gridBox: EditText? = null
    private var gpsBox: TextView? = null
    /** Callsign and grid boxes, as last drawn: their warning mark follows SWL (Listening only). */
    private val requiredBoxes = mutableListOf<TextView>()
    private val dp get() = resources.displayMetrics.density
    private val font by lazy { ResourcesCompat.getFont(requireContext(), R.font.selawik) }
    private val textPx get() = resources.getDimension(R.dimen.js8_text)
    private fun color(id: Int) = ContextCompat.getColor(requireContext(), id)
    private fun px(v: Int) = (v * dp).toInt()

    private val embedded get() = arguments?.getBoolean(ARG_EMBEDDED) == true ||
        parentFragment != null && parentFragment !is androidx.navigation.fragment.NavHostFragment

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        // One set of unsaved values per window
        vm = ViewModelProvider(requireActivity())[if (station) "station" else "settings", Js8SettingsViewModel::class.java]
        draft = Js8SettingsSchema.Draft(PreferenceManager.getDefaultSharedPreferences(requireContext()))
        draft.values.putAll(vm.values)
        sideways = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(color(R.color.js8_frame_bg))
        }
        if (!embedded) {
            // Station upright: ← Back at the top (a page returns to the list; the list works like
            // Cancel). Sideways: the PC window's title (Rob, 2026-10-19)
            if (station && !sideways) root.addView(label("← Back", size = 1.15f, bold = true).apply {
                setPadding(px(12), 0, px(12), 0)
                minHeight = px(40)
                gravity = Gravity.CENTER_VERTICAL
                setOnClickListener { stationBack() }
            })
            else if (station) root.addView(label("Station", size = 1.15f).apply { setPadding(px(12), px(8), px(12), px(4)) })
            // Settings window: ← Back (works like Cancel) at the left, ⚙ opening Station at the
            // right (Rob, 2026-10-19)
            else root.addView(LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(label("← Back", size = 1.15f, bold = true).apply {
                    setPadding(px(12), 0, px(12), 0)
                    minHeight = px(40)
                    gravity = Gravity.CENTER_VERTICAL
                    setOnClickListener { onCancel() }
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(View(requireContext()), LinearLayout.LayoutParams(0, 0, 1f))
                addView(android.widget.ImageView(requireContext()).apply {
                    setImageResource(R.drawable.js8_gear)
                    contentDescription = "Station"
                    setPadding(px(8), px(8), px(8), px(8))
                    setOnClickListener { findNavController().navigate(R.id.navigation_js8_station) }
                }, LinearLayout.LayoutParams(px(44), px(44)).apply { marginEnd = px(4) })
            })
        }
        content = FrameLayout(requireContext())
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // OK / Cancel pinned to the bottom, right-aligned as on the PC
        val bar = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(px(10), px(8), px(10), px(8))
        }
        // Settings window: Save (Rob, 2026-10-19); Station keeps the PC's OK
        bar.addView(button(if (station) "OK" else "Save") { onOk() }, LinearLayout.LayoutParams(px(64), px(30)))
        bar.addView(button("Cancel") { onCancel() }, LinearLayout.LayoutParams(px(64), px(30)).apply { marginStart = px(8) })
        root.addView(bar)
        render()
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        vm.gpsText.observe(viewLifecycleOwner) { gpsBox?.text = it }
        // No leaving Station with the back button until callsign and grid are saved (or SWL)
        if (station && !embedded) {
            requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner,
                object : androidx.activity.OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        when {
                            !sideways && vm.page != null -> { vm.page = null; render() } // page -> list
                            stationIncomplete() -> showStationRequired()
                            else -> findNavController().popBackStack()
                        }
                    }
                })
        }
    }

    /** Station's ← Back: a page returns to the list of pages; the list works like Cancel. */
    private fun stationBack() {
        if (vm.page != null) { vm.page = null; render() } else onCancel()
    }

    /** Saved values: no callsign or grid, and SWL (Listening only) not checked. */
    private fun stationIncomplete(): Boolean {
        val p = draft.prefs
        if (p.getBoolean(Js8SettingsSchema.SWL_KEY, false)) return false
        return p.getString("callsign", "").isNullOrBlank() || p.getString("grid", "").isNullOrBlank()
    }

    private fun showStationRequired() {
        AlertDialog.Builder(requireContext()).setMessage(Js8StationRequiredDialog.MESSAGE)
            .setPositiveButton(android.R.string.ok, null).show()
    }

    private fun swlChecked(): Boolean =
        (draft.values[Js8SettingsSchema.SWL_KEY] ?: draft.prefs.getBoolean(Js8SettingsSchema.SWL_KEY, false).toString()) == "true"

    override fun onPause() {
        vm.values.clear(); vm.values.putAll(draft.values)
        super.onPause()
    }

    // ---- OK / Cancel --------------------------------------------------------------------------

    private fun allFields(items: List<Item> = sections.flatMap { it.items }): List<Field> = items.flatMap {
        when (it) {
            is Field -> listOf(it)
            is Group -> allFields(it.items)
            is RadioItem -> listOf(it.field)
            else -> emptyList()
        }
    }

    private fun onOk() {
        val ctx = requireContext()
        val prefs = draft.prefs
        // PC (Configuration.cpp validate): a bad or empty callsign or grid keeps the window open.
        // With SWL (Listening only) both may be empty; anything typed is still checked.
        val callsign = (draft.values["callsign"] ?: prefs.getString("callsign", "")).orEmpty().trim().uppercase(Locale.US)
        val grid = (draft.values["grid"] ?: prefs.getString("grid", "")).orEmpty().trim()
        val swl = swlChecked()
        val problem = when {
            !station -> null
            (callsign.isNotEmpty() || !swl) && (!Js8Callsign.isValid(callsign) || callsign.startsWith("@")) ->
                "The callsign format you provided is not supported"
            (grid.isNotEmpty() || !swl) && !Js8Grid.isValid(grid) -> "The grid you provided is not valid"
            else -> null
        }
        if (problem != null) {
            AlertDialog.Builder(ctx).setMessage(problem).setPositiveButton(android.R.string.ok, null).show()
            return
        }
        val e = prefs.edit()
        val changedLayout = draft.values.containsKey(Js8MainFragment.PREF_LANDSCAPE_LAYOUT)
        for (f in allFields()) {
            // Changed values, plus PC defaults for settings never stored, so the engine has them
            val v = draft.values[f.key] ?: if (!prefs.contains(f.key) && f.kind != Kind.AUDIO_INPUT) f.load(prefs) else continue
            when (f.kind) {
                Kind.AUDIO_INPUT -> {
                    val device = AudioDevices.list(ctx).firstOrNull { it.id.toString() == v }
                    if (device != null) {
                        val running = ViewModelProvider(requireActivity())[MonitorViewModel::class.java].isRunning.value == true
                        AudioDevices.select(ctx, device, running)
                    }
                }
                else -> f.save(e, v, ctx)
            }
        }
        draft.values[Js8SettingsSchema.SAVED_MESSAGES_KEY]?.let { e.putString(Js8SettingsSchema.SAVED_MESSAGES_KEY, it) }
        draft.values.filterKeys { it.startsWith("js8cfg_notify_") || it.startsWith("js8cfg_ui_") || it == "js8cfg_pills" }
            .forEach { (k, v) -> e.putString(k, v) }
        e.apply()
        clearDraft()
        if (embedded) {
            Toast.makeText(ctx, "Settings saved", Toast.LENGTH_SHORT).show()
            render()
            if (changedLayout) requireActivity().recreate()
        } else {
            findNavController().popBackStack()
        }
    }

    private fun onCancel() {
        if (station && !embedded && stationIncomplete()) { showStationRequired(); return }
        clearDraft()
        if (embedded) render() else findNavController().popBackStack()
    }

    private fun clearDraft() {
        draft.values.clear(); vm.values.clear()
        vm.page = null // Station opens on its list of pages next time
        vm.resetGps()
    }

    // ---- Layout -------------------------------------------------------------------------------

    private fun render() {
        content.removeAllViews()
        requiredBoxes.clear()
        when {
            sideways -> renderTabs()
            station -> renderStationPage()
            else -> renderList()
        }
    }

    /**
     * Station upright: a list of the PC's tab names (General, Radio, ...); tap one to see only
     * that page (Rob, 2026-10-19: one long list was too much).
     */
    private fun renderStationPage() {
        // About last, upright only (Rob, 2026-10-19: Settings > gear > Station > About)
        val pages = sections.map { it.tab }.distinct() + ABOUT_PAGE
        val page = vm.page?.takeIf { it in pages }
        val col = column().apply { setPadding(px(10), px(4), px(10), px(16)) }
        if (page == null) {
            // Title centred over the page list (Rob, 2026-10-19)
            col.addView(label("Station Settings", size = 1.2f, bold = true).apply {
                setPadding(0, px(10), 0, px(10))
                gravity = Gravity.CENTER_HORIZONTAL
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            pages.forEach { name ->
                col.addView(LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    // 280 dp wide, centred, 40 dp tall: all pages fit upright (Rob, 2026-10-19)
                    minimumHeight = px(40)
                    setPadding(px(12), 0, px(12), 0)
                    setBackgroundResource(R.drawable.js8_cfg_field)
                    addView(label(name, size = 1.1f), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(label("›", size = 1.4f))
                    setOnClickListener { vm.page = name; render() }
                }, LinearLayout.LayoutParams(px(280), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    bottomMargin = px(6)
                })
            }
        } else if (page == ABOUT_PAGE) {
            renderAbout(col)
        } else {
            col.addView(label(page, size = 1.2f, bold = true).apply { setPadding(0, px(10), 0, px(6)) })
            for (s in sections.filter { it.tab == page }) {
                if (!hasVisible(s.items)) continue // e.g. CAT Control while Rig is None
                if (s.subTab != null) col.addView(label(s.subTab, size = 1.1f, bold = true).apply { setPadding(0, px(14), 0, px(4)) })
                renderItems(col, s.items)
            }
        }
        content.addView(ScrollView(requireContext()).apply { addView(col) })
    }

    /** Station > About: version, features over JS8Android, source, licence, credits (Rob, 2026-10-19). */
    private fun renderAbout(col: LinearLayout) {
        fun line(text: String, size: Float = 1f, bold: Boolean = false, top: Int = 0) =
            col.addView(label(text, size, bold).apply {
                setPadding(0, px(top), 0, px(3))
                setLinkTextColor(color(R.color.js8_blue))
                android.text.util.Linkify.addLinks(this, android.text.util.Linkify.WEB_URLS) // tap opens the browser
            })
        line("About Easy JS8", size = 1.2f, bold = true, top = 10)
        line("by VA3OSO")
        line("Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
        line("Built ${BuildConfig.BUILD_DATE}")
        line("Easy JS8 is a fork of JS8Android.", top = 12)
        line("Features", size = 1.1f, bold = true, top = 16)
        listOf("Traditional style", "Upright and sideways layouts", "SWL mode", "JS8 Slow decoding",
            "APRS forms", "FT8 decodes").forEach { line("  •  $it") }
        line("Source: https://github.com/RobBobbins/Easy-JS8", top = 16)
        line("Licence: GPLv3. No warranty.")
        line("Credits", size = 1.1f, bold = true, top = 16)
        line("WSJT-X: Joe Taylor K1JT and the WSJT Development Group")
        line("JS8Call: Jordan Sherer KN4CRD and the JS8Call-improved team (AC9KH, W6BAZ, KJ4CTD, K0OG, DJ3EI, K4RWR)", top = 6)
        line("JS8Android: https://github.com/JS8Call-improved/Android-port", top = 6)
        line("ft8_lib (FT8 decodes): Kārlis Goba, MIT licence", top = 6)
        line("Fonts: Selawik, Cascadia Mono (SIL Open Font Licence)", top = 6)
    }

    private fun sectionTitle(s: Section) = if (s.subTab != null) "${s.tab} – ${s.subTab}" else s.tab

    /** Upright: every section one after another. */
    private fun renderList() {
        val col = column().apply { setPadding(px(10), px(4), px(10), px(16)) }
        for (s in sections) {
            col.addView(label(sectionTitle(s), size = 1.2f, bold = true).apply { setPadding(0, px(18), 0, px(6)) })
            renderItems(col, s.items)
        }
        content.addView(ScrollView(requireContext()).apply { addView(col) })
    }

    /** Sideways: PC window with main tabs and sub-tabs. */
    private fun renderTabs() {
        val tabs = sections.map { it.tab }.distinct()
        vm.tab = vm.tab.coerceIn(0, tabs.lastIndex)
        val tabName = tabs[vm.tab]
        val inTab = sections.filter { it.tab == tabName }
        val head = inTab.firstOrNull { it.subTab == null }
        val subs = inTab.filter { it.subTab != null }
        vm.subTab = vm.subTab.coerceIn(0, (subs.size - 1).coerceAtLeast(0))

        val outer = column().apply { setPadding(px(10), px(4), px(10), 0) }
        outer.addView(tabStrip(tabs, vm.tab) { i -> vm.tab = i; vm.subTab = 0; render() })

        val pane = column().apply {
            setBackgroundResource(R.drawable.js8_cfg_pane)
            setPadding(px(10), px(10), px(10), px(10))
        }
        val scroll = ScrollView(requireContext())
        val inner = column()
        scroll.addView(inner)
        head?.let { renderItems(inner, it.items) }
        if (subs.isNotEmpty()) {
            inner.addView(tabStrip(subs.map { it.subTab!! }, vm.subTab) { i -> vm.subTab = i; render() })
            val subPane = column().apply {
                setBackgroundColor(color(R.color.js8_frame_bg))
                setPadding(px(10), px(10), px(10), px(10))
            }
            renderItems(subPane, subs[vm.subTab].items)
            inner.addView(subPane)
        }
        pane.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        outer.addView(pane, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        content.addView(outer)
    }

    private fun tabStrip(names: List<String>, selected: Int, onPick: (Int) -> Unit): View {
        val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
        names.forEachIndexed { i, n ->
            row.addView(label(n).apply {
                setBackgroundResource(R.drawable.js8_cfg_tab)
                isSelected = i == selected
                gravity = Gravity.CENTER
                setPadding(px(14), 0, px(14), 0)
                setOnClickListener { onPick(i) }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(30)).apply { marginEnd = px(3) })
        }
        return HorizontalScrollView(requireContext()).apply { isHorizontalScrollBarEnabled = false; addView(row) }
    }

    private fun renderItems(parent: LinearLayout, items: List<Item>) {
        for (item in items) {
            when (item) {
                is Group -> if (item.visible(draft) && hasVisible(item.items)) {
                    parent.addView(groupBox(item.title) { box -> renderItems(box, item.items) })
                }
                is RadioItem -> parent.addView(radioGroup(item))
                is Field -> if (item.visible(draft)) parent.addView(fieldRow(item))
                is ButtonRow -> parent.addView(buttonRow(item))
                is Note -> parent.addView(label(item.text).apply { setPadding(0, px(4), 0, px(4)) })
                is Special -> parent.addView(special(item.id))
            }
        }
    }

    /** A box with nothing visible inside is left out (e.g. CAT Control while Rig is None). */
    private fun hasVisible(items: List<Item>): Boolean = items.any {
        when (it) {
            is Field -> it.visible(draft)
            is Group -> it.visible(draft) && hasVisible(it.items)
            else -> true
        }
    }

    // ---- Widgets ------------------------------------------------------------------------------

    private fun column() = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }

    private fun label(text: String, size: Float = 1f, bold: Boolean = false) = TextView(requireContext()).apply {
        this.text = text
        typeface = if (bold) Typeface.create(font, Typeface.BOLD) else font
        setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx * size)
        setTextColor(color(R.color.js8_frame_text))
        includeFontPadding = false
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

    /** Box with a thin border and its title sitting on the top edge, like a Qt group box. */
    private fun groupBox(title: String?, fill: (LinearLayout) -> Unit): View {
        val frame = FrameLayout(requireContext())
        val box = column().apply {
            setBackgroundResource(R.drawable.js8_cfg_group)
            setPadding(px(10), px(if (title != null) 16 else 10), px(10), px(10))
        }
        fill(box)
        frame.addView(box, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = px(if (title != null) 9 else 0)
        })
        if (title != null) frame.addView(label(title).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(px(3), 0, px(3), 0)
            setBackgroundColor(currentBackground())
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginStart = px(10)
        })
        frame.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = px(6); bottomMargin = px(8)
        }
        return frame
    }

    /** Group titles sit on the window color (the area inside the PC pages is the frame color). */
    private fun currentBackground() = color(R.color.js8_frame_bg)

    /** Label + control. Sideways: label beside the box. Upright: label above it. */
    private fun fieldRow(f: Field): View {
        if (f.kind == Kind.CHECK) return checkBox(f)
        val control = when (f.kind) {
            Kind.TEXT -> textBox(f)
            Kind.SPIN -> spinBox(f)
            Kind.CHOICE, Kind.AUDIO_INPUT -> choiceBox(f)
            else -> View(requireContext())
        }
        val row = LinearLayout(requireContext()).apply {
            orientation = if (sideways) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            gravity = if (sideways) Gravity.CENTER_VERTICAL else Gravity.NO_GRAVITY
            setPadding(0, px(4), 0, px(4))
        }
        val lab = label(f.label).apply { if (!f.enabled) alpha = 0.5f }
        if (sideways) {
            row.addView(lab, LinearLayout.LayoutParams(px(260), ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = px(8) })
            row.addView(control, LinearLayout.LayoutParams(0, px(32), 1f))
        } else {
            row.addView(lab)
            row.addView(control, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(34)).apply { topMargin = px(4) })
        }
        return row
    }

    private fun styledField(v: TextView) = v.apply {
        setBackgroundResource(R.drawable.js8_cfg_field)
        typeface = font
        setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx)
        setTextColor(color(R.color.js8_frame_text))
        setHintTextColor(color(R.color.js8_cfg_placeholder))
        setPadding(px(8), 0, px(8), 0)
        gravity = Gravity.CENTER_VERTICAL
        isSingleLine = true
    }

    private fun textBox(f: Field) = styledField(EditText(requireContext())).apply {
        setText(draft.get(f))
        hint = f.hint
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
            (if (f.upper) InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS else 0)
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        if (f.maxLength > 0) filters = arrayOf(InputFilter.LengthFilter(f.maxLength))
        if (f.key == "grid") gridBox = this as EditText
        if (f.required) { requiredBoxes += this; markRequired(this) }
        addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val v = s?.toString().orEmpty()
                draft.set(f, if (f.upper) v.uppercase(Locale.US) else v)
                if (f.required) markRequired(this@apply)
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
    }

    /**
     * PC attachRequiredIndicator: warning mark at the right end of the box while it is empty.
     * Not with SWL (Listening only) checked: callsign and grid may stay empty then.
     */
    private fun markRequired(box: TextView) {
        val warn = if (box.text.isNullOrBlank() && !swlChecked()) ContextCompat.getDrawable(requireContext(), R.drawable.js8_warning) else null
        box.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, warn, null)
        box.compoundDrawablePadding = px(4)
    }

    private fun checkBox(f: Field) = CheckBox(requireContext()).apply {
        text = f.label
        typeface = font
        setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx)
        setTextColor(color(R.color.js8_frame_text))
        CompoundButtonCompat.setButtonTintList(this, android.content.res.ColorStateList.valueOf(color(R.color.js8_cfg_group_border)))
        isChecked = draft.bool(f)
        if (!f.enabled || !f.active(draft)) { isEnabled = false; alpha = 0.5f }
        setOnCheckedChangeListener { _, c ->
            draft.set(f, c.toString())
            if (f.key == Js8SettingsSchema.SWL_KEY) requiredBoxes.forEach { markRequired(it) }
            if (f.key in Js8SettingsSchema.ACTIVE_TRIGGERS) content.post { render() } // ACK follows AUTO and HB
        }
    }

    private fun spinText(f: Field, v: String): String {
        val n = v.toDoubleOrNull() ?: 0.0
        if (f.special != null && n == f.min) return f.special
        val num = if (f.decimals == 0) n.toLong().toString() else String.format(Locale.US, "%.${f.decimals}f", n)
        return f.prefix + num + f.suffix
    }

    /** Spin box: value, with ▲ ▼ on the right; tap the value to type a number. */
    private fun spinBox(f: Field): View {
        val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
        val value = styledField(TextView(requireContext())).apply { text = spinText(f, draft.get(f)) }
        fun step(dir: Int) {
            val n = (draft.get(f).toDoubleOrNull() ?: 0.0) + dir * f.step
            val c = n.coerceIn(f.min, f.max)
            val s = if (f.decimals == 0) c.toLong().toString() else String.format(Locale.US, "%.${f.decimals}f", c)
            draft.set(f, s); value.text = spinText(f, s)
        }
        value.setOnClickListener {
            val input = EditText(requireContext()).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or
                    (if (f.decimals > 0) InputType.TYPE_NUMBER_FLAG_DECIMAL else 0) or
                    (if (f.min < 0) InputType.TYPE_NUMBER_FLAG_SIGNED else 0)
                setText(draft.get(f))
            }
            AlertDialog.Builder(requireContext()).setTitle(f.label.trimEnd(':')).setView(input)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val n = input.text.toString().toDoubleOrNull() ?: return@setPositiveButton
                    val c = n.coerceIn(f.min, f.max)
                    val s = if (f.decimals == 0) c.toLong().toString() else String.format(Locale.US, "%.${f.decimals}f", c)
                    draft.set(f, s); value.text = spinText(f, s)
                }
                .setNegativeButton(android.R.string.cancel, null).show()
        }
        val arrows = column()
        listOf("▲" to 1, "▼" to -1).forEach { (t, d) ->
            arrows.addView(label(t, size = 0.7f).apply {
                gravity = Gravity.CENTER
                setBackgroundResource(R.drawable.js8_cfg_field)
                setOnClickListener { step(d) }
            }, LinearLayout.LayoutParams(px(28), 0, 1f))
        }
        row.addView(value, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        row.addView(arrows, LinearLayout.LayoutParams(px(28), ViewGroup.LayoutParams.MATCH_PARENT))
        return row
    }

    private fun choices(f: Field): List<Pair<String, String>> = if (f.kind == Kind.AUDIO_INPUT) {
        AudioDevices.list(requireContext()).map { it.name to it.id.toString() }
    } else f.choices?.invoke(requireContext(), draft) ?: emptyList()

    /** Drop-down: current entry with ▾; tap for the list. */
    private fun choiceBox(f: Field): View {
        val opts = choices(f)
        val current = if (f.kind == Kind.AUDIO_INPUT) {
            draft.values[f.key] ?: AudioDevices.selected(requireContext()).id.toString()
        } else draft.get(f)
        val name = opts.firstOrNull { it.second == current }?.first ?: current
        return styledField(TextView(requireContext())).apply {
            text = "$name  ▾"
            isEnabled = f.enabled
            if (!f.enabled) alpha = 0.5f
            setOnClickListener {
                val list = choices(f)
                val idx = list.indexOfFirst { it.second == (draft.values[f.key] ?: current) }
                AlertDialog.Builder(requireContext())
                    .setTitle(f.label.trimEnd(':'))
                    .setSingleChoiceItems(list.map { it.first.ifEmpty { " " } }.toTypedArray(), idx) { d, which ->
                        d.dismiss()
                        draft.set(f, list[which].second)
                        render() // some choices show / hide other settings (Rig)
                    }.show()
            }
        }
    }

    private fun radioGroup(item: RadioItem): View = groupBox(item.title) { box ->
        val rg = RadioGroup(requireContext()).apply {
            orientation = if (sideways && !item.vertical) RadioGroup.HORIZONTAL else RadioGroup.VERTICAL
        }
        val cur = draft.get(item.field)
        item.options.forEach { (lab, value) ->
            rg.addView(RadioButton(requireContext()).apply {
                id = View.generateViewId()
                text = lab
                typeface = font
                setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx)
                setTextColor(color(R.color.js8_frame_text))
                CompoundButtonCompat.setButtonTintList(this, android.content.res.ColorStateList.valueOf(color(R.color.js8_cfg_group_border)))
                isChecked = value == cur
                setOnCheckedChangeListener { _, c -> if (c) draft.set(item.field, value) }
                if (value in item.disabled) { isEnabled = false; alpha = 0.5f }
                if (sideways && !item.vertical) setPadding(0, 0, px(16), 0)
            })
        }
        box.addView(rg)
    }

    private fun buttonRow(item: ButtonRow): View {
        val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, px(6), 0, px(6)) }
        item.buttons.forEach { (lab, action) ->
            row.addView(button(lab) { onAction(action) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(28)).apply { marginEnd = px(8) })
        }
        return row
    }

    // ---- Actions ------------------------------------------------------------------------------

    // Android 12+ ignores a request for fine location alone; ask for both
    private val locationPermissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    private val locationPermission = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { granted -> if (granted.values.any { it }) acquireGps() else toast("Location permission is needed for Acquire GPS") }

    private fun acquireGps() {
        val ctx = requireContext()
        if (locationPermissions.none { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }) {
            locationPermission.launch(locationPermissions)
            return
        }
        if (!vm.acquireGps()) toast("Location is off. Turn it on in the phone's settings.")
    }

    private fun activityVm() = ViewModelProvider(requireActivity())[Js8ActivityViewModel::class.java]

    private fun onAction(id: String) {
        when (id) {
            // Settings > View (was Menu > View): at once, not on OK
            "clear_band" -> { activityVm().activity.clearBand(); activityVm().changed(); toast("Band Activity cleared") }
            "clear_call" -> { activityVm().activity.clearCalls(); activityVm().changed(); toast("Call Activity cleared") }
            "clear_rx" -> { activityVm().activity.clearRx(); activityVm().changed(); toast("RX Activity cleared") }
            "clear_all" -> {
                activityVm().activity.clearBand(); activityVm().activity.clearCalls(); activityVm().activity.clearRx()
                activityVm().changed(); toast("All Activity cleared")
            }
            "test_cat", "test_ptt" -> toast("Test CAT / Test PTT are not built yet")
            "diag_run" -> { vm.diagnostics = diagnostics(); render() }
            "diag_copy" -> {
                val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Easy JS8 diagnostics", vm.diagnostics.ifEmpty { diagnostics() }))
                toast("Copied")
            }
        }
    }

    private fun toast(t: String) = Toast.makeText(requireContext(), t, Toast.LENGTH_SHORT).show()

    /** Under My Maidenhead Grid Locator: Acquire GPS, the grid it found, Set (copies it into the grid box). */
    private fun gpsRow(): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            // Sideways the row sits under the box, past the 260 dp label column
            setPadding(if (sideways) px(268) else 0, px(2), 0, px(6))
        }
        val found = styledField(TextView(requireContext())).apply { text = vm.gpsText.value }
        gpsBox = found
        row.addView(button("Acquire GPS") { acquireGps() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(28)))
        row.addView(found, LinearLayout.LayoutParams(0, px(28), 1f).apply { marginStart = px(8); marginEnd = px(8) })
        row.addView(button("Set") { vm.gpsGrid?.let { gridBox?.setText(it) } },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(28)))
        return row
    }

    private fun diagnostics(): String {
        val ctx = requireContext()
        val p = draft.prefs
        return buildString {
            appendLine("Easy JS8 ${BuildConfig.VERSION_NAME}")
            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) on ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Callsign: ${p.getString("callsign", "")}   Grid: ${p.getString("grid", "")}")
            appendLine("Rig: ${p.getString("rig_type", "none")}  control=${p.getBoolean("rig_control_enabled", false)}")
            appendLine("Audio input: ${AudioDevices.selectedName(ctx)}")
            appendLine("Audio inputs available:")
            AudioDevices.list(ctx).forEach { appendLine("  ${it.name} (id ${it.id})") }
        }
    }

    // ---- Tables and lists ---------------------------------------------------------------------

    private fun special(id: String): View = when (id) {
        "grid_gps" -> gpsRow()
        "frequencies_table" -> table("cfg_freq", listOf(
            Js8TableView.Column("region", "IARU Region", 90), Js8TableView.Column("mode", "Mode", 60),
            Js8TableView.Column("freq", "Frequency", 90), Js8TableView.Column("mhz", "Frequency (MHz)", 120),
            Js8TableView.Column("desc", "Description", 120, stretch = true)
        ), frequencyRows(), 160)
        "schedule_table" -> table("cfg_sched", listOf(
            Js8TableView.Column("band", "Band", 60), Js8TableView.Column("mhz", "Freq. (MHz)", 90),
            Js8TableView.Column("at", "Switch at (UTC)", 110), Js8TableView.Column("until", "Until (UTC)", 90),
            Js8TableView.Column("desc", "Description", 120, stretch = true)
        ), emptyList(), 110)
        "saved_messages" -> savedMessages()
        "notifications_table" -> notifications()
        "ui_band_call" -> colorButtons(listOf(
            "Table Background" to "table_bg", "Foreground Color" to "table_fg", "Font" to "table_font",
            "Primary Highlight Background" to "primary_bg", "Secondary Highlight Background" to "secondary_bg",
            "Directed Messages Background" to "directed_bg", "CQ Messages Background" to "cq_bg",
            "Selected Row Background" to "selected_bg"
        ), extra = listOf(
            "js8cfg_ui_primary_words" to "Highlight these words or callsigns with the primary background color (comma separated):",
            "js8cfg_ui_secondary_words" to "Highlight these words or callsigns with the secondary background color (comma separated):"
        ))
        "ui_messages" -> colorButtons(listOf(
            "Received: Textbox Background Color" to "rx_bg", "Received: Foreground Color" to "rx_fg", "Received: Font" to "rx_font",
            "Transmitted: Foreground Color" to "tx_fg", "Transmitted: Font" to "tx_font"
        ))
        "ui_composition" -> colorButtons(listOf(
            "Textbox Background Color" to "compose_bg", "Foreground Color" to "compose_fg", "Font" to "compose_font",
            "Recipient Background" to "pill_recipient_bg", "Recipient Foreground" to "pill_recipient_fg",
            "Command Background" to "pill_command_bg", "Command Foreground" to "pill_command_fg",
            "Group Background" to "pill_group_bg", "Group Foreground" to "pill_group_fg",
            "Sender Background" to "pill_sender_bg", "Sender Foreground" to "pill_sender_fg"
        ), pills = true)
        "diagnostics_text" -> styledField(TextView(requireContext())).apply {
            isSingleLine = false
            gravity = Gravity.TOP or Gravity.START
            setPadding(px(8), px(6), px(8), px(6))
            minHeight = px(140)
            text = vm.diagnostics
            setTextIsSelectable(true)
        }
        else -> View(requireContext())
    }

    private fun table(key: String, cols: List<Js8TableView.Column>, rows: List<Js8TableView.Row>, heightDp: Int) =
        Js8TableView(requireContext()).apply {
            setColumns(key, cols)
            setRows(rows)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(heightDp)).apply {
                topMargin = px(4); bottomMargin = px(6)
            }
        }

    private fun frequencyRows(): List<Js8TableView.Row> {
        val entries = resources.getStringArray(R.array.js8_frequency_entries)
        val values = resources.getStringArray(R.array.js8_frequency_values)
        return entries.indices.map { i ->
            val hz = values[i].toLongOrNull() ?: 0L
            Js8TableView.Row("f$i", mapOf(
                "region" to "All", "mode" to "JS8",
                "freq" to entries[i].substringBefore(" - ").trim(),
                "mhz" to String.format(Locale.US, "%.6f", hz / 1_000_000.0),
                "desc" to ""
            ))
        }
    }

    private fun savedMessages(): View {
        val key = Js8SettingsSchema.SAVED_MESSAGES_KEY
        fun current(): MutableList<String> = (draft.values[key]
            ?: draft.prefs.getString(key, Js8SettingsSchema.SAVED_MESSAGES_DEFAULT).orEmpty())
            .split('\n').filter { it.isNotBlank() }.toMutableList()
        val col = column()
        val addRow = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val input = styledField(EditText(requireContext())).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        val list = table("cfg_saved", listOf(Js8TableView.Column("msg", "Message", 200, stretch = true)),
            current().mapIndexed { i, m -> Js8TableView.Row("$i", mapOf("msg" to m)) }, 200)
        fun refresh(items: List<String>) {
            draft.values[key] = items.joinToString("\n")
            list.setRows(items.mapIndexed { i, m -> Js8TableView.Row("$i", mapOf("msg" to m)) })
        }
        addRow.addView(button("Add") {
            val t = input.text.toString().trim().uppercase(Locale.US)
            if (t.isNotEmpty()) { refresh(current() + t); input.setText("") }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(30)))
        addRow.addView(input, LinearLayout.LayoutParams(0, px(32), 1f).apply { marginStart = px(8); marginEnd = px(8) })
        addRow.addView(button("Delete") {
            val sel = list.selectedRowId?.toIntOrNull() ?: return@button
            refresh(current().filterIndexed { i, _ -> i != sel }); list.selectedRowId = null
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(30)))
        col.addView(addRow)
        col.addView(list)
        return col
    }

    private fun notifications(): View {
        val col = column().apply { setPadding(0, px(4), 0, 0) }
        Js8SettingsSchema.NOTIFY_EVENTS.forEach { (k, name) ->
            val key = "js8cfg_notify_$k"
            col.addView(CheckBox(requireContext()).apply {
                text = name
                typeface = font
                setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx)
                setTextColor(color(R.color.js8_frame_text))
                CompoundButtonCompat.setButtonTintList(this, android.content.res.ColorStateList.valueOf(color(R.color.js8_cfg_group_border)))
                isChecked = (draft.values[key] ?: draft.prefs.getString(key, "false")) == "true"
                setOnCheckedChangeListener { _, c -> draft.values[key] = c.toString() }
            })
        }
        return col
    }

    private val palette = listOf(
        "#FFFFFF", "#000000", "#1E1E1E", "#3C3C3C", "#808080", "#C0C0C0",
        "#FFEAA7", "#FFFFE0", "#DDEEFF", "#A5CDFF", "#6699FF", "#003EAA",
        "#22FF22", "#39FF14", "#2ECC40", "#FF2222", "#FF8C00", "#F2C300",
        "#8E44AD", "#E91E63", "#00BCD4", "#795548", "#FFC0CB", "#90EE90"
    )

    /** UI tab: one button per color / font with a "K1ABC" sample, like the PC tab. */
    private fun colorButtons(
        items: List<Pair<String, String>>,
        extra: List<Pair<String, String>> = emptyList(),
        pills: Boolean = false
    ): View {
        val col = column()
        if (pills) col.addView(checkBox(Field("js8cfg_pills", "Display callsigns and commands as pills", Kind.CHECK, "true")))
        var lastHeading: String? = null
        items.forEach { (lab, k) ->
            val heading = if (lab.contains(": ")) lab.substringBefore(": ") else null
            if (heading != null && heading != lastHeading) {
                col.addView(label("$heading:", bold = true).apply { setPadding(0, px(8), 0, px(2)) })
            }
            lastHeading = heading
            val key = "js8cfg_ui_$k"
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, px(3), 0, px(3))
            }
            val sample = label("K1ABC").apply {
                gravity = Gravity.CENTER
                setPadding(px(10), px(3), px(10), px(3))
            }
            fun paint() {
                val v = draft.values[key] ?: draft.prefs.getString(key, null)
                if (k.endsWith("_font")) sample.text = "K1ABC ${v ?: "10"}pt"
                else v?.let { runCatching { sample.setBackgroundColor(Color.parseColor(it)) } }
            }
            paint()
            row.addView(button(lab.substringAfter(": ")) {
                if (k.endsWith("_font")) {
                    val sizes = (8..20).map { it.toString() }
                    AlertDialog.Builder(requireContext()).setTitle("Font size")
                        .setItems(sizes.map { "$it pt" }.toTypedArray()) { _, w -> draft.values[key] = sizes[w]; paint() }.show()
                } else {
                    AlertDialog.Builder(requireContext()).setTitle(lab)
                        .setItems(palette.toTypedArray()) { _, w -> draft.values[key] = palette[w]; paint() }.show()
                }
            }, LinearLayout.LayoutParams(0, px(28), 1f))
            row.addView(sample, LinearLayout.LayoutParams(px(110), ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = px(8) })
            col.addView(row)
        }
        extra.forEach { (key, lab) -> col.addView(fieldRow(Field(key, lab, Kind.TEXT, "", upper = true))) }
        return col
    }
}
