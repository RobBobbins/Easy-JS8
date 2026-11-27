"""Drag bars invisible everywhere (Rob, 2026-11-26): no grey bar in the gaps, and the lines still drag.
barsoff.py <serial> <tag>. Shots of PC and FT8Call sideways, Pages Activity and Inbox tabs, upright top and Inbox,
light and PC dark. Checks: no #8A8A8A pixels in a gap; PC Band Activity | Message Window line dragged 120 px makes
Band Activity wider; upright line under the reply row still drags. The app's settings file, rotation and dark mode
are put back. Each part has an estimate and a limit 10% over (parts.py)."""
import sys, time, re
import numpy as np
from parts import part

SER, TAG = sys.argv[1], sys.argv[2]
exec(open('inboxlook.py', encoding='utf8').read().split('\nROT = (')[0]
     .replace("open(f'shots/inboxlook.{TAG}.{t}.png', 'wb')", "open(f'shots/barsoff.{TAG}.{t}.png', 'wb')"))


def grey(im):
    """Pixels of the old bar colour #8A8A8A (138 grey), +-3."""
    return int((np.abs(im - 138) <= 3).all(axis=2).sum())


def look(t):
    im = shot(t)
    n = grey(im)
    mark(f'{t} grey', n < 400, f'{n} pixels of #8A8A8A on screen')


ROT = (adb('shell', 'settings', 'get', 'system', 'accelerometer_rotation').decode().strip(),
       adb('shell', 'settings', 'get', 'system', 'user_rotation').decode().strip())
NIGHT = adb('shell', 'cmd', 'uimode', 'night').decode().strip()
print('device rotation', ROT, '| dark mode:', NIGHT, '| density', DENSITY, flush=True)
adb('shell', 'am', 'force-stop', PKG)
adb('shell', f'run-as {PKG} cp {PREFS} files/barsoff_copy.xml')
adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0')
try:
    adb('shell', 'cmd', 'uimode', 'night', 'no')
    with part('PC', 50, ['pc grey', 'pc drag', 'pcinbox grey'], res, mark):
        set_layout('pc', 1)
        look('pc')
        bb, _ = by_id('js8_band_activity'); mb, _ = by_id('js8_message_window')
        x0 = (bb[2] + mb[0]) // 2; y0 = (bb[1] + bb[3]) // 2
        adb('shell', 'input', 'swipe', str(x0), str(y0), str(x0 + 120), str(y0), '700'); time.sleep(1.5)
        bb2, _ = by_id('js8_band_activity')
        mark('pc drag', (bb2[2] - bb2[0]) - (bb[2] - bb[0]) > 60, f'Band Activity width {bb[2] - bb[0]} to {bb2[2] - bb2[0]}')
        shot('pc_dragged')
        clickid('js8_menubar_inbox'); time.sleep(3)
        look('pcinbox')
    with part('FT8Call', 40, ['ft8call grey'], res, mark):
        set_layout('ft8call', 1)
        look('ft8call')
    with part('Pages', 50, ['pagesact grey', 'pagesinbox grey'], res, mark):
        set_layout('swipe', 1)
        clicktext('Activity'); time.sleep(2)
        look('pagesact')
        clicktext('Inbox'); time.sleep(2)
        look('pagesinbox')
    with part('Upright', 60, ['up grey', 'upinbox grey', 'up drag'], res, mark):
        set_layout('pc', 0)
        look('up')
        up_reach('js8_inbox_reply_row')
        look('upinbox')
        rb, _ = by_id('js8_inbox_reply_row')
        y = rb[3] + int(3 * DENSITY)
        adb('shell', 'input', 'swipe', '300', str(y), '300', str(y + 100), '700'); time.sleep(1.5)
        rb2, _ = by_id('js8_inbox_reply_row')
        mark('up drag', (rb2[3] - rb2[1]) - (rb[3] - rb[1]) > 40, f'reply row height {rb[3] - rb[1]} to {rb2[3] - rb2[1]}')
    with part('PC dark', 50, ['pcdark grey'], res, mark):
        adb('shell', 'cmd', 'uimode', 'night', 'yes')
        set_layout('pc', 1)
        look('pcdark')
finally:
    with part('put back', 25):
        adb('shell', 'cmd', 'uimode', 'night', 'yes' if 'yes' in NIGHT else 'no')
        adb('shell', 'am', 'force-stop', PKG)
        adb('shell', f'run-as {PKG} cp files/barsoff_copy.xml {PREFS}')
        adb('shell', f'run-as {PKG} rm files/barsoff_copy.xml')
        adb('shell', 'input', 'keyevent', 'KEYCODE_HOME')
        adb('shell', 'cmd', 'window', 'user-rotation', 'free')
        adb('shell', 'settings', 'put', 'system', 'user_rotation', ROT[1])
        adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', ROT[0])
        print('put back: rotation', (adb('shell', 'settings', 'get', 'system', 'accelerometer_rotation').decode().strip(),
              adb('shell', 'settings', 'get', 'system', 'user_rotation').decode().strip()),
              '| dark mode:', adb('shell', 'cmd', 'uimode', 'night').decode().strip(),
              '|', 'copy left' if b'barsoff_copy' in adb('shell', f'run-as {PKG} ls files') else 'no copy left', flush=True)
print('RESULT', 'PASS' if res and all(r[1] for r in res) else 'FAIL', flush=True)
