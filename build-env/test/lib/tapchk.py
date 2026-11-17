"""W137-W139 (2026-11-06). W137, W138: a typed message with a callsign selected goes out whole (S20: "30W 30FT VERTICAL"
lost "30W 30" to typeahead, the selected call counted as typed); W137 adds text while it is sent. W139: a quick triple tap
on a Band Activity row prints it once, two double taps print it twice (PC). tapchk.py <run> [ID,ID]"""
from onair2 import *
import u
assert_avd()
run = int(sys.argv[1])
only = sys.argv[2].split(',') if len(sys.argv) > 2 else None
res = []
R = chr(65 + (run - 1) % 26)  # A-Z: run 66 gave chr(130), not a callsign letter (2026-11-07)
fresh(tx_submode=0)  # Normal speed, as the S20


def alltxt():
    """The newest ALL*.TXT: the app writes "ALL (1).TXT" when it can't reach the old ALL.TXT (seen 2026-11-06)."""
    name = adb('shell', 'ls -t /sdcard/Download/JS8CallLogs/ | grep "^ALL" | head -1').decode().strip()
    return adb('shell', f'cat "/sdcard/Download/JS8CallLogs/{name}"').decode('utf8', 'replace').splitlines()


def mark(id, ok, note=''):
    print(id, 'PASS' if ok else 'FAIL', note, flush=True)
    res.append((id, ok, note))


def want(id):
    return not only or id in only


def node_text(rid):
    m = re.search(r'<node [^>]*resource-id="[^"]*%s"[^>]*>' % rid, dump())
    t = re.search(r' text="([^"]*)"', m.group(0)) if m else None
    return html.unescape(t.group(1)) if t else ''


def box():
    v = node_text('js8_typing_box')
    return '' if v.startswith('TYPE YOUR OUTGOING') else v


def wait_until(f, wait):
    end = time.time() + wait
    while time.time() < end:
        v = f()
        if v:
            return v
        time.sleep(1)
    return None


def typed_send(id, call, msg, extra):
    """Select [call], type [msg], Send; with [extra], type it at the end once the first part is on the air."""
    inj(f'{call}: @ALLCALL CQ CQ CQ FN31', 1234, 3, -5); time.sleep(3)
    show_call(call)
    picked = tapk(call, 1.5)
    tapk('js8_typing_box', .8)
    text(msg); time.sleep(1)
    adb('shell', 'input', 'keyevent', '111'); time.sleep(.8)  # the keyboard covers Send
    adb('logcat', '-c')
    base = len(alltxt())
    tapk('js8_btn_send', .5)
    on_air = wait_until(lambda: any('Transmitting' in l for l in alltxt()[base:]), 40)
    if extra and on_air:
        tapk('js8_typing_box', .5)
        adb('shell', 'input', 'keyevent', '123')  # cursor to the end
        text(' ' + extra); time.sleep(.5)
        shot(f'{id.lower()}_extra_{run}')
        adb('shell', 'input', 'keyevent', '111')
    done = wait_until(lambda: not box(), 150)
    time.sleep(2)
    lines = alltxt()[base:]
    joined = tx_joined(lines)
    typeahead = re.findall(r'Typeahead: "([^"]*)"', logtext())
    tapk('js8_btn_deselect', 1)
    expect = f'{msg} {extra}'.strip() if extra else msg
    ok = picked and bool(on_air) and bool(done) and any(expect in j for j in joined)
    mark(id, ok, f'{call} selected {picked}; typed {msg!r}' + (f' then {extra!r}' if extra else '') +
         f'; sent {joined[1]!r}; typeahead {typeahead}')


if want('W137'):
    with part('W137', 132, ('W137',), res, mark):
        typed_send('W137', f'W3T{R}A', f'30W 30FT VERTICAL OUT N49 R{run}', 'QTH OWEN')

if want('W138'):
    with part('W138', 110, ('W138',), res, mark):
        typed_send('W138', f'W3T{R}B', f'30W 30FT VERTICAL OUT N50 R{run}', '')


def mw_count(s):
    return node_text('js8_message_window').count(s)


def band_row(s):
    for n in re.findall(r'<node [^>]*>', dump()):
        t = re.search(r' text="([^"]*)"', n)
        if t and s in html.unescape(t.group(1)) and 'js8_message_window' not in n:
            a, b, c, d = map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n).groups())
            return (a + c) // 2, (b + d) // 2
    return None


if want('W139'):
    with part('W139', 40, ('W139',), res, mark):
        key = f'TRIPLE TAP R{run}'
        inj(f'K4U{R}A: @ALLCALL {key}', 2300, 3, -5); time.sleep(3)
        p = wait_until(lambda: band_row(key), 10)
        n0 = mw_count(key)
        gaps = []
        if p:
            dump(); d = u._d
            t = time.time()
            for i in range(3):
                d.click(*p)
                gaps.append(round(time.time() - t, 2)); t = time.time()
            time.sleep(2)
        n1 = mw_count(key)
        if p:
            p = band_row(key) or p
            d.double_click(*p, duration=.1); time.sleep(1)
            d.double_click(*p, duration=.1); time.sleep(2)
        n2 = mw_count(key)
        quick = len(gaps) == 3 and max(gaps[1:]) < .4
        mark('W139', bool(p) and quick and n1 - n0 == 1 and n2 - n1 == 2,
             f'row found {bool(p)}; tap gaps {gaps[1:]} s; Message Window lines with the row: before {n0}, after triple tap {n1}, after two double taps {n2}')

print('RESULT', ' '.join(f'{i}:{"PASS" if ok else "FAIL"}' for i, ok, _ in res), flush=True)
