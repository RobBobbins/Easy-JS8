from onair import *
assert_avd()
run = int(sys.argv[1]); only = sys.argv[2].split(',') if len(sys.argv) > 2 else None
BASE = dict(autoreply_enabled=True, js8cfg_autoreply_startup=True, js8cfg_autoreply_confirmation=False, relay_enabled=True, tx_submode=2,
  js8cfg_multi=True, js8cfg_transmit_off=False, js8cfg_monitor_off=False, js8cfg_reset_activity=False, psk_reporter=True, aprs_is=False,
  js8cfg_swl=False, js8cfg_auto_whitelist='', js8cfg_auto_blacklist='', js8cfg_rx_blocklist='', js8cfg_avoid_allcall=False, my_groups='@TESTG',
  my_info='TEST RIG', my_status='IDLE', js8cfg_hb=True, js8cfg_hb_ack=True, js8cfg_tx_watchdog='60', js8cfg_activity_aging='2', js8cfg_callsign_aging='0')
def fresh(**k):
    setprefs(**{**BASE, **k}); start()
t = T(run, only); c = t.call
fresh()
M = 'VE3TST'
a = c(); t.expect('B01', [f'{a}: {M} SNR?'], f'{a} SNR')
a = c(); t.expect('B02', [f'{a}: {M} INFO?'], f'{a} INFO TEST RIG')
a = c(); t.expect('B03', [f'{a}: {M} STATUS?'], f'{a} (STATUS )?IDLE')
a = c(); t.expect('B04', [f'{a}: {M} GRID?'], f'{a} GRID CM87')
a = c(); t.expect('B05', [f'{a}: {M} HEARING?'], f'{a} HEARING')

a = c(); b = c(1); t.expect('B07', [f'{b}: @ALLCALL CQ CQ', f'{a}: {M} QUERY CALL {b} {sum16(b)}'], f'{a} YES')
a = c(); t.expect('B09', [f'{a}: {M} QUERY MSGS'], f'{a} NO')
a = c(); d = c(1); pl = f'TO:{d} HI YOU'
t.expect('C03', [f'{a}: {M} MSG {pl} {sum16(pl)}'], f'{a} ACK')
t.expect('B08', [f'{d}: {M} QUERY MSGS'], f'{d} YES MSG ID')
ids = [re.search(r'MSG ID (\d+)', l).group(1) for l in alltxt() if re.search(f'{d} YES MSG ID', l)]
q = f'MSG {ids[-1]}' if ids else 'MSG 0'
t.silent('C05', [f'{d}: {M} QUERY {q} ZZZ'])
t.expect('B10', [f'{d}: {M} QUERY {q} {sum16(q)}'], f'{d} MSG HI YOU')
a = c(); d = c(1); pl = f'TO:{d} HI YOU'; t.silent('C04', [f'{a}: {M} MSG {pl} ZZZ'])
a = c(); t.expect('B11', [f'{a}: @HB QUERY MSGS'], f'{a} NO', 16)
a = c(); t.expect('B17', [f'{a}: @ALLCALL SNR?'], f'{a} SNR', 20)
t.silent('B17b', [f'{a}: @ALLCALL SNR?'])
fresh(js8cfg_avoid_allcall=True); a = c(); t.silent('B18', [f'{a}: @ALLCALL SNR?'])
fresh(autoreply_enabled=False, js8cfg_autoreply_startup=False); a = c(); t.silent('B19', [f'{a}: {M} SNR?'])
fresh(js8cfg_auto_whitelist='W1AW'); a = c(); t.silent('B23a', [f'{a}: {M} SNR?']); t.expect('B23', [f'W1AW: {M} SNR?'], 'W1AW SNR')
fresh(js8cfg_auto_blacklist='K9BLK'); t.silent('B24', [f'K9BLK: {M} SNR?']); a = c(); t.expect('B24a', [f'{a}: {M} SNR?'], f'{a} SNR')
fresh()
a = c(); pl = 'A LONG'; b2 = 'MESSAGE HERE'; full = pl + ' ' + b2
t.silent('C08a', [(f'{a}: {M} MSG {pl}', 1500, 1)], '.', 7)
t.expect('C08', [(f' {b2} {sum16(full)}', 1500, 6)], f'{a} ACK')
a = c(); t.silent('B26', [(f'{a}: {M} MSG ARRIVING', 1700, 1), (f'{c()}: {M} SNR?', 900, 3)], '.', 12)
a = c(); dest = c(1); pl = 'HELLO BOB'
t.expect('D01', [f'{a}: {M}> {dest} {pl} {sum16(dest + " " + pl)}'], dest, 18)
a = c(); dest = c(1); t.silent('C06', [f'{a}: {M}> {dest} HELLO BOB ZZZ'])
fresh(relay_enabled=False); a = c(); dest = c(1); pl='HELLO BOB'; t.silent('D03', [f'{a}: {M}> {dest} {pl} {sum16(dest + " " + pl)}'])
fresh(autoreply_enabled=False, js8cfg_autoreply_startup=False); a = c(); dest = c(1); t.silent('D02', [f'{a}: {M}> {dest} {pl} {sum16(dest + " " + pl)}'])
import json; json.dump(t.res, open(f'air1.{run}.json', 'w'))
