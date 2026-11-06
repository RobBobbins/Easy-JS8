#pragma once

// Easy JS8: RTTY monitor. Decodes RTTY signals that appear in the JS8 audio (receive only),
// using fldigi's multi-channel RTTY viewer (vendor/fldigi_rtty, GPL-3.0-or-later) with fldigi's
// defaults: 170 Hz shift, 45 baud, Baudot. Every signal in the audio is decoded at once; each
// signal's text is handed on a line at a time: at the sender's line end, after a few seconds
// without characters, when the signal is lost, or at 80 characters.

#include <atomic>
#include <condition_variable>
#include <cstddef>
#include <cstdint>
#include <deque>
#include <functional>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

struct RttyDecode {
  long long utc_ms = 0;  // when the line's first character arrived (UTC, ms since epoch)
  float freq_hz = 0.0f;  // audio offset, the middle of mark and space
  std::string text;      // e.g. "CQ CQ DE W1AW W1AW K"
};

class RttyMonitor {
 public:
  using Sink = std::function<void(std::vector<RttyDecode> const&)>;
  // sink and on_worker_exit run on the worker thread.
  RttyMonitor(Sink sink, std::function<void()> on_worker_exit);
  ~RttyMonitor();

  void set_enabled(bool on);

  // Received mono audio (12000 or 8000 samples a second).
  void feed(std::int16_t const* samples, std::size_t frames, int sample_rate);

  // Called by fldigi's viewer (vendor/fldigi_rtty/port/include/Viewer.h), worker thread only.
  void on_char(int ch, int freq, char c);
  void on_clear(int ch);

 private:
  struct Task {
    std::vector<double> samples;  // 8000 samples a second
  };
  struct Line {
    std::string text;
    long long utc_ms = 0;
    int freq = 0;
    long long last_sample = 0;  // audio position of the last character
  };

  // 12000 -> 8000 samples a second: up by 2, low-pass at 4 kHz, down by 3
  struct Resampler {
    std::vector<double> history;  // recent input samples
    long long in_count = 0;       // input samples taken
    long long out_count = 0;      // output samples made
    void run(std::int16_t const* samples, std::size_t frames, int sample_rate, std::vector<double>& out);
  };

  void push(Task task);
  void worker_loop();
  void end_line(int ch);

  Sink sink_;
  std::function<void()> on_worker_exit_;
  std::atomic<bool> enabled_{false};

  Resampler live_;  // audio thread only

  // Worker thread only
  std::vector<Line> lines_;
  std::vector<RttyDecode> ready_;
  long long sample_pos_ = 0;
  std::vector<double> block_;

  std::thread thread_;
  std::mutex mutex_;
  std::condition_variable cv_;
  std::deque<Task> queue_;
  std::size_t queued_samples_ = 0;
  bool stop_ = false;
};
