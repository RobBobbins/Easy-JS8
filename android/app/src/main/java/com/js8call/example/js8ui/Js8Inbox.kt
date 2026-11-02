package com.js8call.example.js8ui

import com.js8call.example.data.MessageEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

/** Message Inbox list (PC MessagePanel): columns and rows, shared by the main window and the Message Inbox screen. */
object Js8Inbox {
    private val date = SimpleDateFormat("EEE MMM d HH:mm:ss yyyy", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

    fun setColumns(table: Js8TableView) = table.setColumns("inbox", listOf(
        Js8TableView.Column("flag", "⚑", 24),
        Js8TableView.Column("id", "ID", 40, numeric = true),
        Js8TableView.Column("date", "Date", 120),
        Js8TableView.Column("freq", "Frequency", 80),
        Js8TableView.Column("from", "From", 70),
        Js8TableView.Column("to", "To", 70),
        Js8TableView.Column("message", "Message", 200, stretch = true)
    ))

    /**
     * Received messages and the ones held for other stations, not yet collected (Inbox::fetchForCall:
     * STORE, READ, UNREAD). The JS8Android store also holds sent ones, left out.
     */
    fun incoming(all: List<MessageEntity>) = all.filter {
        it.direction == MessageEntity.DIRECTION_INCOMING ||
            it.direction == MessageEntity.DIRECTION_HELD && it.status == MessageEntity.STATUS_PENDING
    }

    fun isHeld(m: MessageEntity) = m.direction == MessageEntity.DIRECTION_HELD

    /** Inbox::fetchForCall: from them, or held for them. */
    fun isFor(m: MessageEntity, call: String) =
        (if (isHeld(m)) m.conversationId else m.senderCallsign ?: m.conversationId).equals(call, ignoreCase = true)

    fun rows(messages: List<MessageEntity>, myCall: String) = messages.sortedByDescending { it.timestamp }.map { m ->
        Js8TableView.Row(
            id = m.id.toString(),
            values = mapOf(
                "flag" to if (m.isRead) "" else "⚑",
                "id" to m.id.toString(),
                "date" to date.format(Date(m.timestamp)),
                "freq" to frequencyText(m),
                "from" to from(m).split('>').reversed().joinToString(" via "),
                "to" to if (isHeld(m) || m.conversationId.startsWith("@")) m.conversationId else myCall,
                "message" to m.text
            ),
            sortValues = mapOf("id" to m.id, "date" to m.timestamp, "freq" to (m.dialHz ?: 0L)),
            bold = !m.isRead, center = setOf("flag")
        )
    }

    /** PC pretty_frequency_MHz_string + " MHz"; a message stored before the dial was kept shows its offset. */
    private fun frequencyText(m: MessageEntity): String {
        val dial = m.dialHz ?: return m.frequency?.let { "${it.roundToInt()} Hz" } ?: ""
        return String.format(Locale.US, "%.6f", dial / 1_000_000.0).let { "${it.dropLast(3)}\u00A0${it.takeLast(3)} MHz" }
    }

    fun from(m: MessageEntity) = if (isHeld(m)) m.senderCallsign ?: "" else m.relayPath ?: m.senderCallsign ?: m.conversationId

    /**
     * Press and hold a message: Delete (MessagePanel's Delete action: every selected row, the
     * PC's Ctrl and Shift clicks), Select More (taps then add or take away rows, until the
     * delete) and Select All. [delete] gets the row ids to remove from the message store.
     */
    fun showMessageMenu(table: Js8TableView, row: Js8TableView.Row, anchor: android.view.View, delete: (List<String>) -> Unit) {
        val menu = android.widget.PopupMenu(android.view.ContextThemeWrapper(anchor.context, com.js8call.example.R.style.ThemeOverlay_Js8PopupMenu), anchor)
        menu.menu.add(0, 1, 1, "Delete")
        menu.menu.add(0, 2, 2, "Select More")
        menu.menu.add(0, 3, 3, "Select All")
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                2 -> table.startMultiSelect(row.id)
                3 -> table.selectAll()
                else -> {
                    val ids = table.selectedRowIds.takeIf { row.id in it }?.toList() ?: listOf(row.id)
                    table.clearSelection()
                    delete(ids)
                }
            }
            true
        }
        menu.show()
    }

    /** Reply: "FROM MSG [MESSAGE]" for the Typing Box (MessagePanel::on_replyPushButton_clicked). */
    fun replyText(m: MessageEntity, typed: String) = "${from(m)} MSG ${typed.trim().ifEmpty { "[MESSAGE]" }}"
}
