"""Fake JS8NET spot server on UDP 127.0.0.1:50000. Logs each datagram to spot.log."""
import socket, time, os
HERE = os.path.dirname(os.path.abspath(__file__))
s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
s.bind(('127.0.0.1', 50000))
while True:
    d, a = s.recvfrom(65535)
    with open(os.path.join(HERE, 'spot.log'), 'a', encoding='utf8') as f:
        f.write(time.strftime('%H:%M:%S ') + d.decode('utf8', 'replace') + '\n')
