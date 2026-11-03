for r in 1 2; do for L in G; do
  IDS=J23$L
  rm -f ui11.$r.json; S=$SECONDS; timeout 60 python ui11.py $r $L $IDS 2>&1 | grep -E 'FAIL|DONE|Error' | cut -c1-140
  echo "step8 run $r $L real $((SECONDS-S))s" | tee -a timing.log
  python - $r <<'Y'
import json,checklist,sys
r=int(sys.argv[1]); d=json.load(open(f'ui11.{r}.json')); checklist.mark(r,'Pass',[i for i,o,n in d if o])
for i,o,n in d:
    if not o: checklist.mark(r,'Failed',[i],str(n)[:90].replace('|','/'))
Y
done; done
python checklist.py count
