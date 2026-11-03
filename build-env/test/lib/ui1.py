"""Screens, one layout at a time. ui1.py <run> [layout U, P, G or F] [section letters, e.g. HLMN]. JS8_ROWS=H01U,M02U runs only those rows."""
from onair2 import *
assert_avd()
run = int(sys.argv[1])
LAYS = (sys.argv[2] if len(sys.argv) > 2 else 'U') or 'U'
if len(LAYS) != 1:
    sys.exit('one layout at a time (Rob, 2026-11-02)')
ONLY = set(filter(None, os.environ.get('JS8_ROWS', '').split(',')))
SECS = sys.argv[3] if len(sys.argv) > 3 else 'HLMN'
res = []
L = 'U'
os.makedirs('shots', exist_ok=True)
MAIN = ('js8_btn_settings', 'js8_tabs', 'js8_tab_0')


def layout(code):
    global L
    L = code
    adb('forward', 'tcp:2442', 'tcp:2442')
    prefs = dict(landscape_layout={'U': 'pc', 'P': 'pc', 'G': 'swipe', 'F': 'ft8call'}[code], js8cfg_transmit_directed=False,
                 autoreply_enabled=False, js8cfg_autoreply_startup=False, js8cfg_multi=True, js8cfg_hb=True, tx_submode=0, js8cfg_reset_activity=True, js8cfg_wf_fps=4, js8cfg_tx_watchdog='0')
    x_ = getprefs()
    stale = {k: None for k in re.findall(r'name="(js8cfg_up_split_[^"]*)"', x_)}
    setprefs(**{**BASE, **prefs, **stale})
    start()
    rotate(code)
    time.sleep(2)


def rotated(code):
    m = re.search(r'cur=(\d+)x(\d+)', adb('shell', 'dumpsys', 'window', 'displays').decode('utf8', 'replace'))
    return (int(m.group(1)) < int(m.group(2))) == (code == 'U')


def rotate(code):
    """With the reader running, a plain user_rotation change can snap back to upright, so it is rechecked."""
    adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0')
    for _ in range(5):
        adb('shell', 'settings', 'put', 'system', 'user_rotation', '0' if code == 'U' else '1')
        time.sleep(2)
        if rotated(code):
            return True
        print('ROTATION RETRY', flush=True)
    return False


def x(): return dump()
def on(k, xml=None): return find(xml or x(), k) is not None


def has(*keys):
    seen = ''
    for i in range(4):
        xml = x()
        seen += xml
        if all(k in seen for k in keys):
            return True
        m = [n for n in re.findall(r'<node [^>]*>', xml) if 'android:id/title' in n]
        if not m:
            return False
        a, b, c, d = map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', m[len(m) // 2]).groups())
        ax, top, bot = (a + c) // 2, int(re.search(r'\[\d+,(\d+)\]', m[0]).group(1)) + 20, (b + d) // 2 + 150
        adb('shell', 'input', 'swipe', str(ax), str(bot), str(ax), str(top), '350')
        time.sleep(.9)
    return False


def at_main():
    xml = x()
    return any(find(xml, k) for k in MAIN)


def anr():
    xml = x()
    if "isn't responding" in html.unescape(xml):
        print('ANR dialog seen', flush=True)
        shot(f'anr_{int(time.time())}')
        tapk('Wait', 2)
        return True
    return False


def home():
    """Closes a menu or page. Back only when something is open: Back on the main screen closes the app."""
    for _ in range(4):
        anr()
        xml = x()
        menu = 'android:id/title' in xml or 'select_dialog' in xml or 'android:id/message' in xml
        if any(find(xml, k) for k in MAIN) and not menu:
            return
        if not xml.strip():
            time.sleep(2)
            continue
        adb('shell', 'input', 'keyevent', '4')
        time.sleep(1.2)
    if not at_main():
        start()


def scroll_up():
    adb('shell', 'input', 'swipe', '540', '330', '540', '100', '200')


def reach(k):
    """Brings control k on screen in the current layout."""
    if on(k):
        return True
    if L == 'G' and k.startswith('js8_wf_'):
        tapk('js8_tab_2', 1)
        if on(k):
            return True
    elif L == 'G':
        for i in range(5):
            tapk(f'js8_tab_{i}', 1)
            if on(k):
                return True
    if L == 'U':
        for down in (0, 1):
            for _ in range(4):
                xml = x()
                g = find(xml, 'js8_btn_hb') or find(xml, 'js8_btn_tune') or (540, 500)
                ex = max(100, g[1] - 500) if not down else min(2100, g[1] + 800)
                adb('shell', 'input', 'swipe', str(g[0]), str(g[1]), str(g[0]), str(ex), '150')
                time.sleep(1.2)
                if on(k):
                    return True
    if k.startswith('js8_wf_'):
        for sy, ey in ((300, 60), (60, 300)):
            for _ in range(6):
                xml = x()
                t_ = next((find(xml, i) for i in re.findall(r'id/(js8_wf_[a-z_]+)"', xml) if find(xml, i)), None)
                if not t_:
                    return False
                top, bot = (1500, 1950) if L == 'U' else (640, 880)  # inside the panel, clear of the bottom edge gesture
                adb('shell', 'input', 'swipe', str(t_[0]), str(bot if sy > ey else top), str(t_[0]), str(top if sy > ey else bot), '300')
                time.sleep(1)
                if on(k):
                    return True
    return on(k)


def tp(k, wait=1.5):
    return reach(k) and tapk(k, wait)


def holdk(k):
    if not reach(k):
        return False
    p = find(x(), k)
    adb('shell', 'input', 'swipe', str(p[0]), str(p[1]), str(p[0]), str(p[1]), '900')
    time.sleep(1.5)
    return True


def rec(row, ok, note=''):
    rid = f'{row}{L}'
    res.append((rid, bool(ok), note))
    print(rid, 'PASS' if ok else 'FAIL', note, flush=True)
    if not ok:
        shot(f'fail_{rid}_{run}')


def check(row, fn, note=''):
    if ONLY and row + L not in ONLY and row not in ONLY:
        return
    if not rotated(L):
        print(row, 'ROTATION CHANGED', flush=True)
        rotate(L)
    try:
        rec(row, fn(), note)
    except Exception as e:
        rec(row, False, repr(e)[:90])
    home()


def opens(k, *expect):
    def f():
        return tp(k, 2) and has(*expect)
    return f


def holds(k, *expect):
    def f():
        return holdk(k) and has(*expect)
    return f


def select():
    inj('K4AQA: @ALLCALL CQ CQ CQ FN31', 1234, 3, -5)
    time.sleep(2.5)
    reach('js8_call_activity')
    return tapk('K4AQA', 1.5)


def boxtext():
    p = re.search(r'<node [^>]*js8_typing_box[^>]*>', x())
    return html.unescape(re.search(r' text="([^"]*)"', p.group(0)).group(1)) if p else ''


def clearbox():
    api('TX.SET_TEXT', '', wait=1)
    time.sleep(.6)


def fills(k, pat):
    def f():
        select()
        ok = tp(k, 1.5) and re.search(pat, boxtext())
        note = boxtext()
        clearbox()
        tp('js8_btn_deselect', 1)
        return ok
    return f


def sec_h():
    check('H01', lambda: has('js8_callsign', 'js8_clock') or reach('js8_callsign'))
    check('H03', lambda: reach('js8_btn_rx'))
    check('H04', lambda: reach('js8_btn_tx'))
    check('H05', opens('js8_btn_mode', 'JS8 Fast'))
    check('H06', lambda: opens('js8_btn_settings', 'Save', 'Cancel')() and tapk('Cancel'))
    check('H07', lambda: reach('js8_btn_spot'))
    check('H08', lambda: opens('js8_btn_log', 'Add to Log')() and tapk('Cancel'))
    check('H10', holds('js8_btn_hb', 'Send Heartbeat Now'))
    check('H11', holds('js8_btn_cq', 'Send CQ Now'))
    check('H12', fills('js8_btn_reply', r'K4AQA'))
    check('H13', fills('js8_btn_snr', r'K4AQA SNR'))
    check('H14', fills('js8_btn_info', r'INFO'))
    check('H15', fills('js8_btn_status', r'STATUS|IDLE'))
    check('H16', opens('js8_btn_saved', 'Edit Saved Messages…'))
    check('H17', lambda: opens('js8_btn_saved', 'Edit Saved Messages…')() and tapk('Edit Saved Messages…', 2.5) and has('Saved Messages'))
    for row, name in (('H18', 'APRS EMAIL'), ('H19', 'APRS TXT'), ('H20', 'APRS GRID'), ('H21', 'APRS MSG')):
        check(row, opens('js8_btn_saved', name))
    check('H22', lambda: select() and tp('js8_btn_directed', 2) and has('SNR'))
    check('H23', lambda: select() and tp('js8_btn_deselect', 1.5))
    check('H24', lambda: reach('js8_btn_send'))
    check('H25', lambda: reach('js8_btn_halt'))
    check('H26', lambda: select() and 'OUTGOING DIRECTED MESSAGE TO K4AQA' in x())
    check('H32', lambda: reach('js8_message_window'))
    check('H33', holds('js8_message_window', 'Copy'))
    check('H34', holds('js8_message_window', 'Clear'))
    check('H36', holds('js8_typing_box', 'Restore Previous Message'))


SETTINGS = [('L01', 'Enable Simultaneous Decoding of All Speeds (MULTI)'), ('L02', 'Enable Autoreply'), ('L03', 'Enable Heartbeat Networking (HB)'),
            ('L04', 'Enable Heartbeat Acknowledgments (ACK)'), ('L05', 'JS8 Normal'), ('L06', 'Frequency:'), ('L07', 'Custom frequency (MHz):'),
            ('L08', 'Offset:'), ('L10', 'Show Band Heartbeats and ACKs'), ('L11', 'Show Offset in Band Activity'), ('L12', 'Show SNR in Band Activity'),
            ('L13', 'Show Column Labels in Band Activity'), ('L14', 'Show FT8 decodes in the Message Window'), ('L16', 'Clear Band Activity'),
            ('L17', 'Clear Call Activity'), ('L18', 'Clear RX Activity'), ('L19', 'Clear All Activity'), ('L20', 'Configurations...'), ('L21', 'Exit'),
            ('L22', 'Copyright notice'), ('L23', 'Check for Updates'), ('L24', 'JS8Call User Guide'), ('L25', 'Save'), ('L26', 'Cancel')]


def scrolled_find(*words):
    seen = ''
    p = None
    for _ in range(9):
        seen += x()
        if all(w in seen for w in words):
            return True
        adb('shell', 'input', 'swipe', '600', '800' if L == 'U' else '700', '600', '300' if L == 'U' else '250', '350')
        time.sleep(.7)
    print('  missing', [w for w in words if w not in seen], flush=True)
    return False


def sec_l():
    tp('js8_btn_settings', 2)
    seen = ''
    h = adb('shell', 'wm', 'size')
    for _ in range(10):
        seen += x()
        adb('shell', 'input', 'swipe', '600', '900' if L == 'U' else '700', '600', '300' if L == 'U' else '250', '350')
        time.sleep(.7)
    for row, w in SETTINGS:
        rec(row, html.escape(w, quote=False) in seen or w in seen)
    home()


STATION = {'General': ('My Callsign:', 'Callsign Groups', 'Station Messages', 'Display distance in miles', 'Heartbeat interval', 'Autoreply'),
           'Radio': ('Rig:', 'Test CAT', 'Test PTT'), 'Audio': ('Input:', 'Input channel:', 'Output channel:', 'Remember power settings by band'),
           'Reporting': ('Enable spotting to reporting networks', 'APRS Server', 'UDP Server Port', 'N3FJP Logger', 'N1MM Logger'),
           'Frequencies': ('Slope:', 'Intercept:', 'Frequency Schedule'), 'Saved Messages': (), 'Notifications': ('Enable Notifications',),
           'UI': (), 'Diagnostics': ('Run Diagnostics',), 'Phone': ('Keep screen on',)}


def open_station():
    tp('js8_btn_settings', 2)
    for _ in range(3):
        time.sleep(1)
        xml = x()
        gear = [n for n in re.findall(r'<node [^>]*>', xml) if 'ImageButton' in n or 'ImageView' in n]
        if gear:
            a, b, c, d = map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', gear[0]).groups())
            adb('shell', 'input', 'tap', str((a + c) // 2), str((b + d) // 2))
        for _ in range(8):
            time.sleep(1)
            xml = x()
            if 'Radio' in xml and 'Audio' in xml:
                return True
    return False


def sec_m():
    if not open_station():
        print('no Station', flush=True)
        shot(f'nostation_{L}')
        return
    shot(f'station_{L}')
    rows = {'General': 'M01', 'Radio': 'M18', 'Audio': 'M25', 'Reporting': 'M30', 'Frequencies': 'M34', 'Saved Messages': 'M39', 'Notifications': 'M40', 'UI': 'M41', 'Diagnostics': 'M43', 'Phone': 'M44'}
    for page, words in STATION.items():
        if not tapk(page, 2):
            rec(rows[page], False, 'page missing')
            continue
        seen = ''
        for _ in range(9):
            seen += x()
            adb('shell', 'input', 'swipe', '600', '900' if L == 'U' else '700', '600', '300' if L == 'U' else '250', '350')
            time.sleep(.6)
        miss = [w for w in words if w not in seen]
        rec(rows[page], not miss, 'missing ' + str(miss) if miss else page)
        shot(f'st_{page.split()[0]}_{L}')
        for _ in range(3):
            if 'Diagnostics' in x() and 'Audio' in x():
                break
            tapk('← Back', 1.5)
    home()


def sec_n():
    if L in 'PF':
        check('N01', opens('js8_menubar_file', 'Station…', 'Exit'))
        check('N02', opens('js8_menubar_configurations', 'Default'))
        check('N08', opens('js8_menubar_view', 'Show Message Inbox...', 'Show Fullscreen'))
        check('N09', opens('js8_menubar_mode', 'JS8 Fast'))
        check('N10', opens('js8_menubar_control', 'Send Heartbeat...'))
        check('N11', opens('js8_menubar_log', 'Add Log Entry...', 'Open Log Directory...'))
        check('N12', opens('js8_menubar_help', 'Copyright notice', 'About'))
        check('N13', opens('js8_menubar_view', 'Show Fullscreen'))
        check('N14', opens('js8_menubar_view', 'Show Time Drift Controls...'))
        check('N15', opens('js8_menubar_log', 'Open Log Directory...'))
    else:
        for r in ('N01', 'N08', 'N09', 'N10', 'N11', 'N12', 'N13', 'N14', 'N15'):
            res.append((f'{r}{L}', None, 'N/A: no menu bar'))


for code in LAYS:
    layout(code)
    shot(f'main_{code}_{run}')
    clearbox()
    if 'H' in SECS: sec_h()
    if 'L' in SECS: sec_l()
    if 'M' in SECS: sec_m()
    if 'N' in SECS: sec_n()
adb('shell', 'settings', 'put', 'system', 'user_rotation', '0')
json.dump(res, open(f'ui1.{run}.json', 'w'))
print('DONE', flush=True)
