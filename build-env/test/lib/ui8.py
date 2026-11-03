"""Upright rows: H35, I04, K, L09, L15. ui8.py <run> [ID,ID]"""
import sys as _s
_a = list(_s.argv)
_s.argv = _a
exec(open('ui7.py', encoding='utf8').read().split("row_list = [")[0].replace('"""Upright rows never tested: H, I, J. ui7.py <run> [ID,ID]"""', ''))
M = 'VE3TST'


def msg(frm, pl):
    inj(f'{frm}: {M} MSG {pl} {sum16(pl)}', 900, 3, -5); time.sleep(2.5)


def gesture(kind, px, py):
    o = bounds_of(re.findall(r'<node [^>]*>', x())[0])  # window pixels: sideways the window starts right of the camera cutout (x 136)
    adb('shell', f'am broadcast -n {PKG}/com.js8call.example.debug.DebugGestureReceiver --es gesture {kind} --ef x {px - o[0]} --ef y {py - o[1]}')
    time.sleep(1.2)


def nodes(s):
    return [n for n in re.findall(r'<node [^>]*>', x()) if s in n]


def drag(down, x_='540'):
    """One page drag inside the screen: upright 800 to 1500, sideways 350 to 850 (the screen is 1080 high)."""
    a, b = ('800', '1500') if L == 'U' else ('350', '850')
    x_ = x_ if L == 'U' else '1200'
    adb('shell', 'input', 'swipe', x_, a if down else b, x_, b if down else a, '300')


def inbox_rows():
    if L in ('P', 'F'):  # the inbox is its own screen there: View > Show Message Inbox... (Js8InboxFragment)
        for _ in range(5):
            if find(x(), 'js8_inbox_table'):
                return True
            if not (tp('js8_menubar_view', 1.2) and tapk('Show Message Inbox...', 2)):
                home(); time.sleep(3)  # the New message notification at the top takes the tap until it goes
        return False
    if L == 'G':
        return reach('js8_inbox_table')
    for _ in range(9):
        xml = x()
        b = bounds('js8_inbox_table')
        if b and b[3] - b[1] > 250 and find(xml, 'js8_inbox_reply'):
            return True
        p = find(xml, 'js8_wf_tab_timing')
        if p:
            p = (1000, p[1])
        else:
            p = find(xml, 'js8_btn_cat')
            p = (90, min(p[1] + 180, 2100)) if p else (find(xml, 'js8_btn_hb') or find(xml, 'js8_typing_box') or (90, 2000))
        adb('shell', 'input', 'swipe', str(p[0]), str(p[1]), str(p[0]), '150', '400'); time.sleep(1)
    return False


def h35():
    inj('K4WRD: @ALLCALL CQ CQ CQ FN31', 900, 3, -5); time.sleep(.5)
    top(); time.sleep(1)
    time.sleep(2)
    n = nodes('js8_message_window')
    b = bounds_of(n[0])
    gesture('doubletap', b[0] + 300, b[1] + 40)
    p = ((b[0] + b[2]) // 2, (b[1] + b[3]) // 2)
    adb('shell', 'input', 'swipe', str(b[0] + 300), str(b[1] + 40), str(b[0] + 300), str(b[1] + 40), '900'); time.sleep(1.5)
    copy = [m for m in re.findall(r'<node [^>]*>', x()) if 'text="Copy"' in m]
    return bool(copy) and 'enabled="true"' in copy[0], 'Copy enabled after double tap' if copy else 'no menu'


def i04():
    for f, c_ in ((1000, 'K4DBL'),):
        inj(f'{c_}: @ALLCALL CQ CQ CQ FN31', f, 3, -5)
    time.sleep(2.5); top()
    n = nodes('K4DBL') or nodes('CQ CQ')
    b = bounds_of(n[0])
    a = pv('js8cfg_tx_offset')
    gesture('doubletap', (b[0] + b[2]) // 2, (b[1] + b[3]) // 2)
    time.sleep(1); c = pv('js8cfg_tx_offset')
    return c and float(c) == 1000 and float(a) != 1000, f'{a} to {c}'


def date_rows():
    """Inbox rows top to bottom as (time text, bounds); the From column is past the screen edge."""
    r = []
    for n in re.findall(r'<node [^>]*>', x()):
        m = re.search(r' text="(\w{3} \w{3} +\d+ \d\d:\d\d:\d\d)', n)
        if m:
            r.append((m.group(1), bounds_of(n)))
    return sorted(r, key=lambda z: z[1][1])


def k01():
    msg('K4OLD', 'FIRST ONE'); time.sleep(1.5); msg('K4NEW', 'SECOND ONE')
    inbox_rows(); r = date_rows()
    return len(r) >= 2 and r[0][0] > r[1][0], [z[0][-8:] for z in r]


def k03():
    inbox_rows(); r = date_rows()
    b = r[0][1]; adb('shell', 'input', 'tap', str((b[0] + b[2]) // 2), str((b[1] + b[3]) // 2)); time.sleep(1.2)
    shown = 'SECOND ONE' in html.unescape(x())
    ok = tapk('js8_inbox_reply', 1.5)
    top(); reach('js8_typing_box'); bx = boxtext(); clearbox()
    return shown and ok and 'K4NEW' in bx, bx[:40]


def k04():
    top()
    n = [m for m in nodes('K4OLD') if L == 'U' or 'text="K4OLD"' in m]  # sideways Band Activity shows K4OLD too: the Callsigns row
    if not n:
        reach('js8_call_activity'); n = nodes('K4OLD')
    b = bounds_of(n[0]); p = ((b[0] + b[2]) // 2, (b[1] + b[3]) // 2)
    adb('shell', 'input', 'swipe', str(p[0]), str(p[1]), str(p[0]), str(p[1]), '900'); time.sleep(1.5)
    ok = has('Show Message Inbox...') and tapk('Show Message Inbox...', 2)
    time.sleep(1); inbox_rows()
    w = html.unescape(x()); shot(f'k04_{_run}')
    return ok and 'K4OLD' in w and 'K4NEW' not in w, 'list shows only K4OLD'


def k02():
    home(); inbox_rows(); r = date_rows()
    if len(r) < 2:
        return False, 'rows missing'
    b = r[0][1]; p = ((b[0] + b[2]) // 2, (b[1] + b[3]) // 2); bo = r[1][1]
    adb('shell', 'input', 'swipe', str(p[0]), str(p[1]), str(p[0]), str(p[1]), '900'); time.sleep(1.5)
    tapk('Select More', 1.2)
    adb('shell', 'input', 'tap', str((bo[0] + bo[2]) // 2), str((bo[1] + bo[3]) // 2)); time.sleep(1)
    adb('shell', 'input', 'swipe', str(p[0]), str(p[1]), str(p[0]), str(p[1]), '900'); time.sleep(1.5)
    tapk('Delete', 2); time.sleep(1)
    return len(date_rows()) == 0, str(len(date_rows()))


def logpage():
    inj(f'K4WRD: {M} HELLO WORDONE', 900, 3, -5); time.sleep(2.5)
    top()
    (tapk if L == 'U' else tp)('js8_btn_log', 2.5)  # Pages: LOG is on the Header tab
    for _ in range(6):
        if has_box():
            break
        drag(False); time.sleep(.8)


def has_box():
    return any('WORDONE' in n for n in re.findall(r'<node [^>]*>', x()))


def word_box():
    return bounds_of([n for n in re.findall(r'<node [^>]*>', x()) if 'WORDONE' in n][0])


def field_after(label):
    """The EditText that follows a label in screen order."""
    ns = re.findall(r'<node [^>]*>', x())
    for i, n in enumerate(ns):
        if f'text="{label}' in n:
            for m in ns[i + 1:]:
                if 'EditText' in m:
                    return m
    return None


def to_field(label):
    for ys in (('800', '1500'), ('1500', '800')):
        for _ in range(7):
            m = field_after(label)
            if m:
                return bounds_of(m)
            drag(ys[0] == '800'); time.sleep(.7)


def kword(label, want):
    logpage()
    b = to_field(label)
    adb('shell', 'input', 'tap', str((b[0] + b[2]) // 2), str((b[1] + b[3]) // 2)); time.sleep(1)
    if 'mInputShown=true' in adb('shell', 'dumpsys', 'input_method').decode('utf8', 'replace'):
        adb('shell', 'input', 'keyevent', '4'); time.sleep(1)  # closes the keyboard only, the field keeps the focus
    for _ in range(6):
        if has_box():
            break
        drag(True); time.sleep(.8)
    if not has_box():
        for _ in range(14):
            drag(False); time.sleep(.7)
            if has_box():
                break
    if L != 'U':  # sideways the screen edge cuts the box: the page end shows it whole (8 lines of 57 px and 24 px padding)
        for _ in range(6):
            b_ = word_box()
            if b_[3] - b_[1] >= 470:
                break
            drag(False); time.sleep(.7)
    w = word_box()
    # the word is near the end of the line
    t_ = [n for n in re.findall(r'<node [^>]*>', x()) if 'WORDONE' in n][0]
    if L == 'U':
        gesture('doubletap', w[0] + 620, w[1] + 40)
    else:  # sideways a message is one line: WORDONE (characters 39 to 45) on the bottom line; measured 25 px a character on a 57 px line
        gesture('doubletap', w[0] + 12 + int(42.5 * 25), w[3] - 12 - 28)
        shot(f'kword_{label}_{L}_{_run}')
    time.sleep(1)
    m = field_after(label) or ''
    if not m:
        to_field(label); m = field_after(label) or ''
    val = html.unescape(re.search(r' text="([^"]*)"', m).group(1)) if m else ''
    adb('shell', 'input', 'keyevent', '111'); time.sleep(.8)
    if 'Add to Log' in x():
        tapk('Cancel', 1)
    return want(val), val


def k12():
    tp('js8_btn_settings', 2)
    for _ in range(10):
        if 'Export Log...' in html.unescape(x()):
            break
        adb('shell', 'input', 'swipe', '600', '900' if L == 'U' else '780', '600', '300', '350'); time.sleep(.7)  # sideways 900 is on Save
    tapk('Export Log...', 2)
    def st():
        return [('checked="true"' in n) for n in re.findall(r'<node [^>]*>', x()) if 'CheckBox' in n]
    a = st()
    ns = [n for n in re.findall(r'<node [^>]*>', x()) if 'CheckBox' in n]
    for n in ns:
        b = bounds_of(n); adb('shell', 'input', 'tap', str((b[0] + b[2]) // 2), str((b[1] + b[3]) // 2)); time.sleep(.5)
    c = st()
    return len(a) == 3 and a == [True, False, False] and c == [False, True, True], f'{a} to {c}'


def settings_to(word):
    tp('js8_btn_settings', 2)
    for _ in range(10):
        if word in html.unescape(x()):
            return True
        adb('shell', 'input', 'swipe', '600', '900' if L == 'U' else '780', '600', '300', '350'); time.sleep(.7)  # sideways 900 is on Save
    return False


def l09():
    settings_to('dB')
    ns = [n for n in re.findall(r'<node [^>]*>', x()) if 'SeekBar' in n]
    b = bounds_of(ns[0]); a = pv('js8cfg_out_attenuation') or ''
    k = re.findall(r'name="([^"]*atten[^"]*)"', getprefs(), re.I)
    y = (b[1] + b[3]) // 2
    adb('shell', 'input', 'tap', str(b[0] + (b[2] - b[0]) // 3), str(y)); time.sleep(1)
    txt = re.findall(r' text="(-?[\d.]+ dB)"', x())
    return bool(txt) and txt[0] != '0.0 dB' and txt[0] != '-0.0 dB', str(txt)


def l15():
    settings_to('Show Waterfall Controls')
    ok = True
    n = [n for n in re.findall(r'<node [^>]*>', x()) if 'Show Waterfall Controls' in n]
    return bool(n), 'list present'


row_list = [('H35U', h35), ('I04U', i04), ('K01U', k01), ('K03U', k03), ('K04U', k04), ('K02U', k02),
            ('K09U', lambda: kword('Name', lambda v: 'K4WRD' in v or 'WORDONE' in v)),
            ('K10U', lambda: kword('Comments', lambda v: 'K4WRD' in v or 'WORDONE' in v)),
            ('K11U', lambda: kword('Start', lambda v: bool(v) and 'K4WRD' not in v and 'WORDONE' not in v)),
            ('K12U', k12), ('L09U', l09), ('L15U', l15)]
stop(); adb('shell', f'run-as {PKG} sh -c "rm -f databases/js8_messages.db*"')
up()
for i, f in row_list:
    row(i, f)
adb('shell', 'settings', 'put', 'system', 'user_rotation', '0')
json.dump(out, open(f'ui8.{_run}.json', 'w'))
print('DONE', flush=True)
