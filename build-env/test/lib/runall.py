"""Runs a batch and marks the checklist: runall.py <script> <run> [script run ...]"""
import subprocess, sys, json, checklist
a = sys.argv[1:]
for s, r in zip(a[::2], a[1::2]):
    out = subprocess.run([sys.executable, s + '.py', r], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding='utf8', errors='replace').stdout
    open(f'{s}.{r}.out', 'w', encoding='utf8').write(out)
    try: res = json.load(open(f'{s}.{r}.json'))
    except Exception as e: print(s, r, 'no json', out[-300:]); continue
    ok = [i for i, o, n in res if o and len(i) == 3]
    bad = [(i, n) for i, o, n in res if not o]
    checklist.mark(int(r), 'Pass', ok)
    for i, n in bad:
        if len(i) == 3: checklist.mark(int(r), 'Failed', [i], n[:120].replace('|', '/'))
    print(s, r, len(ok), 'pass', 'FAIL:', bad, flush=True)
