"""part(): one timed part of a test run (a row, a probe, a restart): an estimate, and a limit 10% over that the run
enforces itself. Past the estimate: an OVER line. Past the limit: the adb calls under way are stopped, TimedOut is
raised in the main thread, each of [ids] not yet in [res] is marked FAIL "timed out", and the run goes on."""
import contextlib, ctypes, os, threading, time
import psutil


class TimedOut(BaseException):
    """BaseException, so an except Exception (u.dump) lets it by."""


@contextlib.contextmanager
def part(name, est, ids=(), res=None, mark=None, after=None):
    """A time.sleep finishes before the raise lands, so a part can run past its limit by its longest sleep.
    [after] runs once a part has timed out (e.g. a closed API connection opened again)."""
    # PARTS_SCALE=3: a measuring run, the limits 3 times as long, to set estimates from the END times
    lim, t0, lock, on = est * 1.1 * float(os.environ.get('PARTS_SCALE', '1')), time.time(), threading.Lock(), [True]
    main = ctypes.c_ulong(threading.main_thread().ident)
    print(f'START {name} est {est:.0f} s, limit {lim:.0f} s', flush=True)

    def over():
        print(f'OVER {name} past its estimate of {est:.0f} s', flush=True)

    def expire():
        with lock:
            if not on[0]:
                return
            ctypes.pythonapi.PyThreadState_SetAsyncExc(main, ctypes.py_object(TimedOut))
            for ch in psutil.Process().children(recursive=True):
                try:
                    if ch.name().lower() == 'adb.exe' and 'fork-server' not in ' '.join(ch.cmdline()):
                        ch.kill()
                except psutil.Error:
                    pass
    timers = [threading.Timer(est, over), threading.Timer(lim, expire)]
    for tm in timers:
        tm.daemon = True; tm.start()
    try:
        yield
        with lock:
            on[0] = False
            ctypes.pythonapi.PyThreadState_SetAsyncExc(main, None)  # a raise that had not landed yet
    except TimedOut:
        on[0] = False
        print(f'TIMEOUT {name} past its limit of {lim:.0f} s', flush=True)
        done = {r[0] for r in (res or [])}
        for i in ids:
            if mark and i not in done:
                mark(i, False, 'timed out')
        if after:
            try:
                after()
            except Exception as e:
                print('AFTER', repr(e)[:80], flush=True)
    finally:
        for tm in timers:
            tm.cancel()
    print(f'END {name} {time.time() - t0:.0f} s, est {est:.0f} s', flush=True)
