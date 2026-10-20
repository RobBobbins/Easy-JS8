# Easy JS8 — Layout Spec

Easy JS8 is a fork of JS8Android (github.com/JS8Call-improved/Android-port), GPLv3.
The JS8Android engine (decoder, transmit, audio, rig control) is kept. The screens are
replaced with a copy of the JS8Call 3.0.3 desktop window.

Reference: JS8Call v3.0.3 source, files `JS8_UI/styles.h`, `JS8_UI/mainwindow.ui`,
`JS8_UI/mainwindow.cpp` (Windows style branch). Colors, sizes and fonts below come from
that source and were checked against a capture of JS8Call 3.0.2 on Rob's PC
(3.0.2 → 3.0.3 changed only Mode menu shortcut letters).

Test phone: Samsung Galaxy S22 (about 360 dp wide in portrait).

Updated 2026-10-19 (second session): upright header, Settings and Station windows, ⋮ removed
upright, waterfall offset. **Sideways was not changed in that session** and still matches the
older description below (PC mode, Swipe layout, Menu page, Settings tabs).

---

## Modes

| Phone held | Layout | Behaviour |
|---|---|---|
| Upright (portrait) | **Vertical mode** (always) | Sections stacked, one long page, scrolls up/down. No pinch zoom. |
| Sideways (landscape) | **PC** or **Swipe**, chosen by the **Sideways layout** setting (default PC) | see below |

App name **Easy JS8**, app ID `com.easyjs8.app` (installs next to JS8Android).

## Sections (names used in this project)

| Name | PC window contents |
|---|---|
| Menu | File, Configurations, Mode, Log, View, Control, Help |
| Header | Frequency, up/down arrows, Offset slider, callsign, UTC clock, RX, TX, SPOT, LOG, NORMAL+MULTI+AUTO+CONF, TUNE |
| Band Activity | Left list: Offset, Age, SNR, Message(s) |
| Message Window | Yellow box "INCOMING AND OUTGOING MESSAGES WILL APPEAR HERE." |
| Typing Box | White box "TYPE YOUR OUTGOING MESSAGES HERE." |
| Call Activity | Right list: Callsigns, Age, SNR, Offset, ✓, Name, Comment |
| Buttons | CQ, REPLY, SNR, INFO, STATUS, Saved, Directed, Deselect, Send, Halt |
| Waterfall | Waterfall, with CAT button and level meter (0–90 dB) on its left |
| Waterfall Controls | Control tab: Offset, QSY, Center, Filter; level slider |
| Status Bar | Receiving, (blank), JS8 Normal, Freq, Auto Reply, cycle progress (e.g. 3/15) |
| Inbox | Docked messages panel: ID column, Reply button |

In Easy JS8 the PC's NORMAL+MULTI+AUTO+CONF button is the **Settings** button (all layouts):
same place, same blue bold style, opens the Settings window.

## Vertical mode (portrait), top to bottom

1. **Header**:
   - callsign, UTC clock under it
   - RX, TX, **Settings**
   - SPOT, LOG, TUNE
   - No frequency display, no ▲▼, no Offset slider, no ⋮ (moved to the Settings window).
2. **Band Activity** and **Call Activity**, side by side. Call Activity is 130 dp wide (icon and
   Callsigns columns), Band Activity takes the rest. Band Activity starts with only Age and
   Message(s); **Settings → View → Show Offset in Band Activity / Show SNR in Band Activity** bring
   those columns back (same setting as press-and-hold on the header). Sideways keeps the PC columns.
3. **Message Window**
4. **Typing Box**
5. **Buttons**, two rows of five, neat even widths:
   - CQ, REPLY, SNR, INFO, STATUS
   - Saved, Directed, Deselect, Send, Halt
6. **Status Bar** (the mode label and cycle counter follow the speed: JS8 Normal n/15, JS8 Fast n/10 …)
   Upright order: Receiving · cycle progress bar (to the page's right edge, rounded ends like the PC)
   · JS8 Normal · Freq · Auto Reply · Last Tx; the last four scroll in from the right (`js8_status_bar_upright.xml`).
7. **Waterfall**
8. **Waterfall Controls** (upright: no Offset box; QSY first)
9. **Inbox**

## Settings window (header Settings button)

Operating settings. Easy JS8 only (the PC has these in its Mode / View menus and header).

```
← Back                         ⚙
 Operation
   [x] Enable Simultaneous Decoding of All Speeds (MULTI)
   [ ] Enable Autoreply (AUTO)
   [ ] Enable Heartbeat Networking (HB)
   [ ] Enable Heartbeat Acknowledgments (ACK)      (greyed unless AUTO and HB)
 Mode
   Speed: (•) JS8 Normal  ( ) Fast  ( ) 40  (JS8 60 greyed)  ( ) Slow
 Frequency
   Frequency: [ 20m - 14.078 MHz ▾ ]
   Custom frequency (MHz): [      ]
 Offset
   Offset: [ 1500 Hz ▲▼ ]
 View
   [ ] Show Band Heartbeats and ACKs
   [Clear Band Activity] [Clear Call Activity] [Clear RX Activity] [Clear All Activity]
                                    [Save] [Cancel]
```

- **← Back** works like Cancel. The **gear** opens the Station window.
- **Save** / **Cancel** pinned to the bottom. The Clear buttons act at once.
- Operation = the rest of the PC Mode menu with the PC defaults (MULTI on; AUTO, HB, ACK off).
  AUTO is the live switch; Station → **Turn autoreply on at startup** sets it when the app starts.

## Station window (Settings → gear)

Copy of JS8Call **File → Settings** (JS8_UI/Configuration.ui, defaults from Configuration.cpp).

- **Upright**: **← Back** at the top, then a list of pages (the PC's tab names):
  General, Radio, Audio, Reporting, Frequencies, Saved Messages, Notifications, UI,
  Diagnostics, Phone. Tap a page to see only it; ← Back (or the phone's back) returns to the
  list; ← Back on the list works like Cancel. Sub-tabs (General: Station, Behavior, Networking &
  Autoreply; Radio: CAT Control, Rig Options; UI: Band & Call Activity, Messages, Composition)
  are headings on their page. Label above its box.
- **Sideways**: the PC window: tabs and sub-tabs, label beside its box.
- **OK** / **Cancel** pinned to the bottom in both. Turning the phone keeps the window open and
  keeps unsaved changes.
- General → Station Details: My Callsign, **SWL (Listening only)**, My Maidenhead Grid Locator,
  **Acquire GPS · (grid found) · Set**, Callsign Groups, Do not participate in the @ALLCALL group.
- Empty callsign / grid boxes show a ⚠ at their right end (PC attachRequiredIndicator).
- PC-only settings hidden: TCP/UDP API, WSJT-X, N3FJP, N1MM, Save Directory, PTT command,
  Application stylesheet / font, Check for software updates.
- Extra **Phone** page at the end for JS8Android settings with no PC equivalent
  (Sideways layout, Transmit audio boost, Keep screen on, Keep decoded messages, Automatic time sync).
- Settings JS8Android already had keep their stored keys (callsign, grid, my_groups, my_info,
  my_status, rig_*, transmit_mode, heartbeat_interval, autoreply_enabled, relay_enabled,
  psk_reporter, aprs_is, log_messages_to_file, custom_frequency_mhz, last_audio_device_id,
  last_frequency, tx_submode). New settings are stored as js8cfg_*.

## PC mode (sideways, setting "PC")

The PC window at PC size, 1176 x 714 dp (capture pixels / 1.5), every section at its measured
position (generated by `build-env/gen_pc_layout.py`). Opens fitted to the screen.
Pinch to zoom (up to 4x), two fingers to move, one finger moves on empty areas,
double-tap an empty area to toggle fitted / 2x. Text is redrawn at each zoom (sharp).
Menu bar items open the Menu page.

## Swipe layout (sideways, setting "Swipe")

Five screens, swipe left/right or tap the tab bar at the bottom
(Header | Activity | Waterfall | Inbox | Settings).

1. **Header**: freq + Offset, callsign + clock, ⋮, RX/TX/Settings/SPOT/LOG/TUNE, scaled to fill.
2. **Activity**: Band Activity | Message Window over Typing Box | Call Activity, Buttons in one row
   of ten, Status Bar.
3. **Waterfall**: CAT + level meter | ruler + waterfall | Waterfall Controls | gain slider.
4. **Inbox**: table | selected message + reply text + Reply.
5. **Settings**: shows the Station window (tab label not yet changed).

## Menu page (sideways only)

Opened from ⋮ (Swipe) or the PC mode menu bar. File (Station…, Exit), Log, View (Clear Band /
Call / RX / All Activity, Decoded messages), Control, Help. Upright has no ⋮ and no Menu page.

## Band Activity / Call Activity lists (all layouts)

- Each list scrolls sideways on its own.
- Tap a column header: sort by that column.
- Press and hold a column header: checklist to show/hide columns.
- Columns at first install = JS8Call defaults (`buildShowColumnsMenu`, mainwindow.cpp):

| List | Shown by default | Hidden by default |
|---|---|---|
| Band Activity | Offset, Age, SNR, Message(s) | Time Delta, Mode Speed |
| Call Activity | Callsigns, Age, SNR, Offset, Worked Before (✓), Logged Name, Logged Comment | Time Delta, Mode Speed, Grid Locator, Distance, Azimuth |

## Waterfall offset dial and taps

- Dial (CPlotter::drawDials): left edge at the offset, width = the speed's bandwidth
  (Slow 25, Normal 50, Fast 80, JS8 40 160, JS8 60 250 Hz), 2 px red outline, white
  alpha-75 checker fill, full waterfall height. Shown with RX off too.
- Tap the waterfall or its frequency scale: offset = whole Hz under the finger
  (CPlotter::mouseReleaseEvent). With RX off the scale is 0–3000 Hz. Ignored while a message is
  queued or sending, unless Station → Behavior → **Allow Tx frequency changes while transmitting**.

## Colors (from styles.h, Windows branch)

| Item | Value |
|---|---|
| Header background | `#DDEEFF` |
| Frequency display | text `#39FF14` on `#000000`, bold, corner 4 px, min 200×40 px |
| Frequency up/down arrows | `#39FF14` on `#000000`, corner 2 px; pressed `#222222` |
| UTC clock | text `#39FF14` on `#000000`, bold, 14 pt, corner 4 px |
| Callsign label | black, 12 pt |
| Offset slider | groove `#A5CDFF` (filled) / `#E0E0E0` (empty), border `#B0B0B0`; handle `#6699FF`, border `#C2C8D1`, 16 px wide, corner 5 px |
| Blue buttons (CQ, STATUS, Saved, Halt, Settings button, checked SPOT/LOG/TUNE) | `#6699FF`, black text, corner 4 px |
| Button hover / pressed | `#4D7FFF` / `#003EAA` |
| Disabled buttons (REPLY, SNR, INFO, Directed, Deselect, Send; all transmit buttons with SWL) | `#ECECEC`, text `#888888` |
| Unchecked header buttons (LOG, TUNE, SPOT) | lightgray `#D3D3D3`, black text |
| RX / TX checked, CAT connected, Status "Receiving" | `#22FF22`, black text |
| TX transmitting, Status "Transmitting" | `#FF2222`, black text |
| Status idle timeout | `#000000`, white text |
| Settings button (was NORMAL+MULTI+AUTO+CONF) | `#6699FF`, bold |
| Message Window | `#FFEAA7` |
| Typing Box, list bodies | `#FFFFFF` |
| Waterfall | `#000000` |
| Status bar labels | corner 4 px, padding 0 8 px, border 1 px rgba(0,0,0,0.25) |
| Progress bar | track `#FFFFFF`, fill `#A5CDFF` |

### Window frame (follows phone dark mode)

| Item | Dark (phone dark mode on) | Light |
|---|---|---|
| Window / menu bar / status bar background | `#1E1E1E` | light grey (Qt light palette) |
| List column header row | `#3C3C3C`, white text | light grey, black text |

The JS8Call-specific colors above (header, buttons, yellow box, black displays) stay the
same in both.

## Fonts

| PC (Windows) | Easy JS8 | License |
|---|---|---|
| Segoe UI | Selawik | SIL OFL (Microsoft) |
| Consolas (frequency, clock) | Cascadia Mono | SIL OFL (Microsoft) |

Point sizes follow the PC source.

## Checking

After each section is built: screenshot the phone (or emulator), place it beside the PC
capture, list differences, fix, repeat.

## Activity logic (port of the PC)

`js8ui/Js8Activity.kt` ports processDecodeEvent / displayBandActivity / displayCallActivity /
processRxActivity / processCommandActivity (display parts):

- Band Activity: one row per offset (follows drift within the submode RX threshold), last 10 frames,
  EOT mark after the last frame, aging, heartbeats/ACKs hidden unless Settings → View →
  Show Band Heartbeats and ACKs. Row colors: CQ #66FF66, my call #FF6666, primary/secondary highlight words.
- Call Activity: @ALLCALL (unless "Do not participate"), my groups, heard calls; icons
  ⚑ unread message, ★ heard me / ACK, ☎ CQ in last 5 min, ✕ blocked; "Callsigns (N)"; grid,
  distance, azimuth; selected row #3498DB.
- Message Window: frames near my offset, messages to me / my groups, my transmissions in red.
- Buttons: CQ, REPLY, SNR, INFO, STATUS, Saved, Directed (buildQueryMenu), Deselect, Send, Halt;
  with "Immediately transmit..." on, they send at once (PC default). With no callsign or grid
  they show "Enter callsign and gridsquare in the settings" and open Station.
- Inbox: JS8Android's stored MSG messages; Reply puts "CALL MSG [MESSAGE]" in the Typing Box.
- Header: TX = transmitter enabled, SPOT = spotting on/off, TUNE = tone on/off, Settings opens
  the Settings window. LOG not built yet.
