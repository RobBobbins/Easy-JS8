package com.js8call.example.data

import android.content.Context
import androidx.preference.PreferenceManager
import com.js8call.example.js8ui.Js8Activity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Messages held for other stations (the PC's inbox "STORE" entries; JS8_Main/Inbox.cpp,
 * mainwindow.cpp getNextMessageIdForCallsign and friends): Call Activity > Store Message...,
 * "MSG TO:CALL ..." heard for us, and the API's INBOX.STORE_MESSAGE. Kept in the message
 * database with direction HELD; status PENDING until collected, SENT (the PC's DELIVERED)
 * after. The id is the MSG ID sent over the air.
 *
 * Group messages (to an @GROUP) stay held for everyone in the group for 2 days; who has
 * collected one is kept in the settings (the PC's inbox_group_recip_v1 table).
 */
object Js8Mailbox {
    private const val GROUP_DAYS_MS = 2 * 24 * 3600 * 1000L
    private const val GROUP_DELIVERED_PREFIX = "js8mailbox_group_delivered_"

    private fun dao(context: Context) = MessageDatabase.getInstance(context).messageDao()

    /** Holds [text] from [from] for [to] (callsign or @GROUP). Returns the message id. */
    suspend fun store(context: Context, to: String, from: String, text: String, relayPath: String?,
                      snr: Int? = null, offset: Float? = null): Long = withContext(Dispatchers.IO) {
        dao(context).insertMessage(MessageEntity(
            conversationId = to.trim().uppercase(),
            direction = MessageEntity.DIRECTION_HELD,
            senderCallsign = from.trim().uppercase(),
            text = text.trim(),
            timestamp = System.currentTimeMillis(),
            snr = snr,
            frequency = offset,
            status = MessageEntity.STATUS_PENDING,
            isRead = true, // never counts as unread
            relayPath = relayPath
        ))
    }

    /** Held messages not yet collected, oldest first (the PC's ORDER BY id). */
    private suspend fun held(context: Context): List<MessageEntity> = withContext(Dispatchers.IO) {
        dao(context).getAllMessagesSync()
            .filter { it.direction == MessageEntity.DIRECTION_HELD && it.status == MessageEntity.STATUS_PENDING && it.text.isNotBlank() }
            .sortedBy { it.id }
    }

    private fun matches(m: MessageEntity, call: String) =
        m.conversationId.equals(call, true) || m.conversationId.equals(Js8Activity.baseCall(call), true)

    /** getNextMessageIdForCallsign: the first message held for this callsign (or its base call). */
    suspend fun nextIdFor(context: Context, call: String): Long =
        held(context).firstOrNull { !it.conversationId.startsWith("@") && matches(it, call) }?.id ?: -1

    /** getLookaheadMessageIdForCallsign: the next one after [afterId]. */
    suspend fun lookaheadIdFor(context: Context, call: String, afterId: Long): Long =
        held(context).firstOrNull { it.id != afterId && !it.conversationId.startsWith("@") && matches(it, call) }?.id ?: -1

    /** countUnreadForCallsign. */
    suspend fun countFor(context: Context, call: String): Int =
        held(context).count { !it.conversationId.startsWith("@") && it.conversationId.equals(call, true) }

    private fun deliveredTo(context: Context, id: Long): Set<String> =
        PreferenceManager.getDefaultSharedPreferences(context).getString("$GROUP_DELIVERED_PREFIX$id", "").orEmpty()
            .split(',').filter { it.isNotEmpty() }.toSet()

    private suspend fun groupHeld(context: Context, group: String, call: String): List<MessageEntity> {
        val since = System.currentTimeMillis() - GROUP_DAYS_MS
        return held(context).filter {
            it.conversationId.equals(group, true) && it.timestamp > since && call.uppercase() !in deliveredTo(context, it.id)
        }
    }

    /** getNextGroupMessageIdForCallsign. */
    suspend fun nextGroupIdFor(context: Context, group: String, call: String): Long =
        groupHeld(context, group, call).firstOrNull()?.id ?: -1

    /** getLookaheadGroupMessageIdForCallsign. */
    suspend fun lookaheadGroupIdFor(context: Context, group: String, call: String, afterId: Long): Long =
        (groupHeld(context, group, call).firstOrNull { it.id != afterId }
            ?: groupHeld(context, group, Js8Activity.baseCall(call)).firstOrNull { it.id != afterId })?.id ?: -1

    /** countGroupUnreadForCallsign. */
    suspend fun countGroupFor(context: Context, group: String, call: String): Int = groupHeld(context, group, call).size

    suspend fun get(context: Context, id: Long): MessageEntity? = withContext(Dispatchers.IO) { dao(context).getMessageById(id) }

    /** markMsgDelivered: no longer offered or listed. */
    suspend fun markDelivered(context: Context, id: Long) = withContext(Dispatchers.IO) {
        dao(context).updateMessageStatus(id, MessageEntity.STATUS_SENT)
    }

    /** markGroupMsgDeliveredForCallsign. */
    fun markGroupDelivered(context: Context, id: Long, call: String) {
        val p = PreferenceManager.getDefaultSharedPreferences(context)
        val set = deliveredTo(context, id) + call.uppercase()
        p.edit().putString("$GROUP_DELIVERED_PREFIX$id", set.joinToString(",")).apply()
    }
}
