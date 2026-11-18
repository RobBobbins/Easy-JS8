package com.js8call.example.js8ui

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.preference.PreferenceManager
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/**
 * Pages layout Map screen (Easy JS8 only, the PC has no map; Rob, 2026-11-17).
 *
 * Offline outline map (Natural Earth 1:50m, assets/map/world50m.bin, made by tools/make_map_asset.py),
 * a dot for every Call Activity station with a grid, and who-hears-who lines from the PC heard graph
 * (Js8Activity.hearing). Settings: Station > Layout > Map.
 *
 * Pinch to zoom, drag to move, double tap to zoom in on the tapped spot.
 */
class Js8MapView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    /** The activity lists to draw; set by Js8MainFragment. */
    var activity: Js8Activity? = null

    /** Dial frequency for the title; set by Js8MainFragment.renderFrequency. 0 = not known yet. */
    var dialHz: Long = 0
        set(v) { if (field != v) { field = v; invalidate() } }

    companion object {
        const val PREF_LINES = "js8cfg_map_lines"          // arrows / plain / none
        const val PREF_AGING = "js8cfg_map_aging"          // minutes, 0 = same as Call Activity
        const val PREF_CALLS = "js8cfg_map_callsigns"
        const val PREF_STATES = "js8cfg_map_states"
        const val PREF_GRIDS = "js8cfg_map_grids"
        val LINE_CHOICES = listOf("Arrows" to "arrows", "Plain lines" to "plain", "No lines" to "none")
        val AGING_CHOICES = listOf("Same as Call Activity" to "0", "15 minutes" to "15", "30 minutes" to "30",
            "1 hour" to "60", "2 hours" to "120")

        private const val MAX_LAT = 85.0

        /** World map lines, loaded once: per layer, per line, x / y pairs (longitude, Mercator y in degrees). */
        @Volatile private var world: List<List<FloatArray>>? = null

        private fun mercY(lat: Double): Double {
            val r = Math.toRadians(lat.coerceIn(-MAX_LAT, MAX_LAT))
            return Math.toDegrees(ln(tan(PI / 4 + r / 2)))
        }

        private fun load(context: Context): List<List<FloatArray>> {
            world?.let { return it }
            val bytes = context.assets.open("map/world50m.bin").use { it.readBytes() }
            val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            b.position(4) // "EJM1"
            val layers = (0 until b.int).map {
                (0 until b.int).map {
                    val n = b.int
                    FloatArray(n * 2).also { a ->
                        for (i in 0 until n) {
                            a[i * 2] = b.short / 150f
                            a[i * 2 + 1] = mercY(b.short / 150.0).toFloat()
                        }
                    }
                }
            }
            world = layers
            return layers
        }
    }

    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val dp = resources.displayMetrics.density
    private val sp = resources.displayMetrics.scaledDensity

    // View: map point (cx, cy) at the screen centre, scale = pixels per degree
    private var cx = 0.0
    private var cy = 0.0
    private var scale = 0.0
    private var fitted = false
    private var userMoved = false

    private val countryPaint = stroke(Color.rgb(216, 216, 216), 0.9f)
    private val lakePaint = stroke(Color.rgb(95, 168, 255), 0.8f)
    private val statePaint = stroke(Color.rgb(224, 160, 64), 0.7f)
    private val gridPaint = stroke(Color.rgb(255, 64, 64), 1f)
    // Lines: green both ways, yellow one way (Rob, 2026-11-17); the arrow heads the same colour as their line
    private val bothPaint = stroke(Color.rgb(64, 224, 64), 1.2f)
    private val onePaint = stroke(Color.rgb(255, 230, 0), 1.2f)
    private val bothArrow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(64, 224, 64); style = Paint.Style.FILL }
    private val oneArrow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 230, 0); style = Paint.Style.FILL }
    // Station dots white with a black edge (Rob, 2026-11-17; were yellow, the one-way line colour now)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val dotEdge = stroke(Color.BLACK, 1.5f)
    private val myDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 64, 64) }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 12 * sp; setShadowLayer(2 * dp, 0f, 0f, Color.BLACK)
    }
    private val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 13 * sp }
    // Title: white on a dark see-through strip (Rob, 2026-11-17)
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 14 * sp; typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val titleBack = Paint().apply { color = Color.argb(170, 0, 0, 0) }
    private var segs = FloatArray(4096)

    private fun stroke(c: Int, w: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = c; style = Paint.Style.STROKE; strokeWidth = w * resources.displayMetrics.density
    }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key?.startsWith("js8cfg_map_") == true || key == "grid" || key == "callsign" || key == "js8cfg_callsign_aging") invalidate()
    }
    private val tick = object : Runnable {
        override fun run() { invalidate(); postDelayed(this, 30_000) } // stations age out
    }

    init {
        setBackgroundColor(Color.rgb(11, 26, 46))
        isClickable = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        postDelayed(tick, 30_000)
    }

    override fun onDetachedFromWindow() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    // ---- Stations ---------------------------------------------------------------------------

    private class Station(val call: String, val grid: String, val x: Float, val y: Float, val mine: Boolean)

    private fun intPref(key: String, default: Int) = prefs.all[key]?.toString()?.toIntOrNull() ?: default
    private fun boolPref(key: String, default: Boolean) = prefs.all[key]?.toString()?.toBooleanStrictOrNull() ?: default

    private fun stationAt(call: String, grid: String, mine: Boolean): Station? {
        val (lat, lon) = Js8Activity.gridToLatLon(grid) ?: return null
        return Station(call, grid.uppercase(Locale.US), lon.toFloat(), mercY(lat).toFloat(), mine)
    }

    /** My station first, then every Call Activity station with a grid inside both agings. */
    private fun stations(): List<Station> {
        val list = mutableListOf<Station>()
        val myCall = prefs.getString("callsign", "").orEmpty().trim().uppercase(Locale.US)
        val myGrid = prefs.getString("grid", "").orEmpty().trim()
        stationAt(myCall.ifEmpty { "ME" }, myGrid, true)?.let { list += it }
        val a = activity ?: return list
        val callAging = intPref("js8cfg_callsign_aging", 0)
        val mapAging = intPref(PREF_AGING, 0)
        val now = System.currentTimeMillis()
        a.callActivity.values.forEach { d ->
            val ageMin = (now - d.utcMs) / 60_000
            if (callAging > 0 && ageMin >= callAging) return@forEach
            if (mapAging > 0 && ageMin >= mapAging) return@forEach
            if (d.call.isBlank() || d.call.uppercase(Locale.US) == myCall) return@forEach
            stationAt(d.call.uppercase(Locale.US), d.grid, false)?.let { list += it }
        }
        return list
    }

    /** Pairs (hearer, heard) with both ends on the map; true in the map = both directions. */
    private fun links(st: List<Station>): Map<Pair<Station, Station>, Boolean> {
        val byCall = HashMap<String, Station>()
        st.forEach { s -> byCall[s.call] = s; byCall.putIfAbsent(Js8Activity.baseCall(s.call), s) }
        fun find(c: String) = c.uppercase(Locale.US).let { byCall[it] ?: byCall[Js8Activity.baseCall(it)] }
        val heard = mutableSetOf<Pair<Station, Station>>()
        val a = activity
        val me = st.firstOrNull { it.mine }
        // I heard every station in Call Activity, except one heard only through a relay
        if (me != null && a != null) {
            a.callActivity.values.filter { it.through.isEmpty() }.forEach { d -> find(d.call)?.let { if (it !== me) heard += me to it } }
        }
        a?.hearing?.forEach { (from, tos) ->
            val f = find(from) ?: return@forEach
            tos.forEach { t -> find(t)?.let { if (it !== f) heard += f to it } }
        }
        val out = LinkedHashMap<Pair<Station, Station>, Boolean>()
        heard.forEach { p ->
            val back = p.second to p.first
            if (back in out) out[back] = true else out[p] = back in heard
        }
        return out
    }

    // ---- View -------------------------------------------------------------------------------

    private fun sx(x: Float) = ((x - cx) * scale + width / 2.0).toFloat()
    private fun sy(y: Float) = ((cy - y) * scale + height / 2.0).toFloat()

    /** Zoom to every station (at least about 10 degrees across), or the whole world when there are none. */
    private fun fit(st: List<Station>) {
        if (width == 0 || height == 0) return
        if (st.isEmpty()) {
            cx = 0.0; cy = 20.0; scale = width / 360.0; fitted = true; return
        }
        var x0 = st.minOf { it.x }.toDouble(); var x1 = st.maxOf { it.x }.toDouble()
        var y0 = st.minOf { it.y }.toDouble(); var y1 = st.maxOf { it.y }.toDouble()
        val padX = max(5.0, (x1 - x0) * 0.15); val padY = max(4.0, (y1 - y0) * 0.15)
        x0 -= padX; x1 += padX; y0 -= padY; y1 += padY
        // The title's strip is kept clear at the top, so it covers no station
        val top = titleHeight()
        scale = min(width / (x1 - x0), (height - top) / (y1 - y0))
        cx = (x0 + x1) / 2; cy = (y0 + y1) / 2 + top / 2.0 / scale
        fitted = true
    }

    private fun clampView() {
        scale = scale.coerceIn(width / 360.0 * 0.8, width / 0.5)
        cx = cx.coerceIn(-180.0, 180.0)
        cy = cy.coerceIn(mercY(-MAX_LAT), mercY(MAX_LAT))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (!userMoved) fitted = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        val st = stations()
        if (!fitted || !userMoved) fit(st) // follows new stations until the map is moved
        val layers = runCatching { load(context) }.getOrNull()
        if (layers != null) {
            drawLayer(canvas, layers[0], countryPaint)
            drawLayer(canvas, layers[1], lakePaint)
            if (boolPref(PREF_STATES, true)) drawLayer(canvas, layers[2], statePaint)
        }
        if (boolPref(PREF_GRIDS, false)) st.forEach { drawGrid(canvas, it) }
        when (prefs.getString(PREF_LINES, "arrows")) {
            "none" -> Unit
            "plain" -> links(st).forEach { (p, both) ->
                canvas.drawLine(sx(p.first.x), sy(p.first.y), sx(p.second.x), sy(p.second.y), if (both) bothPaint else onePaint)
            }
            else -> links(st).forEach { (p, both) ->
                val (a, b) = p
                canvas.drawLine(sx(a.x), sy(a.y), sx(b.x), sy(b.y), if (both) bothPaint else onePaint)
                drawArrow(canvas, a, b, if (both) bothArrow else oneArrow)
                if (both) drawArrow(canvas, b, a, bothArrow)
            }
        }
        val showCalls = boolPref(PREF_CALLS, true)
        st.sortedBy { it.mine }.forEach { s ->
            val x = sx(s.x); val y = sy(s.y)
            canvas.drawCircle(x, y, (if (s.mine) 5f else 4f) * dp, if (s.mine) myDotPaint else dotPaint)
            if (!s.mine) canvas.drawCircle(x, y, 4f * dp, dotEdge)
            if (showCalls) canvas.drawText(s.call, x + 6 * dp, y - 4 * dp, labelPaint)
        }
        if (st.none { !it.mine }) {
            val note = if (st.isEmpty()) "No stations with a grid yet. Your grid is set in Station > General."
                else "No stations with a grid yet."
            canvas.drawText(note, 10 * dp, height - 10 * dp, notePaint)
        }
        drawTitle(canvas)
    }

    /**
     * Title, top left (Rob, 2026-11-17): "EASY JS8 - callsign - band - date", the date in UTC.
     * Outside the bands the frequency in MHz takes the band's place; no callsign set = left out.
     */
    /** Pixels from the map's top to just under the title's strip. */
    private fun titleHeight(): Float {
        val fm = titlePaint.fontMetrics
        return 8 * dp + (fm.descent - fm.ascent) + 12 * dp + 4 * dp
    }

    private fun drawTitle(canvas: Canvas) {
        val call = prefs.getString("callsign", "").orEmpty().trim().uppercase(Locale.US)
        val band = if (dialHz <= 0) "" else com.js8call.example.util.BandUtils.bandOrNull(dialHz)
            ?: String.format(Locale.US, "%.4f MHz", dialHz / 1e6)
        val date = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date())
        val title = listOf("EASY JS8", call, band, date).filter { it.isNotEmpty() }.joinToString(" - ")
        if (contentDescription?.toString() != title) contentDescription = title // readable by the tests and TalkBack
        val pad = 6 * dp
        val w = titlePaint.measureText(title)
        val fm = titlePaint.fontMetrics
        val left = 8 * dp; val top = 8 * dp
        canvas.drawRect(left, top, left + w + pad * 2, top + (fm.descent - fm.ascent) + pad * 2, titleBack)
        canvas.drawText(title, left + pad, top + pad - fm.ascent, titlePaint)
    }

    private fun drawLayer(canvas: Canvas, lines: List<FloatArray>, paint: Paint) {
        val left = cx - width / 2.0 / scale; val right = cx + width / 2.0 / scale
        val bottom = cy - height / 2.0 / scale; val top = cy + height / 2.0 / scale
        var n = 0
        for (line in lines) {
            val pts = line.size / 2
            for (i in 0 until pts - 1) {
                val x0 = line[i * 2]; val y0 = line[i * 2 + 1]; val x1 = line[i * 2 + 2]; val y1 = line[i * 2 + 3]
                if (max(x0, x1) < left || min(x0, x1) > right || max(y0, y1) < bottom || min(y0, y1) > top) continue
                if (n + 4 > segs.size) { canvas.drawLines(segs, 0, n, paint); n = 0 }
                segs[n++] = sx(x0); segs[n++] = sy(y0); segs[n++] = sx(x1); segs[n++] = sy(y1)
            }
        }
        if (n > 0) canvas.drawLines(segs, 0, n, paint)
    }

    /** The grid's own square: 2 x 1 degrees for 4 characters, 5 x 2.5 minutes for 6 or more. */
    private fun drawGrid(canvas: Canvas, s: Station) {
        val (lat, lon) = Js8Activity.gridToLatLon(s.grid) ?: return
        val (w, h) = if (s.grid.length >= 6) (5.0 / 60) to (2.5 / 60) else 2.0 to 1.0
        val l = sx((lon - w / 2).toFloat()); val r = sx((lon + w / 2).toFloat())
        val t = sy(mercY(lat + h / 2).toFloat()); val b = sy(mercY(lat - h / 2).toFloat())
        canvas.drawRect(l, t, r, b, gridPaint)
    }

    /** Arrow head at [to], on the line from [from]. */
    private fun drawArrow(canvas: Canvas, from: Station, to: Station, paint: Paint) {
        val x0 = sx(from.x); val y0 = sy(from.y); val x1 = sx(to.x); val y1 = sy(to.y)
        if (hypot(x1 - x0, y1 - y0) < 16 * dp) return
        val ang = atan2(y1 - y0, x1 - x0)
        val tipX = x1 - cos(ang) * 6 * dp; val tipY = y1 - sin(ang) * 6 * dp // stop at the dot's edge
        val len = 10 * dp; val wing = 0.45f
        val path = android.graphics.Path().apply {
            moveTo(tipX, tipY)
            lineTo(tipX - len * cos(ang - wing), tipY - len * sin(ang - wing))
            lineTo(tipX - len * cos(ang + wing), tipY - len * sin(ang + wing))
            close()
        }
        canvas.drawPath(path, paint)
    }

    // ---- Touch ------------------------------------------------------------------------------

    /** Fit button: every station again; the map then follows new stations until it is moved. */
    fun fitAll() {
        userMoved = false; fitted = false; invalidate()
    }

    /** Zoom buttons: [factor] times, around the map's middle. */
    fun zoomBy(factor: Double) {
        if (!fitted) fit(stations())
        scale *= factor
        clampView()
        userMoved = true
        invalidate()
    }

    /** Zooms [factor] times keeping the map point under screen point ([px], [py]) in place (pinch, double tap). */
    private fun zoomAt(px: Float, py: Float, factor: Double) {
        if (!fitted) fit(stations())
        val fx = cx + (px - width / 2.0) / scale
        val fy = cy - (py - height / 2.0) / scale
        scale *= factor
        clampView()
        cx = fx - (px - width / 2.0) / scale
        cy = fy + (py - height / 2.0) / scale
        clampView()
        userMoved = true
        invalidate()
    }

    private val scaler = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            zoomAt(d.focusX, d.focusY, d.scaleFactor.toDouble())
            return true
        }
    })

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            cx += dx / scale; cy -= dy / scale
            clampView(); userMoved = true; invalidate()
            return true
        }
        // Double tap: 2 times in on the tapped spot (Rob, 2026-11-17; was fit every station, now the Fit button)
        override fun onDoubleTap(e: MotionEvent): Boolean {
            zoomAt(e.x, e.y, 2.0)
            return true
        }
    })

    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        scaler.onTouchEvent(event)
        if (!scaler.isInProgress) gestures.onTouchEvent(event)
        return true
    }
}
