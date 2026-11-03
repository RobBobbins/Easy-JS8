"""Separate JS8Call profile for the T rows: JS8Call.exe --rig-name ELINK. Rob's own JS8Call.ini is only read.
No rig, audio on VB-Matrix virtual devices only, every reporting network off, API on TCP 2443."""
import os, re, subprocess
SRC = os.path.expandvars(r'%LOCALAPPDATA%\JS8Call\JS8Call.ini')
DIR = os.path.expandvars(r'%LOCALAPPDATA%\JS8Call - ELINK')
os.makedirs(DIR, exist_ok=True)


def endpoint(name):
    """{0.0.x.00000000}.{guid} of an audio endpoint, what SoundInId and SoundOutId hold."""
    o = subprocess.run(['powershell', '-c', f"(Get-PnpDevice -Class AudioEndpoint -Status OK | ? {{ $_.FriendlyName -like '{name} (*' }}).InstanceId"], capture_output=True, text=True).stdout.strip()
    return o.split('\\')[-1], subprocess.run(['powershell', '-c', f"(Get-PnpDevice -Class AudioEndpoint -Status OK | ? {{ $_.FriendlyName -like '{name} (*' }}).FriendlyName"], capture_output=True, text=True).stdout.strip()


in_id, in_name = endpoint('VBMatrix Out 4')
out_id, out_name = endpoint('VBMatrix In 8')
print(in_name, in_id, out_name, out_id)
SET = {'MyCall': 'K4PCT', 'MyGrid': 'FN03', 'MyGroups': '@TESTG', 'Rig': 'None', 'SoundInName': in_name, 'SoundOutName': out_name,
       'SoundInId': f'@ByteArray({in_id})', 'SoundOutId': f'@ByteArray({out_id})',
       'NotificationSoundOutName': out_name, 'NotificationSoundOutId': f'@ByteArray({out_id})',
       'PSKReporter': 'false', 'SpotToAPRS': 'false', 'APRSSpotting': 'false', 'APRSISRelay': 'false', 'CheckForUpdates': 'false',
       'AutoreplyOnAtStartup': 'true', 'AutoreplyConfirmation': 'false', 'TCPEnabled': 'true', 'AcceptTCPRequests': 'true', 'TCPServerPort': '2443',
       'UDPEnabled': 'false', 'WSJTX_UDPEnabled': 'false', 'N1MM_Enabled': 'false', 'N3FJP_Enabled': 'false', 'DialFreq': '10130000'}
SET.update(PTTport='', CATSerialPort='')
out, seen = [], set()
for l in open(SRC, encoding='utf8', errors='replace').read().split('\n'):
    k = l.split('=', 1)[0]
    if k in SET and '=' in l:
        l = f'{k}={SET[k]}'; seen.add(k)
    out.append(l)
open(os.path.join(DIR, 'JS8Call.ini'), 'w', encoding='utf8', newline='\n').write('\n'.join(out))
print('not in the file', sorted(set(SET) - seen))
print([l[:60] for l in out if re.match(r'.*(pots|Spot|APRS|Report|TCP|Udp|UDP|Update|Server)', l)][:22])
