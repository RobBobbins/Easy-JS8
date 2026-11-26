"""Station > UI as Android rows (Rob, 2026-11-26): each colour / font a row with its code (or font · style · size) under
the name and a K1ABC chip at the right end; the colour box (sample, 24 ready colours, Red / Green / Blue sliders) and
one Font box (Font, Style, Size drop-downs and a sample). uichk.py <run> [serial]. The app's settings file is copied
first and put back at the end. Each row is a part with its own estimate and a limit 10% over (parts.py)."""
import re, subprocess, sys, time
import uiautomator2
from parts import part

sys.stdout.reconfigure(encoding='utf8', errors='replace')
RUN = sys.argv[1]
SER = sys.argv[2] if len(sys.argv) > 2 else 'emulator-5600'
ADB = r'C:\Users\Robert\AppData\Local\Android\Sdk\platform-tools\adb.exe'
PKG = 'com.easyjs8.app'
PREFS = f'shared_prefs/{PKG}_preferences.xml'
COLOURS = ['Table Background', 'Foreground Color', 'Primary Highlight Background', 'Secondary Highlight Background',
           'Directed Messages Background', 'CQ Messages Background', 'Selected Row Background',
           'Textbox Background Color', 'Recipient Background', 'Sender Foreground']
d = uiautomator2.connect(SER)
res = []


def adb(*a, timeout=30):
    return subprocess.run([ADB, '-s', SER, *a], capture_output=True, timeout=timeout).stdout


def mark(i, ok, note=''):
    print(i, 'PASS' if ok else 'FAIL', str(note)[:300], flush=True)
    res.append((i, bool(ok)))


def snap(tag):
    open(f'shots/uichk.{RUN}.{tag}.png', 'wb').write(adb('exec-out', 'screencap', '-p'))


def prefs():
    return adb('shell', f'run-as {PKG} cat {PREFS}').decode('utf8', 'replace')


def pv(k):
    m = re.search(rf'<string name="{re.escape(k)}">([^<]*)</string>', prefs())
    return m.group(1) if m else None


def start():
    adb('shell', 'am', 'force-stop', PKG)
    adb('shell', 'am', 'start', '-n', f'{PKG}/com.js8call.example.MainActivity')
    time.sleep(7)


def rotate(r):
    adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0')
    adb('shell', 'cmd', 'window', 'user-rotation', 'lock', str(r)); time.sleep(2.5)
    d.set_orientation('natural' if r == 0 else 'left'); time.sleep(2.5)


def tap(o):
    b = o.info['bounds']
    d.click((b['left'] + b['right']) // 2, (b['top'] + b['bottom']) // 2)


def page_view():
    sc = d(className='android.widget.ScrollView')
    return sc[sc.count - 1]


def open_ui():
    if not d(resourceId=f'{PKG}:id/js8_btn_settings').exists and d(text='Header').exists:
        tap(d(text='Header')); time.sleep(1.5)  # sideways the main screen can open on another tab
    for _ in range(3):  # the first tap after an install can come before the screen is ready
        d(resourceId=f'{PKG}:id/js8_btn_settings').click()
        if d(description='Station').wait(timeout=5):
            break
    tap(d(description='Station')); time.sleep(2)
    if d(text='Band & Call Activity').exists:  # Station opened on this page
        return
    if not d(text='Notifications').exists and not d(text='Radio').exists:  # opened on another page: ← to the list
        d.press('back'); time.sleep(1.5)
    d(className='android.widget.ScrollView')[0].scroll.to(text='UI'); time.sleep(1)
    tap(d(text='UI')); time.sleep(2)


def to(**k):
    page_view().scroll.to(**k); time.sleep(.8)
    return d(**k)


def shows(t, wait=3):
    return d(text=t).wait(timeout=wait)


def btn(n):
    return d(resourceId=f'android:id/button{n}')


def summary_of(name, instance=0):
    """The line under the row titled [name]."""
    m = re.findall(r'text="' + re.escape(name) + r'"[^>]*>\s*<node [^>]*text="([^"]*)"', d.dump_hierarchy())
    return m[instance] if len(m) > instance else None


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


def w234():
    """Upright: colour rows with their #RRGGBB under the name, Font rows with font and size, K1ABC chips, Received /
    Transmitted labels; no buttons."""
    start(); open_ui(); snap('upright')
    seen = d.dump_hierarchy()
    t = summary_of('Table Background')
    f = summary_of('Font')
    for n in range(4):
        page_view().scroll.forward(); time.sleep(.8); seen += d.dump_hierarchy(); snap(f'upright_{n + 2}')
    missing = [c for c in COLOURS if f'text="{c}"' not in seen]
    labels = 'text="Received"' in seen and 'text="Transmitted"' in seen
    buttons = len(re.findall(r'class="android.widget.Button"', seen))
    chips = seen.count('text="K1ABC"')
    ok = bool(t and re.fullmatch(r'#[0-9A-F]{6}', t)) and bool(f and f.endswith(' pt')) and not missing and labels \
        and buttons == 0 and chips >= 20
    return ok, f'Table Background {t!r} Font {f!r} missing {missing} labels {labels} buttons {buttons} chips {chips}'


def w235():
    """Colour box: title, 24 ready colours, Red / Green / Blue; Cancel keeps the colour as it was."""
    page_view().scroll.toBeginning(); time.sleep(.8)
    before = pv('js8cfg_ui_primary_bg')
    tap(to(text='Primary Highlight Background'))
    title = shows('Primary Highlight Background', 3) and d(text='K1ABC').exists
    sw = d(descriptionMatches='#[0-9A-F]{6}').count
    sl = all(d(description=n).exists for n in ('Red', 'Green', 'Blue'))
    snap('colour')
    tap(d(description='#000000')); time.sleep(.5)
    tap(btn(2)); time.sleep(1.5)
    same = pv('js8cfg_ui_primary_bg') == before
    return title and sw == 24 and sl and same, f'title {title} swatches {sw} sliders {sl} unchanged after Cancel {same} ({before})'


def w236():
    """Tapping #22FF22 sets the numbers to 34 255 34; OK saves it and the row shows #22FF22 with a green chip."""
    tap(to(text='Primary Highlight Background')); shows('K1ABC')
    tap(d(description='#22FF22')); time.sleep(.8); snap('colour_picked')
    x = d.dump_hierarchy()
    nums = re.findall(r'text="(\d{1,3})"', x)
    tap(btn(1)); time.sleep(2)
    saved = pv('js8cfg_ui_primary_bg')
    s = summary_of('Primary Highlight Background')
    snap('colour_saved')
    return nums[-3:] == ['34', '255', '34'] and saved == '#22FF22' and s == '#22FF22', \
        f'numbers {nums[-3:]} saved {saved} row {s!r}'


def w237():
    """Dragging the Red slider to its left end sets Red to 0 (sample follows); Cancel."""
    tap(to(text='Primary Highlight Background')); shows('K1ABC')
    b = d(description='Red').info['bounds']
    y = (b['top'] + b['bottom']) // 2
    adb('shell', 'input', 'swipe', str(b['right'] - 30), str(y), str(b['left'] - 40), str(y), '600'); time.sleep(1)
    nums = re.findall(r'text="(\d{1,3})"', d.dump_hierarchy())
    snap('slider')
    tap(btn(2)); time.sleep(1)
    return nums[-3] == '0', f'numbers {nums[-3:]}'


def w238():
    """Font row: one Font box with Font / Style / Size and a K1ABC sample; Monospace, Bold, 14 pt saves and the row
    reads Monospace · Bold · 14 pt."""
    page_view().scroll.toBeginning(); time.sleep(.8)
    tap(to(text='Font'))
    shows('Selawik', 3)
    x = d.dump_hierarchy()  # the drop-downs' names are box hints, seen only in the dump
    title = all(f'"{t}"' in x for t in ('Style', 'Size')) and 'text="K1ABC"' in x
    snap('font')
    for now, pick in (('Selawik', 'Monospace'), ('Regular', 'Bold'), ('10 pt', '14 pt')):
        for n in range(3):  # the drop-down, by the value it shows; a tap can focus it without opening the list
            tap(d(text=now)); time.sleep(1)
            if d(text=pick).exists or d(className='android.widget.ListView').exists:
                break
            print('   list not open after tap', n + 1, 'on', now, flush=True)
        if not d(text=pick).exists:
            d(scrollable=True).scroll.to(text=pick)
        tap(d(text=pick)); time.sleep(1.5)  # a tap under ~1 s after a pick can leave the next list shut (Material)
    snap('font_picked')
    tap(btn(1)); time.sleep(2)
    s = summary_of('Font')
    saved = (pv('js8cfg_ui_table_font'), pv('js8cfg_ui_table_font_family'), pv('js8cfg_ui_table_font_style'))
    return title and s == 'Monospace · Bold · 14 pt' and saved == ('14', 'Monospace', 'Bold'), \
        f'box {title} row {s!r} saved {saved}'


def side():
    open_ui(); snap('side')
    rows = d(text='Table Background').exists and d(text='K1ABC').exists
    tap(d(text='Table Background')); ok = bool(shows('K1ABC', 3)); snap('side_colour')
    okb = bool(btn(1).wait(timeout=3)); sw = bool(d(description='#FFFFFF').exists)
    print('   side: sample', ok, 'OK button', okb, 'white swatch', sw, flush=True)
    ok = ok and okb and sw
    tap(btn(2)); time.sleep(1)
    return bool(rows) and ok, f'rows {bool(rows)} colour box with OK {ok}'


def w239():
    """Sideways (two panes): rows in the right pane; the colour box fits with OK on screen."""
    rotate(1); start()
    try:
        return side()
    finally:
        rotate(0)


def w240():
    """Dark mode upright: page, colour box and Font box (checked by eye)."""
    adb('shell', 'cmd', 'uimode', 'night', 'yes'); start()
    try:
        open_ui(); snap('dark')
        tap(to(text='Table Background')); a = bool(d(description='#FFFFFF').wait(timeout=3)); snap('dark_colour')
        tap(btn(2)); time.sleep(1)
        tap(to(text='Font')); shows('Selawik', 3); b = '"Style"' in d.dump_hierarchy(); snap('dark_font'); tap(btn(2)); time.sleep(1)
    finally:
        adb('shell', 'cmd', 'uimode', 'night', 'no')
    return a and b, f'colour box {a} font box {b}; dark screenshots checked by eye'


with part('setup', 15):
    rotate(0)
    adb('shell', 'am', 'force-stop', PKG)
    adb('shell', f'run-as {PKG} cp {PREFS} files/uichk_prefs_copy.xml')
row('W234', 40, w234)
row('W235', 20, w235)
row('W236', 20, w236)
row('W237', 15, w237)
row('W238', 30, w238)
row('W239', 50, w239)
row('W240', 35, w240)
with part('restore settings', 15):
    adb('shell', 'am', 'force-stop', PKG)
    adb('shell', f'run-as {PKG} cp files/uichk_prefs_copy.xml {PREFS}')
    adb('shell', f'run-as {PKG} rm files/uichk_prefs_copy.xml')
    print('restored primary_bg', pv('js8cfg_ui_primary_bg'), 'table_font', pv('js8cfg_ui_table_font'), flush=True)
print('RESULT', 'PASS' if res and all(r[1] for r in res) else 'FAIL', flush=True)
