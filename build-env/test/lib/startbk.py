"""W50, W51: leaving with Back on the main screen and opening the app again is a new start (PC close and restart),
even though Android keeps the process: RX and TX are set again from Receiver (RX) off at startup /
Transmitter (TX) off at startup (MainActivity.onStop -> Js8MainFragment.appClosed). Upright. startbk.py <run>"""
import sys as _s
exec(open('ui7.py', encoding='utf8').read().split("row_list = [")[0].replace('"""Upright rows never tested: H, I, J. ui7.py <run> [ID,ID]"""', ''))


def status():
    """RX on: Decoding or Receiving. RX off: the label is empty (PC guiUpdate, not monitoring)."""
    reach('js8_status_state'); w = html.unescape(x())
    for s in ('Decoding', 'Receiving'):
        if f'text="{s}"' in w:
            return 'on'
    m = re.search(r'<node [^>]*text="([^"]*)"[^>]*resource-id="[^"]*js8_status_state"', w)
    return 'off' if m and m.group(1) == '' else '?'


def txpref():
    m = re.search(r'name="js8cfg_tx_enabled" value="(\w+)"', getprefs())
    return m.group(1) if m else '?'


def back_and_open():
    home(); top()
    adb('shell', 'input', 'keyevent', '4'); time.sleep(3)
    pid = adb('shell', 'pidof', PKG).decode().strip()
    adb('shell', 'monkey', '-p', PKG, '-c', 'android.intent.category.LAUNCHER', '1'); time.sleep(8)
    return pid


def w50():
    """RX off at startup and TX off at startup unticked: RX and TX turned off by hand, Back, open: both on again."""
    up(js8cfg_monitor_off=False, js8cfg_transmit_off=False); time.sleep(4)
    before = status()
    tp('js8_btn_rx', 2); tp('js8_btn_tx', 2)  # setprefs would stop the app: a new start of its own
    mid, txm = status(), txpref()
    pid = back_and_open()
    after, tx = status(), txpref()
    return before == 'on' and mid == 'off' and txm == 'false' and after == 'on' and tx == 'true', f'RX {before} -> {mid} -> {after}, TX {txm} -> {tx} after Back and open (process kept: {bool(pid)})'


def w51():
    """Both ticked: RX turned on by hand and TX on, Back, open: both off again."""
    up(js8cfg_monitor_off=True, js8cfg_transmit_off=True); time.sleep(4)
    if status() != 'on':
        tp('js8_btn_rx', 3)
    tp('js8_btn_tx', 2)
    mid, txm = status(), txpref()
    pid = back_and_open()
    after, tx = status(), txpref()
    return mid == 'on' and txm == 'true' and after == 'off' and tx == 'false', f'RX {mid} -> {after}, TX {txm} -> {tx} after Back and open (process kept: {bool(pid)})'


row('W50', w50)
row('W51', w51)
fresh(js8cfg_monitor_off=False, js8cfg_transmit_off=False)
json.dump(out, open(f'startbk.{_run}.json', 'w'))
print('DONE', flush=True)
