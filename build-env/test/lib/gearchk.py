"""Settings top bar gear 32 dp (Rob, 2026-11-26; the gear shape is 80% of its box, so about 25.6 dp drawn): measured upright, sideways and dark; the bar keeps its height (56 dp
upright, 48 dp sideways); a tap opens Station. gearchk.py <serial> <tag>. Rotation and dark mode are put back.
Each part has its own estimate and a limit 10% over (parts.py)."""
import sys, time, subprocess, re
import uiautomator2
import numpy as np
from PIL import Image
import io
from parts import part

SER, TAG = sys.argv[1], sys.argv[2]
ADB = r'C:\Users\Robert\AppData\Local\Android\Sdk\platform-tools\adb.exe'
PKG = 'com.easyjs8.app'
d = uiautomator2.connect(SER)
res = []


def adb(*a):
    return subprocess.run([ADB, '-s', SER, *a], capture_output=True, timeout=30).stdout


def mark(i, ok, note=''):
    print(i, 'PASS' if ok else 'FAIL', note, flush=True); res.append((i, ok))


def shot(t):
    png = adb('exec-out', 'screencap', '-p')
    open(f'shots/gearchk.{TAG}.{t}.png', 'wb').write(png)
    return Image.open(io.BytesIO(png)).convert('L')


def tap(o):
    b = o.info['bounds']; d.click((b['left'] + b['right']) // 2, (b['top'] + b['bottom']) // 2)


DENSITY = int(re.search(rb'(\d+)\s*$', adb('shell', 'wm', 'density').strip()).group(1)) / 160


def measure(t):
    """The gear's drawn width and its button's and the bar's height, in dp."""
    g = d(description='Station')
    b = g.info['bounds']
    im = np.array(shot(t)).astype(int)
    box = im[b['top']:b['bottom'], b['left']:b['right']]
    bg = np.median(box)
    ys, xs = np.nonzero(abs(box - bg) > 60)
    drawn = (xs.max() - xs.min() + 1) / DENSITY if len(xs) else 0
    button = (b['bottom'] - b['top']) / DENSITY
    back = d(description='Back').info['bounds']
    return drawn, button, (back['bottom'] - back['top']) / DENSITY


def open_settings():
    adb('shell', 'am', 'force-stop', PKG)
    adb('shell', 'am', 'start', '-n', f'{PKG}/com.js8call.example.MainActivity'); time.sleep(7)


def to_settings():
    if not d(resourceId=f'{PKG}:id/js8_btn_settings').exists and d(text='Header').exists:
        tap(d(text='Header')); time.sleep(1.5)
    for _ in range(3):
        d(resourceId=f'{PKG}:id/js8_btn_settings').click()
        if d(description='Station').wait(timeout=5):
            break


ROT = (adb('shell', 'settings', 'get', 'system', 'accelerometer_rotation').decode().strip(),
       adb('shell', 'settings', 'get', 'system', 'user_rotation').decode().strip())
NIGHT = adb('shell', 'cmd', 'uimode', 'night').decode().strip()
print('device rotation', ROT, '| dark mode:', NIGHT, '| density', DENSITY, flush=True)
try:
    with part('upright', 30, ['upright'], res, mark):
        adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0')
        open_settings(); adb('shell', 'cmd', 'window', 'user-rotation', 'lock', '0'); time.sleep(3)
        to_settings(); time.sleep(1)
        g, btn, bar = measure('upright')
        mark('upright', 24 <= g <= 27 and abs(btn - 48) < 1.5, f'gear drawn {g:.1f} dp, button {btn:.1f} dp, back button {bar:.1f} dp')
    with part('gear opens Station', 15, ['station'], res, mark):
        tap(d(description='Station')); time.sleep(2.5)
        ok = bool(d(text='Station Settings').exists or d(text='General').exists)  # Exists re-checks the screen on every bool()
        shot('station'); mark('station', ok, f'Station page shown {ok}')
        d.press('back'); time.sleep(1.5)
    with part('sideways', 30, ['sideways'], res, mark):
        open_settings(); adb('shell', 'cmd', 'window', 'user-rotation', 'lock', '1'); time.sleep(4)
        to_settings(); time.sleep(1)
        g, btn, bar = measure('sideways')
        mark('sideways', 24 <= g <= 27 and abs(btn - 48) < 1.5, f'gear drawn {g:.1f} dp, button {btn:.1f} dp')
    with part('dark upright', 35, ['dark'], res, mark):
        adb('shell', 'cmd', 'uimode', 'night', 'yes')
        open_settings(); adb('shell', 'cmd', 'window', 'user-rotation', 'lock', '0'); time.sleep(4)
        to_settings(); time.sleep(1)
        g, btn, bar = measure('dark')
        mark('dark', 24 <= g <= 27, f'gear drawn {g:.1f} dp')
finally:
    with part('put back', 20):
        adb('shell', 'cmd', 'uimode', 'night', 'yes' if 'yes' in NIGHT else 'no')
        adb('shell', 'am', 'force-stop', PKG)
        adb('shell', 'input', 'keyevent', 'KEYCODE_HOME')
        adb('shell', 'cmd', 'window', 'user-rotation', 'free')
        adb('shell', 'settings', 'put', 'system', 'user_rotation', ROT[1])
        adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', ROT[0])
        print('put back: rotation', (adb('shell', 'settings', 'get', 'system', 'accelerometer_rotation').decode().strip(),
              adb('shell', 'settings', 'get', 'system', 'user_rotation').decode().strip()),
              '| dark mode:', adb('shell', 'cmd', 'uimode', 'night').decode().strip(), flush=True)
print('RESULT', 'PASS' if res and all(r[1] for r in res) else 'FAIL', flush=True)
