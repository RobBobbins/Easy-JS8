"""Station > Frequencies as Android rows (Rob, 2026-11-26): Add frequency, one row per frequency (a tap opens Edit
Frequency with Delete), Load from file… / Save to file… / Merge from file… / Reset to default frequencies; Frequency
Schedule: the switch, Add schedule and one row per entry, greyed while the switch is off. Replaces ui9.py M35-M38.
freqchk.py <run> [serial]. Each row is a part with its own estimate and a limit 10% over (parts.py)."""
import datetime, re, subprocess, sys, time
import uiautomator2
from parts import part

sys.stdout.reconfigure(encoding='utf8', errors='replace')
RUN = sys.argv[1]
SER = sys.argv[2] if len(sys.argv) > 2 else 'emulator-5600'
ADB = r'C:\Users\Robert\AppData\Local\Android\Sdk\platform-tools\adb.exe'
PKG = 'com.easyjs8.app'
SWITCH = 'Automatically switch bands / frequencies at specific times of day'
OLD = ['Insert ...', 'Load ...', 'Save as ...', 'Merge ...', 'Reset', 'Delete', 'Switch to Frequency Now']
FILE_ROWS = ['Load from file…', 'Save to file…', 'Merge from file…', 'Reset to default frequencies']
# Schedule times away from now: a window holding the current UTC time starts the app's own countdown
H = datetime.datetime.now(datetime.timezone.utc).hour
AT, UNTIL = f'{(H + 3) % 24:02d}:00', f'{(H + 4) % 24:02d}:00'
SUMMARY = f'{AT} to {UNTIL} UTC · ZS'
R40 = r'7\.078.000 MHz \(40m\)'
R20 = r'14\.078.000 MHz \(20m\)'
d = uiautomator2.connect(SER)
res = []


def adb(*a, timeout=30):
    return subprocess.run([ADB, '-s', SER, *a], capture_output=True, timeout=timeout).stdout


def mark(i, ok, note=''):
    print(i, 'PASS' if ok else 'FAIL', str(note)[:300], flush=True)
    res.append((i, bool(ok)))


def snap(tag):
    open(f'shots/freqchk.{RUN}.{tag}.png', 'wb').write(adb('exec-out', 'screencap', '-p'))


def prefs():
    return adb('shell', f'run-as {PKG} cat shared_prefs/{PKG}_preferences.xml').decode('utf8', 'replace')


def pv(k):
    m = re.search(rf'<string name="{re.escape(k)}">([^<]*)</string>', prefs())
    return m.group(1) if m else ''


def start():
    adb('shell', 'am', 'force-stop', PKG)
    adb('shell', 'am', 'start', '-n', f'{PKG}/com.js8call.example.MainActivity')
    time.sleep(7)


def rotate(r):
    adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0')
    adb('shell', 'cmd', 'window', 'user-rotation', 'lock', str(r)); time.sleep(2.5)
    d.set_orientation('natural' if r == 0 else 'left'); time.sleep(2.5)


def tap(o):
    """Coordinate tap at the element's centre (uiautomator2's element click missed buttons with the keyboard up)."""
    b = o.info['bounds']
    d.click((b['left'] + b['right']) // 2, (b['top'] + b['bottom']) // 2)


def page_view():
    sc = d(className='android.widget.ScrollView')
    return sc[sc.count - 1]


def open_frequencies():
    if not d(resourceId=f'{PKG}:id/js8_btn_settings').exists and d(text='Header').exists:
        tap(d(text='Header')); time.sleep(1.5)  # sideways the main screen can open on another tab
    d(resourceId=f'{PKG}:id/js8_btn_settings').click(); time.sleep(2)
    tap(d(description='Station')); time.sleep(2)
    if d(text='Frequency Calibration').exists:  # Station opened on this page
        return
    if not d(text='Notifications').exists and not d(text='Radio').exists:  # opened on another page: ← to the list
        d.press('back'); time.sleep(1.5)
    d(className='android.widget.ScrollView')[0].scroll.to(text='Frequencies'); time.sleep(1)
    tap(d(text='Frequencies')); time.sleep(2)


def to(**k):
    page_view().scroll.to(**k); time.sleep(.8)
    return d(**k)


def shows(t, wait=3):
    """A dialog title: waits for the dialog to finish opening."""
    return d(text=t).wait(timeout=wait)


def under(title_re, summary):
    """The row titled [title_re] has [summary] on the line under it."""
    return re.search(title_re + r'"[^>]*>\s*<node [^>]*text="' + re.escape(summary) + '"', d.dump_hierarchy()) is not None


def btn(n):
    return d(resourceId=f'android:id/button{n}')


def edit(i):
    return d(className='android.widget.EditText')[i]


def hide_kb():
    """Back closes the keyboard only while it shows; otherwise back would close the form."""
    if 'mInputShown=true' in adb('shell', 'dumpsys', 'input_method').decode('utf8', 'replace'):
        d.press('back')
    time.sleep(.8)


def row(i, est, fn):
    with part(i, est, [i], res, mark):
        try:
            r = fn()
            ok, note = r if isinstance(r, tuple) else (r, '')
        except Exception as e:
            ok, note = False, repr(e)[:200]
        mark(i, ok, note)
        if not ok:
            snap(f'fail_{i}')


def w217():
    """Upright: Add frequency at the top, 7.078 000 MHz (40m) with JS8 · All regions under it, the four file rows at
    the end; none of the old buttons."""
    start(); open_frequencies(); snap('upright')
    seen = d.dump_hierarchy()
    add = 'text="Add frequency"' in seen
    to(textContains='10.130'); seen += d.dump_hierarchy()  # the 7.078 row and the line under it on screen
    r40 = re.search(R40, seen) is not None
    summ = under(R40, 'JS8 · All regions')
    to(text='Reset to default frequencies'); snap('upright_files'); seen += d.dump_hierarchy()
    files = [t for t in FILE_ROWS if f'text="{t}"' not in seen]
    old = [t for t in OLD if f'text="{t}"' in seen]
    return add and r40 and summ and not files and not old, \
        f'add row {add} 40m row {r40} summary {summ} missing file rows {files} old buttons {old}'


def w218():
    """Add frequency: Add Frequency form, OK greyed while empty and on 0 (red text); 0.501 + ztest adds the
    0.501 000 MHz row, saved."""
    tap(to(text='Add frequency'))
    title = shows('Add Frequency')
    grey0 = not btn(1).info['enabled']
    edit(0).set_text('0'); time.sleep(.8)
    red = 'Enter the frequency in MHz' in d.dump_hierarchy(); grey1 = not btn(1).info['enabled']
    edit(0).set_text('0.501'); edit(1).set_text('ztest'); time.sleep(.8)
    hide_kb(); snap('add')
    on = btn(1).info['enabled']
    tap(btn(1)); time.sleep(2)
    t = to(textContains='0.501').get_text() if page_view().scroll.to(textContains='0.501') else ''
    saved = '501000|All|JS8|ztest' in pv('js8cfg_frequencies')
    return title and grey0 and red and grey1 and on and 'MHz (' in t and saved, \
        f'title {title} greyed empty {grey0} red on 0 {red} greyed on 0 {grey1} OK on {on} row {t!r} saved {saved}'


def w219():
    """Tapping the row opens Edit Frequency filled in; 0.502, Region 2 saves and the row reads JS8 · Region 2 · ztest."""
    tap(to(textContains='0.501'))
    title = shows('Edit Frequency')
    filled = edit(0).get_text() == '0.501000' and edit(1).get_text() == 'ztest'
    dele = btn(3).exists and btn(3).get_text().lower() == 'delete'
    edit(0).set_text('0.502'); hide_kb()
    tap(d(text='All')); time.sleep(1.2); snap('region_list')
    tap(d(text='Region 2')); time.sleep(1); snap('edit')
    tap(btn(1)); time.sleep(2)
    page_view().scroll.to(textContains='0.502'); time.sleep(.8)
    summ = under(r'0\.502.000 MHz \([^)]*\)', 'JS8 · Region 2 · ztest')
    gone = not d(textContains='0.501').exists
    saved = '502000|Region 2|JS8|ztest' in pv('js8cfg_frequencies')
    return title and filled and dele and summ and gone and saved, \
        f'title {title} filled {filled} delete button {dele} summary {summ} old gone {gone} saved {saved}'


def w220():
    """Delete in Edit Frequency removes the row and the saved line."""
    tap(to(textContains='0.502')); shows('Edit Frequency')
    tap(btn(3)); time.sleep(2)
    gone = not d(textContains='0.502').exists and '502000|' not in pv('js8cfg_frequencies')
    return gone, f'gone {gone}'


def w221():
    """Load from file…, Save to file… and Merge from file… open Android's file picker; back returns to the page."""
    seen = {}
    for t in FILE_ROWS[:3]:
        t0 = time.time()
        tap(to(text=t)); time.sleep(3)
        seen[t] = 'documentsui' in d.app_current().get('package', '')
        if t == FILE_ROWS[1]:
            snap('save_picker')
        for _ in range(4):  # the picker's back goes up a folder first
            if PKG in d.app_current().get('package', ''):
                break
            d.press('back'); time.sleep(1.5)
        time.sleep(1)
        print('  ', t, seen[t], f'{time.time() - t0:.0f} s', flush=True)
    back = d(text=FILE_ROWS[0]).exists
    return all(seen.values()) and back, f'picker {seen} back on page {back}'


def w222():
    """Reset to default frequencies: Yes puts back the default list (an added 0.503 is gone)."""
    tap(to(text='Add frequency')); shows('Add Frequency')
    edit(0).set_text('0.503'); hide_kb(); tap(btn(1)); time.sleep(2)
    added = '503000|' in pv('js8cfg_frequencies')
    tap(to(text='Reset to default frequencies'))
    ask = shows('Reset Working Frequencies')
    tap(d(text='Yes')); time.sleep(2)
    gone = '503000|' not in pv('js8cfg_frequencies') and not d(textContains='0.503').exists
    return added and ask and gone, f'added {added} asked {ask} gone {gone}'


def sw_on():
    return 'name="js8cfg_auto_switch_bands" value="true"' in prefs()


def w223():
    """Schedule switch off: Add schedule greyed (a tap opens nothing). On: Add Schedule with OK greyed, red text on
    25:00; 14.078 AT UNTIL ZS adds 14.078 000 MHz (20m) with "AT to UNTIL UTC · ZS" under it."""
    if sw_on():
        tap(to(text=SWITCH)); time.sleep(1.5)
    tap(to(text='Add schedule')); time.sleep(1.5)
    closed = not d(text='Add Schedule').exists
    snap('sched_off')
    tap(to(text=SWITCH)); time.sleep(1.5)
    on = sw_on()
    tap(to(text='Add schedule'))
    title = shows('Add Schedule')
    edit(0).set_text('14.078'); edit(1).set_text('25:00'); edit(2).set_text(UNTIL); time.sleep(.8)
    red = 'Enter the time as HH:MM' in d.dump_hierarchy(); grey = not btn(1).info['enabled']
    edit(1).set_text(AT); edit(3).set_text('ZS'); time.sleep(.8)
    hide_kb(); snap('sched_add')
    ok = btn(1).info['enabled']
    tap(btn(1)); time.sleep(2)
    page_view().scroll.toEnd(); time.sleep(1); snap('sched_rows')
    summ = under(R20, SUMMARY)
    saved = f'14078000|{AT}|{UNTIL}|ZS' in pv('js8cfg_schedule')
    return closed and on and title and red and grey and ok and summ and saved, \
        f'greyed when off {closed} switch on {on} title {title} red {red} greyed {grey} OK on {ok} row {summ} saved {saved}'


def w224():
    """Edit Schedule shows Delete and Switch to Frequency Now; Switch to Frequency Now goes to 14.078 at once
    (saved as last_frequency); Delete removes the entry; the switch goes back off."""
    pid = adb('shell', 'pidof', PKG).strip()
    tap(d(text=SUMMARY))
    title = shows('Edit Schedule'); snap('sched_edit')
    now = d(text='Switch to Frequency Now').wait(timeout=3)
    dele = btn(3).wait(timeout=3)
    tap(d(text='Switch to Frequency Now')); time.sleep(3)
    hop = pv('last_frequency') == '14078000'
    alive = adb('shell', 'pidof', PKG).strip() == pid
    tap(btn(3)); time.sleep(2)
    gone = f'14078000|{AT}' not in pv('js8cfg_schedule') and not d(text=SUMMARY).exists
    tap(to(text=SWITCH)); time.sleep(1.5)
    off = not sw_on()
    return title and now and dele and hop and alive and gone and off, \
        f'title {title} switch-now {now} delete {dele} went to 14.078 {hop} same process {alive} gone {gone} switch off {off}'


def side():
    open_frequencies(); snap('side')
    add = bool(d(text='Add frequency').exists)
    rows = bool(page_view().scroll.to(textMatches=R40))
    tap(to(text='Add frequency')); ok = bool(shows('Add Frequency')); hide_kb(); snap('side_add')
    ok = ok and bool(btn(1).wait(timeout=3))
    rows = add and rows
    tap(btn(2)); time.sleep(1)
    page_view().scroll.toEnd(); time.sleep(1); snap('side_end')
    end = bool(d(text='Add schedule').exists)
    return rows and ok and end, f'rows {rows} form with OK {ok} schedule at end {end}'


def w225():
    """Sideways (two panes): the rows in the right pane; Add Frequency form opens with its OK reachable."""
    rotate(1); start()
    try:
        return side()
    finally:
        rotate(0)


def w226():
    """Dark mode upright: the page and Edit Frequency (checked by eye)."""
    adb('shell', 'cmd', 'uimode', 'night', 'yes'); start()
    try:
        open_frequencies(); snap('dark')
        tap(to(textMatches=R40)); ok = bool(shows('Edit Frequency')); hide_kb(); snap('dark_edit')
        tap(btn(2)); time.sleep(1)
    finally:
        adb('shell', 'cmd', 'uimode', 'night', 'no')
    return ok, 'dark screenshots taken (checked by eye)'


with part('setup', 15):
    rotate(0)
row('W217', 35, w217)
row('W218', 25, w218)
row('W219', 25, w219)
row('W220', 12, w220)
row('W221', 50, w221)
row('W222', 25, w222)
row('W223', 40, w223)
row('W224', 25, w224)
row('W225', 60, w225)
row('W226', 30, w226)
print('RESULT', 'PASS' if res and all(r[1] for r in res) else 'FAIL', flush=True)
