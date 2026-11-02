"""Writes TEST-CHECKLIST.md once. After that the file is the record: checklist.py marks its rows."""
import os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
LAYOUTS = [('U', 'Upright'), ('P', 'PC'), ('G', 'Pages'), ('F', 'FT8Call')]

ON_AIR = {
    'A. Decoding (audio fed in)': [
        'JS8 Normal frame decodes', 'JS8 Fast frame decodes', 'JS8 40 frame decodes', 'JS8 60 frame decodes',
        'JS8 Slow frame decodes', 'MULTI on: every speed decodes', 'MULTI off: only the selected speed decodes',
        'Enable Filter off: signal at 3000 Hz decodes', 'Enable Filter on: signal outside the filter does not decode',
        'Two-frame message joined in the Message Window', 'Band Activity row from a decode', 'Call Activity row from a decode',
        'Low-confidence text shows in [ ] in Band Activity', 'Duplicate frame dropped',
        'FT8 line shown (Show FT8 decodes ticked)', 'FT8 line not shown (unticked)',
    ],
    'B. Directed commands and replies': [
        'SNR? answered', 'INFO? answered', 'STATUS? answered', 'GRID? answered', 'HEARING? answered',
        'HEARING? with empty list still answers', 'QUERY CALL answered', 'QUERY MSGS answered YES', 'QUERY MSGS answered NO',
        'QUERY MSG n sends the message and marks it collected', 'QUERY MSGS to @HB answered',
        'SNR? to my @GROUP answered', 'INFO? to my @GROUP answered', 'STATUS? to my @GROUP answered', 'GRID? to my @GROUP answered',
        'HEARING? to my @GROUP answered', '@ALLCALL reply limit: one per station in 15 minutes',
        'Do not participate in the @ALLCALL group: no reply', 'AUTO off: no reply', 'Autoreply Confirmation Yes sends',
        'Autoreply Confirmation No does not send', 'Autoreply Confirmation No leaves a held message uncollected',
        'Only autoreply to these callsigns', 'Never autoreply to these callsigns', 'No reply with text in the Typing Box',
        'No reply while a message to me is arriving', 'Reply heard while transmitting goes out after the message',
        'Idle timeout box appears and turns AUTO off', 'Hide messages from these callsigns',
    ],
    'C. Checksums and buffers': [
        'MSG with good checksum reaches the Inbox', 'MSG with bad checksum dropped', 'MSG TO: with good checksum held',
        'MSG TO: with bad checksum dropped', 'QUERY with bad checksum dropped', 'Relay with bad checksum not forwarded',
        '@APRSIS MSG TO: keeps a 3-character last word', 'Command answered on the last frame, not the first',
        'Buffer closes at 60 s', 'Buffer dropped at 90 s', 'First frame clears an old buffer',
        'Drifting frames inside the speed\'s range join',
    ],
    'D. Relay and mailbox': [
        'A>B>C relay forwarded', 'Relay obeys AUTO off', 'Relay obeys Disable message relay', 'Relay heard while transmitting is queued',
        'Relayed MSG reaches the Inbox', 'Relayed MSG TO: held', 'Relayed QUERY MSGS answered', 'Relayed QUERY CALL answered',
        'MSG with a DE path: ACK follows the path', 'Relay to my @GROUP', 'Group message held 2 days and collected per station',
        'Store Message... held for a station', 'HB ACK carries MSG ID', 'Group MSG ID on HB ACK',
    ],
    'E. Heartbeat, CQ and transmit queue': [
        'HB button sends one heartbeat', 'HB timer sends after a full interval', 'CQ button sends one CQ', 'CQ timer sends after a full interval',
        'Starting CQ timer stops HB timer', 'Starting HB timer stops CQ timer', 'Selecting a callsign stops HB and CQ timers',
        'Deselect restores HB timer', 'Halt stops HB and CQ timers', 'CQ timer stops when a message is shown',
        'HB ACK sent with AUTO, HB and ACK on', 'No HB ACK in JS8 40', 'No HB ACK in JS8 60', 'No HB ACK while a buffer is open',
        'Pause heartbeat transmissions while in a QSO blocks ACK', 'Never acknowledge heartbeats list',
        'Suppress ACK under 55 minutes', 'Heartbeat offset avoids busy offsets', 'Heartbeat uses my offset at or below 1000 Hz',
        'Allow heartbeat transmissions outside of heartbeat sub-channel', 'Timed HB waits while transmitting',
        'Reply priority ahead of a queued message', 'No double send when a reply queues between parts',
        'Halt during a message', 'Halt during a heartbeat', 'Halt during TUNE', 'TUNE on and off', 'TX off stops sending and greys Send',
        'RX button refused while transmitting', 'SWL blocks every transmit', '30 m WSPR guard band refused',
        'Message Not Sent box after a stuck key', 'Send without callsign (Allow sending standard messages without callsign)',
        'Typing ahead while sending', 'Part-by-part strike-out in the Typing Box', 'Last Tx label',
        'All five speeds send: frame length correct',
    ],
    'F. Macros': [
        '<MYCALL>', '<MYGRID4>', '<MYGRID12>', '<MYINFO>', '<MYSTATUS>', '<MYIDLE>', '<MYVERSION> sends the plain version',
        '<CALL>', '<SNR>', '<TDELTA>', '<MYHB>', 'Values upper-cased', 'Leftover <...> removed',
    ],
    'G. Log files': [
        'ALL.TXT received lines', 'ALL.TXT transmitted lines', 'ALL.TXT frequency-change line', 'ALL.TXT start-of-day header',
        'DIRECTED.TXT line after whole message', 'Write log files unticked: nothing written', 'js8call_log.adi after Add to Log',
        'js8call.log after Add to Log', 'Erase ALL.TXT', 'Erase js8call_log.adi', 'Export Log... all three files',
    ],
}

SCREENS = {
    'H. Main window': [
        'Header shows callsign and UTC clock', 'Clock drift suffix', 'RX button', 'TX button', 'Mode button menu', 'Settings button',
        'SPOT button', 'LOG button opens Log QSO', 'TUNE button', 'HB press-and-hold menu', 'CQ press-and-hold menu',
        'REPLY button', 'SNR button', 'INFO button', 'STATUS button', 'Saved menu', 'Saved > Edit Saved Messages', 'Saved > APRS EMAIL',
        'Saved > APRS TXT', 'Saved > APRS GRID', 'Saved > APRS MSG', 'Directed menu greying', 'Deselect button', 'Send button texts',
        'Halt button', 'Typing Box hint with a selection', 'wpm box', 'Status bar labels', 'Signal meter', 'CAT button colours',
        'Power slider', 'Message Window scrolls', 'Message Window menu Cut Copy Paste', 'Message Window Clear',
        'Double tap a word in the Message Window', 'Typing Box menu', 'Restore Previous Message fills the box',
    ],
    'I. Lists': [
        'Band Activity header tap sorts', 'Band Activity press-and-hold columns', 'Band Activity row menu', 'Band Activity double tap',
        'Call Activity header tap sorts', 'Call Activity press-and-hold columns', 'Call Activity empty list menu',
        'Call menu Jump to offset', 'Call menu Jump to speed', 'Call menu Jump to time drift', 'Call menu Log...',
        'Call menu Show Message Inbox...', 'Call menu Store Message...', 'Call menu Saved Messages', 'Call menu Directed to CALL...',
        'Call menu Relay via CALL...', 'Call menu Deselect', 'Call menu Add New Station or Group...', 'Call menu Remove This Station/Group',
        'Call menu Block This Station', 'Call menu Clear Entire List', 'Call menu Clear All Lists asks first', 'Lists kept after restart',
        'Line dividers drag', 'Reset Frame Sizes',
    ],
    'J. Waterfall': [
        'Tap sets offset', 'Scale tap sets offset', 'Tap refused while a message is queued', 'Control tab Offset arrows', 'QSY',
        'Center arrows', 'Enable Filter', 'Sync', 'Filter Center and Width arrows', 'Filter knobs', 'Timing tab Time Drift arrows',
        'Start Automatic Time Drift', 'Stop After count', 'Manual buttons', 'Display tab Start', 'Bins Per Pixel', 'Palette list',
        'Adjust... Palette Designer', 'Scroll Speed', 'Flatten off', 'Gain', 'Zero', 'Filter Opacity', 'Avg N', 'Current list and Smooth',
        'Display Decode Attempts', 'Spectrum Height, Gain, Zero', 'Green cycle line and text', 'Frequency scale colours', 'WSPR marker on 30 m',
    ],
    'K. Inbox, Log QSO, Export': [
        'Inbox list newest first', 'Inbox multi-row Delete', 'Inbox Reply', 'Inbox filter by call', 'Log QSO fills from selection',
        'Log QSO Now buttons', 'Log QSO Add New Field', 'Log QSO Retain Comments', 'Log QSO word into focused field',
        'Log QSO word into Comments', 'Log QSO Start/End word exclusion', 'Export Options checkboxes',
    ],
    'L. Settings window': [
        'MULTI', 'AUTO', 'HB', 'ACK greying', 'Speed list', 'Frequency list', 'Custom frequency', 'Offset box', 'Power',
        'Show Band Heartbeats and ACKs', 'Show Offset in Band Activity', 'Show SNR in Band Activity', 'Show Column Labels',
        'Show FT8 decodes', 'Show panels', 'Clear Band Activity', 'Clear Call Activity', 'Clear RX Activity', 'Clear All Activity',
        'Configurations...', 'Exit', 'Copyright notice', 'Check for Updates', 'JS8Call User Guide', 'Save', 'Cancel', 'gear opens Station',
    ],
    'M. Station window': [
        'General page opens', 'My Callsign refuses bad call', 'SWL (Listening only)', 'Grid refuses bad grid', 'Acquire GPS and Set',
        'Callsign Groups refuses bad group', 'CQ Message refuses bad format', 'Reply, Info, Status text', 'Behavior checkboxes save',
        'Aging spin boxes', 'End of Transmission Character Indicator refuses empty', 'Missing Frame Character Indicator',
        'Heartbeat interval', 'Heartbeat Network group', 'Autoreply group', 'Only autoreply refuses bad callsign', 'Callsign Filtering',
        'Radio page Rig search', 'Radio page greying follows the rig', 'Test CAT', 'Test PTT', 'Split Operation', 'Tx delay', 'Hold PTT',
        'Audio page Input', 'Audio page Output', 'Input channel list has no Both', 'Remember power settings by band', 'Layout page',
        'Reporting Network Services', 'Reporting API group', 'Reporting N3FJP group', 'Reporting N1MM group', 'Frequencies Calibration',
        'Frequency table Insert Delete Reset', 'Frequency table edit in place', 'Frequency table Load Save as Merge', 'Frequency Schedule',
        'Saved Messages drag and edit', 'Notifications table Test', 'UI colours', 'UI fonts', 'Run Diagnostics', 'Phone page', 'About', 'OK', 'Cancel',
    ],
    'N. Menus': [
        'File menu', 'Configurations Clone', 'Configurations Clone Into', 'Configurations Rename', 'Configurations Reset',
        'Configurations Delete', 'Configurations Switch To', 'View menu', 'Mode menu', 'Control menu', 'Log menu', 'Help menu', 'Show Fullscreen',
        'Show Time Drift Controls...', 'Open Log Directory...', 'Status bar configuration name',
    ],
}

SERVERS = {
    'O. Rig against the fake rigctld': [
        'Frequency read', 'Frequency set', 'Mode USB set', 'Mode Data/Pkt set', 'PTT CAT key and release', 'VOX sends no key command',
        'Hold PTT one key-down', 'Tx delay lead time', 'Split Rig', 'Split Fake It', 'Poll Interval', 'Rig Control Error box',
        'Rig reconnect after a drop', 'QSY moves Band Activity rows', 'Band change on a rig read', 'Remember power by band',
    ],
    'P. Other rig stand-ins': ['DX Lab stand-in read, set, PTT', 'Ham Radio Deluxe stand-in read, set, PTT', 'Hamlib model 2 NET'],
    'Q. API': [
        'UDP PING', 'UDP STATION.STATUS', 'UDP RX.ACTIVITY', 'UDP RX.DIRECTED', 'RIG.PTT event', 'TX.FRAME event', 'RX.SPOT event',
        'All TCP commands answered (44)', 'Allow setting station information', 'TCP Max Connections', 'STATION.CLOSING',
        'WSJT-X Heartbeat', 'WSJT-X Status', 'WSJT-X Decode', 'WSJT-X QSOLogged', 'WSJT-X LoggedADIF', 'N3FJP contact', 'N1MM contact',
    ],
    'R. Network stand-ins': [
        'APRS-IS login and spot', 'APRS inbound relay', 'APRS ACK filter', 'APRS off with RX off', 'JS8NET RX.LOCAL', 'JS8NET RX.SPOT',
        'JS8NET RX.DIRECTED to a group', 'JS8NET bad checksum dropped', 'Spot blacklist', 'PSK Reporter packet reaches the stand-in',
        'PSK Reporter antenna info', 'PSK Reporter program name', 'Check for Updates finds a version', 'Failed update read says nothing',
    ],
    'S. Startup and restart': [
        'Fresh install opens Station on General', 'No callsign: Station cannot be left', 'RX off at startup', 'TX off at startup',
        'Turn autoreply on at startup', 'HB timer kept after restart', 'Reset activity at startup', 'Waterfall settings kept after restart',
    ],
}

PC = {
    'T. Against JS8Call on the PC': [
        'PC sends Normal, app decodes', 'PC sends Fast, app decodes', 'PC sends JS8 40, app decodes', 'PC sends Slow, app decodes',
        'App sends Normal, PC decodes', 'App sends Fast, PC decodes', 'App sends JS8 40, PC decodes', 'App sends Slow, PC decodes',
        'App sends JS8 60, PC decodes', 'App CQ reads the same on the PC', 'App heartbeat reads the same on the PC',
        'App directed message reads the same on the PC', 'App multi-frame message with checksum accepted by the PC',
        'PC multi-frame message with checksum accepted by the app', 'PC SNR? answered by the app', 'App SNR? answered by the PC',
        'Compound callsign both ways', 'Group message both ways',
    ],
}

REAL = {
    'U. Real networks (genuine traffic only)': ['Real APRS-IS login', 'Real APRS-IS @APRSIS GRID', 'Real JS8NET RX.LOCAL'],
}


def table(prefix_rows, out):
    out.append('| ID | Test | Run 1 | Run 2 | Note |')
    out.append('|---|---|---|---|---|')
    out.extend(f'| {i} | {t} |  |  |  |' for i, t in prefix_rows)
    out.append('')


def main():
    out = ['# Easy JS8 test checklist', '',
           'Rules: every row passes twice, with an app restart between the runs. A row passes only when seen running.',
           'A failure with no quick fix is marked **Failed** with what happened; Failed rows are retried at the end.',
           'Real phone and real radio tests come after all of this and are not on this list.',
           'Layouts: U Upright, P PC, G Pages, F FT8Call. N/A means the layout has no such control.', '']
    for group in (ON_AIR, SERVERS, PC, REAL):
        for title, rows in group.items():
            out.append(f'## {title}\n')
            letter = title[0]
            table([(f'{letter}{n + 1:02}', t) for n, t in enumerate(rows)], out)
    for title, rows in SCREENS.items():
        out.append(f'## {title}\n')
        letter = title[0]
        table([(f'{letter}{n + 1:02}{code}', f'{t} ({name})') for n, t in enumerate(rows) for code, name in LAYOUTS], out)
    path = os.path.join(ROOT, 'TEST-CHECKLIST.md')
    open(path, 'w', encoding='utf8', newline='\n').write('\n'.join(out))
    print(sum(1 for l in out if l.startswith('| ') and not l.startswith('| ID')), 'rows')


if __name__ == '__main__':
    main()
