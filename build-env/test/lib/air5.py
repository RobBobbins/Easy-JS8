"""Decodes on screen, buttons that send, Halt, TUNE, macros. air5.py <run> [ID,ID]"""
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


up(autoreply_enabled=False, js8cfg_autoreply_startup=False)
# decodes on screen
if want('A11') or want('A12'):
    a = c(); inj(f'{a}: @ALLCALL CQ CQ CQ FN31', 1500, 3, -7); time.sleep(3)
    xml = x()
    fix('A11', '1500' in xml and 'CQ' in xml, 'Band Activity')
    reach('js8_call_activity')
    fix('A12', a in x(), a)
if want('A10'):
    a = c(); pl = 'FIRST PART'; b2 = 'SECOND PART'
    inj(f'{a}: {M} MSG {pl}', 1600, 1); time.sleep(.5)
    inj(f' {b2} {sum16(pl + " " + b2)}', 1600, 6); time.sleep(3)
    xml = html.unescape(x())
    fix('A10', f'{pl} {b2}' in xml, 'one line in the Message Window')
if want('A14'):
    a = c(); inj(f'{a}: {M} DUPE ONE', 1700, 3); time.sleep(.5); inj(f'{a}: {M} DUPE ONE', 1700, 3); time.sleep(3)
    w = [n for n in re.findall(r'<node [^>]*>', x()) if 'DUPE ONE' in n]
    fix('A14', len(w) == 2 and all(n.count('DUPE ONE') == 1 for n in w), f'{len(w)} lists, {[n.count("DUPE ONE") for n in w]} copies')
# buttons that send
up(autoreply_enabled=False, js8cfg_autoreply_startup=False, js8cfg_transmit_directed=True)
if want('E01'):
    b = len(alltxt()); ok = menu_send('js8_btn_hb', 'Send Heartbeat Now'); h = sending(b, 'HEARTBEAT|HB ')
    fix('E01', ok and len(h) == 1, str(h[:1])[-60:]); idle()
if want('E03'):
    b = len(alltxt()); ok = menu_send('js8_btn_cq', 'Send CQ Now'); h = sending(b, 'CQ')
    fix('E03', ok and len(h) == 1, str(h[:1])[-60:]); idle()
if want('E36'):
    fix('E36', 'Last Tx' in x(), 'status bar label')
if want('E24'):
    b = len(alltxt()); send('HALT TEST LONG MESSAGE ONE TWO THREE FOUR FIVE SIX SEVEN EIGHT NINE TEN')
    h = sending(b, 'HALT|.', 16); ok = halted(); n = len([l for l in alltxt()[b:] if 'Transmitting' in l]); time.sleep(12)
    n2 = len([l for l in alltxt()[b:] if 'Transmitting' in l])
    fix('E24', h and ok and n2 == n, f'frames at Halt {n}, 12 s later {n2}'); api('TX.SET_TEXT', '', wait=.5); time.sleep(4)
if want('E25'):
    b = len(alltxt()); menu_send('js8_btn_hb', 'Send Heartbeat Now'); h = sending(b, '.', 16); ok = halted()
    fix('E25', h and ok, str(state())); time.sleep(4)
if want('E27') or want('E26'):
    tp('js8_btn_tune', 2); on_ = state()
    fix('E27', 'Tx:' in on_ or 'Tuning' in on_, f'on {on_}')
    if want('E26'):
        fix('E26', halted(), str(state()))
    else:
        tp('js8_btn_tune', 2)
    time.sleep(2)
    fix('E27b', not ('Tx:' in state()), 'off')
if want('E29'):
    tp('js8_btn_tune', 2); before = x(); reach('js8_btn_rx'); tapk('js8_btn_rx', 1); s_ = state(); halted(); time.sleep(2)
    fix('E29', 'Tx:' in s_, f'still sending after the RX tap: {s_}')
if want('E34'):
    b = len(alltxt()); send('TYPE AHEAD ONE TWO THREE FOUR FIVE SIX SEVEN EIGHT'); sending(b, '.', 16)
    tapk('js8_typing_box', .5); text('MORE'); adb('shell', 'input', 'keyevent', '111'); time.sleep(1)
    fix('E34', 'MORE' in boxtext(), boxtext()[:50]); halted(); api('TX.SET_TEXT', '', wait=.5); time.sleep(3)
if want('E37'):
    for sub, nm in ((0, 'Normal'), (1, 'Fast'), (2, 'JS840'), (8, 'Slow')):
        pass
# macros through STATUS?
up(my_status='IDLE', my_info='<MYSTATUS>')
if want('F05'):
    a = c(); b = len(alltxt()); inj(f'{a}: {M} INFO?'); h = t.tx(b, a, 24); l = (h or [''])[0]
    fix('F05', 'IDLE' in l and '<' not in l.split('JS8:')[-1], l[-60:]); time.sleep(8)
up(my_status='X<MYHB>Y')
if want('F11'):
    a = c(); b = len(alltxt()); inj(f'{a}: {M} STATUS?'); h = t.tx(b, a, 24); l = (h or [''])[0]
    fix('F11', 'XY' in l, l[-60:]); time.sleep(8)
up(js8cfg_reply_message='<CALL> <SNR> <TDELTA>', autoreply_enabled=False, js8cfg_autoreply_startup=False)
if want('F08') or want('F09') or want('F10'):
    a = 'K4AQA'; select()
    tp('js8_btn_reply', 1.5); bx = boxtext(); print('box', bx, flush=True)
    fix('F08', a in bx, bx); fix('F09', '-05' in bx, bx); fix('F10', ' ms' in bx or 'MS' in bx, bx)
    clearbox()
up()
if want('B06'):
    fresh(js8cfg_reset_activity=True, tx_submode=1, js8cfg_tx_watchdog='0'); rotate('U')
    a = c(); t.expect('B06', [f'{a}: {M} HEARING?'], f'{a} HEARING')
adb('shell', 'settings', 'put', 'system', 'user_rotation', '0')
res_ = [r for r in t.res]
json.dump(res_, open(f'air5.{_run}.json', 'w'))
print('DONE', flush=True)
