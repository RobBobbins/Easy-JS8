"""W61-W66: N45-N48. N45-N47, the frames a send builds (the engine's build_message_frames log lines); N48, DIRECTED.TXT
and RX.DIRECTED for free text to a call and for commands with frames after them. n48chk.py <run> [ID,ID]"""
import threading
from onair2 import *
assert_avd()
run = int(sys.argv[1])
only = sys.argv[2].split(',') if len(sys.argv) > 2 else None
res = []
DIRECTED = '/sdcard/Download/JS8CallLogs/DIRECTED.TXT'
R = chr(64 + run)  # A, B: a new set of calls each run


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


def frames_log():
    """The engine's build_message_frames lines since the last logcat -c."""
    return [l for l in adb('logcat', '-d', '-v', 'raw', '-s', 'varicode').decode('utf8', 'replace').splitlines()
            if l.startswith('build_message_frames:')]


def built(line):
    """For each build of [line]: dirTo, and the frame kind picked (BCN, DIR, CMP, DAT)."""
    out, logs = [], frames_log()
    for i, l in enumerate(logs):
        if l.startswith(f"build_message_frames: line='{line}' "):
            to = re.search(r"dirTo='([^']*)'", l).group(1)
            kind = next((re.search(r'using (\w+) frame', x).group(1) for x in logs[i + 1:i + 4] if 'using ' in x), '')
            out.append((to, kind))
    return out


def directed():
    return adb('shell', 'cat', DIRECTED).decode('utf8', 'replace').splitlines()


class Events:
    """RX.DIRECTED over the TCP API, from a connection held open while the decodes go in."""
    def __init__(s):
        adb('forward', 'tcp:2442', 'tcp:2442')
        s.sock = socket.create_connection(('127.0.0.1', 2442), 5); s.sock.settimeout(.5)
        s.buf, s.stop = b'', False
        threading.Thread(target=s.rx, daemon=True).start()

    def rx(s):
        while not s.stop:
            try:
                d = s.sock.recv(65536)
                if not d:
                    break
                s.buf += d
            except OSError:
                pass

    def directed(s, frm):
        out = []
        for l in s.buf.decode('utf8', 'replace').splitlines():
            try:
                j = json.loads(l)
            except ValueError:
                continue
            if j.get('type') == 'RX.DIRECTED' and j.get('params', {}).get('FROM') == frm:
                out.append(j)
        return out

    def close(s):
        s.stop = True; s.sock.close()


fresh(landscape_layout='pc', tx_submode=1, js8cfg_tx_watchdog='0', autoreply_enabled=False, js8cfg_autoreply_startup=False)
adb('shell', 'cmd', 'window', 'user-rotation', 'lock', '0'); time.sleep(3)

if want('W61'):
    with part('W61', 60, ('W61',), res, mark):
        adb('logcat', '-c')
        base = len(alltxt())
        api('TX.SEND_MESSAGE', f'K1ABC: N45 {run}', wait=1.5)
        first = sent(base, f'K1ABC: N45 {run}')
        time.sleep(3); empty_box()
        b1 = built(f'K1ABC: N45 {run}')
        base2 = len(alltxt())
        api('TX.SEND_MESSAGE', f'K1ABC N45B {run}', wait=1.5)
        second = sent(base2, f'K1ABC N45B {run}')
        time.sleep(3); empty_box()
        b2 = built(f'K1ABC N45B {run}')
        mark('W61', bool(first) and bool(b1) and all(b == ('', 'DAT') for b in b1) and bool(second) and bool(b2) and all(b == ('K1ABC', 'DIR') for b in b2),
             f'"K1ABC: N45 {run}" sent {first!r}, built {b1}; "K1ABC N45B {run}" sent {second!r}, built {b2}')

if want('W62'):
    with part('W62', 30, ('W62',), res, mark):
        adb('logcat', '-c')
        base = len(alltxt())
        msg = f'`K1ABC/P SNR? N46 {run}'
        api('TX.SEND_MESSAGE', msg, wait=1.5)
        first = sent(base, f'N46 {run}')
        time.sleep(3); empty_box()
        b1, rest = built(msg), built(f' N46 {run}')
        mark('W62', bool(first) and bool(b1) and all(k == 'CMP' for _, k in b1) and bool(rest) and all(k == 'DAT' for _, k in rest),
             f'{msg!r} sent {first!r}; whole line built {b1}; the rest " N46 {run}" built {rest}')

if want('W63'):
    with part('W63', 75, ('W63',), res, mark):
        call = f'K4S{R}A'
        inj(f'{call}: @ALLCALL CQ CQ CQ FN31', 1234, 3, -5); time.sleep(3)
        show_call(call)  # new calls go at the bottom of the Callsigns list
        picked = tapk(call, 1.5)
        adb('logcat', '-c')
        tapk('js8_typing_box', .8)
        text(f'FN42 N47 {run}'); time.sleep(1)
        adb('shell', 'input', 'keyevent', '111'); time.sleep(.8)  # the keyboard covers Send
        shot(f'w63_{run}')
        base = len(alltxt())
        tapk('js8_btn_send', .5)
        first = sent(base, f'N47 {run}')
        empty_box()
        b1 = built(f'{call} FN42 N47 {run}')
        rest = built(f'FN42 N47 {run}')  # the text after the call; before the fix it went out directed to FN42
        tapk('js8_btn_deselect', 1)
        mark('W63', picked and bool(first) and bool(b1) and all(b == (call, 'DIR') for b in b1) and bool(rest) and all(k == 'DAT' for _, k in rest),
             f'{call} selected {picked}; typed "FN42 N47 {run}"; sent {first!r}; built {b1}; the text after the call built {rest}')

ev = Events() if any(want(i) for i in ('W64', 'W65', 'W66')) else None

if want('W64'):
    with part('W64', 10, ('W64',), res, mark):
        a = f'K4F{R}A'
        d0 = len(directed())
        inj(f'{a}: K1ABC', 1500, 1); time.sleep(2)
        early = [l for l in directed()[d0:] if a in l]
        inj(f'N48 FREE TEXT {run}', 1500, 6); time.sleep(4)
        got = [l for l in directed()[d0:] if a in l]
        want_line = f'{a}: K1ABC  N48 FREE TEXT {run} ♢ '
        e = ev.directed(a)
        p = e[-1]['params'] if e else {}
        mark('W64', not early and len(got) == 1 and got[0].endswith(want_line) and len(e) == 1 and p.get('CMD') == ' '
             and p.get('TEXT') == f'K1ABC  N48 FREE TEXT {run} ♢ ',
             f'DIRECTED.TXT after the first frame {early}, after the last {got}; RX.DIRECTED {[(x["params"].get("CMD"), x["params"].get("TEXT")) for x in e]}')

if want('W65'):
    with part('W65', 10, ('W65',), res, mark):
        a, b = f'K4A{R}A', f'K4N{R}A'
        d0 = len(directed())
        inj(f'{a}: K1ABC ACK', 1600, 1); inj(f'{b}: K1ABC SNR -05', 1700, 1); time.sleep(2)
        early = [l for l in directed()[d0:] if a in l or b in l]
        inj(f'N48 ACK TEXT {run}', 1600, 6); inj(f'N48 SNR TEXT {run}', 1700, 6); time.sleep(4)
        ga = [l for l in directed()[d0:] if a in l]; gb = [l for l in directed()[d0:] if b in l]
        ea, eb = ev.directed(a), ev.directed(b)
        ok_a = len(ga) == 1 and ga[0].endswith(f'{a}: K1ABC ACK N48 ACK TEXT {run} ♢ ') and len(ea) == 1 and ea[0]['params'].get('CMD') == ' ACK'
        ok_b = len(gb) == 1 and gb[0].endswith(f'{b}: K1ABC SNR -05 N48 SNR TEXT {run} ♢ ') and len(eb) == 1 and eb[0]['params'].get('CMD') == ' SNR'
        mark('W65', not early and ok_a and ok_b,
             f'after the first frames {early}; ACK {ga} {[x["params"].get("TEXT") for x in ea]}; SNR {gb} {[x["params"].get("TEXT") for x in eb]}')

if want('W66'):
    with part('W66', 10, ('W66',), res, mark):
        a, c, d = f'K4O{R}A', f'K4Q{R}A', f'K4D{R}A'
        d0 = len(directed())
        inj(f'{a}: K1ABC ACK', 1800, 3)                   # one frame: at once, as before
        inj(f'{c}: @ALLCALL CQ CQ CQ FN31', 1900, 1)      # CQ with frames after it: at once, as the PC
        inj(f'{d}: K1ABC N48 DATA {run}', 2000, 7)        # a data frame: not free text to a call
        time.sleep(3)
        g = directed()[d0:]
        ga, gc, gd = [l for l in g if a in l], [l for l in g if c in l], [l for l in g if d in l]
        mark('W66', len(ga) == 1 and ga[0].endswith(f'{a}: K1ABC ACK ♢ ') and len(gc) == 1 and 'CQ' in gc[0] and not gd,
             f'one-frame ACK {ga}; CQ first frame {gc}; data frame {gd}')

if ev:
    ev.close()
api('TX.SET_TEXT', '', wait=1)
print('RESULT', ' '.join(f'{i}={"PASS" if ok else "FAIL"}' for i, ok, _ in res), flush=True)
