"""Settings Reset Frame Sizes now also resets PC and FT8Call. ui12.py <run> <P|F>"""
import sys as _s
_s.argv = _s.argv[:3]
exec(open('ui11.py', encoding='utf8').read().split("\nrow_list = [")[0])


def i25s():
    """Drag Band Activity's right edge, then Settings > Reset Frame Sizes, then Cancel."""
    up()
    ok, moved = i24l()
    if not ok:
        return False, f'divider did not move {moved}'
    shot(f'i25s_{L}_{_run}_moved')
    tp('js8_btn_settings', 2)
    for _ in range(8):
        if 'Reset Frame Sizes' in x():
            break
        adb('shell', 'input', 'swipe', '600', '700', '600', '250', '350'); time.sleep(.7)
    hit = tapk('Reset Frame Sizes', 1.5)
    left = splits()
    tapk('Cancel', 1); time.sleep(1.5); home()
    b = bounds('js8_band_activity'); shot(f'i25s_{L}_{_run}_reset')
    return hit and not left, f'saved after reset {left}, Band Activity {b}'


up()
row('I25S' + L, i25s)
adb('shell', 'settings', 'put', 'system', 'user_rotation', '0')
json.dump(out, open(f'ui12.{_run}.{L}.json', 'w'))
print('DONE', flush=True)
