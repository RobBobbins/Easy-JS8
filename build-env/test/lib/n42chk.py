"""W56-W60: N42, the PC Typing Box filter (TransmitTextEdit normalizeText): upper case, then newline, 0x10, 0x1A
and characters 32-127 only, in the box and at every send. n42chk.py <run> [ID,ID]"""
import base64
from onair2 import *
assert_avd()
run = int(sys.argv[1])
only = sys.argv[2].split(',') if len(sys.argv) > 2 else None
res = []
SAMPLE = 'hello 20°C café\tok \U0001F600 line'


def pc(s):
    """The PC rule, written again here to check the app against."""
    return ''.join(c for c in s.upper() if c == '\n' or c in '\x10\x1a' or 32 <= ord(c) <= 127)


def mark(id, ok, note=''):
    print(id, 'PASS' if ok else 'FAIL', note, flush=True)
    res.append((id, ok, note))


def want(id):
    return not only or id in only


def box():
    m = re.search(r'<node [^>]*resource-id="[^"]*js8_typing_box"[^>]*>', dump())
    t = re.search(r' text="([^"]*)"', m.group(0)) if m else None
    v = html.unescape(t.group(1)) if t else None
    return '' if v and v.startswith('TYPE YOUR OUTGOING') else v  # an empty box reports its hint


def screen_has(s):
    return any(s in html.unescape(t) for t in re.findall(r' text="([^"]*)"', dump()))


def cpu_secs(wait=5):
    """CPU seconds the app used in [wait] seconds (utime + stime from /proc). A stuck frame builder adds about [wait]."""
    pid = adb('shell', 'pidof', PKG).decode().strip().split()[0]
    def now():
        f = adb('shell', 'cat', f'/proc/{pid}/stat').decode().rsplit(')', 1)[1].split()
        return (int(f[11]) + int(f[12])) / 100.0
    a = now(); time.sleep(wait); return round(now() - a, 2)


def clipboard(s):
    """Windows clipboard; the emulator takes it over (clipboard sharing)."""
    b = base64.b64encode(s.encode('utf8')).decode()
    cmd = f"Set-Clipboard -Value ([System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('{b}')))"
    subprocess.run(['powershell', '-NoProfile', '-EncodedCommand', base64.b64encode(cmd.encode('utf-16-le')).decode()], capture_output=True)


def empty_box(wait=60):
    end = time.time() + wait
    while time.time() < end:
        if not box():
            return True
        time.sleep(1)
    return False


def sent(base, pat, wait=40):
    end = time.time() + wait
    while time.time() < end:
        lines = alltxt()[base:]
        hit = [l for l in lines if 'Transmitting' in l and re.search(pat, l)]
        if hit:
            return hit[0].split('JS8:')[-1].strip()
        hit = [j for j in tx_joined(lines) if re.search(pat, j)]  # N30: one frame per line
        if hit:
            return hit[0].strip()
        time.sleep(1)
    return ''


fresh(landscape_layout='pc', tx_submode=1, js8cfg_tx_watchdog='0', autoreply_enabled=False, js8cfg_autoreply_startup=False)
adb('shell', 'cmd', 'window', 'user-rotation', 'lock', '0'); time.sleep(3)
adb('forward', 'tcp:2442', 'tcp:2442')

if want('W56'):
    with part('W56', 15, ('W56',), res, mark):
        api('TX.SET_TEXT', '', wait=1)
        clipboard(SAMPLE); time.sleep(2)
        tapk('js8_typing_box', .8)
        adb('shell', 'input', 'keyevent', '279'); time.sleep(2)  # KEYCODE_PASTE
        got = box()
        mark('W56', got == pc(SAMPLE), f'pasted {SAMPLE!r}, box {got!r}, PC rule {pc(SAMPLE)!r}')

if want('W57'):
    with part('W57', 10, ('W57',), res, mark):
        api('TX.SET_TEXT', '', wait=1)
        api('TX.SET_TEXT', SAMPLE + '\nNEXT', wait=1.5); time.sleep(1)
        got = box()
        mark('W57', got == pc(SAMPLE + '\nNEXT'), f'TX.SET_TEXT {SAMPLE + chr(10) + "NEXT"!r}, box {got!r} (newline kept, as the PC)')

if want('W58'):
    with part('W58', 10, ('W58',), res, mark):
        api('TX.SET_TEXT', '', wait=1)
        tapk('js8_typing_box', .8)
        text('hello lower'); time.sleep(1.5)
        got = box()
        mark('W58', got == 'HELLO LOWER', f'typed key events "hello lower", box {got!r}')

if want('W59'):
    with part('W59', 80, ('W59',), res, mark):
        api('TX.SET_TEXT', '', wait=1); time.sleep(1)
        idle_cpu = cpu_secs()
        api('TX.SET_TEXT', f'N42 TEMP 20°C {run}', wait=1.5); time.sleep(1)
        busy_before = cpu_secs()
        shown = box()
        base = len(alltxt())
        adb('shell', 'input', 'keyevent', '111'); time.sleep(.8)  # the keyboard covers Send (ui3.py typed)
        tapk('js8_btn_send', .5)
        first = sent(base, f'N42 TEMP 20C {run}')
        done = empty_box()
        busy_after = cpu_secs()
        tapk('js8_typing_box', .8)
        text(f'N42 SECOND {run}'); time.sleep(1)
        adb('shell', 'input', 'keyevent', '111'); time.sleep(.8)
        base2 = len(alltxt())
        tapk('js8_btn_send', .5)
        second = sent(base2, f'N42 SECOND {run}')
        empty_box()
        mark('W59', shown == f'N42 TEMP 20C {run}' and bool(first) and bool(second) and max(busy_before, busy_after) < idle_cpu + 2.5,
             f'box {shown!r}; sent {first!r}; box emptied after {done}; app CPU in 5 s: empty box {idle_cpu} s, with the text {busy_before} s, after sending {busy_after} s; next send {second!r}')

if want('W60'):
    with part('W60', 55, ('W60',), res, mark):
        base = len(alltxt())
        api('TX.SEND_MESSAGE', f'N42 API 20°C café {run}', wait=1.5)
        first = sent(base, f'N42 API 20C CAF {run}')
        time.sleep(3); empty_box()
        base2 = len(alltxt())
        api('TX.SEND_MESSAGE', f'N42 API NEXT {run}', wait=1.5)
        second = sent(base2, f'N42 API NEXT {run}')
        mark('W60', bool(first) and bool(second), f'TX.SEND_MESSAGE with ° and é sent {first!r}; next message sent {second!r}')

api('TX.SET_TEXT', '', wait=1)
print('RESULT', ' '.join(f'{i}={"PASS" if ok else "FAIL"}' for i, ok, _ in res), flush=True)
