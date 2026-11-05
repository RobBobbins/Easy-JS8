#!/bin/bash
# Regression pass: air2, air3, air1, n42chk, n48chk, s1chk, s27chk, each with its own limit (est x 1.1, in seconds).
# regress.sh <run>. s1chk's W76 needs fakespot.py: started for it, stopped after.
cd "$(dirname "$0")"
r=$1
for s in "air2 1320" "air3 990" "air1 660" "n42chk 198" "n48chk 264" "s1chk 528" "s27chk 1980"; do
    set -- $s
    echo "STEP $1 run $r limit $2 s at $(date +%H:%M:%S)"
    if [ $1 = s1chk ]; then python fakespot.py > /dev/null 2>&1 & fs=$!; sleep 2; fi
    timeout $2 python $1.py $r > $1.$r.out 2>&1
    echo "STEPEND $1 exit $? at $(date +%H:%M:%S)"
    if [ $1 = s1chk ]; then kill $fs; fi
done
