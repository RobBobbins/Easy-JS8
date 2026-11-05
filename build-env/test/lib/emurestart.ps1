# Restart S22-Fresh (emulator-5600): est 50 s, limit 55 s, enforced here. Exit 0 when it is back and named S22-Fresh.
# sync first: closing it without one left empty bytes at the end of ALL.TXT (W129 run 33).
# Started with Start-Process: the Git Bash one froze, then died (HANDOFF).
$adb = "C:\Users\Robert\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$emu = "C:\Users\Robert\AppData\Local\Android\Sdk\emulator\emulator.exe"
$t0 = Get-Date; $limit = $t0.AddSeconds(55)
"START emulator restart est 50 s, limit 55 s"
if ((& $adb -s emulator-5600 emu avd name 2>$null | Select-Object -First 1) -ne 'S22-Fresh') { "not S22-Fresh on 5600"; exit 2 }
& $adb -s emulator-5600 shell sync
& $adb -s emulator-5600 emu kill | Out-Null
# the old one must be gone first: a start while it shuts down exits at once (2026-11-05)
$gone = $t0.AddSeconds(15)
while ((Get-Process -Name 'qemu-system*', 'emulator' -ErrorAction SilentlyContinue) -and (Get-Date) -lt $gone) { Start-Sleep 1 }
# one close took the whole limit (15:31, 2026-11-05): after 15 s it is stopped (the sync above is already done)
Get-Process -Name 'qemu-system*', 'emulator' -ErrorAction SilentlyContinue | Stop-Process -Force
while ((Get-Process -Name 'qemu-system*', 'emulator' -ErrorAction SilentlyContinue) -and (Get-Date) -lt $limit) { Start-Sleep 1 }
Start-Process $emu -ArgumentList "-avd S22-Fresh -no-snapshot -port 5600"
$b = ''
# the reply as one trimmed string: before boot adb can give a one-item list, and -notmatch on a list filters it
# (an empty list is false), so the wait ended at the first try (2026-11-05)
while ($b -ne '1' -and (Get-Date) -lt $limit) { Start-Sleep 3; $b = ("" + (& $adb -s emulator-5600 shell getprop sys.boot_completed 2>$null)).Trim() }
# shared storage mounts a few seconds after boot_completed: until then ALL.TXT is not there (2026-11-05)
while ($b -eq '1' -and -not (& $adb -s emulator-5600 shell ls /sdcard/Download 2>$null) -and (Get-Date) -lt $limit) { Start-Sleep 1 }
if (-not (& $adb -s emulator-5600 shell ls /sdcard/Download 2>$null)) { $b = '' }
$s = [int]((Get-Date) - $t0).TotalSeconds
if ($s -gt 50) { "OVER emulator restart past its estimate of 50 s" }
if ($b -ne '1') { "TIMEOUT emulator restart past its limit of 55 s"; exit 1 }
if ((& $adb -s emulator-5600 emu avd name | Select-Object -First 1) -ne 'S22-Fresh') { "not S22-Fresh after restart"; exit 2 }
"END emulator restart $s s, est 50 s"
exit 0
