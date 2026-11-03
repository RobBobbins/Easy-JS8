"""Upright waterfall picture for J28 to J30. ulook.py <run>"""
exec(open('ui7.py', encoding='utf8').read().split("row_list = [")[0].replace('"""Upright rows never tested: H, I, J. ui7.py <run> [ID,ID]"""', ''))
up(last_frequency='10130000'); wf_shown(); time.sleep(22); shot(f'jlook_U_{_run}')
print('DONE', flush=True)
