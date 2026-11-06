// Easy JS8 stand-in for fldigi's modem.h: the part of fldigi's modem class the RTTY viewer uses.
// fldigi is GPL-3.0-or-later (../../upstream/COPYING); so is this file.
#pragma once

#include <cmath>

#include "globals.h"

#define SIGSEARCH 5
#define TWOPI (2.0 * M_PI)

class modem {
public:
	enum { CAP_AFC = 1 << 0, CAP_AFC_SR = 1 << 1, CAP_REV = 1 << 2 };
	virtual ~modem() {}
	virtual void init() {}
	virtual void rx_init() {}
	virtual void tx_init() {}
	virtual void restart() {}
	virtual int rx_process(const double *, int) { return 0; }
	virtual int tx_process() { return 0; }

protected:
	trx_mode mode = 0;
	unsigned cap = 0;
	double samplerate = 8000;
	bool reverse = false;
	double bandwidth = 0;
	void set_bandwidth(double bw) { bandwidth = bw; }
};
