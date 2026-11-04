"""W45: a real decode from a call on the spot list (js8cfg_spot_blacklist) reaches neither the PSK Reporter stand-in
(fakepsk.py) nor the JS8NET stand-in (fakespot.py); the same frame with the list empty reaches both. spotblk.py <run>"""
import sys as _s
exec(open('air13.py', encoding='utf8').read().split('\nrow_list = [')[0].replace('"""Heartbeat, rig, reporting, update and startup rows. air13.py <run> [ID,ID]. fakes.py, fakepsk.py and fakeaprs.py must be running."""', ''))
SPOTLOG = r'C:\Users\Robert\AppData\Local\Temp\claude\E--new-w3bguru-websites-easy-js8\6d32cd7d-2fa3-49a3-9478-be58a39c04b5\scratchpad\spot.log'


def lines(p):
    return open(p, encoding='utf8', errors='replace').read().splitlines()


def one(blocked):
    """Play FRAME0 (VE3TST heard) to an app called K4SPOT; RX off and on flushes the PSK report. Returns (heard, psk, js8net)."""
    import sig
    up(psk_reporter=True, aprs_is=False, js8cfg_aprs_server='10.0.2.2', tx_submode=0, js8cfg_filter_enabled=False, callsign='K4SPOT',
       js8cfg_spot_blacklist='VE3TST' if blocked else '')
    tn = None
    for _ in range(4):
        tn = sig.tones(FRAME0, 1, 0); time.sleep(18)
        if tn:
            break
    base = len(alltxt()); n0 = len(lines('fakepsk.log')); s0 = len(lines(SPOTLOG))
    sig.play([(tn, 1500), (None, 0)], 'a.pcm', 0)
    end = time.time() + 50
    while time.time() < end and not any('VE3TST' in l for l in alltxt()[base:]):
        time.sleep(2)
    heard = any('VE3TST' in l for l in alltxt()[base:])
    time.sleep(65)  # SpotClient sends its JS8NET queue once a minute
    top(); tapk('js8_btn_rx', 3); time.sleep(6); tapk('js8_btn_rx', 2); time.sleep(3)
    psk = any('VE3TST' in l for l in lines('fakepsk.log')[n0:])
    net = any('VE3TST' in l for l in lines(SPOTLOG)[s0:])
    return heard, psk, net


def w45():
    b = one(True)
    o = one(False)
    fresh(psk_reporter=False, callsign='VE3TST', js8cfg_spot_blacklist='')
    return b[0] and not b[1] and not b[2] and o[0] and o[1] and o[2], f'on the list: heard {b[0]}, PSK {b[1]}, JS8NET {b[2]}; list empty: heard {o[0]}, PSK {o[1]}, JS8NET {o[2]}'


row('W45', w45)
json.dump(out, open(f'spotblk.{_run}.json', 'w'))
print('DONE', flush=True)
