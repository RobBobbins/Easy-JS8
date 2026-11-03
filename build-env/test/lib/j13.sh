for r in 2; do for L in F; do
  rm -f ui11.$r.json; timeout 130 python ui11.py $r $L J13$L,JL$L 2>&1 | grep -E 'PASS|FAIL|Error' | cut -c1-120
  python - $r <<'Y'
import json,checklist,sys
r=int(sys.argv[1]); d=json.load(open(f'ui11.{r}.json')); checklist.mark(r,'Pass',[i for i,o,n in d if o and i.startswith('J13')],'')
for i,o,n in d:
    if not o: checklist.mark(r,'Failed',[i],str(n)[:90].replace('|','/'))
Y
done; done
