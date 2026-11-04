"""W42-W44: API grid / info / status kept after a restart; API answers with the screen off; the Pages screen follows
API changes. apichk.py <run> [ID,ID]"""
import sys as _s
exec(open('ui8.py', encoding='utf8').read().split('\ndef h35():')[0].replace('"""Upright rows: H35, I04, K, L09, L15. ui8.py <run> [ID,ID]"""', ''))


def val(typ, want, wait=1.5):
    for r in api(typ, wait=wait):
        if r.get('type') == want:
            return r.get('value')
    return None


def api_up(secs=30):
    end = time.time() + secs
    while time.time() < end:
        try:
            if val('STATION.GET_CALLSIGN', 'STATION.CALLSIGN', 1):
                return True
        except OSError:
            pass
        time.sleep(1)
    return False


def w42():
    """PC: STATION.SET_GRID / SET_INFO / SET_STATUS with Allow setting station information; the app keeps them (js8cfg_api_dyn_*)."""
    up(js8cfg_api_dynamic_info=True); api_up()
    api('STATION.SET_GRID', 'FN03KR', wait=1); api('STATION.SET_INFO', f'APIINFO{_run}', wait=1); api('STATION.SET_STATUS', f'APISTATUS{_run}', wait=1)
    before = (val('STATION.GET_GRID', 'STATION.GRID'), val('STATION.GET_INFO', 'STATION.INFO'), val('STATION.GET_STATUS', 'STATION.STATUS'))
    stop(); time.sleep(2); start(); time.sleep(4); api_up()
    after = (val('STATION.GET_GRID', 'STATION.GRID'), val('STATION.GET_INFO', 'STATION.INFO'), val('STATION.GET_STATUS', 'STATION.STATUS'))
    return before == ('FN03KR', f'APIINFO{_run}', f'APISTATUS{_run}') and after == before, f'before {before}, after restart {after}'


def w43():
    """Screen off for 30 s (KEYCODE_SLEEP): API still answers, then the screen comes back to the app."""
    up(); api_up()
    adb('shell', 'input', 'keyevent', 'KEYCODE_SLEEP'); time.sleep(30)
    awake = 'mWakefulness=Awake' in adb('shell', 'dumpsys', 'power').decode('utf8', 'replace')
    call = val('STATION.GET_CALLSIGN', 'STATION.CALLSIGN'); freq = val('RIG.GET_FREQ', 'RIG.FREQ')
    r = api('RIG.GET_FREQ', wait=1.5); dial = next((x.get('params', {}).get('DIAL') for x in r if x.get('type') == 'RIG.FREQ'), None)
    adb('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP'); time.sleep(1); adb('shell', 'wm', 'dismiss-keyguard'); time.sleep(2)
    back = at_main()
    return (not awake) and bool(call) and bool(dial) and back, f'screen off {not awake}, callsign {call}, dial {dial}, app back {back}'


def w44():
    """Pages layout sideways: TX.SET_TEXT fills the Typing Box and MODE.SET_SPEED changes the speed label."""
    global L
    up(landscape_layout='swipe'); L = 'G'; rotate('G'); time.sleep(3); api_up()
    api('TX.SET_TEXT', f'PAGES API {_run}', wait=1.5); time.sleep(1)
    t1 = html.unescape(x())
    api('MODE.SET_SPEED', params={'SPEED': 0}, wait=1.5); time.sleep(1.5)
    t2 = html.unescape(x()); shot(f'w44_{_run}')
    api('TX.SET_TEXT', '', wait=1)
    L = 'U'; rotate('U')
    return f'PAGES API {_run}' in t1 and 'JS8 Normal' in t2 and 'JS8 Fast' in t1, f"typing box {f'PAGES API {_run}' in t1}, speed label Fast then Normal {'JS8 Fast' in t1 and 'JS8 Normal' in t2}"


row('W42', w42)
row('W43', w43)
row('W44', w44)
json.dump(out, open(f'apichk.{_run}.json', 'w'))
print('DONE', flush=True)
