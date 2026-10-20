#pragma once

#include <cstdint>
#include <functional>
#include <memory>
#include <string>
#include <string_view>
#include <utility>
#include <variant>
#include <vector>

#include "js8core/audio.hpp"
#include "js8core/clock.hpp"
#include "js8core/protocol/varicode.hpp"
#include "js8core/logger.hpp"
#include "js8core/network.hpp"
#include "js8core/rig.hpp"
#include "js8core/storage.hpp"
#include "js8core/types.hpp"
#include "js8core/protocol/varicode.hpp"

namespace js8core {

namespace events {

struct DecodeStarted {
  int submodes = 0;
};

struct SyncStart {
  int position = 0;
  int size = 0;
};

struct SyncState {
  enum class Kind { Candidate, Decoded } kind = Kind::Candidate;
  int mode = 0;
  float frequency = 0.0f;
  float dt = 0.0f;
  union {
    int candidate;
    float decoded;
  } sync {0};
};

struct Decoded {
  int utc = 0;
  int snr = 0;
  float xdt = 0.0f;
  float frequency = 0.0f;
  std::string data;
  int type = 0;
  float quality = 0.0f;
  int mode = 0;
  // Suggested total drift (ms) to center this signal's cycle.
  int drift_ms = 0;
  // Ring alignment (ms) when the audio was captured. The signal is on time
  // with capture_drift_ms - 1000 * xdt.
  std::int64_t capture_drift_ms = 0;
};

struct DecodeFinished {
  std::size_t decoded = 0;
};

struct Spectrum {
  std::vector<float> bins;
  float bin_hz = 0.0f;
  float power_db = 0.0f;
  float peak_db = 0.0f;
};

using Variant = std::variant<DecodeStarted, SyncStart, SyncState, Decoded, DecodeFinished, Spectrum>;

}  // namespace events

struct EngineConfig {
  int sample_rate_hz = 0;
  int submodes = 0;
  int tx_output_rate_hz = 48000;
  float tx_output_gain = 1.0f;
  bool tx_output_gain_boost_enabled = false;
};

struct TxMessageRequest {
  std::string text;
  std::string my_call;
  std::string my_grid;
  std::string selected_call;
  int submode = 0;
  double audio_frequency_hz = 0.0;
  double tx_delay_s = 0.0;
  bool force_identify = false;
  bool force_data = false;
};

struct TxFrameRequest {
  std::string frame;
  int bits = 0;
  int submode = 0;
  double audio_frequency_hz = 0.0;
  double tx_delay_s = 0.0;
};

struct EngineCallbacks {
  std::function<void(events::Variant const&)> on_event;
  std::function<void(std::string_view message)> on_error;
  std::function<void(LogLevel level, std::string_view message)> on_log;
  // Received audio as it enters the decode ring: mono samples, the first one's position in the
  // ring (which is aligned to the UTC minute) and the ring size. Easy JS8 FT8 monitor.
  std::function<void(std::int16_t const* samples, std::size_t frames, int ring_pos, int ring_size)> on_rx_audio;
};

struct EngineDependencies {
  AudioInput* audio_in = nullptr;
  AudioOutput* audio_out = nullptr;
  RigControl* rig = nullptr;
  Scheduler* scheduler = nullptr;
  Storage* storage = nullptr;
  Logger* logger = nullptr;
  UdpChannel* udp = nullptr;
};

class Js8Engine {
public:
  virtual ~Js8Engine() = default;

  virtual bool start() = 0;
  virtual void stop() = 0;

  virtual bool submit_capture(AudioInputBuffer const& buffer) = 0;

  virtual bool transmit_message(TxMessageRequest const& request) = 0;
  virtual bool transmit_frame(TxFrameRequest const& request) = 0;
  virtual bool start_tune(double audio_frequency_hz, int submode, double tx_delay_s) = 0;
  virtual void stop_transmit() = 0;
  virtual bool is_transmitting() const = 0;
  virtual bool is_transmitting_audio() const = 0;
  virtual int tx_milliseconds_until_audio() const = 0;
  virtual void set_tx_ready(bool ready) = 0;
  // Parts (12-character frame + bits) of the message being sent by transmit_message, in order,
  // and how many of them have been handed to the modulator. Empty / 0 for TUNE and single frames.
  virtual std::vector<std::pair<std::string, int>> tx_message_frames() { return {}; }
  virtual int tx_frames_started() { return 0; }
  virtual void set_tx_boost_enabled(bool enabled) = 0;
  virtual void set_submodes(int submodes) = 0;
  virtual void set_sync_stats(bool enabled) = 0;
  // Waterfall Controls > Timing > Start Automatic Time Drift (PC WideGraph::isAutoSyncEnabled):
  // Normal and Slow are decoded every second instead of once per cycle.
  virtual void set_auto_sync(bool /*enabled*/) {}

  // Positive = engine clock ahead of system clock; takes effect at the next captured buffer.
  virtual void set_time_drift_ms(std::int64_t drift_ms) = 0;
  virtual std::int64_t time_drift_ms() const = 0;
};

std::unique_ptr<Js8Engine> make_engine(EngineConfig const& config,
                                       EngineCallbacks callbacks,
                                       EngineDependencies deps);

}  // namespace js8core
