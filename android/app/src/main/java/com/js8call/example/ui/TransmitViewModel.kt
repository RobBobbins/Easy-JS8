package com.js8call.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.js8call.example.model.TransmitMessage
import com.js8call.example.model.TransmitState

/**
 * ViewModel for the Transmit screen.
 * Manages message composition and TX queue.
 */
class TransmitViewModel(application: Application) : AndroidViewModel(application) {

    private val _txState = MutableLiveData<TransmitState>(TransmitState.IDLE)
    val txState: LiveData<TransmitState> = _txState

    private val _queue = MutableLiveData<List<TransmitMessage>>(emptyList())
    val queue: LiveData<List<TransmitMessage>> = _queue

    private val _composedMessage = MutableLiveData<String>("")
    val composedMessage: LiveData<String> = _composedMessage

    private val _directedTo = MutableLiveData<String>("")
    val directedTo: LiveData<String> = _directedTo

    private val _txOffsetHz = MutableLiveData<Float>(1500f)
    val txOffsetHz: LiveData<Float> = _txOffsetHz

    private val txQueue = mutableListOf<TransmitMessage>()

    /**
     * Queue a message for transmission.
     * @param clearComposed If true, clears the composed message field (for user-initiated sends)
     */
    fun queueMessage(text: String, directed: String? = null, priority: Int = 0, clearComposed: Boolean = true) {
        if (text.isBlank()) return

        val message = TransmitMessage(
            text = text.trim(),
            directed = directed?.takeIf { it.isNotBlank() },
            priority = priority
        )

        txQueue.add(message)
        txQueue.sortByDescending { it.priority }

        _queue.value = txQueue.toList()
        _txState.value = TransmitState.QUEUED

        // User sends start a new composition; automated queue entries do not.
        if (clearComposed) {
            _composedMessage.value = ""
            _directedTo.value = ""
        }
    }

    /**
     * Set composed message text.
     */
    fun setComposedMessage(text: String) {
        _composedMessage.value = text
    }

    /**
     * Set directed callsign.
     */
    fun setDirectedTo(callsign: String) {
        _directedTo.value = callsign.uppercase()
    }

    /**
     * Set the TX offset frequency in Hz.
     */
    fun setTxOffset(offsetHz: Float) {
        _txOffsetHz.value = offsetHz
    }

    /**
     * Get the current TX offset frequency in Hz.
     */
    fun getTxOffset(): Float {
        return _txOffsetHz.value ?: 1500f
    }

    /**
     * Send CQ.
     */
    fun sendCQ() {
        // CQ Message from Settings (PC default "CQ CQ CQ <MYGRID4>"), macros expanded
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(getApplication())
        val cq = com.js8call.example.js8ui.Js8Macros.expand(
            prefs.getString("js8cfg_cq_message", "CQ CQ CQ <MYGRID4>").orEmpty(), prefs)
        queueMessage(cq.ifBlank { "CQ CQ CQ" }, priority = 1)
    }

    /**
     * Send SNR report to specific station.
     */
    fun sendSnrReport(callsign: String, snr: Int) {
        queueMessage("$callsign SNR $snr", directed = callsign, priority = 2)
    }

    /**
     * Remove message from queue.
     */
    fun removeFromQueue(message: TransmitMessage) {
        txQueue.remove(message)
        _queue.value = txQueue.toList()

        if (txQueue.isEmpty()) {
            _txState.value = TransmitState.IDLE
        }
    }

    /**
     * Clear TX queue.
     */
    fun clearQueue() {
        txQueue.clear()
        _queue.value = emptyList()
        _txState.value = TransmitState.IDLE
    }

    // Easy JS8: the queued message handed to the engine, until it finishes or fails. Between its
    // parts the state is QUEUED, so the state alone let a newly queued reply send it again.
    private var inFlight: TransmitMessage? = null

    // Easy JS8: the engine reported a transmission (ours, or the service's own heartbeat / TUNE)
    private var engineBusy = false

    /** Easy JS8: a message is with the engine; the next one waits until it is finished. */
    fun isBusy(): Boolean = inFlight != null || engineBusy

    fun markSent(message: TransmitMessage) {
        inFlight = message
    }

    /** Easy JS8: engine stopped; nothing is on the air any more. */
    fun engineStopped() {
        inFlight = null
        engineBusy = false
    }

    /**
     * Start transmitting (called when engine starts TX).
     */
    fun startTransmitting() {
        engineBusy = true
        _txState.value = TransmitState.TRANSMITTING
    }

    fun setQueued() {
        engineBusy = true
        _txState.value = TransmitState.QUEUED
    }

    /**
     * Transmission complete (called when engine finishes TX).
     */
    fun transmissionComplete() {
        // Easy JS8: remove the message that was sent, not the first in the queue (a higher-priority
        // reply may be first by now; a heartbeat or TUNE from the service removes nothing)
        inFlight?.let { sent ->
            if (txQueue.removeAll { it === sent }) _queue.value = txQueue.toList()
        }
        inFlight = null
        engineBusy = false

        _txState.value = if (txQueue.isEmpty()) {
            TransmitState.IDLE
        } else {
            TransmitState.QUEUED
        }
    }

    fun transmissionFailed() {
        inFlight = null
        engineBusy = false
        _txState.value = if (txQueue.isEmpty()) {
            TransmitState.IDLE
        } else {
            TransmitState.QUEUED
        }
    }

    /**
     * Get next message to transmit.
     */
    fun getNextMessage(): TransmitMessage? {
        return txQueue.firstOrNull()
    }
}
