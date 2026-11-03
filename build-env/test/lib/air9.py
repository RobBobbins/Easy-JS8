"""WSJT-X, N3FJP, N1MM, TCP limit, closing. Listeners on this PC only. air9.py <run>"""
from onair2 import *
import threading, struct
assert_avd()
run = int(sys.argv[1])
t = T(run)
t.res = []
fix = t.mark
W, N1, N3 = 22237, 22333, 21100
got = {'w': [], 'n1': [], 'n3': []}


def udp(port, k):
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    s.bind(('0.0.0.0', port))
    def f():
        while True:
            got[k].append(s.recvfrom(65535)[0])
    threading.Thread(target=f, daemon=True).start()


def tcp(port, k):
    s = socket.socket()
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    s.bind(('0.0.0.0', port)); s.listen(5)
    def f():
        while True:
            c, _ = s.accept()
            c.settimeout(1)
            try:
                b = c.recv(65535)
                got[k].append(b)
                c.send(b'<CMD><ADDDIRECT>OK</ADDDIRECT></CMD>\r\n')
            except OSError:
                pass
            c.close()
    threading.Thread(target=f, daemon=True).start()


udp(W, 'w'); udp(N1, 'n1'); tcp(N3, 'n3')
fresh(js8cfg_wsjtx_enabled=True, js8cfg_wsjtx_server='10.0.2.2', js8cfg_wsjtx_port=str(W), js8cfg_n1mm_enabled=True, js8cfg_n1mm_server='10.0.2.2', js8cfg_n1mm_port=str(N1),
      js8cfg_n3fjp_enabled=True, js8cfg_n3fjp_server='10.0.2.2', js8cfg_n3fjp_port=str(N3), js8cfg_tcp_max_connections=str(1), landscape_layout='pc')
time.sleep(18)
inj('K4WSJ: @ALLCALL CQ CQ CQ FN31', 1400, 3, -6); time.sleep(3)
adb('shell', 'am', 'broadcast', '-n', f'{PKG}/com.js8call.example.debug.DebugLogQsoReceiver', '--es', 'call', 'K4LOG'); time.sleep(4)
ty = [struct.unpack('>I', b[8:12])[0] for b in got['w'] if b[:4] == bytes.fromhex('adbccbda')]
fix('Q12', 0 in ty, f'{ty.count(0)} heartbeats'); fix('Q13', 1 in ty, f'{ty.count(1)} status')
dec = [b for b in got['w'] if b[8:12] == struct.pack('>I', 2)]
fix('Q14', any(b'K4WSJ' in b for b in dec), f'{len(dec)} decodes')
fix('Q15', any(b[8:12] == struct.pack('>I', 5) and b'K4LOG' in b for b in got['w']), 'QSOLogged')
fix('Q16', any(b[8:12] == struct.pack('>I', 12) and b'K4LOG' in b for b in got['w']), 'LoggedADIF')
fix('Q17', any(b'K4LOG' in b for b in got['n3']), str(got['n3'][:1])[:70])
fix('Q18', any(b'K4LOG' in b for b in got['n1']), str(got['n1'][:1])[:70])
adb('forward', 'tcp:2442', 'tcp:2442')
a = socket.create_connection(('127.0.0.1', 2442), 5); time.sleep(1)
b = socket.create_connection(('127.0.0.1', 2442), 5); b.settimeout(3)
try:
    closed = b.recv(100) == b''
except OSError:
    closed = False
fix('Q10', closed, 'second connection closed' if closed else 'second connection stayed open')
b.close()
a.settimeout(.5); stop(); buf = b''; end = time.time() + 5
while time.time() < end:
    try:
        d = a.recv(65536)
        if not d: break
        buf += d
    except OSError:
        pass
fix('Q11', b'STATION.CLOSING' in buf, buf[-80:].decode('utf8', 'replace'))
a.close(); start()
setprefs(js8cfg_wsjtx_enabled=False, js8cfg_n1mm_enabled=False, js8cfg_n3fjp_enabled=False); start()
json.dump(t.res, open(f'air9.{run}.json', 'w'))
print('DONE', flush=True)
