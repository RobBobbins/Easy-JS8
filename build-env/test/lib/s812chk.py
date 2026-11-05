"""W105-W123: audit B sections 8-12 and N54. QSY and Call Activity offsets (N21), Settings: OK checks (N24), idle
timeout (N25), UDP port (N26), frequency text (N27), Rename (N28), control characters; ALL.TXT decode, date and
transmit lines (N29-N31); DIRECTED.TXT relay text, closing and time (N49, N50, N32); TDRIFT (N33); SET_GROUPS (N34);
spots after a dial change (N35); PSK Reporter packets (N37, N38, N41); INFO? with a macro (N54).
Needs fakepsk.py and fakespot.py, started here when not running. s812chk.py <run> [ID,ID]"""
from onair2 import *
import sig, ast, threading, calendar, parts
import xml.etree.ElementTree as ET
assert_avd()
run = int(sys.argv[1])
only = sys.argv[2].split(',') if len(sys.argv) > 2 else None
t = T(run, only)
c = t.call
T.n = (int(time.time()) // 60 % 200) * 26 + 17
HERE = os.path.dirname(os.path.abspath(__file__))
os.chdir(HERE)
os.makedirs('shots', exist_ok=True)
NBSP = '\u00a0'
DIRECTED = '/sdcard/Download/JS8CallLogs/DIRECTED.TXT'
FRAME = 'UeNbK+GGOo00'
DEC = re.compile(r'^\d{4}-\d\d-\d\d (\d\d):(\d\d):(\d\d)(.{3}) (.{4}) (.{4}) ([A-Z])  (.{12}) {9}(\d) {4}(\S.*)$')
DATE = re.compile(r'^\d{4}-\d\d-\d\d \d\d:\d\d:\d\d  [\d.]+ MHz  JS8$')


def want(*ids):
    return not only or any(i in only for i in ids)


def fix(id, ok, note=''):
    if want(id):
        t.mark(id, bool(ok), note)


def reopen_events():
    """After a timed-out row: it may have closed ev, so a new one for the rows after."""
    if isinstance(globals().get('ev'), Events):
        ev.close(); globals()['ev'] = Events()


def part(name, est, *ids):
    """parts.part with this run's results (each row its estimate, a limit 10% over)."""
    return parts.part(name, est, [i for i in ids if want(i)], t.res, t.mark, reopen_events)


# ---- screens ------------------------------------------------------------------------------------------------------

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
    m = re.search(r'<node [^>]*resource-id="[^"]*%s"[^>]*>' % re.escape(rid), xml or dump())
    return m.group(0) if m else ''


def attr(n, a):
    m = re.search(r' %s="([^"]*)"' % a, n)
    return html.unescape(m.group(1)) if m else None


def rect(n):
    m = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n or '')
    return tuple(map(int, m.groups())) if m else None


def text_node(prefix, xml=None):
    """The first node whose text starts with [prefix]."""
    for n in re.findall(r'<node [^>]*>', xml or dump()):
        tx = attr(n, 'text')
        if tx and tx.startswith(prefix):
            return n
    return ''


def rows(xml=None):
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


def tap_item(text_, wait=1.5):
    for down in (True, True, True, False, False, False):
        r = rows()
        for tx, en, b in r:
            if tx.strip() == text_:
                adb('shell', 'input', 'tap', str((b[0] + b[2]) // 2), str((b[1] + b[3]) // 2)); time.sleep(wait)
                return True
        if not r:
            break
        scr = rect(re.findall(r'<node [^>]*>', dump())[0])
        top, bot = max(r[0][2][1], scr[1] + 150), min(r[-1][2][3], scr[3] - 250)
        cx = (r[0][2][0] + r[0][2][2]) // 2
        a, b = (bot - 20, top + 20) if down else (top + 20, bot - 20)
        adb('shell', 'input', 'swipe', str(cx), str(a), str(cx), str(b), '800'); time.sleep(1)
    print('NO ITEM', text_, flush=True)
    return False


def close_menus():
    for _ in range(3):
        if not rows():
            return
        adb('shell', 'input', 'keyevent', '4'); time.sleep(.8)


def at_main(xml=None):
    xml = xml or dump()
    return any(find(xml, k) for k in ('js8_btn_settings', 'js8_tabs', 'js8_tab_0'))


def home():
    """Closes boxes and pages until the main screen shows. Back only when something is open."""
    for _ in range(5):
        xml = dump()
        menu = 'android:id/title' in xml or 'select_dialog' in xml or 'android:id/message' in xml or 'android:id/button1' in xml
        if at_main(xml) and not menu:
            return
        if find(xml, '← Back'):
            tapk('← Back', 1.5)
        else:
            adb('shell', 'input', 'keyevent', '4'); time.sleep(1.2)
    if not at_main():
        start()


def open_station():
    """Settings, then the gear: the Station window lists its pages (ui1.py open_station)."""
    tapk('js8_btn_settings', 2)
    for _ in range(3):
        time.sleep(1)
        xml = dump()
        gear = [n for n in re.findall(r'<node [^>]*>', xml) if 'ImageButton' in n or 'ImageView' in n]
        if gear:
            r = rect(gear[0])
            adb('shell', 'input', 'tap', str((r[0] + r[2]) // 2), str((r[1] + r[3]) // 2))
        for _ in range(8):
            time.sleep(1)
            xml = dump()
            if 'Radio' in xml and 'Audio' in xml:
                return True
    return False


def scroll_find(key, tries=12, exact=True):
    """Swipe the page up (then down) until a node with text [key] shows; its node."""
    for up in (True, False):
        for _ in range(tries):
            xml = dump()
            n = text_node(key, xml) if not exact else next((m for m in re.findall(r'<node [^>]*>', xml) if attr(m, 'text') == key), '')
            r = rect(n)
            if n and r and 150 < (r[1] + r[3]) // 2 < 2000:
                return n
            a, b = ('1500', '700') if up else ('700', '1500')
            adb('shell', 'input', 'swipe', '540', a, '540', b, '400'); time.sleep(1)
    return ''


def page(name):
    return open_station() and bool(scroll_find(name)) and tapk(name, 2)


def tap_node(n, wait=1.0):
    r = rect(n)
    adb('shell', 'input', 'tap', str((r[0] + r[2]) // 2), str((r[1] + r[3]) // 2)); time.sleep(wait)


def type_dialog(value):
    """The open number box: clear it, type [value], OK."""
    tapk('android:id/edit', .5) if find(dump(), 'android:id/edit') else None
    e = next((n for n in re.findall(r'<node [^>]*>', dump()) if 'android.widget.EditText' in n), '')
    if e:
        tap_node(e, .5)
    adb('shell', 'input', 'keyevent', '123')
    for _ in range(8):
        adb('shell', 'input', 'keyevent', '67')
    text(value); time.sleep(.5)
    return tapk('OK', 1.5)


def pref(k):
    m = re.search(r'name="%s"(?: value="([^"]*)"[^>]*/>|>([^<]*)</string>)' % re.escape(k), getprefs())
    return (m.group(1) or m.group(2)) if m else None


def list_cells(rid='js8_call_activity'):
    x = dump()
    lb = rect(node(rid, x))
    out = []
    for n in re.findall(r'<node [^>]*>', x):
        tx, r = attr(n, 'text'), rect(n)
        if tx and r and lb and lb[0] <= r[0] < lb[2] and lb[1] <= r[1] < lb[3]:
            out.append((r, tx))
    return out


def call_row(call):
    cells = list_cells()
    me = [r for r, tx in cells if tx == call]
    if not me:
        return []
    y = (me[0][1] + me[0][3]) // 2
    return [tx for r, tx in sorted(cells, key=lambda q: q[0][0]) if r[1] <= y <= r[3]]


# ---- logs, API, stand-in servers ----------------------------------------------------------------------------------

def directed():
    return adb('shell', 'cat', DIRECTED).decode('utf8', 'replace').splitlines()


def dev_now():
    return int(adb('shell', 'date', '+%s').decode().strip())


def dial():
    for r in api('RIG.GET_FREQ', wait=1):
        if r.get('type') == 'RIG.FREQ':
            return r['params'].get('DIAL')


def inj_mode(text_, f, ty, mode, snr=-10):
    adb('shell', f"""am broadcast -n {PKG}/com.js8call.example.debug.DebugDecodeReceiver --es text "{text_}" --ef freq {f} --ei type {ty} --ei snr {snr} --ei mode {mode}""")


class Events:
    """Every API message, with its arrival time, from a connection held open."""
    def __init__(s):
        adb('forward', 'tcp:2442', 'tcp:2442')
        s.sock = socket.create_connection(('127.0.0.1', 2442), 5); s.sock.settimeout(.3)
        s.buf, s.got, s.stop = b'', [], False
        threading.Thread(target=s.rx, daemon=True).start()

    def rx(s):
        while not s.stop:
            try:
                d = s.sock.recv(65536)
                if not d:
                    break
                s.buf += d
                while b'\n' in s.buf:
                    l, s.buf = s.buf.split(b'\n', 1)
                    try:
                        s.got.append((time.time(), json.loads(l)))
                    except ValueError:
                        pass
            except OSError:
                pass

    def of(s, typ, since=0.0, pred=lambda p: True):
        """(time, params) of each [typ] message since [since]; params '_value' is the message's value (RX.ACTIVITY's text)."""
        out = []
        for tm, j in list(s.got):
            p = dict(j.get('params', {}), _value=j.get('value', ''))
            if tm >= since and j.get('type') == typ and pred(p):
                out.append((tm, p))
        return out

    def wait(s, typ, since, secs, pred=lambda p: True):
        end = time.time() + secs
        while time.time() < end:
            h = s.of(typ, since, pred)
            if h:
                return h[0]
            time.sleep(.2)
        return None

    def close(s):
        s.stop = True; s.sock.close()


SERVERS = []


def serve(script, port, host='0.0.0.0'):
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.bind((host, port)); s.close()
    except OSError:
        return  # already running
    SERVERS.append(subprocess.Popen([sys.executable, script], cwd=HERE, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL))
    time.sleep(1.5)


def psk_lines():
    try:
        return open('fakepsk.log', encoding='utf8', errors='replace').read().splitlines()
    except OSError:
        return []


def packets(n0):
    out = []
    for l in psk_lines()[n0:]:
        m = re.match(r"^\S+ \(.*?\) \d+ (b.*)$", l)
        if m:
            try:
                out.append(ast.literal_eval(m.group(1)))
            except (ValueError, SyntaxError):
                pass
    return out


def seq(b):
    return int.from_bytes(b[8:12], 'big')


def export(b):
    return int.from_bytes(b[4:8], 'big')


def new_packets(n0, secs, count=1):
    end = time.time() + secs
    while time.time() < end:
        p = packets(n0)
        if len(p) >= count:
            return p
        time.sleep(1)
    return packets(n0)


def spot_button():
    return tapk('js8_btn_spot', 2)


def clipboard(value=None):
    """The PC clipboard's text (the emulator shares it); with [value], set it to exactly that text."""
    if value is None:
        r = subprocess.run(['powershell', '-NoProfile', '-Command', '[Console]::OutputEncoding = [Text.Encoding]::UTF8; Get-Clipboard -Raw'],
                           capture_output=True, text=True, encoding='utf8')
        return r.stdout.removesuffix('\n').removesuffix('\r')
    subprocess.run(['powershell', '-NoProfile', '-Command', '[Console]::InputEncoding = [Text.Encoding]::UTF8; Set-Clipboard -Value ([Console]::In.ReadToEnd())'],
                   input=value, capture_output=True, text=True, encoding='utf8')


def dev_clock():
    """The emulator clock to the ms: the PC clock runs ahead of it (44 s on 2026-11-05)."""
    return float(adb('shell', 'date', '+%s.%N').decode().strip())


def wait_mix(secs=25):
    """When the played frame went in (the service logs it at the slot start): (emulator clock, PC clock)."""
    end = time.time() + secs
    while time.time() < end:
        l = [x for x in adb('logcat', '-d', '-v', 'epoch', '-e', 'JS8.test:.*samples').decode('utf8', 'replace').splitlines() if 'JS8 test: ' in x]
        if l:
            d = float(l[0].split()[0])
            return d, d + time.time() - dev_clock()
        time.sleep(.3)
    return None, None


_fresh = fresh


def fresh(**k):
    """onair2 fresh, then until the API answers: the first start after an install is slow to open it."""
    _fresh(**k)
    end = time.time() + 30
    while time.time() < end and not api('STATION.GET_CALLSIGN', wait=1):
        time.sleep(1)


M = pref('callsign') or 'VE3TST'
OK0 = dict(callsign=M, my_groups='@TESTG', js8cfg_cq_message='CQ CQ CQ <MYGRID4>', js8cfg_eot='♢', js8cfg_auto_whitelist='', js8cfg_swl=False,
           rig_control_enabled=False, rig_type='none', js8cfg_ptt_method='vox')
UP = dict(tx_submode=1, js8cfg_reset_activity=True, js8cfg_tx_watchdog='0', js8cfg_api_dynamic_info=False, landscape_layout='pc',
          autoreply_enabled=False, js8cfg_autoreply_startup=False, **OK0)


def okbox(**bad):
    """A bad value in, OK in the Station window: the words on the box that opens ('' if Station closed)."""
    fresh(**{**UP, **bad}); rotate('U'); time.sleep(1)
    open_station()
    tapk('OK', 2)
    xml = html.unescape(dump())
    msg = attr(node('android:id/message', xml), 'text') or ''
    return msg, xml


# ======== Block A: the Station window, upright =======================================================================

if want('W122'):  # N24: OK checks in the PC order, comma lists, letter-digit groups, greyed PTT Method
    with part('W122', 240, 'W122'):
        got = {}
        for k, bad in (('eot_first', dict(js8cfg_eot='', callsign='B@D!')), ('comma', dict(my_groups='@TESTG,')),
                       ('word', dict(my_groups='ABCDEF')), ('order', dict(my_groups='ABCDEF,@APRSIS')), ('at', dict(my_groups='@ABCDEF')),
                       ('space', dict(js8cfg_auto_whitelist='K1ABC K2DEF')), ('commas', dict(js8cfg_auto_whitelist='K1ABC,K2DEF')),
                       ('ptt', dict(js8cfg_ptt_method='cat'))):
            msg, xml = okbox(**bad)
            # no box and the Station pages gone: OK went through (Station returns to the Settings window)
            got[k] = msg if msg else ('saved' if not ('Radio' in xml and 'Audio' in xml) else 'Station still open')
            print('W122', k, repr(got[k]), flush=True)
            home()
        fix('W122', got['eot_first'] == 'Please enter an end of transmission character' and got['comma'] == ' is not a valid group'
            and got['word'] == 'ABCDEF is not a valid group' and got['order'] == 'ABCDEF is not a valid group' and got['at'] == 'saved'
            and got['space'] == 'K1ABC K2DEF is not a valid callsign to whitelist' and got['commas'] == 'saved'
            and got['ptt'] == 'Invalid PTT method', ' | '.join(f'{k}: {v!r}' for k, v in got.items()))
        fresh(**UP)

if want('W106'):  # N26: UDP Server Port takes 65535
    with part('W106', 70, 'W106'):
        fresh(**{**UP, 'js8cfg_udp_port': '4243'}); rotate('U'); time.sleep(1)
        ok = page('Reporting') and bool(scroll_find('4243'))
        ok = ok and tapk('4243', 1.5)
        title = 'UDP Server Port' in html.unescape(dump())
        type_dialog('65535')
        shown = bool(scroll_find('65535'))
        tapk('← Back', 1.5); tapk('OK', 2)
        p = pref('js8cfg_udp_port')
        fix('W106', ok and title and shown and p == '65535', f'box opened {ok}, titled {title}; shows 65535 {shown}; saved {p!r}')
        home()
        fresh(**{**UP, 'js8cfg_udp_port': None})

if want('W107'):  # N27: frequency text in the Frequencies and Schedule tables
    with part('W107', 80, 'W107'):
        fresh(**{**UP, 'js8cfg_frequencies': '7078000|All|JS8|W107A\n6000000|All|JS8|W107B', 'js8cfg_schedule': '7078000|01:00|02:00|W107S'})
        rotate('U'); time.sleep(1)
        page('Frequencies')
        seen = set()
        for _ in range(8):
            seen.update(html.unescape(t_) for t_ in re.findall(r' text="([^"]*)"', dump()))
            adb('shell', 'input', 'swipe', '540', '1500', '540', '900', '400'); time.sleep(1)
        f1, f2, s1 = f'7.078{NBSP}000 MHz (40m)', f'6.000{NBSP}000 MHz (OOB)', f'7.078{NBSP}000 MHz'
        screen = adb('exec-out', 'screencap', '-p'); open(f'shots/w107_{run}.png', 'wb').write(screen)
        fix('W107', f1 in seen and f2 in seen and s1 in seen and 'Frequency' in seen and 'Frequency (MHz)' not in seen and 'Freq. (MHz)' in seen,
            f'{f1!r} {f1 in seen}; {f2!r} {f2 in seen}; schedule {s1!r} {s1 in seen}; Frequency (MHz) column {"Frequency (MHz)" in seen}')
        home()
        fresh(**{**UP, 'js8cfg_frequencies': None, 'js8cfg_schedule': None})

if want('W109'):  # control characters refused in Reply, CQ, Status, Info and Callsign Groups; another box keeps them
    with part('W109', 110, 'W109'):
        BOX = dict(my_info='W109I', my_status='W109S', my_groups='@W109G', js8cfg_cq_message='CQ W109C', js8cfg_reply_message='W109R',
                   js8cfg_auto_whitelist='W1ABC')
        fresh(**{**UP, **BOX}); rotate('U'); time.sleep(1)
        page('General')
        keep = clipboard()
        clipboard('Q\tZ7')
        after = {}
        for k, v in BOX.items():
            n = scroll_find(v)
            if not n:
                after[k] = None
                continue
            tap_node(n, .8)
            adb('shell', 'input', 'keyevent', '123'); adb('shell', 'input', 'keyevent', '279'); time.sleep(1)
            after[k] = attr(text_node(v), 'text')
            adb('shell', 'input', 'keyevent', '111'); time.sleep(.5)
        clipboard(keep)  # the PC clipboard's text back
        home()  # Back without OK: nothing saved
        refused = all(after[k] == v for k, v in BOX.items() if k != 'js8cfg_auto_whitelist')
        fix('W109', refused and after['js8cfg_auto_whitelist'] == 'W1ABCQ\tZ7', f'{after}')
        fresh(**{**UP, 'js8cfg_reply_message': None})

if want('W110'):  # N25: idle timeout 2 minutes used until the next start, then 5
    with part('W110', 220, 'W110'):
        fresh(**{**UP, 'autoreply_enabled': True, 'js8cfg_tx_watchdog': '2'}); rotate('U'); time.sleep(2)
        raised = pref('js8cfg_tx_watchdog')
        page('General')
        n = scroll_find('5 minutes of inactivity')
        ok = bool(n) and (tap_node(n, 1.5) or True) and type_dialog('2')
        shown = bool(scroll_find('2 minutes of inactivity'))
        tapk('← Back', 1.5); tapk('OK', 2); home()
        t0, x_ = time.time(), ''
        while time.time() < t0 + 240:  # no touches: a touch restarts the idle time
            x_ = html.unescape(dump())
            if 'Idle Timeout' in x_:
                break
            time.sleep(5)
        secs = int(time.time() - t0)
        box2 = 'You have been idle for more than 2 minutes.' in x_
        tapk('OK', 2)
        stop(); start(); time.sleep(3)
        again = pref('js8cfg_tx_watchdog')
        fix('W110', raised == '5' and ok and shown and box2 and 100 <= secs <= 160 and again == '5',
            f'stored 2 at start -> {raised!r}; typed 2 {ok and shown}; box after {secs} s says 2 minutes {box2}; next start {again!r}')
        fresh(**UP)

# ======== Block B: API and injected decodes, PC layout ===============================================================

with part('restart sideways', 45):
    fresh(**UP)  # after an emulator restart the home screen keeps the screen upright: the app open first
    print('sideways', rotate('P'), flush=True); time.sleep(3)

if want('W118'):  # N34: STATION.SET_GROUPS checks the names as sent
    with part('W118', 20, 'W118'):
        def groups_reply(g):
            for r in api('STATION.SET_GROUPS', params={'GROUPS': g}, wait=1.5):
                if r.get('type') == 'STATION.SET_GROUPS':
                    return r['params'].get('ERROR') or r['params'].get('GROUPS')
        r1, r2, r3, r4 = groups_reply(['@w118x']), groups_reply(['@ALLCALL/X']), groups_reply(['@aprsis']), groups_reply(['@APRSIS'])
        r5 = groups_reply(['@TESTG', ' @W118 ', 'NOAT'])
        p = pref('my_groups')
        fix('W118', r1 == 'Invalid group name: @w118x' and r2 == 'Invalid group name: @ALLCALL/X' and r3 == 'Invalid group name: @aprsis'
            and r4 == 'Group not allowed: @APRSIS' and sorted(r5 or []) == ['@TESTG', '@W118'] and p == '@TESTG,@W118',
            f'@w118x {r1!r}; @ALLCALL/X {r2!r}; @aprsis {r3!r}; @APRSIS {r4!r}; good {r5}, stored {p!r}')
        api('STATION.SET_GROUPS', params={'GROUPS': ['@TESTG']}, wait=1)

with part('events', 10):
    ev = Events()

if want('W114'):  # N49: relay text "K4YYY> ..." in DIRECTED.TXT and RX.DIRECTED
    with part('W114', 20, 'W114'):
        a = c()
        body = f'K4ZZZ HELLO{run}'
        d0, t0 = len(directed()), time.time()
        inj(f'{a}: K4YYY> {body} {sum16(body)}', 1500, 3); time.sleep(4)
        g = [l for l in directed()[d0:] if a in l]
        e = ev.of('RX.DIRECTED', t0, lambda p: p.get('FROM') == a)
        tx = e[-1][1].get('TEXT') if e else None
        fix('W114', len(g) == 1 and g[0].endswith(f'{a}: K4YYY> {body} ♢ ') and tx == f'K4YYY> {body} ♢ ' and e[-1][1].get('CMD') == '>',
            f'DIRECTED.TXT {g}; RX.DIRECTED {tx!r} CMD {e[-1][1].get("CMD") if e else None!r}')

if want('W116'):  # N32: DIRECTED.TXT time from the drifted clock
    with part('W116', 50, 'W116'):
        ev.close()
        fresh(**{**UP, 'time_drift_ms': Long(90000)}); time.sleep(3)
        ev = Events()
        a = c()
        d0 = len(directed())
        inj(f'{a}: K1ABC ACK', 1800, 3); now = dev_now(); time.sleep(4)
        g = [l for l in directed()[d0:] if a in l]
        import calendar
        lt = calendar.timegm(time.strptime(g[0].split('\t')[0], '%Y-%m-%d %H:%M:%S')) if g else 0
        fix('W116', bool(g) and 85 <= lt - now <= 95, f'line time - phone time {lt - now} s (drift +90 s); {g[:1]}')
        ev.close()
        fresh(**{**UP, 'time_drift_ms': Long(0)}); time.sleep(3)
        ev = Events()

if want('W117'):  # N33: TDRIFT is the clock drift on Normal and Slow with auto sync, the decode's offset otherwise
    with part('W117', 100, 'W117'):
        def tdrift(mode, directed_=False):
            a = c(); t0 = time.time()
            if directed_:
                inj_mode(f'{a}: K1ABC ACK', 1700, 3, mode)
                h = ev.wait('RX.DIRECTED', t0, 8, lambda p: p.get('FROM') == a)
            else:
                inj_mode(f'{a}: @ALLCALL CQ CQ CQ FN31', 1600, 3, mode)
                h = ev.wait('RX.ACTIVITY', t0, 8, lambda p: a in str(p.get('_value', '')))
            return h[1].get('TDRIFT') if h else None
        ev.close()
        fresh(**{**UP, 'js8cfg_auto_drift_on': True, 'time_drift_ms': Long(1500), 'tx_submode': 0}); time.sleep(3)
        ev = Events()
        on_n, on_s, on_f, on_d = tdrift(0), tdrift(4), tdrift(1), tdrift(0, True)
        ev.close()
        fresh(**{**UP, 'js8cfg_auto_drift_on': False, 'time_drift_ms': Long(1500), 'tx_submode': 0}); time.sleep(3)
        ev = Events()
        off_n = tdrift(0)
        fix('W117', on_n == 1.5 and on_s == 1.5 and on_f == 0.0 and on_d == 1.5 and off_n == 0.0,
            f'auto sync on: Normal {on_n}, Slow {on_s}, Fast {on_f}, RX.DIRECTED {on_d}; off: Normal {off_n} (drift 1.5 s, decode offset 0)')
        ev.close()
        fresh(**{**UP, 'js8cfg_auto_drift_on': False, 'time_drift_ms': Long(0)}); time.sleep(3)
        ev = Events()

if want('W123'):  # N54: INFO? and STATUS? answered when the text is a macro that fills in empty
    with part('W123', 110, 'W123'):
        ev.close()
        fresh(**{**UP, 'autoreply_enabled': True, 'js8cfg_autoreply_startup': True, 'my_info': '<CALL>', 'my_status': '<CALL>'}); time.sleep(3)
        ev = Events()
        a, b = c(), c(1)
        base = len(alltxt())
        inj(f'{a}: {M} INFO?', 1500, 3)
        hi = t.tx(base, rf'{a} INFO', 40)
        time.sleep(8); base = len(alltxt())
        inj(f'{b}: {M} STATUS?', 1500, 3)
        hs = t.tx(base, rf'{b} STATUS', 40)
        fix('W123', bool(hi) and bool(hs), f'INFO {hi[0].split("JS8:")[-1].strip() if hi else "not sent"!r}; '
            f'STATUS {hs[0].split("JS8:")[-1].strip() if hs else "not sent"!r}')
        time.sleep(8)
        ev.close()
        fresh(**UP); time.sleep(3)
        ev = Events()

if want('W115'):  # N50: closed 60 s after its latest frame when a text frame came; dropped at 90 s with none
    with part('W115', 130, 'W115'):
        a, b = c(), c(1)
        d0, t0 = len(directed()), time.time()
        inj(f'{a}: K1ABC', 1600, 1); inj(f'{b}: K1ABC', 1700, 1)
        time.sleep(30)
        inj(f'W115 PART {run}', 1600, 4); t2 = time.time()
        when_a = None
        while time.time() < t0 + 110:
            g = directed()[d0:]
            if when_a is None and any(a in l for l in g):
                when_a = int(time.time() - t2)
            time.sleep(2)
        g = directed()[d0:]
        ga, gb = [l for l in g if a in l], [l for l in g if b in l]
        fix('W115', when_a is not None and 58 <= when_a <= 66 and len(ga) == 1 and f'K1ABC  W115 PART {run}' in ga[0] and not gb,
            f'two frames, no last: written {when_a} s after the second frame {ga}; one frame: {gb or "not written"}')

if want('W105'):  # N21: QSY moves Call Activity offsets
    with part('W105', 50, 'W105'):
        a = c()
        # the PC layout's Callsigns list shows 4 rows: emptied first, so the one station is in view
        close_menus(); tapk('js8_menubar_control', 1.2); tap_item('Clear Activity...', 1.2); tapk('Clear Call Activity')  # the submenu opens late: tapk waits for it
        inj(f'{a}: @ALLCALL CQ CQ CQ FN31', 1500, 3, -6); time.sleep(3)
        api('RIG.SET_FREQ', params={'OFFSET': 1800}, wait=1); time.sleep(1)
        turned_on = False
        if not find(dump(), 'js8_wf_qsy'):
            tapk('js8_menubar_view', 1.2); tap_item('Show Waterfall Controls'); turned_on = True; time.sleep(1)
        r0, d_0 = call_row(a), dial()
        tapk('js8_wf_qsy', 2)
        r1, d_1 = call_row(a), dial()
        open(f'shots/w105_{run}.png', 'wb').write(adb('exec-out', 'screencap', '-p'))
        delta = (d_1 or 0) - (d_0 or 0)
        num = lambda r: [int(re.match(r'(-?\d+)', x).group(1)) for x in r if re.fullmatch(r'-?\d+( Hz)?', x)]
        # the dial moves by offset - center (1800 - the Center box); every Call Activity offset by the same amount
        fix('W105', delta != 0 and 1500 in num(r0) and (1500 - delta) in num(r1) and 1500 not in num(r1),
            f'dial moved {delta} Hz; {a} row before {r0}, after {r1}')
        if turned_on:
            tapk('js8_menubar_view', 1.2); tap_item('Show Waterfall Controls')

if want('W108'):  # N28: Rename: Old name, an empty New name box, OK greyed while the name is in use, / and \ blocked
    with part('W108', 50, 'W108'):
        close_menus()
        tapk('js8_menubar_configurations', 1.2); tap_item('Default'); tap_item('Rename ...', 1.5)
        x0 = html.unescape(dump())
        open(f'shots/w108a_{run}.png', 'wb').write(adb('exec-out', 'screencap', '-p'))  # the box before the keyboard covers it
        ok0 = attr(node('android:id/button1', x0), 'enabled')
        e = next((n for n in re.findall(r'<node [^>]*>', x0) if 'android.widget.EditText' in n), '')
        box0 = attr(e, 'text') if e else None
        tap_node(e, .5) if e else None
        text('Default'); time.sleep(.8)
        ok_taken = attr(node('android:id/button1'), 'enabled')
        for _ in range(8):
            adb('shell', 'input', 'keyevent', '67')
        text('W108/X'); time.sleep(.8)
        x1 = html.unescape(dump())
        e1 = next((n for n in re.findall(r'<node [^>]*>', x1) if 'android.widget.EditText' in n), '')
        ok_new, box1 = attr(node('android:id/button1', x1), 'enabled'), attr(e1, 'text')
        open(f'shots/w108_{run}.png', 'wb').write(adb('exec-out', 'screencap', '-p'))
        tapk('Cancel', 1.5)
        fix('W108', 'New Configuration Name' in x0 and 'Old name:' in x0 and 'New name:' in x0 and box0 == '' and ok0 == 'false'
            and ok_taken == 'false' and box1 == 'W108X' and ok_new == 'true',
            f'title and labels {"New Configuration Name" in x0 and "Old name:" in x0}; box {box0!r}; OK at start {ok0}, '
            f'for "Default" {ok_taken}; typed W108/X -> {box1!r}, OK {ok_new}')
        home()

if want('W113'):  # N30: each transmit line holds the text of its own frame
    with part('W113', 150, 'W113'):
        ev.close()
        fresh(**{**UP, 'tx_submode': 2}); time.sleep(3)
        ev = Events()
        msg = f'W113 EACH FRAME OWN TEXT {run} ALPHA BRAVO CHARLIE'
        base = len(alltxt())
        api('TX.SEND_MESSAGE', msg, wait=1)
        end = time.time() + 90
        while time.time() < end:
            tl = [l.split('JS8:', 1)[-1][2:] for l in alltxt()[base:] if 'Transmitting' in l]
            if tl and 'CHARLIE' in ''.join(tl):
                break
            time.sleep(2)
        tl = [l.split('JS8:', 1)[-1][2:] for l in alltxt()[base:] if 'Transmitting' in l]
        joined = ''.join(tl)
        fix('W113', len(tl) >= 2 and all(msg not in l for l in tl) and all(w in joined for w in msg.split()),
            f'{len(tl)} lines: {tl}')
        time.sleep(6)
        ev.close()
        fresh(**UP); time.sleep(3)
        ev = Events()

ev.close()

# ======== Block C: real audio, PC layout, one speed (Normal) ==========================================================

AUDIO = dict(UP, tx_submode=0, js8cfg_multi=False, js8cfg_filter_enabled=False, psk_reporter=False)
tn = None
if want('W111', 'W112', 'W119'):
    with part('tones', 110, 'W111', 'W112', 'W119'):
        fresh(**AUDIO); time.sleep(3)
        for _ in range(4):
            tn = sig.tones(FRAME, 1, 0); time.sleep(18)
            if tn:
                break
        print('tones', (tn or '')[:16], flush=True)


def play_frame():
    adb('logcat', '-c')
    sig.play([(tn, 1500), (None, 0)], 'a.pcm', 0)
    return wait_mix()


def erase_ready():
    """Log > Erase ALL.TXT, up to its Yes box: where Yes is, so the erase is one quick tap."""
    tapk('js8_menubar_log', 1.2); tap_item('Erase ALL.TXT', 1.5)
    for _ in range(5):
        p = find(dump(), 'Yes')
        if p:
            return p
        time.sleep(1)
    print('MISSING Yes', flush=True)


def tap_at(p):
    if p:
        adb('shell', 'input', 'tap', str(p[0]), str(p[1]))
    return bool(p)


def erase_all():
    ok = tap_at(erase_ready()); time.sleep(2); return ok


def frame_lines(n0, since, secs=30):
    """ALL.TXT from line [n0], once the decode of the frame that went in at [since] (emulator clock) is in it,
    about 14 s after the slot start; an older decode of the same frame does not count."""
    def ours(x):
        return DEC.match(x) and FRAME in x and calendar.timegm(time.strptime(x[:19], '%Y-%m-%d %H:%M:%S')) >= int(since or 0) - 1
    end = time.time() + secs
    while time.time() < end:
        l = alltxt()[n0:]
        if any(ours(x) for x in l):
            return l
        time.sleep(1)
    return alltxt()[n0:]


if want('W111', 'W112') and tn:
    with part('W111,W112', 200, 'W111', 'W112'):
        fresh(**AUDIO); time.sleep(8)
        # 1: erased just after the frame goes in: after the last decode cycle ended, before the frame's decode (PC clears
        # the date line at every decode cycle's end, so the erase must fall between the two): date line, then the decode
        yes = erase_ready()
        S, B = play_frame()
        erased = tap_at(yes); te = dev_clock()
        l1 = frame_lines(0, S)
        print('W112 frame in', S, 'erased', te, flush=True)
        # 2: a later decode: no date line
        n1 = len(l1)
        S2, _ = play_frame(); l2 = frame_lines(n1, S2)
        # 3: erased, then a send: no date line before a transmit line
        erase_all(); time.sleep(1)
        api('TX.SEND_MESSAGE', f'W112 {run}', wait=1)
        end = time.time() + 40
        while time.time() < end and not any('Transmitting' in l for l in alltxt()):
            time.sleep(1)
        l3 = alltxt()
        print('W112 lines', l1[:3], l2[:3], l3[:2], flush=True)
        dec1 = [i for i, l in enumerate(l1) if DEC.match(l)]
        dec2 = [i for i, l in enumerate(l2) if DEC.match(l)]
        m = DEC.match(l1[dec1[0]]) if dec1 else None
        if want('W111'):
            # PC: hh:mm:ss of the period start, the SNR right after it, the 12-character frame, 4 spaces before the text
            ok = bool(m) and m.group(8) == FRAME and m.group(7) == 'A' and int(m.group(3)) % 15 == 0 \
                and re.fullmatch(r' *-?\d+', m.group(4)) is not None and m.group(9) in '0123'
            hh = int(m.group(1)) * 3600 + int(m.group(2)) * 60 + int(m.group(3)) if m else -1
            gap = (int(S) - hh) % 86400 if S else 9
            fix('W111', ok and min(gap, 86400 - gap) <= 2, f'line {l1[dec1[0]] if dec1 else "no decode"!r}; slot start {time.strftime("%H:%M:%S", time.gmtime(S)) if S else None}')
        if want('W112'):
            date_first = bool(dec1) and dec1[0] == 1 and DATE.match(l1[0]) is not None
            none_later = bool(dec2) and not any(DATE.match(l) for l in l2)
            tx_first = bool(l3) and 'Transmitting' in l3[0] and not DATE.match(l3[0])
            fix('W112', erased and date_first and none_later and tx_first,
                f'after erase {l1[:2]}; later decode {l2[:2]}; after erase and a send {l3[:1]}')
        time.sleep(10)

if want('W119') and tn:  # N35: a spot waits 4/5 of a period after a dial change, with the dial at decode
    with part('W119', 150, 'W119'):
        serve('fakepsk.py', 4739); serve('fakespot.py', 50000, '127.0.0.1')
        # the frame's sender is VE3TST: another callsign here, or it is our own call and never spotted (air13.py real_spot)
        fresh(**{**AUDIO, 'psk_reporter': True, 'debug_spot_host': '10.0.2.2', 'callsign': 'K4SPOT'}); time.sleep(8)
        ev = Events()
        d0 = dial(); d1, d2 = d0 + 2000, d0 + 4000
        n0 = len(psk_lines())
        S, B = play_frame()
        time.sleep(max(0, (B or time.time()) + 8 - time.time()))
        api('RIG.SET_FREQ', params={'DIAL': d1}, wait=.5); tc1 = time.time()
        act = ev.wait('RX.ACTIVITY', tc1, 20)
        api('RIG.SET_FREQ', params={'DIAL': d2}, wait=.5); tc2 = time.time()
        sp = ev.wait('RX.SPOT', tc1 - 1, 30)
        held = int(sp[0] - tc2) if sp else None
        spot_button(); time.sleep(6)  # spotting off sends what waits
        pk = new_packets(n0, 10)
        # the spot's 5-byte frequency: dial + the decode's offset (within a few Hz of 1500)
        in_pk = any((d1 + f).to_bytes(5, 'big') in p for p in pk for f in range(1490, 1511))
        wrong = any((d2 + f).to_bytes(5, 'big') in p for p in pk for f in range(1490, 1511))
        fix('W119', bool(act) and sp is not None and 11 <= held <= 16 and sp[1].get('DIAL') == d1 and in_pk and not wrong,
            f'decode {act is not None}; RX.SPOT {held} s after the second dial change, DIAL {sp[1].get("DIAL") if sp else None} '
            f'(dial at decode {d1}, now {d2}); PSK packet has dial at decode {in_pk}, new dial {wrong}')
        ev.close()
        api('RIG.SET_FREQ', params={'DIAL': d0}, wait=.5)
        fresh(**AUDIO)  # the callsign back

if want('W120'):  # N38, N41: one sequence number per packet from 1; drifted export time; sends with an empty grid
    with part('W120', 100, 'W120'):
        serve('fakepsk.py', 4739)
        g0 = pref('grid')
        # an empty grid keeps the Station window open until OK; SWL lets it go (and stops sending, not spotting)
        fresh(**{**UP, 'psk_reporter': True, 'time_drift_ms': Long(30000), 'grid': '', 'js8cfg_swl': True}); time.sleep(6)
        home()
        n0 = len(psk_lines())
        spot_button(); p1 = new_packets(n0, 12); tp1 = dev_clock()  # the export time is the app's clock, not the PC's
        spot_button(); time.sleep(3)
        spot_button(); p2 = new_packets(n0, 12, 2)
        s_ = [seq(p) for p in p2]
        ex = export(p2[0]) - int(tp1) if p2 else None
        fix('W120', len(p2) == 2 and s_ == [1, 2] and ex is not None and 25 <= ex <= 35,
            f'{len(p2)} packets with no spots and an empty grid; sequence {s_}; export time - PC time {ex} s (drift +30 s)')
        fresh(**{**UP, 'psk_reporter': False, 'time_drift_ms': Long(0), 'grid': g0})

if want('W121'):  # N37: no spots: nothing at 2, 4 and 6 minutes; the 4th report time sends the station record
    with part('W121', 580, 'W121'):
        serve('fakepsk.py', 4739)
        fresh(**{**UP, 'psk_reporter': True}); t0 = time.time()
        n0 = len(psk_lines())
        seen_at = []
        while time.time() < t0 + 4 * 125 + 25:
            p = packets(n0)
            if len(p) > len(seen_at):
                seen_at.append(int(time.time() - t0))
            time.sleep(2)
        p = packets(n0)
        fix('W121', len(p) == 1 and 4 * 120 - 10 <= seen_at[0] <= 4 * 125 + 15 and seq(p[0]) == 1,
            f'packets at {seen_at} s after start; sequence {[seq(x) for x in p]}')
        fresh(**{**UP, 'psk_reporter': False})

def colour_of(label):
    """The middle pixel's colour of the node with text [label] (r, g, b), or None."""
    n = next((m for m in re.findall(r'<node [^>]*>', dump()) if attr(m, 'text') == label), '')
    r = rect(n)
    if not r:
        return None
    from PIL import Image
    import io
    im = Image.open(io.BytesIO(adb('exec-out', 'screencap', '-p'))).convert('RGB')
    return im.getpixel((r[0] + 10, (r[1] + r[3]) // 2))  # left of the words, on the button colour


def reddish(c):
    return bool(c) and c[0] > 2 * c[1] + 20 and c[0] > 2 * c[2] + 20


def list_items(xml=None):
    """The open picker's rows: (text, enabled)."""
    x = html.unescape(xml or dump())
    return [(attr(n, 'text'), attr(n, 'enabled')) for n in re.findall(r'<node [^>]*>', x)
            if 'CheckedTextView' in n or 'android:id/text1' in n]


def setting_box(label):
    """The drop-down box beside [label] on an open Settings page: tapped, the picker's rows."""
    n = scroll_find(label)
    if not n:
        return []
    ns = re.findall(r'<node [^>]*>', dump())
    i = next((k for k, m in enumerate(ns) if attr(m, 'text') == label), None)
    box = next((m for m in ns[i + 1:] if attr(m, 'text').endswith('▾')), '') if i is not None else ''
    if not box:
        return []
    tap_node(box, 1.2)
    r = list_items()
    adb('shell', 'input', 'keyevent', '4'); time.sleep(.8)
    return r


if want('W125'):  # Rename: a name typed and deleted turns OK on, as the PC (MultiSettings.cpp 92-96)
    with part('W125', 40, 'W125'):
        rotate('P'); home(); close_menus()  # the Configurations menu bar is the sideways PC layout's (W108)
        tapk('js8_menubar_configurations', 1.2); tap_item('Default'); tap_item('Rename ...', 1.5)
        x0 = dump()
        ok0 = attr(node('android:id/button1', x0), 'enabled')
        e = next((n for n in re.findall(r'<node [^>]*>', x0) if 'android.widget.EditText' in n), '')
        tap_node(e, .5) if e else None
        text('X'); time.sleep(.8)
        adb('shell', 'input', 'keyevent', '67'); time.sleep(.8)
        adb('shell', 'input', 'keyevent', '4'); time.sleep(1)  # the keyboard away, so the screenshot shows the box
        x1 = dump()
        e1 = next((n for n in re.findall(r'<node [^>]*>', x1) if 'android.widget.EditText' in n), '')
        ok_empty, box1 = attr(node('android:id/button1', x1), 'enabled'), attr(e1, 'text')
        open(f'shots/w125_{run}.png', 'wb').write(adb('exec-out', 'screencap', '-p'))
        tapk('Cancel', 1.5)
        fix('W125', ok0 == 'false' and box1 == '' and ok_empty == 'true',
            f'OK at start {ok0}; X typed and deleted -> box {box1!r}, OK {ok_empty}')
        home()

if want('W126'):  # N55: a directed frame's text as the PC's DecodedText, "VE3TST: K4ABC> " (tryUnpackDirected)
    with part('W126', 90, 'W126'):
        fresh(**UP); time.sleep(3)
        a, b_ = c(), c(1)
        base = len(alltxt())
        api('TX.SEND_MESSAGE', f'{a}>{b_} W126 {run}', wait=1)
        end = time.time() + 60
        while time.time() < end and not any(f'W126 {run}' in j for j in tx_joined(alltxt()[base:])):
            time.sleep(2)
        tl = [l.split('JS8:', 1)[-1][2:] for l in alltxt()[base:] if 'Transmitting' in l]
        first = tl[0] if tl else ''
        fix('W126', first.startswith(f'{M}: {a}>') and f'{a} >' not in first and any(f'W126 {run}' in j for j in tx_joined(alltxt()[base:])),
            f'{len(tl)} lines, first {first!r}')
        time.sleep(6)

if want('W127'):  # N22: Test PTT greyed until Test CAT is green (cpp 4515-4521); RTS PTT (PC Rig None, PTT RTS) live
    with part('W127', 140, 'W127'):
        HAM_ = dict(rig_control_enabled=True, rig_type='hamlib_usb', rig_hamlib_model='3073', rig_hamlib_usb_port='auto',
                    js8cfg_ptt_method='dtr')
        got = {}
        for name, k in (('Hamlib IC-7300, DTR', HAM_), ('RTS PTT', dict(rig_control_enabled=True, rig_type='rts_ptt', js8cfg_ptt_method='rts')),
                        ('None', dict(rig_control_enabled=False, rig_type='none', js8cfg_ptt_method='vox'))):
            fresh(**{**UP, **k}); rotate('U'); time.sleep(2)
            page('Radio'); scroll_find('Test PTT')
            before = attr(next((n for n in re.findall(r'<node [^>]*>', dump()) if attr(n, 'text') == 'Test PTT'), ''), 'enabled')
            after_cat = None
            if name.startswith('Hamlib'):
                tapk('Test CAT', 1)
                end = time.time() + 20  # the app's Test CAT gives up after 15 s (RIG_TEST_TIMEOUT_MS)
                while time.time() < end and not reddish(colour_of('Test CAT')):
                    time.sleep(1)
                got['Test CAT red'] = reddish(colour_of('Test CAT'))
                scroll_find('Test PTT')
                after_cat = attr(next((n for n in re.findall(r'<node [^>]*>', dump()) if attr(n, 'text') == 'Test PTT'), ''), 'enabled')
                open(f'shots/w127_{run}.png', 'wb').write(adb('exec-out', 'screencap', '-p'))
            got[name] = (before, after_cat)
            tapk('Cancel', 1.5); home()
        fresh(**UP)
        fix('W127', got.get('Test CAT red') and got['Hamlib IC-7300, DTR'] == ('false', 'false') and got['RTS PTT'][0] == 'true' and got['None'][0] == 'false',
            f'Test PTT enabled (before, after a failed Test CAT): {got}')

if want('W128'):  # N23: channel lists as the PC's update_audio_channels (cpp 4747-4783)
    with part('W128', 90, 'W128'):
        fresh(**{**UP, 'js8cfg_output_device': 'default'}); rotate('U'); time.sleep(2)
        page('Audio')
        inp = setting_box('Input channel:')
        out = setting_box('Output channel:')
        open(f'shots/w128_{run}.png', 'wb').write(adb('exec-out', 'screencap', '-p'))
        tapk('Cancel', 1.5); home()
        ins = dict(inp); outs = dict(out)
        fix('W128', list(ins) == ['Mono', 'Left', 'Right', 'Both'] and ins['Both'] == 'false'
            and outs == {'Mono': 'true', 'Left': 'false', 'Right': 'false', 'Both': 'false'},
            f'Input channel {inp}; Output channel (phone\'s current output) {out}')

for s_ in SERVERS:
    s_.terminate()
json.dump(t.res, open(f's812chk.{run}.json', 'w'))
print('RESULT ' + ' '.join(f'{i}={"PASS" if ok else "FAIL"}' for i, ok, _ in t.res), flush=True)
