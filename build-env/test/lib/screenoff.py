"""Check: does an SNR? query to me get answered while the screen is off? Same query with the screen on as the control.
Found 2026-11-04: screen on, answered; screen off, no answer in 40 s (MainActivity's receivers are registered only
between onStart and onStop)."""
from onair2 import *
assert_avd()
M = 'VE3TST'


def awake():
    return 'mWakefulness=Awake' in adb('shell', 'dumpsys', 'power').decode()


def ask(a, f):
    base = len(alltxt())
    inj(f'{a}: {M} SNR?', f, 3, -8)
    end = time.time() + 40
    while time.time() < end:
        hit = [l for l in alltxt()[base:] if 'Transmitting' in l and a in l]
        if hit:
            return hit[0].split('JS8:')[-1].strip()
        time.sleep(1)
    return ''


fresh(tx_submode=1)
on = ask('K4SCA', 1300)
print('screen on, awake', awake(), 'reply:', repr(on), flush=True)
time.sleep(10)
adb('shell', 'input', 'keyevent', '26'); time.sleep(3)
print('screen off, awake', awake(), flush=True)
off = ask('K4SCB', 1400)
print('screen off reply:', repr(off), flush=True)
adb('shell', 'input', 'keyevent', '26'); time.sleep(2)
adb('shell', 'input', 'keyevent', '82'); time.sleep(2)
print('screen back on, awake', awake(), flush=True)
