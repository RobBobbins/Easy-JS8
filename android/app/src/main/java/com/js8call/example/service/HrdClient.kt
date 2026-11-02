package com.js8call.example.service

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Ham Radio Deluxe over TCP (PC HRDTransceiver), default port 7809. Protocol v5 first (works for
 * v6), then v4. The rig's buttons and dropdowns are found by name, as on the PC. Only the USB and
 * Data modes are used, so the mode lists hold those two. Reversed VFOs are read but, as the app
 * only asks for simple split, rigs needing the Receiver dropdown dance use the plain buttons.
 */
class HrdClient(private val host: String, private val port: Int) : NetworkRig {
    private class HrdError(message: String) : Exception(message)

    private enum class Protocol { NONE, V4, V5 }

    private var socket: Socket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    private var protocol = Protocol.NONE
    private var radios = emptyList<Pair<Int, String>>()
    private var currentRadio = 0
    private var vfoCount = 0
    private var buttons = emptyList<String>()
    private var dropdownNames = emptyList<String>()
    private val dropdowns = mutableMapOf<String, List<String>>()
    private var reversed = false

    private var vfoA = -1
    private var vfoB = -1
    private var vfoToggle = -1
    private var modeA = -1
    private var modeB = -1
    private var modeUsbA = -1
    private var modeDataA = -1
    private var modeUsbB = -1
    private var modeDataB = -1
    private var dataToggle = -1
    private var dataOn = -1
    private var dataOff = -1
    private var dataDropdown = -1
    private var dataDropdownOn = -1
    private var dataDropdownOff = -1
    private var splitButton = -1
    private var splitOffButton = -1
    private var splitDropdown = -1
    private var splitDropdownOn = -1
    private var splitDropdownOff = -1
    private var txA = -1
    private var txB = -1
    private var rxA = -1
    private var rxB = -1
    private var ptt = -1
    private var altPtt = -1

    private fun open(timeoutMs: Int) {
        val s = Socket()
        s.connect(InetSocketAddress(host, port), timeoutMs)
        s.soTimeout = 3000
        socket = s
        input = s.getInputStream()
        output = s.getOutputStream()
    }

    @Synchronized
    override fun connect(timeoutMs: Int): Boolean {
        disconnect()
        return try {
            open(timeoutMs)
            protocol = Protocol.V5
            try {
                send("get context", context = false)
            } catch (_: Exception) {
                disconnect()
                open(timeoutMs)
                protocol = Protocol.V4
                send("get context", context = false)
            }
            start()
            true
        } catch (e: Exception) {
            android.util.Log.e("HrdClient", "Failed to connect to $host:$port - ${e.message}")
            disconnect()
            false
        }
    }

    /** PC do_start. */
    private fun start() {
        val list = send("get radios", context = false).trim().split(',').filter { it.isNotEmpty() }
        if (list.isEmpty()) throw HrdError("Ham Radio Deluxe: no rig found")
        radios = list.map { r -> r.trim().split(':').let { it[0].toInt() to it[1] } }
        val currentName = send("get radio", context = false, recurse = true)
        if (currentName.isEmpty()) throw HrdError("Ham Radio Deluxe: no rig found")
        vfoCount = send("get vfo-count").toIntOrNull() ?: 0
        buttons = send("get buttons").trim().split(',').filter { it.isNotEmpty() }.map { it.replace(' ', '~') }
        dropdownNames = send("get dropdowns").trim().split(',').filter { it.isNotEmpty() }
        dropdowns.clear()
        dropdownNames.forEach { dropdowns[it] = send("get dropdown-list {$it}").trim().split(',') }

        rxA = findButton("^(RX~A)$")
        rxB = findButton("^(RX~B)$")
        vfoA = findButton("^(VFO~A|Main)$")
        vfoB = findButton("^(VFO~B|Sub)$")
        vfoToggle = findButton("^(A~/~B)$")
        splitButton = findButton("^(Spl~On|Spl_On|Split|Split~On)$")
        splitOffButton = findButton("^(Spl~Off|Spl_Off|Split~Off)$")
        splitDropdown = findDropdown("^(Split)$")
        if (splitDropdown >= 0) {
            splitDropdownOn = findSelection(splitDropdown, "^(On)$")
            splitDropdownOff = findSelection(splitDropdown, "^(Off)$")
        }
        txA = findButton("^(TX~main|TX~-~A|TX~A)$")
        txB = findButton("^(TX~sub|TX~-~B|TX~B)$")
        modeA = findDropdown("^(Main Mode|Mode|Mode A)$")
        if (modeA >= 0) {
            modeUsbA = findSelection(modeA, USB)
            modeDataA = findSelection(modeA, DIG_U)
        }
        modeB = findDropdown("^(Sub Mode|Mode B)$")
        if (modeB >= 0) {
            modeUsbB = findSelection(modeB, USB)
            modeDataB = findSelection(modeB, DIG_U)
        }
        // Some Kenwood rigs have a "Data" button for their DSP, so by rig name as well
        if (currentName.startsWith("IC")) dataToggle = findButton("^(Data)$")
        dataOn = findButton("^(DATA-ON\\(mid\\))$")
        dataOff = findButton("^(DATA-OFF)$")
        dataDropdown = findDropdown("^(Data)$")
        if (dataDropdown >= 0) {
            dataDropdownOn = findSelection(dataDropdown, "^(On|Data1|D1|D1-FIL1)$")
            dataDropdownOff = findSelection(dataDropdown, "^(Off)$")
        }
        ptt = findButton("^(TX)$")
        altPtt = findButton("^(TX~Alt|TX~Data)$")
    }

    private fun findButton(re: String) = buttons.indexOfFirst { Regex(re).containsMatchIn(it) }
    private fun findDropdown(re: String) = dropdownNames.indexOfFirst { Regex(re).containsMatchIn(it) }

    /** Searches from the end: more specialised modes tend to be later in the list (PC find_dropdown_selection). */
    private fun findSelection(dropdown: Int, re: String) =
        dropdowns[dropdownNames[dropdown]].orEmpty().indexOfLast { Regex(re).containsMatchIn(it) }

    @Synchronized
    override fun disconnect() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        input = null
        output = null
        protocol = Protocol.NONE
        currentRadio = 0
        vfoCount = 0
    }

    /** PC send_command. Throws when the link or the answer is wrong. */
    private fun send(cmd: String, context: Boolean = true, recurse: Boolean = false): String {
        if (currentRadio != 0 && context && vfoCount < 2) Thread.sleep(50) // some radios (Icom) need the gap
        if (!recurse && context) {
            val name = send("get radio", context = currentRadio != 0, recurse = true)
            currentRadio = radios.firstOrNull { it.second == name }?.first
                ?: throw HrdError("Ham Radio Deluxe: rig has disappeared or changed")
        }
        val text = if (context) "[$currentRadio] $cmd" else cmd
        val out = output ?: throw HrdError("Ham Radio Deluxe: not connected")
        val stream = input ?: throw HrdError("Ham Radio Deluxe: not connected")
        if (protocol == Protocol.V4) {
            out.write("$text\r".toByteArray(Charsets.ISO_8859_1))
            out.flush()
            val buffer = ByteArray(4096)
            val n = stream.read(buffer)
            if (n <= 0) throw HrdError("Ham Radio Deluxe: no reply")
            return String(buffer, 0, n, Charsets.ISO_8859_1).trim()
        }
        val payload = (text + "\u0000").toByteArray(Charsets.UTF_16LE)
        val message = ByteBuffer.allocate(16 + payload.size).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(16 + payload.size).putInt(MAGIC_1).putInt(MAGIC_2).putInt(0).put(payload)
        out.write(message.array())
        out.flush()

        val reply = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        var size = Int.MAX_VALUE
        while (reply.size() < size) {
            val n = stream.read(buffer)
            if (n <= 0) throw HrdError("Ham Radio Deluxe: no reply")
            reply.write(buffer, 0, n)
            if (size == Int.MAX_VALUE && reply.size() >= 16) {
                val head = ByteBuffer.wrap(reply.toByteArray()).order(ByteOrder.LITTLE_ENDIAN)
                size = head.getInt(0)
                if (head.getInt(4) != MAGIC_1 && head.getInt(8) != MAGIC_2) {
                    throw HrdError("Ham Radio Deluxe sent an invalid reply to our command \"$cmd\"")
                }
            }
        }
        return String(reply.toByteArray(), 16, size - 16, Charsets.UTF_16LE).substringBefore('\u0000')
    }

    private fun simple(cmd: String) {
        if (send(cmd) != "OK") throw HrdError("Ham Radio Deluxe didn't respond to command \"$cmd\" as expected")
    }

    private fun setButton(index: Int, checked: Boolean = true) {
        if (index < 0) throw HrdError("Ham Radio Deluxe: button not available")
        simple("set button-select ${buttons[index]} ${if (checked) 1 else 0}")
    }

    private fun isChecked(index: Int) = index >= 0 && send("get button-select ${buttons[index]}") == "1"

    private fun setDropdown(dd: Int, value: Int) {
        if (dd < 0 || value < 0) throw HrdError("Ham Radio Deluxe: item not found in dropdown list")
        val name = dropdownNames[dd]
        simple("set dropdown ${name.replace(' ', '~')} ${dropdowns[name].orEmpty()[value].replace(' ', '~')} $value")
    }

    private fun getDropdown(dd: Int): Int {
        if (dd < 0) return -1
        val name = dropdownNames[dd]
        val reply = send("get dropdown-text {$name}")
        if (':' !in reply) return -1
        return dropdowns[name].orEmpty().indexOf(reply.substringAfter(':').trim())
    }

    /** "1234-5678": VFO A, VFO B. */
    private fun frequencies() = send("get frequencies").trim().split('-').filter { it.isNotEmpty() }.map { it.toLong() }

    private fun attempt(block: () -> Unit): Boolean = try {
        block()
        true
    } catch (e: HrdError) {
        android.util.Log.w("HrdClient", e.message.orEmpty())
        false
    } catch (e: java.io.IOException) {
        android.util.Log.w("HrdClient", e.message.orEmpty())
        disconnect()
        false
    }

    @Synchronized
    override fun setFrequency(frequencyHz: Long) = attempt {
        if (vfoCount > 1 && reversed) simple("set frequencies-hz ${frequencies()[0]} $frequencyHz")
        else simple("set frequency-hz $frequencyHz")
    }

    @Synchronized
    override fun getFrequency(): Long = try {
        if (vfoCount > 1) frequencies()[if (reversed) 1 else 0] else send("get frequency").toLongOrNull() ?: 0L
    } catch (_: Exception) {
        0L
    }

    private fun setDataMode(data: Boolean) {
        when {
            dataToggle >= 0 -> setButton(dataToggle, data)
            dataOn >= 0 && dataOff >= 0 -> setButton(if (data) dataOn else dataOff)
            dataDropdown >= 0 && dataDropdownOn >= 0 && dataDropdownOff >= 0 ->
                setDropdown(dataDropdown, if (data) dataDropdownOn else dataDropdownOff)
        }
    }

    /** Sets the other VFO's mode (PC do_tx_frequency): its own dropdown, or switching over to it and back. */
    private fun setTxVfoMode(mode: String) {
        val data = mode == "PKTUSB"
        if (modeB >= 0) {
            setDropdown(modeB, if (data) modeDataB else modeUsbB)
        } else if (vfoA >= 0 && vfoB >= 0 || vfoToggle >= 0) {
            setButton(if (vfoB >= 0) vfoB else vfoToggle)
            setDropdown(modeA, if (data) modeDataA else modeUsbA)
            setDataMode(data)
            setButton(if (vfoA >= 0) vfoA else vfoToggle)
        }
    }

    @Synchronized
    override fun setSplit(txFrequencyHz: Long, enabled: Boolean, mode: String?) = attempt {
        if (enabled) {
            mode?.let { setTxVfoMode(it) }
            if (vfoCount > 1) {
                simple("set frequencies-hz ${frequencies()[0]} $txFrequencyHz")
            } else if (vfoB >= 0 && vfoA >= 0 || vfoToggle >= 0) {
                setButton(if (vfoB >= 0) vfoB else vfoToggle)
                simple("set frequency-hz $txFrequencyHz")
                setButton(if (vfoA >= 0) vfoA else vfoToggle)
            }
        }
        when {
            splitButton >= 0 -> if (splitOffButton >= 0 && !enabled) setButton(splitOffButton) else setButton(splitButton, enabled)
            splitDropdown >= 0 && splitDropdownOn >= 0 && splitDropdownOff >= 0 ->
                setDropdown(splitDropdown, if (enabled) splitDropdownOn else splitDropdownOff)
            vfoA >= 0 && vfoB >= 0 && txA >= 0 && txB >= 0 -> {
                setButton(if (rxA >= 0) rxA else vfoA)
                setButton(if (enabled) txB else txA)
            }
        }
    }

    @Synchronized
    override fun setPtt(enabled: Boolean, rearData: Boolean) = attempt {
        // PC do_ptt: a rig interface with no PTT button is not even tried
        if (altPtt >= 0 && rearData) setButton(altPtt, enabled)
        else if (ptt >= 0) setButton(ptt, enabled)
    }

    @Synchronized
    override fun setMode(mode: String) = attempt {
        val data = mode == "PKTUSB"
        if (reversed && modeB >= 0) setDropdown(modeB, if (data) modeDataB else modeUsbB)
        else setDropdown(modeA, if (data) modeDataA else modeUsbA)
        setDataMode(data)
    }

    @Synchronized
    override fun readState(): NetworkRig.State = try {
        var split: Boolean? = null
        when {
            splitOffButton >= 0 -> {} // probably an Icom, split can't be read
            splitButton >= 0 && !(txA >= 0 && txB >= 0) -> split = isChecked(splitButton)
            splitDropdown >= 0 -> getDropdown(splitDropdown).let { if (it >= 0 && splitDropdownOff >= 0) split = it == splitDropdownOn }
            vfoA >= 0 && vfoB >= 0 && txA >= 0 && txB >= 0 -> {
                val rxOnA = isChecked(if (rxA >= 0) rxA else vfoA)
                val rxOnB = !rxOnA && isChecked(if (rxB >= 0) rxB else vfoB)
                split = rxOnB == isChecked(txA)
                reversed = rxOnB
            }
        }
        val selection = getDropdown(modeA)
        val mode = when {
            selection < 0 -> null
            selection == modeUsbA && getDropdown(dataDropdown).let { it >= 0 && it != dataDropdownOff } -> "PKTUSB"
            selection == modeUsbA -> "USB"
            selection == modeDataA -> "PKTUSB"
            else -> dropdowns[dropdownNames[modeA]]?.getOrNull(selection)
        }
        val tx = if (split == true && vfoCount > 1) frequencies()[if (reversed) 0 else 1] else null
        NetworkRig.State(split = split, txHz = tx, mode = mode)
    } catch (e: Exception) {
        android.util.Log.w("HrdClient", "poll failed: ${e.message}")
        NetworkRig.State()
    }

    companion object {
        private const val MAGIC_1 = 0x1234ABCD
        private const val MAGIC_2 = 0xABCD1234.toInt()
        private const val USB = "^(USB\\(N\\)|USB)$"
        private const val DIG_U = "^(DIG|DIGU|DATA-U|PKT-U|DATA|USER-U|USB)$"
    }
}
