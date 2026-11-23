"""W162-W172: Waterfall Controls > Display > Label decoded signals (Rob, 2026-11-22). wflabel.py <run>
Screenshots in shots/wl_*; the label rows are judged by eye on them. Spotting is off for the whole run."""
import sys as _s
_run = _s.argv[1]
_s.argv = ['ui1', _run, 'U', '']
exec(open('ui1.py', encoding='utf8').read().split('\nfor code in LAYS:')[0])
import sig
_s.stdout.reconfigure(encoding='utf8', errors='replace')
out = []
KEY = 'js8cfg_wf_decode_labels'
VAL = {'U': 'pc', 'P': 'pc', 'G': 'swipe', 'F': 'ft8call'}
FRAME = 'UeNbK+GGOo00'


def lay(code, **k):
    global L
    L = code
    stale = {n: None for n in re.findall(r'name="(js8cfg_up_split_[^"]*)"', getprefs())}
    with part(f'restart {code}', 20):
        setprefs(**{**BASE, 'landscape_layout': VAL[code], 'autoreply_enabled': False, 'js8cfg_autoreply_startup': False,
                    'js8cfg_transmit_directed': False, 'js8cfg_hb': False, 'js8cfg_hb_ack': False, 'tx_submode': 0,
                    'js8cfg_tx_watchdog': '0', 'js8cfg_wf_start': None, 'js8cfg_wf_fps': 4, 'js8cfg_filter_enabled': False,
                    'psk_reporter': False, **stale, **k})
        start()
        rotate(code)
        time.sleep(1)


ONLY = set(filter(None, os.environ.get('WL_ROWS', '').split(',')))


def row(i, est, fn):
    if ONLY and i not in ONLY:
        return
    with part(i, est, [i], out, lambda a, b, c: out.append((a, b, c))):
        try:
            r = fn()
            ok, note = r if isinstance(r, tuple) else (r, '')
        except Exception as e:
            ok, note = False, repr(e)[:100]
        print(i, 'PASS' if ok else 'FAIL', note, flush=True)
        out.append((i, bool(ok), str(note)[:120]))


def node(k):
    for n in re.findall(r'<node [^>]*>', x()):
        if '/' + k + '"' in n:
            return n
    return ''


def checked(k): return 'checked="true"' in node(k)


def wb():
    n = node('js8_waterfall_view')
    return tuple(map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n).groups())) if n else None


def wf_up():
    """Upright: scrolls the page so the waterfall's top is near y 457. The drag runs on the level meter (x 90),
    which takes no touches; a drag on the waterfall would move the offset."""
    if L != 'U':
        return reach('js8_waterfall_view')
    for _ in range(5):
        b = wb()
        if not b:
            adb('shell', 'input', 'swipe', '540', '2150', '540', '900', '400'); time.sleep(1.5)
            continue
        d = b[1] - 457
        if abs(d) < 60:
            return True
        sy = max(200, min(2200, b[1] + 415 if b[3] - b[1] > 200 else 2150))
        x_ = '90' if b[3] - b[1] > 200 and 200 < sy < b[3] else '540'
        adb('shell', 'input', 'swipe', x_, str(sy), x_, str(max(150, min(2250, sy - d))), '500'); time.sleep(1.5)
    return False


def display_page():
    """Upright: waterfall at the top, the Display page under it (Start on screen)."""
    wf_up()
    if not node('js8_wf_start'):
        tapk('js8_wf_tab_display', 1.2)


def display_tab():
    """Waterfall Controls > Display open, its Label decoded signals checkbox on screen."""
    if L == 'U':
        wf_up()
        if not node('js8_wf_start'):  # Display page not shown; a tap on the shown tab would close it
            tapk('js8_wf_tab_display', 1.2)
        for _ in range(3):
            if node('js8_wf_decode_labels'):
                return True
            adb('shell', 'input', 'swipe', '40', '2100', '40', '1300', '500'); time.sleep(1.5)
        return bool(node('js8_wf_decode_labels'))
    reach('js8_wf_tab_control')
    tapk('js8_wf_tab_display', 1.2)
    return reach('js8_wf_decode_labels')


def wf_shot(name):
    wf_up(); time.sleep(.5)
    shot(f'wl_{name}_{_run}')


def w162():
    lay('U', **{KEY: None})
    display_tab()
    there, on_ = bool(node('js8_wf_decode_labels')), checked('js8_wf_decode_labels')
    shot(f'wl_w162_box_{_run}')
    return there and not on_, f'checkbox shown {there}, checked {on_}'


def w163():
    lay('U', **{KEY: False})
    inj('K4TYP: VE3TST HELLO THERE', 1200); inj('W1AW: @ALLCALL CQ CQ', 2000)
    time.sleep(4); wf_shot('w163_off')
    return True, 'screenshot wl_w163_off: no labels expected'


TONES = {}


def w164():
    lay('U', **{KEY: True})
    for _ in range(3):
        TONES[0] = sig.tones(FRAME, 1, 0); time.sleep(3)
        if TONES[0]:
            break
    if not TONES.get(0):
        return False, 'no tones from the app'
    base = len(alltxt())
    sig.play([(TONES[0], 1500), (None, 0)], 'wl.pcm', 0)
    got = []
    for _ in range(40):
        time.sleep(1)
        got = [l for l in alltxt()[base:] if 'Transmitting' not in l and re.search(r'^\S+ \S+ +-?\d+ ', l)]
        if got:
            break
    time.sleep(1.5); wf_shot('w164_audio')
    time.sleep(6); wf_shot('w164_audio_later')
    return bool(got), (got or ['no decode'])[0][:90]


def w165():
    inj('K4TYP: VE3TST HELLO THERE', 900); inj('N1MM: SNR -10', 930); inj('W1AW: @ALLCALL CQ CQ', 2100)
    time.sleep(3); wf_shot('w165_inject')
    return True, 'screenshot wl_w165_inject: K4TYP: VE3... at 950 Hz, W1AW: @ALL... at 2150 Hz, N1MM: SNR... one line under K4TYP'


def ft8_play():
    """Plays the busy FT8 recording through DebugFt8Receiver; True once the engine logs an FT8 decode (the Pages
    layout's Waterfall page shows no Message Window to read)."""
    adb('logcat', '-c')
    w = 'E:/new-w3bguru-websites/easyft8/ft8af/app/src/main/cpp/ft8af_glue/testdata/ft8'
    f = sorted(os.path.join(w, n) for n in os.listdir(w) if n.endswith('.wav') and 'busy' in n)[0]
    adb('push', f, '/data/local/tmp/ft8.wav'); adb('shell', f'run-as {PKG} cp /data/local/tmp/ft8.wav files/ft8.wav')
    adb('shell', f'am broadcast -n {PKG}/com.js8call.example.debug.DebugFt8Receiver --es file ft8.wav')
    for _ in range(24):
        time.sleep(1)
        if b'JS8EngineService: FT8: ' in adb('logcat', '-d', '-s', 'JS8EngineService:I'):
            return True
    return False


def w166():
    lay('U', **{KEY: True, 'js8cfg_show_ft8': True})
    seenft8 = ft8_play()
    time.sleep(2); wf_shot('w166_ft8')
    return seenft8, f'FT8 lines shown {seenft8}; screenshot wl_w166_ft8: labels stacked, none touching'


def w167():
    inj('K4TYP: VE3TST HELLO THERE', 900)
    time.sleep(2); wf_shot('w167_before')
    display_page(); tapk('js8_wf_start_up', 1.5)
    wf_shot('w167_start_up')
    inj('W1AW: @ALLCALL CQ CQ', 1200); time.sleep(2)
    wf_shot('w167_new_label')
    display_page(); tapk('js8_wf_start_down', 1.5)
    wf_shot('w167_start_back')
    return True, 'screenshots wl_w167_*: start_up and start_back no labels, new_label W1AW only'


def w168():
    display_tab()
    tapk('js8_wf_decode_labels', 1.5)
    off = not checked('js8_wf_decode_labels')
    wf_shot('w168_unchecked')
    inj('N0NE: SHOULD NOT SHOW', 1500); time.sleep(3); wf_shot('w168_after_inject')
    return off, f'unchecked {off}; screenshots wl_w168_*: no labels expected'


def lay_row(code):
    lay(code, **{KEY: True, 'js8cfg_show_ft8': True})
    inj('K4TYP: VE3TST HELLO THERE', 900); inj('W1AW: @ALLCALL CQ CQ', 930); inj('N1MM: SNR -10', 960)
    time.sleep(3); wf_shot(f'{code}_layout')
    seenft8 = ft8_play()
    time.sleep(2); wf_shot(f'{code}_ft8')
    return seenft8, f'FT8 lines shown {seenft8}; screenshots wl_{code}_*: three stacked at 950 Hz, FT8 stacked'


prev_ft8 = 'name="js8cfg_show_ft8" value="true"' in getprefs()
row('W162', 30, w162)
row('W163', 30, w163)
row('W164', 75, w164)
row('W165', 12, w165)
row('W166', 55, w166)
row('W167', 25, w167)
row('W168', 20, w168)
row('W169', 65, lambda: lay_row('P'))
row('W170', 70, lambda: lay_row('G'))
row('W171', 65, lambda: lay_row('F'))
with part('restore', 25):
    setprefs(**{KEY: None, 'js8cfg_show_ft8': prev_ft8, 'psk_reporter': True})
    start(); rotate('U')
json.dump(out, open(f'wflabel.{_run}.json', 'w'))
print('RESULTS', out, flush=True)
print('DONE', flush=True)
