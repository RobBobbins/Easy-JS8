"""W06, W10 to W18: screens in one layout a run. wui.py <run> <layout U, P, G or F> [ID,ID]. fakes.py must be running (rigctld on 4599).
Rows by layout: U W06 W12 W13 W18; P W11 W16 W17; G W14 W15; F W10 W14 W17. Pictures in shots/w*_<layout>_<run>*.png."""
import sys as _s
import io
_lay = _s.argv[2]
_s.argv = [_s.argv[0], _s.argv[1]] + _s.argv[3:]
_src = open('ui7.py', encoding='utf8').read().split("row_list = [")[0].replace('"""Upright rows never tested: H, I, J. ui7.py <run> [ID,ID]"""', '')
_src = _src.replace("_s.argv = ['ui1', _run, 'U', '']", f"_s.argv = ['ui1', _run, '{_lay}', '']").replace("rotate('U')", f"rotate('{_lay}')")
exec(_src)
import numpy as np
from PIL import Image
L = _lay
NAME = {'U': 'pc', 'P': 'pc', 'G': 'swipe', 'F': 'ft8call'}[_lay]
_up = up
FAKERIG = dict(rig_control_enabled=True, rig_type='network', rig_network_kind='rigctl', rigctld_host='10.0.2.2', rigctld_port='4599',
               js8cfg_cat_poll_interval='1', js8cfg_ptt_method='vox', js8cfg_split='none')
NORIG = dict(rig_control_enabled=False, rig_type='none', js8cfg_ptt_method='vox', js8cfg_split='none')


def up(**k):
    _up(**{'landscape_layout': NAME, **NORIG, **k})


def grab():
    return np.asarray(Image.open(io.BytesIO(adb('exec-out', 'screencap', '-p'))).convert('RGB')).astype(int)


def node(k, xml=None):
    for n in re.findall(r'<node [^>]*>', xml or x()):
        if '/' + k + '"' in n:
            return n
    return ''


def ntext(k, xml=None):
    m = re.search(r' text="([^"]*)"', node(k, xml))
    return html.unescape(m.group(1)) if m else None


def dialog():
    """Text of an open alert box, or None."""
    xml = x()
    if 'android:id/message' not in xml and 'android:id/alertTitle' not in xml:
        return None
    return ' / '.join(html.unescape(t) for t in re.findall(r'resource-id="android:id/(?:alertTitle|message)"[^>]*?text="([^"]*)"', xml) +
                      re.findall(r' text="([^"]*)"[^>]*resource-id="android:id/(?:alertTitle|message)"', xml))


def cat_state(**k):
    """CAT button: colour left of its text, enabled, selected; and any alert box that opened."""
    up(**k)
    time.sleep(8)
    box = dialog()
    shot(f'w06_{L}_{_run}_{k.get("rigctld_port", "none")}')
    if box:
        home()
    reach('js8_btn_cat'); time.sleep(1)
    n = node('js8_btn_cat'); b = bounds('js8_btn_cat'); a = grab()
    c = tuple(int(v) for v in a[(b[1] + b[3]) // 2, b[0] + 10])
    return c, 'enabled="true"' in n, 'selected="true"' in n, box


def w06():
    green = lambda c: c[1] > 150 and c[0] < 120 and c[2] < 120
    red = lambda c: c[0] > 150 and c[1] < 100 and c[2] < 100
    none = cat_state(**NORIG)
    good = cat_state(**FAKERIG)
    bad = cat_state(**{**FAKERIG, 'rigctld_port': '4597'})
    ok = green(none[0]) and green(good[0]) and not good[1] and red(bad[0]) and bad[1]
    return ok, f'no rig {none[:3]}, rig {good[:3]}, rig error {bad[:3]} box {bad[3]}'


def w10():
    """FT8Call header: HB timer on, Next Beacon read twice 4 s apart. TX off, so nothing goes out."""
    up(js8cfg_hb=True, js8cfg_hb_ack=False, heartbeat_interval='10', js8cfg_transmit_off=True)
    t0 = ntext('js8_next_beacon')
    tp('js8_btn_hb', 1.5)
    t1 = ntext('js8_next_beacon'); time.sleep(4); t2 = ntext('js8_next_beacon')
    shot(f'w10_{L}_{_run}')
    tp('js8_btn_hb', 1.5)
    t3 = ntext('js8_next_beacon')
    n = [int(m.group(1)) if (m := re.search(r'(\d+) s', t or '')) else None for t in (t1, t2)]
    ok = not t0 and None not in n and 2 <= n[0] - n[1] <= 6 and not t3
    return ok, f'before {t0!r}, on {t1!r} then {t2!r}, off {t3!r}'


def offset_drag(a, b_):
    """Drags the header Offset slider from a to b_ of its width."""
    b = bounds('js8_offset_slider')
    y = (b[1] + b[3]) // 2
    v0 = ntext('js8_offset_value')
    adb('shell', 'input', 'swipe', str(b[0] + int((b[2] - b[0]) * a)), str(y), str(b[0] + int((b[2] - b[0]) * b_)), str(y), '400')
    time.sleep(1.2)
    return v0, ntext('js8_offset_value')


def w11():
    """A message sent waits for its slot, then goes out: the header Offset slider must not move. After Halt it moves.
    (TX off greys Send, so the wait is the slot wait with TX on; no radio, the tones go to the emulator's audio out.)"""
    up(js8cfg_tx_qsy=False)
    typed('HELLO QUEUE THIS MESSAGE TAKES SEVERAL FRAMES'); tapk('js8_btn_send', .3)
    locked = offset_drag(.25, .75)
    shot(f'w11_{L}_{_run}')
    send = ntext('js8_btn_send')
    tapk('js8_btn_halt', 2)
    free = offset_drag(.75, .35)
    return locked[0] == locked[1] and free[0] != free[1], f'Send reads {send!r}; sending {locked}, after Halt {free}'


def rxtx():
    reach('js8_status_state'); time.sleep(1.5)
    t = ntext('js8_status_state')
    return t in ('Receiving', 'Decoding'), 'js8cfg_tx_enabled" value="true"' in getprefs(), t


def w12():
    """Both startup boxes unticked, then both ticked: RX / TX flipped by hand, Back closes the app, the app opened again."""
    notes, ok = [], True
    for off in (False, True):
        up(js8cfg_monitor_off=off, js8cfg_transmit_off=off); time.sleep(4)
        s1 = rxtx()
        tp('js8_btn_rx', 2); tp('js8_btn_tx', 2)
        s2 = rxtx()
        home(); adb('shell', 'input', 'keyevent', '4'); time.sleep(3)
        alive = bool(adb('shell', 'pidof', PKG).strip())
        adb('shell', 'am', 'start', '-n', f'{PKG}/com.js8call.example.MainActivity'); time.sleep(6); rotate(L); time.sleep(2)
        s3 = rxtx()
        good = s1[:2] == (not off, not off) and s3[:2] == (not off, not off)
        ok = ok and good
        notes.append(f"{'ticked' if off else 'unticked'}: start {s1[:2]}, by hand {s2[:2]}, process left {alive}, reopened {s3}")
    return ok, '; '.join(notes)


def w13():
    """CQ heard with the CQ notification on: a new notification player starts in Android's audio log."""
    up(js8cfg_notifications=True, js8cfg_notify_cq='true', js8cfg_notify_call_new='false', js8cfg_notify_call_old='false',
       js8cfg_notify_hb='false', js8cfg_notify_directed='false', js8cfg_notify_ack='false', js8cfg_notify_inbox='false')
    time.sleep(3)
    before = set(re.findall(r'piid:(\d+)', adb('shell', 'dumpsys', 'audio').decode('utf8', 'replace')))
    clearlog(); inj('K4NSA: @ALLCALL CQ CQ FN03', 1200, 3, -5)
    n = seen(r'Notify: \w+', 10)
    time.sleep(4)
    d = adb('shell', 'dumpsys', 'audio').decode('utf8', 'replace')
    new = sorted(set(p for p in re.findall(r'piid:(\d+) new AudioAttributes:AudioAttributes: usage=USAGE_NOTIFICATION', d) if p not in before))
    played = [p for p in new if re.search(rf'piid:{p} event:started', d)]
    return n == ['Notify: cq'] and bool(played), f'{n}, new notification players {new}, started {played}'


FONTS = dict(js8cfg_ui_table_font='15', js8cfg_ui_rx_font='12', js8cfg_ui_tx_font='18', js8cfg_ui_compose_font='15')


def fonts_shot(name, **k):
    up(**k)
    if L == 'G':
        tapk('js8_tab_1', 1.5)
    inj('K4FNA: @ALLCALL CQ CQ FN03', 800, 3, -7); inj('K4FNB: VE3TST HELLO FONTS', 1300, 3, -9); time.sleep(1)
    typed('FONT TX'); tapk('js8_btn_send', 3)
    for _ in range(40):
        if ntext('js8_btn_send') in ('Send', None) or (ntext('js8_btn_send') or '').startswith('Send ('):
            break
        time.sleep(1)
    time.sleep(2); typed('COMPOSE TEXT'); time.sleep(1)
    shot(name)


def w14():
    """Table 15, Received 12, Transmitted 18, Composition 15 against the 10 pt defaults: two pictures, checked by eye."""
    fonts_shot(f'w14_{L}_{_run}_fonts', **FONTS)
    fonts_shot(f'w14_{L}_{_run}_default', **{k: None for k in FONTS})
    return True, f'see shots/w14_{L}_{_run}_fonts.png and _default.png'


def w15():
    up(); tapk('js8_tab_4', 2)
    w = html.unescape(x()); shot(f'w15_{L}_{_run}')
    return '← Back' in w, 'Station screen ' + ('has' if '← Back' in w else 'has no') + ' ← Back'


def row_of(xml, word):
    """Left x and top y of the view whose text is word (a trailing colon ignored)."""
    for n in re.findall(r'<node [^>]*>', xml):
        m = re.search(r' text="([^"]*)"', n)
        if m and html.unescape(m.group(1)).rstrip(':') == word:
            return bounds_of(n)[:2]


def page_shot(page, name):
    open_station(); tapk(page, 1.5)
    time.sleep(1); w = html.unescape(x()); shot(name)
    texts_ = [html.unescape(t) for t in re.findall(r' text="([^"]+)"', w)]
    return w, texts_


def w16():
    """Station > Radio sideways with a network rig picked: Rig, CAT Control (Network Server), PTT Method, Mode left to right on one row."""
    up(**FAKERIG)
    w, t_ = page_shot('Radio', f'w16_{L}_{_run}')
    at = {k: row_of(w, k) for k in ('Rig', 'Network Server', 'PTT Method', 'Mode')}
    tapk('Cancel', 1); home()
    ok = None not in at.values() and max(v[1] for v in at.values()) - min(v[1] for v in at.values()) < 100 and         [v[0] for v in at.values()] == sorted(v[0] for v in at.values())
    return ok, f'left, top of Rig, the CAT Control box, PTT Method, Mode: {at}'


def w17():
    """Station > Audio sideways: picture, and whether the page scrolls (its last label below the screen)."""
    up()
    w, t_ = page_shot('Audio', f'w17_{L}_{_run}')
    tapk('Cancel', 1); home()
    return 'Audio' in t_, f'texts {t_[:14]}'


def w18():
    """Upright Station page list: picture, every page name, and whether Layout is listed (sideways only)."""
    up()
    open_station(); time.sleep(1)
    seen_ = set(); w = ''
    for i in range(4):
        w = html.unescape(x()); seen_ |= set(html.unescape(t) for t in re.findall(r' text="([^"]+)"', w))
        if i == 0:
            shot(f'w18_{L}_{_run}')
        adb('shell', 'input', 'swipe', '540', '1800', '540', '900', '400'); time.sleep(1)
    want = ['General', 'Radio', 'Audio', 'Reporting', 'Frequencies', 'Saved Messages', 'Notifications', 'UI', 'Diagnostics', 'Phone', 'About']
    miss = [p for p in want if p not in seen_]
    home()
    return not miss and 'Layout' not in seen_, f'missing {miss}, Layout listed {"Layout" in seen_}'


ROWS = {'U': [('W06', w06), ('W12', w12), ('W13', w13), ('W18', w18)], 'P': [('W11', w11), ('W16', w16), ('W17', w17)],
        'G': [('W14', w14), ('W15', w15)], 'F': [('W10', w10), ('W14', w14), ('W17', w17)]}[L]
if only:
    only = [o if o.endswith(L) else o + L for o in only]
for i, f in ROWS:
    row(i + L, f)
up()
adb('shell', 'settings', 'put', 'system', 'user_rotation', '0')
json.dump(out, open(f'wui.{_run}.{L}.json', 'w'))
print('DONE', flush=True)
