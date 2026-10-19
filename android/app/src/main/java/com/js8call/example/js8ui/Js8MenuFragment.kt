package com.js8call.example.js8ui

import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.js8call.example.R

/**
 * Menu page opened from the ⋮ button: the desktop menu bar
 * (File, Configurations, Mode, Log, View, Control, Help) as a full page.
 * Items without an Android screen yet open the existing JS8Android screens.
 */
class Js8MenuFragment : Fragment() {

    private data class Item(val label: String, val action: (() -> Unit)?)

    private fun activityVm() =
        androidx.lifecycle.ViewModelProvider(requireActivity())[Js8ActivityViewModel::class.java]

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val dp = resources.displayMetrics.density
        val font = ResourcesCompat.getFont(ctx, R.font.selawik)
        val textColor = ContextCompat.getColor(ctx, R.color.js8_frame_text)
        val nav = findNavController()

        val menus = linkedMapOf(
            // File > Station…: the PC's File > Settings window (Rob, 2026-10-19)
            "File" to listOf(
                Item("Station…") { nav.navigate(R.id.navigation_js8_station) },
                Item("Exit") { requireActivity().finish() }
            ),
            // Configurations, Mode and View > Show Band Heartbeats and ACKs moved to the
            // Settings window (Rob, 2026-10-19)
            "Log" to listOf(Item("Messages (JS8Android)") { nav.navigate(R.id.navigation_messages) }),
            "View" to listOf(
                Item("Clear Band Activity") { activityVm().activity.clearBand(); activityVm().changed(); nav.popBackStack() },
                Item("Clear Call Activity") { activityVm().activity.clearCalls(); activityVm().changed(); nav.popBackStack() },
                Item("Clear RX Activity") { activityVm().activity.clearRx(); activityVm().changed(); nav.popBackStack() },
                Item("Clear All Activity") {
                    activityVm().activity.clearBand(); activityVm().activity.clearCalls(); activityVm().activity.clearRx()
                    activityVm().changed(); nav.popBackStack()
                },
                Item("Decoded messages (JS8Android)") { nav.navigate(R.id.navigation_monitor) }
            ),
            "Control" to listOf(Item("Transmit queue (JS8Android)") { nav.navigate(R.id.navigation_transmit) }),
            "Help" to listOf(Item("About", null))
        )

        val column = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt())
        }
        fun label(text: String, bold: Boolean, indent: Int) = TextView(ctx).apply {
            this.text = text
            typeface = font
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.js8_text) * (if (bold) 1.15f else 1f))
            setTextColor(textColor)
            gravity = Gravity.CENTER_VERTICAL
            minHeight = (40 * dp).toInt()
            setPadding((indent * dp).toInt(), 0, 0, 0)
        }
        column.addView(label("← Back", true, 0).apply { setOnClickListener { nav.popBackStack() } })
        menus.forEach { (title, items) ->
            column.addView(label(title, true, 0))
            items.forEach { item ->
                column.addView(label(item.label, false, 20).apply {
                    if (item.action != null) setOnClickListener { item.action.invoke() } else alpha = 0.5f
                })
            }
        }
        return ScrollView(ctx).apply {
            setBackgroundColor(ContextCompat.getColor(ctx, R.color.js8_frame_bg))
            addView(column)
        }
    }
}
