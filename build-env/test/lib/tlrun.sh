timeout 170 python tlink.py 8 T17 2>&1 | grep -E 'PASS|FAIL|PortAudio|Error' | cut -c1-170
for r in 1 2; do
  S=$SECONDS; timeout 1300 python tlink.py $r 2>&1 | grep -E 'FAIL|DONE|PortAudio' | cut -c1-170
  echo "tlink run $r real $((SECONDS-S))s" | tee -a timing.log
  python - $r <<'Y'
import json,checklist,sys
r=int(sys.argv[1]); d=json.load(open(f'tlink.{r}.json')); checklist.mark(r,'Pass',[i for i,o,n in d if o])
for i,o,n in d:
    if not o: checklist.mark(r,'Failed',[i],str(n)[:90].replace('|','/'))
Y
done
python checklist.py count
