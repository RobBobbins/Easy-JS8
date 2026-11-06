"""W131-W136: FT8 and RTTY decodes start off; RTTY decodes in the Message Window (Rob, 2026-11-05).
RTTY sound: fldigi's own RTTY transmitter (fldigi-android modetest, `modetest browser RTTY rtty2.raw` run on
S22-Fresh): two stations at once, "CQ CQ DE N0CALL N0CALL K" at 1000 Hz and "W1AW DE K1ABC TEST TEST" at 1800 Hz,
170 Hz shift, 45 baud, 8000 samples a second (rtty_src8k.raw). Played into the receive path by DebugJs8Receiver at
12000 samples a second. FT8: 20m_busy_test_01.wav (Easy FT8 test data) through DebugFt8Receiver.
rttychk.py <run> [ID,ID]"""
import os, re, sys, time
import numpy as np
from scipy.signal import resample_poly
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from onair import adb, assert_avd, setprefs, start, clearlog, logtext, PKG
from u import texts, shot
from parts import part

assert_avd()
run = int(sys.argv[1])
only = sys.argv[2].split(',') if len(sys.argv) > 2 else None
HERE = os.path.dirname(os.path.abspath(__file__))
os.chdir(HERE)
os.makedirs('shots', exist_ok=True)
SRC = 'rtty_src8k.raw'
FT8 = r'E:\new-w3bguru-websites\easyft8\ft8af\app\src\main\cpp\ft8af_glue\testdata\ft8\20m_busy_test_01.wav'
JS8RX = f'{PKG}/com.js8call.example.debug.DebugJs8Receiver'
FT8RX = f'{PKG}/com.js8call.example.debug.DebugFt8Receiver'
RTTY_LINE = re.compile(r'RTTY: (.*) \((\d+) Hz\)')
res = []


def want(i):
    return not only or i in only


def mark(i, ok, note=''):
    print(i, 'PASS' if ok else 'FAIL', note, flush=True)
    res.append((i, ok, note))


def push(name, data):
    open('shots/' + name, 'wb').write(data)
    adb('push', 'shots/' + name, '/data/local/tmp/' + name)
    adb('shell', f"run-as {PKG} sh -c 'cat /data/local/tmp/{name} > files/{name}'")


def sound(snr_db=None, noise_only_s=0):
    """12 kHz 16-bit sound: 2 s quiet, fldigi's two stations, 6 s quiet; light noise (as sig.synth) or snr_db
    (signal to noise in 2500 Hz). noise_only_s: that many seconds of the light noise alone."""
    rng = np.random.default_rng(run)
    if noise_only_s:
        x = rng.normal(0, 500, noise_only_s * 12000)
    else:
        s8 = np.frombuffer(open(SRC, 'rb').read(), dtype='<i2').astype(float)
        s = resample_poly(s8, 3, 2)
        x = np.concatenate([np.zeros(2 * 12000), s, np.zeros(6 * 12000)])
        on = s[np.abs(s) > 0]
        sd = 500 if snr_db is None else np.sqrt(np.mean(on ** 2) / 10 ** (snr_db / 10) * (6000 / 2500))
        x = x + rng.normal(0, sd, len(x))
    return np.clip(x, -32768, 32767).astype('<i2').tobytes()


def play(name, data, wait):
    """Mixed in at the next 15 s boundary; then wait [wait] s for its lines."""
    push(name, data)
    clearlog()
    adb('shell', 'am', 'broadcast', '-n', JS8RX, '--es', 'file', name, '--ei', 'period', '15')
    time.sleep(15 + len(data) / 24000 + wait)
    return [(m.group(1), int(m.group(2))) for m in RTTY_LINE.finditer(logtext())]


def found(lines, words, freq):
    return any(words in t and abs(f - freq) < 60 for t, f in lines)


def ft8_lines():
    push('ft8.wav', open(FT8, 'rb').read())
    clearlog()
    adb('shell', 'am', 'broadcast', '-n', FT8RX, '--es', 'file', 'ft8.wav')
    time.sleep(12)
    return re.findall(r'FT8: (.*) \(\d+ Hz', logtext())


def row(i, est, fn):
    if not want(i):
        return
    with part(i, est, [i], res, mark):
        fn()


def w131():
    setprefs(js8cfg_show_ft8=None); start()
    on = 'FT8 monitor: off' in logtext()
    got = ft8_lines()
    mark('W131', on and not got, f'FT8 starts off: log off={on}, FT8 lines {len(got)}')


def w132():
    setprefs(js8cfg_show_ft8=True); start()
    got = ft8_lines()
    setprefs(js8cfg_show_ft8=None); start()
    mark('W132', len(got) >= 10, f'FT8 on still decodes: {len(got)} lines, e.g. {got[:2]}')


def w133():
    setprefs(js8cfg_show_rtty=None); start()
    on = 'RTTY monitor: off' in logtext()
    got = play('rtty_clean.raw', sound(), 4)
    mark('W133', on and not got, f'RTTY starts off: log off={on}, RTTY lines {got}')


def w134():
    setprefs(js8cfg_show_rtty=True); start()
    got = play('rtty_clean.raw', sound(), 4)
    a, b = found(got, 'N0CALL N0CALL K', 1000), found(got, 'TEST TEST', 1800)
    seen = [t for t in texts() if 'RTTY: ' in t]
    shot(f'rtty_mw_{run}')
    mark('W134', a and b and any('N0CALL N0CALL K' in t for t in seen),
         f'lines {got}; Message Window shows RTTY: {len(seen) > 0}')


def w135():
    got = play('rtty_10db.raw', sound(snr_db=10), 4)
    mark('W135', found(got, 'N0CALL K', 1000) and found(got, 'TEST', 1800), f'10 dB: {got}')


def w136():
    got = play('rtty_noise.raw', sound(noise_only_s=60), 6)
    setprefs(js8cfg_show_rtty=None); start()
    mark('W136', len(got) == 0, f'60 s of noise alone: {len(got)} RTTY lines {got[:5]}')


row('W131', 40, w131)
row('W132', 50, w132)
row('W133', 50, w133)
row('W134', 55, w134)
row('W135', 45, w135)
row('W136', 110, w136)
print('RESULT', ' '.join(f'{i}:{"PASS" if ok else "FAIL"}' for i, ok, _ in res), flush=True)
