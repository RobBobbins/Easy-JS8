"""Fake rig servers on this PC, nothing leaves it. rigctl 4599, DX Lab 52002, HRD v5 7809. Logs to fakes.log."""
import socket, struct, threading, time, re

LOG = open('fakes.log', 'a', buffering=1)
def log(*a):
    LOG.write(time.strftime('%H:%M:%S') + '.%03d ' % (time.time() % 1 * 1000) + ' '.join(str(x) for x in a) + '\n')

def serve(port, handler):
    s = socket.socket(); s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    s.bind(('0.0.0.0', port)); s.listen(5)
    def loop():
        while True:
            c, _ = s.accept()
            threading.Thread(target=handler, args=(c,), daemon=True).start()
    threading.Thread(target=loop, daemon=True).start()

R = dict(f=10136000, split=0, tx=10137000, mode='USB', xmode='USB', ptt=0, vfo='VFOA')

def rigctl(c):
    f = c.makefile('rw', newline='\n')
    for line in f:
        cmd = line.strip()
        if not cmd: continue
        log('rigctl', cmd)
        p = cmd.split()
        if p[0] == '\\dump_state':
            out = ("1\n2\n2\n150000.000000 1500000000.000000 0x1ff -1 -1 0x10000003 0x3\n0 0 0 0 0 0 0\n"
                   "150000.000000 1500000000.000000 0x1ff 5000 100000 0x10000003 0x3\n0 0 0 0 0 0 0\n"
                   "0x1ff 1\n0 0\n0x1ff 2400\n0 0\n0\n0\n0\n0\n0\n0\n0x0\n0x0\n0x0\n0x0\n0x0\n0x0\nvfo_ops=0x0\nptt_type=0x1\ndone\n")
        elif p[0] == '\\chk_vfo': out = '0\n'
        elif p[0] == '\\get_powerstat': out = '1\n'
        elif p[0] == 'f': out = f"{R['f']}\n"
        elif p[0] == 'F': R['f'] = int(float(p[1])); out = 'RPRT 0\n'
        elif p[0] == 'v': out = R['vfo'] + '\n'
        elif p[0] == 's': out = f"{R['split']}\nVFOB\n"
        elif p[0] == 'S': R['split'] = int(p[1]); out = 'RPRT 0\n'
        elif p[0] == 'i': out = f"{R['tx']}\n"
        elif p[0] == 'I': R['tx'] = int(float(p[1])); out = 'RPRT 0\n'
        elif p[0] == 'm': out = f"{R['mode']}\n2400\n"
        elif p[0] == 'M': R['mode'] = p[1]; out = 'RPRT 0\n'
        elif p[0] == 'X': R['xmode'] = p[1]; out = 'RPRT 0\n'
        elif p[0] == 't': out = f"{R['ptt']}\n"
        elif p[0] == 'T': R['ptt'] = int(p[1]); out = 'RPRT 0\n'
        else: out = 'RPRT 0\n'
        try:
            f.write(out); f.flush()
        except OSError:
            return

D = dict(f=10136000, tx=0, split=False, mode='USB', tx_on=False)
def khz(hz): return f"{hz/1e3:,.3f}".rjust(10)
def tag(n, v): return f"<{n}:{len(v)}>{v}"

def dxlab(c):
    while True:
        try: b = c.recv(4096)
        except OSError: return
        if not b: return
        t = b.decode('latin1'); log('dxlab', t)
        m = re.match(r'<command:\d+>(\w+)<parameters:\d+>(.*)', t, re.S)
        if not m: continue
        cmd, par = m.groups()
        v = dict(re.findall(r'<(\w+):\d+>([^<]*)', par))
        if cmd == 'CmdGetFreq': c.send(tag('CmdFreq', khz(D['f'])).encode())
        elif cmd == 'CmdSetFreq': D['f'] = int(float(v['xcvrfreq'].replace(',', '')) * 1000)
        elif cmd == 'CmdQSXSplit': D['tx'] = int(float(v['xcvrfreq'].replace(',', '')) * 1000); D['split'] = True
        elif cmd == 'CmdSplit': D['split'] = False
        elif cmd == 'CmdTX': D['tx_on'] = True
        elif cmd == 'CmdRX': D['tx_on'] = False
        elif cmd == 'CmdSendTx': c.send(tag('CmdTX', 'ON' if D['tx_on'] else 'OFF').encode())
        elif cmd == 'CmdSetMode': D['mode'] = v['1']
        elif cmd == 'CmdSendSplit': c.send(tag('CmdSplit', 'ON' if D['split'] else 'OFF').encode())
        elif cmd == 'CmdGetTXFreq': c.send(tag('CmdTXFreq', khz(D['tx'])).encode())
        elif cmd == 'CmdSendMode': c.send(tag('CmdMode', D['mode']).encode())

H = dict(a=10136000, b=10136000, mode='USB', split=0, tx=0)
BTN = ['TX', 'Spl On', 'VFO A', 'VFO B']
DD = {'Mode': ['LSB', 'USB', 'CW', 'DATA-U']}

def hrd_answer(cmd):
    cmd = re.sub(r'^\[\d+\] ', '', cmd)
    if cmd == 'get context': return '1'
    if cmd == 'get radios': return '1:TS-2000'
    if cmd == 'get radio': return 'TS-2000'
    if cmd == 'get vfo-count': return '2'
    if cmd == 'get buttons': return ','.join(BTN)
    if cmd == 'get dropdowns': return ','.join(DD)
    if cmd.startswith('get dropdown-list'): return ','.join(DD['Mode'])
    if cmd.startswith('get dropdown-text'): return 'Mode: ' + H['mode']
    if cmd == 'get frequencies': return f"{H['a']}-{H['b']}"
    if cmd == 'get frequency': return str(H['a'])
    if cmd.startswith('get button-select Spl~On'): return str(H['split'])
    if cmd.startswith('get button-select TX'): return str(H['tx'])
    if cmd.startswith('get button-select'): return '0'
    p = cmd.split()
    if cmd.startswith('set frequencies-hz'): H['a'], H['b'] = int(p[2]), int(p[3]); return 'OK'
    if cmd.startswith('set frequency-hz'): H['a'] = int(p[2]); return 'OK'
    if cmd.startswith('set button-select Spl~On'): H['split'] = int(p[3]); return 'OK'
    if cmd.startswith('set button-select TX'): H['tx'] = int(p[3]); return 'OK'
    if cmd.startswith('set button-select'): return 'OK'
    if cmd.startswith('set dropdown'): H['mode'] = p[3].replace('~', ' '); return 'OK'
    return 'ERROR'

def hrd(c):
    buf = b''
    while True:
        try: b = c.recv(4096)
        except OSError: return
        if not b: return
        buf += b
        while len(buf) >= 16:
            size, m1, m2, _ = struct.unpack('<IIII', buf[:16])
            if len(buf) < size: break
            text = buf[16:size].decode('utf-16-le').rstrip('\0'); buf = buf[size:]
            ans = hrd_answer(text); log('hrd', text, '->', ans)
            pay = (ans + '\0').encode('utf-16-le')
            c.send(struct.pack('<IIII', 16 + len(pay), m1, m2, 0) + pay)

serve(4599, rigctl); serve(52002, dxlab); serve(7809, hrd)
log('fakes up')
while True:
    time.sleep(3600)
