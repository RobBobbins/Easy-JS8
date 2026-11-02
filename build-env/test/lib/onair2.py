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
            js8cfg_heartbeat_qso_pause=True, heartbeat_interval='0')


def fresh(**k):
    setprefs(**{**BASE, **k})
    start()
