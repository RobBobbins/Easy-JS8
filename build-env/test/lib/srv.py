"""Sections O, P, Q, R, S against stand-ins on this PC; fakes.py, fakeaprs.py, fakespot.py, fakepsk.py must be running. srv.py <run>"""
from onair2 import *
import threading
assert_avd()
run = int(sys.argv[1])
t = T(run)
t.res = []
M = 'VE3TST'
T.n = (int(time.time()) // 60 % 200) * 26
fix = t.mark
c = t.call
RIG = dict(rig_control_enabled=True, rig_type='network', rig_network_kind='rigctl', rigctld_host='10.0.2.2', rigctld_port='4599',
           js8cfg_cat_poll_interval='1', js8cfg_ptt_method='cat', js8cfg_split='none', transmit_mode='usb', js8cfg_hold_ptt=False, tx_submode=2, autoreply_enabled=False, js8cfg_autoreply_startup=False)
NORIG = dict(rig_control_enabled=False, rig_type='none', js8cfg_ptt_method='vox', js8cfg_split='none')
def flog(name='fakes.log'):
    try: return open(name, encoding='utf8', errors='replace').read().splitlines()
    except OSError: return []
def rigcmds(base): return [l.split(' ', 2)[2] for l in flog()[base:] if ' rigctl ' in l]
def wait_tx(secs=14): time.sleep(secs)
def say(s): return api('TX.SEND_MESSAGE', s, wait=1)
for attempt in ('net',):
    b = len(flog()); fresh(**RIG); time.sleep(6)
    cm = rigcmds(b)
    fix('O01', any(x == 'f' for x in cm), f'{len(cm)} commands')
    b = len(flog()); time.sleep(10); cm = rigcmds(b); fix('O11', 6 <= sum(x == 'f' for x in cm) <= 14, f"{sum(x == 'f' for x in cm)} reads in 10 s")
    b = len(flog()); r = api('RIG.SET_FREQ', params={'DIAL': 14078000}, wait=2); time.sleep(2); cm = rigcmds(b)
    fix('O02', any(x.startswith('F 14078000') for x in cm), str(r)[:80])
    api('RIG.SET_FREQ', params={'DIAL': 10136000}, wait=1); time.sleep(2)
    b = len(flog()); say('HI RIG'); wait_tx(12); cm = rigcmds(b)
    fix('O05', any(re.match(r'T [123]', x) for x in cm) and 'T 0' in cm, str([x for x in cm if x[0] == 'T']))
    fix('O03', any(x.startswith('M USB') for x in cm), str([x for x in cm if x.startswith('M')][:2]))
    # hold PTT: a three-frame message keys once
    fresh(**{**RIG, 'js8cfg_hold_ptt': True}); b = len(flog()); say('THIS IS A LONGER MESSAGE TO FILL THREE FRAMES OF JS8 40 TEXT'); wait_tx(26); cm = rigcmds(b)
    kd = sum(bool(re.match(r'T [123]', x)) for x in cm); fix('O07', kd == 1, f'{kd} key-downs')
    fresh(**{**RIG, 'js8cfg_ptt_method': 'vox'}); b = len(flog()); say('HI VOX'); wait_tx(12); cm = rigcmds(b); fix('O06', not any(re.match(r'T [123]', x) for x in cm))
    fresh(**{**RIG, 'transmit_mode': 'usb_data'}); b = len(flog()); time.sleep(4); say('HI DATA'); wait_tx(12); cm = rigcmds(b)
    fix('O04', any(x.startswith('M PKTUSB') for x in cm), str([x for x in cm if x.startswith('M')][:2]))
    fresh(**{**RIG, 'js8cfg_split': 'rig', 'js8cfg_tx_offset': '2300'}); b = len(flog()); say('HI SPLIT'); wait_tx(12); cm = rigcmds(b)
    fix('O09', any(x.startswith('S 1') for x in cm) and any(x.startswith('I ') for x in cm), str([x for x in cm if x[0] in 'SI'][:3]))
    fresh(**{**RIG, 'js8cfg_split': 'emulate', 'js8cfg_tx_offset': '2300'}); b = len(flog()); say('HI FAKE'); wait_tx(12); cm = rigcmds(b)
    fix('O10', sum(x.startswith('F ') for x in cm) >= 2, str([x for x in cm if x[0] == 'F'][:3]))
    fresh(**{**RIG, 'rigctld_port': '4598'}); time.sleep(5); xt = texts(); fix('O12', 'Rig Control Error' in ' '.join(xt), str(xt[:2])[:80])
    adb('shell', 'input', 'keyevent', '4')
# stand-ins for the other two programs
fresh(**{**RIG, 'rig_network_kind': 'dxlab', 'rigctld_port': '52002'}); b = len(flog()); time.sleep(4); say('HI DX'); wait_tx(12); l = flog()[b:]
fix('P01', any('CmdTX' in x for x in l) and any('CmdGetFreq' in x for x in l), f'{len(l)} lines')
fresh(**{**RIG, 'rig_network_kind': 'hrd', 'rigctld_port': '7809'}); b = len(flog()); time.sleep(4); say('HI HRD'); wait_tx(12); l = flog()[b:]
fix('P02', any('button-select TX 1' in x for x in l), f'{len(l)} lines')
# API
fresh(**NORIG, tx_submode=2, autoreply_enabled=False, js8cfg_autoreply_startup=False, js8cfg_api_dynamic_info=True)
replies = {'RIG.GET_PTT': 'RIG.PTT', 'RIG.GET_FREQ': 'RIG.FREQ', 'STATION.GET_CALLSIGN': 'STATION.CALLSIGN', 'STATION.GET_GRID': 'STATION.GRID', 'STATION.GET_INFO': 'STATION.INFO',
           'STATION.GET_STATUS': 'STATION.STATUS', 'STATION.VERSION': 'STATION.VERSION', 'STATION.GET_OS': 'STATION.GET_OS', 'STATION.GET_SPOT': 'STATION.SPOT', 'STATION.GET_CONFIG': 'STATION.CONFIG',
           'RX.GET_CALL_ACTIVITY': 'RX.CALL_ACTIVITY', 'RX.GET_CALL_SELECTED': 'RX.CALL_SELECTED', 'RX.GET_BAND_ACTIVITY': 'RX.BAND_ACTIVITY', 'RX.GET_TEXT': 'RX.TEXT',
           'RX.GET_FREE_OFFSETS': 'RX.FREE_OFFSETS', 'RX.GET_FILTER': 'RX.FILTER', 'TX.GET_TEXT': 'TX.TEXT', 'TX.GET_QUEUE_DEPTH': 'TX.QUEUE_DEPTH', 'MODE.GET_SPEED': 'MODE.SPEED', 'INBOX.GET_MESSAGES': 'INBOX.MESSAGES'}
bad = []
for q, a in replies.items():
    r = api(q, wait=1.2)
    if not any(x.get('type', '').startswith(a) for x in r):
        bad.append(q)
setters = [('STATION.SET_GRID', 'FN03', {}), ('STATION.SET_INFO', 'NEWINFO', {}), ('STATION.SET_STATUS', 'NEWSTATUS', {}), ('STATION.SET_SPOT', '', {'SPOT': True}), ('STATION.SET_AUTO_REPLY', '', {'AUTO_REPLY': False}),
           ('STATION.SET_HBACK', '', {'HBACK': False}), ('STATION.SET_MULTI_DECODER', '', {'MULTI_DECODER': True}), ('STATION.SET_HB_INTERVAL', '', {'HB_INTERVAL': 30}), ('STATION.SET_HB_TIMER', '', {'HB_TIMER': False}),
           ('STATION.SET_AUTOREPLY_CONFIRMATION', '', {'AUTOREPLY_CONFIRMATION': False}), ('STATION.SET_GROUPS', '@TESTG', {}), ('STATION.SET_AVOID_ALLCALL', '', {'AVOID_ALLCALL': False}),
           ('RX.SET_FILTER', '', {'CENTER': 1500, 'WIDTH': 2000}), ('RX.SET_FILTER_ENABLED', '', {'ENABLED': False}), ('TX.SET_TEXT', 'API TEXT', {}), ('MODE.SET_SPEED', '', {'SPEED': 2}), ('INBOX.STORE_MESSAGE', '', {'CALLSIGN': 'W1AW', 'TEXT': 'STORED'})]
for q, v, p in setters:
    r = api(q, v, p, wait=1.2)
    if not r:
        bad.append(q)
for q in ('RIG.SET_TUNE', 'RIG.TX_HALT', 'STATION.SEND_HB', 'WINDOW.RAISE'):
    if q != 'WINDOW.RAISE' and not api(q, params={'TUNE': False}, wait=1.2): bad.append(q)
    api('RIG.TX_HALT', wait=.5)
api('TX.SET_TEXT', '', wait=1)
fix('Q08', not bad, 'no answer: ' + str(bad) if bad else f'{len(replies) + len(setters)} commands')
g = [x for x in api('STATION.GET_GRID', wait=1.2) if x.get('type') == 'STATION.GRID']
fix('Q09', bool(g) and g[0].get('value') == 'FN03', str(g)[:80])
# UDP events
got = []
us = socket.socket(socket.AF_INET, socket.SOCK_DGRAM); us.bind(('127.0.0.1', 2242)); us.settimeout(.5)
stopped = [False]
def rx():
    while not stopped[0]:
        try: got.append(us.recvfrom(65535)[0].decode('utf8', 'replace'))
        except OSError: pass
threading.Thread(target=rx, daemon=True).start()
fresh(**NORIG, tx_submode=2, autoreply_enabled=False, js8cfg_autoreply_startup=False, js8cfg_udp_enabled=True, js8cfg_udp_server='10.0.2.2', js8cfg_udp_accept=True)
inj('K4ZZA: @ALLCALL CQ CQ CQ FN31', 1300); inj(f'K4ZZB: {M} HELLO', 1400); time.sleep(3); say('HI EVENTS'); time.sleep(20)
all_ = ' '.join(got)
for row, ty in (('Q02', 'STATION.STATUS'), ('Q03', 'RX.ACTIVITY'), ('Q04', 'RX.DIRECTED'), ('Q05', 'RIG.PTT'), ('Q06', 'TX.FRAME')):
    fix(row, ty in all_, f'{len(got)} packets')
time.sleep(0)
fix('Q01', 'PING' in all_ or 'STATION.STATUS' in all_, 'PING every 15 s' if 'PING' in all_ else 'no PING seen')
stopped[0] = True
us.close()
# network stand-ins
fresh(**NORIG, tx_submode=2, autoreply_enabled=False, js8cfg_autoreply_startup=False, psk_reporter=True, debug_spot_host='10.0.2.2', js8cfg_spot_blacklist='K9NOT')
OLD = 'C:/Users/Robert/AppData/Local/Temp/claude/E--new-w3bguru-websites-easy-js8/6d32cd7d-2fa3-49a3-9478-be58a39c04b5/scratchpad/'
def spots():
    try: return open(OLD + 'spot.log', encoding='utf8', errors='replace').read().splitlines()
    except OSError: return []
b = len(spots()); inj('K4ZYA: @ALLCALL CQ CQ CQ FN31', 1300); inj('K9NOT: @ALLCALL CQ CQ CQ FN31', 1300)
pl = 'A SPOT'; inj(f'K4ZYB: @JS8NET MSG {pl} {sum16(pl)}', 1400); inj(f'K4ZYC: @JS8NET MSG {pl} ZZZ', 1500); time.sleep(70); s = spots()[b:]; j = ' '.join(s)
fix('R05', 'RX.LOCAL' in j or 'RX.LOCAL' in ' '.join(spots()), '')
fix('R06', 'RX.SPOT' in j and 'K4ZYA' in j, f'{len(s)} lines')
fix('R07', 'RX.DIRECTED' in j, '')
fix('R08', 'K4ZYC' not in j or 'RX.DIRECTED' not in [x for x in s if 'K4ZYC' in x][0], '')
fix('R09', 'K9NOT' not in j, '')
p = flog('fakepsk.log'); fix('R10', len(p) > 0, f'{len(p)} packets at the stand-in')
def aprsl():
    try: return open('aprs.log', encoding='utf8', errors='replace').read().splitlines()
    except OSError: return []
b = len(aprsl())
fresh(**NORIG, tx_submode=2, autoreply_enabled=True, js8cfg_autoreply_startup=True, psk_reporter=True, aprs_is=True, js8cfg_aprs_relay=True, js8cfg_aprs_server='10.0.2.2', js8cfg_aprs_port='14580')
time.sleep(6); l = aprsl()[b:]; fix('R01', any('user VE3TST' in x or 'APJ8CL' in x for x in l), str(l[-1:])[:90])
call = 'K4WXA'; inj(f'{call}: @ALLCALL CQ CQ CQ FN31', 1300); time.sleep(2)
open('aprs_send.txt', 'w').write(f'W1AW>APRS,TCPIP*::{call:<9}:HELLO VIA APRS{{01' + chr(10))
ba = len(alltxt()); h = t.tx(ba, 'APRSIS MSG TO:' + call, 26); fix('R02', bool(h), (h or ['no relay sent'])[0][-70:])
time.sleep(4); fix('R03', any(f':W1AW     :ack01' in x for x in aprsl()[b:]), 'ack line')
time.sleep(12); api('STATION.GET_CONFIG', wait=.5); reach_rx = None
fresh(**NORIG, psk_reporter=False, aprs_is=False, autoreply_enabled=False)
json.dump(t.res, open(f'srv.{run}.json', 'w'))
print('DONE', flush=True)
