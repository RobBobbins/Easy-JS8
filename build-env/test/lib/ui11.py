"""Waterfall panel and dividers in one sideways layout. ui11.py <run> <layout> [ID,ID]"""
import sys as _s
_lay = _s.argv[2]
_a = [_s.argv[0], _s.argv[1]] + _s.argv[3:]
_s.argv = _a
_src = open('ui7.py', encoding='utf8').read().split("row_list = [")[0].replace('"""Upright rows never tested: H, I, J. ui7.py <run> [ID,ID]"""', '')
_src = _src.replace("_s.argv = ['ui1', _run, 'U', '']", f"_s.argv = ['ui1', _run, '{_lay}', '']").replace("rotate('U')", f"rotate('{_lay}')")
exec(_src)
L = _lay
_up = up
NAME = {'P': 'pc', 'G': 'swipe', 'F': 'ft8call'}[_lay]
FIRST = {}
SPLIT = ('js8cfg_pc_split_band', 'js8cfg_pc_split_call', 'js8cfg_pc_split_text', 'js8cfg_pc_split_main', 'js8cfg_pc_split_wf')


def tall_panel():
    """reach() scrolls the panel at y 640 to 880; this divider place keeps that inside the panel."""
    import base64
    stop(); p = getprefs()
    p = re.sub(r'\s*<float name="js8cfg_pc_split_main"[^>]*/>', '', p).replace('</map>', '    <float name="js8cfg_pc_split_main" value="340.8" />\n</map>')
    b_ = base64.b64encode(p.encode('utf8')).decode()
    adb('shell', f"echo {b_} | base64 -d | run-as {PKG} sh -c 'cat > shared_prefs/{PKG}_preferences.xml'")


def up(**k):
    tall_panel()
    _up(**{**{q: None for q in SPLIT if not q.endswith('main')}, 'landscape_layout': NAME, 'js8cfg_filter_enabled': False, 'js8cfg_filter_center': None, 'js8cfg_filter_opacity': None, 'js8cfg_wf_smooth': None, **k})


def wf_shown():
    reach('js8_waterfall_view')
    time.sleep(.5)


def wf_page():
    if L == 'G':
        tapk('js8_tab_2', 1.5)


def j02l():
    wf_page(); wf_shown()
    b = bounds('js8_ruler')
    a = pv('js8cfg_tx_offset')
    adb('shell', 'input', 'tap', str(b[0] + (b[2] - b[0]) // 3), str((b[1] + b[3]) // 2)); time.sleep(1.5)
    c = pv('js8cfg_tx_offset')
    return a != c, f'{a} to {c}'


def splits():
    return dict(re.findall(r'name="(js8cfg_pc_split_[^"]*)" value="([^"]*)"', getprefs()))


def sw():
    return dict(re.findall(r'name="(js8cfg_sw_split_[^"]*)" value="([^"]*)"', getprefs()))


def i24g():
    a = sw()
    for i in range(5):
        tapk(f'js8_tab_{i}', 1)
        bb, mb = bounds('js8_band_activity'), bounds('js8_message_window')
        if bb and mb:
            break
    if not (bb and mb):
        return False, 'Band Activity and Message Window never share a page'
    y = (bb[1] + bb[3]) // 2
    gx = (bb[2] + mb[0]) // 2
    adb('shell', 'input', 'swipe', str(gx), str(y), str(gx - 130), str(y), '600'); time.sleep(1.5)
    c = sw()
    return a != c, c


def i25g():
    a = sw()
    tp('js8_btn_settings', 2)
    for _ in range(8):
        if 'Reset Frame Sizes' in x():
            break
        adb('shell', 'input', 'swipe', '600', '700', '600', '250', '350'); time.sleep(.7)
    ok = tapk('Reset Frame Sizes', 1.5); tapk('Cancel', 1); time.sleep(1)
    return ok and not sw(), f'{a} to {sw()}'


def i24l():
    """The line at Band Activity's right edge moves and is saved."""
    if L == 'G':
        return i24g()
    a = splits(); FIRST['s'] = a
    bb = bounds('js8_band_activity')
    y = (bb[1] + bb[3]) // 2
    adb('shell', 'input', 'swipe', str(bb[2] + 6), str(y), str(bb[2] - 130), str(y), '600'); time.sleep(1.5)
    c = splits()
    return a != c, {k[-5:]: (a.get(k), c.get(k)) for k in SPLIT if a.get(k) != c.get(k)}


def i25l():
    """PC View > Reset Frame Sizes."""
    if L == 'G':
        return i25g()
    tp('js8_menubar_view', .8); has('Reset Frame Sizes'); tapk('Reset Frame Sizes', 1.2); home(); time.sleep(1)
    keys = splits()
    return not keys or keys != FIRST.get('x'), keys


def wfrow(fn):
    def f():
        wf_page()
        return fn()
    return f


def j23():
    wf_tab('js8_wf_tab_display')
    for _ in range(14):
        if on('js8_wf_opacity_up'):
            break
        adb('shell', 'input', 'swipe', '1850', '820', '1850', '300', '300'); time.sleep(.7)
    return changes('js8cfg_filter_opacity', 'js8_wf_opacity_up')()


def s(tab, k, key):
    return wfrow(lambda: seek(tab, k, key))


def j13l():
    import sig
    up(tx_submode=0, callsign='K4SPOT', js8cfg_auto_drift_stop=True)
    tn = None
    for _ in range(4):
        tn = sig.tones('UeNbK+GGOo00', 1, 0); time.sleep(18)
        if tn:
            break
    wf_page(); wf_tab('js8_wf_tab_timing'); reach('js8_wf_auto_drift'); tapk('js8_wf_auto_drift', 1.5)
    n_ = re.findall(r'js8cfg_auto_drift_stop_after_run" value="(\d+)"', getprefs())
    sig.play([(tn, 1500), (None, 0)], 'a.pcm', 0); time.sleep(40)
    off = re.findall(r'js8cfg_auto_drift_on" value="(\w+)"', getprefs())
    fresh(callsign='VE3TST')
    return off == ['false'] and n_ not in ([], ['0']), f'stop after {n_}, on now {off}'


def look():
    """Waterfall picture for J28 to J30, checked by eye."""
    up(last_frequency='10130000', js8cfg_tx_offset='1500'); wf_page(); wf_shown(); time.sleep(22); shot(f'jlook_{L}_{_run}')
    return True, 'see picture'


PAGES = {'General': 'M01', 'Radio': 'M18', 'Audio': 'M25', 'Layout': 'M29', 'Reporting': 'M30', 'Frequencies': 'M34', 'Saved Messages': 'M39',
         'Notifications': 'M40', 'UI': 'M41', 'Diagnostics': 'M43', 'Phone': 'M44', 'About': 'M45'}


def pages():
    home(); open_station()
    miss = []
    for page, rid in PAGES.items():
        ok = tapk(page, .8)
        w = html.unescape(x())
        ok = ok and any(k in w for k in (STATION.get(page) or ('Back',)))
        if only is None or 'M' in only:
            out.append((rid + L, bool(ok), page)); print(rid + L, 'PASS' if ok else 'FAIL', page, flush=True)
        if not ok:
            miss.append(page); shot(f'fail_{rid}{L}_{_run}')
        for _ in range(3):
            w = x()
            if 'Diagnostics' in w and 'Audio' in w:
                break
            tapk('← Back', .7)
    home()


row_list = [('J02', j02l), ('J03', wfrow(j03)), ('J08', wfrow(j08)), ('J10', wfrow(j10)),
            ('J21', s('js8_wf_tab_display', 'js8_wf_gain', 'js8cfg_wf_gain')),
            ('J22', s('js8_wf_tab_display', 'js8_wf_zero', 'js8cfg_wf_zero')),
            ('J23', wfrow(j23)),
            ('J25', wfrow(spec)), ('J13', j13l), ('JL', look), ('I24', i24l), ('I25', i25l)]
SET = 'Save'
MENUS = (('H05', 'js8_btn_mode', 'JS8 Fast'), ('H16', 'js8_btn_saved', 'Edit Saved Messages…'), ('H06', 'js8_btn_settings', SET))
BAR = (('N01', 'js8_menubar_file', 'Exit'), ('N02', 'js8_menubar_configurations', 'Default'), ('N08', 'js8_menubar_view', 'Show Fullscreen'), ('N09', 'js8_menubar_mode', 'JS8 Fast'),
       ('N10', 'js8_menubar_control', 'Send Heartbeat...'), ('N11', 'js8_menubar_log', 'Add Log Entry...'), ('N12', 'js8_menubar_help', 'About'))


def menus():
    for rid, k, w in MENUS + (BAR if L in 'PF' else ()):
        ok = tp(k, .6) and has(w)
        out.append((rid + L, bool(ok), '')); print(rid + L, 'PASS' if ok else 'FAIL', flush=True)
        if not ok:
            shot(f'fail_{rid}{L}_{_run}')
        home()
    for rid, k, w in (('H10', 'js8_btn_hb', 'Send Heartbeat Now'), ('H11', 'js8_btn_cq', 'Send CQ Now')):
        ok = holdk(k) and has(w)
        out.append((rid + L, bool(ok), '')); print(rid + L, 'PASS' if ok else 'FAIL', flush=True)
        home()


up()
if only is None or 'M' in only:
    menus(); pages()
for i, f in row_list:
    if only is None or i + L in only:
        row(i + L, f)
adb('shell', 'settings', 'put', 'system', 'user_rotation', '0')
json.dump(out, open(f'ui11.{_run}.json', 'w'))
print('DONE', flush=True)
