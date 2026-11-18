"""W160-W164 (2026-11-17), APRS beacon, Station > General (Easy JS8 only). beaconchk.py <run> [ID,ID]
W160: Station has an APRS Beacon page after Reporting (its own page, Rob 2026-11-17; General no longer has it): APRS beacon: Off / 2 / 5 / 10 / 15 / 30 minutes / 1 hour /
      SmartBeaconing; the SmartBeaconing settings greyed unless SmartBeaconing is picked. Off: no GPS, no beacon.
W161: every 2 minutes: a GPS fix, then "@APRSIS GRID <10-character grid>" queued for the transmitter, the grid in
      My Maidenhead Grid Locator; the next fix starts before beacon time by as long as the last fix took; the next
      beacon goes with the screen off.
W162: no new GPS fix (location turned off): at beacon time "Beacon time: waiting for the GPS fix"; the GPS keeps trying
      (45 s, no beacon, no skip); location back on: the beacon goes out at the fix, the next one 2 minutes after it.
W163: SmartBeaconing: a beacon on the first fix, one by rate at 100 km/h (highway rate 1 minute), one on a 90 degree
      turn (least time between turn beacons 30 seconds), none while stopped.
W164: a scheduled heartbeat goes out with the screen off after a transmission that finished with the screen off
      (2026-11-17 fix: the "message queued" flag was copied from the screen only while it was on, and stuck).
W166: Station > APRS Beacon status window (Rob 2026-11-17): every 2 minutes, then SmartBeaconing, then every 5 minutes,
      picked on the page and OK; the window lists their lines in order with UTC times, the last line counts down
      live, and a drag inside the window scrolls the window, not the page.
Positions come from the emulator's GPS (adb emu geo fix); the app's log lines are read with logcat."""
from onair2 import *
import math, threading, io, html
from PIL import Image, ImageChops
assert_avd()
os.makedirs('shots', exist_ok=True)

run = int(sys.argv[1])
only = sys.argv[2].split(',') if len(sys.argv) > 2 else None
res = []


def mark(id, ok, note=''):
    print(id, 'PASS' if ok else 'FAIL', note, flush=True)
    res.append((id, ok, note))


def want(id):
    return not only or id in only


LAT, LON = 43.6532, -79.3832


def grid10(lat, lon):
    """Maidenhead, 10 characters (the app's Js8Grid.fromLatLon(..., 10) worked out separately)."""
    lon_m, lat_m = (lon + 180) * 60, (lat + 90) * 60
    a, b = int(lon_m // 1200), int(lat_m // 600); lon_m -= a * 1200; lat_m -= b * 600
    c, d = int(lon_m // 120), int(lat_m // 60); lon_m -= c * 120; lat_m -= d * 60
    e, f = int(lon_m // 5), int(lat_m // 2.5); lon_m -= e * 5; lat_m -= f * 2.5
    g, h = int(lon_m // .5), int(lat_m // .25); lon_m -= g * .5; lat_m -= h * .25
    i, j = int(lon_m // (.5 / 24)), int(lat_m // (.25 / 24))
    L = 'ABCDEFGHIJKLMNOPQRSTUVWX'
    return L[a] + L[b] + str(c) + str(d) + L[e] + L[f] + str(g) + str(h) + L[i] + L[j]


class Feeder:
    """Sends the emulator a GPS position every second; [step] moves it (metres east, metres north) each second."""
    def __init__(self, test_provider=False):
        """test_provider: positions through Android's test GPS provider (no speed or direction: the app works them
        out from the positions); the emulator's own GPS always reports direction 0."""
        self.lat, self.lon, self.east, self.north, self.on, self.tp = LAT, LON, 0.0, 0.0, True, test_provider
        self.t = threading.Thread(target=self.loop, daemon=True); self.t.start()

    def loop(self):
        while self.on:
            self.lat += self.north / 111320
            self.lon += self.east / (111320 * math.cos(math.radians(self.lat)))
            kn = math.hypot(self.east, self.north) * 3600 / 1852
            if self.tp:
                adb('shell', 'cmd', 'location', 'providers', 'set-test-provider-location', 'gps',
                    '--location', f'{self.lat:.6f},{self.lon:.6f}', '--accuracy', '5')
            else:
                adb('emu', 'geo', 'fix', f'{self.lon:.6f}', f'{self.lat:.6f}', '0', '8', f'{kn:.1f}')
            time.sleep(1)

    def stop(self):
        self.on = False


def log():
    return [l for l in logtext().splitlines() if 'AprsBeacon' in l or 'APRS beacon' in l or 'Heartbeat' in l]


def wait_for(pat, secs, after=0):
    """Waits for a log line matching pat; [after] = matches to skip. Returns (line, seconds waited) or (None, secs)."""
    t0 = time.time()
    while time.time() - t0 < secs:
        hits = [l for l in log() if re.search(pat, l)]
        if len(hits) > after:
            return hits[after], time.time() - t0
        time.sleep(1)
    return None, secs


def setup(mode, **k):
    adb('shell', 'pm', 'grant', PKG, 'android.permission.ACCESS_FINE_LOCATION')
    adb('shell', 'pm', 'grant', PKG, 'android.permission.ACCESS_COARSE_LOCATION')
    adb('shell', 'cmd', 'location', 'set-location-enabled', 'true')
    setprefs(**{**BASE, 'landscape_layout': 'swipe', 'autoreply_enabled': False, 'js8cfg_hb_ack': False, 'js8cfg_hb': False,
                'js8cfg_aprs_beacon': mode, 'js8cfg_sb_fast_speed': None, 'js8cfg_sb_fast_rate': None,
                'js8cfg_sb_slow_speed': None, 'js8cfg_sb_slow_rate': None, 'js8cfg_sb_turn_angle': None,
                'js8cfg_sb_turn_slope': None, 'js8cfg_sb_turn_time': None, **k})
    clearlog()
    adb('shell', 'input', 'keyevent', '224')  # screen on
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


def grid_pref():
    m = re.search(r'name="grid">([^<]*)<', getprefs())
    return m.group(1) if m else None


def node(label):
    for n in re.findall(r'<node [^>]*>', dump()):
        if f'text="{label}"' in n:
            return n
    return None


def page_to(label, down=True, n=8):
    """Scrolls the settings page by its left margin (x 170), outside the APRS Beacon status window."""
    for _ in range(n):
        if node(label):
            return True
        y0, y1 = ('800', '400') if down else ('400', '800')
        adb('shell', 'input', 'swipe', '170', y0, '170', y1, '400'); time.sleep(.8)
    return False


def scroll_to(label, n=12):
    for _ in range(n):
        if node(label):
            return True
        adb('shell', 'input', 'swipe', '1200', '800', '1200', '400', '400'); time.sleep(.8)
    return False


feed = Feeder()
try:
    if want('W160'):
        with part('W160 settings and Off', 75, ['W160'], res, mark):
            setup('0')
            time.sleep(15)
            quiet = not [l for l in log() if 'GPS fix' in l or 'Beacon' in l]
            rotate('L')
            tapk('Station', 2)
            pages = [t for t in texts() if t in ('Reporting', 'APRS Beacon', 'Frequencies')]
            order = pages == ['Reporting', 'APRS Beacon', 'Frequencies']
            tapk('General', 1.5)
            on_general = scroll_to('APRS beacon:', 6)
            tapk('← Back', 1.5) or tapk('Back', 1.5)
            tapk('APRS Beacon', 1.5)
            found = scroll_to('APRS beacon:')
            adb('exec-out', 'screencap', '-p')
            open(f'shots/beacon.{run}.settings.png', 'wb').write(adb('exec-out', 'screencap', '-p'))
            page_to('100 km/h')
            greyed = 'enabled="false"' in (node('100 km/h') or '')
            page_to('Off  ▾', down=False)
            tapk('Off  ▾', 1.5) or tapk('Off', 1.5)
            st = set(texts())
            for _ in range(3):  # the list scrolls
                adb('shell', 'input', 'swipe', '1170', '700', '1170', '300', '400'); time.sleep(.8)
                st |= set(texts())
            need = ['Off', '2 minutes', '5 minutes', '10 minutes', '15 minutes', '30 minutes', '1 hour', 'SmartBeaconing']
            missing = [t for t in need if t not in st]
            tapk('SmartBeaconing', 1.5)
            page_to('100 km/h')
            live = 'enabled="true"' in (node('100 km/h') or '')
            open(f'shots/beacon.{run}.smart.png', 'wb').write(adb('exec-out', 'screencap', '-p'))
            mark('W160', quiet and order and not on_general and found and greyed and not missing and live,
                 f'no GPS or beacon while Off {quiet}, pages {pages}, still on General {on_general}, on its page {found}, SmartBeaconing settings greyed {greyed} then on {live}, list missing {missing}')
            adb('shell', 'input', 'keyevent', '4'); time.sleep(1)

    if want('W161') or want('W162'):
        with part('W161/W162 every 2 minutes, screen off, no fix', 400, ['W161', 'W162'], res, mark):
            setup('2')
            b1, t1 = wait_for(r'Beacon made: @APRSIS GRID', 40)
            q1, _ = wait_for(r'Beacon waiting for the next transmit slot', 20)
            want_grid = grid10(LAT, LON)
            g1 = re.search(r'GRID (\w+)', b1 or '')
            pref1 = grid_pref()
            adb('shell', 'input', 'keyevent', '223')  # screen off
            lead = [l for l in log() if 'GPS fix starts' in l]
            b2, t2 = wait_for(r'Beacon made: @APRSIS GRID', 135, after=1)
            q2, _ = wait_for(r'Beacon waiting for the next transmit slot', 20, after=1)
            fixes = [l for l in log() if 'GPS fix in' in l]
            screen = adb('shell', 'dumpsys', 'power').decode('utf8', 'replace')
            off = 'mWakefulness=Asleep' in screen or 'mWakefulness=Dozing' in screen
            print('LOG', *log()[-12:], sep='\n  ', flush=True)
            ok1 = bool(b1 and q1 and b2 and q2) and g1 and g1.group(1) == want_grid and pref1 == want_grid and len(fixes) >= 2 \
                and 100 <= t2 <= 135 and off and bool(lead)
            mark('W161', ok1, f'beacon 1 {g1.group(1) if g1 else None} (expected {want_grid}, grid setting {pref1}), queued {bool(q1)}; '
                              f'beacon 2 after {t2:.0f} s with the screen off {off}, queued {bool(q2)}; fixes {len(fixes)}; {lead[-1] if lead else "no lead line"}')
            # W162: location off, no new fix
            feed.on = False; time.sleep(1.5)
            adb('shell', 'cmd', 'location', 'set-location-enabled', 'false')
            n_wait = len([l for l in log() if 'Beacon time: waiting for the GPS fix' in l])
            w, _ = wait_for(r'Beacon time: waiting for the GPS fix', 135, after=n_wait)
            n_made = len([l for l in log() if 'Beacon made' in l])
            time.sleep(45)
            made_while_off = len([l for l in log() if 'Beacon made' in l]) - n_made
            skipped = [l for l in log() if 'no GPS fix in' in l]
            adb('shell', 'cmd', 'location', 'set-location-enabled', 'true')
            adb('shell', 'input', 'keyevent', '224')
            feed = Feeder()
            m, tm = wait_for(r'Beacon made: @APRSIS GRID', 30, after=n_made)
            time.sleep(1)
            nxt = ([l for l in log() if 'Next beacon in' in l] or [None])[-1]
            secs = int(re.search(r'Next beacon in (\d+) s', nxt).group(1)) if nxt else -1
            mark('W162', bool(w and m) and made_while_off == 0 and not skipped and 115 <= secs <= 120,
                 f'waiting line {bool(w)}, beacons while no fix {made_while_off}, skip lines {len(skipped)}, '
                 f'beacon {tm:.0f} s after location on {bool(m)}, next beacon in {secs} s')

    if want('W163'):
        with part('W163 SmartBeaconing', 150, ['W163'], res, mark):
            feed.stop(); time.sleep(1.5)
            adb('shell', 'cmd', 'location', 'providers', 'add-test-provider', 'gps', '--supportsSpeed', '--supportsBearing')
            adb('shell', 'cmd', 'location', 'providers', 'set-test-provider-enabled', 'gps', 'true')
            feed = Feeder(test_provider=True)
            setup('smart', js8cfg_sb_fast_rate='60', js8cfg_sb_turn_time='30')
            b1, _ = wait_for(r'Beacon made \(first GPS fix\)', 25)
            feed.east = 100 / 3.6  # 100 km/h east
            b2, t2 = wait_for(r'Beacon made \(rate', 75)
            time.sleep(32)
            feed.east, feed.north = 0.0, 100 / 3.6  # turn north
            b3, t3 = wait_for(r'Beacon made \(turn', 12)
            feed.north = 0.0  # stopped
            time.sleep(15)
            all_b = [l for l in log() if 'Beacon (' in l]
            print('LOG', *log()[-10:], sep='\n  ', flush=True)
            mark('W163', bool(b1 and b2 and b3) and 55 <= t2 <= 75 and len(all_b) == 3,
                 f'first fix {bool(b1)}, by rate after {t2:.0f} s {b2.split("Beacon")[1][:45] if b2 else None}, '
                 f'turn after {t3:.0f} s {b3.split("Beacon")[1][:50] if b3 else None}, beacons in all {len(all_b)}')
    if want('W164'):
        with part('W164 heartbeat with the screen off', 190, ['W164'], res, mark):
            setup('0', tx_submode=0, js8cfg_hb=True, js8cfg_hb_timer_on=True, heartbeat_interval='1')
            h1, t1 = wait_for(r'Sending Heartbeat', 80)
            adb('shell', 'input', 'keyevent', '223')  # screen off while the first heartbeat is on the air
            h2, t2 = wait_for(r'Sending Heartbeat', 80, after=1)
            stuck = len([l for l in logtext().splitlines() if 'Heartbeat waiting for the transmitter' in l])
            screen = adb('shell', 'dumpsys', 'power').decode('utf8', 'replace')
            off = 'mWakefulness=Asleep' in screen or 'mWakefulness=Dozing' in screen
            adb('shell', 'input', 'keyevent', '224')
            mark('W164', bool(h1 and h2) and off and 50 <= t2 <= 80,
                 f'first heartbeat after {t1:.0f} s {bool(h1)}, second after {t2:.0f} s with the screen off {off} {bool(h2)}, '
                 f'"waiting for the transmitter" lines {stuck}')
            setprefs(js8cfg_hb=False, js8cfg_hb_timer_on=False, heartbeat_interval='0')

    def status_node():
        for n in re.findall(r'<node [^>]*>', dump()):
            if re.search(r'resource-id="[^"]*/js8_aprs_status"', n):
                b = tuple(map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n).groups()))
                t = re.search(r' text="([^"]*)"', n)
                return b, html.unescape(t.group(1)) if t else ''
        return None, ''


    def open_beacon_page():
        tapk('Station', 2)
        tapk('APRS Beacon', 1.5)


    def pick(now, choice):
        """On the APRS Beacon page: opens the APRS beacon: list, picks [choice], OK."""
        tapk(f'{now}  ▾', 1.5) or tapk(now, 1.5)
        for k in range(8):  # the list opens at the current choice: down first, then back up
            if find(dump(), choice):
                break
            y0, y1 = ('700', '300') if k < 4 else ('300', '700')
            adb('shell', 'input', 'swipe', '1170', y0, '1170', y1, '400'); time.sleep(.8)
        tapk(choice, 1.5)
        tapk('OK', 2)


    if want('W166'):
        with part('W166 status window', 130, ['W166'], res, mark):
            setup('2')
            rotate('L')
            q1, _ = wait_for(r'Beacon waiting for the next transmit slot', 40)
            open_beacon_page()
            b, t1 = status_node()
            time.sleep(3)
            _, t2 = status_node()
            cd = lambda t: (lambda m: int(m.group(1)) * 60 + int(m.group(2)) if m else None)(
                re.search(r'Next beacon in (\d+):(\d\d)', t.splitlines()[-1] if t else ''))
            c1, c2 = cd(t1), cd(t2)
            shot1 = adb('exec-out', 'screencap', '-p'); open(f'shots/beacon.{run}.status.png', 'wb').write(shot1)
            pick('2 minutes', 'SmartBeaconing')
            wait_for(r'Beacon made \(first GPS fix\)', 20)
            wait_for(r'Beacon waiting for the next transmit slot', 20, after=1)
            open_beacon_page()
            time.sleep(1.5)
            _, t3 = status_node()
            smart_live = t3.splitlines()[-1] if t3 else ''
            pick('SmartBeaconing', '5 minutes')
            wait_for(r'Beacon waiting for the next transmit slot', 25, after=2)
            open_beacon_page()
            time.sleep(1.5)
            b4, t4 = status_node()
            lines = t4.splitlines()
            want_order = ['Beacon every 2 minutes started', 'GPS fix in', 'Beacon made: @APRSIS GRID', 'Beacon waiting for the next transmit slot',
                          'SmartBeaconing started', 'Beacon made (first GPS fix): @APRSIS GRID', 'Beacon waiting for the next transmit slot',
                          'Beacon every 5 minutes started', 'GPS fix in', 'Beacon made: @APRSIS GRID', 'Beacon waiting for the next transmit slot']
            i, at = 0, []
            for n, l in enumerate(lines):
                if i < len(want_order) and want_order[i] in l:
                    at.append(n); i += 1
            in_order = i == len(want_order)
            timed = all(re.match(r'\d\d:\d\d:\d\d  ', l) for l in lines)
            once = [sum(k in l for l in lines) for k in ('minutes started', 'SmartBeaconing started')] == [2, 1]
            # a drag inside the window scrolls it (finger down = earlier lines), the page stays
            crop = lambda png, bb: Image.open(io.BytesIO(png)).convert('RGB').crop(bb)
            before = adb('exec-out', 'screencap', '-p')
            cx, cy = (b4[0] + b4[2]) // 2, (b4[1] + b4[3]) // 2
            adb('shell', 'input', 'swipe', str(cx), str(cy - 120), str(cx), str(cy + 200), '500'); time.sleep(1.5)
            after = adb('exec-out', 'screencap', '-p'); open(f'shots/beacon.{run}.status-scrolled.png', 'wb').write(after)
            b5, _ = status_node()
            moved = ImageChops.difference(crop(before, b4), crop(after, b4)).getbbox() is not None
            print('WINDOW', *lines, sep='\n  ', flush=True)
            mark('W166', bool(q1) and c1 is not None and c2 is not None and 1 <= c1 - c2 <= 5 and in_order and timed
                 and smart_live and 'SmartBeaconing:' in smart_live and 'km/h' in smart_live and len(lines) > 10 and moved and b5 == b4 and once,
                 f'one started line per change {once}, countdown {c1} -> {c2} s, in order {in_order} ({i}/{len(want_order)}), UTC times {timed}, '
                 f'SmartBeaconing live line {smart_live[10:]!r}, lines {len(lines)}, window scrolled {moved}, page still {b5 == b4}')
            adb('shell', 'input', 'keyevent', '4'); time.sleep(1)


finally:
    feed.stop()
    adb('shell', 'cmd', 'location', 'providers', 'remove-test-provider', 'gps')
    adb('shell', 'cmd', 'location', 'set-location-enabled', 'true')
    adb('shell', 'input', 'keyevent', '224')

print('RESULT', sum(1 for r in res if r[1]), '/', len(res), flush=True)
