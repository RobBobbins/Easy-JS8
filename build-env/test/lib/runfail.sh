#!/bin/bash
# runfail.sh <run>: failed.txt rows, one layout at a time.
r=$1
while read L ROWS <&3; do
  H=$(echo $ROWS | tr ' ' '\n' | grep '^H' | paste -sd,)
  J=$(echo $ROWS | tr ' ' '\n' | grep -v '^H' | paste -sd,)
  t=$SECONDS
  [ -n "$H" ] && JS8_ROWS=$H python runui.py ui1 $r $L H </dev/null 2>&1 | tail -2
  [ -n "$J" ] && JS8_ROWS=$J python runui.py ui2 $r $L </dev/null 2>&1 | tail -2
  echo "run $r $L $((SECONDS-t))s" | tee -a timing.log
done 3< failed.txt
