"""Station rows, Upright. ui9.py <run> [ID,ID]"""
import sys as _s
exec(open('ui7.py', encoding='utf8').read().split("row_list = [")[0].replace('"""Upright rows never tested: H, I, J. ui7.py <run> [ID,ID]"""', ''))
RIG = dict(rig_control_enabled=True, rig_type='network', rig_network_kind='rigctl', rigctld_host='10.0.2.2', rigctld_port='4599', js8cfg_cat_poll_interval='1', js8cfg_ptt_method='cat')
NORIG = dict(rig_control_enabled=False, rig_type='none', js8cfg_ptt_method='vox')


def nodes():
    return re.findall(r'<node [^>]*>', x())


def page(name):
    home(); top()
    return open_station() and tapk(name, 2)


def scroll_to(word, n=14):
    sx = '60' if L == 'U' else '150'  # sideways the window starts at x 136 (camera cutout) and the screen is 1080 high
    for ys in ((('1500', '700'), ('700', '1500')) if L == 'U' else (('850', '350'), ('350', '850'))):
        for _ in range(n):
            if word in html.unescape(x()):
                return True
            adb('shell', 'input', 'swipe', sx, ys[0], sx, ys[1], '300'); time.sleep(.6)
    return False


def after(label, what):
    ns = nodes()
    for i, n in enumerate(ns):
        if label in html.unescape(n) and 'text=' in n:
            for m in ns[i + 1:]:
                if what in m:
                    return m


def show_below(label):
    """Sideways a label at the bottom edge has its box below the screen: the label is dragged up to y 400."""
    if L == 'U':
        return
    for n in nodes():
        if label in html.unescape(n) and 'text=' in n:
            b = bounds_of(n); y = (b[1] + b[3]) // 2
            if y > 600:
                adb('shell', 'input', 'swipe', '150', str(y), '150', '400', '800'); time.sleep(1)
            return


def tap_n(n):
    b = bounds_of(n)
    adb('shell', 'input', 'tap', str((b[0] + b[2]) // 2), str((b[1] + b[3]) // 2)); time.sleep(1)


def ok():
    return tapk('OK', 2)


def spin(p_, label, key):
    page(p_); scroll_to(label); show_below(label)
    a = pv(key)
    tap_n(after(label, 'text="▲"')); ok(); time.sleep(1)
    c = pv(key)
    return a != c, f'{a} to {c}'


def typefield(p_, label, key, val):
    page(p_); scroll_to(label); show_below(label)
    tap_n(after(label, 'EditText')); adb('shell', 'input', 'keyevent', '123')
    for _ in range(40):
        adb('shell', 'input', 'keyevent', '67')
    text(val); adb('shell', 'input', 'keyevent', '111'); time.sleep(.8)
    ok(); time.sleep(1)
    c = pv(key)
    return c == val, c


def m05():
    page('General'); scroll_to('Acquire GPS')
    tapk('Acquire GPS', 2)
    if 'While using the app' in html.unescape(x()):
        tapk('While using the app', 2)
    end = time.time() + 25
    while time.time() < end:
        adb('emu', 'geo', 'fix', '-79.4', '43.7')
        w = html.unescape(x())
        if not re.search('Acquiring', w):
            break
        time.sleep(2)
    w = html.unescape(x())
    g = re.findall(r' text="([A-R]{2}\d\d[A-Xa-x]{2}\d\d)"', w)
    ok_ = bool(g) and tapk('Set', 1.5)
    w2 = html.unescape(x())
    tapk('Cancel', 1)
    return ok_ and len(re.findall(g[-1], w2)) >= 2, g


def m19():
    fresh(**NORIG, landscape_layout='pc'); rotate('U')
    page('Radio'); scroll_to('Test PTT')
    n = [n for n in nodes() if 'text="Test PTT"' in n]
    return bool(n) and 'enabled="false"' in n[0], 'Test PTT greyed with Rig None'


def flog():
    return open('fakes.log', encoding='utf8', errors='replace').read().splitlines()


def m20():
    fresh(**RIG, landscape_layout='pc'); rotate('U'); time.sleep(2)
    page('Radio'); scroll_to('Test CAT')
    b = len(flog()); tapk('Test CAT', 8)
    shot(f'm20_{_run}')
    return len(flog()) > b, f'{len(flog()) - b} rig commands'


def m21():
    page('Radio'); scroll_to('Test PTT')
    b = len(flog()); tapk('Test PTT', 2); on_ = [l for l in flog()[b:] if re.search(r'rigctl T [123]', l)]
    tapk('Test PTT', 2); off = [l for l in flog()[b:] if re.search(r'rigctl T 0', l)]
    r = bool(on_) and bool(off), f'{on_[:1]} {off[:1]}'
    tapk('Cancel', 1)
    fresh(**NORIG, landscape_layout='pc'); rotate('U')
    return r


def m26():
    page('Audio'); scroll_to('Output channel:')
    tap_n(after('Output:', 'clickable="true"'))
    w = html.unescape(x()); shot(f'm26_{_run}')
    return "Phone's current audio output" in w, re.findall(r' text="([^"]+)"', w)[:6]


def m27():
    page('Audio'); scroll_to('Input channel:')
    tap_n(after('Input channel:', 'clickable="true"'))
    w = html.unescape(x())
    t_ = re.findall(r' text="([^"]+)"', w)
    return 'Mono' in t_ and 'Left' in t_ and 'Right' in t_ and 'Both' in t_, t_[:6]  # N23: the PC lists Both, greyed


def freq_rows():
    return len([n for n in nodes() if re.search(r' text="\d+\.\d+', n)])


def fill_freq(mhz, desc):
    """Dialog boxes get their text through uiautomator; adb text does not reach them."""
    import uiautomator2
    d = uiautomator2.connect('emulator-5600')
    if L == 'U':
        e = d(className='android.widget.EditText')
        n = e.count
        e[n - 2].set_text(mhz); e[n - 1].set_text(desc); time.sleep(.5)
    else:  # sideways the box shows one field at a time: Frequency, then scrolled down, Description
        d(text='Frequency (MHz):').down(className='android.widget.EditText').set_text(mhz)
        adb('shell', 'input', 'swipe', '1200', '700', '1200', '300', '400'); time.sleep(.8)
        d(text='Description:').down(className='android.widget.EditText').set_text(desc); time.sleep(.5)
    tapk('button1', 2)


def m35():
    page('Frequencies'); scroll_to('Insert ...')
    tapk('Insert ...', 2); fill_freq('0.137', 'ZTEST')
    shot(f'm35_{_run}')
    ins = scroll_to('0.137000', 6)  # sideways the new row is below the screen edge
    n = [n for n in nodes() if '0.137000' in n]
    tap_n(n[0]); tapk('Delete', 1.5)
    gone = '0.137000' not in x()
    tapk('Insert ...', 2); fill_freq('0.138', 'ZTWO'); tapk('Reset', 1.5); tapk('Yes', 1.5)
    reset = '0.138000' not in x()
    tapk('Cancel', 1)
    return ins and gone and reset, f'insert {ins} delete {gone} reset {reset}'


def m36():
    page('Frequencies'); scroll_to('Insert ...')
    tapk('Insert ...', 2); fill_freq('0.137', 'ZTEST'); scroll_to('0.137000', 6)
    n = [n for n in nodes() if '0.137000' in n]; b = bounds_of(n[0]); p = ((b[0] + b[2]) // 2, (b[1] + b[3]) // 2)
    adb('shell', 'input', 'swipe', str(p[0]), str(p[1]), str(p[0]), str(p[1]), '900'); time.sleep(1.5)
    filled = '0.137' in html.unescape(x())
    fill_freq('0.139', 'ZEDIT')
    r = filled and '0.139000' in x() and '0.137000' not in x()
    tapk('Cancel', 1)
    return r, ''


def picker(btn):
    page('Frequencies'); scroll_to('Load ...')
    tapk(btn, 3)
    f = adb('shell', 'dumpsys', 'window').decode('utf8', 'replace')
    m = re.search(r'mCurrentFocus=.*', f).group(0)
    adb('shell', 'input', 'keyevent', '4'); time.sleep(2)
    return 'documentsui' in m or 'DocumentsActivity' in m, m[-60:]


def m37():
    a = picker('Load ...'); home(); c = picker('Merge ...'); home()
    tapk('Cancel', 1)
    return a[0] and c[0], a[1]


def m38():
    page('Frequencies')
    tp_ = scroll_to('Switch to Frequency Now')
    ns = [n for n in nodes() if 'Insert ...' in html.unescape(n)]
    tap_n(ns[-1]); w = html.unescape(x())
    t_ = re.findall(r' text="([^"]+)"', w)[:7]
    adb('shell', 'input', 'keyevent', '4'); time.sleep(1); tapk('Cancel', 1)
    return tp_ and 'Cancel' in w, t_


def m42():
    page('UI'); tapk('Font', 2) if 'text="Font"' in x() else scroll_to('Font')
    tapk('Font', 2)
    w = html.unescape(x()); shot(f'm42_{_run}')
    t_ = re.findall(r' text="([^"]+)"', w)[:7]
    adb('shell', 'input', 'keyevent', '4'); time.sleep(1); tapk('Cancel', 1)
    return 'Selawik' in w and 'Monospace' in w, t_


def m45():
    home(); top(); open_station(); scroll_to('About'); tapk('About', 2)
    w = html.unescape(x())
    return 'Easy JS8' in w and 'fork of JS8Android' in w, ''


row_list = [('M08U', lambda: typefield('General', 'Reply Message:', 'js8cfg_reply_message', 'HW Q')),
            ('M12U', lambda: typefield('General', 'Missing Frame Character Indicator:', 'js8cfg_mfi', 'XX')),
            ('M17U', lambda: typefield('General', 'Hide messages from these callsigns', 'js8cfg_rx_blocklist', 'K4BAD')),
            ('M10U', lambda: spin('General', 'Remove callsigns from call activity after:', 'js8cfg_callsign_aging')),
            ('M13U', lambda: spin('General', 'Heartbeat interval:', 'heartbeat_interval')),
            ('M23U', lambda: spin('Radio', 'Tx delay:', 'js8cfg_tx_delay')),
            ('M05U', m05), ('M26U', m26), ('M27U', m27), ('M19U', m19), ('M20U', m20), ('M21U', m21), ('M35U', m35), ('M36U', m36), ('M37U', m37), ('M38U', m38), ('M42U', m42), ('M45U', m45)]
up(js8cfg_rx_blocklist='', js8cfg_mfi=None, js8cfg_reply_message=None, js8cfg_tx_delay='0.2', heartbeat_interval='0')
for i, f in row_list:
    row(i, f)
adb('shell', 'settings', 'put', 'system', 'user_rotation', '0')
fresh(js8cfg_rx_blocklist='', js8cfg_mfi=None, js8cfg_reply_message=None, heartbeat_interval='0', js8cfg_tx_delay='0.2')
json.dump(out, open(f'ui9.{_run}.json', 'w'))
print('DONE', flush=True)
