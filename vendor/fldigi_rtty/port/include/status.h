// Easy JS8 stand-in for fldigi's status.h: the RTTY viewer's squelch, fldigi's default
// (status/status.cxx: VIEWER_rttysquelch -6.0).
// fldigi is GPL-3.0-or-later (../../upstream/COPYING); so is this file.
#pragma once

struct status {
	double VIEWER_rttysquelch = -6.0;
};
extern status progStatus;
