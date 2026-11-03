"""W07 to W09: heartbeats over the audio link with JS8Call on the PC (profile ELINK). tlink2.py <run> [ID,ID]
Each run uses its own calls on both ends: a station's HB is answered once in 55 minutes."""
import sys as _s
_r = int(_s.argv[1])
exec(open('tlink.py', encoding='utf8').read().split("print('devs'")[0])
APP = {1: 'VE3TSA', 2: 'VE3TSB'}.get(_r, 'VE3TSZ')  # any other run number: a trial
PCC = {1: 'K4PCA', 2: 'K4PCB'}.get(_r, 'K4PCZ')


def appup(sub, **k):
    """tlink appup with every setting open to change (the offset too)."""
    up(**{**dict(tx_submode=sub, autoreply_enabled=True, js8cfg_autoreply_startup=True, js8cfg_transmit_directed=True, js8cfg_autoreply_confirmation=False, js8cfg_tx_offset='1500', js8cfg_tcp_enabled=True, js8cfg_tcp_accept=True, js8cfg_tcp_max_connections=4), **k})
    L_.period = PERIOD[sub]; L_.sub = sub; L_.n0 = len(L_.ev); pc.base = len(pc.lines())
    pc.send('MODE.SET_SPEED', SPEED=sub); pc.send('RIG.SET_FREQ', DIAL=10130000, OFFSET=1500)
    time.sleep(2)


HB = dict(callsign=APP, js8cfg_hb=True, js8cfg_hb_ack=True, js8cfg_tx_offset='750', js8cfg_hb_rate_limit=False, js8cfg_hb_blacklist='', heartbeat_interval='1')


def pcprof(call):
    """The test profile with HB and ACK on, so JS8Call answers heartbeats."""
    p_ = os.path.expandvars(r'%LOCALAPPDATA%/JS8Call - ELINK/JS8Call - ELINK.ini')
    t_ = open(p_, encoding='utf8').read()
    for k, v in (('SubModeHB', 'true'), ('SubModeHBAck', 'true'), ('HeartbeatAckSNR', 'false'), ('AutoreplyOnAtStartup', 'true')):
        t_ = re.sub(rf'(?m)^{k}=.*$', f'{k}={v}', t_) if re.search(rf'(?m)^{k}=', t_) else t_.replace('[Configuration]\n', f'[Configuration]\n{k}={v}\n', 1)
    open(p_, 'w', encoding='utf8', newline='').write(t_)
    pcall(call)


def w07():
    """PC sends a heartbeat; the app answers it; JS8Call decodes the answer."""
    L_.f0 = 750
    appup(0, **HB); n0 = len(pc.ev)
    pc.send('TX.SEND_MESSAGE', '@HB HEARTBEAT FN03')
    got = heard(L_.ev, f'{PCC}: @HB HEARTBEAT', 60, L_.n0)
    r = pc.heard(f'{APP}: {PCC} HEARTBEAT SNR', 80) or pc.heard(f'{APP}: {PCC} ACK', 1)
    return got and r, f'app heard HB {got}, PC heard the answer {r}'


def w08():
    """App sends a heartbeat with the HB button; JS8Call answers; the app decodes the answer."""
    L_.f0 = 750
    appup(0, **HB); n0 = L_.n0
    reach('js8_btn_hb'); holdk('js8_btn_hb'); tapk('Send Heartbeat Now', .5)
    sent = pc.heard(f'{APP}: @HB HEARTBEAT', 60)
    r = heard(L_.ev, f'{PCC}: {APP} HEARTBEAT SNR', 80, n0) or heard(L_.ev, f'{PCC}: {APP} ACK', 1, n0)
    return sent and r, f'PC heard HB {sent}, app heard the answer {r}'


def w09():
    """HB button with a 1 minute repeat: the timed heartbeat goes out over audio and JS8Call decodes it."""
    L_.f0 = 750
    appup(0, **{**HB, 'js8cfg_hb_ack': False}); t0 = time.time()
    tp('js8_btn_hb', .5)
    r = pc.heard(f'{APP}: @HB HEARTBEAT', 110)
    took = round(time.time() - t0)
    tapk('js8_btn_halt', .5)
    return r and took >= 55, f'heard after {took} s'


pcprof(PCC)
row_list = [('W07', w07), ('W08', w08), ('W09', w09)]
for i, f in row_list:
    row(i, f)
up(callsign='VE3TST', js8cfg_hb_ack=False, js8cfg_hb=False)
pcall('K4PCT')
adb('shell', 'settings', 'put', 'system', 'user_rotation', '0')
json.dump(out, open(f'tlink2.{_run}.json', 'w'))
print('DONE', flush=True)
