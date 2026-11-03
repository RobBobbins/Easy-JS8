"""Failed rows with corrected checks: Q10, H28U, E36, S07, H37U. ui6.py <run> [ID,ID]"""
import sys as _s
_run = _s.argv[1]
only = _s.argv[2].split(',') if len(_s.argv) > 2 else None
_s.argv = ['ui1', _run, 'U', '']
src = open('ui1.py', encoding='utf8').read().split('\nfor code in LAYS:')[0]
exec(src)
t = T(int(_run), only)
NOAUTO = dict(autoreply_enabled=False, js8cfg_autoreply_startup=False, js8cfg_transmit_directed=False, log_messages_to_file=True)


def want(i):
    return not only or i in only


def fix(i, ok, note=''):
    if want(i):
        t.mark(i, bool(ok), note)


def up(**k):
    fresh(**{'landscape_layout': 'pc', 'tx_submode': 1, 'js8cfg_tx_watchdog': '0', 'js8cfg_api_dynamic_info': False, 'js8cfg_reset_activity': True, **NOAUTO, **k})
    rotate('U')
    time.sleep(1)


def idle(wait=45):
    """The Typing Box empties after the last part; between parts the bar says Receiving."""
    end = time.time() + wait
    while time.time() < end and 'TYPE YOUR' not in boxtext():
        time.sleep(1)
    time.sleep(1)


def typed(s):
    tapk('js8_typing_box', .8)
    text(s)
    adb('shell', 'input', 'keyevent', '111')
    time.sleep(.8)


def bar():
    """Status bar scrolled to its right end; the labels past the screen edge are then on screen."""
    if not reach('js8_status_state'):
        return ''
    p = find(x(), 'js8_status_state')
    seen = html.unescape(x())
    for _ in range(4):
        adb('shell', 'input', 'swipe', '700', str(p[1]), '250', str(p[1]), '400')
        time.sleep(1.2)
        seen += html.unescape(x())
    return seen


if want('H28U'):
    up()
    w = bar()
    fix('H28U', 'Freq:' in w and 'Auto Reply' in w and 'JS8' in w, re.findall(r' text="((?:JS8|Freq:|Auto Reply)[^"]*)"', w))
    home()
if want('E36'):
    up()
    typed('HI LAST'); tapk('js8_btn_send', .5); idle()
    w = bar()
    fix('E36', 'Last Tx' in w, re.findall(r' text="(Last Tx[^"]*)"', w))
    home()
if want('H37U'):
    up()
    typed('RESTORE ME'); tapk('js8_btn_send', .5); idle()
    holdk('js8_typing_box'); ok = has('Restore Previous Message') and tapk('Restore Previous Message', 1)
    bx = boxtext(); fix('H37U', ok and 'RESTORE ME' in bx, bx[:40]); clearbox()
    home()
if want('S07'):
    who = 'K4Z' + 'ABCDEFGH'[int(time.time() / 60) % 8] + 'Q'
    up(js8cfg_reset_activity=False)
    inj(f'{who}: @ALLCALL CQ CQ CQ FN31', 900, 3, -5); time.sleep(8); reach('js8_call_activity'); had = who in x()
    stop(); start(); rotate('U'); time.sleep(2); reach('js8_call_activity'); kept = who in x()
    time.sleep(1)
    setprefs(js8cfg_reset_activity=True); start(); rotate('U'); time.sleep(2); reach('js8_call_activity'); gone = who not in x()
    fix('S07', had and kept and gone, f'{who} had {had}, kept with box off {kept}, gone with box on {gone}')
if want('Q10'):
    up(js8cfg_tcp_max_connections=1); time.sleep(2)
    adb('forward', 'tcp:2442', 'tcp:2442')
    a = socket.create_connection(('127.0.0.1', 2442), 5); time.sleep(1)
    b2 = socket.create_connection(('127.0.0.1', 2442), 5); b2.settimeout(8)
    got = b''; closed = False
    try:
        while True:
            d = b2.recv(200)
            if not d:
                closed = True
                break
            got += d
    except OSError:
        pass
    fix('Q10', closed and b'Connections Full' in got, got[:90].decode('utf8', 'replace'))
    a.close(); b2.close()
adb('shell', 'settings', 'put', 'system', 'user_rotation', '0')
json.dump(t.res, open(f'ui6.{_run}.json', 'w'))
print('DONE', flush=True)
