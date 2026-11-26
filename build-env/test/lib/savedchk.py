"""Station > Saved Messages as Android rows (Rob, 2026-11-26): Add saved message, one row per message (a tap opens
Edit Saved Message with Delete), a ≡ handle at each row's right end that drags it up or down; the Saved button keeps
that order. savedchk.py <run> [serial]. Each row is a part with its own estimate and a limit 10% over (parts.py)."""
import html, re, subprocess, sys, time
import uiautomator2
from parts import part

sys.stdout.reconfigure(encoding='utf8', errors='replace')
RUN = sys.argv[1]
SER = sys.argv[2] if len(sys.argv) > 2 else 'emulator-5600'
ADB = r'C:\Users\Robert\AppData\Local\Android\Sdk\platform-tools\adb.exe'
PKG = 'com.easyjs8.app'
KEY = 'js8cfg_saved_messages'
d = uiautomator2.connect(SER)
res = []
first = []  # the list before the test, put back by W231


def adb(*a, timeout=30):
    return subprocess.run([ADB, '-s', SER, *a], capture_output=True, timeout=timeout).stdout


def mark(i, ok, note=''):
    print(i, 'PASS' if ok else 'FAIL', str(note)[:300], flush=True)
    res.append((i, bool(ok)))


def snap(tag):
    open(f'shots/savedchk.{RUN}.{tag}.png', 'wb').write(adb('exec-out', 'screencap', '-p'))


def saved():
    x = adb('shell', f'run-as {PKG} cat shared_prefs/{PKG}_preferences.xml').decode('utf8', 'replace')
    m = re.search(rf'<string name="{KEY}">([^<]*)</string>', x)
    return [l for l in html.unescape(m.group(1)).split('\n') if l.strip()] if m else ['TNX 73 GL']


def start():
    adb('shell', 'am', 'force-stop', PKG)
    adb('shell', 'am', 'start', '-n', f'{PKG}/com.js8call.example.MainActivity')
    time.sleep(7)


def rotate(r):
    adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0')
    adb('shell', 'cmd', 'window', 'user-rotation', 'lock', str(r)); time.sleep(2.5)
    d.set_orientation('natural' if r == 0 else 'left'); time.sleep(2.5)


def tap(o):
    """Coordinate tap at the element's centre (uiautomator2's element click missed buttons with the keyboard up)."""
    b = o.info['bounds']
    d.click((b['left'] + b['right']) // 2, (b['top'] + b['bottom']) // 2)


def centre(o):
    b = o.info['bounds']
    return (b['left'] + b['right']) // 2, (b['top'] + b['bottom']) // 2


def page_view():
    sc = d(className='android.widget.ScrollView')
    return sc[sc.count - 1]


def open_saved():
    if not d(resourceId=f'{PKG}:id/js8_btn_settings').exists and d(text='Header').exists:
        tap(d(text='Header')); time.sleep(1.5)  # sideways the main screen can open on another tab
    d(resourceId=f'{PKG}:id/js8_btn_settings').click(); time.sleep(2)
    tap(d(description='Station')); time.sleep(2)
    if d(text='Add saved message').exists:  # Station opened on this page
        return
    if not d(text='Notifications').exists and not d(text='Radio').exists:  # opened on another page: ← to the list
        d.press('back'); time.sleep(1.5)
    d(className='android.widget.ScrollView')[0].scroll.to(text='Saved Messages'); time.sleep(1)
    tap(d(text='Saved Messages')); time.sleep(2)


def to(**k):
    page_view().scroll.to(**k); time.sleep(.8)
    return d(**k)


def shows(t, wait=3):
    return d(text=t).wait(timeout=wait)


def btn(n):
    return d(resourceId=f'android:id/button{n}')


def hide_kb():
    """Back closes the keyboard only while it shows; otherwise back would close the form."""
    if 'mInputShown=true' in adb('shell', 'dumpsys', 'input_method').decode('utf8', 'replace'):
        d.press('back')
    time.sleep(.8)


def add(text):
    tap(to(text='Add saved message')); shows('Add Saved Message')
    d(className='android.widget.EditText').set_text(text); time.sleep(.5); hide_kb()
    tap(btn(1)); time.sleep(2)


def drag(msg, rows):
    """Drag [msg]'s ≡ handle by [rows] row heights (minus = up), as a finger would: down, slow move, up."""
    h = d(description=f'Move {msg}')
    x, y = centre(h)
    rh = d(text=msg).info['bounds']
    step = 168  # a one-line row on S22-Fresh (56 dp at 3x)
    adb('shell', 'input', 'swipe', str(x), str(y), str(x), str(int(y + rows * step * 1.1)), '1500'); time.sleep(2)


def row(i, est, fn):
    with part(i, est, [i], res, mark):
        try:
            r = fn()
            ok, note = r if isinstance(r, tuple) else (r, '')
        except Exception as e:
            ok, note = False, repr(e)[:200]
        mark(i, ok, note)
        if not ok:
            snap(f'fail_{i}')


def w227():
    """Upright: Add saved message at the top, each message a row with a Move handle; no Add / Delete buttons, no
    Message table heading."""
    first[:] = saved()
    start(); open_saved(); snap('upright')
    x = d.dump_hierarchy()
    addrow = 'text="Add saved message"' in x
    rows = [m for m in first if f'text="{html.escape(m, quote=True)}"' not in x]
    handles = [m for m in first if f'content-desc="Move {html.escape(m, quote=True)}"' not in x]
    old = [t for t in ('Add', 'Delete', 'Message') if f'text="{t}"' in x]
    return addrow and not rows and not handles and not old, \
        f'list {first} add row {addrow} missing rows {rows} missing handles {handles} old {old}'


def w228():
    """Add saved message: Add Saved Message form, OK greyed while empty; "za one" adds ZA ONE at the end, saved."""
    tap(to(text='Add saved message'))
    title = shows('Add Saved Message')
    grey = not btn(1).info['enabled']
    d(className='android.widget.EditText').set_text('za one'); time.sleep(.6)
    hide_kb(); snap('add')
    on = btn(1).info['enabled']
    tap(btn(1)); time.sleep(2)
    s = saved()
    shown = bool(page_view().scroll.to(text='ZA ONE'))
    return title and grey and on and s[-1] == 'ZA ONE' and shown, \
        f'title {title} greyed empty {grey} OK on {on} saved {s} row shown {shown}'


def w229():
    """Tapping ZA ONE opens Edit Saved Message filled in, with Delete; "za edit" replaces it in place."""
    tap(to(text='ZA ONE'))
    title = shows('Edit Saved Message')
    filled = d(className='android.widget.EditText').get_text() == 'ZA ONE'
    dele = bool(btn(3).wait(timeout=3)) and btn(3).get_text().lower() == 'delete'
    d(className='android.widget.EditText').set_text('za edit'); time.sleep(.5); hide_kb(); snap('edit')
    tap(btn(1)); time.sleep(2)
    s = saved()
    return title and filled and dele and s[-1] == 'ZA EDIT' and 'ZA ONE' not in s, \
        f'title {title} filled {filled} delete {dele} saved {s}'


def w230():
    """Dragging ZB TWO's handle up one row puts it above ZA EDIT (saved); the Saved button lists that order."""
    add('zb two')
    page_view().scroll.toEnd(); time.sleep(1); snap('before_drag')
    before = saved()
    drag('ZB TWO', -1); snap('after_drag')
    s = saved()
    moved = s.index('ZB TWO') == s.index('ZA EDIT') - 1
    # the Saved button on the main screen
    for _ in range(3):
        if d(resourceId=f'{PKG}:id/js8_btn_saved').exists:
            break
        d.press('back'); time.sleep(1.5)
    tap(d(resourceId=f'{PKG}:id/js8_btn_saved')); time.sleep(1.5); snap('saved_menu')
    a, b = d(text='ZB TWO'), d(text='ZA EDIT')
    menu = a.exists and b.exists and a.info['bounds']['top'] < b.info['bounds']['top']
    d.press('back'); time.sleep(1)
    return moved and menu, f'before {before} after {s} menu order {menu}'


def w231():
    """Dragging down moves it back below; Delete in Edit Saved Message removes ZA EDIT and ZB TWO, the list is as it
    was before the test."""
    open_saved(); page_view().scroll.toEnd(); time.sleep(1)
    drag('ZB TWO', 1)
    s1 = saved()
    down = s1.index('ZB TWO') == s1.index('ZA EDIT') + 1
    for m in ('ZA EDIT', 'ZB TWO'):
        tap(to(text=m)); shows('Edit Saved Message'); btn(3).wait(timeout=3)
        tap(btn(3)); time.sleep(2)
    s = saved()
    return down and s == first, f'after drag down {s1} end {s} first {first}'


def side():
    open_saved(); snap('side')
    ok = d(text='Add saved message').exists and d(descriptionStartsWith='Move ').exists
    tap(d(text='Add saved message')); f = bool(shows('Add Saved Message')); hide_kb(); snap('side_add')
    f = f and bool(btn(1).wait(timeout=3))
    tap(btn(2)); time.sleep(1)
    return bool(ok) and f, f'rows {bool(ok)} form with OK {f}'


def w232():
    """Sideways (two panes): rows and handles in the right pane; Add Saved Message form with OK on screen."""
    rotate(1); start()
    try:
        return side()
    finally:
        rotate(0)


def w233():
    """Dark mode upright: the page and Edit Saved Message (checked by eye)."""
    adb('shell', 'cmd', 'uimode', 'night', 'yes'); start()
    try:
        open_saved(); snap('dark')
        tap(to(text=first[0])); ok = bool(shows('Edit Saved Message')); hide_kb(); snap('dark_edit')
        tap(btn(2)); time.sleep(1)
    finally:
        adb('shell', 'cmd', 'uimode', 'night', 'no')
    return ok, 'dark screenshots taken (checked by eye)'


with part('setup', 15):
    rotate(0)
row('W227', 25, w227)
row('W228', 20, w228)
row('W229', 20, w229)
row('W230', 40, w230)
row('W231', 45, w231)
row('W232', 50, w232)
row('W233', 30, w233)
print('RESULT', 'PASS' if res and all(r[1] for r in res) else 'FAIL', flush=True)
