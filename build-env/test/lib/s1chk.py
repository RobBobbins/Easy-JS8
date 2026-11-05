"""W67-W76: audit B section 1 and N51. Screen off, HEARING? / QUERY CALL from Call Activity, relay to a group, the send
queue (AUTO off to the Typing Box, the Typing Box at send time, timed HB), drift (N3), relay and spot buffers (N4).
s1chk.py <run> [ID,ID]"""
from onair2 import *
assert_avd()
run = int(sys.argv[1])
only = sys.argv[2].split(',') if len(sys.argv) > 2 else None
t = T(run, only)
c = t.call
M = 'VE3TST'
T.n = (int(time.time()) // 60 % 200) * 26
DIRECTED = '/sdcard/Download/JS8CallLogs/DIRECTED.TXT'
SPOTS = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'spot.log')  # fakespot.py writes it here


def want(id):
    return not only or id in only


def fix(id, ok, note=''):
    if want(id):
        t.mark(id, ok, note)


def box():
    m = re.search(r'<node [^>]*resource-id="[^"]*js8_typing_box"[^>]*>', dump())
    v = re.search(r' text="([^"]*)"', m.group(0)) if m else None
    v = html.unescape(v.group(1)) if v else ''
    return '' if v.startswith('TYPE YOUR OUTGOING') else v  # an empty box reports its hint


def type_box(s):
    tapk('js8_typing_box', .8); text(s); time.sleep(.8)
    adb('shell', 'input', 'keyevent', '111'); time.sleep(.8)  # the keyboard covers the buttons


def awake():
    return 'mWakefulness=Awake' in adb('shell', 'dumpsys', 'power').decode()




def quiet(limit=120):
    """Wait until nothing has gone out for 12 s (Fast: a period and a bit), so the next reply isn't queued behind one."""
    end, n, since = time.time() + limit, -1, time.time()
    while time.time() < end:
        m = sum(1 for l in alltxt() if 'Transmitting' in l)
        if m != n:
            n, since = m, time.time()
        elif time.time() - since >= 12:
            return True
        time.sleep(1)
    return False


def directed():
    return adb('shell', 'cat', DIRECTED).decode('utf8', 'replace').splitlines()


def spots():
    try:
        return open(SPOTS, encoding='utf8', errors='replace').read().splitlines()
    except OSError:
        return []


def tap_menu(k, wait=1.5):
    """Tap a row of the open menu; a menu that runs past the screen is scrolled up to reach it (the group
    lines, audit B N5, made the Call Activity menu taller). The swipe stays clear of the bottom edge: Home."""
    # an open menu's dump holds only the menu window, so the screen height comes from wm size
    h = int(re.search(r'x(\d+)', adb('shell', 'wm', 'size').decode()).group(1))
    for _ in range(4):
        x = dump()
        if find(x, k):
            return tapk(k, wait)
        m = re.search(r'class="android.widget.ListView"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x)
        if not m:
            break
        l, top, r, bot = map(int, m.groups())
        cx, y1, y2 = (l + r) // 2, min(bot, h - 250) - 20, top + 20
        adb('shell', 'input', 'swipe', str(cx), str(y1), str(cx), str(y2), '800'); time.sleep(1)
    print('MISSING', k, flush=True)
    return False


# Call Activity starts with only the ⚑ inbox senders, not 180 old calls; the lists at the layout's height
fresh(tx_submode=1, js8cfg_reset_activity=True, js8cfg_up_split_lists=None)
adb('shell', 'cmd', 'window', 'user-rotation', 'lock', '0'); time.sleep(3)

if want('W67'):  # N51: the screen off
    with part('W67', 60, ('W67',), t.res, t.mark):
        a, h = c(), c(1)
        adb('shell', 'input', 'keyevent', '26'); time.sleep(3)
        off = not awake()
        base = len(alltxt())
        inj(f'{h}: @ALLCALL CQ CQ CQ FN31', 1100); time.sleep(2)
        inj(f'{a}: {M} SNR?', 1300, 3, -8)
        got = t.tx(base, f'{a} SNR -08', 40)
        adb('shell', 'input', 'keyevent', '26'); time.sleep(2); adb('shell', 'input', 'keyevent', '82'); time.sleep(3)
        listed = bool(show_call(h))
        fix('W67', off and bool(got) and listed, f'screen off {off}; reply {got[:1]}; {h} in Call Activity after {listed}')

if want('W68') or want('W69'):  # HEARING? and QUERY CALL read Call Activity; Remove This Station takes a call out
    with part('W68,W69', 220, ('W68', 'W69'), t.res, t.mark):
        x1, x2, a, b = c(), c(1), c(2), c(3)
        quiet()
        inj(f'{x1}: @ALLCALL CQ CQ CQ FN31', 1100); time.sleep(2); inj(f'{x2}: @ALLCALL CQ CQ CQ FN32', 1200); time.sleep(3)
        base = len(alltxt()); inj(f'{a}: {M} HEARING?', 1300)
        h1 = t.tx(base, f'{a} HEARING', 30)
        h1 = tx_joined(alltxt()[base:]) if h1 and quiet() else []  # N30: the call list is in the later frames
        p = show_call(x1)
        if p:
            adb('shell', 'input', 'swipe', str(p[0]), str(p[1]), str(p[0]), str(p[1]), '900'); time.sleep(1.5)
        removed = bool(p) and tap_menu('Remove This Station', 1.5) and not find(dump(), x1)
        quiet()
        base = len(alltxt()); inj(f'{b}: {M} HEARING?', 1400)
        h2 = t.tx(base, f'{b} HEARING', 30)
        h2 = tx_joined(alltxt()[base:]) if h2 and quiet() else []
        has = lambda h, x: any(x in j for j in h)
        fix('W68', has(h1, x1) and has(h1, x2) and removed and bool(h2) and not has(h2, x1) and has(h2, x2),
            f'before {h1[1:2]}; {x1} removed {removed}; after {h2[1:2]}')
        if want('W69'):
            quiet()
            base = len(alltxt()); inj(f'{a}: {M} QUERY CALL {x1} {sum16(x1)}', 1500)
            q1 = t.tx(base, f'{a} YES', 40)
            quiet()
            base = len(alltxt()); inj(f'{b}: {M} QUERY CALL {x2} {sum16(x2)}', 1600)
            q2 = t.tx(base, f'{b} YES', 30)
            fix('W69', removed and not q1 and bool(q2), f'{x1} removed {removed}: {q1[:1]}; kept {x2}: {q2[:1]}')

if want('W70'):  # a relay to my group is ACKed, not forwarded
    with part('W70', 40, ('W70',), t.res, t.mark):
        quiet()
        a, dest = c(), c(1); pl = f'GROUP RELAY {run}'
        base = len(alltxt()); inj(f'{a}: @TESTG> {dest} {pl} {sum16(dest + " " + pl)}', 1300)
        ack = t.tx(base, f'{a} ACK', 30)
        fwd = [j for j in tx_joined(alltxt()[base:]) if re.search(f'{dest} ?>', j)]  # N30: frames joined; app writes "{dest} >"
        fix('W70', bool(ack) and not fwd, f'ACK {ack[:1]}; forwarded {fwd[:1]}')

if want('W71'):  # N1: AUTO off puts the reply in the Typing Box; Send sends it; no HB ACK without AUTO
    with part('W71', 75, ('W71',), t.res, t.mark):
        fresh(tx_submode=1, autoreply_enabled=False, js8cfg_autoreply_startup=False)
        a, hb = c(), c(1)
        base = len(alltxt()); inj(f'{a}: {M} SNR?', 1300, 3, -8)
        sent_early = t.tx(base, f'{a} SNR', 20)
        inbox_text = box()
        shot(f'w71_{run}')
        base = len(alltxt()); tapk('js8_btn_send', .5)
        sent = t.tx(base, f'{a} SNR -08', 30)
        time.sleep(8)
        base = len(alltxt()); inj(f'{hb}: @HB HEARTBEAT FN31', 1500); time.sleep(10)
        hb_box, hb_sent = box(), t.tx(base, f'{hb}', 10)
        fix('W71', not sent_early and inbox_text == f'{a} SNR -08' and bool(sent) and not hb_box and not hb_sent,
            f'sent before Send {sent_early[:1]}; Typing Box {inbox_text!r}; after Send {sent[:1]}; HB from {hb}: box {hb_box!r}, sent {hb_sent[:1]}')
        api('TX.SET_TEXT', '', wait=1)

if want('W72'):  # N2: a queued reply waits while the Typing Box has text
    with part('W72', 50, ('W72',), t.res, t.mark):
        fresh(tx_submode=1)
        a = c()
        tapk('js8_btn_tx', 1)  # TX off: the reply stays queued
        tx_off = 'js8cfg_tx_enabled" value="false' in getprefs()
        base = len(alltxt()); inj(f'{a}: {M} SNR?', 1300, 3, -8); time.sleep(3)
        api('TX.SET_TEXT', f'N2WAIT{run}', wait=1)  # text in the box without the keyboard, which pushes TX off the screen
        tapk('js8_btn_tx', 1)  # TX on
        held = t.tx(base, f'{a} SNR', 20)
        api('TX.SET_TEXT', '', wait=1)
        after = t.tx(base, f'{a} SNR -08', 30)
        fix('W72', tx_off and not held and bool(after), f'TX off {tx_off}; with text in the box {held[:1]}; box emptied {after[:1]}')

if want('W73'):  # timed HB waits for an empty Typing Box
    with part('W73', 145, ('W73',), t.res, t.mark):
        fresh(tx_submode=1, autoreply_enabled=False, js8cfg_autoreply_startup=False, heartbeat_interval='1')
        tapk('js8_btn_hb', .5)
        type_box(f'HBWAIT{run}')
        base = len(alltxt())
        held = t.tx(base, 'HEARTBEAT', 100)
        api('TX.SET_TEXT', '', wait=1)
        after = t.tx(base, 'HEARTBEAT', 30)
        tapk('js8_btn_halt', 1)
        fix('W73', not held and bool(after), f'100 s with text in the box {held[:1]}; box emptied {after[:1]}')

if want('W74'):  # N3: the message buffer follows a drifting signal
    with part('W74', 20, ('W74',), t.res, t.mark):
        fresh(tx_submode=1)
        a = c(); d0 = len(directed())
        p1, p2, p3 = 'N3 DRIFT', ' ONE TWO', ' THREE'
        base = len(alltxt())
        inj(f'{a}: {M} MSG {p1}', 1500, 1); inj(p2, 1508, 4); inj(f'{p3} {sum16(p1 + p2 + p3)}', 1516, 6)
        ack = t.tx(base, f'{a} ACK', 30)
        dl = [l for l in directed()[d0:] if a in l]
        fix('W74', bool(ack) and len(dl) == 1 and 'N3 DRIFT ONE TWO THREE' in dl[0], f'ACK {ack[:1]}; DIRECTED.TXT {dl}')

if want('W75'):  # N4: relay buffers follow drift, and close 60 s after their last frame
    with part('W75', 180, ('W75',), t.res, t.mark):
        time.sleep(12)
        a, dest, b, dest2 = c(), c(1), c(2), c(3)
        base = len(alltxt())
        body = f'{dest} HELLO BOB'
        inj(f'{a}: {M}>', 1500, 1); inj(f' {dest}', 1508, 4); inj(f' HELLO BOB {sum16(body)}', 1516, 6)
        fwd1 = t.tx(base, f'{dest} ?>', 30)  # N55: app frame "{dest} >"
        time.sleep(12)
        body2 = f'{dest2} OPEN END'
        base = len(alltxt()); t0 = time.time()
        inj(f'{b}: {M}>', 1800, 1); inj(f' {body2} {sum16(body2)}', 1800, 4)  # no last frame
        early = t.tx(base, f'{dest2} ?>', 50)
        fwd2 = t.tx(base, f'{dest2} ?>', 45)
        fix('W75', bool(fwd1) and not early and bool(fwd2),
            f'drifting relay {fwd1[:1]}; no last frame: before 50 s {early[:1]}, then {fwd2[:1]} at {round(time.time() - t0)} s')

if want('W76'):  # N4: group spot buffers follow drift and close at 60 s
    with part('W76', 185, ('W76',), t.res, t.mark):
        fresh(tx_submode=1, autoreply_enabled=False, js8cfg_autoreply_startup=False, psk_reporter=True, debug_spot_host='10.0.2.2')
        s1, s3 = c(), c(1)
        b = len(spots())
        q1, q2, q3 = 'N4 SPOT', f' DRIFT{run}', ' END'
        inj(f'{s1}: @JS8NET MSG {q1}', 1300, 1); inj(q2, 1308, 4); inj(f'{q3} {sum16(q1 + q2 + q3)}', 1316, 6)
        q4, q5 = 'N4 SHUT', f' CLOSED{run}'
        inj(f'{s3}: @JS8NET MSG {q4}', 2100, 1); inj(f'{q5} {sum16(q4 + q5)}', 2100, 4)  # no last frame: sent at 60 s
        time.sleep(140)
        s = spots()[b:]; j = ' '.join(s)
        fix('W76', s1 in j and f'DRIFT{run}' in j and s3 in j and f'CLOSED{run}' in j,
            f'{len(s)} lines; drifting {s1} {s1 in j and f"DRIFT{run}" in j}; no last frame {s3} {s3 in j and f"CLOSED{run}" in j}')
        fresh(tx_submode=1, psk_reporter=False)

json.dump(t.res, open(f's1chk.{run}.json', 'w'))
print('RESULT', ' '.join(f'{i}={"PASS" if ok else "FAIL"}' for i, ok, _ in t.res), flush=True)
