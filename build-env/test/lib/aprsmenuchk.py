"""Long-press Saved Messages... menus get the Saved button's APRS items (Rob, 2026-11-26): the Typing Box and Band Activity
(upright) and Call Activity (sideways PC) show APRS EMAIL, APRS TXT, APRS GRID, APRS MSG under Edit Saved Messages / Save Current
Message; APRS EMAIL, APRS MSG and APRS TXT open their pages; the Saved button keeps its four. APRS GRID is not tapped (it sends).
aprsmenuchk.py <serial> <tag>. The app's settings file and the device's rotation settings are put back.
Each part has its own estimate and a limit 10% over (parts.py)."""
import sys, time, subprocess, re
import uiautomator2
from parts import part

SER, TAG = sys.argv[1], sys.argv[2]
ADB = r'C:\Users\Robert\AppData\Local\Android\Sdk\platform-tools\adb.exe'
PKG = 'com.easyjs8.app'
PREFS = f'shared_prefs/{PKG}_preferences.xml'
TMP = r'C:\Users\Robert\AppData\Local\Temp\claude\08874037d42d1ae2\cb17df91-e634-4729-8951-fae0e40e1f25\scratchpad\p.xml'
APRS = ['APRS EMAIL', 'APRS TXT', 'APRS GRID', 'APRS MSG']
d = uiautomator2.connect(SER)
res = []


def adb(*a):
    return subprocess.run([ADB, '-s', SER, *a], capture_output=True, timeout=30).stdout


def mark(i, ok, note=''):
    print(i, 'PASS' if ok else 'FAIL', note, flush=True); res.append((i, bool(ok)))


def snap(t):
    open(f'shots/aprsmenu.{TAG}.{t}.png', 'wb').write(adb('exec-out', 'screencap', '-p'))


def tap(o):
    b = o.info['bounds']; d.click((b['left'] + b['right']) // 2, (b['top'] + b['bottom']) // 2)


def hold(o):
    b = o.info['bounds']; d.long_click((b['left'] + b['right']) // 2, (b['top'] + b['bottom']) // 2, 1.2)


def start(rot):
    adb('shell', 'am', 'force-stop', PKG)
    adb('shell', 'am', 'start', '-n', f'{PKG}/com.js8call.example.MainActivity'); time.sleep(7)
    adb('shell', 'cmd', 'window', 'user-rotation', 'lock', str(rot)); time.sleep(4)
    if not d(resourceId=f'{PKG}:id/js8_typing_box').wait(timeout=8):
        snap(f'start{rot}')
        print('typing box not found; screen texts:', re.findall(r'text="([^"]+)"', d.dump_hierarchy())[:15], flush=True)


def menu_list():
    return d(className='android.widget.ListView')


def items_order():
    """Texts of the open menu, top to bottom, scrolling it when it is taller than the screen (sideways)."""
    seen = []
    for _ in range(6):
        vis = re.findall(r'text="([^"]+)"[^>]*?bounds="\[(\d+),(\d+)\]', d.dump_hierarchy())
        # a tablet shows the submenu beside the menu: only the Save Current Message column
        col = [int(x) for t, x, _ in vis if t == 'Save Current Message']
        if col:
            vis = [v for v in vis if abs(int(v[1]) - col[0]) < 20]
        new = [t for t, _, y in sorted(vis, key=lambda e: int(e[2])) if t not in seen]
        if not new:
            break
        seen += new
        if menu_list().exists:
            menu_list().scroll.forward(steps=30); time.sleep(.8)
    if menu_list().exists:
        menu_list().scroll.toBeginning(steps=30); time.sleep(.8)
    return seen


def find(text):
    """[text] in the open menu, scrolled into view."""
    if not d(text=text).exists and menu_list().exists:
        menu_list().scroll.to(text=text); time.sleep(.8)
    return d(text=text)


def check_submenu(label, target):
    """Long-press [target], open Saved Messages..., check the APRS items follow Save Current Message, open [label]."""
    b = target.info['bounds']
    print('pressed', label, 'list bounds', b, flush=True)
    hold(target); time.sleep(1.5)
    if not d(text='Saved Messages...').exists:
        snap(f'{label.split()[1].lower()}_hold')
        print('after hold:', re.findall(r'text="([^"]+)"', d.dump_hierarchy())[:20], flush=True)
    tap(find('Saved Messages...')); time.sleep(1.5)
    snap(f'{label.split()[1].lower()}_menu')
    t = items_order()
    ok = 'Save Current Message' in t and t[t.index('Save Current Message') + 1:][:4] == APRS
    note = f'items after Save Current Message: {t[t.index("Save Current Message") + 1:] if "Save Current Message" in t else t}'
    tap(find(label)); time.sleep(2.5)
    snap(f'{label.split()[1].lower()}_page')
    x = d.dump_hierarchy()
    page = f'text="{label}"' in x and 'Insert' in x and 'Saved Messages...' not in x
    if not page:
        print('page texts:', re.findall(r'text="([^"]+)"', x)[:15], flush=True)
    for _ in range(3):  # the first back may only close the keyboard
        if d(resourceId=f'{PKG}:id/js8_typing_box').exists:
            break
        d.press('back'); time.sleep(1.5)
    return ok and page, f'{note}; {label} page opened {page}'


ROT = (adb('shell', 'settings', 'get', 'system', 'accelerometer_rotation').decode().strip(),
       adb('shell', 'settings', 'get', 'system', 'user_rotation').decode().strip())
print('device rotation', ROT, flush=True)
adb('shell', 'am', 'force-stop', PKG)
adb('shell', f'run-as {PKG} cp {PREFS} files/aprsmenu_copy.xml')
try:
    with part('Typing Box long press (upright)', 40, ['typing'], res, mark):
        adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0')
        start(0)
        ok, note = check_submenu('APRS EMAIL', d(resourceId=f'{PKG}:id/js8_typing_box'))
        mark('typing', ok, note)
    with part('Band Activity long press (upright)', 30, ['band activity'], res, mark):
        ok, note = check_submenu('APRS MSG', d(resourceId=f'{PKG}:id/js8_band_activity'))
        mark('band activity', ok, note)
    with part('Saved button keeps its four', 20, ['saved button'], res, mark):
        tap(d(resourceId=f'{PKG}:id/js8_btn_saved')); time.sleep(1.5)
        snap('saved_button')
        ok = all(d(text=a).exists for a in APRS)
        mark('saved button', ok, f'APRS items in the Saved button menu: {ok}')
        d.press('back'); time.sleep(1)
    with part('Call Activity long press (sideways PC)', 50, ['call activity'], res, mark):
        adb('shell', 'am', 'force-stop', PKG)
        x = adb('shell', f'run-as {PKG} cat {PREFS}').decode()
        if 'name="landscape_layout"' in x:
            x = re.sub(r'<string name="landscape_layout">[^<]*</string>', '<string name="landscape_layout">pc</string>', x)
        else:
            x = x.replace('</map>', '    <string name="landscape_layout">pc</string>\n</map>')
        open(TMP, 'w', encoding='utf8', newline='\n').write(x)
        adb('push', TMP, '/data/local/tmp/p.xml')
        adb('shell', f'run-as {PKG} sh -c "cat /data/local/tmp/p.xml > {PREFS}"'); adb('shell', 'rm', '/data/local/tmp/p.xml')
        start(1)
        ok, note = check_submenu('APRS TXT', d(resourceId=f'{PKG}:id/js8_call_activity'))
        mark('call activity', ok, note)
finally:
    with part('put back', 25):
        adb('shell', 'am', 'force-stop', PKG)
        adb('shell', f'run-as {PKG} cp files/aprsmenu_copy.xml {PREFS}')
        adb('shell', f'run-as {PKG} rm files/aprsmenu_copy.xml')
        adb('shell', 'input', 'keyevent', 'KEYCODE_HOME')
        adb('shell', 'cmd', 'window', 'user-rotation', 'free')
        adb('shell', 'settings', 'put', 'system', 'user_rotation', ROT[1])
        adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', ROT[0])
        print('put back: rotation', (adb('shell', 'settings', 'get', 'system', 'accelerometer_rotation').decode().strip(),
              adb('shell', 'settings', 'get', 'system', 'user_rotation').decode().strip()),
              'copy left' if b'aprsmenu_copy' in adb('shell', f'run-as {PKG} ls files') else 'no copy left', flush=True)
print('RESULT', 'PASS' if res and all(r[1] for r in res) else 'FAIL', flush=True)
