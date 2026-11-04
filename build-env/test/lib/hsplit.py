"""W48, W49: split through the app's built-in Hamlib (NET rigctl, model 2) against Hamlib's own rigctld on the PC.
The Rig list no longer offers the network choices (2026-11-03), so this is the path still left for a network rigctld:
rig_type hamlib_usb, model 2, which the service opens by host and port (openHamlibNetwork). Same checks as wham2.py W25 / W26.
hsplit.py <run>. rigctld must be running: rigctld.exe -m 1 -t 4532; the app reaches it as 10.0.2.2:4532."""
import sys as _s
exec(open('wham2.py', encoding='utf8').read().split("\nrow('W20'")[0].replace('"""W20 to W33', '"""', 1))
HAM = {**HAM, 'rig_type': 'hamlib_usb', 'rig_hamlib_model': '2'}
HAM.pop('rig_network_kind')

row('W48', w25)
row('W49', w26)
up(**NORIG)
adb('shell', 'settings', 'put', 'system', 'user_rotation', '0')
json.dump(out, open(f'hsplit.{_run}.json', 'w'))
print('DONE', flush=True)
