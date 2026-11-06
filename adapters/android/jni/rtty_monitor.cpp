#include "rtty_monitor.hpp"

#include <android/log.h>

#include <chrono>
#include <cmath>
#include <memory>

#include "view_rtty.h"
#include "waterfall.h"

namespace {

constexpr int kRate = 8000;               // fldigi's RTTY sample rate
constexpr int kBlock = 512;               // fldigi's sound block (WF_BLOCKSIZE)
constexpr int kSilenceSeconds = 5;        // a line ends after this long without characters
constexpr std::size_t kMaxLine = 80;      // a line ends at this many characters
constexpr std::size_t kMaxQueued = 10 * kRate;  // phone too slow: drop the oldest audio
constexpr int kFirTaps = 145;

long long now_utc_ms() {
  return std::chrono::duration_cast<std::chrono::milliseconds>(
             std::chrono::system_clock::now().time_since_epoch()).count();
}

// Low-pass for the resampler: 24000 samples a second (12000 up by 2), cut at 4000 Hz,
// Blackman-windowed sinc.
std::vector<double> const& fir() {
  static std::vector<double> const taps = []() {
    std::vector<double> h(kFirTaps);
    double const fc = 4000.0 / 24000.0;
    int const mid = (kFirTaps - 1) / 2;
    double sum = 0;
    for (int k = 0; k < kFirTaps; ++k) {
      int const n = k - mid;
      double const sinc = n == 0 ? 2 * fc : std::sin(2 * M_PI * fc * n) / (M_PI * n);
      double const w = 0.42 - 0.5 * std::cos(2 * M_PI * k / (kFirTaps - 1)) +
                       0.08 * std::cos(4 * M_PI * k / (kFirTaps - 1));
      h[k] = sinc * w;
      sum += h[k];
    }
    for (auto& v : h) v /= sum;
    return h;
  }();
  return taps;
}

// fldigi's viewer and waterfall live on the worker thread; the viewer reports to this monitor.
thread_local RttyMonitor* t_monitor = nullptr;

}  // namespace

// fldigi's Viewer.h (vendor/fldigi_rtty/port/include/Viewer.h)
void viewaddchr(int ch, int freq, char c, int /* md */) {
  if (t_monitor) t_monitor->on_char(ch, freq, c);
}

void viewclearchannel(int ch) {
  if (t_monitor) t_monitor->on_clear(ch);
}

void RttyMonitor::Resampler::run(std::int16_t const* samples, std::size_t frames, int sample_rate,
                                 std::vector<double>& out) {
  if (sample_rate == kRate) {
    for (std::size_t i = 0; i < frames; ++i) out.push_back(samples[i] / 32768.0);
    return;
  }
  if (sample_rate != 12000) return;
  auto const& h = fir();
  for (std::size_t i = 0; i < frames; ++i) {
    history.push_back(samples[i] / 32768.0);
    ++in_count;
    // Output m sits at position 3m of the 24 kHz stream; input i at position 2i
    for (;;) {
      long long const p = 3 * out_count;
      if (p > 2 * (in_count - 1)) break;
      long long const base = in_count - static_cast<long long>(history.size());
      double acc = 0;
      for (int k = static_cast<int>(p % 2); k < kFirTaps; k += 2) {
        long long const idx = (p - k) / 2;
        if (p - k < 0 || idx < base) break;
        acc += h[k] * history[static_cast<std::size_t>(idx - base)];
      }
      out.push_back(2 * acc);
      ++out_count;
    }
    // Keep only the inputs the next outputs still need
    long long const base = in_count - static_cast<long long>(history.size());
    long long const keep_from = (3 * out_count - kFirTaps) / 2 - 1;
    if (keep_from - base > 4096) history.erase(history.begin(), history.begin() + (keep_from - base));
  }
}

RttyMonitor::RttyMonitor(Sink sink, std::function<void()> on_worker_exit)
    : sink_(std::move(sink)), on_worker_exit_(std::move(on_worker_exit)) {
  thread_ = std::thread([this]() { worker_loop(); });
}

RttyMonitor::~RttyMonitor() {
  {
    std::lock_guard<std::mutex> lock(mutex_);
    stop_ = true;
    queue_.clear();
  }
  cv_.notify_one();
  if (thread_.joinable()) thread_.join();
}

void RttyMonitor::set_enabled(bool on) { enabled_.store(on); }

void RttyMonitor::feed(std::int16_t const* samples, std::size_t frames, int sample_rate) {
  if (!enabled_.load() || !samples || frames == 0) return;
  Task task;
  live_.run(samples, frames, sample_rate, task.samples);
  if (!task.samples.empty()) push(std::move(task));
}

void RttyMonitor::push(Task task) {
  {
    std::lock_guard<std::mutex> lock(mutex_);
    queued_samples_ += task.samples.size();
    while (queued_samples_ > kMaxQueued && queue_.size() > 1) {
      queued_samples_ -= queue_.front().samples.size();
      queue_.pop_front();
    }
    queue_.push_back(std::move(task));
  }
  cv_.notify_one();
}

void RttyMonitor::worker_loop() {
  t_monitor = this;
  auto waterfall_ = std::make_unique<waterfall>();
  wf = waterfall_.get();
  auto viewer = std::make_unique<view_rtty>(MODE_RTTY);
  viewer->init();
  lines_.assign(MAX_CHANNELS, Line{});

  for (;;) {
    Task task;
    {
      std::unique_lock<std::mutex> lock(mutex_);
      cv_.wait(lock, [&]() { return stop_ || !queue_.empty(); });
      if (stop_) break;
      task = std::move(queue_.front());
      queue_.pop_front();
      queued_samples_ -= task.samples.size();
    }
    // fldigi's receive loop: each block goes to the waterfall, then to the viewer
    block_.insert(block_.end(), task.samples.begin(), task.samples.end());
    std::size_t pos = 0;
    for (; pos + kBlock <= block_.size(); pos += kBlock) {
      wf->sig_data(block_.data() + pos, kBlock);
      viewer->rx_process(block_.data() + pos, kBlock);
      sample_pos_ += kBlock;
      for (int ch = 0; ch < MAX_CHANNELS; ++ch) {
        if (!lines_[ch].text.empty() && sample_pos_ - lines_[ch].last_sample > kSilenceSeconds * kRate) end_line(ch);
      }
    }
    block_.erase(block_.begin(), block_.begin() + static_cast<long>(pos));
    if (!ready_.empty()) {
      if (sink_ && enabled_.load()) sink_(ready_);
      ready_.clear();
    }
  }

  viewer.reset();
  wf = nullptr;
  t_monitor = nullptr;
  if (on_worker_exit_) on_worker_exit_();
}

void RttyMonitor::on_char(int ch, int freq, char c) {
  if (ch < 0 || ch >= static_cast<int>(lines_.size())) return;
  Line& line = lines_[ch];
  if (c == 0) {  // a new signal on this channel
    end_line(ch);
    return;
  }
  if (c == '\n' || c == '\r') {
    end_line(ch);
    return;
  }
  if (static_cast<unsigned char>(c) < 32) return;  // bell and other control characters
  if (line.text.empty()) {
    if (c == ' ') return;
    line.utc_ms = now_utc_ms();
    line.freq = freq;
  }
  line.text += c;
  line.last_sample = sample_pos_;
  if (line.text.size() >= kMaxLine) end_line(ch);
}

void RttyMonitor::on_clear(int ch) { end_line(ch); }

void RttyMonitor::end_line(int ch) {
  if (ch < 0 || ch >= static_cast<int>(lines_.size())) return;
  Line& line = lines_[ch];
  while (!line.text.empty() && line.text.back() == ' ') line.text.pop_back();
  // Noise decodes as a stray letter or two: a line needs two letters or digits
  int alnum = 0;
  for (char c : line.text) {
    if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) ++alnum;
  }
  if (alnum >= 2) {
    RttyDecode d;
    d.utc_ms = line.utc_ms;
    d.freq_hz = static_cast<float>(line.freq);
    d.text = line.text;
    __android_log_print(ANDROID_LOG_INFO, "JS8Rtty", "RTTY %d Hz: %s", line.freq, line.text.c_str());
    ready_.push_back(std::move(d));
  }
  line.text.clear();
}
