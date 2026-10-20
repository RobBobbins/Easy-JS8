package com.js8call.example.js8ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.js8call.example.R
import com.js8call.example.data.MessageEntity
import com.js8call.example.data.MessageRepository
import kotlinx.coroutines.launch

/**
 * Message Inbox screen (PC mode: View > Inbox, Call Activity > Show Message Inbox...). The PC
 * window has no inbox strip; this is the PC's Message Inbox dock as its own screen (Rob, 2026-10-20).
 * Reply puts its line in the Typing Box (same result as the APRS pages) and goes back.
 */
class Js8InboxFragment : Fragment() {

    private val repository by lazy { MessageRepository.getInstance(requireContext()) }
    private var messages: List<MessageEntity> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_js8_inbox, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val table = view.findViewById<Js8TableView>(R.id.js8_inbox_table)
        val message = view.findViewById<TextView>(R.id.js8_inbox_message)
        val replyText = view.findViewById<EditText>(R.id.js8_inbox_reply_text)
        Js8Inbox.setColumns(table)
        view.findViewById<View>(R.id.js8_inbox_back).setOnClickListener { findNavController().popBackStack() }
        repository.getAllMessages().observe(viewLifecycleOwner) { all ->
            messages = Js8Inbox.incoming(all)
            val my = PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getString("callsign", "").orEmpty().uppercase()
            table.setRows(Js8Inbox.rows(messages, my))
        }
        table.onRowClick = { row ->
            messages.firstOrNull { it.id.toString() == row.id }?.let { m ->
                message.text = m.text
                if (!m.isRead) viewLifecycleOwner.lifecycleScope.launch { repository.markMessageAsRead(m.id) }
            }
        }
        view.findViewById<View>(R.id.js8_inbox_reply).setOnClickListener {
            val m = messages.firstOrNull { it.id.toString() == table.selectedRowId } ?: return@setOnClickListener
            parentFragmentManager.setFragmentResult(Js8AprsFragment.RESULT_KEY,
                bundleOf(Js8AprsFragment.RESULT_TEXT to Js8Inbox.replyText(m, replyText.text.toString())))
            findNavController().popBackStack()
        }
    }
}
