"""Sideways PC and FT8Call layouts: Log menu has Export Log... third; it opens Export Options; ← back to the main screen.
logmenu.py <serial> <tag> [layouts]. The app's settings file and the device's rotation settings are put back."""
import sys, time, subprocess, re
sys.path.insert(0, r'E:\new-w3bguru-websites\easy js8\build-env\test\lib')
import uiautomator2
from parts import part

SER, TAG = sys.argv[1], sys.argv[2]
LAYOUTS = sys.argv[3].split(',') if len(sys.argv) > 3 else ['pc', 'ft8call']
ADB = r'C:\Users\Robert\AppData\Local\Android\Sdk\platform-tools\adb.exe'
PKG = 'com.easyjs8.app'
PREFS = f'shared_prefs/{PKG}_preferences.xml'
OUT = r'E:\new-w3bguru-websites\easy js8\build-env\test\lib\shots'
TMP = r'C:\Users\Robert\AppData\Local\Temp\claude\08874037d42d1ae2\cb17df91-e634-4729-8951-fae0e40e1f25\scratchpad\p.xml'
d = uiautomator2.connect(SER)
res = []


def adb(*a):
    return subprocess.run([ADB, '-s', SER, *a], capture_output=True, timeout=30).stdout


def mark(i, ok, note=''):
    print(i, 'PASS' if ok else 'FAIL', note, flush=True); res.append((i, ok))


def snap(t):
    open(rf'{OUT}\logmenu.{TAG}.{t}.png', 'wb').write(adb('exec-out', 'screencap', '-p'))


def tap(o):
    b = o.info['bounds']; d.click((b['left'] + b['right']) // 2, (b['top'] + b['bottom']) // 2)


def set_layout(v):
    x = adb('shell', f'run-as {PKG} cat {PREFS}').decode()
    if 'name="landscape_layout"' in x:
        x = re.sub(r'<string name="landscape_layout">[^<]*</string>', f'<string name="landscape_layout">{v}</string>', x)
    else:
        x = x.replace('</map>', f'    <string name="landscape_layout">{v}</string>\n</map>')
    open(TMP, 'w', encoding='utf8', newline='\n').write(x)
    adb('push', TMP, '/data/local/tmp/p.xml')
    adb('shell', f'run-as {PKG} sh -c "cat /data/local/tmp/p.xml > {PREFS}"')
    adb('shell', 'rm', '/data/local/tmp/p.xml')


ROT = (adb('shell', 'settings', 'get', 'system', 'accelerometer_rotation').decode().strip(),
       adb('shell', 'settings', 'get', 'system', 'user_rotation').decode().strip())
print('device rotation settings', ROT, flush=True)
adb('shell', 'am', 'force-stop', PKG)
adb('shell', f'run-as {PKG} cp {PREFS} files/logmenu_copy.xml')
try:
    for lay in LAYOUTS:
        with part(f'{lay} Log menu', 35, [f'{lay} menu'], res, mark):
            adb('shell', 'am', 'force-stop', PKG)
            set_layout(lay)
            adb('shell', 'am', 'start', '-n', f'{PKG}/com.js8call.example.MainActivity'); time.sleep(7)
            adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0')
            adb('shell', 'cmd', 'window', 'user-rotation', 'lock', '1'); time.sleep(4)
            tap(d(resourceId=f'{PKG}:id/js8_menubar_log')); time.sleep(1.5); snap(f'{lay}_menu')
            items = [d(resourceId='android:id/title')[i].info['text'] for i in range(d(resourceId='android:id/title').count)]
            mark(f'{lay} menu', items == ['Add Log Entry...', 'Open Log Directory...', 'Export Log...', 'Erase ALL.TXT',
                                          'Erase js8call_log.adi'], items)
        with part(f'{lay} Export Options', 15, [f'{lay} export'], res, mark):
            tap(d(text='Export Log...'))
            ok = d(text='Export Options').wait(timeout=5); time.sleep(.8); snap(f'{lay}_export')
            d.press('back'); time.sleep(2)
            back = d(resourceId=f'{PKG}:id/js8_menubar_log').wait(timeout=5)
            mark(f'{lay} export', ok and back, f'Export Options {ok}, back on the main screen {back}')
finally:
    with part('put back', 20):
        adb('shell', 'am', 'force-stop', PKG)
        adb('shell', f'run-as {PKG} cp files/logmenu_copy.xml {PREFS}')
        adb('shell', f'run-as {PKG} rm files/logmenu_copy.xml')
        adb('shell', 'cmd', 'window', 'user-rotation', 'free')
        adb('shell', 'settings', 'put', 'system', 'user_rotation', ROT[1])
        adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', ROT[0])
        print('put back: rotation', (adb('shell', 'settings', 'get', 'system', 'accelerometer_rotation').decode().strip(),
              adb('shell', 'settings', 'get', 'system', 'user_rotation').decode().strip()),
              'copy left' if b'logmenu_copy' in adb('shell', f'run-as {PKG} ls files') else 'no copy left', flush=True)
print('RESULT', 'PASS' if res and all(r[1] for r in res) else 'FAIL', flush=True)
