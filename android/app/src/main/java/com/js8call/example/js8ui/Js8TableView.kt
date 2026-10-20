package com.js8call.example.js8ui

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.js8call.example.R

/**
 * Desktop-style table (Band Activity, Call Activity, Inbox).
 *
 * - Header row in the list-header colors, body white, like the JS8Call QTableWidget.
 * - The whole table scrolls sideways; the body scrolls up/down.
 * - Tap a column header: sort by it (tap again: reverse).
 * - Press and hold a column header: checklist to show/hide columns (saved per table).
 */
class Js8TableView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    companion object {
        val SELECTED_BG = android.graphics.Color.parseColor("#3498DB")
    }

    data class Column(
        val key: String,
        val label: String,
        val widthDp: Int,
        val defaultVisible: Boolean = true,
        /** Last visible column stretches to fill the table width. */
        val stretch: Boolean = false,
        val numeric: Boolean = false,
        val menuLabel: String = label
    )

    /** One row: values keyed by column key, plus an optional sort value per key. */
    data class Row(
        val id: String,
        val values: Map<String, String>,
        val sortValues: Map<String, Comparable<*>> = emptyMap(),
        val bold: Boolean = false,
        /** Row background (CQ green, to-me red, highlight words), null = table background. */
        val bg: Int? = null,
        /** Text color for one column (e.g. red for a blocked station's icon). */
        val colors: Map<String, Int> = emptyMap(),
        /** Center this column's text (Age, icon). */
        val center: Set<String> = emptySet()
    )

    var tableKey: String = ""
    var onRowClick: ((Row) -> Unit)? = null
    var onRowDoubleClick: ((Row) -> Unit)? = null
    var selectedRowId: String? = null
        set(value) { field = value; rebindAll() }

    private var columns: List<Column> = emptyList()
    private var rows: List<Row> = emptyList()
    private var sortedRows: List<Row> = emptyList()
    private var sortKey: String? = null
    private var sortAscending = true

    private val dp = resources.displayMetrics.density
    private val font: Typeface? = ResourcesCompat.getFont(context, R.font.selawik)
    private val boldFont: Typeface = Typeface.create(font, Typeface.BOLD)
    private val textSizePx = resources.getDimension(R.dimen.js8_text)
    private val headerHeight = resources.getDimensionPixelSize(R.dimen.js8_list_header_height)
    private val rowHeight = resources.getDimensionPixelSize(R.dimen.js8_list_row_height)

    private val hScroll = HorizontalScrollView(context).apply {
        isFillViewport = true
        isHorizontalScrollBarEnabled = true
    }
    private val inner = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val header = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private val list = RecyclerView(context)
    private val adapter = RowAdapter()

    init {
        setBackgroundResource(R.drawable.js8_panel_list)
        setPadding(1, 1, 1, 1)
        list.layoutManager = LinearLayoutManager(context)
        list.adapter = adapter
        // Rows change in place, no fade / slide animations (they made the text bounce on each refresh)
        list.itemAnimator = null
        list.isNestedScrollingEnabled = true
        list.setBackgroundColor(ContextCompat.getColor(context, R.color.js8_list_bg))
        inner.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, headerHeight))
        inner.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        hScroll.addView(inner, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(hScroll, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    fun setColumns(key: String, cols: List<Column>) {
        tableKey = key
        columns = cols
        rebuildHeader()
    }

    /** Change one header label (desktop shows "Callsigns (N)"). */
    fun setHeaderLabel(key: String, label: String) {
        val i = columns.indexOfFirst { it.key == key }
        if (i < 0 || columns[i].label == label) return
        columns = columns.toMutableList().also { it[i] = it[i].copy(label = label) }
        rebuildHeader()
    }

    /**
     * New rows. Only rows whose text or color changed are redrawn, in place; the list keeps its
     * scroll position (PC displayBandActivity: updates off, rebuild, scroll bar put back).
     */
    fun setRows(newRows: List<Row>) {
        if (newRows == rows) return
        rows = newRows
        if (measureContents()) rebuildHeader()
        applySort()
    }

    /** Redraw every row in place (column widths, selection), without rebuilding the list. */
    private fun rebindAll() {
        if (adapter.itemCount > 0) adapter.notifyItemRangeChanged(0, adapter.itemCount)
    }

    /** Show [newSorted]: inserts, removals and changed rows only, scroll position kept. */
    private fun showRows(newSorted: List<Row>) {
        val old = sortedRows
        if (old == newSorted) return
        val lm = list.layoutManager as LinearLayoutManager
        val first = lm.findFirstVisibleItemPosition()
        val top = list.getChildAt(0)?.top ?: 0
        val diff = androidx.recyclerview.widget.DiffUtil.calculateDiff(object : androidx.recyclerview.widget.DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = newSorted.size
            override fun areItemsTheSame(o: Int, n: Int) = old[o].id == newSorted[n].id
            override fun areContentsTheSame(o: Int, n: Int) = old[o] == newSorted[n]
        }, false)
        sortedRows = newSorted
        diff.dispatchUpdatesTo(adapter)
        // Put the scroll position back unless a finger is moving the list
        if (first != RecyclerView.NO_POSITION && list.scrollState == RecyclerView.SCROLL_STATE_IDLE &&
            first < newSorted.size) {
            lm.scrollToPositionWithOffset(first, top - list.paddingTop)
        }
    }

    private fun prefKey(col: Column) = "js8_table_${tableKey}_col_${col.key}"

    private fun isVisible(col: Column): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean(prefKey(col), col.defaultVisible)

    private fun visibleColumns(): List<Column> = columns.filter { isVisible(it) }

    /** Widest text per column (header or any row), like QTableWidget::resizeColumnToContents. */
    private var contentPx: Map<String, Int> = emptyMap()
    private val measure = android.graphics.Paint().apply { textSize = textSizePx; typeface = font }

    private fun widthPx(col: Column): Int {
        val min = (col.widthDp * dp).toInt()
        if (col.stretch) return min
        return maxOf(min, contentPx[col.key] ?: 0)
    }

    private fun measureContents(): Boolean {
        val pad = (12 * dp).toInt()
        val m = columns.associate { c ->
            val widest = (sequenceOf(c.label) + rows.asSequence().map { it.values[c.key].orEmpty() })
                .maxOf { measure.measureText(it) }
            c.key to widest.toInt() + pad
        }
        val changed = m != contentPx
        contentPx = m
        return changed
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw) post { rebuildHeader() }
    }

    /** Width for a stretch column: whatever is left of the table width, but never narrower than its own width. */
    private fun columnWidths(): List<Int> {
        val vis = visibleColumns()
        val fixed = vis.sumOf { widthPx(it) }
        val spare = (width - paddingLeft - paddingRight - fixed).coerceAtLeast(0)
        return vis.mapIndexed { i, c ->
            if (c.stretch || (i == vis.lastIndex && vis.none { it.stretch })) widthPx(c) + spare else widthPx(c)
        }
    }

    private fun rebuildHeader() {
        header.removeAllViews()
        val vis = visibleColumns()
        val widths = columnWidths()
        vis.forEachIndexed { i, col ->
            val label = TextView(context).apply {
                text = col.label
                typeface = font
                setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
                setTextColor(ContextCompat.getColor(context, R.color.js8_list_header_text))
                // Desktop centers header labels. A stretch column wider than the visible table
                // would hide a centered label off-screen, so that one starts at the left edge.
                val offScreen = widths[i] > this@Js8TableView.width / 2
                gravity = if (offScreen) Gravity.CENTER_VERTICAL or Gravity.START else Gravity.CENTER
                if (offScreen) setPadding((6 * dp).toInt(), 0, 0, 0)
                setBackgroundResource(R.drawable.js8_list_header_cell)
                isSingleLine = true
                includeFontPadding = false
                setOnClickListener { onHeaderTap(col) }
                setOnLongClickListener { showColumnChooser(); true }
            }
            header.addView(label, LinearLayout.LayoutParams(widths[i], ViewGroup.LayoutParams.MATCH_PARENT))
        }
        rebindAll()
    }

    private fun onHeaderTap(col: Column) {
        if (sortKey == col.key) sortAscending = !sortAscending else { sortKey = col.key; sortAscending = true }
        applySort()
    }

    @Suppress("UNCHECKED_CAST")
    private fun applySort() {
        val key = sortKey
        showRows(if (key == null) rows else {
            val cmp = Comparator<Row> { a, b ->
                val av = (a.sortValues[key] ?: a.values[key] ?: "") as Comparable<Any>
                val bv = (b.sortValues[key] ?: b.values[key] ?: "") as Comparable<Any>
                try { av.compareTo(bv) } catch (_: ClassCastException) { av.toString().compareTo(bv.toString()) }
            }
            if (sortAscending) rows.sortedWith(cmp) else rows.sortedWith(cmp.reversed())
        })
    }


    private fun showColumnChooser() {
        val labels = columns.map { it.menuLabel }.toTypedArray()
        val checked = columns.map { isVisible(it) }.toBooleanArray()
        AlertDialog.Builder(context)
            .setTitle("Show Columns")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val editor = PreferenceManager.getDefaultSharedPreferences(context).edit()
                columns.forEachIndexed { i, c -> editor.putBoolean(prefKey(c), checked[i]) }
                editor.apply()
                rebuildHeader()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private inner class RowHolder(val rowView: LinearLayout) : RecyclerView.ViewHolder(rowView)

    private inner class RowAdapter : RecyclerView.Adapter<RowHolder>() {
        private var lastClickId: String? = null
        private var lastClickTime = 0L

        override fun getItemCount() = sortedRows.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder {
            val row = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, rowHeight)
            }
            return RowHolder(row)
        }

        override fun onBindViewHolder(holder: RowHolder, position: Int) {
            val row = sortedRows[position]
            val vis = visibleColumns()
            val widths = columnWidths()
            val rv = holder.rowView
            // Same number of cells: change them in place (no new views, so nothing jumps)
            if (rv.childCount != vis.size) {
                rv.removeAllViews()
                repeat(vis.size) {
                    rv.addView(TextView(context).apply {
                        setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
                        isSingleLine = true
                        includeFontPadding = false
                        setPadding((4 * dp).toInt(), 0, (4 * dp).toInt(), 0)
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT))
                }
            }
            val selected = row.id == selectedRowId
            // Selected row: desktop colorTableHighlight #3498db
            rv.setBackgroundColor(if (selected) SELECTED_BG else row.bg ?: 0)
            val face = if (row.bold) boldFont else font
            vis.forEachIndexed { i, col ->
                val tv = rv.getChildAt(i) as TextView
                val text = row.values[col.key].orEmpty()
                if (tv.text.toString() != text) tv.text = text
                if (tv.typeface != face) tv.typeface = face
                tv.setTextColor(row.colors[col.key] ?: ContextCompat.getColor(context, R.color.js8_list_text))
                val g = Gravity.CENTER_VERTICAL or when {
                    col.key in row.center -> Gravity.CENTER_HORIZONTAL
                    col.numeric -> Gravity.END
                    else -> Gravity.START
                }
                if (tv.gravity != g) tv.gravity = g
                val lp = tv.layoutParams
                if (lp.width != widths[i]) { lp.width = widths[i]; tv.layoutParams = lp }
            }
            rv.setOnClickListener {
                val now = System.currentTimeMillis()
                if (lastClickId == row.id && now - lastClickTime < 400) {
                    onRowDoubleClick?.invoke(row)
                } else {
                    selectedRowId = if (selectedRowId == row.id) null else row.id
                    onRowClick?.invoke(row)
                }
                lastClickId = row.id
                lastClickTime = now
            }
        }
    }
}
