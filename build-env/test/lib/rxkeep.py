"""W169-W171 (2026-11-17), RX kept on, beacon only with RX (Easy JS8 only). rxkeep.py <run> [ID,ID]
W169: Easy JS8 closed from the phone's open-apps screen (Recents, swipe up): RX keeps running ("Task removed; engine
      keeps running", no "Engine stopped"), and the APRS beacon (2 minutes) keeps going: a beacon made after the close.
W170: a beacon goes out: "Beacon waiting for the next transmit slot", "Beacon transmitting", "Beacon sent" in order;
      then RX turned off with the RX button: "RX turned off: APRS beacon stopped", no more GPS fixes or beacons,
      the GPS line "GPS: off (RX is off)" and the live line "APRS beacon waiting: RX is off".
W174: APRS beacon on (2 minutes), the app closed and opened again: the beacon is Off (saved "0", the page
      shows Off, no beacon), RX on.
W171: the app's process killed while RX is on (as the phone does on low memory): the service comes back by itself
      and RX with it ("Restarted by the phone; RX was on", "Engine started successfully")."""
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


class Feeder:
    def __init__(self):
        self.on = True
        self.t = threading.Thread(target=self.loop, daemon=True); self.t.start()

    def loop(self):
        while self.on:
            adb('emu', 'geo', 'fix', f'{LON:.6f}', f'{LAT:.6f}', '0', '8')
            time.sleep(1)

    def stop(self):
        self.on = False


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


def setup(mode):
    adb('shell', 'pm', 'grant', PKG, 'android.permission.ACCESS_FINE_LOCATION')
    adb('shell', 'pm', 'grant', PKG, 'android.permission.ACCESS_COARSE_LOCATION')
    adb('shell', 'cmd', 'location', 'set-location-enabled', 'true')
    setprefs(**{**BASE, 'landscape_layout': 'swipe', 'autoreply_enabled': False, 'js8cfg_hb_ack': False, 'js8cfg_hb': False,
                'js8cfg_aprs_beacon': mode, 'js8cfg_monitor_off': False})
    clearlog()
    adb('shell', 'input', 'keyevent', '224')
    adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0')
    start(); time.sleep(3)
    beacon_on(mode)
    for _ in range(5):  # upright, checked (RX is on the main screen upright)
        adb('shell', 'settings', 'put', 'system', 'user_rotation', '0'); time.sleep(2)
        m = re.search(r'cur=(\d+)x(\d+)', adb('shell', 'dumpsys', 'window', 'displays').decode('utf8', 'replace'))
        if m and int(m.group(1)) < int(m.group(2)):
            break


def node_text(rid):
    for n in re.findall(r'<node [^>]*>', dump()):
        if re.search(r'resource-id="[^"]*/%s"' % rid, n):
            t = re.search(r' text="([^"]*)"', n)
            return html.unescape(t.group(1)) if t else ''
    return None


def service_up():
    return 'JS8EngineService' in adb('shell', 'dumpsys', 'activity', 'services', PKG).decode('utf8', 'replace')


def open_beacon_page():
    """Upright: Settings, then the gear, then APRS Beacon."""
    tapk('js8_btn_settings', 2)
    gear = [n for n in re.findall(r'<node [^>]*>', dump()) if 'content-desc="Station"' in n]
    if gear:
        a, b, c, d = map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', gear[0]).groups())
        adb('shell', 'input', 'tap', str((a + c) // 2), str((b + d) // 2)); time.sleep(2)
    tapk('APRS Beacon', 1.5)


feed = Feeder()
try:
    if want('W169'):
        with part('W169 closed from open apps, RX and beacon keep going', 180, ['W169'], res, mark):
            setup('2')
            started, _ = wait_for(r'Engine started successfully', 20)
            b1, _ = wait_for(r'Beacon made: @APRSIS GRID', 40)
            time.sleep(3)
            adb('shell', 'input', 'keyevent', '187'); time.sleep(2.5)  # open-apps screen
            open(f'shots/rxkeep.{run}.recents.png', 'wb').write(adb('exec-out', 'screencap', '-p'))
            adb('shell', 'input', 'swipe', '540', '1300', '540', '150', '250'); time.sleep(2)  # card swiped up
            adb('shell', 'input', 'keyevent', '3'); time.sleep(1)
            removed, _ = wait_for(r'Task removed; engine keeps running', 10)
            b2, t2 = wait_for(r'Beacon made: @APRSIS GRID', 135, after=1)
            stopped = any('Engine stopped' in l for l in log())
            up = service_up()
            mark('W169', bool(started and b1 and removed and b2) and not stopped and up,
                 f'RX on {bool(started)}, beacon 1 {bool(b1)}, task removed {bool(removed)}, beacon 2 after close {bool(b2)} '
                 f'({t2:.0f} s), engine stopped {stopped}, service running {up}')

    if want('W170'):
        with part('W170 beacon sent, then RX off', 120, ['W170'], res, mark):
            setup('2')
            q, _ = wait_for(r'Beacon waiting for the next transmit slot', 45)
            tx, _ = wait_for(r'Beacon transmitting: @APRSIS GRID', 30)
            sent, _ = wait_for(r'Beacon sent: @APRSIS GRID', 60)
            lines = [l for l in log() if 'AprsBeacon' in l]
            order = [i for k in ('next transmit slot', 'Beacon transmitting', 'Beacon sent') for i, l in enumerate(lines) if k in l][:3]
            in_order = len(order) == 3 and order == sorted(order) \
                and sum('Beacon transmitting' in l for l in lines) == 1  # once per beacon, though it goes in parts
            open(f'shots/rxkeep.{run}.main-sent.png', 'wb').write(adb('exec-out', 'screencap', '-p'))
            tapk('RX', 3)
            off, _ = wait_for(r'RX turned off: APRS beacon stopped', 10)
            n0 = len([l for l in log() if 'GPS fix started' in l or 'Beacon made' in l])
            open_beacon_page()
            g = node_text('js8_aprs_gps')
            st = node_text('js8_aprs_status') or ''
            live = st.splitlines()[-1] if st else ''
            open(f'shots/rxkeep.{run}.rxoff.png', 'wb').write(adb('exec-out', 'screencap', '-p'))
            time.sleep(20)
            n1 = len([l for l in log() if 'GPS fix started' in l or 'Beacon made' in l])
            mark('W170', bool(q and tx and sent) and in_order and bool(off) and g == 'GPS: off (RX is off)'
                 and live.endswith('APRS beacon waiting: RX is off') and n1 == n0,
                 f'waiting {bool(q)}, transmitting {bool(tx)}, sent {bool(sent)}, in order {in_order}; RX off line {bool(off)}, '
                 f'GPS line {g!r}, live line {live[10:]!r}, fixes/beacons after RX off {n1 - n0}')
            adb('shell', 'input', 'keyevent', '4'); time.sleep(1); adb('shell', 'input', 'keyevent', '4'); time.sleep(1)

    if want('W171'):
        with part('W171 process killed, RX back by itself', 90, ['W171'], res, mark):
            setup('0')
            started, _ = wait_for(r'Engine started successfully', 20)
            pid = adb('shell', 'pidof', PKG).decode().strip()
            clearlog()
            adb('shell', 'run-as', PKG, 'kill', '-9', pid)
            back, tb = wait_for(r'Restarted by the phone; RX was on', 60)
            again, _ = wait_for(r'Engine started successfully', 15)
            pid2 = adb('shell', 'pidof', PKG).decode().strip()
            mark('W171', bool(started and back and again) and pid2 and pid2 != pid,
                 f'RX on {bool(started)}, killed pid {pid}, back after {tb:.0f} s {bool(back)}, engine started again {bool(again)}, new pid {pid2}')
    if want('W174'):
        with part('W174 APRS beacon Off after the app starts again', 75, ['W174'], res, mark):
            setup('2')
            on, _ = wait_for(r'Beacon every 2 minutes started', 15)
            stop(); clearlog()
            start()
            reset, _ = wait_for(r'App started: APRS beacon set to Off', 10)
            rx, _ = wait_for(r'Engine started successfully', 15)
            time.sleep(5)
            again = [l for l in log() if 'Beacon every' in l or 'Beacon made' in l]
            saved = re.search(r'name="js8cfg_aprs_beacon">([^<]*)<', getprefs())
            open_beacon_page()
            shown = bool(find(dump(), 'Off  ▾'))
            open(f'shots/rxkeep.{run}.beacon-off.png', 'wb').write(adb('exec-out', 'screencap', '-p'))
            adb('shell', 'input', 'keyevent', '4'); time.sleep(1); adb('shell', 'input', 'keyevent', '4'); time.sleep(1)
            mark('W174', bool(on and reset and rx) and not again and saved and saved.group(1) == '0' and shown,
                 f'beacon on before {bool(on)}, reset line {bool(reset)}, RX on {bool(rx)}, beacon lines after {len(again)}, '
                 f'saved {saved.group(1) if saved else None!r}, page shows Off {shown}')
finally:
    feed.stop()
    print('RESULT', ' '.join(f'{i}:{"PASS" if ok else "FAIL"}' for i, ok, _ in res), flush=True)
