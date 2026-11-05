"""W77-W104: audit B sections 2-7. Upright: the Directed menu and Log... on @ALLCALL, double-tap, <MYHB> and spaces,
Send while a heartbeat waits, the status label with RX off, the inbox title, empty-list menus, the header callsign,
a band change, which messages put the flag on a sender, the Callsigns count, the star column, the Columns menus,
blocked offsets, colours, the CAT button and the rig error box, the idle timeout label. PC layout: the Control and View
menus, the frequency menu, clone names, the power slider, the signal meter, Send. s27chk.py <run> [ID,ID]"""
from onair2 import *
import io
import xml.etree.ElementTree as ET
from PIL import Image
assert_avd()
run = int(sys.argv[1])
only = sys.argv[2].split(',') if len(sys.argv) > 2 else None
t = T(run, only)
c = t.call
M = 'VE3TST'
T.n = (int(time.time()) // 60 % 200) * 26 + 11
NBSP = ' '


def want(*ids):
    return not only or any(i in only for i in ids)


def fix(id, ok, note=''):
    if want(id):
        t.mark(id, ok, note)


def quiet(limit=120):
    """Wait until nothing has gone out for 12 s."""
    end, n, since = time.time() + limit, -1, time.time()
    while time.time() < end:
        m = sum(1 for l in alltxt() if 'Transmitting' in l)
        if m != n:
            n, since = m, time.time()
        elif time.time() - since >= 12:
            return True
        time.sleep(1)
    return False


def rotated(code):
    m = re.search(r'cur=(\d+)x(\d+)', adb('shell', 'dumpsys', 'window', 'displays').decode('utf8', 'replace'))
    return (int(m.group(1)) < int(m.group(2))) == (code == 'U')


def rotate(code):
    adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0')
    for _ in range(5):
        adb('shell', 'settings', 'put', 'system', 'user_rotation', '0' if code == 'U' else '1')
        time.sleep(2)
        if rotated(code):
            return True
    return False


def node(rid, xml=None):
    m = re.search(r'<node [^>]*resource-id="[^"]*/%s"[^>]*>' % rid, xml or dump())
    return m.group(0) if m else ''


def attr(n, a):
    m = re.search(r' %s="([^"]*)"' % a, n)
    return html.unescape(m.group(1)) if m else None


def rect(n):
    m = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n or '')
    return tuple(map(int, m.groups())) if m else None


def ntext(rid, xml=None):
    return attr(node(rid, xml), 'text')


def rows(xml=None):
    """The open menu or list box: (text, enabled, bounds) per row, top to bottom."""
    try:
        root = ET.fromstring(xml or dump())
    except ET.ParseError:
        return []
    out = []
    for lv in root.iter('node'):
        if lv.get('class') == 'android.widget.ListView':
            for row in list(lv):
                ts = [n.get('text') for n in row.iter('node') if n.get('text')]
                if ts:
                    out.append((ts[0], row.get('enabled') == 'true', rect(f'bounds="{row.get("bounds")}"')))
    return out


def items(title=None):
    """The open menu's rows; a sub-menu's own title row (its header) is left out."""
    return [(tx, en) for tx, en, _ in rows() if tx != title]


def menu_scroll(r, down=True):
    """Scroll the open menu [r] one screen down (or up), clear of the bottom edge: that is the Home gesture."""
    scr = rect(re.findall(r'<node [^>]*>', dump())[0])
    top, bot = max(r[0][2][1], scr[1] + 150), min(r[-1][2][3], scr[3] - 250)
    cx = (r[0][2][0] + r[0][2][2]) // 2
    a, b = (bot - 20, top + 20) if down else (top + 20, bot - 20)
    adb('shell', 'input', 'swipe', str(cx), str(a), str(cx), str(b), '800'); time.sleep(1)


def all_items(title=None):
    """Every row of a long menu: the list is scrolled to its end."""
    seen = []
    for _ in range(6):
        r = rows()
        new = [(tx, en) for tx, en, _ in r if (tx, en) not in seen and tx != title]
        if not new and seen:
            break
        seen += new
        if not r:
            break
        menu_scroll(r)
    return seen


def checked_items():
    """The open menu's rows: (text, ticked)."""
    try:
        root = ET.fromstring(dump())
    except ET.ParseError:
        return []
    out = []
    for lv in root.iter('node'):
        if lv.get('class') == 'android.widget.ListView':
            for row in list(lv):
                ts = [n.get('text') for n in row.iter('node') if n.get('text')]
                if ts:
                    out.append((ts[0], any(n.get('checked') == 'true' for n in row.iter('node'))))
    return out


def tap_item(text_, wait=1.5):
    """Tap a menu row; a long menu is scrolled down, then up, to reach it."""
    for down in (True, True, True, False, False, False, False):
        r = rows()
        for tx, en, b in r:
            if tx.strip() == text_:
                adb('shell', 'input', 'tap', str((b[0] + b[2]) // 2), str((b[1] + b[3]) // 2)); time.sleep(wait)
                return True
        if not r:
            break
        menu_scroll(r, down)
    print('NO ITEM', text_, flush=True)
    return False


def close_menus():
    for _ in range(3):
        if not rows():
            return
        adb('shell', 'input', 'keyevent', '4'); time.sleep(.8)


def hold(p, wait=1.5):
    adb('shell', 'input', 'swipe', str(p[0]), str(p[1]), str(p[0]), str(p[1]), '900'); time.sleep(wait)


def gesture(kind, px, py):
    o = rect(re.findall(r'<node [^>]*>', dump())[0])
    adb('shell', f'am broadcast -n {PKG}/com.js8call.example.debug.DebugGestureReceiver --es gesture {kind} --ef x {px - o[0]} --ef y {py - o[1]}')
    time.sleep(1.2)


def screen(name=None):
    b = adb('exec-out', 'screencap', '-p')
    if name:
        open(f'shots/{name}.png', 'wb').write(b)
    return Image.open(io.BytesIO(b)).convert('RGB')


def count(img, colours, box=None):
    im = img.crop(box) if box else img
    want_ = set(colours)
    return sum(n for n, px in im.getcolors(maxcolors=1 << 24) if px in want_)


def anchor():
    """A place to swipe the page from: a button or label clear of the top edge (the quick settings pull-down)
    and the bottom edge (the Home gesture)."""
    x = dump()
    for k in ('js8_btn_hb', 'js8_btn_saved', 'js8_status_state', 'js8_callsign'):
        p = find(x, k)
        if p and 300 <= p[1] <= 2050:
            return p
    return find(x, 'js8_btn_hb') or find(x, 'js8_callsign') or (540, 1000)


def page_top():
    for _ in range(5):
        r = rect(node('js8_callsign'))
        if r and r[1] >= 150:
            return True
        g = anchor()
        adb('shell', 'input', 'swipe', str(g[0]), str(g[1]), str(g[0]), str(min(2150, g[1] + 900)), '300'); time.sleep(1.2)
    return False


def show(rid, top=130, bottom=2230):
    """Upright: scroll the page until [rid] is all on the screen."""
    for down in (True, False):
        for _ in range(5):
            r = rect(node(rid))
            if r and r[1] >= top and r[3] <= bottom:
                return r
            g = anchor()
            y2 = max(150, g[1] - 600) if down else min(2150, g[1] + 600)
            adb('shell', 'input', 'swipe', str(g[0]), str(g[1]), str(g[0]), str(y2), '300'); time.sleep(1.2)
    return rect(node(rid))


def grid():
    m = re.search(r'<string name="grid">([^<]*)</string>', getprefs())
    return (m.group(1) if m else '').strip().upper()


def pref(k):
    m = re.search(r'name="%s"(?: value="([^"]*)" />|>([^<]*)</string>)' % re.escape(k), getprefs())
    return (m.group(1) or m.group(2)) if m else None


def box():
    v = ntext('js8_typing_box') or ''
    return '' if v.startswith('TYPE YOUR OUTGOING') else v


def type_box(s):
    tapk('js8_typing_box', .8); text(s); time.sleep(.8)
    adb('shell', 'input', 'keyevent', '111'); time.sleep(.8)


def list_cells(rid='js8_call_activity'):
    x = dump()
    lb = rect(node(rid, x))
    out = []
    for n in re.findall(r'<node [^>]*>', x):
        tx, r = attr(n, 'text'), rect(n)
        if tx and r and lb and lb[0] <= r[0] < lb[2] and lb[1] <= r[1] < lb[3]:
            out.append((r, tx))
    return lb, out


def hold_call(call, tries=3):
    """Press and hold [call]'s Call Activity row once it has stopped moving (a swipe in the list can also scroll
    the page). True when the menu that opens is that call's: the call is selected, or named in the menu."""
    for _ in range(tries):
        close_menus()
        page_top()
        p = show_call(call)
        if not p:
            continue
        time.sleep(1)
        q = find(dump(), call)
        if not q or abs(q[1] - p[1]) > 5:
            continue
        hold(q)
        # the press selects the row first (MF showCallMenu); the menu's call rows may be below its visible part
        sel = next((r.get('value') for r in api('RX.GET_CALL_SELECTED', wait=1) if r.get('type') == 'RX.CALL_SELECTED'), None)
        if sel == call or any(call in tx for tx, _, _ in rows()):
            return True
        print('MENU NOT FOR', call, flush=True)
    return False


def call_row(call):
    """Texts in the Call Activity row of [call], left to right."""
    lb, cells = list_cells()
    me = [r for r, tx in cells if tx == call]
    if not me:
        return []
    y = (me[0][1] + me[0][3]) // 2
    return [tx for r, tx in sorted(cells, key=lambda q: q[0][0]) if r[1] <= y <= r[3]]


def row_of(call, tries=3):
    """Scroll [call] into the Callsigns list and read its row; again if the list redrew between the two reads."""
    for _ in range(tries):
        close_menus()  # a swipe in a sliver of the list can turn into a press and hold
        page_top()
        show_call(call)
        r = call_row(call)
        if r:
            return r
        time.sleep(1)
    return []


def call_snr(call):
    """The app's SNR for [call] in Call Activity (API RX.GET_CALL_ACTIVITY)."""
    for r in api('RX.GET_CALL_ACTIVITY', wait=1.5):
        if r.get('type') == 'RX.CALL_ACTIVITY':
            return (r['params'].get(call) or {}).get('SNR')
    return None


def header(rid='js8_call_activity'):
    lb, cells = list_cells(rid)
    return [tx for r, tx in sorted(cells, key=lambda q: q[0][0]) if lb and r[1] < lb[1] + 40]


def callsigns_label():
    return next((h for h in header() if h.startswith('Callsigns')), None)


def empty_press(rid):
    """Press and hold below the last row (above the line's drag strip)."""
    r = rect(node(rid))
    hold(((r[0] + r[2]) // 2, r[3] - 60))


def sent(base, pat, wait=40):
    end = time.time() + wait
    while time.time() < end:
        lines = alltxt()[base:]
        hit = [l for l in lines if 'Transmitting' in l and re.search(pat, l)]
        if hit:
            return hit[0].split('JS8:')[-1].strip()
        hit = [j for j in tx_joined(lines) if re.search(pat, j)]  # N30: one frame per line
        if hit:
            return hit[0].strip()
        time.sleep(1)
    return ''


def send_watch(secs=25, typed=None):
    """Send button (text, enabled) and the Typing Box every 0.3 s. A heartbeat waits for an empty Typing Box
    (PC processTxQueue), so [typed] goes in once Send says Ready."""
    out, end = [], time.time() + secs
    while time.time() < end:
        x = dump(); n = node('js8_btn_send', x)
        b = attr(node('js8_typing_box', x), 'text') or ''
        s = (attr(n, 'text'), attr(n, 'enabled'), '' if b.startswith('TYPE YOUR OUTGOING') else b)
        if not out or out[-1] != s:
            out.append(s)
        if typed and (s[0] or '').startswith('Ready'):
            api('TX.SET_TEXT', typed, wait=.3); typed = None
        time.sleep(.3)
    return out


def send_ok(w, timed):
    """PC updateTxButtonDisplay: Send greyed while a message waits or goes out; Ready with its time."""
    busy = [s for s in w if (s[0] or '').startswith(('Ready', 'Sending'))]
    return bool(busy) and all(s[1] == 'false' for s in busy) and any(s[2] for s in busy) and         (not timed or any(re.match(r'Ready \(\d+s\)$', s[0]) for s in busy)) and w[-1][0].startswith('Send') and w[-1][1] == 'true'


SAVED = f'S27 {run}  A <MYHB>'
UP = dict(tx_submode=1, js8cfg_reset_activity=True, js8cfg_up_split_lists=None, js8cfg_saved_messages=SAVED, landscape_layout=None,
          js8cfg_api_dynamic_info=False, js8cfg_callsign_aging='0', rig_control_enabled=False, rig_type='none', js8cfg_ptt_method='vox')
rotate('U')
fresh(**UP); time.sleep(3)
g4 = grid()[:4]

if want('W77', 'W78'):  # Directed menu and Log... on @ALLCALL (audit B section 2, N5)
    with part('W77,W78', 35, ('W77', 'W78'), t.res, t.mark):
        page_top()
        hold_call('@ALLCALL')
        top = dict(items())
        on_dir = tap_item('Directed to @ALLCALL...')
        screen(f'w77_{run}')
        sub = all_items('Directed to @ALLCALL...')
        screen(f'w77b_{run}')  # scrolled: the line after QUERY MSG [ID] (N5), for the eye
        close_menus()
        GREY = ('SNR - ', 'SNR? ', 'INFO? ', 'GRID? ', 'STATUS? ', 'HEARING? ', '>[MESSAGE]', 'MSG [MESSAGE]', 'MSG TO:')
        LIVE = ('QUERY CALL', 'QUERY MSGS', 'QUERY MSG [ID]', 'AGN?', 'QSL?', 'HW CPY?', 'INFO - ', 'GRID ')
        grey = [tx for tx, en in sub if not en]
        live = [tx for tx, en in sub if en]
        ok = len(grey) == len(GREY) and all(sum(g.startswith(k) for g in grey) == 1 for k in GREY) and \
            all(any(l.startswith(k) for l in live) for k in LIVE)
        fix('W77', on_dir and ok, f'greyed {[g.split(" - ")[0] for g in grey]}; {len(live)} live')
        hold_call('@TESTG')
        tg = dict(items()); close_menus()
        fix('W78', top.get('Log...') is False and tg.get('Log...') is True, f"@ALLCALL Log... {top.get('Log...')}, @TESTG {tg.get('Log...')}")

if want('W79'):  # N6: double-tap a Call Activity row
    with part('W79', 40, ('W79',), t.res, t.mark):
        d = c()
        inj(f'{d}: @ALLCALL CQ CQ CQ FN31', 1234); time.sleep(3)
        p = show_call(d)
        api('TX.SET_TEXT', '', wait=1)
        if p:
            gesture('doubletap', p[0], p[1])
        b = box()
        fix('W79', b == d, f'Typing Box {b!r}')
        api('TX.SET_TEXT', '', wait=1)

if want('W80', 'W102'):  # <MYHB>, spaces kept (N8); Edit Saved Messages (names)
    with part('W80,W102', 10, ('W80', 'W102'), t.res, t.mark):
        page_top(); show('js8_btn_saved')
        tapk('js8_btn_saved', 1.2)
        it = [tx for tx, _ in items()]
        close_menus()
        exp = f'S27 {run}  A HB {g4}'
        fix('W80', exp in it, 'saved message shown as expected' if exp in it else f'shown {[i for i in it if i.startswith("S27")]}')
        saved_name = 'Edit Saved Messages' in it

if want('W81'):  # Send while a heartbeat waits (N9, Ready countdown), upright
    with part('W81', 50, ('W81',), t.res, t.mark):
        quiet()
        page_top(); show('js8_btn_send')
        api('TX.SET_TEXT', '', wait=1)
        tapk('js8_btn_hb', .1)
        w = send_watch(24, f'W81 {run}')
        fix('W81', send_ok(w, False), f'Send states {w}')
        api('TX.SET_TEXT', '', wait=1)

if want('W82'):  # Heartbeat interval default: PC every 30 minutes (Configuration.cpp 2409), setting removed
    with part('W82', 30, ('W82',), t.res, t.mark):
        fresh(**{**UP, 'heartbeat_interval': None}); time.sleep(3)
        hb_api = next((r['params'].get('HB_INTERVAL') for r in api('STATION.GET_CONFIG') if r.get('type') == 'STATION.CONFIG'), None)
        page_top(); show('js8_btn_hb')
        hold(find(dump(), 'js8_btn_hb'))
        screen(f'w82_{run}')
        ticked = [tx for tx, on in checked_items() if on]
        close_menus()
        fix('W82', hb_api == 30 and ticked == ['Repeat every 30 minutes'], f'API HB_INTERVAL {hb_api}; heartbeat menu ticked {ticked}')
        fresh(**UP); time.sleep(2)

if want('W85'):  # status label with RX off
    with part('W85', 25, ('W85',), t.res, t.mark):
        quiet()
        page_top(); tapk('js8_btn_rx', 2)
        show('js8_status_state', bottom=2270)
        n = node('js8_status_state'); off_t = attr(n, 'text'); r = rect(n)
        img = screen(f'w85_{run}')
        px = img.getpixel(((r[0] + r[2]) // 2, (r[1] + r[3]) // 2))
        page_top(); tapk('js8_btn_rx', 3)
        on_t = ntext('js8_status_state')
        fix('W85', off_t == '' and px not in ((34, 255, 34), (0x66, 0x99, 0xFF)) and on_t in ('Receiving', 'Decoding'),
            f'RX off {off_t!r} colour {px}; RX on {on_t!r}')

if want('W86'):  # Show Message Inbox... title
    with part('W86', 50, ('W86',), t.res, t.mark):
        quiet()
        i = c(); pl = f'W86 INBOX {run}'
        inj(f'{i}: {M} MSG {pl} {sum16(pl)}', 900, 3, -5); time.sleep(4)
        page_top(); hold_call(i)
        opened = tap_item('Show Message Inbox...', 3)
        title = ntext('js8_inbox_title')
        lb, cells = list_cells('js8_inbox_table')
        ids = [r for r, tx in cells if re.fullmatch(r'\d+', tx)]
        if ids:
            adb('shell', 'input', 'tap', str((ids[0][0] + ids[0][2]) // 2), str((ids[0][1] + ids[0][3]) // 2)); time.sleep(1.5)
        has_msg = pl in (ntext('js8_inbox_message') or '')
        screen(f'w86_{run}')
        if not tapk('js8_inbox_back', 2):
            close_menus()
        fix('W86', opened and title == 'Message Inbox' and has_msg, f'title {title!r}; message listed {has_msg}')

if want('W87'):  # press and hold an empty list
    with part('W87', 30, ('W87',), t.res, t.mark):
        page_top()
        hold_call('@ALLCALL'); tap_item('Clear Entire List', 2)
        empty_press('js8_call_activity')
        screen(f'w87c_{run}'); cm = [(tx.strip(), en) for tx, en in all_items()]; close_menus()
        empty_press('js8_band_activity')
        screen(f'w87b_{run}'); bm = [(tx.strip(), en) for tx, en in all_items()]; close_menus()
        CM = [('Log...', False), ('Show Message Inbox...', False), ('Store Message...', False), ('Saved Messages...', True),
              ('Directed to ...', False), ('Relay via ...', False), ('Deselect', False), ('Add New Station or Group...', True),
              ('Remove This Station', False), ('Block This Station', False), ('Clear Entire List', True), ('Clear All Lists', True)]
        BM = [('Log...', False), ('Saved Messages...', True), ('Directed to ...', False), ('Relay via ...', False), ('Deselect', False),
              ('Remove Activity', False), ('Clear', True), ('Clear All Lists', True)]
        fix('W87', cm == CM and bm == BM, f'Call Activity {cm if cm != CM else "as PC"}; Band Activity {bm if bm != BM else "as PC"}')

if want('W89'):  # N15: a band change empties the Typing Box and forgets the last message
    with part('W89', 70, ('W89',), t.res, t.mark):
        quiet()
        page_top()
        api('TX.SET_TEXT', f'W89A {run}', wait=1)
        base = len(alltxt()); show('js8_btn_send'); tapk('js8_btn_send', 1)
        first = sent(base, f'W89A {run}', 50); quiet()
        api('TX.SET_TEXT', f'W89B {run}', wait=1); time.sleep(1)
        before = box()
        api('RIG.SET_FREQ', params={'DIAL': 7078000}, wait=1); time.sleep(3)
        after = box()
        p = find(dump(), 'js8_typing_box'); hold(p)
        restore = dict(items()).get('Restore Previous Message'); close_menus()
        api('RIG.SET_FREQ', params={'DIAL': 14078000}, wait=1); time.sleep(3)
        fix('W89', bool(first) and before == f'W89B {run}' and after == '' and restore is False,
            f'sent {bool(first)}; box {before!r} then {after!r}; Restore Previous Message enabled {restore}')

if want('W90'):  # N16: the flag only for unread messages to me; the sender row from the message
    with part('W90', 75, ('W90',), t.res, t.mark):
        # every run leaves an unread message to me, and its sender stays in Callsigns: past 100 rows the sender's
        # row is out of sight (runs 12, 43, 45, 46). The emulator's inbox is test data: emptied first.
        stop(); adb('shell', f"run-as {PKG} sh -c 'rm -f databases/js8_messages.db*'"); fresh(**UP); time.sleep(4)
        quiet()
        senders = {m.get('params', {}).get('FROM') for m in inbox()}
        gq, dq = c(), c(1)
        for _ in range(20):  # call names repeat across runs: two with nothing in the inbox
            if gq not in senders and dq not in senders:
                break
            gq, dq = c(), c(1)
        p1, p2 = f'W90 GROUP {run}', f'W90 ME {run}'
        inj(f'{gq}: @TESTG MSG {p1} {sum16(p1)}', 1000, 3, -7); time.sleep(4)
        inj(f'{dq}: {M} MSG {p2} {sum16(p2)}', 1100, 3, -5); time.sleep(4)
        page_top()
        rg, rd = row_of(gq), row_of(dq)
        in_app = call_snr(dq)  # None: not in the app's Call Activity
        if not rd:
            screen(f'w90_{run}')
        stored = [m for m in map(json.dumps, inbox()) if (p1 in m and gq in m) or (p2 in m and dq in m)]
        fresh(**UP); time.sleep(4)
        page_top(); rd2 = row_of(dq)
        gone = not show_call(gq)
        snr = call_snr(dq)  # upright, the SNR cell is right of the screen
        fix('W90', len(stored) == 2 and '⚑' not in rg and '⚑' in rd and '⚑' in rd2 and snr == -5 and gone,
            f'{len(stored)} stored; group sender row {rg}; my sender row {rd} (app SNR {in_app}); after restart {rd2} SNR {snr}, group sender gone {gone}')

if want('W91'):  # N17: Callsigns (N) counts the calls inside Callsign aging
    with part('W91', 140, ('W91',), t.res, t.mark):
        fresh(**{**UP, 'js8cfg_callsign_aging': '1'}); time.sleep(3)
        a1, b1 = c(), c(1)
        inj(f'{a1}: @ALLCALL CQ CQ CQ FN31', 1300); time.sleep(3)
        page_top(); p = show_call(a1)
        if p:
            adb('shell', 'input', 'tap', str(p[0]), str(p[1])); time.sleep(1)
        time.sleep(66)
        page_top(); h0 = callsigns_label(); shown = bool(show_call(a1))
        inj(f'{b1}: @ALLCALL CQ CQ CQ FN31', 1400); time.sleep(4)
        page_top(); h1 = callsigns_label()
        fix('W91', h0 == 'Callsigns' and shown and h1 == 'Callsigns (1)', f'after 66 s {h0!r} with {a1} selected and shown {shown}; new call {h1!r}')

if want('W92', 'W93'):  # N18: the star column; N19: Columns menus
    with part('W92,W93', 65, ('W92', 'W93'), t.res, t.mark):
        fresh(**{**UP, 'my_groups': ''}); time.sleep(4)
        page_top()
        h0 = '★' in header()
        hold_call('@ALLCALL'); tap_item('Clear Entire List', 2)
        h1 = '★' in header()
        e = c(); inj(f'{e}: @ALLCALL CQ CQ CQ FN31', 1500); time.sleep(4)
        h2 = '★' in header()
        fix('W92', h0 and not h1 and h2, f'star column: inbox flags {h0}, list cleared {h1}, after a CQ {h2}')
        if want('W93'):
            def columns_menu(rid, label):
                lb, cells = list_cells(rid)
                hc = [r for r, tx in cells if tx.startswith(label)]
                if not hc:
                    return []
                hold(((hc[0][0] + hc[0][2]) // 2, (hc[0][1] + hc[0][3]) // 2))
                if not tapk('Show Columns...', 1.5):
                    return []
                return [tx for tx, _ in all_items()]
            CC = ['Show Column Labels', 'Minimal Column Labels', 'Callsign', 'Frequency Offset', 'Last heard timestamp', 'SNR', 'Time Delta',
                  'Mode Speed', 'Grid Locator', 'Distance', 'Azimuth', 'Worked Before', 'Logged Name', 'Logged Comment']
            BC = ['Show Column Labels', 'Minimal Column Labels', 'Frequency Offset', 'Last heard timestamp', 'SNR', 'Time Delta', 'Mode Speed']
            # upright, Age and SNR are right of the screen: the Callsigns header is pressed, the SNR tick read from its setting
            snr_on = lambda: pref('js8_table_call_col_snr') != 'false'
            cc = columns_menu('js8_call_activity', 'Callsigns')
            screen(f'w93c_{run}')
            snr0 = snr_on()
            at_once = tap_item('SNR', 1.5)  # the call list: one tap, no OK
            snr1 = snr_on()
            columns_menu('js8_call_activity', 'Callsigns'); tap_item('SNR', 1.5)
            snr2 = snr_on()
            close_menus()
            bc = columns_menu('js8_band_activity', 'Age')
            close_menus()
            fix('W93', cc == CC and bc == BC and at_once and snr0 and not snr1 and snr2,
                f'call {cc if cc != CC else "as PC"}; band {bc if bc != BC else "as PC"}; SNR column {snr0} -> {snr1} -> {snr2}')

if want('W94'):  # blocked station: only its exact offset stays hidden
    with part('W94', 45, ('W94',), t.res, t.mark):
        quiet()
        blk = c()
        fresh(**{**UP, 'js8cfg_rx_blocklist': blk}); time.sleep(4)
        off = next((r['params']['OFFSET'] for r in api('RIG.GET_FREQ') if r.get('type') == 'RIG.FREQ'), 1500)
        inj(f'{blk}: {M} W94 START', off, 1); time.sleep(2)
        inj(f'W94 NEAR {run}', off + 3, 0); time.sleep(2)
        inj(f'W94 SAME {run}', off, 0); time.sleep(2)
        inj(f'W94 LAST {run}', off, 2); time.sleep(2)
        inj(f'{c(1)}: {M} W94 AFTER {run}', off, 3); time.sleep(3)
        mw = html.unescape(ntext('js8_message_window') or '')
        fix('W94', f'W94 NEAR {run}' in mw and f'W94 SAME {run}' not in mw and f'W94 LAST {run}' not in mw and 'W94 START' not in mw
            and f'W94 AFTER {run}' in mw, f'Message Window: near {f"W94 NEAR {run}" in mw}, same {f"W94 SAME {run}" in mw}, '
            f'last {f"W94 LAST {run}" in mw}, first {"W94 START" in mw}, next station {f"W94 AFTER {run}" in mw}')

if want('W95'):  # N20: colours
    with part('W95', 10, ('W95',), t.res, t.mark):
        page_top(); r = show('js8_waterfall_view', bottom=2240)
        img = screen(f'w95_{run}')
        new_scale = count(img, [(241, 196, 15), (46, 204, 113)])
        old_scale = count(img, [(242, 195, 0), (46, 204, 64)])
        wb = r if r else None
        blue_new, blue_old = count(img, [(0, 0, 128)], wb), count(img, [(0, 0, 139)], wb)
        dots_new, dots_old = count(img, [(128, 128, 128)], wb), count(img, [(68, 68, 68)], wb)
        fix('W95', new_scale > 0 and old_scale == 0 and blue_new > 0 and blue_old == 0 and dots_new > 0 and dots_old == 0,
            f'scale PC {new_scale} old {old_scale}; spectrum top PC {blue_new} old {blue_old}; grid PC {dots_new} old {dots_old} pixels')

if want('W88'):  # N43: the callsign in the header
    with part('W88', 45, ('W88',), t.res, t.mark):
        fresh(**UP); time.sleep(2)
        h0 = ntext('js8_callsign')
        fresh(**{**UP, 'js8cfg_api_dynamic_info': True}); time.sleep(3)
        h1 = ntext('js8_callsign')
        g_on = next((r_.get('value') for r_ in api('STATION.GET_GRID') if r_.get('type') == 'STATION.GRID'), None)  # an API grid stays after a restart
        api('STATION.SET_GRID', 'FN20AB', wait=1); time.sleep(2)
        h2 = ntext('js8_callsign')
        fix('W88', h0 == M and bool(g_on) and h1 == f'{M} - {g_on}' and h2 == f'{M} - FN20AB',
            f'box off {h0 == M}; box on {h1 == f"{M} - {g_on}"}; API grid {h2 == f"{M} - FN20AB"}')
        fresh(**UP); time.sleep(2)

if want('W84'):  # CAT orange while the rig opens; Rig Control Error box with Show Details... (Hamlib model 2 by host and port, as hsplit.py)
    with part('W84', 35, ('W84',), t.res, t.mark):
        setprefs(**{**UP, 'rig_control_enabled': True, 'rig_type': 'hamlib_usb', 'rig_hamlib_model': '2', 'rigctld_host': '10.255.255.1',
                    'rigctld_port': '4532', 'js8cfg_cat_poll_interval': '1', 'js8cfg_ptt_method': 'cat', 'js8cfg_split': 'none'})
        adb('shell', 'am', 'start', '-n', f'{PKG}/com.js8call.example.MainActivity')
        orange, box_, end = 0, '', time.time() + 70
        time.sleep(2)
        r = None
        while time.time() < end:
            x = html.unescape(dump())
            if 'Rig Control Error' in x:
                box_ = x
                break
            r = r or show('js8_btn_cat')
            if r:
                img = screen()
                orange = max(orange, count(img, [(255, 165, 0)], r))
            time.sleep(.5)
        msg_ok = 'Do you want to reconfigure the radio interface?' in box_
        shown0 = 'Show Details...' in box_
        screen(f'w84a_{run}')
        tapk('Show Details...', 1.5)
        x2 = html.unescape(dump()); hide = 'Hide Details...' in x2
        screen(f'w84b_{run}')
        detail = len(re.findall(r' text="([^"]+)"', x2)) > len(re.findall(r' text="([^"]+)"', box_))
        adb('shell', 'input', 'keyevent', '4'); time.sleep(1)
        fix('W84', orange > 0 and msg_ok and shown0 and hide and detail,
            f'orange CAT {orange} px; question {msg_ok}; Show Details... {shown0}; Hide Details... {hide}; reason shown {detail}')
        fresh(**UP); time.sleep(2)

# PC layout
PC = dict(UP, landscape_layout='pc', js8cfg_pwr_band_tune=True, js8cfg_pwr_band_tx=False, js8cfg_pwr_band_tune_memory='20m=300',
          js8cfg_pwr_band_tx_memory=None, js8cfg_out_attenuation='50', js8cfg_pwr_last_band='20m', last_frequency='14078000')
if want('W96', 'W97', 'W98', 'W99', 'W100', 'W101', 'W102', 'W103', 'W83'):
    with part('W96,W97,W98,W99,W100,W101,W102,W103,W83', 20, ('W96', 'W97', 'W98', 'W99', 'W100', 'W101', 'W102', 'W103', 'W83'), t.res, t.mark):
        rotate('P')
        fresh(**PC); time.sleep(4)

if want('W100'):  # power slider follows TUNE (Remember power settings by band)
    with part('W100', 15, ('W100',), t.res, t.mark):
        r = rect(node('js8_gain_slider'))
        a0 = screen(f'w100a_{run}').crop(r)
        tapk('js8_btn_tune', 2.5)
        p1 = pref('js8cfg_out_attenuation'); a1 = screen(f'w100b_{run}').crop(r)
        tapk('js8_btn_tune', 2.5)
        p2 = pref('js8cfg_out_attenuation'); a2 = screen(f'w100c_{run}').crop(r)
        same = lambda u, v: list(u.getdata()) == list(v.getdata())
        fix('W100', p1 == '300' and p2 == '50' and not same(a0, a1) and same(a0, a2), f'power 50 -> {p1} -> {p2}; slider moved {not same(a0, a1)}, back {same(a0, a2)}')

if want('W101'):  # signal meter 0-100: for the eye
    with part('W101', 10, ('W101',), t.res, t.mark):
        r = rect(node('js8_level_meter'))
        screen().crop(r).save(f'shots/w101_{run}.png')
        print('W101 SHOT', f'shots/w101_{run}.png', flush=True)

if want('W96', 'W83'):  # Control menu: order, sub-menus, Set Focus..., Set Frequency Offset; frequency rows (N10)
    with part('W96,W83', 60, ('W96', 'W83'), t.res, t.mark):
        tapk('js8_menubar_control', 1.2)
        screen(f'w96_{run}')
        top = [tx for tx, _ in all_items()]
        CT = ['Enable Receiver (RX)', 'Enable Transmitter (TX)', 'Enable Reporting (SPOT)', 'Enable Tuning Tone (TUNE)', 'Set Frequency...',
              'Set Offset...', 'Set Focus...', 'Send Heartbeat...', 'Send CQ Message...', 'Clear Activity...']
        tap_item('Set Frequency...')
        fr = [tx for tx, _ in all_items('Set Frequency...')]; screen(f'w83_{run}')
        close_menus()
        row20 = next((f for f in fr if f.startswith('20m:')), '')
        fix('W83', bool(fr) and fr[0] == 'Set a Custom Frequency...' and row20.startswith(f'20m:    14.078{NBSP}000 MHz'), f'rows {fr[:3]}; 20m row {row20!r}')
        base = len(alltxt())
        tapk('js8_menubar_control', 1.2); tap_item('Send Heartbeat...')
        hb = [tx for tx, _ in items('Send Heartbeat...')]; close_menus()
        time.sleep(12); hb_sent = any('Transmitting' in l for l in alltxt()[base:])
        tapk('js8_menubar_control', 1.2); tap_item('Set Focus...')
        fo = [tx for tx, _ in items('Set Focus...')]
        tap_item('Message Reply Area', 1.5)
        focused = attr(node('js8_typing_box'), 'focused') == 'true'
        adb('shell', 'input', 'keyevent', '111'); time.sleep(.8)
        tapk('js8_menubar_control', 1.2); tap_item('Set Offset...')
        off_title = 'Set Frequency Offset' in html.unescape(dump())
        adb('shell', 'input', 'keyevent', '4'); time.sleep(1)
        FO = ['Message Receive Area', 'Message Reply Area', 'Band Activity Table', 'Call Activity Table']
        fix('W96', top == CT and 'Send Heartbeat Now' in hb and not hb_sent and fo[-4:] == FO and focused and off_title,
            f'order {top if top != CT else "as PC"}; heartbeat menu {"Send Heartbeat Now" in hb}, sent on opening {hb_sent}; '
            f'Set Focus {fo[-4:] == FO}, Typing Box focused {focused}; offset box title {off_title}')

if want('W97'):  # N12: Clear All Activity at once
    with part('W97', 15, ('W97',), t.res, t.mark):
        h = c(); inj(f'{h}: @ALLCALL CQ CQ CQ FN31', 1600); time.sleep(3)
        had = h in dump()
        tapk('js8_menubar_control', 1.2); tap_item('Clear Activity...')
        cl = [tx for tx, _ in items('Clear Activity...')]
        tap_item('Clear All Activity', 2)
        x = html.unescape(dump())
        asked = 'Are you sure' in x
        fix('W97', had and cl[:1] == ['Clear All Activity'] and not asked and h not in x, f'{h} listed {had}; asked {asked}; still listed {h in x}')

if want('W98', 'W102'):  # N14: View menu in the PC order
    with part('W98,W102', 25, ('W98', 'W102'), t.res, t.mark):
        tapk('js8_menubar_view', 1.2)
        vw = [tx for tx, _ in all_items()]; screen(f'w98_{run}')
        close_menus()
        VW = ['Settings…', 'Show Frequency & Clock', 'Show Band Activity', 'Show Band Heartbeats and ACKs', 'Show Band Activity Columns...',
              'Sort Band Activity By...', 'Show Call Activity', 'Show Call Activity Columns...', 'Sort Call Activity By...', 'Show Waterfall',
              'Show Waterfall Controls', 'Show Time Drift Controls...', 'Show Fullscreen', 'Show Statusbar', 'Show Message Inbox...', 'Reset Frame Sizes']
        hb0 = pref('js8cfg_show_hb_acks')
        tapk('js8_menubar_view', 1.2); tap_item('Show Band Heartbeats and ACKs')
        hb1 = pref('js8cfg_show_hb_acks')
        tapk('js8_menubar_view', 1.2); tap_item('Show Band Heartbeats and ACKs')
        hb2 = pref('js8cfg_show_hb_acks')
        fix('W98', vw == VW and hb1 == 'false' and hb2 == 'true', f'order {vw if vw != VW else "as PC"}; heartbeats {hb0} -> {hb1} -> {hb2}')
        if want('W102'):
            sn = globals().get('saved_name', False)
            fix('W102', 'Sort Call Activity By...' in vw and sn, f'Sort Call Activity By... {"Sort Call Activity By..." in vw}; Edit Saved Messages {sn}')

if want('W99'):  # N7: clone names
    with part('W99', 15, ('W99',), t.res, t.mark):
        CFG = 'files/configurations'
        adb('shell', f"run-as {PKG} sh -c 'rm -f {CFG}/Default+-+Copy*'")
        for _ in range(2):
            tapk('js8_menubar_configurations', 1.2); tap_item('Default'); tap_item('Clone', 1.5)
        names = [html.unescape(n) for n in adb('shell', f'run-as {PKG} ls {CFG}').decode().split()]
        import urllib.parse
        names = [urllib.parse.unquote_plus(n).removesuffix('.json') for n in names]
        adb('shell', f"run-as {PKG} sh -c 'rm -f {CFG}/Default+-+Copy*'")
        fix('W99', 'Default - Copy' in names and 'Default - Copy(2)' in names, f'{[n for n in names if n.startswith("Default")]}')

if want('W103'):  # Send while a heartbeat waits, PC layout
    with part('W103', 50, ('W103',), t.res, t.mark):
        quiet()
        api('TX.SET_TEXT', '', wait=1)
        tapk('js8_btn_hb', .1)
        w = send_watch(24, f'W103 {run}')
        fix('W103', send_ok(w, True), f'Send states {w}')
        api('TX.SET_TEXT', '', wait=1)

if want('W104'):  # idle timeout label: black, white text, while the Idle Timeout box is open (PC tx_watchdog, mw 6912-6960)
    with part('W104', 455, ('W104',), t.res, t.mark):
        rotate('U')
        fresh(**{**UP, 'autoreply_enabled': True, 'js8cfg_tx_watchdog': '5'}); time.sleep(3)
        # the box hides the main screen from the dump: the label's place is read first, below the box (screen rows 966-1437)
        page_top(); r = show('js8_status_state', top=1550)
        end, x = time.time() + 420, ''
        while time.time() < end:  # no touches: a touch restarts the idle time
            x = html.unescape(dump())
            if 'Idle Timeout' in x:
                break
            time.sleep(5)
        time.sleep(2)
        box_ = 'You have been idle for more than 5 minutes.' in x
        img = screen(f'w104_{run}')
        area = (r[2] - r[0]) * (r[3] - r[1])
        black = count(img, [(0, 0, 0)], r)
        light = sum(n for n, (cr, cg, cb) in img.crop(r).getcolors(maxcolors=1 << 24) if cr == cg == cb and cr > 60)  # white text under the dim
        tapk('OK', 2)
        after = ntext('js8_status_state')
        fix('W104', box_ and black > area // 2 and light > 0 and after in ('Receiving', 'Decoding'),
            f'box {box_}; label black {black} of {area} px, light text {light} px; after OK {after!r}')
        fresh(**UP)

rotate('U')
print('RESULT ' + ' '.join(f'{i}={"PASS" if ok else "FAIL"}' for i, ok, _ in t.res), flush=True)
