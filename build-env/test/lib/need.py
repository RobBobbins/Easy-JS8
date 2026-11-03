"""Rows still N/A under the one-layout rule for one sideways layout and one script. need.py <P|G|F> <script> [run]
Prints the IDs without the layout letter (with it for ui1, which reads JS8_ROWS). With a run, rows that run already passed are left out."""
import re, sys

BY = {'ui1': 'H01 H03 H04 H07 H08 H12 H13 H14 H15 H17 H18 H19 H20 H21 H22 H23 H24 H25 H26 H32 H33 H34 H36 L01 L02 L03 L04 L05 L06 L07 L08 '
             'L10 L11 L12 L13 L14 L16 L17 L18 L19 L20 L21 L22 L23 L24 L25 L26 N01 N02 N08 N09 N10 N11 N12 N13 N14 N15',
      'ui7': 'H02 H27 I06 I07', 'ui11': 'I24', 'ui5': 'H09 H29 H30 I09 I10 I22 I23 L27', 'ui6': 'H28 H37',
      'ui8': 'H35 I04 K01 K02 K03 K04 K09 K10 K11 K12 L09 L15', 'ui3': 'M02 M03 M04 M06 M07 M09 M11 M16 M24 M46 M47',
      'ui4': 'M14 M15 M22 M28 M31 M32 M33', 'ui9': 'M05 M08 M10 M12 M13 M17 M19 M20 M21 M23 M26 M27 M35 M36 M37 M38 M42',
      'wside': 'H31 N03 N04 N05 N06 N07 N16'}
lay, scr = sys.argv[1], sys.argv[2]
run = int(sys.argv[3]) if len(sys.argv) > 3 else 0
mine = set(BY[scr].split())
left = []
for l in open('E:/new-w3bguru-websites/easy js8/TEST-CHECKLIST.md', encoding='utf8'):
    c = [s.strip() for s in l.split('|')]
    if len(c) < 6 or not re.match(r'[A-Z]\d\d[PGF]$', c[1]) or c[1][3] != lay or c[1][:3] not in mine:
        continue
    # a run 1 failure note replaces the Upright only note: a row with a Pass or Failed run is still step 16's
    touched = 'Upright only (TEST-PLAN One layout)' in c[5] or {'Pass', 'Failed'} & {c[3], c[4]}
    if touched and not (run and c[2 + run] == 'Pass'):
        left.append(c[1] if scr == 'ui1' else c[1][:3])
print(','.join(left))
