package com.js8call.example.js8ui

import android.content.Context
import android.content.Intent
import android.widget.EditText
import android.widget.PopupMenu
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.js8call.example.service.JS8EngineService
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * PC Configurations menu (MultiSettings): named sets of settings, one a file in the app's files
 * folder holding a copy of every setting. The PC restarts on a switch; here the engine stops and
 * the screens are rebuilt, and RX is pressed again.
 */
object Js8Configurations {

    const val DEFAULT = "Default"
    private const val META = "js8_configurations"
    private const val KEY_CURRENT = "current"

    private fun meta(c: Context) = c.getSharedPreferences(META, Context.MODE_PRIVATE)
    private fun prefs(c: Context) = PreferenceManager.getDefaultSharedPreferences(c)
    private fun folder(c: Context) = File(c.filesDir, "configurations").apply { mkdirs() }
    private fun file(c: Context, name: String) = File(folder(c), URLEncoder.encode(name, "UTF-8") + ".json")

    private fun current(c: Context) = meta(c).getString(KEY_CURRENT, DEFAULT) ?: DEFAULT

    fun currentName(c: Context) = current(c)

    private fun names(c: Context): List<String> {
        val stored = folder(c).listFiles().orEmpty().map { URLDecoder.decode(it.name.removeSuffix(".json"), "UTF-8") }
        return (stored + DEFAULT + current(c)).distinct().sorted()
    }

    private fun snapshot(c: Context): JSONObject {
        val out = JSONObject()
        prefs(c).all.forEach { (k, v) ->
            val type = when (v) {
                is Boolean -> "b"
                is Int -> "i"
                is Long -> "l"
                is Float -> "f"
                is String -> "s"
                is Set<*> -> "t"
                else -> return@forEach
            }
            val value: Any = if (v is Set<*>) JSONArray(v.map { it.toString() }) else if (v is Float) v.toDouble() else v!!
            out.put(k, JSONObject().put("t", type).put("v", value))
        }
        return out
    }

    private fun saveCurrent(c: Context) = file(c, current(c)).writeText(snapshot(c).toString())

    /** A missing or empty file is a configuration at its defaults (PC Reset). */
    private fun load(c: Context, name: String) {
        val e = prefs(c).edit().clear()
        val json = runCatching { JSONObject(file(c, name).readText()) }.getOrDefault(JSONObject())
        json.keys().forEach { k ->
            val item = json.getJSONObject(k)
            when (item.getString("t")) {
                "b" -> e.putBoolean(k, item.getBoolean("v"))
                "i" -> e.putInt(k, item.getInt("v"))
                "l" -> e.putLong(k, item.getLong("v"))
                "f" -> e.putFloat(k, item.getDouble("v").toFloat())
                "s" -> e.putString(k, item.getString("v"))
                "t" -> item.getJSONArray("v").let { a -> e.putStringSet(k, (0 until a.length()).map { a.getString(it) }.toSet()) }
            }
        }
        e.commit()
    }

    private fun restart(f: Fragment) {
        val a = f.requireActivity()
        a.stopService(Intent(a, JS8EngineService::class.java))
        a.recreate()
    }

    /** PC MultiSettings menu: one submenu per configuration, the current one ticked. */
    fun showMenu(f: Fragment, popup: PopupMenu) {
        val c = f.requireContext()
        val cur = current(c)
        names(c).forEachIndexed { i, name ->
            val sub = popup.menu.addSubMenu(0, 100 + i * 10, i, name)
            sub.item.isCheckable = name == cur
            sub.item.isChecked = name == cur
            val base = 100 + i * 10
            if (name != cur) sub.add(0, base + 1, 1, "Switch To").setOnMenuItemClickListener { switchTo(f, name); true }
            sub.add(0, base + 2, 2, "Clone").setOnMenuItemClickListener { clone(f, name); true }
            if (names(c).size > 1) sub.add(0, base + 6, 2, "Clone Into ...").setOnMenuItemClickListener { cloneInto(f, name); true }
            sub.add(0, base + 3, 4, "Rename ...").setOnMenuItemClickListener { rename(f, name); true }
            sub.add(0, base + 4, 3, "Reset").setOnMenuItemClickListener { reset(f, name); true }
            if (name != cur) sub.add(0, base + 5, 5, "Delete").setOnMenuItemClickListener { delete(f, name); true }
        }
        popup.show()
    }

    private fun switchTo(f: Fragment, name: String) {
        val c = f.requireContext()
        saveCurrent(c)
        load(c, name)
        meta(c).edit().putString(KEY_CURRENT, name).commit()
        restart(f)
    }

    private fun askName(f: Fragment, title: String, start: String, onName: (String) -> Unit) {
        val c = f.requireContext()
        val box = EditText(c).apply { setText(start); setSingleLine() }
        val dialog = AlertDialog.Builder(c).setTitle(title).setView(box)
            .setPositiveButton(android.R.string.ok, null).setNegativeButton(android.R.string.cancel, null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = box.text.toString().trim()
                when {
                    name.isEmpty() -> box.error = "Name"
                    name in names(c) -> box.error = "Already in use"
                    else -> { onName(name); dialog.dismiss() }
                }
            }
        }
        dialog.show()
    }

    /** PC MultiSettings clone_configuration: "<name> - Copy", no question. */
    private fun clone(f: Fragment, name: String) {
        val c = f.requireContext()
        if (name == current(c)) saveCurrent(c)
        val source = file(c, name)
        var new = "$name - Copy"
        var n = 1
        while (new in names(c)) new = "$name - Copy (${n++})"
        file(c, new).writeText(if (source.exists()) source.readText() else "{}")
    }

    /** PC clone_into_configuration: this configuration takes the settings of the one picked. */
    private fun cloneInto(f: Fragment, name: String) {
        val c = f.requireContext()
        val sources = names(c).filter { it != name }
        AlertDialog.Builder(c).setTitle("Configuration to Clone From")
            .setItems(sources.toTypedArray()) { _, which ->
                val source = sources[which]
                AlertDialog.Builder(c).setTitle("Clone Into Configuration")
                    .setMessage("Confirm overwrite of all values for configuration \"$name\" with values from \"$source\"?")
                    .setPositiveButton("Yes") { _, _ ->
                        if (source == current(c)) saveCurrent(c)
                        val from = file(c, source)
                        file(c, name).writeText(if (from.exists()) from.readText() else "{}")
                        if (name == current(c)) {
                            load(c, name)
                            restart(f)
                        }
                    }.setNegativeButton("No", null).show()
            }.show()
    }

    private fun rename(f: Fragment, name: String) = askName(f, "New Configuration Name", name) { new ->
        val c = f.requireContext()
        if (name == current(c)) {
            saveCurrent(c)
            meta(c).edit().putString(KEY_CURRENT, new).commit()
        }
        val source = file(c, name)
        file(c, new).writeText(if (source.exists()) source.readText() else "{}")
        source.delete()
    }

    private fun reset(f: Fragment, name: String) {
        val c = f.requireContext()
        AlertDialog.Builder(c).setTitle("Reset Configuration")
            .setMessage("Confirm reset to default values for configuration \"$name\"?")
            .setPositiveButton("Yes") { _, _ ->
                file(c, name).writeText("{}")
                if (name == current(c)) {
                    load(c, name)
                    restart(f)
                }
            }.setNegativeButton("No", null).show()
    }

    private fun delete(f: Fragment, name: String) {
        val c = f.requireContext()
        AlertDialog.Builder(c).setTitle("Delete Configuration")
            .setMessage("Confirm deletion of configuration \"$name\"?")
            .setPositiveButton("Yes") { _, _ -> file(c, name).delete() }
            .setNegativeButton("No", null).show()
    }
}
