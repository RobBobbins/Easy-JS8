import sys
sys.argv = ['ui1', '1', 'U', '']
src = open('ui1.py', encoding='utf8').read().split('\nfor code in LAYS:')[0]
exec(src)
layout('U')
print(open_station(), flush=True)
def nodes():
    out=[]
    for n in re.findall(r'<node [^>]*>', x()):
        t=re.search(r' text="([^"]*)"',n).group(1); r=re.search(r'resource-id="([^"]*)"',n).group(1).split('/')[-1]; c=re.search(r' class="([^"]*)"',n).group(1).split('.')[-1]
        if t or r: out.append(f'{c}|{r}|{t[:40]}')
    return out
tapk('General',2)
print(nodes()[:40], flush=True)
print(pv('x') if 'pv' in globals() else '', flush=True)
print(sorted(set(re.findall(r'name="([^"]+)"', getprefs())))[:150], flush=True)
print('DONE')
