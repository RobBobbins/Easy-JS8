from sig import *
import sys
sys.stdout.reconfigure(encoding='utf8')
def inj(t, f=1200, ty=3, snr=-10):
    adb('shell', f"""am broadcast -n com.easyjs8.app/com.js8call.example.debug.DebugDecodeReceiver --es text "{t}" --ef freq {f} --ei type {ty} --ei snr {snr}""")
def tapxy(x, y): adb('shell', 'input', 'tap', str(int(x * 1.17)), str(int(y * 1.17)))
def has(*k):
    t = texts()
    return all(any(a == x for x in t) for a in k)
def to_inbox():
    for i in range(6):
        x = dump()
        if find(x, 'Message Inbox') and find(x, 'ID') and find(x, 'Reply'): return True
        p = find(x, 'Directed') or find(x, 'Control') or find(x, 'Timing')
        if not p: return False
        adb('shell', 'input', 'swipe', str(p[0]), str(p[1]), str(p[0]), '150', '250'); time.sleep(1.5)
def hold(k):
    x=dump(); p=find(x,k); adb('shell','input','swipe',str(p[0]),str(p[1]),str(p[0]),str(p[1]),'900'); time.sleep(2.5)
def menu(k):
    x=dump(); d=find(x,k); adb('shell','input','tap',str(d[0]),str(d[1])); time.sleep(3)
def fnd(k, n=4):
    for i in range(n):
        p = find(dump(), k)
        if p: return p
        time.sleep(1.5)
def tp(k, wait=2):
    p = fnd(k)
    if not p: print('MISSING', k); return False
    adb('shell', 'input', 'tap', str(p[0]), str(p[1])); time.sleep(wait); return True
def settings():
    tp('Settings'); tapxy(456, 173); time.sleep(2)
