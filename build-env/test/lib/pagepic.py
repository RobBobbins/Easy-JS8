"""Pictures of Station > Radio with a rig picked (Icom IC-7300, Hamlib 3073) and Station > Audio, sideways.
pagepic.py <P|F>: shots/pg_radio_<code>.png, shots/pg_audio_<code>.png, for checking by eye (HANDOFF "not looked at")."""
import sys as _s
_code = _s.argv[1]
_rig = _s.argv[2] if len(_s.argv) > 2 else '3073'  # 'none': Rig None
_s.argv = ['ui7', '9']
exec(open('ui7.py', encoding='utf8').read().split("row_list = [")[0].replace("_s.argv = ['ui1', _run, 'U', '']", "_s.argv = ['ui1', _run, _code, '']"))
from PIL import Image
import io

layout(_code)
if _rig == 'none':
    setprefs(rig_control_enabled=False, rig_type='none', js8cfg_ptt_method='vox')
else:
    setprefs(rig_control_enabled=True, rig_type='hamlib_usb', rig_hamlib_model=_rig, js8cfg_ptt_method='cat')
start(); rotate(_code); time.sleep(2)
for name in ('Radio', 'Audio'):
    home(); top()
    ok = open_station() and tapk(name, 2)
    time.sleep(1.5)
    Image.open(io.BytesIO(adb('exec-out', 'screencap', '-p'))).save(f'shots/pg_{name.lower()}_{_code}_{_rig}.png')
    if name == 'Radio':  # the bottom of the page
        for n in range(1, 5):
            adb('shell', 'input', 'swipe', '1200', '750', '1200', '250', '600'); time.sleep(1.5)
            Image.open(io.BytesIO(adb('exec-out', 'screencap', '-p'))).save(f'shots/pg_{name.lower()}_{_code}_{_rig}_end{n}.png')
    print(name, 'opened' if ok else 'NOT opened', flush=True)
    tapk('Cancel', 1)
setprefs(rig_control_enabled=False, rig_type='none', js8cfg_ptt_method='vox')
print('DONE', flush=True)
