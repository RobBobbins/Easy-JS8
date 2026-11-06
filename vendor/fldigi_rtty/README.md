# fldigi RTTY viewer (Easy JS8 RTTY decodes)

fldigi's multi-channel RTTY decoder (the signal browser's RTTY viewer, `view_rtty`), used by
`adapters/android/jni/rtty_monitor.cpp` to show RTTY heard in the JS8 audio in the Message Window.

- `upstream/` holds files copied unchanged from fldigi 4.2.13
  (https://git.code.sf.net/p/fldigi/fldigi, commit `e7c3ab3709ad62a7d91829364eb48a05fa325c0d`),
  in fldigi's own folders, with fldigi's `COPYING` (GPL-3.0-or-later) and `AUTHORS`.
  Taken from Rob's fldigi-android (`E:\new-w3bguru-websites\fldigi-android\upstream`).
- `port/include/` replaces the fldigi headers that belong to its FLTK screen (main window, modem
  base class, settings, waterfall widget, screen-thread queue) with the few names the viewer reads.
- `port/waterfall.cxx` is fldigi's waterfall arithmetic (8192-point FFT, powerDensity) without drawing;
  `port/settings.cxx` holds fldigi's default settings: 170 Hz shift, 45 baud, 5-bit Baudot, 1.5 stop
  bits, 30 channels 100 Hz apart, squelch -6.0, 15 s channel timeout.
