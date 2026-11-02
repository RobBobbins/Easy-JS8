"""Sections I, J, K: lists, waterfall, Inbox, Log QSO. ui2.py <run> [layout]"""
import sys as _s
_run = _s.argv[1]
_lays = (_s.argv[2] if len(_s.argv) > 2 else 'U') or 'U'
if len(_lays) != 1:
    _s.exit('one layout at a time (Rob, 2026-11-02)')
_s.argv = ['ui1', _run, '', '']
src = open('ui1.py', encoding='utf8').read().split('\nfor code in LAYS:')[0]
exec(src)
LAYS = _lays


def pv(key):
    m = re.search(r'name="%s"(?: value="([^"]*)"[^>]*/>|>([^<]*)<)' % re.escape(key), getprefs())
    return (m.group(1) or m.group(2)) if m else None


def changes(key, tap_id):
    def f():
        a = pv(key)
        ok = tp(tap_id, 1.2)
        return ok and pv(key) != a
    return f


def rows():
    return [n for n in re.findall(r'<node [^>]*>', x())]


def seed():
    """Band restore after a start replaces the lists, so repeats until the calls stay."""
    for _ in range(6):
        for i, (c1, o) in enumerate((('K4AAA', 900), ('K4BBB', 1100), ('K4AQA', 1300))):
            inj(f'{c1}: @ALLCALL CQ CQ CQ FN31', o, 3, -5 - i)
            time.sleep(.3)
        time.sleep(4)
        if 'K4AAA' in x():
            r = api('RX.GET_CALL_ACTIVITY', wait=.5)
            if 'K4AAA' in str(r):
                return


def callmenu(*want):
    def f():
        inj('K4AQA: @ALLCALL CQ CQ CQ FN31', 900, 3, -5)
        time.sleep(2.5)
        return holdk('K4AQA') and has(*want)
    return f


def wf_tab(tab):
    reach('js8_wf_tab_control') or None
    return tp(tab, 1.2)


def sec_i():
    check('I01', lambda: seed() or tapk('Offset', 1))
    check('I02', holds('js8_band_activity', 'Offset'))
    check('I03', lambda: seed() or (reach('js8_call_activity') and holdk('K4AQA') and has('Clear Entire List')))
    check('I05', lambda: reach('js8_call_activity'))
    for row, w in (('I08', 'Jump to 900Hz'), ('I11', 'Log...'), ('I12', 'Show Message Inbox...'), ('I13', 'Store Message...'), ('I14', 'Saved Messages...'),
                   ('I15', 'Directed to K4AQA...'), ('I16', 'Relay via K4AQA...'), ('I17', 'Deselect K4AQA'), ('I18', 'Add New Station or Group...'),
                   ('I19', 'Remove This Station'), ('I20', 'Block This Station'), ('I21', 'Clear Entire List')):
        check(row, callmenu(html.escape(w, quote=False) if False else w))


def sec_j():
    if L == 'G':
        tapk('js8_tab_2', 1.5)
    check('J01', lambda: reach('js8_waterfall_view') or reach('js8_waterfall'))
    check('J04', lambda: reach('js8_wf_offset_up') and changes('js8cfg_tx_offset', 'js8_wf_offset_up')())
    check('J05', lambda: reach('js8_wf_qsy'))
    check('J06', changes('js8cfg_wf_center', 'js8_wf_center_up'))
    check('J07', changes('js8cfg_filter_enabled', 'js8_wf_filter_enable'))
    check('J09', lambda: reach('js8_wf_filter_center_up'))
    check('J11', lambda: wf_tab('js8_wf_tab_timing') and reach('js8_wf_drift_up'))
    check('J12', lambda: wf_tab('js8_wf_tab_timing') and reach('js8_wf_auto_drift'))
    check('J14', lambda: wf_tab('js8_wf_tab_timing') and reach('js8_wf_drift_minute') and reach('js8_wf_drift_reset'))
    check('J15', lambda: wf_tab('js8_wf_tab_display') and changes('js8cfg_wf_start', 'js8_wf_start_up')())
    check('J16', lambda: wf_tab('js8_wf_tab_display') and reach('js8_wf_bpp_up'))
    check('J17', lambda: wf_tab('js8_wf_tab_display') and reach('js8_wf_palette'))
    check('J18', lambda: wf_tab('js8_wf_tab_display') and tp('js8_wf_palette_adjust', 2) and has('Import...', 'Export...'))
    check('J19', lambda: wf_tab('js8_wf_tab_display') and changes('js8cfg_wf_fps', 'js8_wf_fps_up')())
    check('J20', lambda: wf_tab('js8_wf_tab_display') and changes('js8cfg_wf_flatten', 'js8_wf_flatten')())
    check('J24', lambda: wf_tab('js8_wf_tab_display') and reach('js8_wf_avg_up'))
    check('J26', lambda: wf_tab('js8_wf_tab_display') and reach('js8_wf_decode_attempts'))
    check('J27', lambda: wf_tab('js8_wf_tab_display') and reach('js8_wf_height_up'))


def logbox(*want):
    """Opens Log QSO and scrolls its fields until every wanted text has been seen."""
    def f():
        if not tp('js8_btn_log', 2.5):
            return False
        seen = ''
        h, w = (2300, 1000) if L == 'U' else (1000, 2300)
        for _ in range(8):
            seen += x()
            if all(k in seen for k in want):
                break
            adb('shell', 'input', 'swipe', str(w // 2), str(int(h * .6)), str(w // 2), str(int(h * .25)), '300')
            time.sleep(.8)
        tapk('Cancel')
        return all(k in seen for k in want)
    return f


def sec_k():
    check('K05', lambda: seed() or (holdk('K4AQA') and tapk('Log...', 2.5) and has('K4AQA', 'Add to Log') and tapk('Cancel')))
    check('K06', logbox('Now'))
    check('K07', logbox('Add New Field'))
    check('K08', logbox('Retain Comments'))


for code in LAYS:
    layout(code)
    sec_i()
    sec_j()
    sec_k()
adb('shell', 'settings', 'put', 'system', 'user_rotation', '0')
json.dump(res, open(f'ui2.{_run}.json', 'w'))
print('DONE', flush=True)
