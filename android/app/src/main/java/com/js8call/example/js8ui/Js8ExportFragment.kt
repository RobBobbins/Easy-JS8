package com.js8call.example.js8ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.js8call.example.data.Js8LogBook
import com.js8call.example.data.MessageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Export Options (Settings > Log > Export Log..., Rob 2026-10-20; the PC has Log > Open Log
 * Directory...): tick the files, Export... asks for a folder with the phone's folder picker
 * (it opens on the folder used last time) and saves each ticked file there.
 */
class Js8ExportFragment : Fragment() {

    companion object {
        private const val KEY_FOLDER = "js8cfg_export_folder"
        private const val INBOX_FILE = "js8call_inbox.txt"
    }

    private lateinit var ui: Js8Page
    private lateinit var adif: CheckBox
    private lateinit var csv: CheckBox
    private lateinit var inbox: CheckBox

    private fun prefs() = PreferenceManager.getDefaultSharedPreferences(requireContext())

    private val pickFolder = registerForActivityResult(object : ActivityResultContracts.OpenDocumentTree() {
        override fun createIntent(context: android.content.Context, input: Uri?): Intent =
            super.createIntent(context, input).addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }) { tree ->
        if (tree == null) return@registerForActivityResult
        // Kept, so next time the picker opens on this folder
        runCatching {
            requireContext().contentResolver.takePersistableUriPermission(tree,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        prefs().edit().putString(KEY_FOLDER, tree.toString()).apply()
        export(tree)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        ui = Js8Page(requireContext())
        val page = ui.column()
        adif = ui.checkBox("Log (${Js8LogBook.ADIF_FILE})    ADIF, for logbook programs", true)
        csv = ui.checkBox("Log (${Js8LogBook.CSV_FILE})    the PC's plain text copy of the log", false)
        inbox = ui.checkBox("Message Inbox    received and held messages", false)
        listOf(adif, csv, inbox).forEach { page.addView(it) }
        return ui.frame("Export Options", onBack = { findNavController().popBackStack() }, content = page,
            buttons = listOf("Export..." to { onExport() }, "Cancel" to { findNavController().popBackStack() }))
    }

    private fun onExport() {
        if (!adif.isChecked && !csv.isChecked && !inbox.isChecked) {
            message("Tick at least one file to export")
            return
        }
        val last = prefs().getString(KEY_FOLDER, null)?.let { Uri.parse(it) }
        // EXTRA_INITIAL_URI wants a document uri inside the tree
        val initial = last?.let { runCatching { DocumentsContract.buildDocumentUriUsingTree(it, DocumentsContract.getTreeDocumentId(it)) }.getOrNull() }
        pickFolder.launch(initial)
    }

    private fun export(tree: Uri) {
        val ctx = requireContext().applicationContext
        val wantAdif = adif.isChecked
        val wantCsv = csv.isChecked
        val wantInbox = inbox.isChecked
        viewLifecycleOwner.lifecycleScope.launch {
            val saved = mutableListOf<String>()
            val problems = mutableListOf<String>()
            withContext(Dispatchers.IO) {
                fun write(name: String, bytes: ByteArray) {
                    try {
                        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                        val doc = DocumentsContract.createDocument(ctx.contentResolver, parent, "application/octet-stream", name)
                            ?: throw IllegalStateException("the folder did not accept the file")
                        ctx.contentResolver.openOutputStream(doc)?.use { it.write(bytes) }
                            ?: throw IllegalStateException("cannot write")
                        saved += name
                    } catch (e: Exception) {
                        problems += "$name: ${e.message}"
                    }
                }
                if (wantAdif) {
                    val f = Js8LogBook.adifFile(ctx)
                    if (f.exists()) write(f.name, f.readBytes()) else problems += "${f.name}: there is no log yet"
                }
                if (wantCsv) {
                    val f = Js8LogBook.csvFile(ctx)
                    if (f.exists()) write(f.name, f.readBytes()) else problems += "${f.name}: there is no log yet"
                }
                if (wantInbox) write(INBOX_FILE, inboxText(ctx).toByteArray(Charsets.UTF_8))
            }
            if (!isAdded) return@launch
            if (problems.isEmpty()) {
                Toast.makeText(requireContext(), "Exported: ${saved.joinToString(", ")}", Toast.LENGTH_LONG).show()
                findNavController().popBackStack()
            } else {
                message((if (saved.isEmpty()) "" else "Exported: ${saved.joinToString(", ")}\n\n") +
                    "Not exported:\n" + problems.joinToString("\n"))
            }
        }
    }

    /** One message per line: date (UTC), from, to, message. Oldest first. */
    private suspend fun inboxText(ctx: android.content.Context): String {
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val my = prefs().getString("callsign", "").orEmpty().uppercase(Locale.US)
        val all = Js8Inbox.incoming(MessageRepository.getInstance(ctx).getAllMessagesSync()).sortedBy { it.timestamp }
        return all.joinToString("") { m ->
            val to = if (Js8Inbox.isHeld(m) || m.conversationId.startsWith("@")) m.conversationId else my
            "${date.format(Date(m.timestamp))}, ${Js8Inbox.from(m)}, $to, ${m.text}\n"
        }
    }

    private fun message(text: String) {
        AlertDialog.Builder(requireContext()).setMessage(text).setPositiveButton(android.R.string.ok, null).show()
    }
}
