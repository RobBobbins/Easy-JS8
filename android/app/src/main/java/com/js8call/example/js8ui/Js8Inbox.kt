package com.js8call.example.js8ui

import com.js8call.example.data.MessageEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

/** Message Inbox list (PC MessagePanel): its rows, shared by the main window and the Message Inbox screen. */
object Js8Inbox {
    /** Date in the row's first line, UTC (the preview Rob passed, 2026-11-26) */
    private val date = SimpleDateFormat("MMM d HH:mm", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

    /** Grey hints while the Inbox boxes are empty, like the Typing Box (Rob, 2026-11-22, as the iOS app). */
    const val LIST_HINT = "YOUR STORED MESSAGES WILL BE LISTED HERE."
    const val MESSAGE_HINT = "TAP A MESSAGE IN THE LIST TO READ IT HERE."
    const val REPLY_HINT = "TYPE YOUR REPLY HERE."

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

    /** Newest first; From with "via" for a relayed message; to: the group or held-for call, else my call. */
    fun rows(messages: List<MessageEntity>, myCall: String) = messages.sortedByDescending { it.timestamp }.map { m ->
        Js8InboxList.Item(
            id = m.id.toString(),
            from = from(m).split('>').reversed().joinToString(" via "),
            date = date.format(Date(m.timestamp)),
            text = m.text,
            freq = frequencyText(m),
            to = if (isHeld(m) || m.conversationId.startsWith("@")) m.conversationId else myCall,
            unread = !m.isRead
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
    fun showMessageMenu(table: Js8InboxList, row: Js8InboxList.Item, anchor: android.view.View, delete: (List<String>) -> Unit) {
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
