for r in 1 2; do for L in P G F; do
  S=$SECONDS; timeout 90 python ui11.py $r $L M 2>&1 | grep -E 'FAIL|DONE|Error' | cut -c1-120
  echo "step7 run $r $L real $((SECONDS-S))s" | tee -a timing.log
  python - $r $L <<'Y'
import json,checklist,sys
r=int(sys.argv[1]); d=json.load(open(f'ui11.{r}.json')); checklist.mark(r,'Pass',[i for i,o,n in d if o])
for i,o,n in d:
    if not o: checklist.mark(r,'Failed',[i],'open check')
Y
done; done
python checklist.py count
