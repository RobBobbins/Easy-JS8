"""u.py helpers: tap by exact text or resource id via uiautomator. Emulator only."""
import subprocess, re, os, sys, html, time, socket
ADB = os.path.expandvars(r'%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe')
def adb(*a):
    return subprocess.run([ADB, '-s', 'emulator-5600', *a], capture_output=True).stdout
_d = None
def dump():
    """uiautomator2 stays running, about 0.3 s a read. JS8_READ=old is the 2.1 s uiautomator dump."""
    global _d
    if os.environ.get('JS8_READ') == 'old':
        adb('shell', 'uiautomator', 'dump', '/sdcard/u.xml')
        return adb('exec-out', 'cat', '/sdcard/u.xml').decode('utf8', 'replace')
    import uiautomator2
    for i in range(3):
        try:
            if _d is None:
                _d = uiautomator2.connect('emulator-5600')
            return _d.dump_hierarchy(root_in_active=True)
        except Exception as e:
            print('READER RESTART', repr(e)[:80], flush=True)
            _d = None
            time.sleep(2)
    return ''
def find(xml, key):
    for n in re.findall(r'<node [^>]*>', xml):
        mt, mr = re.search(r' text="([^"]*)"', n), re.search(r'resource-id="([^"]*)"', n)
        if not mt or not mr:  # text with a stray character cuts the node short
            continue
        t, r = html.unescape(mt.group(1)), mr.group(1)
        if t == key or r.endswith('/' + key):
            a, b, c, d = map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n).groups())
            return (a + c) // 2, (b + d) // 2, t
    return None
def tap(key):
    p = find(dump(), key)
    if not p: print('NOT FOUND', key); return False
    adb('shell', 'input', 'tap', str(p[0]), str(p[1])); return True
def text(s): adb('shell', 'input', 'text', s.replace(' ', '%s'))
def shot(n):
    open(f'shots/{n}.png', 'wb').write(adb('exec-out', 'screencap', '-p'))
def rig(cmd, port=4599):
    s = socket.create_connection(('127.0.0.1', port)); s.send((cmd + '\n').encode()); print(s.recv(100)); s.close()
def log(pat, n=20):
    x = adb('logcat', '-d').decode('utf8', 'replace').splitlines()
    for l in [l for l in x if re.search(pat, l)][-n:]: print(l[:200])
def texts():
    return [html.unescape(t) for t in re.findall(r' text="([^"]+)"', dump())]
