"""T rows: real JS8Call 3.0.2 on this PC (profile ELINK, no rig, TCP 2443) against the app. tlink.py <run> [ID,ID]
PC to app: JS8Call's audio on VBMatrix Out 8 is recorded a slot at a time and mixed into the app's decoder.
App to PC: the tones of each TX.FRAME are played into VBMatrix In 4, JS8Call's input, in the next slot."""
import sys as _s
import atexit, json as _j, queue, threading
import numpy as np, sounddevice as sd
exec(open('ui7.py', encoding='utf8').read().split("row_list = [")[0].replace('"""Upright rows never tested: H, I, J. ui7.py <run> [ID,ID]"""', ''))
import sig
sig.MODES[4] = (3840, 3.125, 30, 0.5)
PERIOD = {0: 15, 1: 10, 2: 6, 8: 4, 4: 30}
M = 'VE3TST'


def dev(word, out_):
    apis = sd.query_hostapis()
    w = next(i for i, a in enumerate(apis) if 'WASAPI' in a['name'])
    for i, d in enumerate(sd.query_devices()):
        if d['hostapi'] == w and word in d['name'] and d['max_output_channels' if out_ else 'max_input_channels'] > 0:
            return i, int(d['default_samplerate']), d['max_output_channels' if out_ else 'max_input_channels']


def heard(ev, word, wait, n0=0):
    """The word in the frames joined: a message comes in one event a frame."""
    w = word.replace(' ', '')
    end = time.time() + wait
    while time.time() < end:
        t_ = ''.join(str(e.get('value', '')) + '|' if e.get('type') in ('RX.DIRECTED',) else str(e.get('params', {}).get('TEXT', e.get('value', ''))) for e in ev[n0:] if str(e.get('type', '')).startswith('RX.'))
        if w in t_.replace(' ', ''):
            return True
        time.sleep(.5)
    return False


class PC:
    def __init__(self):
        self.s = socket.create_connection(('127.0.0.1', 2443), 5); self.ev = []
        threading.Thread(target=self.read, daemon=True).start()

    def read(self):
        b = b''
        while True:
            try:
                b += self.s.recv(65536)
            except OSError:
                return
            while b'\n' in b:
                l, b = b.split(b'\n', 1)
                try:
                    self.ev.append(_j.loads(l))
                except ValueError:
                    pass

    def send(self, t, v='', **p):
        self.s.send((_j.dumps({'type': t, 'value': v, 'params': {'_ID': int(time.time() * 1000) % 2000000000, **p}}) + '\n').encode())
        time.sleep(.3)

    def lines(self):
        return [l for l in open(os.path.expandvars(r'%LOCALAPPDATA%\JS8Call - ELINK\ALL.TXT'), encoding='utf8', errors='replace').read().split('\n') if 'Transmitting' not in l and len(l) > 40]

    def heard(self, word, wait, n0=0):
        """JS8Call's own ALL.TXT, the frames joined."""
        w = word.replace(' ', ''); base = self.base
        end = time.time() + wait
        while time.time() < end:
            if w in ''.join(l[50:] for l in self.lines()[base:]).replace(' ', ''):
                return True
            time.sleep(1)
        return False


class Link:
    """Both audio directions, running for the whole script."""
    def __init__(self):
        self.ev = []; self.period = 10; self.sub = 1; self.q = queue.Queue(); self.fed = 0; self.frames = 0
        self.ri, self.rr, self.rc = dev('VBMatrix Out 8', False)
        self.pi, self.pr, self.pc = dev('VBMatrix In 4', True)
        self.record()
        time.sleep(2)
        threading.Thread(target=self.tones, daemon=True).start()
        self.done = False; self.pt = threading.Thread(target=self.play, daemon=True); self.pt.start()
        # sounddevice stops every stream at exit; end the play thread first, or its next write
        # prints "Exception in thread (play): Stream is stopped" (registered later = runs first)
        atexit.register(self.quit)

    def quit(self):
        self.done = True; self.pt.join(20)

    def record(self):
        """Every sample kept with its place in time, so a slot is cut exactly at its start."""
        lp = np.hanning(int(self.rr / 3000) | 1); lp /= lp.sum()
        ring = np.zeros(self.rr * 70, 'float32'); st_ = {'n': 0, 't0': None}

        def cb(d, n, ti, status):
            if st_['t0'] is None:
                st_['t0'] = time.time() - n / self.rr
            i = st_['n'] % len(ring); a = d.mean(axis=1) if self.rc > 1 else d[:, 0]
            k = min(n, len(ring) - i); ring[i:i + k] = a[:k]; ring[:n - k] = a[k:]
            st_['n'] += n

        self.rs = sd.InputStream(device=self.ri, samplerate=self.rr, channels=self.rc, dtype='float32', callback=cb)
        self.rs.start()
        threading.Thread(target=self.cut, args=(ring, st_, lp), daemon=True).start()

    def cut(self, ring, st_, lp):
        if True:
            while True:
                p = self.period
                time.sleep(p - time.time() % p)
                end = time.time() // p * p
                time.sleep(.15)
                i1 = int((end - st_['t0']) * self.rr); i0 = i1 - int(p * self.rr)
                if i0 < 0 or p != self.period:
                    continue
                a = ring[np.arange(i0, i1) % len(ring)]
                if np.abs(a).max() < .01:
                    continue
                x12 = np.interp(np.arange(0, len(a) * 12000 / self.rr) * self.rr / 12000, np.arange(len(a)), np.convolve(a, lp, 'same'))
                pcm = (x12 / np.abs(x12).max() * 9000 + np.random.normal(0, 300, len(x12))).astype('int16')
                name = f'pc{self.fed % 2}.pcm'; self.fed += 1
                open('shots/' + name, 'wb').write(pcm.tobytes())
                adb('push', 'shots/' + name, '/data/local/tmp/' + name)
                adb('shell', f"run-as {PKG} sh -c 'cat /data/local/tmp/{name} > files/{name}'")
                adb('shell', 'am', 'broadcast', '-n', sig.RX, '--es', 'file', name, '--ei', 'period', str(p))

    def tones(self):
        while True:
            try:
                adb('forward', 'tcp:2442', 'tcp:2442')
                s_ = socket.create_connection(('127.0.0.1', 2442), 5); s_.settimeout(None)
                b = b''
                while True:
                    d = s_.recv(65536)
                    if not d:
                        break
                    b += d
                    while b'\n' in b:
                        l, b = b.split(b'\n', 1)
                        try:
                            e = _j.loads(l)
                        except ValueError:
                            continue
                        self.ev.append(e)
                        if e.get('type') == 'TX.FRAME':
                            self.frames += 1; self.q.put(e['params']['TONES'])
            except OSError:
                time.sleep(1)

    def wave(self, tn, f0):
        nsps, df, per, delay = sig.MODES[self.sub]
        sr = self.pr; n = int(nsps * sr / 12000)
        ph = 2 * np.pi * np.cumsum(np.repeat(f0 + np.array(tn) * df, n) / sr)
        w = (.25 * np.sin(ph)).astype('float32')
        return np.tile(w[:, None], (1, self.pc))

    def play(self):
        with sd.OutputStream(device=self.pi, samplerate=self.pr, channels=self.pc, dtype='float32') as o:
            while not self.done:
                try:
                    tn = self.q.get(timeout=.05)
                except queue.Empty:
                    o.write(np.zeros((self.pr // 20, self.pc), 'float32')); continue
                p = self.period; delay = sig.MODES[self.sub][3]
                wait = p - time.time() % p + delay - .05
                if p - wait + delay > .4 and wait < p:
                    pass
                while wait > .06 and not self.done:
                    o.write(np.zeros((self.pr // 20, self.pc), 'float32'))
                    wait = (p - time.time() % p + delay - .05) % p
                o.write(self.wave(tn, self.f0))
                time.sleep(.02)


pc = PC()
L_ = Link()
L_.f0 = 1500


def appup(sub, **k):
    """Autoreply on, both ends on one speed. Fresh app run; API server needs no connection until now."""
    up(tx_submode=sub, autoreply_enabled=True, js8cfg_autoreply_startup=True, js8cfg_transmit_directed=True, js8cfg_autoreply_confirmation=False, js8cfg_tx_offset='1500', js8cfg_tcp_enabled=True, js8cfg_tcp_accept=True, js8cfg_tcp_max_connections=4, **k)
    L_.period = PERIOD[sub]; L_.sub = sub; L_.n0 = len(L_.ev); pc.base = len(pc.lines())
    pc.send('MODE.SET_SPEED', SPEED=sub); pc.send('RIG.SET_FREQ', DIAL=10130000, OFFSET=1500)
    time.sleep(2)


def seen_app(word, wait, base):
    return heard(L_.ev, word, wait, L_.n0)


def pc_to_app(sub, text='HELLO FROM PC', word='HELLO FROM PC', wait=None):
    appup(sub); b = len(alltxt())
    pc.send('TX.SEND_MESSAGE', text)
    r = seen_app(word, wait or PERIOD[sub] * 6 + 10, b)
    return r, f'fed {L_.fed}'


def app_to_pc(sub, text='K4PCT HELLO FROM APP', word='HELLO FROM APP', wait=None):
    appup(sub); n0 = len(pc.ev); f0 = L_.frames
    api('TX.SEND_MESSAGE', text, wait=.3)
    r = pc.heard(word, wait or PERIOD[sub] * 6 + 10, n0)
    return r, f'{L_.frames - f0} frames'


def t10():
    appup(1); n0 = len(pc.ev)
    reach('js8_btn_cq'); holdk('js8_btn_cq'); tapk('Send CQ Now', .5)
    return pc.heard('CQ', 40, n0), ''


def t11():
    appup(1); n0 = len(pc.ev)
    reach('js8_btn_hb'); holdk('js8_btn_hb'); tapk('Send Heartbeat Now', .5)
    return pc.heard('HEARTBEAT', 40, n0) or pc.heard('HB', 1, n0), ''


def t13():
    p = 'FROM APP WITH CHECKSUM'
    return app_to_pc(1, f'K4PCT MSG {p}', 'WITH CHECKSUM', 70)


def t14():
    return pc_to_app(1, 'VE3TST MSG FROM PC WITH CHECKSUM', 'WITH CHECKSUM', 70)


def t15():
    appup(1); b = len(alltxt()); n0 = len(pc.ev)
    pc.send('TX.SEND_MESSAGE', 'VE3TST SNR?')
    r = pc.heard('SNR', 60, n0)
    return r, [l[-40:] for l in alltxt()[b:] if 'Transmitting' in l][:2]


def t16():
    appup(1); b = len(alltxt())
    api('TX.SEND_MESSAGE', 'K4PCT SNR?', wait=.3)
    return seen_app('SNR', 70, b), ''


def pcall(call):
    """JS8Call's API cannot change the call: its test profile is restarted with the new one."""
    p_ = os.path.expandvars(r'%LOCALAPPDATA%/JS8Call - ELINK/JS8Call - ELINK.ini')
    subprocess.run(['powershell', '-c', 'Stop-Process -Name JS8Call -Force'], capture_output=True); time.sleep(2)
    t_ = open(p_, encoding='utf8').read()
    open(p_, 'w', encoding='utf8', newline='').write(re.sub(r'(?m)^MyCall=.*$', 'MyCall=' + call, t_))
    subprocess.run(['powershell', '-c', "Start-Process 'C:/Program Files/JS8Call/JS8Call.exe' -ArgumentList '--rig-name','ELINK' -WindowStyle Minimized"], capture_output=True)
    time.sleep(10); pc.__init__(); pc.base = len(pc.lines())
    pc.send('MODE.SET_SPEED', SPEED=1)


def t17():
    a = app_to_pc(1, 'K4PCT HELLO COMPOUND', 'COMPOUND', 50) if False else None
    appup(1, callsign='VE3TST/P'); n0 = len(pc.ev)
    api('TX.SEND_MESSAGE', 'K4PCT HELLO COMPOUND', wait=.3)
    r1 = pc.heard('VE3TST/P', 50, n0)
    up(callsign='VE3TST', tx_submode=1, autoreply_enabled=True, js8cfg_autoreply_startup=True, js8cfg_transmit_directed=True)
    time.sleep(2)
    pcall('K4PCT/P'); L_.n0 = len(L_.ev)
    pc.send('TX.SEND_MESSAGE', 'VE3TST HELLO BACK')
    r2 = heard(L_.ev, 'K4PCT/P', 50, L_.n0)
    pcall('K4PCT')
    return r1 and r2, f'app to PC {r1}, PC to app {r2}'


def t18():
    r1 = app_to_pc(1, '@TESTG HELLO GROUP', 'HELLO GROUP', 50)[0]
    r2 = pc_to_app(1, '@TESTG HI GROUP', 'HI GROUP', 50)[0]
    return r1 and r2, f'app to PC {r1}, PC to app {r2}'


print('devs', L_.ri, L_.rr, L_.rc, L_.pi, L_.pr, L_.pc, sd.query_devices(L_.ri)['name'], sd.query_hostapis(sd.query_devices(L_.ri)['hostapi'])['name'], flush=True)
row_list = [('T02', lambda: pc_to_app(1)), ('T01', lambda: pc_to_app(0)), ('T03', lambda: pc_to_app(2)), ('T04', lambda: pc_to_app(4)),
            ('T06', lambda: app_to_pc(1)), ('T05', lambda: app_to_pc(0)), ('T07', lambda: app_to_pc(2)), ('T08', lambda: app_to_pc(4)), ('T09', lambda: app_to_pc(8)),
            ('T10', t10), ('T11', t11), ('T12', lambda: app_to_pc(1, 'K4PCT HELLO DIRECTED', 'HELLO DIRECTED')), ('T13', t13), ('T14', t14), ('T15', t15), ('T16', t16), ('T17', t17), ('T18', t18)]
for i, f in row_list:
    row(i, f)
adb('shell', 'settings', 'put', 'system', 'user_rotation', '0')
json.dump(out, open(f'tlink.{_run}.json', 'w'))
print('DONE', flush=True)
