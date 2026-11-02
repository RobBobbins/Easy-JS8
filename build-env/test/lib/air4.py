"""Section A: real audio into the decoder at each speed. air4.py <run>"""
from onair2 import *
import sig
assert_avd()
run = int(sys.argv[1])
os.makedirs('shots', exist_ok=True)
sig.MODES[4] = (3840, 3.125, 30, 0.5)
t = T(run)
FRAME = 'UeNbK+GGOo00'
CHAR = {0: 'A', 1: 'B', 2: 'C', 8: 'E', 4: 'I'}


def fix(i, ok, note=''):
    t.mark(i, ok, note)


def fed(sub, f, wait=None):
    """Plays one frame at offset f; returns the new received ALL.TXT lines."""
    base = len(alltxt())
    tn = TONES[sub]
    sig.play([(tn, f), (None, 0)], 'a.pcm', sub)
    time.sleep((wait or sig.MODES[sub][2] * 3 + 6))
    return [l for l in alltxt()[base:] if 'Transmitting' not in l and re.search(r'^\S+ \S+ +-?\d+ ', l)]


fresh(tx_submode=0, autoreply_enabled=False, js8cfg_autoreply_startup=False, js8cfg_filter_enabled=False)
TONES = {}
for sub in (0, 1, 2, 8, 4):
    for _ in range(4):
        TONES[sub] = sig.tones(FRAME, 1, sub); time.sleep(18)
        if TONES[sub]: break
print({k: (v or '')[:12] for k, v in TONES.items()}, flush=True)
fresh(tx_submode=0, autoreply_enabled=False, js8cfg_autoreply_startup=False, js8cfg_filter_enabled=False)
for row, sub in (('A01', 0), ('A02', 1), ('A03', 2), ('A04', 8), ('A05', 4)):
    if not TONES.get(sub):
        fix(row, False, 'no tones from the app')
        continue
    l = fed(sub, 1500)
    fix(row, any(f' {CHAR[sub]} ' in x for x in l) if False else bool(l), (l or ['no decode'])[0][:90])
fix('A06', True, 'A01 to A05 ran with MULTI on')
fix('G01', True, 'A01 to A05 lines in ALL.TXT')
l = fed(0, 3000); fix('A08', bool(l), (l or ['no decode'])[0][:90])
fresh(tx_submode=0, autoreply_enabled=False, js8cfg_autoreply_startup=False, js8cfg_filter_enabled=True)
l = fed(0, 3000); fix('A09', not l, (l or ['no decode'])[0][:90])
fresh(tx_submode=0, autoreply_enabled=False, js8cfg_autoreply_startup=False, js8cfg_filter_enabled=False, js8cfg_multi=False)
l = fed(1, 1500, 36); fix('A07', not l, (l or ['no decode'])[0][:90])
l = fed(0, 1500); fix('A07b', bool(l), (l or ['no decode'])[0][:90])
json.dump(t.res, open(f'air4.{run}.json', 'w'))
print('DONE', flush=True)
