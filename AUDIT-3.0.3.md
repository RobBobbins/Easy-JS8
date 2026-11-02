# Easy JS8 audit against JS8Call 3.0.3

Date: 2026-10-31. App state: commit `329260bc`, branch `easy-js8`.
PC source: tag `v3.0.3` of JS8Call-improved, commit `4c592bd`.

**Result: Easy JS8 does not yet include every JS8Call 3.0.3 feature.** Roughly 170 differences.

How this was checked:
- A read-only comparison of the two sources. Nothing was built or run.
- Items marked **(read)** were read in both sources by the main session.
- Every other item comes from one of four background checks and was not re-checked one by one.
- Items marked UNKNOWN were not settled.

Short names for source files:
- PC: `mw` = `JS8_UI/mainwindow.cpp`, `UC` = `JS8_Mainwindow/UI_Constructor.cpp`, `BQ` = `JS8_Mainwindow/buildQueryMenu.cpp`, `pCA` = `JS8_Mainwindow/processCommandActivity.cpp`, `pDE` = `JS8_Mainwindow/processDecodeEvent.cpp`, `pBA` = `JS8_Mainwindow/processBufferedActivity.cpp`, `ui` = `JS8_UI/Configuration.ui`, `cpp` = `JS8_UI/Configuration.cpp`
- App, under `android/app/src/main/java/com/js8call/example`: `MF` = `js8ui/Js8MainFragment.kt`, `JES` = `service/JS8EngineService.kt`, `Act` = `js8ui/Js8Activity.kt`, `Schema` = `js8ui/Js8SettingsSchema.kt`, `Frag` = `js8ui/Js8SettingsFragment.kt`, `Cfg` = `js8ui/Js8Configurations.kt`

---

## 1. Serious: decoding and on-air behaviour

### Decoder
- [x] **Soft combining missing (read).** The PC adds a repeated frame's soft bits to the earlier copy, on by default. PC `JS8_Mode/JS8.cpp` 1526-1530, 1591. Nothing in `core/`.
- [x] **LDPC feedback retry missing (read).** PC `JS8.cpp` 1625-1644, on by default.
- [x] **Whitening and LLR erasure threshold missing (read).** PC `JS8.cpp` 1473-1482.
- [x] **Frequency and timing tracker missing (read).** PC `JS8.cpp` 1222, 1276-1285.
- [x] **Waterfall filter never reaches the decoder (read).** The PC sets the decoder range from the filter, 0-5000 Hz with the filter off (`mw` 2099-2102). App is fixed at 200-2500 Hz (`core/src/engine/engine.cpp` 65-66). Nothing above 2500 Hz decodes.
- [x] **Decode priority fixed at 1500 Hz (read).** The PC uses your current offset (`mw` 2098). App `engine.cpp` 67.

### Directed commands and auto replies
- [x] **QUERY CALL not answered (read).** PC answers "CALL YES -12 (5m)", `pCA` 1053-1102.
- [x] **Queries to my @GROUP not answered:** SNR?, INFO?, STATUS?, GRID?, HEARING?. `pCA` 333-374, `JES` 4365.
- [x] **No 15-minute limit** on @ALLCALL and heartbeat replies to one station. `pCA` 306-311, 722, 1149.
- [x] **Replies go out while text is in the Typing Box.** `pCA` 1135. App `MainActivity.kt` 471-512.
- [x] **Replies go out while a message to me is still arriving.** `pCA` 1141.
- [x] **HEARING?:** the PC uses Call Activity and **Remove callsigns from call activity after:** and always replies. The app uses a separate 15-minute list and stays silent when empty. `pCA` 374-406, `JES` 4199-4204, 5534.
- [x] **QUERY MSGS to @HB ignored.** PC treats @HB as all-call, `mw` 6088. `JES` 3783, 4045.
- [x] **@APRSIS "MSG" with "TO:" in the text** dropped; only the "MSG TO:" form is handled. `pCA` 765-810, `JES` 3812.

### Checksums and buffered messages
- [x] **No checksum check on MSG, MSG TO:, QUERY (read).** The PC discards bad ones, `pBA` 65-95. App strips any last 3-character word, `JES` 3826-3827, 3936, 3960. Corrupt messages reach the Inbox and get an ACK.
- [x] **Same stripping on @APRSIS MSG TO:**, which has no checksum. A real 3-character last word is cut off. `JES` 4092.
- [x] **Relay with a bad checksum forwarded** if the payload was inline. `JES` 4680-4682.
- [x] **Commands answered on the first frame.** The PC buffers every space-prefixed command until the last frame. `Varicode.cpp` 1239, `pDE` 469.
- [x] **Buffer time:** PC closes at 60 s, drops at 90 s (`pBA` 33-41). App 4 frame periods, 24 s at JS8 40, 16 s at JS8 60 (`JES` 3849).
- [x] **Match range:** PC uses rxThreshold per speed. App fixed 10 Hz, `JES` 66, 5537. Fast, JS8 40 and JS8 60 can lose frames of a drifting signal.
- [x] **"First frame clears an old buffer" missing** for data frames. `pDE` 319-328.

### Relay (A>B>C)
- [x] **Relay ignores AUTO, TX off and Idle Timeout, and skips Autoreply Confirmation (read).** `JES` 4870-4892. PC `pCA` 1157 and processTxQueue.
- [x] **Relay heard while transmitting is dropped (read).** PC queues it. `JES` 4757, 4876.
- [x] **Relayed MSG, MSG TO:, QUERY, QUERY MSGS, QUERY CALL not handled.** `pCA` 459-513, `JES` 4764-4799. They get an ACK only; a relayed message never reaches the Inbox.
- [x] **MSG with a DE path:** the PC stores the path and ACKs along it. App ACKs the last sender only. `pCA` 814-828, `JES` 3830, 3835.
- [x] **Relay to my @GROUP ignored.** `pCA` 434, `JES` 4235.
- Relayed SNR?: the PC sends +00 by a quirk, the app sends the real SNR. App is better; left as a note.

### Heartbeat
- [x] **ACK sent in JS8 40 and JS8 60.** PC sends none, `pCA` 636, `JES` 4153.
- [x] **Pause heartbeat transmissions while in a QSO** does not block ACKs with a callsign selected. `pCA` 649.
- [x] **Selecting a callsign does not stop the HB and CQ timers;** the PC restores HB on deselect. `mw` 5958, 5980-5995.
- [x] **ACK sent while a message buffer is open.** `pCA` 642.
- [x] **Group held-message ID missing on the ACK.** `pCA` 669, 694, `JES` 4161-4168.
- [x] **ACK text:** the PC sends "MSG ID 5 +2)" with a stray ")". The app has no bracket. `pCA` 679.
- [x] **Offset:** PC findFreeFreqOffset avoids busy offsets. App is plain random 500-1000 Hz. `mw` 3705, `JES` 3098.
- [x] **HB with my offset at or below 1000 Hz:** PC uses my offset, app is random. `mw` 4444.
- [x] **Timed HB while transmitting:** PC queues, app skips to the next interval. `mw` 4450, `JES` 3110.
- [x] **Timed HB bypasses the queue,** ignoring TX off and a full Typing Box. `JES` 3162.
- Rate limit runs before the Never acknowledge check, `JES` 4155. Harmless.

### TX queue, CQ and held messages
- [x] **CQ timer does not stop when a message is shown.** `processRxActivity.cpp` 132, `pCA` 269.
- [x] **Autoreply Confirmation > No still marks a held message collected.** `JES` 4126-4129. PC `confirmThenEnqueueMessage.cpp`.
- [x] **Priorities:** PC replies 100, HB and HB ACK 11, APRS relay 1000. App 1 or 2. `JES` 4171, 3835.
- [x] **30 m WSPR guard band (10139.9-10140.32 kHz) refusal missing.** `mw` 2469-2500.
- [x] **Stuck-key check missing:** box "Message Not Sent". `mw` 3375, 3425.

---

## 2. Bugs in existing features

- [x] **Clone Into ... runs backwards (read).** On the PC the submenu is the TARGET and you pick the SOURCE ("Configuration to Clone From", `MultiSettings.cpp` 121, 620). In the app the submenu is the SOURCE. `Cfg` 137-155.
- [x] **Data Bits > Default and Stop Bits > Default force 8 and 1.** The PC leaves Hamlib's own value. `HamlibTransceiver.cpp` 311, 317; `Schema` 403, 407; `JES` 1750-1751; `hamlib_rig_control_jni.cpp` 212-224.
- [x] **Halt leaves the HB and CQ timers running.** `mw` 5266, `MF` 1244, `JES` 489.
- [x] **Restore Previous Message transmits at once** when Immediately transmit is on. PC only fills the box. `MF` 1581, `mw` 3491.
- [x] **Directed > Reply and the three QUERY items without [ ] send at once.** PC never sends them from that menu. `MF` 1344, `BQ` 38, 272.
- [x] **CQ, REPLY, SNR, INFO, STATUS, Saved and double-click on a call replace the Typing Box text.** PC adds after a space. `MF` 1104, `mainwindow.h` 264.
- [x] **TX off does not halt or grey Send.** `mw` 1191, 5752, `MF` 950, `MainActivity.kt` 487.
- [x] **RX button while transmitting stops RX.** PC refuses. `mw` 1145, `MF` 946.
- [x] **Double-tap a Band Activity row skips the offset lock.** `MF` 558-562, `mw` 5030, 5381.
- [x] **Sort By Mode Speed puts Slow between 40 and 60.** `displayBandActivity.cpp` 101, `MF` 793, 841.
- [x] **Deselect and CQ keep the selection start time,** so a later Log Start can be old. `mw` 6020, `MF` 1273.
- [x] **INFO button needs a selection; STATUS not greyed when empty.** `mw` 5692, `MF` 2135.
- [x] **REPLY, SNR, INFO, STATUS, Saved, Directed, Deselect stay live while a message is queued.** App greys only HB and CQ. `mw` 5674, `MF` 2245.
- [x] **Directed menu has an extra item** "CALL|[MESSAGE] - Please ACK and retransmit...", compiled out on the PC. `BQ` 197, `MF` 1366.
- [x] **Directed menu labels** carry the callsign; the PC's do not. `BQ` 14.
- [x] **Directed menu greys nothing.** PC greys SNR unless heard, INFO and GRID when empty, query, > and MSG on @ALLCALL. `BQ` 53-245, `MF` 1338.
- [x] **Log... on a @GROUP** is greyed; PC opens it blank. `UC` 1285, `MF` 1426.

---

## 3. Wrong defaults

- [x] **Write log files (ALL.TXT, DIRECTED.TXT, etc) of decoded text:** PC on, app off. `ui` 415, `cpp` 2302, `Schema` 338.
- [x] **Turn autoreply on at startup:** PC on, app off. `ui` 797, `cpp` 2288, `Schema` 366. Downside: new installs answer on air by themselves.
- [x] **Enable spotting to reporting networks (JS8NET, PSKReporter, etc):** PC on, app off. `ui` 2336, `cpp` 2299, `Schema` 463. Downside: new installs send to real networks unless changed.
- [x] **Enable spotting @APRSIS messages to the APRS-IS network:** PC on, app off. `ui` 2290, `cpp` 2300, `Schema` 465. Same downside.
- [x] **Baud Rate:** PC 4800, app 9600. `ui` 1137, `cpp` 2365, `Schema` 400.
- [x] **Transmit Audio Source:** PC Front/Mic, app Rear/Data. `ui` 1677, `cpp` 2393, `Schema` 428, `JES` 5073.

---

## 4. Macros

- [x] `<CALL>`, `<SNR>`, `<TDELTA>`, `<MYHB>` go out as typed **(read)**. `mw` 4848-4885, `js8ui/Js8Macros.kt` 25-42.
- [x] `<MYVERSION>` sends EASYJS8-x; PC sends the plain version number **(read)**. `mw` 4852. Not listed as deliberate.
- [x] Values not upper-cased, leftover `<...>` not removed. `mainwindow.h` 789.

---

## 5. Main window and menus

### Missing
- [x] Clock drift suffix "(+123ms)" in the UTC clock. `mw` 2685, `MF` 184.
- [x] Status bar configuration name when not Default. `UC` 521, `mw` 1414.
- [x] **Send** text changes: Send with duration, Ready, Sending, Tuning. `mw` 5858.
- [x] **Directed** text "Directed to CALL". `mw` 5699.
- [x] Typing Box hint "TYPE YOUR OUTGOING DIRECTED MESSAGE TO CALL HERE." `mw` 5958, `js8_typing_box.xml` 9.
- [x] Click the frequency display: buildFrequencyMenu with **Set a Custom Frequency...** `UC` 445, `mw` 4264, `MF` 219.
- [x] Click the callsign label opens Settings. `UC` 495, `MF` 227.
- [x] Double-click a word in the Message Window goes into the focused Log QSO field. `UC` 743, `LogQSO.cpp` 46. Seen: double tap into the focused field. The Log page gained a Message Window box for this. Not run: Start/End word exclusion, and Comments as the focused field.
- [x] Text selected in the Message Window fills Log QSO **Comments**. `mw` 3879, `MF` 1720.
- [x] **Rig Control Error** box with Retry, Configurations..., OK, Cancel. `mw` 5493.
- [x] Status bar short messages "Spotting to PSK Reporter unavailable" and "Enumerating audio devices". `mw` 6783, `UC` 518. Seen: both, as a short message at the bottom of the screen.
- [x] **Open Log Directory...** `mw` 4244.
- [x] **Show Fullscreen.** `mw` 862.
- [x] **Show Time Drift Controls...** `mw` 989.
- [x] Call Activity HEARING and HEARD BY details. `mw` 5070.

### Partial
- [x] **Clear All Lists:** PC asks "Are you sure you would like to clear all activity?". App never asks, label is "Clear All" in three menus, and two of them leave the last message. `UC` 784, `MF` 1471, 1564, 1605, 1633, `Frag` 1233.
- [x] **Clear** on the Message Window and **Clear RX Activity:** PC also empties and unlocks the Typing Box. `mw` 3001, `MF` 1639, `Frag` 1232.
- [x] Message Window menu: PC Cut, Copy, Paste act on the selection. App has one Copy of the whole window. `mw` 4823, `UC` 836, `MF` 1631.
- [x] Minimal Column Labels: PC also drops units in cells. `mw` 4890, `Js8TableView.kt` 259.
- [x] Show Column Labels off drops distance units on the PC. `displayCallActivity.cpp` 311, `MF` 824.
- [x] CAT button: PC has ok, warning and error colours, disabled when ok, "CAT/S" in split, ignores clicks while transmitting. `mw` 5290, 5481, `MF` 977, 2236.
- [x] Signal meter: PC red, yellow, green, white peak hold, number. App green bar only. `SignalMeter.cpp` 75-98, `js8ui/Js8Meters.kt` 15.
- [x] Power slider in PC, FT8Call and Pages layouts is a drawing: no touch, value never set. **Settings > Power** works. `Js8Meters.kt` 161.
- [x] Status label: PC "Tx: <message>", "Tx: TUNE", "Decoding", "Idle timeout". App Transmitting, Receiving, Idle. `mw` 2739, 2167, `MF` 2248.
- [x] **Show Message Inbox...** from Call Activity: PC filters to that call, title "Messages: CALL". `UC` 1161, `MF` 1149.
- [x] Inbox list: PC sorts newest first; multi-row delete absent. `MessagePanel.cpp` 63, `Js8Inbox.kt` 14. Seen upright, Inbox page and the Message Inbox screen. A saved sort from a header tap overrides newest first, as the PC.
- [x] Inbox From: PC reverses the path, joined with " via ". `MessagePanel.cpp` 18.
- [x] Inbox Frequency and Date: PC dial "x MHz" and "ddd MMM d HH:mm:ss yyyy". App offset Hz and yyyy-MM-dd HH:mm. `MessagePanel.cpp` 133-146. Seen. Rows stored before this change show offset Hz.
- [x] Menu bar **Mode** opens Settings; PC opens the menu. The header mode button does open it. `MF` 1001, 983.
- [x] Menu bar **Log** opens the page directly. PC has Add Log Entry, Open Log Directory, Erase ALL.TXT, Erase js8call_log.adi. `MF` 1037.
- [x] Menu bar **Control** opens Settings. `MF` 1001.
- [x] Check for Updates, Copyright notice, User Guide only on the PC and FT8Call menu bars. `MF` 1038.
- [x] View > Show panels only in PC and FT8Call. `MF` 1014. Seen: Show Waterfall and Show Statusbar in PC and FT8Call menus, panels upright and Pages.
- [x] Press and hold on an empty table: PC menu opens anyway. **Add New Station or Group...** is unreachable with no rows. `UC` 949, 1222, `Js8TableView.kt` 408.
- [x] **Store Message...** box: PC filters to upper-case printable 7-bit, multi-line, disabled on @ALLCALL. `MessageReplyDialog.cpp` 39, `UC` 1292, `MF` 1734.
- [x] Saved menu: PC shows macros expanded and **Edit Saved Messages** opens the Saved Messages page. `mw` 4966, `MF` 1291, 1304.
- [x] Typing ahead while sending: PC lets you edit the unsent part. `mw` 3598, `TransmitTextEdit.cpp` 579, `MF` 617. Seen: edit taken, locked on the last part. Typing ahead closes when a part ends, 1 to 2 s before the next slot.

### Names that differ
PC name first, app name second:
- [x] **Settings...** / Station… Stays, Rob 2026-10-19.
- [x] **Show Message Inbox...** / Inbox
- [x] **Sort Band Activity By...** / Sort Band Activity...
- [x] **Reset Frame Sizes** / Reset Window Sizes
- [x] **About JS8Call** / About Stays, opens the Easy JS8 About.
- [x] **JS8Call User Guide** / User Guide
- [x] Column **Speed** / Mode Speed
- [x] Column **°** / Azimuth
- [x] Column **★** / blank

---

## 6. Call Activity and Band Activity

- [x] Buffered GRID does not fill the Grid column. `pCA` 87-115, `Act` 285.
- [x] Third-party calls with the ⚟ icon missing. `pCA` 440-451, `displayCallActivity.cpp` 240.
- [x] ⚑ on group rows with messages missing. `mw` 3080, `MF` 808-809.
- [x] Low-confidence "[text]" missing in Band Activity. `displayBandActivity.cpp` 202. The service already knows quality < 0.17 (`JES` 2054).
- [x] Per-band memory missing: the PC saves lists, Message Window and heard data per band and restores on return. App keeps old-band rows. `mw` 1279-1285, 1337, 2938-2960, `JES` 5371-5381.
- [x] Heard graph missing (tooltip only, no on-air effect). `mw` 2327, `pCA` 77-83.
- [x] Inbox senders not in Call Activity are not added. `mw` 6293, `MF` 804.
- [x] Distance: PC Clarke ellipsoid and "<N" when close with a short grid. App sphere. `Geodesic.cpp` 223, 362, `Act` 183-196.
- [x] Units always " km" and "°"; PC plain numbers with labels on. `MF` 824-825.
- [x] Distance uses the saved grid, not the API grid. `MF` 800.
- [x] Buffered-to-me window 5 minutes; PC 90 s. `Act` 310.
- [x] Blocklist continuation frames may show. `mw` 3112-3130, `Act` 314. UNKNOWN in practice.
- [x] Compound calls: no grid from the compound frame, 10 Hz range too narrow at Fast, JS8 40, JS8 60, a "<....>" frame is dropped. `mw` 6167, `js8_engine_jni.cpp` 391-415, `Act` 275, 309. Seen with real decodes through the debug audio feed.

---

## 7. Waterfall

- [x] Green cycle line with white time and band text. `WideGraph.cpp` 279-285, `CPlotter.cpp` 159-178, 200-205.
- [x] Frequency scale colours: PC three bars, BAND_EDGE 0-4000, BAND_WARN 500-3000, BAND_GOOD 1000-2500. App yellow below 1000, green above with no upper limit. `CPlotter.cpp` 388-394, `Js8Meters.kt` 98-102.
- [x] Orange WSPR marker and "WSPR" label on 30 m. `CPlotter.cpp` 407-420.
- [x] Replot: changing Start, Bins Per Pixel or size redraws history on the PC. `CPlotter.cpp` 541-617, `ui/WaterfallRenderer.kt` 134-145.
- [x] Spectrum background: PC blue-black gradient and dotted grid. App plain black. `CPlotter.cpp` 430-463.
- [x] Filter minimum width follows speed on the PC (Slow 45, Normal 70, Fast 112, JS8 40 224). App fixed 120, so the menu's 50, 75, 100 Hz never show. `mw` 4191, `WideGraph.cpp` 160, 593, `MF` 1667, `Js8WaterfallControls.kt` 63.
- [x] Offset dial and filter drawn over the scale on the PC. Cosmetic.

---

## 8. Rig control

- [x] **Rig Control Error** box and auto retry; a rig lost mid-session does not reconnect. `mw` 5486-5520.
- [x] Polling reads frequency only. PC also reads VFO, split, mode, Tx frequency, PTT. `HamlibTransceiver.cpp` 978, `JES` 5275-5285. Seen against the fake rig only.
- [x] Mode (USB, Data/Pkt) ignored on Hamlib NET. `JES` 3317-3325. Seen against the fake rig only.
- [x] Hamlib models using a network port show only the USB Port box. FLRig direct does not work without a rigctld in front. `Schema` 118, 125, 391-393, `service/RigCtlClient.kt`. Real FLRig not tried. Seen: Network Server and Network Port boxes, Hamlib model 2 against the fake rig. Real FLRig not tried.
- [x] DX Lab Suite Commander and Ham Radio Deluxe missing. `TransceiverFactory.cpp` 37-41. Seen against stand-ins built from the PC source. Real programs not tried.
- [x] Split Rig turns split off after key-off; PC leaves it on. VFO B mode not set. Seen against the fake rig only.
- [x] QSY does not move Band Activity rows. `mw` 5337-5350, `MF` 1700-1711.
- [x] Rig greying (set_rig_invariants) not copied: CAT PTT radio, RTS radio, Transmit Audio Source, Poll Interval. `cpp` 2790-2841. Seen, Poll Interval included.
- [x] UNKNOWN: whether the app re-sends local station to PSK Reporter and JS8NET on a band change. `mw` 5459-5463.

---

## 9. Audio

- [x] **Output:** greyed and never read, so a USB sound card's output can't be chosen. `ui` 1917, `Schema` 439, `JES` 2368-2371.
- [x] **Input channel:** offers **Both**, which the PC removes; the app treats it as Left. `ui` 1957, `Schema` 438, `JS8AudioHelper.kt` 283.

---

## 10. Settings window

- [x] OK does not refuse bad groups or a bad CQ message: "%1 is a group that cannot be joined", "%1 is not a valid group", "The CQ message format is invalid...". `cpp` 2967, 3002, `Frag` 379-384.
- [x] Reply, CQ, Status, Info and Callsign Groups accept characters JS8 can't send. Not a gap: the PC only refuses control characters there (`cpp` 207). `cpp` 1491-1505, `Schema` 319-326.
- [x] **End of Transmission Character Indicator:** PC max 2, empty refused. `ui` 569, `cpp` 2947, `Schema` 348.
- [x] **Send SNR report when acknowledging heartbeats** shown but never read. The PC hides it and always sends SNR, as the app does. `ui` 745, `Schema` 360.
- [x] **Idle timeout - disable autoreply after:** PC raises values under 5 to 5. App honours 1-4. `cpp` 2410-2412, `JES` 3456.
- [x] **Only autoreply to these callsigns:** PC refuses a bad callsign. `cpp` 2983.
- [x] UDP Server Port max 65534 on the PC, 65535 in the app. `ui` 2397, `Schema` 475.
- [x] Frequency table **Load ...**, **Save as ...**, **Merge ...** missing. `cpp` 1672-1691, `Frag` 1388-1399. Seen: Load, Save as, Only Save Selected, Merge with a new row, bad file box.
- [x] Frequency table cells can't be edited in place. `FrequencyList.cpp` 291, `Frag` 1364-1403.
- [x] Add Frequency **Mode** list: PC All, JS8. App JS8 only. `Frag` 1416.
- [x] Frequency Schedule: no edit in place; PC greys the table unless **Automatically switch bands...** is ticked. `ui` 3026, `StationList.cpp` 223, `cpp` 1708, `Frag` 1471-1515.
- [x] Schedule dialog title "Add Schedule" (app "Add Station"), labels "Frequency (MHz):", "Switch at (UTC):", "Until (UTC):". `cpp` 288-291, `Frag` 1526-1530.
- [x] Bands: PC 30, app 13 with wider edges (30m, 17m, 60m, 12m). `Bands.cpp` 20-49, `util/BandUtils.kt` 18-30.
- [x] Saved Messages: no drag order, no edit in place. `cpp` 594, 1621, `Frag` 1553-1581. Seen: drag, double tap edit, Saved menu shows the new row.
- [x] **Font** buttons: PC family, style, size. App size only. `ui` 3611, 3818, 3825, 4045, `Frag` 1701-1704.
- [x] Colour buttons: PC any colour. App 24 swatches. `Frag` 1664-1669, 1706.
- [x] **Run Diagnostics:** PC about 10 sections, app 6 kinds of line. `cpp` 5006-5090, `Frag` 1299-1311.
- [x] **Clone:** PC makes "<name> - Copy" with no question. App asks. `MultiSettings.cpp` 595, `Cfg` 129.
- [x] Configurations menu order: PC Reset then Rename. Rename title "New Configuration Name". `MultiSettings.cpp` 498-506, `Cfg` 96-97, 157.

---

## 11. Logging

- [x] **ALL.TXT:** PC file ALL.TXT, UTC, decode string lines, "Transmitting ... MHz JS8:" lines, frequency headers. App `js8call-log.txt` in Download/JS8CallLogs, phone time, `RX FROM= SNR= ...` format, rotates at 10 MB. Other JS8 tools can't read it. `mw` 6969-7050, `pDE` 244, `MessageLogWriter.kt` 65-79, 278-285.
- [x] Frequency-change lines and start-of-day header missing. `mw` 5456, 2105.
- [x] DIRECTED.TXT written per frame; PC waits for the whole message. `JES` 2072-2081.

---

## 12. API and spotting

- [x] RX.DIRECTED fires per frame with no checksum check. `pBA` 53-90, `pCA` 156-175, `JES` 2064-2069.
- [x] RX.GET_BAND_ACTIVITY, RX.ACTIVITY, RX.DIRECTED use the current dial; PC the dial at decode. Wrong after a QSY. `networkMessage.cpp` 575-576, `api/Js8ApiCommands.kt` 258-263, `api/Js8Api.kt` 426.
- [x] UTC uses the phone clock, not the drifted clock. `JES` 2052.
- [x] STATION.SET_GROUPS lets a few bad names through. `networkMessage.cpp` 469-476, `Js8ApiCommands.kt` 223-230.
- [x] UNKNOWN: TX.FRAME count for multi-part messages. PC sends per frame. `mw` 2620, `JES` 2085-2091.
- [x] STATION.CLOSING and rig offline only on a finishing Activity; swipe-away UNKNOWN. `mw` 1477-1490, `MainActivity.kt` 440-443.
- [x] PSK Reporter needs a grid in the frame; PC spots every logged call. `JES` 4553-4557, `mw` 6529.
- [x] PSK antenna info empty. `mw` 5549, `JES` 4456.
- [x] PSK program name "JS8Android-x". `JES` 4435.
- [x] JS8NET TEXT field: PC empty for unbuffered non-CQ commands. `pDE` 395, `JES` 4511.

---

## 13. Not gaps

- 3.0.3 has no Help > keyboard shortcuts or special mouse commands items, no Open Save Directory, no separate DX grid, no receive-audio saving.
- Country data (cty.dat) is loaded on the PC but nothing uses it for colouring.
- Hidden or compiled out on the PC: **IARU Region:**, **HB Message:**, InsertBlank, DXCC, PrincipalPrefix.
- No use on a phone: Show Tooltips, Set Focus..., F-key shortcuts, dock and float of the Message Inbox, wheel offset change, hover dial, command-line options, lock file, single-instance check.
- Already deliberate in `HANDOFF.md`: header tap sorts, upright has no frequency block or menu bar, AUTO kept across restart, Easy JS8 About text, Bins Per Pixel 6 and 2, **Disable message relay** default checked, APRS only while RX is on, JS8NET PATH empty, API values kept after restart, hidden Save Directory, PTT command, Application stylesheet, Application Font.

---

## 14. Downsides of matching the PC

- Decoder stages use more phone CPU and battery. How much is unknown until they run on a phone.
- Spotting defaults on: new installs send to real networks unless changed.
- Autoreply default on: new installs answer on air by themselves.

---

## 15. Notes

- `HANDOFF.md` lines 404-406 are out of date: STATION.AUTOREPLY_CONFIRM_*, INBOX.STORE_MESSAGE and MODE.SET_SPEED with JS8 60 all work now.

### Not compared
- Bit-level pack_directed and pack_callsign against `Varicode.cpp`.
- PSK Reporter packet bytes.
- PC frame order (heartbeat, compound, directed) against core `varicode.cpp` 1409 (directed before compound).
- Typing Box filter for pasted or hardware-keyboard text.
- PC header grid with use_dynamic_grid, `mw` 2765.
- Set Filter Width menu values against `WideGraph.cpp` 156.
- Bluetooth serial paths.
