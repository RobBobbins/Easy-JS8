"""W20 to W33: the app against Hamlib's own rigctld (4.7.2, dummy rig -m 1) on the PC, TCP 4532. The fake rig (fakes.py) logged
every command; rigctld keeps no log, so each check asks rigctld what it now holds (frequency, mode, PTT, split).
wham2.py <run> [ID,ID]. rigctld must be running: rigctld.exe -m 1 -t 4532. The app reaches it as 10.0.2.2:4532.
W31 to W33 run only when asked for: Rob closes the rigctld window, waits a minute, and starts it again."""
import sys as _s
exec(open('ui9.py', encoding='utf8').read().split('\ndef flog():')[0].replace('"""Station rows, Upright. ui9.py <run> [ID,ID]"""', ''))
import io
import numpy as np
from PIL import Image
HAM = dict(rig_control_enabled=True, rig_type='network', rig_network_kind='rigctl', rigctld_host='10.0.2.2', rigctld_port='4532',
           js8cfg_cat_poll_interval='1', js8cfg_ptt_method='cat', js8cfg_split='none', transmit_mode='usb', js8cfg_hold_ptt=False, tx_submode=2)
NORIG = dict(rig_control_enabled=False, rig_type='none', js8cfg_ptt_method='vox', js8cfg_split='none')
DIAL = 10136000
green = lambda c: c[1] > 150 and c[0] < 120 and c[2] < 120
dark_green = lambda c: c[1] > 100 and c[0] < 60 and c[2] < 60  # Test CAT's #008000
reddish = lambda c: c[0] > 2 * c[1] + 20 and c[0] > 2 * c[2] + 20  # red under the alert box's dark scrim


def rc(cmd):
    """One rigctld command; its reply lines."""
    s = socket.create_connection(('127.0.0.1', 4532), 5); s.settimeout(3)
    s.send((cmd + '\n').encode()); time.sleep(.3)
    r = s.recv(300).decode('ascii', 'replace').strip(); s.close()
    return r.splitlines()


def rcs(cmds):
    """Several rigctld commands in one packet, one reply line each, a few ms apart."""
    s = socket.create_connection(('127.0.0.1', 4532), 5); s.settimeout(3)
    s.send(''.join(c + '\n' for c in cmds).encode()); buf = ''
    while buf.count('\n') < len(cmds):
        buf += s.recv(300).decode('ascii', 'replace')
    s.close()
    return buf.splitlines()


def alive():
    try:
        socket.create_connection(('127.0.0.1', 4532), 2).close()
        return True
    except OSError:
        return False


def dial():
    for r in api('RIG.GET_FREQ', wait=1):
        if r.get('type') == 'RIG.FREQ':
            return r['params'].get('DIAL')


def ham(**k):
    """rigctld back to 10.136 MHz, AM, no split, PTT off; the app started on it."""
    rc(f'F {DIAL}'); rc('M AM 0'); rc('S 0 VFOA'); rc('T 0')
    up(**{**HAM, **k}); time.sleep(6)


def send_watch(text, cmds=('t',), secs=45, read=None):
    """Sends text; asks rigctld each command every half second until the whole message is done: PTT has been on,
    then off for 4 s (with Hold PTT off the app keys once a frame, about 2 s apart). read: one reading as a dict."""
    api('TX.SEND_MESSAGE', text, wait=.3)
    seen, end, off_since = [], time.time() + secs, None
    while time.time() < end:
        r = read() if read else {c: rc(c)[:1] for c in cmds}
        seen.append(r)
        if r['t'] == ['1']:
            off_since = None
        elif any(s['t'] == ['1'] for s in seen):
            off_since = off_since or time.time()
            if time.time() - off_since >= 4:
                break
        time.sleep(.5)
    return seen


def runs(seen, keys):
    """The readings run-length packed, e.g. 't0s0x3 t1s0x8 t0s1x2'."""
    out_, last, n = [], None, 0
    for s in seen:
        v = ''.join(k + (s[k][0] if s[k] else '?') for k in keys)
        if v == last:
            n += 1
        else:
            if last:
                out_.append(f'{last}x{n}')
            last, n = v, 1
    return ' '.join(out_ + ([f'{last}x{n}'] if last else []))


def grab():
    return np.asarray(Image.open(io.BytesIO(adb('exec-out', 'screencap', '-p'))).convert('RGB')).astype(int)


def colour_at(b):
    a = grab()
    return tuple(int(v) for v in a[(b[1] + b[3]) // 2, b[0] + 10])


def cat_bounds():
    reach('js8_btn_cat'); time.sleep(1)
    return bounds('js8_btn_cat')


def text_bounds(label):
    for n in nodes():
        if f'text="{label}"' in n:
            return bounds_of(n)


def w20():
    ham(); d = dial()
    return d == DIAL, f'rigctld {DIAL}, app read {d}'


def w21():
    ham()
    api('RIG.SET_FREQ', params={'DIAL': 14078000, 'OFFSET': 1500}, wait=1); time.sleep(3)
    f = rc('f')[:1]
    return f == ['14078000'], f'app set 14078000, rigctld has {f}'


def mode(want, tm):
    ham(transmit_mode=tm); seen = send_watch('HI MODE'); m = rc('m')[:1]
    keyed = any(s['t'] == ['1'] for s in seen)
    return m == [want] and keyed, f'rigctld mode {m} after a send (was AM), keyed {keyed}'


def w24():
    ham(); seen = send_watch('HI PTT'); t_ = [s['t'][0] for s in seen if s['t']]
    keyed = '1' in t_ and t_[-1] == '0'
    return keyed, f'PTT reads {"".join(t_)}'


def w25():
    """Split Rig, offset 2300: XIT (2300 / 500) * 500 - 1500 = 500, so rigctld transmits on 10.136500 with split on."""
    ham(js8cfg_split='rig', js8cfg_tx_offset='2300'); seen = send_watch('HI SPLIT', ('t', 's', 'i'))
    on_ = [s for s in seen if s['t'] == ['1']]
    ok = bool(on_) and all(s['s'] == ['1'] and s['i'] == ['10136500'] for s in on_)
    return ok, f'{runs(seen, ("t", "s"))}; TX freq while keyed {sorted({s["i"][0] for s in on_ if s["i"]})}'


def w26():
    """Split Fake It, offset 2300: the dial moves to 10.136500 for the send and back to 10.136000 after.
    Each reading is PTT (t), dial (f), PTT again (u) in one packet; keyed = both PTT readings on, because the app
    sends PTT off and then the dial back, which could land between a PTT read and a dial read."""
    ham(js8cfg_split='emulate', js8cfg_tx_offset='2300')
    seen = send_watch('HI FAKE', read=lambda: dict(zip('tfu', ([v] for v in rcs(('t', 'f', 't'))))))
    on_ = [s['f'] for s in seen if s['t'] == ['1'] and s['u'] == ['1']]; after_ = rc('f')[:1]
    ok = bool(on_) and all(f == ['10136500'] for f in on_) and after_ == [str(DIAL)]
    return ok, f'after {after_}; {runs(seen, ("t", "f", "u"))}'


def w27():
    """Poll Interval 1 s: a dial change made in rigctld reaches the app."""
    ham(); rc('F 7078000'); d, end = None, time.time() + 6
    while time.time() < end and d != 7078000:
        d = dial(); time.sleep(.5)
    rc(f'F {DIAL}')
    return d == 7078000, f'rigctld set to 7078000, app dial {d}'


def w28():
    ham(); c = colour_at(cat_bounds()); shot(f'w28_{_run}')
    return green(c), f'CAT colour {c}'


def w29():
    ham(); page('Radio'); scroll_to('Test CAT'); tapk('Test CAT', 1)
    c, end = None, time.time() + 16
    while time.time() < end and not (c and dark_green(c)):
        b = text_bounds('Test CAT'); c = colour_at(b) if b else None; time.sleep(1)
    shot(f'w29_{_run}'); tapk('Cancel', 1)
    return bool(c) and dark_green(c), f'Test CAT colour {c}'


def w30():
    ham(); page('Radio'); scroll_to('Test PTT')
    tapk('Test PTT', 1.5); on_ = rc('t')[:1]; shot(f'w30_{_run}')
    tapk('Test PTT', 1.5); off = rc('t')[:1]; tapk('Cancel', 1)
    return on_ == ['1'] and off == ['0'], f'rigctld PTT after first tap {on_}, after second {off}'


def rec(i, ok, note):
    print(i, 'PASS' if ok else 'FAIL', note, flush=True)
    out.append((i, bool(ok), str(note)[:100]))


def stop_start():
    """W31 to W33 in one go, so the alert box stays up for Retry."""
    ham(); cb = cat_bounds()
    print('WAITING for the rigctld window to close', flush=True)
    gone = not alive() or any(time.sleep(1) or not alive() for _ in range(300))
    box, end = False, time.time() + 45
    while gone and time.time() < end and not box:
        box = 'Rig Control Error' in html.unescape(x()); time.sleep(1)
    c = colour_at(cb); shot(f'w31_{_run}')
    rec('W31', box, f'rigctld closed {gone}, box {box}')
    rec('W32', box and reddish(c), f'CAT colour under the box {c}')
    print('WAITING for rigctld to start again', flush=True)
    back = alive() or any(time.sleep(1) or alive() for _ in range(300))
    time.sleep(2); tapk('RETRY', 3); time.sleep(5)
    f = rc('f')[:1] if back else []; d = dial(); still = 'Rig Control Error' in html.unescape(x())
    cb2 = None if still else cat_bounds(); c2 = colour_at(cb2) if cb2 else None; shot(f'w33_{_run}')
    rec('W33', back and not still and f == [str(d)] and bool(c2) and green(c2), f'rigctld back {back}, rigctld {f}, app {d}, box gone {not still}, CAT {c2}')


row('W20', w20)
row('W21', w21)
row('W22', lambda: mode('USB', 'usb'))
row('W23', lambda: mode('PKTUSB', 'usb_data'))
row('W24', w24)
row('W25', w25)
row('W26', w26)
row('W27', w27)
row('W28', w28)
row('W29', w29)
row('W30', w30)
if only and 'W31' in only:
    stop_start()
up(**NORIG)
adb('shell', 'settings', 'put', 'system', 'user_rotation', '0')
json.dump(out, open(f'wham2.{_run}.json', 'w'))
print('DONE', flush=True)
