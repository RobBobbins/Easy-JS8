"""W38, W39: two-finger pinch zooms the sideways PC and FT8Call windows. zoomchk.py <run> [ID,ID]"""
import sys as _s
exec(open('ui8.py', encoding='utf8').read().split('\ndef h35():')[0].replace('"""Upright rows: H35, I04, K, L09, L15. ui8.py <run> [ID,ID]"""', ''))


def width_of(rid):
    for n in nodes(f'/{rid}"'):
        b = bounds_of(n)
        return b[2] - b[0]
    return 0


def pinch_row(code, value):
    """Sideways layout `value`: the Send button's width before and after a 2x pinch at the screen centre."""
    global L
    up(landscape_layout=value); L = code; rotate(code); time.sleep(2.5)
    w0 = width_of('js8_btn_send')
    gesture('pinch', 1200, 540); time.sleep(1.5)
    w1 = width_of('js8_btn_send')
    shot(f'w{"38" if code == "P" else "39"}_{_run}')
    # the app restarts at the next up(); a scripted pinch-in only shrinks a little (zooming out checked by hand by Rob)
    for _ in range(3):
        adb('shell', f'am broadcast -n {PKG}/com.js8call.example.debug.DebugGestureReceiver --es gesture pinch --ef x {1200 - bounds_of(re.findall(r"<node [^>]*>", x())[0])[0]} --ef y 540 --ef factor 0.6'); time.sleep(1.5)  # pinch out: back to fitted
    w2 = width_of('js8_btn_send')
    L = 'U'; rotate('U')
    return w0 > 0 and w1 > w0 * 1.6, f'Send {w0} px, after pinch {w1} px, after pinch out {w2} px'


row('W38', lambda: pinch_row('P', 'pc'))
row('W39', lambda: pinch_row('F', 'ft8call'))
json.dump(out, open(f'zoomchk.{_run}.json', 'w'))
print('DONE', flush=True)
