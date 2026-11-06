// Easy JS8 stand-in for fldigi's fl_digi.h: the names the RTTY viewer (view_rtty.cxx) reads from
// fldigi's main window headers, with fldigi's values (rtty.h, util.h).
// fldigi is GPL-3.0-or-later (../../upstream/COPYING); so is this file.
#pragma once

#include <algorithm>
#include <cmath>
#include <cstdlib>

#define RTTY_SampleRate 8000
#define LETTERS 0x100
#define FIGURES 0x200
#define CLAMP(x, low, high) (((x)>(high))?(high):(((x)<(low))?(low):(x)))
