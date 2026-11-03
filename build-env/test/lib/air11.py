"""Rig rows against fakes.py. air11.py <run>"""
from onair2 import *
assert_avd()
run = int(sys.argv[1])
t = T(run); t.res = []
fix = t.mark
RIG = dict(rig_control_enabled=True, rig_type='network', rig_network_kind='rigctl', rigctld_host='10.0.2.2', rigctld_port='4599', js8cfg_cat_poll_interval='1', js8cfg_ptt_method='cat', js8cfg_split='none', transmit_mode='usb', js8cfg_hold_ptt=False, tx_submode=2, autoreply_enabled=False, js8cfg_autoreply_startup=False, js8cfg_reset_activity=True)
NORIG = dict(rig_control_enabled=False, rig_type='none', js8cfg_ptt_method='vox', js8cfg_split='none')


def rigset(cmd):
    s = socket.create_connection(('127.0.0.1', 4599), 5); s.send((cmd + '\n').encode()); s.recv(100); s.close()


def flog():
    return open('fakes.log', encoding='utf8', errors='replace').read().splitlines()


def dial():
    for r in api('RIG.GET_FREQ', wait=1):
        if r.get('type') == 'RIG.FREQ':
            return r['params'].get('DIAL')


rigset('F 10136000'); fresh(**RIG); time.sleep(5)
d0 = dial()
inj('K4OOO: @ALLCALL CQ CQ CQ FN31', 1500, 3, -6); time.sleep(2)
rigset('F 10136500'); time.sleep(3); xml = dump(); d1 = dial()
fix('O14', d1 == 10136500 and 'K4OOO' in xml, f'dial {d0} to {d1}, row {re.findall(r"text=.(1000|1500|2000).", xml)[:2]}')
rigset('F 14078000'); time.sleep(3); d2 = dial(); xml = dump()
fix('O15', d2 == 14078000 and 'K4OOO' not in xml, f'dial {d2}, old band row gone {"K4OOO" not in xml}')
rigset('F 10136000')
b = len(flog()); fresh(**{**RIG, 'js8cfg_tx_delay': '0.5'}); time.sleep(4); api('TX.SEND_MESSAGE', 'HI LEAD', wait=1); time.sleep(12)
l = [x for x in flog()[b:] if re.search(r'rigctl T [123]', x)]
print(l[:2], flush=True)
fix('O08', bool(l), 'key-down seen; lead time not measurable from this log, see note')
fresh(**NORIG, tx_submode=2)
json.dump(t.res, open(f'air11.{run}.json', 'w'))
print('DONE', flush=True)
