package com.js8call.example.js8ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.google.android.material.R as M

/**
 * Message Inbox list in the Android look (Rob, 2026-11-26): one row per message, From (bold while unread) with the
 * date at the right, the message on one line, and "frequency · to CALL". Tap a row to read it (tap again: no row);
 * press and hold for Delete / Select More / Select All (Js8Inbox.showMessageMenu). Device light and dark colours.
 */
class Js8InboxList @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {

    data class Item(
        val id: String,
        val from: String,
        val date: String,
        val text: String,
        val freq: String,
        val to: String,
        val unread: Boolean
    )

    var onRowClick: ((Item) -> Unit)? = null
    /** Press and hold a row: the row and the view to anchor a menu to. */
    var onRowLongClick: ((Item, View) -> Unit)? = null

    var selectedRowId: String? = null
        set(value) { field = value; rebindAll() }

    /** Rows picked after [startMultiSelect] or [selectAll]; empty otherwise. */
    private val multiSelected = linkedSetOf<String>()
    private var multiSelecting = false
    val selectedRowIds: Set<String> get() = multiSelected.ifEmpty { setOfNotNull(selectedRowId) }

    fun startMultiSelect(firstId: String) {
        val current = setOfNotNull(selectedRowId)
        multiSelected.clear()
        multiSelected += current + firstId
        multiSelecting = true
        rebindAll()
    }

    fun selectAll() {
        multiSelected.clear()
        multiSelected += items.map { it.id }
        multiSelecting = true
        rebindAll()
    }

    fun clearSelection() {
        multiSelected.clear()
        multiSelecting = false
        selectedRowId = null
    }

    var emptyHintText: String? = null
        set(value) { field = value; emptyHint.text = value; updateEmptyHint() }

    private val dp = resources.displayMetrics.density
    private fun px(v: Float) = (v * dp).toInt()
    private fun color(a: Int, fallback: Int) = MaterialColors.getColor(this, a, fallback)

    private val onSurface = color(M.attr.colorOnSurface, Color.BLACK)
    private val onSurfaceVariant = color(M.attr.colorOnSurfaceVariant, Color.DKGRAY)
    private val selectedBg = color(M.attr.colorSecondaryContainer, Color.LTGRAY)

    private var items: List<Item> = emptyList()
    private val list = RecyclerView(context)
    private val adapter = ItemAdapter()
    private val emptyHint = TextView(context).apply {
        setTextColor(onSurfaceVariant)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setPadding(px(12f), px(12f), px(12f), 0)
        visibility = GONE
        isClickable = false
        isFocusable = false
    }

    init {
        // Outlined, rounded box in the surface colour
        background = GradientDrawable().apply {
            cornerRadius = 8 * dp
            setColor(color(M.attr.colorSurface, Color.WHITE))
            setStroke(px(1f).coerceAtLeast(1), color(M.attr.colorOutline, Color.GRAY))
        }
        setPadding(px(1f), px(1f), px(1f), px(1f))
        clipToOutline = true
        list.layoutManager = LinearLayoutManager(context)
        list.adapter = adapter
        list.itemAnimator = null
        list.addItemDecoration(MaterialDividerItemDecoration(context, LinearLayoutManager.VERTICAL).apply {
            isLastItemDecorated = false
            dividerColor = color(M.attr.colorOutlineVariant, Color.LTGRAY)
        })
        addView(list, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(emptyHint, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    fun setRows(newItems: List<Item>) {
        if (newItems == items) return
        val old = items
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = newItems.size
            override fun areItemsTheSame(o: Int, n: Int) = old[o].id == newItems[n].id
            override fun areContentsTheSame(o: Int, n: Int) = old[o] == newItems[n]
        }, false)
        items = newItems
        diff.dispatchUpdatesTo(adapter)
        // A deleted message leaves no selection behind
        val ids = newItems.map { it.id }.toSet()
        multiSelected.retainAll(ids)
        if (multiSelected.isEmpty()) multiSelecting = false
        if (selectedRowId != null && selectedRowId !in ids) selectedRowId = null
        updateEmptyHint()
    }

    private fun updateEmptyHint() {
        emptyHint.visibility = if (items.isEmpty() && !emptyHintText.isNullOrEmpty()) VISIBLE else GONE
    }

    private fun rebindAll() {
        if (adapter.itemCount > 0) adapter.notifyItemRangeChanged(0, adapter.itemCount)
    }

    private inner class Holder(val row: LinearLayout, val from: TextView, val date: TextView, val text: TextView,
                               val info: TextView) : RecyclerView.ViewHolder(row)

    private inner class ItemAdapter : RecyclerView.Adapter<Holder>() {
        override fun getItemCount() = items.size

        private fun line(size: Float, c: Int) = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            setTextColor(c)
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val from = line(15f, onSurface)
            val date = line(12f, onSurfaceVariant)
            val text = line(14f, onSurface)
            val info = line(12f, onSurfaceVariant)
            val top = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(from, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(date, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { marginStart = px(8f) })
            }
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(px(12f), px(8f), px(12f), px(8f))
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                addView(top)
                addView(text)
                addView(info)
            }
            return Holder(row, from, date, text, info)
        }

        override fun onBindViewHolder(h: Holder, position: Int) {
            val item = items[position]
            h.row.tag = item.id
            h.from.text = item.from
            h.from.typeface = if (item.unread) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            h.date.text = item.date
            h.text.text = item.text
            h.info.text = listOf(item.freq, if (item.to.isEmpty()) "" else "to ${item.to}").filter { it.isNotEmpty() }
                .joinToString(" · ")
            val selected = if (multiSelected.isEmpty()) item.id == selectedRowId else item.id in multiSelected
            h.row.setBackgroundColor(if (selected) selectedBg else Color.TRANSPARENT)
            h.row.isSelected = selected
            h.row.setOnClickListener {
                if (multiSelecting) {
                    if (!multiSelected.remove(item.id)) multiSelected += item.id
                    if (multiSelected.isEmpty()) multiSelecting = false
                    rebindAll()
                } else {
                    selectedRowId = if (selectedRowId == item.id) null else item.id
                    onRowClick?.invoke(item)
                }
            }
            h.row.setOnLongClickListener { v ->
                val handler = onRowLongClick ?: return@setOnLongClickListener false
                handler(item, v)
                true
            }
        }
    }
}
