"""U rows: pass-through to the real networks, logged to realrelay.log.
TCP 127.0.0.1:14581 -> rotate.aprs2.net:14580 (APRS-IS), UDP 127.0.0.1:50000 -> spot.js8call.com:50000 (JS8NET).
Stop fakespot.py first: it holds UDP 50000."""
import socket, threading, time, os
HERE = os.path.dirname(os.path.abspath(__file__))
LOG = os.path.join(HERE, 'realrelay.log')
lock = threading.Lock()


def log(s):
    with lock, open(LOG, 'a', encoding='utf8') as f:
        f.write(time.strftime('%H:%M:%S ') + s.rstrip() + '\n')


def pipe(src, dst, tag, linger):
    try:
        while True:
            d = src.recv(65535)
            if not d:
                break
            for l in d.decode('utf8', 'replace').splitlines():
                log(f'{tag} {l}')
            dst.sendall(d)
    except OSError as e:
        log(f'{tag} end {e}')
    if linger:
        # the app closes right after writing; keep the server side open to read its answer
        try:
            dst.shutdown(socket.SHUT_WR)
        except OSError:
            pass


def aprs(c):
    up = socket.create_connection(('rotate.aprs2.net', 14580), 15)
    log(f'APRS connected to {up.getpeername()}')
    up.settimeout(10)
    threading.Thread(target=pipe, args=(c, up, 'APP>', True), daemon=True).start()
    pipe(up, c, 'SRV>', False)
    up.close(); c.close()


def tcp():
    s = socket.socket(); s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    s.bind(('127.0.0.1', 14581)); s.listen(4)
    while True:
        c, _ = s.accept()
        threading.Thread(target=aprs, args=(c,), daemon=True).start()


def udp():
    host = socket.gethostbyname('spot.js8call.com')
    log(f'JS8NET spot.js8call.com is {host}')
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM); s.bind(('127.0.0.1', 50000))
    out = socket.socket(socket.AF_INET, socket.SOCK_DGRAM); out.settimeout(.2)
    while True:
        d, _ = s.recvfrom(65535)
        n = out.sendto(d, (host, 50000))
        log(f'JS8NET sent {n} bytes: ' + d.decode('utf8', 'replace'))
        try:
            r = out.recv(65535); log('JS8NET answer: ' + r.decode('utf8', 'replace'))
        except OSError:
            pass


def watch():
    """Read-only APRS-IS feed of packets from VA3OSO, to see the spot come back from the network."""
    while True:
        try:
            w = socket.create_connection(('rotate.aprs2.net', 14580), 15)
            w.settimeout(None)
            w.sendall(b'user EJS8TEST pass -1 vers EasyJS8-test 1 filter b/VA3OSO*\r\n')
            log(f'WATCH connected to {w.getpeername()}')
            f = w.makefile('r', encoding='utf8', errors='replace')
            for l in f:
                if not l.startswith('# aprsc') and not l.startswith('# javAPRSSrvr'):
                    log('WATCH ' + l)
        except OSError as e:
            log(f'WATCH end {e}')
        time.sleep(5)


threading.Thread(target=tcp, daemon=True).start()
threading.Thread(target=udp, daemon=True).start()
threading.Thread(target=watch, daemon=True).start()
log('relay up')
while True:
    time.sleep(60)
