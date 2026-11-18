"""TCP API and tap helpers for the S22-Fresh test scripts."""
from onair import *
import json, socket


def api(typ, value='', params=None, wait=2.0):
    """One TCP API request; returns every reply line."""
    adb('forward', 'tcp:2442', 'tcp:2442')
    p = dict(params or {})
    p.setdefault('_ID', int(time.time() * 1000) % 1000000)
    s = socket.create_connection(('127.0.0.1', 2442), 5)
    s.settimeout(.4)
    s.send((json.dumps({'type': typ, 'value': value, 'params': p}) + '\n').encode())
    b, end = b'', time.time() + wait
    while time.time() < end:
        try:
            d = s.recv(65536)
            if not d:
                break
            b += d
        except OSError:
            pass
    s.close()
    out = []
    for l in b.decode('utf8', 'replace').splitlines():
        try:
            out.append(json.loads(l))
        except ValueError:
            pass
    return out


def inbox():
    for r in api('INBOX.GET_MESSAGES', wait=2.5):
        if r.get('type') == 'INBOX.MESSAGES':
            return r['params'].get('MESSAGES', [])
    return []


def tapk(k, wait=1.5):
    p = None
    for _ in range(5):
        p = find(dump(), k)
        if p:
            break
        time.sleep(1)
    if not p:
        print('MISSING', k, flush=True)
        return False
    adb('shell', 'input', 'tap', str(p[0]), str(p[1]))
    time.sleep(wait)
    return True


BASE = dict(autoreply_enabled=True, js8cfg_autoreply_startup=True, js8cfg_autoreply_confirmation=False, relay_enabled=True, tx_submode=2,
            js8cfg_multi=True, js8cfg_transmit_off=False, js8cfg_monitor_off=False, js8cfg_reset_activity=False, psk_reporter=True, aprs_is=False,
            js8cfg_swl=False, js8cfg_auto_whitelist='', js8cfg_auto_blacklist='', js8cfg_rx_blocklist='', js8cfg_avoid_allcall=False, my_groups='@TESTG',
            my_info='TEST RIG', my_status='IDLE', js8cfg_hb=True, js8cfg_hb_ack=True, js8cfg_tx_watchdog='60', js8cfg_activity_aging='2',
            js8cfg_callsign_aging='0', js8cfg_tcp_enabled=True, js8cfg_tcp_accept=True, js8cfg_hb_blacklist='', js8cfg_hb_rate_limit=False,
            js8cfg_heartbeat_qso_pause=True, heartbeat_interval='0',
            time_drift_ms=Long(0), js8cfg_auto_drift_on=False)  # drift tests left +5897 ms behind (2026-11-03)


def fresh(**k):
    with part('restart', 20):  # setprefs about 3 s, start 6 s
        setprefs(**{**BASE, **k})
        start()

def show_call(call):
    """Scroll the Callsigns list until the call shows. The list's height changes with the Message Window
    (upright 240 to 630 px), so the swipe is read from its bounds each time; down to the end, then back up."""
    step, last = 1, None
    for _ in range(60):
        x = dump()
        p = find(x, call)
        if p:
            return p
        m = re.search(r'resource-id="[^"]*js8_call_activity"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x)
        if not m:
            return None
        l, top, r, bot = map(int, m.groups())
        rows = re.findall(r'<node [^>]*text="([^"]+)"[^>]*bounds="\[(\d+),\d+\]\[\d+,\d+\]"', x)
        seen = [t for t, a in rows if l <= int(a) < r]
        if seen == last:
            if step == -1:
                return None
            step = -1  # end of the list: go back up
        last = seen
        cx, y1, y2 = (l + r) // 2, bot - 50, top + 95  # under the header row (81 px), above the line's drag strip (24 dp)
        adb('shell', 'input', 'swipe', str(cx), str(y1 if step == 1 else y2), str(cx), str(y2 if step == 1 else y1), '700')
        time.sleep(.8)
    return None


BEACON_LABELS = {'2': '2 minutes', '5': '5 minutes', '10': '10 minutes', '15': '15 minutes', '30': '30 minutes',
                 '60': '1 hour', 'smart': 'SmartBeaconing'}


def beacon_on(mode):
    """APRS beacon: picked on Station > APRS Beacon and OK, upright (2026-11-17: the app sets it to Off at every start,
    so a saved value before start() no longer turns it on). Returns True when the setting was saved."""
    if mode in (None, '0'):
        return True
    adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0')
    for _ in range(5):
        adb('shell', 'settings', 'put', 'system', 'user_rotation', '0'); time.sleep(2)
        m = re.search(r'cur=(\d+)x(\d+)', adb('shell', 'dumpsys', 'window', 'displays').decode('utf8', 'replace'))
        if m and int(m.group(1)) < int(m.group(2)):
            break
    tapk('js8_btn_settings', 2)
    gear = [n for n in re.findall(r'<node [^>]*>', dump()) if 'content-desc="Station"' in n]
    if gear:
        a, b, c, d = map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', gear[0]).groups())
        adb('shell', 'input', 'tap', str((a + c) // 2), str((b + d) // 2)); time.sleep(2)
    tapk('APRS Beacon', 1.5)
    tapk('Off  ▾', 1.5)
    label = BEACON_LABELS[mode]
    for _ in range(4):  # the list scrolls
        if find(dump(), label):
            break
        adb('shell', 'input', 'swipe', '540', '1500', '540', '700', '400'); time.sleep(.8)
    tapk(label, 1.5)
    tapk('OK', 2)
    ok = f'name="js8cfg_aprs_beacon">{mode}<' in getprefs()
    for _ in range(4):  # Back until the main screen (its Settings button) shows
        if 'js8_btn_settings' in dump():
            break
        adb('shell', 'input', 'keyevent', '4'); time.sleep(1.5)
    print('BEACON ON', mode, ok, flush=True)
    return ok
