"""Station window checks and OK and Cancel. ui3.py <run> [ID,ID]"""
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



OK0 = dict(callsign='VE3TST', my_groups='@TESTG', js8cfg_cq_message='CQ CQ CQ <MYGRID4>', js8cfg_eot='♢', js8cfg_auto_whitelist='', js8cfg_swl=False)
OK0['grid'] = 'CM87WK91'


def okbox(**bad):
    """Puts a bad value in, presses OK in Station, returns the words on the box that opens."""
    setprefs(**{**OK0, **bad}); start(); rotate('U'); time.sleep(1); open_station()
    tapk('OK', 1.5); time.sleep(.5)
    xml = html.unescape(x())
    return xml


def closebox():
    tapk('OK', 1)
    for _ in range(3):
        if at_main():
            return
        tapk('← Back', 1.5) if on('← Back') else adb('shell', 'input', 'keyevent', '4')
        time.sleep(1)


for row, bad, msg in (('M02', dict(callsign='B@D!'), 'callsign format you provided is not supported'),
                      ('M04', dict(grid='ZZZZ'), 'The grid you provided is not valid'),
                      ('M06', dict(my_groups='@JS8NET'), 'is a group that cannot be joined'),
                      ('M07', dict(js8cfg_cq_message='HELLO THERE'), 'The CQ message format is invalid'),
                      ('M11', dict(js8cfg_eot=''), 'end of transmission character'),
                      ('M16', dict(js8cfg_auto_whitelist='B@D!'), 'is not a valid callsign to whitelist')):
    if want(row):
        xml = okbox(**bad)
        if msg not in xml:
            shot(f'{row.lower()}_{L}_{_run}')
        fix(row, msg in xml, msg[:40])
        closebox()
if want('M03'):
    xml = okbox(callsign='', grid='', js8cfg_swl=True)
    time.sleep(1); ok = 'not supported' not in xml and 'not valid' not in xml
    fix('M03', ok and 'js8_btn_settings' not in xml and 'Radio' not in x(), 'OK leaves Station with no callsign when SWL is ticked')
    home()


def page(p):
    open_station(); return tapk(p, 2)


def toggle(row, p, label, key, press='OK'):
    if not want(row):
        return
    setprefs(**OK0); start(); rotate('U'); time.sleep(1)
    a = pv_(key); page(p); seen = False
    for _ in range(10 if L == 'U' else 16):  # sideways a swipe moves the page about 340 px
        if find(x(), label):
            seen = True
            break
        adb('shell', 'input', 'swipe', '600', '900' if L == 'U' else '780', '600', '400', '350'); time.sleep(.6)  # sideways 900 is on Save
    tapk(label, 1)
    tapk('← Back', 1.5)
    tapk(press, 2) if press == 'OK' else tapk('← Back', 2)
    home()
    b = pv_(key)
    fix(row, seen and ((a != b) if press == 'OK' else (a == b)), f'{a} to {b}')


def pv_(key):
    m = re.search(r'name="%s"(?: value="([^"]*)"[^>]*/>|>([^<]*)<)' % re.escape(key), getprefs())
    return (m.group(1) or m.group(2)) if m else None


toggle('M46', 'General', 'Display distance in miles', 'js8cfg_miles')
toggle('M47', 'General', 'Display distance in miles', 'js8cfg_miles', 'Cancel')
toggle('M09', 'General', 'Allow sending standard messages without callsign', 'js8cfg_avoid_forced_identify')
toggle('M24', 'Radio', 'Hold PTT between frames while there are more to be transmitted' if L == 'U' else 'Hold PTT between frames', 'js8cfg_hold_ptt')  # sideways the short label (Rob, 2026-10-21)
setprefs(**OK0); start()
adb('shell', 'settings', 'put', 'system', 'user_rotation', '0')
json.dump(t.res, open(f'ui3.{_run}.json', 'w'))
print('DONE', flush=True)
