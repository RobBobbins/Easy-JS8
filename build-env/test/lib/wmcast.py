"""W52: WSJT-X UDP API to a multicast group (239.255.0.1:2239, TTL 1) out of each emulator interface, received on the PC.
First the same messages to 10.0.2.2:2239 (unicast) show the PC listener hears the app at all. wmcast.py <run>
JTAlert listens on 2237 on this PC, so 2239 is used."""
import sys as _s
exec(open('ui7.py', encoding='utf8').read().split("row_list = [")[0].replace('"""Upright rows never tested: H, I, J. ui7.py <run> [ID,ID]"""', ''))
import socket, struct, threading

G, P = '239.255.0.1', 2239
got = []


def listen():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    s.bind(('', P))
    for ip in socket.gethostbyname_ex(socket.gethostname())[2] + ['127.0.0.1']:
        try:
            s.setsockopt(socket.IPPROTO_IP, socket.IP_ADD_MEMBERSHIP, struct.pack('4s4s', socket.inet_aton(G), socket.inet_aton(ip)))
        except OSError:
            pass
    while True:
        b, a = s.recvfrom(65535)
        got.append((time.time(), a[0], b[:4]))


threading.Thread(target=listen, daemon=True).start()


def heard(server, ifs, wait=40):
    n0 = len(got)
    fresh(js8cfg_wsjtx_enabled=True, js8cfg_wsjtx_server=server, js8cfg_wsjtx_port=str(P), js8cfg_wsjtx_ttl='1', js8cfg_wsjtx_interfaces=ifs)
    time.sleep(wait)
    return len(got) - n0


def w52():
    uni = heard('10.0.2.2', '')
    m = {i: heard(G, i) for i in ('eth0', 'wlan0')}
    ok = uni > 0 and all(v > 0 for v in m.values())
    return ok, f'unicast 10.0.2.2: {uni} packets; multicast {G}: ' + ', '.join(f'{k} {v} packets' for k, v in m.items())


row('W52', w52)
fresh(js8cfg_wsjtx_enabled=False, js8cfg_wsjtx_server=None, js8cfg_wsjtx_port=None, js8cfg_wsjtx_interfaces=None)
json.dump(out, open(f'wmcast.{_run}.json', 'w'))
print('DONE', flush=True)
