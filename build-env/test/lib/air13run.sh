for r in 1 2; do
  timeout 200 python air12.py $r E31 2>&1 | grep -E 'PASS|FAIL' | cut -c1-200
  python - $r <<'Y'
import json,checklist,sys
r=int(sys.argv[1]); d=json.load(open(f'air12.{r}.json')); checklist.mark(r,'Pass',[i for i,o,n in d if o])
Y
  S=$SECONDS; timeout 1250 python air13.py $r 2>&1 | grep -E 'PASS|FAIL|DONE' | cut -c1-200
  echo "air13 run $r real $((SECONDS-S))s" | tee -a timing.log
  python - $r <<'Y'
import json,checklist,sys
r=int(sys.argv[1]); d=json.load(open(f'air13.{r}.json')); checklist.mark(r,'Pass',[i for i,o,n in d if o])
for i,o,n in d:
    if not o: checklist.mark(r,'Failed',[i],str(n)[:90].replace('|','/'))
Y
done
python checklist.py count
