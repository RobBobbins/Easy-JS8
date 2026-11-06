// Easy JS8 stand-in for fldigi's waterfall.h: the waterfall's signal arithmetic only. The RTTY
// viewer finds signals with powerDensity, read from the same 8192-point FFT fldigi's waterfall
// runs on the received sound (8000 samples a second, 1 Hz a value). Nothing is drawn.
// Arithmetic as fldigi 4.2.13 waterfall.cxx (processFFT, powerDensity), by way of fldigi-android
// (port/waterfall.cxx, VA3OSO).
// fldigi is GPL-3.0-or-later (../../upstream/COPYING); so is this file.
#pragma once

#include "complex.h"
#include "gfft.h"

#define WF_FFTLEN    8192
#define WF_SAMPLERATE 8000
#define WF_BLOCKSIZE 512
#define WF_IMAGE_MAX 4000

class waterfall {
public:
	waterfall();
	~waterfall();
	// Received sound at 8000 samples a second
	void sig_data(const double *sig, int len);
	bool Reverse() { return false; }
	bool USB() { return true; }
	double powerDensity(double f0, double bw);

private:
	void processFFT();
	double fftwindow[WF_FFTLEN];
	double circbuff[WF_FFTLEN];
	std::complex<double> wfbuf[WF_FFTLEN];
	double pwr[WF_IMAGE_MAX];
	g_fft<double> *wfft;
	int fill = 0;
};

extern waterfall *wf;
