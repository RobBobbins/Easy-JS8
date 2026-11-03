"""On-air rows still open. air12.py <run> [ID,ID]"""
import sys as _s
exec(open('ui7.py', encoding='utf8').read().split("row_list = [")[0].replace('"""Upright rows never tested: H, I, J. ui7.py <run> [ID,ID]"""', ''))
t = T(int(_run), only)
c = t.call
M = 'VE3TST'
T.n = (int(time.time()) // 60 % 200) * 26
AUTO = dict(autoreply_enabled=True, js8cfg_autoreply_startup=True, js8cfg_transmit_directed=True, relay_enabled=True)
LONG = 'LONG MESSAGE TO KEEP THE TX BUSY'


def clear_box(wait=50):
    end = time.time() + wait
    while time.time() < end and 'TYPE YOUR' not in boxtext():
        time.sleep(1)
    time.sleep(1)


def tx_lines(base):
    return [l.split('JS8:')[-1].strip() for l in alltxt()[base:] if 'Transmitting' in l]


def sent_wait(sec):
    time.sleep(sec)


def b27():
    """Message from the API: the Typing Box stays empty. PC drops replies while the box holds text."""
    up(**AUTO); b = len(alltxt()); a = c()
    api('TX.SEND_MESSAGE', LONG, wait=.3); time.sleep(7)
    inj(f'{a}: {M} SNR?', 900, 3, -7); sent_wait(55)
    l = tx_lines(b); i = next((n for n, s in enumerate(l) if a in s and 'SNR' in s), -1)
    return i > 0, l[-2:]


def b28():
    """PC raises 1 to 4 minutes to 5."""
    up(**{**AUTO, 'js8cfg_tx_watchdog': '5'})
    end = time.time() + 400
    while time.time() < end and pv('autoreply_enabled') != 'false':
        time.sleep(5)
    time.sleep(2); w = html.unescape(x()); shot(f'b28_{_run}')
    return pv('autoreply_enabled') == 'false' and ('Idle' in w), re.findall(r' text="([^"]+)"', w)[:3]


def d04():
    up(**AUTO); b = len(alltxt()); a = c(); d = c()
    api('TX.SEND_MESSAGE', LONG, wait=.3); time.sleep(7)
    pl = f'{d} HI THERE'
    inj(f'{a}: {M}> {pl} {sum16(pl)}', 900, 3, -7); sent_wait(60)
    l = tx_lines(b)
    return any(d in s and 'HI' in s for s in l), l[-3:]


def e22():
    up(**AUTO); b = len(alltxt()); a = c()
    api('TX.SEND_MESSAGE', 'FIRST ONE IS LONG ENOUGH', wait=.3); api('TX.SEND_MESSAGE', 'SECOND QUEUED', wait=.3); time.sleep(2)
    inj(f'{a}: {M} SNR?', 900, 3, -7); time.sleep(50)
    l = tx_lines(b)
    ia = next((n for n, s in enumerate(l) if a in s), 99); ib = next((n for n, s in enumerate(l) if 'SECOND' in s), 99)
    return ia < ib < 99, l[:6]


def e23():
    """Baseline send counts the parts; the same message with a reply queued between parts must take as many."""
    up(**AUTO); a = c()
    b = len(alltxt()); api('TX.SEND_MESSAGE', LONG, wait=.3); sent_wait(38)
    n0 = sum(1 for s in tx_lines(b) if 'LONG' in s)
    b = len(alltxt()); api('TX.SEND_MESSAGE', LONG, wait=.3); time.sleep(11)
    inj(f'{a}: {M} SNR?', 900, 3, -7); sent_wait(50)
    l = tx_lines(b); n1 = sum(1 for s in l if 'LONG' in s)
    return n1 == n0 and n0 > 0 and any(a in s for s in l), f'{n0} parts, then {n1}, {l[-1:]}'


def e31():
    up(last_frequency='10138500', js8cfg_tx_offset='1500')
    b = len(alltxt())
    api('TX.SEND_MESSAGE', 'NOPE', wait=.3); time.sleep(10)
    w = html.unescape(x()); r = tx_lines(b)
    shot(f'e31_{_run}')
    fresh(last_frequency='10130000', js8cfg_tx_offset='1500')
    return not r, (r[:2], [m for m in re.findall(r' text="([^"]+)"', w) if 'WSPR' in m or 'another' in m][:2])


def e35():
    up(**NOAUTO, tx_submode=1) if False else up(tx_submode=1)
    typed('STRIKE OUT PARTS HERE IS MORE'); tapk('js8_btn_send', .5); time.sleep(14)
    shot(f'e35_{_run}')
    bx = boxtext()
    home(); halt = find(x(), 'js8_btn_halt')
    if halt:
        tapk('js8_btn_halt', 1)
    return 'STRIKE' in bx, f'see shots/e35_{_run}.png'


def e37():
    res_ = []
    for sub, name, nlen in ((0, 'Normal', 15), (1, 'Fast', 10), (2, '40', 6), (8, '60', 4), (4, 'Slow', 30)):
        up(tx_submode=sub); b = len(alltxt())
        api('TX.SEND_MESSAGE', 'HI', wait=.3); time.sleep(nlen + 12)
        res_.append((name, len(tx_lines(b))))
        if nlen == 30:
            time.sleep(20); res_[-1] = (name, len(tx_lines(b)))
    return all(n > 0 for _, n in res_), res_


def a15(on):
    up(js8cfg_show_ft8=on)
    w = 'E:/new-w3bguru-websites/easyft8/ft8af/app/src/main/cpp/ft8af_glue/testdata/ft8'
    f = sorted(os.path.join(w, n) for n in os.listdir(w) if n.endswith('.wav') and 'busy' in n)[0]
    adb('push', f, '/data/local/tmp/ft8.wav'); adb('shell', f'run-as {PKG} cp /data/local/tmp/ft8.wav files/ft8.wav')
    adb('shell', f'am broadcast -n {PKG}/com.js8call.example.debug.DebugFt8Receiver --es file ft8.wav')
    time.sleep(22)
    s_ = 'FT8:' in html.unescape(x())
    shot(f'a15_{on}_{_run}')
    return s_ == on, f'FT8 lines shown {s_}'


def q07():
    up(psk_reporter=True, aprs_is=False, js8cfg_aprs_server='10.0.2.2')
    time.sleep(3)
    adb('forward', 'tcp:2442', 'tcp:2442')
    s_ = socket.create_connection(('127.0.0.1', 2442), 5); s_.settimeout(1)
    a = c(); inj(f'{a}: @ALLCALL CQ CQ CQ FN31', 900, 3, -7)
    b = b''; end = time.time() + 8
    while time.time() < end:
        try:
            b += s_.recv(65536)
        except OSError:
            pass
    s_.close()
    fresh(psk_reporter=False)
    return b'RX.SPOT' in b, b[:60]


def s01():
    """Station opens on General when nothing is saved: clean install data."""
    stop(); xml0 = getprefs()
    adb('shell', 'pm', 'clear', PKG); start(); time.sleep(3)
    for w_ in ('While using the app', 'Allow'):
        tapk(w_, 1)
    w = html.unescape(x()); shot(f's01_{_run}')
    return 'My Callsign:' in w, re.findall(r' text="([^"]+)"', w)[:5]


def s02():
    tapk('Cancel', 2) if 'text="Cancel"' in x() else None
    w = html.unescape(x())
    return 'My Callsign:' in w or 'Enter callsign' in w, re.findall(r' text="([^"]+)"', w)[:4]


row_list = [('B27', b27), ('D04', d04), ('E22', e22), ('E23', e23), ('E31', e31), ('E35', e35), ('E37', e37),
            ('A15', lambda: a15(True)), ('A16', lambda: a15(False)), ('Q07', q07), ('B28', b28)]
for i, f in row_list:
    row(i, f)
adb('shell', 'settings', 'put', 'system', 'user_rotation', '0')
json.dump(out, open(f'air12.{_run}.json', 'w'))
print('DONE', flush=True)
