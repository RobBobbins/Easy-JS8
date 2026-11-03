"""U rows against the real APRS-IS and JS8NET as VA3OSO (Rob, 2026-11-02). ureal.py <run>. realrelay.py must be running."""
exec(open('ui7.py', encoding='utf8').read().split("row_list = [")[0].replace('"""Upright rows never tested: H, I, J. ui7.py <run> [ID,ID]"""', ''))
# Rob's own call and grid from his JS8Call.ini; dial is his DialFreq
REAL = dict(callsign='VA3OSO', grid='EN94MN', my_info='', last_frequency='14078000', psk_reporter=True, aprs_is=True, js8cfg_aprs_relay=False,
            js8cfg_aprs_server='10.0.2.2', js8cfg_aprs_port='14581', debug_spot_host='10.0.2.2', debug_psk_host='10.0.2.2',
            js8cfg_heartbeat_anywhere=False, js8cfg_hb_timer_on=False)
STAND = dict(js8cfg_aprs_server='10.0.2.2', js8cfg_aprs_port='14580', aprs_is=False, psk_reporter=False)


def rlog():
    try:
        return open('realrelay.log', encoding='utf8', errors='replace').read().splitlines()
    except OSError:
        return []


b = len(rlog())
up(**REAL)
time.sleep(3)
inj('VA3OSO: @APRSIS GRID EN94MN', 1500, 3, -5)
t0 = time.time()
got = {}
while time.time() - t0 < 200:
    l = rlog()[b:]
    got['U01'] = [x for x in l if 'SRV> # logresp' in x]
    got['U02'] = [x for x in l if x[9:].startswith('WATCH VA3OSO>') and 'JS8' in x]
    got['U03'] = [x for x in l if 'JS8NET sent' in x and '"RX.LOCAL"' in x and 'VA3OSO' in x]
    if all(got.values()):
        break
    time.sleep(5)
l = rlog()[b:]
fresh(**STAND)
res = [('U01', any('verified' in x for x in got['U01']), (got['U01'] or [x for x in l if 'APRS' in x or 'APP>' in x][-1:] or ['no APRS connection'])[0][9:100]),
       ('U02', bool(got['U02']), (got['U02'] or ['not seen on the APRS-IS feed'])[0][9:100]),
       ('U03', bool(got['U03']), (got['U03'] or ['no RX.LOCAL sent'])[0][9:100])]
for i, ok, note in res:
    print(i, 'PASS' if ok else 'FAIL', note, flush=True)
print(f'{time.time() - t0:.0f} s')
json.dump(res, open(f'ureal.{_run}.json', 'w'))
print('DONE', flush=True)
