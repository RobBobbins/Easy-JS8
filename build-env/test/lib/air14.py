"""Last rig, startup, log and decode rows. air14.py <run> [ID,ID]. fakes.py must be running."""
import sys as _s
import base64
exec(open('ui7.py', encoding='utf8').read().split("row_list = [")[0].replace('"""Upright rows never tested: H, I, J. ui7.py <run> [ID,ID]"""', ''))
RIG = dict(rig_control_enabled=True, rig_type='network', rig_network_kind='rigctl', rigctld_host='10.0.2.2', rigctld_port='4599', js8cfg_cat_poll_interval='1', js8cfg_ptt_method='cat', js8cfg_split='none', transmit_mode='usb', js8cfg_hold_ptt=False)
NORIG = dict(rig_control_enabled=False, rig_type='none', js8cfg_ptt_method='vox', js8cfg_split='none', js8cfg_pwr_band_tx=False, last_frequency='10130000')
PREFS = f'shared_prefs/{PKG}_preferences.xml'


def flog():
    return open('fakes.log', encoding='utf8', errors='replace').read().splitlines()


def rigset(cmd):
    s = socket.create_connection(('127.0.0.1', 4599), 5); s.send((cmd + '\n').encode()); s.recv(100); s.close()


def dial():
    for r in api('RIG.GET_FREQ', wait=1):
        if r.get('type') == 'RIG.FREQ':
            return r['params'].get('DIAL')


def a13():
    up()
    adb('shell', f"""am broadcast -n {PKG}/com.js8call.example.debug.DebugDecodeReceiver --es text "K4LOW: @ALLCALL CQ CQ CQ FN31" --ef freq 1100 --ei type 3 --ei snr -20 --ef quality 0.1""")
    time.sleep(2.5); top()
    w = html.unescape(x()); shot(f'a13_{_run}')
    m = re.findall(r' text="(\[K4LOW[^"]*)"', w)
    return bool(m), m[:1]


def o14_15():
    rigset('F 10136000'); fresh(**RIG, landscape_layout='pc'); rotate('U'); time.sleep(5)
    inj('K4OOO: @ALLCALL CQ CQ CQ FN31', 1500, 3, -6); time.sleep(2)
    rigset('F 10136500'); time.sleep(3); top(); w = x(); d1 = dial()
    o14 = d1 == 10136500 and 'K4OOO' in w
    rigset('F 14078000'); time.sleep(3); d2 = dial(); w = x()
    o15 = d2 == 14078000 and 'K4OOO' not in w
    rigset('F 10136000')
    return o14, o15, f'{d1} {d2}'


R = {}
ATTN = 'js8cfg_out_attenuation'


def o14():
    R['o'] = o14_15()
    return R['o'][0], R['o'][2]


def o15():
    return R['o'][1], R['o'][2]


def keytime():
    """Seconds into the 10 s period at which the rig was keyed, from fakes.log with milliseconds."""
    b = len(flog()); api('TX.SEND_MESSAGE', 'HI LEAD', wait=.3); time.sleep(14)
    l = [s for s in flog()[b:] if re.search(r'rigctl T [123]', s)]
    m = re.match(r'(\d\d):(\d\d):(\d\d)\.(\d\d\d)', l[0])
    return (int(m[2]) * 60 + int(m[3]) + int(m[4]) / 1000) % 10


def o08():
    res_ = {}
    for d in ('0.1', '0.5'):
        v = []
        fresh(**RIG, js8cfg_tx_delay=d, tx_submode=1, landscape_layout='pc'); time.sleep(4)
        for _ in range(3):
            v.append(keytime()); time.sleep(3)
        res_[d] = sorted(v)[1]
    fresh(**NORIG, js8cfg_tx_delay='0.2')
    gap = (res_['0.1'] - res_['0.5']) % 10
    return .2 <= gap <= .6, f'{res_}, keyed {gap:.2f} s earlier, want 0.4'


def attn():
    m = re.search(r'name="%s"[^>]*?(?:value="|>)([^"<]*)' % ATTN, getprefs())
    return m.group(1) if m else None


def o16():
    """Power saved on 30 m comes back after a trip to 20 m at another power."""
    rigset('F 10136000')
    fresh(**RIG, js8cfg_pwr_band_tx=True, js8cfg_pwr_band_tx_memory=None, landscape_layout='pc', **{ATTN: '100'}); rotate('U'); time.sleep(5)
    api('TX.SEND_MESSAGE', 'HI', wait=.3); time.sleep(13)
    a = attn()
    rigset('F 14078000'); time.sleep(3)
    fresh(**RIG, js8cfg_pwr_band_tx=True, landscape_layout='pc', last_frequency='14078000', **{ATTN: '200'}); rotate('U'); time.sleep(5)
    api('TX.SEND_MESSAGE', 'HI', wait=.3); time.sleep(13)
    b = attn()
    rigset('F 10136000'); time.sleep(4)
    c = attn()
    m = re.findall(r'js8cfg_pwr_band_tx_memory"[^>]*?(?:value="|>)([^"<]*)', getprefs())
    fresh(**NORIG)
    return c == a and b != a, f'30 m {a}, 20 m {b}, back on 30 m {c}, memory {m}'


def p03():
    b = len(flog())
    fresh(**{**RIG, 'rig_type': 'hamlib_usb', 'rig_hamlib_model': '2'}, landscape_layout='pc'); rotate('U'); time.sleep(8)
    n = len(flog()) - b
    w = html.unescape(x())
    fresh(**NORIG, rig_hamlib_model='0')
    return n > 0 and 'Rig Control Error' not in w, f'{n} commands through Hamlib NET'


def backup():
    return adb('shell', f'run-as {PKG} base64 -w 0 {PREFS}').decode().strip()


def restore(b):
    stop()
    adb('shell', f"echo {b} | base64 -d | run-as {PKG} sh -c 'cat > {PREFS}'")


def s01_02():
    stop(); b = backup()
    adb('shell', f'run-as {PKG} rm -f {PREFS}')
    start(); time.sleep(4)
    for w_ in ('While using the app', 'Allow'):
        tapk(w_, 1)
    w = html.unescape(x()); shot(f's01_{_run}')
    s1 = 'My Callsign:' in w
    tapk('OK', 2)
    w2 = html.unescape(x()); shot(f's02_{_run}')
    adb('shell', 'input', 'keyevent', '4'); time.sleep(2)
    w3 = html.unescape(x())
    s2 = 'My Callsign:' in w3 or 'Enter' in w2
    restore(b)
    start(); time.sleep(3)
    ok_ = pv('callsign') == 'VE3TST' or 'VE3TST' in getprefs()
    return s1, s2, ok_, re.findall(r' text="([^"]+)"', w2)[:4]


def s01():
    R['s'] = s01_02()
    return R['s'][0] and R['s'][2], R['s'][3]


def s02():
    return R['s'][1] and R['s'][2], R['s'][3]


def g11():
    def files():
        o = adb('shell', 'ls', '/sdcard/Documents').decode('utf8', 'replace')
        return [len(re.findall(p, o)) for p in (r'js8call_log.*adi', r'js8call[ (\d)]*\.log', r'js8call_inbox')]
    f0 = files()
    up()
    inj('K4QSO: @ALLCALL CQ CQ CQ FN31', 900, 3, -5); time.sleep(2.5); top()
    holdk('K4QSO'); tapk('Log...', 2.5); tapk('Add to Log', 2.5); home()
    shot(f'g11z_{_run}'); top()
    tp('js8_btn_settings', 2)
    for _ in range(16):
        if 'Export Log...' in html.unescape(x()):
            break
        adb('shell', 'input', 'swipe', '600', '900', '600', '300', '350'); time.sleep(.7)
    tapk('Export Log...', 2)
    for n in [n for n in re.findall(r'<node [^>]*>', x()) if 'CheckBox' in n and 'checked="false"' in n]:
        b = bounds_of(n); adb('shell', 'input', 'tap', str((b[0] + b[2]) // 2), str((b[1] + b[3]) // 2)); time.sleep(.5)
    tapk('Export...', 3)
    w = html.unescape(x()); shot(f'g11a_{_run}')
    if 'Documents' in w:
        tapk('Documents', 2)
    w = html.unescape(x())
    for t_ in ('USE THIS FOLDER', 'Use this folder'):
        if t_ in w:
            tapk(t_, 2); break
    w = html.unescape(x()); shot(f'g11b_{_run}')
    for t_ in ('ALLOW', 'Allow'):
        if t_ in w:
            tapk(t_, 3); break
    time.sleep(2)
    w = html.unescape(x()); shot(f'g11c_{_run}')
    t_ = re.findall(r' text="([^"]+)"', w)[:6]
    home()
    f1 = files()
    return all(b_ > a_ for a_, b_ in zip(f0, f1)), f'{f0} to {f1}'


def j13():
    import sig
    up(tx_submode=0, js8cfg_filter_enabled=False, callsign='K4SPOT', js8cfg_auto_drift_stop=True)
    tn = None
    for _ in range(4):
        tn = sig.tones('UeNbK+GGOo00', 1, 0); time.sleep(18)
        if tn:
            break
    wf_tab('js8_wf_tab_timing'); reach('js8_wf_auto_drift_stop')
    if pv('js8cfg_auto_drift_stop') != 'true':
        tapk('js8_wf_auto_drift_stop', 1)
    reach('js8_wf_auto_drift'); tapk('js8_wf_auto_drift', 1.5)
    on_ = pv('js8cfg_auto_drift_stop_after_run')
    sig.play([(tn, 1500), (None, 0)], 'a.pcm', 0); time.sleep(40)
    reach('js8_wf_auto_drift')
    w = html.unescape(x()); shot(f'j13_{_run}')
    off = re.findall(r'js8cfg_auto_drift_on" value="(\w+)"', getprefs())
    fresh(callsign='VE3TST')
    return off == ['false'] and on_ not in (None, '0'), f'stop after {on_}, on now {off}'


row_list = [('A13', a13), ('O14', o14), ('O15', o15), ('O08', o08), ('O16', o16), ('P03', p03), ('S01', s01), ('S02', s02), ('G11', g11), ('J13U', j13)]
for i, f in row_list:
    row(i, f)
adb('shell', 'settings', 'put', 'system', 'user_rotation', '0')
json.dump(out, open(f'air14.{_run}.json', 'w'))
print('DONE', flush=True)
