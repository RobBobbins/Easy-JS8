"""Sideways rows no script had: H31 Power slider; N03 to N07 Configurations menu; N16 status bar configuration name.
wside.py <run> <P|G|F> [ID,ID]. The Configurations rows use throwaway configurations TCFG..., never the current one,
and end on Default. Pages has no menu bar: N rows are N/A there."""
import sys as _s
import base64
_lay = _s.argv[2]
_s.argv = [_s.argv[0], _s.argv[1]] + _s.argv[3:]
_src = open('ui7.py', encoding='utf8').read().split("row_list = [")[0].replace('"""Upright rows never tested: H, I, J. ui7.py <run> [ID,ID]"""', '')
_src = _src.replace("_s.argv = ['ui1', _run, 'U', '']", f"_s.argv = ['ui1', _run, '{_lay}', '']").replace("rotate('U')", f"rotate('{_lay}')")
exec(_src)
L = _lay
NAME = {'P': 'pc', 'G': 'swipe', 'F': 'ft8call'}[_lay]
_up = up
CFG = 'files/configurations'


def up(**k):
    _up(**{'landscape_layout': NAME, **k})


def h31():
    """Power slider: a tap a third and two thirds down its track sets Power (js8cfg_out_attenuation) to two different values."""
    up(js8cfg_out_attenuation='0')
    reach('js8_gain_slider')
    b = bounds('js8_gain_slider'); xm = (b[0] + b[2]) // 2
    vals = []
    for f in (.33, .66):
        adb('shell', 'input', 'tap', str(xm), str(int(b[1] + (b[3] - b[1]) * f))); time.sleep(1.2)
        m = re.search(r'name="js8cfg_out_attenuation">([^<]*)<', getprefs())
        vals.append(m.group(1) if m else None)
    ok = None not in vals and vals[0] != '0' and vals[0] != vals[1]
    return ok, f'Power after taps {vals}'


def files():
    return set(adb('shell', f"run-as {PKG} ls {CFG}").decode('utf8', 'replace').split())


def put(name, text):
    b_ = base64.b64encode(text.encode('utf8')).decode()
    adb('shell', f"run-as {PKG} mkdir -p {CFG}")
    adb('shell', f"echo {b_} | base64 -d | run-as {PKG} sh -c 'cat > {CFG}/{name}.json'")


def cat(name):
    return adb('shell', f"run-as {PKG} cat {CFG}/{name}.json").decode('utf8', 'replace')


def current():
    m = re.search(r'name="current">([^<]*)<', adb('exec-out', 'run-as', PKG, 'cat', f'shared_prefs/js8_configurations.xml').decode('utf8', 'replace'))
    return m.group(1) if m else 'Default'


def snapshot(**change):
    """The current settings file as a configuration file (Js8Configurations.snapshot), with changes."""
    out = {}
    for t, k, v in re.findall(r'<(boolean|int|long|float|string) name="([^"]+)"(?: value="([^"]*)" />|>)', getprefs()):
        if t == 'string':
            m = re.search(r'<string name="%s">(.*?)</string>' % re.escape(k), getprefs(), re.S)
            out[k] = {'t': 's', 'v': html.unescape(m.group(1)) if m else ''}
        elif t == 'boolean':
            out[k] = {'t': 'b', 'v': v == 'true'}
        else:
            out[k] = {'t': t[0], 'v': float(v) if t == 'float' else int(v)}
    for k, v in change.items():
        out[k] = {'t': 's', 'v': v}
    return json.dumps(out)


def menu(cfg, item, wait=1.5):
    """Configurations > cfg > item."""
    return tp('js8_menubar_configurations', 1.2) and tapk(cfg, 1.2) and tapk(item, wait)


def clean():
    for f in files():
        if f.startswith('TCFG'):
            adb('shell', f"run-as {PKG} rm {CFG}/{f}")


def n03():
    """Clone Into: TCFGA (empty) takes the current configuration's values."""
    up(); clean(); put('TCFGA', '{}'); put('TCFGB', '{}')
    ok = menu('TCFGA', 'Clone Into ...') and tapk('Default', 1.2)
    w = html.unescape(x()); asked = 'Confirm overwrite' in w
    ok = ok and asked and tapk('Yes', 1.5)
    got = cat('TCFGA')
    return ok and 'VE3TST' in got, f'asked {asked}, TCFGA now {len(got)} characters, has VE3TST {"VE3TST" in got}'


def n04():
    """Rename TCFGA to TCFGR."""
    if 'TCFGA.json' not in files():
        put('TCFGA', '{"callsign":{"t":"s","v":"VE3TST"}}')
    ok = menu('TCFGA', 'Rename ...')
    p = find(x(), 'TCFGA')
    if ok and p:
        adb('shell', 'input', 'tap', str(p[0]), str(p[1])); time.sleep(.5)
        adb('shell', 'input', 'keyevent', '123')
        for _ in range(8):
            adb('shell', 'input', 'keyevent', '67')
        text('TCFGR'); time.sleep(.5)
        adb('shell', 'input', 'keyevent', '111'); time.sleep(1)  # sideways the keyboard fills the screen and hides OK
        tapk('OK', 1.5)
    f = files()
    return 'TCFGR.json' in f and 'TCFGA.json' not in f, f'files {sorted(f)}'


def n05():
    """Reset TCFGR: asked, then its file holds no values."""
    if 'TCFGR.json' not in files():
        put('TCFGR', '{"callsign":{"t":"s","v":"VE3TST"}}')
    ok = menu('TCFGR', 'Reset')
    asked = 'Confirm reset' in html.unescape(x())
    ok = ok and asked and tapk('Yes', 1.5)
    got = cat('TCFGR').strip()
    return ok and got == '{}', f'asked {asked}, TCFGR now {got[:40]!r}'


def n06():
    """Delete TCFGR: asked, then the file is gone."""
    if 'TCFGR.json' not in files():
        put('TCFGR', '{}')
    ok = menu('TCFGR', 'Delete')
    asked = 'Confirm deletion' in html.unescape(x())
    ok = ok and asked and tapk('Yes', 1.5)
    f = files()
    return ok and 'TCFGR.json' not in f, f'asked {asked}, files {sorted(f)}'


R = {}


def n07():
    """Switch To TCFGS (the current settings with callsign VE3SWT), then back to Default."""
    up(); put('TCFGS', snapshot(callsign='VE3SWT'))
    menu('TCFGS', 'Switch To', 6); rotate(L); time.sleep(2)
    cur, call = current(), re.search(r'name="callsign">([^<]*)<', getprefs())
    w = html.unescape(x())
    R['label'] = 'TCFGS' in w
    shot(f'n07_{L}_{_run}')
    menu('Default', 'Switch To', 6); rotate(L); time.sleep(2)
    back, call2 = current(), re.search(r'name="callsign">([^<]*)<', getprefs())
    R['gone'] = 'TCFGS' not in html.unescape(x())
    clean()
    c1, c2 = call and call.group(1), call2 and call2.group(1)
    return cur == 'TCFGS' and c1 == 'VE3SWT' and back == 'Default' and c2 == 'VE3TST', f'switched to {cur} ({c1}), back to {back} ({c2})'


def n16():
    """Status bar shows the configuration name while TCFGS is current, nothing on Default (seen during N07)."""
    if not R:
        n07()
    return R.get('label') and R.get('gone'), f'name shown on TCFGS {R.get("label")}, gone on Default {R.get("gone")}'


ROWS = [('H31', h31)] + ([] if L == 'G' else [('N03', n03), ('N04', n04), ('N05', n05), ('N06', n06), ('N07', n07), ('N16', n16)])
if only:
    only = [o if o.endswith(L) else o + L for o in only]
for i, f in ROWS:
    row(i + L, f)
if L == 'G':
    for i in ('N03', 'N04', 'N05', 'N06', 'N07', 'N16'):
        out.append((i + L, None, 'N/A: no menu bar in Pages'))
adb('shell', 'settings', 'put', 'system', 'user_rotation', '0')
json.dump(out, open(f'wside.{_run}.{L}.json', 'w'))
print('DONE', flush=True)
