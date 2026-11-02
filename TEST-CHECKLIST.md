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
| A10 | Two-frame message joined in the Message Window |  |  |  |
| A11 | Band Activity row from a decode |  |  |  |
| A12 | Call Activity row from a decode |  |  |  |
| A13 | Low-confidence text shows in [ ] in Band Activity |  |  |  |
| A14 | Duplicate frame dropped |  |  |  |
| A15 | FT8 line shown (Show FT8 decodes ticked) |  |  |  |
| A16 | FT8 line not shown (unticked) |  |  |  |

## B. Directed commands and replies

| ID | Test | Run 1 | Run 2 | Note |
|---|---|---|---|---|
| B01 | SNR? answered | Pass | Pass |  |
| B02 | INFO? answered | Pass | Pass |  |
| B03 | STATUS? answered | Pass | Pass |  |
| B04 | GRID? answered | Pass | Pass |  |
| B05 | HEARING? answered | Pass | Pass |  |
| B06 | HEARING? with empty list still answers |  |  |  |
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
| B22 | Autoreply Confirmation No leaves a held message uncollected |  |  |  |
| B23 | Only autoreply to these callsigns | Pass | Pass |  |
| B24 | Never autoreply to these callsigns | Pass | Pass |  |
| B25 | No reply with text in the Typing Box | Pass | Pass |  |
| B26 | No reply while a message to me is arriving | Pass | Pass |  |
| B27 | Reply heard while transmitting goes out after the message |  |  |  |
| B28 | Idle timeout box appears and turns AUTO off |  |  |  |
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
| D04 | Relay heard while transmitting is queued |  |  |  |
| D05 | Relayed MSG reaches the Inbox | Pass | Pass |  |
| D06 | Relayed MSG TO: held | Pass | Pass |  |
| D07 | Relayed QUERY MSGS answered | Pass | Pass |  |
| D08 | Relayed QUERY CALL answered | Pass | Pass |  |
| D09 | MSG with a DE path: ACK follows the path | Pass | Pass |  |
| D10 | Relay to my @GROUP | Pass | Pass |  |
| D11 | Group message held 2 days and collected per station |  |  |  |
| D12 | Store Message... held for a station |  |  |  |
| D13 | HB ACK carries MSG ID | Pass | Pass |  |
| D14 | Group MSG ID on HB ACK |  |  |  |

## E. Heartbeat, CQ and transmit queue

| ID | Test | Run 1 | Run 2 | Note |
|---|---|---|---|---|
| E01 | HB button sends one heartbeat |  |  |  |
| E02 | HB timer sends after a full interval |  |  |  |
| E03 | CQ button sends one CQ |  |  |  |
| E04 | CQ timer sends after a full interval |  |  |  |
| E05 | Starting CQ timer stops HB timer |  |  |  |
| E06 | Starting HB timer stops CQ timer |  |  |  |
| E07 | Selecting a callsign stops HB and CQ timers |  |  |  |
| E08 | Deselect restores HB timer |  |  |  |
| E09 | Halt stops HB and CQ timers |  |  |  |
| E10 | CQ timer stops when a message is shown |  |  |  |
| E11 | HB ACK sent with AUTO, HB and ACK on | Pass | Pass |  |
| E12 | No HB ACK in JS8 40 | Pass | Pass |  |
| E13 | No HB ACK in JS8 60 | Pass | Pass |  |
| E14 | No HB ACK while a buffer is open | Pass | Pass |  |
| E15 | Pause heartbeat transmissions while in a QSO blocks ACK |  |  |  |
| E16 | Never acknowledge heartbeats list | Pass | Pass |  |
| E17 | Suppress ACK under 55 minutes | Pass | Pass |  |
| E18 | Heartbeat offset avoids busy offsets |  |  |  |
| E19 | Heartbeat uses my offset at or below 1000 Hz |  |  |  |
| E20 | Allow heartbeat transmissions outside of heartbeat sub-channel |  |  |  |
| E21 | Timed HB waits while transmitting |  |  |  |
| E22 | Reply priority ahead of a queued message |  |  |  |
| E23 | No double send when a reply queues between parts |  |  |  |
| E24 | Halt during a message |  |  |  |
| E25 | Halt during a heartbeat |  |  |  |
| E26 | Halt during TUNE |  |  |  |
| E27 | TUNE on and off |  |  |  |
| E28 | TX off stops sending and greys Send | Pass | Pass |  |
| E29 | RX button refused while transmitting |  |  |  |
| E30 | SWL blocks every transmit | Pass | Pass |  |
| E31 | 30 m WSPR guard band refused |  |  |  |
| E32 | Message Not Sent box after a stuck key |  |  |  |
| E33 | Send without callsign (Allow sending standard messages without callsign) |  |  |  |
| E34 | Typing ahead while sending |  |  |  |
| E35 | Part-by-part strike-out in the Typing Box |  |  |  |
| E36 | Last Tx label |  |  |  |
| E37 | All five speeds send: frame length correct |  |  |  |

## F. Macros

| ID | Test | Run 1 | Run 2 | Note |
|---|---|---|---|---|
| F01 | <MYCALL> | Pass | Pass |  |
| F02 | <MYGRID4> | Pass | Pass |  |
| F03 | <MYGRID12> | Pass | Pass |  |
| F04 | <MYINFO> | Pass | Pass |  |
| F05 | <MYSTATUS> |  |  |  |
| F06 | <MYIDLE> | Pass | Pass |  |
| F07 | <MYVERSION> sends the plain version | Pass | Pass |  |
| F08 | <CALL> |  |  |  |
| F09 | <SNR> |  |  |  |
| F10 | <TDELTA> |  |  |  |
| F11 | <MYHB> |  |  |  |
| F12 | Values upper-cased | Pass | Pass |  |
| F13 | Leftover <...> removed | Pass | Pass |  |

## G. Log files

| ID | Test | Run 1 | Run 2 | Note |
|---|---|---|---|---|
| G01 | ALL.TXT received lines | Pass | Pass |  |
| G02 | ALL.TXT transmitted lines | Pass | Pass |  |
| G03 | ALL.TXT frequency-change line | Pass | Pass |  |
| G04 | ALL.TXT start-of-day header |  |  |  |
| G05 | DIRECTED.TXT line after whole message | Pass | Pass |  |
| G06 | Write log files unticked: nothing written |  |  |  |
| G07 | js8call_log.adi after Add to Log |  |  |  |
| G08 | js8call.log after Add to Log |  |  |  |
| G09 | Erase ALL.TXT |  |  |  |
| G10 | Erase js8call_log.adi |  |  |  |
| G11 | Export Log... all three files |  |  |  |

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
| O08 | Tx delay lead time |  |  |  |
| O09 | Split Rig | Pass | Pass |  |
| O10 | Split Fake It | Pass | Pass |  |
| O11 | Poll Interval | Pass | Pass |  |
| O12 | Rig Control Error box | Failed | Failed | ['VE3TST', '2026 Nov 01 23:18:10'] |
| O13 | Rig reconnect after a drop |  |  |  |
| O14 | QSY moves Band Activity rows |  |  |  |
| O15 | Band change on a rig read |  |  |  |
| O16 | Remember power by band |  |  |  |

## P. Other rig stand-ins

| ID | Test | Run 1 | Run 2 | Note |
|---|---|---|---|---|
| P01 | DX Lab stand-in read, set, PTT | Pass | Pass |  |
| P02 | Ham Radio Deluxe stand-in read, set, PTT | Pass | Pass |  |
| P03 | Hamlib model 2 NET |  |  |  |

## Q. API

| ID | Test | Run 1 | Run 2 | Note |
|---|---|---|---|---|
| Q01 | UDP PING | Pass | Pass |  |
| Q02 | UDP STATION.STATUS | Pass | Pass |  |
| Q03 | UDP RX.ACTIVITY | Pass | Pass |  |
| Q04 | UDP RX.DIRECTED | Pass | Pass |  |
| Q05 | RIG.PTT event | Pass | Pass |  |
| Q06 | TX.FRAME event | Pass | Pass |  |
| Q07 | RX.SPOT event |  |  |  |
| Q08 | All TCP commands answered (44) | Pass | Pass |  |
| Q09 | Allow setting station information | Pass | Pass |  |
| Q10 | TCP Max Connections |  |  |  |
| Q11 | STATION.CLOSING |  |  |  |
| Q12 | WSJT-X Heartbeat |  |  |  |
| Q13 | WSJT-X Status |  |  |  |
| Q14 | WSJT-X Decode |  |  |  |
| Q15 | WSJT-X QSOLogged |  |  |  |
| Q16 | WSJT-X LoggedADIF |  |  |  |
| Q17 | N3FJP contact |  |  |  |
| Q18 | N1MM contact |  |  |  |

## R. Network stand-ins

| ID | Test | Run 1 | Run 2 | Note |
|---|---|---|---|---|
| R01 | APRS-IS login and spot | Failed | Failed | [] |
| R02 | APRS inbound relay | Failed | Failed | no relay sent |
| R03 | APRS ACK filter | Failed | Failed | ack line |
| R04 | APRS off with RX off |  |  |  |
| R05 | JS8NET RX.LOCAL | Pass | Pass |  |
| R06 | JS8NET RX.SPOT | Pass | Pass | 0 lines |
| R07 | JS8NET RX.DIRECTED to a group | Pass | Pass |  |
| R08 | JS8NET bad checksum dropped | Pass | Pass |  |
| R09 | Spot blacklist | Pass | Pass |  |
| R10 | PSK Reporter packet reaches the stand-in | Pass | Pass |  |
| R11 | PSK Reporter antenna info |  |  |  |
| R12 | PSK Reporter program name |  |  |  |
| R13 | Check for Updates finds a version |  |  |  |
| R14 | Failed update read says nothing |  |  |  |

## S. Startup and restart

| ID | Test | Run 1 | Run 2 | Note |
|---|---|---|---|---|
| S01 | Fresh install opens Station on General |  |  |  |
| S02 | No callsign: Station cannot be left |  |  |  |
| S03 | RX off at startup |  |  |  |
| S04 | TX off at startup |  |  |  |
| S05 | Turn autoreply on at startup |  |  |  |
| S06 | HB timer kept after restart |  |  |  |
| S07 | Reset activity at startup |  |  |  |
| S08 | Waterfall settings kept after restart |  |  |  |

## T. Against JS8Call on the PC

| ID | Test | Run 1 | Run 2 | Note |
|---|---|---|---|---|
| T01 | PC sends Normal, app decodes |  |  |  |
| T02 | PC sends Fast, app decodes |  |  |  |
| T03 | PC sends JS8 40, app decodes |  |  |  |
| T04 | PC sends Slow, app decodes |  |  |  |
| T05 | App sends Normal, PC decodes |  |  |  |
| T06 | App sends Fast, PC decodes |  |  |  |
| T07 | App sends JS8 40, PC decodes |  |  |  |
| T08 | App sends Slow, PC decodes |  |  |  |
| T09 | App sends JS8 60, PC decodes |  |  |  |
| T10 | App CQ reads the same on the PC |  |  |  |
| T11 | App heartbeat reads the same on the PC |  |  |  |
| T12 | App directed message reads the same on the PC |  |  |  |
| T13 | App multi-frame message with checksum accepted by the PC |  |  |  |
| T14 | PC multi-frame message with checksum accepted by the app |  |  |  |
| T15 | PC SNR? answered by the app |  |  |  |
| T16 | App SNR? answered by the PC |  |  |  |
| T17 | Compound callsign both ways |  |  |  |
| T18 | Group message both ways |  |  |  |

## U. Real networks (genuine traffic only)

| ID | Test | Run 1 | Run 2 | Note |
|---|---|---|---|---|
| U01 | Real APRS-IS login |  |  |  |
| U02 | Real APRS-IS @APRSIS GRID |  |  |  |
| U03 | Real JS8NET RX.LOCAL |  |  |  |

## H. Main window

| ID | Test | Run 1 | Run 2 | Note |
|---|---|---|---|---|
| H01U | Header shows callsign and UTC clock (Upright) | Pass |  |  |
| H01P | Header shows callsign and UTC clock (PC) | Pass |  |  |
| H01G | Header shows callsign and UTC clock (Pages) | Pass |  |  |
| H01F | Header shows callsign and UTC clock (FT8Call) | Pass |  |  |
| H02U | Clock drift suffix (Upright) |  |  |  |
| H02P | Clock drift suffix (PC) |  |  |  |
| H02G | Clock drift suffix (Pages) |  |  |  |
| H02F | Clock drift suffix (FT8Call) |  |  |  |
| H03U | RX button (Upright) | Pass |  |  |
| H03P | RX button (PC) | Pass |  |  |
| H03G | RX button (Pages) | Pass |  |  |
| H03F | RX button (FT8Call) | Pass |  |  |
| H04U | TX button (Upright) | Pass |  |  |
| H04P | TX button (PC) | Pass |  |  |
| H04G | TX button (Pages) | Pass |  |  |
| H04F | TX button (FT8Call) | Pass |  |  |
| H05U | Mode button menu (Upright) | Pass |  |  |
| H05P | Mode button menu (PC) | Pass |  |  |
| H05G | Mode button menu (Pages) | Pass |  |  |
| H05F | Mode button menu (FT8Call) | Pass |  |  |
| H06U | Settings button (Upright) | Pass |  |  |
| H06P | Settings button (PC) | Pass |  |  |
| H06G | Settings button (Pages) | Pass |  |  |
| H06F | Settings button (FT8Call) | Pass |  |  |
| H07U | SPOT button (Upright) | Pass |  |  |
| H07P | SPOT button (PC) | Pass |  |  |
| H07G | SPOT button (Pages) | Pass |  |  |
| H07F | SPOT button (FT8Call) | Pass |  |  |
| H08U | LOG button opens Log QSO (Upright) | Pass |  |  |
| H08P | LOG button opens Log QSO (PC) | Pass |  |  |
| H08G | LOG button opens Log QSO (Pages) | Pass |  |  |
| H08F | LOG button opens Log QSO (FT8Call) | Pass |  |  |
| H09U | TUNE button (Upright) |  |  |  |
| H09P | TUNE button (PC) |  |  |  |
| H09G | TUNE button (Pages) |  |  |  |
| H09F | TUNE button (FT8Call) |  |  |  |
| H10U | HB press-and-hold menu (Upright) | Pass |  |  |
| H10P | HB press-and-hold menu (PC) | Pass |  |  |
| H10G | HB press-and-hold menu (Pages) | Pass |  |  |
| H10F | HB press-and-hold menu (FT8Call) | Pass |  |  |
| H11U | CQ press-and-hold menu (Upright) | Pass |  |  |
| H11P | CQ press-and-hold menu (PC) | Pass |  |  |
| H11G | CQ press-and-hold menu (Pages) | Pass |  |  |
| H11F | CQ press-and-hold menu (FT8Call) | Pass |  |  |
| H12U | REPLY button (Upright) | Pass |  |  |
| H12P | REPLY button (PC) | Pass |  |  |
| H12G | REPLY button (Pages) | Pass |  |  |
| H12F | REPLY button (FT8Call) | Pass |  |  |
| H13U | SNR button (Upright) | Pass |  |  |
| H13P | SNR button (PC) | Pass |  |  |
| H13G | SNR button (Pages) | Pass |  |  |
| H13F | SNR button (FT8Call) | Pass |  |  |
| H14U | INFO button (Upright) | Pass |  |  |
| H14P | INFO button (PC) | Pass |  |  |
| H14G | INFO button (Pages) | Pass |  |  |
| H14F | INFO button (FT8Call) | Pass |  |  |
| H15U | STATUS button (Upright) | Pass |  |  |
| H15P | STATUS button (PC) | Pass |  |  |
| H15G | STATUS button (Pages) | Pass |  |  |
| H15F | STATUS button (FT8Call) | Pass |  |  |
| H16U | Saved menu (Upright) | Pass |  |  |
| H16P | Saved menu (PC) | Pass |  |  |
| H16G | Saved menu (Pages) | Pass |  |  |
| H16F | Saved menu (FT8Call) | Pass |  |  |
| H17U | Saved > Edit Saved Messages (Upright) | Pass |  |  |
| H17P | Saved > Edit Saved Messages (PC) | Pass |  |  |
| H17G | Saved > Edit Saved Messages (Pages) | Pass |  |  |
| H17F | Saved > Edit Saved Messages (FT8Call) | Pass |  |  |
| H18U | Saved > APRS EMAIL (Upright) | Pass |  |  |
| H18P | Saved > APRS EMAIL (PC) | Pass |  |  |
| H18G | Saved > APRS EMAIL (Pages) | Pass |  |  |
| H18F | Saved > APRS EMAIL (FT8Call) | Pass |  |  |
| H19U | Saved > APRS TXT (Upright) | Pass |  |  |
| H19P | Saved > APRS TXT (PC) | Failed |  | see screenshot |
| H19G | Saved > APRS TXT (Pages) | Pass |  |  |
| H19F | Saved > APRS TXT (FT8Call) | Pass |  |  |
| H20U | Saved > APRS GRID (Upright) | Pass |  |  |
| H20P | Saved > APRS GRID (PC) | Failed |  | see screenshot |
| H20G | Saved > APRS GRID (Pages) | Pass |  |  |
| H20F | Saved > APRS GRID (FT8Call) | Pass |  |  |
| H21U | Saved > APRS MSG (Upright) | Pass |  |  |
| H21P | Saved > APRS MSG (PC) | Failed |  | see screenshot |
| H21G | Saved > APRS MSG (Pages) | Pass |  |  |
| H21F | Saved > APRS MSG (FT8Call) | Pass |  |  |
| H22U | Directed menu greying (Upright) | Pass |  |  |
| H22P | Directed menu greying (PC) | Pass |  |  |
| H22G | Directed menu greying (Pages) | Pass |  |  |
| H22F | Directed menu greying (FT8Call) | Pass |  |  |
| H23U | Deselect button (Upright) | Pass |  |  |
| H23P | Deselect button (PC) | Pass |  |  |
| H23G | Deselect button (Pages) | Pass |  |  |
| H23F | Deselect button (FT8Call) | Pass |  |  |
| H24U | Send button texts (Upright) | Pass |  |  |
| H24P | Send button texts (PC) | Pass |  |  |
| H24G | Send button texts (Pages) | Pass |  |  |
| H24F | Send button texts (FT8Call) | Pass |  |  |
| H25U | Halt button (Upright) | Pass |  |  |
| H25P | Halt button (PC) | Pass |  |  |
| H25G | Halt button (Pages) | Pass |  |  |
| H25F | Halt button (FT8Call) | Pass |  |  |
| H26U | Typing Box hint with a selection (Upright) | Pass |  |  |
| H26P | Typing Box hint with a selection (PC) | Pass |  |  |
| H26G | Typing Box hint with a selection (Pages) | Pass |  |  |
| H26F | Typing Box hint with a selection (FT8Call) | Pass |  |  |
| H27U | wpm box (Upright) |  |  |  |
| H27P | wpm box (PC) |  |  |  |
| H27G | wpm box (Pages) |  |  |  |
| H27F | wpm box (FT8Call) |  |  |  |
| H28U | Status bar labels (Upright) |  |  |  |
| H28P | Status bar labels (PC) |  |  |  |
| H28G | Status bar labels (Pages) |  |  |  |
| H28F | Status bar labels (FT8Call) |  |  |  |
| H29U | Signal meter (Upright) |  |  |  |
| H29P | Signal meter (PC) |  |  |  |
| H29G | Signal meter (Pages) |  |  |  |
| H29F | Signal meter (FT8Call) |  |  |  |
| H30U | CAT button colours (Upright) |  |  |  |
| H30P | CAT button colours (PC) |  |  |  |
| H30G | CAT button colours (Pages) |  |  |  |
| H30F | CAT button colours (FT8Call) |  |  |  |
| H31U | Power slider (Upright) |  |  |  |
| H31P | Power slider (PC) |  |  |  |
| H31G | Power slider (Pages) |  |  |  |
| H31F | Power slider (FT8Call) |  |  |  |
| H32U | Message Window scrolls (Upright) | Pass |  |  |
| H32P | Message Window scrolls (PC) | Pass |  |  |
| H32G | Message Window scrolls (Pages) | Pass |  |  |
| H32F | Message Window scrolls (FT8Call) | Pass |  |  |
| H33U | Message Window menu Cut Copy Paste (Upright) | Pass |  |  |
| H33P | Message Window menu Cut Copy Paste (PC) | Pass |  |  |
| H33G | Message Window menu Cut Copy Paste (Pages) | Pass |  |  |
| H33F | Message Window menu Cut Copy Paste (FT8Call) | Pass |  |  |
| H34U | Message Window Clear (Upright) | Pass |  |  |
| H34P | Message Window Clear (PC) | Pass |  |  |
| H34G | Message Window Clear (Pages) | Pass |  |  |
| H34F | Message Window Clear (FT8Call) | Pass |  |  |
| H35U | Double tap a word in the Message Window (Upright) |  |  |  |
| H35P | Double tap a word in the Message Window (PC) |  |  |  |
| H35G | Double tap a word in the Message Window (Pages) |  |  |  |
| H35F | Double tap a word in the Message Window (FT8Call) |  |  |  |
| H36U | Typing Box menu (Upright) | Pass |  |  |
| H36P | Typing Box menu (PC) | Failed |  | see screenshot |
| H36G | Typing Box menu (Pages) | Pass |  |  |
| H36F | Typing Box menu (FT8Call) | Pass |  |  |
| H37U | Restore Previous Message fills the box (Upright) |  |  |  |
| H37P | Restore Previous Message fills the box (PC) |  |  |  |
| H37G | Restore Previous Message fills the box (Pages) |  |  |  |
| H37F | Restore Previous Message fills the box (FT8Call) |  |  |  |

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
| I04U | Band Activity double tap (Upright) |  |  |  |
| I04P | Band Activity double tap (PC) |  |  |  |
| I04G | Band Activity double tap (Pages) |  |  |  |
| I04F | Band Activity double tap (FT8Call) |  |  |  |
| I05U | Call Activity header tap sorts (Upright) | Pass | Pass |  |
| I05P | Call Activity header tap sorts (PC) | Pass | Pass |  |
| I05G | Call Activity header tap sorts (Pages) | Pass | Pass |  |
| I05F | Call Activity header tap sorts (FT8Call) | Pass | Pass |  |
| I06U | Call Activity press-and-hold columns (Upright) |  |  |  |
| I06P | Call Activity press-and-hold columns (PC) |  |  |  |
| I06G | Call Activity press-and-hold columns (Pages) |  |  |  |
| I06F | Call Activity press-and-hold columns (FT8Call) |  |  |  |
| I07U | Call Activity empty list menu (Upright) |  |  |  |
| I07P | Call Activity empty list menu (PC) |  |  |  |
| I07G | Call Activity empty list menu (Pages) |  |  |  |
| I07F | Call Activity empty list menu (FT8Call) |  |  |  |
| I08U | Call menu Jump to offset (Upright) | Pass | Pass | see screenshot |
| I08P | Call menu Jump to offset (PC) | Pass | Pass |  |
| I08G | Call menu Jump to offset (Pages) | Pass | Pass |  |
| I08F | Call menu Jump to offset (FT8Call) | Pass | Pass | see screenshot |
| I09U | Call menu Jump to speed (Upright) |  |  |  |
| I09P | Call menu Jump to speed (PC) |  |  |  |
| I09G | Call menu Jump to speed (Pages) |  |  |  |
| I09F | Call menu Jump to speed (FT8Call) |  |  |  |
| I10U | Call menu Jump to time drift (Upright) |  |  |  |
| I10P | Call menu Jump to time drift (PC) |  |  |  |
| I10G | Call menu Jump to time drift (Pages) |  |  |  |
| I10F | Call menu Jump to time drift (FT8Call) |  |  |  |
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
| I22U | Call menu Clear All Lists asks first (Upright) |  |  |  |
| I22P | Call menu Clear All Lists asks first (PC) |  |  |  |
| I22G | Call menu Clear All Lists asks first (Pages) |  |  |  |
| I22F | Call menu Clear All Lists asks first (FT8Call) |  |  |  |
| I23U | Lists kept after restart (Upright) |  |  |  |
| I23P | Lists kept after restart (PC) |  |  |  |
| I23G | Lists kept after restart (Pages) |  |  |  |
| I23F | Lists kept after restart (FT8Call) |  |  |  |
| I24U | Line dividers drag (Upright) |  |  |  |
| I24P | Line dividers drag (PC) |  |  |  |
| I24G | Line dividers drag (Pages) |  |  |  |
| I24F | Line dividers drag (FT8Call) |  |  |  |
| I25U | Reset Frame Sizes (Upright) |  |  |  |
| I25P | Reset Frame Sizes (PC) |  |  |  |
| I25G | Reset Frame Sizes (Pages) |  |  |  |
| I25F | Reset Frame Sizes (FT8Call) |  |  |  |

## J. Waterfall

| ID | Test | Run 1 | Run 2 | Note |
|---|---|---|---|---|
| J01U | Tap sets offset (Upright) | Pass | Pass |  |
| J01P | Tap sets offset (PC) | Pass | Pass | see screenshot |
| J01G | Tap sets offset (Pages) | Pass | Pass |  |
| J01F | Tap sets offset (FT8Call) | Pass | Pass | see screenshot |
| J02U | Scale tap sets offset (Upright) |  |  |  |
| J02P | Scale tap sets offset (PC) |  |  |  |
| J02G | Scale tap sets offset (Pages) |  |  |  |
| J02F | Scale tap sets offset (FT8Call) |  |  |  |
| J03U | Tap refused while a message is queued (Upright) |  |  |  |
| J03P | Tap refused while a message is queued (PC) |  |  |  |
| J03G | Tap refused while a message is queued (Pages) |  |  |  |
| J03F | Tap refused while a message is queued (FT8Call) |  |  |  |
| J04U | Control tab Offset arrows (Upright) | Failed | Failed | see screenshot |
| J04P | Control tab Offset arrows (PC) | Pass | Pass |  |
| J04G | Control tab Offset arrows (Pages) | Failed | Pass | see screenshot |
| J04F | Control tab Offset arrows (FT8Call) | Pass | Pass | see screenshot |
| J05U | QSY (Upright) | Pass | Pass | see screenshot |
| J05P | QSY (PC) | Pass | Pass |  |
| J05G | QSY (Pages) | Failed | Pass | see screenshot |
| J05F | QSY (FT8Call) | Pass | Pass | see screenshot |
| J06U | Center arrows (Upright) | Pass | Pass | see screenshot |
| J06P | Center arrows (PC) | Pass | Pass |  |
| J06G | Center arrows (Pages) | Failed | Pass | see screenshot |
| J06F | Center arrows (FT8Call) | Pass | Pass | see screenshot |
| J07U | Enable Filter (Upright) | Pass | Pass | see screenshot |
| J07P | Enable Filter (PC) | Pass | Pass |  |
| J07G | Enable Filter (Pages) | Failed | Pass | see screenshot |
| J07F | Enable Filter (FT8Call) | Pass | Pass | see screenshot |
| J08U | Sync (Upright) |  |  |  |
| J08P | Sync (PC) |  |  |  |
| J08G | Sync (Pages) |  |  |  |
| J08F | Sync (FT8Call) |  |  |  |
| J09U | Filter Center and Width arrows (Upright) | Pass | Pass | see screenshot |
| J09P | Filter Center and Width arrows (PC) | Pass | Pass |  |
| J09G | Filter Center and Width arrows (Pages) | Failed | Failed | see screenshot |
| J09F | Filter Center and Width arrows (FT8Call) | Pass | Pass | see screenshot |
| J10U | Filter knobs (Upright) |  |  |  |
| J10P | Filter knobs (PC) |  |  |  |
| J10G | Filter knobs (Pages) |  |  |  |
| J10F | Filter knobs (FT8Call) |  |  |  |
| J11U | Timing tab Time Drift arrows (Upright) | Pass | Pass | see screenshot |
| J11P | Timing tab Time Drift arrows (PC) | Pass | Pass |  |
| J11G | Timing tab Time Drift arrows (Pages) | Failed | Pass | see screenshot |
| J11F | Timing tab Time Drift arrows (FT8Call) | Pass | Pass | see screenshot |
| J12U | Start Automatic Time Drift (Upright) | Pass | Pass | see screenshot |
| J12P | Start Automatic Time Drift (PC) | Pass | Pass |  |
| J12G | Start Automatic Time Drift (Pages) | Failed | Pass | see screenshot |
| J12F | Start Automatic Time Drift (FT8Call) | Pass | Pass | see screenshot |
| J13U | Stop After count (Upright) |  |  |  |
| J13P | Stop After count (PC) |  |  |  |
| J13G | Stop After count (Pages) |  |  |  |
| J13F | Stop After count (FT8Call) |  |  |  |
| J14U | Manual buttons (Upright) | Pass | Pass | see screenshot |
| J14P | Manual buttons (PC) | Pass | Pass | see screenshot |
| J14G | Manual buttons (Pages) | Failed | Failed | see screenshot |
| J14F | Manual buttons (FT8Call) | Pass | Pass | see screenshot |
| J15U | Display tab Start (Upright) | Pass | Pass | see screenshot |
| J15P | Display tab Start (PC) | Failed | Failed | see screenshot |
| J15G | Display tab Start (Pages) | Failed | Pass | see screenshot |
| J15F | Display tab Start (FT8Call) | Failed | Pass | see screenshot |
| J16U | Bins Per Pixel (Upright) | Pass | Pass | see screenshot |
| J16P | Bins Per Pixel (PC) | Failed | Failed | see screenshot |
| J16G | Bins Per Pixel (Pages) | Failed | Pass | see screenshot |
| J16F | Bins Per Pixel (FT8Call) | Failed | Pass | see screenshot |
| J17U | Palette list (Upright) | Pass | Pass | see screenshot |
| J17P | Palette list (PC) | Failed | Pass | see screenshot |
| J17G | Palette list (Pages) | Failed | Pass | see screenshot |
| J17F | Palette list (FT8Call) | Failed | Pass | see screenshot |
| J18U | Adjust... Palette Designer (Upright) | Pass | Pass | see screenshot |
| J18P | Adjust... Palette Designer (PC) | Failed | Pass | see screenshot |
| J18G | Adjust... Palette Designer (Pages) | Failed | Pass | see screenshot |
| J18F | Adjust... Palette Designer (FT8Call) | Failed | Pass | see screenshot |
| J19U | Scroll Speed (Upright) | Pass | Pass | see screenshot |
| J19P | Scroll Speed (PC) | Failed | Pass | see screenshot |
| J19G | Scroll Speed (Pages) | Failed | Failed | see screenshot |
| J19F | Scroll Speed (FT8Call) | Failed | Pass | see screenshot |
| J20U | Flatten off (Upright) | Pass | Pass | see screenshot |
| J20P | Flatten off (PC) | Failed | Pass | see screenshot |
| J20G | Flatten off (Pages) | Failed | Failed | see screenshot |
| J20F | Flatten off (FT8Call) | Failed | Pass | see screenshot |
| J21U | Gain (Upright) |  |  |  |
| J21P | Gain (PC) |  |  |  |
| J21G | Gain (Pages) |  |  |  |
| J21F | Gain (FT8Call) |  |  |  |
| J22U | Zero (Upright) |  |  |  |
| J22P | Zero (PC) |  |  |  |
| J22G | Zero (Pages) |  |  |  |
| J22F | Zero (FT8Call) |  |  |  |
| J23U | Filter Opacity (Upright) |  |  |  |
| J23P | Filter Opacity (PC) |  |  |  |
| J23G | Filter Opacity (Pages) |  |  |  |
| J23F | Filter Opacity (FT8Call) |  |  |  |
| J24U | Avg N (Upright) | Pass | Pass | see screenshot |
| J24P | Avg N (PC) | Failed | Failed | see screenshot |
| J24G | Avg N (Pages) | Failed | Failed | see screenshot |
| J24F | Avg N (FT8Call) | Failed | Pass | see screenshot |
| J25U | Current list and Smooth (Upright) |  |  |  |
| J25P | Current list and Smooth (PC) |  |  |  |
| J25G | Current list and Smooth (Pages) |  |  |  |
| J25F | Current list and Smooth (FT8Call) |  |  |  |
| J26U | Display Decode Attempts (Upright) | Pass | Pass | see screenshot |
| J26P | Display Decode Attempts (PC) | Failed | Failed | see screenshot |
| J26G | Display Decode Attempts (Pages) | Failed | Failed | see screenshot |
| J26F | Display Decode Attempts (FT8Call) | Failed | Failed | see screenshot |
| J27U | Spectrum Height, Gain, Zero (Upright) | Pass | Pass | see screenshot |
| J27P | Spectrum Height, Gain, Zero (PC) | Failed | Failed | see screenshot |
| J27G | Spectrum Height, Gain, Zero (Pages) | Failed | Failed | see screenshot |
| J27F | Spectrum Height, Gain, Zero (FT8Call) | Failed | Failed | see screenshot |
| J28U | Green cycle line and text (Upright) |  |  |  |
| J28P | Green cycle line and text (PC) |  |  |  |
| J28G | Green cycle line and text (Pages) |  |  |  |
| J28F | Green cycle line and text (FT8Call) |  |  |  |
| J29U | Frequency scale colours (Upright) |  |  |  |
| J29P | Frequency scale colours (PC) |  |  |  |
| J29G | Frequency scale colours (Pages) |  |  |  |
| J29F | Frequency scale colours (FT8Call) |  |  |  |
| J30U | WSPR marker on 30 m (Upright) |  |  |  |
| J30P | WSPR marker on 30 m (PC) |  |  |  |
| J30G | WSPR marker on 30 m (Pages) |  |  |  |
| J30F | WSPR marker on 30 m (FT8Call) |  |  |  |

## K. Inbox, Log QSO, Export

| ID | Test | Run 1 | Run 2 | Note |
|---|---|---|---|---|
| K01U | Inbox list newest first (Upright) |  |  |  |
| K01P | Inbox list newest first (PC) |  |  |  |
| K01G | Inbox list newest first (Pages) |  |  |  |
| K01F | Inbox list newest first (FT8Call) |  |  |  |
| K02U | Inbox multi-row Delete (Upright) |  |  |  |
| K02P | Inbox multi-row Delete (PC) |  |  |  |
| K02G | Inbox multi-row Delete (Pages) |  |  |  |
| K02F | Inbox multi-row Delete (FT8Call) |  |  |  |
| K03U | Inbox Reply (Upright) |  |  |  |
| K03P | Inbox Reply (PC) |  |  |  |
| K03G | Inbox Reply (Pages) |  |  |  |
| K03F | Inbox Reply (FT8Call) |  |  |  |
| K04U | Inbox filter by call (Upright) |  |  |  |
| K04P | Inbox filter by call (PC) |  |  |  |
| K04G | Inbox filter by call (Pages) |  |  |  |
| K04F | Inbox filter by call (FT8Call) |  |  |  |
| K05U | Log QSO fills from selection (Upright) | Pass | Pass | see screenshot |
| K05P | Log QSO fills from selection (PC) | Pass | Pass | see screenshot |
| K05G | Log QSO fills from selection (Pages) | Failed | Pass | see screenshot |
| K05F | Log QSO fills from selection (FT8Call) | Pass | Pass | see screenshot |
| K06U | Log QSO Now buttons (Upright) | Pass | Pass |  |
| K06P | Log QSO Now buttons (PC) | Pass | Pass |  |
| K06G | Log QSO Now buttons (Pages) | Failed | Pass | see screenshot |
| K06F | Log QSO Now buttons (FT8Call) | Pass | Pass | see screenshot |
| K07U | Log QSO Add New Field (Upright) | Pass | Pass | see screenshot |
| K07P | Log QSO Add New Field (PC) | Pass | Pass | see screenshot |
| K07G | Log QSO Add New Field (Pages) | Failed | Pass | see screenshot |
| K07F | Log QSO Add New Field (FT8Call) | Pass | Pass | see screenshot |
| K08U | Log QSO Retain Comments (Upright) | Pass | Pass | see screenshot |
| K08P | Log QSO Retain Comments (PC) | Pass | Pass | see screenshot |
| K08G | Log QSO Retain Comments (Pages) | Failed | Pass | see screenshot |
| K08F | Log QSO Retain Comments (FT8Call) | Pass | Pass | see screenshot |
| K09U | Log QSO word into focused field (Upright) |  |  |  |
| K09P | Log QSO word into focused field (PC) |  |  |  |
| K09G | Log QSO word into focused field (Pages) |  |  |  |
| K09F | Log QSO word into focused field (FT8Call) |  |  |  |
| K10U | Log QSO word into Comments (Upright) |  |  |  |
| K10P | Log QSO word into Comments (PC) |  |  |  |
| K10G | Log QSO word into Comments (Pages) |  |  |  |
| K10F | Log QSO word into Comments (FT8Call) |  |  |  |
| K11U | Log QSO Start/End word exclusion (Upright) |  |  |  |
| K11P | Log QSO Start/End word exclusion (PC) |  |  |  |
| K11G | Log QSO Start/End word exclusion (Pages) |  |  |  |
| K11F | Log QSO Start/End word exclusion (FT8Call) |  |  |  |
| K12U | Export Options checkboxes (Upright) |  |  |  |
| K12P | Export Options checkboxes (PC) |  |  |  |
| K12G | Export Options checkboxes (Pages) |  |  |  |
| K12F | Export Options checkboxes (FT8Call) |  |  |  |

## L. Settings window

| ID | Test | Run 1 | Run 2 | Note |
|---|---|---|---|---|
| L01U | MULTI (Upright) | Pass |  |  |
| L01P | MULTI (PC) | Pass |  |  |
| L01G | MULTI (Pages) | Pass |  |  |
| L01F | MULTI (FT8Call) | Pass |  |  |
| L02U | AUTO (Upright) | Pass |  |  |
| L02P | AUTO (PC) | Pass |  |  |
| L02G | AUTO (Pages) | Pass |  |  |
| L02F | AUTO (FT8Call) | Pass |  |  |
| L03U | HB (Upright) | Pass |  |  |
| L03P | HB (PC) | Pass |  |  |
| L03G | HB (Pages) | Pass |  |  |
| L03F | HB (FT8Call) | Pass |  |  |
| L04U | ACK greying (Upright) | Pass |  |  |
| L04P | ACK greying (PC) | Pass |  |  |
| L04G | ACK greying (Pages) | Pass |  |  |
| L04F | ACK greying (FT8Call) | Pass |  |  |
| L05U | Speed list (Upright) | Pass |  |  |
| L05P | Speed list (PC) | Pass |  |  |
| L05G | Speed list (Pages) | Pass |  |  |
| L05F | Speed list (FT8Call) | Pass |  |  |
| L06U | Frequency list (Upright) | Pass |  |  |
| L06P | Frequency list (PC) | Pass |  |  |
| L06G | Frequency list (Pages) | Pass |  |  |
| L06F | Frequency list (FT8Call) | Pass |  |  |
| L07U | Custom frequency (Upright) | Pass |  |  |
| L07P | Custom frequency (PC) | Pass |  |  |
| L07G | Custom frequency (Pages) | Pass |  |  |
| L07F | Custom frequency (FT8Call) | Pass |  |  |
| L08U | Offset box (Upright) | Pass |  |  |
| L08P | Offset box (PC) | Pass |  |  |
| L08G | Offset box (Pages) | Pass |  |  |
| L08F | Offset box (FT8Call) | Pass |  |  |
| L09U | Power (Upright) |  |  |  |
| L09P | Power (PC) |  |  |  |
| L09G | Power (Pages) |  |  |  |
| L09F | Power (FT8Call) |  |  |  |
| L10U | Show Band Heartbeats and ACKs (Upright) | Pass |  |  |
| L10P | Show Band Heartbeats and ACKs (PC) | Pass |  |  |
| L10G | Show Band Heartbeats and ACKs (Pages) | Pass |  |  |
| L10F | Show Band Heartbeats and ACKs (FT8Call) | Pass |  |  |
| L11U | Show Offset in Band Activity (Upright) | Pass |  |  |
| L11P | Show Offset in Band Activity (PC) | Pass |  |  |
| L11G | Show Offset in Band Activity (Pages) | Pass |  |  |
| L11F | Show Offset in Band Activity (FT8Call) | Pass |  |  |
| L12U | Show SNR in Band Activity (Upright) | Pass |  |  |
| L12P | Show SNR in Band Activity (PC) | Pass |  |  |
| L12G | Show SNR in Band Activity (Pages) | Pass |  |  |
| L12F | Show SNR in Band Activity (FT8Call) | Pass |  |  |
| L13U | Show Column Labels (Upright) | Pass |  |  |
| L13P | Show Column Labels (PC) | Pass |  |  |
| L13G | Show Column Labels (Pages) | Pass |  |  |
| L13F | Show Column Labels (FT8Call) | Pass |  |  |
| L14U | Show FT8 decodes (Upright) | Pass |  |  |
| L14P | Show FT8 decodes (PC) | Pass |  |  |
| L14G | Show FT8 decodes (Pages) | Pass |  |  |
| L14F | Show FT8 decodes (FT8Call) | Pass |  |  |
| L15U | Show panels (Upright) |  |  |  |
| L15P | Show panels (PC) |  |  |  |
| L15G | Show panels (Pages) |  |  |  |
| L15F | Show panels (FT8Call) |  |  |  |
| L16U | Clear Band Activity (Upright) | Pass |  |  |
| L16P | Clear Band Activity (PC) | Pass |  |  |
| L16G | Clear Band Activity (Pages) | Pass |  |  |
| L16F | Clear Band Activity (FT8Call) | Pass |  |  |
| L17U | Clear Call Activity (Upright) | Pass |  |  |
| L17P | Clear Call Activity (PC) | Pass |  |  |
| L17G | Clear Call Activity (Pages) | Pass |  |  |
| L17F | Clear Call Activity (FT8Call) | Pass |  |  |
| L18U | Clear RX Activity (Upright) | Pass |  |  |
| L18P | Clear RX Activity (PC) | Pass |  |  |
| L18G | Clear RX Activity (Pages) | Pass |  |  |
| L18F | Clear RX Activity (FT8Call) | Pass |  |  |
| L19U | Clear All Activity (Upright) | Pass |  |  |
| L19P | Clear All Activity (PC) | Pass |  |  |
| L19G | Clear All Activity (Pages) | Pass |  |  |
| L19F | Clear All Activity (FT8Call) | Pass |  |  |
| L20U | Configurations... (Upright) | Pass |  |  |
| L20P | Configurations... (PC) | Pass |  |  |
| L20G | Configurations... (Pages) | Pass |  |  |
| L20F | Configurations... (FT8Call) | Pass |  |  |
| L21U | Exit (Upright) | Pass |  |  |
| L21P | Exit (PC) | Pass |  |  |
| L21G | Exit (Pages) | Pass |  |  |
| L21F | Exit (FT8Call) | Pass |  |  |
| L22U | Copyright notice (Upright) | Pass |  |  |
| L22P | Copyright notice (PC) | Pass |  |  |
| L22G | Copyright notice (Pages) | Pass |  |  |
| L22F | Copyright notice (FT8Call) | Pass |  |  |
| L23U | Check for Updates (Upright) | Pass |  |  |
| L23P | Check for Updates (PC) | Pass |  |  |
| L23G | Check for Updates (Pages) | Pass |  |  |
| L23F | Check for Updates (FT8Call) | Pass |  |  |
| L24U | JS8Call User Guide (Upright) | Pass |  |  |
| L24P | JS8Call User Guide (PC) | Pass |  |  |
| L24G | JS8Call User Guide (Pages) | Pass |  |  |
| L24F | JS8Call User Guide (FT8Call) | Pass |  |  |
| L25U | Save (Upright) | Pass |  |  |
| L25P | Save (PC) | Pass |  |  |
| L25G | Save (Pages) | Pass |  |  |
| L25F | Save (FT8Call) | Pass |  |  |
| L26U | Cancel (Upright) | Pass |  |  |
| L26P | Cancel (PC) | Pass |  |  |
| L26G | Cancel (Pages) | Pass |  |  |
| L26F | Cancel (FT8Call) | Pass |  |  |
| L27U | gear opens Station (Upright) |  |  |  |
| L27P | gear opens Station (PC) |  |  |  |
| L27G | gear opens Station (Pages) |  |  |  |
| L27F | gear opens Station (FT8Call) |  |  |  |

## M. Station window

| ID | Test | Run 1 | Run 2 | Note |
|---|---|---|---|---|
| M01U | General page opens (Upright) | Pass |  |  |
| M01P | General page opens (PC) | Pass |  |  |
| M01G | General page opens (Pages) | Pass |  |  |
| M01F | General page opens (FT8Call) | Pass |  |  |
| M02U | My Callsign refuses bad call (Upright) |  |  |  |
| M02P | My Callsign refuses bad call (PC) |  |  |  |
| M02G | My Callsign refuses bad call (Pages) |  |  |  |
| M02F | My Callsign refuses bad call (FT8Call) |  |  |  |
| M03U | SWL (Listening only) (Upright) |  |  |  |
| M03P | SWL (Listening only) (PC) |  |  |  |
| M03G | SWL (Listening only) (Pages) |  |  |  |
| M03F | SWL (Listening only) (FT8Call) |  |  |  |
| M04U | Grid refuses bad grid (Upright) |  |  |  |
| M04P | Grid refuses bad grid (PC) |  |  |  |
| M04G | Grid refuses bad grid (Pages) |  |  |  |
| M04F | Grid refuses bad grid (FT8Call) |  |  |  |
| M05U | Acquire GPS and Set (Upright) |  |  |  |
| M05P | Acquire GPS and Set (PC) |  |  |  |
| M05G | Acquire GPS and Set (Pages) |  |  |  |
| M05F | Acquire GPS and Set (FT8Call) |  |  |  |
| M06U | Callsign Groups refuses bad group (Upright) |  |  |  |
| M06P | Callsign Groups refuses bad group (PC) |  |  |  |
| M06G | Callsign Groups refuses bad group (Pages) |  |  |  |
| M06F | Callsign Groups refuses bad group (FT8Call) |  |  |  |
| M07U | CQ Message refuses bad format (Upright) |  |  |  |
| M07P | CQ Message refuses bad format (PC) |  |  |  |
| M07G | CQ Message refuses bad format (Pages) |  |  |  |
| M07F | CQ Message refuses bad format (FT8Call) |  |  |  |
| M08U | Reply, Info, Status text (Upright) |  |  |  |
| M08P | Reply, Info, Status text (PC) |  |  |  |
| M08G | Reply, Info, Status text (Pages) |  |  |  |
| M08F | Reply, Info, Status text (FT8Call) |  |  |  |
| M09U | Behavior checkboxes save (Upright) |  |  |  |
| M09P | Behavior checkboxes save (PC) |  |  |  |
| M09G | Behavior checkboxes save (Pages) |  |  |  |
| M09F | Behavior checkboxes save (FT8Call) |  |  |  |
| M10U | Aging spin boxes (Upright) |  |  |  |
| M10P | Aging spin boxes (PC) |  |  |  |
| M10G | Aging spin boxes (Pages) |  |  |  |
| M10F | Aging spin boxes (FT8Call) |  |  |  |
| M11U | End of Transmission Character Indicator refuses empty (Upright) |  |  |  |
| M11P | End of Transmission Character Indicator refuses empty (PC) |  |  |  |
| M11G | End of Transmission Character Indicator refuses empty (Pages) |  |  |  |
| M11F | End of Transmission Character Indicator refuses empty (FT8Call) |  |  |  |
| M12U | Missing Frame Character Indicator (Upright) |  |  |  |
| M12P | Missing Frame Character Indicator (PC) |  |  |  |
| M12G | Missing Frame Character Indicator (Pages) |  |  |  |
| M12F | Missing Frame Character Indicator (FT8Call) |  |  |  |
| M13U | Heartbeat interval (Upright) |  |  |  |
| M13P | Heartbeat interval (PC) |  |  |  |
| M13G | Heartbeat interval (Pages) |  |  |  |
| M13F | Heartbeat interval (FT8Call) |  |  |  |
| M14U | Heartbeat Network group (Upright) |  |  |  |
| M14P | Heartbeat Network group (PC) |  |  |  |
| M14G | Heartbeat Network group (Pages) |  |  |  |
| M14F | Heartbeat Network group (FT8Call) |  |  |  |
| M15U | Autoreply group (Upright) |  |  |  |
| M15P | Autoreply group (PC) |  |  |  |
| M15G | Autoreply group (Pages) |  |  |  |
| M15F | Autoreply group (FT8Call) |  |  |  |
| M16U | Only autoreply refuses bad callsign (Upright) |  |  |  |
| M16P | Only autoreply refuses bad callsign (PC) |  |  |  |
| M16G | Only autoreply refuses bad callsign (Pages) |  |  |  |
| M16F | Only autoreply refuses bad callsign (FT8Call) |  |  |  |
| M17U | Callsign Filtering (Upright) |  |  |  |
| M17P | Callsign Filtering (PC) |  |  |  |
| M17G | Callsign Filtering (Pages) |  |  |  |
| M17F | Callsign Filtering (FT8Call) |  |  |  |
| M18U | Radio page Rig search (Upright) | Pass |  |  |
| M18P | Radio page Rig search (PC) | Pass |  |  |
| M18G | Radio page Rig search (Pages) | Pass |  |  |
| M18F | Radio page Rig search (FT8Call) | Pass |  |  |
| M19U | Radio page greying follows the rig (Upright) |  |  |  |
| M19P | Radio page greying follows the rig (PC) |  |  |  |
| M19G | Radio page greying follows the rig (Pages) |  |  |  |
| M19F | Radio page greying follows the rig (FT8Call) |  |  |  |
| M20U | Test CAT (Upright) |  |  |  |
| M20P | Test CAT (PC) |  |  |  |
| M20G | Test CAT (Pages) |  |  |  |
| M20F | Test CAT (FT8Call) |  |  |  |
| M21U | Test PTT (Upright) |  |  |  |
| M21P | Test PTT (PC) |  |  |  |
| M21G | Test PTT (Pages) |  |  |  |
| M21F | Test PTT (FT8Call) |  |  |  |
| M22U | Split Operation (Upright) |  |  |  |
| M22P | Split Operation (PC) |  |  |  |
| M22G | Split Operation (Pages) |  |  |  |
| M22F | Split Operation (FT8Call) |  |  |  |
| M23U | Tx delay (Upright) |  |  |  |
| M23P | Tx delay (PC) |  |  |  |
| M23G | Tx delay (Pages) |  |  |  |
| M23F | Tx delay (FT8Call) |  |  |  |
| M24U | Hold PTT (Upright) |  |  |  |
| M24P | Hold PTT (PC) |  |  |  |
| M24G | Hold PTT (Pages) |  |  |  |
| M24F | Hold PTT (FT8Call) |  |  |  |
| M25U | Audio page Input (Upright) | Pass |  |  |
| M25P | Audio page Input (PC) | Pass |  |  |
| M25G | Audio page Input (Pages) | Pass |  |  |
| M25F | Audio page Input (FT8Call) | Pass |  |  |
| M26U | Audio page Output (Upright) |  |  |  |
| M26P | Audio page Output (PC) |  |  |  |
| M26G | Audio page Output (Pages) |  |  |  |
| M26F | Audio page Output (FT8Call) |  |  |  |
| M27U | Input channel list has no Both (Upright) |  |  |  |
| M27P | Input channel list has no Both (PC) |  |  |  |
| M27G | Input channel list has no Both (Pages) |  |  |  |
| M27F | Input channel list has no Both (FT8Call) |  |  |  |
| M28U | Remember power settings by band (Upright) |  |  |  |
| M28P | Remember power settings by band (PC) |  |  |  |
| M28G | Remember power settings by band (Pages) |  |  |  |
| M28F | Remember power settings by band (FT8Call) |  |  |  |
| M29U | Layout page (Upright) |  |  |  |
| M29P | Layout page (PC) |  |  |  |
| M29G | Layout page (Pages) |  |  |  |
| M29F | Layout page (FT8Call) |  |  |  |
| M30U | Reporting Network Services (Upright) | Pass |  |  |
| M30P | Reporting Network Services (PC) | Pass |  |  |
| M30G | Reporting Network Services (Pages) | Pass |  |  |
| M30F | Reporting Network Services (FT8Call) | Pass |  |  |
| M31U | Reporting API group (Upright) |  |  |  |
| M31P | Reporting API group (PC) |  |  |  |
| M31G | Reporting API group (Pages) |  |  |  |
| M31F | Reporting API group (FT8Call) |  |  |  |
| M32U | Reporting N3FJP group (Upright) |  |  |  |
| M32P | Reporting N3FJP group (PC) |  |  |  |
| M32G | Reporting N3FJP group (Pages) |  |  |  |
| M32F | Reporting N3FJP group (FT8Call) |  |  |  |
| M33U | Reporting N1MM group (Upright) |  |  |  |
| M33P | Reporting N1MM group (PC) |  |  |  |
| M33G | Reporting N1MM group (Pages) |  |  |  |
| M33F | Reporting N1MM group (FT8Call) |  |  |  |
| M34U | Frequencies Calibration (Upright) | Pass |  |  |
| M34P | Frequencies Calibration (PC) | Pass |  |  |
| M34G | Frequencies Calibration (Pages) | Pass |  |  |
| M34F | Frequencies Calibration (FT8Call) | Pass |  |  |
| M35U | Frequency table Insert Delete Reset (Upright) |  |  |  |
| M35P | Frequency table Insert Delete Reset (PC) |  |  |  |
| M35G | Frequency table Insert Delete Reset (Pages) |  |  |  |
| M35F | Frequency table Insert Delete Reset (FT8Call) |  |  |  |
| M36U | Frequency table edit in place (Upright) |  |  |  |
| M36P | Frequency table edit in place (PC) |  |  |  |
| M36G | Frequency table edit in place (Pages) |  |  |  |
| M36F | Frequency table edit in place (FT8Call) |  |  |  |
| M37U | Frequency table Load Save as Merge (Upright) |  |  |  |
| M37P | Frequency table Load Save as Merge (PC) |  |  |  |
| M37G | Frequency table Load Save as Merge (Pages) |  |  |  |
| M37F | Frequency table Load Save as Merge (FT8Call) |  |  |  |
| M38U | Frequency Schedule (Upright) |  |  |  |
| M38P | Frequency Schedule (PC) |  |  |  |
| M38G | Frequency Schedule (Pages) |  |  |  |
| M38F | Frequency Schedule (FT8Call) |  |  |  |
| M39U | Saved Messages drag and edit (Upright) | Pass |  |  |
| M39P | Saved Messages drag and edit (PC) | Pass |  |  |
| M39G | Saved Messages drag and edit (Pages) | Pass |  |  |
| M39F | Saved Messages drag and edit (FT8Call) | Pass |  |  |
| M40U | Notifications table Test (Upright) | Pass |  |  |
| M40P | Notifications table Test (PC) | Pass |  |  |
| M40G | Notifications table Test (Pages) | Pass |  |  |
| M40F | Notifications table Test (FT8Call) | Pass |  |  |
| M41U | UI colours (Upright) | Pass |  |  |
| M41P | UI colours (PC) | Pass |  |  |
| M41G | UI colours (Pages) | Pass |  |  |
| M41F | UI colours (FT8Call) | Pass |  |  |
| M42U | UI fonts (Upright) |  |  |  |
| M42P | UI fonts (PC) |  |  |  |
| M42G | UI fonts (Pages) |  |  |  |
| M42F | UI fonts (FT8Call) |  |  |  |
| M43U | Run Diagnostics (Upright) | Pass |  |  |
| M43P | Run Diagnostics (PC) | Pass |  |  |
| M43G | Run Diagnostics (Pages) | Pass |  |  |
| M43F | Run Diagnostics (FT8Call) | Pass |  |  |
| M44U | Phone page (Upright) | Pass |  |  |
| M44P | Phone page (PC) | Pass |  |  |
| M44G | Phone page (Pages) | Pass |  |  |
| M44F | Phone page (FT8Call) | Pass |  |  |
| M45U | About (Upright) |  |  |  |
| M45P | About (PC) |  |  |  |
| M45G | About (Pages) |  |  |  |
| M45F | About (FT8Call) |  |  |  |
| M46U | OK (Upright) |  |  |  |
| M46P | OK (PC) |  |  |  |
| M46G | OK (Pages) |  |  |  |
| M46F | OK (FT8Call) |  |  |  |
| M47U | Cancel (Upright) |  |  |  |
| M47P | Cancel (PC) |  |  |  |
| M47G | Cancel (Pages) |  |  |  |
| M47F | Cancel (FT8Call) |  |  |  |

## N. Menus

| ID | Test | Run 1 | Run 2 | Note |
|---|---|---|---|---|
| N01U | File menu (Upright) |  |  |  |
| N01P | File menu (PC) | Pass |  |  |
| N01G | File menu (Pages) |  |  |  |
| N01F | File menu (FT8Call) | Pass |  |  |
| N02U | Configurations Clone (Upright) |  |  |  |
| N02P | Configurations Clone (PC) | Pass |  |  |
| N02G | Configurations Clone (Pages) |  |  |  |
| N02F | Configurations Clone (FT8Call) | Pass |  |  |
| N03U | Configurations Clone Into (Upright) |  |  |  |
| N03P | Configurations Clone Into (PC) |  |  |  |
| N03G | Configurations Clone Into (Pages) |  |  |  |
| N03F | Configurations Clone Into (FT8Call) |  |  |  |
| N04U | Configurations Rename (Upright) |  |  |  |
| N04P | Configurations Rename (PC) |  |  |  |
| N04G | Configurations Rename (Pages) |  |  |  |
| N04F | Configurations Rename (FT8Call) |  |  |  |
| N05U | Configurations Reset (Upright) |  |  |  |
| N05P | Configurations Reset (PC) |  |  |  |
| N05G | Configurations Reset (Pages) |  |  |  |
| N05F | Configurations Reset (FT8Call) |  |  |  |
| N06U | Configurations Delete (Upright) |  |  |  |
| N06P | Configurations Delete (PC) |  |  |  |
| N06G | Configurations Delete (Pages) |  |  |  |
| N06F | Configurations Delete (FT8Call) |  |  |  |
| N07U | Configurations Switch To (Upright) |  |  |  |
| N07P | Configurations Switch To (PC) |  |  |  |
| N07G | Configurations Switch To (Pages) |  |  |  |
| N07F | Configurations Switch To (FT8Call) |  |  |  |
| N08U | View menu (Upright) |  |  |  |
| N08P | View menu (PC) | Pass |  |  |
| N08G | View menu (Pages) |  |  |  |
| N08F | View menu (FT8Call) | Pass |  |  |
| N09U | Mode menu (Upright) |  |  |  |
| N09P | Mode menu (PC) | Pass |  |  |
| N09G | Mode menu (Pages) |  |  |  |
| N09F | Mode menu (FT8Call) | Pass |  |  |
| N10U | Control menu (Upright) |  |  |  |
| N10P | Control menu (PC) | Pass |  |  |
| N10G | Control menu (Pages) |  |  |  |
| N10F | Control menu (FT8Call) | Pass |  |  |
| N11U | Log menu (Upright) |  |  |  |
| N11P | Log menu (PC) | Pass |  |  |
| N11G | Log menu (Pages) |  |  |  |
| N11F | Log menu (FT8Call) | Pass |  |  |
| N12U | Help menu (Upright) |  |  |  |
| N12P | Help menu (PC) | Pass |  |  |
| N12G | Help menu (Pages) |  |  |  |
| N12F | Help menu (FT8Call) | Pass |  |  |
| N13U | Show Fullscreen (Upright) |  |  |  |
| N13P | Show Fullscreen (PC) | Pass |  |  |
| N13G | Show Fullscreen (Pages) |  |  |  |
| N13F | Show Fullscreen (FT8Call) | Pass |  |  |
| N14U | Show Time Drift Controls... (Upright) |  |  |  |
| N14P | Show Time Drift Controls... (PC) | Pass |  |  |
| N14G | Show Time Drift Controls... (Pages) |  |  |  |
| N14F | Show Time Drift Controls... (FT8Call) | Pass |  |  |
| N15U | Open Log Directory... (Upright) |  |  |  |
| N15P | Open Log Directory... (PC) | Pass |  |  |
| N15G | Open Log Directory... (Pages) |  |  |  |
| N15F | Open Log Directory... (FT8Call) | Pass |  |  |
| N16U | Status bar configuration name (Upright) |  |  |  |
| N16P | Status bar configuration name (PC) |  |  |  |
| N16G | Status bar configuration name (Pages) |  |  |  |
| N16F | Status bar configuration name (FT8Call) |  |  |  |
