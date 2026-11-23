"""W148-W161: "IS TYPING..." (Settings > General > Behavior, Easy JS8 only, Rob 2026-11-22). With a callsign
selected and text typed in the Typing Box, one undirected data frame "IS TYPING..." goes at the first key; with
Every 30 / 45 / 60 seconds again that long after the last one went out, only if a key was pressed since. Text the
app puts in the box does not count; pastes and word suggestions do. Send, an empty box, Deselect, Halt and TX off
stop it. Read from ALL.TXT (one transmit line per frame). typenote.py <run> [ID,ID]"""
from onair2 import *
from datetime import datetime
assert_avd()
run = int(sys.argv[1])
only = sys.argv[2].split(',') if len(sys.argv) > 2 else None
res = []
CALL = 'K4TYP'
KEY = 'js8cfg_typing_notice'
NOTICE = re.compile(r'JS8:\s+IS TYPING\.\.\.\s*$')


def mark(id, ok, note=''):
    print(id, 'PASS' if ok else 'FAIL', note, flush=True); res.append((id, ok, note))


def want(id):
    return not only or id in only


def txl(base):
    return [l for l in alltxt()[base:] if 'Transmitting' in l]


def notices(base):
    return [l for l in txl(base) if 'IS TYPING' in l]


def one_frame(ls):
    """Each notice line holds the whole text: the frame was not split."""
    return all(NOTICE.search(l) for l in ls)


def slot(l):
    return datetime.strptime(l[:19], '%Y-%m-%d %H:%M:%S')


def gaps(ls):
    return [int((slot(b) - slot(a)).total_seconds()) for a, b in zip(ls, ls[1:])]


def selected():
    return next((r.get('value') for r in api('RX.GET_CALL_SELECTED', wait=1) if r.get('type') == 'RX.CALL_SELECTED'), None)


def select_call():
    inj(f'{CALL}: VE3TST HELLO', 1500); time.sleep(4)
    for _ in range(3):
        p = show_call(CALL)
        if p:
            adb('shell', 'input', 'tap', str(p[0]), str(p[1])); time.sleep(1.5)
            if selected() == CALL:
                return True
    return False


def box_text():
    n = next((m for m in re.findall(r'<node [^>]*>', dump()) if 'js8_typing_box' in m), '')
    m = re.search(r' text="([^"]*)"', n)
    v = html.unescape(m.group(1)) if m else ''
    return '' if v.startswith('TYPE YOUR OUTGOING') else v


def focus():
    tapk('js8_typing_box', .8)


def keys(seconds, every=4, ch='A'):
    """A key every [every] s for [seconds] s (the box keeps the focus)."""
    end = time.time() + seconds
    while time.time() < end:
        text(ch); time.sleep(every)


def wait_notice(base, n=1, limit=40):
    end = time.time() + limit
    while time.time() < end:
        if len(notices(base)) >= n:
            return True
        time.sleep(1)
    return False


def kb_away():
    adb('shell', 'input', 'keyevent', '111'); time.sleep(.8)


def setup(mode, submode=0, select=True, **k):
    # spotting off: debug_spot_host is not set on S22-Fresh (2026-11-22), test decodes would reach JS8NET
    fresh(tx_submode=submode, psk_reporter=False, **{KEY: mode}, **k)
    ok = select_call() if select else True
    base = len(alltxt())
    return ok, base


if want('W148'):  # Off (no setting saved): nothing is sent
    with part('W148', 75, ('W148',), res, mark):
        ok, base = setup(None)
        focus(); keys(24)
        time.sleep(10)
        n = notices(base)
        mark('W148', ok and not n, f'call selected {ok}; notices {len(n)}')
        kb_away()

if want('W149'):  # Once, NORMAL: one notice, one frame, the box keeps the typed text
    with part('W149', 95, ('W149',), res, mark):
        ok, base = setup('once', 0)
        focus(); keys(50)
        n = notices(base)
        bx = box_text()
        mark('W149', ok and len(n) == 1 and one_frame(n) and bx.startswith('A') and 'TYPING' not in bx,
             f'call selected {ok}; notices {len(n)} {[l[11:19] for l in n]}; one frame {one_frame(n)}; box "{bx}"')
        kb_away()

if want('W150'):  # Every 30 seconds, NORMAL (15 s slots): 30 s apart
    with part('W150', 120, ('W150',), res, mark):
        ok, base = setup('30', 0)
        focus(); keys(80)
        n = notices(base)
        g = gaps(n)
        mark('W150', ok and len(n) >= 2 and all(x == 30 for x in g) and one_frame(n),
             f'call selected {ok}; notices {len(n)} at {[l[11:19] for l in n]}; gaps {g}; one frame {one_frame(n)}')
        kb_away()

if want('W151'):  # Every 45 seconds, TURBO (6 s slots): the first slot at or after 45 s is 48 s
    with part('W151', 140, ('W151',), res, mark):
        ok, base = setup('45', 2)
        focus(); keys(105, every=3)
        n = notices(base)
        g = gaps(n)
        mark('W151', ok and len(n) >= 2 and all(x == 48 for x in g) and one_frame(n),
             f'call selected {ok}; notices {len(n)} at {[l[11:19] for l in n]}; gaps {g}; one frame {one_frame(n)}')
        kb_away()

if want('W152'):  # Every 30: typing stops, the notices stop
    with part('W152', 110, ('W152',), res, mark):
        ok, base = setup('30', 0)
        focus(); text('ABC')
        time.sleep(75)
        n = notices(base)
        mark('W152', ok and len(n) == 1, f'call selected {ok}; notices {len(n)} {[l[11:19] for l in n]}')
        kb_away()

if want('W153'):  # no callsign selected: nothing
    with part('W153', 70, ('W153',), res, mark):
        ok, base = setup('30', 0, select=False)
        sel = selected()
        focus(); keys(35)
        time.sleep(5)
        n = notices(base)
        mark('W153', not sel and not n, f'selected {sel}; notices {len(n)}')
        kb_away()

if want('W154'):  # text the app puts in (API TX.SET_TEXT, as the menus and buttons): nothing
    with part('W154', 75, ('W154',), res, mark):
        ok, base = setup('30', 0)
        api('TX.SET_TEXT', 'HELLO THERE', wait=1)
        time.sleep(35)
        n = notices(base)
        bx = box_text()
        mark('W154', ok and bx == 'HELLO THERE' and not n, f'call selected {ok}; box "{bx}"; notices {len(n)}')

if want('W155'):  # Send: not greyed by the notice, the reply goes after it, no more notices, AGN? sends the reply
    with part('W155', 150, ('W155',), res, mark):
        ok, base = setup('30', 0)
        focus(); text('REPLY%sONE')
        went = wait_notice(base, 1, 30)
        kb_away()
        x = dump()
        send_on = 'enabled="true"' in next((m for m in re.findall(r'<node [^>]*>', x) if 'js8_btn_send' in m), '')
        tapk('js8_btn_send', 1)
        time.sleep(40)
        t1 = txl(base)
        reply = [l for l in t1 if 'REPLY ONE' in l]
        n = notices(base)
        base2 = len(alltxt())
        inj(f'{CALL}: VE3TST AGN?', 1500)
        time.sleep(35)
        agn = [l.split('JS8:')[-1].strip() for l in txl(base2)]
        mark('W155', ok and went and send_on and reply and len(n) == 1 and agn and not any('TYPING' in a for a in agn) and any('REPLY' in a for a in agn),
             f'call selected {ok}; notice went {went}; Send enabled during it {send_on}; reply sent {bool(reply)}; notices {len(n)}; AGN? answer {agn}')

if want('W156'):  # the box emptied by hand: no more notices
    with part('W156', 100, ('W156',), res, mark):
        ok, base = setup('30', 0)
        focus(); text('ABC')
        went = wait_notice(base, 1, 30)
        adb('shell', 'input', 'keycombination', '113', '29'); time.sleep(.5)  # Ctrl+A
        adb('shell', 'input', 'keyevent', '67'); time.sleep(1)  # Delete
        bx = box_text()
        time.sleep(45)
        n = notices(base)
        mark('W156', ok and went and bx == '' and len(n) == 1, f'call selected {ok}; notice went {went}; box "{bx}"; notices {len(n)}')
        kb_away()

if want('W157'):  # Deselect: no more notices while typing goes on
    with part('W157', 115, ('W157',), res, mark):
        ok, base = setup('30', 0)
        focus(); text('ABC')
        went = wait_notice(base, 1, 30)
        kb_away()
        tapk('js8_btn_deselect', 1)
        sel = selected()
        focus(); keys(45)
        n = notices(base)
        mark('W157', ok and went and not sel and len(n) == 1, f'call selected {ok}; notice went {went}; selected after Deselect {sel}; notices {len(n)}')
        kb_away()

if want('W158'):  # a paste counts
    with part('W158', 80, ('W158',), res, mark):
        ok, base = setup('once', 0)
        api('TX.SET_TEXT', 'PASTED WORDS', wait=1)
        focus()
        adb('shell', 'input', 'keycombination', '113', '29'); time.sleep(.5)  # Ctrl+A
        adb('shell', 'input', 'keycombination', '113', '31'); time.sleep(.5)  # Ctrl+C
        adb('shell', 'input', 'keyevent', '67'); time.sleep(1)  # Delete: the box is empty
        empty = box_text() == ''
        quiet = not notices(base)
        adb('shell', 'input', 'keycombination', '113', '50'); time.sleep(1)  # Ctrl+V
        bx = box_text()
        went = wait_notice(base, 1, 30)
        mark('W158', ok and empty and quiet and bx == 'PASTED WORDS' and went,
             f'call selected {ok}; emptied {empty}; nothing before the paste {quiet}; box "{bx}"; notice after the paste {went}')
        kb_away()

if want('W159'):  # a word suggestion (press and hold the Typing Box) counts
    with part('W159', 80, ('W159',), res, mark):
        ok, base = setup('once', 0, js8cfg_spellcheck=True)
        api('TX.SET_TEXT', 'HELO', wait=1)
        time.sleep(1)
        bn = next((m for m in re.findall(r'<node [^>]*>', dump()) if 'js8_typing_box' in m), '')
        b = list(map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', bn).groups())) if bn else None
        quiet = not notices(base)
        picked = ''
        if b:
            x0, y0 = b[0] + 40, b[1] + 40
            adb('shell', 'input', 'swipe', str(x0), str(y0), str(x0), str(y0), '900'); time.sleep(1.5)
            std = {'Cut', 'Copy', 'Paste', 'Clear', 'Clear All Lists', 'Restore Previous Message', 'No Suggestions'}
            for n_ in re.findall(r'<node [^>]*>', dump()):
                if 'android:id/title' in n_:
                    t_ = html.unescape(re.search(r' text="([^"]*)"', n_).group(1))
                    if t_ and t_ not in std and not t_.startswith(('Saved', 'Directed', 'Relay', 'Save Current')):
                        r_ = list(map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n_).groups()))
                        adb('shell', 'input', 'tap', str((r_[0] + r_[2]) // 2), str((r_[1] + r_[3]) // 2)); time.sleep(1.5)
                        picked = t_
                        break
        bx = box_text()
        went = wait_notice(base, 1, 30)
        mark('W159', ok and quiet and bool(picked) and bx != 'HELO' and went,
             f'call selected {ok}; nothing before {quiet}; suggestion "{picked}"; box "{bx}"; notice after it {went}')
        adb('shell', 'input', 'keyevent', '4'); time.sleep(.5)

if want('W160'):  # Halt while the notice goes out: the box empties, nothing more without new keys
    with part('W160', 90, ('W160',), res, mark):
        ok, base = setup('30', 0)
        focus(); text('ABC')
        went = wait_notice(base, 1, 30)
        kb_away()
        tapk('js8_btn_halt', 1)
        bx = box_text()
        time.sleep(45)
        n = notices(base)
        mark('W160', ok and went and bx == '' and len(n) == 1, f'call selected {ok}; notice went {went}; box after Halt "{bx}"; notices {len(n)}')

if want('W161'):  # TX off: nothing. Startup sets TX from "Transmitter (TX) off at startup" (MF 365), so that is used
    with part('W161', 70, ('W161',), res, mark):
        ok, base = setup('30', 0, js8cfg_transmit_off=True)
        txoff = 'name="js8cfg_tx_enabled" value="false"' in getprefs()
        focus(); keys(35)
        time.sleep(5)
        n = notices(base)
        mark('W161', ok and txoff and not n, f'call selected {ok}; TX off {txoff}; notices {len(n)}')
        kb_away()

with part('restore', 25):
    setprefs(**{KEY: None, 'js8cfg_tx_enabled': True, 'js8cfg_transmit_off': False, 'psk_reporter': True})
    start()
print('RESULT', res, flush=True)
