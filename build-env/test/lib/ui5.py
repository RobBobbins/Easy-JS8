"""Upright screen rows that were left. ui5.py <run> [ID,ID]"""
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



NOAUTO = dict(autoreply_enabled=False, js8cfg_autoreply_startup=False, js8cfg_transmit_directed=False, log_messages_to_file=True)
up(**NOAUTO)
if want('H09'):
    tp('js8_btn_tune', 2); s_ = state(); ok = 'Tuning' in s_; halted(); fix('H09', ok, str(s_)); time.sleep(1)
if want('H28'):
    ok = reach('js8_status_state') and reach('js8_status_mode') and reach('js8_status_freq') and reach('js8_status_autoreply')
    w = html.unescape(x()); fix('H28', ok and 'Freq:' in w and 'Auto Reply' in w and 'JS8' in w, ''); home()
if want('H29'):
    fix('H29', reach('js8_level_meter'), 'meter on screen'); home()
if want('H30'):
    fix('H30', reach('js8_btn_cat'), 'CAT button on screen; colour by screenshot'); shot(f'h30_{_run}'); home()
if want('L27'):
    fix('L27', open_station(), 'Station list opened'); home()
if want('I09') or want('I10'):
    inj('K4AQA: @ALLCALL CQ CQ CQ FN31', 900, 3, -5); time.sleep(2.5); holdk('K4AQA')
    w = html.unescape(x()); fix('I09', 'speed' in w and 'Jump to' in w, ''); fix('I10', ' ms' in w, ''); home()
if want('I22'):
    inj('K4AQA: @ALLCALL CQ CQ CQ FN31', 900, 3, -5); time.sleep(2.5); holdk('K4AQA')
    has('Clear All Lists'); ok = tapk('Clear All Lists', 1.5); w = html.unescape(x()); fix('I22', ok and ('Cancel' in w or 'No' in w), re.findall(r' text="([^"]+)"', w)[:4])
    tapk('Cancel', 1) or tapk('No', 1); home()
if want('I23'):
    up(js8cfg_reset_activity=False, **NOAUTO); inj('K4AQA: @ALLCALL CQ CQ CQ FN31', 900, 3, -5); time.sleep(3); stop(); start(); rotate('U'); time.sleep(2); reach('js8_call_activity')
    fix('I23', 'K4AQA' in x(), ''); home()
if want('H37'):
    up(**NOAUTO); typed('RESTORE ME'); clearbox(); api('TX.SET_TEXT', '', wait=.5); tapk('js8_btn_halt', .8); holdk('js8_typing_box'); ok = has('Restore Previous Message') and tapk('Restore Previous Message', 1)
    bx = boxtext(); fix('H37', ok and 'RESTORE ME' in bx, bx[:40]); clearbox()
adb('shell', 'settings', 'put', 'system', 'user_rotation', '0')
json.dump(t.res, open(f'ui5.{_run}.json', 'w'))
print('DONE', flush=True)
