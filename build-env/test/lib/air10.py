"""APRS, rig error box, TCP limit, closing. fakes.py and fakeaprs.py must be running. air10.py <run>"""
from onair2 import *
import threading, struct
assert_avd()
run = int(sys.argv[1])
t = T(run)
t.res = []
fix = t.mark

NORIG = dict(rig_control_enabled=False, rig_type='none', js8cfg_ptt_method='vox', js8cfg_split='none')
RIG = dict(rig_control_enabled=True, rig_type='network', rig_network_kind='rigctl', rigctld_host='10.0.2.2', rigctld_port='4598', js8cfg_cat_poll_interval='1', js8cfg_ptt_method='cat', js8cfg_split='none', transmit_mode='usb', js8cfg_hold_ptt=False, tx_submode=2)


def aprsl():
    try:
        return open('aprs.log', encoding='utf8', errors='replace').read().splitlines()
    except OSError:
        return []


b = len(aprsl())
fresh(**NORIG, tx_submode=2, autoreply_enabled=True, js8cfg_autoreply_startup=True, psk_reporter=True, aprs_is=True, js8cfg_aprs_relay=True, js8cfg_aprs_server='10.0.2.2', js8cfg_aprs_port='14580', js8cfg_tx_watchdog='0')
end = time.time() + 75
while time.time() < end and not any('user' in x for x in aprsl()[b:]):
    time.sleep(1)
l = aprsl()[b:]; fix('R01', any('user' in x for x in l), str(l[-1:])[:90])
call = 'K4WXA'; inj(f'{call}: @ALLCALL CQ CQ CQ FN31', 1300); time.sleep(2)
open('aprs_send.txt', 'w').write(f'W1AW>APRS,TCPIP*::{call:<9}:HELLO VIA APRS{{01' + chr(10))
ba = len(alltxt()); h = t.tx(ba, 'APRSIS|MSG TO:' + call, 30); fix('R02', bool(h), (h or ['no relay sent: ' + str([x[-50:] for x in alltxt()[ba:]][-2:])])[0][-70:])
end = time.time() + 75
while time.time() < end and not any('ack01' in x for x in aprsl()[b:]):
    time.sleep(1)
fix('R03', any('ack01' in x for x in aprsl()[b:]), str(aprsl()[-1:])[:90])
time.sleep(10)
fresh(**{**NORIG, **RIG}); end = time.time() + 45; seen = False
while time.time() < end and not seen:
    seen = 'Rig Control Error' in dump(); time.sleep(1)
fix('O12', seen, 'box seen' if seen else 'no box in 45 s')
adb('shell', 'input', 'keyevent', '4'); time.sleep(1)
fresh(**NORIG, psk_reporter=False, aprs_is=False, autoreply_enabled=False, js8cfg_tcp_max_connections=1); time.sleep(3)
api('STATION.GET_CONFIG', wait=.5)
adb('forward', 'tcp:2442', 'tcp:2442')
a = socket.create_connection(('127.0.0.1', 2442), 5); time.sleep(1)
b2 = socket.create_connection(('127.0.0.1', 2442), 5); b2.settimeout(8)
try:
    closed = b2.recv(100) == b''
except OSError:
    closed = False
fix('Q10', closed, 'second connection closed' if closed else 'second connection stayed open 8 s')
b2.close(); a.settimeout(.5)
# a proper close: Station Settings Exit through the menu
for k in ('js8_btn_settings',):
    tapk(k, 2.5)
for _ in range(8):
    if find(dump(), 'Exit'):
        break
    adb('shell', 'input', 'swipe', '600', '900', '600', '300', '350'); time.sleep(.7)
tapk('Exit', .5); buf = b''; end = time.time() + 6
while time.time() < end:
    try:
        d = a.recv(65536)
        if not d:
            break
        buf += d
    except OSError:
        pass
fix('Q11', b'STATION.CLOSING' in buf, buf[-70:].decode('utf8', 'replace'))
a.close(); start()
json.dump(t.res, open(f'air10.{run}.json', 'w'))
print('DONE', flush=True)
