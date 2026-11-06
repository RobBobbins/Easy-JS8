// Easy JS8: fldigi's waterfall arithmetic for the RTTY viewer (see include/waterfall.h).
// As fldigi 4.2.13 waterfall.cxx handle_sig_data / processFFT / powerDensity, by way of
// fldigi-android (port/waterfall.cxx, VA3OSO), with fldigi's default Blackman window and
// without the display rows.
// fldigi is GPL-3.0-or-later (../upstream/COPYING); so is this file.

#include <config.h>

#include <cmath>
#include <cstring>

#include "misc.h"
#include "waterfall.h"

waterfall *wf = 0;

waterfall::waterfall()
	: wfft(new g_fft<double>(WF_FFTLEN))
{
	memset(circbuff, 0, sizeof(circbuff));
	memset(pwr, 0, sizeof(pwr));
	BlackmanWindow(fftwindow, WF_FFTLEN);
}

waterfall::~waterfall()
{
	delete wfft;
}

void waterfall::sig_data(const double *sig, int len)
{
	// fldigi: shift the 8192-sample buffer by one block of 512, append the new block, run the FFT
	for (int n = 0; n < len; n++) {
		circbuff[WF_FFTLEN - WF_BLOCKSIZE + fill] = sig[n];
		if (++fill == WF_BLOCKSIZE) {
			processFFT();
			memmove(circbuff, circbuff + WF_BLOCKSIZE, (WF_FFTLEN - WF_BLOCKSIZE) * sizeof(double));
			fill = 0;
		}
	}
}

void waterfall::processFFT()
{
	double scale = WF_FFTLEN / 8000.0;
	double vscale = 2.0 / WF_FFTLEN;

	memset(wfbuf, 0, sizeof(wfbuf));
	double *pbuf = reinterpret_cast<double *>(wfbuf);
	for (int i = 0; i < WF_FFTLEN; i++)
		pbuf[i] = fftwindow[i] * circbuff[i] * vscale;

	wfft->RealFFT(wfbuf);

	pwr[0] = 0;
	for (int i = 1; i < WF_IMAGE_MAX; i++)
		pwr[i] = std::norm(wfbuf[(int)round(scale * i)]);
}

double waterfall::powerDensity(double f0, double bw)
{
	double pwrdensity = 0.0;
	int flower = (int)(f0 - bw / 2), fupper = (int)(f0 + bw / 2);
	if (flower < 0 || fupper > WF_IMAGE_MAX - 1)
		return 0.0;
	for (int i = flower; i <= fupper; i++)
		pwrdensity += pwr[i];
	return pwrdensity / (bw + 1);
}
