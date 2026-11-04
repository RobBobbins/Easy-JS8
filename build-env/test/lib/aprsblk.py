"""W46: an @APRSIS GRID from a call on the spot list (js8cfg_spot_blacklist) is not sent to the APRS-IS stand-in
(fakeaprs.py, aprs.log); the same frame with the list empty is. aprsblk.py <run>"""
import sys as _s
exec(open('air13.py', encoding='utf8').read().split('\nrow_list = [')[0].replace('"""Heartbeat, rig, reporting, update and startup rows. air13.py <run> [ID,ID]. fakes.py, fakepsk.py and fakeaprs.py must be running."""', ''))


def alog():
    try:
        return open('aprs.log', encoding='utf8', errors='replace').read().splitlines()
    except OSError:
        return []


def one(blocked, call):
    up(psk_reporter=True, aprs_is=True, js8cfg_aprs_server='10.0.2.2', js8cfg_aprs_port='14580', tx_submode=2,
       js8cfg_spot_blacklist=call if blocked else '')
    b = len(alog()); time.sleep(6)
    inj(f'{call}: @APRSIS GRID EN94MN', 1500, 3, -5)
    # AprsIsClient sends once a minute; 26% of the time it waits one more minute (PC throttle)
    end = time.time() + (130 if blocked else 200)
    while time.time() < end and not any(call in l for l in alog()[b:]):
        time.sleep(5)
    login = any("RX 'user " in l for l in alog()[b:])  # PC logs in as APJ8CL, only when there is a report
    sent = [l for l in alog()[b:] if call in l]
    return login, bool(sent), sent[:1]


def w46():
    call = f'K4B{"QRSTUVWXYZ"[int(_run) % 10]}{"ABCDEFGHJK"[int(time.time()) % 10]}'
    bl = one(True, call)
    op = one(False, call)
    fresh(aprs_is=False, js8cfg_spot_blacklist='')
    return not bl[1] and op[1], f'{call} on the list: login {bl[0]}, sent {bl[1]}; list empty: login {op[0]}, sent {op[1]} {op[2]}'


row('W46', w46)
json.dump(out, open(f'aprsblk.{_run}.json', 'w'))
print('DONE', flush=True)
