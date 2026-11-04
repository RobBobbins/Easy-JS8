"""Station > Radio sideways with Icom IC-7300: fling to the bottom, picture there. radioend.py <P|F>"""
import sys as _s
_code = _s.argv[1]
_s.argv = ['ui7', '9']
exec(open('ui7.py', encoding='utf8').read().split("row_list = [")[0].replace("_s.argv = ['ui1', _run, 'U', '']", "_s.argv = ['ui1', _run, _code, '']"))
from PIL import Image
import io
layout(_code)
setprefs(rig_control_enabled=True, rig_type='hamlib_usb', rig_hamlib_model='3073', js8cfg_ptt_method='cat')
start(); rotate(_code); time.sleep(2)
home(); top()
print('opened' if open_station() and tapk('Radio', 2) else 'NOT opened', flush=True)
time.sleep(1.5)
for _ in range(10):
    adb('shell', 'input', 'swipe', '1200', '750', '1200', '200', '80'); time.sleep(.8)
for n in range(1, 4):  # back up the page slowly, a picture each step
    adb('shell', 'input', 'swipe', '1200', '250', '1200', '650', '800'); time.sleep(1.5)
    Image.open(io.BytesIO(adb('exec-out', 'screencap', '-p'))).save(f'shots/pg_radio_{_code}_3073_up{n}.png')
tapk('Cancel', 1)
setprefs(rig_control_enabled=False, rig_type='none', js8cfg_ptt_method='vox')
print('DONE', flush=True)
