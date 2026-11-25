package com.js8call.example.js8ui

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.js8call.example.R

/**
 * Map screen (PC and FT8Call menu bar > Map, Rob 2026-11-24). The same map, stations and map options as the
 * Pages Map page; its own zoom. MainActivity keeps feeding the stations while this screen is open.
 */
class Js8MapFragment : Fragment() {

    private val prefs: SharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(requireContext()) }
    private var map: Js8MapView? = null

    private val freqListener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
        if (key == "last_frequency") map?.dialHz = dialHz(p)
    }

    private fun dialHz(p: SharedPreferences) = p.getString("last_frequency", "14078000")?.toLongOrNull() ?: 14_078_000L

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_js8_map, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val vm = ViewModelProvider(requireActivity())[Js8ActivityViewModel::class.java]
        val m = view.findViewById<Js8MapView>(R.id.js8_map)
        map = m
        m.activity = vm.activity
        m.dialHz = dialHz(prefs)
        view.findViewById<View>(R.id.js8_map_back).setOnClickListener { findNavController().popBackStack() }
        view.findViewById<View>(R.id.js8_map_zoom_in).setOnClickListener { m.zoomBy(2.0) }
        view.findViewById<View>(R.id.js8_map_zoom_out).setOnClickListener { m.zoomBy(0.5) }
        view.findViewById<View>(R.id.js8_map_fit).setOnClickListener { m.fitAll() }
        view.findViewById<View>(R.id.js8_map_options).setOnClickListener { Js8MapOptions.show(it, m) }
        vm.version.observe(viewLifecycleOwner) { m.invalidate() }
        prefs.registerOnSharedPreferenceChangeListener(freqListener)
    }

    override fun onDestroyView() {
        prefs.unregisterOnSharedPreferenceChangeListener(freqListener)
        map = null
        super.onDestroyView()
    }
}
