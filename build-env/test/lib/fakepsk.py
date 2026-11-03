"""Stand-in for report.pskreporter.info: logs each UDP packet on port 4739. Nothing leaves this PC."""
import socket, time
s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM); s.bind(('0.0.0.0', 4739))
log = open('fakepsk.log', 'a', buffering=1)
while True:
    b, a = s.recvfrom(65535)
    log.write(f"{time.strftime('%H:%M:%S')} {a} {len(b)} {b!r}\n")
