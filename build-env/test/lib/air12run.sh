for r in 8 1 2; do
  rows=B27,D04,E22,E23,E31,E35,E37,A15,A16,Q07,B28
  [ $r = 8 ] && rows=D04,E31,E37,A15,A16,Q07
  S=$SECONDS; timeout 1100 python air12.py $r $rows 2>&1 | grep -E 'PASS|FAIL|DONE' | cut -c1-200
  echo "air12 run $r real $((SECONDS-S))s" | tee -a timing.log
  [ $r != 8 ] && python - $r <<'Y'
import json,checklist,sys
r=int(sys.argv[1])
d=json.load(open(f'air12.{r}.json')); checklist.mark(r,'Pass',[i for i,o,n in d if o])
for i,o,n in d:
    if not o: checklist.mark(r,'Failed',[i],str(n)[:90].replace('|','/'))
Y
done
python checklist.py count
