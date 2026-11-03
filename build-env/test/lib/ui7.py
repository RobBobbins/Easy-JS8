"""Upright rows never tested: H, I, J. ui7.py <run> [ID,ID]"""
import sys as _s
_run = _s.argv[1]
only = _s.argv[2].split(',') if len(_s.argv) > 2 else None
_s.argv = ['ui1', _run, 'U', '']
src = open('ui1.py', encoding='utf8').read().split('\nfor code in LAYS:')[0]
exec(src)
_two = open('ui2.py', encoding='utf8').read()
exec('def pv(key):' + _two.split('def pv(key):')[1].split('def callmenu')[0])
exec('def wf_tab' + _two.split('def wf_tab')[1].split('def sec_i')[0])
_s.stdout.reconfigure(encoding='utf8', errors='replace')
out = []
NOAUTO = dict(autoreply_enabled=False, js8cfg_autoreply_startup=False, js8cfg_transmit_directed=False, log_messages_to_file=True)


def row(i, fn):
    if only and i not in only:
        return
    try:
        r = fn()
        ok, note = r if isinstance(r, tuple) else (r, '')
    except Exception as e:
        ok, note = False, repr(e)[:100]
    print(i, 'PASS' if ok else 'FAIL', note, flush=True)
    out.append((i, bool(ok), str(note)[:100]))
    if not ok:
        shot(f'fail_{i}_{_run}')
    home()


def up(**k):
    fresh(**{'js8cfg_wf_start': None, 'js8cfg_wf_gain': None, 'js8cfg_wf_zero': None, 'js8cfg_tx_offset': '1500', 'landscape_layout': 'pc', 'tx_submode': 1, 'js8cfg_tx_watchdog': '0', 'js8cfg_api_dynamic_info': False, 'js8cfg_reset_activity': True, **NOAUTO, **k})
    rotate('U')
    time.sleep(1)


def bounds_of(n):
    return tuple(map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n).groups()))


def bounds(k):
    for n in re.findall(r'<node [^>]*>', x()):
        if '/' + k + '"' in n:
            return bounds_of(n)


def typed(s):
    reach('js8_typing_box'); tapk('js8_typing_box', .8); text(s); adb('shell', 'input', 'keyevent', '111'); time.sleep(.8)


def drag_h(k, frac):
    b = bounds(k)
    y = (b[1] + b[3]) // 2
    adb('shell', 'input', 'swipe', str(b[0] + (b[2] - b[0]) // 2), str(y), str(int(b[0] + (b[2] - b[0]) * frac)), str(y), '300')
    time.sleep(1.2)


def wf_shown():
    """Waterfall fully on screen; the drag is on the level meter, which does not take touches."""
    reach('js8_waterfall_view'); time.sleep(.5)
    b = bounds('js8_waterfall_view')
    y = min(b[1] + 200, 2100)
    adb('shell', 'input', 'swipe', '90', str(y), '90', str(max(150, y - 700)), '400'); time.sleep(1.5)


def top():
    """Page to its top. Drags start on a spot that lets the page scroll: below CAT, the HB row, or right of the tabs."""
    for _ in range(12):
        xml = x()
        if find(xml, 'js8_clock'):
            return True
        p = find(xml, 'js8_btn_cat')
        if p:
            p = (90, min(p[1] + 180, 2100))
        else:
            p = find(xml, 'js8_typing_box') or find(xml, 'js8_btn_hb')
        if not p:
            t_ = find(xml, 'js8_wf_tab_timing')
            p = (1000, t_[1]) if t_ else (90, 300)
        adb('shell', 'input', 'swipe', str(p[0]), str(p[1]), str(p[0]), '2200', '350'); time.sleep(.8)
    return False


def j02():
    b = None
    for _ in range(5):
        wf_shown()
        b = bounds('js8_ruler')
        if b and 200 < b[1] < 1800:
            break
    a = pv('js8cfg_tx_offset')
    print(b, flush=True); adb('logcat', '-c')
    adb('shell', 'input', 'tap', str(b[0] + 400), str((b[1] + b[3]) // 2)); time.sleep(1.5)
    c = pv('js8cfg_tx_offset')
    print([l[-60:] for l in adb('logcat', '-d').decode('utf8', 'replace').splitlines() if 'Tap at' in l or 'Ruler' in l][-3:], flush=True); shot('j02x')
    return a != c, f'{a} to {c}'


def j03():
    typed('LONG QUEUED MESSAGE THAT TAKES SEVERAL FRAMES TO SEND')
    tapk('js8_btn_send', .5); time.sleep(3)
    wf_shown()
    b = bounds('js8_waterfall')
    a = pv('js8cfg_tx_offset')
    adb('shell', 'input', 'tap', str(b[0] + 300), str(b[1] + 200)); time.sleep(1.2)
    c = pv('js8cfg_tx_offset')
    reach('js8_btn_halt'); tapk('js8_btn_halt', 2)
    return a == c, f'{a} stays {c}'


def j08():
    wf_tab('js8_wf_tab_control')
    reach('js8_wf_filter_enable')
    if pv('js8cfg_filter_enabled') == 'false':
        tapk('js8_wf_filter_enable', 1)
    reach('js8_wf_filter_sync')
    tapk('js8_wf_filter_sync', 1.2)
    a, c = pv('js8cfg_filter_center'), pv('js8cfg_tx_offset')
    return a == c, f'center {a} offset {c}'


def j10():
    wf_tab('js8_wf_tab_control')
    reach('js8_wf_filter_enable')
    if pv('js8cfg_filter_enabled') == 'false':
        tapk('js8_wf_filter_enable', 1)
    reach('js8_wf_filter_center_knob')
    a = pv('js8cfg_filter_center')
    b = bounds('js8_wf_filter_center_knob')
    adb('shell', 'input', 'swipe', str(b[0] + 15), str(b[3] - 15), str(b[2] - 15), str(b[1] + 15), '500'); time.sleep(1.2)
    c = pv('js8cfg_filter_center')
    return a != c, f'{a} to {c}'


def seek(tab, k, key):
    wf_tab(tab); reach(k)
    for _ in range(3):
        if bounds(k):
            break
        wf_tab(tab); reach(k)
    a = pv(key); drag_h(k, .85); c = pv(key)
    return a != c, f'{a} to {c}'


def spec():
    wf_tab('js8_wf_tab_display'); reach('js8_wf_spectrum'); tapk('js8_wf_spectrum', 1.2)
    ok = tapk('Linear Avg', 1.2); s0 = pv('js8cfg_wf_smooth'); reach('js8_wf_smooth_up'); tapk('js8_wf_smooth_up', 1); s1 = pv('js8cfg_wf_smooth')
    return ok and s0 != s1, f'spectrum {pv("js8cfg_wf_spectrum")} smooth {s0} to {s1}'


def i06():
    top()
    n = [n for n in re.findall(r'<node [^>]*>', x()) if 'text="Callsigns' in n]
    b = bounds_of(n[0]); p = ((b[0] + b[2]) // 2, (b[1] + b[3]) // 2)
    adb('shell', 'input', 'swipe', str(p[0]), str(p[1]), str(p[0]), str(p[1]), '900'); time.sleep(1.5)
    w = html.unescape(x())
    ok = 'Show Columns...' in w and tapk('Show Columns...', 1.2) and has('Callsigns', 'Last heard timestamp')
    return ok, re.findall(r' text="([^"]+)"', html.unescape(x()))[:6]


def i07():
    reach('js8_call_activity')
    cb = bounds('js8_call_activity')
    n = [n for n in re.findall(r'<node [^>]*>', x()) if '@TESTG' in n]
    if not n:
        return False, 'no list'
    # press on the column rows: the empty part of the list below the last row
    y = cb[3] - 25
    adb('shell', 'input', 'swipe', str((cb[0] + cb[2]) // 2), str(y), str((cb[0] + cb[2]) // 2), str(y), '900'); time.sleep(1.5)
    w = html.unescape(x())
    return 'Add New Station or Group...' in w and 'Clear Entire List' in w, re.findall(r' text="([^"]+)"', w)[:5]


def i24():
    reach('js8_message_window'); mb = bounds('js8_message_window')
    adb('shell', 'input', 'swipe', '500', str(mb[1] - 12), '500', str(mb[1] + 60), '500'); time.sleep(1.5)
    keys = re.findall(r'name="(js8cfg_up_split_[^"]*)" value="([^"]*)"', getprefs())
    return bool(keys), keys


def i25():
    tp('js8_btn_settings', 2)
    for _ in range(8):
        if 'Reset Frame Sizes' in x():
            break
        adb('shell', 'input', 'swipe', '600', '900', '600', '300', '350'); time.sleep(.7)
    ok = tapk('Reset Frame Sizes', 1.5); tapk('Cancel', 1); time.sleep(1)
    keys = re.findall(r'name="(js8cfg_up_split_[^"]*)"', getprefs())
    return ok and not keys, keys


def h02():
    wf_tab('js8_wf_tab_timing'); reach('js8_wf_drift_up'); tapk('js8_wf_drift_up', 1.5)
    reach('js8_btn_hb'); top(); t_ = find(x(), 'js8_clock')
    wf_tab('js8_wf_tab_timing'); reach('js8_wf_drift_reset'); tapk('js8_wf_drift_reset', 1)
    return bool(t_) and 'ms)' in t_[2], t_[2] if t_ else 'no clock'


def h27():
    typed('HELLO THERE')
    time.sleep(.6)
    t_ = find(x(), 'js8_status_wpm')
    clearbox()
    return bool(t_) and 'wpm' in t_[2], t_[2] if t_ else 'none'


row_list = [('J02U', j02), ('J03U', j03), ('J08U', j08), ('J10U', j10),
            ('J21U', lambda: seek('js8_wf_tab_display', 'js8_wf_gain', 'js8cfg_wf_gain')),
            ('J22U', lambda: seek('js8_wf_tab_display', 'js8_wf_zero', 'js8cfg_wf_zero')),
            ('J23U', lambda: wf_tab('js8_wf_tab_display') and reach('js8_wf_opacity_up') and changes('js8cfg_filter_opacity', 'js8_wf_opacity_up')()),
            ('J25U', spec), ('I06U', i06), ('I07U', i07), ('I24U', i24), ('I25U', i25), ('H02U', h02), ('H27U', h27)]
if not only or 'I07U' in only:
    stop(); adb('shell', f'run-as {PKG} sh -c "rm -f databases/js8_messages.db*"')
up()
for i, f in row_list:
    row(i, f)
adb('shell', 'settings', 'put', 'system', 'user_rotation', '0')
json.dump(out, open(f'ui7.{_run}.json', 'w'))
print('DONE', flush=True)
