"""W143: Halt while a heartbeat waits or goes out, 50 rounds, never freezes the app (tablet 2026-11-06: stopping the
sound output waited on its own callback, audio_oboe.cpp). Each round taps HB, waits 0.3-28 s (before the slot and
inside the sound), taps Halt, and checks the app process is the same one. Then one HB with no Halt must go out, the
screen must answer, and the log must hold no ANR. hbhalt.py <run> [rounds]"""
from onair2 import *
import random
assert_avd()
run = int(sys.argv[1])
rounds = int(sys.argv[2]) if len(sys.argv) > 2 else 50
res = []


def mark(id, ok, note=''):
    print(id, 'PASS' if ok else 'FAIL', note, flush=True); res.append((id, ok, note))


def pid():
    return adb('shell', 'pidof', PKG).decode().strip()


def count(pat):
    return len(re.findall(pat, logtext()))


with part('setup', 40, ('W143',), res, mark):
    adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0')
    adb('shell', 'settings', 'put', 'system', 'user_rotation', '0')
    fresh(tx_submode=0)  # Normal, as the tablet: the HB button shows in Normal only
    x = dump()
    hb, halt = find(x, 'js8_btn_hb'), find(x, 'js8_btn_halt')
    print('HB', hb, 'Halt', halt, flush=True)
    clearlog()
    p0 = pid()

bad = ''
if hb and halt and p0:
    for i in range(1, rounds + 1):
        with part(f'round {i}', 31, ('W143',), res, mark):
            adb('shell', 'input', 'tap', str(hb[0]), str(hb[1]))
            w = random.uniform(.3, 28)  # 15 s slot, then about 13 s of sound
            time.sleep(w)
            adb('shell', 'input', 'tap', str(halt[0]), str(halt[1]))
            time.sleep(.5)
            p = pid()
            print(f'round {i} halt after {w:.1f} s pid {p}', flush=True)
            if p != p0:
                bad = f'round {i}: app process {p0} became {p or "none"}'
        if bad or res:
            break
else:
    bad = 'setup: HB %s Halt %s pid %s' % (hb, halt, p0)

if not bad and not res:
    with part('final HB', 45, ('W143',), res, mark):
        n0 = count(r'openStream\(\) OUTPUT')
        adb('shell', 'input', 'tap', str(hb[0]), str(hb[1]))
        end = time.time() + 30
        while time.time() < end and count(r'openStream\(\) OUTPUT') == n0:
            time.sleep(1)
        went = count(r'openStream\(\) OUTPUT') > n0
        time.sleep(12)
        screen = bool(find(dump(), 'js8_btn_hb'))
        log = logtext()
        anr = re.findall(r'ANR in com\.easyjs8\.app', log)
        sound = len(re.findall(r'openStream\(\) OUTPUT', log))
        hbs = len(re.findall(r'Sending Heartbeat', log))
        ok = went and screen and not anr and pid() == p0
        mark('W143', ok, f'{rounds} rounds; heartbeats started {hbs}, sound opened {sound} times; final HB sent {went}; '
                         f'screen answers {screen}; ANR {len(anr)}; same process {pid() == p0}')
elif bad:
    mark('W143', False, bad)
print('RESULT', res, flush=True)
