// Easy JS8 stand-in for fldigi's configuration.h: the settings the RTTY viewer reads, with
// fldigi's defaults (fldigi 4.2.13 configuration.h).
// fldigi is GPL-3.0-or-later (../../upstream/COPYING); so is this file.
#pragma once

struct configuration {
	int LowFreqCutoff = 0;
	int rtty_shift = 3;          // 170 Hz
	int rtty_custom_shift = 450;
	double RTTY_BW = 68.0;
	int rtty_baud = 0;           // 45
	int rtty_bits = 0;           // 5 (Baudot)
	int rtty_parity = 0;         // none
	int rtty_stop = 1;           // 1.5
	int rtty_afcspeed = 1;       // normal
	bool ITA2 = false;
	bool UOSrx = true;
	int VIEWERchannels = 30;
	int VIEWERtimeout = 15;
};
extern configuration progdefaults;

// fldigi's rtty::numshifts (rtty.cxx: the 10 values of rtty::SHIFT)
struct rtty {
	static constexpr int numshifts = 10;
};
