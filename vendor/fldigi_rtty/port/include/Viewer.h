// Easy JS8 stand-in for fldigi's Viewer.h: where the RTTY viewer writes its characters.
// Easy JS8's RTTY monitor (adapters/android/jni/rtty_monitor.cpp) defines both functions.
// fldigi is GPL-3.0-or-later (../../upstream/COPYING); so is this file.
#pragma once

#define NULLFREQ 1e6   // fldigi viewpsk.h

// c == 0: a signal was found on channel ch at freq (Hz); otherwise one decoded character
void viewaddchr(int ch, int freq, char c, int md);
// channel ch lost its signal
void viewclearchannel(int ch);
