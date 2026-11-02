"""Marks TEST-CHECKLIST.md. Usage: checklist.py <run 1|2> <Pass|Failed|N/A> <ID,ID,...> [note]   or: checklist.py count"""
import os, re, sys

PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'TEST-CHECKLIST.md')


def mark(run, result, ids, note=''):
    lines = open(PATH, encoding='utf8').read().split('\n')
    left = set(ids)
    for n, line in enumerate(lines):
        c = line.split(' | ')
        if not line.startswith('| ') or len(c) != 5:
            continue
        rid = c[0][2:]
        if rid in left:
            c[1 + run] = result
            if note:
                c[4] = note + ' |'
            lines[n] = ' | '.join(c)
            left.discard(rid)
    open(PATH, 'w', encoding='utf8', newline='\n').write('\n'.join(lines))
    if left:
        print('NOT FOUND', sorted(left))


def count():
    text = open(PATH, encoding='utf8').read()
    rows = [l.split(' | ') for l in text.split('\n') if re.match(r'\| [A-Z]\d\d', l)]
    both = sum(1 for r in rows if r[2].strip() in ('Pass', 'N/A') and r[3].strip() in ('Pass', 'N/A'))
    failed = sum(1 for r in rows if 'Failed' in (r[2].strip(), r[3].strip()))
    print(f'{len(rows)} rows, {both} done twice, {failed} Failed, {len(rows) - both - failed} left')


if __name__ == '__main__':
    if sys.argv[1] == 'count':
        count()
    else:
        mark(int(sys.argv[1]), sys.argv[2], sys.argv[3].split(','), sys.argv[4] if len(sys.argv) > 4 else '')
