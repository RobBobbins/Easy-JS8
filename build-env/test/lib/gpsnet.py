"""M05N: Acquire GPS with a Wi-Fi / cell (network) answer, Upright. gpsnet.py <run>
Test providers stand in for Wi-Fi / cell (network, Ottawa, FN25) and GPS (Toronto, FN03): the emulator's own
GPS re-sends its stored position at once, so it can never be the late or missing answer.
N1: network grid shown at once. N2: a GPS fix then replaces it and ends the search.
N3: network only, the grid is still there when the 2 minute search ends (not "No position")."""
import sys as _s
exec(open('ui9.py', encoding='utf8').read().split("\nrow_list = [")[0].replace('"""Station rows, Upright. ui9.py <run> [ID,ID]"""', ''))

NET = '45.42,-75.69'


def sh(*a):
    return adb('shell', *a)


def netup():
    sh('appops', 'set', 'com.android.shell', 'android:mock_location', 'allow')
    for p_ in ('network', 'gps'):
        sh('cmd', 'location', 'providers', 'add-test-provider', p_)
        sh('cmd', 'location', 'providers', 'set-test-provider-enabled', p_, 'true')


def netfix():
    sh('cmd', 'location', 'providers', 'set-test-provider-location', 'network', '--location', NET)


def box():
    """The text in the box beside Acquire GPS: an 8-character grid, Acquiring…, or No position."""
    w = html.unescape(x())
    g = re.findall(r' text="([A-R]{2}\d\d[A-Xa-x]{2}\d\d)"', w)
    return g[-1] if g else ('Acquiring' if 'Acquiring' in w else ('No position' if 'No position' in w else '?'))


def start():
    page('General'); scroll_to('Acquire GPS')
    tapk('Acquire GPS', 2)
    if 'While using the app' in html.unescape(x()):
        tapk('While using the app', 2)


def n12():
    start(); netfix(); time.sleep(3)
    first = box()
    sh('cmd', 'location', 'providers', 'set-test-provider-location', 'gps', '--location', '43.7,-79.4'); time.sleep(3)
    second = box()
    netfix(); time.sleep(3)
    after = box()  # search ended: a later network answer must not replace the GPS grid
    tapk('Cancel', 1)
    return first.startswith('FN25') and second.startswith('FN03') and after.startswith('FN03'), f'network {first}, then GPS {second}, network again {after}'


def n3():
    start(); netfix(); time.sleep(3)
    first = box()
    time.sleep(125)
    last = box()
    tapk('Cancel', 1)
    return first.startswith('FN25') and last == first, f'network {first}, after 2 min {last}'


netup()
row('M05N3', n3)
row('M05N1', n12)
for p_ in ('network', 'gps'):
    sh('cmd', 'location', 'providers', 'remove-test-provider', p_)
adb('shell', 'settings', 'put', 'system', 'user_rotation', '0')
json.dump(out, open(f'gpsnet.{_run}.json', 'w'))
print('DONE', flush=True)
