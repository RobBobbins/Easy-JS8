"""Probe: Pages layout, Waterfall page: the waterfall view's bounds next to the Waterfall Controls. wlg.py <run>"""
import sys as _s
_s.argv = ['wflabel', _s.argv[1]]
exec(open('wflabel.py', encoding='utf8').read().split("\nprev_ft8 = ")[0])
with part('probe G', 30):
    lay('G', **{KEY: True})
    reach('js8_waterfall_view')
    for k in ('js8_waterfall_view', 'js8_wf_tab_control'):
        print(k, re.search(r'bounds="[^"]*"', node(k)).group(0) if node(k) else 'none', flush=True)
with part('restore', 25):
    setprefs(**{KEY: None, 'psk_reporter': True}); start(); rotate('U')
print('DONE', flush=True)
