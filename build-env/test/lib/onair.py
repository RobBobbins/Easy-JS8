"""Test harness for S22-Fresh: stand-in servers, settings, injected decodes, transmit log. Run from build-env/test/lib."""
import subprocess, sys, time, re, os, html, base64, binascii
sys.stdout.reconfigure(encoding='utf8')
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from u import adb, ADB, dump, find, tap, text, shot, texts
from sig import synth
from parts import part, TimedOut
PKG = 'com.easyjs8.app'
PREFS = f'shared_prefs/{PKG}_preferences.xml'
ALPHA = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ+-./?'
def assert_avd():
    assert adb('emu', 'avd', 'name').decode().split()[0] == 'S22-Fresh'
def stop(): adb('shell', 'am', 'force-stop', PKG)
def start():
    adb('shell', 'am', 'start', '-n', f'{PKG}/com.js8call.example.MainActivity'); time.sleep(6)
def getprefs(): return adb('exec-out', 'run-as', PKG, 'cat', PREFS).decode('utf8')
class Long(int):
    """A pref the app keeps with putLong, e.g. time_drift_ms: written as <long>, an <int> would crash its getLong."""
def setprefs(**kv):
    stop(); x = getprefs()
    for k, v in kv.items():
        x = re.sub(r'\s*<[a-z]+ name="%s"[^>]*?(/>|>.*?</string>)' % re.escape(k), '', x, flags=re.S)
        if v is None: continue
        if isinstance(v, bool): e = f'<boolean name="{k}" value="{str(v).lower()}" />'
        elif isinstance(v, Long): e = f'<long name="{k}" value="{int(v)}" />'
        elif isinstance(v, int): e = f'<int name="{k}" value="{v}" />'
        else: e = f'<string name="{k}">{html.escape(v, quote=False)}</string>'
        x = x.replace('</map>', f'    {e}\n</map>')
    b = base64.b64encode(x.encode('utf8')).decode()
    adb('shell', f"echo {b} | base64 -d | run-as {PKG} sh -c 'cat > {PREFS}'")
def clearlog(): adb('logcat', '-c')
def logtext(): return adb('logcat', '-d', '-v', 'brief').decode('utf8', 'replace')
def inj(t, f=1200, ty=3, snr=-10):
    adb('shell', f"""am broadcast -n {PKG}/com.js8call.example.debug.DebugDecodeReceiver --es text "{t}" --ef freq {f} --ei type {ty} --ei snr {snr}""")
def seen(pat, wait=12):
    end = time.time() + wait
    while True:
        m = re.findall(pat, logtext())
        if m: return m
        if time.time() > end: return []
        time.sleep(1)
def crc16k(s):
    c = 0
    for b in s.encode():
        c ^= b
        for _ in range(8): c = (c >> 1) ^ 0x8408 if c & 1 else c >> 1
    return c
def sum16(s):
    v = crc16k(s); return ALPHA[v // 1681] + ALPHA[v // 41 % 41] + ALPHA[v % 41]
ALL = '/sdcard/Download/JS8CallLogs/ALL.TXT'
def alltxt(): return adb('shell', 'cat', ALL).decode('utf8', 'replace').splitlines()
def tx_joined(lines):
    """The transmit lines' frames joined, as the message went out (N30: one frame per line). A frame can end
    inside a word ("K4IB" then "A") or without its space ("VE3TST: K4MRB YES" then "MSG ID 147"): both readings."""
    fr = [l.split('JS8:', 1)[-1][2:] for l in lines if 'Transmitting' in l]
    return [''.join(fr), re.sub(' +', ' ', ' '.join(fr))]
class T:
    """Injected-decode tests: each gets a new callsign, checks what ALL.TXT transmits."""
    n = 0
    def __init__(s, run, only=None): s.run, s.only, s.res = run, only, []
    def call(s, i=0):
        T.n += 1; return 'K4' + chr(65 + T.n // 26 % 26) + chr(65 + T.n % 26) + chr(65 + i)
    def mark(s, id, ok, note=''):
        print(id, 'PASS' if ok else 'FAIL', note, flush=True); s.res.append((id, ok, note))
    def tx(s, base, pat, wait=24):
        end = time.time() + wait
        while time.time() < end:
            new = alltxt()[base:]
            tl = [l for l in new if 'Transmitting' in l]
            hit = [l for l in tl if re.search(pat, l)]
            if hit: return hit
            # each transmit line holds its own frame (N30): the frames joined, as the message went out
            for joined in tx_joined(tl):
                if tl and re.search(pat, joined): return ['JS8:  ' + joined]
            time.sleep(1)
        return []
    def part(s, id, sends, wait):
        """The row's timer: its wait, 8 s after a send, 3 s an injected decode, 5 s for the reads."""
        return part(id, wait + 8 + 3 * len(sends) + 5, (id,), s.res, s.mark)
    def expect(s, id, sends, pat, wait=24, note=''):
        if s.only and id not in s.only: return
        wait += 15  # N30: a reply's later frames reach ALL.TXT one period later, each in its own line
        with s.part(id, sends, wait):
            base = len(alltxt())
            for m in sends:
                if isinstance(m, str): inj(m)
                elif callable(m): m()
                else: inj(*m)
                time.sleep(.3)
            h = s.tx(base, pat, wait)
            s.mark(id, bool(h), (h[0].split('JS8:')[-1].strip() if h else 'no matching send: ' + ' / '.join(l.split('JS8:')[-1].strip() for l in alltxt()[base:] if 'Transmitting' in l)))
            if h: time.sleep(8)
    def silent(s, id, sends, pat='.', wait=14):
        if s.only and id not in s.only: return
        with s.part(id, sends, wait):
            base = len(alltxt())
            for m in sends:
                if isinstance(m, str): inj(m)
                elif callable(m): m()
                else: inj(*m)
                time.sleep(.3)
            h = s.tx(base, pat, wait)
            s.mark(id, not h, ('sent: ' + h[0].split('JS8:')[-1].strip()) if h else 'nothing sent')
            if h: time.sleep(8)
