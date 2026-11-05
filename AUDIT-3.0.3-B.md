# Easy JS8 audit B against JS8Call 3.0.3

Date: 2026-11-04. App state: commit `c831379e` (code as `a38c4077`), branch `easy-js8`.
PC source: tag `v3.0.3` of JS8Call-improved, commit `4c592bd`.
First audit: `AUDIT-3.0.3.md` (2026-10-31), left as it is.

How this was checked:
- Every item was read in both sources by the main session. No agent results are used.
- Reading shows the code is there. It does not show the code runs. Items that need a run say so.
- `[x]` = read in both, the app does what the PC does (or the difference is a decision written in `HANDOFF.md`).
- `[ ]` = read in both, the app differs. Open.
- Line numbers are today's.

Short names for source files:
- PC, under the 3.0.3 checkout: `mw` = `JS8_UI/mainwindow.cpp`, `UC` = `JS8_Mainwindow/UI_Constructor.cpp`, `BQ` = `JS8_Mainwindow/buildQueryMenu.cpp`, `pCA` = `JS8_Mainwindow/processCommandActivity.cpp`, `pDE` = `JS8_Mainwindow/processDecodeEvent.cpp`, `pBA` = `JS8_Mainwindow/processBufferedActivity.cpp`, `ui` = `JS8_UI/Configuration.ui`, `cpp` = `JS8_UI/Configuration.cpp`, `JS8` = `JS8_Mode/JS8.cpp`, `WG` = `JS8_UI/WideGraph.cpp`, `CP` = `JS8_Main/CPlotter.cpp`
- App, under `android/app/src/main/java/com/js8call/example`: `MF` = `js8ui/Js8MainFragment.kt`, `JES` = `service/JS8EngineService.kt`, `Act` = `js8ui/Js8Activity.kt`, `Schema` = `js8ui/Js8SettingsSchema.kt`, `Frag` = `js8ui/Js8SettingsFragment.kt`, `Cfg` = `js8ui/Js8Configurations.kt`, `WC` = `js8ui/Js8WaterfallControls.kt`, `WR` = `ui/WaterfallRenderer.kt`
- App engine, under `core/src`: `LD` = `decoder/legacy_decoder.cpp`, `ENG` = `engine/engine.cpp`, `VAR` = `protocol/varicode.cpp`

---

## 1. Serious: decoding and on-air behaviour

### Decoder
- [x] **Soft combining.** PC `JS8` 1526-1530 (on unless `JS8_SOFT_COMBINING=0`, `soft_combiner.h` 198-201). App `LD` 1560-1563, 1639; `core/src/decoder/soft_combiner.h` 184 always on. Same file apart from Qt logging.
- [x] **LDPC feedback retry.** PC `JS8` 1085-1086, `ldpc_feedback.h` 53-66 (on, 8 passes). App `LD` 1140-1141, 1654-1681; `core/src/decoder/ldpc_feedback.h` 49-52 (on, 8 passes).
- [x] **Whitening and LLR erasure threshold.** PC `JS8` 1473-1507. App `LD` 1535-1550. Same default (`LLR_ERASURE_THRESHOLD_DEFAULT`, same `ldpc_feedback.h`). App passes `false` for the debug flag only.
- [x] **Frequency and timing tracker.** PC `JS8` 1222-1238, 1304-1397. App `LD` 1305-1321, 1363-1456. `FrequencyTracker.cpp` is the same file.
- [x] **Waterfall filter sets the decoder range.** PC `mw` 2099-2102, `WG` 565-571 (centre ± width/2, 0-5000 Hz with the filter off). App `JES` 2325-2335, re-sent on every change of the 4 prefs at `JES` 5952-5954. Same arithmetic. Defaults match: on, 1500 Hz, 2000 Hz (PC `WG` 237-241).
- [x] **Decode priority at my offset.** PC `mw` 2098 `nfqso = freq()`. App `JES` 2333 offset from `js8cfg_tx_offset`.

### Directed commands and auto replies
- [x] **QUERY CALL answered.** PC `pCA` 1053-1102 (needs AUTO, "CALL YES -12 (5m)"). App `JES` 4284-4288, AUTO at 4321, @ALLCALL limit at 4323. Same reply text. The list it looks in differs, see HEARING? below.
- [x] **Queries to my @GROUP answered** (SNR?, INFO?, STATUS?, GRID?, HEARING?). PC `pCA` 186-190, 333-374. App `JES` 4566-4572 (`shouldReplyToDirected`), 4389-4418.
- [x] **15-minute limit on @ALLCALL and heartbeat replies.** PC `pCA` 306-311, 722, 1098, 1149. App `JES` 4234, 4323, 4358, 4379; gap 15 min at 6033.
- [x] **No reply while text is in the Typing Box.** PC `pCA` 1135. App `JES` 3644. A second PC check at send time is missing, see N2.
- [x] **No reply while a message to me is still arriving.** PC `pCA` 1140-1145. App `JES` 3644, 4090-4094.
- [x] **HEARING? and QUERY CALL read the wrong list.** Fixed 2026-11-04, W68 and W69 passed twice: both read Call Activity (`JES` callActivityNow, through `MainActivity.kt`). PC reads Call Activity (`pCA` 375, 1073), so **Clear Call Activity** (`mw` 3016-3019) and **Remove This Station** (`UC` 1112) take a call out of the answer. App reads its own heard list (`JES` 196, 5155-5160, 5169-5190), filled from every decoded "CALL:" frame and never cleared. The rest matches: newest first, 4 calls, **Remove callsigns from call activity after:**, always replies. The app already reads Call Activity for APRS (`MainActivity.kt` 361).
- [x] **QUERY MSGS to @HB.** PC `mw` 6088-6090 (@HB counts as all-call), `pCA` 983-1050. App `JES` 5226, 3952-3956.
- [x] **@APRSIS MSG with "TO:" in the text.** PC `pCA` 765-810. App `JES` 3945, 4245-4261. Same 60 s repeat filter, sender "APRS".

### Checksums and buffered messages
- [x] **Checksum checked on MSG, MSG TO:, QUERY, QUERY MSGS, QUERY CALL.** PC `pBA` 60-95, 16-bit (`Varicode.cpp` 134-135). App `JES` 4144-4149, 5044-5050, used at 3961, 3993, 4110, 4115. Bad ones are dropped.
- [ ] **@APRSIS MSG / MSG TO: with no checksum.** The PC sender leaves the checksum off (`Varicode.cpp` 2275-2279; app engine `VAR` 1459-1461, same). The PC receiver still demands one (`pBA` 63-76) and drops the message, so its own APRS-to-JS8 relay (`pCA` 541-589, 765-810) never stores one. App accepts them without a checksum (`JES` 4146). Matching the PC would break the feature, see section 14. Kept as is (Rob, 2026-11-04), section 13.
- [x] **Relay with a bad checksum.** PC `pBA` 63-76 (relay `>` is checksummed). App `JES` 4893-4899 drops it.
- [x] **Commands answered on the last frame.** PC `Varicode.cpp` 1239-1242 (every space-prefixed command is buffered), `pDE` 469-508. App `JES` 2188-2209 (`deferAutoReply`).
- [x] **Buffer times 60 s / 90 s.** PC `pBA` 29-41. App `JES` 4069-4082, 5861-5862. Relay buffers differ, see N4.
- [x] **Match range per speed.** PC `JS8Submode.cpp` 124-153 (Normal and Slow 10, Fast 16, JS8 40 32, JS8 60 50 Hz), `mw` 2239. App `JES` 4096-4102. Relay and spot buffers differ, see N4.
- [x] **A first frame clears an old buffer.** PC `pDE` 316-328. App `JES` 3914-3919.

### Relay (A>B>C)
- [x] **Relay follows AUTO, TX off, Idle Timeout and Autoreply Confirmation.** PC `pCA` 313-318, 410, 1157. App `JES` 5100-5106 (AUTO, SWL, then `queueAutoReply` 3642-3659), TX off in `MainActivity.kt` 487-491, idle timeout turns AUTO off (`JES` 3609-3623).
- [x] **Relay heard while transmitting** waits in the queue. App `MainActivity.kt` 472-484.
- [x] **Relayed MSG, MSG TO:, QUERY, QUERY MSGS, QUERY CALL.** PC `pCA` 459-513. App `JES` 4929-4958.
- [x] **MSG with a DE path** stored with the path and ACKed along it. PC `pCA` 814-828. App `JES` 3994-4001, 4128-4135.
- [x] **Relay to my @GROUP.** Fixed 2026-11-04, W70 passed twice: ACKed, not forwarded. PC takes it (`pCA` 410) but never forwards it to a group (`pCA` 424 `!isGroupCall`); it ACKs or answers the command instead. App takes it (`JES` 4440) and forwards it like one to me (`JES` 4903-4911).

### Heartbeat
- [x] **No ACK in JS8 40 and JS8 60.** PC `pCA` 636, `mw` 4113-4117. App `JES` 4353.
- [x] **Pause heartbeat transmissions while in a QSO** blocks ACKs with a callsign selected. PC `pCA` 649. App `JES` 4355. Default on in both (`cpp` 2292, `Schema` 396).
- [x] **Selecting a callsign stops the HB and CQ timers; deselect restores HB.** PC `mw` 5958-5999. App `MF` 2602-2621.
- [x] **No ACK while a message buffer is open.** PC `pCA` 642. App `JES` 4354. (App counts message buffers only, not relay buffers.)
- [x] **Held-message ID on the ACK, group included.** PC `pCA` 662-706. App `JES` 4362-4376.
- [x] **ACK text "MSG ID 5 +2)"** with the PC's stray bracket. PC `pCA` 679. App `JES` 4374.
- [x] **HB offset from a free 50 Hz slot in 500-1000 Hz.** PC `mw` 3679-3725. App `JES` 3213-3238. (The PC also counts offsets in its directed cache as free, `mw` 3683; the app does not.)
- [x] **Timed HB at my offset when it is at or below 1000 Hz.** PC `mw` 4443-4447. App `JES` 3217. (The PC's HB ACK always picks a free slot, `mw` 4469-4470; the app uses the same rule as the timed HB, `JES` 3353-3354.)
- [x] **Timed HB while transmitting waits.** PC `mw` 4450-4451. App `JES` 3244-3247.
- [x] **Timed HB goes out with text in the Typing Box.** Fixed 2026-11-04, W73 passed twice: it waits for an empty Typing Box. The PC queues it (`mw` 4450) and the queue waits for an empty Typing Box (`mw` 6625-6628). App waits for TX on, nothing on the air and no queued message (`JES` 3244), not for the Typing Box.

### TX queue, CQ and held messages
- [x] **CQ timer stops when a message is shown.** PC `processRxActivity.cpp` 130-135, `pCA` 266-274. App `JES` 5191-5200.
- [x] **Autoreply Confirmation > No leaves a held message uncollected.** PC `confirmThenEnqueueMessage.cpp` 89-90 (enqueue only on Yes). App `JES` 3662-3669 (`onAccepted` only on Yes).
- [x] **Priorities:** replies 100, HB and HB ACK 11, APRS relay 1000. PC `mainwindow.h` 812-815, `mw` 4450, 4475, `UC` 156. App `JES` 6028-6030. Queue order by priority: PC `mainwindow.h` 825-830, app `ui/TransmitViewModel.kt` 46-47.
- [x] **30 m WSPR guard band 10139.9-10140.32 kHz refused.** PC `mw` 2469-2500. App `JES` 5202-5212, 5886.
- [x] **Stuck-key check "Message Not Sent".** PC `mw` 3375-3384, 3423-3429. App `MF` 1492-1497.

### New in audit B
- [x] **N1. With AUTO off the PC still answers me, into the Typing Box.** Fixed 2026-11-04, W71 passed twice: the queue puts the reply in the Typing Box unless it is priority 1000 or more, a heartbeat or HB, an ACK, or AUTO is on (`MainActivity.kt` processNextTxIfIdle, PC `mw` 6650-6662). PC skips only @ALLCALL replies without AUTO (`pCA` 1121-1124). Every other reply is queued; the queue puts it in the Typing Box and sends it only with AUTO on (`mw` 6650-6662, run every second at `mw` 2800). So SNR?, INFO?, STATUS?, GRID?, HEARING?, AGN?, a relay, and the ACK for a MSG or MSG TO: land in the Typing Box for you to send. QUERY MSGS, QUERY CALL and HB ACKs need AUTO on the PC too (`pCA` 984, 1054, 638). App drops all of them with AUTO off (`JES` 4339, 3999, 4133, 4321, 4949, 5101).
- [x] **N2. Queued replies don't wait for the Typing Box at send time.** Fixed 2026-11-04, W72 passed twice: the queue is checked every second and a reply waits while the box has text. PC checks the box when it takes a message off the queue (`mw` 6625-6628), so a reply waits while you type. App checks only when the reply is queued (`JES` 3644); `MainActivity.kt` 472-513 sends it even if you have typed since.
- [x] **N3. Message buffer doesn't follow a drifting signal.** Fixed 2026-11-04, W74 passed twice: every buffer moves to the new frame's offset (`JES` followDrift). PC moves the buffer to each new frame's offset (`mw` 2248-2251). App keeps the first offset (`JES` 4084-4088), so a signal that drifts past the match range part way through loses its last frames.
- [x] **N4. Relay and group-spot buffers use a fixed 10 Hz and never close at 60 s.** Fixed 2026-11-04, W75 and W76 passed twice: the match range per speed, drift followed, closed 60 s after the latest frame, dropped at 90 s. PC uses one buffer list for all of them (`mw` 2231-2259, `pBA` 29-41). App relay buffers: 10 Hz (`JES` 4872-4881, 5867), dropped at 90 s only (`JES` 4836-4846, 5866). Group-spot buffers: 10 Hz (`JES` 4709).
- [x] **N51. With the screen off the app sends no replies.** Found 2026-11-04 (`screenoff.py`). Fixed 2026-11-04, W67 passed twice: decodeReceiver, monitorReceiver and idleReceiver run from `onCreate` to `onDestroy` (hopReceiver and confirmReceiver stay in `onStart` / `onStop`; each opens a dialog box: the scheduled band hop and Autoreply Confirmation). PC JS8Call answers with its monitor off. App: `MainActivity.kt` registered its receivers in `onStart` and removed them in `onStop`, so the TX queue (ACTION_QUEUE_TX), Call Activity, Band Activity and the Message Window stopped while the screen was off. Side effect of matching: more battery use with the screen off.
- [x] **N52. A relay whose text starts with "@" is dropped.** Fixed 2026-11-05 (Rob: "we do what js8call does"), W130 passed twice: the early return is gone, so the text goes the PC's way: not forwarded, ACKed or its command answered (`JES` processRelayBuffer). Found 2026-11-04. PC: the text doesn't start with a callsign, so it is not forwarded; it is ACKed or its command answered (`pCA` 416-434). App ignores it (`JES` processRelayBuffer, "Relay payload starts with group token").
- [x] **N53. TX.SEND_MESSAGE with AUTO off.** Fixed 2026-11-05, W129 passed twice: queued at the PC's normal priority (`MainActivity` API_PRIORITY_NORMAL, `Js8ApiCommands`), so it waits for an empty Typing Box, and with AUTO off it is put there and not sent. Test scripts that send through the API now run with AUTO on (n42chk, n48chk, s812chk W111-W113, W126). Found 2026-11-04. PC queues it at normal priority (`networkMessage.cpp` 744-748); with AUTO off the queue puts it in the Typing Box and does not send it (`mw` 6650-6664). App sends it. Side effect of matching: an API program can't send with AUTO off.
- [x] **N54. INFO? and STATUS? with text that fills in empty.** Fixed 2026-11-05, W123 passed twice: skipped only when the stored text, trimmed, is empty; the macros are filled in after (`JES` 4490, 4498, 5172, 5179; PC `pCA` 340-356).

### Frame building (not-compared areas 1 and 3 of 7)
Some of these were run in the app engine on the S22-Fresh emulator: the frame builder alone, compiled from `core/src`, with a given selected call and text. The PC side is read only.
- [x] **Callsign and directed frame bits match.** (Area 1.) Callsign: 28 bits, the same 54 base calls in the same order, the same portable flag. Directed frame: 3-bit type, from (28), to (28), command (5), then a byte with the portable flags and the number plus 31, kept to -30..31. The command codes match. PC `Varicode.cpp` 50-120, 213-275, 958-1081, 1169-1179, 1605-1750. App `VAR` 71-97, 577-650, 1020-1146.
- [x] **N45. Directed pattern is looser in the app.** Fixed 2026-11-04, W61 passed twice. (Area 1.) PC: the line starts with the callsign, then a command, with no colon between, no space before, capitals only (`Varicode.cpp` 137-152). App also takes a space before, a colon after the callsign, and any case (`VAR` 130-134). Run: "K1ABC: HELLO" builds a directed frame to K1ABC plus "HELLO", the same frames as "K1ABC HELLO". The PC finds no command after "K1ABC" and sends "K1ABC: HELLO" as text (`Varicode.cpp` 1627-1631).
- [x] **Frame order.** (Area 3.) PC tries heartbeat, compound, directed, data (`Varicode.cpp` 2169-2189). App tries heartbeat, directed, compound, data (`VAR` 1404-1422). No effect: a compound frame needs a line starting with a backtick in both (`Varicode.cpp` 157; `VAR` 128-129), and the directed pattern can't start with one. Which commands wait for the last frame matches (`VAR` 1455-1456; `Varicode.cpp` 1239-1242).
- [x] **N46. Backtick line with text after the command.** Fixed 2026-11-04, W62 passed twice. (Area 3.) PC sends the compound part, then the rest as text: it matches the start of the line only (`Varicode.cpp` 1444, 1487). App needs the whole line to match (`VAR` 129, ends in `\s*$`; `VAR` 905). Run: "`K1ABC/P SNR? HELLO" goes out as one text message, backtick included.
- [x] **N47. Selected call and a line starting with a grid.** Fixed 2026-11-04, W63 passed twice. (Area 3.) With K1ABC selected, the PC puts "K1ABC " in front of "FN42 HELLO", because its callsign search skips grids (`Varicode.cpp` 518-538, 2076-2105). App takes the first word as a callsign when it is a valid callsign over 3 characters (`VAR` 1361-1371). Run: the app sends a directed message to "FN42", plus "HELLO".

---

## 2. Bugs in existing features

- [x] **Clone Into ... direction.** PC: the submenu is the target, you pick the source (`JS8_Main/MultiSettings.cpp` 489-494, 620-650, title "Configuration to Clone From" 121). App `Cfg` 143-161, same titles.
- [x] **Data Bits > Default and Stop Bits > Default leave Hamlib's value.** PC `HamlibTransceiver.cpp` 310-321. App `Schema` 439-445 save "default", `JES` 1787-1788 turn it into 0, `adapters/android/jni/hamlib_rig_control_jni.cpp` 218-227 then sets nothing.
- [x] **Halt stops the HB and CQ timers.** PC `mw` 5266-5280 (the button press keeps `m_stopTxButtonIsLongterm` true, `UC` 23). App `MF` 1653-1662.
- [x] **Restore Previous Message only fills the box.** PC `mw` 3491-3496. App `MF` 2042-2043 (`send = false`).
- [x] **Directed menu items that wait for you.** PC `BQ`: the first item, Reply, `>`, MSG, MSG TO:, QUERY CALL, QUERY MSGS, QUERY MSG have no `toggleTx` (21-46, 211-298); the rest send with **Immediately transmit ...** (70, 87, 107 ...). App `MF` 1467-1471, 1464.
- [x] **CQ, REPLY, SNR, INFO, STATUS, Saved add after a space.** PC `mw` 3288-3317. App `MF` 1448-1458.
- [x] **TX off halts and greys Send.** PC `mw` 1191-1199, 5752-5757. App `MF` 1111-1116, 2654.
- [x] **RX while transmitting is refused.** PC `mw` 1145-1160. App `MF` 1105.
- [x] **Double-tap a Band Activity row follows the offset lock.** PC `mw` 5030-5038, 5381-5390. App `MF` 640-654.
- [x] **Sort By Mode Speed puts Slow first.** PC `displayBandActivity.cpp` 97-110. App `MF` 2638, used at 941 and 994.
- [x] **Deselect and CQ drop the selection start time.** PC `mw` 6020-6022, 4539. App `MF` 1664-1665, 2513.
- [x] **INFO needs no selection; INFO and STATUS grey when empty.** PC `mw` 5692-5693. App `MF` 2647-2648.
- [x] **REPLY, SNR, INFO, STATUS, Saved, Directed, Deselect grey while a message waits.** PC `mw` 5674-5697. App `MF` 2644-2652.
- [x] **No "CALL|[MESSAGE]" item.** PC `BQ` 197-209 is `#if 0`. App `MF` 1768-1799 has none.
- [x] **Directed menu labels without the callsign.** PC `BQ` 12-14. App `MF` 1769-1798.
- [x] **Directed menu greys six items the PC leaves on for @ALLCALL and @HB.** Fixed 2026-11-05, W77 passed twice. PC greys SNR? INFO? GRID? STATUS? HEARING? `>` MSG and MSG TO: (`BQ` 115-245). App also greys QUERY CALL, QUERY MSGS, QUERY MSG, AGN?, QSL? and HW CPY?, because its rule is "ends with ? or has QUERY" (`MF` 1480). SNR, INFO and GRID greying match (`BQ` 53, 76, 96; `MF` 1477-1479).
- [x] **Log... on @ALLCALL.** Fixed 2026-11-05, W78 passed twice. PC greys it (`UC` 1284-1285, `missingCallsign || isAllCall`). App always enables it (`MF` 1842). Other groups: enabled in both.

### New in audit B
- [x] **N5. Directed menu has no separators.** Fixed 2026-11-05, W77 passed twice; the three lines checked by eye on its screenshots. PC has three: after the first item, after GRID, after QUERY MSG (`BQ` 32, 111, 300). App none (`MF` 1752-1762).
- [x] **N6. Double-tap a Call Activity row adds "CALL " with a space.** Fixed 2026-11-05, W79 passed twice. PC adds the call with no space after it (`mw` 5101-5104, `addMessageText(call)`). App adds `row.id + " "` (`MF` 630).
- [x] **N7. Clone name for a second copy.** Fixed 2026-11-05, W99 passed twice. PC "NAME - Copy(2)", then (3) (`MultiSettings.cpp` 594-603: the counter is raised before it is first used). App "NAME - Copy (1)", with a space, starting at 1 (`Cfg` 136-138).

---

## 3. Wrong defaults

- [x] **Write log files (ALL.TXT, DIRECTED.TXT, etc) of decoded text:** on. PC `cpp` 2302. App `Schema` 375.
- [x] **Turn autoreply on at startup:** on. PC `cpp` 2286-2287. App `Schema` 402.
- [x] **Enable spotting to reporting networks (JS8NET, PSKReporter, etc):** on. PC `cpp` 2298-2299. App `Schema` 503.
- [x] **Enable spotting @APRSIS messages to the APRS-IS network:** on. PC `cpp` 2300. App `Schema` 505.
- [x] **Baud Rate:** 4800. PC `cpp` 2365. App `Schema` 438.
- [x] **Transmit Audio Source:** Front/Mic. PC `cpp` 2390-2394. App `Schema` 467.
- [x] **Every other default in the PC's settings read** (`cpp` 2124-2156, 2225-2454): callsign and activity aging 0 and 2, EOT ♢, MFI ……, CQ, Reply and Status messages, Tx delay 0.2 (0.1-0.5), Idle timeout 60, Autoreply Confirmation on, HB pause on, HB rate limit off, heartbeat anywhere off, Allow Tx frequency changes off, Hold PTT off, PTT Method VOX, Split None, Mode None, Handshake Default, UDP 2242, TCP 2442, TCP max 1, WSJT-X 2237 and TTL 1, N3FJP 1100, N1MM 2333, APRS rotate.aprs2.net 14580, saved message "TNX 73 GL". All match `Schema` 352-538.
- [x] **Heartbeat interval:** Fixed 2026-11-05, W82 passed twice: every 30 minutes in Settings, the HB menu, the API and the service (`Schema` 390, `MF` hbInterval, `api/Js8ApiCommands.kt` 342, `JES` 3132). PC every 30 minutes (`cpp` 2409, box 0-1440 step 5, `ui` 629-660). App Disabled (`Schema` 390, comment: kept from JS8Android so a new install never transmits on its own). With HB on, a tap on the PC's HB button starts a 30-minute timer; the app's sends one heartbeat. Not in `HANDOFF.md`'s deliberate list.
- [x] **Disable message relay (>) when autoreply is enabled:** PC off (`cpp` 2293), app on (`Schema` 404-406). Deliberate, `HANDOFF.md`.

---

## 4. Macros

- [x] **`<CALL>`, `<SNR>`, `<TDELTA>`** filled from the selected call in Call Activity, `<SNR>` only above -31. PC `mw` 4868-4878. App `js8ui/Js8Macros.kt` 51-57.
- [x] **`<MYHB>` is empty.** Fixed 2026-11-05, W80 passed twice. PC fills it with the hidden HB Message, "HB <MYGRID4>" (`cpp` 2153, 1286; `mw` 4859, 4884), so it sends "HB EN94". App sets it to "" (`Js8Macros.kt` 42).
- [x] **`<MYVERSION>`** is the program's plain version. PC `mw` 4852, `JS8_Main/revision_utils.cpp` 10-16. App `Js8Macros.kt` 37 (`BuildConfig.VERSION_NAME`).
- [x] **`<MYIDLE>`** in M / H / D. PC `mw` 4849-4851. App `Js8Macros.kt` 20-27.
- [x] **Values upper-cased, leftover `<...>` removed.** PC `mainwindow.h` 789-799. App `Js8Macros.kt` 46-48.
- [x] **N8. App also squeezes runs of spaces to one and trims** (`Js8Macros.kt` 48). PC keeps the spaces as typed (`mainwindow.h` 793-798). Fixed 2026-11-05, W80 passed twice.

---

## 5. Main window and menus

### Missing in audit A, now built
- [x] **Clock drift** "+123ms". PC after the time (`mw` 2685-2695). App right of the callsign in Upright, PC and Pages, own line in FT8Call (`MF` 197-213). Rob's choice, HANDOFF.md "Clock drift display (done 2026-11-03, Rob)".
- [x] **Configuration name in the status bar** unless Default, left of the mode box. PC `UC` 521-528, `mw` 1414-1418. App `MF` 1364-1390.
- [x] **Send button text.** Fixed 2026-11-05, W103 passed twice. Send (time), Sending (time), Tuning and the time format match (PC `mw` 35-50, 5858-5892; App `MF` 2671-2698). **Ready has no countdown:** PC "Ready (1m 30s)" (`mw` 5875-5878), app "Ready" (`MF` 2691).
- [x] **N9. Send stays live while a heartbeat or reply is queued or sending.** Fixed 2026-11-05, W81 and W103 passed twice. PC greys Send while any message is queued (`mw` 5863, 5881, 5889-5890, `isMessageQueuedForTransmit` 3264-3266). App greys it only for its own typed message (`MF` 2654).
- [x] **Directed** reads "Directed to CALL". PC `mw` 5699-5701. App `MF` 2657-2661 (falls back to "Directed" when the button is too narrow).
- [x] **Typing Box hint**, both texts. PC `mainwindow.ui` 884, `mw` 6002. App `MF` 2663-2664, `js8_typing_box.xml` 9.
- [x] **Tap the frequency** opens the frequency menu, **Set a Custom Frequency...** first, then the list lowest first; box "Set a Custom Frequency" / "Frequency in MHz:". PC `UC` 448-456, `mw` 4264-4306. App `MF` 1145, 1314-1343.
- [x] **N10. Frequency menu rows.** Fixed 2026-11-05, W83 passed twice. App "20m:  14.078 MHz", 3 decimals, no line under **Set a Custom Frequency...** (`MF` 1318-1322). PC "20m:     14.078 000 MHz", 6 decimals with a space, band padded to 5, a line after the first item (`mw` 4283, 4292-4302, `JS8_Main/Radio.cpp` 79-82).
- [x] **Tap the callsign** opens Settings on its first page. PC `UC` 472-479 (`openSettings(0)`). App `MF` 1144.
- [x] **Double-tap a word** in the Message Window goes into the focused Log QSO field, appended with a space, a report keeps its + or -. PC `UC` 743-776, `JS8_UI/LogQSO.cpp` 46-67. App `js8ui/Js8LogFragment.kt` 126-167.
- [x] **Selected Message Window text fills Comments.** PC `mw` 3883, 3903. App `MF` 2198-2200.
- [x] **Rig Control Error box**: one automatic retry, then Retry, Configurations..., OK (Settings, radio page), Cancel (closes); sending stops. PC `UC` 29-31, 97-99, 531-532, `mw` 5486-5527. App `MF` 505-528, `JES` 5572-5584.
- [x] **N11. Rig Control Error text.** Fixed 2026-11-05, W84 passed twice. App puts the reason above the question (`MF` 514). PC shows only "Do you want to reconfigure the radio interface?" and puts the reason under **Show Details...** (`UC` 97-98, `mw` 5499).
- [x] **Short messages** "Spotting to PSK Reporter unavailable" and "Enumerating audio devices". PC `mw` 6780-6783, `UC` 517-518, `cpp` 4585, 4667. App `JES` 4647, `Frag` 505.
- [x] **Open Log Directory...** PC `mw` 4244-4247. App `MF` 1237.
- [x] **Show Fullscreen.** PC `mw` 862-870. App `MF` 1177, 1392.
- [x] **Show Time Drift Controls...** PC `mw` 989. App `MF` 1176, `WC` 249.
- [x] **HEARING and HEARD BY** for a call. PC row tooltip (`mw` 5070-5091). App heads the row's menu, a phone has no hover (`MF` 1829-1831).

### Partial in audit A
- [x] **Clear All Lists** asks "Are you sure you would like to clear all activity?" in every table and Message Window menu. PC `UC` 784-797. App `MF` 1953-1959, used at 1857, 1900, 1916, 2026, 2067, 2098.
- [x] **N12. Control > Clear Activity... > Clear All Activity asks first in the app** (`MF` 1290, 1304). PC clears at once (`mw` 829-831). PC also has a line under it (`mainwindow.ui` menuClear_Activity). Fixed 2026-11-05, W97 passed twice.
- [x] **Clear** in the Message Window and **Clear RX Activity** empty and unlock the Typing Box. PC `mw` 3001-3014. App `MF` 1961-1967.
- [x] **Message Window menu**: Cut and Paste grey (window is read-only), Copy needs a selection, Clear, Clear All Lists, Save As... PC `UC` 802-846, `mw` 4823-4846. App `MF` 2089-2115.
- [x] **Minimal Column Labels** drops Hz, dB, ms in the cells. PC `mw` 4657-4667, `displayBandActivity.cpp` 232-251, `displayCallActivity.cpp` 280-291. App `MF` 899-900, `Js8TableView.kt` 338-351.
- [x] **Show Column Labels off** puts km / mi and ° in the cells. PC `displayCallActivity.cpp` 311-320. App `MF` 950-952, 977-978.
- [x] **CAT button.** Fixed 2026-11-05, W84 passed twice. Green and greyed when fine, red and live after a failure, "CAT/S" in split, a tap while sending does nothing: match (PC `mw` 5290-5296, 5481-5489; App `MF` 1139-1141, 2807-2817). **No orange while the rig opens:** PC turns it orange in `rigOpen` (`mw` 5281-5285, `mainwindow.ui` 1485); the app has green and red only.
- [x] **Signal meter.** Fixed 2026-11-05: red on the loudest sample, scale 0-100. W101 (the scale, by eye) passed twice; the red rule is read only, not run. Last 10 readings, white peak mark, yellow when the peak is under 15, number and dB: match (PC `JS8_Main/SignalMeter.cpp` 55-100; App `js8ui/Js8Meters.kt` 25-77). **Red uses the average level:** app red when the level is over 85 (`Js8Meters.kt` 62-63, fed `powerDb` at `MF` 2781). PC red when the loudest sample is over 85 dB (`SignalMeter.cpp` 77, `m_pxmax` from `dataSink.cpp` 138, 263). The engine already sends the loudest sample as `peakDb` (`engine.cpp` 1163). **Scale 0-90**, PC 0-100 (`SignalMeter.cpp` 25).
- [x] **Power slider** now takes touch, 0-450, 0 at the top (App `Js8Meters.kt` 224-276, `MF` 317-320; PC `mainwindow.ui` outAttenuation, `mw` 540-541). **It does not move when Remember power settings by band or TUNE changes the power:** the service writes the new value (`JES` 5687, 5697, 5711), the slider reads it only when the screen is built. PC moves the slider (`mw` 4252, 5180, 5204). Fixed 2026-11-05, W100 passed twice.
- [x] **Status label.** Fixed 2026-11-05, W85 and W104 passed twice. "Tx: <message>" (40 characters), "Tx: TUNE", "Decoding", "Receiving" match (PC `mw` 2739-2759; App `MF` 2819-2837). **Idle timeout is red**; PC black with white text (`JS8_UI/styles.h` 109-110). **With RX off the app shows "Idle" on blue**; PC shows an empty label with no colour (`mw` 2761-2763).
- [x] **Show Message Inbox...** from Call Activity shows only that call. PC `UC` 1159-1167, `MessagePanel.cpp` 73-80. App `MF` 1531-1533, `Js8InboxFragment.kt` 44-47. **Title:** app "Messages: CALL" / "Message Inbox". The PC dock's title is "Message Inbox" (`mw` 115); "Messages: CALL" / "Messages: All" is set on the panel inside the dock (`MessagePanel.cpp` 75). Fixed 2026-11-05, W86 passed twice.
- [x] **Inbox newest first**; Delete, Select More, Select All for several rows. PC `MessagePanel.cpp` 63-65, 92-95. App `js8ui/Js8Inbox.kt` 39, 69-80.
- [x] **Inbox From** reversed, joined with " via ". PC `MessagePanel.cpp` 17-21, 147-149. App `Js8Inbox.kt` 47.
- [x] **Inbox Frequency and Date** "14.078 000 MHz", "Wed Nov 4 12:00:00 2026". PC `MessagePanel.cpp` 133-143. App `Js8Inbox.kt` 12, 57-60. Rows stored before the dial was kept show the offset in Hz.
- [x] **Menu bar Mode** opens the Mode menu: five speeds greyed while a message is queued, MULTI, AUTO (+CONF), HB, ACK with the PC's enable rules. PC `mainwindow.ui` menuModeJS8, `mw` 694-716, 4131-4134. App `MF` 1167, 793-835.
- [x] **Menu bar Log**: Add Log Entry..., Open Log Directory..., Erase ALL.TXT, Erase js8call_log.adi, each Erase asks. PC `mainwindow.ui` menu_Log. App `MF` 1212, 1234-1261.
- [x] **N13. Menu bar Control.** Fixed 2026-11-05, W96 passed twice (the CQ menu is read only, not run). Opens the menu now (`MF` 1168, 1274-1312). Differences from PC `mainwindow.ui` menuControl and `mw` 718-735:
  - Order: app puts **Send Heartbeat...** and **Send CQ Message...** before **Set Frequency...** and **Set Offset...**; PC has Set first.
  - No **Set Focus...** (Message Receive Area, Message Reply Area, Band Activity Table, Call Activity Table).
  - **Set Frequency...**, **Send Heartbeat...**, **Send CQ Message...** are sub-menus on the PC: the frequency menu, the heartbeat menu, the CQ menu (`mw` 719-729, 4264, 4308, 4326). The app opens the custom frequency box, sends one heartbeat, sends one CQ (`MF` 1300-1302). The app already has the heartbeat and CQ menus (`MF` 2418-2430).
  - **Set Offset...** box title: app "Set Offset" (`MF` 1353), PC "Set Frequency Offset" (`mw` 849).
- [x] **Menu bar Help**: Copyright notice, Check for Updates, About, JS8Call User Guide, PC order. PC `mainwindow.ui` menuHelp. App `MF` 1213-1218.
- [x] **N14. Menu bar View.** Fixed 2026-11-05, W98 passed twice. Order differs. PC: Show Frequency & Clock / Show Band Activity, Show Band Heartbeats and ACKs, Show Band Activity Columns..., Sort Band Activity By... / Show Call Activity, Show Call Activity Columns..., Sort Call Activity By... / Show Waterfall, Show Waterfall Controls, Show Time Drift Controls... / Show Fullscreen, Show Statusbar, Show Tooltips / Show Message Inbox... / Reset Frame Sizes (`mainwindow.ui` menuWindow). App: Show Message Inbox..., Settings…, Reset Frame Sizes, the Sort and Columns items, Show Time Drift Controls..., Show Fullscreen, then the six panel items (`MF` 1171-1185, `Js8PcSplitters.kt` 37-38). **Show Band Heartbeats and ACKs** is in Settings > View, not this menu (`Schema` 254). No **Show Tooltips** (no hover). **Settings…** here is Rob's (2026-10-20).
- [x] **Press and hold an empty list.** Fixed 2026-11-05, W87 passed twice. App shows a short menu: Call Activity **Add New Station or Group...**, **Clear Entire List**, **Clear All Lists**; Band Activity **Clear**, **Clear All Lists** (`MF` 1896-1922). PC shows its whole menu with the call items greyed; **Saved Messages...** stays live in both lists (`UC` 1290-1296, 1018-1022). The app's empty menus have no **Saved Messages...**.
- [x] **Store Message...**: upper case, newline and printable 7-bit only, greyed on @ALLCALL, title "Message", "Store this message locally for CALL:". PC `MessageReplyDialog.cpp` 38-58, `UC` 1170-1196, 1290-1291. App `MF` 1844, 2206-2229. App trims and does not store an empty box; PC stores the box as it is (`UC` 1191).
- [x] **Saved menu**: macros shown filled in, **Edit Saved Messages** opens the Saved Messages page, **Save Current Message** greyed with an empty box. PC `mw` 4960-4991. App `MF` 1702-1720, 1930-1940. The APRS items in the app's Saved button menu are Rob's (2026-10-20).
- [x] **Typing ahead while sending**: the unsent part stays editable, sent part crossed out. PC `JS8_Main/TransmitTextEdit.cpp` 166-210, 320-350, 640-655. App `MF` 700-728.
- [x] **N42. Typing Box keeps characters the PC removes.** Fixed 2026-11-04, W56-W60 passed twice; see the last line. (Not-compared area 4 of 7.)
  - PC: on every change it upper-cases the box and keeps only newline, 0x10, 0x1A and characters 32-127 (the JSC one-character entries, `JS8_JSC/JSC_list.cpp` 262173-262278, are all newline or in 32-127). Tabs, °, accented letters and emoji are removed, typed or pasted (`JS8_Main/TransmitTextEdit.cpp` 26-48, 358-396; `JS8_Include/commons.h` 19; `Varicode.cpp` 359-370). Every PC message passes the box, API sends included (`networkMessage.cpp` 744-748, `mw` 6650).
  - App: no filter while the box is unlocked (`MF` 694). The keyboard capitals setting (`js8_typing_box.xml` 11) does not cover a paste or a hardware keyboard. Send passes the text on as typed (`MF` 1487-1500, `JES` 3328, 3363).
  - Run on the S22-Fresh emulator: the app's frame builder (`VAR` 1332-1491) with 8 texts.
    - "hello world": sent as HELLO WORLD. The box keeps the lower case.
    - "HELLO", newline, "WORLD": the newline goes on air, as on the PC.
    - "HI 😀 THERE": sends "HI THERE", then "THERE" a second time.
    - "CAFÉ OK": sends a broken character, then "OK" a second time.
    - "TEMP 20°C", "ÉTÉ", and "A", tab, "B": the builder never finishes. One CPU core at 89 %, memory flat, stopped after 10 s.
  - Why: a character the engine can't pack gives 0 characters used, and the loop goes round again on the same text (`VAR` 1374, 1418-1422, 1474-1477; `core/src/protocol/jsc.cpp` 70-77). The PC loop is the same (`Varicode.cpp` 2110, 2185-2189, 2305-2310). The PC's Typing Box keeps those characters out.
  - Where the app runs that builder: the wpm / cpm text, a new background run on every change in the box (`MF` 870-875); and **Send** (`JES` 419, 3377; `ENG` 222). A stuck Send holds the one transmit thread, Js8Tx (`JES` 152), so later messages posted to it never run. It also holds the engine lock, so closing the engine waits (`adapters/android/jni/kotlin/JS8Engine.kt` 357-363, 369-378). The app itself was not run with these texts.
  - Fix: `JS8Engine.normalizeText` (`adapters/android/jni/kotlin/JS8Engine.kt`) is the PC rule. The Typing Box applies it to every change (`MF` pcTextFilter), and so do the three ways into the frame builder (`JS8Engine.kt` messageFrameTexts, replaceUnsentText, transmitMessage) and the last sent message (`JES` updateLastTxMessage). Checked on S22-Fresh: `build-env/test/lib/n42chk.py` W56-W60, `air5.py` (20 rows) and `ui6.py` E36, each twice.
- [x] **Grid sent on air follows the API grid.** (Not-compared area 5 of 7.) With **Allow setting station information (Grid, Info, Status, etc) from the APIs** ticked, a STATION.SET_GRID grid of 4 or more characters replaces the Station grid. Ticking the box clears an old one. PC `ui` 2479-2486; `cpp` 1172-1178, 3481-3485; `networkMessage.cpp` 205-211; sent through `mw` 3560 (buildMessageFrames), 3808, 4423, 4535, 4856, 5811. App `Schema` 512; `api/Js8Api.kt` 129-133, 150, 238-242; `api/Js8ApiCommands.kt` 109-113; sent through `JES` 3251, 3341, 4414, 4657, 5031, 5124 and `Js8Macros.kt` 30. The app keeps the API grid after a restart, the PC forgets it: HANDOFF.md "Remembered after a restart", item 5.
- [x] **N43. Callsign in the header.** Fixed 2026-11-05, W88 passed twice. PC shows "CALL - GRID" while that box is ticked and a grid is set; the grid is the API grid if one came in (`mw` 2766-2771, in guiUpdate). App shows the Station callsign alone (`MF` 437, 478). With no callsign: PC shows no callsign, app shows "NOCALL".

### Names
PC name first, app name second:
- [x] **Settings...** / Station… Rob 2026-10-19.
- [x] **Show Message Inbox...** same (`MF` 1173, 1843).
- [x] **Sort Band Activity By...** same (`MF` 1178).
- [x] **Sort Call Activity By...** / Sort Call Activity... (`MF` 1180). Fixed 2026-11-05, W102 passed twice.
- [x] **Reset Frame Sizes** same (`MF` 1175).
- [x] **About JS8Call** / About. Stays, opens the Easy JS8 About.
- [x] **JS8Call User Guide** same (`MF` 1218).
- [x] Columns **Speed**, **°**, **★** same (`MF` 594-607).
- [x] **Edit Saved Messages** / Edit Saved Messages… (`MF` 1706, 1847, 1937). Fixed 2026-11-05, W102 passed twice.

---

## 6. Call Activity and Band Activity

- [x] **Buffered GRID fills the Grid column.** PC `pCA` 86-115. App `Act` 372-378, 443.
- [x] **Third-party calls** from a relay join Call Activity with ⚟, SNR -64. PC `pCA` 436-451, `displayCallActivity.cpp` 236-240. App `Act` 445-449, `MF` 969-971.
- [x] **Icons in order** ✕ blocked, ⚑ message, ★ ACK, ☎ CQ in the last 5 minutes, ⚟ relay; ✕ red. PC `displayCallActivity.cpp` 203-247. App `MF` 964-1003.
- [x] **⚑ on group rows** with messages. PC `mw` 3076-3091. App `MF` 958-962.
- [x] **Low-confidence text in [brackets]** in Band Activity. PC `displayBandActivity.cpp` 202-204, `pDE` 301. App `Act` 348, 585.
- [x] **Each band keeps its own lists, Message Window and heard data.** PC `mw` 1279-1285, 1337, 2938-2967. App `js8ui/Js8ActivityViewModel.kt` 58-77, `MF` 1430.
- [x] **N15. A band change leaves the Typing Box alone in the app.** Fixed 2026-11-05, W89 passed twice. PC clears all activity on a band change (`mw` 1279-1285), and that empties and unlocks the Typing Box and forgets the last sent message (`mw` 2970-2990, 3001-3014). App clears the three lists only (`Js8ActivityViewModel.kt` 67).
- [x] **Heard graph** (HEARING / HEARD BY), also from a HEARING reply. PC `mw` 2327-2360, `pCA` 70, 77-83. App `Act` 377, 423-433.
- [x] **Inbox senders not in Call Activity are added.** PC `mw` 6295-6315. App `MF` 1065-1069.
- [x] **N16. Which unread messages put ⚑ on a sender.** Fixed 2026-11-05, W90 passed twice. The message store keeps no Time Delta or speed, so a row added for a sender has neither. PC counts only unread messages addressed to my callsign or its base (`mw` 6279-6293). App counts every unread message in the inbox list by its sender (`MF` 1063). A row added for an inbox sender gets only the offset and time in the app (`MF` 1067-1068); PC also fills SNR, dial, Time Delta, speed and the ★ time from the message (`mw` 6296-6314).
- [x] **N17. Callsigns (N) counts the rows shown.** Fixed 2026-11-05, W91 passed twice. App counts the shown list, which keeps an old selected call or one with a message (`MF` 955-957). PC counts the calls inside **Callsign aging** only (`mw` 3042-3054).
- [x] **N18. The ★ column.** Fixed 2026-11-05, W92 passed twice. PC shows it only while a row has an icon, and it is not in **Show Call Activity Columns...** (`displayCallActivity.cpp` 35, 257-258, 482; `mw` 4669-4690). App always shows it, and lists it as "Message / ACK / CQ icon" (`MF` 598).
- [x] **N19. Show ... Columns lists.** Fixed 2026-11-05, W93 passed twice. App lists every column in table order (`Js8TableView.kt` 417-432). PC lists Frequency Offset before Last heard timestamp, has no Message(s) entry for Band Activity, and says "Callsign" (`mw` 4669-4694). PC ticks act at once in a menu; app has a box with OK and Cancel.
- [x] **Distance** Clarke 1866, "<120" when close and a grid has 4 characters. PC `JS8_Main/Geodesic.cpp` 229, 353-374, `Geodesic.h` 78-88. App `Act` 223-275.
- [x] **Units** in the cells only with column labels off. See section 5.
- [x] **Distance from my grid as the API set it.** PC `m_config.my_grid()`. App `MF` 947.
- [x] **Buffered-to-me window 90 s.** App `Act` 401.
- [x] **Blocked stations' later frames stay hidden.** Fixed 2026-11-05, W94 passed twice: the exact offset, as the PC. Found by W94: a directed message from a blocked station returned before its offset was kept (`Act` 384), so its later frames still showed. The offset is now kept for every frame the PC hands to displayTextForFreq (`pCA` 241, `processRxActivity.cpp` 127). Match (PC `mw` 3111-3133; App `Act` 404-414). PC hides frames at the exact same offset; app hides anything within the speed's range (`Act` 410).
- [x] **Compound calls**: "<....>" frames wait for their compound frame within the speed's range (10, 16, 32, 50 Hz), are not shown before. PC `mw` 6167-6240, `processRxActivity.cpp` 70-73. App `adapters/android/jni/js8_engine_jni.cpp` 313-334, `Act` 359-361, 400.

---

## 7. Waterfall

- [x] **Green cycle line** at each period, white "hh:mm:ss    20m" text a text-height later. PC `WG` 35-40, 275-285, 685-687, `CP` 159-178, 200-205. App `WR` 224-248.
- [x] **Frequency scale bars**: gray 0-4000, yellow 500-3000, green 1000-2500 Hz. PC `CP` 64-66, 391-397. App `Js8Meters.kt` 133-138.
- [x] **N20. Colours a shade off.** Fixed 2026-11-05, W95 passed twice. Scale yellow #F2C300 and green #2ECC40 (`res/values/js8_colors.xml` 34-35); PC #F1C40F and #2ECC71 (`CP` 65-66). Spectrum top #00008B (`WR` 514); PC Qt darkBlue #000080 (`CP` 431). Grid dots Android DKGRAY #444444 (`WR` 148-152); PC Qt darkGray #808080 (`CP` 438).
- [x] **Orange WSPR mark and "WSPR"** on 30 m (10.130-10.150 MHz), 200 Hz from 10.1401 MHz, at the edge when off screen. PC `CP` 54-60, 67, 411-423, 686-688. App `Js8Meters.kt` 154-161, 172-178.
- [x] **Replot** after Start, Bins Per Pixel or a size change, from the kept lines. PC `CP` 113-127, 175, 306. App `WR` 154-200.
- [x] **Spectrum background** dark blue to black, dotted grid, 7 rows, grid step 500/200/100/50/20 Hz by span. PC `CP` 43, 86-100, 427-460. App `WR` 505-526.
- [x] **Filter minimum width** = speed bandwidth + 2 x range: Slow 45, Normal 70, Fast 112, JS8 40 224, JS8 60 350. PC `mw` 4191-4193, `WG` 593-600, `JS8_Mode/JS8Submode.cpp` 67. App `MF` 772, `Act` 152-165, `WC` 349-353.
- [x] **Set Filter Width to...** values 25, 50, 75, 100, 250, 500, 750, 1000, 1500, 2000 Hz, those under the minimum left out. PC `WG` 156-170. App `MF` 2133-2138. (Not-compared area 6 of 7.)
- [x] **Offset bracket under the scale, filter shade over it.** PC `CP` 137-155. App `Js8Meters.kt` 162-169.

---

## 8. Rig control

- [x] **Rig Control Error box and one automatic retry.** See section 5. PC `mw` 5486-5527. App `JES` 5572-5584.
- [x] **Poll reads VFO, split, Tx frequency, mode, PTT** as well as the frequency (Hamlib). PC `JS8_Transceiver/HamlibTransceiver.cpp` 978. App `JES` 5533-5570.
- [x] **Station > Radio > Mode** (None, USB, Data/Pkt) sent to the rig. App `JES` 3442-3451, 5739-5743.
- [x] **Hamlib NET rigctl, FLRig, DX Lab Suite Commander, Ham Radio Deluxe** removed from the Rig list. Rob 2026-11-03 ("zero reason to include them"), HANDOFF.md "Network rig choices removed".
- [x] **Split Rig leaves split on after key-off** and sets the Tx VFO's mode; Fake It retunes at key-on and back at key-off. PC `HamlibTransceiver.cpp` 786-880. App `JES` 5721-5791.
- [x] **N21. QSY does not move Call Activity offsets.** Fixed 2026-11-05, W105 passed twice: QSY moves every Band Activity and Call Activity offset by the dial change (`Act` 463-470, `MF` 2268-2275; PC `mw` 5340-5361).
- [x] **Rig greying**: CAT PTT needs a rig with CAT PTT, RTS greyed when the CAT port uses hardware handshake, Transmit Audio Source, Poll Interval. PC `cpp` 2771-2845. App `Schema` 155-176, `Frag` 979.
- [x] **N22. Test PTT is live before Test CAT.** Fixed 2026-11-05, W127 passed twice: greyed with Hamlib IC-7300 and DTR before Test CAT and after a red Test CAT; live with RTS PTT; greyed with None (`Schema` rigTestEnabled). Not run: Test PTT turning on after a green Test CAT needs the radio. App: live for any rig except None, Hamlib needs a PTT Method other than VOX (`Schema` 179-183). PC: with a rig, Test PTT stays grey until Test CAT turns green, then CAT (if the rig keys by CAT), DTR or RTS (`cpp` 2839-2840, 4515-4521, 4560). With Rig None the PC allows Test PTT for DTR or RTS (`cpp` 2831-2833); the app's RTS PTT rig covers that.
- [x] **Band or dial change sends my station to PSK Reporter and JS8NET again** (UNKNOWN in audit A). PC `mw` 5459-5465. App `JES` 5604-5607, 4653-4676.
- [x] **Bluetooth serial ports in the port lists.** (Not-compared area 7 of 7.) PC lists every serial port the computer has, a paired Bluetooth COM port among them, then "USB"; PTT Port adds "CAT" (`cpp` 4826-4840, 1592-1593). App lists Automatic, USB ports, then paired Bluetooth serial devices as "Bluetooth NAME (ADDRESS) Port 0" (`Schema` 190-197, `adapters/android/jni/kotlin/BluetoothSerialPortCatalog.kt`). Hamlib reads and writes the rig over it through `android/hamlib/hamlib-src/src/android_serial_bridge.c` 296-354. Automatic takes the first USB port, then the first Bluetooth port (`JES` 1419-1436, 1574-1591); the PC has no Automatic.
- [x] **N44. DTR and RTS do nothing on a Bluetooth port, and report success.** Changed 2026-11-05: `setRts` / `setDtr` return false, so Hamlib gets an I/O error and RTS PTT reports "PTT command failed". Built; not run: needs a Bluetooth serial rig. Android can't set the lines; the fix is to report a failure (section 13). App `setRts` and `setDtr` return true and change nothing (`adapters/android/jni/kotlin/BluetoothSerialBridge.kt` 268-274). So on a Bluetooth port:
  - **RTS PTT** rig: Test PTT and every key-up report OK; the line never moves (`JES` 1527-1528, 5442-5453).
  - Hamlib **PTT Method** DTR or RTS, and the **DTR** / **RTS** line settings: Hamlib gets OK, nothing changes (`android_serial_bridge.c` 488-514; `adapters/android/jni/bluetooth_serial_bridge_jni.cpp` 216-238).
  - PC hands DTR and RTS to the port like any other serial port (`JS8_Transceiver/HamlibTransceiver.cpp` 214-218, 379-385).
  - Not run: whether a Bluetooth serial device passes DTR / RTS on to a radio was not tested.

---

## 9. Audio

- [x] **Output** device can be chosen and is used. PC `ui` 1917. App `Schema` 479-480, `JES` 338, 2496.
- [x] **N23. Channel lists.** Fixed 2026-11-05, W128 passed twice: Input lists Both, always greyed; each list greys by the device's channels (`AudioDevices.greyedChannels`, the highest count Android lists); unknown channels (Phone's current audio output) keep only Mono. The emulator microphone lists stereo, so Mono shows greyed there, still selected, as the PC. App Input channel: Mono, Left, Right (`Schema` 478). PC lists Both greyed, and greys Mono on a stereo device and Left / Right on a mono one (`cpp` 4747-4783, `ui` 1959-1978). Output channel the same way, Both allowed there (`cpp` 1447-1450). App greys nothing by device.

---

## 10. Settings window

- [x] **OK refuses** a bad callsign or grid, an empty End of Transmission character, @APRSIS or @JS8NET in the groups, a bad group, a bad whitelist callsign and a bad CQ message, with the PC's words. PC `cpp` 2946-3062. App `Frag` 385-418. With **SWL (Listening only)** the callsign and grid may stay empty (HANDOFF.md "Callsign, grid, SWL").
- [x] **N24. OK checks differ in five places.** Fixed 2026-11-05, W122 passed twice: four of the five now match (`Frag` 394-422, `Js8Callsign.kt`). The fifth has no case in the app, see the last line.
  - Order: the End of Transmission character first (`Frag` 396; PC `cpp` 2947).
  - Groups: an empty entry from a stray comma is kept and refused; ABCDEF is refused; each group is checked for "cannot be joined", then for a valid name, in turn (`Frag` 402-409; PC `cpp` 2915-2929, `Varicode.cpp` 1258-1291, 1326-1347).
  - Callsign lists split on commas only, in the check and where the lists are used (`Frag` 402, `Act` 286-293, `MF` 706-707, 2670-2703, `JES` 4672, `Js8ApiCommands.kt` 346; PC `cpp` 2932-2943).
  - A greyed PTT Method: "Invalid PTT method" (`Frag` 414-421; PC `cpp` 3040-3043).
  - Not changed: the PC's audio-device and PTT-port checks (`cpp` 3008-3062) have no case in the app. Input always shows a device; PTT Port is a list with CAT as the default.
- [x] **Reply, CQ, Status, Info and Callsign Groups boxes.** Fixed 2026-11-05, W109 passed twice: typed or pasted text with a control character is refused, as the PC (`Frag` 1056-1061, 1735; PC `cpp` 207, 1491-1504). The whitelist box keeps a pasted tab, as the PC.
- [x] **End of Transmission Character Indicator:** max 2 characters, default ♢, empty refused. PC `ui` 562-571, `cpp` 2947. App `Schema` 385, `Frag` 406.
- [x] **Send SNR report when acknowledging heartbeats.** PC hides the box (`ui` 745-748). JS8_HB_ACK_SNR_CONFIGURABLE is not set, so the ACK is always "HEARTBEAT SNR" (`mw` 4454-4469). App has no box and sends the same text (`JES` 4377).
- [x] **N25. Idle timeout 1 to 4 minutes.** Fixed 2026-11-05, W110 passed twice: the typed value is used until the next start, then raised to 5 (`JES` 3685; PC `cpp` 2410-2412, 3351).
- [x] **Only autoreply to these callsigns:** OK refuses a bad callsign. PC `cpp` 2983-2991. App `Frag` 404, 409. For how the list is split, see N24.
- [x] **N26. UDP Server Port: max 65535.** Fixed 2026-11-05, W106 passed twice (`Schema` 515; PC `cpp` 1517-1519).
- [x] **Frequency table** buttons **Insert ...**, **Delete**, **Load ...**, **Save as ...**, **Merge ...**, **Reset**, and the **Only Save Selected  Working Frequencies** question. PC `cpp` 1655-1694, 4193. App `Frag` 1472-1505.
- [x] **Frequency table editing.** PC edits every cell except Frequency (MHz) in place (`FrequencyList.cpp` 284-293). App: press and hold opens the same fields filled in, titled **Edit Frequency** (`Frag` 1465-1469, 1560). This is the touch version. It is not a written decision.
- [x] **Add Frequency** fields: IARU Region, Mode (All, JS8), Frequency (MHz), Description. PC `cpp` 225-241. App `Frag` 1532-1560.
- [x] **N27. Frequency text in the Frequencies and Schedule tables.** Fixed 2026-11-05, W107 passed twice: one Frequency column "7.078 000 MHz (40m)", "OOB" outside the 30 bands, no Frequency (MHz) column; Schedule "7.078 000 MHz" (`Frag` 1465, 1637; PC `cpp` 1639-1640, `FrequencyList.cpp` 356-359, `StationList.cpp` 279-281).
- [x] **Frequency Schedule**:
  - greyed until **Automatically switch bands / frequencies at specific times of day** is ticked (PC `cpp` 1708-1709; App `Frag` 1656-1660)
  - press and hold edits a row (PC `StationList.cpp` 215-226; App `Frag` 1631-1636)
  - **Switch to Frequency Now** (PC `cpp` 1738; App `Frag` 1647)
- [x] **Add Schedule** title and labels "Frequency (MHz):", "Switch at (UTC):", "Until (UTC):". PC `cpp` 282, 295-297. App `Frag` 1664-1681. The table headings Band, Freq. (MHz), Switch at (UTC), Until (UTC), Description match (`StationList.cpp` 371-383; `Frag` 1621-1623).
- [x] **Bands**: the same 30 bands and edges, ends included. PC `Bands.cpp` 19-50, 64-75. App `util/BandUtils.kt` 19-48.
- [x] **Saved Messages**: press and hold, then drag, moves a row; double tap edits. PC `cpp` 340, 594, 1620-1624. App `Frag` 1704-1750.
- [x] **Font** buttons choose a family, a style and a size. PC QFontDialog, any installed font (`cpp` 3557, 3588, 3786, 3839, 3852). App: 4 families, 4 styles, 8-20 pt (`Frag` 189-190, 1900-1913).
- [x] **Colour** buttons choose any colour: R/G/B sliders and an HTML code. PC QColorDialog (`cpp` 3604-3610). App `Frag` 1835.
- [x] **Run Diagnostics**: Application, System, Selected and audio device lines. The PC's Qt, environment, pactl and PowerShell parts are desktop-only. PC `cpp` 4986-5120. App `Frag` 1356-1388.
- [x] **Clone** makes "<name> - Copy" without asking. PC `MultiSettings.cpp` 594-603. App `Cfg` 131-140.
- [x] **Configurations menu order**: Switch To, Clone, Clone Into ..., Reset, Rename ..., Delete. Switch To and Delete appear for other configurations only. PC `MultiSettings.cpp` 468-512. App `Cfg` 95-100.
- [x] **N28. Clone and Rename details.** Fixed 2026-11-05, W108 passed twice: **New Configuration Name** with "Old name:" and an empty "New name:" box, OK greyed at the start and while the name is in use, / and \ blocked (`Cfg` 114-190; PC `MultiSettings.cpp` 66-110). Empty name as the PC since 2026-11-05 (W125 passed twice): a name typed and deleted turns OK on, and OK saves the empty name.

---

## 11. Logging

- [x] **ALL.TXT** by that name, in UTC, with the PC's kinds of line: decodes, "Transmitting x MHz  JS8:" at the start of the period, dial lines and a date line. PC `mw` 6969-7050, `pDE` 244-245. App `MessageLogWriter.kt` 69-131 in Download/JS8CallLogs.
- [x] **N29. ALL.TXT decode line layout.** Fixed 2026-11-05, W111 passed twice: the period time, the SNR right after it, the 12-character frame, 4 spaces before the text (`MessageLogWriter.kt` 89; PC `DecodedText.cpp` 326, 340).
- [x] **N30. ALL.TXT transmit line text.** Fixed 2026-11-05, W113 passed twice: each line holds the text of its own frame (`JES` 5025, `MessageLogWriter.kt` 102; PC `mw` 2667-2670, 6994-7009).
- [x] **N55. Text of a directed frame.** Fixed 2026-11-05 (Rob: "we do what js8call does"), W126 passed twice: the PC's text, "VE3TST: K4NMA> " (`js8_engine_jni.cpp` join_tokens; the command 31 key " " as the PC's QMap key order, `core/src/protocol/varicode.cpp` unpack_directed_message). The app's readers already take "K4NMA>" (`JES` parseDirectedCommand, extractHeardCallsign) and a trailing space (Band Activity joins). Not run: a received frame through the decoder (injected decodes skip it); the same function makes it. Found by the air2 regression run. PC: the call, ": ", the call, the command as it is listed, then a space: "VE3TST: K4NMA> " (`JS8_Main/DecodedText.cpp` tryUnpackDirected, the command ">" has no space before it, `Varicode.cpp` 68). App: the parts joined with a space and no space at the end: "VE3TST: K4NMA >" (`adapters/android/jni/js8_engine_jni.cpp` 336-340, 467-487). Seen in the ALL.TXT transmit lines since N30. The same text is used for every decode the app shows, so a received relay frame likely shows "K4NMA >" too (not checked on screen). The app's readers of that text (relay, DIRECTED.TXT, the API) would all need checking before it changes.
- [x] **Dial lines below 30 MHz only**, on a dial change. PC `mw` 5455-5457, 6969-6991. App `MessageLogWriter.kt` 87-93, `JES` 5606.
- [x] **N31. ALL.TXT date line.** Fixed 2026-11-05, W112 passed twice: before decode lines only, the first after start, after the UTC day turns and after **Erase ALL.TXT**; cleared at the end of each decode cycle, as the PC (`MessageLogWriter.kt` 31; PC `UI_Constructor.cpp` 43, `mw` 2104-2105, 2188, 4227, 7032-7038).
- [x] **DIRECTED.TXT** waits for the whole buffered message and drops a bad checksum. It skips <....> and non-commands, adds the End of Transmission character on the last frame, and uses tabs with dial MHz to 6 places. PC `pCA` 42-49, 127-156; `mw` 7052-7076. App `JES` 2121-2155, `MessageLogWriter.kt` 98-101.
- [x] **N48. DIRECTED.TXT and RX.DIRECTED skip text sent to a call, and cut some commands short.** Fixed 2026-11-04, W64-W66 passed twice.
  - Text to a call, such as "VA3OSO: K1ABC HELLO THERE" (command " "): PC writes it to DIRECTED.TXT and sends RX.DIRECTED. " " is an allowed command, and it waits for the last frame (`Varicode.cpp` 115-116, 120-122, 1234-1242; `pCA` 47). App skips it: its command list has no " ", and its reader gives free text an empty command (`util/Js8Commands.kt` 23-50; `Act` 305-318; `JES` 2125).
  - A command outside the buffered list with more frames after it, such as ACK or 73 followed by text: PC waits for the last frame for every command with a space in its name, which is every command except "?" (`Varicode.cpp` 50-118, 1239-1242; `pDE` 469-472). App writes and sends it at the first frame, with that frame's text only (`JES` 2127-2133, 2150-2160).
- [x] **N49. Relay text in DIRECTED.TXT and RX.DIRECTED.** Fixed 2026-11-05, W114 passed twice: "K4YYY> K4ZZZ HELLO" (`JES` 2203-2206; PC `pCA` 131).
- [x] **N50. When an unfinished message is closed.** Fixed 2026-11-05, W115 passed twice: 60 s after its newest frame if a text frame came, dropped at 90 s, checked on the clock as well as at each decode (`JES` 2133-2139, 3670, 4161, 4785, 4964; PC `processBufferedActivity.cpp` 18-40). The app checks every second; the PC once a period.
- [x] **N32. DIRECTED.TXT time.** Fixed 2026-11-05, W116 passed twice: the drifted clock (`MessageLogWriter.kt` 120; PC `mw` 7062).

---

## 12. API and spotting

- [x] **RX.DIRECTED** waits for the whole buffered message and drops a bad checksum. Fields: _ID, FROM, TO, CMD (leading space), GRID, EXTRA, TEXT, FREQ, DIAL, OFFSET, SNR, SPEED, TDRIFT, UTC. PC `pCA` 127-176. App `JES` 2121-2160, `api/Js8Api.kt` 436-444.
- [x] **RX.DIRECTED for text to a call, and for commands outside the buffered list.** See N48, fixed 2026-11-04.
- [x] **RX.GET_BAND_ACTIVITY, RX.ACTIVITY, RX.DIRECTED** carry the dial at decode. PC `JS8_Mainwindow/networkMessage.cpp` 564-583, `processRxActivity.cpp` 24-34, `pDE` 305. App `api/Js8ApiCommands.kt` 257-268, `JES` 2094, 2159.
- [x] **UTC** from the drifted clock. PC `pDE` 308. App `JES` 2089, 4860.
- [x] **N33. TDRIFT and Time Delta with auto time sync.** Fixed 2026-11-05, W117 passed twice: the clock drift on Normal and Slow with auto sync on, the decode's offset otherwise, in RX.ACTIVITY, RX.DIRECTED, the Time Delta column and `<TDELTA>` (`JES` 2245-2251). W117 checks the API; the Time Delta column (hidden by default) and `<TDELTA>` take the same value and were not run.
- [x] **STATION.SET_GROUPS** refuses @APRSIS, @JS8NET and bad names with the PC's ERROR text, and skips names without @. PC `networkMessage.cpp` 462-496. App `Js8ApiCommands.kt` 218-236.
- [x] **N34. STATION.SET_GROUPS with lower case.** Fixed 2026-11-05, W118 passed twice: the name is checked as sent (`Js8ApiCommands.kt` 223; PC `networkMessage.cpp` 466-483).
- [x] **TX.FRAME** once per frame, with that frame's tones. PC `mw` 2620, 6714. App `JES` 259-263, 2163-2170.
- [x] **STATION.CLOSING** on close, also when the app is swiped away. PC `mw` 1477-1482. App `MainActivity.kt` 440-441, `JES` 577-579.
- [x] **PSK Reporter** spots heard calls with or without a grid, and skips the spot blacklist. RX.SPOT goes with each spot. PC `mw` 2456-2467, 6529-6575. App `JES` 4753-4778.
- [x] **N35. Spots right after a dial change.** Fixed 2026-11-05, W119 passed twice: spots wait 4/5 of a period after the dial changes, then go with the dial at decode, to the API (RX.SPOT), PSK Reporter and JS8NET (`JES` 306, 4864; PC `mw` 6529-6575).
- [x] **PSK antenna** = Station Info with macros expanded. PC `mw` 5548-5552. App `JES` 4667-4669.
- [ ] **N36. PSK Reporter program name.** PC sends "JS8Call 3.0.3", name and version with a space, to PSK Reporter and JS8NET (`JS8_Main/main.cpp` 91-92, 275; `revision_utils.cpp` 28-32; `UC` 68-69). App sends "EasyJS8-<version>" to PSK Reporter (`JES` 4638) and "Easy JS8 <version>" to JS8NET (`JES` 4673). Kept as is (Rob, 2026-11-04), section 13.
- [x] **JS8NET TEXT**: only a CQ carries its text. PC `pDE` 377-397, 444-470. App `JES` 4724-4726.

### PSK Reporter packets (not-compared area 2 of 7)

PC `JS8_Network/PSKReporter.cpp`. App `network/PskReporterClient.kt`.

- [x] **Bytes match.**
  - IPFIX header: version 10, length, export time, sequence, observation ID.
  - Sender and receiver descriptors, the same fields in the same order, sent 3 times at the start and again every hour.
  - Receiver record: call, grid, program, antenna.
  - Spot record: call, 5-byte frequency, SNR, mode, grid, source 1, time.
  - 4-byte padding; UTF-8 cut at 254 bytes on a character boundary; 10000-byte limit.
  - PC 97-135, 148-180, 189-263, 350-356, 406-407, 443-490, 528-543, 550. App 54-59, 149-166, 241-386, 400-410, 518-522.
- [x] **N37. When packets go.** Fixed 2026-11-05, W121 and W124 passed twice: every 120-125 s once the spots pass 508 bytes; every 4th time whatever waits, a station-only packet with no spots (`PskReporterClient.kt` 211, 479; PC `PSKReporter.cpp` 50-52, 425-427, 505-556).
- [x] **N38. Sequence number.** Fixed 2026-11-05, W120 and W124 passed twice: 1 per packet from 1 (`PskReporterClient.kt` 196; PC 448).
- [x] **N39. Eclipse dates.** Fixed 2026-11-05, W124 passed twice (unit test; the dates can't be reached on the phone): the 15 dates from `eclipse.txt`, 6 hours each side (`PskReporterClient.kt` 133-134, 396, 489).
- [x] **N40. Band for the 1-hour repeat limit.** Fixed 2026-11-05, W124 passed twice (unit test): the 30 bands (`PskReporterClient.kt` 124; PC 693-694).
- [x] **N41. Smaller differences.** Fixed 2026-11-05.
  - Export time from the drifted clock: W120 passed twice (`PskReporterClient.kt` 16; PC 572-573).
  - A socket error drops the waiting spots (`PskReporterClient.kt` 262; PC 385-397). Read only, not run.
  - Sends with an empty callsign or grid: W120 passed twice (with SWL on: the app keeps the Station window open while the grid is empty, `Frag` 323, 464).

---

## 13. Not gaps

From audit A, checked again:
- 3.0.3 has no Help > keyboard shortcuts or special mouse commands items, no Open Save Directory, no separate DX grid, no receive-audio saving (`mainwindow.ui`).
- Country data (cty.dat) is loaded on the PC (`mw` 4260) but nothing uses it for colouring.
- Hidden or compiled out on the PC: **IARU Region:**, **HB Message:** (`ui`, `visible` false), InsertBlank, DXCC, PrincipalPrefix.
- No use on a phone: Show Tooltips, Set Focus..., F-key shortcuts, dock and float of the Message Inbox, wheel offset change, hover dial, command-line options, lock file, single-instance check.
- Already deliberate in `HANDOFF.md`: header tap sorts, upright has no frequency block or menu bar, AUTO kept across restart, Easy JS8 About text, Bins Per Pixel 6 and 2, **Disable message relay** default checked, APRS only while RX is on, JS8NET PATH empty, API values kept after restart, hidden Save Directory, PTT command, Application stylesheet, Application Font.

New in audit B:
- Network rigs removed (Hamlib NET rigctl, FLRig, DX Lab Suite Commander, Ham Radio Deluxe). Rob 2026-11-03, section 8.
- OmniRig's RTS / DTR through the CAT port (`has_CAT_indirect_serial_PTT`, `JS8_Transceiver/TransceiverFactory.h` 47): OmniRig is Windows software.
- **Send SNR report when acknowledging heartbeats** is hidden on the PC, so neither has it (section 10).
- The Qt Varicode backend (`adapters/qt/src/varicode_adapter.cpp` 251-259) is not used on Android, so the app engine's own code is what runs (section 1).
- Serial port **Automatic** and Bluetooth device names in the port lists: phone versions of the PC's COM port list (section 8).

Kept different on purpose (Rob, 2026-11-04, "99 will be fine"):
- **@APRSIS MSG / MSG TO: with no checksum** (section 1): the app takes these messages. Matching the PC would drop every APRS-to-JS8 message.
- **N36, PSK Reporter program name** (section 12): the app keeps its own name, "EasyJS8-<version>" to PSK Reporter and "Easy JS8 <version>" to JS8NET, so its spots are not listed as JS8Call's.
- **N44, DTR and RTS on a Bluetooth port** (section 8): Android's `BluetoothSocket` has only connect, close and the two data streams, with no way to set DTR or RTS (developer.android.com, BluetoothSocket reference, read 2026-11-04). The fix is to report a failure in place of success; the lines can't be driven.

---

## 14. Downsides of matching the PC

From audit A:
- Decoder stages use more CPU and battery. The emulator decoder test (2026-10-31) measured about 30 % more CPU. No phone figure yet (`HANDOFF.md`, the line starting "Decoder test").
- Spotting defaults on: new installs send to real networks unless changed.
- Autoreply default on: new installs answer on air by themselves.

New in audit B:
- **@APRSIS checksum** (section 1): demanding a checksum, as the PC receiver does, drops every APRS-to-JS8 message. The PC's own relay never gets one through.
- **Heartbeat interval 30 minutes** (section 3): a new install with HB turned on sends a heartbeat every 30 minutes by itself.
- **N1, AUTO off**: replies to you appear in the Typing Box for you to send.
- **N9, Send greyed**: you can't press Send while a heartbeat or reply waits in the queue.
- **N12, Clear All Activity**: clears at once, with no question.
- **N15, band change**: what you are typing is cleared, and the last sent message is forgotten, whenever the band changes.
- **Show Message Inbox... title**: the box is titled "Message Inbox", as the PC dock; it no longer names the call.
- **Set Focus...** (N13): on a phone, Message Receive Area, Band Activity Table and Call Activity Table only bring the area into view; Message Reply Area also opens the keyboard.
- **N25, idle timeout 1-4**: the value you typed turns into 5 at the next start.
- **N36, program name**: PSK Reporter and JS8NET would list Easy JS8 as "JS8Call 3.0.3", so its spots can't be told apart from the PC's.
- **N37, PSK Reporter timing**: a packet every 2 minutes once 508 bytes of spots are waiting, and about every 8 minutes whatever is waiting, a station-only packet if there are no spots. More mobile data than the app's one packet every 10 minutes.
- **N42, Typing Box filter**: characters the radio can't send disappear from the box as you type or paste.
- **N45, directed pattern**: "K1ABC: HELLO" with a colon goes out as plain text, no longer as a message to K1ABC.
- **N48, DIRECTED.TXT and RX.DIRECTED**: ACK, 73, SNR and other commands with text after them are written at the last frame, not the first. Text to a call has two spaces after the call ("K1ABC  HELLO"), as the PC writes it.
- **N24, OK checks**: OK now refuses a whitelist with spaces between the calls ("K1ABC K2DEF"), a group without a letter-digit pair (ABCDEF) and a stray comma in the groups. Settings saved before may need editing at the next OK.
- **N34, STATION.SET_GROUPS**: a program that sends group names in lower case gets "Invalid group name".
- **N35, spots after a dial change**: spots wait about 12 s (Normal) after you change the dial.
- **N41, empty grid**: PSK Reporter gets your station with an empty grid.
- **N50, unfinished messages**: a message cut short is written 60 s after its newest frame; a message with only its first frame is never written.
- **Turning spotting off** sends what waits at once, as the PC (`mw` 1044-1047).

---

## 15. Notes

- Totals (2026-11-05, sections 1-7 fixed): 206 items match ([x]), 29 differ ([ ]). N1-N50 were new in audit B; N51-N53 were found while fixing section 1, N54 while testing sections 2-7. Before the fixes: 167 match, 64 differ.
- All 7 "Not compared" areas from audit A are compared above: area 1 and 3 in section 1, area 4 and 5 in section 5, area 6 in section 7, area 7 in section 8, area 2 in section 12.
- N42 was the one item in this audit that hung part of the app: a pasted °, a tab, or a word starting with an accented letter made the frame builder loop for ever. Fixed 2026-11-04 (section 5).
- N44: Android can't set DTR / RTS on a Bluetooth serial link: `BluetoothSocket` has no call for it (checked 2026-11-04, section 13).
- N7 and N28 first said different things about the PC's second-copy name. Both now say "- Copy(2)", read from `MultiSettings.cpp` 594-603.
- `HANDOFF.md` is out of date in two places:
  - The line starting "The API's grid / info / status" says they are "not kept after restart, like the PC". The code keeps them (`api/Js8Api.kt` 145), as "Remembered after a restart" item 5 says.
  - The line starting "Differences from the PC: no autoreply confirmation" still lists STATION.AUTOREPLY_CONFIRM_*, INBOX.STORE_MESSAGE and MODE.SET_SPEED with JS8 60 as not working. Audit A found all three work. A later `HANDOFF.md` line also says INBOX.STORE_MESSAGE "holds and returns the id".
