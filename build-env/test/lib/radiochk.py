"""W172-W173 (2026-11-17), radio settings take effect on OK, beacon failures name the cause (Easy JS8 only). radiochk.py <run>
W172: RX on with Rig: Icom IC-7300 (Hamlib, no radio plugged in), PTT Method VOX, APRS beacon every 2 minutes: the
      beacon is not sent and the status window names the cause: "Beacon not sent: radio not connected (PTT Method is VOX)".
W173: then Station > Radio, Rig: None, OK: the radio reconnects at once ("Radio settings changed; reconnecting the
      radio", "Engine started successfully" again) with no RX off/on: no "RX turned off" line, the beacon is not
      restarted; the next beacon goes out ("Beacon sent")."""
from onair2 import *
import html, threading
assert_avd()
os.makedirs('shots', exist_ok=True)

run = int(sys.argv[1])
res = []
LAT, LON = 43.6532, -79.3832


def mark(id, ok, note=''):
    print(id, 'PASS' if ok else 'FAIL', note, flush=True)
    res.append((id, ok, note))


class Feeder:
    def __init__(self):
        self.on = True
        threading.Thread(target=self.loop, daemon=True).start()

    def loop(self):
        while self.on:
            adb('emu', 'geo', 'fix', f'{LON:.6f}', f'{LAT:.6f}', '0', '8')
            time.sleep(1)


def log():
    return [l for l in logtext().splitlines() if 'AprsBeacon' in l or 'JS8EngineService' in l]


def wait_for(pat, secs, after=0):
    t0 = time.time()
    while time.time() - t0 < secs:
        hits = [l for l in log() if re.search(pat, l)]
        if len(hits) > after:
            return hits[after], time.time() - t0
        time.sleep(1)
    return None, secs


def pref(k):
    m = re.search(r'name="%s"[^>]*?(?:value="([^"]*)"|>([^<]*)<)' % k, getprefs())
    return (m.group(1) or m.group(2)) if m else None


feed = Feeder()
try:
    with part('W172/W173 radio not connected, then Rig None and OK', 300, ['W172', 'W173'], res, mark):
        adb('shell', 'pm', 'grant', PKG, 'android.permission.ACCESS_FINE_LOCATION')
        adb('shell', 'cmd', 'location', 'set-location-enabled', 'true')
        setprefs(**{**BASE, 'landscape_layout': 'swipe', 'autoreply_enabled': False, 'js8cfg_hb_ack': False, 'js8cfg_hb': False,
                    'js8cfg_aprs_beacon': '2', 'js8cfg_monitor_off': False, 'rig_control_enabled': True, 'rig_type': 'hamlib_usb',
                    'rig_hamlib_model': '3073', 'js8cfg_ptt_method': 'vox'})
        clearlog()
        adb('shell', 'input', 'keyevent', '224')
        adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0')
        adb('shell', 'settings', 'put', 'system', 'user_rotation', '0')
        start(); time.sleep(3)
        beacon_on('2')
        started, _ = wait_for(r'Engine started successfully', 20)
        cause, _ = wait_for(r'Beacon not sent: ', 70)
        mark('W172', bool(started) and bool(cause) and cause.endswith('Beacon not sent: radio not connected (PTT Method is VOX)'),
             f'RX on {bool(started)}, line {cause.split("AprsBeacon")[-1] if cause else None!r}')

        n_beacon_start = len([l for l in log() if 'Beacon every 2 minutes started' in l])
        tapk('js8_btn_settings', 2)
        gear = [n for n in re.findall(r'<node [^>]*>', dump()) if 'content-desc="Station"' in n]
        a, b, c, d = map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', gear[0]).groups())
        adb('shell', 'input', 'tap', str((a + c) // 2), str((b + d) // 2)); time.sleep(2)
        tapk('Radio', 1.5)
        tapk('Icom IC-7300  ▾', 2)
        tapk('Search (e.g. 7300, yaesu)', 1)
        adb('shell', 'input', 'text', 'None'); time.sleep(1.5)
        rows = [n for n in re.findall(r'<node [^>]*>', dump()) if ' text="None"' in n and 'EditText' not in n]
        picked = bool(rows)
        if rows:  # the first list row (the search box above it also reads "None")
            a, b, c, d = map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', rows[0]).groups())
            adb('shell', 'input', 'tap', str((a + c) // 2), str((b + d) // 2)); time.sleep(1.5)
        open(f'shots/radiochk.{run}.rig-none.png', 'wb').write(adb('exec-out', 'screencap', '-p'))
        tapk('OK', 2)
        rec, trec = wait_for(r'Radio settings changed; reconnecting the radio', 10)
        again, _ = wait_for(r'Engine started successfully', 20, after=1)
        adb('shell', 'input', 'keyevent', '4'); time.sleep(1); adb('shell', 'input', 'keyevent', '4'); time.sleep(1)
        rx_off = [l for l in log() if 'RX turned off' in l or 'RX off (' in l]
        restarted = len([l for l in log() if 'Beacon every 2 minutes started' in l]) - n_beacon_start
        sent, tsent = wait_for(r'Beacon sent: @APRSIS GRID', 150)
        open(f'shots/radiochk.{run}.main.png', 'wb').write(adb('exec-out', 'screencap', '-p'))
        print('LOG', *[l for l in log() if 'AprsBeacon' in l or 'Radio' in l or 'Engine' in l][-14:], sep='\n  ', flush=True)
        mark('W173', picked and bool(rec and again and sent) and not rx_off and restarted == 0 and pref('rig_type') == 'none',
             f'None picked {picked}, reconnect line {bool(rec)} ({trec:.0f} s), engine started again {bool(again)}, '
             f'RX off lines {len(rx_off)}, beacon restarts {restarted}, rig_type {pref("rig_type")!r}, beacon sent {bool(sent)} ({tsent:.0f} s)')
finally:
    feed.on = False
    print('RESULT', ' '.join(f'{i}:{"PASS" if ok else "FAIL"}' for i, ok, _ in res), flush=True)
