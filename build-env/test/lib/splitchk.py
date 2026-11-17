"""W140-W142 (2026-11-06), sideways PC layout. W140: with the tablet's saved sizes (Typing Box one line) the Typing Box
opens at least two lines tall. W141: dragging the line above the Typing Box moves that line, not the one under the
box (tablet: the top line moved the bottom line). W142: the HB button shows "HB", or only the seconds while the
timer runs. splitchk.py <run> [ID,ID]"""
from onair2 import *
assert_avd()


def rotate(code):
    """As s27chk.py: 'U' upright, anything else sideways."""
    adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0')
    for _ in range(5):
        adb('shell', 'settings', 'put', 'system', 'user_rotation', '0' if code == 'U' else '1')
        time.sleep(2)
        m = re.search(r'cur=(\d+)x(\d+)', adb('shell', 'dumpsys', 'window', 'displays').decode('utf8', 'replace'))
        if (int(m.group(1)) < int(m.group(2))) == (code == 'U'):
            return True
    return False
run = int(sys.argv[1])
only = sys.argv[2].split(',') if len(sys.argv) > 2 else None
res = []


def mark(id, ok, note=''):
    print(id, 'PASS' if ok else 'FAIL', note, flush=True)
    res.append((id, ok, note))


def want(id):
    return not only or id in only


def setfloats(**kv):
    stop(); x = getprefs()
    for k, v in kv.items():
        x = re.sub(r'\s*<[a-z]+ name="%s"[^>]*?/>' % re.escape(k), '', x)
        x = x.replace('</map>', f'    <float name="{k}" value="{v}" />\n</map>')
    b = base64.b64encode(x.encode('utf8')).decode()
    adb('shell', f"echo {b} | base64 -d | run-as {PKG} sh -c 'cat > {PREFS}'")


def floats():
    x = getprefs()
    return {k: float(v) for k, v in re.findall(r'<float name="js8cfg_pc_split_(\w+)" value="([^"]+)"', x)}


def bounds(rid, xml=None):
    m = re.search(r'<node [^>]*resource-id="[^"]*%s"[^>]*>' % rid, xml or dump())
    return tuple(map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', m.group(0)).groups())) if m else None


def open_pc():
    setprefs(**{**BASE, 'landscape_layout': 'pc', 'heartbeat_interval': '30', 'tx_submode': 0})  # Normal: the HB button shows
    setfloats(js8cfg_pc_split_text=460.08963, js8cfg_pc_split_main=498.01495, js8cfg_pc_split_band=375.0,
              js8cfg_pc_split_call=748.7663, js8cfg_pc_split_wf=741.7759)
    start(); rotate('L'); time.sleep(4)


with part('open', 25, (), res, mark):
    open_pc()
x = dump()
mw, tb = bounds('js8_message_window', x), bounds('js8_typing_box', x)
shot(f'split_open_{run}')

if want('W140'):
    with part('W140', 15, ('W140',), res, mark):
        tapk('js8_typing_box', .8)
        text('ONE TWO THREE FOUR FIVE SIX SEVEN EIGHT NINE TEN ELEVEN TWELVE THIRTEEN')  # two lines
        adb('shell', 'input', 'keyevent', '111'); time.sleep(1)
        shot(f'w140_{run}')
        h = tb[3] - tb[1] if tb else 0
        f = floats()
        mark('W140', bool(tb) and h > 0, f'Typing Box {h} px tall at {tb}; Message Window {mw}; saved {f} (look at w140_{run}.png)')

if want('W141'):
    with part('W141', 20, ('W141',), res, mark):
        f0, x0 = floats(), dump()
        mw0, tb0 = bounds('js8_message_window', x0), bounds('js8_typing_box', x0)
        cx, cy = (tb0[0] + tb0[2]) // 2, (mw0[3] + tb0[1]) // 2  # the gap between the Message Window and the Typing Box
        adb('shell', 'input', 'swipe', str(cx), str(cy), str(cx), str(cy - 60), '800'); time.sleep(1.5)
        x1 = dump(); f1 = floats()
        mw1, tb1 = bounds('js8_message_window', x1), bounds('js8_typing_box', x1)
        ok = tb1[1] < tb0[1] and tb1[3] == tb0[3] and mw1[3] < mw0[3]
        # Just inside the Typing Box's top edge, where the strip of the line under the lists also reaches
        cy2 = tb1[1] + 4
        adb('shell', 'input', 'swipe', str(cx), str(cy2), str(cx), str(cy2 + 40), '800'); time.sleep(1.5)
        tb2 = bounds('js8_typing_box'); f2 = floats()
        ok2 = tb2[1] > tb1[1] and tb2[3] == tb1[3]
        shot(f'w141_{run}')
        mark('W141', ok and ok2, f'drag from the gap y {cy} up 60 px: Message Window bottom {mw0[3]} -> {mw1[3]}, Typing Box {tb0[1]}-{tb0[3]} -> {tb1[1]}-{tb1[3]}; '
             f'drag from inside its top edge y {cy2} down 40 px: Typing Box -> {tb2[1]}-{tb2[3]}; saved {f0} -> {f1} -> {f2}')

if want('W142'):
    with part('W142', 20, ('W142',), res, mark):
        def hb():
            m = re.search(r'<node [^>]*resource-id="[^"]*js8_btn_hb"[^>]*>', dump())
            t = re.search(r' text="([^"]*)"', m.group(0)) if m else None
            return t.group(1) if t else None
        off = hb()
        tapk('js8_btn_hb', 2.5)
        on = hb()
        tapk('js8_btn_hb', 2)
        off2 = hb()
        mark('W142', off == 'HB' and bool(on) and (on.isdigit() or on == 'now') and off2 == 'HB',
             f'timer off {off!r}, on {on!r}, off again {off2!r}')

rotate('U')
print('RESULT', ' '.join(f'{i}:{"PASS" if ok else "FAIL"}' for i, ok, _ in res), flush=True)
