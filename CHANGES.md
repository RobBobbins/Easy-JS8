# Easy JS8 changes

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
