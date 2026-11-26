# Easy JS8 test checklist

Rules: every row passes twice, with an app restart between the runs. A row passes only when seen running.
A failure with no quick fix is marked **Failed** with what happened; Failed rows are retried at the end.
Real phone and real radio tests come after all of this and are not on this list.
Layouts: U Upright, P PC, G Pages, F FT8Call. N/A means the layout has no such control.
Sections L, M, N (Settings, Station, menus) use the same code in all four layouts (Rob, 2026-11-01): each row runs twice in Upright; in PC, Pages and FT8Call each window or menu is only opened, and the row says **Open** instead of Pass.
From 2026-11-01 the test build's engine is compiled at -O3, the same as release. Rows passed before that ran at -O0.

## A. Decoding (audio fed in)

| ID | Test | Run 1 | Run 2 | Note |
|---|---|---|---|---|
| A01 | JS8 Normal frame decodes | Pass | Pass |  |
| A02 | JS8 Fast frame decodes | Pass | Pass |  |
| A03 | JS8 40 frame decodes | Pass | Pass |  |
| A04 | JS8 60 frame decodes | Pass | Pass |  |
| A05 | JS8 Slow frame decodes | Pass | Pass |  |
| A06 | MULTI on: every speed decodes | Pass | Pass |  |
| A07 | MULTI off: only the selected speed decodes | Pass | Pass |  |
| A08 | Enable Filter off: signal at 3000 Hz decodes | Pass | Pass |  |
| A09 | Enable Filter on: signal outside the filter does not decode | Pass | Pass |  |
| A10 | Two-frame message joined in the Message Window | Pass | Pass |  |
| A11 | Band Activity row from a decode | Pass | Pass |  |
| A12 | Call Activity row from a decode | Pass | Pass |  |
| A13 | Low-confidence text shows in [ ] in Band Activity | Pass | Pass |  |
| A14 | Duplicate frame dropped | Pass | Pass |  |
| A15 | FT8 line shown (Show FT8 decodes ticked) | Pass | Pass |  |
| A16 | FT8 line not shown (unticked) | Pass | Pass |  |

## B. Directed commands and replies

| ID | Test | Run 1 | Run 2 | Note |
|---|---|---|---|---|
| B01 | SNR? answered | Pass | Pass |  |
| B02 | INFO? answered | Pass | Pass |  |
| B03 | STATUS? answered | Pass | Pass |  |
| B04 | GRID? answered | Pass | Pass |  |
| B05 | HEARING? answered | Pass | Pass |  |
| B06 | HEARING? with empty list still answers | Pass | Pass |  |
| B07 | QUERY CALL answered | Pass | Pass |  |
| B08 | QUERY MSGS answered YES | Pass | Pass |  |
| B09 | QUERY MSGS answered NO | Pass | Pass |  |
| B10 | QUERY MSG n sends the message and marks it collected | Pass | Pass |  |
| B11 | QUERY MSGS to @HB answered | Pass | Pass |  |
| B12 | SNR? to my @GROUP answered | Pass | Pass |  |
| B13 | INFO? to my @GROUP answered | Pass | Pass |  |
| B14 | STATUS? to my @GROUP answered | Pass | Pass |  |
| B15 | GRID? to my @GROUP answered | Pass | Pass |  |
| B16 | HEARING? to my @GROUP answered | Pass | Pass |  |
| B17 | @ALLCALL reply limit: one per station in 15 minutes | Pass | Pass |  |
| B18 | Do not participate in the @ALLCALL group: no reply | Pass | Pass |  |
| B19 | AUTO off: no reply | Pass | Pass |  |
| B20 | Autoreply Confirmation Yes sends | Pass | Pass |  |
| B21 | Autoreply Confirmation No does not send | Pass | Pass |  |
| B22 | Autoreply Confirmation No leaves a held message uncollected | Pass | Pass |  |
| B23 | Only autoreply to these callsigns | Pass | Pass |  |
| B24 | Never autoreply to these callsigns | Pass | Pass |  |
| B25 | No reply with text in the Typing Box | Pass | Pass |  |
| B26 | No reply while a message to me is arriving | Pass | Pass |  |
| B27 | Reply heard while transmitting goes out after the message | Pass | Pass |  |
| B28 | Idle timeout box appears and turns AUTO off | Pass | Pass |  |
| B29 | Hide messages from these callsigns | Pass | Pass | hidden in lists |

## C. Checksums and buffers

| ID | Test | Run 1 | Run 2 | Note |
|---|---|---|---|---|
| C01 | MSG with good checksum reaches the Inbox | Pass | Pass |  |
| C02 | MSG with bad checksum dropped | Pass | Pass |  |
| C03 | MSG TO: with good checksum held | Pass | Pass |  |
| C04 | MSG TO: with bad checksum dropped | Pass | Pass | sent: VE3TST: K4RRB MSG HI YOU FROM K4RQA |
| C05 | QUERY with bad checksum dropped | Pass | Pass |  |
| C06 | Relay with bad checksum not forwarded | Pass | Pass | sent: VE3TST: K4RZB>HELLO BOB *DE* K4RYA |
| C07 | @APRSIS MSG TO: keeps a 3-character last word | Pass | Pass |  |
| C08 | Command answered on the last frame, not the first | Pass | Pass |  |
| C09 | Buffer closes at 60 s | Pass | Pass |  |
| C10 | Buffer dropped at 90 s | Pass | Pass |  |
| C11 | First frame clears an old buffer | Pass | Pass |  |
| C12 | Drifting frames inside the speed's range join | Pass | Pass |  |

## D. Relay and mailbox

| ID | Test | Run 1 | Run 2 | Note |
|---|---|---|---|---|
| D01 | A>B>C relay forwarded | Pass | Pass |  |
| D02 | Relay obeys AUTO off | Pass | Pass |  |
| D03 | Relay obeys Disable message relay | Pass | Pass |  |
| D04 | Relay heard while transmitting is queued | Pass | Pass |  |
| D05 | Relayed MSG reaches the Inbox | Pass | Pass |  |
| D06 | Relayed MSG TO: held | Pass | Pass |  |
| D07 | Relayed QUERY MSGS answered | Pass | Pass |  |
| D08 | Relayed QUERY CALL answered | Pass | Pass |  |
| D09 | MSG with a DE path: ACK follows the path | Pass | Pass |  |
| D10 | Relay to my @GROUP | Pass | Pass |  |
| D11 | Group message held 2 days and collected per station | Pass | Pass |  |
| D12 | Store Message... held for a station | Pass | Pass |  |
| D13 | HB ACK carries MSG ID | Pass | Pass |  |
| D14 | Group MSG ID on HB ACK | Pass | Pass |  |

## E. Heartbeat, CQ and transmit queue

| ID | Test | Run 1 | Run 2 | Note |
|---|---|---|---|---|
| E01 | HB button sends one heartbeat | Pass | Pass |  |
| E02 | HB timer sends after a full interval | Pass | Pass |  |
| E03 | CQ button sends one CQ | Pass | Pass |  |
| E04 | CQ timer sends after a full interval | Pass | Pass |  |
| E05 | Starting CQ timer stops HB timer | Pass | Pass |  |
| E06 | Starting HB timer stops CQ timer | Pass | Pass |  |
| E07 | Selecting a callsign stops HB and CQ timers | Pass | Pass |  |
| E08 | Deselect restores HB timer | Pass | Pass |  |
| E09 | Halt stops HB and CQ timers | Pass | Pass |  |
| E10 | CQ timer stops when a message is shown | Pass | Pass |  |
| E11 | HB ACK sent with AUTO, HB and ACK on | Pass | Pass |  |
| E12 | No HB ACK in JS8 40 | Pass | Pass |  |
| E13 | No HB ACK in JS8 60 | Pass | Pass |  |
| E14 | No HB ACK while a buffer is open | Pass | Pass |  |
| E15 | Pause heartbeat transmissions while in a QSO blocks ACK | Pass | Pass |  |
| E16 | Never acknowledge heartbeats list | Pass | Pass |  |
| E17 | Suppress ACK under 55 minutes | Pass | Pass |  |
| E18 | Heartbeat offset avoids busy offsets | Pass | Pass | free slot picked, PC random search |
| E19 | Heartbeat uses my offset at or below 1000 Hz | Pass | Pass |  |
| E20 | Allow heartbeat transmissions outside of heartbeat sub-channel | Pass | Pass |  |
| E21 | Timed HB waits while transmitting | Pass | Pass |  |
| E22 | Reply priority ahead of a queued message | Pass | Pass |  |
| E23 | No double send when a reply queues between parts | Pass | Pass |  |
| E24 | Halt during a message | Pass | Pass |  |
| E25 | Halt during a heartbeat | Pass | Pass |  |
| E26 | Halt during TUNE | Pass | Pass |  |
| E27 | TUNE on and off | Pass | Pass |  |
| E28 | TX off stops sending and greys Send | Pass | Pass |  |
| E29 | RX button refused while transmitting | Pass | Pass |  |
| E30 | SWL blocks every transmit | Pass | Pass |  |
| E31 | 30 m WSPR guard band refused | Pass | Pass | (['NOPE'], []) |
| E32 | Message Not Sent box after a stuck key | Pass | Pass |  |
| E33 | Send without callsign (Allow sending standard messages without callsign) | Pass | Pass |  |
| E34 | Typing ahead while sending | Pass | Pass |  |
| E35 | Part-by-part strike-out in the Typing Box | Pass | Pass |  |
| E36 | Last Tx label | Pass | Pass | corrected check |
| E37 | All five speeds send: frame length correct | Pass | Pass |  |

## F. Macros

| ID | Test | Run 1 | Run 2 | Note |
|---|---|---|---|---|
| F01 | <MYCALL> | Pass | Pass |  |
| F02 | <MYGRID4> | Pass | Pass |  |
| F03 | <MYGRID12> | Pass | Pass |  |
| F04 | <MYINFO> | Pass | Pass |  |
| F05 | <MYSTATUS> | Pass | Pass |  |
| F06 | <MYIDLE> | Pass | Pass |  |
| F07 | <MYVERSION> sends EASY JS8 and the version | Pass | Pass |  |
| F08 | <CALL> | Pass | Pass |  |
| F09 | <SNR> | Pass | Pass |  |
| F10 | <TDELTA> | Pass | Pass |  |
| F11 | <MYHB> | Pass | Pass |  |
| F12 | Values upper-cased | Pass | Pass |  |
| F13 | Leftover <...> removed | Pass | Pass |  |

## G. Log files

| ID | Test | Run 1 | Run 2 | Note |
|---|---|---|---|---|
| G01 | ALL.TXT received lines | Pass | Pass |  |
| G02 | ALL.TXT transmitted lines | Pass | Pass |  |
| G03 | ALL.TXT frequency-change line | Pass | Pass | Retired 2026-11-05: the PC writes no line at a frequency change (audit B N31); W112 checks the date line |
| G04 | ALL.TXT start-of-day header | Pass | Pass |  |
| G05 | DIRECTED.TXT line after whole message | Pass | Pass |  |
| G06 | Write log files unticked: nothing written | Pass | Pass |  |
| G07 | js8call_log.adi after Add to Log | Pass | Pass |  |
| G08 | js8call.log after Add to Log | Pass | Pass |  |
| G09 | Erase ALL.TXT | Pass | Pass |  |
| G10 | Erase js8call_log.adi | Pass | Pass |  |
| G11 | Export Log... all three files | Pass | Pass | ['← Back', 'Save', 'Cancel', 'Operation', 'Enable Simultaneous Decoding of All Speeds (MUL |

## O. Rig against the fake rigctld

These rows and W19-W33 ran through the network rigctl choice, which was removed on 2026-11-03 (Rob). The results stand for the rig code the USB radios share: dial, mode, PTT, split, polling.

| ID | Test | Run 1 | Run 2 | Note |
|---|---|---|---|---|
| O01 | Frequency read | Pass | Pass |  |
| O02 | Frequency set | Pass | Pass |  |
| O03 | Mode USB set | Pass | Pass | [] |
| O04 | Mode Data/Pkt set | Pass | Pass |  |
| O05 | PTT CAT key and release | Pass | Pass | ['T 3', 'T 0', 'T 3'] |
| O06 | VOX sends no key command | Pass | Pass |  |
| O07 | Hold PTT one key-down | Pass | Pass | 0 key-downs |
| O08 | Tx delay lead time | Pass | Pass |  |
| O09 | Split Rig | Pass | Pass |  |
| O10 | Split Fake It | Pass | Pass |  |
| O11 | Poll Interval | Pass | Pass |  |
| O12 | Rig Control Error box | Pass | Pass | ['VE3TST', '2026 Nov 01 23:18:10'] |
| O13 | Rig reconnect after a drop | Pass | Pass |  |
| O14 | QSY moves Band Activity rows | Pass | Pass |  |
| O15 | Band change on a rig read | Pass | Pass |  |
| O16 | Remember power by band | Pass | Pass |  |

## P. Other rig stand-ins

| ID | Test | Run 1 | Run 2 | Note |
|---|---|---|---|---|
| P01 | DX Lab stand-in read, set, PTT | N/A | N/A | choice removed from the app (Rob, 2026-11-03) |
| P02 | Ham Radio Deluxe stand-in read, set, PTT | N/A | N/A | choice removed from the app (Rob, 2026-11-03) |
| P03 | Hamlib model 2 NET | N/A | N/A | choice removed from the app (Rob, 2026-11-03) |

## Q. API

| ID | Test | Run 1 | Run 2 | Note |
|---|---|---|---|---|
| Q01 | UDP PING | Pass | Pass |  |
| Q02 | UDP STATION.STATUS | Pass | Pass |  |
| Q03 | UDP RX.ACTIVITY | Pass | Pass |  |
| Q04 | UDP RX.DIRECTED | Pass | Pass |  |
| Q05 | RIG.PTT event | Pass | Pass |  |
| Q06 | TX.FRAME event | Pass | Pass |  |
| Q07 | RX.SPOT event | Pass | Pass | air13.py real_spot: real frame, spot with antenna and program; air12.py Q07 injects and cannot spot |
| Q08 | All TCP commands answered (44) | Pass | Pass |  |
| Q09 | Allow setting station information | Pass | Pass |  |
| Q10 | TCP Max Connections | Pass | Pass | corrected check |
| Q11 | STATION.CLOSING | Pass | Pass |  |
| Q12 | WSJT-X Heartbeat | Pass | Pass |  |
| Q13 | WSJT-X Status | Pass | Pass |  |
| Q14 | WSJT-X Decode | Pass | Pass |  |
| Q15 | WSJT-X QSOLogged | Pass | Pass |  |
| Q16 | WSJT-X LoggedADIF | Pass | Pass |  |
| Q17 | N3FJP contact | Pass | Pass |  |
| Q18 | N1MM contact | Pass | Pass |  |

## R. Network stand-ins

| ID | Test | Run 1 | Run 2 | Note |
|---|---|---|---|---|
| R01 | APRS-IS login and spot | Pass | Pass | [] |
| R02 | APRS inbound relay | Pass | Pass | no relay sent |
| R03 | APRS ACK filter | Pass | Pass | ['11:56:51 TX W1AW>APRS,TCPIP*::K4WXA    :HELLO VIA APRS{01'] |
| R04 | APRS off with RX off | Pass | Pass |  |
| R05 | JS8NET RX.LOCAL | Pass | Pass |  |
| R06 | JS8NET RX.SPOT | Pass | Pass | 0 lines |
| R07 | JS8NET RX.DIRECTED to a group | Pass | Pass |  |
| R08 | JS8NET bad checksum dropped | Pass | Pass |  |
| R09 | Spot blacklist | Pass | Pass |  |
| R10 | PSK Reporter packet reaches the stand-in | Pass | Pass |  |
| R11 | PSK Reporter antenna info | Pass | Pass |  |
| R12 | PSK Reporter program name | Pass | Pass |  |
| R13 | Check for Updates finds a version | Pass | Pass |  |
| R14 | Failed update read says nothing | Pass | Pass |  |

## S. Startup and restart

| ID | Test | Run 1 | Run 2 | Note |
|---|---|---|---|---|
| S01 | Fresh install opens Station on General | Pass | Pass | ['The callsign format you provided is not supported', 'OK'] |
| S02 | No callsign: Station cannot be left | Pass | Pass | ['The callsign format you provided is not supported', 'OK'] |
| S03 | RX off at startup | Pass | Pass |  |
| S04 | TX off at startup | Pass | Pass |  |
| S05 | Turn autoreply on at startup | Pass | Pass | box unticked starts AUTO off, ticked starts it on, as the PC (2026-11-03) |
| S06 | HB timer kept after restart | Pass | Pass |  |
| S07 | Reset activity at startup | Pass | Pass | corrected check |
| S08 | Waterfall settings kept after restart | Pass | Pass |  |

## T. Against JS8Call on the PC

| ID | Test | Run 1 | Run 2 | Note |
|---|---|---|---|---|
| T01 | PC sends Normal, app decodes | Pass | Pass |  |
| T02 | PC sends Fast, app decodes | Pass | Pass |  |
| T03 | PC sends JS8 40, app decodes | Pass | Pass |  |
| T04 | PC sends Slow, app decodes | Pass | Pass |  |
| T05 | App sends Normal, PC decodes | Pass | Pass |  |
| T06 | App sends Fast, PC decodes | Pass | Pass |  |
| T07 | App sends JS8 40, PC decodes | Pass | Pass |  |
| T08 | App sends Slow, PC decodes | Pass | Pass |  |
| T09 | App sends JS8 60, PC decodes | Pass | Pass |  |
| T10 | App CQ reads the same on the PC | Pass | Pass |  |
| T11 | App heartbeat reads the same on the PC | Pass | Pass |  |
| T12 | App directed message reads the same on the PC | Pass | Pass |  |
| T13 | App multi-frame message with checksum accepted by the PC | Pass | Pass |  |
| T14 | PC multi-frame message with checksum accepted by the app | Pass | Pass |  |
| T15 | PC SNR? answered by the app | Pass | Pass |  |
| T16 | App SNR? answered by the PC | Pass | Pass |  |
| T17 | Compound callsign both ways | Pass | Pass |  |
| T18 | Group message both ways | Pass | Pass |  |

## U. Real networks (genuine traffic only)

| ID | Test | Run 1 | Run 2 | Note |
|---|---|---|---|---|
| U01 | Real APRS-IS login | Pass | Pass | logresp APJ8CL verified, T2PANAMA |
| U02 | Real APRS-IS @APRSIS GRID | Pass | Pass | VA3OSO EN94MN spot seen back on the APRS-IS feed |
| U03 | Real JS8NET RX.LOCAL | Pass | Pass | RX.LOCAL VA3OSO EN94MN sent to spot.js8call.com; no answer expected |

## W. More PC tests (2026-11-03)

| ID | Test | Run 1 | Run 2 | Note |
|---|---|---|---|---|
| W01 | Real JS8 frame: red / cyan decode bracket on the waterfall | Pass | Pass | Upright |
| W02 | Real JS8 frame: sharp lines on the waterfall (picture) | Pass | Pass | Upright |
| W03 | Real JS8 frame: Cumulative spectrum rises at the signal | Pass | Pass | Upright |
| W04 | Real JS8 frame: Avg N change smooths the spectrum | Pass | Pass | Upright |
| W05 | Real JS8 frame: signal meter moves | Pass | Pass | Upright |
| W06 | CAT button colours: no rig, rig connected, rig error | Pass | Pass | Upright |
| W07 | PC heartbeat over audio: app sends HB ACK, PC decodes it | Pass | Pass | Upright |
| W08 | App heartbeat over audio: PC sends HB ACK, app decodes it | Pass | Pass | Upright |
| W09 | App timed heartbeat over audio decoded by the PC | Pass | Pass | Upright |
| W10 | FT8Call Next Beacon countdown counts down | Pass | Pass | FT8Call |
| W11 | Header Offset slider locked while a message is queued | Pass | Pass | PC |
| W12 | RX and TX at startup after Back closes the app | Pass | Pass | Upright |
| W13 | Notification sound plays (Android audio log) | Pass | Pass | Upright |
| W14 | UI fonts in Pages and FT8Call (picture) | Pass | Pass | Pages, FT8Call |
| W15 | Pages Station screen has ← Back | Pass | Pass | Pages |
| W16 | Station > Radio sideways with a rig picked (picture) | Pass | Pass | PC |
| W17 | Station > Audio in PC and FT8Call (picture) | Pass | Pass | PC, FT8Call |
| W18 | Upright Station page list (picture) | Pass | Pass | Upright |
| W19 | Real Hamlib rigctld dummy rig (needs Rob to OK a Hamlib download) | Pass | Pass | Hamlib 4.7.2 rigctld -m 1 on the PC, started by Rob (2026-11-03): read, set and PTT |
| W20 | Real rigctld: the app reads the dial | Pass | Pass | Hamlib 4.7.2 rigctld -m 1 on the PC (2026-11-03), wham2.py |
| W21 | Real rigctld: the app sets the dial (RIG.SET_FREQ) | Pass | Pass | wham2.py |
| W22 | Real rigctld: Mode USB set on a send | Pass | Pass | wham2.py |
| W23 | Real rigctld: Mode Data (PKTUSB) set on a send | Pass | Pass | wham2.py |
| W24 | Real rigctld: PTT on for each frame, off after | Pass | Pass | wham2.py |
| W25 | Real rigctld: Split Rig at offset 2300, split on, TX 10.136500 while keyed | Pass | Pass | wham2.py |
| W26 | Real rigctld: Split Fake It, dial 10.136500 while keyed, 10.136000 after | Pass | Pass | wham2.py; PTT, dial, PTT read in one packet (script fix 2026-11-03) |
| W27 | Real rigctld: Poll Interval 1 s picks up a dial change made in rigctld | Pass | Pass | wham2.py |
| W28 | Real rigctld: CAT button green | Pass | Pass | wham2.py |
| W29 | Real rigctld: Test CAT turns green | Pass | Pass | wham2.py |
| W30 | Real rigctld: Test PTT keys and releases | Pass | Pass | wham2.py |
| W31 | rigctld closed: Rig Control Error box | Pass | Pass | wham2.py W31, Rob closed rigctld |
| W32 | rigctld closed: CAT red under the box | Pass | Pass | wham2.py W31 |
| W33 | rigctld started again: RETRY reconnects, box gone, CAT green | Pass | Pass | wham2.py W31, Rob started rigctld after a minute |
| W34 | Station > UI upright: Secondary Highlight Background shows both lines | Pass | Pass | fixchk.py (2026-11-03 fix) |
| W35 | Inbox upright: bold (unread) Date shows in full | Pass | Pass | fixchk.py, judged on the screenshot |
| W36 | Station > Frequencies: ticking the schedule box keeps the page where it was | Pass | Pass | fixchk.py |
| W37 | Station > Audio sideways: Remember power settings by band shows in full on opening | Pass | Pass | fixchk.py |
| W38 | PC sideways: two-finger pinch zooms the window | Pass | Pass | zoomchk.py runs 1 and 2: Send 112 to 204 px |
| W39 | FT8Call sideways: two-finger pinch zooms the window | Pass | Pass | Run 1 by hand (Rob, 2026-11-03); run 2 zoomchk.py, Send 114 to 208 px |
| W40 | Phone in dark mode: main screen in Upright, PC, Pages, FT8Call; Settings; Station > Radio and UI; Log QSO all readable | Pass | Pass | darkchk.py, judged on the screenshots; the test pass before ran in light mode |
| W41 | Station > UI colour samples paint like the PC (background setting + text setting), repaint together after a pick; picker opens at the current colour | Pass | Pass | uicolchk.py; fixed 2026-11-03: Received Foreground Color showed black on black in light mode |
| W42 | API STATION.SET_GRID / SET_INFO / SET_STATUS kept after an app restart | Pass | Pass | apichk.py |
| W43 | API answers with the screen off 30 s; the app is on screen after waking | Pass | Pass | apichk.py; first try 23:22 hit an emulator stall (Easy JS8 and Google search ANR the same second), passed after an emulator restart |
| W44 | Pages layout: TX.SET_TEXT fills the Typing Box, MODE.SET_SPEED changes the speed label | Pass | Pass | apichk.py |
| W45 | A call on the spot list (`js8cfg_spot_blacklist`) is not sent to PSK Reporter or JS8NET; with the list empty it is sent to both | Pass | Pass | spotblk.py: on the list, heard True, PSK False, JS8NET False; list empty, heard True, PSK True, JS8NET True |
| W46 | A call on the spot list (`js8cfg_spot_blacklist`) sending `@APRSIS GRID` is not sent to APRS-IS; with the list empty it is | Pass | Pass | aprsblk.py: on the list, sent False; list empty, login True (`APJ8CL`), sent True |
| W48 | Split Rig through the built-in Hamlib (model 2, NET rigctl) against rigctld.exe: offset 2300 transmits on 10.136500 with split on | Pass | Pass | hsplit.py; log "Hamlib network rig opened (model=2 path=10.0.2.2:4532)" |
| W49 | Split Fake It through the built-in Hamlib: the dial moves to 10.136500 for the send and back to 10.136000 after | Pass | Pass | hsplit.py |
| W50 | Back on the main screen, then open again (process kept): RX and TX turned off by hand come back on (RX off / TX off at startup unticked) | Pass | Pass | startbk.py |
| W51 | Same with RX off / TX off at startup ticked: RX and TX turned on by hand are off again | Pass | Pass | startbk.py |
| W52 | WSJT-X UDP API to multicast 239.255.0.1:2239, TTL 1, Outgoing interface eth0: received on the PC | Pass | Pass | wmcast.py: unicast control 66 packets; eth0 74 / 66 packets |
| W53 | Same out of wlan0 | Failed | Failed | wmcast.py: 0 packets both runs, no send error in the app log; the emulator's simulated Wi-Fi may not pass multicast (not proven). Retry on the phone on real Wi-Fi |
| W54 | Station > Radio sideways, rig picked (Icom IC-7300) and Rig None: Rig Options on its own row | Pass | Pass | radioend.py, pagepic.py, PC and FT8Call. Was: Rig Options squeezed at the right edge of row 1, PTT Method cut off. Fix (2026-11-04): Rig Options always starts its own row; PTT Port: sits under PTT Method (as in the PC's PTT Method box). Row 1 Rig / Port / Parameters; row 2 PTT Method, Mode, Split Operation; row 3 Transmit Audio Source, Advanced |
| W55 | Station > Audio sideways packed layout fits (PC, FT8Call) | Pass | Pass | pagepic.py pictures, checked by eye |
| W56 | Paste into the Typing Box (Windows clipboard, KEYCODE_PASTE): upper case, only newline, 0x10, 0x1A and 32-127 kept (PC TransmitTextEdit normalizeText). Audit B N42 | Pass | Pass | n42chk.py: "hello 20°C café", tab, "ok 😀 line" shows "HELLO 20C CAFOK  LINE" |
| W57 | TX.SET_TEXT into the Typing Box: same rule, newline kept as on the PC | Pass | Pass | n42chk.py |
| W58 | Lower-case key events (hardware keyboard) show in capitals | Pass | Pass | n42chk.py |
| W59 | Send with ° in the box: goes out without it, the next message goes out too, no busy CPU | Pass | Pass | n42chk.py: app CPU 1.0-1.8 s per 5 s, as with an empty box. Before the fix the frame builder never finished |
| W60 | API TX.SEND_MESSAGE with ° and é: sent without them, the next message goes out too | Pass | Pass | n42chk.py |
| W61 | Send "K1ABC: N45" goes out as text, "K1ABC N45B" as a directed message to K1ABC (PC directed pattern: no colon, no space before). Audit B N45 | Pass | Pass | n48chk.py: the engine's build_message_frames log lines, DAT for the colon line, DIR to K1ABC for the other |
| W62 | Send "`K1ABC/P SNR? N46": compound frame for "`K1ABC/P SNR?", then " N46" as text (PC matches the start of the line). Audit B N46 | Pass | Pass | n48chk.py: CMP, then DAT. Before the fix the whole line went out as text, backtick included |
| W63 | Call selected, Typing Box "FN42 N47": goes out as "K4xxx FN42 N47", directed to the selected call (PC callsign search skips grids). Audit B N47 | Pass | Pass | n48chk.py: DIR to the selected call, "FN42 N47" as text. Before the fix it went to "FN42" |
| W64 | Free text to a call over two frames ("K4xxx: K1ABC", then "N48 FREE TEXT"): one DIRECTED.TXT line and one RX.DIRECTED at the last frame, "K1ABC  N48 FREE TEXT ♢ " with two spaces, CMD " ", as the PC. Audit B N48 | Pass | Pass | n48chk.py, injected decodes, RX.DIRECTED over a TCP API connection. Before the fix nothing was written |
| W65 | ACK and SNR -05 with a frame after them: nothing at the first frame; at the last, "K1ABC ACK N48 ACK TEXT ♢ " and "K1ABC SNR -05 N48 SNR TEXT ♢ " (PC waits for the last frame for every command but "?"). Audit B N48 | Pass | Pass | n48chk.py. Before the fix they went out at the first frame without the text |
| W66 | Unchanged: a one-frame ACK and a CQ with frames after it go out at once; a data frame is never free text to a call | Pass | Pass | n48chk.py |
| W67 | Screen off (power key): SNR? is answered, and a call heard with the screen off is in the Callsigns list after waking (PC answers with its monitor off). Audit B N51 | Pass | Pass | s1chk.py |
| W68 | HEARING? lists the Callsigns list; after long-press **Remove This Station** the removed call is no longer in the reply (PC m_callActivity). Audit B line 37 | Pass | Pass | s1chk.py; the reply after the removal leaves out the removed call. 2026-11-05 regression: W68 and W69 failed twice, the menu (taller with the N5 lines) ran past the screen; s1chk.py now scrolls it, both passed twice |
| W69 | QUERY CALL for the removed call: no reply; for a kept call: YES. Audit B line 37 | Pass | Pass | s1chk.py |
| W70 | Relay to my group (`@TESTG>`): ACKed, not forwarded (PC pCA 424). Audit B line 55 | Pass | Pass | s1chk.py; air2.py D10 changed to expect the ACK |
| W71 | AUTO off: SNR? reply goes into the Typing Box, not sent; **Send** sends it; a heartbeat gets no HB ACK. Audit B N1 | Pass | Pass | s1chk.py |
| W72 | A queued reply waits while the Typing Box has text, and goes out once the box is empty. Audit B N2 | Pass | Pass | s1chk.py |
| W73 | Timed heartbeat waits while the Typing Box has text (100 s), and goes out once the box is empty. Audit B line 67 | Pass | Pass | s1chk.py, Heartbeat interval 1 min |
| W74 | MSG over three frames drifting 1500 / 1508 / 1516 Hz: one ACK and one full DIRECTED.TXT line. Audit B N3 | Pass | Pass | s1chk.py |
| W75 | Relay over three drifting frames is forwarded; a relay with no last frame is forwarded 60 s after its last frame, not before 50 s. Audit B N4 | Pass | Pass | s1chk.py; the open relay went out 68 s and 69 s after its first frame |
| W76 | `@JS8NET` MSG drifting, and one with no last frame: both reach JS8NET. Audit B N4 | Pass | Pass | s1chk.py, fakespot.py on the PC |
| W77 | Directed menu on @ALLCALL: SNR? INFO? GRID? STATUS? HEARING? `>` MSG and MSG TO: greyed; QUERY CALL, QUERY MSGS, QUERY MSG, AGN?, QSL?, HW CPY? live (PC BQ 115-245). Lines after the first item, GRID and QUERY MSG [ID]. Audit B section 2, N5 | Pass | Pass | s27chk.py; the lines checked by eye (w77, w77b screenshots) |
| W78 | **Log...** greyed on @ALLCALL, live on @TESTG (PC UC 1284-1285). Audit B section 2 | Pass | Pass | s27chk.py |
| W79 | Double-tap a Call Activity row: the call goes in the Typing Box with no space after it (PC mw 5101-5104). Audit B N6 | Pass | Pass | s27chk.py |
| W80 | Saved message with `<MYHB>` is shown as "HB" and the 4-character grid; two spaces kept (PC mainwindow.h 793-798). Audit B section 4, N8 | Pass | Pass | s27chk.py |
| W81 | Upright: **Send** greyed while a heartbeat waits and goes out with text in the Typing Box; live again after (PC mw 5863-5890). Audit B N9 | Pass | Pass | s27chk.py; upright shows "Ready" with no time, cut to fit |
| W82 | **Heartbeat interval:** every 30 minutes with the setting removed: API STATION.GET_CONFIG and the HB button's menu (**Repeat every 30 minutes** ticked). PC cpp 2409. Audit B section 3 | Pass | Pass | s27chk.py |
| W83 | Frequency menu: **Set a Custom Frequency...** first, rows like "20m:    14.078 000 MHz" (PC mw 4283-4302). Audit B N10 | Pass | Pass | s27chk.py, PC layout |
| W84 | CAT orange while the rig opens (PC rigOpen, mainwindow.ui 1485); **Rig Control Error** box asks only "Do you want to reconfigure the radio interface?", the reason under **Show Details...** / **Hide Details...** (PC UC 97-98). Audit B section 5, N11 | Pass | Pass | s27chk.py; Hamlib model 2 at an address that does not answer |
| W85 | RX off: status label empty, no colour; RX on: Receiving (PC mw 2761-2763). Audit B section 5 | Pass | Pass | s27chk.py |
| W86 | **Show Message Inbox...** from a Call Activity row: title "Message Inbox" (PC mw 115), that call's message listed. Audit B section 5 | Pass | Pass | s27chk.py |
| W87 | Press and hold below the rows: the whole Call Activity and Band Activity menus, items needing a call greyed, **Saved Messages...** live (PC UC 1018-1022, 1290-1296). Audit B section 5 | Pass | Pass | s27chk.py |
| W88 | Header callsign: the call alone with **Allow setting station information (Grid, Info, Status, etc) from the APIs** off; "CALL - GRID" with it on; an API grid shown (PC mw 2766-2771). Audit B N43 | Pass | Pass | s27chk.py |
| W89 | A band change empties the Typing Box and greys **Restore Previous Message** (PC mw 1279-1285, 2970-3014). Audit B N15 | Pass | Pass | s27chk.py |
| W90 | ⚑ only for unread messages to my call, not to a group; after a restart the sender's row is added from the message with its SNR (PC mw 6279-6314). Audit B N16 | Pass | Pass | s27chk.py; SNR read with RX.GET_CALL_ACTIVITY, the upright SNR cell is off screen. Failed once (pass 2): the script reused call names and a swipe opened a menu; script fixed, then passed 4 more times |
| W91 | **Callsigns (N)** counts the calls inside **Callsign aging** only (PC mw 3042-3054). Audit B N17 | Pass | Pass | s27chk.py |
| W92 | ★ column shown only while a row has an icon (PC displayCallActivity 257-258). Audit B N18 | Pass | Pass | s27chk.py |
| W93 | **Show Call Activity Columns...** and **Show Band Activity Columns...**: the PC list and order, a tick acts at once (PC mw 4669-4694). Audit B N19 | Pass | Pass | s27chk.py; the SNR tick read from its setting, the upright SNR column is off screen |
| W94 | Blocked station: its later frames at the exact offset stay hidden until its last frame, also when its first frame is a directed message; a frame 3 Hz away and the next station are shown (PC mw 3106-3131). Audit B section 6 | Pass | Pass | s27chk.py; found an app bug in the shakedown, fixed |
| W95 | Colours: scale #F1C40F and #2ECC71, spectrum top #000080, grid dots #808080 (PC CP 65-66, 431, 438). Audit B N20 | Pass | Pass | s27chk.py, pixel counts |
| W96 | **Control** menu in PC order; **Send Heartbeat...** is a menu (nothing sent on opening); **Set Focus...** > **Message Reply Area** focuses the Typing Box; **Set Offset...** box titled "Set Frequency Offset". Audit B N13 | Pass | Pass | s27chk.py, PC layout |
| W97 | **Control** > **Clear Activity...** > **Clear All Activity** clears at once, no question (PC mw 829-831). Audit B N12 | Pass | Pass | s27chk.py, PC layout |
| W98 | **View** menu in PC order; **Show Band Heartbeats and ACKs** in it and toggles. Audit B N14 | Pass | Pass | s27chk.py, PC layout |
| W99 | Clone names "Default - Copy", then "Default - Copy(2)" (PC MultiSettings.cpp 594-603). Audit B N7 | Pass | Pass | s27chk.py, PC layout |
| W100 | Power slider moves when TUNE changes the power with **Remember power settings by band** (PC mw 4252, 5180, 5204). Audit B section 5 | Pass | Pass | s27chk.py, PC layout |
| W101 | Signal meter scale 0-100, labels 90 70 50 30 10 (PC SignalMeter.cpp 25, 146-154). Audit B section 5 | Pass | Pass | s27chk.py, checked by eye; red on the loudest sample read only, not run |
| W102 | Names: **Sort Call Activity By...**, **Edit Saved Messages**. Audit B section 5 names | Pass | Pass | s27chk.py |
| W103 | PC layout: **Send** greyed, "Ready (Ns)" counting down while a heartbeat waits (PC mw 5875-5878). Audit B section 5, N9 | Pass | Pass | s27chk.py |
| W104 | Idle timeout: label "Idle timeout" black with white text while the **Idle Timeout** box is open; Receiving after **OK** (PC mw 6912-6960, styles.h 109-110). Audit B section 5 | Pass | Pass | s27chk.py, Idle timeout 5 min |
| W105 | **QSY** moves every Call Activity offset by the dial change, as Band Activity (PC mw 5357-5361). Audit B N21 | Pass | Pass | s812chk.py, PC layout |
| W106 | **UDP Server Port:** takes 65535 (PC cpp 1517-1519). Audit B N26 | Pass | Pass | s812chk.py |
| W107 | Frequencies table: one **Frequency** column "7.078 000 MHz (40m)", "OOB" outside the 30 bands, no Frequency (MHz) column; Schedule "7.078 000 MHz" (PC cpp 1639-1640, FrequencyList.cpp 356-359, StationList.cpp 279-281). Audit B N27 | Pass | Pass | s812chk.py |
| W108 | **Rename ...**: "New Configuration Name", "Old name:" with the name, an empty "New name:" box, OK greyed at the start and for a name in use, / and \ blocked (PC MultiSettings.cpp 66-110). Audit B N28 | Pass | Pass | s812chk.py, PC layout |
| W109 | Control characters refused in Reply, CQ, Status, Info and Callsign Groups; the whitelist box keeps a pasted tab (PC cpp 207, 1491-1504). Audit B section 10 | Pass | Pass | s812chk.py, a tab pasted from the PC clipboard |
| W110 | Idle timeout 2 minutes: used until the next start (box after about 2 minutes), raised to 5 at the start (PC cpp 2410-2412, 3351). Audit B N25 | Pass | Pass | s812chk.py |
| W111 | ALL.TXT decode line: period time, SNR right after it, the 12-character frame, 4 spaces before the text (PC DecodedText.cpp 326, 340). Audit B N29 | Pass | Pass | s812chk.py, real audio |
| W112 | ALL.TXT date line before the first decode after **Erase ALL.TXT**, not before a later decode or a transmit line (PC mw 2104-2105, 2188, 4227, 7032-7038). Audit B N31 | Pass | Pass | s812chk.py, real audio |
| W113 | ALL.TXT: each transmit line holds the text of its own frame (PC mw 2667-2670, 6994-7009). Audit B N30 | Pass | Pass | s812chk.py |
| W114 | Relay in DIRECTED.TXT and RX.DIRECTED: "K4YYY> K4ZZZ HELLO" (PC pCA 131, Varicode.cpp 50-118). Audit B N49 | Pass | Pass | s812chk.py |
| W115 | Unfinished message: written 60 s after its latest frame when a text frame came, with no new decode; one frame only, not written (PC processBufferedActivity.cpp 18-40). Audit B N50 | Pass | Pass | s812chk.py |
| W116 | DIRECTED.TXT time from the drifted clock (PC mw 7062). Audit B N32 | Pass | Pass | s812chk.py, drift +90 s |
| W117 | TDRIFT: the clock drift on Normal and Slow with auto sync on, in RX.ACTIVITY and RX.DIRECTED; the decode's offset on Fast or with auto sync off (PC WG 369-376, pDE 312-314). Audit B N33 | Pass | Pass | s812chk.py, injected decodes |
| W118 | STATION.SET_GROUPS checks the names as sent: "@w118x", "@ALLCALL/X", "@aprsis" "Invalid group name", "@APRSIS" "Group not allowed" (PC networkMessage.cpp 466-482). Audit B N34 | Pass | Pass | s812chk.py |
| W119 | Spot after a dial change waits 4/5 of a period, with the dial at decode, on the API (RX.SPOT) and to PSK Reporter (PC mw 6529-6575). Audit B N35 | Pass | Pass | s812chk.py, real audio, fakepsk.py |
| W120 | PSK Reporter: sequence 1, 2 per packet; drifted export time; sends with an empty grid; turning spotting off sends what waits (PC PSKReporter.cpp 448, 572-573; mw 1044-1047). Audit B N38, N41 | Pass | Pass | s812chk.py, fakepsk.py, empty grid with SWL on |
| W121 | PSK Reporter with no spots: nothing at 2, 4 and 6 minutes; the 4th report time (about 8 minutes) sends the station record (PC PSKReporter.cpp 50-52, 425-427, 505-556). Audit B N37 | Pass | Pass | s812chk.py, fakepsk.py, 9 minutes |
| W122 | Station OK checks: End of Transmission first; a stray comma and ABCDEF refused as groups, @ABCDEF taken, each group in turn; "K1ABC K2DEF" refused, "K1ABC,K2DEF" taken; a greyed PTT Method refused (PC cpp 2915-3043). Audit B N24 | Pass | Pass | s812chk.py |
| W123 | INFO? and STATUS? answered when the text is a macro that fills in empty ("<CALL>", no call selected) (PC pCA 340-356). Audit B N54 | Pass | Pass | s812chk.py |
| W124 | PSK Reporter unit tests: sequence from 1, descriptors in the first 3 packets, every 4th time flushes, eclipse ±6 h, the 30 bands for the 1-hour limit. Audit B N37-N40 | Pass | Pass | PskReporterClientTest.kt in Docker; eclipse dates can't be reached on the phone |
| W125 | Rename: a name typed and deleted turns OK on (PC MultiSettings.cpp 92-96). Audit B N28 | Pass | Pass | s812chk.py, runs 3 and 4 |
| W126 | A directed frame's text as the PC: "VE3TST: K4ABC> " (PC DecodedText.cpp tryUnpackDirected). Audit B N55 | Pass | Pass | s812chk.py |
| W127 | Test PTT greyed until Test CAT is green (Hamlib IC-7300, DTR: before and after a red Test CAT); live with RTS PTT; greyed with None (PC cpp 2831-2840, 4515-4521). Audit B N22 | Pass | Pass | s812chk.py, runs 2 and 5; green Test CAT needs the radio |
| W128 | Input channel lists Mono, Left, Right, Both, Both greyed; channels greyed by the device; Phone's current audio output keeps only Mono (PC cpp 4747-4783). Audit B N23 | Pass | Pass | s812chk.py |
| W129 | TX.SEND_MESSAGE with AUTO off goes into the Typing Box and is not sent; with AUTO on it is sent (PC networkMessage.cpp 744-748, mw 6650-6664). Audit B N53 | Pass | Pass | s1chk.py, runs 31 and 34 |
| W130 | A relay to me whose text starts with "@" is ACKed, not dropped or forwarded (PC pCA 420-434). Audit B N52 | Pass | Pass | s1chk.py, runs 33 and 34 |
| W131 | **Show FT8 decodes in the Message Window** starts off: no FT8 lines from a busy FT8 recording (Rob, 2026-11-05) | Pass | Pass | rttychk.py runs 1 and 2 |
| W132 | FT8 ticked: the same recording gives 18 FT8 lines | Pass | Pass | rttychk.py runs 1 and 2 |
| W133-W136 | (RTTY rows, removed with RTTY decodes) | | | |
| W137 | Callsign selected, "30W 30FT VERTICAL OUT N49" typed, " QTH OWEN" typed while the first part is on the air: all of it goes out (S20 lost "30W 30" to typeahead, 2026-11-06) | Pass | Pass | tapchk.py runs 2, 3, 4 (run 1 read the old ALL.TXT) |
| W138 | Callsign selected, "30W 30FT VERTICAL OUT N50" typed, no edits: goes out whole; typeahead restarts at "30W" | Pass | Pass | tapchk.py runs 3 and 4 (run 1 sent it whole, script read the old ALL.TXT) |
| W139 | Band Activity row: quick triple tap prints it once; two double taps 1 s apart print it twice (PC) | Pass | Pass | tapchk.py runs 1, 3, 4 |
| W140 | Sideways PC layout with the tablet's saved sizes (Typing Box one line): the Typing Box opens two lines tall, two typed lines both show | Pass | Pass | splitchk.py runs 2 and 3 |
| W141 | Sideways PC layout: a drag from the gap above the Typing Box, and from just inside its top edge, moves that line; the line under the lists stays (tablet: it moved instead) | Pass | Pass | splitchk.py runs 2 and 3 |
| W142 | Sideways PC layout: HB button shows "HB", only the seconds while the HB timer runs, "HB" after | Pass | Pass | splitchk.py runs 2 and 3 |
| W143 | Upright, Normal: HB then Halt 0.3-28 s later, 50 rounds (before the slot and inside the sound): the app never freezes or restarts, a last HB still goes out, no ANR (tablet froze 2026-11-06: stopping the sound waited on its own callback, `audio_oboe.cpp`) | Pass | Pass | hbhalt.py runs 1 and 2 |
| W144 | Upright: the Message Window's lower divider dragged 180 px up and down, twice at the newest line and twice scrolled up: at the newest line it stays at the newest line; scrolled up it keeps the lines it showed (Rob 2026-11-07: every resize jumped to the first line) | Pass | Pass | mwband.py runs 2, 3, 6, 7 (run 1 on the first try of the fix: still jumped; run 4: the test's strip reached the clipped bottom edge) |
| W145 | Settings > View "New Band Activity line for each message": ticked, two messages at one offset show on 2 lines; unticked, on 1 line (the PC); the box shows in Settings > View | Pass | Pass | mwband.py runs 1-7 |
| W146 | Band Activity keeps 50 frames per offset (PC 10): a 30-frame message's line still starts with its first frame | Pass | Pass | mwband.py runs 1-7 |
| W147 | Defaults (Rob 2026-11-07), neither setting saved: Band Activity shows a line per message, the Settings > View box is ticked, a line is still shown 130 s after it was heard ("Remove messages from band activity after:" Disabled; it was 2 minutes) | Pass | Pass | mwband.py runs 6 and 7 (runs 4-5 read Save on a page without the Aging box) |
| W148 | Pages layout (sideways): six buttons Header, Activity, Waterfall, Inbox, Map, Station, left to right; Station still opens the Station window | Pass | Pass | mapchk.py runs 1, 2 |
| W149 | Map, defaults: my station red, Call Activity stations with a grid white with a black edge and callsigns, state and province lines, arrow lines: green both ways (a head at each end), yellow one way (one head) (colours Rob 2026-11-17) | Pass | Pass | mapchk.py runs 15, 16 (runs 1-12: one green colour, yellow dots); screenshots looked at |
| W150 | Station > Layout > Map "Lines:" Plain lines draws no arrow heads; No lines draws none | Pass | Pass | mapchk.py runs 1, 2 |
| W151 | "Show callsigns" off, "Show state and province lines" off, "Show grid squares" on | Pass | Pass | mapchk.py run 1b (run 1: the test counted the white button bar), run 2 |
| W152 | "Remove stations from map after:" 15 minutes drops a station heard 20 minutes ago; Same as Call Activity (Callsign aging Disabled) keeps it | Pass | Pass | mapchk.py runs 1, 2 |
| W153 | Station > Layout shows the Map group with its defaults; ticking Show grid squares and OK redraws the map with grid boxes | Pass | Pass | mapchk.py run 1b (run 1: the test read the list text without its "▾"), run 2 |
| W154 | Map: a drag moves it and Fit brings it back; a double tap on a station's dot zooms in 2 times with that dot staying under the finger (Rob 2026-11-17; double tap used to fit every station) | Pass | Pass | mapchk.py runs 10, 11, 12 (runs 1-9 tested the old double tap, fit every station) |
| W155 | Map: + and − buttons in the bottom-right corner; + zooms in 2 times, − back to the same view, − again zooms out | Pass | Pass | mapchk.py runs 3, 4 (W148-W154 passed again in run 3) |
| W156 | Map: Fit button (44 dp) under −; after a drag and +, Fit shows every station again | Pass | Pass | mapchk.py runs 5, 6 (W148-W155 passed again in run 5) |
| W157 | Map: gear button above +; it opens the Map options box (Lines, Remove stations from map after, three ticks); Show grid squares and No lines show on the map at once and are saved; a tap outside closes the box | Pass | Pass | mapchk.py runs 8, 9 (run 7: the test counted grid boxes while the box covered them; W148-W156 passed again in run 7) |
| W158 | After a restart, map lines between stations come back from the saved Band Activity ("A: B ..." = A hears B): W4BBB <-> K1AAA and N0CCC -> VE7DDD drawn; with no saved messages, only my lines | Pass | Pass | mapchk.py runs 13, 14 |
| W159 | Map title top left, white on a dark strip: "EASY JS8 - callsign - band - date", date in UTC; outside the bands the frequency in MHz (27.555 MHz shows "27.5550 MHz"); Fit keeps the strip clear of stations (Rob 2026-11-17) | Pass | Pass | mapchk.py runs 20, 21 (W148-W158 rechecked with the title strip blacked out before counting; W157 judges grid boxes after the box closes, the open box covers my dot); screenshots looked at |
| W160 | Station > APRS Beacon page, after Reporting (own page, Rob 2026-11-17; was on General, now gone from there): **APRS beacon:** Off / 2 / 5 / 10 / 15 / 30 minutes / 1 hour / SmartBeaconing; SmartBeaconing settings greyed unless SmartBeaconing; Off = no GPS, no beacon (Rob 2026-11-17) | Pass | Pass | beaconchk.py runs 10, 11; again 15, 16 after the message-queued fix; again 19, 20 on the APRS Beacon page |
| W161 | APRS beacon every 2 minutes: GPS fix, then "@APRSIS GRID" with the 10-character grid queued for the transmitter, grid put in My Maidenhead Grid Locator; next fix starts early by the last fix time; next beacon goes with the screen off | Pass | Pass | beaconchk.py runs 10, 11; again 15, 16 after the message-queued fix; again 19, 20 on the APRS Beacon page |
| W162 | APRS beacon with no new GPS fix (location off): waits 30 seconds past beacon time, then skipped, nothing sent | Pass | Pass | beaconchk.py runs 10, 11; again 15, 16 after the message-queued fix; again 19, 20 on the APRS Beacon page |
| W163 | SmartBeaconing: beacon on the first fix, by rate at 100 km/h (highway rate 1 minute), on a 90 degree turn (least turn time 30 s), none while stopped; speed and direction worked out from positions (test provider) | Pass | Pass | beaconchk.py runs 10, 11; again 15, 16 after the message-queued fix; GPS-reported direction only on the phone (RADIO-CHECKLIST A2); again 19, 20 on the APRS Beacon page |
| W164 | Scheduled heartbeat goes out with the screen off after a transmission that ended with the screen off (MainActivity copies "message queued" with the screen off too; before, it stuck and held back heartbeats, the APRS beacon and Frequency Schedule changes) | Pass | Pass | beaconchk.py runs 15, 16 (not run against the old build); again 19, 20 on the APRS Beacon page |
| W176 | Station > Radio > Advanced, Hold PTT between frames greyed (keeps its tick) with Rig None and with a Hamlib rig on PTT Method VOX; picking DTR on the page makes it usable at once, VOX greys it again; usable with RTS PTT and truSDX on VOX (Easy JS8 only, Rob 2026-11-23) | Pass | | holdptt.py runs 2 (upright), 3 (PC sideways); run 1 the test's own Hamlib part timed out (3 checks in 45 s, now 80 s), states all read right; screenshots shots/hp_* looked at. M24 now with Rig RTS PTT (greyed with None): ui3.py run 90 PASS |
| W177 | js8core-plus decoder: made-up JS8 Normal signals, 12 per slot, 2 slots per level, white noise; old build (16:35 2026-11-23) against new: set -12/-15/-18 dB 24 of 24 both; -20 dB old 4 new 23; -21 dB old 1 new 4; -22 dB old 0 new 5; 0 wrong decodes either build (Rob 2026-11-24) | Pass | | decchk.py old (runs 2, 3), new (run 1); run 1 of old stopped at the test's own check (read the service's text line, not the raw frame); the app reports about 7 dB lower SNR than set (-20 dB set = -27 reported); noise differs between runs, 24 frames a level |
| W178 | Transmit unchanged: the new build's encoder (js8core-plus legacy_encode) gives the same 79 tones as the old build for 2 frames | Pass | | decchk.py new, notes in decchk.new.json |
| W179 | Settings > Operation > Enable Deep Decoding: ticked reaches the engine (log "Decoder: deep=on"); -20 dB 24 of 24, -21 dB 12 of 24, -22 dB 1 of 24, 0 wrong; unticked + Save gives "deep=off" and pref false | Pass | | decchk.py deep (DEC_ONLY=-20,-21,-22); scratchpad deepui2.py; screenshots shots/deep_settings_1.png (upright, label on 2 lines), shots/deep_settings_side.png |
| W180 | Callsign hints: the station callsign reaches the engine (log "hints call=VE3TST") | Pass | | decchk.py; hints decoding a weak frame to the own call not tested |
| W181 | About credits: "js8core-plus (JS8 decoder): Kelly-Shane Fuller, VA3OSO, MIT licence" and "pocketfft: Max-Planck-Society, BSD licence" under ft8_lib, upright and PC sideways (Rob 2026-11-24) | Pass | | scratchpad aboutcred.py run 1; shots/aboutcred.U.png, shots/aboutcred.P.png |
| W182 | Upright header band (20m in "VE3TST 20m JS8"): a tap opens the Settings window, Frequency: 20m - 14.078 MHz at the top; Cancel goes back (Rob 2026-11-24) | Pass | | scratchpad bandtap.py run 1; shots/bandtap_before.png, shots/bandtap_after.png |
| W165 | Upright: the map between Waterfall Controls and Inbox, 300 dp, title and four buttons; + zooms, Fit fits; a drag on the map moves the map, not the page; a drag beside it scrolls the page | Pass | Pass | mapchk.py runs 22, 23; W148-W159 again 24, 25 |
| W166 | Station > APRS Beacon status window: 2 minutes, then SmartBeaconing, then 5 minutes picked on the page and OK; lines in order with UTC times, one "started" line per change, the last line counts down, a drag in the window scrolls the window and not the page | Pass | Pass | beaconchk.py runs 22, 23, 24; W161-W164 passed again runs 23, 24; W160 (now scrolls the page beside the window) runs 25, 26 |
| W183 | Android-style Station Settings (upright): page list General, Radio, Audio, Reporting, APRS Beacon, Frequencies, Saved Messages, Notifications, UI, Diagnostics, Phone, About with icons and summaries; no OK / Cancel / Save (Rob 2026-11-24) | Pass | | droidset.py U runs 1-4; shots/droidset.*.U_list.png |
| W184 | General > My Callsign popup: "12" shows "The callsign format you provided is not supported" with OK greyed; VE3TST allowed and saved at OK | Pass | | droidset.py U; shots/droidset.*.U_callsign_bad.png |
| W185 | Switch row (Display distance in miles): a tap on the row flips the switch and saves at once, both ways | Pass | | droidset.py U |
| W186 | Number popup (Heartbeat interval): 2000 refused (OK greyed), 45 saved, summary "every 45 minutes" | Pass | | droidset.py U |
| W187 | Choice popup (Send "IS TYPING..." while replying): Once saved at once and shown as the summary | Pass | | droidset.py U |
| W188 | Radio page: Rig and Baud Rate save at once, no reconnect while on the Radio page (js8_radio_hold true); leaving it reconnects once; Rig back to None, leaving reconnects once more | Pass | | droidset.py U run 4; shots/droidset.*.U_radio.png |
| W189 | Settings window (upright): one page with headings Frequency, Operation, Mode, Offset, Power, View, Log; switch and Speed save at once; no Save / Cancel | Pass | | droidset.py U; shots/droidset.*.U_settings.png |
| W190 | Enable Heartbeat Acknowledgments (ACK) greyed unless AUTO and HB are both on, and follows them at once | Pass | | droidset.py U |
| W196 | Station > About page shows Easy JS8 and Credits | Pass | | droidset.py U; shots/droidset.*.U_about.png |
| W191 | PC sideways Station Settings: two panes (page list left, page right), Radio page with Rig and Test CAT, no OK / Cancel | Pass | | droidset.py P run 3; shots/droidset.*.P_station.png, P_radio.png |
| W192 | PC sideways Settings window: Frequency first, no Save | Pass | | droidset.py P run 3; shots/droidset.*.P_settings.png |
| W193 | Pages sideways Station tab: two panes, no Back arrow; Layout > Sideways layout > PC saved at once and the screen switches to PC | Pass | | droidset.py P run 7; shots/droidset.7.G_station.png, G_layout.png |
| W197 | Sideways My Callsign popup with the full-screen keyboard: "12" then DONE shows the red message and OK greyed; Cancel keeps VE3TST | Pass | | droidset.py P run 7; shots/droidset.7.P_callsign_done.png |
| W195 | Dark mode: page list, General page and callsign popup look right (checked by eye); Cancel with the keyboard down keeps the callsign | Pass | | droidset.py D run 8 (came out sideways; upright dark checked in an earlier run); shots/droidset.8.D_*.png |
| W198 | PC sideways menu bar: File Configurations Mode Log View Control Inbox Map Help, in that order, none overlapping; Inbox opens the Message Inbox screen, ← Back returns (Rob 2026-11-24) | Pass | | menuinmap.py run 2; shots/menuinmap.2.pc_menu.png, pc_inbox.png |
| W199 | PC Map screen: ← Back, title Map, map with ⚙ + − Fit, no tab bar; ⚙ opens the Map options box; ← Back returns | Pass | | menuinmap.py run 2; shots/menuinmap.2.pc_map.png, pc_map_options.png |
| W200 | FT8Call sideways: same menu bar order, Inbox and Map open their screens and ← Back returns; layout set back to PC after | Pass | | menuinmap.py run 2; shots/menuinmap.2.ft8_menu.png, ft8_map.png |
| W201 | Station > Phone > Keep decoded messages after restart shows on for an install with persist_decodes stored false and Reset off (kept is the default) (Rob 2026-11-24) | Pass | | keepreset.py run 1 |
| W202 | Keep decoded messages after restart off: stored persist_decodes false, js8cfg_reset_activity true; General > Reset the Band Activity, Call Activity, and RX history at startup shows on | Pass | | keepreset.py run 1; shots/keepreset.1.general_reset_on.png |
| W203 | Reset ... at startup off on General: stored persist_decodes true, reset false; Phone > Keep decoded messages after restart shows on | Pass | | keepreset.py run 1; shots/keepreset.1.phone_keep_back_on.png |
| W204 | APRS EMAIL form, Android look (Rob 2026-11-26): ← and title APRS EMAIL in the top bar, Insert at its right end greyed, no OK / Cancel / ← Back text, no red text on open | Pass | | aprsform.py run 4; shots/aprsform.4.email_empty.png |
| W205 | APRS EMAIL: "bob" shows red Enter an email address, Insert greyed; bob@mail.com + HELLO: no red, counter 18/67, Insert on; Insert puts @APRSIS CMD :EMAIL-2  :BOB@MAIL.COM HELLO{NN in the Typing Box (next number) and leaves | Pass | | aprsform.py run 4; shots/aprsform.4.email_bad.png, email_good.png |
| W206 | APRS TXT: 5195551212 + HI, Insert puts @APRSIS CMD :SMSGTE   :@5195551212 HI{NN in the Typing Box | Pass | | aprsform.py run 4 |
| W207 | APRS MSG: VA3-XYZ-9 shows red Enter a callsign (e.g. ...), Insert greyed; ← leaves with the Typing Box empty | Pass | | aprsform.py run 4 (runs 1, 3 timed out: uiautomator2's element click misses ← with the keyboard up; test taps its centre now) |
| W208 | Sideways APRS EMAIL: top bar ← title Insert, boxes under it; Insert fills the Typing Box | Pass | | aprsform.py run 4; shots/aprsform.4.side_email.png |
| W209 | Dark mode upright APRS EMAIL with a problem shown (checked by eye) | Pass | | aprsform.py run 4; shots/aprsform.4.dark_email.png |
| W210 | Station > Notifications as Android rows (Rob 2026-11-26): Enable Notifications and the 7 events, each with Phone's notification sound under it and its own switch; no Select / Test / Clear | Pass | | notifchk.py run 3; shots/notifchk.3.upright.png |
| W211 | CQ Message Received switch saves js8cfg_notify_cq true, then false | Pass | | notifchk.py run 3 |
| W212 | Tapping CQ Message Received opens Phone's notification sound / Choose a sound file… / Play sound (no Cancel); Play sound closes it, app still running | Pass | | notifchk.py run 3; shots/notifchk.3.choices.png |
| W213 | Choose a sound file… opens Android's file picker; back returns to the Notifications page | Pass | | notifchk.py run 3 (run 1 failed on the test: the picker's back goes up a folder first) |
| W214 | Phone's notification sound sets the sound back and leaves the switch on (Clear used to turn the event off) | Pass | | notifchk.py run 3 |
| W215 | Sideways two panes: rows in the right pane, each switch on its row's line; choice box shows all three without scrolling | Pass | | notifchk.py run 3; shots/notifchk.3.side.png, notifchk.probe.side_choices.png |
| W216 | Station > APRS Beacon Android look (Rob 2026-11-26): GPS row (status under the name GPS, no "GPS: " start), Status label over a rounded box, Android font; upright, sideways, dark looked at | Pass | | screenshots shots/notifchk.aprsbeacon.{up,side,dark}.png; W166 beaconchk run 206, W168 + W175 gpschk run 205, W170 rxkeep run 202 passed on this build (scripts updated for the 2026-11-24 settings: no OK / drop-down, list scrolls) |
| W217 | Station > Frequencies as Android rows (Rob 2026-11-26): Add frequency at the top, 7.078 000 MHz (40m) with JS8 · All regions under it, Load from file… / Save to file… / Merge from file… / Reset to default frequencies at the end; none of the old buttons | Pass | | freqchk.py run 5; shots/freqchk.5.upright.png, freqchk.5.upright_files.png |
| W218 | Add frequency: Add Frequency form, OK greyed while empty and on 0 (red text under Frequency (MHz)); 0.501 + ztest adds the row and saves it | Pass | | freqchk.py run 5; shots/freqchk.5.add.png |
| W219 | Tapping a row opens Edit Frequency filled in, with Delete; 0.502 and IARU Region 2 from the drop-down save, the row reads JS8 · Region 2 · ztest | Pass | | freqchk.py run 5; shots/freqchk.5.region_list.png, freqchk.5.edit.png |
| W220 | Delete in Edit Frequency removes the row and its saved line | Pass | | freqchk.py run 5 |
| W221 | Load from file…, Save to file… and Merge from file… open Android's file picker; back returns to the page | Pass | | freqchk.py run 5; shots/freqchk.5.save_picker.png |
| W222 | Reset to default frequencies asks Reset Working Frequencies; Yes puts the default list back | Pass | | freqchk.py run 5 |
| W223 | Frequency Schedule: switch off greys Add schedule (a tap opens nothing); on: Add Schedule with OK greyed and red text on 25:00; a good entry adds 14.078 000 MHz (20m) with "HH:MM to HH:MM UTC · ZS" under it, saved | Pass | | freqchk.py run 5; shots/freqchk.5.sched_off.png, freqchk.5.sched_add.png, freqchk.5.sched_rows.png |
| W224 | Edit Schedule has Delete and Switch to Frequency Now; Switch to Frequency Now goes to 14.078 at once (last_frequency), app keeps running; Delete removes the entry | Pass | | freqchk.py run 5 (runs 1-4 failed on the test: a schedule window holding the current time starts the app's Scheduled Frequency Change countdown; the button hops at once, no countdown) |
| W225 | Sideways two panes: rows in the right pane, Add Frequency form with OK on screen, Add schedule at the end | Pass | | freqchk.py run 5; shots/freqchk.5.side.png, freqchk.5.side_add.png; tablet SM-X210 sideways dark looked at (shots/freqchk.tablet.rows.png, freqchk.tablet.form.png) |
| W226 | Dark mode upright: page and Edit Frequency | Pass | | freqchk.py run 5; shots/freqchk.5.dark.png, freqchk.5.dark_edit.png (checked by eye) |
| W227 | Station > Saved Messages as Android rows (Rob 2026-11-26): Add saved message at the top, each message a row with a ≡ handle (Move <message>); no Add / Delete buttons, no Message table | Pass | | savedchk.py run 1; shots/savedchk.1.upright.png |
| W228 | Add saved message: Add Saved Message form, OK greyed while empty; "za one" saves ZA ONE at the end | Pass | | savedchk.py run 1; shots/savedchk.1.add.png |
| W229 | Tapping a message opens Edit Saved Message filled in, with Delete; the edit replaces it in place, in capitals | Pass | | savedchk.py run 1; shots/savedchk.1.edit.png |
| W230 | Dragging ZB TWO's ≡ up one row puts it above ZA EDIT (saved); the main screen's Saved button lists that order | Pass | | savedchk.py run 1; shots/savedchk.1.after_drag.png, savedchk.1.saved_menu.png; mid-drag look shots/savedchk.mid.middrag.png |
| W231 | Dragging down moves it back; Delete removes both test messages, the list is as before the test | Pass | | savedchk.py run 1 |
| W232 | Sideways two panes: rows and handles in the right pane, Add Saved Message form with OK on screen | Pass | | savedchk.py run 1; shots/savedchk.1.side.png, savedchk.1.side_add.png |
| W233 | Dark mode upright: page and Edit Saved Message | Pass | | savedchk.py run 1; shots/savedchk.1.dark.png, savedchk.1.dark_edit.png (checked by eye); tablet SM-X210 upright dark shots/savedchk.tablet.page.png |
| W234 | Station > UI as Android rows (Rob 2026-11-26): each colour a row with its #RRGGBB under the name, each Font row with font · style · size, a K1ABC chip at the right; Received / Transmitted grey labels; no buttons | Pass | | uichk.py run 9; shots/uichk.9.upright.png |
| W235 | Colour box: name as title, K1ABC sample, 24 ready colours, Red / Green / Blue sliders (no tick dots); Cancel keeps the colour | Pass | | uichk.py run 9; shots/uichk.9.colour.png |
| W236 | Tapping #22FF22 sets the numbers to 34 255 34; OK saves it, the row reads #22FF22 with a green chip | Pass | | uichk.py run 9; shots/uichk.9.colour_picked.png, uichk.9.colour_saved.png |
| W237 | Dragging the Red slider to its left end sets Red to 0, the sample follows | Pass | | uichk.py run 9; shots/uichk.9.slider.png |
| W238 | Font row: one Font box (Font, Style, Size, K1ABC sample); Monospace, Bold, 14 pt saves and the row reads Monospace · Bold · 14 pt | Pass | | uichk.py run 9; shots/uichk.9.font_picked.png. Monospace sample measured: same letter shapes as Android's Droid Sans Mono (looks close to Selawik for K1ABC) |
| W239 | Sideways two panes: rows in the right pane, colour box with OK on screen (scrolls to the sliders) | Pass | | uichk.py run 9; shots/uichk.9.side.png, uichk.9.side_colour.png |
| W240 | Dark mode upright: page, colour box, Font box | Pass | | uichk.py run 9; shots/uichk.9.dark*.png (checked by eye); tablet SM-X210 sideways dark shots/uichk.tablet.ui_colour.png, uichk.tablet.ui_font.png |
| W241 | Pull-down text size (Rob 2026-11-26): phone (S22-Fresh, 360 dp wide) sideways PC layout: File, Configurations (and its Default sub-menu), Mode, Log, View, Control, Help pull-downs all 9.5 dp | Pass | | Checked by eye; shots/menulook.phone.all.png |
| W242 | Phone upright: Mode and Saved pull-downs both 9.5 dp | Pass | | Checked by eye; shots/menulook.phone.upright.png |
| W243 | Tablet (SM-X210, 800 dp wide) sideways PC layout: the same pull-downs all 14.3 dp, Mode included; Enable Simultaneous Decoding of All Speeds (MULTI) on one line | Pass | | Checked by eye; shots/menulook.tablet.all.png. Tablet upright not checked |
| W244 | Menu bar Log (Rob 2026-11-26): Add Log Entry..., Open Log Directory..., Export Log..., Erase ALL.TXT, Erase js8call_log.adi, in the PC and FT8Call layouts | Pass | | logmenu.py on S22-Fresh (both layouts) and tablet SM-X210 (PC); shots/logmenu.emu.all.png, logmenu.tablet.pc_menu.png |
| W245 | Log > Export Log... opens Export Options; ← goes back to the main screen | Pass | | logmenu.py; shots/logmenu.emu.pc_export.png, logmenu.tablet.pc_export.png |
| W246 | Settings > Log (Rob 2026-11-26): Open Log Directory... above Export Log..., then the two Erase rows | Pass | | logdirchk.py on S22-Fresh and tablet SM-X210; shots/logdir.emu.settings_log.png, logdir.tablet.settings_log.png |
| W247 | Settings > Log > Open Log Directory... opens Download/JS8CallLogs in the file app; no crash | Pass | | logdirchk.py; shots/logdir.emu.settings_open.png, logdir.tablet.settings_open.png |
| W248 | Menu bar Log > Open Log Directory... opens Download/JS8CallLogs; no crash (before 2026-11-26 it stopped the app on Android 14: SecurityException) | Pass | | logdirchk.py; shots/logdir.emu.menu_open.png, logdir.tablet.menu_open.png |
| W47 | Cumulative spectrum: the same audio (1000 Hz tone + noise) read at the same second of the minute in the app and in JS8Call on the PC | Pass | Pass | cumchk.py, tone / noise dB at second 28.5: app 49.7 / 19.0, JS8Call 48.1 / 16.4; at 43.5: app 50.4 / 19.7, JS8Call 48.1 / 16.4. App 1.6-2.3 dB higher on the tone, 2.6-3.3 on the noise; Rob: that difference doesn't matter, the function does. Fixed first (2026-11-04): the count Cumulative is divided by now follows JS8Call's dataSink m_ihsym (not reset each cycle), and the line is reduced like CPlotter's RDP (2 px). Before: JS8Call moved 6 dB between runs, the app never did |

## H. Main window

| ID | Test | Run 1 | Run 2 | Note |
|---|---|---|---|---|
| H01U | Header shows callsign and UTC clock (Upright) | Pass | Pass |  |
| H01P | Header shows callsign and UTC clock (PC) | Pass | Pass |  |
| H01G | Header shows callsign and UTC clock (Pages) | Pass | Pass |  |
| H01F | Header shows callsign and UTC clock (FT8Call) | Pass | Pass |  |
| H02U | Clock drift suffix (Upright) | Pass | Pass |  |
| H02P | Clock drift suffix (PC) | Pass | Pass |  |
| H02G | Clock drift suffix (Pages) | Pass | Pass |  |
| H02F | Clock drift suffix (FT8Call) | Pass | Pass |  |
| H03U | RX button (Upright) | Pass | Pass |  |
| H03P | RX button (PC) | Pass | Pass |  |
| H03G | RX button (Pages) | Pass | Pass |  |
| H03F | RX button (FT8Call) | Pass | Pass |  |
| H04U | TX button (Upright) | Pass | Pass |  |
| H04P | TX button (PC) | Pass | Pass |  |
| H04G | TX button (Pages) | Pass | Pass |  |
| H04F | TX button (FT8Call) | Pass | Pass |  |
| H05U | Mode button menu (Upright) | Pass | Pass |  |
| H05P | Mode button menu (PC) | Pass | Pass |  |
| H05G | Mode button menu (Pages) | Pass | Pass |  |
| H05F | Mode button menu (FT8Call) | Pass | Pass |  |
| H06U | Settings button (Upright) | Pass | Pass |  |
| H06P | Settings button (PC) | Pass | Pass |  |
| H06G | Settings button (Pages) | Pass | Pass |  |
| H06F | Settings button (FT8Call) | Pass | Pass |  |
| H07U | SPOT button (Upright) | Pass | Pass |  |
| H07P | SPOT button (PC) | Pass | Pass |  |
| H07G | SPOT button (Pages) | Pass | Pass |  |
| H07F | SPOT button (FT8Call) | Pass | Pass |  |
| H08U | LOG button opens Log QSO (Upright) | Pass | Pass |  |
| H08P | LOG button opens Log QSO (PC) | Pass | Pass |  |
| H08G | LOG button opens Log QSO (Pages) | Pass | Pass |  |
| H08F | LOG button opens Log QSO (FT8Call) | Pass | Pass |  |
| H09U | TUNE button (Upright) | Pass | Pass |  |
| H09P | TUNE button (PC) | Pass | Pass |  |
| H09G | TUNE button (Pages) | Pass | Pass |  |
| H09F | TUNE button (FT8Call) | Pass | Pass |  |
| H10U | HB press-and-hold menu (Upright) | Pass | Pass |  |
| H10P | HB press-and-hold menu (PC) | Pass | Pass |  |
| H10G | HB press-and-hold menu (Pages) | Pass | Pass |  |
| H10F | HB press-and-hold menu (FT8Call) | Pass | Pass |  |
| H11U | CQ press-and-hold menu (Upright) | Pass | Pass |  |
| H11P | CQ press-and-hold menu (PC) | Pass | Pass |  |
| H11G | CQ press-and-hold menu (Pages) | Pass | Pass |  |
| H11F | CQ press-and-hold menu (FT8Call) | Pass | Pass |  |
| H12U | REPLY button (Upright) | Pass | Pass |  |
| H12P | REPLY button (PC) | Pass | Pass |  |
| H12G | REPLY button (Pages) | Pass | Pass |  |
| H12F | REPLY button (FT8Call) | Pass | Pass |  |
| H13U | SNR button (Upright) | Pass | Pass |  |
| H13P | SNR button (PC) | Pass | Pass |  |
| H13G | SNR button (Pages) | Pass | Pass |  |
| H13F | SNR button (FT8Call) | Pass | Pass |  |
| H14U | INFO button (Upright) | Pass | Pass |  |
| H14P | INFO button (PC) | Pass | Pass |  |
| H14G | INFO button (Pages) | Pass | Pass |  |
| H14F | INFO button (FT8Call) | Pass | Pass |  |
| H15U | STATUS button (Upright) | Pass | Pass |  |
| H15P | STATUS button (PC) | Pass | Pass |  |
| H15G | STATUS button (Pages) | Pass | Pass |  |
| H15F | STATUS button (FT8Call) | Pass | Pass |  |
| H16U | Saved menu (Upright) | Pass | Pass |  |
| H16P | Saved menu (PC) | Pass | Pass |  |
| H16G | Saved menu (Pages) | Pass | Pass |  |
| H16F | Saved menu (FT8Call) | Pass | Pass |  |
| H17U | Saved > Edit Saved Messages (Upright) | Pass | Pass |  |
| H17P | Saved > Edit Saved Messages (PC) | Pass | Pass |  |
| H17G | Saved > Edit Saved Messages (Pages) | Pass | Pass |  |
| H17F | Saved > Edit Saved Messages (FT8Call) | Pass | Pass |  |
| H18U | Saved > APRS EMAIL (Upright) | Pass | Pass |  |
| H18P | Saved > APRS EMAIL (PC) | Pass | Pass |  |
| H18G | Saved > APRS EMAIL (Pages) | Pass | Pass |  |
| H18F | Saved > APRS EMAIL (FT8Call) | Pass | Pass |  |
| H19U | Saved > APRS TXT (Upright) | Pass | Pass |  |
| H19P | Saved > APRS TXT (PC) | Pass | Pass |  |
| H19G | Saved > APRS TXT (Pages) | Pass | Pass |  |
| H19F | Saved > APRS TXT (FT8Call) | Pass | Pass |  |
| H20U | Saved > APRS GRID (Upright) | Pass | Pass |  |
| H20P | Saved > APRS GRID (PC) | Pass | Pass |  |
| H20G | Saved > APRS GRID (Pages) | Pass | Pass |  |
| H20F | Saved > APRS GRID (FT8Call) | Pass | Pass |  |
| H21U | Saved > APRS MSG (Upright) | Pass | Pass |  |
| H21P | Saved > APRS MSG (PC) | Pass | Pass |  |
| H21G | Saved > APRS MSG (Pages) | Pass | Pass |  |
| H21F | Saved > APRS MSG (FT8Call) | Pass | Pass |  |
| H22U | Directed menu greying (Upright) | Pass | Pass |  |
| H22P | Directed menu greying (PC) | Pass | Pass |  |
| H22G | Directed menu greying (Pages) | Pass | Pass |  |
| H22F | Directed menu greying (FT8Call) | Pass | Pass |  |
| H23U | Deselect button (Upright) | Pass | Pass |  |
| H23P | Deselect button (PC) | Pass | Pass |  |
| H23G | Deselect button (Pages) | Pass | Pass |  |
| H23F | Deselect button (FT8Call) | Pass | Pass |  |
| H24U | Send button texts (Upright) | Pass | Pass |  |
| H24P | Send button texts (PC) | Pass | Pass |  |
| H24G | Send button texts (Pages) | Pass | Pass |  |
| H24F | Send button texts (FT8Call) | Pass | Pass |  |
| H25U | Halt button (Upright) | Pass | Pass |  |
| H25P | Halt button (PC) | Pass | Pass |  |
| H25G | Halt button (Pages) | Pass | Pass |  |
| H25F | Halt button (FT8Call) | Pass | Pass |  |
| H26U | Typing Box hint with a selection (Upright) | Pass | Pass |  |
| H26P | Typing Box hint with a selection (PC) | Pass | Pass |  |
| H26G | Typing Box hint with a selection (Pages) | Pass | Pass |  |
| H26F | Typing Box hint with a selection (FT8Call) | Pass | Pass |  |
| H27U | wpm box (Upright) | Pass | Pass |  |
| H27P | wpm box (PC) | Pass | Pass |  |
| H27G | wpm box (Pages) | Pass | Pass |  |
| H27F | wpm box (FT8Call) | Pass | Pass |  |
| H28U | Status bar labels (Upright) | Pass | Pass | corrected check |
| H28P | Status bar labels (PC) | Pass | Pass |  |
| H28G | Status bar labels (Pages) | Pass | Pass |  |
| H28F | Status bar labels (FT8Call) | Pass | Pass | check fixed: FT8Call's bar has no Freq or Auto Reply (js8_status_bar_ft8call.xml) |
| H29U | Signal meter (Upright) | Pass | Pass |  |
| H29P | Signal meter (PC) | Pass | Pass |  |
| H29G | Signal meter (Pages) | Pass | Pass |  |
| H29F | Signal meter (FT8Call) | Pass | Pass |  |
| H30U | CAT button colours (Upright) | Pass | Pass |  |
| H30P | CAT button colours (PC) | Pass | Pass |  |
| H30G | CAT button colours (Pages) | Pass | Pass |  |
| H30F | CAT button colours (FT8Call) | Pass | Pass |  |
| H31U | Power slider (Upright) | N/A | N/A | no Power slider upright; Power is in the Settings window (L09U) |
| H31P | Power slider (PC) | Pass | Pass |  |
| H31G | Power slider (Pages) | Pass | Pass |  |
| H31F | Power slider (FT8Call) | Pass | Pass |  |
| H32U | Message Window scrolls (Upright) | Pass | Pass |  |
| H32P | Message Window scrolls (PC) | Pass | Pass |  |
| H32G | Message Window scrolls (Pages) | Pass | Pass |  |
| H32F | Message Window scrolls (FT8Call) | Pass | Pass |  |
| H33U | Message Window menu Cut Copy Paste (Upright) | Pass | Pass |  |
| H33P | Message Window menu Cut Copy Paste (PC) | Pass | Pass |  |
| H33G | Message Window menu Cut Copy Paste (Pages) | Pass | Pass |  |
| H33F | Message Window menu Cut Copy Paste (FT8Call) | Pass | Pass |  |
| H34U | Message Window Clear (Upright) | Pass | Pass |  |
| H34P | Message Window Clear (PC) | Pass | Pass | app fixed 2026-11-03: the menu opens at the press point, all 6 items show |
| H34G | Message Window Clear (Pages) | Pass | Pass |  |
| H34F | Message Window Clear (FT8Call) | Pass | Pass |  |
| H35U | Double tap a word in the Message Window (Upright) | Pass | Pass |  |
| H35P | Double tap a word in the Message Window (PC) | Pass | Pass |  |
| H35G | Double tap a word in the Message Window (Pages) | Pass | Pass |  |
| H35F | Double tap a word in the Message Window (FT8Call) | Pass | Pass |  |
| H36U | Typing Box menu (Upright) | Pass | Pass |  |
| H36P | Typing Box menu (PC) | Pass | Pass | fixed: grab strips no longer cover the Typing Box |
| H36G | Typing Box menu (Pages) | Pass | Pass |  |
| H36F | Typing Box menu (FT8Call) | Pass | Pass |  |
| H37U | Restore Previous Message fills the box (Upright) | Pass | Pass | corrected check |
| H37P | Restore Previous Message fills the box (PC) | Pass | Pass |  |
| H37G | Restore Previous Message fills the box (Pages) | Pass | Pass |  |
| H37F | Restore Previous Message fills the box (FT8Call) | Pass | Pass |  |

## I. Lists

| ID | Test | Run 1 | Run 2 | Note |
|---|---|---|---|---|
| I01U | Band Activity header tap sorts (Upright) | Pass | Pass |  |
| I01P | Band Activity header tap sorts (PC) | Pass | Pass |  |
| I01G | Band Activity header tap sorts (Pages) | Pass | Pass |  |
| I01F | Band Activity header tap sorts (FT8Call) | Pass | Pass |  |
| I02U | Band Activity press-and-hold columns (Upright) | Pass | Pass |  |
| I02P | Band Activity press-and-hold columns (PC) | Pass | Pass |  |
| I02G | Band Activity press-and-hold columns (Pages) | Pass | Pass |  |
| I02F | Band Activity press-and-hold columns (FT8Call) | Pass | Pass |  |
| I03U | Band Activity row menu (Upright) | Pass | Pass |  |
| I03P | Band Activity row menu (PC) | Pass | Pass |  |
| I03G | Band Activity row menu (Pages) | Pass | Pass |  |
| I03F | Band Activity row menu (FT8Call) | Pass | Pass |  |
| I04U | Band Activity double tap (Upright) | Pass | Pass |  |
| I04P | Band Activity double tap (PC) | Pass | Pass |  |
| I04G | Band Activity double tap (Pages) | Pass | Pass |  |
| I04F | Band Activity double tap (FT8Call) | Pass | Pass |  |
| I05U | Call Activity header tap sorts (Upright) | Pass | Pass |  |
| I05P | Call Activity header tap sorts (PC) | Pass | Pass |  |
| I05G | Call Activity header tap sorts (Pages) | Pass | Pass |  |
| I05F | Call Activity header tap sorts (FT8Call) | Pass | Pass |  |
| I06U | Call Activity press-and-hold columns (Upright) | Pass | Pass |  |
| I06P | Call Activity press-and-hold columns (PC) | Pass | Pass |  |
| I06G | Call Activity press-and-hold columns (Pages) | Pass | Pass |  |
| I06F | Call Activity press-and-hold columns (FT8Call) | Pass | Pass |  |
| I07U | Call Activity empty list menu (Upright) | Pass | Pass |  |
| I07P | Call Activity empty list menu (PC) | Pass | Pass |  |
| I07G | Call Activity empty list menu (Pages) | Pass | Pass |  |
| I07F | Call Activity empty list menu (FT8Call) | Pass | Pass |  |
| I08U | Call menu Jump to offset (Upright) | Pass | Pass |  |
| I08P | Call menu Jump to offset (PC) | Pass | Pass |  |
| I08G | Call menu Jump to offset (Pages) | Pass | Pass |  |
| I08F | Call menu Jump to offset (FT8Call) | Pass | Pass |  |
| I09U | Call menu Jump to speed (Upright) | Pass | Pass |  |
| I09P | Call menu Jump to speed (PC) | Pass | Pass |  |
| I09G | Call menu Jump to speed (Pages) | Pass | Pass |  |
| I09F | Call menu Jump to speed (FT8Call) | Pass | Pass |  |
| I10U | Call menu Jump to time drift (Upright) | Pass | Pass |  |
| I10P | Call menu Jump to time drift (PC) | Pass | Pass |  |
| I10G | Call menu Jump to time drift (Pages) | Pass | Pass |  |
| I10F | Call menu Jump to time drift (FT8Call) | Pass | Pass |  |
| I11U | Call menu Log... (Upright) | Pass | Pass |  |
| I11P | Call menu Log... (PC) | Pass | Pass |  |
| I11G | Call menu Log... (Pages) | Pass | Pass |  |
| I11F | Call menu Log... (FT8Call) | Pass | Pass |  |
| I12U | Call menu Show Message Inbox... (Upright) | Pass | Pass |  |
| I12P | Call menu Show Message Inbox... (PC) | Pass | Pass |  |
| I12G | Call menu Show Message Inbox... (Pages) | Pass | Pass |  |
| I12F | Call menu Show Message Inbox... (FT8Call) | Pass | Pass |  |
| I13U | Call menu Store Message... (Upright) | Pass | Pass |  |
| I13P | Call menu Store Message... (PC) | Pass | Pass |  |
| I13G | Call menu Store Message... (Pages) | Pass | Pass |  |
| I13F | Call menu Store Message... (FT8Call) | Pass | Pass |  |
| I14U | Call menu Saved Messages (Upright) | Pass | Pass |  |
| I14P | Call menu Saved Messages (PC) | Pass | Pass |  |
| I14G | Call menu Saved Messages (Pages) | Pass | Pass |  |
| I14F | Call menu Saved Messages (FT8Call) | Pass | Pass |  |
| I15U | Call menu Directed to CALL... (Upright) | Pass | Pass |  |
| I15P | Call menu Directed to CALL... (PC) | Pass | Pass |  |
| I15G | Call menu Directed to CALL... (Pages) | Pass | Pass |  |
| I15F | Call menu Directed to CALL... (FT8Call) | Pass | Pass |  |
| I16U | Call menu Relay via CALL... (Upright) | Pass | Pass |  |
| I16P | Call menu Relay via CALL... (PC) | Pass | Pass |  |
| I16G | Call menu Relay via CALL... (Pages) | Pass | Pass |  |
| I16F | Call menu Relay via CALL... (FT8Call) | Pass | Pass |  |
| I17U | Call menu Deselect (Upright) | Pass | Pass |  |
| I17P | Call menu Deselect (PC) | Pass | Pass |  |
| I17G | Call menu Deselect (Pages) | Pass | Pass |  |
| I17F | Call menu Deselect (FT8Call) | Pass | Pass |  |
| I18U | Call menu Add New Station or Group... (Upright) | Pass | Pass |  |
| I18P | Call menu Add New Station or Group... (PC) | Pass | Pass |  |
| I18G | Call menu Add New Station or Group... (Pages) | Pass | Pass |  |
| I18F | Call menu Add New Station or Group... (FT8Call) | Pass | Pass |  |
| I19U | Call menu Remove This Station/Group (Upright) | Pass | Pass |  |
| I19P | Call menu Remove This Station/Group (PC) | Pass | Pass |  |
| I19G | Call menu Remove This Station/Group (Pages) | Pass | Pass |  |
| I19F | Call menu Remove This Station/Group (FT8Call) | Pass | Pass |  |
| I20U | Call menu Block This Station (Upright) | Pass | Pass |  |
| I20P | Call menu Block This Station (PC) | Pass | Pass |  |
| I20G | Call menu Block This Station (Pages) | Pass | Pass |  |
| I20F | Call menu Block This Station (FT8Call) | Pass | Pass |  |
| I21U | Call menu Clear Entire List (Upright) | Pass | Pass |  |
| I21P | Call menu Clear Entire List (PC) | Pass | Pass |  |
| I21G | Call menu Clear Entire List (Pages) | Pass | Pass |  |
| I21F | Call menu Clear Entire List (FT8Call) | Pass | Pass |  |
| I22U | Call menu Clear All Lists asks first (Upright) | Pass | Pass |  |
| I22P | Call menu Clear All Lists asks first (PC) | Pass | Pass |  |
| I22G | Call menu Clear All Lists asks first (Pages) | Pass | Pass |  |
| I22F | Call menu Clear All Lists asks first (FT8Call) | Pass | Pass |  |
| I23U | Lists kept after restart (Upright) | Pass | Pass |  |
| I23P | Lists kept after restart (PC) | Pass | Pass |  |
| I23G | Lists kept after restart (Pages) | Pass | Pass |  |
| I23F | Lists kept after restart (FT8Call) | Pass | Pass |  |
| I24U | Line dividers drag (Upright) | Pass | Pass |  |
| I24P | Line dividers drag (PC) | Pass | Pass |  |
| I24G | Line dividers drag (Pages) | Pass | Pass |  |
| I24F | Line dividers drag (FT8Call) | Pass | Pass |  |
| I25U | Reset Frame Sizes (Upright) | Pass | Pass |  |
| I25P | Reset Frame Sizes (PC) | Pass | Pass | View menu and Settings button both reset (Settings fixed 2026-11-03, ui12.py) |
| I25G | Reset Frame Sizes (Pages) | Pass | Pass |  |
| I25F | Reset Frame Sizes (FT8Call) | Pass | Pass | View menu and Settings button both reset (Settings fixed 2026-11-03, ui12.py) |

## J. Waterfall

| ID | Test | Run 1 | Run 2 | Note |
|---|---|---|---|---|
| J01U | Tap sets offset (Upright) | Pass | Pass |  |
| J01P | Tap sets offset (PC) | Pass | Pass |  |
| J01G | Tap sets offset (Pages) | Pass | Pass |  |
| J01F | Tap sets offset (FT8Call) | Pass | Pass |  |
| J02U | Scale tap sets offset (Upright) | Pass | Pass |  |
| J02P | Scale tap sets offset (PC) | Pass | Pass |  |
| J02G | Scale tap sets offset (Pages) | Pass | Pass |  |
| J02F | Scale tap sets offset (FT8Call) | Pass | Pass |  |
| J03U | Tap refused while a message is queued (Upright) | Pass | Pass |  |
| J03P | Tap refused while a message is queued (PC) | Pass | Pass |  |
| J03G | Tap refused while a message is queued (Pages) | Pass | Pass |  |
| J03F | Tap refused while a message is queued (FT8Call) | Pass | Pass |  |
| J04U | Control tab Offset arrows (Upright) | N/A | N/A | no Offset group upright by design, Offset is in Settings |
| J04P | Control tab Offset arrows (PC) | Pass | Pass |  |
| J04G | Control tab Offset arrows (Pages) | Pass | Pass |  |
| J04F | Control tab Offset arrows (FT8Call) | Pass | Pass |  |
| J05U | QSY (Upright) | Pass | Pass |  |
| J05P | QSY (PC) | Pass | Pass |  |
| J05G | QSY (Pages) | Pass | Pass |  |
| J05F | QSY (FT8Call) | Pass | Pass |  |
| J06U | Center arrows (Upright) | Pass | Pass |  |
| J06P | Center arrows (PC) | Pass | Pass |  |
| J06G | Center arrows (Pages) | Pass | Pass |  |
| J06F | Center arrows (FT8Call) | Pass | Pass |  |
| J07U | Enable Filter (Upright) | Pass | Pass |  |
| J07P | Enable Filter (PC) | Pass | Pass |  |
| J07G | Enable Filter (Pages) | Pass | Pass |  |
| J07F | Enable Filter (FT8Call) | Pass | Pass |  |
| J08U | Sync (Upright) | Pass | Pass |  |
| J08P | Sync (PC) | Pass | Pass |  |
| J08G | Sync (Pages) | Pass | Pass |  |
| J08F | Sync (FT8Call) | Pass | Pass |  |
| J09U | Filter Center and Width arrows (Upright) | Pass | Pass |  |
| J09P | Filter Center and Width arrows (PC) | Pass | Pass |  |
| J09G | Filter Center and Width arrows (Pages) | Pass | Pass |  |
| J09F | Filter Center and Width arrows (FT8Call) | Pass | Pass |  |
| J10U | Filter knobs (Upright) | Pass | Pass |  |
| J10P | Filter knobs (PC) | Pass | Pass |  |
| J10G | Filter knobs (Pages) | Pass | Pass |  |
| J10F | Filter knobs (FT8Call) | Pass | Pass |  |
| J11U | Timing tab Time Drift arrows (Upright) | Pass | Pass |  |
| J11P | Timing tab Time Drift arrows (PC) | Pass | Pass |  |
| J11G | Timing tab Time Drift arrows (Pages) | Pass | Pass |  |
| J11F | Timing tab Time Drift arrows (FT8Call) | Pass | Pass |  |
| J12U | Start Automatic Time Drift (Upright) | Pass | Pass |  |
| J12P | Start Automatic Time Drift (PC) | Pass | Pass |  |
| J12G | Start Automatic Time Drift (Pages) | Pass | Pass |  |
| J12F | Start Automatic Time Drift (FT8Call) | Pass | Pass |  |
| J13U | Stop After count (Upright) | Pass | Pass |  |
| J13P | Stop After count (PC) | Pass | Pass |  |
| J13G | Stop After count (Pages) | Pass | Pass |  |
| J13F | Stop After count (FT8Call) | Pass | Pass |  |
| J14U | Manual buttons (Upright) | Pass | Pass |  |
| J14P | Manual buttons (PC) | Pass | Pass |  |
| J14G | Manual buttons (Pages) | Pass | Pass |  |
| J14F | Manual buttons (FT8Call) | Pass | Pass |  |
| J15U | Display tab Start (Upright) | Pass | Pass |  |
| J15P | Display tab Start (PC) | Pass | Pass |  |
| J15G | Display tab Start (Pages) | Pass | Pass |  |
| J15F | Display tab Start (FT8Call) | Pass | Pass |  |
| J16U | Bins Per Pixel (Upright) | Pass | Pass |  |
| J16P | Bins Per Pixel (PC) | Pass | Pass |  |
| J16G | Bins Per Pixel (Pages) | Pass | Pass |  |
| J16F | Bins Per Pixel (FT8Call) | Pass | Pass |  |
| J17U | Palette list (Upright) | Pass | Pass |  |
| J17P | Palette list (PC) | Pass | Pass |  |
| J17G | Palette list (Pages) | Pass | Pass |  |
| J17F | Palette list (FT8Call) | Pass | Pass |  |
| J18U | Adjust... Palette Designer (Upright) | Pass | Pass |  |
| J18P | Adjust... Palette Designer (PC) | Pass | Pass |  |
| J18G | Adjust... Palette Designer (Pages) | Pass | Pass |  |
| J18F | Adjust... Palette Designer (FT8Call) | Pass | Pass |  |
| J19U | Scroll Speed (Upright) | Pass | Pass |  |
| J19P | Scroll Speed (PC) | Pass | Pass |  |
| J19G | Scroll Speed (Pages) | Pass | Pass |  |
| J19F | Scroll Speed (FT8Call) | Pass | Pass |  |
| J20U | Flatten off (Upright) | Pass | Pass |  |
| J20P | Flatten off (PC) | Pass | Pass |  |
| J20G | Flatten off (Pages) | Pass | Pass |  |
| J20F | Flatten off (FT8Call) | Pass | Pass |  |
| J21U | Gain (Upright) | Pass | Pass |  |
| J21P | Gain (PC) | Pass | Pass |  |
| J21G | Gain (Pages) | Pass | Pass |  |
| J21F | Gain (FT8Call) | Pass | Pass |  |
| J22U | Zero (Upright) | Pass | Pass |  |
| J22P | Zero (PC) | Pass | Pass |  |
| J22G | Zero (Pages) | Pass | Pass |  |
| J22F | Zero (FT8Call) | Pass | Pass |  |
| J23U | Filter Opacity (Upright) | Pass | Pass |  |
| J23P | Filter Opacity (PC) | Pass | Pass |  |
| J23G | Filter Opacity (Pages) | Pass | Pass |  |
| J23F | Filter Opacity (FT8Call) | Pass | Pass |  |
| J24U | Avg N (Upright) | Pass | Pass |  |
| J24P | Avg N (PC) | Pass | Pass |  |
| J24G | Avg N (Pages) | Pass | Pass |  |
| J24F | Avg N (FT8Call) | Pass | Pass |  |
| J25U | Current list and Smooth (Upright) | Pass | Pass |  |
| J25P | Current list and Smooth (PC) | Pass | Pass |  |
| J25G | Current list and Smooth (Pages) | Pass | Pass |  |
| J25F | Current list and Smooth (FT8Call) | Pass | Pass |  |
| J26U | Display Decode Attempts (Upright) | Pass | Pass |  |
| J26P | Display Decode Attempts (PC) | Pass | Pass |  |
| J26G | Display Decode Attempts (Pages) | Pass | Pass |  |
| J26F | Display Decode Attempts (FT8Call) | Pass | Pass |  |
| J27U | Spectrum Height, Gain, Zero (Upright) | Pass | Pass |  |
| J27P | Spectrum Height, Gain, Zero (PC) | Pass | Pass |  |
| J27G | Spectrum Height, Gain, Zero (Pages) | Pass | Pass |  |
| J27F | Spectrum Height, Gain, Zero (FT8Call) | Pass | Pass |  |
| J28U | Green cycle line and text (Upright) | Pass | Pass | checked by eye in shots/jlook_*.png |
| J28P | Green cycle line and text (PC) | Pass | Pass | checked by eye in shots/jlook_*.png |
| J28G | Green cycle line and text (Pages) | Pass | Pass | checked by eye in shots/jlook_*.png |
| J28F | Green cycle line and text (FT8Call) | Pass | Pass | checked by eye in shots/jlook_*.png |
| J29U | Frequency scale colours (Upright) | Pass | Pass | checked by eye in shots/jlook_*.png |
| J29P | Frequency scale colours (PC) | Pass | Pass | checked by eye in shots/jlook_*.png |
| J29G | Frequency scale colours (Pages) | Pass | Pass | checked by eye in shots/jlook_*.png |
| J29F | Frequency scale colours (FT8Call) | Pass | Pass | checked by eye in shots/jlook_*.png |
| J30U | WSPR marker on 30 m (Upright) | Pass | Pass | checked by eye in shots/jlook_*.png |
| J30P | WSPR marker on 30 m (PC) | Pass | Pass | checked by eye in shots/jlook_*.png |
| J30G | WSPR marker on 30 m (Pages) | Pass | Pass | checked by eye in shots/jlook_*.png |
| J30F | WSPR marker on 30 m (FT8Call) | Pass | Pass | checked by eye in shots/jlook_*.png |

## K. Inbox, Log QSO, Export

| ID | Test | Run 1 | Run 2 | Note |
|---|---|---|---|---|
| K01U | Inbox list newest first (Upright) | Pass | Pass |  |
| K01P | Inbox list newest first (PC) | Pass | Pass |  |
| K01G | Inbox list newest first (Pages) | Pass | Pass |  |
| K01F | Inbox list newest first (FT8Call) | Pass | Pass |  |
| K02U | Inbox multi-row Delete (Upright) | Pass | Pass |  |
| K02P | Inbox multi-row Delete (PC) | Pass | Pass |  |
| K02G | Inbox multi-row Delete (Pages) | Pass | Pass |  |
| K02F | Inbox multi-row Delete (FT8Call) | Pass | Pass |  |
| K03U | Inbox Reply (Upright) | Pass | Pass |  |
| K03P | Inbox Reply (PC) | Pass | Pass |  |
| K03G | Inbox Reply (Pages) | Pass | Pass |  |
| K03F | Inbox Reply (FT8Call) | Pass | Pass |  |
| K04U | Inbox filter by call (Upright) | Pass | Pass |  |
| K04P | Inbox filter by call (PC) | Pass | Pass |  |
| K04G | Inbox filter by call (Pages) | Pass | Pass |  |
| K04F | Inbox filter by call (FT8Call) | Pass | Pass |  |
| K05U | Log QSO fills from selection (Upright) | Pass | Pass |  |
| K05P | Log QSO fills from selection (PC) | Pass | Pass |  |
| K05G | Log QSO fills from selection (Pages) | Pass | Pass |  |
| K05F | Log QSO fills from selection (FT8Call) | Pass | Pass |  |
| K06U | Log QSO Now buttons (Upright) | Pass | Pass |  |
| K06P | Log QSO Now buttons (PC) | Pass | Pass |  |
| K06G | Log QSO Now buttons (Pages) | Pass | Pass |  |
| K06F | Log QSO Now buttons (FT8Call) | Pass | Pass |  |
| K07U | Log QSO Add New Field (Upright) | Pass | Pass |  |
| K07P | Log QSO Add New Field (PC) | Pass | Pass |  |
| K07G | Log QSO Add New Field (Pages) | Pass | Pass |  |
| K07F | Log QSO Add New Field (FT8Call) | Pass | Pass |  |
| K08U | Log QSO Retain Comments (Upright) | Pass | Pass |  |
| K08P | Log QSO Retain Comments (PC) | Pass | Pass |  |
| K08G | Log QSO Retain Comments (Pages) | Pass | Pass |  |
| K08F | Log QSO Retain Comments (FT8Call) | Pass | Pass |  |
| K09U | Log QSO word into focused field (Upright) | Pass | Pass |  |
| K09P | Log QSO word into focused field (PC) | Pass | Pass |  |
| K09G | Log QSO word into focused field (Pages) | Pass | Pass |  |
| K09F | Log QSO word into focused field (FT8Call) | Pass | Pass |  |
| K10U | Log QSO word into Comments (Upright) | Pass | Pass |  |
| K10P | Log QSO word into Comments (PC) | Pass | Pass |  |
| K10G | Log QSO word into Comments (Pages) | Pass | Pass |  |
| K10F | Log QSO word into Comments (FT8Call) | Pass | Pass |  |
| K11U | Log QSO Start/End word exclusion (Upright) | Pass | Pass |  |
| K11P | Log QSO Start/End word exclusion (PC) | Pass | Pass |  |
| K11G | Log QSO Start/End word exclusion (Pages) | Pass | Pass |  |
| K11F | Log QSO Start/End word exclusion (FT8Call) | Pass | Pass |  |
| K12U | Export Options checkboxes (Upright) | Pass | Pass |  |
| K12P | Export Options checkboxes (PC) | Pass | Pass |  |
| K12G | Export Options checkboxes (Pages) | Pass | Pass |  |
| K12F | Export Options checkboxes (FT8Call) | Pass | Pass |  |

## L. Settings window

| ID | Test | Run 1 | Run 2 | Note |
|---|---|---|---|---|
| L01U | MULTI (Upright) | Pass | Pass |  |
| L01P | MULTI (PC) | Pass | Pass |  |
| L01G | MULTI (Pages) | Pass | Pass |  |
| L01F | MULTI (FT8Call) | Pass | Pass |  |
| L02U | AUTO (Upright) | Pass | Pass |  |
| L02P | AUTO (PC) | Pass | Pass |  |
| L02G | AUTO (Pages) | Pass | Pass |  |
| L02F | AUTO (FT8Call) | Pass | Pass |  |
| L03U | HB (Upright) | Pass | Pass |  |
| L03P | HB (PC) | Pass | Pass |  |
| L03G | HB (Pages) | Pass | Pass |  |
| L03F | HB (FT8Call) | Pass | Pass |  |
| L04U | ACK greying (Upright) | Pass | Pass |  |
| L04P | ACK greying (PC) | Pass | Pass |  |
| L04G | ACK greying (Pages) | Pass | Pass |  |
| L04F | ACK greying (FT8Call) | Pass | Pass |  |
| L05U | Speed list (Upright) | Pass | Pass |  |
| L05P | Speed list (PC) | Pass | Pass |  |
| L05G | Speed list (Pages) | Pass | Pass |  |
| L05F | Speed list (FT8Call) | Pass | Pass |  |
| L06U | Frequency list (Upright) | Pass | Pass |  |
| L06P | Frequency list (PC) | Pass | Pass |  |
| L06G | Frequency list (Pages) | Pass | Pass |  |
| L06F | Frequency list (FT8Call) | Pass | Pass |  |
| L07U | Custom frequency (Upright) | Pass | Pass |  |
| L07P | Custom frequency (PC) | Pass | Pass |  |
| L07G | Custom frequency (Pages) | Pass | Pass |  |
| L07F | Custom frequency (FT8Call) | Pass | Pass |  |
| L08U | Offset box (Upright) | Pass | Pass |  |
| L08P | Offset box (PC) | Pass | Pass |  |
| L08G | Offset box (Pages) | Pass | Pass |  |
| L08F | Offset box (FT8Call) | Pass | Pass |  |
| L09U | Power (Upright) | Pass | Pass |  |
| L09P | Power (PC) | Pass | Pass |  |
| L09G | Power (Pages) | Pass | Pass |  |
| L09F | Power (FT8Call) | Pass | Pass |  |
| L10U | Show Band Heartbeats and ACKs (Upright) | Pass | Pass |  |
| L10P | Show Band Heartbeats and ACKs (PC) | Pass | Pass |  |
| L10G | Show Band Heartbeats and ACKs (Pages) | Pass | Pass |  |
| L10F | Show Band Heartbeats and ACKs (FT8Call) | Pass | Pass |  |
| L11U | Show Offset in Band Activity (Upright) | Pass | Pass |  |
| L11P | Show Offset in Band Activity (PC) | Pass | Pass |  |
| L11G | Show Offset in Band Activity (Pages) | Pass | Pass |  |
| L11F | Show Offset in Band Activity (FT8Call) | Pass | Pass |  |
| L12U | Show SNR in Band Activity (Upright) | Pass | Pass |  |
| L12P | Show SNR in Band Activity (PC) | Pass | Pass |  |
| L12G | Show SNR in Band Activity (Pages) | Pass | Pass |  |
| L12F | Show SNR in Band Activity (FT8Call) | Pass | Pass |  |
| L13U | Show Column Labels (Upright) | Pass | Pass |  |
| L13P | Show Column Labels (PC) | Pass | Pass |  |
| L13G | Show Column Labels (Pages) | Pass | Pass |  |
| L13F | Show Column Labels (FT8Call) | Pass | Pass |  |
| L14U | Show FT8 decodes (Upright) | Pass | Pass |  |
| L14P | Show FT8 decodes (PC) | Pass | Pass |  |
| L14G | Show FT8 decodes (Pages) | Pass | Pass |  |
| L14F | Show FT8 decodes (FT8Call) | Pass | Pass |  |
| L15U | Show panels (Upright) | Pass | Pass |  |
| L15P | Show panels (PC) | Pass | Pass |  |
| L15G | Show panels (Pages) | Pass | Pass |  |
| L15F | Show panels (FT8Call) | Pass | Pass |  |
| L16U | Clear Band Activity (Upright) | Pass | Pass |  |
| L16P | Clear Band Activity (PC) | Pass | Pass |  |
| L16G | Clear Band Activity (Pages) | Pass | Pass |  |
| L16F | Clear Band Activity (FT8Call) | Pass | Pass |  |
| L17U | Clear Call Activity (Upright) | Pass | Pass |  |
| L17P | Clear Call Activity (PC) | Pass | Pass |  |
| L17G | Clear Call Activity (Pages) | Pass | Pass |  |
| L17F | Clear Call Activity (FT8Call) | Pass | Pass |  |
| L18U | Clear RX Activity (Upright) | Pass | Pass |  |
| L18P | Clear RX Activity (PC) | Pass | Pass |  |
| L18G | Clear RX Activity (Pages) | Pass | Pass |  |
| L18F | Clear RX Activity (FT8Call) | Pass | Pass |  |
| L19U | Clear All Activity (Upright) | Pass | Pass |  |
| L19P | Clear All Activity (PC) | Pass | Pass |  |
| L19G | Clear All Activity (Pages) | Pass | Pass |  |
| L19F | Clear All Activity (FT8Call) | Pass | Pass |  |
| L20U | Configurations... (Upright) | Pass | Pass |  |
| L20P | Configurations... (PC) | Pass | Pass |  |
| L20G | Configurations... (Pages) | Pass | Pass |  |
| L20F | Configurations... (FT8Call) | Pass | Pass |  |
| L21U | Exit (Upright) | Pass | Pass |  |
| L21P | Exit (PC) | Pass | Pass |  |
| L21G | Exit (Pages) | Pass | Pass |  |
| L21F | Exit (FT8Call) | Pass | Pass |  |
| L22U | Copyright notice (Upright) | Pass | Pass |  |
| L22P | Copyright notice (PC) | Pass | Pass |  |
| L22G | Copyright notice (Pages) | Pass | Pass |  |
| L22F | Copyright notice (FT8Call) | Pass | Pass |  |
| L23U | Check for Updates (Upright) | Pass | Pass |  |
| L23P | Check for Updates (PC) | Pass | Pass |  |
| L23G | Check for Updates (Pages) | Pass | Pass |  |
| L23F | Check for Updates (FT8Call) | Pass | Pass |  |
| L24U | JS8Call User Guide (Upright) | Pass | Pass |  |
| L24P | JS8Call User Guide (PC) | Pass | Pass |  |
| L24G | JS8Call User Guide (Pages) | Pass | Pass |  |
| L24F | JS8Call User Guide (FT8Call) | Pass | Pass |  |
| L25U | Save (Upright) | Pass | Pass |  |
| L25P | Save (PC) | Pass | Pass |  |
| L25G | Save (Pages) | Pass | Pass |  |
| L25F | Save (FT8Call) | Pass | Pass |  |
| L26U | Cancel (Upright) | Pass | Pass |  |
| L26P | Cancel (PC) | Pass | Pass |  |
| L26G | Cancel (Pages) | Pass | Pass |  |
| L26F | Cancel (FT8Call) | Pass | Pass |  |
| L27U | gear opens Station (Upright) | Pass | Pass |  |
| L27P | gear opens Station (PC) | Pass | Pass |  |
| L27G | gear opens Station (Pages) | Pass | Pass |  |
| L27F | gear opens Station (FT8Call) | Pass | Pass |  |

## M. Station window

| ID | Test | Run 1 | Run 2 | Note |
|---|---|---|---|---|
| M01U | General page opens (Upright) | Pass | Pass |  |
| M01P | General page opens (PC) | Pass | Pass |  |
| M01G | General page opens (Pages) | Pass | Pass |  |
| M01F | General page opens (FT8Call) | Pass | Pass |  |
| M02U | My Callsign refuses bad call (Upright) | Pass | Pass |  |
| M02P | My Callsign refuses bad call (PC) | Pass | Pass |  |
| M02G | My Callsign refuses bad call (Pages) | Pass | Pass |  |
| M02F | My Callsign refuses bad call (FT8Call) | Pass | Pass |  |
| M03U | SWL (Listening only) (Upright) | Pass | Pass | OK leaves Station with no callsign when SWL is ticked |
| M03P | SWL (Listening only) (PC) | Pass | Pass |  |
| M03G | SWL (Listening only) (Pages) | Pass | Pass |  |
| M03F | SWL (Listening only) (FT8Call) | Pass | Pass |  |
| M04U | Grid refuses bad grid (Upright) | Pass | Pass |  |
| M04P | Grid refuses bad grid (PC) | Pass | Pass |  |
| M04G | Grid refuses bad grid (Pages) | Pass | Pass |  |
| M04F | Grid refuses bad grid (FT8Call) | Pass | Pass |  |
| M05U | Acquire GPS and Set (Upright) | Pass | Pass |  |
| M05P | Acquire GPS and Set (PC) | Pass | Pass |  |
| M05G | Acquire GPS and Set (Pages) | Pass | Pass |  |
| M05F | Acquire GPS and Set (FT8Call) | Pass | Pass |  |
| M05N1 | Acquire GPS: a Wi-Fi / cell grid shows at once, a GPS fix replaces it and ends the search (Upright) | Pass | Pass | gpsnet.py, test providers for network and GPS: FN25DK70, then FN03HQ27, a later network answer leaves FN03HQ27 |
| M05N3 | Acquire GPS: with only Wi-Fi / cell answering, its grid is still shown when the 2 minute search ends (Upright) | Pass | Pass | gpsnet.py: FN25DK70 before and after 2 min, not "No position" |
| M06U | Callsign Groups refuses bad group (Upright) | Pass | Pass |  |
| M06P | Callsign Groups refuses bad group (PC) | Pass | Pass |  |
| M06G | Callsign Groups refuses bad group (Pages) | Pass | Pass |  |
| M06F | Callsign Groups refuses bad group (FT8Call) | Pass | Pass |  |
| M07U | CQ Message refuses bad format (Upright) | Pass | Pass |  |
| M07P | CQ Message refuses bad format (PC) | Pass | Pass |  |
| M07G | CQ Message refuses bad format (Pages) | Pass | Pass |  |
| M07F | CQ Message refuses bad format (FT8Call) | Pass | Pass |  |
| M08U | Reply, Info, Status text (Upright) | Pass | Pass |  |
| M08P | Reply, Info, Status text (PC) | Pass | Pass |  |
| M08G | Reply, Info, Status text (Pages) | Pass | Pass |  |
| M08F | Reply, Info, Status text (FT8Call) | Pass | Pass |  |
| M09U | Behavior checkboxes save (Upright) | Pass | Pass |  |
| M09P | Behavior checkboxes save (PC) | Pass | Pass |  |
| M09G | Behavior checkboxes save (Pages) | Pass | Pass |  |
| M09F | Behavior checkboxes save (FT8Call) | Pass | Pass |  |
| M10U | Aging spin boxes (Upright) | Pass | Pass |  |
| M10P | Aging spin boxes (PC) | Pass | Pass |  |
| M10G | Aging spin boxes (Pages) | Pass | Pass |  |
| M10F | Aging spin boxes (FT8Call) | Pass | Pass |  |
| M11U | End of Transmission Character Indicator refuses empty (Upright) | Pass | Pass |  |
| M11P | End of Transmission Character Indicator refuses empty (PC) | Pass | Pass |  |
| M11G | End of Transmission Character Indicator refuses empty (Pages) | Pass | Pass |  |
| M11F | End of Transmission Character Indicator refuses empty (FT8Call) | Pass | Pass |  |
| M12U | Missing Frame Character Indicator (Upright) | Pass | Pass |  |
| M12P | Missing Frame Character Indicator (PC) | Pass | Pass |  |
| M12G | Missing Frame Character Indicator (Pages) | Pass | Pass |  |
| M12F | Missing Frame Character Indicator (FT8Call) | Pass | Pass |  |
| M13U | Heartbeat interval (Upright) | Pass | Pass |  |
| M13P | Heartbeat interval (PC) | Pass | Pass |  |
| M13G | Heartbeat interval (Pages) | Pass | Pass |  |
| M13F | Heartbeat interval (FT8Call) | Pass | Pass |  |
| M14U | Heartbeat Network group (Upright) | Pass | Pass |  |
| M14P | Heartbeat Network group (PC) | Pass | Pass |  |
| M14G | Heartbeat Network group (Pages) | Pass | Pass |  |
| M14F | Heartbeat Network group (FT8Call) | Pass | Pass |  |
| M15U | Autoreply group (Upright) | Pass | Pass |  |
| M15P | Autoreply group (PC) | Pass | Pass |  |
| M15G | Autoreply group (Pages) | Pass | Pass |  |
| M15F | Autoreply group (FT8Call) | Pass | Pass |  |
| M16U | Only autoreply refuses bad callsign (Upright) | Pass | Pass |  |
| M16P | Only autoreply refuses bad callsign (PC) | Pass | Pass |  |
| M16G | Only autoreply refuses bad callsign (Pages) | Pass | Pass |  |
| M16F | Only autoreply refuses bad callsign (FT8Call) | Pass | Pass | failed once in run 2 (cause not known, no screenshot then); passed in a trial and on retry |
| M17U | Callsign Filtering (Upright) | Pass | Pass |  |
| M17P | Callsign Filtering (PC) | Pass | Pass |  |
| M17G | Callsign Filtering (Pages) | Pass | Pass |  |
| M17F | Callsign Filtering (FT8Call) | Pass | Pass |  |
| M18U | Radio page Rig search (Upright) | Pass | Pass |  |
| M18P | Radio page Rig search (PC) | Pass | Pass |  |
| M18G | Radio page Rig search (Pages) | Pass | Pass |  |
| M18F | Radio page Rig search (FT8Call) | Pass | Pass |  |
| M19U | Radio page greying follows the rig (Upright) | Pass | Pass |  |
| M19P | Radio page greying follows the rig (PC) | Pass | Pass |  |
| M19G | Radio page greying follows the rig (Pages) | Pass | Pass |  |
| M19F | Radio page greying follows the rig (FT8Call) | Pass | Pass |  |
| M20U | Test CAT (Upright) | Pass | Pass |  |
| M20P | Test CAT (PC) | Pass | Pass |  |
| M20G | Test CAT (Pages) | Pass | Pass |  |
| M20F | Test CAT (FT8Call) | Pass | Pass |  |
| M21U | Test PTT (Upright) | Pass | Pass |  |
| M21P | Test PTT (PC) | Pass | Pass |  |
| M21G | Test PTT (Pages) | Pass | Pass |  |
| M21F | Test PTT (FT8Call) | Pass | Pass |  |
| M22U | Split Operation (Upright) | Pass | Pass |  |
| M22P | Split Operation (PC) | Pass | Pass |  |
| M22G | Split Operation (Pages) | Pass | Pass |  |
| M22F | Split Operation (FT8Call) | Pass | Pass |  |
| M23U | Tx delay (Upright) | Pass | Pass |  |
| M23P | Tx delay (PC) | Pass | Pass |  |
| M23G | Tx delay (Pages) | Pass | Pass |  |
| M23F | Tx delay (FT8Call) | Pass | Pass |  |
| M24U | Hold PTT (Upright) | Pass | Pass |  |
| M24P | Hold PTT (PC) | Pass | Pass |  |
| M24G | Hold PTT (Pages) | Pass | Pass |  |
| M24F | Hold PTT (FT8Call) | Pass | Pass |  |
| M25U | Audio page Input (Upright) | Pass | Pass |  |
| M25P | Audio page Input (PC) | Pass | Pass |  |
| M25G | Audio page Input (Pages) | Pass | Pass |  |
| M25F | Audio page Input (FT8Call) | Pass | Pass |  |
| M26U | Audio page Output (Upright) | Pass | Pass |  |
| M26P | Audio page Output (PC) | Pass | Pass |  |
| M26G | Audio page Output (Pages) | Pass | Pass |  |
| M26F | Audio page Output (FT8Call) | Pass | Pass |  |
| M27U | Input channel list has no Both (Upright) | Pass | Pass |  |
| M27P | Input channel list has no Both (PC) | Pass | Pass |  |
| M27G | Input channel list has no Both (Pages) | Pass | Pass |  |
| M27F | Input channel list has no Both (FT8Call) | Pass | Pass |  |
| M28U | Remember power settings by band (Upright) | Pass | Pass |  |
| M28P | Remember power settings by band (PC) | Pass | Pass |  |
| M28G | Remember power settings by band (Pages) | Pass | Pass |  |
| M28F | Remember power settings by band (FT8Call) | Pass | Pass |  |
| M29U | Layout page (Upright) | N/A | N/A | Layout page is listed only sideways; open check in PC |
| M29P | Layout page (PC) | Pass | Pass |  |
| M29G | Layout page (Pages) | Pass | Pass |  |
| M29F | Layout page (FT8Call) | Pass | Pass |  |
| M30U | Reporting Network Services (Upright) | Pass | Pass |  |
| M30P | Reporting Network Services (PC) | Pass | Pass |  |
| M30G | Reporting Network Services (Pages) | Pass | Pass |  |
| M30F | Reporting Network Services (FT8Call) | Pass | Pass |  |
| M31U | Reporting API group (Upright) | Pass | Pass |  |
| M31P | Reporting API group (PC) | Pass | Pass |  |
| M31G | Reporting API group (Pages) | Pass | Pass |  |
| M31F | Reporting API group (FT8Call) | Pass | Pass |  |
| M32U | Reporting N3FJP group (Upright) | Pass | Pass |  |
| M32P | Reporting N3FJP group (PC) | Pass | Pass |  |
| M32G | Reporting N3FJP group (Pages) | Pass | Pass |  |
| M32F | Reporting N3FJP group (FT8Call) | Pass | Pass |  |
| M33U | Reporting N1MM group (Upright) | Pass | Pass |  |
| M33P | Reporting N1MM group (PC) | Pass | Pass |  |
| M33G | Reporting N1MM group (Pages) | Pass | Pass |  |
| M33F | Reporting N1MM group (FT8Call) | Pass | Pass |  |
| M34U | Frequencies Calibration (Upright) | Pass | Pass |  |
| M34P | Frequencies Calibration (PC) | Pass | Pass |  |
| M34G | Frequencies Calibration (Pages) | Pass | Pass |  |
| M34F | Frequencies Calibration (FT8Call) | Pass | Pass |  |
| M35U | Frequency table Insert Delete Reset (Upright) | Pass | Pass |  |
| M35P | Frequency table Insert Delete Reset (PC) | Pass | Pass |  |
| M35G | Frequency table Insert Delete Reset (Pages) | Pass | Pass |  |
| M35F | Frequency table Insert Delete Reset (FT8Call) | Pass | Pass |  |
| M36U | Frequency table edit in place (Upright) | Pass | Pass |  |
| M36P | Frequency table edit in place (PC) | Pass | Pass |  |
| M36G | Frequency table edit in place (Pages) | Pass | Pass |  |
| M36F | Frequency table edit in place (FT8Call) | Pass | Pass |  |
| M37U | Frequency table Load Save as Merge (Upright) | Pass | Pass |  |
| M37P | Frequency table Load Save as Merge (PC) | Pass | Pass |  |
| M37G | Frequency table Load Save as Merge (Pages) | Pass | Pass |  |
| M37F | Frequency table Load Save as Merge (FT8Call) | Pass | Pass |  |
| M38U | Frequency Schedule (Upright) | Pass | Pass |  |
| M38P | Frequency Schedule (PC) | Pass | Pass |  |
| M38G | Frequency Schedule (Pages) | Pass | Pass |  |
| M38F | Frequency Schedule (FT8Call) | Pass | Pass |  |
| M39U | Saved Messages drag and edit (Upright) | Pass | Pass |  |
| M39P | Saved Messages drag and edit (PC) | Pass | Pass |  |
| M39G | Saved Messages drag and edit (Pages) | Pass | Pass |  |
| M39F | Saved Messages drag and edit (FT8Call) | Pass | Pass |  |
| M40U | Notifications table Test (Upright) | Pass | Pass |  |
| M40P | Notifications table Test (PC) | Pass | Pass |  |
| M40G | Notifications table Test (Pages) | Pass | Pass |  |
| M40F | Notifications table Test (FT8Call) | Pass | Pass |  |
| M41U | UI colours (Upright) | Pass | Pass |  |
| M41P | UI colours (PC) | Pass | Pass |  |
| M41G | UI colours (Pages) | Pass | Pass |  |
| M41F | UI colours (FT8Call) | Pass | Pass |  |
| M42U | UI fonts (Upright) | Pass | Pass |  |
| M42P | UI fonts (PC) | Pass | Pass |  |
| M42G | UI fonts (Pages) | Pass | Pass |  |
| M42F | UI fonts (FT8Call) | Pass | Pass |  |
| M43U | Run Diagnostics (Upright) | Pass | Pass |  |
| M43P | Run Diagnostics (PC) | Pass | Pass |  |
| M43G | Run Diagnostics (Pages) | Pass | Pass |  |
| M43F | Run Diagnostics (FT8Call) | Pass | Pass |  |
| M44U | Phone page (Upright) | Pass | Pass |  |
| M44P | Phone page (PC) | Pass | Pass |  |
| M44G | Phone page (Pages) | Pass | Pass |  |
| M44F | Phone page (FT8Call) | Pass | Pass |  |
| M45U | About (Upright) | Pass | Pass |  |
| M45P | About (PC) | Pass | Pass |  |
| M45G | About (Pages) | Pass | Pass |  |
| M45F | About (FT8Call) | Pass | Pass |  |
| M46U | OK (Upright) | Pass | Pass |  |
| M46P | OK (PC) | Pass | Pass |  |
| M46G | OK (Pages) | Pass | Pass |  |
| M46F | OK (FT8Call) | Pass | Pass |  |
| M47U | Cancel (Upright) | Pass | Pass |  |
| M47P | Cancel (PC) | Pass | Pass |  |
| M47G | Cancel (Pages) | Pass | Pass |  |
| M47F | Cancel (FT8Call) | Pass | Pass |  |

## N. Menus

| ID | Test | Run 1 | Run 2 | Note |
|---|---|---|---|---|
| N01U | File menu (Upright) | N/A | N/A | No menu bar in Upright |
| N01P | File menu (PC) | Pass | Pass |  |
| N01G | File menu (Pages) | N/A | N/A | No menu bar in Pages (Rob, 2026-10-20: no ⋮ menu) |
| N01F | File menu (FT8Call) | Pass | Pass |  |
| N02U | Configurations Clone (Upright) | N/A | N/A | No menu bar in Upright |
| N02P | Configurations Clone (PC) | Pass | Pass |  |
| N02G | Configurations Clone (Pages) | N/A | N/A | No menu bar in Pages (Rob, 2026-10-20: no ⋮ menu) |
| N02F | Configurations Clone (FT8Call) | Pass | Pass |  |
| N03U | Configurations Clone Into (Upright) | N/A | N/A | No menu bar in Upright |
| N03P | Configurations Clone Into (PC) | Pass | Pass |  |
| N03G | Configurations Clone Into (Pages) | N/A | N/A | N/A: no menu bar in Pages |
| N03F | Configurations Clone Into (FT8Call) | Pass | Pass |  |
| N04U | Configurations Rename (Upright) | N/A | N/A | No menu bar in Upright |
| N04P | Configurations Rename (PC) | Pass | Pass |  |
| N04G | Configurations Rename (Pages) | N/A | N/A | N/A: no menu bar in Pages |
| N04F | Configurations Rename (FT8Call) | Pass | Pass |  |
| N05U | Configurations Reset (Upright) | N/A | N/A | No menu bar in Upright |
| N05P | Configurations Reset (PC) | Pass | Pass |  |
| N05G | Configurations Reset (Pages) | N/A | N/A | N/A: no menu bar in Pages |
| N05F | Configurations Reset (FT8Call) | Pass | Pass |  |
| N06U | Configurations Delete (Upright) | N/A | N/A | No menu bar in Upright |
| N06P | Configurations Delete (PC) | Pass | Pass |  |
| N06G | Configurations Delete (Pages) | N/A | N/A | N/A: no menu bar in Pages |
| N06F | Configurations Delete (FT8Call) | Pass | Pass |  |
| N07U | Configurations Switch To (Upright) | N/A | N/A | No menu bar in Upright |
| N07P | Configurations Switch To (PC) | Pass | Pass |  |
| N07G | Configurations Switch To (Pages) | N/A | N/A | N/A: no menu bar in Pages |
| N07F | Configurations Switch To (FT8Call) | Pass | Pass |  |
| N08U | View menu (Upright) | N/A | N/A | No menu bar in Upright |
| N08P | View menu (PC) | Pass | Pass |  |
| N08G | View menu (Pages) | N/A | N/A | No menu bar in Pages (Rob, 2026-10-20: no ⋮ menu) |
| N08F | View menu (FT8Call) | Pass | Pass |  |
| N09U | Mode menu (Upright) | N/A | N/A | No menu bar in Upright |
| N09P | Mode menu (PC) | Pass | Pass |  |
| N09G | Mode menu (Pages) | N/A | N/A | No menu bar in Pages (Rob, 2026-10-20: no ⋮ menu) |
| N09F | Mode menu (FT8Call) | Pass | Pass |  |
| N10U | Control menu (Upright) | N/A | N/A | No menu bar in Upright |
| N10P | Control menu (PC) | Pass | Pass |  |
| N10G | Control menu (Pages) | N/A | N/A | No menu bar in Pages (Rob, 2026-10-20: no ⋮ menu) |
| N10F | Control menu (FT8Call) | Pass | Pass |  |
| N11U | Log menu (Upright) | N/A | N/A | No menu bar in Upright |
| N11P | Log menu (PC) | Pass | Pass |  |
| N11G | Log menu (Pages) | N/A | N/A | No menu bar in Pages (Rob, 2026-10-20: no ⋮ menu) |
| N11F | Log menu (FT8Call) | Pass | Pass | open check |
| N12U | Help menu (Upright) | N/A | N/A | No menu bar in Upright |
| N12P | Help menu (PC) | Pass | Pass |  |
| N12G | Help menu (Pages) | N/A | N/A | No menu bar in Pages (Rob, 2026-10-20: no ⋮ menu) |
| N12F | Help menu (FT8Call) | Pass | Pass |  |
| N13U | Show Fullscreen (Upright) | N/A | N/A | No menu bar in Upright |
| N13P | Show Fullscreen (PC) | Pass | Pass |  |
| N13G | Show Fullscreen (Pages) | N/A | N/A | No menu bar in Pages (Rob, 2026-10-20: no ⋮ menu) |
| N13F | Show Fullscreen (FT8Call) | Pass | Pass |  |
| N14U | Show Time Drift Controls... (Upright) | N/A | N/A | No menu bar in Upright |
| N14P | Show Time Drift Controls... (PC) | Pass | Pass |  |
| N14G | Show Time Drift Controls... (Pages) | N/A | N/A | No menu bar in Pages (Rob, 2026-10-20: no ⋮ menu) |
| N14F | Show Time Drift Controls... (FT8Call) | Pass | Pass |  |
| N15U | Open Log Directory... (Upright) | N/A | N/A | No menu bar in Upright |
| N15P | Open Log Directory... (PC) | Pass | Pass |  |
| N15G | Open Log Directory... (Pages) | N/A | N/A | No menu bar in Pages (Rob, 2026-10-20: no ⋮ menu) |
| N15F | Open Log Directory... (FT8Call) | Pass | Pass |  |
| N16U | Status bar configuration name (Upright) | N/A | N/A | No menu bar in Upright |
| N16P | Status bar configuration name (PC) | Pass | Pass |  |
| N16G | Status bar configuration name (Pages) | N/A | N/A | N/A: no menu bar in Pages |
| N16F | Status bar configuration name (FT8Call) | Pass | Pass |  |
