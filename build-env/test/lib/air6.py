"""HB and CQ timers. air6.py <run> [ID,ID]"""
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


# HB button with a repeat time turns the timer on; first send comes after a full interval
up(**Q)
if want('E02'):
    b = len(alltxt()); tap0 = time.time(); tp('js8_btn_hb', .5); early = t.tx(b, 'HEARTBEAT', 35); h = t.tx(b, 'HEARTBEAT', 40)
    fix('E02', not early and h, f'{round(time.time() - tap0)} s, early {len(early)}'); idle(); halted(); time.sleep(1)
up(**Q)
if want('E04'):
    b = len(alltxt()); tap0 = time.time(); tp('js8_btn_cq', .5); early = t.tx(b, 'CQ CQ', 35); h = t.tx(b, 'CQ CQ', 40)
    fix('E04', not early and h, f'{round(time.time() - tap0)} s, early {len(early)}'); idle(); halted(); time.sleep(1)
up(**Q)
if want('E05') or want('E06'):
    tp('js8_btn_hb', 1); hb = timing('js8_btn_hb'); tp('js8_btn_cq', 1)
    fix('E05', hb and timing('js8_btn_cq') and not timing('js8_btn_hb'), lab('js8_btn_hb') + '/' + lab('js8_btn_cq'))
    tp('js8_btn_hb', 1)
    fix('E06', timing('js8_btn_hb') and not timing('js8_btn_cq'), lab('js8_btn_hb') + '/' + lab('js8_btn_cq'))
    halted(); time.sleep(1)
up(**Q)
if want('E07') or want('E08') or want('E09'):
    tp('js8_btn_hb', 1); on_ = timing('js8_btn_hb')
    select(); off = not timing('js8_btn_hb')
    fix('E07', on_ and off, lab('js8_btn_hb'))
    tp('js8_btn_deselect', 1)
    fix('E08', timing('js8_btn_hb'), lab('js8_btn_hb'))
    halted(); fix('E09', not timing('js8_btn_hb') and not timing('js8_btn_cq'), lab('js8_btn_hb'))
up(**Q)
if want('E10'):
    tp('js8_btn_cq', 1); on_ = timing('js8_btn_cq'); a = c(); pl = 'HELLO'
    inj(f'{a}: {M} MSG {pl} {sum16(pl)}', 1500, 3); time.sleep(3)
    fix('E10', on_ and not timing('js8_btn_cq'), lab('js8_btn_cq')); halted()
adb('shell', 'settings', 'put', 'system', 'user_rotation', '0')
json.dump(t.res, open(f'air6.{_run}.json', 'w'))
print('DONE', flush=True)
