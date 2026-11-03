"""Startup rows. air8.py <run> [ID,ID]"""
import sys as _s
_run = _s.argv[1]
only = _s.argv[2].split(',') if len(_s.argv) > 2 else None
_s.argv = ['ui1', _run, 'U', '']
src = open('ui1.py', encoding='utf8').read().split('\nfor code in LAYS:')[0]
exec(src)
t = T(int(_run), only)
c = t.call
M = 'VE3TST'
T.n = (int(time.time()) // 60 % 200) * 26


def want(i):
    return not only or i in only


def fix(i, ok, note=''):
    if want(i):
        t.mark(i, bool(ok), note)


def up(**k):
    """Upright, autoreply on unless a row turns it off, fast sends."""
    fresh(**{'landscape_layout': 'pc', 'tx_submode': 1, 'js8cfg_tx_watchdog': '0', 'js8cfg_api_dynamic_info': False, 'js8cfg_reset_activity': True, **k})
    rotate('U')
    time.sleep(1)


def state():
    return [s for s in ('Receiving', 'Decoding', 'Tuning', 'Tx:', 'Transmitting') if s in x()]


def sending(base, pat, wait=20):
    return t.tx(base, pat, wait)


def idle(wait=30):
    """Waits until nothing is sending or queued, so the next button is not refused."""
    end = time.time() + wait
    while time.time() < end and any(s in state() for s in ('Tx:', 'Transmitting')):
        time.sleep(1)
    time.sleep(3)


def typed(s):
    tapk('js8_typing_box', .8)
    text(s)
    adb('shell', 'input', 'keyevent', '111')
    time.sleep(.8)


def send(s):
    typed(s)
    tapk('js8_btn_send', .5)


def menu_send(btn, item):
    return holdk(btn) and tapk(item, .5)


def halted(wait=6):
    tapk('js8_btn_halt', .5)
    end = time.time() + wait
    while time.time() < end:
        s = state()
        if 'Tx:' not in s and 'Transmitting' not in s and 'Tuning' not in s:
            return True
        time.sleep(.5)
    return False



Q = dict(autoreply_enabled=False, js8cfg_autoreply_startup=False, heartbeat_interval='1')
Q['js8cfg_cq_interval'] = '1'


def lab(k):
    n = re.search(r'<node [^>]*%s"[^>]*>' % k, x())
    return html.unescape(re.search(r' text="([^"]*)"', n.group(0)).group(1)) if n else ''


def timing(k):
    time.sleep(1.2)
    l = lab(k)
    return l.isdigit() or l == 'now'



def restart():
    stop(); start(); rotate('U'); time.sleep(2)


if want('S03'):
    up(js8cfg_monitor_off=True); s_ = state(); shot(f's03_{_run}')
    fix('S03', 'Receiving' not in s_ and 'Decoding' not in s_, str(s_))
if want('S04'):
    up(js8cfg_transmit_off=True); a = c(); t.silent('S04', [f'{a}: {M} SNR?'], a, 12)
if want('S05'):
    # Unticked, AUTO starts off even when it was left on, as the PC (UI_Constructor: setChecked(autoreply_on_at_startup))
    up(autoreply_enabled=True, js8cfg_autoreply_startup=False); time.sleep(2)
    m = re.search(r'name="autoreply_enabled" value="([^"]*)"', getprefs())
    if not (m and m.group(1) == 'false'):
        fix('S05', False, f'box unticked, AUTO at start {m.group(1) if m else None}')
    up(autoreply_enabled=False, js8cfg_autoreply_startup=True); a = c(); t.expect('S05', [f'{a}: {M} SNR?'], f'{a} SNR')
    idle()
if want('S06'):
    up(**Q); tp('js8_btn_hb', 1); on_ = timing('js8_btn_hb'); restart()
    fix('S06', on_ and timing('js8_btn_hb'), lab('js8_btn_hb')); halted()
if want('S07'):
    up(js8cfg_reset_activity=False); select(); had = 'K4AQA' in x(); home(); tp('js8_btn_deselect', 1); restart(); kept = 'K4AQA' in x()
    setprefs(js8cfg_reset_activity=True); start(); rotate('U'); time.sleep(2); reach('js8_call_activity'); gone = 'K4AQA' not in x()
    fix('S07', had and kept and gone, f'had {had}, kept with box off {kept}, gone with box on {gone}')
if want('S08'):
    up(js8cfg_wf_center=1700); restart()
    m = re.search(r'name="js8cfg_wf_center"(?: value="([^"]*)"[^>]*/>|>([^<]*)<)', getprefs())
    fix('S08', m and (m.group(1) or m.group(2)) == '1700', str(m.groups() if m else None))
    fix('S08b', 'js8_btn_settings' in x(), 'app still opens')
adb('shell', 'settings', 'put', 'system', 'user_rotation', '0')
json.dump(t.res, open(f'air8.{_run}.json', 'w'))
print('DONE', flush=True)
