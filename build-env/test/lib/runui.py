import subprocess, sys, re, checklist
"""runui.py <script> <run> <args...>: runs, marks Pass and Failed for layout rows."""
s, r = sys.argv[1], sys.argv[2]
o = subprocess.run([sys.executable, s + '.py', r, *sys.argv[3:]], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding='utf8', errors='replace').stdout
open(f'{s}.{r}.{"".join(sys.argv[3:])}.out', 'w', encoding='utf8').write(o)
ok, bad = [], []
for l in o.splitlines():
    m = re.match(r'([A-Z]\d\d[UPGF]?) (PASS|FAIL) ?(.*)', l)
    if m: (ok if m.group(2) == 'PASS' else bad).append((m.group(1), m.group(3)))
checklist.mark(int(r), 'Pass', [i for i, _ in ok])
for i, n in bad: checklist.mark(int(r), 'Failed', [i], (n or 'see screenshot')[:100].replace('|', '/'))
print(s, r, len(ok), 'pass', 'FAIL', bad[:40]); print(o[-200:] if 'DONE' not in o else '')
