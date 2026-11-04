"""W41: Station > UI colour samples paint like the PC (its background setting, its text setting) and repaint
together when a colour is picked; the picker opens at the current colour. uicolchk.py <run>"""
import sys as _s
exec(open('fixchk.py', encoding='utf8').read().split('\ndef node_text')[0])
from PIL import Image
import io


def samples():
    """Button label -> K1ABC sample bounds on the same row, top to bottom (first match per label)."""
    ns = re.findall(r'<node [^>]*>', x())
    labs = [(html.unescape(re.search(r' text="([^"]*)"', n).group(1)), bounds_of(n)) for n in ns if re.search(r' text="[^"]+"', n)]
    ks = [b for t, b in labs if t == 'K1ABC']
    out_ = {}
    for t, b in labs:
        if t in ('K1ABC',) or b[0] > 300:
            continue
        cy = (b[1] + b[3]) // 2
        for k in ks:
            if k[1] <= cy <= k[3]:
                out_.setdefault(t, k)
    return out_


def img():
    return Image.open(io.BytesIO(adb('exec-out', 'screencap', '-p'))).convert('RGB')


def bg(im, b):
    return '#%02X%02X%02X' % im.getpixel((b[0] + 6, (b[1] + b[3]) // 2))


def count(im, b, test):
    return sum(1 for xx in range(b[0], b[2], 2) for yy in range(b[1], b[3], 2) if test(im.getpixel((xx, yy))))


def w41():
    up(); page('UI'); time.sleep(1)
    s = samples(); im = img()
    notes = []
    want = {'Directed Messages Background': '#FF6666', 'Selected Row Background': '#3498DB', 'Table Background': '#FFFFFF'}
    ok = True
    for lab, c in want.items():
        got = bg(im, s[lab]) if lab in s else None
        ok &= got == c; notes.append(f'{lab.split()[0]} {got}')
    # Received: Foreground Color is the second "Foreground Color" on the page: the received box colour, black text
    ns = [bounds_of(n) for n in re.findall(r'<node [^>]*>', x()) if 'text="K1ABC"' in n]
    rx = [b for b in ns if bg(im, b) == '#FFEAA7']
    dark = count(im, rx[1], lambda p: sum(p) < 150) if len(rx) > 1 else 0
    ok &= len(rx) > 1 and dark > 15; notes.append(f'received samples {len(rx)}, black text px {dark}')
    # pick: table Foreground Color, Red bar to the right end (from black it gives #FF0000)
    b = s['Foreground Color']; adb('shell', 'input', 'tap', str((b[0] + b[2]) // 2 - 400), str((b[1] + b[3]) // 2)); time.sleep(1.5)
    bars = [bounds_of(n) for n in re.findall(r'<node [^>]*>', x()) if 'SeekBar' in n]
    if len(bars) == 3:
        r = bars[0]; adb('shell', 'input', 'swipe', str(r[0] + 5), str((r[1] + r[3]) // 2), str(r[2] + 50), str((r[1] + r[3]) // 2), '400'); time.sleep(1)
    tapk('OK', 1.5); time.sleep(1)
    im = img(); s = samples()
    red = {lab: count(im, s[lab], lambda p: p[0] > 200 and p[1] < 70 and p[2] < 70) for lab in ('Primary Highlight Background', 'CQ Messages Background') if lab in s}
    ok &= len(bars) == 3 and len(red) == 2 and all(v > 15 for v in red.values()); notes.append(f'red text px after pick {red}')
    shot(f'w41_{_run}'); tapk('Cancel', 1)
    return ok, '; '.join(notes)


row('W41', w41)
json.dump(out, open(f'uicolchk.{_run}.json', 'w'))
print('DONE', flush=True)
