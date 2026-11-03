"""W01 to W05: JS8Call on the PC (profile ELINK) sends, the app's upright waterfall is read from screenshots. wlink.py <run> [ID,ID]
The signal is JS8Call's own audio through the tlink.py link: recorded a slot at a time and played into the app's decoder ring,
which the waterfall, spectrum and level meter read too. Pictures: shots/w02_<run>.png, w04_<run>_avg1.png, w04_<run>_avg8.png."""
import sys as _s
import io
exec(open('tlink.py', encoding='utf8').read().split("print('devs'")[0])
from PIL import Image

F0, BW = 1000, 50  # JS8Call's offset, clear of the app's red TX marker at 1500; Normal is 8 tones of 6.25 Hz
WFP = dict(js8cfg_wf_palette='Default', js8cfg_wf_bpp_upright=6, js8cfg_wf_start=None, js8cfg_wf_decode_attempts=True,
           js8cfg_wf_spec_height=40, js8cfg_wf_spectrum=1, js8cfg_wf_avg=1, js8cfg_wf_fps=4, js8cfg_wf_spec_gain=0, js8cfg_wf_spec_zero=0)


def grab():
    return np.asarray(Image.open(io.BytesIO(adb('exec-out', 'screencap', '-p'))).convert('RGB')).astype(int)


def fx(f, wb):
    """Screen x of a frequency in js8_waterfall: Start 500 Hz, 6 PC bins a dp, 3 px a dp (WaterfallRenderer.xFromFreq)."""
    return wb[0] + int(((f - 500) / (6 * 1500 / 2048) + 0.5) * 3)


def tops(m):
    """Row of the highest marked pixel in each column, NaN where none."""
    t = m.argmax(axis=0).astype(float)
    t[~m.any(axis=0)] = np.nan
    return t


def look(a, wb, mb):
    """One screen: meter bar rows, red / cyan bracket pixels near the signal, signal against its sides, spectrum line tops."""
    x0, y0, x1, y1 = wb
    h = y1 - y0
    s0, s1 = fx(F0, wb) - x0, fx(F0 + BW, wb) - x0
    wf = a[y0:y0 + int(h * .58), x0:x1]
    r, g, b = wf[..., 0], wf[..., 1], wf[..., 2]
    near = slice(max(0, s0 - 40), s1 + 40)
    red = int(((r > 200) & (g < 40) & (b < 40))[:, near].sum())
    cyan = int(((r < 40) & (g > 100) & (b > 100) & (abs(g - b) < 30))[:, near].sum())
    lum = wf.mean(axis=2)
    sig = lum[:, s0 + 3:s1 - 3].mean()
    side = np.r_[lum[:, s0 - 45:s0 - 15].ravel(), lum[:, s1 + 15:s1 + 45].ravel()].mean()
    sp = a[y1 - int(h * .40):y1, x0:x1]
    r, g, b = sp[..., 0], sp[..., 1], sp[..., 2]
    ct = tops((r < 90) & (g > 170) & (b > 170))
    gt = tops((g > 200) & (r < 80) & (b < 80))
    noise = np.r_[np.arange(30, s0 - 60), np.arange(s1 + 60, x1 - x0 - 30)]
    rise = float(np.nanmedian(ct[noise]) - np.nanmin(ct[s0:s1])) if np.isfinite(ct[s0:s1]).any() and np.isfinite(ct[noise]).any() else 0.0
    rough = float(np.nanmean(abs(np.diff(gt[noise])))) if np.isfinite(gt[noise]).sum() > 50 else np.nan
    m = a[mb[1]:mb[3], mb[0]:mb[2]]
    sat = (m.max(axis=2) - m.min(axis=2)) > 100
    bar = int((sat.sum(axis=1) >= 3).sum())
    return dict(red=red, cyan=cyan, contrast=round(sig - side, 1), rise=rise, rough=rough, bar=bar)


def wf_full():
    """The whole upright waterfall (244 dp) on screen; the drags are on the level meter, which takes no touches."""
    reach('js8_waterfall')
    for _ in range(4):
        b = bounds('js8_waterfall')
        if b and b[3] - b[1] >= 720:
            return b
        y = min(b[1] + 200, 2100) if b else 2100
        adb('shell', 'input', 'swipe', '90', str(y), '90', str(max(150, y - 900)), '400'); time.sleep(1.5)
    return bounds('js8_waterfall')


def watch(text, **k):
    """JS8Call sends [text]; screens read from the send until 4 s after the app decodes it (75 s at most)."""
    appup(0, **{**WFP, **k})
    pc.send('RIG.SET_FREQ', DIAL=10130000, OFFSET=F0)
    wb, mb = wf_full(), bounds('js8_level_meter')
    q = look(grab(), wb, mb)
    n0 = len(L_.ev)
    pc.send('TX.SEND_MESSAGE', text)
    shots_, done, end = [], None, time.time() + 75
    while time.time() < end:
        a = grab()
        shots_.append((look(a, wb, mb), a))
        if done is None and heard(L_.ev, text, .05, n0):
            done = time.time()
        if done and time.time() > done + 4:
            break
    return q, shots_, done


def save(a, name):
    Image.fromarray(a.astype('uint8')).save(f'shots/{name}.png')


_A = {}


def pass_a():
    """Cumulative spectrum, Decode Attempts on, Avg 1: one transmission for W01, W02, W03 and W05."""
    if not _A:
        q, s_, done = watch('HELLO WATERFALL')
        best = max(s_, key=lambda p: p[0]['contrast'])
        save(best[1], f'w02_{_run}')
        _A.update(q=q, s=[p[0] for p in s_], done=done, best=best[0])
    return _A


def w01():
    a = pass_a(); red = max(p['red'] for p in a['s']); cyan = max(p['cyan'] for p in a['s'])
    return a['done'] is not None and red > a['q']['red'] + 20, f"decoded {a['done'] is not None}, red px {a['q']['red']}->{red}, cyan px {a['q']['cyan']}->{cyan}"


def w02():
    a = pass_a()
    return a['best']['contrast'] > 40, f"signal minus sides {a['q']['contrast']}->{a['best']['contrast']} (shots/w02_{_run}.png)"


def w03():
    a = pass_a(); rise = max(p['rise'] for p in a['s'])
    return rise > 15, f"Cumulative line above its noise by {a['q']['rise']}->{rise} px"


def w05():
    a = pass_a(); bar = max(p['bar'] for p in a['s'])
    return bar > a['q']['bar'] + 30, f"meter bar rows {a['q']['bar']}->{bar}"


def w04():
    """Current spectrum (green) with Avg 1, then Avg 8: the line's step from column to column, read while the signal is in."""
    r = {}
    for n in (1, 8):
        q, s_, done = watch(f'HELLO AVG {n}', js8cfg_wf_spectrum=0, js8cfg_wf_avg=n)
        live = [p for p in s_ if p[0]['bar'] > q['bar'] + 30 and np.isfinite(p[0]['rough'])]
        if live:
            save(live[len(live) // 2][1], f'w04_{_run}_avg{n}')
        r[n] = round(float(np.median([p[0]['rough'] for p in live])), 2) if live else np.nan
    return bool(r[8] < r[1] * .8), f'line step Avg 1 {r[1]} px, Avg 8 {r[8]} px'


pcall('K4PCT')
row_list = [('W01', w01), ('W02', w02), ('W03', w03), ('W05', w05), ('W04', w04)]
for i, f in row_list:
    row(i, f)
adb('shell', 'settings', 'put', 'system', 'user_rotation', '0')
json.dump(out, open(f'wlink.{_run}.json', 'w'))
print('DONE', flush=True)
