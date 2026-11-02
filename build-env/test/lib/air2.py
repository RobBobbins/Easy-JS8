"""Replies, checksums, buffers, relay, mailbox, gates. air2.py <run> [ID,ID]"""
from onair2 import *
assert_avd()
run = int(sys.argv[1])
only = sys.argv[2].split(',') if len(sys.argv) > 2 else None
t = T(run, only)
c = t.call
M = 'VE3TST'
T.n = (int(time.time()) // 60 % 200) * 26


def fix(i, ok, note=''):
    if not only or i in only:
        t.mark(i, ok, note)


fresh()
# direct queries
a = c(); t.expect('B01', [f'{a}: {M} SNR?'], f'{a} SNR')
a = c(); t.expect('B02', [f'{a}: {M} INFO?'], f'{a} INFO TEST RIG')
a = c(); t.expect('B03', [f'{a}: {M} STATUS?'], f'{a} (STATUS )?IDLE')
a = c(); t.expect('B04', [f'{a}: {M} GRID?'], f'{a} GRID CM87')
a = c(); t.expect('B05', [f'{a}: {M} HEARING?'], f'{a} HEARING')
a = c(); b = c(1); t.expect('B07', [f'{b}: @ALLCALL CQ CQ', f'{a}: {M} QUERY CALL {b} {sum16(b)}'], f'{a} YES')
a = c(); t.expect('B09', [f'{a}: {M} QUERY MSGS'], f'{a} NO')
a = c(); t.expect('B12', [f'{a}: @TESTG SNR?'], f'{a} SNR')
a = c(); t.expect('B13', [f'{a}: @TESTG INFO?'], f'{a} INFO')
a = c(); t.expect('B14', [f'{a}: @TESTG STATUS?'], f'{a} (STATUS )?IDLE')
a = c(); t.expect('B15', [f'{a}: @TESTG GRID?'], f'{a} GRID')
a = c(); t.expect('B16', [f'{a}: @TESTG HEARING?'], f'{a} HEARING')
a = c(); pl = 'HI THERE'; t.expect('C01', [f'{a}: {M} MSG {pl} {sum16(pl)}'], f'{a} ACK')
a = c(); t.silent('C02', [f'{a}: {M} MSG HI THERE ZZZ'])
# mailbox
a = c(); d = c(1); pl = f'{d} HI YOU'
t.expect('C03', [f'{a}: {M} MSG TO:{pl} {sum16(pl)}'], f'{a} ACK')
t.expect('B08', [f'{d}: {M} QUERY MSGS'], f'{d} YES MSG ID')
ids = [re.search(r'MSG ID (\d+)', l).group(1) for l in alltxt() if re.search(f'{d} YES MSG ID', l)]
q = f'MSG {ids[-1]}' if ids else 'MSG 0'
t.silent('C05', [f'{d}: {M} QUERY {q} ZZZ'])
t.expect('B10', [f'{d}: {M} QUERY {q} {sum16(q)}'], f'{d} MSG')
a = c(); d = c(1); pl = f'{d} HI YOU'; t.silent('C04', [f'{a}: {M} MSG TO:{pl} ZZZ'], a)
a = c(); d = c(1); pl = f'{d} HI YOU'
t.expect('B11a', [f'{a}: {M} MSG TO:{pl} {sum16(pl)}'], f'{a} ACK')
t.expect('B11', [f'{d}: @HB QUERY MSGS'], f'{d} YES MSG ID', 16)
a = c(); d = c(1); pl = f'{d} HI YOU'
t.expect('B17a', [f'{a}: {M} MSG TO:{pl} {sum16(pl)}'], f'{a} ACK')
t.expect('B17', [f'{d}: @ALLCALL QUERY MSGS'], f'{d} YES', 16)
t.silent('B17b', [f'{d}: @ALLCALL QUERY MSGS'], '.', 12)
# relay
a = c(); dest = c(1); pl = 'HELLO BOB'; t.expect('D01', [f'{a}: {M}> {dest} {pl} {sum16(dest + " " + pl)}'], dest, 18)
a = c(); dest = c(1); t.silent('C06', [f'{a}: {M}> {dest} HELLO BOB ZZZ'], dest)
a = c(); o = c(1); p = f'MSG RELAY TEXT *DE* {o}'
t.expect('D05', [f'{a}: {M}> {p} {sum16(p)}'], 'ACK', 16)
fix('D05i', any('RELAY TEXT' in str(m) for m in inbox()), 'Inbox via API')
a = c(); o = c(1); d = c(2); p = f'MSG TO:{d} VIA YOU *DE* {o}'; t.expect('D06', [f'{a}: {M}> {p} {sum16(p)}'], 'ACK', 16)
a = c(); o = c(1); p = f'QUERY MSGS *DE* {o}'; t.expect('D07', [f'{a}: {M}> {p} {sum16(p)}'], 'NO', 16)
a = c(); o = c(1); b = c(2); p = f'QUERY CALL {b} *DE* {o}'; t.expect('D08', [f'{b}: @ALLCALL CQ CQ', f'{a}: {M}> {p} {sum16(p)}'], 'YES', 16)
a = c(); o = c(1); pl = f'HI PATH *DE* {o}'; t.expect('D09', [f'{a}: {M} MSG {pl} {sum16(pl)}'], f'{a}>{o} ACK|{o}>{a} ACK', 16)
a = c(); dest = c(1); pl = 'GROUP RELAY'; t.expect('D10', [f'{a}: @TESTG> {dest} {pl} {sum16(dest + " " + pl)}'], dest, 16)
# buffers
a = c(); pl = 'A LONG'; b2 = 'MESSAGE HERE'
t.silent('C08a', [(f'{a}: {M} MSG {pl}', 1500, 1)], a, 7)
t.expect('C08', [(f' {b2} {sum16(pl + " " + b2)}', 1500, 6)], f'{a} ACK')
a = c(); e = c(1); t.silent('B26', [(f'{a}: {M} MSG ARRIVING', 1700, 1), f'{e}: {M} SNR?'], '.', 12)
a = c(); pl = 'CLOSE ME'; t.expect('C09', [(f'{a}: {M} MSG {pl} {sum16(pl)}', 2000, 1)], f'{a} ACK', 80)
a = c(); t.silent('C10', [(f'{a}: {M} MSG', 2200, 1)], '.', 95)
time.sleep(5)
a = c(); e = c(1); pl = 'NEW ONE'; t.expect('C11', [(f'{a}: {M} MSG OLD TEXT', 2300, 1), (f'{e}: {M} MSG {pl} {sum16(pl)}', 2300, 3)], f'{e} ACK', 16)
time.sleep(70)
a = c(); pl = 'DRIFT'; b2 = 'OK HERE'
t.expect('C12', [(f'{a}: {M} MSG {pl}', 1800, 1), (f' {b2} {sum16(pl + " " + b2)}', 1806, 6)], f'{a} ACK', 16)
a = c(); inj(f'{a}: @APRSIS MSG TO:{M} HELLO DE BOB'); time.sleep(4)
msgs = inbox(); fix('C07', any('HELLO DE BOB' in str(m) for m in msgs), str(msgs[:1])[:140])
# Typing Box gate
tapk('js8_typing_box'); text('HELLOBOX'); adb('shell', 'input', 'keyevent', '111'); time.sleep(1)
a = c(); t.silent('B25', [f'{a}: {M} SNR?'], '.', 12)
fresh()  # AUTO settings below restart anyway; Typing Box emptied by hand next
p = find(dump(), 'js8_typing_box')
if p:
    adb('shell', 'input', 'tap', str(p[0]), str(p[1]))
    for _ in range(9):
        adb('shell', 'input', 'keyevent', '67')
    adb('shell', 'input', 'keyevent', '111')
# settings
fresh(js8cfg_avoid_allcall=True); a = c(); d = c(1); t.silent('B18', [f'{a}: @ALLCALL QUERY MSGS'])
fresh(autoreply_enabled=False, js8cfg_autoreply_startup=False); a = c(); t.silent('B19', [f'{a}: {M} SNR?'])
a = c(); dest = c(1); pl = 'HELLO BOB'; t.silent('D02', [f'{a}: {M}> {dest} {pl} {sum16(dest + " " + pl)}'])
fresh(relay_enabled=False); a = c(); dest = c(1); t.silent('D03', [f'{a}: {M}> {dest} {pl} {sum16(dest + " " + pl)}'])
fresh(js8cfg_auto_whitelist='W1AW'); a = c(); t.silent('B23a', [f'{a}: {M} SNR?']); t.expect('B23', [f'W1AW: {M} SNR?'], 'W1AW SNR')
fresh(js8cfg_auto_blacklist='K9BLK'); t.silent('B24', [f'K9BLK: {M} SNR?']); a = c(); t.expect('B24a', [f'{a}: {M} SNR?'], f'{a} SNR')
fresh(js8cfg_autoreply_confirmation=True)
a = c(); inj(f'{a}: {M} SNR?'); time.sleep(4); xt = texts()
fix('B20a', 'Autoreply Confirmation Required' in xt, str(xt[:3]))
b = len(alltxt()); tapk('Yes') or tapk('YES'); fix('B20', bool(t.tx(b, f'{a} SNR', 20)))
time.sleep(8)
a = c(); inj(f'{a}: {M} SNR?'); time.sleep(4); b = len(alltxt()); tapk('No') or tapk('NO'); fix('B21', not t.tx(b, '.', 12))
json.dump(t.res, open(f'air2.{run}.json', 'w'))
print('DONE', flush=True)
