"""Sideways PC and FT8Call: the grey bar under the lists starts 2 dp below them with frame colour showing between, as
each side of the Band Activity bar; the two upright bars come down to meet it flush (Rob, 2026-11-26).
barchk.py <serial> <tag>. Measured on screen pixels down the Band Activity column and down each upright bar; zoomed
shots saved. The app's settings file and the device's rotation settings are put back.
Each part has its own estimate and a limit 10% over (parts.py)."""
import sys, time, subprocess, re, io
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


def mark(i, ok, note=''):
    print(i, 'PASS' if ok else 'FAIL', note, flush=True); res.append((i, bool(ok)))


def bounds(rid):
    b = d(resourceId=f'{PKG}:id/{rid}').info['bounds']
    return b['left'], b['top'], b['right'], b['bottom']


def near(a, b, tol=8):
    return all(abs(x - y) <= tol for x, y in zip(a, b))


def runs(px):
    """[(colour, length)] down a list of pixels, blends of 1 px kept as their own runs."""
    out = []
    for p in px:
        if out and near(out[-1][0], p, 3):
            out[-1][1] += 1
        else:
            out.append([p, 1])
    return out


def check(layout):
    x = adb('shell', f'run-as {PKG} cat {PREFS}').decode()
    if 'name="landscape_layout"' in x:
        x = re.sub(r'<string name="landscape_layout">[^<]*</string>', f'<string name="landscape_layout">{layout}</string>', x)
    else:
        x = x.replace('</map>', f'    <string name="landscape_layout">{layout}</string>\n</map>')
    adb('shell', 'am', 'force-stop', PKG)
    open(TMP, 'w', encoding='utf8', newline='\n').write(x)
    adb('push', TMP, '/data/local/tmp/p.xml')
    adb('shell', f'run-as {PKG} sh -c "cat /data/local/tmp/p.xml > {PREFS}"'); adb('shell', 'rm', '/data/local/tmp/p.xml')
    adb('shell', 'am', 'start', '-n', f'{PKG}/com.js8call.example.MainActivity'); time.sleep(7)
    adb('shell', 'cmd', 'window', 'user-rotation', 'lock', '1'); time.sleep(4)
    png = adb('exec-out', 'screencap', '-p')
    open(f'shots/barchk.{TAG}.{layout}.png', 'wb').write(png)
    im = Image.open(io.BytesIO(png)).convert('RGB')
    bl, bt, br, bb = bounds('js8_band_activity')
    cl, ct, cr, cb = bounds('js8_btn_cq')
    frame = im.getpixel((cl - 3, (ct + cb) // 2))
    # Down the Band Activity column, from 10 px above its bottom to the CQ button's top
    col = (bl + br) // 2
    down = runs([im.getpixel((col, y)) for y in range(bb - 10, ct + 2)])
    # Across the Band Activity | Message Window gap at mid height
    across = runs([im.getpixel((x, (bt + bb) // 2)) for x in range(br - 3, br + 20)])
    # Down the Band Activity bar: its centre is the middle of the non-frame run in [across]
    pos, gx = br - 3, None
    for c, n in across:
        if not near(c, frame) and pos > br - 1 and n >= 2:
            gx = pos + n // 2; break
        pos += n
    junction = runs([im.getpixel((gx, y)) for y in range(bb - 10, ct + 2)]) if gx else []
    print(layout, 'frame', frame, flush=True)
    print('  down Band Activity:', [(c, n) for c, n in down], flush=True)
    print('  across the Band Activity gap:', [(c, n) for c, n in across], flush=True)
    print('  down the Band Activity bar at x', gx, ':', [(c, n) for c, n in junction], flush=True)
    # Zoomed shot of the corner: Band Activity bottom right, the bar, the CQ row
    z = im.crop((br - 60, bb - 40, br + 60, ct + 10))
    z.resize((z.width * 6, z.height * 6), Image.NEAREST).save(f'shots/barchk.{TAG}.{layout}.zoom.png')
    # Expected down the list: list rows, then frame (the gap above the bar), then the bar, then frame, then CQ
    frames = [i for i, (c, n) in enumerate(down) if near(c, frame)]
    gap_above = down[frames[0]][1] if frames else 0
    # the bar: the first run of 3 px or more after that gap (1 px runs are edge blends)
    bar = next((r for r in down[frames[0] + 1:] if r[1] >= 3), None) if frames else None
    ok_gap = frames and 1 <= gap_above <= 4 and bar and bar[1] >= 3 and not near(bar[0], frame)
    # Band Activity bar: frame each side; going down it, no frame colour before the bar under the lists
    sides = [n for c, n in across if near(c, frame)]
    flush = bool(junction) and not any(near(c, frame) for c, n in junction[:next((i for i, (c, n) in enumerate(junction)
                                                                                 if i and n >= 3 and near(c, frame)), len(junction))])
    return ok_gap, flush, f'black above the bar {gap_above} px, bar {bar[1] if bar else 0} px; frame beside the upright bar {sides} px'


ROT = (adb('shell', 'settings', 'get', 'system', 'accelerometer_rotation').decode().strip(),
       adb('shell', 'settings', 'get', 'system', 'user_rotation').decode().strip())
print('device rotation', ROT, flush=True)
adb('shell', 'am', 'force-stop', PKG)
adb('shell', f'run-as {PKG} cp {PREFS} files/barchk_copy.xml')
adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0')
try:
    for layout in ('pc', 'ft8call'):
        with part(f'sideways {layout}', 30, [f'{layout} gap', f'{layout} flush'], res, mark):
            g, f, note = check(layout)
            mark(f'{layout} gap', g, note)
            mark(f'{layout} flush', f, 'upright bar meets the bar under the lists with no black between')
finally:
    with part('put back', 25):
        adb('shell', 'am', 'force-stop', PKG)
        adb('shell', f'run-as {PKG} cp files/barchk_copy.xml {PREFS}')
        adb('shell', f'run-as {PKG} rm files/barchk_copy.xml')
        adb('shell', 'input', 'keyevent', 'KEYCODE_HOME')
        adb('shell', 'cmd', 'window', 'user-rotation', 'free')
        adb('shell', 'settings', 'put', 'system', 'user_rotation', ROT[1])
        adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', ROT[0])
        print('put back: rotation', (adb('shell', 'settings', 'get', 'system', 'accelerometer_rotation').decode().strip(),
              adb('shell', 'settings', 'get', 'system', 'user_rotation').decode().strip()),
              'copy left' if b'barchk_copy' in adb('shell', f'run-as {PKG} ls files') else 'no copy left', flush=True)
print('RESULT', 'PASS' if res and all(r[1] for r in res) else 'FAIL', flush=True)
