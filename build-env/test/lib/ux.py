from onair2 import *
print(adb('shell','wm','size').decode().strip(), adb('shell','settings','get','system','user_rotation').decode().strip())
fresh(landscape_layout='pc')
adb('shell','settings','put','system','accelerometer_rotation','0'); adb('shell','settings','put','system','user_rotation','0'); time.sleep(3)
x=dump(); print(len(x)); print([t for t in texts()][:40]); shot('u0')
ids=sorted(set(re.findall(r'resource-id="com.easyjs8.app:id/([^"]+)"',x))); print(ids)
