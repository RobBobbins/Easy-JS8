# Easy JS8 — Radio checklist

The major tests with a real radio. Run them in order: each one needs the one before it to work.
Use a dummy load for every test that transmits, until test 6.

Device: SM-X210 or S22 Ultra. Claude watches the app's log over Wi-Fi while you test.

| # | Test | How | Pass when | Done |
|---|------|-----|-----------|------|
| 1 | Rig connects | Station > Radio: pick your rig in **Rig:**, tap **Test CAT** | **Test CAT** turns green | [ ] |
| 2 | Frequency follows the radio | Turn the radio's dial, then pick a band in the app | The app shows the radio's frequency; the radio moves to the app's band | [ ] |
| 3 | PTT keys and lets go | Station > Radio > Rig Options: set **PTT Method** (the default is **VOX**: a CAT rig left on VOX won't key). Then tap **Test PTT**, and tap it again | The radio goes to transmit, then back to receive | [ ] |
| 4 | Receive audio | Tune to a JS8 frequency (20m 14.078 MHz) and wait a few minutes | Signals show on the waterfall and decodes show in Band Activity | [ ] |
| 5 | Transmit audio and power | Tap **TUNE**, watch the radio's power meter and ALC, then tap **Halt** | Steady power, no ALC; **Halt** stops it | [ ] |
| 6 | Send a CQ on the air | Off the dummy load, onto the antenna: send CQ | The radio transmits for the whole frame and stops; PSK Reporter or another station hears you | [ ] |
| 7 | Contact with another station | Answer a station or reply to one that calls you | Their text shows in the Message Window, yours reaches them | [ ] |
| 8 | HB and ACK | Turn on **HB**, and **AUTO** for ACK replies | A heartbeat goes out on its timer; another station's heartbeat gets an ACK | [ ] |
| 9 | Log a QSO | After test 7, log it | The QSO is in `js8call_log.adi` with the right frequency | [ ] |
| 10 | Long run | Leave it receiving for an hour with **AUTO** on | No crash, decodes keep coming, the rig stays connected | [ ] |
| A1 | APRS beacon | Station > **APRS Beacon**: **APRS beacon:** **2 minutes**, **OK** (allow precise location), screen off, wait 5 minutes outdoors | Two `@APRSIS GRID` beacons go out, each with a 10-character grid; your position shows on aprs.fi | [ ] |
| A2 | SmartBeaconing turns | **APRS beacon:** **SmartBeaconing**, drive, make a right-angle turn above 40 km/h | A beacon goes out within a few seconds of the turn (the emulator's GPS cannot report direction, so only the phone tests this) | [ ] |

## Only if your rig uses them

| # | Test | Pass when | Done |
|---|------|-----------|------|
| 11 | Bluetooth serial rig (audit N44) | The rig connects; if it needs DTR / RTS, the app reports that it can't set them | [ ] |
| 12 | Station > Radio > Rig Options: **Split Operation** set to **Fake It** or **Rig**, then send | The radio's transmit frequency moves for the send, then comes back | [ ] |
| 13 | Station > General > Behavior: tick **Monitor returns to last used frequency**. Turn **RX** off, turn the radio's dial, turn **RX** on | The radio goes back to the frequency it was on | [ ] |

## Write down when something fails

- The test number and what the radio showed (frequency, TX light, power, ALC).
- The time, so Claude can find it in the log.
