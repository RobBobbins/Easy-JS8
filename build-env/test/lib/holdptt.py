"""W176 (Rob, 2026-11-23, Easy JS8 only): Station > Radio > Advanced, Hold PTT between frames greyed when the app can't
key the rig: Rig None, or a Hamlib rig with PTT Method VOX. Usable with RTS PTT and truSDX even on VOX. Picking DTR on
the page makes it usable at once, VOX greys it again; greyed it keeps its tick. holdptt.py <run> <layout U or P>
Screenshots shots/hp_<layout>_<state>_<run>.png."""
from onair2 import *
assert_avd()
os.makedirs('shots', exist_ok=True)
run = int(sys.argv[1])
L = sys.argv[2] if len(sys.argv) > 2 else 'U'
assert L in ('U', 'P')
res = []
LABEL = 'Hold PTT between frames'


def mark(i, ok, note=''):
    print(i, 'PASS' if ok else 'FAIL', note, flush=True)
    res.append((i, bool(ok), note))


def rotate():
    adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0')
    for _ in range(5):
        adb('shell', 'settings', 'put', 'system', 'user_rotation', '0' if L == 'U' else '1')
        time.sleep(2)
        m = re.search(r'cur=(\d+)x(\d+)', adb('shell', 'dumpsys', 'window', 'displays').decode('utf8', 'replace'))
        if (int(m.group(1)) < int(m.group(2))) == (L == 'U'):
            return True
    return False


def open_station():
    tapk('js8_btn_settings', 2)
    for _ in range(3):
        time.sleep(1)
        gear = [n for n in re.findall(r'<node [^>]*>', dump()) if 'ImageButton' in n or 'ImageView' in n]
        if gear:
            a, b, c, d = map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', gear[0]).groups())
            adb('shell', 'input', 'tap', str((a + c) // 2), str((b + d) // 2))
        for _ in range(8):
            time.sleep(1)
            xml = dump()
            if 'Radio' in xml and 'Audio' in xml:
                return True
    return False


def hold():
    """(enabled, checked) of the Hold PTT checkbox, or None when not on screen. Upright the CheckBox carries the
    label; sideways the label is a TextView above an empty CheckBox."""
    nodes = re.findall(r'<node [^>]*>', dump())
    for i, n in enumerate(nodes):
        t = html.unescape((re.search(r' text="([^"]*)"', n) or [None, ''])[1])
        if t.startswith(LABEL):
            box = n if 'CheckBox' in n else next((m for m in nodes[i + 1:] if 'CheckBox' in m), '')
            b = re.search(r'bounds="\[\d+,(\d+)\]\[\d+,(\d+)\]"', box)
            if not box or not b or not (150 < (int(b.group(1)) + int(b.group(2))) // 2 < (2150 if L == 'U' else 1000)):
                return None
            return ('enabled="true"' in box, 'checked="true"' in box)
    return None


def swipe(up):
    if L == 'U':
        a, b = ('1600', '900') if up else ('900', '1600')
        adb('shell', 'input', 'swipe', '600', a, '600', b, '350')
    else:
        a, b = ('780', '350') if up else ('350', '780')
        adb('shell', 'input', 'swipe', '1200', a, '1200', b, '350')
    time.sleep(.8)


def bring(fn):
    """Swipes the page up, then down, until fn() is not None."""
    for up in (True, False):
        for _ in range(10):
            r = fn()
            if r is not None:
                return r
            swipe(up)
    return fn()


def radio_page(**k):
    setprefs(**{**BASE, 'landscape_layout': 'pc', 'autoreply_enabled': False, 'js8cfg_autoreply_startup': False, 'js8cfg_hb': False,
                'js8cfg_tx_watchdog': '0', 'psk_reporter': False, 'js8cfg_hold_ptt': True, **k})
    start(); rotate(); time.sleep(1)
    return open_station() and tapk('Radio', 2)


def pick(word):
    """Taps the PTT Method radio button [word] (VOX, DTR) on the page."""
    def at():
        n = next((m for m in re.findall(r'<node [^>]*>', dump()) if 'RadioButton' in m and f' text="{word}"' in m), '')
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        if not b:
            return None
        a, y0, c, y1 = map(int, b.groups())
        return ((a + c) // 2, (y0 + y1) // 2) if 150 < (y0 + y1) // 2 < (2150 if L == 'U' else 1000) else None
    p = bring(at)
    if not p:
        return False
    adb('shell', 'input', 'tap', str(p[0]), str(p[1])); time.sleep(1.5)
    return True


got = {}
NONE = dict(rig_control_enabled=False, rig_type='none', js8cfg_ptt_method='vox')
HAM = dict(rig_control_enabled=True, rig_type='hamlib_usb', rig_hamlib_model='3073', js8cfg_ptt_method='vox')
try:
    for name, k, est in (('none', NONE, 45), ('hamlib_vox', HAM, 80),
                         ('rts_vox', dict(rig_control_enabled=True, rig_type='rts_ptt', js8cfg_ptt_method='vox'), 45),
                         ('trusdx_vox', dict(rig_control_enabled=True, rig_type='trusdx_serial', js8cfg_ptt_method='vox'), 45)):
        with part(f'W176 {L} {name}', est, (), res, mark):
            opened = radio_page(**k)
            got[name] = bring(hold) if opened else 'Station did not open'
            shot(f'hp_{L}_{name}_{run}')
            print(name, got[name], flush=True)
            if name == 'hamlib_vox':
                got['hamlib_dtr'] = bring(hold) if pick('DTR') else 'DTR not found'
                shot(f'hp_{L}_hamlib_dtr_{run}')
                print('hamlib_dtr', got['hamlib_dtr'], flush=True)
                got['hamlib_vox_again'] = bring(hold) if pick('VOX') else 'VOX not found'
                shot(f'hp_{L}_hamlib_vox_again_{run}')
                print('hamlib_vox_again', got['hamlib_vox_again'], flush=True)
    want = {'none': (False, True), 'hamlib_vox': (False, True), 'hamlib_dtr': (True, True), 'hamlib_vox_again': (False, True),
            'rts_vox': (True, True), 'trusdx_vox': (True, True)}
    mark(f'W176{L}', all(got.get(n) == w for n, w in want.items()), f'(enabled, ticked): {got}')
finally:
    stop()
    setprefs(**NONE)
    start()
    adb('shell', 'settings', 'put', 'system', 'user_rotation', '0')
    print('RESULT', ' '.join(f'{i}:{"PASS" if ok else "FAIL"}' for i, ok, _ in res), flush=True)
