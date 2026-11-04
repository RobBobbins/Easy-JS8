"""W34 to W37: the four screen fixes of 2026-11-03. fixchk.py <run> [ID,ID]"""
import sys as _s
exec(open('ui8.py', encoding='utf8').read().split('\ndef h35():')[0].replace('"""Upright rows: H35, I04, K, L09, L15. ui8.py <run> [ID,ID]"""', ''))


def page(name):  # as ui9.py
    home(); top()
    return open_station() and tapk(name, 2)


def scroll_to(word, n=14):  # as ui9.py
    sx = '60' if L == 'U' else '150'
    for ys in ((('1500', '700'), ('700', '1500')) if L == 'U' else (('850', '350'), ('350', '850'))):
        for _ in range(n):
            if word in html.unescape(x()):
                return True
            adb('shell', 'input', 'swipe', sx, ys[0], sx, ys[1], '300'); time.sleep(.6)
    return False


def node_text(label):
    for n in nodes(f'text="{label}"'):
        return n


def w34():
    """Station > UI upright: Secondary Highlight Background is taller than the one-line Primary button (its text wraps)."""
    up(); page('UI'); scroll_to('Secondary Highlight Background'); time.sleep(1)
    p, s_ = node_text('Primary Highlight Background'), node_text('Secondary Highlight Background')
    hp = bounds_of(p)[3] - bounds_of(p)[1] if p else 0
    hs = bounds_of(s_)[3] - bounds_of(s_)[1] if s_ else 0
    shot(f'w34_{_run}')
    return abs(hp - 84) <= 3 and hs > hp + 20, f'Primary {hp} px (28 dp = 84), Secondary {hs} px'


def w35():
    """Inbox upright: an unread (bold) message; the Date column is judged on the screenshot."""
    up(); msg('K4RDX', 'DATE WIDTH CHECK'); ok = inbox_rows(); time.sleep(1)
    shot(f'w35_{_run}')
    return ok, 'inbox shown, see screenshot'


def w36():
    """Station > Frequencies: ticking Automatically switch bands ... keeps the page where it was."""
    key = 'Automatically switch bands / frequencies at specific times of day'
    up(js8cfg_auto_switch_bands=False); page('Frequencies'); scroll_to(key); time.sleep(1)
    # bring it well down the page first, so a jump to the top would move it
    for _ in range(3):
        b = bounds_of(node_text(key))
        if b[1] > 900:
            break
        adb('shell', 'input', 'swipe', '60', '900', '60', '1300', '300'); time.sleep(1)
    y0 = bounds_of(node_text(key))[1]
    tapk(key, 2.5)
    n = node_text(key); y1 = bounds_of(n)[1] if n else None
    shot(f'w36_{_run}'); tapk('Cancel', 1)
    return y1 is not None and abs(y1 - y0) < 30 and y0 > 400, f'box top {y0} before the tap, {y1} after'


def w37():
    """Station > Audio sideways (PC): as the page opens, the whole Remember power settings by band frame shows above
    the bottom of the scroll area (a frame cut off there reads as ending exactly at it). The page still scrolls 33 dp
    of empty space below, the 14 dp under every row plus the page's padding."""
    global L
    up(landscape_layout='pc'); L = 'P'; rotate('P'); time.sleep(2)
    page('Audio'); time.sleep(1.5)
    sv = bounds_of(nodes('ScrollView')[0]); t = bounds_of(node_text('Tune'))
    frames = [bounds_of(n) for n in nodes('FrameLayout')]
    box = min((f for f in frames if f[0] <= t[0] and f[1] <= t[1] and f[3] >= t[3] and f[1] > sv[1]), key=lambda f: f[3] - f[1], default=None)
    shot(f'w37_{_run}')
    tapk('Cancel', 1); L = 'U'; rotate('U')
    return bool(box) and box[3] < sv[3], f'Remember frame {box}, scroll area bottom {sv[3]}'


row('W34', w34)
row('W35', w35)
row('W36', w36)
row('W37', w37)
json.dump(out, open(f'fixchk.{_run}.json', 'w'))
print('DONE', flush=True)
