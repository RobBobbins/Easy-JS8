"""Map screen (menu bar > Map, sideways PC and FT8Call) gets the Settings top bar (Rob, 2026-11-26): the ← arrow the
same size as in Settings in a 48 dp button, the title Map the same height as Settings' title; ← goes back to the main
screen. Checked in PC and FT8Call, light, and PC dark. mapbarchk.py <serial> <tag>. The app's settings file, rotation
and dark mode are put back. Each part has its own estimate and a limit 10% over (parts.py)."""
import sys, time, subprocess, re, io
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


def adb(*a):
    return subprocess.run([ADB, '-s', SER, *a], capture_output=True, timeout=30).stdout


DENSITY = int(re.search(rb'(\d+)\s*$', adb('shell', 'wm', 'density').strip()).group(1)) / 160


def mark(i, ok, note=''):
    print(i, 'PASS' if ok else 'FAIL', note, flush=True); res.append((i, bool(ok)))


def tap(o):
    b = o.info['bounds']; d.click((b['left'] + b['right']) // 2, (b['top'] + b['bottom']) // 2)


def top_bar(t):
    """The Back button's size and its arrow's drawn width, and the title's height, in dp; screenshot saved."""
    png = adb('exec-out', 'screencap', '-p')
    open(f'shots/mapbar.{TAG}.{t}.png', 'wb').write(png)
    im = np.array(Image.open(io.BytesIO(png)).convert('L')).astype(int)
    b = d(description='Back', packageName=PKG).info['bounds']
    box = im[b['top']:b['bottom'], b['left']:b['right']]
    ys, xs = np.nonzero(abs(box - np.median(box)) > 60)
    arrow = (xs.max() - xs.min() + 1) / DENSITY if len(xs) else 0
    titles = re.findall(r'text="(Map|Settings)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', d.dump_hierarchy())
    title = min(titles, key=lambda e: int(e[2])) if titles else None
    th = (int(title[4]) - int(title[2])) / DENSITY if title else 0
    return (b['bottom'] - b['top']) / DENSITY, arrow, th


def set_layout(layout):
    adb('shell', 'am', 'force-stop', PKG)
    x = adb('shell', f'run-as {PKG} cat {PREFS}').decode()
    if 'name="landscape_layout"' in x:
        x = re.sub(r'<string name="landscape_layout">[^<]*</string>', f'<string name="landscape_layout">{layout}</string>', x)
    else:
        x = x.replace('</map>', f'    <string name="landscape_layout">{layout}</string>\n</map>')
    open(TMP, 'w', encoding='utf8', newline='\n').write(x)
    adb('push', TMP, '/data/local/tmp/p.xml')
    adb('shell', f'run-as {PKG} sh -c "cat /data/local/tmp/p.xml > {PREFS}"'); adb('shell', 'rm', '/data/local/tmp/p.xml')
    adb('shell', 'am', 'start', '-n', f'{PKG}/com.js8call.example.MainActivity'); time.sleep(7)
    adb('shell', 'cmd', 'window', 'user-rotation', 'lock', '1'); time.sleep(4)


def compare(layout, t):
    set_layout(layout)
    tap(d(resourceId=f'{PKG}:id/js8_btn_settings')); time.sleep(3)
    sb, sa, st = top_bar(f'{t}_settings')
    d.press('back'); time.sleep(2)
    tap(d(resourceId=f'{PKG}:id/js8_menubar_map')); time.sleep(3)
    mb, ma, mt = top_bar(f'{t}_map')
    same = abs(mb - 48) < 1.5 and abs(ma - sa) < 1 and abs(mt - st) < 2 and ma > 5  # tablet: title box 2 px taller, same text
    mark(f'{t} bar', same, f'Back button {mb:.1f} dp (Settings {sb:.1f}), arrow {ma:.1f} dp (Settings {sa:.1f}), '
                           f'title {mt:.1f} dp tall (Settings {st:.1f})')
    tap(d(description='Back', packageName=PKG)); time.sleep(2)
    back = d(resourceId=f'{PKG}:id/js8_menubar_map').exists
    mark(f'{t} back', back, f'Back went back to the main screen: {back}')


ROT = (adb('shell', 'settings', 'get', 'system', 'accelerometer_rotation').decode().strip(),
       adb('shell', 'settings', 'get', 'system', 'user_rotation').decode().strip())
NIGHT = adb('shell', 'cmd', 'uimode', 'night').decode().strip()
print('device rotation', ROT, '| dark mode:', NIGHT, '| density', DENSITY, flush=True)
adb('shell', 'am', 'force-stop', PKG)
adb('shell', f'run-as {PKG} cp {PREFS} files/mapbar_copy.xml')
adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0')
try:
    adb('shell', 'cmd', 'uimode', 'night', 'no')
    with part('PC light', 40, ['pc bar', 'pc back'], res, mark):
        compare('pc', 'pc')
    with part('FT8Call light', 40, ['ft8call bar', 'ft8call back'], res, mark):
        compare('ft8call', 'ft8call')
    with part('PC dark', 40, ['pcdark bar', 'pcdark back'], res, mark):
        adb('shell', 'cmd', 'uimode', 'night', 'yes')
        compare('pc', 'pcdark')
finally:
    with part('put back', 25):
        adb('shell', 'cmd', 'uimode', 'night', 'yes' if 'yes' in NIGHT else 'no')
        adb('shell', 'am', 'force-stop', PKG)
        adb('shell', f'run-as {PKG} cp files/mapbar_copy.xml {PREFS}')
        adb('shell', f'run-as {PKG} rm files/mapbar_copy.xml')
        adb('shell', 'input', 'keyevent', 'KEYCODE_HOME')
        adb('shell', 'cmd', 'window', 'user-rotation', 'free')
        adb('shell', 'settings', 'put', 'system', 'user_rotation', ROT[1])
        adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', ROT[0])
        print('put back: rotation', (adb('shell', 'settings', 'get', 'system', 'accelerometer_rotation').decode().strip(),
              adb('shell', 'settings', 'get', 'system', 'user_rotation').decode().strip()),
              '| dark mode:', adb('shell', 'cmd', 'uimode', 'night').decode().strip(),
              '|', 'copy left' if b'mapbar_copy' in adb('shell', f'run-as {PKG} ls files') else 'no copy left', flush=True)
print('RESULT', 'PASS' if res and all(r[1] for r in res) else 'FAIL', flush=True)
