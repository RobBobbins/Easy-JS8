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
import android.widget.ImageView
import android.widget.LinearLayout
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
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
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

/** Settings window state kept while the phone rotates (the page shown, Test CAT / PTT, Acquire GPS). */
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
 * The Station and Settings windows, drawn like Android's own settings (Rob, 2026-11-24: "i want the android user
 * to be familiar with the settings"): a top bar with ← and the title, headings in the accent colour, one row per
 * setting (its name, the value under it, a switch at the right for on / off), a popup to change a value. Every
 * change is saved at once: no OK, Save or Cancel. Popups check a value as it is typed (the PC's OK checks).
 * Station upright: the list of pages, tap one to open it. Sideways: the list on the left, the page on the right.
 * Settings window: one page, its sections under headings. Radio changes reconnect the radio once, when the Radio
 * page is left, or at Test CAT (JS8EngineService.PREF_RADIO_HOLD).
 */
class Js8SettingsFragment : Fragment() {

    companion object {
        /** True when shown as screen 6 of the Pages layout. */
        const val ARG_EMBEDDED = "embedded"
        /** "station" (default: Menu > File > Station…, Pages screen 6) or "settings" (header Settings). */
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

        /** Station page icons (Material Design icons). */
        private val PAGE_ICONS = mapOf(
            "General" to R.drawable.js8_pref_general, "Radio" to R.drawable.js8_pref_radio,
            "Audio" to R.drawable.js8_pref_audio, Js8SettingsSchema.LAYOUT_PAGE to R.drawable.js8_pref_layout,
            "Reporting" to R.drawable.js8_pref_reporting, "APRS Beacon" to R.drawable.js8_pref_beacon,
            "Frequencies" to R.drawable.js8_pref_frequencies, "Saved Messages" to R.drawable.ic_chat_bubble,
            "Notifications" to R.drawable.js8_pref_notifications, "UI" to R.drawable.js8_pref_ui,
            "Diagnostics" to R.drawable.js8_pref_diagnostics, "Phone" to R.drawable.js8_pref_phone,
            ABOUT_PAGE to R.drawable.js8_pref_about
        )
    }

    private lateinit var vm: Js8SettingsViewModel
    private lateinit var draft: Js8SettingsSchema.Draft
    /** Station window: the PC's File > Settings. Otherwise the operating Settings window. */
    private val station get() = (arguments?.getString(ARG_WINDOW) ?: "station") == "station"
    private val sections by lazy {
        if (station) Js8SettingsSchema.stationSections() else Js8SettingsSchema.settingsSections(
            PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getBoolean("js8cfg_autoreply_confirmation", true))
    }
    private lateinit var content: FrameLayout
    private var sideways = false
    /** Sideways Station: the page list on the left, the page on the right (Android's large-screen settings). */
    private val twoPane get() = station && sideways
    private var titleView: TextView? = null
    private var backView: View? = null
    /** The page's and the page list's scrollers, as last drawn: a redraw keeps their places. */
    private var pageScroll: ScrollView? = null
    private var listScroll: ScrollView? = null
    /** Acquire GPS row: the grid found, and its Set button. */
    private var gpsSummary: TextView? = null
    private var gpsSet: View? = null
    private val dp get() = resources.displayMetrics.density
    private val font by lazy { ResourcesCompat.getFont(requireContext(), R.font.selawik) }
    /** Tables, lists and About keep the PC's Selawik at 14.3 dp. */
    private val textPx get() = resources.getDimension(R.dimen.js8_text)
    private fun color(id: Int) = ContextCompat.getColor(requireContext(), id)
    /** The app theme's Material colours (light and dark). */
    private fun attr(a: Int) = MaterialColors.getColor(requireContext(), a, Color.GRAY)
    private val onSurface get() = attr(com.google.android.material.R.attr.colorOnSurface)
    private val onSurfaceVariant get() = attr(com.google.android.material.R.attr.colorOnSurfaceVariant)
    private val accent get() = attr(androidx.appcompat.R.attr.colorPrimary)
    private val errorColor get() = attr(androidx.appcompat.R.attr.colorError)
    private fun px(v: Int) = (v * dp).toInt()

    private val embedded get() = arguments?.getBoolean(ARG_EMBEDDED) == true ||
        parentFragment != null && parentFragment !is androidx.navigation.fragment.NavHostFragment

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        vm = ViewModelProvider(requireActivity())[if (station) "station" else "settings", Js8SettingsViewModel::class.java]
        draft = Js8SettingsSchema.Draft(PreferenceManager.getDefaultSharedPreferences(requireContext()))
        draft.onChange = { scheduleCommit() }
        sideways = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        // Opened on one page (Help > About); after rotating, the page shown stays as it was
        if (savedInstanceState == null) arguments?.getString(ARG_PAGE)?.let { vm.page = it }

        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(attr(com.google.android.material.R.attr.colorSurface))
        }
        root.addView(topBar(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        content = FrameLayout(requireContext())
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        render()
        return root
    }

    /** Android top bar: ← and the title; the Settings window's gear opens Station (Rob, 2026-10-19). */
    private fun topBar(): View = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = px(if (sideways) 48 else 56)
        setPadding(px(4), 0, px(4), 0)
        addView(iconButton(R.drawable.js8_pref_back, "Back") { goBack() }.also { backView = it })
        addView(TextView(requireContext()).apply {
            titleView = this
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTextColor(onSurface)
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(px(12), 0, px(12), 0)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (!station) addView(iconButton(R.drawable.js8_gear, "Station") { findNavController().navigate(R.id.navigation_js8_station) })
    }

    private fun iconButton(icon: Int, description: String, onClick: () -> Unit) = ImageView(requireContext()).apply {
        setImageResource(icon)
        imageTintList = ColorStateList.valueOf(onSurface)
        contentDescription = description
        setPadding(px(12), px(12), px(12), px(12))
        setBackgroundResource(themeDrawable(android.R.attr.selectableItemBackgroundBorderless))
        layoutParams = LinearLayout.LayoutParams(px(48), px(48))
        setOnClickListener { onClick() }
    }

    private fun themeDrawable(a: Int): Int {
        val tv = TypedValue()
        requireContext().theme.resolveAttribute(a, tv, true)
        return tv.resourceId
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        vm.gpsText.observe(viewLifecycleOwner) { t ->
            gpsSummary?.text = t.ifEmpty { "Find this phone's grid" }
            gpsSet?.isEnabled = vm.gpsGrid != null
        }
        // No leaving Station with the back button until callsign and grid are saved (or SWL)
        if (station && !embedded) {
            requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner,
                object : androidx.activity.OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() = goBack()
                })
        }
    }

    /** ←: a Station page returns to the list of pages (upright); otherwise the window closes. */
    private fun goBack() {
        when {
            station && !twoPane && vm.page != null -> { vm.page = null; render() }
            embedded -> Unit
            station && stationIncomplete() -> showStationRequired()
            else -> findNavController().popBackStack()
        }
    }

    /** Saved values: no callsign or grid, and SWL (Listening only) not checked. */
    private fun stationIncomplete(): Boolean {
        val p = draft.prefs
        if (p.getBoolean(Js8SettingsSchema.SWL_KEY, false)) return false
        return p.getString("callsign", "").isNullOrBlank() || p.getString("grid", "").isNullOrBlank()
    }

    private fun showStationRequired() {
        MaterialAlertDialogBuilder(requireContext()).setMessage(Js8StationRequiredDialog.MESSAGE)
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
            render(keepScroll = true)
        }
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(rigTestReceiver, IntentFilter(JS8EngineService.ACTION_RIG_TEST))
        holdRadio(station && vm.page == "Radio")
    }

    override fun onPause() {
        commit()
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(rigTestReceiver)
        if (vm.testPtt) { vm.testPtt = false; sendTestPtt(false) } // never leave the radio keyed
        holdRadio(false) // leaving the Radio page: one reconnect for its changes
        super.onPause()
    }

    // ---- Saving -------------------------------------------------------------------------------

    private fun allFields(items: List<Item> = sections.flatMap { it.items }): List<Field> = items.flatMap {
        when (it) {
            is Field -> listOf(it)
            is Group -> allFields(it.items)
            is RadioItem -> listOf(it.field)
            else -> emptyList()
        }
    }

    private var commitPosted = false

    /** A change was made: saved right after (one save for the keys a popup sets together, e.g. Font). */
    private fun scheduleCommit() {
        if (commitPosted || !::content.isInitialized) return
        commitPosted = true
        content.post { commitPosted = false; commit() }
    }

    /** Writes the changed values at once (Android: no OK / Save, Rob 2026-11-24). */
    private fun commit() {
        if (draft.values.isEmpty()) return
        val ctx = context ?: return
        val prefs = draft.prefs
        val changed = draft.values.toMap()
        draft.values.clear()
        vm.values.clear()
        val fields = allFields().associateBy { it.key }
        val e = prefs.edit()
        // PC defaults for settings never stored, so the engine has them (OK wrote these before)
        for (f in fields.values) {
            if (f.key !in changed && !prefs.contains(f.key) && f.kind != Kind.AUDIO_INPUT) f.save(e, f.load(prefs), ctx)
        }
        for ((k, v) in changed) {
            val f = fields[k]
            when {
                f?.kind == Kind.AUDIO_INPUT -> AudioDevices.list(ctx).firstOrNull { it.id.toString() == v }?.let { device ->
                    val running = ViewModelProvider(requireActivity())[MonitorViewModel::class.java].isRunning.value == true
                    AudioDevices.select(ctx, device, running)
                }
                f != null -> f.save(e, v, ctx)
                // Tables and lists (frequencies, schedule, saved messages, notifications, UI colours)
                else -> e.putString(k, v)
            }
        }
        e.apply()
        // Pages' Station screen: a new sideways layout redraws the screen now (the window: when it is left)
        if (embedded && Js8MainFragment.PREF_LANDSCAPE_LAYOUT in changed) requireActivity().recreate()
    }

    /** Station > Radio on screen: its changes are saved, the radio reconnects once when the page is left. */
    private fun holdRadio(on: Boolean) {
        val p = draft.prefs
        if (p.getBoolean(JS8EngineService.PREF_RADIO_HOLD, false) != on) p.edit().putBoolean(JS8EngineService.PREF_RADIO_HOLD, on).apply()
    }

    /**
     * The PC's OK checks (Configuration.cpp validate), made as the value is typed. Null = the value is fine.
     * With SWL (Listening only) callsign and grid may be empty; anything typed is still checked.
     */
    private fun problem(key: String, raw: String): String? {
        val v = raw.trim().uppercase(Locale.US)
        val swl = swlChecked()
        fun split() = if (v.isEmpty()) emptyList() else v.split(",").map { it.trim() }
        return when (key) {
            "js8cfg_eot" -> if (v.isEmpty()) "Please enter an end of transmission character" else null
            "callsign" -> if ((v.isNotEmpty() || !swl) && (!Js8Callsign.isValid(v) || v.startsWith("@")))
                "The callsign format you provided is not supported" else null
            "grid" -> if ((v.isNotEmpty() || !swl) && !Js8Grid.isValid(raw.trim())) "The grid you provided is not valid" else null
            // PC splitGroups: each group in turn, a group that cannot be joined, then not a valid group
            "my_groups" -> split().firstNotNullOfOrNull { g ->
                when {
                    g == "@APRSIS" || g == "@JS8NET" -> "$g is a group that cannot be joined"
                    !Js8Callsign.isCompound(g) -> "$g is not a valid group"
                    else -> null
                }
            }
            "js8cfg_auto_whitelist" -> split().firstOrNull { !Js8Callsign.isValid(it) }?.let { "$it is not a valid callsign to whitelist" }
            "js8cfg_cq_message" -> {
                val call = draft.prefs.getString("callsign", "").orEmpty().trim().uppercase(Locale.US)
                if (v.isNotEmpty() && !(v.startsWith("CQ") || v.contains(call)))
                    "The CQ message format is invalid. It must either start with \"CQ\" or contain your callsign." else null
            }
            else -> null
        }
    }

    // ---- Layout -------------------------------------------------------------------------------

    /** keepScroll: a change redrew the page, so the page stays where it was. */
    private fun render(keepScroll: Boolean = false) {
        if (vm.testPtt && vm.page != "Radio") { vm.testPtt = false; sendTestPtt(false) } // left the Radio page
        val pages = if (station) stationPages() else emptyList()
        if (station && vm.page != null && vm.page !in pages) vm.page = null
        if (twoPane && vm.page == null) vm.page = GENERAL_PAGE
        holdRadio(isResumed && station && vm.page == "Radio")
        val pageY = if (keepScroll) pageScroll?.scrollY ?: 0 else 0
        val listY = listScroll?.scrollY ?: 0
        content.removeAllViews()
        pageScroll = null; listScroll = null; gpsSummary = null; gpsSet = null
        val page = vm.page
        titleView?.text = when {
            !station -> "Settings"
            twoPane || page == null -> "Station Settings"
            else -> page
        }
        // Pages' Station screen: nowhere to go back to except from a page to the list
        backView?.visibility = if (embedded && !(station && !twoPane && page != null)) View.GONE else View.VISIBLE
        when {
            !station -> content.addView(scroller(page = true).apply { addView(settingsWindow()) })
            twoPane -> content.addView(LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                val width = (resources.displayMetrics.widthPixels * 0.34f).toInt().coerceIn(px(240), px(340))
                addView(scroller(page = false).apply { addView(pageList(pages)) }, LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT))
                addView(scroller(page = true).apply { addView(pageBody(page ?: GENERAL_PAGE)) },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            })
            page == null -> content.addView(scroller(page = false).apply { addView(pageList(pages)) })
            else -> content.addView(scroller(page = true).apply { addView(pageBody(page)) })
        }
        if (pageY > 0 || listY > 0) content.post { pageScroll?.scrollTo(0, pageY); listScroll?.scrollTo(0, listY) }
    }

    private fun scroller(page: Boolean) = ScrollView(requireContext()).apply {
        isFillViewport = true
        if (page) pageScroll = this else listScroll = this
    }

    /** Station's pages (the PC's tab names) with About last; the Layout page only sideways (Rob, 2026-10-21). */
    private fun stationPages() = sections.map { it.tab }.distinct().filter { sideways || it != Js8SettingsSchema.LAYOUT_PAGE } + ABOUT_PAGE

    /** Station's list of pages: icon, name, and what is set there, as Android's Settings list. */
    private fun pageList(pages: List<String>) = column().apply {
        setPadding(0, px(4), 0, px(16))
        pages.forEach { addView(pageRow(it, selected = twoPane && it == vm.page)) }
    }

    private fun pageRow(name: String, selected: Boolean): View = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = px(72)
        setPadding(px(16), px(12), px(16), px(12))
        if (selected) background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = px(28).toFloat()
            setColor(attr(com.google.android.material.R.attr.colorSecondaryContainer))
        } else setBackgroundResource(themeDrawable(android.R.attr.selectableItemBackground))
        addView(ImageView(requireContext()).apply {
            setImageResource(PAGE_ICONS[name] ?: R.drawable.js8_pref_general)
            imageTintList = ColorStateList.valueOf(onSurfaceVariant)
        }, LinearLayout.LayoutParams(px(24), px(24)).apply { marginEnd = px(24) })
        addView(texts(name, pageSummary(name)), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        setOnClickListener {
            vm.page = name
            // PC: 5 s on the status bar while the Audio page reads the device lists
            if (name == "Audio") toast("Enumerating audio devices")
            render()
        }
        if (twoPane) layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { marginStart = px(8); marginEnd = px(8) }
    }

    /** The grey line under a page's name: what is set there. */
    private fun pageSummary(name: String): String {
        val p = draft.prefs
        fun valueOf(key: String) = allFields().firstOrNull { it.key == key }?.let { summaryOf(it).first }.orEmpty()
        fun count(key: String, default: String?) = (p.getString(key, default) ?: "").split('\n').count { it.isNotBlank() }
        return when (name) {
            "General" -> listOf(p.getString("callsign", ""), p.getString("grid", "")).filterNot { it.isNullOrBlank() }
                .joinToString("  ·  ").ifEmpty { "Callsign and grid not set" }
            "Radio" -> valueOf(Js8SettingsSchema.RIG_KEY)
            "Audio" -> AudioDevices.selectedName(requireContext())
            Js8SettingsSchema.LAYOUT_PAGE -> "Sideways layout: " + valueOf(Js8MainFragment.PREF_LANDSCAPE_LAYOUT)
            "Reporting" -> "Spotting, API, loggers"
            "APRS Beacon" -> valueOf(com.js8call.example.service.AprsBeacon.PREF_BEACON)
            "Frequencies" -> "${Js8SettingsSchema.frequencyItems(requireContext(), p.getString(Js8SettingsSchema.FREQUENCIES_KEY, null)).size} working frequencies"
            "Saved Messages" -> count(Js8SettingsSchema.SAVED_MESSAGES_KEY, Js8SettingsSchema.SAVED_MESSAGES_DEFAULT).let { if (it == 1) "1 message" else "$it messages" }
            "Notifications" -> if (p.getBoolean("js8cfg_notifications", false)) "On" else "Off"
            "UI" -> "Colours and fonts"
            "Diagnostics" -> "System, device and app details"
            "Phone" -> "Audio boost, screen, decodes, time sync"
            ABOUT_PAGE -> "Version ${BuildConfig.VERSION_NAME}"
            else -> ""
        }
    }

    /** One Station page: its headings and rows, or About. */
    private fun pageBody(page: String): View {
        val col = column().apply { setPadding(0, 0, 0, px(24)) }
        if (page == ABOUT_PAGE) {
            col.setPadding(px(16), px(8), px(16), px(16))
            col.gravity = Gravity.CENTER_VERTICAL // centred top to bottom when it fits (Rob, 2026-10-31)
            renderAbout(col)
            return col
        }
        for (s in sections.filter { it.tab == page }) {
            if (!hasVisible(s.items)) continue // e.g. CAT Control while Rig is None
            // A sub-page's name heads it unless its first box has its own title (General: Station Details)
            val first = s.items.firstOrNull()
            if (s.subTab != null && !(first is Group && first.title != null)) col.addView(header(s.subTab, col))
            addItems(col, s.items)
        }
        return col
    }

    /** Settings window: one page, each section under its heading, Frequency first (Rob, 2026-11-06). */
    private fun settingsWindow(): View {
        val col = column().apply { setPadding(0, 0, 0, px(24)) }
        for (s in sections.sortedBy { it.tab != "Frequency" }) {
            col.addView(header(s.tab, col))
            addItems(col, s.items)
        }
        return col
    }

    private fun addItems(col: LinearLayout, items: List<Item>) {
        for (item in items) when (item) {
            is Group -> if (item.visible(draft) && hasVisible(item.items)) {
                item.title?.let { col.addView(header(it, col)) }
                addItems(col, item.items)
            }
            is Field -> if (item.visible(draft)) col.addView(fieldRow(item))
            is RadioItem -> col.addView(radioRow(item))
            is ButtonRow -> item.buttons.forEach { (lab, action) -> col.addView(actionRow(lab, action)) }
            is Note -> col.addView(TextView(requireContext()).apply {
                text = item.text
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(onSurfaceVariant)
                setPadding(px(16), px(8), px(16), px(8))
            })
            is Special -> col.addView(when (item.id) {
                "grid_gps" -> gpsRow()
                "power_slider" -> powerSlider()
                // Tables and lists keep their own look (Rob, 2026-11-24), inside the page's margins
                else -> FrameLayout(requireContext()).apply {
                    setPadding(px(16), px(8), px(16), px(8))
                    addView(special(item.id))
                }
            })
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

    // ---- Rows ---------------------------------------------------------------------------------

    /** Android category heading: small text in the accent colour. */
    private fun header(text: String, col: LinearLayout) = TextView(requireContext()).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(accent)
        setPadding(px(16), px(if (col.childCount == 0) 16 else 24), px(16), px(8))
    }

    /** Name over its value, Android's two-line row text. */
    private fun texts(title: String, summary: String?, warn: Boolean = false) = column().apply {
        addView(TextView(requireContext()).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(onSurface)
        })
        if (!summary.isNullOrEmpty()) addView(TextView(requireContext()).apply {
            text = summary
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(if (warn) errorColor else onSurfaceVariant)
            maxLines = 3
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, px(2), 0, 0)
        })
    }

    /** A tappable row: name, value under it; greyed (Android's 38 %) when it can't be used. */
    private fun row(title: String, summary: String?, enabled: Boolean = true, warn: Boolean = false, end: View? = null,
                    onClick: (View) -> Unit): LinearLayout = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = px(56)
        setPadding(px(16), px(12), px(16), px(12))
        setBackgroundResource(themeDrawable(android.R.attr.selectableItemBackground))
        addView(texts(title, summary, warn), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        end?.let { addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = px(16) }) }
        if (enabled) setOnClickListener { onClick(it) } else { alpha = 0.38f; isClickable = false }
    }

    private fun title(f: Field) = f.label.trimEnd(':', ' ')

    private fun fieldRow(f: Field): View {
        val on = f.enabled && f.active(draft)
        if (f.kind == Kind.CHECK) return switchRow(f, on)
        val (summary, warn) = summaryOf(f)
        return row(title(f), summary, on, warn) {
            when (f.kind) {
                Kind.TEXT -> textDialog(f)
                Kind.SPIN -> numberDialog(f)
                else -> if (f.key == Js8SettingsSchema.RIG_KEY) searchableChoice(f) else choiceDialog(f)
            }
        }
    }

    /** The value shown under a setting's name, and whether it is shown as a problem (red). */
    private fun summaryOf(f: Field): Pair<String, Boolean> = when (f.kind) {
        Kind.TEXT -> {
            val v = draft.get(f)
            val optional = swlChecked() && (f.key == "callsign" || f.key == "grid")
            when {
                v.isNotBlank() -> v to (problem(f.key, v) != null)
                f.required && !optional -> "Required" to true
                else -> f.hint.ifEmpty { "Not set" } to false
            }
        }
        Kind.SPIN -> spinText(f, draft.get(f)) to false
        else -> {
            val current = if (f.kind == Kind.AUDIO_INPUT) draft.values[f.key] ?: AudioDevices.selected(requireContext()).id.toString() else draft.get(f)
            val name = choices(f).firstOrNull { it.second == current }?.first ?: current
            name.ifBlank { "Not set" } to false
        }
    }

    /** On / off: Android's switch at the right; the whole row toggles it. */
    private fun switchRow(f: Field, on: Boolean): View {
        val sw = MaterialSwitch(requireContext()).apply {
            isChecked = draft.bool(f)
            isClickable = false
            isFocusable = false
        }
        sw.setOnCheckedChangeListener { _, c ->
            draft.set(f, c.toString())
            // SWL: callsign and grid may be empty; AUTO and HB grey or un-grey ACK
            if (f.key in Js8SettingsSchema.ACTIVE_TRIGGERS || f.key == Js8SettingsSchema.SWL_KEY) content.post { render(keepScroll = true) }
        }
        return row(title(f), null, on, end = sw) { sw.toggle() }
    }

    /** Radio buttons (PTT Method, Speed, ...): a row with the choice under the name; a list to pick from. */
    private fun radioRow(item: RadioItem): View {
        val cur = draft.get(item.field)
        val greyed = item.disabled + item.greyed(draft)
        val name = item.options.firstOrNull { it.second == cur }?.first ?: cur
        // PC OK: "Invalid PTT method" when the chosen one is greyed (a new rig without CAT PTT)
        val invalid = cur in greyed && item.field.key == "js8cfg_ptt_method"
        val usable = item.options.any { it.second !in greyed }
        return row(item.title, if (invalid) "$name: Invalid PTT method" else name, usable, invalid) {
            choose(item.title, item.options, cur, greyed) { v ->
                draft.set(item.field, v)
                render(keepScroll = true)
            }
        }
    }

    /** Buttons become rows; Test CAT and Test PTT show their result under the name. */
    private fun actionRow(label: String, action: String): View {
        var summary: String? = null
        var warn = false
        var enabled = true
        if (action == "test_cat" || action == "test_ptt") {
            enabled = Js8SettingsSchema.rigTestEnabled(draft, action, vm.testCat)
            if (action == "test_cat") vm.testCat?.let { ok -> summary = if (ok) "Rig connected" else "Rig did not connect"; warn = !ok }
            else if (vm.testPtt) { summary = "Transmitting. Tap to stop."; warn = true }
        }
        return row(label, summary, enabled, warn) { v -> onAction(action, v) }
    }

    // ---- Popups -------------------------------------------------------------------------------

    /** Android's single-choice list; greyed entries show and can't be picked (PC: a disabled combo box item). */
    private fun choose(title: String, list: List<Pair<String, String>>, current: String, greyed: Set<String>, onPick: (String) -> Unit) {
        val idx = list.indexOfFirst { it.second == current }
        val adapter = object : android.widget.ArrayAdapter<String>(requireContext(),
            android.R.layout.simple_list_item_single_choice, list.map { it.first.ifEmpty { " " } }) {
            override fun areAllItemsEnabled() = greyed.isEmpty()
            override fun isEnabled(position: Int) = list[position].second !in greyed
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                super.getView(position, convertView, parent).also { v ->
                    val on = list[position].second !in greyed
                    v.isEnabled = on; v.alpha = if (on) 1f else 0.38f
                }
        }
        val dialog = MaterialAlertDialogBuilder(requireContext()).setTitle(title)
            .setSingleChoiceItems(adapter, idx) { d, which ->
                d.dismiss()
                if (list[which].second != current) onPick(list[which].second)
            }
            .setNegativeButton(android.R.string.cancel, null).show()
        // The chosen entry in the middle of the list; a short list starts at its top (sideways the list opened
        // scrolled down, its first entry cut off)
        if (idx >= 0) dialog.listView?.let { lv ->
            lv.post { lv.setSelectionFromTop(idx, ((lv.height - (lv.getChildAt(0)?.height ?: 0)) / 2).coerceAtLeast(0)) }
        }
    }

    private fun choiceDialog(f: Field) {
        val current = if (f.kind == Kind.AUDIO_INPUT) draft.values[f.key] ?: AudioDevices.selected(requireContext()).id.toString() else draft.get(f)
        choose(title(f), choices(f), current, f.greyedChoices(requireContext(), draft)) { pick(f, it) }
    }

    /** APRS beacon waiting for location permission before it is saved. */
    private var pendingBeacon: String? = null

    private fun pick(f: Field, v: String) {
        // APRS beacon on: the GPS needs precise location first; the choice is saved once it is allowed
        if (f.key == com.js8call.example.service.AprsBeacon.PREF_BEACON && v != "0" &&
            !com.js8call.example.service.AprsBeacon.hasPermission(requireContext())) {
            pendingBeacon = v
            beaconPermission.launch(locationPermissions)
            return
        }
        draft.set(f, v)
        render(keepScroll = true) // some choices show / hide other settings (Rig)
    }

    /** Android text box with the setting's name as title; a problem shows under the box while typing. */
    private fun textDialog(f: Field) {
        val ctx = requireContext()
        val layout = TextInputLayout(ctx).apply { boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE }
        val edit = TextInputEditText(layout.context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
                (if (f.upper) InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS else 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            var list = arrayOf<InputFilter>()
            if (f.maxLength > 0) list += InputFilter.LengthFilter(f.maxLength)
            if (f.key in messageBoxes) list += messageAlphabet
            filters = list
            setText(draft.get(f))
            setSelection(text?.length ?: 0)
        }
        layout.addView(edit, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        if (f.hint.isNotEmpty()) layout.placeholderText = f.hint
        fun value() = edit.text.toString().let { if (f.upper) it.uppercase(Locale.US) else it }
        editorDialog(title(f), layout, edit, check = {
            val v = value()
            val p = problem(f.key, v)
            layout.error = if (p != null && v.isBlank()) "Required" else p
            p == null
        }) { draft.set(f, value()) }
    }

    /** Number box (the PC's spin boxes): the allowed range under the box. */
    private fun numberDialog(f: Field) {
        val ctx = requireContext()
        fun num(d: Double) = if (f.decimals == 0) d.toLong().toString() else String.format(Locale.US, "%.${f.decimals}f", d)
        val layout = TextInputLayout(ctx).apply {
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            suffixText = f.suffix.trim().ifEmpty { null }
            helperText = "${num(f.min)} to ${num(f.max)}" + (f.special?.let { " (${num(f.min)} = $it)" } ?: "")
        }
        val edit = TextInputEditText(layout.context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or
                (if (f.decimals > 0) InputType.TYPE_NUMBER_FLAG_DECIMAL else 0) or
                (if (f.min < 0) InputType.TYPE_NUMBER_FLAG_SIGNED else 0)
            setText(draft.get(f))
            setSelection(text?.length ?: 0)
        }
        layout.addView(edit, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        fun value() = edit.text.toString().trim().toDoubleOrNull()
        editorDialog(title(f), layout, edit, check = {
            val n = value()
            val ok = n != null && n >= f.min && n <= f.max
            layout.error = if (ok || edit.text.isNullOrBlank()) null else "${num(f.min)} to ${num(f.max)}"
            ok
        }) { draft.set(f, num(value()!!)) }
    }

    /** OK is greyed while [check] fails; OK saves, Cancel leaves the setting as it was. */
    private fun editorDialog(title: String, layout: TextInputLayout, edit: EditText, check: () -> Boolean, save: () -> Unit) {
        val box = FrameLayout(requireContext()).apply { setPadding(px(24), px(16), px(24), 0); addView(layout) }
        val dialog = MaterialAlertDialogBuilder(requireContext()).setTitle(title).setView(box)
            .setPositiveButton(android.R.string.ok, null).setNegativeButton(android.R.string.cancel, null).create()
        dialog.setOnShowListener {
            val ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            ok.isEnabled = check()
            ok.setOnClickListener {
                if (!check()) return@setOnClickListener
                save()
                dialog.dismiss()
                render(keepScroll = true)
            }
            edit.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { ok.isEnabled = check() }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
            edit.requestFocus()
        }
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
    }

    /** Station > About: version, features over JS8Android, source, licence, credits (Rob, 2026-10-19). */
    private fun renderAbout(col: LinearLayout) {
        // Centred (Rob, 2026-10-31)
        fun line(text: String, size: Float = 1f, bold: Boolean = false, top: Int = 0, parent: LinearLayout = col) =
            parent.addView(label(text, size, bold).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, px(top), 0, px(3))
                setTextColor(onSurface)
                setLinkTextColor(accent)
                android.text.util.Linkify.addLinks(this, android.text.util.Linkify.WEB_URLS) // tap opens the browser
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        line("Easy JS8", size = 1.2f, bold = true, top = 10)
        line("by Carson VA3OSO")
        line("Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
        line("Built ${BuildConfig.BUILD_DATE}")
        line("Easy JS8 is a fork of JS8Android.", top = 12)
        line("Features", size = 1.1f, bold = true, top = 16)
        val features = listOf("Traditional style", "Upright and sideways layouts", "SWL mode", "JS8 Slow decoding",
            "APRS forms", "FT8 decodes", "APRS-IS relay", "Message mailbox", "HB and CQ timers",
            "Log QSO and ADIF export", "API and logging links", "Waterfall Controls", "Station map",
            "APRS beacon with GPS and SmartBeaconing")
        // Two columns, half each (Rob, 2026-10-31: six each; 2026-11-18: seven with the map and beacon)
        val half = (features.size + 1) / 2
        val first = column()
        val second = column()
        col.addView(LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(first, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(second, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        })
        features.forEachIndexed { i, f -> line("  •  $f", parent = if (i < half) first else second) }
        line("Source: https://github.com/RobBobbins/Easy-JS8", top = 16)
        line("Licence: GPLv3. No warranty.")
        line("Credits", size = 1.1f, bold = true, top = 16)
        line("WSJT-X: Joe Taylor K1JT\nand the WSJT Development Group")
        line("JS8Call: Jordan Sherer KN4CRD", top = 6)
        line("JS8Call-improved team: AC9KH, W6BAZ, KJ4CTD, K0OG, DJ3EI, K4RWR, KN4CRD", top = 6)
        line("JS8Android: https://github.com/JS8Call-improved/Android-port", top = 6)
        line("ft8_lib (FT8 decodes): Kārlis Goba, MIT licence", top = 6)
        line("js8core-plus (JS8 decoder): Kelly-Shane Fuller, Carson VA3OSO, MIT licence", top = 6)
        line("pocketfft: Max-Planck-Society, BSD licence", top = 6)
        line("Fonts: Selawik, Cascadia Mono (SIL Open Font Licence)", top = 6)
    }

    // ---- Widgets used by the tables and lists --------------------------------------------------

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

    /** PC message_alphabet "[^\x00-\x1F]*" (cpp 207): typed or pasted text with a control character is refused. */
    private val messageAlphabet = InputFilter { s, start, end, _, _, _ ->
        if ((start until end).any { s[it] < ' ' }) "" else null
    }

    /** The boxes the PC gives message_alphabet (cpp 1491-1504); its HB Message box is hidden. */
    private val messageBoxes = setOf("my_groups", "js8cfg_cq_message", "js8cfg_reply_message", "my_info", "my_status")

    private fun spinText(f: Field, v: String): String {
        val n = v.toDoubleOrNull() ?: 0.0
        if (f.special != null && n == f.min) return f.special
        val num = if (f.decimals == 0) n.toLong().toString() else String.format(Locale.US, "%.${f.decimals}f", n)
        return (f.prefix + num + f.suffix).trim()
    }

    private fun choices(f: Field): List<Pair<String, String>> = if (f.kind == Kind.AUDIO_INPUT) {
        AudioDevices.list(requireContext()).map { it.name to it.id.toString() }
    } else f.choices?.invoke(requireContext(), draft) ?: emptyList()

    /**
     * Rig list with a search box (Rob, 2026-10-21): typing keeps the rigs whose name contains it,
     * ignoring case, spaces and dashes ("ic7300" finds "Icom IC-7300"); tap one to pick it.
     */
    private fun searchableChoice(f: Field) {
        val ctx = requireContext()
        val list = choices(f)
        val current = draft.get(f)
        fun key(t: String) = t.lowercase(Locale.US).filter { it.isLetterOrDigit() }
        var shown = list
        val adapter = android.widget.ArrayAdapter(ctx, android.R.layout.simple_list_item_single_choice, shown.map { it.first.ifEmpty { " " } }.toMutableList())
        val idx = list.indexOfFirst { it.second == current }
        val listView = android.widget.ListView(ctx).apply {
            choiceMode = android.widget.ListView.CHOICE_MODE_SINGLE
            this.adapter = adapter
            if (idx >= 0) { setItemChecked(idx, true); setSelection(idx) }
        }
        val search = TextInputEditText(ctx).apply {
            hint = "Search (e.g. 7300, yaesu)"
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(16), px(8), px(16), 0)
            addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(listView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        val dialog = MaterialAlertDialogBuilder(ctx).setTitle(title(f)).setView(box)
            .setNegativeButton(android.R.string.cancel, null).create()
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = key(s?.toString().orEmpty())
                shown = if (q.isEmpty()) list else list.filter { key(it.first).contains(q) }
                adapter.clear()
                adapter.addAll(shown.map { it.first.ifEmpty { " " } })
                listView.clearChoices()
                val sel = shown.indexOfFirst { it.second == current }
                if (sel >= 0) listView.setItemChecked(sel, true)
            }
        })
        listView.setOnItemClickListener { _, _, pos, _ ->
            dialog.dismiss()
            if (shown[pos].second != current) pick(f, shown[pos].second)
        }
        dialog.show()
        // Tall enough for the list under the search box
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, (resources.displayMetrics.heightPixels * 0.8f).toInt())
    }

    // ---- Actions ------------------------------------------------------------------------------

    // Android 12+ ignores a request for fine location alone; ask for both
    private val locationPermissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    private val locationPermission = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { granted -> if (granted.values.any { it }) acquireGps() else toast("Location permission is needed for Acquire GPS") }

    private val beaconPermission = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val v = pendingBeacon
        pendingBeacon = null
        if (granted[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            val f = allFields().firstOrNull { it.key == com.js8call.example.service.AprsBeacon.PREF_BEACON }
            if (f != null && v != null) { draft.set(f, v); render(keepScroll = true) }
        } else toast("Precise location permission is needed for the APRS beacon")
    }

    private fun acquireGps() {
        val ctx = requireContext()
        if (locationPermissions.none { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }) {
            locationPermission.launch(locationPermissions)
            return
        }
        if (!vm.acquireGps()) toast("Location is off. Turn it on in the phone's settings.")
    }

    private fun activityVm() = ViewModelProvider(requireActivity())[Js8ActivityViewModel::class.java]

    private fun onAction(id: String, anchor: View) {
        when (id) {
            // Settings > View (was Menu > View): at once
            "clear_band" -> { activityVm().activity.clearBand(); activityVm().changed(); toast("Band Activity cleared") }
            "clear_call" -> { activityVm().activity.clearCalls(); activityVm().changed(); toast("Call Activity cleared") }
            "clear_rx" -> { activityVm().activity.clearRx(); activityVm().changed(); toast("RX Activity cleared") }
            "clear_all" -> {
                activityVm().activity.clearBand(); activityVm().activity.clearCalls(); activityVm().activity.clearRx()
                activityVm().changed(); toast("All Activity cleared")
            }
            "reset_window_sizes" -> { Js8LineDividers.reset(draft.prefs); Js8PcSplitters.clear(draft.prefs); toast("Window sizes reset") }
            "configurations" -> Js8Configurations.showMenu(this,
                android.widget.PopupMenu(android.view.ContextThemeWrapper(requireContext(), R.style.ThemeOverlay_Js8PopupMenu), anchor))
            "copyright" -> MaterialAlertDialogBuilder(requireContext()).setMessage(Js8MainFragment.COPYRIGHT_NOTICE)
                .setPositiveButton(android.R.string.ok, null).show()
            "check_updates" -> Js8Updates.check(requireActivity(), true)
            "user_guide" -> startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(Js8MainFragment.USER_GUIDE_URL)))
            "exit" -> { // PC File > Exit
                requireContext().stopService(Intent(requireContext(), JS8EngineService::class.java))
                requireActivity().finishAndRemoveTask()
            }
            "test_cat" -> {
                // The rig opens from saved settings: anything still waiting to be saved goes first
                commit()
                vm.testCat = null
                render(keepScroll = true)
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
            "diag_run" -> { vm.diagnostics = diagnostics(); render(keepScroll = true) }
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

    /** Under My Maidenhead Grid Locator: Acquire GPS (tap the row), the grid found under it, Set copies it in. */
    private fun gpsRow(): View {
        val set = com.google.android.material.button.MaterialButton(requireContext(), null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Set"
            isEnabled = vm.gpsGrid != null
            setOnClickListener {
                val grid = vm.gpsGrid ?: return@setOnClickListener
                allFields().firstOrNull { it.key == "grid" }?.let { draft.set(it, grid) }
                render(keepScroll = true)
            }
        }
        gpsSet = set
        val row = row("Acquire GPS", vm.gpsText.value.orEmpty().ifEmpty { "Find this phone's grid" }, end = set) { acquireGps() }
        gpsSummary = (row.getChildAt(0) as LinearLayout).getChildAt(1) as TextView
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
        "aprs_status" -> aprsStatusWindow()
        "aprs_gps" -> aprsGpsLine()
        else -> View(requireContext())
    }

    /**
     * APRS Beacon GPS status (Rob 2026-11-17), updated every second while on screen. One line, two when it does
     * not fit (Rob 2026-11-18: the fix line was cut off upright on the S22 Ultra).
     */
    private fun aprsGpsLine(): View {
        val text = label("").apply {
            id = R.id.js8_aprs_gps
            isSingleLine = false
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, px(4), 0, px(4))
        }
        val tick = object : Runnable {
            override fun run() {
                text.text = com.js8call.example.service.AprsBeacon.gpsLine(text.context)
                text.postDelayed(this, 1000L)
            }
        }
        text.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { text.removeCallbacks(tick); tick.run() }
            override fun onViewDetachedFromWindow(v: View) { text.removeCallbacks(tick) }
        })
        return text
    }

    /**
     * APRS Beacon status window (Rob 2026-11-17): the beacon's events with their UTC time, the last line counting
     * down live; updated every second while it is on screen. Follows the newest line unless scrolled up.
     */
    private fun aprsStatusWindow(): View {
        val text = styledField(TextView(requireContext())).apply {
            id = R.id.js8_aprs_status
            isSingleLine = false
            gravity = Gravity.TOP or Gravity.START
            setPadding(px(8), px(6), px(8), px(6))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx * 0.9f)
            setHorizontallyScrolling(false)
            movementMethod = android.text.method.ScrollingMovementMethod()
            isVerticalScrollBarEnabled = true
            setLines(10)
            // The page scrolls too: a drag inside the window scrolls the window
            setOnTouchListener { v, e ->
                if (e.action == android.view.MotionEvent.ACTION_DOWN) v.parent?.requestDisallowInterceptTouchEvent(true)
                false
            }
        }
        val tick = object : Runnable {
            override fun run() {
                val layout = text.layout
                val bottom = layout == null ||
                    text.scrollY >= layout.height - (text.height - text.totalPaddingTop - text.totalPaddingBottom) - text.lineHeight
                val all = com.js8call.example.service.AprsBeacon.lines() + com.js8call.example.service.AprsBeacon.liveLine(text.context)
                text.text = all.joinToString("\n")
                if (bottom) text.post {
                    val l = text.layout ?: return@post
                    text.scrollTo(0, (l.height - (text.height - text.totalPaddingTop - text.totalPaddingBottom)).coerceAtLeast(0))
                }
                text.postDelayed(this, 1000L)
            }
        }
        text.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { text.removeCallbacks(tick); tick.run() }
            override fun onViewDetachedFromWindow(v: View) { text.removeCallbacks(tick) }
        })
        return text
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
        // PC FrequencyList frequency_column: "7.078 000 MHz (40m)", OOB outside the 30 bands; the
        // Frequency (MHz) column is hidden (cpp 1639-1640, FrequencyList.cpp 356-359)
        fun rows(items: List<Js8SettingsSchema.FrequencyItem>) = items.mapIndexed { i, f ->
            val band = com.js8call.example.util.BandUtils.bandOrNull(f.hz) ?: "OOB"
            Js8TableView.Row("$i", mapOf(
                "region" to f.region, "mode" to f.mode,
                "freq" to "${com.js8call.example.util.BandUtils.prettyMhz(f.hz)} MHz ($band)",
                "desc" to f.description
            ), sortValues = mapOf("freq" to f.hz))
        }
        val list = table("cfg_freq", listOf(
            Js8TableView.Column("region", "IARU Region", 90), Js8TableView.Column("mode", "Mode", 60),
            Js8TableView.Column("freq", "Frequency", 150, numeric = true),
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
        // Android slider row: the slider, its value at the right (Rob, 2026-11-24)
        val value = TextView(requireContext()).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(onSurfaceVariant)
            gravity = Gravity.END
        }
        fun show(a: Int) { value.text = String.format(Locale.US, "%.1f dB", -a / 10.0) }
        show(start)
        val col = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = px(56)
            setPadding(px(4), 0, px(16), 0)
        }
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
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        col.addView(value, LinearLayout.LayoutParams(px(64), ViewGroup.LayoutParams.WRAP_CONTENT))
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
                // PC StationList frequency_column: "7.078 000 MHz" (StationList.cpp 279-281)
                "mhz" to "${com.js8call.example.util.BandUtils.prettyMhz(hz)} MHz",
                "at" to p.getOrElse(1) { "" }, "until" to p.getOrElse(2) { "" }, "desc" to p.getOrElse(3) { "" }
            ), sortValues = mapOf("mhz" to hz))
        }
        val list = table("cfg_sched", listOf(
            Js8TableView.Column("band", "Band", 60), Js8TableView.Column("mhz", "Freq. (MHz)", 110, numeric = true),
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
            filters = arrayOf(messageAlphabet) // PC add_macro_line_edit (cpp 1491)
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
        if (pills) col.addView(switchRow(Field("js8cfg_pills", "Display callsigns and commands as pills", Kind.CHECK, "true"), true))
        var lastHeading: String? = null
        val painters = mutableListOf<() -> Unit>()
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
                if (k.endsWith("_font")) { sample.text = "K1ABC ${v ?: "10"}pt"; return }
                // PC Configuration: each sample is "background: <its group's background>; color: <its group's text>"
                val (bg, fg) = UiColors.SAMPLE_COLORS[k] ?: return
                fun c(n: String) = runCatching { Color.parseColor(draft.values["js8cfg_ui_$n"] ?: draft.prefs.getString("js8cfg_ui_$n", null) ?: UiColors.UI_DEFAULTS.getValue(n)) }
                    .getOrDefault(Color.parseColor(UiColors.UI_DEFAULTS.getValue(n)))
                sample.setBackgroundColor(c(bg))
                sample.setTextColor(c(fg))
            }
            painters += ::paint
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
                    pickColor(lab, draft.values[key] ?: draft.prefs.getString(key, null) ?: UiColors.UI_DEFAULTS[k]) { draft.values[key] = it; painters.forEach { p -> p() } }
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

    private object UiColors {
        /** PC Configuration::read_settings defaults. */
        val UI_DEFAULTS = mapOf(
            "table_bg" to "#FFFFFF", "table_fg" to "#000000", "selected_bg" to "#3498DB",
            "primary_bg" to "#F1C40F", "secondary_bg" to "#FFFF66", "directed_bg" to "#FF6666", "cq_bg" to "#66FF66",
            "rx_bg" to "#FFEAA7", "rx_fg" to "#000000", "tx_fg" to "#FF0000",
            "compose_bg" to "#FFFFFF", "compose_fg" to "#000000",
            "pill_recipient_bg" to "#2980B9", "pill_recipient_fg" to "#FFFFFF",
            "pill_command_bg" to "#E67E22", "pill_command_fg" to "#FFFFFF",
            "pill_group_bg" to "#16A085", "pill_group_fg" to "#FFFFFF",
            "pill_sender_bg" to "#7F8C8D", "pill_sender_fg" to "#FFFFFF"
        )

        /** Each colour button's sample: its background setting and its text setting (PC Configuration.cpp 1833-1878). */
        val SAMPLE_COLORS = mapOf(
            "table_bg" to ("table_bg" to "table_fg"), "table_fg" to ("table_bg" to "table_fg"),
            "selected_bg" to ("selected_bg" to "table_fg"), "primary_bg" to ("primary_bg" to "table_fg"),
            "secondary_bg" to ("secondary_bg" to "table_fg"), "directed_bg" to ("directed_bg" to "table_fg"),
            "cq_bg" to ("cq_bg" to "table_fg"),
            "rx_bg" to ("rx_bg" to "rx_fg"), "rx_fg" to ("rx_bg" to "rx_fg"), "tx_fg" to ("rx_bg" to "tx_fg"),
            "compose_bg" to ("compose_bg" to "compose_fg"), "compose_fg" to ("compose_bg" to "compose_fg")
        ) + listOf("recipient", "command", "group", "sender").flatMap { p ->
            listOf("pill_${p}_bg" to ("pill_${p}_bg" to "pill_${p}_fg"), "pill_${p}_fg" to ("pill_${p}_bg" to "pill_${p}_fg"))
        }
    }
}
