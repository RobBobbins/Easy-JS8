"""W47: Cumulative spectrum level, the app against JS8Call 3.0.3 on this PC, the same audio into both. cumchk.py <run>
Audio: 1000 Hz tone (300 counts) + noise (300 counts rms), 12 kHz. JS8Call gets it through VBMatrix In 4 (its input is
VBMatrix Out 4; the route's gain is measured first and the app's copy scaled to match); the app gets it through the
decoder ring (DebugJs8Receiver), which the waterfall reads. Both: Normal, Cumulative, Avg 1, Start 700, Bins Per Pixel 2,
spectrum gain 0 / zero 0. Each plot's level is read against its 6 dotted lines (10 dB apart at gain 0, CPlotter
VERT_DIVS 7): value = 63 - 10 x (row - area top) / line spacing. Pictures: shots/cum_app_<run>.png, shots/cum_pc_<run>.png.
The ELINK profile's [WideGraph] lines are put back at the end."""
import sys as _s
exec(open('wlink.py', encoding='utf8').read().split('\ndef watch(')[0])
import ctypes, shutil

SR = 12000; F = 1000
# both read at the same second of the minute: the Cumulative count (dataSink m_ihsym) follows the minute
S = {1: 28.5, 2: 43.5}.get(int(_run), 13.5)


def until(s):
    time.sleep((s - time.time() % 60) % 60)


INI = os.path.expandvars(r'%LOCALAPPDATA%/JS8Call - ELINK/JS8Call - ELINK.ini')
rng = np.random.default_rng(int(_run))
t12 = np.arange(SR * 15) / SR
x12 = 300 * np.sin(2 * np.pi * F * t12) + rng.normal(0, 300, len(t12))


def up48(a):
    """12 kHz to 48 kHz without adding anything above 6 kHz (FFT zero padding)."""
    s = np.fft.rfft(a)
    return np.fft.irfft(np.r_[s, np.zeros(len(a) * 2 - len(s) + 1)], len(a) * 4) * 4


x48 = (up48(x12) / 32768).astype('float32')


def route_gain():
    """Play into VBMatrix In 4, record VBMatrix Out 4 (JS8Call's input) at the same time."""
    ri, rr, rc = dev('VBMatrix Out 4', False)
    got = sd.playrec(np.tile(x48[:48000 * 3, None], (1, L_.pc)), samplerate=48000, device=(ri, L_.pi), channels=rc, dtype='float32', blocking=True)
    g = got[48000:, 0]; p = x48[48000:48000 * 3]
    return float(np.sqrt((g ** 2).mean() / (p ** 2).mean()))


def save(a, name):
    Image.fromarray(a.astype('uint8')).save(f'shots/{name}.png')


def pc_setup():
    if not os.path.exists('shots/elink_backup.ini'):  # a run that stopped early leaves the original here
        shutil.copy(INI, 'shots/elink_backup.ini')
    t_ = open(INI, encoding='utf8').read()
    for k, v in (('Percent2D', '40'), ('WaterfallSpectrum', r'@Variant(\0\0\0\x7f\0\0\0\tSpectrum\0\0\0\0\x1)'), ('Plot2dGain', '0'), ('Plot2dZero', '0'),
                 ('BinsPerPixel', '2'), ('StartFreq', '700'), ('WaterfallAvg', '1')):
        t_ = re.sub(rf'(?m)^{k}=.*$', lambda m_: f'{k}={v}', t_)
    open(INI, 'w', encoding='utf8', newline='').write(t_)
    pcall('K4PCT'); pc.send('MODE.SET_SPEED', SPEED=0)


def pc_restore():
    subprocess.run(['powershell', '-c', 'Stop-Process -Name JS8Call -Force'], capture_output=True); time.sleep(2)
    keep = open('shots/elink_backup.ini', encoding='utf8').read().split('[WideGraph]')[1].split('\n[')[0]
    t_ = open(INI, encoding='utf8').read()
    head, rest = t_.split('[WideGraph]'); tail = rest.split('\n[', 1)
    open(INI, 'w', encoding='utf8', newline='').write(head + '[WideGraph]' + keep + ('\n[' + tail[1] if len(tail) > 1 else ''))
    os.remove('shots/elink_backup.ini')
    pcall('K4PCT')


def pc_shot():
    """JS8Call's window drawn into a bitmap (PrintWindow), so other windows on top don't matter; minimised again."""
    import win32gui, win32ui
    u = ctypes.windll.user32; ctypes.windll.shcore.SetProcessDpiAwareness(2)
    hw = []
    cb = ctypes.WINFUNCTYPE(ctypes.c_bool, ctypes.c_void_p, ctypes.c_void_p)(
        lambda h, _: (hw.append(h) if u.IsWindowVisible(h) and 'JS8Call' in _title(h) and 'ELINK' in _title(h) else None) or True)
    u.EnumWindows(cb, 0)

    def area(h_):
        r_ = ctypes.wintypes.RECT(); u.GetWindowRect(h_, ctypes.byref(r_)); return (r_.right - r_.left) * (r_.bottom - r_.top)
    h = max(hw, key=area); u.ShowWindow(h, 4); time.sleep(1.5)  # shown without taking the focus
    l_, t_, r_, b_ = win32gui.GetWindowRect(h); w, hh = r_ - l_, b_ - t_
    wdc = win32gui.GetWindowDC(h); dc = win32ui.CreateDCFromHandle(wdc); mdc = dc.CreateCompatibleDC()
    bmp = win32ui.CreateBitmap(); bmp.CreateCompatibleBitmap(dc, w, hh); mdc.SelectObject(bmp)
    u.PrintWindow(h, mdc.GetSafeHdc(), 2)
    raw = bmp.GetBitmapBits(True)
    a = np.frombuffer(raw, 'uint8').reshape(hh, w, 4)[..., 2::-1].astype(int)
    win32gui.DeleteObject(bmp.GetHandle()); mdc.DeleteDC(); dc.DeleteDC(); win32gui.ReleaseDC(h, wdc)
    dpr = u.GetDpiForWindow(h) / 96
    u.ShowWindow(h, 6)
    return a, dpr


def _title(h):
    b = ctypes.create_unicode_buffer(256); ctypes.windll.user32.GetWindowTextW(h, b, 256); return b.value


import ctypes.wintypes


def level(a, x0, x1, y0, y1, fx_):
    """Cumulative value (dB) at the tone and the noise median, from the cyan line against the dotted lines."""
    sp = a[y0:y1, x0:x1]
    r, g, b = sp[..., 0], sp[..., 1], sp[..., 2]
    # app DKGRAY #444444; PC darkGray #808080, its dots blended with the blue gradient
    grey = ((abs(r - g) < 12) & (abs(g - b) < 12) & (r > 50) & (r < 170)) | ((abs(r - 128) < 30) & (abs(g - 128) < 30) & (abs(b - 128) < 30))
    rows = np.where(grey.sum(axis=1) > (x1 - x0) * .15)[0]
    lines = [int(v) for i, v in enumerate(rows) if i == 0 or v - rows[i - 1] > 2]
    if len(lines) < 4:
        return None, None, lines
    d = float(np.median(np.diff(lines))); top = lines[0] - d
    cy = tops((g > 170) & (b > 170) & (r < 90))
    val = 63 - 10 * (cy - top) / d
    peak = float(np.nanmax(val))  # the tone is the line's highest point; the noise is its median
    noise = float(np.nanmedian(val))
    return round(peak, 1), round(noise, 1), lines


def app_part(gain):
    appup(0, **{**WFP, 'js8cfg_wf_bpp_upright': 2, 'js8cfg_wf_decode_attempts': False, 'js8cfg_wf_start': 700})
    wb = wf_full()
    pcm = np.clip(np.tile(x12, 2) * gain, -32767, 32767).astype('<i2')
    open('shots/cum.pcm', 'wb').write(pcm.tobytes())
    adb('push', 'shots/cum.pcm', '/data/local/tmp/cum.pcm')
    adb('shell', f"run-as {PKG} sh -c 'cat /data/local/tmp/cum.pcm > files/cum.pcm'")
    c = S // 15 * 15
    until((c - 20) % 60)  # mixed in from the next 15 s start (c - 15), 30 s long, so the target cycle's spectra are all tone
    adb('shell', 'am', 'broadcast', '-n', sig.RX, '--es', 'file', 'cum.pcm', '--ei', 'period', '15')
    until(S)
    a = grab(); save(a, f'cum_app_{_run}')
    x0, y0, x1, y1 = wb; h = y1 - y0
    fxa = lambda f: x0 + int(((f - 700) / (2 * 1500 / 2048) + 0.5) * 3)
    return level(a, x0, x1, y1 - int(h * .42), y1, fxa)


def pc_part():
    """Tone looped into JS8Call; captured at second S of the minute."""
    pos = [0]

    def cb(o, n, ti, st):
        i = (pos[0] + np.arange(n)) % len(x48); pos[0] += n
        o[:] = x48[i, None]
    with sd.OutputStream(device=L_.pi, samplerate=48000, channels=L_.pc, dtype='float32', callback=cb):
        time.sleep(17); until(S - 1.5)  # pc_shot waits 1.5 s: drawn at second S
        a, dpr = pc_shot()
    save(a, f'cum_pc_{_run}')
    # the plotter: the widest run of the dark-blue-to-black gradient with dotted lines; found from the cyan line's columns
    cyan = (a[..., 1] > 170) & (a[..., 2] > 170) & (a[..., 0] < 90)
    ys, xs = np.where(cyan)
    if not len(xs):
        return None, None, []
    x0, x1 = int(np.percentile(xs, 1)), int(np.percentile(xs, 99))
    col = a[:, (x0 + x1) // 2]
    dark = (col.max(axis=1) < 140) | cyan[:, (x0 + x1) // 2]
    yc = int(np.median(ys)); y0 = yc; y1 = yc
    while y0 > 0 and dark[y0 - 1]: y0 -= 1
    while y1 < len(dark) - 1 and dark[y1 + 1]: y1 += 1
    fxp = lambda f: x0 + int(((f - 700) / (2 * 1500 / 2048)) * dpr + 0.5)
    return level(a, x0, x1, y0, y1, fxp)


def w47():
    g = route_gain()
    pc_setup()
    try:
        pc_ = pc_part()
        app = app_part(g)
    finally:
        pc_restore()
    ok_ = None not in (app[0], pc_[0]) and abs(app[0] - pc_[0]) <= 2 and abs(app[1] - pc_[1]) <= 2
    return ok_, f'route gain {g:.3f}; tone dB app {app[0]} PC {pc_[0]}; noise dB app {app[1]} PC {pc_[1]}; lines app {app[2]} PC {pc_[2]}'


row('W47', w47)
adb('shell', 'settings', 'put', 'system', 'user_rotation', '0')
json.dump(out, open(f'cumchk.{_run}.json', 'w'))
print('DONE', flush=True)
