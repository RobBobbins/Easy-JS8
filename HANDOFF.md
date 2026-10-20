# Easy JS8 — Handoff (2026-10-20, end of the third session)

Read this first, then `LAYOUT.md` (the layout spec and the PC values it copies; updated for
this session too).

**Fourth session (2026-10-20) worked on sideways, mostly PC mode** (see "Sideways, fourth session").
**Fifth session (2026-10-20):** Waterfall Controls → **Timing** tab built (see "Timing tab").
**Next:** Rob to decide on the engine's waterfall detail (see "Engine spectrum detail" below); the
wpm box upright is still to place. Committed: fourth session 7717b9d8, Timing tab 71a831f2, Display
first part 7d421c12; Display bigger group not committed yet.

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
  Bottom: **OK** / **Cancel** (PC's). Sideways: the PC window with tabs (unchanged).
  PC-only settings hidden (TCP/UDP API, WSJT-X, N3FJP, N1MM, Save Directory, PTT command,
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
  (0–3000 Hz scale until the spectrum arrives). No 500 Hz floor.
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
- Easy JS8 itself does not gate @APRSIS to the internet (engine has no APRS-IS code); a PC station
  with spotting on has to hear it.

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
  (`js8cfg_show_ft8`, on by default); off = decoder not fed.
- ft8_lib (Kārlis Goba, MIT) copied from Easy FT8 into `vendor/ft8_lib`, built into js8core-jni.
  Core engine `EngineCallbacks.on_rx_audio` hands each audio chunk with its UTC-minute ring position
  to `adapters/android/jni/ft8_monitor.cpp`, which assembles 15 s slots and decodes on its own thread.
- Debug test (no radio): `adb push <15 s 12 kHz WAV> /data/local/tmp/ft8.wav`,
  `adb shell "run-as com.easyjs8.app cp /data/local/tmp/ft8.wav files/ft8.wav"`, then
  `adb shell am broadcast -n com.easyjs8.app/com.js8call.example.debug.DebugFt8Receiver --es file ft8.wav`
  (RX on). Test WAVs: `E:\new-w3bguru-websites\easyft8\ft8af\app\src\main\cpp\ft8af_glue\testdata\ft8`
  (20m_busy_test_01: 18 of 24 listed decodes). Not tested with real FT8 over a radio.

### Call Activity menu, menus, status bar (third session, 2026-10-19/20)

- **Press and hold a callsign** in Call Activity (all layouts) = the PC's right-click menu
  (`UI_Constructor.cpp` tableWidgetCalls customContextMenuRequested), `Js8MainFragment.showCallMenu`:
  Jump to <offset>Hz · Jump to <speed> speed (only if different and sendable) · Jump to <n> ms
  time drift · Log... (greyed, LOG not built) · Show Message Inbox... (scrolls to Inbox; greyed
  without messages from them) · Store Message... (greyed: engine can't hold messages for others)
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

### Waiting for Rob's yes

- Settings window field widths (upright): **Frequency:** dropdown 200 dp, **Custom frequency
  (MHz):** 120 dp, **Offset:** 120 dp including ▲▼, each left-aligned under its label. Speed
  box, checkboxes, Clear buttons unchanged; Station pages keep full-width boxes.

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
  Upright / Swipe keep `js8_header_buttons.xml` (87:87:217, 27 dp).
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
  **Next:** Timing tab (Rob's capture: Time Drift ∧ ∨, Start Automatic Time Drift, Stop After n
  decode(s), three Set Time Drift to Now buttons, Reset Time Drift; engine has time drift), then
  Display tab (Rob's capture: Start, Bins Per Pixel, Palette, Scroll Speed, Avg N, Flatten, Smooth,
  Waterfall Gain/Zero, Display Decode Attempts, Spectrum, Filter Opacity — mostly new drawing code).
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
  Line endings: most source files are CRLF, some LF — keep each file's own when editing.
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
- **Engine spectrum detail (found, not changed):** the engine's waterfall FFT uses one audio buffer:
  `compute_spectrum` takes min(frames, 4096) rounded down to a power of two; the emulator delivers
  384 frames, so 256-point FFTs = 47 Hz per bin (PC: 16384 points, 0.73 Hz), interpolated onto
  the display. That is why the phone waterfall shows wide soft stripes. Fix would collect samples
  across buffers in the engine. Real S22 buffer size unknown.
- Station sideways: **About** is the last tab; the tab row scrolls to the lit tab.
- Header Offset slider: locked while a message waits or is sent (same rule as waterfall taps).
  Not tested (needs a queued message).
- Still open: header ▲▼ beside the frequency do nothing (no code behind them).
- **wpm box** (PC `wpm_label`, `updateTextStatsDisplay`): PC mode only so far, after the progress
  bar (`js8_status_wpm`, 150 dp, 9 dp gap). "12.0wpm / 58.0cpm" for the Typing
  Box text, counted 100 ms after typing stops, hidden while the box is empty. Engine: new static
  `JS8Engine.messageFrameTexts()` (JNI `nativeBuildFrameTexts`, core `build_message_frames`).
  PC mode progress bar now 136 x 14 dp (was 200 x 12; width measured beside the wpm box on Rob's
  PC capture, thicker at Rob's request).
  Swipe: placed after Auto Reply, 150 dp; to fit, Swipe's Receiving box is 110 dp and its
  progress bar 100 dp (`js8_status_bar_swipe.xml`). Upright: not placed yet (Rob: one at a time).

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

## Choices made without Rob confirming yet

- APRS page buttons named **OK** / **Cancel**; APRS menu items hidden sideways.

- **Phone** tab is in Station (Rob unsure; waits for the sideways work).
- Heading name **Operation** for MULTI / AUTO / HB / ACK in Settings.
- Clear toasts: "Band Activity cleared", "Call Activity cleared", "RX Activity cleared", "All Activity cleared".
- ⚠ icon is a drawn yellow triangle (PC uses the Windows warning icon; no hover text on phones).
- Speed list order: Normal first (PC 3.0.3 source lists Slow first).
- **Set grid from GPS** idea replaced by the Acquire GPS / Set row (Rob's design).

## Not done yet

- HB (timed heartbeats) and ACK (heartbeat answers) not tested with real transmitting;
  debug decode injection doesn't reach the engine's auto-reply code.
- Fixed this session: the decoder never listened for JS8 Slow (JS8Android passed speed numbers
  as decoder bits). The engine now starts with all four speeds (its timetable can only be
  narrowed later) and `configuredRxSubmodes()` narrows it for MULTI.
- Sideways (PC and Swipe) not updated for any of this session's window changes; Swipe tab 5
  still says **Settings** but shows Station.
- Header **Offset** slider (sideways) is not blocked while a message waits (PC blocks it).
- LOG button (Log QSO window); ✓ / Name / Comment columns stay empty until there is a log.
- CQ button repeat menu (PC right-click: repeat every 1/5/10/15 min, Send CQ Now) not built.
- PC keeps Call Activity and Message Window text across restarts unless **Reset the Band
  Activity, Call Activity, and RX history at startup**; Easy JS8 starts empty and that checkbox
  is unused. **Keep decoded messages after restart** (Phone) does nothing visible.
- Saved but not used: autoreply allow/block lists, PTT method, split, notifications, UI fonts,
  frequency schedule, Test CAT / Test PTT.
- Waterfall Controls → Display tab; waterfall shows 0–3000 Hz (PC ~650–1400 Hz), which is why
  the offset dial is only ~4 dp wide on the phone.
- Halt / TUNE / transmit / SWL blocking not tested with a radio — test on a dummy load first.
- Swipe Activity screen: Status Bar wider than screen (progress bar scrolls into view).

## Build (Windows, Git Bash, folder `E:\new-w3bguru-websites\easy js8`)

Docker Desktop must be running. First time only: `docker build -t easyjs8-build build-env`

```
build-env/test/docker-build.sh
```

Uses volumes `easyjs8-gradle`, `easyjs8-android` (debug signing key — keep it, or updates won't
install over the last build) and `easyjs8-ndk` (avoids a 600 MB NDK download every build).
APK: `android/app/build/outputs/apk/debug/app-debug.apk`. Details: `build-env/build.cmd.txt`.

## Test on the emulator

- AVD **S22** (1080×2340, 480 dpi) = `emulator-5554`. Another emulator **EasyFT8**
  (`emulator-5556`) belongs to another project — never touch it. Scripts use `-s emulator-5554`.
- Start it: `C:\Users\Robert\AppData\Local\Android\Sdk\emulator\emulator.exe -avd S22`
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
