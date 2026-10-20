#pragma once

// Easy JS8: FT8 monitor. Decodes FT8 signals that appear in the JS8 audio (receive only),
// using ft8_lib (vendor/ft8_lib, MIT). Audio arrives from the engine's receive ring; each
// 15-second UTC slot is decoded on a worker thread once it is complete.

#include <atomic>
#include <condition_variable>
#include <cstddef>
#include <cstdint>
#include <deque>
#include <functional>
#include <mutex>
#include <string>
#include <thread>
#include <utility>
#include <vector>

struct Ft8Decode {
  long long slot_utc_ms = 0;  // start of the 15 s slot (UTC, ms since epoch)
  float freq_hz = 0.0f;       // audio offset
  int snr = 0;
  std::string text;           // e.g. "CQ W1AW FN31"
  std::string call_de;        // transmitting callsign, empty when the message has none
};

class Ft8Monitor {
 public:
  using Sink = std::function<void(std::vector<Ft8Decode> const&)>;
  // sink and on_worker_exit run on the worker thread.
  Ft8Monitor(Sink sink, std::function<void()> on_worker_exit);
  ~Ft8Monitor();

  void set_enabled(bool on);

  // Received mono audio; ring_pos is the first sample's index in the engine ring, which is
  // aligned to the UTC minute (ring_size samples = 60 s).
  void feed(std::int16_t const* samples, std::size_t frames, int ring_pos, int ring_size, int sample_rate);

  // Debug builds: decode one 15 s recording now (emulator tests without a radio).
  void decode_recording(std::int16_t const* samples, std::size_t frames, int sample_rate);

 private:
  void finish_slot(int sample_rate);
  void worker_loop();
  std::vector<Ft8Decode> decode(std::vector<float> const& slot, long long slot_utc_ms, int sample_rate);

  Sink sink_;
  std::function<void()> on_worker_exit_;
  std::atomic<bool> enabled_{false};

  // Slot being filled (audio thread only)
  std::vector<float> slot_;
  int slot_index_ = -1;
  int slot_filled_ = 0;

  struct Task {
    std::vector<float> samples;
    long long slot_utc_ms = 0;
    int sample_rate = 0;
  };
  std::thread thread_;
  std::mutex mutex_;
  std::condition_variable cv_;
  std::deque<Task> queue_;
  bool stop_ = false;
};
