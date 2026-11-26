# Easy JS8 changes

## 1.1.0 (2026-11-25)

New:
- JS8 decoder is js8core-plus: more weak signals decoded, with callsign hints from my callsign and the stations
  heard in the last 10 minutes. Settings > Operation > Enable Deep Decoding (off by default).
- Station and Settings look and work like Android settings: a page list with icons and summaries (two panes
  sideways), switches and popups, changes saved at once, no OK / Cancel.
- PC and FT8Call layouts: Inbox and Map in the menu bar, left of Help. Map opens a full-screen map.
- Settings > General > Behavior: Send "IS TYPING..." while replying (Off, Once, every 30, 45 or 60 seconds).
- Waterfall Controls > Display: Label decoded signals (off by default). Each decoded frame's text beside its
  signal, which gets a box.
- Settings > View: Keep flagged stations at the top of Call Activity (ticked by default).
- Inbox: grey hints in the empty list, the message box and the reply box.

Changed:
- Built for Android 16, which Google Play needs: the screens stay clear of the status bar, the navigation bar,
  the camera cutout and the keyboard.
- Speed names JS8 40 wpm and JS8 60 wpm in the status bar, Jump to and the Band Activity Speed column. The Mode
  menu and the Settings Speed list have no descriptions.
- Call Activity: flagged stations follow the chosen sort among themselves.
- Upright: a tap on the header band (callsign, band, mode) opens Settings at Frequency.
- Upright Waterfall Controls: the shown tab tapped again closes them; a gear right of Timing opens or closes them.
- Hold PTT between frames is greyed when the app doesn't key the radio (Rig None, or a Hamlib rig on PTT Method
  VOX).
- Keep decoded messages after restart and Reset the Band Activity, Call Activity, and RX history at startup are
  one setting: flipping either flips the other. Decoded messages are kept by default.
- Map: call signs spread round their dot; Fit leaves room for call signs and the map buttons.
- About: "by Carson VA3OSO"; js8core-plus and pocketfft in the credits.

Fixed:
- Easy JS8 closed at start on phones with 16 KB memory pages.

## 1.0.5 (2026-11-18)

New:
- Station map. Pages layout: Map button between Inbox and Station. Upright: between Waterfall Controls and Inbox.
  Offline outline map, a dot for my grid and for every Call Activity station with a grid, who-hears-who lines.
  Pinch, drag, double tap; +, - and Fit buttons. Options in Station > Layout > Map.
- APRS beacon (Station > APRS Beacon): "@APRSIS GRID" and a 10-character grid over the air every 2, 5, 10, 15
  or 30 minutes, 1 hour, or SmartBeaconing. A GPS fix before each beacon, which also updates My Maidenhead Grid
  Locator. Status window with the beacon's events, and a GPS line.

Changed:
- Station OK saves and stays on the page. ← Back or Cancel leaves.
- APRS beacon is Off at every app start.
- APRS beacon waits for the GPS fix, with no time limit, and runs only while RX is on.
- Beacon not sent: the status window says why (RX off, radio not connected, PTT Method, no callsign, and others).
- GPS line goes onto two lines when it does not fit.

Fixed:
- A change to the rig or PTT settings needed RX off and on. Now the radio reconnects after OK.
- Closing the app from the phone's open-apps screen stopped RX.
- Timed beacons after the phone had slept were scheduled hours late.
- "Message queued" stuck with the screen off and held back heartbeats, the beacon and Frequency Schedule changes.
- A late rig connection from a stopped engine could open a second connection to the radio.
- Message Window lost its place when resized.

## 1.0.4 (2026-11-06)

Fixed:
- Rig and send errors no longer turn RX off.

## 1.0.3 (2026-11-06)

Fixed:
- A screen opened over a running engine shows RX on.

## 1.0.2 (2026-11-06)

Fixed:
- ALL.TXT stays one file after a restart.

## 1.0.1 (2026-11-06)

Changed:
- `<MYVERSION>` sends EASY JS8 and the version.
