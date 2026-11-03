timeout 60 python air14.py 8 G11 2>&1 | grep -E 'G11|Error' | cut -c1-200
for r in 1 2; do
  S=$SECONDS; timeout 500 python air14.py $r 2>&1 | grep -E 'PASS|FAIL|DONE' | cut -c1-200
  echo "air14 run $r real $((SECONDS-S))s" | tee -a timing.log
  python - $r <<'Y'
import json,checklist,sys
r=int(sys.argv[1]); d=json.load(open(f'air14.{r}.json')); checklist.mark(r,'Pass',[i for i,o,n in d if o])
for i,o,n in d:
    if not o: checklist.mark(r,'Failed',[i],str(n)[:90].replace('|','/'))
Y
done
python checklist.py count
