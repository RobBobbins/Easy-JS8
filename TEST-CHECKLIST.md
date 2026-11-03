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
| F07 | <MYVERSION> sends the plain version | Pass | Pass |  |
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
| G03 | ALL.TXT frequency-change line | Pass | Pass |  |
| G04 | ALL.TXT start-of-day header | Pass | Pass |  |
| G05 | DIRECTED.TXT line after whole message | Pass | Pass |  |
| G06 | Write log files unticked: nothing written | Pass | Pass |  |
| G07 | js8call_log.adi after Add to Log | Pass | Pass |  |
| G08 | js8call.log after Add to Log | Pass | Pass |  |
| G09 | Erase ALL.TXT | Pass | Pass |  |
| G10 | Erase js8call_log.adi | Pass | Pass |  |
| G11 | Export Log... all three files | Pass | Pass | ['← Back', 'Save', 'Cancel', 'Operation', 'Enable Simultaneous Decoding of All Speeds (MUL |

## O. Rig against the fake rigctld

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
| P01 | DX Lab stand-in read, set, PTT | Pass | Pass |  |
| P02 | Ham Radio Deluxe stand-in read, set, PTT | Pass | Pass |  |
| P03 | Hamlib model 2 NET | Pass | Pass |  |

## Q. API

| ID | Test | Run 1 | Run 2 | Note |
|---|---|---|---|---|
| Q01 | UDP PING | Pass | Pass |  |
| Q02 | UDP STATION.STATUS | Pass | Pass |  |
| Q03 | UDP RX.ACTIVITY | Pass | Pass |  |
| Q04 | UDP RX.DIRECTED | Pass | Pass |  |
| Q05 | RIG.PTT event | Pass | Pass |  |
| Q06 | TX.FRAME event | Pass | Pass |  |
| Q07 | RX.SPOT event | Pass | Pass | b'{"params":{"BITS":3,"DIAL":10130000,"FREQ":10130900,"OFFSET"' |
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
| S05 | Turn autoreply on at startup | Pass | Pass | no matching send:  |
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

## H. Main window

| ID | Test | Run 1 | Run 2 | Note |
|---|---|---|---|---|
| H01U | Header shows callsign and UTC clock (Upright) | Pass | Pass |  |
| H01P | Header shows callsign and UTC clock (PC) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H01G | Header shows callsign and UTC clock (Pages) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H01F | Header shows callsign and UTC clock (FT8Call) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H02U | Clock drift suffix (Upright) | Pass | Pass |  |
| H02P | Clock drift suffix (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| H02G | Clock drift suffix (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| H02F | Clock drift suffix (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| H03U | RX button (Upright) | Pass | Pass |  |
| H03P | RX button (PC) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H03G | RX button (Pages) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H03F | RX button (FT8Call) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H04U | TX button (Upright) | Pass | Pass |  |
| H04P | TX button (PC) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H04G | TX button (Pages) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H04F | TX button (FT8Call) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H05U | Mode button menu (Upright) | Pass | Pass |  |
| H05P | Mode button menu (PC) | Pass | Pass |  |
| H05G | Mode button menu (Pages) | Pass | Pass |  |
| H05F | Mode button menu (FT8Call) | Pass | Pass |  |
| H06U | Settings button (Upright) | Pass | Pass |  |
| H06P | Settings button (PC) | Pass | Pass |  |
| H06G | Settings button (Pages) | Pass | Pass |  |
| H06F | Settings button (FT8Call) | Pass | Pass |  |
| H07U | SPOT button (Upright) | Pass | Pass |  |
| H07P | SPOT button (PC) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H07G | SPOT button (Pages) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H07F | SPOT button (FT8Call) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H08U | LOG button opens Log QSO (Upright) | Pass | Pass |  |
| H08P | LOG button opens Log QSO (PC) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H08G | LOG button opens Log QSO (Pages) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H08F | LOG button opens Log QSO (FT8Call) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H09U | TUNE button (Upright) | Pass | Pass |  |
| H09P | TUNE button (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| H09G | TUNE button (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| H09F | TUNE button (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| H10U | HB press-and-hold menu (Upright) | Pass | Pass |  |
| H10P | HB press-and-hold menu (PC) | Pass | Pass |  |
| H10G | HB press-and-hold menu (Pages) | Pass | Pass |  |
| H10F | HB press-and-hold menu (FT8Call) | Pass | Pass |  |
| H11U | CQ press-and-hold menu (Upright) | Pass | Pass |  |
| H11P | CQ press-and-hold menu (PC) | Pass | Pass |  |
| H11G | CQ press-and-hold menu (Pages) | Pass | Pass |  |
| H11F | CQ press-and-hold menu (FT8Call) | Pass | Pass |  |
| H12U | REPLY button (Upright) | Pass | Pass |  |
| H12P | REPLY button (PC) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H12G | REPLY button (Pages) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H12F | REPLY button (FT8Call) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H13U | SNR button (Upright) | Pass | Pass |  |
| H13P | SNR button (PC) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H13G | SNR button (Pages) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H13F | SNR button (FT8Call) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H14U | INFO button (Upright) | Pass | Pass |  |
| H14P | INFO button (PC) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H14G | INFO button (Pages) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H14F | INFO button (FT8Call) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H15U | STATUS button (Upright) | Pass | Pass |  |
| H15P | STATUS button (PC) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H15G | STATUS button (Pages) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H15F | STATUS button (FT8Call) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H16U | Saved menu (Upright) | Pass | Pass |  |
| H16P | Saved menu (PC) | Pass | Pass |  |
| H16G | Saved menu (Pages) | Pass | Pass |  |
| H16F | Saved menu (FT8Call) | Pass | Pass |  |
| H17U | Saved > Edit Saved Messages (Upright) | Pass | Pass |  |
| H17P | Saved > Edit Saved Messages (PC) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H17G | Saved > Edit Saved Messages (Pages) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H17F | Saved > Edit Saved Messages (FT8Call) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H18U | Saved > APRS EMAIL (Upright) | Pass | Pass |  |
| H18P | Saved > APRS EMAIL (PC) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H18G | Saved > APRS EMAIL (Pages) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H18F | Saved > APRS EMAIL (FT8Call) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H19U | Saved > APRS TXT (Upright) | Pass | Pass |  |
| H19P | Saved > APRS TXT (PC) | Pass | Pass | see screenshot |
| H19G | Saved > APRS TXT (Pages) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H19F | Saved > APRS TXT (FT8Call) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H20U | Saved > APRS GRID (Upright) | Pass | Pass |  |
| H20P | Saved > APRS GRID (PC) | Pass | Pass | see screenshot |
| H20G | Saved > APRS GRID (Pages) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H20F | Saved > APRS GRID (FT8Call) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H21U | Saved > APRS MSG (Upright) | Pass | Pass |  |
| H21P | Saved > APRS MSG (PC) | Pass | Pass | see screenshot |
| H21G | Saved > APRS MSG (Pages) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H21F | Saved > APRS MSG (FT8Call) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H22U | Directed menu greying (Upright) | Pass | Pass |  |
| H22P | Directed menu greying (PC) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H22G | Directed menu greying (Pages) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H22F | Directed menu greying (FT8Call) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H23U | Deselect button (Upright) | Pass | Pass |  |
| H23P | Deselect button (PC) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H23G | Deselect button (Pages) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H23F | Deselect button (FT8Call) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H24U | Send button texts (Upright) | Pass | Pass |  |
| H24P | Send button texts (PC) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H24G | Send button texts (Pages) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H24F | Send button texts (FT8Call) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H25U | Halt button (Upright) | Pass | Pass |  |
| H25P | Halt button (PC) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H25G | Halt button (Pages) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H25F | Halt button (FT8Call) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H26U | Typing Box hint with a selection (Upright) | Pass | Pass |  |
| H26P | Typing Box hint with a selection (PC) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H26G | Typing Box hint with a selection (Pages) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H26F | Typing Box hint with a selection (FT8Call) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H27U | wpm box (Upright) | Pass | Pass |  |
| H27P | wpm box (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| H27G | wpm box (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| H27F | wpm box (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| H28U | Status bar labels (Upright) | Pass | Pass | corrected check |
| H28P | Status bar labels (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| H28G | Status bar labels (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| H28F | Status bar labels (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| H29U | Signal meter (Upright) | Pass | Pass |  |
| H29P | Signal meter (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| H29G | Signal meter (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| H29F | Signal meter (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| H30U | CAT button colours (Upright) | Pass | Pass |  |
| H30P | CAT button colours (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| H30G | CAT button colours (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| H30F | CAT button colours (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| H31U | Power slider (Upright) | N/A | N/A | no Power slider upright; Power is in the Settings window (L09U) |
| H31P | Power slider (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| H31G | Power slider (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| H31F | Power slider (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| H32U | Message Window scrolls (Upright) | Pass | Pass |  |
| H32P | Message Window scrolls (PC) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H32G | Message Window scrolls (Pages) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H32F | Message Window scrolls (FT8Call) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H33U | Message Window menu Cut Copy Paste (Upright) | Pass | Pass |  |
| H33P | Message Window menu Cut Copy Paste (PC) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H33G | Message Window menu Cut Copy Paste (Pages) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H33F | Message Window menu Cut Copy Paste (FT8Call) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H34U | Message Window Clear (Upright) | Pass | Pass |  |
| H34P | Message Window Clear (PC) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H34G | Message Window Clear (Pages) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H34F | Message Window Clear (FT8Call) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H35U | Double tap a word in the Message Window (Upright) | Pass | Pass |  |
| H35P | Double tap a word in the Message Window (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| H35G | Double tap a word in the Message Window (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| H35F | Double tap a word in the Message Window (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| H36U | Typing Box menu (Upright) | Pass | Pass |  |
| H36P | Typing Box menu (PC) | Pass | Pass | fixed: grab strips no longer cover the Typing Box |
| H36G | Typing Box menu (Pages) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H36F | Typing Box menu (FT8Call) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| H37U | Restore Previous Message fills the box (Upright) | Pass | Pass | corrected check |
| H37P | Restore Previous Message fills the box (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| H37G | Restore Previous Message fills the box (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| H37F | Restore Previous Message fills the box (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |

## I. Lists

| ID | Test | Run 1 | Run 2 | Note |
|---|---|---|---|---|
| I01U | Band Activity header tap sorts (Upright) | Pass | Pass |  |
| I01P | Band Activity header tap sorts (PC) | Pass | Pass |  |
| I01G | Band Activity header tap sorts (Pages) | Pass | Pass |  |
| I01F | Band Activity header tap sorts (FT8Call) | Pass | Pass |  |
| I02U | Band Activity press-and-hold columns (Upright) | Pass | Pass | see screenshot |
| I02P | Band Activity press-and-hold columns (PC) | Pass | Pass |  |
| I02G | Band Activity press-and-hold columns (Pages) | Pass | Pass |  |
| I02F | Band Activity press-and-hold columns (FT8Call) | Pass | Pass |  |
| I03U | Band Activity row menu (Upright) | Pass | Pass | see screenshot |
| I03P | Band Activity row menu (PC) | Pass | Pass | see screenshot |
| I03G | Band Activity row menu (Pages) | Pass | Pass | see screenshot |
| I03F | Band Activity row menu (FT8Call) | Pass | Pass | see screenshot |
| I04U | Band Activity double tap (Upright) | Pass | Pass |  |
| I04P | Band Activity double tap (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| I04G | Band Activity double tap (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| I04F | Band Activity double tap (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| I05U | Call Activity header tap sorts (Upright) | Pass | Pass |  |
| I05P | Call Activity header tap sorts (PC) | Pass | Pass |  |
| I05G | Call Activity header tap sorts (Pages) | Pass | Pass |  |
| I05F | Call Activity header tap sorts (FT8Call) | Pass | Pass |  |
| I06U | Call Activity press-and-hold columns (Upright) | Pass | Pass |  |
| I06P | Call Activity press-and-hold columns (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| I06G | Call Activity press-and-hold columns (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| I06F | Call Activity press-and-hold columns (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| I07U | Call Activity empty list menu (Upright) | Pass | Pass |  |
| I07P | Call Activity empty list menu (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| I07G | Call Activity empty list menu (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| I07F | Call Activity empty list menu (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| I08U | Call menu Jump to offset (Upright) | Pass | Pass | see screenshot |
| I08P | Call menu Jump to offset (PC) | Pass | Pass |  |
| I08G | Call menu Jump to offset (Pages) | Pass | Pass |  |
| I08F | Call menu Jump to offset (FT8Call) | Pass | Pass | see screenshot |
| I09U | Call menu Jump to speed (Upright) | Pass | Pass |  |
| I09P | Call menu Jump to speed (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| I09G | Call menu Jump to speed (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| I09F | Call menu Jump to speed (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| I10U | Call menu Jump to time drift (Upright) | Pass | Pass |  |
| I10P | Call menu Jump to time drift (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| I10G | Call menu Jump to time drift (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| I10F | Call menu Jump to time drift (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| I11U | Call menu Log... (Upright) | Pass | Pass | see screenshot |
| I11P | Call menu Log... (PC) | Pass | Pass |  |
| I11G | Call menu Log... (Pages) | Pass | Pass | see screenshot |
| I11F | Call menu Log... (FT8Call) | Pass | Pass | see screenshot |
| I12U | Call menu Show Message Inbox... (Upright) | Pass | Pass | see screenshot |
| I12P | Call menu Show Message Inbox... (PC) | Pass | Pass |  |
| I12G | Call menu Show Message Inbox... (Pages) | Pass | Pass |  |
| I12F | Call menu Show Message Inbox... (FT8Call) | Pass | Pass | see screenshot |
| I13U | Call menu Store Message... (Upright) | Pass | Pass | see screenshot |
| I13P | Call menu Store Message... (PC) | Pass | Pass |  |
| I13G | Call menu Store Message... (Pages) | Pass | Pass |  |
| I13F | Call menu Store Message... (FT8Call) | Pass | Pass | see screenshot |
| I14U | Call menu Saved Messages (Upright) | Pass | Pass | see screenshot |
| I14P | Call menu Saved Messages (PC) | Pass | Pass |  |
| I14G | Call menu Saved Messages (Pages) | Pass | Pass |  |
| I14F | Call menu Saved Messages (FT8Call) | Pass | Pass | see screenshot |
| I15U | Call menu Directed to CALL... (Upright) | Pass | Pass | see screenshot |
| I15P | Call menu Directed to CALL... (PC) | Pass | Pass | see screenshot |
| I15G | Call menu Directed to CALL... (Pages) | Pass | Pass |  |
| I15F | Call menu Directed to CALL... (FT8Call) | Pass | Pass | see screenshot |
| I16U | Call menu Relay via CALL... (Upright) | Pass | Pass | see screenshot |
| I16P | Call menu Relay via CALL... (PC) | Pass | Pass | see screenshot |
| I16G | Call menu Relay via CALL... (Pages) | Pass | Pass |  |
| I16F | Call menu Relay via CALL... (FT8Call) | Pass | Pass | see screenshot |
| I17U | Call menu Deselect (Upright) | Pass | Pass | see screenshot |
| I17P | Call menu Deselect (PC) | Pass | Pass | see screenshot |
| I17G | Call menu Deselect (Pages) | Pass | Pass |  |
| I17F | Call menu Deselect (FT8Call) | Pass | Pass | see screenshot |
| I18U | Call menu Add New Station or Group... (Upright) | Pass | Pass | see screenshot |
| I18P | Call menu Add New Station or Group... (PC) | Pass | Pass |  |
| I18G | Call menu Add New Station or Group... (Pages) | Pass | Pass |  |
| I18F | Call menu Add New Station or Group... (FT8Call) | Pass | Pass | see screenshot |
| I19U | Call menu Remove This Station/Group (Upright) | Pass | Pass | see screenshot |
| I19P | Call menu Remove This Station/Group (PC) | Pass | Pass | see screenshot |
| I19G | Call menu Remove This Station/Group (Pages) | Pass | Pass |  |
| I19F | Call menu Remove This Station/Group (FT8Call) | Pass | Pass | see screenshot |
| I20U | Call menu Block This Station (Upright) | Pass | Pass | see screenshot |
| I20P | Call menu Block This Station (PC) | Pass | Pass |  |
| I20G | Call menu Block This Station (Pages) | Pass | Pass | see screenshot |
| I20F | Call menu Block This Station (FT8Call) | Pass | Pass | see screenshot |
| I21U | Call menu Clear Entire List (Upright) | Pass | Pass | see screenshot |
| I21P | Call menu Clear Entire List (PC) | Pass | Pass |  |
| I21G | Call menu Clear Entire List (Pages) | Pass | Pass | see screenshot |
| I21F | Call menu Clear Entire List (FT8Call) | Pass | Pass | see screenshot |
| I22U | Call menu Clear All Lists asks first (Upright) | Pass | Pass |  |
| I22P | Call menu Clear All Lists asks first (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| I22G | Call menu Clear All Lists asks first (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| I22F | Call menu Clear All Lists asks first (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| I23U | Lists kept after restart (Upright) | Pass | Pass |  |
| I23P | Lists kept after restart (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| I23G | Lists kept after restart (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| I23F | Lists kept after restart (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| I24U | Line dividers drag (Upright) | Pass | Pass |  |
| I24P | Line dividers drag (PC) | Pass | Pass |  |
| I24G | Line dividers drag (Pages) | Pass | Pass |  |
| I24F | Line dividers drag (FT8Call) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| I25U | Reset Frame Sizes (Upright) | Pass | Pass |  |
| I25P | Reset Frame Sizes (PC) | Pass | Pass | View menu and Settings button both reset (Settings fixed 2026-11-03, ui12.py) |
| I25G | Reset Frame Sizes (Pages) | Pass | Pass |  |
| I25F | Reset Frame Sizes (FT8Call) | Pass | Pass | View menu and Settings button both reset (Settings fixed 2026-11-03, ui12.py) |

## J. Waterfall

| ID | Test | Run 1 | Run 2 | Note |
|---|---|---|---|---|
| J01U | Tap sets offset (Upright) | Pass | Pass |  |
| J01P | Tap sets offset (PC) | Pass | Pass | see screenshot |
| J01G | Tap sets offset (Pages) | Pass | Pass |  |
| J01F | Tap sets offset (FT8Call) | Pass | Pass | see screenshot |
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
| J04G | Control tab Offset arrows (Pages) | Pass | Pass | see screenshot |
| J04F | Control tab Offset arrows (FT8Call) | Pass | Pass | see screenshot |
| J05U | QSY (Upright) | Pass | Pass | see screenshot |
| J05P | QSY (PC) | Pass | Pass |  |
| J05G | QSY (Pages) | Pass | Pass | see screenshot |
| J05F | QSY (FT8Call) | Pass | Pass | see screenshot |
| J06U | Center arrows (Upright) | Pass | Pass | see screenshot |
| J06P | Center arrows (PC) | Pass | Pass |  |
| J06G | Center arrows (Pages) | Pass | Pass | see screenshot |
| J06F | Center arrows (FT8Call) | Pass | Pass | see screenshot |
| J07U | Enable Filter (Upright) | Pass | Pass | see screenshot |
| J07P | Enable Filter (PC) | Pass | Pass |  |
| J07G | Enable Filter (Pages) | Pass | Pass | see screenshot |
| J07F | Enable Filter (FT8Call) | Pass | Pass | see screenshot |
| J08U | Sync (Upright) | Pass | Pass |  |
| J08P | Sync (PC) | Pass | Pass |  |
| J08G | Sync (Pages) | Pass | Pass |  |
| J08F | Sync (FT8Call) | Pass | Pass |  |
| J09U | Filter Center and Width arrows (Upright) | Pass | Pass | see screenshot |
| J09P | Filter Center and Width arrows (PC) | Pass | Pass |  |
| J09G | Filter Center and Width arrows (Pages) | Pass | Pass | see screenshot |
| J09F | Filter Center and Width arrows (FT8Call) | Pass | Pass | see screenshot |
| J10U | Filter knobs (Upright) | Pass | Pass |  |
| J10P | Filter knobs (PC) | Pass | Pass |  |
| J10G | Filter knobs (Pages) | Pass | Pass |  |
| J10F | Filter knobs (FT8Call) | Pass | Pass |  |
| J11U | Timing tab Time Drift arrows (Upright) | Pass | Pass | see screenshot |
| J11P | Timing tab Time Drift arrows (PC) | Pass | Pass |  |
| J11G | Timing tab Time Drift arrows (Pages) | Pass | Pass | see screenshot |
| J11F | Timing tab Time Drift arrows (FT8Call) | Pass | Pass | see screenshot |
| J12U | Start Automatic Time Drift (Upright) | Pass | Pass | see screenshot |
| J12P | Start Automatic Time Drift (PC) | Pass | Pass |  |
| J12G | Start Automatic Time Drift (Pages) | Pass | Pass | see screenshot |
| J12F | Start Automatic Time Drift (FT8Call) | Pass | Pass | see screenshot |
| J13U | Stop After count (Upright) | Pass | Pass |  |
| J13P | Stop After count (PC) | Pass | Pass |  |
| J13G | Stop After count (Pages) | Pass | Pass |  |
| J13F | Stop After count (FT8Call) | Pass | Pass |  |
| J14U | Manual buttons (Upright) | Pass | Pass | see screenshot |
| J14P | Manual buttons (PC) | Pass | Pass | see screenshot |
| J14G | Manual buttons (Pages) | Pass | Pass | see screenshot |
| J14F | Manual buttons (FT8Call) | Pass | Pass | see screenshot |
| J15U | Display tab Start (Upright) | Pass | Pass | see screenshot |
| J15P | Display tab Start (PC) | Pass | Pass | see screenshot |
| J15G | Display tab Start (Pages) | Pass | Pass | see screenshot |
| J15F | Display tab Start (FT8Call) | Pass | Pass | see screenshot |
| J16U | Bins Per Pixel (Upright) | Pass | Pass | see screenshot |
| J16P | Bins Per Pixel (PC) | Pass | Pass | see screenshot |
| J16G | Bins Per Pixel (Pages) | Pass | Pass | see screenshot |
| J16F | Bins Per Pixel (FT8Call) | Pass | Pass | see screenshot |
| J17U | Palette list (Upright) | Pass | Pass | see screenshot |
| J17P | Palette list (PC) | Pass | Pass | see screenshot |
| J17G | Palette list (Pages) | Pass | Pass | see screenshot |
| J17F | Palette list (FT8Call) | Pass | Pass | see screenshot |
| J18U | Adjust... Palette Designer (Upright) | Pass | Pass | see screenshot |
| J18P | Adjust... Palette Designer (PC) | Pass | Pass | see screenshot |
| J18G | Adjust... Palette Designer (Pages) | Pass | Pass | see screenshot |
| J18F | Adjust... Palette Designer (FT8Call) | Pass | Pass | see screenshot |
| J19U | Scroll Speed (Upright) | Pass | Pass | see screenshot |
| J19P | Scroll Speed (PC) | Pass | Pass | see screenshot |
| J19G | Scroll Speed (Pages) | Pass | Pass | see screenshot |
| J19F | Scroll Speed (FT8Call) | Pass | Pass | see screenshot |
| J20U | Flatten off (Upright) | Pass | Pass | see screenshot |
| J20P | Flatten off (PC) | Pass | Pass | see screenshot |
| J20G | Flatten off (Pages) | Pass | Pass | see screenshot |
| J20F | Flatten off (FT8Call) | Pass | Pass | see screenshot |
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
| J24U | Avg N (Upright) | Pass | Pass | see screenshot |
| J24P | Avg N (PC) | Pass | Pass | see screenshot |
| J24G | Avg N (Pages) | Pass | Pass | see screenshot |
| J24F | Avg N (FT8Call) | Pass | Pass | see screenshot |
| J25U | Current list and Smooth (Upright) | Pass | Pass |  |
| J25P | Current list and Smooth (PC) | Pass | Pass |  |
| J25G | Current list and Smooth (Pages) | Pass | Pass |  |
| J25F | Current list and Smooth (FT8Call) | Pass | Pass |  |
| J26U | Display Decode Attempts (Upright) | Pass | Pass | see screenshot |
| J26P | Display Decode Attempts (PC) | Pass | Pass | see screenshot |
| J26G | Display Decode Attempts (Pages) | Pass | Pass | see screenshot |
| J26F | Display Decode Attempts (FT8Call) | Pass | Pass | see screenshot |
| J27U | Spectrum Height, Gain, Zero (Upright) | Pass | Pass | see screenshot |
| J27P | Spectrum Height, Gain, Zero (PC) | Pass | Pass | see screenshot |
| J27G | Spectrum Height, Gain, Zero (Pages) | Pass | Pass | see screenshot |
| J27F | Spectrum Height, Gain, Zero (FT8Call) | Pass | Pass | see screenshot |
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
| K01P | Inbox list newest first (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| K01G | Inbox list newest first (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| K01F | Inbox list newest first (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| K02U | Inbox multi-row Delete (Upright) | Pass | Pass |  |
| K02P | Inbox multi-row Delete (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| K02G | Inbox multi-row Delete (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| K02F | Inbox multi-row Delete (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| K03U | Inbox Reply (Upright) | Pass | Pass |  |
| K03P | Inbox Reply (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| K03G | Inbox Reply (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| K03F | Inbox Reply (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| K04U | Inbox filter by call (Upright) | Pass | Pass |  |
| K04P | Inbox filter by call (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| K04G | Inbox filter by call (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| K04F | Inbox filter by call (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| K05U | Log QSO fills from selection (Upright) | Pass | Pass | see screenshot |
| K05P | Log QSO fills from selection (PC) | Pass | Pass | see screenshot |
| K05G | Log QSO fills from selection (Pages) | Pass | Pass | see screenshot |
| K05F | Log QSO fills from selection (FT8Call) | Pass | Pass | see screenshot |
| K06U | Log QSO Now buttons (Upright) | Pass | Pass |  |
| K06P | Log QSO Now buttons (PC) | Pass | Pass |  |
| K06G | Log QSO Now buttons (Pages) | Pass | Pass | see screenshot |
| K06F | Log QSO Now buttons (FT8Call) | Pass | Pass | see screenshot |
| K07U | Log QSO Add New Field (Upright) | Pass | Pass | see screenshot |
| K07P | Log QSO Add New Field (PC) | Pass | Pass | see screenshot |
| K07G | Log QSO Add New Field (Pages) | Pass | Pass | see screenshot |
| K07F | Log QSO Add New Field (FT8Call) | Pass | Pass | see screenshot |
| K08U | Log QSO Retain Comments (Upright) | Pass | Pass | see screenshot |
| K08P | Log QSO Retain Comments (PC) | Pass | Pass | see screenshot |
| K08G | Log QSO Retain Comments (Pages) | Pass | Pass | see screenshot |
| K08F | Log QSO Retain Comments (FT8Call) | Pass | Pass | see screenshot |
| K09U | Log QSO word into focused field (Upright) | Pass | Pass |  |
| K09P | Log QSO word into focused field (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| K09G | Log QSO word into focused field (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| K09F | Log QSO word into focused field (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| K10U | Log QSO word into Comments (Upright) | Pass | Pass |  |
| K10P | Log QSO word into Comments (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| K10G | Log QSO word into Comments (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| K10F | Log QSO word into Comments (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| K11U | Log QSO Start/End word exclusion (Upright) | Pass | Pass |  |
| K11P | Log QSO Start/End word exclusion (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| K11G | Log QSO Start/End word exclusion (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| K11F | Log QSO Start/End word exclusion (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| K12U | Export Options checkboxes (Upright) | Pass | Pass |  |
| K12P | Export Options checkboxes (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| K12G | Export Options checkboxes (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| K12F | Export Options checkboxes (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |

## L. Settings window

| ID | Test | Run 1 | Run 2 | Note |
|---|---|---|---|---|
| L01U | MULTI (Upright) | Pass | Pass |  |
| L01P | MULTI (PC) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L01G | MULTI (Pages) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L01F | MULTI (FT8Call) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L02U | AUTO (Upright) | Pass | Pass |  |
| L02P | AUTO (PC) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L02G | AUTO (Pages) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L02F | AUTO (FT8Call) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L03U | HB (Upright) | Pass | Pass |  |
| L03P | HB (PC) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L03G | HB (Pages) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L03F | HB (FT8Call) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L04U | ACK greying (Upright) | Pass | Pass |  |
| L04P | ACK greying (PC) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L04G | ACK greying (Pages) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L04F | ACK greying (FT8Call) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L05U | Speed list (Upright) | Pass | Pass |  |
| L05P | Speed list (PC) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L05G | Speed list (Pages) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L05F | Speed list (FT8Call) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L06U | Frequency list (Upright) | Pass | Pass |  |
| L06P | Frequency list (PC) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L06G | Frequency list (Pages) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L06F | Frequency list (FT8Call) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L07U | Custom frequency (Upright) | Pass | Pass |  |
| L07P | Custom frequency (PC) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L07G | Custom frequency (Pages) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L07F | Custom frequency (FT8Call) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L08U | Offset box (Upright) | Pass | Pass |  |
| L08P | Offset box (PC) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L08G | Offset box (Pages) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L08F | Offset box (FT8Call) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L09U | Power (Upright) | Pass | Pass |  |
| L09P | Power (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| L09G | Power (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| L09F | Power (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| L10U | Show Band Heartbeats and ACKs (Upright) | Pass | Pass |  |
| L10P | Show Band Heartbeats and ACKs (PC) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L10G | Show Band Heartbeats and ACKs (Pages) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L10F | Show Band Heartbeats and ACKs (FT8Call) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L11U | Show Offset in Band Activity (Upright) | Pass | Pass |  |
| L11P | Show Offset in Band Activity (PC) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L11G | Show Offset in Band Activity (Pages) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L11F | Show Offset in Band Activity (FT8Call) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L12U | Show SNR in Band Activity (Upright) | Pass | Pass |  |
| L12P | Show SNR in Band Activity (PC) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L12G | Show SNR in Band Activity (Pages) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L12F | Show SNR in Band Activity (FT8Call) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L13U | Show Column Labels (Upright) | Pass | Pass |  |
| L13P | Show Column Labels (PC) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L13G | Show Column Labels (Pages) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L13F | Show Column Labels (FT8Call) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L14U | Show FT8 decodes (Upright) | Pass | Pass |  |
| L14P | Show FT8 decodes (PC) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L14G | Show FT8 decodes (Pages) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L14F | Show FT8 decodes (FT8Call) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L15U | Show panels (Upright) | Pass | Pass |  |
| L15P | Show panels (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| L15G | Show panels (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| L15F | Show panels (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| L16U | Clear Band Activity (Upright) | Pass | Pass |  |
| L16P | Clear Band Activity (PC) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L16G | Clear Band Activity (Pages) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L16F | Clear Band Activity (FT8Call) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L17U | Clear Call Activity (Upright) | Pass | Pass |  |
| L17P | Clear Call Activity (PC) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L17G | Clear Call Activity (Pages) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L17F | Clear Call Activity (FT8Call) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L18U | Clear RX Activity (Upright) | Pass | Pass |  |
| L18P | Clear RX Activity (PC) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L18G | Clear RX Activity (Pages) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L18F | Clear RX Activity (FT8Call) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L19U | Clear All Activity (Upright) | Pass | Pass |  |
| L19P | Clear All Activity (PC) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L19G | Clear All Activity (Pages) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L19F | Clear All Activity (FT8Call) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L20U | Configurations... (Upright) | Pass | Pass |  |
| L20P | Configurations... (PC) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L20G | Configurations... (Pages) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L20F | Configurations... (FT8Call) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L21U | Exit (Upright) | Pass | Pass |  |
| L21P | Exit (PC) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L21G | Exit (Pages) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L21F | Exit (FT8Call) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L22U | Copyright notice (Upright) | Pass | Pass |  |
| L22P | Copyright notice (PC) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L22G | Copyright notice (Pages) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L22F | Copyright notice (FT8Call) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L23U | Check for Updates (Upright) | Pass | Pass |  |
| L23P | Check for Updates (PC) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L23G | Check for Updates (Pages) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L23F | Check for Updates (FT8Call) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L24U | JS8Call User Guide (Upright) | Pass | Pass |  |
| L24P | JS8Call User Guide (PC) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L24G | JS8Call User Guide (Pages) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L24F | JS8Call User Guide (FT8Call) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L25U | Save (Upright) | Pass | Pass |  |
| L25P | Save (PC) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L25G | Save (Pages) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L25F | Save (FT8Call) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L26U | Cancel (Upright) | Pass | Pass |  |
| L26P | Cancel (PC) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L26G | Cancel (Pages) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L26F | Cancel (FT8Call) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| L27U | gear opens Station (Upright) | Pass | Pass |  |
| L27P | gear opens Station (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| L27G | gear opens Station (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| L27F | gear opens Station (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |

## M. Station window

| ID | Test | Run 1 | Run 2 | Note |
|---|---|---|---|---|
| M01U | General page opens (Upright) | Pass | Pass |  |
| M01P | General page opens (PC) | Pass | Pass |  |
| M01G | General page opens (Pages) | Pass | Pass |  |
| M01F | General page opens (FT8Call) | Pass | Pass |  |
| M02U | My Callsign refuses bad call (Upright) | Pass | Pass |  |
| M02P | My Callsign refuses bad call (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M02G | My Callsign refuses bad call (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M02F | My Callsign refuses bad call (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M03U | SWL (Listening only) (Upright) | Pass | Pass | OK leaves Station with no callsign when SWL is ticked |
| M03P | SWL (Listening only) (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M03G | SWL (Listening only) (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M03F | SWL (Listening only) (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M04U | Grid refuses bad grid (Upright) | Pass | Pass |  |
| M04P | Grid refuses bad grid (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M04G | Grid refuses bad grid (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M04F | Grid refuses bad grid (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M05U | Acquire GPS and Set (Upright) | Pass | Pass |  |
| M05P | Acquire GPS and Set (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M05G | Acquire GPS and Set (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M05F | Acquire GPS and Set (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M06U | Callsign Groups refuses bad group (Upright) | Pass | Pass |  |
| M06P | Callsign Groups refuses bad group (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M06G | Callsign Groups refuses bad group (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M06F | Callsign Groups refuses bad group (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M07U | CQ Message refuses bad format (Upright) | Pass | Pass |  |
| M07P | CQ Message refuses bad format (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M07G | CQ Message refuses bad format (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M07F | CQ Message refuses bad format (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M08U | Reply, Info, Status text (Upright) | Pass | Pass |  |
| M08P | Reply, Info, Status text (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M08G | Reply, Info, Status text (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M08F | Reply, Info, Status text (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M09U | Behavior checkboxes save (Upright) | Pass | Pass |  |
| M09P | Behavior checkboxes save (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M09G | Behavior checkboxes save (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M09F | Behavior checkboxes save (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M10U | Aging spin boxes (Upright) | Pass | Pass |  |
| M10P | Aging spin boxes (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M10G | Aging spin boxes (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M10F | Aging spin boxes (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M11U | End of Transmission Character Indicator refuses empty (Upright) | Pass | Pass |  |
| M11P | End of Transmission Character Indicator refuses empty (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M11G | End of Transmission Character Indicator refuses empty (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M11F | End of Transmission Character Indicator refuses empty (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M12U | Missing Frame Character Indicator (Upright) | Pass | Pass |  |
| M12P | Missing Frame Character Indicator (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M12G | Missing Frame Character Indicator (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M12F | Missing Frame Character Indicator (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M13U | Heartbeat interval (Upright) | Pass | Pass |  |
| M13P | Heartbeat interval (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M13G | Heartbeat interval (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M13F | Heartbeat interval (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M14U | Heartbeat Network group (Upright) | Pass | Pass |  |
| M14P | Heartbeat Network group (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M14G | Heartbeat Network group (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M14F | Heartbeat Network group (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M15U | Autoreply group (Upright) | Pass | Pass |  |
| M15P | Autoreply group (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M15G | Autoreply group (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M15F | Autoreply group (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M16U | Only autoreply refuses bad callsign (Upright) | Pass | Pass |  |
| M16P | Only autoreply refuses bad callsign (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M16G | Only autoreply refuses bad callsign (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M16F | Only autoreply refuses bad callsign (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M17U | Callsign Filtering (Upright) | Pass | Pass |  |
| M17P | Callsign Filtering (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M17G | Callsign Filtering (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M17F | Callsign Filtering (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M18U | Radio page Rig search (Upright) | Pass | Pass |  |
| M18P | Radio page Rig search (PC) | Pass | Pass |  |
| M18G | Radio page Rig search (Pages) | Pass | Pass |  |
| M18F | Radio page Rig search (FT8Call) | Pass | Pass |  |
| M19U | Radio page greying follows the rig (Upright) | Pass | Pass |  |
| M19P | Radio page greying follows the rig (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M19G | Radio page greying follows the rig (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M19F | Radio page greying follows the rig (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M20U | Test CAT (Upright) | Pass | Pass |  |
| M20P | Test CAT (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M20G | Test CAT (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M20F | Test CAT (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M21U | Test PTT (Upright) | Pass | Pass |  |
| M21P | Test PTT (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M21G | Test PTT (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M21F | Test PTT (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M22U | Split Operation (Upright) | Pass | Pass |  |
| M22P | Split Operation (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M22G | Split Operation (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M22F | Split Operation (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M23U | Tx delay (Upright) | Pass | Pass |  |
| M23P | Tx delay (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M23G | Tx delay (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M23F | Tx delay (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M24U | Hold PTT (Upright) | Pass | Pass |  |
| M24P | Hold PTT (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M24G | Hold PTT (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M24F | Hold PTT (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M25U | Audio page Input (Upright) | Pass | Pass |  |
| M25P | Audio page Input (PC) | Pass | Pass |  |
| M25G | Audio page Input (Pages) | Pass | Pass |  |
| M25F | Audio page Input (FT8Call) | Pass | Pass |  |
| M26U | Audio page Output (Upright) | Pass | Pass |  |
| M26P | Audio page Output (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M26G | Audio page Output (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M26F | Audio page Output (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M27U | Input channel list has no Both (Upright) | Pass | Pass |  |
| M27P | Input channel list has no Both (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M27G | Input channel list has no Both (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M27F | Input channel list has no Both (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M28U | Remember power settings by band (Upright) | Pass | Pass |  |
| M28P | Remember power settings by band (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M28G | Remember power settings by band (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M28F | Remember power settings by band (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M29U | Layout page (Upright) | N/A | N/A | Layout page is listed only sideways; open check in PC |
| M29P | Layout page (PC) | Pass | Pass |  |
| M29G | Layout page (Pages) | Pass | Pass |  |
| M29F | Layout page (FT8Call) | Pass | Pass |  |
| M30U | Reporting Network Services (Upright) | Pass | Pass |  |
| M30P | Reporting Network Services (PC) | Pass | Pass |  |
| M30G | Reporting Network Services (Pages) | Pass | Pass |  |
| M30F | Reporting Network Services (FT8Call) | Pass | Pass |  |
| M31U | Reporting API group (Upright) | Pass | Pass |  |
| M31P | Reporting API group (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M31G | Reporting API group (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M31F | Reporting API group (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M32U | Reporting N3FJP group (Upright) | Pass | Pass |  |
| M32P | Reporting N3FJP group (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M32G | Reporting N3FJP group (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M32F | Reporting N3FJP group (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M33U | Reporting N1MM group (Upright) | Pass | Pass |  |
| M33P | Reporting N1MM group (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M33G | Reporting N1MM group (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M33F | Reporting N1MM group (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M34U | Frequencies Calibration (Upright) | Pass | Pass |  |
| M34P | Frequencies Calibration (PC) | Pass | Pass |  |
| M34G | Frequencies Calibration (Pages) | Pass | Pass |  |
| M34F | Frequencies Calibration (FT8Call) | Pass | Pass |  |
| M35U | Frequency table Insert Delete Reset (Upright) | Pass | Pass |  |
| M35P | Frequency table Insert Delete Reset (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M35G | Frequency table Insert Delete Reset (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M35F | Frequency table Insert Delete Reset (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M36U | Frequency table edit in place (Upright) | Pass | Pass |  |
| M36P | Frequency table edit in place (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M36G | Frequency table edit in place (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M36F | Frequency table edit in place (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M37U | Frequency table Load Save as Merge (Upright) | Pass | Pass |  |
| M37P | Frequency table Load Save as Merge (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M37G | Frequency table Load Save as Merge (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M37F | Frequency table Load Save as Merge (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M38U | Frequency Schedule (Upright) | Pass | Pass |  |
| M38P | Frequency Schedule (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M38G | Frequency Schedule (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M38F | Frequency Schedule (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
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
| M42P | UI fonts (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M42G | UI fonts (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M42F | UI fonts (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
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
| M46P | OK (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M46G | OK (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M46F | OK (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M47U | Cancel (Upright) | Pass | Pass |  |
| M47P | Cancel (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M47G | Cancel (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| M47F | Cancel (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |

## N. Menus

| ID | Test | Run 1 | Run 2 | Note |
|---|---|---|---|---|
| N01U | File menu (Upright) | N/A | N/A | No menu bar in Upright |
| N01P | File menu (PC) | Pass | Pass |  |
| N01G | File menu (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| N01F | File menu (FT8Call) | Pass | Pass |  |
| N02U | Configurations Clone (Upright) | N/A | N/A | No menu bar in Upright |
| N02P | Configurations Clone (PC) | Pass | Pass |  |
| N02G | Configurations Clone (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| N02F | Configurations Clone (FT8Call) | Pass | Pass |  |
| N03U | Configurations Clone Into (Upright) | N/A | N/A | No menu bar in Upright |
| N03P | Configurations Clone Into (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| N03G | Configurations Clone Into (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| N03F | Configurations Clone Into (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| N04U | Configurations Rename (Upright) | N/A | N/A | No menu bar in Upright |
| N04P | Configurations Rename (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| N04G | Configurations Rename (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| N04F | Configurations Rename (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| N05U | Configurations Reset (Upright) | N/A | N/A | No menu bar in Upright |
| N05P | Configurations Reset (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| N05G | Configurations Reset (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| N05F | Configurations Reset (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| N06U | Configurations Delete (Upright) | N/A | N/A | No menu bar in Upright |
| N06P | Configurations Delete (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| N06G | Configurations Delete (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| N06F | Configurations Delete (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| N07U | Configurations Switch To (Upright) | N/A | N/A | No menu bar in Upright |
| N07P | Configurations Switch To (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| N07G | Configurations Switch To (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| N07F | Configurations Switch To (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| N08U | View menu (Upright) | N/A | N/A | No menu bar in Upright |
| N08P | View menu (PC) | Pass | Pass |  |
| N08G | View menu (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| N08F | View menu (FT8Call) | Pass | Pass |  |
| N09U | Mode menu (Upright) | N/A | N/A | No menu bar in Upright |
| N09P | Mode menu (PC) | Pass | Pass |  |
| N09G | Mode menu (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| N09F | Mode menu (FT8Call) | Pass | Pass |  |
| N10U | Control menu (Upright) | N/A | N/A | No menu bar in Upright |
| N10P | Control menu (PC) | Pass | Pass |  |
| N10G | Control menu (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| N10F | Control menu (FT8Call) | Pass | Pass |  |
| N11U | Log menu (Upright) | N/A | N/A | No menu bar in Upright |
| N11P | Log menu (PC) | Pass | Pass |  |
| N11G | Log menu (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| N11F | Log menu (FT8Call) | Pass | Pass | open check |
| N12U | Help menu (Upright) | N/A | N/A | No menu bar in Upright |
| N12P | Help menu (PC) | Pass | Pass |  |
| N12G | Help menu (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| N12F | Help menu (FT8Call) | Pass | Pass |  |
| N13U | Show Fullscreen (Upright) | N/A | N/A | No menu bar in Upright |
| N13P | Show Fullscreen (PC) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| N13G | Show Fullscreen (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| N13F | Show Fullscreen (FT8Call) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| N14U | Show Time Drift Controls... (Upright) | N/A | N/A | No menu bar in Upright |
| N14P | Show Time Drift Controls... (PC) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| N14G | Show Time Drift Controls... (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| N14F | Show Time Drift Controls... (FT8Call) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| N15U | Open Log Directory... (Upright) | N/A | N/A | No menu bar in Upright |
| N15P | Open Log Directory... (PC) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| N15G | Open Log Directory... (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| N15F | Open Log Directory... (FT8Call) | Pass | N/A | Upright only (TEST-PLAN One layout) |
| N16U | Status bar configuration name (Upright) | N/A | N/A | No menu bar in Upright |
| N16P | Status bar configuration name (PC) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| N16G | Status bar configuration name (Pages) | N/A | N/A | Upright only (TEST-PLAN One layout) |
| N16F | Status bar configuration name (FT8Call) | N/A | N/A | Upright only (TEST-PLAN One layout) |
