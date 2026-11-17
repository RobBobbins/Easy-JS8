"""W144: resizing the Message Window keeps its place (Rob 2026-11-07: every resize jumped to the first line; Android
scrolls a selectable TextView to its cursor, which sits at the start of the text). At the newest line it stays at the
newest line; scrolled up it keeps the line it showed. Both drag directions.
W145: Settings > View "New Band Activity line for each message": ticked, a frame the sender marked first starts a new
Band Activity line at the same offset; unticked, one line per offset as the PC. The box is in Settings > View.
W146: Band Activity keeps 50 frames per offset (PC 10): a 30-frame message keeps its first frame.
W147: defaults (Rob 2026-11-07): with neither setting saved, Band Activity shows a line per message, the Settings > View
box is ticked, and a line is still shown 130 s after it was heard ("Remove messages from band activity after:"
Disabled; it was 2 minutes).
Upright. mwband.py <run> [ID,ID]"""
from onair2 import *
import io
from PIL import Image, ImageChops, ImageStat
assert_avd()
run = int(sys.argv[1])
only = sys.argv[2].split(',') if len(sys.argv) > 2 else None
res = []
SHOTS = 'shots'
os.makedirs(SHOTS, exist_ok=True)


def want(i):
    return not only or i in only


def mark(id, ok, note=''):
    print(id, 'PASS' if ok else 'FAIL', note, flush=True); res.append((id, ok, note))


def mw_screen():
    """The Message Window's bounds on the screen (uiautomator)."""
    n = re.search(r'<node [^>]*resource-id="[^"]*/js8_message_window"[^>]*>', dump())
    if not n:
        return None
    return tuple(map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n.group(0)).groups()))


def drag_divider(dy):
    """Drag the divider under the Message Window by dy px."""
    b = mw_screen()
    x, y = (b[0] + b[2]) // 2, b[3] + 8
    adb('shell', 'input', 'swipe', str(x), str(y), str(x), str(y + dy), '1500')
    time.sleep(1.5)


def rows_with(s):
    n = 0
    for nd in re.findall(r'<node [^>]*>', dump()):
        t = re.search(r' text="([^"]*)"', nd)
        if t and s in html.unescape(t.group(1)) and 'js8_message_window' not in nd and 'js8_typing_box' not in nd:
            n += 1
    return n


def grab():
    """The Message Window as a grey picture, inside its 5 dp padding."""
    b = mw_screen()
    if not b:  # a menu over the screen hides the box from the reader
        adb('shell', 'input', 'keyevent', 'BACK'); time.sleep(1); b = mw_screen()
    img = Image.open(io.BytesIO(adb('exec-out', 'screencap', '-p'))).convert('L')
    return img.crop((b[0] + 20, b[1], b[2] - 30, b[3]))


def find_band(before, y0, y1, after):
    """Where rows y0-y1 of [before] show in [after]: (y, mean pixel difference), the best match."""
    band = before.crop((0, y0, before.width, y1))
    best = (None, 999.0)
    for y in range(0, after.height - (y1 - y0) + 1):
        d = ImageStat.Stat(ImageChops.difference(band, after.crop((0, y, after.width, y + y1 - y0)))).mean[0]
        if d < best[1]:
            best = (y, d)
    return best


if want('W144'):
    with part('W144 setup', 50, ('W144',), res, mark):
        adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0')
        adb('shell', 'settings', 'put', 'system', 'user_rotation', '0')
        fresh()
        for i in range(1, 31):
            inj(f'K4M{chr(65 + (i - 1) % 26)}{chr(65 + (i - 1) // 26)}: VE3TST R{run} LINE {i:02d} END', 1000 + i * 20, 3, -10)
            time.sleep(.3)
        time.sleep(3)
    notes, ok = [], True
    for n, dy in enumerate((-180, 180, -180, 180)):
        with part(f'W144 bottom {n + 1}', 12, ('W144',), res, mark):
            a = grab(); drag_divider(dy); b = grab()
            # the newest line stays at the bottom: a's bottom 85 rows are b's (the box is 121 px at its smallest)
            y, d = find_band(a, a.height - 100, a.height - 15, b)
            good = a.height != b.height and d < 1.5 and abs(y - (b.height - 100)) <= 3
            notes.append(f'bottom drag {dy}: height {a.height}->{b.height}, newest line at {y} (want {b.height - 100}) diff {d:.1f}')
            ok &= good
            shot(f'W144_r{run}_bottom{n + 1}')
    with part('W144 scroll up', 12, ('W144',), res, mark):
        a = grab()
        r = mw_screen(); x = (r[0] + r[2]) // 2
        adb('shell', 'input', 'swipe', str(x), str(r[1] + 40), str(x), str(r[1] + 240), '400')  # short: no fling, no press-and-hold
        time.sleep(2)
        b = grab()
        y, d = find_band(a, a.height - 100, a.height - 15, b)
        moved = not (d < 1.5 and abs(y - (b.height - 100)) <= 3)
        notes.append(f'scrolled up: newest line left the bottom {moved}')
        ok &= moved
        shot(f'W144_r{run}_scrolled')
    for n, dy in enumerate((-180, 180, -180, 180)):
        with part(f'W144 middle {n + 1}', 12, ('W144',), res, mark):
            a = grab(); drag_divider(dy); b = grab()
            # the lines shown at the top stay at the top: moved down less than one line (about 60 px), not gone
            y, d = find_band(a, 15, 55, b)  # 40 rows: clear of the bottom 14 px padding after a shift of up to a line (about 60 px)
            good = a.height != b.height and d < 1.5 and 0 <= y - 15 < 70
            notes.append(f'middle drag {dy}: height {a.height}->{b.height}, top lines at {y} (were at 15) diff {d:.1f}')
            ok &= good
            shot(f'W144_r{run}_middle{n + 1}')
    mark('W144', ok, '; '.join(notes))

if want('W145') or want('W146'):
    with part('W145 setup', 25, ('W145', 'W146'), res, mark):
        fresh(**{'js8cfg_band_line_per_message': True})
        k = f'R{run}'
        inj(f'K4BA: VE3TST {k} FIRSTMSG', 1700, 1, -10); time.sleep(.4)
        inj(f'{k} MOREFIRST', 1700, 2, -10); time.sleep(.4)
        inj(f'K4BB: VE3TST {k} SECONDMSG', 1700, 3, -10); time.sleep(3)
        on_first, on_second = rows_with(f'{k} FIRSTMSG'), rows_with(f'{k} SECONDMSG')
        shot(f'W145_r{run}_ticked')
    with part('W145 unticked', 25, ('W145',), res, mark):
        fresh(**{'js8cfg_band_line_per_message': False})
        time.sleep(2)
        x = dump()
        both = 0
        for nd in re.findall(r'<node [^>]*>', x):
            t = re.search(r' text="([^"]*)"', nd)
            if t and f'{k} FIRSTMSG' in t.group(1) and f'{k} SECONDMSG' in t.group(1) and 'js8_message_window' not in nd:
                both += 1
        shot(f'W145_r{run}_unticked')
    with part('W145 settings', 25, ('W145',), res, mark):
        box = False
        if tap('Settings'):
            time.sleep(2)
            tap('View'); time.sleep(1.5)
            for _ in range(6):
                if find(dump(), 'New Band Activity line for each message'):
                    box = True; break
                adb('shell', 'input', 'swipe', '540', '1700', '540', '900', '400'); time.sleep(1)
            shot(f'W145_r{run}_settings')
        adb('shell', 'input', 'keyevent', 'BACK'); time.sleep(1.5)
    if want('W145'):
        mark('W145', on_first == 1 and on_second == 1 and both == 1 and box,
             f'ticked: first-message lines {on_first}, second-message lines {on_second} (1 and 1 = separate lines); '
             f'unticked: lines holding both {both}; Settings > View box shown {box}')
    if want('W146'):
        with part('W146', 40, ('W146',), res, mark):
            k = f'R{run}'
            inj(f'K4BC: VE3TST {k} F01', 2200, 1, -10); time.sleep(.3)
            for i in range(2, 30):
                inj(f'{k} F{i:02d}', 2200, 0, -10); time.sleep(.3)
            inj(f'{k} F30', 2200, 2, -10); time.sleep(3)
            got = ''
            for nd in re.findall(r'<node [^>]*>', dump()):
                t = re.search(r' text="([^"]*)"', nd)
                if t and f'{k} F30' in t.group(1) and 'js8_message_window' not in nd:
                    got = html.unescape(t.group(1))
            mark('W146', f'{k} F01' in got and f'{k} F30' in got, f'line: {got[:60]} ... {got[-30:]}')
if want('W147'):
    with part('W147', 200, ('W147',), res, mark):
        fresh(**{'js8cfg_band_line_per_message': None, 'js8cfg_activity_aging': None})
        k = f'D{run}'
        inj(f'K4BD: VE3TST {k} ONEMSG', 1800, 3, -10); time.sleep(.4)
        inj(f'K4BE: VE3TST {k} TWOMSG', 1800, 3, -10); t0 = time.time(); time.sleep(3)
        lines = (rows_with(f'{k} ONEMSG'), rows_with(f'{k} TWOMSG'))
        shot(f'W147_r{run}_band')
        ticked = None
        if tap('Settings'):
            time.sleep(2)
            for _ in range(6):
                n = re.search(r'<node [^>]*text="New Band Activity line for each message"[^>]*>', dump())
                if n:
                    ticked = 'checked="true"' in n.group(0); break
                adb('shell', 'input', 'swipe', '540', '1700', '540', '900', '400'); time.sleep(1)
            shot(f'W147_r{run}_settings')
        adb('shell', 'input', 'keyevent', 'BACK'); time.sleep(1.5)
        time.sleep(max(0, 130 - (time.time() - t0)))  # past the old 2 minutes
        kept = rows_with(f'{k} TWOMSG')
        shot(f'W147_r{run}_aged')
        mark('W147', lines == (1, 1) and ticked is True and kept == 1,
             f'lines per message {lines}; box ticked on screen {ticked}; line still shown after {time.time() - t0:.0f} s: {kept}')
print('RESULT', ' '.join(f'{i}={"PASS" if o else "FAIL"}' for i, o, _ in res), flush=True)
