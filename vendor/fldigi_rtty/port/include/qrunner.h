// Easy JS8 stand-in for fldigi's qrunner.h: fldigi queues REQ calls for its screen thread;
// Easy JS8 runs the viewer on its own thread and calls them at once.
// fldigi is GPL-3.0-or-later (../../upstream/COPYING); so is this file.
#pragma once

#define REQ(f, ...) (f)(__VA_ARGS__)
