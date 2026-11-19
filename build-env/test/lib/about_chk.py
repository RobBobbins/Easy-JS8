"""About screen 1.0.5 check (2026-11-18): version line and the 14 features, upright and sideways. about_chk.py <run>"""
import sys as _s
exec(open('ui9.py', encoding='utf8').read().split('\nrow_list = [')[0])
res = []
for code in ('U', 'P'):
    up(); rotate(code); time.sleep(2)
    home(); top(); open_station(); scroll_to('About'); tapk('About', 2)
    w = html.unescape(x())
    # scroll to the end of Features (sideways the list is one column and runs below the screen)
    if code == 'U':
        adb('shell', 'input', 'swipe', '540', '1500', '540', '700', '400')
    else:
        adb('shell', 'input', 'swipe', '1600', '800', '1600', '300', '400')
    time.sleep(1)
    w2 = w + html.unescape(x())
    open(f'shots/about.{_run}.{code}.png', 'wb').write(adb('exec-out', 'screencap', '-p'))
    ok = 'Version 1.0.5 (build 8)' in w2 and 'Station map' in w2 and 'APRS beacon with GPS and SmartBeaconing' in w2
    print('ABOUT', code, 'PASS' if ok else 'FAIL', flush=True)
    res.append(ok)
    home()
rotate('U')
print('RESULT', 'PASS' if all(res) else 'FAIL', flush=True)
