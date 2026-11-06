# Easy JS8 — Handoff (2026-10-20, end of the third session)

## Next session starts here (2026-11-05): audit B sections 1-12 are fixed

**State at 19:58, 2026-11-05: tablet header buttons and RTTY decodes, not committed**
- Tablet (smallest width 600 dp or more), upright: header buttons half their stretched width, centred (`Js8MainFragment.kt`, `fragment_js8_main.xml` id `js8_header_buttons_row`). Checked on SM-X210 and S22-Fresh.
- RTTY decodes added and FT8 decodes now start off: see "RTTY monitor (2026-11-05)". W131-W135 passed twice; W136 (junk from noise) failed twice, Rob keeps fldigi's squelch.

**State at 11:15, 2026-11-05 (read this block first): N55, N22, N23, N44 and Rename written as the PC, regressions on the new build**
- Rob, 11:00: "there is no desicions for me, we do what js8call does". So N55, Rename (empty name turns OK on) and N22 / N23 / N44 follow the PC; audit B items marked [x] with what was not run.
- Built 10:55 and installed on S22-Fresh. W125-W128 (`s812chk.py`, end of file) passed twice: outputs `build-env/test/lib/s812chk.n55.{1,2,3,4,5}.out`. W125 runs sideways (the Configurations menu bar); W127 waits up to 20 s for the red Test CAT (the app gives up after 15 s).
- s27chk measuring run: 27/27 passed; its estimates set at about 1.2 times the END times.
- N52 and N53 also follow the PC (W129, W130 in `s1chk.py`, passed twice). API-sending test rows now run with AUTO on. Committed before them: 09724615 (pushed to fork). Run 11 was stopped for this build.
- Regression passes 1 and 2 on the N52/N53 build: `regress.sh 12` and `13` (11:46-14:13). Every row has 2 passes on this build: air2 53/53 twice, air3 29/29 twice, air1 22/28 twice (the 6 old checks: C03 B08 B10 B11 B17 D01; air2 has the corrected ones), n42chk 5/5 and n48chk 6/6 twice, s1chk W67-W74 runs 12-13, W75-W76 runs 13 and 49, W129-W130 run 13 plus 31-34. s27chk: the rows not listed here passed runs 12 and 13; W79, W81, W91 passed runs 43-44, W103 runs 40-42, W90 runs 47-48.
- Test fixes from these runs: `regress.sh` limits for s1chk (1290 s) and s27chk (2200 s) from the summed row estimates (528 s stopped s1chk in W75). s27chk W90 empties the emulator inbox first (`databases/js8_messages.db*`): each run left an unread message, its sender stays in Callsigns, and past 100 rows the row was out of sight.
- Long runs slow the emulator: rows that read the screen fail (W90, W91 timed out; W103 found no Send button). W103's cause was the test, not the emulator: HB was tapped with under 1 s left in the 10 s slot (**Ready (10s)**, PC `updateTxButtonDisplay` counts to the end of the message), so the heartbeat went before the typed text (run 53). `slot_wait()` in s27chk.py now taps HB 3-5 s into a slot by the emulator's clock (about 1 s behind the PC's); W81 has the same wait. Runs 54 and 55: s27chk 27/27 twice. regress.sh restarts S22-Fresh once per pass, right before s27chk, with `build-env/test/lib/emurestart.ps1` (est 50 s, limit 55 s; measured 41-44 s, once 51 s). It runs `sync` before the kill (a kill without it left empty bytes in ALL.TXT), force-stops a close that takes over 15 s, waits for `sys.boot_completed`, then for `/sdcard/Download` (storage mounts a few seconds later). PowerShell lesson: `-notmatch` on a one-item list filters the list, so a boot wait ended at once; compare a trimmed string with `-ne`. `tap_item` in s27chk.py waits up to 5 s for a menu to open (W99 run 50).
- A Monitor that expires leaves its `tail -F` processes running; stop them (`tail.exe` with `.out` / `.log` in the command line) after the runs.
- Killing the emulator can leave empty bytes at the end of ALL.TXT; the next transmit line joins them, and a row reading from the line count misses it (W129 run 33).
- Radio last: N44 on a Bluetooth serial rig; N22 Test PTT turning on after a green Test CAT.
- `ui9.py` M27 now expects Both in the Input channel list (N23). `wham2.py` uses the network rig, removed 2026-11-03: it can't run.

**State at 10:15, 2026-11-05 (older): sections 8-12 done, regression passes under way**
- W105-W124 have 2 passes each; rows are in `TEST-CHECKLIST.md` after W104, items marked [x] in `AUDIT-3.0.3-B.md`, section 14 downsides added. Outputs `build-env/test/lib/s812chk.{0,1,2,2b,2c,2d,2e,3}.out`.
- W120 runs with SWL on: the app keeps the Station window open while the grid is empty (`Js8SettingsFragment.kt` 323, 464, a recorded decision); SWL lets it go and stops only sending.
- **Row timers:** `build-env/test/lib/parts.py` `part(name, est, ids, res, mark)`: each row, probe and restart its estimate and a limit 10% over, enforced in the script (FAIL "timed out", run goes on). `T.expect` / `T.silent` (`onair.py`) and `fresh` (`onair2.py`) are timed inside; `s812chk`, `n42chk`, `n48chk`, `s1chk`, `s27chk` wrap each `if want(...)` row. `PARTS_SCALE=3` triples the limits for a measuring run.
- **Estimates for n42chk, n48chk, s1chk, s27chk were guessed from the waits in each row and were wrong** (helpers wait too): W59-W63 and W67-W75 timed out. Measuring run `measure.sh 10 ...` (output `measure.10.log`, `<script>.10.out`, END lines) sets them; then put the measured times in the `with part(...)` lines.
- **N30 changed the ALL.TXT transmit lines to one frame per line; the regression scripts read one line.** Fixed: `onair.tx_joined` (frames joined two ways: no space, and one space), used by `T.tx`, `sent()` in n42chk/n48chk/s27chk, s1chk W70, air1/air2 MSG ID; `T.expect` waits 15 s longer; air2 D09 pattern takes "K4NMA >"; air3 F01-F13 read the whole STATUS reply; air3 G03 retired (the PC writes no ALL.TXT line on a frequency change, N31; checklist row noted).
- Regression pass 1 (run 10) so far: air2 51/53 (B10, D09 test script, fixed since: rerun), air3 23/30 (F rows and G03, fixed since: rerun), air1 22/28 (the same 6 old fails as since 2026-11-01).
- **N55 new (audit B section 11), waits for Rob:** the app's text of a directed frame is "VE3TST: K4NMA >"; the PC's "VE3TST: K4NMA> " (`js8_engine_jni.cpp` 336-340, 467-487 against PC `DecodedText.cpp` tryUnpackDirected).
- **N44, N23, N22 not written, side effects told to Rob first:** N44 a Bluetooth rig with a forced DTR / RTS line or PTT Method DTR / RTS on the CAT port won't open; N23 Mono greyed on a phone mic that lists 1 and 2 channels (Mono is the default); N22 Test PTT greyed until Test CAT is green.
- Stopping a run: TaskStop stops only the bash wrapper; `regress.sh` went on to the next script and two scripts ran on the emulator at once (stray replies). Kill the python, `timeout` and `bash regress.sh` processes and check none are left.
- An air2 run of one or two rows still runs the script's injections and restarts between rows (only rows are skipped).

**State at 08:40, 2026-11-05 (older): sections 8-12 with N54, code done, testing**
- Test script `build-env/test/lib/s812chk.py` (W105-W123); unit tests `PskReporterClientTest.kt` (W124) passed twice in Docker. Draft rows: scratchpad `s812rows.md`; draft audit notes: scratchpad `audit_s812.md` (session 40149c7c). Copy both into `TEST-CHECKLIST.md` (after W104) and `AUDIT-3.0.3-B.md` once every row has 2 passes.
- Passes so far: run 1 (`s812chk.1.out`) 12 rows: W106 W107 W109 W110 W118 W114 W116 W123 W115 W108 W113 W121. Run 0 (single rows, `s812chk.0.out`): W105 W111 W112. Run 2 (`s812chk.2.out`, `s812chk.2b.out`): W122 W106 W107 W109 W110 W118 W114 W116 W117 W123 W115.
- **Before any more runs:** give every row in `s812chk.py` its own estimate and a limit 10% over, enforced in the script (past it: FAIL "timed out", go on to the next row). Check the watcher emits a line before trusting it. Memory note `rob-approved-change-first.md` rule 7.
- Still needed for 2 passes: W105 W108 W113 W111 W112 W119 W120 W121 (run 2), then W122 W117 W119 W120 once more (run 3). Then regressions twice (air2, air3, air1, n42chk, n48chk, s1chk, s27chk), then N44, N23, N22.
- Script fixes this session: the PC clock is 44 s ahead of the emulator clock, so W111 and W120 use `dev_clock()`; W112 opens the **Erase ALL.TXT** box before the frame plays and taps **Yes** right after it goes in; W105 clears Call Activity first (**Control > Clear Activity... > Clear Call Activity**).
- Rotation after an emulator restart: the home screen locks the screen upright (`SCREEN_ORIENTATION_NOSENSOR`), so `rotate('P')` does nothing until the app is open. Open the app (`fresh(**UP)`) first, then `rotate('P')`: checked 2026-11-05, 2340x1080. Start the emulator from PowerShell `Start-Process`; the Git Bash one froze, then died.
- Draft files full path: `C:\Users\Robert\AppData\Local\Temp\claude\C--Users-Robert--claude-spares-spare-xtlgOf\40149c7c-b565-498e-a8bd-741dd9b66513\scratchpad\` (`s812rows.md`, `audit_s812.md`).
- (Superseded 11:00: Rename now lets an empty name through, as the PC.)
- Stopping a background task stops only its `timeout` wrapper; also stop the `python s812chk.py` process.

**State at 05:20, 2026-11-05 (older):**
- **Plan:** unchanged, see the first line of the 20:25 block below. Done: section 1, then sections 2-7. Next: sections 8-12 with N54, then the phone and radio items (N44, N23, N22). Nothing committed; commit only when Rob says, to `fork` only.
- **Section 1 done (2026-11-04):** N51 (decodeReceiver, monitorReceiver and idleReceiver run from `onCreate` to `onDestroy`, `MainActivity.kt`), HEARING? and QUERY CALL read Call Activity, a relay to my group is ACKed and not forwarded, the timed heartbeat waits for an empty Typing Box, N1-N4. Files: `MainActivity.kt`, `service/JS8EngineService.kt`, `ui/TransmitViewModel.kt` (the queue takes the first message that may go). W67-W76 (`build-env/test/lib/s1chk.py`) passed twice. W76 needs `fakespot.py` running on the PC.
- **Sections 2-7 done (2026-11-05):** every open item is [x] in `AUDIT-3.0.3-B.md` with its test. W77-W104 (`build-env/test/lib/s27chk.py`) passed twice (passes 1 and 3 all 28; pass 2 failed W90 on the test script, fixed, then W90 passed 3 times). Main files: `js8ui/Js8MainFragment.kt` (menus, Control and View menu order, columns, status label, header callsign, idle timeout), `js8ui/Js8TableView.kt` (★ column, Show Columns menus), `js8ui/Js8Meters.kt` (0-100 scale, red on the loudest sample), `js8ui/Js8Activity.kt` (blocked offsets), `js8ui/Js8Macros.kt` (`<MYHB>`), `js8ui/Js8Configurations.kt` (clone names), `js8ui/Js8SettingsSchema.kt` and `api/Js8ApiCommands.kt` (heartbeat 30 min), `js8ui/Js8ActivityViewModel.kt` (band change empties the Typing Box, **Send** greyed while a heartbeat waits), `js8ui/Js8InboxFragment.kt` (inbox title), `ui/MonitorViewModel.kt` (CAT orange while the rig opens), `ui/WaterfallRenderer.kt` and `res/values/js8_colors.xml` (colours), new `res/drawable/js8_shape_orange.xml` and `js8_status_black.xml`.
- **App bug found by W94 and fixed:** a directed message from a blocked station returned before its offset was kept (`Js8Activity.kt`), so its later frames still showed.
- **Read only, not run:** the meter's red on the loudest sample; the CQ sub-menu of the **Control** menu; the service's heartbeat default 30 (`JS8EngineService.kt` 3132).
- **N54 found and open** (audit B section 1): INFO? and STATUS? with text that fills in empty. Goes with sections 8-12.
- **N52 and N53 wait for Rob** (audit B section 1): a relay whose text starts with "@" is dropped; TX.SEND_MESSAGE sends with AUTO off.
- **Downsides added to audit B section 14:** the **Show Message Inbox...** box is titled "Message Inbox" and no longer names the call; **Set Focus...** on a phone only brings three of its areas into view (Message Reply Area also opens the keyboard); N16 rows added from a stored message have no Time Delta or speed.
- **Regression, runs 6 and 7 (03:09-04:57):** `air2.py` 53/53 and `air3.py` 30/30 both runs; `n42chk.py` 5/5 and `n48chk.py` 6/6 both runs; `air1.py` 22/28 both runs, the same 6 old checks as since 2026-11-01 (C03, B08, B10, B11, B17, D01). `s1chk.py` 8/10 both runs: W68 and W69 failed because the Call Activity menu, taller with the N5 lines, runs past the screen and **Remove This Station** was below it. `s1chk.py` now scrolls the menu (`tap_menu`); W68 and W69 passed twice (runs 8 and 9), and the scroll was checked once from the lowest row.
- **Test notes (2026-11-05):**
  - A swipe that starts near the top edge pulls down the quick settings panel; near the bottom edge it is the Home gesture. Keep menu swipes 250 px clear of the bottom.
  - A swipe in a sliver of a list becomes a press and hold and opens a menu.
  - With a menu open, the uiautomator dump holds only the menu window (no main screen nodes); read the screen size from `wm size`.
  - Call names from `c()` repeat across runs (`T.n` is time-based); an old run can leave a message from the same call.
  - Upright, Call Activity cells right of the visible width are off screen; read them with the API (RX.GET_CALL_ACTIVITY, RX.GET_CALL_SELECTED).
  - In Git Bash, adb needs its full path `/c/Users/Robert/AppData/Local/Android/Sdk/platform-tools/adb.exe` and `export MSYS_NO_PATHCONV=1`.
  - Restart the emulator after long runs (slow frames, ANRs).

**State at 20:25, 2026-11-04 (older):**
- **Plan (Rob, 2026-11-04 "id like to fix all items", "99 will be fine"):** fix every open item in `AUDIT-3.0.3-B.md` to match the PC, except the @APRSIS checksum and N36, kept as they are (audit section 13). N44: report a failure, Android can't set DTR / RTS on Bluetooth. Heartbeat default goes to 30 minutes, as the PC. Order: section 1, then 2-7, then 8-12, phone and radio items last (N44, N23, N22). Each batch built, tested twice on S22-Fresh, recorded before the next.
- **N45-N48 fixed (Rob, 2026-11-04 "N45-N48 next"):** `core/src/protocol/varicode.cpp` uses the PC directed pattern (N45) and compound pattern (N46, start of the line only, SNR number read in code), and the PC callsign search for the selected-call check (N47, `parse_callsigns`). `JS8EngineService.directedActivity` writes free text to a call and holds every command but "?", CQ and heartbeats until the last frame (N48). W61-W66 (`n48chk.py`) passed twice. Reruns twice: `n42chk.py`, `air2.py` (53 rows) and `air3.py` (30) all passed; `air1.py` failed C03, B08, B10, B11, B17 and D01 both times, the same as its 2026-11-01 run (old checks; `air2.py` has the corrected ones). Not committed.
- Found while fixing N48, now audit items N49 and N50 (section 11, not changed): a relay in DIRECTED.TXT reads "K1ABC > TEXT", the PC writes "K1ABC> TEXT". An unfinished buffered message is written 60 s after its first frame, checked only when the next decode arrives; the PC closes it 60 s after its latest frame, drops it at 90 s if no text frame came, and checks once a period.
- **N42 fixed (Rob, 2026-11-04 "Start with N42"):** the PC Typing Box rule (upper case; newline, 0x10, 0x1A, 32-127 only) is `JS8Engine.normalizeText`, used by the Typing Box (`Js8MainFragment` pcTextFilter), the three frame-builder entries in `JS8Engine.kt` and `updateLastTxMessage`. W56-W60 (`n42chk.py`), `air5.py` (20 rows) and `ui6.py` E36 passed twice on S22-Fresh. `air5.py`'s own E36 check is the old one and always fails; `ui6.py` has the corrected one. Not committed.
- Seen, not changed: a heartbeat or reply shown in the Typing Box while it is sent can lose a space between its parts ("N42 MW CHECK" showed as "N42MW CHECK"; `broadcastTxFrame` joins the frame texts with ""). The Message Window showed it right.
- `AUDIT-3.0.3-B.md` is done: 167 items match, 64 differ after the N45-N48 fixes and N49-N50 (N1-N50 new in audit B, 20 carried from audit A). All 7 not-compared areas are compared. Every audit A item in sections 5, 8, 9 and 10 was matched to an audit B item. Nothing committed.
- **Worst finding, N42 (section 5), now fixed:** a pasted °, a tab, or a word starting with an accented letter made the engine's frame builder loop for ever (`core/src/protocol/varicode.cpp` 1374, 1418-1422, 1474-1477).
- Other new on-air items: N45-N47 (section 1, frame building) and N48 (section 11, DIRECTED.TXT and RX.DIRECTED), all fixed. N44 (section 8): DTR / RTS on a Bluetooth serial port do nothing and report success.
- Two API lines in this file are out of date ("The API's grid / info / status" and "Differences from the PC: no autoreply confirmation"); see audit B section 15.
- **Found 2026-11-04, not in the audit yet, not changed:** with the screen off the app sends no replies, and Call Activity, Band Activity and the Message Window stop updating. `MainActivity.kt` registers its five receivers (decodeReceiver, monitorReceiver, hopReceiver, confirmReceiver, idleReceiver) in `onStart` (381-416) and unregisters them in `onStop` (422-434). Replies go out through decodeReceiver: the service's `queueAutoReply` sends ACTION_QUEUE_TX (`JS8EngineService.kt` 3554, 3652-3669), and `MainActivity.kt` 64-71 queues it. Test: `build-env/test/lib/screenoff.py`; screen on, the SNR? query was answered ("... K4SCA SNR -08"); screen off, no answer in 40 s. Proposed to Rob as N51: keep the receivers on from `onCreate` to `onDestroy`; side effect, more battery use with the screen off. Rob has not decided. It comes before the rest of section 1, because HEARING?, N1 and N2 depend on that queue and on Call Activity.
- **Section 1 design note (not written):** HEARING? and QUERY CALL read the on-screen Call Activity (`Js8Activity.callActivity`, `Js8Activity.kt` 39-63) through a MainActivity hook, read on the main thread (QUERY CALL runs inside a coroutine, `JS8EngineService.kt` 4296). Today they read the service's own `heardCallsigns` (`JS8EngineService.kt` 196, 5165-5199; uses at 4296, 4419, 5037).
- **Next:** Rob's answer on N51, then section 1 in this order: HEARING? / QUERY CALL, relay to my @GROUP (ACK or answer, not forwarded, pCA 410, 424), timed HB waits for an empty Typing Box (mw 4450, 6625-6628), N1, N2, N3, N4. No section 1 code is changed yet. Fixes are made and tested on the PC first, then the phone and radio tests, then the first GitHub release.

**The plan the audit followed (2026-11-04, before it started):**

- **Output:** a new file `AUDIT-3.0.3-B.md`. Leave `AUDIT-3.0.3.md` (2026-10-31, commit `329260bc`) as it is.
- **Sections:** the old file's 14 sections, in its order: 1 decoding and on-air behaviour, 2 bugs in existing features, 3 wrong defaults, 4 macros, 5 main window and menus, 6 Call Activity and Band Activity, 7 waterfall, 8 rig control, 9 audio, 10 settings window, 11 logging, 12 API and spotting, 13 not gaps, 14 downsides of matching the PC.
- **Old items:** re-check all 176 [x] items in `AUDIT-3.0.3.md` against today's code (commit `a38c4077` or later). Many were ticked from background checks and never read one by one.
- **New areas:** the old file's 7 "Not compared" items: bit-level pack_directed / pack_callsign against `Varicode.cpp`; PSK Reporter packet bytes; frame order (heartbeat, compound, directed) against core `varicode.cpp` 1409; Typing Box filter for pasted or hardware-keyboard text; header grid with use_dynamic_grid (`mw` 2765); Set Filter Width menu values against `WideGraph.cpp` 156; Bluetooth serial paths.
- **Rule:** every item is read in both sources by the main session, with the PC file and line and the app file and line. Agents may list candidates; nothing goes in the file on an agent's word alone.
- **No timers:** the audit is not a test, so no job table, estimates or limits (Rob, 2026-11-04: "there are only time limits on tests").
- **JS8Call 3.0.3 source on this PC** (git checkout, tag `v3.0.3`, commit `4c592bd`):
  `C:\Users\Robert\AppData\Local\Temp\claude\E--new-w3bguru-websites-easy-js8\e0f3f7cc-5c4b-456d-a816-63b6a2c8c4e0\scratchpad\pc303\`
  Main files: `JS8_UI/mainwindow.cpp`, `JS8_Mainwindow/UI_Constructor.cpp`, `JS8_Mainwindow/processCommandActivity.cpp`, `JS8_Mainwindow/processDecodeEvent.cpp`, `JS8_Mainwindow/processBufferedActivity.cpp`, `JS8_Mainwindow/buildQueryMenu.cpp`, `JS8_Mainwindow/dataSink.cpp`, `JS8_Main/CPlotter.cpp`, `JS8_UI/Configuration.cpp`.
  **Risk:** this is a temporary folder and can be deleted. If it is gone, check out tag `v3.0.3` of JS8Call-improved again.
- **App source:** `android/app/src/main/java/com/js8call/example/`. Largest files: `service/JS8EngineService.kt` (6,131 lines), `js8ui/Js8MainFragment.kt` (2,866), `js8ui/Js8SettingsFragment.kt` (1,953), `api/Js8Api.kt` (817), `ui/WaterfallRenderer.kt` (667), `js8ui/Js8SettingsSchema.kt` (595), `network/PskReporterClient.kt` (524).
- **After the audit:** the phone and radio tests (W53 multicast over Wi-Fi is retried on the phone), then the first GitHub release.

## Earlier state (2026-11-03)

**State at 09:05, 2026-11-04:** W46 (`aprsblk.py`) and M05N1 / M05N3 (`gpsnet.py`, Acquire GPS Wi-Fi / cell through test providers) passed twice. `tlink.py` play-thread exit error fixed (sounddevice stopped the stream at exit; the thread now ends first). W47 (`cumchk.py`, Cumulative against JS8Call): two app fixes in `WaterfallRenderer.kt` to follow JS8Call: the Cumulative divisor (dataSink `m_ihsym`) is no longer reset each cycle (it wraps at a cycle's spectra and restarts with the minute, so the line moves like the PC's), and every spectrum line is reduced by RDP, epsilon 2 PC px (`RDP.cpp`). Then passed twice, read at the same second of the minute; the app sits 1.6-3.3 dB above JS8Call, which Rob says doesn't matter. App grid lines are #444444, JS8Call's #808080 (seen, not changed). W48 / W49 (`hsplit.py`): split Rig and Fake It through the built-in Hamlib model 2 against `rigctld.exe` passed twice (10:22). The four PC steps listed after W46 are done.
- **Rob, 2026-11-04:** Android 7 is dropped (no Android 7 emulator tests). README status line updated (1,045 checks). Next: a new full audit against JS8Call 3.0.3 in `AUDIT-3.0.3-B.md`.
- **Not-tested notes checked (2026-11-04, 10:50):** of the 26 "not tested / not looked at" lines below, 19 were covered later by checklist rows that passed twice; the radio / real-phone ones wait for the phone. New: W50 / W51 Back-then-open is a new start (passed), W52 multicast out of eth0 (passed), W55 Audio sideways (passed). **Open:** W53 multicast out of wlan0 never arrives (retry on the phone). **Fixed:** W54 Station > Radio sideways: Rig Options was squeezed into row 1 with a rig picked; fixed 11:30, Rig Options now always has its own row and PTT Port: sits under PTT Method (passed twice, PC and FT8Call, IC-7300 and Rig None). Not doable on the PC: "a real API program" (none named; JTAlert is not part of this project, Rob 2026-11-04).

**State at 00:00, 2026-11-04 (read this block first; it replaces "That leaves no PC work" in the block below):**
- Not committed: the colour sample fix in `Js8SettingsFragment.kt` (CRLF), checklist rows W38-W45, `zoomchk.py`, `darkchk.py`, `uicolchk.py`, `apichk.py`, `spotblk.py`, `aprsblk.py`. Commit only when Rob says, to `fork` only.
- W38-W45 passed twice. W45 (`spotblk.py`): VE3TST on the spot list reached neither PSK Reporter nor JS8NET; with the list empty it reached both.
- **W46 needs two clean runs:** `aprsblk.py 1` and `aprsblk.py 2`, about 10 min, then add its row after W45. Run 1 showed the app working (blocked: not sent; open: `K4BRC>APJ8CL,qAS,VE3TST:=4432.50N/08100.00WG#JS8 10.131500MHz -05dB`) but counted as failed because the login check wanted `user VE3TST`; the app logs in as `user APJ8CL` and the check is fixed. Waits: APRS-IS sends once a minute and 26% of the time skips one more minute; JS8NET (SpotClient) sends once a minute.
- **PC steps left after W46:** 5. **Acquire GPS** from a network location (`cmd location providers add-test-provider network`, `set-test-provider-location`), est 20 min. 6. `tlink2.py` "Exception in thread Thread-6 (play)" script fault, est 15 min. 7. **Cumulative** level against JS8Call on the PC, est 30 min. 8. Split through the built-in Hamlib against `rigctld.exe` (Rob starts it), est 30 min.
- **Rig tests after the network removal:** use `rig_type=hamlib_usb`, `rig_hamlib_model=2` (Hamlib NET rigctl; hidden from the list but still runs through `openHamlibNetwork`), `rigctld_host=10.0.2.2`, port 4532. This corrects the "can no longer be tested on the PC" side effect in the block below.
- **Waiting on Rob:** the Android 7 emulator image download; the full audit before the phone: the not-tested notes only, a new full comparison with JS8Call 3.0.3 (like `AUDIT-3.0.3.md`), or both.
- **Emulator restart that works** (Git Bash, run in the background, then poll `getprop sys.boot_completed`; the PowerShell `Start-Process` after `emu kill` failed):
  `"/c/Users/Robert/AppData/Local/Android/Sdk/emulator/emulator.exe" -avd S22-Fresh -no-snapshot -port 5600 -cores 8 -memory 4096 -gpu host`

**State at 19:00, 2026-11-03 (read this block first):**
- Pushed to fork/easy-js8 (2026-11-03): 65fbdd5e, b704edfb, 52bcb0f0 (README). Not committed: `TEST-CHECKLIST.md`, `HANDOFF.md` and `TEST-PLAN.md` (W19 passed), `build-env/test/lib/wham2.py`.
- README Status line out of date: says "1,006 passed, 1 not run yet". Proposed: "...36 hours of test runs; none failed." Waiting on Rob.
- GitHub default branch stays master until Rob says ready (front page still shows the JS8Android README).
- **Real-Hamlib checks W20-W33 done (2026-11-03, 19:53):** all 14 passed twice against `rigctld.exe -m 1 -t 4532`, which Rob starts: `C:\Users\Robert\AppData\Local\Temp\claude\E--new-w3bguru-websites-easy-js8\f8df57d0-2324-45f1-b508-d9d9199b79c9\scratchpad\hamlib\hamlib-w64-4.7.2\bin\rigctld.exe`. Checklist now 1,021 rows, all passed twice or N/A, 0 Failed. Result files `wham2.1/2.json` (W20-W30), `wham2.11/12.json` (W26 rerun), `wham2.21/22.json` (W31-W33).
  - W26 Fake It failure was the script: the app sends PTT off, then the dial back (`JS8EngineService.kt` 3847-3848); the script read PTT, then the dial 0.3 s later. Now PTT, dial, PTT go in one packet (`rcs`), keyed = both PTT reads on.
  - W33 script tapped `Retry`; the button text is `RETRY` (tapk matches exactly). Fixed.
  - Rob's close / wait a minute / start takes about 4 minutes; give W31 runs 8 min.
- **Four screen faults fixed (2026-11-03, 20:30 build, uncommitted), W34-W37 passed twice (`fixchk.py`):** Station > UI colour buttons grow taller when the label wraps (min 28 dp; Secondary Highlight Background showed half its second line); `Js8TableView.measureContents` measures bold rows in bold (Inbox Date cut off on unread rows); `Js8SettingsFragment.render(keepScroll)` keeps the scroll position when a ticked box redraws the page (AUTO, HB, PTT Method, Handshake, Automatically switch bands ...); sideways packed pages lose the extra 14 dp above a stacked box (Audio's Remember power settings by band was cut off on opening; the page still scrolls 33 dp of empty space).
- **Clock drift question settled (2026-11-03):** since 10:14 every script starts with drift 0 (`onair2.py` BASE). The 26 real-audio rows last run before then were rerun twice at drift 0 and passed: T01-T18 (`tlink.py`), E31, Q07, E18, G11, J13U, O16, S01, S02. Q07's real check is `air13.py` `real_spot`; `air12.py`'s Q07 injects a decode and can never spot. Checklist 1,025 rows, 0 Failed.
- S22-Fresh's Android system died at 21:05 (DeadSystemException; also 12:31), taking the app and the screen reader with it; restarted 21:20. JS8Call (ELINK) was found closed after `tlink.py` run 2 hit its limit; restarted 21:22.
- The proposed `@Synchronized` fix on `RigCtlClient.sendCommand` was wrong: it has had `@Synchronized` since JS8Android's first rigctld commit (04814bfa), so no reply can be read by the wrong thread. The one W25 split failure (18:42) has no known cause; W25 passed in every run since.
- Later, needs a radio: the app's built-in Hamlib (USB).
- Still open on the PC: Log QSO page **Message Window** box (Rob's answer), four small screen faults, About page **Features** list, first GitHub release with `version.txt`.
- **Rob, 2026-11-03, later:** Log QSO **Message Window** box: leave it for now. About **Features** list: forget it for now (the earlier question was never written down). First GitHub release: only after the phone and radio tests, not before. That leaves no PC work; next is the phone.
- **Network rig choices removed (Rob, 2026-11-03: "zero reason to include them"):** Station > Radio > **Rig** no longer lists **Hamlib NET rigctl / FLRig (network)**, **DX Lab Suite Commander**, **Ham Radio Deluxe**, or Hamlib's own **FLRig FLRig** and **Hamlib NET rigctl** (filtered by label in `Js8SettingsSchema.rigChoices`). `DxLabCommanderClient.kt`, `HrdClient.kt` and `JS8EngineService.initializeNetworkRigControl` deleted; a saved `rig_type` of `network` shows as None and starts no rig. `RigCtlClient.kt` and the service's other `"network"` branches are left in, unused. Checklist P01-P03 N/A. **Side effect:** rig control (dial, PTT, split, Test CAT) can no longer be tested on the PC; `wham.py`, `wham2.py`, `air10/11/13/14.py`, `srv.py`, `ui9.py`, `wui.py` set `rig_type='network'` and won't work. Checked on S22-Fresh: the list shows None, QMX, truSDX, UV-Pro, RTS PTT, then Hamlib models; searches for flrig, rigctl, commander, deluxe find nothing; 7300 finds the IC-7300.
- At 19:00 `rigctld.exe` was running and S22-Fresh was fine (restarted 16:24).

1. **First:** read the two rules at the top of `TEST-PLAN.md`: PC first (phone and radio absolutely last), and every test runs with updates and timers.
2. **Steps 1 to 17 are done** (2026-11-03, 16:51; W19 at 18:16); see "Test pass state (2026-11-03)". Left on the PC: the Log page **Message Window** box question, the small faults, the first GitHub release (see "Not done yet" and "Audit work state"). Then the phone and radio.
3. **W19 (real Hamlib) passed twice (2026-11-03, 18:16):** Hamlib 4.7.2 unzipped to `C:\Users\Robert\AppData\Local\Temp\claude\E--new-w3bguru-websites-easy-js8\f8df57d0-2324-45f1-b508-d9d9199b79c9\scratchpad\hamlib\hamlib-w64-4.7.2\` (zip SHA256 matched Hamlib's SHA256SUM file). This session may not start `rigctld.exe`, so Rob starts it: `rigctld.exe -m 1 -t 4532` in its `bin` folder. The app read the dial, set it, and keyed and released PTT through it. Script `build-env/test/lib/wham.py`.
4. **Running on the PC:** JS8Call with `--rig-name ELINK` (test profile, MyCall K4PCT, HB and ACK on); `fakes.py`, `fakeaprs.py`, `fakepsk.py`, `fakespot.py`.
5. **Emulator:** S22-Fresh, restarted 16:24 with `-cores 8 -memory 4096 -gpu host`. Test values are left as the last test set them.
6. **Committed and pushed to `fork` (2026-11-03, 65fbdd5e and the commit after it):** the clock drift display (point 9), the app fixes in "Test pass state (2026-11-03)", checklist section W, the `TEST-PLAN.md` changes, `tlink2.py`, `wlink.py`, `wui.py`, `wside.py`, `wham.py`, `side.py`, `side16.sh`, `need.py`, `markjson.py` and the test script changes. Not committed: test result files (`.json`, `.out`), logs, `shots`, the screen recording. (The Reset Frame Sizes fix is d113e7ef.)
7. **JS8Call heartbeat script:** the "Exception in thread Thread-6 (play)" line `tlink2.py` prints when it closes is not looked into.
8. **Talking to Rob:** name tests by what they check, never by checklist codes (W01, H28 ...).
9. **Clock drift display (done 2026-11-03, Rob):** the drift no longer goes in the clock box (it wrapped and was cut off in Upright, PC and Pages). Upright, PC, Pages: small grey text (12 dp, #888888) right of the callsign, an invisible copy on the left keeps the callsign centred (`js8_call_clock_block.xml`). FT8Call: its own line under the time, 9 dp grey (`js8_call_clock_block_ft8call.xml`). Hidden at 0. Code: `Js8MainFragment.kt` clock tick (`clockDrift`, `clockDriftPad`). Checked in screenshots in all four layouts, with the drift on and at 0, and FT8Call with Next Beacon showing.
10. **Open question about earlier results:** the tests ran for hours with the clock drift at +3530 ms, later +5897 ms (pref `time_drift_ms`), left by the drift tests; no script set it back to 0. Not known yet which passed tests ran with drift on, or whether it changed their results. Every test script should set the drift to 0 at the start.
11. **Emulator now:** drift +5897 ms (left on so Rob could see it), speed JS8 Fast, HB on, HB timer off, FT8Call layout, sideways.

Read this first, then `LAYOUT.md` (the layout spec and the PC values it copies; updated for
this session too).

**Latest state:** see "Test pass state (2026-11-03)".

**Fourth session (2026-10-20) worked on sideways, mostly PC mode** (see "Sideways, fourth session").
**Fifth session (2026-10-20):** Waterfall Controls → **Timing** and **Display** tabs built (see
"Timing tab", "Display tab"), engine waterfall detail fixed ("Engine spectrum detail").
**Sixth session:** Settings window 12 dp text, narrower boxes; sideways Station and Settings without tabs, two columns (see "Settings window sizes").
**Seventh session (2026-10-20):** the PC's API, WSJT-X, N3FJP and N1MM (Station > Reporting), see "API and logging";
then LOG (Log QSO page), the mailbox (messages held for other stations) and Export Options, see "LOG, mailbox, export";
then the HB button, see "HB button".
**Eighth session (2026-10-20):** new sideways layout **FT8Call** (Station > Layout > Sideways layout:
PC / Swipe / FT8Call), PC mode restyled to look like FT8Call v0.4.2, see LAYOUT.md "FT8Call layout".
Tested on S22: header, buttons with HB, CQ rows, status bar. Not tested: Next Beacon countdown
(HB timer not started), the drag spots and zoom in this layout, phone light mode.
Then (2026-10-21): Sideways layout moved from Station > Phone to UI, then to its own Station > Layout page
(listed only sideways, after Audio; upright list not looked at);
sideways Station page names moved into the top row beside ← Back, centred on the screen. Checked on
S22 sideways: page list, UI, Phone, About. Upright Station not looked at (code path unchanged).
Station > Radio sideways: Rig | PTT Method | Mode | Split Operation, then Transmit Audio Source |
Advanced (with a rig picked the first row is Rig | CAT Control | PTT Method | Mode, not looked at; with a rig picked, CAT Control joins row 1), boxes sized to what they
hold, same height across each row (see LAYOUT.md). Checked with Rig None and Hamlib NET (then Cancel).
Station > Audio sideways packed the same way (see LAYOUT.md); checked in Swipe's Station screen only
(scrolls a little there; PC / FT8Call mode not looked at). Rob has not said whether other Station pages should get the same.
**Next:** see "Not done yet". Committed: fourth session 7717b9d8, Timing tab
71a831f2, Display first part 7d421c12, Display bigger group 1b4c3056, engine waterfall detail d265512c.

## What this is

Android app copying the **JS8Call 3.0.3 desktop window** as closely as possible.
Fork of JS8Android (github.com/JS8Call-improved/Android-port, GPLv3): its engine (decoder,
transmit, audio, rig control) is kept, its screens are replaced.

- App name **Easy JS8**, app ID `com.easyjs8.app` (installs next to JS8Android).
  Code package is still `com.js8call.example`; new screens are in `js8ui/`.
- Test phone: Samsung Galaxy S22 (about 360 dp wide upright). Not yet run on the real phone or a radio.
- Git: branch **`easy-js8`**, pushed to Rob's fork **https://github.com/RobBobbins/Easy-JS8**
  (remote `fork`, public). `origin` is upstream JS8Call-improved/Android-port — never push there.
  Commit and push to `fork` only when Rob says so.

## Decided with Rob

- **Upright** = always Vertical mode (one scrolling page, no pinch zoom). Section order:
  Header · Band Activity | Call Activity side by side · Message Window · Typing Box ·
  Buttons (two rows of five) · Status Bar · Waterfall · Waterfall Controls · Inbox.
- **Upright header** (changed this session): callsign over the UTC clock, then
  RX · TX · **Settings**, then SPOT · LOG · TUNE. No frequency display, no ▲▼, no Offset slider,
  no ⋮ (all moved to the Settings window). Waterfall Controls → Control has no Offset box upright.
- **Sideways** = setting **Sideways layout**: **PC** (exact PC window, pinch zoom, drag) or
  **Swipe** (5 screens + bottom tabs). **Sideways was not touched this session** ("one thing at a
  time"): it still has the frequency block, slider, ⋮ / menu bar and the Menu page.
- Lists: tap a column header sorts it, press-and-hold picks columns, sideways scroll; default
  columns = PC defaults.
- Fonts: Selawik (for Segoe UI) and Cascadia Mono (for Consolas), OFL licenses in `fonts-licenses/`.
- Frame colors follow the phone's dark mode. Copy target version: 3.0.3.
- General rule from Rob: **"We do what JS8Call would do"**, unless he says otherwise.

### Two windows (this session)

Both are `js8ui/Js8SettingsFragment.kt`, argument `window` = `station` | `settings`
(nav destinations `navigation_js8_station`, `navigation_js8_settings`). Sections are in
`Js8SettingsSchema.stationSections()` / `settingsSections()`. Each window keeps its own unsaved
values (ViewModel keyed by window).

- **Settings** (header **Settings** button, where the PC has NORMAL+MULTI+AUTO+CONF):
  operating settings. Top line: **← Back** (works like Cancel) at the left, **gear** at the right
  (opens Station straight away). Bottom: **Save** / **Cancel**. Sections:
  - Operation (the rest of the PC Mode menu, PC defaults):
    **Enable Simultaneous Decoding of All Speeds (MULTI)** `js8cfg_multi` (on: decode Normal, Fast,
    40, Slow; off: only the Speed), **Enable Autoreply (AUTO)** = live `autoreply_enabled`,
    **Enable Heartbeat Networking (HB)** `js8cfg_hb` (off: no timed heartbeats even with an interval),
    **Enable Heartbeat Acknowledgments (ACK)** `js8cfg_hb_ack` (greyed unless AUTO and HB; heartbeats
    answered only with all three). Station's **Turn autoreply on at startup** is now
    `js8cfg_autoreply_startup` and sets AUTO when the app starts (needs callsign and grid).
  - Mode → **Speed**: JS8 Normal, Fast, 40, 60 (greyed: engine can't send it), Slow → engine `tx_submode` (Int)
  - Frequency → **Frequency:** band list (QMX: only its band profile) + **Custom frequency (MHz):** → `last_frequency`
  - Offset → **Offset:** 0–5000 Hz → `js8cfg_tx_offset` (kept between starts; waterfall taps write it too)
  - View → **Show Band Heartbeats and ACKs**, **Clear Band / Call / RX / All Activity** buttons (act at once, show a short toast)
- **Station** (Settings gear; also the startup check and Saved → Edit Saved Messages…): the PC's
  File → Settings window. Upright: **← Back** at the top, opens on a **list of pages**
  (General, Radio, Audio, Reporting, Frequencies, Saved Messages, Notifications, UI, Diagnostics,
  Phone); tap one to see only it; ← Back / phone back returns to the list; ← Back on the list = Cancel.
  Bottom: **OK** / **Cancel** (PC's). Sideways: no tabs since the sixth session (see "Settings window sizes").
  PC-only settings hidden (Save Directory, PTT command,
  app stylesheet/font, check for updates).
- The ⋮ Menu page is gone upright. Its useful items went to Settings; the three JS8Android
  screens and About were dropped (still reachable sideways via the Menu page).

### Callsign, grid, SWL (this session)

- Station → Station Details: **My Callsign:**, **SWL (Listening only)**, **My Maidenhead Grid Locator:**,
  row **Acquire GPS · (grid found) · Set** (GPS only, no Wi-Fi/cell; first fix or "No position"
  after 2 min; 8-character grid; Set copies it into the grid box).
- Grid box: up to 12 characters, PC rules (4/6/8/10/12, pairs). Callsign: JS8's rule
  (`js8ui/Js8Callsign.kt`, copy of the engine's `is_valid_callsign`).
- **No callsign or grid** (and SWL unchecked): Station opens at app start and **can't be left**
  (Cancel / Back show "Enter callsign and gridsquare in the settings"). Empty boxes show a ⚠
  (`js8_warning.xml`). OK refuses a bad/empty callsign or grid with the PC's messages.
- Sending with no callsign/grid: message "Enter callsign and gridsquare in the settings"
  (`Js8StationRequiredDialog`, survives rotation), its OK opens Station.
- **SWL (Listening only)** checked (`js8cfg_swl`): callsign and grid may be empty; every transmit
  button greyed (TX, TUNE, CQ, REPLY, SNR, INFO, STATUS, Saved, Directed, Send; Halt stays);
  the engine refuses all transmitting (`JS8EngineService.isListenOnly()`: heartbeats, auto-replies,
  relays, typed messages, TUNE).

### Waterfall and offset (this session)

- Offset dial drawn the PC way (`CPlotter::drawDials`): left edge at the offset, width = mode
  bandwidth (Slow 25, Normal 50, Fast 80, JS8 40 160 Hz), 2 px red outline, faint checker fill.
- Tap the waterfall **or its frequency scale** to set the offset (whole Hz), also with RX off
  (the scale follows Display > Start / Bins Per Pixel, also with RX off). No 500 Hz floor.
- Taps are ignored while a message is queued or sending, unless **Allow Tx frequency changes
  while transmitting** is checked (PC `changeFreq`).
- Status bar mode label (JS8 Normal…) and cycle counter (n/15, n/10…) follow the speed.
- Transmit frames get only the first 4 grid characters, like the PC (`my_grid().left(4)`).

### APRS pages (third session, 2026-10-19)

- **Saved** menu (upright only) ends with **APRS EMAIL**, **APRS TXT**, **APRS GRID**, **APRS MSG**.
  Each opens its own page (`js8ui/Js8AprsFragment.kt`, nav `navigation_js8_aprs`, argument `form`).
  **OK** puts the line in the Typing Box and does not send; **Cancel** / **← Back** leave it alone.
  The PC has no APRS forms (you type @APRSIS commands by hand).
- Lines: `@APRSIS CMD :EMAIL-2  :ADDR MSG{NN`, `@APRSIS CMD :SMSGTE   :@PHONE MSG{NN`,
  `@APRSIS GRID <grid>` (grid from Station, no number), `@APRSIS CMD :CALL-SSID:MSG{NN`
  (addressee padded to 9). Rob: message number is `{01` only, no closing `}`.
- Rules (APRS 1.2 spec ch. 14): `{01`–`{99` then `{01`, one counter `js8cfg_aprs_msg_number`,
  goes up on OK; APRS text ≤ 67 characters (email address / `@phone` count toward it); no `{` in the message.
- Easy JS8 has the PC's APRS-IS client (JS8Call 3.0.3): `service/AprsIsClient.kt`, wired in
  `JS8EngineService.kt` (`updateAprsState`, `handleAprsSpot`, `onAprsMessage`). Needs **Enable spotting to
  reporting networks** and **Enable spotting @APRSIS messages**; **Enable relaying inbound APRS messages to
  Heard List stations** keeps the connection up and relays.
  - Out: heard `@APRSIS GRID` and `@APRSIS CMD` (checksum checked) queue up and go on the one-minute timer,
    with the PC's 25% random skip, so a frame can take 2 minutes or more. Login is `APJ8CL`, as on the PC.
  - In: a message for a station in Call Activity (exact call, not on **spot blacklist**, within
    **Remove callsigns from call activity after:**) sends ack to APRS (every copy; 120 s repeat filter), shows a
    bold `APRS-IS Relay:` line in the Message Window and transmits `@APRSIS MSG TO:CALL TEXT DE SENDER`
    at top priority. Reconnects 5 s after a drop.
  - Difference from the PC: APRS is connected only while **RX** is on (`engine != null` in `updateAprsState`). The app
    cannot transmit with **RX** off, so it must not take a message or send an ACK then. Tested: off = disconnected, no ACK.
  - Tested on S22-Fresh against a fake server, `scratchpad` only. Not yet tried against the real APRS-IS network.

### Lists, Message Window, sending (third session, 2026-10-19)

- Upright: Call Activity 130 dp wide; Band Activity starts with Age + Message(s) only, Offset / SNR
  back via **Settings → View → Show Offset / Show SNR in Band Activity** (table key `band_upright`).
- Lists redraw the PC way: decodes gathered, redrawn at most once a second (`activityDirty`),
  only changed rows touched (DiffUtil, no item animations), scroll position kept (`Js8TableView`).
- Message Window text is only set when it changed and scrolls to the end before drawing
  (it used to jump to the top every 5 s — the "bounce" Rob filmed).
- Sending like the PC (`prepareNextMessageFrame`): engine now remembers the message's parts
  (`tx_message_frames()`, `tx_frames_started()` in core; JNI `nativeGetTxFrameTexts` /
  `nativeGetTxFramesStarted`). Service broadcasts `ACTION_TX_FRAME` when each part's audio starts;
  the Message Window adds it in red (first part new line, last part ◊). Typing Box keeps the
  message locked with sent text struck through, empties after the last part or on Halt; Saved /
  CQ / Directed etc. do nothing meanwhile (`Js8ActivityViewModel.typed`). Heartbeats and replies
  show part by part too. Tested on the emulator only, not with a radio.

### FT8 monitor (third session, 2026-10-19)

- FT8 signals heard in the JS8 audio (receive only) appear in the Message Window:
  `HH:MM:SS - (offset) - FT8: CQ **W1AW** FN31`, transmitting callsign bold (2nd callsign of standard /
  nonstandard-call messages). **Settings → View → Show FT8 decodes in the Message Window**
  (`js8cfg_show_ft8`, off by default since 2026-11-05, Rob); off = decoder not fed.
- ft8_lib (Kārlis Goba, MIT) copied from Easy FT8 into `vendor/ft8_lib`, built into js8core-jni.
  Core engine `EngineCallbacks.on_rx_audio` hands each audio chunk with its UTC-minute ring position
  to `adapters/android/jni/ft8_monitor.cpp`, which assembles 15 s slots and decodes on its own thread.
- Debug test (no radio): `adb push <15 s 12 kHz WAV> /data/local/tmp/ft8.wav`,
  `adb shell "run-as com.easyjs8.app cp /data/local/tmp/ft8.wav files/ft8.wav"`, then
  `adb shell am broadcast -n com.easyjs8.app/com.js8call.example.debug.DebugFt8Receiver --es file ft8.wav`
  (RX on). Test WAVs: `E:\new-w3bguru-websites\easyft8\ft8af\app\src\main\cpp\ft8af_glue\testdata\ft8`
  (20m_busy_test_01: 18 of 24 listed decodes). Not tested with real FT8 over a radio.

### RTTY monitor (2026-11-05)

- RTTY signals heard in the JS8 audio (receive only, every signal at once, as FT8) appear in the Message Window:
  `HH:MM:SS - (offset) - RTTY: CQ DE N0CALL N0CALL K`. **Settings → View → Show RTTY decodes in the Message Window**
  (`js8cfg_show_rtty`, off by default, Rob); off = decoder not fed.
- Decoder: fldigi 4.2.13's multi-channel RTTY viewer (`view_rtty`, the signal browser's RTTY part), copied unchanged
  from Rob's fldigi-android into `vendor/fldigi_rtty/upstream` with stand-in headers (`vendor/fldigi_rtty/README.md`).
  fldigi's defaults: 170 Hz shift, 45 baud (index 0, not 45.45), Baudot, squelch -6.0, 30 channels 100 Hz apart.
- `adapters/android/jni/rtty_monitor.cpp`: 12000 -> 8000 resampler, fldigi's waterfall power and viewer on a worker
  thread. A line ends at the sender's line end, after 5 s without characters, when the signal is lost, or at 80
  characters; lines with fewer than 2 letters or digits are dropped. Size: +45 KB in the arm64 debug library.
- Test: `build-env/test/lib/rttychk.py` (W131-W136), sound `rtty_src8k.raw` from fldigi-android's
  `modetest browser RTTY` (fldigi's own transmitter). `core/src/engine/engine.cpp`: test sound mixed in with
  `debug_mix_rx` now also reaches the FT8 and RTTY monitors (before, they got only the microphone samples).
- Noise alone gives 2-3 junk lines a minute at fldigi's squelch (W136 fails), and 2-3 junk lines follow a
  signal on its offset. Rob, 2026-11-05: "Keep fldigi's" squelch. Not tried with a radio.

### Call Activity menu, menus, status bar (third session, 2026-10-19/20)

- **Press and hold a callsign** in Call Activity (all layouts) = the PC's right-click menu
  (`UI_Constructor.cpp` tableWidgetCalls customContextMenuRequested), `Js8MainFragment.showCallMenu`:
  Jump to <offset>Hz · Jump to <speed> speed (only if different and sendable) · Jump to <n> ms
  time drift · Log... (opens Log QSO; greyed for groups) · Show Message Inbox... (scrolls to Inbox; greyed
  without messages from or held for them) · Store Message... (seventh session: held for them)
  · Saved Messages › · Directed to CALL... › · Relay via CALL... (`CALL>` at Typing Box start) ·
  Deselect · Add New Station or Group... · Remove This Station/Group · Block This Station
  (`js8cfg_rx_blocklist`) · Clear Entire List · Clear All Lists. "Center filter" never shown
  (no working waterfall filter). @ALLCALL / groups grey items like the PC.
  Tested: Jump to offset, Directed sub-menu, Relay, Add. Not tested: speed, drift, Remove,
  Block, Clear.
- **Popup menus** (Saved, Directed, Call Activity) use `ThemeOverlay.Js8PopupMenu`: list text
  size (`js8_text`), 36 dp rows.
- **Upright status bar** (`js8_status_bar_upright.xml`): Receiving · cycle progress bar
  (width set in code to reach the page's right edge, same as Halt) · JS8 mode · Freq ·
  Auto Reply · Last Tx (empty, never filled). Progress bar has rounded ends like the PC
  (`progress_bar_stylesheet`, radius 5/14 of height), fills one step per second.

### Station Settings and About (third session)

- Station upright list: title **Station Settings** centred; rows 280 dp wide, centred, 40 dp
  tall (all 11 fit on the S22); last row **About** (upright only, not a settings section).
- **About** (`Js8SettingsFragment.renderAbout`): About Easy JS8 / by VA3OSO / Version x (build n)
  / Built <date> (BuildConfig.BUILD_DATE, set at build time, UTC, "2026 Oct 20") / "Easy JS8 is
  a fork of JS8Android." / Features: Traditional style, Upright and sideways layouts, SWL mode,
  JS8 Slow decoding, APRS forms, FT8 decodes / Source link / "Licence: GPLv3. No warranty." /
  Credits in order: WSJT-X, JS8Call (+ team calls), JS8Android, ft8_lib, fonts.
- **Rob's wording rule for anything public (About, README, store text):** Easy JS8 "is a fork of
  JS8Android". Nothing about copying JS8Call or using it as a starting point, no "not endorsed"
  sentence. Features listed are only what Easy JS8 has over JS8Android (JS8Call has no phone
  app, so it isn't the comparison). Names only, no descriptions.

### Settings window sizes (sixth session, 2026-10-20, Rob said yes)

- Upright Settings window only (`Js8SettingsFragment` textPx / fieldRow): **Frequency:** dropdown
  200 dp, **Custom frequency (MHz):** 120 dp, **Offset:** 120 dp including ▲▼, each left-aligned
  under its label. All text 12 dp instead of 14.3 dp (the PC's 9 pt Segoe UI; headings, ← Back,
  Save / Cancel, Clear buttons scale with it). Speed lines now fit on one line each.
  Station window unchanged. Checked on S22 upright.
- **Sideways, both windows, no tabs** (Rob: "ditch all the tabs", "sit stuff beside each other,
  just not too cramped"): label above box everywhere, radio options one per line.
  Station: **← Back** + **Station Settings** on one line over the page list (3 across x 4 rows, 180 dp each, centred, all
  fit); on a page the page name is under ← Back (10 dp above and below, Rob); a page shows each
  sub-tab heading, then its framed groups in two columns, each into the shorter column
  (`renderSidewaysPage`); a sub-tab with one group shares the columns, its heading on the group;
  tables (frequencies, schedule, saved messages, notifications, diagnostics) full width. Text 14.3 dp.
  Boxes (Rob: "lots of fields can be compacted"): only free text fills its column (Station
  Messages, Callsign Groups, callsign lists); the rest are as wide as their longest value
  (`compactWidth`: spin min/max, longest choice, `SHORT_TEXT` samples for callsign, grid, EOT/MFI,
  host/port/baud, APRS server, operator call). GPS found-grid box as wide as "No position".
  Upright Station boxes stay full width.
  **My Callsign:** and **My Maidenhead Grid Locator:** side by side (8 dp gap), SWL and the Acquire GPS
  row under them; only when both fit with the grid label on one line (narrower phones stack them).
  Field labels are now as wide as their text (were fill-parent, which squeezed a side-by-side label).
  Settings: Operation + Mode left, Frequency + Offset + View right, 12 dp, 200/120/120 dp boxes.
  Checked on S22 PC mode: list, every page, Settings window. **Swipe** (embedded Station screen,
  gets ← Back too) not checked.

### Sideways, fourth session (2026-10-20)

Rob's rule: **every menu has the same items in every layout; only the look differs.**
- Saved menu: APRS EMAIL / TXT / GRID / MSG in all layouts (APRS page checked sideways).
- Swipe: no ⋮ on the Header screen; tab 5 renamed **Station**; own status bar
  `js8_status_bar_swipe.xml` (Receiving · progress bar in the empty box's place · JS8 Normal · Freq ·
  Auto Reply · Last Tx off the right edge). PC mode keeps `js8_status_bar.xml` (PC order).
- PC mode menu bar: File → Station; Configurations, Mode, View, Control → Settings window;
  Log → "Log QSO is not built yet"; Help → Station opened on About. The Menu page
  (`Js8MenuFragment.kt`, nav `navigation_js8_menu`) is no longer opened from anywhere; file kept.
- PC mode has no inbox strip (Rob): window 1077 x 714 dp (`gen_pc_layout.py`, `Js8ZoomLayout`
  default). It fits by height, so nothing got bigger, only more empty space at the sides.
  **View** on the menu bar = popup **Inbox** / **Settings…**; Inbox and Call Activity →
  Show Message Inbox... open the **Message Inbox** screen (`Js8InboxFragment`, nav
  `navigation_js8_inbox`, layout `fragment_js8_inbox.xml`); its Reply fills the Typing Box and
  goes back. List code shared in `Js8Inbox.kt`. `js8_inbox_pc.xml` no longer used (file kept).
- PC mode dividers (PC splitters, `Js8PcSplitters.kt`): three invisible grab spots, 44 PC dp:
  left / right corner where the upright lines meet the Message Window / Typing Box line
  (sideways = list width, up/down = that line) and the STATUS / Saved gap (up/down = buttons row
  and waterfall top). Minimums mine (lists 100, middle 120, text boxes 30, waterfall 40 dp; PC:
  lists/text area >= 50 dp). Kept in `js8cfg_pc_split_*`; **View → Reset Window Sizes**.
  First setting / reset = Rob's layout (Band right 375, Call left 759, Message Window bottom 374,
  lists bottom 441 PC dp; the generated XML keeps the PC capture's 441 / 759 / 277 / 351).
  Fourth spot (24 x 44 PC dp) at the top of the waterfall / Waterfall Controls line: sideways
  trades width (PC WideGraph.ui: waterfall >= 267 dp, controls >= 67 dp). Controls 200 dp wide at
  first (was 116). A drag that starts just left of it lands on the frequency scale and moves the
  offset.
  Each spot has a small raised mark (Rob): corners 10x10, STATUS/Saved 16x8, waterfall line
  8x16 PC dp; face #5A5A5A, light edge #8A8A8A top/left, dark #2A2A2A bottom/right.
  Tested: drag, kept after restart, reset. Seen once: keyboard opened right after a reset; not
  repeatable.
- PC mode header buttons: all six 72 x 24 dp, right-aligned (`js8_header_buttons_pc.xml`, Rob).
  Upright / Swipe use `js8_header_buttons.xml` (four equal columns since 2026-10-31, 27 dp).
  PC frequency block `js8_freq_block_pc.xml`: display 220 x 42 dp, 27 dp text, Offset slider 89 dp
  (half). Callsign + clock centred between it and the buttons, and top to bottom.
- **Waterfall Controls → Control tab** (Rob's capture of the PC, 2026-10-20; `Js8WaterfallControls.kt`,
  `Js8KnobView.kt`, `js8_waterfall_controls.xml`): Offset ∧ ∨ (same lock as the slider), QSY + Center
  ∧ ∨ (QSY moves `last_frequency` by offset − center and sets the offset to center; the PC also shifts
  Band Activity rows, not done; untested with a radio), Filter: Enable Filter (PC default on), Sync
  (filter center = offset), Center / Width ∧ ∨ and two knobs (300° like QDial). ∧ ∨ hold repeats
  (1, then 10, then 100 Hz). Filter drawn on the waterfall (`WaterfallView`): black at 50 % outside,
  yellow edge lines. Call Activity menu gets **Center filter at …Hz** while the filter is on.
  Keys `js8cfg_wf_center`, `js8cfg_filter_enabled/center/width`. Waterfall Controls first width now
  340 dp (filter row fits). Tested: layout, Width ∧, Sync. Not tested: knobs, Enable Filter off, QSY.
  Timing and Display tabs: built in the fifth session (below).
- **Timing tab** (fifth session; WideGraph.ui timingTab / WideGraph.cpp, 3.0.3 source): third tab
  after Control · Display (tabs now switch; Display still has no page). **Time Drift: n ms** ∧ ∨
  (100 ms steps, ±30000), **Automatic**: **Start Automatic Time Drift** (reads **Stop Automatic
  Time Drift**, bold, while on; not kept after restart, like the PC), **Stop After:** (on) +
  **n decode(s)** (1–1024, greyed while running; keys `js8cfg_auto_drift_stop/after`), **Manual**:
  Minute Start / TX End / TX Start / Reset (PC formulas, computer clock, current speed's cycle).
  Engine: new `set_auto_sync` (engine.hpp/.cpp, JNI `nativeSetAutoSync`, `JS8Engine.setAutoSync`):
  while on, Normal and Slow decode the last cycle of audio once a second
  (`isEverySecondReady`, PC decodeEnqueueReadyExperiment everySecond); one decode pass takes ~2 s
  on the emulator, so there it is every ~2 s. Service: `ACTION_SET_AUTO_DRIFT` / broadcast
  `ACTION_AUTO_DRIFT`; Normal/Slow decodes feed the same 60-decode average, then count down.
  **Duplicate frames** are now dropped in the service (same speed + text within half a cycle,
  PC m_messageDupeCache) before auto-reply, relay, inbox and drift see them.
  Rob: **Automatic time sync** (Station → Phone) is a different job (always on) — keep both.
  Tested on the emulator: tab layout PC mode and upright, ∧, Start/Stop look, every-second
  decode windows in the log and back to cycle windows after Stop, four Manual buttons' values.
  Not tested: drift from real signals and the Stop After count (emulator has no JS8 audio), Swipe.
  **Upright sizes (Rob):** on both Waterfall Controls tabs every button and field is only as wide
  as its text (fields as wide as their longest value) and centred; group frames stay full width
  (`Js8WaterfallControls.fitUpright`). Sideways keeps the PC's fill-the-panel widths; there
  **Set Time Drift to Now (Minute Start)** shrinks its text to fit a narrow panel.
  Line endings: most source files are CRLF, some LF — keep each file's own when editing (Python
  writes from Git Bash turned CRLF files into LF once; check `git diff --stat` before committing).
- **Display tab, first part** (fifth session; WideGraph.ui displayTab, CPlotter.cpp, WideGraph.cpp
  dataSink, Flatten.cpp): **Size** Start (100 Hz steps, 0-5000, 500) and Bins Per Pixel (1-1000),
  **Palette** (the PC's 29 .pal files in `android/app/src/main/assets/palettes`, PC interpolation;
  **Adjust...** greyed, not built), **Scrolling & Averaging** Scroll Speed (1-100, 4 fps) + Flatten
  (on), **Waterfall** Gain / Zero sliders (-50..50, 0), **Filter** Opacity (0-100, 50%).
  `WaterfallRenderer` rewritten to the PC's drawing: one PC pixel = one dp = Bins Per Pixel x
  1500/2048 Hz; 10*log10 power, Flatten (Kotlin port of the PC's, `WaterfallFlatten.kt`; the engine's
  own copy in core/src/dsp/flatten.cpp is not the PC's algorithm and crashed, not used), then
  zero + scale*value (Scaler1D); fps dp per second, repeating the last spectrum. The old automatic
  brightness is gone. Frequency scale and taps follow Start / Bins Per Pixel.
  Rob (option 2): Bins Per Pixel kept apart, upright default 6 (`js8cfg_wf_bpp_upright`, 500-1,610 Hz
  on S22), sideways 2 (`js8cfg_wf_bpp_sideways`; PC mode shows 500-~1,400 Hz with the 340 dp controls).
  Other keys `js8cfg_wf_start/palette/fps/flatten/gain/zero`, `js8cfg_filter_opacity`.
  Upright: fit to text, centred; Gain / Zero rows 260 dp. Tested on S22 upright: layout, Start,
  Palette (Linrad), Gain, saved values, no crash with Flatten; PC mode: layout and range.
  Not tested: Flatten off, Zero, Scroll Speed changes, Opacity changes, Swipe, real radio audio.
- **Display tab, bigger group** (fifth session): **Avg N** (1-50; lines average N spectra, scroll
  slows N times, Scaler1D uses it), **Current / Cumulative / Linear Avg** list and **Smooth** (1-7,
  greyed unless Linear Avg), **Display Decode Attempts** (engine `set_sync_stats` via JNI
  `nativeSetSyncStats`, new callback `onSyncState`, service `ACTION_SET_SYNC_STATS`, in-process
  `service/DecodeAttempts.kt`; yellow tick per decode start, red bracket per decode, darkCyan / cyan
  / white candidates within 2 s, PC processDecodeEvent), **Spectrum** group Height (0-100 %, 5 %
  steps) / Gain / Zero (Scaler2D; bottom of the plot, Current green, Cumulative cyan = this cycle's
  average, Linear Avg yellow = dataSink flat1 + smo). **Adjust...** = Palette Designer: colour list
  (tap = Select Color window: R/G/B sliders + HTML code, in place of QColorDialog; press and hold =
  Delete / Insert ... / Insert after ...), Import... / Export... (.pal via the phone's file picker,
  PC format r;g;b), OK selects **User Defined** (last in the Palette list; `js8cfg_wf_user_palette`).
  Palette group corrected to the PC's layout: list above Adjust... (was side by side).
  Designer laid out as wf_palette_design_dialog.ui (Rob): 219 x 209 dp, colour table on the left with
  row numbers and grid lines, buttons in a column on the right (Import..., Export..., OK, Cancel,
  the Qt vertical Windows order; not confirmed on Rob's PC).
  Palette and Current lists are drop-downs (Rob): under the box, as wide as it, 8 rows then scroll,
  entry in use highlighted, a tap elsewhere closes (`Js8WaterfallDisplay.dropDown`). Station /
  Settings windows still use list windows.
  Engine spectra are put on the PC's 0.732 Hz grid first (`WaterfallRenderer.updateSpectrum`), so the
  PC's arithmetic runs as is; power scaled x10 for the waterfall, x0.01 for Cumulative (sample scale).
  Tested on S22 upright: layout, Height 30 % with Current, Cumulative, Linear Avg (Smooth enables),
  yellow decode-start ticks, Designer insert / edit / delete / export / import (file round trip).
  Not tested: Avg N change, red / cyan brackets (no JS8 signals on the emulator), Cumulative's
  level against the PC (emulator input nearly silent, rms 1.7), sideways, Swipe.
- **Engine spectrum detail (fixed, fifth session):** the engine's waterfall FFT used one audio
  buffer (emulator: 384 frames -> 256-point FFT, 47 Hz per bin). Now like the PC's dataSink: every
  3096 samples (JS8_NSPS / 2, 0.258 s) a 16384-point FFT of the last 16384 samples in the ring,
  no window, bins at 0.732 Hz (JS8_NSMAX of them, 0-5000 Hz); the level (powerDb, peak) is over the
  new samples only, so the level meter updates ~4 times a second (was 10). Seen on S22: sharp
  lines where there were 47 Hz-wide stripes. Not tested with JS8 signals or on a real phone.
- Upright only (Rob): CAT + level meter column 58 dp (was 87, 33 % narrower; set in
  `Js8MainFragment`); `Js8LevelMeterView` scales its scale, bar and label with the width, text 85 %.
  Upright waterfall now 500-~1,740 Hz. Sideways unchanged.
- Station sideways: tabs replaced by the page list in the sixth session (see "Settings window sizes").
- Header Offset slider: locked while a message waits or is sent (same rule as waterfall taps).
  Not tested (needs a queued message).
- **wpm box** (PC `wpm_label`, `updateTextStatsDisplay`): PC mode only so far, after the progress
  bar (`js8_status_wpm`, 150 dp, 9 dp gap). "12.0wpm / 58.0cpm" for the Typing
  Box text, counted 100 ms after typing stops, hidden while the box is empty. Engine: new static
  `JS8Engine.messageFrameTexts()` (JNI `nativeBuildFrameTexts`, core `build_message_frames`).
  PC mode progress bar now 136 x 14 dp (was 200 x 12; width measured beside the wpm box on Rob's
  PC capture, thicker at Rob's request).
  Swipe: placed after Auto Reply, 150 dp; to fit, Swipe's Receiving box is 110 dp and its
  progress bar 100 dp (`js8_status_bar_swipe.xml`). Upright (2026-10-22, Rob): Receiving 110 dp | wpm box
  150 dp, centred between (9 dp gaps) | progress bar takes the rest (~70 dp), ending at the right edge under
  Halt. Upright hides the wpm box with INVISIBLE so the progress bar never moves. Checked on S22-Fresh: empty / "14.0wpm / 80.0cpm" / empty again.

### Sideways: where it stood at the start of the fourth session

Two layouts, Station → Phone → **Sideways layout:** PC (full window, pinch zoom) or Swipe
(5 screens + tabs). Emulator currently on Swipe. Not updated since the first session:
1. Header still has frequency display, ▲▼, Offset slider and ⋮ menu (upright moved them into
   the Settings window).
2. ⋮ Menu page still exists; its Help → About is a greyed placeholder (About is now in Station).
3. Swipe tab 5 is labelled **Settings** but opens Station.
4. APRS items not in the Saved menu sideways.
5. Offset slider not locked while a message waits (PC locks it).
6. Swipe Activity screen: status bar wider than the screen.
Already working sideways: FT8 decodes, part-by-part sending, press-and-hold callsign menu,
smaller menus, smooth lists, Message Window fix; the progress bar has rounded ends there too.
Station window sideways = PC tabs, no About tab.

### API and logging (seventh session, 2026-10-20, Rob: "include all the desktop features")

- Station > **Reporting**, after Network Services, PC order and names (Configuration.ui groupBox_4,
  N3FJP Logger, N1MM Logger): **API** group (Allow setting station information..., UDP Server
  Hostname / Enable UDP Server API / UDP Server Port / Accept UDP Requests, TCP Server Hostname /
  Enable TCP Server API / TCP Server Port / Accept TCP Requests / TCP Max Connections, WSJT-X Server /
  Enable WSJT-X UDP Server API / WSJT-X Server Port / Multicast TTL / Accept UDP Requests,
  **Outgoing interfaces:** one checkbox per network interface, none = lo), **N3FJP Logger**, **N1MM
  Logger**. PC defaults: all off, 127.0.0.1, ports 2242 / 2442 / 2237 / 1100 / 2333, max 1, TTL 1.
  Keys `js8cfg_udp_*`, `js8cfg_tcp_*`, `js8cfg_wsjtx_*`, `js8cfg_n3fjp_*`, `js8cfg_n1mm_*`,
  `js8cfg_api_dynamic_info` (constants in `api/Js8Api.kt`).
- Code: `api/ApiMessage.kt` (JSON, sorted keys, _ID rules of Message.cpp), `api/Js8Api.kt` (UDP client
  with PING every 15 s, TCP server one JSON per line, replies only to the asking client or _ID <= 0,
  events, `logQso` for LOG), `api/Js8ApiCommands.kt` (every command of 3.0.3 networkMessage.cpp),
  `api/WsjtxClient.kt` (binary WSJT-X: Heartbeat, Status, Decode, QSOLogged, LoggedADIF, Close).
  Started by MainActivity and JS8EngineService; MainActivity = `Js8Api.Host` (send queue, depth),
  Js8MainFragment = `Js8Api.Controller` (Typing Box, TUNE, Halt, offset, filter, redraw).
- Events: RX.ACTIVITY and RX.DIRECTED per frame (service, commands incl. CQ / HEARTBEAT, not free
  text), RX.SPOT with each PSK Reporter spot, RIG.PTT on / off per frame, TX.FRAME (tones: new engine
  `tx_current_tones`, JNI `nativeGetTxFrameTones`), STATION.STATUS on decode start / end, RX on / off,
  TX, offset, dial, speed, selection; RIG.FREQ on a band change; STATION.CLOSING when the app closes.
- The API's grid / info / status (STATION.SET_GRID etc., only with Allow setting ... checked, not kept
  after restart, like the PC) are used on air: `Js8Api.myGrid/myInfo/myStatus` in the service and
  `Js8Macros`. Station still shows the saved values.
- Differences from the PC: no autoreply confirmation in Easy JS8, so STATION.AUTOREPLY_CONFIRM_*
  never fire; INBOX.STORE_MESSAGE answers ID -1 (can't hold messages for others); STATION.SET_HB_TIMER
  = Settings > HB (no HB button); MODE.SET_SPEED ignores JS8 60; WSJT-X starts / stops when the box
  changes (PC needs a restart). Copied from the PC as is: WSJT-X FreeText / Location requests are sent
  on as API messages, Reply / HaltTx do nothing.
- N3FJP / N1MM / LOG.QSO / WSJT-X QSOLogged wait for the LOG window (`Js8Api.logQso`). Debug test:
  `adb shell am broadcast -n com.easyjs8.app/com.js8call.example.debug.DebugLogQsoReceiver --es call W1AW`.
- Tested on S22 from the PC (scratchpad script, UDP to 10.0.2.2, TCP via `adb forward tcp:2442 tcp:2442`):
  PING, STATION.STATUS, RX.ACTIVITY, RX.DIRECTED, RIG.PTT, TX.FRAME, WSJT-X Heartbeat / Status / Decode /
  QSOLogged / LoggedADIF (all fields parse), N1MM, N3FJP, about 30 TCP commands and a UDP request, screen
  follows (Typing Box, speed, offset, filter, groups, TUNE, Halt). Settings page upright and PC mode.
  **JTAlert on Rob's PC listens on 2237**: the emulator's WSJT-X test ran on 2239 after the first 2 min.
  Not tested: a real API program, a real phone, multicast, screen off, Swipe.
- **Typed text sent as typed (fixed 2026-10-30, Rob: "do what the windows program does"):** core `kHeartbeatRe` now the PC's `heartbeat_re` (no "HB ALT"), found with regex_search, so words after CQ / HB go out and "HBO IS GOOD" is plain text. JS8Android's `applyGridIfHeartbeat` removed: no grid added to typed CQ / HB; the CQ button's CQ Message holds the grid, blank CQ Message = "CQ CQ CQ <MYGRID4>" (PC). Message Window: data parts join without a false space (`JS8Engine.txFirstFrameIsData`, `EXTRA_TX_FRAME_IS_DATA`). Android 7.0 minimum (minSdk 24, Galaxy S6, 2026-10-22) not tested on Android 7. Checked on S22-Fresh, no radio: "@ALLCALL CQ CQ API TEST", "HBO IS GOOD", CQ button "@ALLCALL CQ CQ CQ CM87", "@HB HEARTBEAT CM87".
- Seen while testing, not API (fixed, line above): "CQ CQ API TEST" went out as "@ALLCALL CQ CQ" (the words after
  CQ CQ dropped). Not looked into.

### LOG, mailbox, export (seventh session, 2026-10-20, Rob said LETS GO)

- **Log QSO page** (`js8ui/Js8LogFragment.kt`, nav `navigation_js8_log`; LogQSO.ui fields in order:
  Operator Callsign, Contact Callsign, Start + Now, End + Now, Mode, Band, Signal Report Sent / Received,
  Grid, Name, Comments, Retain Comments, Additional Fields + Add New Field (ADIF name typed or picked from
  the ▾ list), **Add to Log** / **Cancel**). Opened by **LOG** (all layouts), PC mode menu bar **Log**,
  Call Activity **Log...**. Filled like on_logQSOButton_clicked: call = selection (not groups), grid from
  Call Activity, report sent = their SNR, start = when the call was first selected, freq = dial + offset;
  same call again before logging keeps what was typed. Dates typed as yyyy-MM-dd HH:mm:ss UTC (no picker).
- Add to Log (`data/Js8LogBook.kt`): `js8call_log.adi` (PC QSOToADIF, header "JS8Call ADIF Export<eoh>")
  and `js8call.log` (comma line) in the app's files folder; Call Activity ✓ / Name / Comment (and grid
  when none heard) from the log; selection cleared; `Js8Api.logQso` -> LOG.QSO, N1MM, N3FJP, WSJT-X.
- **Mailbox** (`data/Js8Mailbox.kt`, service `handleMailboxCommand`): held messages are rows in the message
  database with direction 2 (HELD), status 0 held / 1 collected; the row id is the MSG ID on air. Group
  messages held 2 days, who collected them in prefs `js8mailbox_group_delivered_<id>`. Heard "MSG TO:CALL
  text" for me / my group: held for CALL, ACK (only if Station > **Disable message relay** is unchecked;
  Easy JS8's default is checked, the PC's unchecked). @APRSIS MSG TO: me -> my Inbox from APRS. QUERY MSGS
  -> "YES MSG ID n [+k]" / "NO"; QUERY MSG n -> "MSG text FROM x [NEXT MSG ID ...]", then collected (marked
  when queued; the PC marks after sending). HB ACK gets "MSG ID n". Replies only with AUTO (Easy JS8's
  rule for all automatic replies). Relayed (A>B) requests answered to the sender only. Before this,
  "MSG TO:" was wrongly put in my own Inbox.
- Inbox (upright strip and Message Inbox screen) lists held messages (To = the other station) until
  collected; **press and hold a row = Delete**. Call Activity **Store Message...** = "Store this message
  locally for CALL:". API INBOX.GET_MESSAGES includes STORE, INBOX.STORE_MESSAGE holds and returns the id.
- **Export Options** (`js8ui/Js8ExportFragment.kt`, nav `navigation_js8_export`): Settings window > **Log**
  section > **Export Log...**. Checkboxes Log (js8call_log.adi) [ticked], Log (js8call.log), Message Inbox
  (`js8call_inbox.txt`: "date, from, to, message" per line). **Export...** = phone folder picker, opens on the
  last folder (`js8cfg_export_folder`). Android 11+ refuses the storage top and the Download folder itself;
  Documents, a folder inside Download, Drive, SD card work. A file already there is not replaced: Android
  saves "name (1)".
- Page widgets shared in `js8ui/Js8Page.kt` (same look as the APRS pages).
- Tested on S22 upright: Log QSO filled and saved (ADIF + CSV checked byte for byte, extra field STATE),
  ✓ and Name shown, selection cleared; PC mode Log menu opens the page; MSG TO held + ACK, QUERY MSGS YES /
  NO, QUERY MSG 3 sent and collected, Store Message..., Inbox shows held, Delete; Export of all three files
  to Documents. Not tested: HB ACK with MSG ID (needs HB + ACK on), group messages, @APRSIS, Swipe,
  a real radio.
- **Double send fixed (2026-10-22):** a reply queued while a multi-part message was going out made the
  queue send the current message again (`processNextTxIfIdle` only waited while TRANSMITTING; between parts
  the state is QUEUED). `TransmitViewModel` now keeps the message handed to the engine (`markSent`,
  `isBusy`) and, when it finishes, removes that message (was: the first in the queue, which could be a
  higher-priority reply, or a message after a service heartbeat / TUNE). Checked on S22-Fresh: long
  @ALLCALL message, "K4RDX: VE3TST MSG HELLO FROM K4RDX" injected during part 1 -> message sent once,
  then "VE3TST: K4RDX ACK".
- **Halt freeze fixed (2026-10-22, Rob saw it when the HB countdown reached the end):** the engine's
  `stop_transmit` (core/src/engine/engine.cpp) held `tx_mutex_` while stopping the audio output, which waits
  for the output callback, and the callback takes `tx_mutex_` -> Halt never returned; the next timed heartbeat
  (then on the main thread) waited on the same lock and the app froze ("not responding", 13:56:17 on S22-Fresh).
  Now the output is stopped after the lock is let go, and the timed heartbeat runs on the Js8Tx thread like
  typed messages. Checked on S22-Fresh: Halt during a typed message x3, during a timed heartbeat, during
  TUNE x3; heartbeat after a Halt went out in full; no freeze.
- **Auto reply while transmitting fixed (2026-10-30, PC: check is #if 0, reply queued, processTxQueue waits for the frames to finish):** `isTransmitActive` check removed from `maybeHandleAutoReply`. Checked on S22-Fresh: "K4RDX: VE3TST SNR?" injected during part 3 of a long message -> message intact, then "VE3TST: K4RDX SNR -05". Relay sends (two `isTransmitActive` checks returning false in JS8EngineService) not changed, not checked against the PC. Old note:
- **Found while testing, since fixed (line above):** an auto reply to a query heard while Easy JS8 is transmitting
  (e.g. "K4RDX: VE3TST SNR?") is dropped, not queued (`maybeHandleAutoReply`: `if (isTransmitActive()) return`).
  PC behaviour not checked yet. MSG ACKs and mailbox replies are queued.

### HB button (seventh session, 2026-10-20, Rob said LETS GO)

- PC hbMacroButton `js8_btn_hb`, first in the button row in every layout, shown while Settings > HB is on
  and the speed is Normal / Fast / Slow (prepareHeartbeatMode). Greyed while transmitting and with SWL.
  - Upright (Rob): HB, CQ, SNR, INFO ~47 dp, REPLY ~59 dp, STATUS ~71 dp (weights 0.8 / 1 / 1.2,
    `layoutUprightButtons`); without HB the row is as before.
  - PC mode: 11 buttons ~86 dp each, STATUS / Saved gap kept (Rob's capture); the divider grab spot
    follows the gap (`Js8PcSplitters.setButtonGap`). Generated by `build-env/gen_pc_layout.py`.
  - Swipe: one row of 11, STATUS text shrinks to fit.
- Tap: with On demand one heartbeat (ACTION_SEND_HB); with a repeat time the timer on / off (lit, label
  "HB (seconds)", "HB + ACK" when AUTO + ACK). Press and hold = buildHeartbeatMenu: Start / Stop
  Heartbeat Timer (with a repeat time), On demand / 10 / 15 / 30 / 60 / Custom (1-1440 min), Send
  Heartbeat Now. Choosing a time starts the timer, On demand stops it.
- One setting (Rob): the menu's time is Station > Heartbeat interval (`heartbeat_interval`).
- Timer state `service/HeartbeatTimer.kt` (not kept after restart, like the PC); the service's timed
  heartbeats need Settings > HB and the timer (before: HB + an interval started them by themselves).
  ACTION_HB_TIMER; API STATION.SET_HB_TIMER / HB_TIMER_ACTIVE use it.
- PC rule (Rob): sending no longer sets the interval to Disabled (JS8Android disableHeartbeat removed);
  a one-off CQ stops the HB timer. Starting the timer deselects when Pause heartbeat transmissions while
  in a QSO is checked.
- Tested on S22: upright row, menu, 10 minutes -> interval 10, timer started, heartbeat sent at the next
  slot and the next planned 10 min later, CQ stopped the timer and kept the interval; PC mode and Swipe
  rows. PC's lit colour for HB not checked (Easy JS8 keeps the same blue, lit = isActivated).

### CQ button repeat (seventh session, 2026-10-20, Rob said LETS GO)

- PC cqMacroButton: tap = one CQ (sendCQ: stops the CQ and HB timers, CQ Message into the Typing Box,
  sent when Immediately transmit ... is on); with a repeat time tap = CQ timer on / off (lit, "CQ (seconds)",
  starting it always deselects). Press and hold = buildCQMenu: Start / Stop CQ Timer (with a time),
  On demand / 1 / 5 / 10 / 15 / Custom, Send CQ Now. Greyed while transmitting.
- Repeat time `js8cfg_cq_interval` (PC CQInterval, kept); timer `service/HeartbeatTimer.kt` CqTimer, not kept
  after restart. The service times it (ACTION_CQ_TIMER, slot aligned like heartbeats) and calls
  `CqTimer.fire` = the main screen's sendCQ(true) (always sends); with no main screen it queues the CQ
  Message itself. HB and CQ menus share `showRepeatMenu`.
- Tested on S22 upright: menu, 1 minute -> CQ sent at the next slot and again 1 minute later, tap stopped
  the timer and kept the time, Start CQ Timer shown with a time, back to On demand.
- Countdown upright (Rob): only the seconds while counting ("570", "now"), PC mode and Swipe keep
  "HB (570)" / "CQ (570)". Checked on S22 upright with both timers running.
- Upright HB label (2026-10-22, Rob: "it either says HB or the time, that's it"): never "HB + ACK" upright
  (was cut off as "HB + /"); PC mode, FT8Call, Swipe keep it. Checked on S22-Fresh with AUTO + ACK on: "HB",
  then "35" with the timer running.
- HB and CQ timer start (2026-10-22, Rob: "do both like the PC"): the first send is a full interval after
  starting (PC TxLoop::onTxLoopPeriodChangeStart), also after stop / start and interval / speed changes; was
  10 s (JS8Android). JS8Android's random extra cycle (1 in 4 heartbeats) removed. Checked on S22-Fresh: HB 907,
  stop + start 898 (15 min), CQ 5 min 302; upright CQ shows only the seconds like HB.
- First load / "Enter callsign and gridsquare in the settings" OK (2026-10-22, Rob): Station opens on
  **General** (Station Details at the top), not the page list (`Js8SettingsFragment.GENERAL_PAGE`). Checked on
  S22-Fresh after clearing the app's data; the dialog's OK not reached in a test (same call).
- Acquire GPS uses GPS and Wi-Fi / cell together (2026-10-22, Rob: "can it do both?"): first answer shown at
  once, a GPS answer replaces a Wi-Fi / cell one and ends the search, Wi-Fi / cell alone stays (search ends
  after 2 min), nothing = "No position". Approximate-only permission still gets Wi-Fi / cell. Checked on
  S22-Fresh: GPS answer CM87WK91. The emulator has no Wi-Fi / cell source: that part is untested.
- Settings window upright, everything under ← Back centred (2026-10-22, Rob): headings, checkboxes, buttons,
  each label over its box; Speed frame full width, its list centred inside as one block (`centreUpright`).
  Station window and sideways unchanged. Checked on S22-Fresh, whole page.
- **Remembered after a restart (2026-10-22, Rob: "all settings remembered"; the PC forgets 2-5):**
  1. List sort: Band / Call Activity start last heard first (PC Sort By "recent first"); a tapped header is kept
     per table (`js8_table_<key>_sort` / `_asc`, `Js8TableView`). @ALLCALL, groups, then message rows stay on
     top (`Row.pin`).
  2. HB / CQ timers with their countdown (`HeartbeatTimer` / `CqTimer` save on every change; service
     `resumeTimers`: a countdown that ran out while closed starts a full interval).
  3. Start Automatic Time Drift and its count (`js8cfg_auto_drift_on/_stop_after_run/_left`); the screen is told
     when the receiver starts.
  4. AUTO as left: Turn autoreply on at startup ticked = on, unticked = as it was (was: off).
  5. API station grid / info / status (`js8cfg_api_dyn_*`). Not tested (needs an API program).
  6. Band / Call Activity and the Message Window: `files/js8_activity.json`, saved 3 s after a change, loaded at
     start unless "Reset the Band Activity, Call Activity, and RX history at startup" (PC rule). Phone >
     "Keep decoded messages after restart" still unused.
  Checked on S22-Fresh with a force stop: lists, sort (Call by callsign), Message Window, HB countdown (549),
  AUTO on, Automatic Time Drift on. Not kept: which Waterfall Controls tab is open.
- Show Band Heartbeats and ACKs: default ticked like the PC (BandHBActivityVisible true) (2026-10-22).
- **Line dividers (2026-10-22, Rob: "not corners, the line"):** each line between two windows is its handle:
  a 12 dp touch strip along the whole line, a thin grey bar in the gap (`Js8LineHandle` in `Js8LineDividers.kt`).
  - PC mode / FT8Call: the corner spots, STATUS / Saved spot and waterfall spot are gone; five lines (Band |
    Message Window, Message Window | Call, Message Window / Typing Box, under the lists (stops at the buttons'
    top), waterfall | Waterfall Controls). Same keys, limits and View > Reset Window Sizes (`Js8PcSplitters`).
  - Upright: Band | Call (Call Activity width), under the lists, under the Message Window, above the waterfall
    (its top follows the finger, the page scrolls so its bottom stays). Keys `js8cfg_up_split_*` (dp).
  - Swipe Activity screen: Band | middle, middle | Call, Message Window / Typing Box (layout weights,
    `js8cfg_sw_split_*`). Dragging a line doesn't swipe the page.
  - Settings > View > **Reset Window Sizes** (under Clear All Activity): upright and Swipe only, at once.
  Checked on S22-Fresh: every upright and Swipe line, four PC lines (not Call | Message Window, not FT8Call),
  sizes saved, reset. Rob: "we'll fix it later if it's a problem" (touches near window edges grab the line).
- One timer at a time (2026-10-22, Rob: "make it like pc"): starting the CQ timer stops the HB timer and the
  other way round (PC UI_Constructor.cpp "disable HB loop while CQ loop runs and vice versa"), in the service's
  ACTION_CQ_TIMER / ACTION_HB_TIMER, so menus, taps and the API all do it. Checked on S22-Fresh: HB on, CQ 5 min
  -> HB plain (302 on CQ); HB on again -> CQ plain (904 on HB).

## Choices made without Rob confirming yet

- APRS page buttons named **OK** / **Cancel**; APRS menu items hidden sideways.

- **Phone** tab is in Station (Rob unsure; waits for the sideways work).
- Heading name **Operation** for MULTI / AUTO / HB / ACK in Settings.
- Clear toasts: "Band Activity cleared", "Call Activity cleared", "RX Activity cleared", "All Activity cleared".
- ⚠ icon is a drawn yellow triangle (PC uses the Windows warning icon; no hover text on phones).
- Speed list order: Normal first (PC 3.0.3 source lists Slow first).
- **Set grid from GPS** idea replaced by the Acquire GPS / Set row (Rob's design).

## Not done yet

- **Audit work (2026-10-31, Rob):** `AUDIT-3.0.3.md` is now the work list, [x] = done; order: section 1, sections 2 and 4, section 3 defaults to the PC, decoder stages, the rest. Done, not committed: decoder range and priority (`applyDecodeRange`, default filter 500-2500 Hz as the PC), MSG / mailbox / relay checksums (`checkedMsgText`; no text or @APRSIS = no check), buffers close at 60 s, drop at 90 s, match range per speed, first frame clears a buffer, commands answered on the last frame (`deferAutoReply`), relay through `queueAutoReply`, no replies with text in the Typing Box or a message to me arriving. Checked on S22-Fresh, no radio: log shows 500-2500 Hz and the saved offset; good MSG in one and two frames accepted, corrupt ones rejected. Not run: relay, the two reply gates, @APRSIS MSG TO: result, 60 s close. Injected MSG tests now need a real checksum.
- **Audit work, second batch (2026-10-31):** QUERY CALL (mailbox path, `heardStation`, `sinceText`), queries to my groups, one @ALLCALL / heartbeat reply per station in 15 minutes, HEARING? always replies and follows **Remove callsigns from call activity after:**, @HB counts as @ALLCALL, @APRSIS MSG TO: in the text, ACK rules (speed, open buffer, Pause while a call is selected, group MSG ID, the PC's stray bracket), heartbeat offset (`heartbeatOffsetHz`, PC findFreeFreqOffset). `JS8EngineService.typingBoxEmpty` ignores the app's own text shown there. Checked on S22-Fresh with AUTO on, no radio: SNR?, QUERY CALL, HEARING?, relay, @TESTG SNR?, no reply with HELLOBOX typed. Not run: 15-minute limit, QUERY MSGS to @HB, every heartbeat ACK rule, heartbeat offset, timed HB waiting. Difference from the PC: a timed HB waits in the service, it does not enter the queue, so it still ignores text in the Typing Box. S22-Fresh now has AUTO on, Autoreply Confirmation off, relay on, group @TESTG, HELLOBOX in the Typing Box.
- **Open list, in order (2026-10-31):** first GitHub release with `version.txt`; real APRS and JS8NET network tests; real radio items last, including HB, ACK and the waterfall with real JS8 signals. The privacy cleanup is Rob's decision. No test traffic to real networks from the emulator.
- **JS8NET spotting (2026-10-31):** `network/SpotClient.kt`, port of the PC's SpotClient: UDP JSON to `spot.js8call.com:50000`, queue sent once a minute, one host lookup. On with **Enable spotting to reporting networks**. `spotDecode` in the service: RX.SPOT for each "CALL:" frame, RX.DIRECTED for commands to @groups, multi-part ones joined with the checksum checked. Difference from the PC: spots are not held back for 4/5 of a cycle after a band change. PATH is always empty (relay calls are not parsed for this). **Trap:** debug pref `debug_spot_host` sends spots elsewhere; S22-Fresh has it set to 10.0.2.2. Set it before installing on any emulator with SPOT on, or test spots reach the real JS8NET. Tested against scratchpad `fakespot.py`: RX.LOCAL, spot, CQ, SNR? to @JS8NET, joined MSG, bad checksum dropped, spot blacklist, SPOT off. Not tried against the real server.
- **Own @APRSIS messages (checked 2026-10-31):** the PC has no direct send: its own @APRSIS messages only go on the air, same as the app. Nothing to change.
- **Check for Updates (2026-10-31):** `js8ui/Js8Updates.kt`, PC checkVersion for Easy JS8: reads `version.txt` from the latest release of RobBobbins/Easy-JS8. Help > **Check for Updates** (PC and FT8Call menu bars) and Station > General **Check for software updates at startup** (on, PC default). A failed read says nothing, as on the PC. **Easy-JS8 has no releases yet, so today nothing shows.** A release needs a `version.txt` asset holding the version. Debug pref `debug_update_url` points it at a test server.
- **Power default (2026-10-31):** 0 like the PC (`OutAttenuation`, was 100), for phones with no saved value.
- **Message Window drag (2026-10-31):** up and down drags scroll the box instead of the page (`Js8MainFragment`, touch listener next to `showMessageWindowMenu`). Seen scrolling upright, PC, Swipe and FT8Call; press-and-hold menu and double-tap word selection work upright. A new line jumps to the bottom, as the PC does.
- **About page (2026-10-31, Rob):** all lines centred; sideways the lines down to `Easy JS8 is a fork of JS8Android.` and Features sit in two columns (`renderAbout`). Sideways the left column starts with a bold `Easy JS8` and is centred top to bottom against Features. In Credits, JS8Call and the JS8Call-improved team are separate lines; upright the WSJT-X credit breaks after K1JT. Features now has 12 lines (added APRS-IS relay, Message mailbox, HB and CQ timers, Log QSO and ADIF export, API and logging links, Waterfall Controls): upright two columns of six, sideways one column. The page centres top to bottom when it fits, and scrolls from the top when not.
- **Upright OK/Save and Cancel (2026-10-31, Rob):** in the ← Back row at the right, in Station and Settings. Settings row order: ← Back, ⚙, Save, Cancel. Sideways unchanged.
- **OK / Cancel in Pages (2026-10-31, Rob):** in the Station screen, sideways Pages layout only, they sit at the right of the ← Back row (`barOnTop` in `Js8SettingsFragment`). PC and FT8Call keep them at the bottom. Upright moved them to the ← Back row too.
- **No tab swiping (2026-10-31, Rob):** the Swipe layout changes screens only with its five buttons (Header, Activity, Waterfall, Inbox, Station). `Js8PagerLayout` has no touch handling now. **Sideways layout:** now lists it as **Pages** (Rob, 2026-10-31); the saved value stays `swipe`, and older notes here still say Swipe. Test trap: `adb input swipe` starting within about 100 px of the screen's side edge is Android's Back gesture and leaves the app.
- **PC comparison batch (2026-10-31, tested on S22-Fresh, no radio):** seen working: JS8 60 send (4 s frames), Autoreply Confirmation Yes and No, Idle Timeout box at 1 minute and OK puts AUTO back, HB rate limit (second heartbeat within 55 minutes adds W8JJK to the Never acknowledge list), Restore Previous Message, press-and-hold menus (Band Activity, Typing Box, Message Window, waterfall, column headers), Sort By lists, Show Columns, Directed menu, File / View / Help / Configurations menus, View > Show panels sideways (hide and restore), Configurations Clone and Switch To, Save As picker opens, Suggest word choices with underline. `js8cfg_spellcheck` default set to true (PC).
- **Second test pass (2026-10-31, S22-Fresh, fake rigctld on the PC, no radio):** all passed: Clone Into, Rename, Reset, Delete; Power (300 = gain 0.032, 30 dB); Remember power by band (20m / 40m); Tx delay; Hold PTT (one key-down for the whole message); Heartbeat anywhere; Poll Interval; Frequency table Insert / Delete / Reset; Notifications Select / Test / Clear (Test sound not listened to); DIRECTED.TXT (needs Write log files, same as the PC); Missing Frame Character Indicator; UI colours; sending without a callsign (identify=false); FT8Call panels; sideways Offset lock; Display tab in PC and Swipe.
- **Fixed in that pass:** Poll Interval now reads the rig on its timer (it never did); a rig read that moves the dial now counts as a band change (Remember power by band follows it); PTT lead = Tx delay + the speed's start delay (was a fixed 500 ms); FT8Call header offset follows the real offset (stayed at 1500); Sound File box shows the file name (showed msf:1000000024).
- Test trap (2026-10-31): `adb shell input text` can lose the end of a long text sideways; the app sends what the box holds. `adb logcat -b crash` while running as the app raised Android's "Allow Easy JS8 to access all device logs?" box; the app does not read logs.
- **Need a real radio:** CAT button, Monitor returns to last used frequency, Handshake, Force Control Lines, Transmit Audio Source, Slope / Intercept, Channels, PTT Port.
- HB (timed heartbeats) and ACK (heartbeat answers) not tested with real transmitting;
  debug decode injection doesn't reach the engine's auto-reply code.
- Fixed this session: the decoder never listened for JS8 Slow (JS8Android passed speed numbers
  as decoder bits). The engine now starts with all four speeds (its timetable can only be
  narrowed later) and `configuredRxSubmodes()` narrows it for MULTI.
- **Keep decoded messages after restart** (Phone) does nothing visible; not a PC setting, left alone (activity lists are kept by the PC's own Reset ... at startup checkbox).
- **Checked against the code 2026-10-31:** CQ press-and-hold repeat menu built; Swipe tab 5 says Station.
- **PTT Method, Test CAT, Test PTT (2026-10-30, Rob):** `js8cfg_ptt_method` now read by `setRigPtt` (PC default VOX). Hamlib: CAT / VOX (no key command) / DTR or RTS on the rig's own serial port (PC's separate PTT Port not built). Network: VOX = no key command, anything else = CAT. RTS rig: RTS, or DTR if picked. truSDX, QMX, UV-Pro always key over their own link. **A Hamlib or network rig left on the default VOX no longer keys by CAT.** Test CAT saves the Radio page (Cancel doesn't undo that), restarts the engine to reopen the rig, 15 s, green / red; RX put back as it was unless the rig failed (the engine won't start without its rig, so RX goes off). Test PTT red while keyed; released by the button, leaving the Radio page, or 30 s (Easy JS8's safety); greyed for Rig None and VOX. Checked on S22-Fresh with a stand-in rigctld on the PC (commands logged): T 1 / T 0 from Test PTT, 30 s release, ← Back, CQ with CAT, no T with VOX, Test CAT green, red with no server, with RX off. Not tested: a real radio, Hamlib serial, DTR / RTS lines, truSDX / QMX / UV-Pro, sideways, Swipe.
- **Callsign lists (2026-10-30):** autoreply allow / block, heartbeat and spot lists read by the service. Autoreply and heartbeat lists tested (2026-10-31: K4RDX on the list gets no ACK, W1AW gets one). Spot list not run: it would send false spots to the real PSK Reporter; it uses the same `isOnCallList` check.
- **Split Operation (2026-10-31):** `js8cfg_split` used. PC formula: XIT = (offset / 500) * 500 - 1500, TX audio = offset - XIT, TX dial = dial + XIT. Fake It sets the dial before key-on and back after key-off. Rig: network `S 1 VFOB` + `I`, Hamlib `nativeSetSplit`, split off after key-off (PC leaves it on). truSDX and QMX use Fake It for both. Tested on a stand-in rigctld at 700 and 2300 Hz; not on a radio; Hamlib split not tested; VFO B mode not set.
- **Notifications (2026-10-31):** `tryNotify` in the service plays the phone's notification sound. PC events all fire in tests, call_old checked 2026-10-31 with a logged call (once per call). Sound not listened to.
- **UI fonts (2026-10-31):** Table, Received, Transmitted, Composition sizes. 10 pt = the built size. Applied on the main fragment, the same four places as the PC (`UI_Constructor.cpp` 390-411). Checked 2026-10-31 sideways in PC mode with Table 15, Received 12, Transmitted 18, Composition 15; Swipe and FT8Call not looked at.
- **Frequency Schedule (2026-10-31):** `js8cfg_schedule`, lines hz|HH:MM|HH:MM|description. Station > Frequencies: Insert ..., Delete, Switch to Frequency Now. `tryBandHop` in the service every 15 s, needs the service running. Box with 30 s countdown lives in `MainActivity` (2026-10-31), so it shows over every screen and comes back after a rotation or a return to the app. Not started while a message is queued or going out (PC isMessageQueuedForTransmit; `JS8EngineService.messageQueued`, set from TransmitViewModel). Checked on S22-Fresh, no rig: over Activity and Settings, through a rotation, Cancel keeps the frequency, 30 s runs out and changes it, a long message spanning the start time held the box until it finished. OK button not tapped (same call as the 30 s run-out). Service restart forgets a Cancel (PC restart too).
- **Last Tx (2026-10-31):** PC `last_tx_label`: `js8_status_blank` shows "Last Tx: " + the last frame that finished, in every layout (`Js8ActivityViewModel.lastTx`). Seen in Swipe sideways ("Last Tx: LAST TX" for "HELLO LAST TX"). Box grows with its text, except FT8Call (160 dp, cut off). Emptied by an app restart, as the PC.
- **Header arrows (2026-10-31):** sideways ▲▼ move the dial 250 Hz (PC). Tested without a rig.
- **Erase ALL.TXT / Erase js8call_log.adi (2026-10-31):** Settings > Log. ALL.TXT on the phone = js8call-log.txt and js8call-log.1.txt. adi tested; js8call-log.txt erase ran with no file to delete.
- Waterfall Controls → Display tab built; still untested with real JS8 signals: decode brackets,
  Cumulative level, Avg N.
- Halt / TUNE / transmit / SWL blocking not tested with a radio — test on a dummy load first.
- Swipe Activity screen: Status Bar row is wider than the screen on purpose (Rob, 2026-10-20: Last Tx runs off the right edge, the row scrolls); seen 2026-10-31, left as it is.

## Audit work state (2026-10-31)

- `AUDIT-3.0.3.md` (2026-11-01): 0 open. Nothing committed. The 17 last items and the listed full test pass were seen on S22-Fresh: upright, PC, Pages, FT8Call. Older ticks outside that test list were not all rerun.
- Fixed during the test pass: multi-row Delete in the Inbox did nothing (selection cleared before it was read); unticking **Automatically switch bands / frequencies at specific times of day** greyed the schedule only after reopening the page (the page now redraws and jumps to the top).
- Not proven: real FLRig, DX Lab Suite Commander, Ham Radio Deluxe and any radio; fake servers only (scratchpad `fakes.py`, ports 4599, 52002, 7809). Log QSO Start/End word exclusion and Comments as the double-tap target not run.
- **Where it stands (2026-11-01, end of session):** branch `easy-js8`, all audit work uncommitted in the working copy; commit only when Rob says, push only to `fork`. Waiting on Rob: whether the **Message Window** box on the Log page stays. Then real FLRig, DX Lab Suite Commander, Ham Radio Deluxe, then a real radio on a dummy load.
- Small faults left: **Secondary Highlight Background** button on Settings > UI has its second line cut off; Inbox Date column clips the last digit on bold rows upright; Frequencies page jumps to the top when the schedule box is ticked.
- Test helpers from this session are in `C:\Users\Robert\AppData\Local\Temp\claude\E--new-w3bguru-websites-easy-js8\3f7d8426-1099-44c1-bd68-34b39530ba8f\scratchpad`: `u.py`, `inj.py`, `sig.py`, `t.py`, `fakes.py`, `sub.py`. S22-Fresh now has rig None and network on; `psk_reporter` is on, with test values left in place.
- Test notes: my scroll swipes drag the upright dividers; delete `js8cfg_up_split_*` to reset. S22-Fresh closed once during testing, cause unknown, no crash record. Debug audio feed: ACTION_DEBUG_JS8_PCM, debug builds only.
- Test paths: fake rig `C:\Users\Robert\AppData\Local\Temp\claude\E--new-w3bguru-websites-easy-js8\99ccad85-68d1-4545-83b4-3b349ad7f132\scratchpad\fakerig.py` (PC port 4599, argument = seconds; app host 10.0.2.2); PC source `...\scratchpad\pc`; S22-Fresh saved prefs `build-env\test\out\prefs.bak.xml`. Wait for rotation before tapping. Git Bash: `export MSYS_NO_PATHCONV=1` before adb paths.
- ALL.TXT (2026-11-01): `MessageLogWriter.kt` writes the PC lines, no rotation; old `js8call-log.txt` is left where it is. The frame column holds the first 12 decoded characters because the app has no raw frame. Received lines not yet seen: injected decodes bypass that path.
- Rig Control Error box tested with a fake rigctld on the PC, port 4599: scratchpad script, S22-Fresh prefs restored after.
- Status label shows "Tx: <typed text>", not the frame being sent; "Decoding" not added.
- Decoder: PC 3.0.3 frequency and timing tracking, whitening with LLR erasure, soft combining and LDPC feedback are in `core/src/decoder/` (`FrequencyTracker.*`, `whitening_processor.h`, `soft_combiner.h`, `ldpc_feedback.h`: PC files with Qt removed, environment switches at PC defaults).
- Decoder test (made-up signals, 60 to 100 frames per case, run on S22-Fresh with a test program, 2026-10-31): single frames 1 to 3 of 60 fewer than the old decoder at -18 to -20 dB, one frame repeated 4 times 15 of 60 against 11, CPU about 30% more. Plain noise only, no real HF audio, no phone CPU figure.
- Injected MSG tests need a checksum over the whole message, for relay including the destination: `K4RDX HI THERE`.
- RX.DIRECTED and DIRECTED.TXT: buffered commands now wait for the last frame and checksum. DIRECTED.TXT needs `log_messages_to_file` on.
- Decided: **File > Station…** stays (Rob, 2026-10-19). **About** stays, it opens the Easy JS8 About. **Directed to CALL** falls back to **Directed** where the button is too narrow. Send shows Send, Ready, Sending with no seconds yet.
- Rob, 2026-10-31: come back to the decoder stages; they are committed, the question of keeping them is open until they are tried on real signals.
- Lines 404-406 above are out of date, see audit section 15.

## Test pass state (2026-11-02, 21:30)

Read `TEST-PLAN.md` and `TEST-CHECKLIST.md` for the steps and every result. Phone and real radio stay last.

**Checklist count.** 988 rows: all done twice, 0 Failed. 379 cells are N/A; 359 of them are sideways copies of H, I, K, L, M, N rows ("Upright only (TEST-PLAN One layout)").

**U rows (real networks), Rob said use VA3OSO.** `ureal.py` with `realrelay.py` running (stop `fakespot.py` first, both use UDP 50000). The relay passes TCP 127.0.0.1:14581 to rotate.aprs2.net:14580 and UDP 50000 to spot.js8call.com, logs both to `realrelay.log`, and watches APRS-IS read-only as EJS8TEST for packets from VA3OSO. The app logs in as APJ8CL (as JS8Call does) and gets "verified"; an injected `VA3OSO: @APRSIS GRID EN94MN` came back on the APRS-IS feed as `VA3OSO>APJ8CL,qAS,VA3OSO:=4432.50N/08100.00WG#JS8 14.079500MHz -05dB`; RX.LOCAL with VA3OSO and EN94MN went to spot.js8call.com (JS8NET sends no answer, so delivery is not proven). Each run sent one APRS position and one RX.DIRECTED to JS8NET for VA3OSO.

**Old Failed rows.** S07, Q10, E36, H28, H37 were test faults ("corrected check"). J04U is N/A: no Offset group upright by design. H36P was an app fault, fixed: the PC and FT8Call grab strips covered the Typing Box (`Js8LineDividers.kt`, `Js8PcSplitters.kt`). Downside: inside the Typing Box only its top and bottom 10 PC dp grab the dividers.

**Fixed 2026-11-03 (Rob).** The Settings **Reset Frame Sizes** button now also clears the PC and FT8Call sizes (`Js8PcSplitters.clear`, also used by **View > Reset Frame Sizes**). One tap resets all four layouts; the dividers move back when Settings closes. Checked with `ui12.py` in PC and FT8Call twice, with screenshots, and I24 / I25 rerun twice in PC and Pages.

**App code changed, uncommitted.** The Typing Box fix above, the Reset Frame Sizes fix. `JS8EngineService.kt` debug decode hook takes `--ef quality` (for A13; debug receiver only).

**Weak passes.** H29, H30 and J rows that only reach a control prove it is on screen. J28 to J30 passed from pictures (`shots/jlook_*.png`). O08 measures only the difference between two Tx delays (0.39 to 0.40 s for 0.4 s); the emulator clock is about 1 s off the PC clock. Pictures show the clock with "(+3530ms)" drift left by the auto-drift tests.

**T rows (JS8Call on the PC).** `tlink.py` runs JS8Call 3.0.2 as K4PCT with `--rig-name ELINK`; its profile is `%LOCALAPPDATA%\JS8Call - ELINK\` and `JS8Call - ELINK.ini`, built by `pcprofile.py` from Rob's `JS8Call.ini` (only read, never changed). No rig, no PTT port, every reporting network off, API on TCP 2443. Audio: JS8Call hears VBMatrix Out 4 and plays to VBMatrix In 8; the script records VBMatrix Out 8, cuts each slot, feeds it to the app with DebugJs8Receiver, and plays the app's TX.FRAME tones into VBMatrix In 4. About 1090 s a run.

**Test scripts added.** `air12.py` to `air14.py`, `ui10.py`, `ui11.py`, `tlink.py`, `pcprofile.py`, run scripts `air13run.sh`, `air14run.sh`, `tlrun.sh`, `step7.sh`, `step8.sh`, `step8b.sh`, `j13.sh`. `fakes.py` logs milliseconds; `fakepsk.py` logs the whole packet. Run times in `build-env/test/lib/timing.log`.

**Emulator.** Check `dumpsys gfxinfo com.easyjs8.app` "50th gpu percentile" before each long run. At 4950 ms restart S22-Fresh; it happened twice today. The first start after a kill fails, the second works.

**Test traps found.** `js8cfg_wf_center` must be an int: a string crashes the app on start. `js8cfg_api_dynamic_info` true overrides `my_status`. `psk_reporter` must be on for APRS and for RX.SPOT. Injected decodes never make a spot report; Q07, R11, R12 play a real frame. `js8cfg_heartbeat_anywhere` left on breaks E18. The PC heartbeat slot pick is random, as in JS8Call.

**Stand-ins.** `fakes.py`, `fakeaprs.py`, `fakepsk.py`, and `fakespot.py` may still be running on the PC. Nothing from today is committed.

## Test pass state (2026-11-03)

**Checklist count.** 1,007 rows: all passed twice or are N/A, 0 Failed. W19 (real Hamlib rigctld) passed at 18:16 with `rigctld.exe` started by Rob. 35 cells are N/A, none of them "Upright only (TEST-PLAN One layout)" any more: step 16 ran every sideways copy twice, one layout at a time, with `side16.sh <run> <P|G|F> <script> <limit s>` (`need.py` picks the rows left, `side.py` patches the upright scripts for the layout).

**W rows.** W01 to W18 passed twice: `wlink.py` (W01 to W05), `tlink2.py` (W07 to W09), `wui.py` (W06, W10 to W18). Their result files name the rows with the layout letter (W06U), so they were copied onto the checklist by hand in step 17.

**App fixes (2026-11-03, uncommitted).**
- Signal meter value held to 0 to 90 dB (`status.powerDb.coerceIn`).
- RX and TX at startup work again after Back closes the app: `MainActivity.onStop` calls `Js8MainFragment.appClosed()` when finishing (W12).
- Message Window press-and-hold menu opens at the press point, as the Band Activity row menus do (`pressAnchor` in `Js8MainFragment`). Sideways it had only two rows of room above the Message Window, so Clear needed a scroll (H34P). All 6 items show in all four layouts; pictures `shots/d1_menu_U/P/G/F.png`.
- AUTO at startup as the PC (Rob, "just do what the program does"): Station > **Turn autoreply on at startup** ticked starts AUTO on (with a callsign and grid), unticked starts it off even if it was left on (UI_Constructor.cpp 691). A new install starts ticked (PC default). Replaces the 2026-10-22 rule "unticked = AUTO as left". S05 now checks both.

**Test script fixes for sideways (step 16).** Gestures subtract the app window's origin (camera cutout, x 136); swipes kept away from the Save / OK bar; PC and FT8Call reach the Inbox through View > **Show Message Inbox...**; Settings and Station scroll more times sideways; dialog fields scrolled into view before typing. H28 in FT8Call checks no Freq or Auto Reply (FT8Call's bar has none, `js8_status_bar_ft8call.xml`). M16 takes a screenshot when it fails: M16F failed once in run 2, cause not known (no screenshot then), passed in a trial and on retry. `ui7.py` line endings put back to LF.

**Test traps found.** The **New message from …** notification banner covers the PC menu bar for about 5 s; `ui8.py` goes Home and waits when a tap is taken. In `ui1.py` the Settings (L) and menu (N) sections record every row whatever `JS8_ROWS` says; rerun H rows with section `H` only. S22-Fresh slowed to 4950 ms frames twice today (14:29, 16:24) and its Android system died once (12:31); a restart fixed each.

**Seen, not changed.** On Station > Audio the bottom edge of the **Remember power settings by band** box is cut off (the page scrolls a little).

**Backups.** Checklist before step 16 and step 17, and the result files step 17 overwrote: `C:\Users\Robert\AppData\Local\Temp\claude\E--new-w3bguru-websites-easy-js8\f8df57d0-2324-45f1-b508-d9d9199b79c9\scratchpad\` (`TEST-CHECKLIST.before-step16.md`, `results-before-step17\`).

## Build (Windows, Git Bash, folder `E:\new-w3bguru-websites\easy js8`)

Docker Desktop must be running. First time only: `docker build -t easyjs8-build build-env`

```
build-env/test/docker-build.sh
```

Uses volumes `easyjs8-gradle`, `easyjs8-android` (debug signing key — keep it, or updates won't
install over the last build) and `easyjs8-ndk` (avoids a 600 MB NDK download every build).
APK: `android/app/build/outputs/apk/debug/app-debug.apk`. Details: `build-env/build.cmd.txt`.

## Test on the emulator

- **Test emulator: AVD S22-Fresh = `emulator-5600`** (Rob, 2026-10-21: "S22 fresh is the one"). Same screen and
  Android as S22 (1080×2340, 480 dpi). Start: `C:\Users\Robert\AppData\Local\Android\Sdk\emulator\emulator.exe -avd S22-Fresh -no-snapshot -port 5600`,
  close: `adb -s emulator-5600 emu kill`. Check the name before installing (`adb -s emulator-5600 emu avd name`).
  Test callsign VE3TST.
- **S22 is retired**: kept on disk, closed, not used (the Easy FT8 session kept installing onto it). Earlier
  "Tested on S22" notes in this file are from it.
- Other projects' emulators (EasyFT8, EasyWEFAX, EasyAPRS) are never touched.
- `build-env/test/run.sh` and `inject.sh` use `-s emulator-5600` (S22-Fresh) since 2026-10-21.
- S22-Fresh was made 2026-10-21 for brand-new-install tests. First test: a fresh install opens Station Settings; Station Status shows IDLE <MYIDLE> VERSION <MYVERSION>
  and the first OK saves it.
- Saved > **APRS GRID** (2026-10-21, Rob): no page any more; sends "@APRSIS GRID <saved grid>" like a saved
  message (alert if no valid grid). Tested on S22-Fresh: "VE3TST: @APRSIS GRID CM87WK91" in the Message Window.
- Station > Radio > **Rig:** list has a search box (2026-10-21, Rob): matches any part of the name, ignoring case,
  spaces and dashes (`searchableChoice`). Checked on S22-Fresh: Hamlib list loads, "7300" -> IC-7300 / IC-7300MK2,
  "yaesu" -> Yaesu rigs, a pick lands in the box.
- Upright (2026-10-22, Rob): Station > Radio **Poll Interval** box 90 dp; Waterfall Controls QSY | Center,
  Palette | Adjust..., Current | Smooth side by side (`Js8WaterfallControls.fitUpright` sideBySide). Sideways unchanged.
  Checked on S22-Fresh.
- Every transmission shows in the Typing Box (2026-10-21, Rob: "all messages do it"): heartbeats, ACKs, AUTO
  replies, CQ timer go into the empty box (text without "CALL: ", from EXTRA_TX_FRAME_FULL_TEXT), struck out
  part by part, emptied at the end; typed text is never replaced (TypedTx.auto, typingBoxEmpty). Checked on
  S22-Fresh: HB button -> "@HB HEARTBEAT CM87" struck out, box emptied. AUTO reply checked too: injected
  "K4RDX: VE3TST SNR?" -> "K4RDX SNR -03" in the box, struck out. (A first try got no reply because AUTO was off:
  "Turn autoreply on at startup" unticked switches AUTO off at each app start, as the PC.)
- RX / TX at startup (2026-10-21): Receiver (RX) off at startup and Transmitter (TX) off at startup were never
  read, so RX stayed off after every fresh start. Now once per app start (Js8MainFragment, startupRxTxDone), as the
  PC (mainwindow.cpp 7761): RX starts unless RX off is ticked, TX is set on / off from TX off. Checked on S22-Fresh
  (both cases) and S22 (Rob closed and reopened: RX came on). Runs once per app process: closing in a way that
  leaves the process running (back button?) would skip it; not tested.
- Typing Box strike-out (2026-10-21, Rob): the count left out the "CALLSIGN: " the first part starts with, so it
  ran 8 characters ahead (short messages crossed out at once). Fixed in JS8EngineService.broadcastTxFrame.
  Checked on S22-Fresh: APRS GRID part 1 strikes "@APRSIS GRID", part 2 "CM87WK91"; Message Window part by part.
- Script traps (2026-10-31): `adb shell input keyevent 4` closes the app when no keyboard is up, and that stops the service. A swipe starting within about 150 px of the right edge is Android's Back. Sideways, taps don't line up with screenshot positions by 136 px (camera cutout bar).
- `build-env/test/run.sh <name> install` installs, launches, saves `build-env/test/out/<name>.png`.
  `ROT=1 run.sh <name>` = sideways first (`ROT=0` upright). Rob may rotate the window by hand,
  so always pass ROT.
- Test decodes: `build-env/test/inject.sh "<text>" <offset Hz> <type> [snr]`
  (type bits: 1 first frame, 2 last frame, 4 data; 3 = single frame).
- Gestures the emulator can't send (pinch, double-tap, two-finger drag), debug builds only:
  `adb -s emulator-5554 shell am broadcast -n com.easyjs8.app/com.js8call.example.debug.DebugGestureReceiver --es gesture pinch --ef x 700 --ef y 300 --ef factor 2`
  (also `doubletap`, `drag` with `--ef dx/dy --ei fingers 2`). Pinch start span must be large (code uses 300 px).
- Settings file: `adb -s emulator-5554 shell "run-as com.easyjs8.app cat shared_prefs/com.easyjs8.app_preferences.xml"`
- Tips: scroll the upright page by dragging the header (a drag on the Typing Box becomes
  keyboard swipe-typing). `adb emu geo fix` positions arrive late; the emulator's built-in
  position is Mountain View (CM87WK91). The emulator is short on memory; its System UI can freeze
  ("System UI isn't responding" → Wait). `uiautomator dump` only lists on-screen views.
- Test values (callsign, grid, frequency) on the emulator don't matter; no need to restore them.
- **Test traffic** (fills Band / Call Activity; Rob asked for it to stay on while tweaking lists):
  run in the background from the project folder, every 60 s:
  `while true; do T=build-env/test/inject.sh; $T "VE3ABC: @ALLCALL CQ CQ CQ FN03" 620 3 -8; $T "W1AW: VE3KTB HW CPY?" 745 3 -14; $T "K4RDX: @ALLCALL CQ CQ FM17" 860 3 -3; $T "VA3TOM: W8JJK SNR -11" 985 3 -11; $T "N0QBF: @ALLCALL CQ CQ CQ EN34" 1105 3 -19; $T "VE7XYZ: KC1ABC GRID?" 1230 3 -6; $T "W8JJK: VA3TOM SNR -15" 1355 3 -15; $T "KC1ABC: VE7XYZ GRID FN42" 1480 3 -9; $T "VE3KTB: W1AW FB OM TNX 73" 1605 3 -2; $T "AA5BC: @ALLCALL CQ CQ EM12" 1730 3 -21; $T "VA2PQ: N0QBF INFO?" 1855 3 -12; $T "K9ZZ: @APRSIS GRID EN61" 1980 3 -7; $T "VE1LU: @ALLCALL CQ CQ CQ FN74" 2105 3 -16; $T "WB6TX: AA5BC QSL?" 2230 3 -4; sleep 60; done`
  None of these are addressed to VA3OSO, so nothing auto-replies.
- Rob also uses S22 by hand: don't type into the emulator while he is testing, and if a
  screenshot looks wrong, ask before assuming. Easy FT8 screenshots (DX Call, Tx even/1st, FT8
  in the status bar) belong to the other project.

## Reference material

- PC screenshots: `docs/reference/pc-main-window-3.0.2.png`, `docs/reference/pc-settings-window.png`.
- PC source 3.0.3: `git clone --depth 1 --branch v3.0.3 https://github.com/JS8Call-improved/JS8Call-improved.git`
  Key files: `JS8_UI/styles.h`, `mainwindow.ui`, `mainwindow.cpp`, `Configuration.ui/.cpp`,
  `JS8_Mainwindow/*.cpp` (processDecodeEvent, displayBandActivity, displayCallActivity,
  processRxActivity, processCommandActivity, buildQueryMenu), `JS8_Main/CPlotter.cpp` (waterfall),
  `JS8_Include/Maidenhead.h` (grid rules), `JS8_Mode/JS8Submode.cpp` (speeds).
- JS8Call is installed on Rob's PC: `C:\Program Files\JS8Call` (3.0.2).

## Working with Rob

Rob's rules are in his global CLAUDE.md (start replies "You're my boy Blue!", no writes until
he types LETS GO, check work visually). Answer only what he asked about; he describes layouts
from the phone's point of view.
- **Don't ask sideways questions during upright work** — it loses him. Sideways waits until he raises it.
- One question at a time, short. A small text mockup of the screen works better than paragraphs.
- When he repeats himself, take his words literally (e.g. "File, Station") instead of offering options again.
- Research before answering (PC source, his captures, the engine code); he notices guesses.

- **Header mode button (2026-10-31, Rob):** every design: RX TX mode / SPOT LOG Settings TUNE. `js8_btn_mode` = PC updateModeButtonText (`renderModeButton`), fixed size, text shrinks to fit. Tap = PC menuModeJS8 (`showModeMenu`), applied at once, same prefs as the Settings window. Speeds grey while a message is queued or sent. `js8_btn_settings` opens Settings. PC header buttons start at 700 dp, callsign block moved to 358 dp; FT8Call right box 252 dp, middle box 665 dp (also in `build-env/gen_pc_layout.py`).
- **Message Window upright (2026-10-31, Rob):** drags claimed at finger down, the page never moves. Sideways unchanged.
