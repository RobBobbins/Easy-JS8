"""Log files, ACK pause, sending without a callsign, priority. air7.py <run> [ID,ID]"""
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



LOGS = '/sdcard/Download/JS8CallLogs/'
NOAUTO = dict(autoreply_enabled=False, js8cfg_autoreply_startup=False, js8cfg_transmit_directed=True, log_messages_to_file=True)


def cat(n):
    return adb('exec-out', 'run-as', PKG, 'cat', 'files/' + n).decode('utf8', 'replace')


def logqso():
    select()
    tp('js8_btn_log', 2.5)
    return tapk('Add to Log', 2)


up(tx_submode=0, js8cfg_heartbeat_qso_pause=True)
if want('E15'):
    select(); a = c(); t.silent('E15', [f'{a}: @HB HEARTBEAT CM87'], a, 30); home(); tp('js8_btn_deselect', 1)
up(js8cfg_avoid_forced_identify=True, **NOAUTO)
if want('E33'):
    b = len(alltxt()); send('NOCALL TEST'); h = t.tx(b, 'NOCALL', 20); l = (h or [''])[0]
    fix('E33', h and M not in l, l[-50:]); idle()
up(**NOAUTO)
if want('G07') or want('G08'):
    a0, c0 = cat('js8call_log.adi').count('<EOR>') + cat('js8call_log.adi').count('<eor>'), len(cat('js8call.log').splitlines())
    ok = logqso()
    a1, c1 = cat('js8call_log.adi').count('<EOR>') + cat('js8call_log.adi').count('<eor>'), len(cat('js8call.log').splitlines())
    fix('G07', ok and a1 == a0 + 1, f'{a0} to {a1} records'); fix('G08', ok and c1 == c0 + 1, f'{c0} to {c1} lines')
    home(); tp('js8_btn_deselect', 1)
fresh(landscape_layout='pc', **NOAUTO); rotate('P'); time.sleep(2); L = 'P'
if want('G09'):
    b = len(alltxt()); ok = tp('js8_menubar_log', 1.5) and tapk('Erase ALL.TXT', 1.5) and has('Confirm Erase')
    tapk('Yes', 1.5); time.sleep(1)
    n = len(alltxt()); fix('G09', ok and n == 0, f'{b} to {n} lines'); home()
if want('G10'):
    b = len(cat('js8call_log.adi')); ok = tp('js8_menubar_log', 1.5) and tapk('Erase js8call_log.adi', 1.5) and has('Confirm Erase')
    tapk('Yes', 1.5); time.sleep(1)
    n = cat('js8call_log.adi').count('<EOR>') + cat('js8call_log.adi').count('<eor>'); fix('G10', ok and n == 0, f'{b} bytes before, {n} records after'); home()
L = 'U'
up(**{**NOAUTO, 'log_messages_to_file': False})
if want('G06'):
    b = len(alltxt()); send('NOLOG TEST'); time.sleep(16); n = len(alltxt())
    fix('G06', n == b, f'{b} to {n} lines'); idle()
up(**NOAUTO)
if want('G04'):
    b = len(alltxt()); send('HEADER TEST'); t.tx(b, 'HEADER', 20); x_ = alltxt()
    fix('G04', any(re.search(r'MHz +JS8$', l.strip()) or re.search(r'^\d{4}-\d\d-\d\d', l) for l in x_[:3]), str(x_[:1])[:70]); idle()
adb('shell', 'settings', 'put', 'system', 'user_rotation', '0')
json.dump(t.res, open(f'air7.{_run}.json', 'w'))
print('DONE', flush=True)
