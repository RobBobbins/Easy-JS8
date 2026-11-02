import os
ROOT = r'E:\new-w3bguru-websites\easy js8'
A = ROOT + r'\android\app\src\main\java\com\js8call\example'

def sub(path, pairs):
    if not os.path.isabs(path):
        path = os.path.join(A, path)
    b = open(path, 'rb').read()
    crlf = b'\r\n' in b
    t = b.decode('utf8').replace('\r\n', '\n')
    for a, c in pairs:
        if a not in t:
            raise SystemExit('NOT FOUND in %s:\n%s' % (path, a))
        t = t.replace(a, c, 1)
    if crlf:
        t = t.replace('\n', '\r\n')
    open(path, 'wb').write(t.encode('utf8'))
