"""W19: the app against Hamlib's own rigctld (4.7.2, dummy rig -m 1) on the PC, TCP 4532. wham.py <run>
rigctld must already be running: rigctld.exe -m 1 -t 4532. The app reaches it as 10.0.2.2:4532."""
import sys as _s
exec(open('ui7.py', encoding='utf8').read().split("row_list = [")[0].replace('"""Upright rows never tested: H, I, J. ui7.py <run> [ID,ID]"""', ''))
HAM = dict(rig_control_enabled=True, rig_type='network', rig_network_kind='rigctl', rigctld_host='10.0.2.2', rigctld_port='4532',
           js8cfg_cat_poll_interval='1', js8cfg_ptt_method='cat', js8cfg_split='none', transmit_mode='usb', js8cfg_hold_ptt=False)


def rc(cmd):
    """One rigctld command, its reply."""
    s = socket.create_connection(('127.0.0.1', 4532), 5); s.settimeout(3)
    s.send((cmd + '\n').encode()); time.sleep(.3)
    r = s.recv(200).decode('ascii', 'replace').strip(); s.close()
    return r


def dial():
    for r in api('RIG.GET_FREQ', wait=1):
        if r.get('type') == 'RIG.FREQ':
            return r['params'].get('DIAL')


def w19():
    rc('F 10136000')
    up(**HAM); time.sleep(6)
    read = dial()
    api('RIG.SET_FREQ', params={'DIAL': 14078000, 'OFFSET': 1500}, wait=1); time.sleep(3)
    setf = rc('f')
    api('RIG.SET_FREQ', params={'DIAL': 10136000, 'OFFSET': 1500}, wait=1); time.sleep(2)
    api('TX.SEND_MESSAGE', 'HI HAMLIB', wait=.3)
    ptt, end = [], time.time() + 40
    while time.time() < end:
        ptt.append(rc('t'))
        if '1' in ptt and ptt[-1] == '0':
            break
        time.sleep(.5)
    keyed = '1' in ptt and ptt[-1] == '0'
    reach('js8_btn_cat'); n = re.findall(r'<node [^>]*js8_btn_cat"[^>]*>', x())
    shot(f'w19_{_run}')
    ok = read == 10136000 and setf.startswith('14078000') and keyed
    return ok, f'app read {read}, app set -> rigctld {setf}, PTT seen {ptt.count("1")} reads keyed then released {keyed}'


row('W19', w19)
up(**{**HAM, 'rig_control_enabled': False, 'rig_type': 'none', 'js8cfg_ptt_method': 'vox'})
adb('shell', 'settings', 'put', 'system', 'user_rotation', '0')
json.dump(out, open(f'wham.{_run}.json', 'w'))
print('DONE', flush=True)
