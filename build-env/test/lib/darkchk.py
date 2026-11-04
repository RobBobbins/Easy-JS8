"""W40: the phone in dark mode: main screen in all four layouts, Settings window, Station > Radio and UI, Log QSO.
Screenshots only; the pass is judged on them. darkchk.py <run>"""
import sys as _s
exec(open('ui8.py', encoding='utf8').read().split('\ndef h35():')[0].replace('"""Upright rows: H35, I04, K, L09, L15. ui8.py <run> [ID,ID]"""', ''))
VAL = {'U': 'pc', 'P': 'pc', 'G': 'swipe', 'F': 'ft8call'}


def night(on):
    adb('shell', 'cmd', 'uimode', 'night', 'yes' if on else 'no'); time.sleep(2)


def w40():
    global L
    night(True)
    try:
        for code in ('U', 'P', 'G', 'F'):
            up(landscape_layout=VAL[code]); L = code; rotate(code); time.sleep(3)
            shot(f'w40_{code}_main_{_run}')
        L = 'U'; rotate('U'); home(); top()
        tp('js8_btn_settings', 2.5); shot(f'w40_U_settings_{_run}')
        home(); top(); open_station(); tapk('Radio', 2); shot(f'w40_U_radio_{_run}')
        tapk('Cancel', 1); home(); top(); open_station(); tapk('UI', 2); shot(f'w40_U_ui_{_run}')
        tapk('Cancel', 1); home(); top(); tp('js8_btn_log', 2.5); shot(f'w40_U_log_{_run}')
        tapk('Cancel', 1)
        return True, 'screenshots w40_*; judged by eye'
    finally:
        night(False)


row('W40', w40)
json.dump(out, open(f'darkchk.{_run}.json', 'w'))
print('DONE', flush=True)
