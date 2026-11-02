"""Fake APRS-IS server on 127.0.0.1:14580. Logs every connection and line to aprs.log.
Lines placed in aprs_send.txt are sent to the connected client, then the file is emptied."""
import socket, threading, time, os
HERE = os.path.dirname(os.path.abspath(__file__))
LOG = os.path.join(HERE, 'aprs.log')
SEND = os.path.join(HERE, 'aprs_send.txt')
current = []
def log(s):
    with open(LOG, 'a', encoding='utf8') as f:
        f.write(time.strftime('%H:%M:%S ') + s + '\n')
def client(c, a):
    log(f'CONNECT {a}')
    c.sendall(b'# aprsc fake 1.0\r\n')
    current.append(c)
    buf = b''
    try:
        while True:
            d = c.recv(4096)
            if not d: break
            buf += d
            while b'\n' in buf:
                line, buf = buf.split(b'\n', 1)
                log('RX ' + repr(line.decode('utf8', 'replace')))
    except OSError:
        pass
    log('DISCONNECT')
    if c in current: current.remove(c)
    c.close()
def sender():
    while True:
        time.sleep(0.5)
        if os.path.exists(SEND) and os.path.getsize(SEND):
            lines = open(SEND, encoding='utf8').read().splitlines()
            open(SEND, 'w').close()
            for l in lines:
                if l == 'KICK':
                    for c in list(current): c.shutdown(socket.SHUT_RDWR)
                    log('KICKED')
                    continue
                log('TX ' + l)
                for c in list(current): c.sendall((l + '\r\n').encode('utf8'))
threading.Thread(target=sender, daemon=True).start()
s = socket.socket()
s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
s.bind(('127.0.0.1', 14580))
s.listen(5)
log('LISTEN')
while True:
    c, a = s.accept()
    threading.Thread(target=client, args=(c, a), daemon=True).start()
