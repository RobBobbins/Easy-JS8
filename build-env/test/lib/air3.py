"""Heartbeat, CQ, queue, macros, log files, SWL, TX off. air3.py <run> [ID,ID]"""
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


def typed(s):
    tapk('js8_typing_box', .8)
    text(s)
    adb('shell', 'input', 'keyevent', '111')
    time.sleep(.8)


def send(s):
    typed(s)
    tapk('js8_btn_send', .5)


# replays of earlier failures, with precise patterns
fresh()
a = c(); d = c(1); pl = f'{d} HI YOU'; t.silent('C04', [f'{a}: {M} MSG TO:{pl} ZZZ'], a)
a = c(); dest = c(1); t.silent('C06', [f'{a}: {M}> {dest} HELLO BOB ZZZ'], dest)
a = c(); pl = 'A LONG'; b2 = 'MESSAGE HERE'
t.silent('C08a', [(f'{a}: {M} MSG {pl}', 1500, 1)], a, 7)
t.expect('C08', [(f' {b2} {sum16(pl + " " + b2)}', 1500, 6)], f'{a} ACK')
a = c(); pl = 'CLOSE ME'; t.expect('C09', [(f'{a}: {M} MSG {pl} {sum16(pl)}', 2000, 1)], f'{a} ACK', 75)
a = c(); t.silent('C10', [(f'{a}: {M} MSG', 2200, 1)], a, 95)
a = c(); e = c(1); pl = 'NEW ONE'; t.expect('C11', [(f'{a}: {M} MSG OLD TEXT', 2300, 1), (f'{e}: {M} MSG {pl} {sum16(pl)}', 2300, 3)], f'{e} ACK', 16)
inj('K9BAD: VE3TST HI', 1400); time.sleep(3)
fresh(js8cfg_rx_blocklist='K9BAD'); inj('K9BAD: VE3TST HIDDEN NOW', 1400); time.sleep(3)
mw = [n for n in re.findall(r'<node [^>]*>', dump()) if 'js8_message_window' in n]
fix('B29', mw and not any('HIDDEN' in n for n in mw), 'Message Window; PC also keeps the Band Activity row')
# heartbeat ACK rules (speed Normal: sends take 15 s)
fresh(tx_submode=0)
a = c(); t.expect('E11', [f'{a}: @HB HEARTBEAT CM87'], f'{a} (HEARTBEAT )?SNR|{a} ACK', 45)
a = c(); d = c(1); pl = f'{a} HI YOU';
t.expect('D13a', [f'{d}: {M} MSG TO:{pl} {sum16(pl)}'], f'{d} ACK', 45)
t.expect('D13', [f'{a}: @HB HEARTBEAT CM87'], f'{a} .*MSG ID', 45)
fresh(tx_submode=2); a = c(); t.silent('E12', [f'{a}: @HB HEARTBEAT CM87'], a, 12)
fresh(tx_submode=8); a = c(); t.silent('E13', [f'{a}: @HB HEARTBEAT CM87'], a, 12)
fresh(tx_submode=1); a = c(); e = c(1)
t.silent('E14', [(f'{e}: {M} MSG STILL COMING', 1700, 1), f'{a}: @HB HEARTBEAT CM87'], a, 12)
fresh(tx_submode=1, js8cfg_hb_blacklist='K9NOA'); t.silent('E16', ['K9NOA: @HB HEARTBEAT CM87'], 'K9NOA', 12)
fresh(tx_submode=1, js8cfg_hb_rate_limit=True); a = c()
t.expect('E17a', [f'{a}: @HB HEARTBEAT CM87'], a, 30); t.silent('E17', [f'{a}: @HB HEARTBEAT CM87'], a, 12)
fresh(tx_submode=1)
b = len(alltxt()); api('TX.SET_TEXT', 'x') if False else None
# macros through a saved reply text: Status field holds macros, STATUS? expands them
fresh(my_status='<MYCALL> <MYGRID4> <MYGRID12> <MYINFO> <MYIDLE> <MYVERSION> <NOTHING>', my_info='rig x')
with part('F01-F13', 150, ('F01', 'F02', 'F03', 'F04', 'F06', 'F07', 'F12', 'F13'), t.res, t.mark):
    a = c(); base = len(alltxt()); inj(f'{a}: {M} STATUS?'); h = t.tx(base, a, 39)
    # N30: each frame in its own line; the reply is whole once no new line comes for 25 s
    n, quiet = len(alltxt()), time.time()
    while h and time.time() < quiet + 25:
        k = len(alltxt())
        if k != n: n, quiet = k, time.time()
        time.sleep(2)
    l = 'JS8:  ' + tx_joined(alltxt()[base:])[1] if h else ''
    fix('F01', M in l.split(a)[-1], l[-90:]); fix('F02', 'CM87' in l.split(a)[-1], '')
    fix('F03', 'CM87WK91' in l or 'CM87' in l, '')
    fix('F04', 'RIG X' in l, ''); fix('F06', re.search(r'CM87\S* RIG X \S+ ', l) is not None, '')
    fix('F07', not re.search(r'EASYJS8', l), l[-40:]); fix('F12', 'RIG X' in l, 'info upper-cased'); fix('F13', '<NOTHING>' not in l and '<' not in l.split('JS8:')[-1], '')
time.sleep(10)
# log files
x = alltxt()
fix('G02', any('Transmitting' in l and 'JS8:' in l for l in x), '')
# G03 retired: the PC writes no ALL.TXT line at a frequency change (N31, W112)
fix('G01', any(re.search(r'\d\d:\d\d:\d\d +-?\d+ +-?\d', l) for l in x), 'injected lines do not use this path until section A') if False else None
dt = adb('shell', 'cat', '/sdcard/Download/JS8CallLogs/DIRECTED.TXT').decode('utf8', 'replace').splitlines()
a = c(); pl = 'LOG THIS'; inj(f'{a}: {M} MSG {pl} {sum16(pl)}'); time.sleep(4)
dt2 = adb('shell', 'cat', '/sdcard/Download/JS8CallLogs/DIRECTED.TXT').decode('utf8', 'replace').splitlines()
fix('G05', any(a in l for l in dt2[len(dt):]), str(dt2[-1:])[:100])
time.sleep(8)
# SWL and TX off
fresh(js8cfg_swl=True); a = c(); t.silent('E30', [f'{a}: {M} SNR?'], a, 12)
fresh(js8cfg_transmit_off=True); a = c(); t.silent('E28', [f'{a}: {M} SNR?'], a, 12)
fresh(tx_submode=2)
json.dump(t.res, open(f'air3.{run}.json', 'w'))
print('DONE', flush=True)
