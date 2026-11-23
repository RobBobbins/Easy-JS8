"""Probe: does the upright waterfall's scale change around W164/W165? Prefs and the scale read before and after
one audio decode and two injected decodes. wlprobe.py <run>"""
import sys as _s
_s.argv = ['wflabel', _s.argv[1]]
src = open('wflabel.py', encoding='utf8').read().split("\nprev_ft8 = ")[0]
exec(src)


def scale():
    p = getprefs()
    return [l.strip() for l in p.splitlines() if 'js8cfg_wf_start' in l or 'js8cfg_wf_bpp' in l],         [t for t in texts() if re.fullmatch(r'\d{3,4}', t)][:6]


with part('probe', 90):
    lay('U', **{KEY: True})
    wf_up(); print('A', scale(), flush=True); shot(f'wlp_a_{_run}')
    t = None
    for _ in range(3):
        t = sig.tones(FRAME, 1, 0); time.sleep(3)
        if t:
            break
    sig.play([(t, 1500), (None, 0)], 'wl.pcm', 0); time.sleep(32)
    wf_up(); print('B', scale(), flush=True); shot(f'wlp_b_{_run}')
    inj('K4TYP: VE3TST HELLO THERE', 900); inj('W1AW: @ALLCALL CQ CQ', 2100); time.sleep(3)
    wf_up(); print('C', scale(), flush=True); shot(f'wlp_c_{_run}')
with part('restore', 25):
    setprefs(**{KEY: None, 'psk_reporter': True})
    start()
print('DONE', flush=True)
