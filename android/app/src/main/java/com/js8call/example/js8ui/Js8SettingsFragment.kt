package com.js8call.example.js8ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
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
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.js8call.example.service.JS8EngineService
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
    /** Test CAT result (PC: button green / red); null = not tried. */
    var testCat: Boolean? = null
    var testPtt = false

    /** Small box beside Acquire GPS: "", "Acquiring…", the grid found, or "No position". */
    val gpsText = MutableLiveData("")
    /** Grid found by Acquire GPS; Set copies it into My Maidenhead Grid Locator. */
    var gpsGrid: String? = null
        private set

    private val handler = Handler(Looper.getMainLooper())
    private val locationManager get() = getApplication<Application>().getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var listener: LocationListener? = null
    // 2 minutes up: keep a Wi-Fi / cell grid if one came, else "No position"
    private val timeout = Runnable { finishGps(null) }

    /**
     * Asks the phone for a new position from GPS and Wi-Fi / cell at the same time (Rob, 2026-10-22).
     * The first answer is shown at once; a GPS answer replaces a Wi-Fi / cell one and ends the
     * search. Without GPS the Wi-Fi / cell grid stays; nothing at all after 2 minutes = "No position".
     * Returns false when the phone's location is off.
     */
    @SuppressLint("MissingPermission")
    fun acquireGps(): Boolean {
        stopGps()
        gpsGrid = null
        val lm = locationManager
        val gps = LocationManager.GPS_PROVIDER
        val net = LocationManager.NETWORK_PROVIDER
        val enabled = listOf(gps, net).filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        if (enabled.isEmpty()) { gpsText.value = "No position"; return false }
        // A GPS fix the phone got in the last 2 minutes is used at once
        fun recent(provider: String) = runCatching { lm.getLastKnownLocation(provider) }.getOrNull()
            ?.takeIf { System.currentTimeMillis() - it.time <= GPS_TIMEOUT_MS }
        if (gps in enabled) recent(gps)?.let { finishGps(it); return true }

        gpsText.value = "Acquiring…"
        if (net in enabled) recent(net)?.let { showGps(it) }
        val l = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (location.provider == gps) finishGps(location) else showGps(location)
            }
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }
        listener = l
        // Each source on its own: approximate-only permission refuses GPS but allows Wi-Fi / cell
        val listening = enabled.count { provider ->
            runCatching { lm.requestLocationUpdates(provider, 0L, 0f, l, Looper.getMainLooper()) }.isSuccess
        }
        if (listening == 0) { finishGps(null); return true }
        handler.postDelayed(timeout, GPS_TIMEOUT_MS)
        return true
    }

    /** A Wi-Fi / cell answer: shown, GPS still listened for (no GPS listening = done). */
    private fun showGps(location: Location) {
        gpsGrid = Js8Grid.fromLatLon(location.latitude, location.longitude)
        gpsText.value = gpsGrid
        val gpsOn = runCatching { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
        if (!gpsOn) stopGps()
    }

    private fun finishGps(location: Location?) {
        stopGps()
        location?.let { gpsGrid = Js8Grid.fromLatLon(it.latitude, it.longitude) }
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
 * Sideways: the same without tabs, framed groups in two columns side by side (Rob, 2026-10-20).
 * OK and Cancel are pinned to the bottom in both.
 */
class Js8SettingsFragment : Fragment() {

    companion object {
        /** True when shown as screen 5 of the Swipe layout (OK / Cancel keep it open). */
        const val ARG_EMBEDDED = "embedded"
        /** "station" (default: Menu > File > Station…, Swipe screen 5) or "settings" (header Settings). */
        const val ARG_WINDOW = "window"
        /** Station: last page of the list, not a settings section. */
        const val ABOUT_PAGE = "About"
        /** Where callsign and grid are entered (Station Details), opened when they are missing. */
        const val GENERAL_PAGE = "General"
        /** Station: page to open on (PC mode menu bar Help opens About). */
        const val ARG_PAGE = "page"
        val FONT_FAMILIES = listOf("Selawik", "Sans Serif", "Serif", "Monospace")
        val FONT_STYLES = listOf("Regular", "Bold", "Italic", "Bold Italic")

        /** The Font button's family and style; null when none was chosen. */
        fun typefaceFor(context: Context, family: String?, style: String?): Typeface? {
            if (family == null) return null
            val base = when (family) {
                "Sans Serif" -> Typeface.SANS_SERIF
                "Serif" -> Typeface.SERIF
                "Monospace" -> Typeface.MONOSPACE
                else -> ResourcesCompat.getFont(context, R.font.selawik)
            }
            return Typeface.create(base, maxOf(FONT_STYLES.indexOf(style), 0))
        }
    }

    private lateinit var vm: Js8SettingsViewModel
    private lateinit var draft: Js8SettingsSchema.Draft
    /** Station window: the PC's File > Settings. Otherwise the operating Settings window. */
    private val station get() = (arguments?.getString(ARG_WINDOW) ?: "station") == "station"
    private val sections by lazy {
        if (station) Js8SettingsSchema.stationSections() else Js8SettingsSchema.settingsSections(
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getBoolean("js8cfg_autoreply_confirmation", true))
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
    /** Sideways Station: page name beside ← Back. */
    private var titleView: TextView? = null
    // Settings window: 12 dp, the PC's 9 pt Segoe UI (Rob, 2026-10-20); Station keeps 14.3 dp
    private val textPx get() = resources.getDimension(R.dimen.js8_text) * (if (!station) 12f / 14.3f else 1f)
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
        // Opened on one page (Help > About); after rotating, the page shown stays as it was
        if (savedInstanceState == null) arguments?.getString(ARG_PAGE)?.let { page ->
            vm.page = page
            vm.tab = stationPages().indexOf(page).coerceAtLeast(0)
            vm.subTab = 0
        }

        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(color(R.color.js8_frame_bg))
        }
        // Station: ← Back at the top (a page returns to the list; the list works like Cancel)
        // (Rob, 2026-10-19). Sideways, also in Swipe's Station screen, the list's title or the
        // page name sits in the middle of that row, centred on the screen (Rob, 2026-10-21)
        var header: FrameLayout? = null
        var settingsRow: LinearLayout? = null
        if (station && (!embedded || sideways)) root.addView(FrameLayout(requireContext()).apply {
            header = this
            if (sideways) addView(label("", size = 1.15f, bold = true).also {
                titleView = it
                it.gravity = Gravity.CENTER
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(40)))
            addView(label("← Back", size = 1.15f, bold = true).apply {
                setPadding(px(12), 0, px(12), 0)
                minHeight = px(40)
                gravity = Gravity.CENTER_VERTICAL
                setOnClickListener { stationBack() }
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        })
        else if (!embedded) {
            // Settings window: ← Back (works like Cancel) at the left, ⚙ opening Station at the
            // right (Rob, 2026-10-19)
            root.addView(LinearLayout(requireContext()).apply {
                settingsRow = this
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

        // OK / Cancel pinned to the bottom, right-aligned as on the PC; in Pages' Station screen
        // and upright in both windows they sit at the right of the ← Back row, for one more row
        // of settings (Rob, 2026-10-31)
        val barOnTop = if (sideways) station && embedded else !embedded
        val bar = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(px(10), if (barOnTop) 0 else px(8), px(10), if (barOnTop) 0 else px(8))
        }
        // Settings window: Save (Rob, 2026-10-19); Station keeps the PC's OK
        bar.addView(button(if (station) "OK" else "Save") { onOk() }, LinearLayout.LayoutParams(px(64), px(30)))
        bar.addView(button("Cancel") { onCancel() }, LinearLayout.LayoutParams(px(64), px(30)).apply { marginStart = px(8) })
        if (!barOnTop) root.addView(bar)
        else if (station) header?.addView(bar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.CENTER_VERTICAL))
        else settingsRow?.addView(bar)
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
                            vm.page != null -> { vm.page = null; render() } // page -> list
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

    private val rigTestReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val ok = i.getBooleanExtra(JS8EngineService.EXTRA_RIG_TEST_OK, false)
            if (i.getStringExtra(JS8EngineService.EXTRA_RIG_TEST_KIND) == JS8EngineService.RIG_TEST_CAT) vm.testCat = ok
            else vm.testPtt = i.getBooleanExtra(JS8EngineService.EXTRA_RIG_TEST_ON, false)
            i.getStringExtra(JS8EngineService.EXTRA_RIG_TEST_TEXT)?.takeIf { it.isNotEmpty() }?.let { toast(it) }
            render()
        }
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(rigTestReceiver, IntentFilter(JS8EngineService.ACTION_RIG_TEST))
    }

    override fun onPause() {
        vm.values.clear(); vm.values.putAll(draft.values)
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(rigTestReceiver)
        if (vm.testPtt) { vm.testPtt = false; sendTestPtt(false) } // never leave the radio keyed
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
            else -> {
                fun value(key: String, default: String = "") = (draft.values[key] ?: prefs.getString(key, default)).orEmpty().trim().uppercase(Locale.US)
                val groups = value("my_groups").split(",").map { it.trim() }.filter { it.isNotEmpty() }
                val badGroup = groups.firstOrNull { it == "@APRSIS" || it == "@JS8NET" }
                val notGroup = groups.firstOrNull { !Js8Callsign.isCompound(it) }
                val cq = value("js8cfg_cq_message", "CQ CQ CQ <MYGRID4>")
                val badCall = value("js8cfg_auto_whitelist").split(Regex("[,\\s]+")).firstOrNull { it.isNotEmpty() && !Js8Callsign.isValid(it) }
                when {
                    value("js8cfg_eot", "♢").isEmpty() -> "Please enter an end of transmission character"
                    badGroup != null -> "$badGroup is a group that cannot be joined"
                    notGroup != null -> "$notGroup is not a valid group"
                    badCall != null -> "$badCall is not a valid callsign to whitelist"
                    cq.isNotEmpty() && !(cq.startsWith("CQ") || cq.contains(callsign)) ->
                        "The CQ message format is invalid. It must either start with \"CQ\" or contain your callsign."
                    else -> null
                }
            }
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
        draft.values[Js8SettingsSchema.SCHEDULE_KEY]?.let { e.putString(Js8SettingsSchema.SCHEDULE_KEY, it) }
        draft.values[Js8SettingsSchema.FREQUENCIES_KEY]?.let { e.putString(Js8SettingsSchema.FREQUENCIES_KEY, it) }
        draft.values.filterKeys { it.startsWith("js8cfg_notify_") || it.startsWith("js8cfg_ui_") || it == "js8cfg_pills" ||
            it == com.js8call.example.api.Js8Api.KEY_WSJTX_INTERFACES }
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

    /** keepScroll: a ticked box redrew the page (greying), so the page stays where it was. */
    private fun render(keepScroll: Boolean = false) {
        if (vm.testPtt && vm.page != "Radio") { vm.testPtt = false; sendTestPtt(false) } // left the Radio page
        val y = if (keepScroll) scroller(content)?.scrollY ?: 0 else 0
        content.removeAllViews()
        requiredBoxes.clear()
        if (station) renderStationPage() else renderList()
        if (y > 0) content.post { scroller(content)?.scrollTo(0, y) }
    }

    private fun scroller(v: View): ScrollView? = v as? ScrollView
        ?: (v as? ViewGroup)?.let { g -> (0 until g.childCount).firstNotNullOfOrNull { scroller(g.getChildAt(it)) } }

    /**
     * Station upright: a list of the PC's tab names (General, Radio, ...); tap one to see only
     * that page (Rob, 2026-10-19: one long list was too much).
     */
    /** Station's pages (the PC's tab names) with About last (Rob, 2026-10-19: Settings > gear > Station > About). */
    // The Layout page (Sideways layout) is listed only sideways (Rob, 2026-10-21)
    private fun stationPages() = sections.map { it.tab }.distinct().filter { sideways || it != Js8SettingsSchema.LAYOUT_PAGE } + ABOUT_PAGE

    private fun renderStationPage() {
        val pages = stationPages()
        val page = vm.page?.takeIf { it in pages }
        val col = column().apply { setPadding(px(10), px(4), px(10), px(16)) }
        // Sideways: the list's title or the page's name in the top row, centred (Rob, 2026-10-21)
        titleView?.text = if (page == ABOUT_PAGE) "About Easy JS8" else page ?: "Station Settings"
        fun pageButton(name: String) = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = px(40)
            setPadding(px(12), 0, px(12), 0)
            setBackgroundResource(R.drawable.js8_cfg_field)
            addView(label(name, size = 1.1f), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(label("›", size = 1.4f))
            setOnClickListener {
                vm.page = name
                // PC: 5 s on the status bar while the Audio page reads the device lists
                if (name == "Audio") android.widget.Toast.makeText(requireContext(), "Enumerating audio devices", android.widget.Toast.LENGTH_SHORT).show()
                render()
            }
        }
        if (page == null && sideways) {
            // 3 across, so all pages show without scrolling; 180 dp each, centred (Rob, 2026-10-20)
            pages.chunked(3).forEach { three ->
                col.addView(LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    for (i in 0 until 3) addView(three.getOrNull(i)?.let { pageButton(it) } ?: View(requireContext()),
                        LinearLayout.LayoutParams(px(180), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            marginStart = px(if (i == 0) 0 else 4); marginEnd = px(if (i == 2) 0 else 4)
                        })
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    bottomMargin = px(6)
                })
            }
        } else if (page == null) {
            // Title centred over the page list (Rob, 2026-10-19)
            col.addView(label("Station Settings", size = 1.2f, bold = true).apply {
                setPadding(0, px(10), 0, px(10))
                gravity = Gravity.CENTER_HORIZONTAL
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            pages.forEach { name ->
                // 280 dp wide, centred, 40 dp tall: all pages fit upright (Rob, 2026-10-19)
                col.addView(pageButton(name), LinearLayout.LayoutParams(px(280), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    bottomMargin = px(6)
                })
            }
        } else if (page == ABOUT_PAGE) {
            renderAbout(col)
        } else if (sideways) {
            // The page name is in the top row (titleView). Radio: three boxes to a row (Rob, 2026-10-21)
            val secs = sections.filter { it.tab == page && hasVisible(it.items) }
            columns = if (page in THREE_ACROSS_PAGES) 3 else 2
            if (columns == 3) renderThreeAcross(col, secs) else renderSidewaysPage(col, secs)
        } else {
            col.addView(label(page, size = 1.2f, bold = true).apply { setPadding(0, px(10), 0, px(6)) })
            for (s in sections.filter { it.tab == page }) {
                if (!hasVisible(s.items)) continue // e.g. CAT Control while Rig is None
                if (s.subTab != null) col.addView(label(s.subTab, size = 1.1f, bold = true).apply { setPadding(0, px(14), 0, px(4)) })
                renderItems(col, s.items)
            }
        }
        content.addView(ScrollView(requireContext()).apply {
            // About: centred top to bottom when it fits, scrolls from the top when not (Rob, 2026-10-31)
            if (page == ABOUT_PAGE) {
                isFillViewport = true
                col.gravity = Gravity.CENTER_VERTICAL
            }
            addView(col)
        })
    }

    /** Station > About: version, features over JS8Android, source, licence, credits (Rob, 2026-10-19). */
    private fun renderAbout(col: LinearLayout) {
        // Centred (Rob, 2026-10-31); sideways the top lines and Features sit in two columns
        fun line(text: String, size: Float = 1f, bold: Boolean = false, top: Int = 0, parent: LinearLayout = col) =
            parent.addView(label(text, size, bold).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, px(top), 0, px(3))
                setLinkTextColor(color(R.color.js8_blue))
                android.text.util.Linkify.addLinks(this, android.text.util.Linkify.WEB_URLS) // tap opens the browser
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        var left = col
        var right = col
        if (sideways) {
            left = column()
            right = column()
            col.addView(LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL // the shorter left column centres against Features
                addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(right, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            })
        }
        if (sideways) line("Easy JS8", size = 1.2f, bold = true, parent = left)
        else line("About Easy JS8", size = 1.2f, bold = true, top = 10) // sideways: in the top row
        line("by VA3OSO", parent = left)
        line("Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})", parent = left)
        line("Built ${BuildConfig.BUILD_DATE}", parent = left)
        line("Easy JS8 is a fork of JS8Android.", top = 12, parent = left)
        line("Features", size = 1.1f, bold = true, top = if (sideways) 0 else 16, parent = right)
        val features = listOf("Traditional style", "Upright and sideways layouts", "SWL mode", "JS8 Slow decoding",
            "APRS forms", "FT8 decodes", "APRS-IS relay", "Message mailbox", "HB and CQ timers",
            "Log QSO and ADIF export", "API and logging links", "Waterfall Controls")
        // Upright the list is two columns of six (Rob, 2026-10-31)
        var first = right
        var second = right
        if (!sideways) {
            first = column()
            second = column()
            col.addView(LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(first, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(second, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            })
        }
        features.forEachIndexed { i, f -> line("  •  $f", parent = if (i < 6) first else second) }
        line("Source: https://github.com/RobBobbins/Easy-JS8", top = 16)
        line("Licence: GPLv3. No warranty.")
        line("Credits", size = 1.1f, bold = true, top = 16)
        line("WSJT-X: Joe Taylor K1JT${if (sideways) " " else "\n"}and the WSJT Development Group")
        line("JS8Call: Jordan Sherer KN4CRD", top = 6)
        line("JS8Call-improved team: AC9KH, W6BAZ, KJ4CTD, K0OG, DJ3EI, K4RWR, KN4CRD", top = 6)
        line("JS8Android: https://github.com/JS8Call-improved/Android-port", top = 6)
        line("ft8_lib (FT8 decodes): Kārlis Goba, MIT licence", top = 6)
        line("Fonts: Selawik, Cascadia Mono (SIL Open Font Licence)", top = 6)
    }

    private fun sectionTitle(s: Section) = if (s.subTab != null) "${s.tab} – ${s.subTab}" else s.tab

    /**
     * Upright: every section one after another. Sideways: Operation and Mode on the left, the
     * rest on the right (Rob, 2026-10-20).
     */
    private fun renderList() {
        val col = column().apply { setPadding(px(10), px(4), px(10), px(16)) }
        fun section(parent: LinearLayout, s: Section) {
            parent.addView(label(sectionTitle(s), size = 1.2f, bold = true).apply { setPadding(0, px(18), 0, px(6)) })
            renderItems(parent, s.items)
        }
        if (sideways) {
            val (left, right) = twoColumns(col)
            sections.take(2).forEach { section(left, it) }
            sections.drop(2).forEach { section(right, it) }
        } else {
            for (s in sections) section(col, s)
            centreUpright(col)
        }
        content.addView(ScrollView(requireContext()).apply { addView(col) })
    }

    /**
     * Settings window upright: everything under ← Back centred on the page (Rob, 2026-10-22), like
     * the upright Waterfall Controls: headings, checkboxes, buttons, and each label centred over its
     * box. Frames (Speed) keep the full width, their list centred inside as one block.
     */
    private fun centreUpright(col: LinearLayout) {
        for (i in 0 until col.childCount) {
            val v = col.getChildAt(i)
            if (v is FrameLayout) {
                val box = v.getChildAt(0) as? LinearLayout ?: continue
                for (j in 0 until box.childCount) centreChild(box.getChildAt(j))
                continue
            }
            centreChild(v)
            if (v is LinearLayout && v.orientation == LinearLayout.VERTICAL) v.gravity = Gravity.CENTER_HORIZONTAL
            // Button rows: no space after the last button, so the row is centred on the buttons
            if (v is LinearLayout && v.orientation == LinearLayout.HORIZONTAL && v.childCount > 0) {
                val last = v.getChildAt(v.childCount - 1)
                (last.layoutParams as? ViewGroup.MarginLayoutParams)?.let { it.marginEnd = 0; last.layoutParams = it }
            }
        }
    }

    private fun centreChild(v: View) {
        val lp = v.layoutParams as? LinearLayout.LayoutParams
            ?: LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.width = ViewGroup.LayoutParams.WRAP_CONTENT
        lp.gravity = Gravity.CENTER_HORIZONTAL
        v.layoutParams = lp
    }

    /** Two columns side by side, half the width each, tops level. */
    private fun twoColumns(parent: LinearLayout): Pair<LinearLayout, LinearLayout> {
        val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false }
        val left = column()
        val right = column()
        row.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = px(8) })
        row.addView(right, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = px(8) })
        parent.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return left to right
    }

    /** Tables (frequencies, schedule, saved messages, notifications, diagnostics) keep the full width. */
    private fun isWide(item: Item): Boolean = item is Special && item.id in WIDE_SPECIALS ||
        item is Group && item.items.any { isWide(it) }

    private val HEADED = "headed"

    /** Radio page sideways: shorter labels for checkboxes beside a spin box (Rob, 2026-10-21). */
    private val SHORT_CHECK_LABELS = mapOf("js8cfg_hold_ptt" to "Hold PTT between frames")

    private val WIDE_SPECIALS = setOf("frequencies_table", "schedule_table", "saved_messages", "notifications_table", "diagnostics_text")

    /**
     * Sideways Station page (Rob, 2026-10-20: no tabs, use the width): each sub-tab's heading,
     * then its framed groups in two columns, each group into the shorter column. A sub-tab with
     * one group shares the columns with its neighbours, its heading on top of the group. Tables
     * take the full width.
     */
    /** Sideways Station: width of one of the two columns (window less 10 dp each side and the 16 dp gap). */
    /** Sideways Station: boxes per row on the page shown (2, or 3 on [THREE_ACROSS_PAGES]). */
    private var columns = 2
    // Radio, then Audio ("tidy up audio", Rob 2026-10-21)
    private val THREE_ACROSS_PAGES = setOf("Radio", "Audio")
    /** Packed pages: a field and the one named here sit side by side (Audio: device beside its channel). */
    /** Packed pages: these boxes go under the box before them, not beside it (Rob, 2026-10-21). */
    private val STACK_UNDER_PREVIOUS = setOf("Remember power settings by band")
    private val SIDE_BY_SIDE = mapOf("last_audio_device_id" to "js8cfg_input_channel", "js8cfg_output_device" to "js8cfg_output_channel")
    private val colWidth get() = if (columns == 3) ((resources.displayMetrics.widthPixels - px(20) - 2 * px(THREE_GAP)) / 3)
        else ((resources.displayMetrics.widthPixels - px(36)) / 2).coerceAtLeast(px(200))
    private val THREE_GAP = 20
    /** Radio page sideways: boxes as wide as what they hold, the same height across a row (Rob, 2026-10-21). */
    private val packed get() = station && sideways && columns == 3

    /**
     * Sideways Radio page (Rob, 2026-10-21: "use the space", "4 in row one is way too crowded"):
     * boxes three to a row in page order, each as wide as what it holds ("huge gaps" inside the
     * boxes); a box with a checkbox's wrapping text takes the rest of its row. A sub-tab's heading sits
     * over its first box, level with the tops of earlier boxes in the row; its other boxes in that
     * row are lowered to match. Tables keep the full width. The first row holds four, with Split
     * Operation moved up beside Mode (Rob, 2026-10-21: "move the split box to the top row").
     */
    private fun renderThreeAcross(col: LinearLayout, secs: List<Section>) {
        class Cell(val view: View, val heading: String?, val section: Section, val fill: Boolean) {
            /** Boxes stacked under this one in the same column (Audio: Remember power settings by band). */
            val under = mutableListOf<View>()
        }
        val row = mutableListOf<Cell>()
        var rows = 0
        fun heading(text: String) = label(text, size = 1.1f, bold = true).apply { setPadding(0, 0, 0, px(2)) }
        fun endRow() {
            if (row.isEmpty()) return
            val line = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false }
            // Every box in the row starts under the row's heading line and ends at the tallest box's bottom
            val rowHeading = row.firstNotNullOfOrNull { it.heading }
            // Beside a stacked column the boxes keep their own height (Audio: Modulation Soundcard ends
            // under Output channel, Rob 2026-10-21); otherwise every box stretches to the tallest
            val stacked = row.any { it.under.isNotEmpty() }
            row.forEachIndexed { i, c ->
                val cell = column()
                when {
                    c.heading != null -> cell.addView(heading(c.heading))
                    rowHeading != null -> cell.addView(heading(rowHeading).apply { visibility = View.INVISIBLE })
                }
                cell.addView(c.view)
                // A stacked column keeps its boxes' own heights
                // Same 14 dp gap as between rows, so Audio fits sideways without scrolling
                c.under.forEach { v -> cell.addView(v) }
                if (!stacked) (c.view.layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
                    lp.height = 0; lp.weight = 1f; c.view.layoutParams = lp
                    (c.view as? FrameLayout)?.getChildAt(0)?.let { box ->
                        box.layoutParams = box.layoutParams.apply { height = ViewGroup.LayoutParams.MATCH_PARENT }
                    }
                }
                line.addView(cell, (if (c.fill) LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                    else LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)).apply {
                    if (i > 0) marginStart = px(THREE_GAP)
                })
            }
            col.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = px(14)
            })
            row.clear()
            rows++
        }
        for (s in secs) {
            var first = true
            // Split Operation before Transmit Audio Source, so it reaches the first row
            val items = s.items.toMutableList()
            val split = items.indexOfFirst { it is RadioItem && it.field.key == "js8cfg_split" }
            val audio = items.indexOfFirst { it is RadioItem && it.field.key == "js8cfg_tx_audio_source" }
            if (split > audio && audio >= 0) items.add(audio, items.removeAt(split))
            for (item in items) {
                val v = itemView(item) ?: continue
                if (isWide(item)) {
                    endRow()
                    if (first) s.subTab?.let { col.addView(heading(it)) }
                    col.addView(v)
                } else if (item is Group && item.title in STACK_UNDER_PREVIOUS && row.isNotEmpty()) {
                    row.last().under.add(v)
                } else {
                    row.add(Cell(v, if (first) s.subTab else null, s, hasCheck(item)))
                    if (row.size == (if (rows == 0) 4 else 3)) endRow()
                }
                first = false
            }
        }
        endRow()
    }

    // Only a long checkbox label (Hold PTT) widens its box; Transmit / Tune keep theirs narrow
    private fun hasCheck(item: Item): Boolean = item is Field && item.kind == Kind.CHECK && textWidth(item.label) > px(200) ||
        item is Group && item.items.any { hasCheck(it) }

    private fun renderSidewaysPage(col: LinearLayout, secs: List<Section>) {
        var cols: Pair<LinearLayout, LinearLayout>? = null
        val heights = intArrayOf(0, 0)
        fun flush() { cols = null; heights.fill(0) }
        fun place(v: View) {
            val c = cols ?: twoColumns(col).also { cols = it }
            v.measure(View.MeasureSpec.makeMeasureSpec(colWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            val i = if (heights[1] < heights[0]) 1 else 0
            val target = if (i == 0) c.first else c.second
            // A heading on its group: space above it only under another group, so both columns' tops line up
            if (v.tag == HEADED && target.childCount > 0) v.setPadding(0, px(10), 0, 0)
            target.addView(v)
            heights[i] += v.measuredHeight + px(14)
        }
        // No space above a heading at the top of the page
        fun heading(text: String, top: Boolean = col.childCount > 0) = label(text, size = 1.1f, bold = true).apply {
            setPadding(0, px(if (top) 10 else 0), 0, px(2))
        }
        for (s in secs) {
            val views = s.items.mapNotNull { item -> itemView(item)?.let { item to it } }
            if (views.size == 1 && !isWide(views[0].first)) {
                val v = views[0].second
                place(if (s.subTab == null) v else column().apply { tag = HEADED; addView(heading(s.subTab, top = false)); addView(v) })
            } else {
                flush()
                s.subTab?.let { col.addView(heading(it)) }
                for ((item, v) in views) if (isWide(item)) { flush(); col.addView(v) } else place(v)
                flush()
            }
        }
    }

    private fun renderItems(parent: LinearLayout, items: List<Item>) {
        // Sideways Station: My Callsign and My Maidenhead Grid Locator side by side, SWL under
        // them (Rob, 2026-10-20)
        val fields = items.filterIsInstance<Field>()
        val call = fields.firstOrNull { it.key == "callsign" }.takeIf { station && sideways }
        // Only when both fit in the column with the grid's label on one line (narrower phones stack them)
        val grid = fields.firstOrNull { it.key == "grid" }?.takeIf { g -> call != null &&
            (compactWidth(call) ?: colWidth) + px(8) + maxOf(textWidth(g.label) + px(6), compactWidth(g) ?: colWidth) <= colWidth - px(20) }
        for (item in items) {
            if (call != null && grid != null && item === call) {
                parent.addView(LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(fieldRow(call))
                    addView(fieldRow(grid), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = px(8) })
                })
                continue
            }
            if (grid != null && item === grid) continue
            // Radio page: a checkbox right after a spin box sits beside its ▲▼ (Advanced: Tx delay, Hold PTT),
            // laid out like the spin box: its label level with "Tx delay:", the box under it level with the
            // value (Rob, 2026-10-21), label shortened to "Hold PTT between frames" (Rob)
            val next = items.getOrNull(items.indexOf(item) + 1)
            if (packed && item is Field && item.kind == Kind.SPIN && next is Field && next.kind == Kind.CHECK && next.visible(draft)) {
                val box = checkBox(next).apply { text = ""; minHeight = 0; minimumHeight = 0 }
                parent.addView(LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(fieldRow(item))
                    addView(column().apply {
                        setPadding(0, px(4), 0, px(4))
                        addView(label(SHORT_CHECK_LABELS[next.key] ?: next.label).apply {
                            if (!box.isEnabled) alpha = 0.5f
                            setOnClickListener { if (box.isEnabled) box.toggle() }
                        })
                        // the checkbox's square starts ~6 dp into its view: pulled left to line up with the label
                        addView(box, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(34)).apply { topMargin = px(4); marginStart = -px(6) })
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = px(16) })
                })
                continue
            }
            if (packed && item is Field && item.kind == Kind.CHECK && items.getOrNull(items.indexOf(item) - 1).let {
                    it is Field && it.kind == Kind.SPIN && it.visible(draft) }) continue
            // Packed pages: pairs side by side, two by two in the box (Audio: Input | Input channel, Output | Output channel)
            val partner = (item as? Field)?.let { f -> SIDE_BY_SIDE[f.key]?.let { k -> items.firstOrNull { it is Field && it.key == k } } } as? Field
            if (packed && item is Field && item.visible(draft) && partner != null && partner.visible(draft)) {
                parent.addView(LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(fieldRow(item))
                    addView(fieldRow(partner), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = px(16) })
                })
                continue
            }
            if (packed && item is Field && SIDE_BY_SIDE.entries.any { (a, b) -> b == item.key &&
                    items.any { it is Field && it.key == a && it.visible(draft) } }) continue
            itemView(item)?.let { parent.addView(it) }
        }
    }

    private fun itemView(item: Item): View? = when (item) {
        is Group -> if (item.visible(draft) && hasVisible(item.items)) groupBox(item.title) { box -> renderItems(box, item.items) } else null
        is RadioItem -> radioGroup(item)
        is Field -> if (item.visible(draft)) fieldRow(item) else null
        is ButtonRow -> buttonRow(item)
        is Note -> label(item.text).apply { setPadding(0, px(4), 0, px(4)) }
        is Special -> special(item.id)
        else -> null
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
            // Radio page: at least as wide as the title on its top edge
            if (packed && title != null) minimumWidth = textWidth(title) + px(36)
        }
        fill(box)
        frame.addView(box, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            // Radio page: a box without a title starts level with titled boxes' frame lines
            topMargin = px(if (title != null || packed) 9 else 0)
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

    /** Label + control, label above the box (sideways too since 2026-10-20). */
    private fun fieldRow(f: Field): View {
        if (f.kind == Kind.CHECK) return checkBox(f)
        val control = when (f.kind) {
            Kind.TEXT -> textBox(f)
            Kind.SPIN -> spinBox(f)
            Kind.CHOICE, Kind.AUDIO_INPUT -> choiceBox(f)
            else -> View(requireContext())
        }
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, px(4), 0, px(4))
        }
        // Spin boxes grey with everything inside them (PC set_rig_invariants, Poll Interval)
        if (f.kind == Kind.SPIN && !f.active(draft)) {
            fun off(v: View) { v.isEnabled = false; (v as? ViewGroup)?.let { g -> (0 until g.childCount).forEach { off(g.getChildAt(it)) } } }
            off(control)
            control.alpha = 0.5f
        }
        // As wide as its text, not the box below it (side by side the row is only the box's width)
        row.addView(label(f.label).apply { if (!f.enabled || !f.active(draft)) alpha = 0.5f },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        // Settings window: boxes sized to their values, left under the label (Rob, 2026-10-20:
        // "runaway fields"); Station boxes fill their page upright; sideways only messages, groups
        // and callsign lists fill their column, the rest are as wide as their longest value
        val width = when {
            station && sideways -> compactWidth(f) ?: ViewGroup.LayoutParams.MATCH_PARENT
            // Upright Radio page: Poll Interval as wide as "99 s" with its arrows (Rob, 2026-10-22)
            station && f.key == "js8cfg_cat_poll_interval" -> px(90)
            station -> ViewGroup.LayoutParams.MATCH_PARENT
            f.kind == Kind.CHOICE -> px(200)
            else -> px(120) // Custom frequency (MHz), Offset with its ▲▼
        }
        row.addView(control, LinearLayout.LayoutParams(width, px(34)).apply { topMargin = px(4) })
        return row
    }

    /** Width of [text] in the settings font, in px. */
    private fun textWidth(text: String) = android.graphics.Paint().apply { typeface = font; textSize = textPx }.measureText(text).toInt()

    /**
     * Sideways Station box width: the longest value it can show, plus padding (and ▲▼ or the
     * warning mark), at most the column. Null = fill the column (free text: messages, lists).
     */
    private fun compactWidth(f: Field): Int? {
        val longest = when (f.kind) {
            Kind.SPIN -> listOf(spinText(f, f.min.toString()), spinText(f, f.max.toString()))
            Kind.CHOICE, Kind.AUDIO_INPUT -> choices(f).map { "${it.first}  ▾" }
            Kind.TEXT -> listOfNotNull(SHORT_TEXT[f.key] ?: return null)
            else -> return null
        }.maxOf { textWidth(it) }
        val extra = px(22) + (if (f.kind == Kind.SPIN) px(28) else 0) + (if (f.required) px(22) else 0) // warning mark 18 dp + 4 dp gap
        // Three to a row: the box also fits inside its group's frame
        // Radio page: up to half the page, so the longest rig name shows in full
        return (longest + extra).coerceAtMost(if (packed) (resources.displayMetrics.widthPixels - px(20)) / 2 else colWidth)
    }

    /** Short free-text boxes and the longest value each is expected to hold. */
    private val SHORT_TEXT = mapOf(
        "callsign" to "VA3OSO/MM", "js8cfg_op_call" to "VA3OSO/MM", "grid" to "FN03HP45KL22",
        "js8cfg_eot" to "♢♢♢♢", "js8cfg_mfi" to "……………", "rigctld_host" to "192.168.100.100",
        "rigctld_port" to "65535", "qmx_cat_baud" to "115200", "js8cfg_aprs_server" to "rotate.aprs2.net",
        com.js8call.example.api.Js8Api.KEY_UDP_SERVER to "192.168.100.100",
        com.js8call.example.api.Js8Api.KEY_TCP_SERVER to "192.168.100.100",
        com.js8call.example.api.Js8Api.KEY_WSJTX_SERVER to "192.168.100.100",
        com.js8call.example.api.Js8Api.KEY_N3FJP_SERVER to "192.168.100.100",
        com.js8call.example.api.Js8Api.KEY_N1MM_SERVER to "192.168.100.100"
    )

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
        // Android 8.0+ only (the app runs on 7.0 since 2026-10-22)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
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
            if (f.key in Js8SettingsSchema.ACTIVE_TRIGGERS) content.post { render(keepScroll = true) } // ACK follows AUTO and HB
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
                if (f.key == Js8SettingsSchema.RIG_KEY) { searchableChoice(f, list, idx); return@setOnClickListener }
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

    /**
     * Rig list with a search box (Rob, 2026-10-21): typing keeps the rigs whose name contains it,
     * ignoring case, spaces and dashes ("ic7300" finds "Icom IC-7300"); tap one to pick it.
     */
    private fun searchableChoice(f: Field, list: List<Pair<String, String>>, idx: Int) {
        val ctx = requireContext()
        fun key(t: String) = t.lowercase(Locale.US).filter { it.isLetterOrDigit() }
        var shown = list
        val adapter = android.widget.ArrayAdapter(ctx, android.R.layout.simple_list_item_single_choice, shown.map { it.first.ifEmpty { " " } }.toMutableList())
        val listView = android.widget.ListView(ctx).apply {
            choiceMode = android.widget.ListView.CHOICE_MODE_SINGLE
            this.adapter = adapter
            if (idx >= 0) { setItemChecked(idx, true); setSelection(idx) }
        }
        val search = EditText(ctx).apply {
            hint = "Search (e.g. 7300, yaesu)"
            isSingleLine = true
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(16), px(8), px(16), 0)
            addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(listView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        val dialog = AlertDialog.Builder(ctx).setTitle(f.label.trimEnd(':')).setView(box)
            .setNegativeButton(android.R.string.cancel, null).create()
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val q = key(s?.toString().orEmpty())
                shown = if (q.isEmpty()) list else list.filter { key(it.first).contains(q) }
                adapter.clear()
                adapter.addAll(shown.map { it.first.ifEmpty { " " } })
                listView.clearChoices()
                val sel = shown.indexOfFirst { it.second == (draft.values[f.key] ?: draft.get(f)) }
                if (sel >= 0) listView.setItemChecked(sel, true)
            }
        })
        listView.setOnItemClickListener { _, _, pos, _ ->
            dialog.dismiss()
            draft.set(f, shown[pos].second)
            render() // some choices show / hide other settings (Rig)
        }
        dialog.show()
        // Tall enough for the list under the search box
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, (resources.displayMetrics.heightPixels * 0.8f).toInt())
    }

    private fun radioGroup(item: RadioItem): View = groupBox(item.title) { box ->
        // One option per line in every layout: side by side overran a sideways column (2026-10-20)
        val rg = RadioGroup(requireContext()).apply { orientation = RadioGroup.VERTICAL }
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
                setOnCheckedChangeListener { _, c ->
                    if (!c) return@setOnCheckedChangeListener
                    draft.set(item.field, value)
                    if (item.field.key in Js8SettingsSchema.ACTIVE_TRIGGERS) content.post { render(keepScroll = true) }
                }
                if (value in item.disabled || value in item.greyed(draft)) { isEnabled = false; alpha = 0.5f }
            })
        }
        box.addView(rg)
    }

    private fun buttonRow(item: ButtonRow): View {
        val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, px(6), 0, px(6)) }
        item.buttons.forEach { (lab, action) ->
            val b = button(lab) { onAction(action) }
            if (action == "configurations") b.setOnClickListener {
                Js8Configurations.showMenu(this, android.widget.PopupMenu(android.view.ContextThemeWrapper(requireContext(), R.style.ThemeOverlay_Js8PopupMenu), it))
            }
            if (action == "test_cat" || action == "test_ptt") {
                b.isEnabled = Js8SettingsSchema.rigTestEnabled(draft, action)
                val tint = if (action == "test_cat") vm.testCat?.let { if (it) Color.rgb(0, 128, 0) else Color.RED }
                    else if (vm.testPtt) Color.RED else null
                if (tint != null) { b.backgroundTintList = ColorStateList.valueOf(tint); b.setTextColor(Color.WHITE) }
            }
            row.addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(28)).apply { marginEnd = px(8) })
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
            "reset_window_sizes" -> { Js8LineDividers.reset(draft.prefs); Js8PcSplitters.clear(draft.prefs); toast("Window sizes reset") }
            "copyright" -> AlertDialog.Builder(requireContext()).setMessage(Js8MainFragment.COPYRIGHT_NOTICE)
                .setPositiveButton(android.R.string.ok, null).show()
            "check_updates" -> Js8Updates.check(requireActivity(), true)
            "user_guide" -> startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(Js8MainFragment.USER_GUIDE_URL)))
            "exit" -> { // PC File > Exit
                requireContext().stopService(Intent(requireContext(), JS8EngineService::class.java))
                requireActivity().finishAndRemoveTask()
            }
            "test_cat" -> {
                // The rig opens from saved settings: the PC tests the window's values, so save the Radio page first
                val e = draft.prefs.edit()
                allFields(sections.filter { it.tab == "Radio" }.flatMap { it.items }).forEach { f ->
                    draft.values[f.key]?.let { v -> f.save(e, v, requireContext()) }
                }
                e.commit()
                vm.testCat = null
                render()
                requireContext().startService(Intent(requireContext(), JS8EngineService::class.java).setAction(JS8EngineService.ACTION_TEST_CAT))
            }
            "test_ptt" -> sendTestPtt(!vm.testPtt)
            // Settings > Log > Export Log...: the Export Options page
            "export_log" -> findNavController().navigate(R.id.navigation_js8_export)
            // PC Log menu
            "erase_all_txt" -> confirmErase("Are you sure you want to erase file ALL.TXT?") {
                requireContext().startService(Intent(requireContext(), JS8EngineService::class.java).setAction(JS8EngineService.ACTION_ERASE_LOG))
            }
            "erase_adi" -> confirmErase("Are you sure you want to erase file js8call_log.adi?") {
                com.js8call.example.data.Js8LogBook.adifFile(requireContext()).delete()
                com.js8call.example.data.Js8LogBook.forget()
            }
            "diag_run" -> { vm.diagnostics = diagnostics(); render() }
            "diag_copy" -> {
                val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Easy JS8 diagnostics", vm.diagnostics.ifEmpty { diagnostics() }))
                toast("Copied")
            }
        }
    }

    private fun sendTestPtt(on: Boolean) {
        requireContext().startService(Intent(requireContext(), JS8EngineService::class.java)
            .setAction(JS8EngineService.ACTION_TEST_PTT).putExtra(JS8EngineService.EXTRA_RIG_TEST_ON, on))
    }

    private fun toast(t: String) = Toast.makeText(requireContext(), t, Toast.LENGTH_SHORT).show()

    /** Under My Maidenhead Grid Locator: Acquire GPS, the grid it found, Set (copies it into the grid box). */
    private fun gpsRow(): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, px(2), 0, px(6))
        }
        val found = styledField(TextView(requireContext())).apply { text = vm.gpsText.value }
        gpsBox = found
        row.addView(button("Acquire GPS") { acquireGps() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(28)))
        // Sideways the found-grid box is only as wide as "No position" (Rob, 2026-10-20)
        row.addView(found, (if (sideways) LinearLayout.LayoutParams(textWidth("No position") + px(24), px(28))
            else LinearLayout.LayoutParams(0, px(28), 1f)).apply { marginStart = px(8); marginEnd = px(8) })
        row.addView(button("Set") { vm.gpsGrid?.let { gridBox?.setText(it) } },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(28)))
        return row
    }

    private fun diagnostics(): String {
        val ctx = requireContext()
        val p = draft.prefs
        return buildString {
            appendLine("Easy JS8 Diagnostics")
            appendLine("Timestamp: ${java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(java.util.Date())}")
            appendLine()
            appendLine("Application")
            appendLine("  App name:     Easy JS8 ${BuildConfig.VERSION_NAME}")
            appendLine("  Package:      ${ctx.packageName}")
            appendLine("  Config file:  ${ctx.packageName}_preferences.xml")
            appendLine("  Data directory:    ${ctx.filesDir.absolutePath}")
            appendLine()
            appendLine("System")
            appendLine("  OS:           Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("  Device:       ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("  Kernel:       ${System.getProperty("os.version")}")
            appendLine("  CPU arch:     ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine()
            appendLine("Selected (from settings)")
            appendLine("  Input:        ${AudioDevices.selectedName(ctx)}")
            appendLine("  Output:       ${p.getString("js8cfg_output_device", "default")}")
            appendLine("  Notification: ${p.getString("js8cfg_notification_output", "default")}")
            appendLine()
            appendLine("Audio outputs available:")
            AudioDevices.outputs(ctx).forEach { appendLine("  ${it.name} (id ${it.id})") }
            appendLine()
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
        "power_slider" -> powerSlider()
        "frequencies_table" -> frequencyTable()
        "schedule_table" -> scheduleTable()
        "saved_messages" -> savedMessages()
        "notifications_table" -> notifications()
        "wsjtx_interfaces" -> wsjtxInterfaces()
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

    /** PC frequencies_table_view and its right-click menu: Insert ..., Delete, Reset. */
    private fun frequencyTable(): View {
        val key = Js8SettingsSchema.FREQUENCIES_KEY
        fun current() = Js8SettingsSchema.frequencyItems(requireContext(), draft.values[key] ?: draft.prefs.getString(key, null))
        fun rows(items: List<Js8SettingsSchema.FrequencyItem>) = items.mapIndexed { i, f ->
            Js8TableView.Row("$i", mapOf(
                "region" to f.region, "mode" to f.mode,
                "freq" to com.js8call.example.util.BandUtils.bandLabelForFrequencyHz(f.hz),
                "mhz" to String.format(Locale.US, "%.6f", f.hz / 1_000_000.0),
                "desc" to f.description
            ))
        }
        val list = table("cfg_freq", listOf(
            Js8TableView.Column("region", "IARU Region", 90), Js8TableView.Column("mode", "Mode", 60),
            Js8TableView.Column("freq", "Frequency", 90), Js8TableView.Column("mhz", "Frequency (MHz)", 120),
            Js8TableView.Column("desc", "Description", 120, stretch = true)
        ), rows(current()), 160)
        list.onRowClick = { list.selectedRowId = it.id }
        fun refresh(items: List<Js8SettingsSchema.FrequencyItem>) {
            val sorted = items.sortedBy { it.hz }
            draft.values[key] = sorted.joinToString("\n") { it.line() }
            list.selectedRowId = null
            list.setRows(rows(sorted))
        }
        // PC edits cells in place; a touch table opens the same fields filled in
        list.onRowLongClick = { row, _ ->
            val i = row.id.toInt()
            insertFrequency(current().getOrNull(i)) { edited -> refresh(current().filterIndexed { n, _ -> n != i } + edited) }
        }
        val col = column()
        val buttons = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(button("Insert ...") { insertFrequency { refresh(current() + it) } }, LinearLayout.LayoutParams(0, px(30), 1f))
        buttons.addView(button("Delete") {
            val sel = list.selectedRowId?.toIntOrNull() ?: return@button
            refresh(current().filterIndexed { i, _ -> i != sel })
        }, LinearLayout.LayoutParams(0, px(30), 1f).apply { marginStart = px(6) })
        val files = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
        files.addView(button("Load ...") {
            frequencyFile = { loaded ->
                if (loaded.isNotEmpty() && current().isEmpty()) refresh(loaded)
                else if (loaded.isNotEmpty()) AlertDialog.Builder(requireContext()).setTitle("Replace Working Frequencies")
                    .setMessage("Are you sure you want to discard your current working frequencies and replace them with the loaded ones?")
                    .setPositiveButton("Yes") { _, _ -> refresh(loaded) }.setNegativeButton("No", null).show()
            }
            pickFrequencies.launch(arrayOf("*/*"))
        }, LinearLayout.LayoutParams(0, px(30), 1f))
        files.addView(button("Save as ...") {
            val all = current()
            val selected = list.selectedRowId?.toIntOrNull()?.let { all.getOrNull(it) }
            fun save(items: List<Js8SettingsSchema.FrequencyItem>) {
                frequencySave = Js8Qrg.write(items)
                saveFrequencies.launch("frequencies.qrg")
            }
            if (selected == null) save(all)
            else AlertDialog.Builder(requireContext()).setTitle("Only Save Selected  Working Frequencies")
                .setMessage("Are you sure you want to save only the working frequencies that are currently selected? Click No to save all.")
                .setPositiveButton("Yes") { _, _ -> save(listOf(selected)) }
                .setNegativeButton("No") { _, _ -> save(all) }.show()
        }, LinearLayout.LayoutParams(0, px(30), 1f).apply { marginStart = px(6) })
        files.addView(button("Merge ...") {
            frequencyFile = { loaded -> refresh((current() + loaded).distinct()) } // PC: only rows not in the table
            pickFrequencies.launch(arrayOf("*/*"))
        }, LinearLayout.LayoutParams(0, px(30), 1f).apply { marginStart = px(6) })
        buttons.addView(button("Reset") {
            AlertDialog.Builder(requireContext()).setTitle("Reset Working Frequencies")
                .setMessage("Are you sure you want to discard your current working frequencies and replace them with default ones?")
                .setPositiveButton("Yes") { _, _ -> refresh(Js8SettingsSchema.frequencyItems(requireContext(), null)) }
                .setNegativeButton("No", null).show()
        }, LinearLayout.LayoutParams(0, px(30), 1f).apply { marginStart = px(6) })
        col.addView(buttons)
        col.addView(files, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(6) })
        col.addView(list)
        return col
    }

    private var frequencyFile: ((List<Js8SettingsSchema.FrequencyItem>) -> Unit)? = null
    private var frequencySave = ByteArray(0)
    private val pickFrequencies = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) requireContext().contentResolver.openInputStream(uri)?.use { s ->
            try {
                frequencyFile?.invoke(Js8Qrg.read(s.readBytes()))
            } catch (e: Js8Qrg.BadFile) {
                AlertDialog.Builder(requireContext()).setTitle("Not a valid frequencies file").setMessage(e.message)
                    .setPositiveButton(android.R.string.ok, null).show()
            }
        }
    }
    private val saveFrequencies = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) requireContext().contentResolver.openOutputStream(uri)?.use { it.write(frequencySave) }
    }

    /** PC FrequencyDialog "Add Frequency"; with [item], the same fields for an edit. */
    private fun insertFrequency(item: Js8SettingsSchema.FrequencyItem? = null, onAdd: (Js8SettingsSchema.FrequencyItem) -> Unit) {
        val box = column().apply { setPadding(px(16), px(8), px(16), 0) }
        fun title(name: String) = box.addView(label(name).apply { setTextColor(Color.BLACK) })
        fun spinner(items: List<String>) = android.widget.Spinner(requireContext()).apply {
            adapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, items)
            box.addView(this)
        }
        title("IARU Region:")
        val regions = listOf("All", "Region 1", "Region 2", "Region 3")
        val region = spinner(regions)
        title("Mode:")
        val modes = listOf("All", "JS8")
        val mode = spinner(modes)
        mode.setSelection(1)
        title("Frequency (MHz):")
        val mhz = styledField(EditText(requireContext())).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            box.addView(this)
        } as EditText
        title("Description:")
        val desc = styledField(EditText(requireContext())).apply { inputType = InputType.TYPE_CLASS_TEXT; box.addView(this) } as EditText
        item?.let {
            region.setSelection(regions.indexOf(it.region).coerceAtLeast(0))
            mode.setSelection(modes.indexOf(it.mode).coerceAtLeast(0))
            mhz.setText(String.format(Locale.US, "%.6f", it.hz / 1_000_000.0))
            desc.setText(it.description)
        }
        val dialog = AlertDialog.Builder(requireContext()).setTitle(if (item == null) "Add Frequency" else "Edit Frequency").setView(ScrollView(requireContext()).apply { addView(box) })
            .setPositiveButton(android.R.string.ok, null).setNegativeButton(android.R.string.cancel, null).create()
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val hz = mhz.text.toString().trim().toDoubleOrNull()?.let { Math.round(it * 1_000_000.0) } ?: 0L
                if (hz <= 0L) { mhz.error = "MHz"; return@setOnClickListener }
                onAdd(Js8SettingsSchema.FrequencyItem(hz, region.selectedItem.toString(), mode.selectedItem.toString(),
                    desc.text.toString().replace('|', ' ').replace('\n', ' ').trim()))
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    /** Settings > Power: PC outAttenuation, acts at once like the PC slider (also while TUNE is on), not on Save. */
    private fun powerSlider(): View {
        val key = JS8EngineService.PREF_OUT_ATTENUATION
        val start = (draft.prefs.all[key]?.toString()?.toIntOrNull() ?: 0).coerceIn(0, 450) // PC default 0
        val value = label("")
        fun show(a: Int) { value.text = String.format(Locale.US, "%.1f dB", -a / 10.0) }
        show(start)
        val col = column()
        col.addView(value)
        // Right = more power, a = 450 - progress
        col.addView(android.widget.SeekBar(requireContext()).apply {
            max = 450
            progress = 450 - start
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: android.widget.SeekBar, p: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    show(450 - p)
                    draft.prefs.edit().putString(key, (450 - p).toString()).apply()
                }
                override fun onStartTrackingTouch(sb: android.widget.SeekBar) {}
                override fun onStopTrackingTouch(sb: android.widget.SeekBar) {}
            })
        }, LinearLayout.LayoutParams(px(240), ViewGroup.LayoutParams.WRAP_CONTENT))
        return col
    }

    private fun confirmErase(message: String, erase: () -> Unit) {
        AlertDialog.Builder(requireContext()).setTitle("Confirm Erase").setMessage(message)
            .setPositiveButton("Yes") { _, _ -> erase() }.setNegativeButton("No", null).show()
    }

    /** PC stations_table_view and its right-click menu: Insert ..., Delete, Switch to Frequency Now. */
    private fun scheduleTable(): View {
        val key = Js8SettingsSchema.SCHEDULE_KEY
        fun current(): MutableList<String> = (draft.values[key] ?: draft.prefs.getString(key, "")).orEmpty()
            .split('\n').filter { it.isNotBlank() }.toMutableList()
        fun rows(items: List<String>) = items.mapIndexed { i, line ->
            val p = line.split('|', limit = 4)
            val hz = p[0].toLongOrNull() ?: 0L
            Js8TableView.Row("$i", mapOf(
                "band" to com.js8call.example.util.BandUtils.bandLabelForFrequencyHz(hz),
                "mhz" to String.format(Locale.US, "%.6f", hz / 1_000_000.0),
                "at" to p.getOrElse(1) { "" }, "until" to p.getOrElse(2) { "" }, "desc" to p.getOrElse(3) { "" }
            ))
        }
        val list = table("cfg_sched", listOf(
            Js8TableView.Column("band", "Band", 60), Js8TableView.Column("mhz", "Freq. (MHz)", 90),
            Js8TableView.Column("at", "Switch at (UTC)", 110), Js8TableView.Column("until", "Until (UTC)", 90),
            Js8TableView.Column("desc", "Description", 120, stretch = true)
        ), rows(current()), 110)
        list.onRowClick = { list.selectedRowId = it.id }
        fun refresh(items: List<String>) {
            draft.values[key] = items.joinToString("\n")
            list.selectedRowId = null
            list.setRows(rows(items))
        }
        list.onRowLongClick = { row, _ ->
            val i = row.id.toInt()
            insertSchedule(current().getOrNull(i)) { line ->
                refresh((current().filterIndexed { n, _ -> n != i } + line).sortedBy { it.split('|').getOrElse(1) { "" } })
            }
        }
        val col = column()
        val buttons = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(button("Insert ...") { insertSchedule { line ->
            refresh((current() + line).sortedBy { it.split('|').getOrElse(1) { "" } })
        } }, LinearLayout.LayoutParams(0, px(30), 1f))
        buttons.addView(button("Delete") {
            val sel = list.selectedRowId?.toIntOrNull() ?: return@button
            refresh(current().filterIndexed { i, _ -> i != sel })
        }, LinearLayout.LayoutParams(0, px(30), 1f).apply { marginStart = px(6) })
        col.addView(buttons)
        col.addView(button("Switch to Frequency Now") {
            val sel = list.selectedRowId?.toIntOrNull() ?: return@button
            val hz = current().getOrNull(sel)?.substringBefore('|')?.toLongOrNull() ?: return@button
            requireContext().startService(Intent(requireContext(), JS8EngineService::class.java).apply {
                action = JS8EngineService.ACTION_SCHEDULE_HOP_NOW
                putExtra(JS8EngineService.EXTRA_FREQUENCY_HZ, hz)
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(30)).apply { topMargin = px(6) })
        col.addView(list)
        // PC stations_table_view: enabled by Automatically switch bands / frequencies at specific times of day
        val on = (draft.values["js8cfg_auto_switch_bands"] ?: draft.prefs.getBoolean("js8cfg_auto_switch_bands", false).toString()) == "true"
        fun grey(v: View) { v.isEnabled = on; (v as? ViewGroup)?.let { g -> (0 until g.childCount).forEach { grey(g.getChildAt(it)) } } }
        grey(col)
        col.alpha = if (on) 1f else 0.45f
        return col
    }

    /** PC StationDialog "Add Schedule"; with [line], the same fields for an edit. The band comes from the frequency, as the PC saves it. */
    private fun insertSchedule(line: String? = null, onAdd: (String) -> Unit) {
        val box = column().apply { setPadding(px(16), px(8), px(16), 0) }
        fun field(name: String, type: Int): EditText {
            box.addView(label(name).apply { setTextColor(Color.BLACK) })
            return styledField(EditText(requireContext())).apply { inputType = type; box.addView(this) } as EditText
        }
        val number = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        val time = InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_TIME
        val mhz = field("Frequency (MHz):", number)
        val at = field("Switch at (UTC):", time)
        val until = field("Until (UTC):", time)
        val desc = field("Description:", InputType.TYPE_CLASS_TEXT)
        line?.split('|', limit = 4)?.let { p ->
            mhz.setText(String.format(Locale.US, "%.6f", (p[0].toLongOrNull() ?: 0L) / 1_000_000.0))
            at.setText(p.getOrElse(1) { "" }); until.setText(p.getOrElse(2) { "" }); desc.setText(p.getOrElse(3) { "" })
        }
        val dialog = AlertDialog.Builder(requireContext()).setTitle(if (line == null) "Add Schedule" else "Edit Schedule").setView(ScrollView(requireContext()).apply { addView(box) })
            .setPositiveButton(android.R.string.ok, null).setNegativeButton(android.R.string.cancel, null).create()
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val clock = Regex("([01]\\d|2[0-3]):[0-5]\\d")
                val hz = mhz.text.toString().trim().toDoubleOrNull()?.let { Math.round(it * 1_000_000.0) } ?: 0L
                val a = at.text.toString().trim()
                val u = until.text.toString().trim()
                when {
                    hz <= 0L -> mhz.error = "MHz"
                    !clock.matches(a) -> at.error = "HH:MM"
                    !clock.matches(u) -> until.error = "HH:MM"
                    else -> {
                        onAdd("$hz|$a|$u|" + desc.text.toString().replace('|', ' ').replace('\n', ' ').trim())
                        dialog.dismiss()
                    }
                }
            }
        }
        dialog.show()
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
        // PC RearrangableMacrosModel: press and hold, then drag, moves a row
        list.onRowsMoved = { order ->
            val items = current()
            refresh(order.mapNotNull { items.getOrNull(it.toInt()) })
        }
        // PC edits in place on a double click; a touch table opens the text in a box
        list.onRowDoubleClick = { row ->
            val i = row.id.toInt()
            val items = current()
            val edit = styledField(EditText(requireContext())).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            } as EditText
            edit.setText(items[i])
            AlertDialog.Builder(requireContext()).setTitle("Saved Message").setView(edit)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val t = edit.text.toString().trim().uppercase(Locale.US)
                    if (t.isNotEmpty()) { items[i] = t; refresh(items) }
                }.setNegativeButton(android.R.string.cancel, null).show()
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

    /**
     * Reporting > API > Outgoing interfaces: (PC CheckableItemComboBox, load_network_interfaces):
     * the phone's network interfaces that are up, one checkbox each; none chosen = the loopback one.
     * Used only when the WSJT-X Server is a multicast address.
     */
    private fun wsjtxInterfaces(): View {
        val key = com.js8call.example.api.Js8Api.KEY_WSJTX_INTERFACES
        val col = column().apply { setPadding(0, px(4), 0, px(4)) }
        col.addView(label("Outgoing interfaces:"))
        val all = com.js8call.example.api.Js8Api.multicastInterfaces()
        val stored = (draft.values[key] ?: draft.prefs.getString(key, "")).orEmpty()
            .split(',').map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
        if (stored.isEmpty()) all.filter { it.second.startsWith("lo") }.forEach { stored += it.second }
        all.forEach { (name, id) ->
            col.addView(CheckBox(requireContext()).apply {
                text = if (name == id) name else "$name ($id)"
                typeface = font
                setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx)
                setTextColor(color(R.color.js8_frame_text))
                CompoundButtonCompat.setButtonTintList(this, android.content.res.ColorStateList.valueOf(color(R.color.js8_cfg_group_border)))
                isChecked = id in stored
                setOnCheckedChangeListener { _, c ->
                    if (c) stored += id else stored -= id
                    draft.values[key] = all.map { it.second }.filter { it in stored }.joinToString(",")
                }
            })
        }
        return col
    }

    private fun notifications(): View {
        val col = column().apply { setPadding(0, px(4), 0, 0) }
        Js8SettingsSchema.NOTIFY_EVENTS.forEach { (k, name) ->
            val key = "js8cfg_notify_$k"
            val fileKey = Js8SettingsSchema.NOTIFY_FILE_PREFIX + k
            val file = (draft.values[fileKey] ?: draft.prefs.getString(fileKey, "")).orEmpty()
            col.addView(CheckBox(requireContext()).apply {
                text = name
                typeface = font
                setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx)
                setTextColor(color(R.color.js8_frame_text))
                CompoundButtonCompat.setButtonTintList(this, android.content.res.ColorStateList.valueOf(color(R.color.js8_cfg_group_border)))
                isChecked = (draft.values[key] ?: draft.prefs.getString(key, "false")) == "true"
                setOnCheckedChangeListener { _, c -> draft.values[key] = c.toString() }
            })
            // PC notifications table: Sound File, Select, Test, Clear. No file = the phone's notification sound
            val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(label(if (file.isEmpty()) "Phone's notification sound" else soundFileName(file)).apply {
                isSingleLine = true
                ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            fun small(name: String, click: () -> Unit) = row.addView(button(name, click),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(26)).apply { marginStart = px(4) })
            small("Select") { notifyPicking = fileKey; pickNotifySound.launch(arrayOf("audio/*")) }
            small("Test") {
                val uri = if (file.isEmpty()) android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION) else Uri.parse(file)
                android.media.RingtoneManager.getRingtone(requireContext(), uri)?.play()
            }
            small("Clear") { draft.values[fileKey] = ""; draft.values[key] = "false"; render() }
            col.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = px(6) })
        }
        return col
    }

    /** The file's own name; the picker's address alone reads like "msf:1000000024". */
    private fun soundFileName(file: String): String = runCatching {
        requireContext().contentResolver.query(Uri.parse(file), arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull() ?: Uri.decode(file).substringAfterLast('/')

    /** Station > Notifications > Select: the phone's file picker (PC QFileDialog). */
    private var notifyPicking: String? = null
    private val pickNotifySound = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        val key = notifyPicking
        if (uri != null && key != null) {
            runCatching { requireContext().contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            draft.values[key] = uri.toString()
            render()
        }
    }

    /** PC QColorDialog: Red, Green, Blue sliders with a sample, any colour. */
    private fun pickColor(title: String, start: String?, onPick: (String) -> Unit) {
        val first = start?.let { runCatching { Color.parseColor(it) }.getOrNull() } ?: Color.WHITE
        val box = column().apply { setPadding(px(16), px(8), px(16), 0) }
        val sample = View(requireContext())
        box.addView(sample, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(36)))
        val bars = listOf("Red" to Color.red(first), "Green" to Color.green(first), "Blue" to Color.blue(first)).map { (name, v) ->
            box.addView(label(name).apply { setTextColor(Color.BLACK); setPadding(0, px(6), 0, 0) })
            android.widget.SeekBar(requireContext()).apply { max = 255; progress = v; box.addView(this) }
        }
        fun chosen() = Color.rgb(bars[0].progress, bars[1].progress, bars[2].progress)
        sample.setBackgroundColor(first)
        bars.forEach { it.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: android.widget.SeekBar, p: Int, user: Boolean) = sample.setBackgroundColor(chosen())
            override fun onStartTrackingTouch(s: android.widget.SeekBar) {}
            override fun onStopTrackingTouch(s: android.widget.SeekBar) {}
        }) }
        AlertDialog.Builder(requireContext()).setTitle(title).setView(box)
            .setPositiveButton(android.R.string.ok) { _, _ -> onPick(String.format("#%06X", chosen() and 0xFFFFFF)) }
            .setNegativeButton(android.R.string.cancel, null).show()
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
                    // PC QFontDialog: family, style, size
                    AlertDialog.Builder(requireContext()).setTitle("Font").setItems(FONT_FAMILIES.toTypedArray()) { _, f ->
                        AlertDialog.Builder(requireContext()).setTitle("Font style").setItems(FONT_STYLES.toTypedArray()) { _, s ->
                            val sizes = (8..20).map { it.toString() }
                            AlertDialog.Builder(requireContext()).setTitle("Font size")
                                .setItems(sizes.map { "$it pt" }.toTypedArray()) { _, w ->
                                    draft.values[key] = sizes[w]
                                    draft.values["${key}_family"] = FONT_FAMILIES[f]
                                    draft.values["${key}_style"] = FONT_STYLES[s]
                                    paint()
                                }.show()
                        }.show()
                    }.show()
                } else {
                    pickColor(lab, draft.values[key] ?: draft.prefs.getString(key, null)) { draft.values[key] = it; paint() }
                }
            }.apply {
                // 28 dp, taller when the label wraps (Secondary Highlight Background upright)
                minimumHeight = px(28); setPadding(px(10), px(3), px(10), px(3))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(sample, LinearLayout.LayoutParams(px(110), ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = px(8) })
            col.addView(row)
        }
        extra.forEach { (key, lab) -> col.addView(fieldRow(Field(key, lab, Kind.TEXT, "", upper = true))) }
        return col
    }
}
