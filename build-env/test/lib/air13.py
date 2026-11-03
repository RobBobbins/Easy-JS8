"""Heartbeat, rig, reporting, update and startup rows. air13.py <run> [ID,ID]. fakes.py, fakepsk.py and fakeaprs.py must be running."""
import sys as _s
exec(open('ui7.py', encoding='utf8').read().split("row_list = [")[0].replace('"""Upright rows never tested: H, I, J. ui7.py <run> [ID,ID]"""', ''))
t = T(int(_run), only)
c = t.call
M = 'VE3TST'
T.n = (int(time.time()) // 60 % 200) * 26
AUTO = dict(autoreply_enabled=True, js8cfg_autoreply_startup=True, js8cfg_transmit_directed=True, relay_enabled=True)
RIG = dict(rig_control_enabled=True, rig_type='network', rig_network_kind='rigctl', rigctld_host='10.0.2.2', rigctld_port='4599', js8cfg_cat_poll_interval='1', js8cfg_ptt_method='cat', js8cfg_split='none', transmit_mode='usb', js8cfg_hold_ptt=False)
NORIG = dict(rig_control_enabled=False, rig_type='none', js8cfg_ptt_method='vox', js8cfg_split='none', js8cfg_pwr_band_tx=False)


def flog():
    return open('fakes.log', encoding='utf8', errors='replace').read().splitlines()


def rigset(cmd):
    s = socket.create_connection(('127.0.0.1', 4599), 5); s.send((cmd + '\n').encode()); s.recv(100); s.close()


def tx_lines(base):
    return [l.split('JS8:')[-1].strip() for l in alltxt()[base:] if 'Transmitting' in l]


def hb_off(wait=20):
    """Offset of the newest heartbeat in the Message Window."""
    end = time.time() + wait
    while time.time() < end:
        top_ = re.findall(r'\((\d+)\) - VE3TST: @HB', html.unescape(x()))
        if top_:
            return int(top_[-1])
        time.sleep(1)


def send_hb():
    reach('js8_btn_hb'); holdk('js8_btn_hb'); tapk('Send Heartbeat Now', .5)


def e18():
    up(js8cfg_tx_offset='1500', js8cfg_heartbeat_anywhere=False)
    time.sleep(3)
    for f in range(500, 1000, 50):
        if f not in (650, 700, 750, 800):
            inj(f'{c()}: @ALLCALL CQ CQ CQ FN31', f, 3, -5); time.sleep(.3)
    time.sleep(2); send_hb(); o = hb_off(25)
    return o in (650, 700, 750, 800), o


def e19():
    up(js8cfg_tx_offset='900'); send_hb(); o = hb_off(25)
    return o == 900, o


def e20():
    up(js8cfg_tx_offset='1500', js8cfg_heartbeat_anywhere=True); send_hb(); o = hb_off(25)
    fresh(js8cfg_heartbeat_anywhere=False)
    return o == 1500, o


def e21():
    up(heartbeat_interval='5')
    reach('js8_btn_hb'); tapk('js8_btn_hb', 1)
    t0 = time.time()
    time.sleep(282 - 15)
    b = len(alltxt()); api('TX.SEND_MESSAGE', 'LONG MESSAGE TO KEEP THE TX BUSY', wait=.3)
    time.sleep(75)
    l = tx_lines(b)
    tapk('js8_btn_hb', 1) if find(x(), 'js8_btn_hb') else None
    li = [i for i, s in enumerate(l) if 'LONG' in s]; hi = [i for i, s in enumerate(l) if 'HEARTBEAT' in s]
    return bool(li) and bool(hi) and hi[0] > li[-1], l[-4:]


def e32():
    up(); typed('AAAAAAAAAAAA'); tapk('js8_btn_send', 1.5)
    w = html.unescape(x())
    r = 'Message Not Sent' in w
    tapk('OK', 1); clearbox()
    return r, ''


def b22():
    up(**{**AUTO, 'js8cfg_autoreply_confirmation': True})
    a = c(); d = c(1); p = f'{a} HELD ONE'
    inj(f'{d}: {M} MSG TO:{p} {sum16(p)}', 900, 3, -5)
    time.sleep(6)
    w = html.unescape(x())
    if 'No' in re.findall(r' text="([^"]+)"', w):
        tapk('No', 1)
    time.sleep(25)
    w = html.unescape(x())
    if 'No' in re.findall(r' text="([^"]+)"', w):
        tapk('No', 1)
    b = len(alltxt()); inj(f'{a}: {M} QUERY MSGS', 900, 3, -5); time.sleep(5)
    w = html.unescape(x()); shown = 'No' in re.findall(r' text="([^"]+)"', w)
    if shown:
        tapk('No', 1)
    up(**{**AUTO, 'js8cfg_autoreply_confirmation': False})
    b = len(alltxt()); inj(f'{a}: {M} QUERY MSGS', 900, 3, -5)
    h = t.tx(b, f'{a} YES MSG ID', 24)
    return shown and bool(h), f'box shown {shown}, {h[:1]}'


def d12():
    up(**AUTO); a = c(); inj(f'{a}: @ALLCALL CQ CQ CQ FN31', 900, 3, -5); time.sleep(2.5); top()
    holdk(a); has('Store Message...'); tapk('Store Message...', 1.5)
    import uiautomator2
    uiautomator2.connect('emulator-5600')(className='android.widget.EditText')[-1].set_text('STORED FOR YOU'); time.sleep(.5)
    tapk('button1', 1.5); b = len(alltxt())
    inj(f'{a}: {M} QUERY MSGS', 900, 3, -5)
    h = t.tx(b, f'{a} YES MSG ID', 24)
    return bool(h), h[:1]


def d11():
    up(**{**AUTO, 'js8cfg_autoreply_confirmation': False}); s_ = c(); pl = '@TESTG GROUP HELLO'
    inj(f'{s_}: {M} MSG TO:{pl} {sum16(pl)}', 900, 3, -5); time.sleep(16)
    a1 = c(); a2 = c(); r = []
    for a in (a1, a2):
        b = len(alltxt()); inj(f'{a}: @TESTG QUERY MSGS', 900, 3, -5); h = t.tx(b, f'{a} YES MSG ID', 24); r.append(h); time.sleep(12)
    if not r[0] or not r[1]:
        return False, r
    i = re.search(r'MSG ID (\d+)', r[0][0]).group(1)
    got = []
    for a in (a1, a2):
        b = len(alltxt()); q = f'MSG {i}'; inj(f'{a}: {M} QUERY {q} {sum16(q)}', 900, 3, -5); h = t.tx(b, 'GROUP HELLO', 30); got.append(bool(h)); print(a, tx_lines(b), flush=True); time.sleep(18)
    b = len(alltxt()); inj(f'{a1}: @TESTG QUERY MSGS', 900, 3, -5); time.sleep(16)
    again = [l for l in tx_lines(b) if 'YES MSG ID' in l]
    return all(got) and not again, f'{got}, offered again {again[:1]}'


def d14():
    up(**{**AUTO, 'js8cfg_autoreply_confirmation': False, 'js8cfg_hb_ack': True, 'js8cfg_hb': True, 'js8cfg_heartbeat_qso_pause': False, 'my_groups': '@TESTG,@HB'})
    s_ = c(); pl = '@HB GROUP TWO'
    inj(f'{s_}: {M} MSG TO:{pl} {sum16(pl)}', 900, 3, -5); time.sleep(16)
    a = c(); b = len(alltxt()); inj(f'{a}: @HB HEARTBEAT FN31', 1500, 3, -5)
    h = t.tx(b, 'MSG ID', 30)
    r = bool(h), h[:1] or tx_lines(b)[:2]
    fresh(my_groups='@TESTG')
    return r


def aprsl():
    return open('aprs.log', encoding='utf8', errors='replace').read().splitlines()


def r04():
    up(psk_reporter=True, aprs_is=True, js8cfg_aprs_relay=True, js8cfg_aprs_server='10.0.2.2', js8cfg_aprs_port='14580')
    time.sleep(8); b = len(aprsl())
    top(); tapk('js8_btn_rx', 2); time.sleep(5)
    l = aprsl()[b:]
    tapk('js8_btn_rx', 2); fresh(psk_reporter=False, aprs_is=False, js8cfg_aprs_relay=False)
    return any('DISCONNECT' in s for s in l), l[-2:]


SPOT = {}
FRAME0 = 'UeNbK+GGOo00'


def real_spot():
    """A real decode, the only kind that reaches processSpots; RX.SPOT on the API, then RX off flushes the report."""
    import sig
    tn = None
    up(psk_reporter=True, aprs_is=False, js8cfg_aprs_server='10.0.2.2', my_info='ZTESTANT', tx_submode=0, js8cfg_filter_enabled=False, callsign='K4SPOT')
    for _ in range(4):
        tn = sig.tones(FRAME0, 1, 0); time.sleep(18)
        if tn:
            break
    adb('forward', 'tcp:2442', 'tcp:2442')
    so = socket.create_connection(('127.0.0.1', 2442), 5); so.settimeout(1)
    base = len(alltxt()); n0 = len(open('fakepsk.log', errors='replace').read().splitlines())
    sig.play([(tn, 1500), (None, 0)], 'a.pcm', 0)
    b_ = b''; end = time.time() + 50
    while time.time() < end and b'RX.SPOT' not in b_:
        try:
            b_ += so.recv(65536)
        except OSError:
            pass
    so.close()
    print('heard', [l[:80] for l in alltxt()[base:]][:3], flush=True)
    top(); tapk('js8_btn_rx', 3); time.sleep(6); tapk('js8_btn_rx', 2)
    pk = open('fakepsk.log', errors='replace').read().splitlines()[n0:]
    SPOT.update(q=b'RX.SPOT' in b_, ant=any('ZTESTANT' in l for l in pk), prog=any('EasyJS8-' in l for l in pk), n=len(pk))
    fresh(psk_reporter=False, my_info=None, callsign='VE3TST')
    return SPOT['q'], SPOT


def update(ver, url):
    os.makedirs('updsrv', exist_ok=True); open('updsrv/version.txt', 'w').write(ver)
    up(js8cfg_check_updates=True, debug_update_url=url); time.sleep(6)
    w = html.unescape(x()); shot(f'upd_{_run}')
    return w, re.findall(r' text="([^"]+)"', w)[:4]


def r13():
    p = subprocess.Popen(['python', '-m', 'http.server', '8770', '--bind', '127.0.0.1', '--directory', 'updsrv'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(1.5)
    w, r = update('99.0.0', 'http://10.0.2.2:8770/version.txt')
    box = 'New Updates Available' in w and '99.0.0' in w
    tapk('button1', 1)
    w, r2 = update('0.0.1', 'http://10.0.2.2:8770/version.txt')
    quiet = 'New Updates Available' not in w
    p.kill()
    return box and quiet, f'newer box {box}, older quiet {quiet}'


def r14():
    w, r = update('0.0.1', 'http://10.0.2.2:9/none.txt')
    bad = 'New Updates Available' not in w and 'Error' not in w
    fresh(debug_update_url=None)
    return bad, 'no box'


def o13():
    fresh(**RIG, landscape_layout='pc'); rotate('U'); time.sleep(5)
    subprocess.run('taskkill /F /FI "COMMANDLINE eq *fakes.py*"', shell=True, capture_output=True)
    subprocess.run(['powershell', '-c', "Get-CimInstance Win32_Process -Filter \"Name='python.exe'\" | ? { $_.CommandLine -like '*fakes.py*' } | % { Stop-Process -Id $_.ProcessId -Force }"], capture_output=True)
    w = ''
    end = time.time() + 30
    while time.time() < end:
        w = html.unescape(x())
        if 'Rig Control Error' in w:
            break
        time.sleep(2)
    box = 'Rig Control Error' in w
    subprocess.run(['powershell', '-c', "Start-Process python -ArgumentList 'fakes.py' -WindowStyle Hidden -WorkingDirectory (Get-Location)"]); time.sleep(3)
    b = len(flog()); tr = tapk('RETRY', 3); time.sleep(5)
    n = len(flog()) - b; print('retry tapped', tr, re.findall(r' text="([^"]+)"', html.unescape(x()))[:5], flush=True)
    fresh(**NORIG)
    return box and n > 0, f'box {box}, {n} commands after Retry'


def o16():
    KEY = 'js8cfg_out_attenuation'
    rigset('F 10136000')
    fresh(**RIG, js8cfg_pwr_band_tx=True, tx_submode=2, out_attenuation='100', landscape_layout='pc'); time.sleep(4)
    return None


def o08():
    res_ = []
    for d in ('0.1', '0.5'):
        fresh(**RIG, js8cfg_tx_delay=d, tx_submode=2, landscape_layout='pc'); time.sleep(4)
        adb('logcat', '-c'); api('TX.SEND_MESSAGE', 'HI LEAD', wait=.3); time.sleep(14)
        l = adb('logcat', '-d', '-v', 'time').decode('utf8', 'replace').splitlines()
        res_.append([s[:150] for s in l if re.search(r'PTT|ptt|Audio start|TX audio|txAudio', s)][:3])
    fresh(**NORIG, js8cfg_tx_delay='0.2')
    return False, res_


row_list = [('E18', e18), ('E19', e19), ('E20', e20), ('E32', e32), ('B22', b22), ('D12', d12), ('D11', d11), ('D14', d14),
            ('R13', r13), ('R14', r14), ('R04', r04), ('Q07', real_spot), ('R11', lambda: (SPOT.get('ant'), SPOT)), ('R12', lambda: (SPOT.get('prog'), SPOT)), ('O13', o13), ('E21', e21)]
for i, f in row_list:
    row(i, f)
adb('shell', 'settings', 'put', 'system', 'user_rotation', '0')
json.dump(out, open(f'air13.{_run}.json', 'w'))
print('DONE', flush=True)
