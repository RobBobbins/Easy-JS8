"""W148-W154 (2026-11-17), Pages layout Map screen, sideways. mapchk.py <run> [ID,ID]
W148: six buttons Header Activity Waterfall Inbox Map Station, Station still opens Station.
W149: default map: my dot, station dots, callsigns, state lines, arrow lines.
W150: Lines Plain lines (no arrow heads) and No lines.
W151: Show callsigns off, Show state and province lines off, Show grid squares on.
W152: Remove stations from map after 15 minutes drops a 20-minute-old station; Same as Call Activity keeps it.
W153: Station > Layout lists the Map group and its defaults; ticking Show grid squares there and OK redraws the map.
W154: drag moves the map, Fit brings it back; a double tap on a station's dot zooms in 2 times with that dot
      staying under the finger (Rob 2026-11-17; double tap used to fit every station).
W155: the + and - buttons (bottom-right) zoom 2 times in and out; + then - puts the map back.
W156: Fit under -: after a drag and +, Fit shows every station again (the same view as at the start).
W157: the gear above + opens the Map options box; ticking Show grid squares and picking No lines show on the map at
      once and are saved; a tap outside closes the box.
W158: after a restart, lines between stations come back from the saved Band Activity ("A: B ..." = A hears B);
      with the same stations and no saved messages between them, only my lines.
W165: upright: the map between Waterfall Controls and Inbox, 300 dp tall, with its title and buttons; + zooms, Fit
      fits; a drag on the map moves the map, not the page; a drag beside it scrolls the page (Rob 2026-11-17).
W159: map title top left "EASY JS8 - callsign - band - UTC date"; outside the bands the frequency in MHz (Rob 2026-11-17).
Screens are saved as shots/map.<run>.<name>.png and read by colour counts (and looked at)."""
from onair2 import *
import io
from PIL import Image, ImageChops
assert_avd()
os.makedirs('shots', exist_ok=True)

run = int(sys.argv[1])
only = sys.argv[2].split(',') if len(sys.argv) > 2 else None
res = []


def mark(id, ok, note=''):
    print(id, 'PASS' if ok else 'FAIL', note, flush=True)
    res.append((id, ok, note))


def want(id):
    return not only or id in only


def rotate(code):
    adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0')
    for _ in range(5):
        adb('shell', 'settings', 'put', 'system', 'user_rotation', '0' if code == 'U' else '1')
        time.sleep(2)
        m = re.search(r'cur=(\d+)x(\d+)', adb('shell', 'dumpsys', 'window', 'displays').decode('utf8', 'replace'))
        if (int(m.group(1)) < int(m.group(2))) == (code == 'U'):
            return True
    return False


MAPKEYS = dict(js8cfg_map_lines=None, js8cfg_map_aging=None, js8cfg_map_callsigns=None, js8cfg_map_states=None,
               js8cfg_map_grids=None)
FRAMES = [("K1AAA: @HB HEARTBEAT FN42", 600), ("W4BBB: @HB HEARTBEAT EM73", 900), ("N0CCC: @HB HEARTBEAT EN34", 1200),
          ("VE7DDD: @HB HEARTBEAT CN89", 1500), ("W4BBB: K1AAA SNR -12", 900), ("K1AAA: W4BBB SNR -08", 600),
          ("N0CCC: VE7DDD SNR -15", 1200)]


def old_station():
    """Call Activity file with W5OLD (EM12) heard 20 minutes ago by the emulator's clock."""
    now = int(adb('shell', 'date', '+%s').decode().strip()) * 1000
    j = {"band": [], "rx": [], "calls": [{"call": "W5OLD", "grid": "EM12", "snr": -10, "offset": 1800,
         "utc": now - 20 * 60_000, "tdrift": 0.0, "submode": 0, "cq": 0, "ack": 0}]}
    b = base64.b64encode(json.dumps(j).encode()).decode()
    adb('shell', f"echo {b} | base64 -d | run-as {PKG} sh -c 'cat > files/js8_activity.json'")


def scene(name, **k):
    """Fresh start with these settings, the stations injected, the Map screen shown; returns the screenshot."""
    setprefs(**{**BASE, **MAPKEYS, 'landscape_layout': 'swipe', 'autoreply_enabled': False, 'js8cfg_hb_ack': False,
                'js8cfg_hb': False, 'js8cfg_callsign_aging': '0', **k})
    old_station()
    start()
    rotate('L')
    time.sleep(2)
    for t, f in FRAMES:
        inj(t, f)
        time.sleep(.6)
    time.sleep(2)
    tapk('Map', 2)
    return grab(name)


def grab(name):
    png = adb('exec-out', 'screencap', '-p')
    open(f'shots/map.{run}.{name}.png', 'wb').write(png)
    return Image.open(io.BytesIO(png)).convert('RGB')


def count(img, rgb, tol=24):
    r0, g0, b0 = rgb
    return sum(1 for r, g, b in img.get_flattened_data() if abs(r - r0) <= tol and abs(g - g0) <= tol and abs(b - b0) <= tol)


def untitled(img):
    """The screenshot with the map title's strip (top left, W159) blacked out, so its letters don't count as dots."""
    img = img.copy()
    img.paste((0, 0, 0), (136, 72, 1250, 195))
    return img


def colours(img):
    img = untitled(img).crop((140, 80, 2340, 900))  # the map only, not the status bar or the buttons under it
    c = dict(yellow=count(img, (255, 230, 0)), red=count(img, (255, 64, 64)), green=count(img, (64, 224, 64)),
             orange=count(img, (224, 160, 64), 16), white=count(img, (255, 255, 255), 4))
    c['lines'] = c['green'] + c['yellow']  # green both ways, yellow one way (2026-11-17)
    print('COLOURS', c, flush=True)
    return c


def diff(a, b):
    d = ImageChops.difference(a, b).convert('L')
    return sum(1 for v in d.get_flattened_data() if v > 40)


base = None
if want('W148') or want('W149') or want('W154'):
    with part('W148/W149 default scene', 45, ['W148', 'W149'], res, mark):
        base = scene('default')
        x = dump()
        pos = [find(x, t) for t in ['Header', 'Activity', 'Waterfall', 'Inbox', 'Map', 'Station']]
        if want('W148'):
            ok = all(pos) and [p[0] for p in pos] == sorted(p[0] for p in pos)
            tapk('Station', 2)
            st = texts()
            ok2 = 'Layout' in st
            mark('W148', ok and ok2, f'buttons {[p[2] if p else None for p in pos]}, Station page has Layout: {ok2}')
            tapk('Map', 2)
        if want('W149'):
            c = colours(base)
            arrows = c['green']
            # W4BBB <-> K1AAA both ways (green), my lines and N0CCC -> VE7DDD one way (yellow), white dots
            mark('W149', c['yellow'] > 300 and c['red'] > 40 and c['green'] > 300 and c['orange'] > 100 and c['white'] > 100,
                 str(c))

if want('W150'):
    with part('W150 plain lines', 40 if base else 65, ['W150'], res, mark):
        a = colours(base) if base else colours(scene('default'))
        p = colours(scene('plain', js8cfg_map_lines='plain'))
    with part('W150 no lines', 40, ['W150'], res, mark):
        n = colours(scene('none', js8cfg_map_lines='none'))
        mark('W150', p['green'] > 100 and p['yellow'] > 100 and p['lines'] < a['lines'] and n['lines'] < 20,
             f"lines arrows {a['lines']} plain {p['lines']} (green {p['green']} yellow {p['yellow']}) none {n['lines']}")

if want('W151'):
    with part('W151 calls off, states off, grids on', 40 if base else 65, ['W151'], res, mark):
        a = colours(base) if base else colours(scene('default'))
        o = colours(scene('options', js8cfg_map_callsigns=False, js8cfg_map_states=False, js8cfg_map_grids=True))
        # white: labels and dots, then the dots only
        mark('W151', 300 < o['white'] < a['white'] * 0.7 and o['orange'] < 20 and o['red'] > a['red'] + 500,
             f"white {a['white']}->{o['white']} orange {a['orange']}->{o['orange']} red {a['red']}->{o['red']}")

if want('W152'):
    with part('W152 aging 15 minutes', 65, ['W152'], res, mark):
        # callsigns off: white is the station dots only; 5 dots against 4
        a = colours(scene('agingall', js8cfg_map_callsigns=False))
        g = colours(scene('aging15', js8cfg_map_callsigns=False, js8cfg_map_aging='15'))
        mark('W152', g['white'] < a['white'] * 0.9 and g['white'] > a['white'] * 0.6,
             f"white dots same-as-call-activity {a['white']} 15 minutes {g['white']}")


if want('W153'):
    with part('W153 Layout page Map group', 50, ['W153'], res, mark):
        a = colours(scene('beforeui'))
        tapk('Station', 2)
        tapk('Layout', 2)
        st = texts()
        need = ['Map', 'Lines:', 'Arrows', 'Remove stations from map after:', 'Same as Call Activity', 'Show callsigns',
                'Show state and province lines', 'Show grid squares']
        missing = [t for t in need if not any(x == t or x.startswith(t + ' ') for x in st)]
        print('TEXTS', [x for x in st if 'Arrow' in x or 'Same' in x], flush=True)
        grab('layoutpage')
        tapk('Show grid squares', 1)
        tapk('OK', 2)
        tapk('Map', 2)
        g = colours(grab('afterui'))
        saved = 'js8cfg_map_grids" value="true"' in getprefs()
        mark('W153', not missing and saved and g['red'] > a['red'] + 500, f"missing {missing}, saved {saved}, red {a['red']}->{g['red']}")


def dots(img):
    """Centres of the white station dots (clusters of white pixels; callsigns off), map area only."""
    img = untitled(img)
    w, h = img.size
    px = img.load()
    pts = [(x, y) for y in range(80, 900, 2) for x in range(140, w, 2)
           if px[x, y][0] >= 251 and px[x, y][1] >= 251 and px[x, y][2] >= 251]
    groups = []
    for x, y in pts:
        for g in groups:
            if abs(g[0] / g[2] - x) < 20 and abs(g[1] / g[2] - y) < 20:
                g[0] += x; g[1] += y; g[2] += 1
                break
        else:
            groups.append([x, y, 1])
    return [(g[0] // g[2], g[1] // g[2]) for g in groups if g[2] >= 8]


if want('W154'):
    with part('W154 drag, Fit, double tap', 65, ['W154'], res, mark):
        base = scene('dotsbase', js8cfg_map_callsigns=False)
        adb('shell', 'input', 'swipe', '1300', '500', '900', '600', '600')
        time.sleep(1.5)
        moved = grab('dragged')
        fit = find(dump(), 'js8_map_fit')
        adb('shell', 'input', 'tap', str(fit[0]), str(fit[1])); time.sleep(1.5)
        back = grab('dragfit')
        before = dots(back)
        # the dot nearest the map's middle, away from the buttons
        tx, ty = min([d for d in before if d[0] < 2000], key=lambda d: abs(d[0] - 1200) + abs(d[1] - 480))
        import uiautomator2
        uiautomator2.connect('emulator-5600').double_click(tx, ty, 0.1)
        time.sleep(1.5)
        after = dots(grab('doubletap'))
        stays = any(abs(x - tx) <= 12 and abs(y - ty) <= 12 for x, y in after)
        others = [d for d in before if d != (tx, ty)]
        spread = all(not any(abs(x - ox) <= 12 and abs(y - oy) <= 12 for x, y in after) for ox, oy in others)
        dm, db = diff(base, moved), diff(base, back)
        mark('W154', dm > 20000 and db < dm / 5 and stays and spread and len(before) >= 4,
             f'dragged {dm}, after Fit {db}; tapped dot {(tx, ty)} stays {stays}, other dots moved {spread}; dots before {before} after {after}')

if want('W155'):
    with part('W155 zoom buttons', 30 if base else 55, ['W155'], res, mark):
        b = scene('zoombase')
        x = dump()
        zin, zout = find(x, 'js8_map_zoom_in'), find(x, 'js8_map_zoom_out')
        ok = bool(zin and zout) and zin[0] > 2000 and zout[1] > zin[1]
        adb('shell', 'input', 'tap', str(zin[0]), str(zin[1])); time.sleep(1.5)
        big = grab('zoomin')
        adb('shell', 'input', 'tap', str(zout[0]), str(zout[1])); time.sleep(1.5)
        back = grab('zoomback')
        adb('shell', 'input', 'tap', str(zout[0]), str(zout[1])); time.sleep(1.5)
        small = grab('zoomout')
        cb, cs = colours(b), colours(small)
        di, db = diff(b, big), diff(b, back)
        mark('W155', ok and di > 20000 and db < di / 5 and cs['orange'] < cb['orange'],
             f"buttons {zin} {zout}, zoom in changed {di}, back {db}, state lines {cb['orange']} -> zoomed out {cs['orange']}")

if want('W156'):
    with part('W156 Fit button', 35, ['W156'], res, mark):
        b = scene('fitbase')
        x = dump()
        zin, zout, fit = find(x, 'js8_map_zoom_in'), find(x, 'js8_map_zoom_out'), find(x, 'js8_map_fit')
        ok = bool(zin and zout and fit) and fit[2] == 'Fit' and fit[1] > zout[1] and abs(fit[0] - zout[0]) < 5
        adb('shell', 'input', 'swipe', '1300', '500', '900', '600', '600'); time.sleep(1.5)
        adb('shell', 'input', 'tap', str(zin[0]), str(zin[1])); time.sleep(1.5)
        moved = grab('fitmoved')
        adb('shell', 'input', 'tap', str(fit[0]), str(fit[1])); time.sleep(1.5)
        back = grab('fitback')
        dm, db = diff(b, moved), diff(b, back)
        mark('W156', ok and dm > 20000 and db < dm / 5, f"buttons {zin} {zout} {fit}, moved {dm}, after Fit {db}")

if want('W157'):
    with part('W157 gear options box', 45, ['W157'], res, mark):
        b = scene('gearbase')
        x = dump()
        gear, zin = find(x, 'js8_map_options'), find(x, 'js8_map_zoom_in')
        ok = bool(gear and zin) and gear[1] < zin[1] and abs(gear[0] - zin[0]) < 5
        adb('shell', 'input', 'tap', str(gear[0]), str(gear[1])); time.sleep(1.5)
        st = texts()
        need = ['Map', 'Lines:', 'Remove stations from map after:', 'Show callsigns', 'Show state and province lines', 'Show grid squares']
        missing = [t for t in need if t not in st] + [t for t in ['Arrows', 'Same as Call Activity'] if not any(v.startswith(t + ' ') for v in st)]
        grab('gearbox')
        tapk('Show grid squares', 1.5)
        g = colours(grab('geargrids'))
        tapk('Arrows  ▾', 1.5)
        tapk('No lines', 1.5)
        n = colours(grab('gearnolines'))
        p = getprefs()
        saved = 'js8cfg_map_grids" value="true"' in p and 'name="js8cfg_map_lines">none<' in p
        adb('shell', 'input', 'tap', '400', '850'); time.sleep(1.5)
        closed = 'Show grid squares' not in texts()
        c = colours(grab('gearclosed'))  # the box covers most stations and my dot while open: grid boxes counted once it closes
        cb = colours(b)
        mark('W157', ok and not missing and c['red'] > cb['red'] + 500 and n['lines'] < 20 and saved and closed,
             f"gear {gear} above + {zin}, missing {missing}, red {cb['red']}->{g['red']} open, {c['red']} closed, lines after No lines {n['lines']}, saved {saved}, closed {closed}")

def saved_scene(name, with_msgs):
    """App started on a saved Call Activity (4 stations with grids) and, if with_msgs, saved Band Activity holding
    W4BBB <-> K1AAA and N0CCC -> VE7DDD; nothing injected."""
    setprefs(**{**BASE, **MAPKEYS, 'landscape_layout': 'swipe', 'autoreply_enabled': False, 'js8cfg_hb_ack': False,
                'js8cfg_hb': False, 'js8cfg_callsign_aging': '0'})
    now = int(adb('shell', 'date', '+%s').decode().strip()) * 1000
    calls = [{"call": c, "grid": g, "snr": -10, "offset": o, "utc": now - 60_000, "tdrift": 0.0, "submode": 0, "cq": 0, "ack": 0}
             for c, g, o in [("K1AAA", "FN42", 600), ("W4BBB", "EM73", 900), ("N0CCC", "EN34", 1200), ("VE7DDD", "CN89", 1500)]]
    msgs = [("W4BBB: K1AAA SNR -12", 900), ("K1AAA: W4BBB SNR -08", 600), ("N0CCC: VE7DDD SNR -15", 1200)] if with_msgs else []
    band = [{"offset": o, "text": t, "snr": -10, "utc": now - 60_000 + i, "tdrift": 0.0, "submode": 0, "bits": 3,
             "directed": True, "data": False} for i, (t, o) in enumerate(msgs)]
    b = base64.b64encode(json.dumps({"band": band, "calls": calls, "rx": []}).encode()).decode()
    adb('shell', f"echo {b} | base64 -d | run-as {PKG} sh -c 'cat > files/js8_activity.json'")
    start()
    rotate('L')
    time.sleep(2)
    tapk('Map', 2)
    return grab(name)


if want('W158'):
    with part('W158 lines from saved Band Activity', 50, ['W158'], res, mark):
        plain = colours(saved_scene('savednomsgs', False))
        full = colours(saved_scene('savedmsgs', True))
        mark('W158', full['lines'] > plain['lines'] + 1500 and plain['lines'] > 1000 and plain['green'] < 20 and full['green'] > 300,
             f"lines: my lines only {plain['lines']} (green {plain['green']}), with saved messages {full['lines']} (green {full['green']})")

def title():
    """The map's title, read from its content description (the same text it draws)."""
    m = re.search(r'resource-id="[^"]*/js8_map"[^>]*content-desc="([^"]*)"', dump())
    return html.unescape(m.group(1)) if m else None


if want('W159'):
    with part('W159 map title', 70, ['W159'], res, mark):
        call = re.search(r'name="callsign">([^<]*)<', getprefs()).group(1).strip().upper()
        inband = scene('title40m', last_frequency='7078000')
        t1 = title()
        off = scene('titleoff', last_frequency='27555000')
        t2 = title()
        day = adb('shell', 'date', '-u', '+%Y-%m-%d').decode().strip()
        strip = inband.crop((140, 80, 700, 160))
        dark = count(strip, (0, 0, 0), 60)
        mark('W159', t1 == f'EASY JS8 - {call} - 40m - {day}' and t2 == f'EASY JS8 - {call} - 27.5550 MHz - {day}' and dark > 3000,
             f'40m: {t1!r}; 27.555: {t2!r}; UTC date {day}; dark pixels top left {dark}')

def map_node():
    for n in re.findall(r'<node [^>]*>', dump()):
        if re.search(r'resource-id="[^"]*/js8_map"', n):
            b = tuple(map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n).groups()))
            d = re.search(r'content-desc="([^"]*)"', n)
            return b, html.unescape(d.group(1)) if d else ''
    return None, ''


if want('W165'):
    with part('W165 upright map', 75, ['W165'], res, mark):
        setprefs(**{**BASE, **MAPKEYS, 'autoreply_enabled': False, 'js8cfg_hb_ack': False, 'js8cfg_hb': False,
                    'js8cfg_callsign_aging': '0'})
        old_station()
        start()
        rotate('U')
        time.sleep(2)
        for t, f in FRAMES:
            inj(t, f)
            time.sleep(.6)
        b = None
        for _ in range(12):  # scroll down until the whole map is on screen
            b, title = map_node()
            if b and b[1] > 200 and b[3] < 2200:
                break
            y = b[1] - 30 if b and b[1] > 700 else 1900  # above the map, so the page scrolls
            adb('shell', 'input', 'swipe', '540', str(y), '540', str(y - 600), '400'); time.sleep(1)
        b, title = map_node()
        dens = int(re.search(r'(\d+)', adb('shell', 'wm', 'density').decode()).group(1)) / 160
        tall = b and abs((b[3] - b[1]) / dens - 300) < 3
        x = dump()
        btns = [find(x, k) for k in ('js8_map_options', 'js8_map_zoom_in', 'js8_map_zoom_out', 'js8_map_fit')]
        inbox_below = (find(x, 'js8_inbox_table') or (0, -1))[1] > b[3]
        before = grab('upright')
        crop = lambda im: im.crop((b[0], b[1], b[2], b[3]))
        adb('shell', 'input', 'tap', str(btns[1][0]), str(btns[1][1])); time.sleep(1.5)
        zoomed = grab('uprightzoom')
        adb('shell', 'input', 'tap', str(btns[3][0]), str(btns[3][1])); time.sleep(1.5)
        fitted = grab('uprightfit')
        dz, df = diff(crop(before), crop(zoomed)), diff(crop(before), crop(fitted))
        my = (b[1] + b[3]) // 2
        adb('shell', 'input', 'swipe', '400', str(my + 150), '400', str(my - 150), '500'); time.sleep(1.5)
        b2, _ = map_node()
        adb('shell', 'input', 'tap', str(btns[3][0]), str(btns[3][1])); time.sleep(1)
        adb('shell', 'input', 'swipe', '540', str(b[3] + 60), '540', str(b[3] - 240), '500'); time.sleep(1.5)
        b3, _ = map_node()
        mark('W165', bool(b) and tall and all(btns) and inbox_below and title.startswith('EASY JS8 - ') and dz > 20000
             and df < dz / 5 and b2 == b and b3 != b,
             f'map {b} {(b[3] - b[1]) / dens:.0f} dp, title {title!r}, buttons {[bool(v) for v in btns]}, inbox below {inbox_below}, '
             f'zoom changed {dz}, after Fit {df}, page still after map drag {b2 == b}, page moved by drag beside it {b3 != b}')
        rotate('L')

print('RESULT', sum(1 for r in res if r[1]), '/', len(res), flush=True)
