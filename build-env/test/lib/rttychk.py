"""W131-W132: FT8 decodes start off (Rob, 2026-11-05); ticked, they still decode. (RTTY rows W133-W136 removed
with the RTTY decoder, Rob, 2026-11-05.) FT8: 20m_busy_test_01.wav (Easy FT8 test data) through DebugFt8Receiver.
rttychk.py <run> [ID,ID]"""
import os, re, sys, time
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from onair import adb, assert_avd, setprefs, start, clearlog, logtext, PKG
from parts import part

assert_avd()
run = int(sys.argv[1])
only = sys.argv[2].split(',') if len(sys.argv) > 2 else None
HERE = os.path.dirname(os.path.abspath(__file__))
os.chdir(HERE)
os.makedirs('shots', exist_ok=True)
FT8 = r'E:\new-w3bguru-websites\easyft8\ft8af\app\src\main\cpp\ft8af_glue\testdata\ft8\20m_busy_test_01.wav'
FT8RX = f'{PKG}/com.js8call.example.debug.DebugFt8Receiver'
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


row('W131', 40, w131)
row('W132', 50, w132)
print('RESULT', ' '.join(f'{i}:{"PASS" if ok else "FAIL"}' for i, ok, _ in res), flush=True)
