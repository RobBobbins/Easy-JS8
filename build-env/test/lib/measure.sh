#!/bin/bash
# Measuring run: each row's limit 3 times its estimate (PARTS_SCALE=3), to set the estimates from the END lines.
# measure.sh <run> <script:limit_s>...  s1chk's W76 needs fakespot.py: started for it, stopped after.
cd "$(dirname "$0")"
r=$1; shift
export PARTS_SCALE=3
for s in "$@"; do
    n=${s%%:*}; lim=${s##*:}
    echo "STEP $n run $r limit $lim s at $(date +%H:%M:%S)"
    if [ $n = s1chk ]; then python fakespot.py > /dev/null 2>&1 & fs=$!; sleep 2; fi
    timeout $lim python $n.py $r > $n.$r.out 2>&1
    echo "STEPEND $n exit $? at $(date +%H:%M:%S)"
    if [ $n = s1chk ]; then kill $fs; fi
done
