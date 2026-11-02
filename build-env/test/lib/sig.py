from u import *
import subprocess, math, random, array, json
RX = 'com.easyjs8.app/com.js8call.example.debug.DebugJs8Receiver'
MODES = {0: (1920, 6.25, 15, 0.5), 1: (1200, 10.0, 10, 0.2), 2: (600, 20.0, 6, 0.1), 8: (384, 31.25, 4, 0.1)}
def tones(frame, bits, sub=0):
    adb('logcat', '-c')
    adb('shell', 'am', 'broadcast', '-n', RX, '--es', 'frame', frame, '--ei', 'bits', str(bits), '--ei', 'submode', str(sub))
    time.sleep(2.5)
    x = adb('logcat', '-d').decode('utf8', 'replace')
    m = re.findall(r'JS8 test tones: (\d+)', x)
    return m[-1] if m else None
def synth(seq, sub=0):
    """seq: list of (tonestring|None, offsetHz), one per slot."""
    nsps, df, period, delay = MODES[sub]
    out = array.array('h')
    for t, f0 in seq:
        slot = [0.0] * (period * 12000)
        if t:
            ph = 0.0; i = int(delay * 12000)
            for c in t:
                w = 2 * math.pi * (f0 + int(c) * df) / 12000
                for _ in range(nsps):
                    if i < len(slot): slot[i] = 9000 * math.sin(ph)
                    ph += w; i += 1
        out.extend(int(s + random.gauss(0, 500)) for s in slot)
    return out
def play(seq, name, sub=0):
    a = synth(seq, sub)
    open('shots/' + name, 'wb').write(a.tobytes())
    adb('push', 'shots/' + name, '/data/local/tmp/' + name)
    adb('shell', f"run-as com.easyjs8.app sh -c 'cat /data/local/tmp/{name} > files/{name}'")
    adb('shell', f"chmod 644 /data/local/tmp/{name}")
    adb('shell', 'am', 'broadcast', '-n', RX, '--es', 'file', name, '--ei', 'period', str(MODES[sub][2]))
