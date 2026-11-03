import time, uiautomator2 as u2, os, re, subprocess
A=os.path.expandvars(r'%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe')
print(re.findall(r'cur=\d+x\d+',subprocess.run([A,'-s','emulator-5600','shell','dumpsys window displays'],capture_output=True).stdout.decode())[:1])
d=u2.connect('emulator-5600')
def T(n,c=3):
    v=[]
    for _ in range(c):
        t=time.time(); x=d.dump_hierarchy(root_in_active=True); v.append(round(time.time()-t,2))
    print(n,v,len(x),flush=True)
print([k for k in dir(d.jsonrpc)][:5] if hasattr(d,'jsonrpc') else 'no jsonrpc')
try: print('cfg',d.jsonrpc.getConfigurator())
except Exception as e: print('get',repr(e)[:100])
T('default')
try:
    c=d.jsonrpc.getConfigurator(); c['waitForIdleTimeout']=0; d.jsonrpc.setConfigurator(c); print('set',d.jsonrpc.getConfigurator())
except Exception as e: print('set',repr(e)[:100])
T('idle 0',5)
