"""Message Inbox in the Android look (Rob, 2026-11-26), all three views: PC menu bar > Inbox (sideways), the Pages Inbox
tab (sideways) and the upright Inbox. inboxlook.py <serial> <tag>. Needs two or more messages in the store.
W259 PC screen top bar as Settings (arrow and title size), arrow goes back.
W260 rows: From, date, "frequency · to CALL"; no column headers, no ID.
W261 Send: the word Send, beside the reply box, on the same row; the hints are shown.
W262 tap a row: highlighted, the message in the message box.
W263 press and hold: Delete / Select More / Select All; Select All highlights every row.
W264 Send puts "FROM MSG text" in the Typing Box (PC screen: and goes back).
W265 the lines still drag: list | boxes, message / reply (sideways); under the reply row (upright).
W266 dark mode: the Inbox in the device's dark colours.
The app's settings file, the Typing Box, rotation and dark mode are put back. Each part has an estimate and a limit
10% over (parts.py)."""
import sys, time, subprocess, re, io, html
import numpy as np
import uiautomator2
from PIL import Image
from parts import part

SER, TAG = sys.argv[1], sys.argv[2]
ADB = r'C:\Users\Robert\AppData\Local\Android\Sdk\platform-tools\adb.exe'
PKG = 'com.easyjs8.app'
PREFS = f'shared_prefs/{PKG}_preferences.xml'
TMP = r'C:\Users\Robert\AppData\Local\Temp\claude\08874037d42d1ae2\cb17df91-e634-4729-8951-fae0e40e1f25\scratchpad\p.xml'
d = uiautomator2.connect(SER)
res = []
FIRST = ['K3FLB']  # the newest message's sender, read from the list
ID = lambda n: f'{PKG}:id/{n}'


def adb(*a):
    return subprocess.run([ADB, '-s', SER, *a], capture_output=True, timeout=30).stdout


DENSITY = int(re.search(rb'(\d+)\s*$', adb('shell', 'wm', 'density').strip()).group(1)) / 160


def mark(i, ok, note=''):
    print(i, 'PASS' if ok else 'FAIL', note, flush=True); res.append((i, bool(ok)))


def shot(t):
    png = adb('exec-out', 'screencap', '-p')
    open(f'shots/inboxlook.{TAG}.{t}.png', 'wb').write(png)
    return np.array(Image.open(io.BytesIO(png)).convert('RGB')).astype(int)


def nodes():
    return [html.unescape(n) for n in re.findall(r'<node [^>]*>', d.dump_hierarchy())]


def bnd(n):
    return [int(v) for v in re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n).groups()]


def by_id(name):
    for n in nodes():
        if f'resource-id="{ID(name)}"' in n:
            return bnd(n), n
    return None, None


def text_of(n):
    m = re.search(r' text="([^"]*)"', n or '')
    return m.group(1) if m else ''


def clickid(name):
    """Tap a view by id from the screen's dump (a held UI object can go stale while the screen changes)."""
    for _ in range(5):
        b, _n = by_id(name)
        if b:
            return tapb(b)
        time.sleep(1)
    raise RuntimeError(f'{name} not on screen')


def clicktext(t):
    for _ in range(5):
        for n in nodes():
            if f' text="{t}"' in n:
                return tapb(bnd(n))
        time.sleep(1)
    raise RuntimeError(f'{t} not on screen')


GRIPS = ('Message Inbox', 'CQ', 'Halt', 'Saved', 'Control', 'Display', 'Timing')


def up_reach(name, down=True, bottom=None):
    """Upright: the page dragged by a button or heading (a drag on the map pans the map) until [name] is on screen,
    then its bottom moved to about y [bottom] (default: 440 px above the screen's bottom)."""
    H = d.window_size()[1]
    bottom = H - 440 if bottom is None else bottom
    for _ in range(10):
        b, _n = by_id(name)
        if b and b[3] < H - 190 and b[1] > 150:
            break
        grips = [bnd(n) for n in nodes() if any(f' text="{g}"' in n for g in GRIPS)]
        grips = [g for g in grips if 200 < (g[1] + g[3]) // 2 < H - 240]
        y = (grips[0][1] + grips[0][3]) // 2 if grips else 1000
        xg = (grips[0][0] + grips[0][2]) // 2 if grips else 60
        adb('shell', 'input', 'swipe', str(xg), str(y), str(xg), str(max(150, y - H // 2) if down else min(H - 240, y + H // 2)), '200')
        time.sleep(1.2)
    b, _n = by_id(name)
    hb, _h = None, None
    for n in nodes():
        if ' text="Message Inbox"' in n:
            hb = bnd(n)
    if b and hb and abs(b[3] - bottom) > 60:
        y = (hb[1] + hb[3]) // 2
        adb('shell', 'input', 'swipe', '200', str(y), '200', str(y - (b[3] - bottom)), '1500'); time.sleep(1.5)


def tapb(b):
    d.click((b[0] + b[2]) // 2, (b[1] + b[3]) // 2)


def set_layout(layout, rot):
    adb('shell', 'am', 'force-stop', PKG)
    x = adb('shell', f'run-as {PKG} cat {PREFS}').decode()
    if 'name="landscape_layout"' in x:
        x = re.sub(r'<string name="landscape_layout">[^<]*</string>', f'<string name="landscape_layout">{layout}</string>', x)
    else:
        x = x.replace('</map>', f'    <string name="landscape_layout">{layout}</string>\n</map>')
    open(TMP, 'w', encoding='utf8', newline='\n').write(x)
    adb('push', TMP, '/data/local/tmp/p.xml')
    adb('shell', f'run-as {PKG} sh -c "cat /data/local/tmp/p.xml > {PREFS}"'); adb('shell', 'rm', '/data/local/tmp/p.xml')
    adb('shell', 'am', 'start', '-n', f'{PKG}/com.js8call.example.MainActivity'); time.sleep(5)
    adb('shell', 'cmd', 'window', 'user-rotation', 'lock', str(rot)); time.sleep(3)
    for _ in range(20):  # the main screen up (the start picture can stay up to 20 s)
        if f'{PKG}:id/js8_btn_settings' in d.dump_hierarchy():
            break
        time.sleep(1)
    time.sleep(1)


def rows():
    """From text nodes inside the list, top to bottom: (from, bounds); and every text in the list."""
    lb, _ = by_id('js8_inbox_table')
    inside = [n for n in nodes() if ' text="' in n and lb and
              lb[0] <= bnd(n)[0] and bnd(n)[2] <= lb[2] and lb[1] <= bnd(n)[1] and bnd(n)[3] <= lb[3]]
    texts = [text_of(n) for n in inside]
    froms = [(text_of(n), bnd(n)) for n in inside if re.fullmatch(r'[A-Z0-9/]{3,}( via .*)?', text_of(n))]
    return lb, froms, texts


def check_rows(t):
    lb, froms, texts = rows()
    if not froms:  # no stored messages on this device: the empty-list hint instead
        hint = 'YOUR STORED MESSAGES WILL BE LISTED HERE.' in texts
        mark(f'{t} rows', hint, f'no messages stored; empty-list hint shown {hint}')
        print(f'{t}: tap, press and hold, Send SKIPPED (no messages stored)', flush=True)
        return lb, froms
    if froms:
        FIRST[0] = froms[0][0]
    info = [s for s in texts if re.search(r'MHz \u00B7 to \S+|Hz \u00B7 to \S+', s)]
    dates = [s for s in texts if re.fullmatch(r'[A-Z][a-z]{2} \d{1,2} \d\d:\d\d', s)]
    headers = [s for s in texts if s in ('ID', 'Date', 'Frequency', 'From', 'To', 'Message', '\u2691')]
    # the last row can be cut by the list's bottom edge: its third line not on screen
    ok = len(froms) >= 2 and froms[0][0] and len(dates) >= len(froms) - 1 and len(info) >= len(froms) - 1 and not headers
    mark(f'{t} rows', ok, f'rows {[f[0] for f in froms]}, {len(dates)} dates {dates[:1]}, {len(info)} third lines {ascii(info[:1])}, headers {headers}')
    return lb, froms


def check_send(t):
    rb, rn = by_id('js8_inbox_reply_text')
    sb, sn = by_id('js8_inbox_reply')
    mb, mn = by_id('js8_inbox_message')
    word = text_of(sn)
    beside = rb and sb and sb[0] >= rb[2] and rb[1] <= (sb[1] + sb[3]) // 2 <= rb[3]
    hints = 'TYPE YOUR REPLY HERE.' in (rn or '') and 'TAP A MESSAGE IN THE LIST TO READ IT HERE.' in (mn or '')
    mark(f'{t} send', word == 'Send' and beside and hints,
         f'button text "{word}", reply box {rb}, button {sb}, beside {beside}, hints shown {hints}')


def px_at(im, x, y):
    return im[y, x]


def check_tap(t, lb, froms):
    if not froms:
        return None
    tapb(froms[0][1]); time.sleep(1.5)
    im = shot(f'{t}_tapped')
    x = lb[0] + int(4 * DENSITY)
    sel = px_at(im, x, (froms[0][1][1] + froms[0][1][3]) // 2)
    other = px_at(im, x, (froms[1][1][1] + froms[1][1][3]) // 2)
    _, mn = by_id('js8_inbox_message')
    msg = text_of(mn)
    ok = np.abs(sel - other).max() > 12 and msg and 'TAP A MESSAGE' not in msg
    mark(f'{t} tap', ok, f'selected row {sel.tolist()} other {other.tolist()}, message box "{msg[:40]}"')
    return other


def check_hold(t, lb, froms, plain):
    if not froms:
        return
    d.long_click((froms[1][1][0] + froms[1][1][2]) // 2, (froms[1][1][1] + froms[1][1][3]) // 2); time.sleep(1.5)
    texts = [text_of(n) for n in nodes()]
    menu = all(w in texts for w in ('Delete', 'Select More', 'Select All'))
    if menu:
        d(text='Select All').click(); time.sleep(1.5)
    im = shot(f'{t}_selectall')
    x = lb[0] + int(4 * DENSITY)
    cols = [px_at(im, x, (b[1] + b[3]) // 2) for _, b in froms]
    allsel = all(np.abs(c - plain).max() > 12 for c in cols)
    mark(f'{t} hold', menu and allsel, f'menu Delete / Select More / Select All {menu}, every row highlighted {allsel}')


def tap_first():
    f = rows()[1]
    if f:
        tapb(f[0][1]); time.sleep(1)


def restore_typing(t, pc=False):
    """The Typing Box back to what it held before the test (only when a reply went into it)."""
    if d(resourceId=ID('js8_typing_box')).exists and typing_text() != t:
        d(resourceId=ID('js8_typing_box')).set_text(t); time.sleep(.5)
    elif pc and not d(resourceId=ID('js8_typing_box')).exists:
        d.press('back'); time.sleep(1.5)  # PC: no reply sent, still on the Inbox screen


def typing_text():
    """The Typing Box's text; an empty box reports its hint as its text, so that is read as empty."""
    _, n = by_id('js8_typing_box')
    t = text_of(n)
    h = re.search(r' hint="([^"]*)"', n or '')
    return '' if h and t == h.group(1) or t.startswith('TYPE YOUR OUTGOING') else t


def drag_sideways(t):
    lb, _ = by_id('js8_inbox_table'); mb, _ = by_id('js8_inbox_message'); rb, _ = by_id('js8_inbox_reply_row')
    x0 = (lb[2] + mb[0]) // 2; y0 = (lb[1] + lb[3]) // 2
    adb('shell', 'input', 'swipe', str(x0), str(y0), str(x0 + 150), str(y0), '700'); time.sleep(1.5)
    lb2, _ = by_id('js8_inbox_table')
    mb, _ = by_id('js8_inbox_message'); rb, _ = by_id('js8_inbox_reply_row')
    xm = (mb[0] + mb[2]) // 2; ym = (mb[3] + rb[1]) // 2
    adb('shell', 'input', 'swipe', str(xm), str(ym), str(xm), str(ym + 100), '700'); time.sleep(1.5)
    mb2, _ = by_id('js8_inbox_message')
    ok = (lb2[2] - lb2[0]) - (lb[2] - lb[0]) > 100 and (mb2[3] - mb2[1]) - (mb[3] - mb[1]) > 50
    mark(f'{t} lines', ok, f'list width {lb[2] - lb[0]} to {lb2[2] - lb2[0]}, message height {mb[3] - mb[1]} to {mb2[3] - mb2[1]}')


def dark_check(t):
    im = shot(t)
    lb, _ = by_id('js8_inbox_table')
    bg = im[lb[1] + int(2 * DENSITY):lb[3] - 2, lb[0] + 2:lb[2] - 2].reshape(-1, 3)
    med = np.median(bg, axis=0)
    mark(f'{t}', med.max() < 60, f'list background {med.tolist()} (dark below 60)')


def top_bar():
    """Back button height and its arrow's drawn width, the title's height, in dp."""
    png = adb('exec-out', 'screencap', '-p')
    im = np.array(Image.open(io.BytesIO(png)).convert('L')).astype(int)
    b = d(description='Back', packageName=PKG).info['bounds']
    box = im[b['top']:b['bottom'], b['left']:b['right']]
    ys, xs = np.nonzero(abs(box - np.median(box)) > 60)
    arrow = (xs.max() - xs.min() + 1) / DENSITY if len(xs) else 0
    titles = re.findall(r'text="(Message Inbox|Settings)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', d.dump_hierarchy())
    title = min(titles, key=lambda e: int(e[2])) if titles else None
    th = (int(title[4]) - int(title[2])) / DENSITY if title else 0
    return (b['bottom'] - b['top']) / DENSITY, arrow, th


def send_and_read(t, word, go_typing):
    if not rows()[1]:
        return ''
    d(resourceId=ID('js8_inbox_reply_text')).set_text(word); time.sleep(1.5)
    if b'mInputShown=true' in adb('shell', 'dumpsys', 'input_method'):  # the keyboard moves the Send button
        d.press('back'); time.sleep(1.5)
    sb, _ = by_id('js8_inbox_reply')
    tapb(sb); time.sleep(2)
    go_typing()
    got = typing_text()
    first = FIRST[0]
    mark(f'{t} reply', got.endswith(f'{first} MSG {word}'), f'Typing Box "{got}" (top row {first})')
    return got


ROT = (adb('shell', 'settings', 'get', 'system', 'accelerometer_rotation').decode().strip(),
       adb('shell', 'settings', 'get', 'system', 'user_rotation').decode().strip())
NIGHT = adb('shell', 'cmd', 'uimode', 'night').decode().strip()
print('device rotation', ROT, '| dark mode:', NIGHT, '| density', DENSITY, flush=True)
adb('shell', 'am', 'force-stop', PKG)
adb('shell', f'run-as {PKG} cp {PREFS} files/inboxlook_copy.xml')
adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0')
typed0 = ''
try:
    adb('shell', 'cmd', 'uimode', 'night', 'no')
    with part('PC top bar', 60, ['pc bar', 'pc back'], res, mark):
        set_layout('pc', 1)
        typed0 = typing_text()
        clickid('js8_btn_settings'); time.sleep(3)
        sb_, sa, st = top_bar()
        d.press('back'); time.sleep(2)
        clickid('js8_menubar_inbox'); time.sleep(3)
        mb_, ma, mt = top_bar()
        shot('pc')
        mark('pc bar', abs(mb_ - 48) < 1.5 and abs(ma - sa) < 1 and abs(mt - st) < 2 and ma > 5,
             f'Back button {mb_:.1f} dp, arrow {ma:.1f} dp (Settings {sa:.1f}), title {mt:.1f} dp (Settings {st:.1f})')
        d(description='Back', packageName=PKG).click(); time.sleep(2)
        mark('pc back', d(resourceId=ID('js8_menubar_inbox')).exists, 'the arrow went back to the main screen')
    with part('PC list', 50, ['pc rows', 'pc send', 'pc tap', 'pc hold'], res, mark):
        clickid('js8_menubar_inbox'); time.sleep(3)
        lb, froms = check_rows('pc')
        check_send('pc')
        plain = check_tap('pc', lb, froms)
        check_hold('pc', lb, froms, plain)
    with part('PC Send', 40, ['pc reply'], res, mark):
        d.press('back'); time.sleep(2)
        clickid('js8_menubar_inbox'); time.sleep(3)
        tap_first()
        send_and_read('pc', 'TEST', lambda: None)
        restore_typing(typed0, pc=True)
    with part('PC lines', 40, ['pc lines'], res, mark):
        clickid('js8_menubar_inbox'); time.sleep(3)
        shot('pc_before_lines')
        drag_sideways('pc'); shot('pc_after_lines')
    with part('PC dark', 55, ['pc dark'], res, mark):
        adb('shell', 'cmd', 'uimode', 'night', 'yes')
        set_layout('pc', 1)
        clickid('js8_menubar_inbox'); time.sleep(3)
        tap_first()
        dark_check('pc dark')
        adb('shell', 'cmd', 'uimode', 'night', 'no')
    with part('Pages', 60, ['pages rows', 'pages send', 'pages tap', 'pages hold'], res, mark):
        set_layout('swipe', 1)
        clicktext('Inbox'); time.sleep(3)
        shot('pages')
        lb, froms = check_rows('pages')
        check_send('pages')
        plain = check_tap('pages', lb, froms)
        check_hold('pages', lb, froms, plain)
    with part('Pages Send and lines', 80, ['pages reply', 'pages lines'], res, mark):
        set_layout('swipe', 1)
        clicktext('Inbox'); time.sleep(3)
        tap_first()
        send_and_read('pages', 'TEST', lambda: (clicktext('Activity'), time.sleep(2)))
        restore_typing(typed0)
        clicktext('Inbox'); time.sleep(2)
        drag_sideways('pages'); shot('pages_after_lines')
    with part('Pages dark', 55, ['pages dark'], res, mark):
        adb('shell', 'cmd', 'uimode', 'night', 'yes')
        set_layout('swipe', 1)
        clicktext('Inbox'); time.sleep(3)
        dark_check('pages dark')
        adb('shell', 'cmd', 'uimode', 'night', 'no')
    with part('Upright', 70, ['up rows', 'up send', 'up tap', 'up hold'], res, mark):
        set_layout('pc', 0)
        up_reach('js8_inbox_reply_row')
        shot('up')
        lb, froms = check_rows('up')
        check_send('up')
        plain = check_tap('up', lb, froms)
        check_hold('up', lb, froms, plain)
    with part('Upright Send and line', 70, ['up reply', 'up lines'], res, mark):
        set_layout('pc', 0)
        up_reach('js8_inbox_reply_row')
        tap_first()
        rb, _ = by_id('js8_inbox_reply_row')
        y = rb[3] + int(3 * DENSITY)
        adb('shell', 'input', 'swipe', '300', str(y), '300', str(y + 80), '700'); time.sleep(1.5)
        rb2, _ = by_id('js8_inbox_reply_row')
        mark('up lines', (rb2[3] - rb2[1]) - (rb[3] - rb[1]) > 50, f'reply row height {rb[3] - rb[1]} to {rb2[3] - rb2[1]}')
        shot('up_after_line')
        send_and_read('up', 'TEST', lambda: up_reach('js8_typing_box', down=False, bottom=d.window_size()[1] // 2))
        restore_typing(typed0)
    with part('Upright dark', 50, ['up dark'], res, mark):
        adb('shell', 'cmd', 'uimode', 'night', 'yes')
        set_layout('pc', 0)
        up_reach('js8_inbox_reply_row')
        dark_check('up dark')
finally:
    with part('put back', 25):
        adb('shell', 'cmd', 'uimode', 'night', 'yes' if 'yes' in NIGHT else 'no')
        adb('shell', 'am', 'force-stop', PKG)
        adb('shell', f'run-as {PKG} cp files/inboxlook_copy.xml {PREFS}')
        adb('shell', f'run-as {PKG} rm files/inboxlook_copy.xml')
        adb('shell', 'input', 'keyevent', 'KEYCODE_HOME')
        adb('shell', 'cmd', 'window', 'user-rotation', 'free')
        adb('shell', 'settings', 'put', 'system', 'user_rotation', ROT[1])
        adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', ROT[0])
        print('put back: rotation', (adb('shell', 'settings', 'get', 'system', 'accelerometer_rotation').decode().strip(),
              adb('shell', 'settings', 'get', 'system', 'user_rotation').decode().strip()),
              '| dark mode:', adb('shell', 'cmd', 'uimode', 'night').decode().strip(),
              '| Typing Box was', repr(typed0),
              '|', 'copy left' if b'inboxlook_copy' in adb('shell', f'run-as {PKG} ls files') else 'no copy left', flush=True)
print('RESULT', 'PASS' if res and all(r[1] for r in res) else 'FAIL', flush=True)
