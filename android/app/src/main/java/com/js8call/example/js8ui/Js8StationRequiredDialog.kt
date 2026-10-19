package com.js8call.example.js8ui

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment

/**
 * "Enter callsign and gridsquare in the settings" (UI_Constructor::ensureCallsignSet).
 * OK, back or a tap outside opens Station (where callsign and grid are). A DialogFragment,
 * so it is still showing after the phone turns.
 */
class Js8StationRequiredDialog : DialogFragment() {

    companion object {
        const val TAG = "js8_station_required"
        const val MESSAGE = "Enter callsign and gridsquare in the settings"
    }

    /** Set by OK / cancel only; onDismiss also runs when the phone turns. */
    private var openSettings = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        AlertDialog.Builder(requireContext())
            .setMessage(MESSAGE)
            .setPositiveButton(android.R.string.ok) { _, _ -> openSettings = true }
            .create()

    override fun onCancel(dialog: DialogInterface) {
        openSettings = true
        super.onCancel(dialog)
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        if (openSettings) {
            openSettings = false
            (parentFragment as? Js8MainFragment)?.openStation()
        }
    }
}
