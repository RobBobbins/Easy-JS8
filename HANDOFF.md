# Easy JS8 — Handoff (2026-10-19, end of the second session)

Read this first, then `LAYOUT.md` (the layout spec and the PC values it copies; updated for
this session too).

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
