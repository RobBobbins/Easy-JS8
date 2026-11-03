"""Marks TEST-CHECKLIST.md from a script's result file: markjson.py <file.json> <run>. True Pass, False Failed, None N/A (with the note)."""
import json, sys
import checklist
rows, run = json.load(open(sys.argv[1], encoding='utf8')), int(sys.argv[2])
if run not in (1, 2):
    sys.exit('runs 1 and 2 only')
checklist.mark(run, 'Pass', [r[0] for r in rows if r[1] is True])
for i, ok, note in rows:
    if ok is False:
        checklist.mark(run, 'Failed', [i], (str(note) or 'see screenshot')[:100].replace('|', '/'))
    elif ok is None:
        checklist.mark(run, 'N/A', [i], str(note)[:100].replace('|', '/'))
print(sys.argv[1], sum(1 for r in rows if r[1] is True), 'pass', 'FAIL', [(r[0], str(r[2])[:60]) for r in rows if r[1] is False])
