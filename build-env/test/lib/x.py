import sys
sys.argv = ['ui2', '1', '']
src = open('ui2.py', encoding='utf8').read().split('\nfor code in LAYS:')[0]
exec(src)
layout('U')
sec_i(); sec_j(); sec_k()
print('DONE', flush=True)
