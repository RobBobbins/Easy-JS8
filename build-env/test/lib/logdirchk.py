"""Settings > Log: Open Log Directory... row above Export Log...; it opens JS8CallLogs in the device's file app (or the
where-message), no crash. Then sideways PC: Log > Open Log Directory... does the same. logdirchk.py <serial> <tag>.
The app's settings file and the device's rotation settings are put back; the file app is closed at the end."""
import sys, time, subprocess, re
sys.path.insert(0, r'E:\new-w3bguru-websites\easy js8\build-env\test\lib')
import uiautomator2
from parts import part

SER, TAG = sys.argv[1], sys.argv[2]
ADB = r'C:\Users\Robert\AppData\Local\Android\Sdk\platform-tools\adb.exe'
PKG = 'com.easyjs8.app'
PREFS = f'shared_prefs/{PKG}_preferences.xml'
OUT = r'E:\new-w3bguru-websites\easy js8\build-env\test\lib\shots'
TMP = r'C:\Users\Robert\AppData\Local\Temp\claude\08874037d42d1ae2\cb17df91-e634-4729-8951-fae0e40e1f25\scratchpad\p.xml'
FILES = ['com.google.android.documentsui', 'com.android.documentsui', 'com.sec.android.app.myfiles']
d = uiautomator2.connect(SER)
res = []


def adb(*a):
    return subprocess.run([ADB, '-s', SER, *a], capture_output=True, timeout=30).stdout


def mark(i, ok, note=''):
    print(i, 'PASS' if ok else 'FAIL', note, flush=True); res.append((i, ok))


def snap(t):
    open(rf'{OUT}\logdir.{TAG}.{t}.png', 'wb').write(adb('exec-out', 'screencap', '-p'))


def tap(o):
    b = o.info['bounds']; d.click((b['left'] + b['right']) // 2, (b['top'] + b['bottom']) // 2)


def close_files():
    for f in FILES:
        adb('shell', 'am', 'force-stop', f)
    adb('logcat', '-c', '-b', 'crash')


def opened():
    """JS8CallLogs shown in the file app, or the app's where-message; and no crash."""
    time.sleep(3)
    crash = b'FATAL EXCEPTION' in adb('logcat', '-d', '-b', 'crash')
    x = d.dump_hierarchy()
    folder = 'JS8CallLogs' in x and 'Files in JS8CallLogs' in x or bool(re.search(r'text="JS8CallLogs"[^>]*', x)) and 'Files in Download' not in x
    msg = 'Download/JS8CallLogs' in x
    return (not crash) and (folder or msg), f'crash {crash}, JS8CallLogs open {folder}, where-message {msg}'


ROT = (adb('shell', 'settings', 'get', 'system', 'accelerometer_rotation').decode().strip(),
       adb('shell', 'settings', 'get', 'system', 'user_rotation').decode().strip())
print('device rotation settings', ROT, flush=True)
adb('shell', 'am', 'force-stop', PKG)
adb('shell', f'run-as {PKG} cp {PREFS} files/logdir_copy.xml')
try:
    with part('Settings > Log upright', 45, ['settings log'], res, mark):
        adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0')
        adb('shell', 'am', 'start', '-n', f'{PKG}/com.js8call.example.MainActivity'); time.sleep(7)
        adb('shell', 'cmd', 'window', 'user-rotation', 'lock', '0'); time.sleep(3)
        if not d(resourceId=f'{PKG}:id/js8_btn_settings').exists and d(text='Header').exists:
            tap(d(text='Header')); time.sleep(1.5)
        tap(d(resourceId=f'{PKG}:id/js8_btn_settings')); time.sleep(3)
        if not d(text='Open Log Directory...').exists:
            d(scrollable=True).scroll.to(text='Open Log Directory...'); time.sleep(1)
        snap('settings_log')
        a, b = d(text='Open Log Directory...'), d(text='Export Log...')
        above = a.exists and b.exists and a.info['bounds']['top'] < b.info['bounds']['top']
        mark('settings log', above, f'Open Log Directory... row above Export Log...: {above}')
    with part('Settings > Log opens the folder', 20, ['settings open'], res, mark):
        close_files()
        tap(d(text='Open Log Directory...'))
        ok, note = opened(); snap('settings_open')
        mark('settings open', ok, note)
    with part('menu bar Log > Open Log Directory...', 45, ['menu open'], res, mark):
        close_files()
        adb('shell', 'am', 'force-stop', PKG)
        x = adb('shell', f'run-as {PKG} cat {PREFS}').decode()
        if 'name="landscape_layout"' in x:
            x = re.sub(r'<string name="landscape_layout">[^<]*</string>', '<string name="landscape_layout">pc</string>', x)
        else:
            x = x.replace('</map>', '    <string name="landscape_layout">pc</string>\n</map>')
        open(TMP, 'w', encoding='utf8', newline='\n').write(x)
        adb('push', TMP, '/data/local/tmp/p.xml')
        adb('shell', f'run-as {PKG} sh -c "cat /data/local/tmp/p.xml > {PREFS}"'); adb('shell', 'rm', '/data/local/tmp/p.xml')
        adb('shell', 'am', 'start', '-n', f'{PKG}/com.js8call.example.MainActivity'); time.sleep(7)
        adb('shell', 'cmd', 'window', 'user-rotation', 'lock', '1'); time.sleep(4)
        tap(d(resourceId=f'{PKG}:id/js8_menubar_log')); time.sleep(1.5)
        tap(d(text='Open Log Directory...'))
        ok, note = opened(); snap('menu_open')
        mark('menu open', ok, note)
finally:
    with part('put back', 25):
        adb('shell', 'am', 'force-stop', PKG)
        adb('shell', f'run-as {PKG} cp files/logdir_copy.xml {PREFS}')
        adb('shell', f'run-as {PKG} rm files/logdir_copy.xml')
        adb('shell', 'input', 'keyevent', 'KEYCODE_HOME')
        close_files()
        adb('shell', 'cmd', 'window', 'user-rotation', 'free')
        adb('shell', 'settings', 'put', 'system', 'user_rotation', ROT[1])
        adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', ROT[0])
        print('put back: rotation', (adb('shell', 'settings', 'get', 'system', 'accelerometer_rotation').decode().strip(),
              adb('shell', 'settings', 'get', 'system', 'user_rotation').decode().strip()),
              'copy left' if b'logdir_copy' in adb('shell', f'run-as {PKG} ls files') else 'no copy left', flush=True)
print('RESULT', 'PASS' if res and all(r[1] for r in res) else 'FAIL', flush=True)
