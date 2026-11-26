"""W167-W168 (2026-11-17), Station OK and the APRS Beacon GPS line (Easy JS8 only). gpschk.py <run> [ID,ID]
W167: Station OK saves and stays on the page (Rob 2026-11-17): sideways Pages (Station in the screen) and the upright
      Station window. The value is saved; ← Back still leaves (page -> list -> window closed).
W175 (2026-11-18, runs with W168): upright Station window with a fix, the GPS line shows in full (two lines if needed).
W168: Station > APRS Beacon GPS line, one line (two upright if needed), never turns the GPS on itself:
      beacon off -> "GPS: off (APRS beacon off)"; location off -> "GPS: turned off in phone settings";
      beacon on, no position yet -> "GPS: searching..."; a fix -> "GPS: fix HH:MM:SS UTC (m:ss ago), ±N m, <10-char grid>",
      the age counting up; location permission taken away -> "GPS: no location permission"."""
from onair2 import *
import html, threading
assert_avd()
os.makedirs('shots', exist_ok=True)

run = int(sys.argv[1])
only = sys.argv[2].split(',') if len(sys.argv) > 2 else None
res = []
LAT, LON = 43.6532, -79.3832


def mark(id, ok, note=''):
    print(id, 'PASS' if ok else 'FAIL', note, flush=True)
    res.append((id, ok, note))


def want(id):
    return not only or id in only


def grid10(lat, lon):
    lon_m, lat_m = (lon + 180) * 60, (lat + 90) * 60
    a, b = int(lon_m // 1200), int(lat_m // 600); lon_m -= a * 1200; lat_m -= b * 600
    c, d = int(lon_m // 120), int(lat_m // 60); lon_m -= c * 120; lat_m -= d * 60
    e, f = int(lon_m // 5), int(lat_m // 2.5); lon_m -= e * 5; lat_m -= f * 2.5
    g, h = int(lon_m // .5), int(lat_m // .25); lon_m -= g * .5; lat_m -= h * .25
    i, j = int(lon_m // (.5 / 24)), int(lat_m // (.25 / 24))
    L = 'ABCDEFGHIJKLMNOPQRSTUVWX'
    return L[a] + L[b] + str(c) + str(d) + L[e] + L[f] + str(g) + str(h) + L[i] + L[j]


class Feeder:
    """The emulator's GPS: one position a second, 8 satellites."""
    def __init__(self):
        self.on = True
        self.t = threading.Thread(target=self.loop, daemon=True); self.t.start()

    def loop(self):
        while self.on:
            adb('emu', 'geo', 'fix', f'{LON:.6f}', f'{LAT:.6f}', '0', '8')
            time.sleep(1)

    def stop(self):
        self.on = False


def setup(mode, grant=True):
    if grant:
        adb('shell', 'pm', 'grant', PKG, 'android.permission.ACCESS_FINE_LOCATION')
        adb('shell', 'pm', 'grant', PKG, 'android.permission.ACCESS_COARSE_LOCATION')
    adb('shell', 'cmd', 'location', 'set-location-enabled', 'true')
    setprefs(**{**BASE, 'landscape_layout': 'swipe', 'autoreply_enabled': False, 'js8cfg_hb_ack': False, 'js8cfg_hb': False,
                'js8cfg_aprs_beacon': mode})
    clearlog()
    adb('shell', 'input', 'keyevent', '224')
    start(); time.sleep(3)
    beacon_on(mode)


def rotate(code):
    adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0')
    for _ in range(5):
        adb('shell', 'settings', 'put', 'system', 'user_rotation', '0' if code == 'U' else '1')
        time.sleep(2)
        m = re.search(r'cur=(\d+)x(\d+)', adb('shell', 'dumpsys', 'window', 'displays').decode('utf8', 'replace'))
        if (int(m.group(1)) < int(m.group(2))) == (code == 'U'):
            return True
    return False


def beacon_page(wait=1.5):
    """Station page list > APRS Beacon. Since the 2026-11-24 Android list it can sit below the screen: swipe the
    list up (left pane sideways) until it shows."""
    for _ in range(4):
        if find(dump(), 'APRS Beacon'):
            break
        x = '500' if rotate_state[0] == 'L' else '540'
        adb('shell', 'input', 'swipe', x, '800', x, '350', '400'); time.sleep(1)
    return tapk('APRS Beacon', wait)


def node_text(rid):
    for n in re.findall(r'<node [^>]*>', dump()):
        if re.search(r'resource-id="[^"]*/%s"' % rid, n):
            t = re.search(r' text="([^"]*)"', n)
            return html.unescape(t.group(1)) if t else ''
    return None


def gps():
    """The GPS row's status read back as the old line: since 2026-11-26 the row shows "Off (APRS beacon off)" under
    the name GPS, the app's "GPS: off (APRS beacon off)" without its start, first letter upper case."""
    t = node_text('js8_aprs_gps')
    return 'GPS: ' + t[0].lower() + t[1:] if t else t


def gps_height():
    """Height in px of the GPS line on screen, None if not shown."""
    for n in re.findall(r'<node [^>]*>', dump()):
        if re.search(r'resource-id="[^"]*/js8_aprs_gps"', n):
            a, b, c, d = map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n).groups())
            return d - b
    return None


def pref(key):
    m = re.search(r'name="%s">([^<]*)<' % key, getprefs())
    return m.group(1) if m else None


def pick(now, choice):
    tapk(f'{now}  ▾', 1.5) or tapk(now, 1.5)
    for k in range(8):
        if find(dump(), choice):
            break
        y0, y1 = ('700', '300') if k < 4 else ('300', '700')
        w = adb('shell', 'wm', 'size').decode()
        x = '540' if 'x2340' in w and rotate_state[0] == 'U' else '1170'
        adb('shell', 'input', 'swipe', x, y0, x, y1, '400'); time.sleep(.8)
    tapk(choice, 1.5)


rotate_state = ['L']


def open_station_window():
    """Upright: Settings, then the gear (Station window, its own screen)."""
    tapk('js8_btn_settings', 2)
    for _ in range(3):
        time.sleep(1)
        gear = [n for n in re.findall(r'<node [^>]*>', dump()) if 'content-desc="Station"' in n]
        if gear:
            a, b, c, d = map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', gear[0]).groups())
            adb('shell', 'input', 'tap', str((a + c) // 2), str((b + d) // 2)); time.sleep(2)
            return True
    return False


feed = None
try:
    if want('W167'):
        with part('W167a sideways Pages OK', 60, ['W167'], res, mark):
            setup('0')
            rotate('L'); rotate_state[0] = 'L'
            tapk('Station', 2)
            beacon_page(1.5)
            pick('Off', '5 minutes')
            tapk('OK', 2.5)
            st = texts()
            stay_a = 'APRS beacon:' in st and gps() is not None
            saved_a = pref('js8cfg_aprs_beacon') == '5'
            open(f'shots/gps.{run}.sideways-ok.png', 'wb').write(adb('exec-out', 'screencap', '-p'))
        with part('W167b upright window OK and Back', 70, ['W167'], res, mark):
            setprefs(js8cfg_aprs_beacon='0'); start()
            rotate('U'); rotate_state[0] = 'U'
            opened = open_station_window()
            beacon_page(1.5)
            pick('Off', '10 minutes')
            tapk('OK', 2.5)
            st = texts()
            stay_b = 'APRS beacon:' in st and gps() is not None
            saved_b = pref('js8cfg_aprs_beacon') == '10'
            open(f'shots/gps.{run}.upright-ok.png', 'wb').write(adb('exec-out', 'screencap', '-p'))
            tapk('← Back', 1.5)
            on_list = 'Radio' in texts() and 'APRS beacon:' not in texts()
            tapk('← Back', 2)
            st = texts()
            left = 'APRS beacon:' not in st and not ('Radio' in st and 'Reporting' in st)
            mark('W167', stay_a and saved_a and opened and stay_b and saved_b and on_list and left,
                 f'sideways: stays {stay_a}, saved {saved_a}; upright window opened {opened}: stays {stay_b}, saved {saved_b}, '
                 f'Back -> list {on_list}, Back -> closed {left}')
            setprefs(js8cfg_aprs_beacon='0')

    if want('W168'):
        got = {}
        with part('W168 beacon off, location off', 50, ['W168'], res, mark):
            setup('0')
            rotate('L'); rotate_state[0] = 'L'
            tapk('Station', 2); beacon_page(1.5)
            got['off'] = gps()
            adb('shell', 'cmd', 'location', 'set-location-enabled', 'false'); time.sleep(2.5)
            got['locoff'] = gps()
            open(f'shots/gps.{run}.locoff.png', 'wb').write(adb('exec-out', 'screencap', '-p'))
            adb('shell', 'cmd', 'location', 'set-location-enabled', 'true')
        with part('W168 searching, then a fix', 75, ['W168'], res, mark):
            # Android's test GPS provider: no position until the test sends one (the emulator's own GPS answers at once)
            adb('shell', 'cmd', 'location', 'providers', 'add-test-provider', 'gps')
            adb('shell', 'cmd', 'location', 'providers', 'set-test-provider-enabled', 'gps', 'true')
            setup('2')  # the first fix is wanted at once
            rotate('L')
            tapk('Station', 2); beacon_page(1.5)
            time.sleep(2)
            got['search'] = gps()
            open(f'shots/gps.{run}.searching.png', 'wb').write(adb('exec-out', 'screencap', '-p'))
            adb('shell', 'cmd', 'location', 'providers', 'set-test-provider-location', 'gps',
                '--location', f'{LAT:.6f},{LON:.6f}', '--accuracy', '5')
            fix = None
            for _ in range(30):
                t = gps() or ''
                if t.startswith('GPS: fix'):
                    fix = t; break
                time.sleep(1)
            time.sleep(4)
            got['fix'] = fix
            got['fix2'] = gps()
            open(f'shots/gps.{run}.fix.png', 'wb').write(adb('exec-out', 'screencap', '-p'))
            h_side = gps_height()
        if want('W175'):
            with part('W175 upright fix line on two lines', 45, ['W175'], res, mark):
                # Rob 2026-11-18: the fix line was cut off upright on the S22 Ultra; two lines when it does not fit
                rotate('U'); rotate_state[0] = 'U'
                opened = open_station_window()
                beacon_page(1.5); time.sleep(1.5)
                t_up, h_up = gps(), gps_height()
                open(f'shots/gps.{run}.upright-fix.png', 'wb').write(adb('exec-out', 'screencap', '-p'))
                fixre_ = r'GPS: fix \d\d:\d\d:\d\d UTC \(\d+:\d\d ago\), ±\d+ m, ' + grid10(LAT, LON) + '$'
                # two lines: upright the line is at least 1.5 times its one-line height sideways (same text size; the
                # 8 dp padding counts once, so two lines measured 126 px against 75 px for one on S22-Fresh)
                # 2026-11-26: the GPS row drops "GPS: " and uses Android's font, so on S22-Fresh the fix line fits on one line;
                # the row still allows two. Full text, and upright never shorter than sideways (checked by eye too).
                mark('W175', opened and bool(re.match(fixre_, t_up or '')) and bool(h_up) and bool(h_side) and h_up >= h_side,
                     f'window {opened}; text {t_up!r}; height upright {h_up} px, sideways {h_side} px')
                adb('shell', 'input', 'keyevent', '4'); time.sleep(1.5); adb('shell', 'input', 'keyevent', '4'); time.sleep(2)
                rotate('L'); rotate_state[0] = 'L'
        adb('shell', 'cmd', 'location', 'providers', 'remove-test-provider', 'gps')
        with part('W168 no permission', 40, ['W168'], res, mark):
            adb('shell', 'pm', 'revoke', PKG, 'android.permission.ACCESS_FINE_LOCATION')
            adb('shell', 'pm', 'revoke', PKG, 'android.permission.ACCESS_COARSE_LOCATION')
            setup('0', grant=False)
            rotate('L')
            tapk('Station', 2); beacon_page(1.5)
            open(f'shots/gps.{run}.noperm.png', 'wb').write(adb('exec-out', 'screencap', '-p'))
            got['noperm'] = gps()
            adb('shell', 'pm', 'grant', PKG, 'android.permission.ACCESS_FINE_LOCATION')
            adb('shell', 'pm', 'grant', PKG, 'android.permission.ACCESS_COARSE_LOCATION')
        print('LINES', *[f'{k}: {v}' for k, v in got.items()], sep='\n  ', flush=True)
        age = lambda t: (lambda m: int(m.group(1)) * 60 + int(m.group(2)) if m else None)(re.search(r'\((\d+):(\d\d) ago\)', t or ''))
        fixre = r'GPS: fix \d\d:\d\d:\d\d UTC \(\d+:\d\d ago\), ±\d+ m, ' + grid10(LAT, LON) + '$'
        a1, a2 = age(got.get('fix')), age(got.get('fix2'))
        # the emulator sends a position every second, so the age stays near 0 while positions keep coming;
        # after the beacon's fix the GPS is off (2 minutes), so the age counts up
        ok = got.get('off') == 'GPS: off (APRS beacon off)' \
            and got.get('locoff') == 'GPS: turned off in phone settings' \
            and (got.get('search') or '').startswith('GPS: searching') \
            and bool(re.match(fixre, got.get('fix') or '')) and bool(re.match(fixre, got.get('fix2') or '')) \
            and a1 is not None and a2 is not None and a2 >= a1 + 3 \
            and got.get('noperm') == 'GPS: no location permission'
        mark('W168', ok, f'age {a1} -> {a2} s')
finally:
    if feed: feed.stop()
    adb('shell', 'cmd', 'location', 'providers', 'remove-test-provider', 'gps')
    adb('shell', 'cmd', 'location', 'set-location-enabled', 'true')
    adb('shell', 'settings', 'put', 'system', 'user_rotation', '0')
    print('RESULT', ' '.join(f'{i}:{"PASS" if ok else "FAIL"}' for i, ok, _ in res), flush=True)
