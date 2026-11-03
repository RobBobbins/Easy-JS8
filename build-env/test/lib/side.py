"""Sideways copies of the upright-only screen scripts (ui3 to ui9). side.py <script> <run> <P|G|F> [ID,ID] (IDs without the layout letter).
The script and the sources it runs (ui1.py, ui7.py) get the layout letter, rotation and Sideways layout switched; results are
marked on TEST-CHECKLIST.md like runui.py, with the layout letter added. One layout a run (TEST-PLAN)."""
import builtins, io, json, re, sys
import checklist

scr, run, lay = sys.argv[1], sys.argv[2], sys.argv[3]
if lay not in ('P', 'G', 'F'):
    sys.exit('one sideways layout: P, G or F')
ids = sys.argv[4].split(',') if len(sys.argv) > 4 else []
NAME = {'P': 'pc', 'G': 'swipe', 'F': 'ft8call'}[lay]


def patch(s):
    s = s.replace("_s.argv = ['ui1', _run, 'U', '']", f"_s.argv = ['ui1', _run, '{lay}', '']")
    s = s.replace("rotate('U')", f"rotate('{lay}')").replace("rotated('U')", f"rotated('{lay}')")
    s = s.replace("'landscape_layout': 'pc'", f"'landscape_layout': '{NAME}'").replace("landscape_layout='pc'", f"landscape_layout='{NAME}'")
    s = re.sub(r"(?m)^L = 'U'$", f"L = '{lay}'", s)
    return re.sub(r"(['\"])([A-Z]\d\d)U\1", rf"\1\2{lay}\1", s)


_open = builtins.open


def popen(f, mode='r', *a, **k):
    if isinstance(f, str) and f.endswith('.py') and 'r' in mode and 'b' not in mode:
        return io.StringIO(patch(_open(f, mode, *a, **k).read()))
    return _open(f, mode, *a, **k)


builtins.open = popen
sys.argv = [scr + '.py', run] + ([','.join(ids + [i + lay for i in ids])] if ids else [])
g = {'__name__': '__main__'}
try:
    exec(compile(patch(_open(scr + '.py', encoding='utf8').read()), scr + '.py', 'exec'), g)
finally:
    builtins.open = _open
    got = []
    for name in ('out', 'res'):
        if isinstance(g.get(name), list):
            got += g[name]
    if g.get('t') is not None and hasattr(g['t'], 'res'):
        got += g['t'].res
    rows = []
    for r in got:
        i, ok, note = r[0], r[1], (r[2] if len(r) > 2 else '')
        i = (i[:3] if re.match(r'[A-Z]\d\d[UPGF]$', i) else i) + lay
        rows.append((i, ok, str(note)))
    json.dump(rows, _open(f'side.{scr}.{run}.{lay}.json', 'w'))
    if run in ('1', '2'):  # other run numbers are trials
        checklist.mark(int(run), 'Pass', [i for i, ok, n in rows if ok])
        for i, ok, n in rows:
            if ok is False:
                checklist.mark(int(run), 'Failed', [i], (n or 'see screenshot')[:100].replace('|', '/'))
    print('SIDE', scr, run, lay, sum(1 for r in rows if r[1]), 'pass', 'FAIL', [(i, n[:60]) for i, ok, n in rows if ok is False], flush=True)
