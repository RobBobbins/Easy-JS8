"""One row with a timestamp on every adb call and read. prof.py <layout> <row>"""
import sys,time,subprocess
import u
t0=time.time(); or_=subprocess.run; od=u.dump
def run(a,*k,**w):
    t=time.time(); r=or_(a,*k,**w); d=time.time()-t
    if isinstance(a,list) and 'adb' in str(a[0]): print(f'{time.time()-t0:6.1f} adb {d:.2f}',' '.join(map(str,a[3:]))[:70],flush=True)
    return r
subprocess.run=run
def dump():
    t=time.time(); x=od(); print(f'{time.time()-t0:6.1f} READ {time.time()-t:.2f} bytes {len(x)}',flush=True); return x
u.dump=dump
s=time.sleep
def sl(n):
    print(f'{time.time()-t0:6.1f} sleep {n}',flush=True); s(n)
time.sleep=sl
sys.argv=['ui2','8',sys.argv[1]]
exec(open('ui2.py',encoding='utf8').read())
