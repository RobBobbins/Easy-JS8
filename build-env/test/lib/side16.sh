#!/bin/bash
# Step 16 (TEST-PLAN): one sideways layout, one script, rows still N/A under the one-layout rule. side16.sh <run> <P|G|F> <script> <limit s>
# Rows that run already passed are left out (need.py). Results are marked on TEST-CHECKLIST.md; times go to timing.log.
cd "$(dirname "$0")"
export MSYS_NO_PATHCONV=1 PYTHONUTF8=1
run=$1 lay=$2 s=$3 lim=$4
rows=$(python need.py "$lay" "$s" "$run")
if [ -z "$rows" ]; then echo "$s $lay run $run: nothing left"; exit 0; fi
start=$(date +%s)
case $s in
  ui1) JS8_ROWS=$rows timeout "$lim" python runui.py ui1 "$run" "$lay" HLN | tail -3 ;;
  wside) timeout "$lim" python wside.py "$run" "$lay" "$rows" | grep -E "^[HN][0-9]+[PGF] |DONE"; python markjson.py "wside.$run.$lay.json" "$run" ;;
  ui11) timeout "$lim" python ui11.py "$run" "$lay" "$(echo "$rows" | sed "s/\([A-Z][0-9][0-9]\)/\1$lay/g")" | grep -E "^I24|DONE"; python markjson.py "ui11.$run.json" "$run" ;;
  *) timeout "$lim" python side.py "$s" "$run" "$lay" "$rows" | grep -E "^SIDE|Traceback" ;;
esac
echo "step16 $s run $run $lay real $(( $(date +%s) - start ))s (limit ${lim}s)" | tee -a timing.log
