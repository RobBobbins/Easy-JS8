"""t.py pref k=v k=v ... | restart | show [regex] | key. Emulator S22-Fresh only."""
import subprocess, sys, re, os, html, base64
ADB = os.path.expandvars(r'%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe')
PKG = 'com.easyjs8.app'
F = f'shared_prefs/{PKG}_preferences.xml'
def adb(*a, inp=None):
    return subprocess.run([ADB, '-s', 'emulator-5600', *a], input=inp, capture_output=True).stdout
def stop(): adb('shell', 'am', 'force-stop', PKG)
def start(): adb('shell', 'am', 'start', '-n', f'{PKG}/com.js8call.example.MainActivity')
name = adb('emu', 'avd', 'name').decode().split()[0]
assert name == 'S22-Fresh', name
c = sys.argv[1]
if c == 'pref':
    stop()
    x = adb('exec-out', 'run-as', PKG, 'cat', F).decode('utf8')
    for kv in sys.argv[2:]:
        k, v = kv.split('=', 1)
        x = re.sub(r'\s*<[a-z]+ name="%s"[^>]*?(/>|>.*?</string>)' % re.escape(k), '', x, flags=re.S)
        if v in ('true', 'false'): e = f'<boolean name="{k}" value="{v}" />'
        elif v.startswith('i:'): e = f'<int name="{k}" value="{v[2:]}" />'
        elif v == 'DEL': continue
        else: e = f'<string name="{k}">{html.escape(v)}</string>'
        x = x.replace('</map>', f'    {e}\n</map>')
    b = base64.b64encode(x.encode('utf8')).decode()
    adb('shell', f"echo {b} | base64 -d | run-as {PKG} sh -c 'cat > {F}'")
    start()
elif c == 'restart':
    stop(); start()
elif c == 'show':
    x = adb('exec-out', 'run-as', PKG, 'cat', F).decode('utf8')
    for l in x.splitlines():
        if len(sys.argv) < 3 or re.search(sys.argv[2], l): print(l.strip())
