"""Open checks in one layout: ui10.py <run> <P|G|F>. Windows and menus through ui1, then marked."""
import json, os, subprocess, sys
import checklist
run, lay = sys.argv[1], sys.argv[2]
rows = 'H05,H06,H08,H10,H11,H16,H33,L27,N01,N02,N08,N09,N10,N11,N12'
env = dict(os.environ, JS8_ROWS=rows, PYTHONUTF8='1')
p = subprocess.run(['python', 'ui1.py', run, lay, 'HN'], env=env, capture_output=True, text=True, encoding='utf8', errors='replace')
print(p.stdout[-2500:], p.stderr[-600:], flush=True)
if run == '8':
    sys.exit()
d = json.load(open(f'ui1.{run}.json'))
checklist.mark(int(run), 'Pass', [r for r, o, n in d if o])
for r, o, n in d:
    if o is False:
        checklist.mark(int(run), 'Failed', [r], str(n)[:90].replace('|', '/'))
    if o is None:
        checklist.mark(int(run), 'N/A', [r], str(n)[:90])
