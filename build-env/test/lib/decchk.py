"""decchk.py: JS8 decoder test on S22-Fresh with made-up signals (js8core-plus swap, 2026-11-24).

python decchk.py LABEL [deep]
  LABEL names the output (decchk.LABEL.json). "deep" ticks Settings > Operation > Enable Deep Decoding.

Normal speed only. 12 signals per 15 s slot (600 to 2250 Hz, 150 Hz apart), each a different random
frame, in white noise at a set SNR (2500 Hz bandwidth, as JS8Call reports it). Each level plays SLOTS slots.
A signal counts as decoded when the service logs "Decoded: <its frame>". Decode time: each
"Calling legacy_decode" to "legacy_decode returned" pair in the log, longest and average.
Tones come from the app's own encoder (DebugJs8Receiver frame) and are kept in decchk_tones.json, so
every build hears the same audio. Spotting, AUTO, HB, APRS-IS and FT8 are off for the run."""
import sys, os, json, math, random, array, re, time
os.chdir(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, '.')
from onair2 import *
import sig

LABEL = sys.argv[1]
DEEP = len(sys.argv) > 2 and sys.argv[2] == 'deep'
OFFS = [600 + 150 * i for i in range(12)]
LEVELS = [-12, -15, -18, -21, -20, -22]  # -20 and -22 added after run 2; each level keeps its own frames
ONLY = {int(x) for x in os.environ.get('DEC_ONLY', '').split(',') if x}  # DEC_ONLY=-20,-22: run just those
SLOTS = 2
SIGMA = 1500.0
A64 = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-+'
TONES = 'decchk_tones.json'
OUT = f'decchk.{LABEL}.json'
if os.path.exists(OUT):  # a DEC_ONLY run adds its levels to the earlier result
    _prev = json.load(open(OUT))

random.seed(20261124)
FRAMES = [''.join(random.choice(A64) for _ in range(12)) for _ in range(len(LEVELS) * SLOTS * len(OFFS))]
res = {'label': LABEL, 'deep': DEEP, 'levels': {}, 'times_ms': [], 'notes': []}
if ONLY and os.path.exists(OUT):
    res = _prev


def save():
    json.dump(res, open(OUT, 'w'), indent=1)


def amp(snr):
    # signal power A^2/2 against the noise power in 2500 Hz (white noise over 6000 Hz)
    return math.sqrt(2 * SIGMA * SIGMA * (2500 / 6000) * 10 ** (snr / 10))


def make(slots, snr):
    """slots: list of [(tones, offset)] per slot."""
    nsps, df, period, delay = sig.MODES[0]
    a = amp(snr)
    out = array.array('h')
    for sigs in slots:
        buf = [random.gauss(0, SIGMA) for _ in range(period * 12000)]
        for t, f0 in sigs:
            ph, i = random.uniform(0, 6.28), int(delay * 12000)
            for c in t:
                w = 2 * math.pi * (f0 + int(c) * df) / 12000
                for _ in range(nsps):
                    buf[i] += a * math.sin(ph)
                    ph += w; i += 1
        out.extend(max(-32767, min(32767, int(s))) for s in buf)
    return out


def play(samples, name):
    open('shots/' + name, 'wb').write(samples.tobytes())
    adb('push', 'shots/' + name, '/data/local/tmp/' + name)
    adb('shell', f"run-as {PKG} sh -c 'cat /data/local/tmp/{name} > files/{name}'")
    adb('shell', 'am', 'broadcast', '-n', sig.RX, '--es', 'file', name, '--ei', 'period', '15')


def decoded_frames(log):
    # JS8Engine_Native: DECODED: SNR=-12 dB, freq=1500.0 Hz, text='...', raw='M05Hx-KusV90', type=1, mode=0
    return set(re.findall(r"DECODED: SNR=-?\d+ dB.*?raw='(\S{12})'", log))


def reported_snr(log, frames):
    return [int(s) for s, r in re.findall(r"DECODED: SNR=(-?\d+) dB.*?raw='(\S{12})'", log) if r in frames]


def decode_times(log):
    """ms from each Normal-speed (nsubmodes=0x1) Calling legacy_decode to the next legacy_decode returned
    (one decode worker), JS8Core lines only."""
    out, t0 = [], None
    for m in re.finditer(r'^(\d\d-\d\d \d\d:\d\d:\d\d\.\d\d\d) I/JS8Core.*?(Calling legacy_decode: nsubmodes=0x(\w+)|legacy_decode returned)', log, re.M):
        ts = time.mktime(time.strptime('2026-' + m.group(1)[:-4], '%Y-%m-%d %H:%M:%S')) + int(m.group(1)[-3:]) / 1000
        if m.group(2).startswith('Calling'):
            t0 = ts if m.group(3) == '1' else None
        elif t0 is not None:
            out.append(round((ts - t0) * 1000)); t0 = None
    return out


assert_avd()
adb('emu', 'avd', 'hostmicoff')  # live WebSDR audio would add real signals
fresh(tx_submode=0, js8cfg_multi=True, psk_reporter=False, aprs_is=False, autoreply_enabled=False,
      js8cfg_autoreply_startup=False, js8cfg_hb=False, js8cfg_show_ft8=False, js8cfg_deep_decoding=DEEP,
      js8cfg_filter_enabled=True, js8cfg_filter_center=1500, js8cfg_filter_width=2000)
time.sleep(4)
log = logtext()
m = re.findall(r'Decoder: deep=(\w+) hints call=(\S+)', log)
res['notes'].append(f'decoder setting line: {m[-1] if m else "none (old build has no such line)"}')

# Tones: from the cache, or from the app's encoder (est 5 s each)
tones = json.load(open(TONES)) if os.path.exists(TONES) else {}
for f in FRAMES:
    if f in tones:
        continue
    with part(f'tones {f}', 6):
        t = sig.tones(f, 1, 0)
    if not t or len(t) != 79:
        print('NO TONES', f, t, flush=True); sys.exit(1)
    tones[f] = t
    json.dump(tones, open(TONES, 'w'))
# Same encoder? Fresh tones for 2 frames against the cache (the new build's encoder is the plus one)
for f in FRAMES[:2]:
    with part(f'tone check {f}', 6):
        t = sig.tones(f, 1, 0)
    res['notes'].append(f'tones {f}: {"same" if t == tones[f] else "DIFFERENT " + str(t)}')
time.sleep(16)  # let the frame tests' slot pass

# One strong signal first (the script's own check): -5 dB, 1 slot
with part('strong check', 45):
    clearlog()
    f0 = FRAMES[0]
    play(make([[(tones[f0], 1500)]], -5), 'dec0.pcm')
    ok = False
    for _ in range(40):
        time.sleep(1)
        if f0 in decoded_frames(logtext()):
            ok = True; break
res['strong'] = ok
print('STRONG', ok, flush=True); save()
if not ok:
    sys.exit(2)
time.sleep(16)

k = 0
for snr in LEVELS:
    slots, k0 = [], k
    for s in range(SLOTS):
        slots.append([(tones[FRAMES[k + i]], OFFS[i]) for i in range(len(OFFS))]); k += len(OFFS)
    want = set(FRAMES[k0:k])
    if ONLY and snr not in ONLY:
        continue
    with part(f'level {snr} dB', 15 * SLOTS + 30):
        clearlog()
        play(make(slots, snr), f'dec{-snr}.pcm')
        end = time.time() + 15 * SLOTS + 24
        while time.time() < end:
            time.sleep(2)
            if want <= decoded_frames(logtext()):
                break
        time.sleep(4)
        log = adb('logcat', '-d', '-v', 'time').decode('utf8', 'replace')
    got = want & decoded_frames(log)
    other = decoded_frames(log) - set(FRAMES)
    t = decode_times(log)
    res['times_ms'] += t
    rs = reported_snr(log, want)
    res['levels'][str(snr)] = {'sent': len(want), 'decoded': len(got), 'wrong': sorted(other),
                               'reported_snr_avg': round(sum(rs) / len(rs), 1) if rs else None,
                               'decode_ms_max': max(t) if t else None, 'decode_ms_avg': round(sum(t) / len(t)) if t else None}
    print('LEVEL', snr, res['levels'][str(snr)], flush=True); save()
    time.sleep(16)
print('DONE', json.dumps(res['levels']), flush=True)
