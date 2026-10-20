package com.js8call.example.js8ui

import com.js8call.example.data.MessageEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

/** Message Inbox list (PC MessagePanel): columns and rows, shared by the main window and the Message Inbox screen. */
object Js8Inbox {
    private val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

    fun setColumns(table: Js8TableView) = table.setColumns("inbox", listOf(
        Js8TableView.Column("flag", "⚑", 24),
        Js8TableView.Column("id", "ID", 40, numeric = true),
        Js8TableView.Column("date", "Date", 120),
        Js8TableView.Column("freq", "Frequency", 80),
        Js8TableView.Column("from", "From", 70),
        Js8TableView.Column("to", "To", 70),
        Js8TableView.Column("message", "Message", 200, stretch = true)
    ))

    /** Received messages only (the JS8Android store also holds sent ones). */
    fun incoming(all: List<MessageEntity>) = all.filter { it.direction == MessageEntity.DIRECTION_INCOMING }

    fun rows(messages: List<MessageEntity>, myCall: String) = messages.map { m ->
        Js8TableView.Row(
            id = m.id.toString(),
            values = mapOf(
                "flag" to if (m.isRead) "" else "⚑",
                "id" to m.id.toString(),
                "date" to date.format(Date(m.timestamp)),
                "freq" to (m.frequency?.let { "${it.roundToInt()} Hz" } ?: ""),
                "from" to from(m),
                "to" to if (m.conversationId.startsWith("@")) m.conversationId else myCall,
                "message" to m.text
            ),
            sortValues = mapOf("id" to m.id, "date" to m.timestamp),
            bold = !m.isRead, center = setOf("flag")
        )
    }

    fun from(m: MessageEntity) = m.relayPath ?: m.senderCallsign ?: m.conversationId

    /** Reply: "FROM MSG [MESSAGE]" for the Typing Box (MessagePanel::on_replyPushButton_clicked). */
    fun replyText(m: MessageEntity, typed: String) = "${from(m)} MSG ${typed.trim().ifEmpty { "[MESSAGE]" }}"
}
