#include "ft8_monitor.hpp"

#include <android/log.h>

#include <chrono>
#include <cstring>
#include <unordered_map>

#include <common/monitor.h>
#include <ft8/decode.h>
#include <ft8/message.h>

// In ft8_lib's decode.c, not in its header.
extern "C" int ft8_snr(const waterfall_t* wf, const candidate_t* candidate);

namespace {

constexpr int kSlotSeconds = 15;
constexpr int kMaxCandidates = 140;  // ft8_lib demo values
constexpr int kMinScore = 10;
constexpr int kLdpcIterations = 25;

// Callsigns heard in full, so hashed ones (<...>) can be shown later (ft8_lib hash interface).
std::mutex g_hash_mutex;
std::unordered_map<std::uint32_t, std::string> g_hash_calls;  // 22-bit hash -> callsign

bool lookup_hash(ftx_callsign_hash_type_e hash_type, std::uint32_t hash, char* callsign) {
  int shift = hash_type == FTX_CALLSIGN_HASH_22_BITS ? 0 : hash_type == FTX_CALLSIGN_HASH_12_BITS ? 10 : 12;
  std::lock_guard<std::mutex> lock(g_hash_mutex);
  for (auto const& [n22, call] : g_hash_calls) {
    if ((n22 >> shift) == hash) {
      std::strncpy(callsign, call.c_str(), 11);
      callsign[11] = '\0';
      return true;
    }
  }
  return false;
}

void save_hash(const char* callsign, std::uint32_t n22) {
  std::lock_guard<std::mutex> lock(g_hash_mutex);
  if (g_hash_calls.size() > 2000) g_hash_calls.clear();
  g_hash_calls[n22 & 0x3FFFFFu] = callsign;
}

long long now_utc_ms() {
  return std::chrono::duration_cast<std::chrono::milliseconds>(
             std::chrono::system_clock::now().time_since_epoch()).count();
}

}  // namespace

Ft8Monitor::Ft8Monitor(Sink sink, std::function<void()> on_worker_exit)
    : sink_(std::move(sink)), on_worker_exit_(std::move(on_worker_exit)) {
  thread_ = std::thread([this]() { worker_loop(); });
}

Ft8Monitor::~Ft8Monitor() {
  {
    std::lock_guard<std::mutex> lock(mutex_);
    stop_ = true;
    queue_.clear();
  }
  cv_.notify_one();
  if (thread_.joinable()) thread_.join();
}

void Ft8Monitor::set_enabled(bool on) { enabled_.store(on); }

void Ft8Monitor::feed(std::int16_t const* samples, std::size_t frames, int ring_pos, int ring_size,
                      int sample_rate) {
  if (!enabled_.load()) {
    slot_index_ = -1;
    return;
  }
  int const slot_len = kSlotSeconds * sample_rate;
  if (!samples || sample_rate <= 0 || ring_size <= 0 || ring_size % slot_len != 0) return;
  if (static_cast<int>(slot_.size()) != slot_len) {
    slot_.assign(slot_len, 0.0f);
    slot_index_ = -1;
  }
  for (std::size_t i = 0; i < frames; ++i) {
    int const pos = static_cast<int>((static_cast<long long>(ring_pos) + static_cast<long long>(i)) % ring_size);
    int const slot = pos / slot_len;
    if (slot != slot_index_) {
      finish_slot(sample_rate);
      slot_index_ = slot;
      std::fill(slot_.begin(), slot_.end(), 0.0f);
      slot_filled_ = 0;
    }
    slot_[pos % slot_len] = static_cast<float>(samples[i]) / 32768.0f;
    ++slot_filled_;
  }
}

void Ft8Monitor::decode_recording(std::int16_t const* samples, std::size_t frames, int sample_rate) {
  if (!samples || sample_rate <= 0) return;
  std::vector<float> slot(static_cast<std::size_t>(kSlotSeconds * sample_rate), 0.0f);
  for (std::size_t i = 0; i < frames && i < slot.size(); ++i) slot[i] = static_cast<float>(samples[i]) / 32768.0f;
  long long const slot_ms = kSlotSeconds * 1000LL;
  {
    std::lock_guard<std::mutex> lock(mutex_);
    queue_.push_back(Task{std::move(slot), (now_utc_ms() / slot_ms) * slot_ms, sample_rate});
  }
  cv_.notify_one();
}

void Ft8Monitor::finish_slot(int sample_rate) {
  // Only slots heard (almost) from start to end: a slot joined halfway can't be decoded
  if (slot_index_ < 0 || slot_filled_ < static_cast<int>(slot_.size() * 9 / 10)) return;
  // The slot just ended, so it started one slot length before the current slot boundary
  long long const slot_ms = kSlotSeconds * 1000LL;
  long long const slot_start = (now_utc_ms() / slot_ms) * slot_ms - slot_ms;
  {
    std::lock_guard<std::mutex> lock(mutex_);
    if (queue_.size() >= 2) queue_.pop_front();  // phone too slow: keep the newest
    queue_.push_back(Task{slot_, slot_start, sample_rate});
  }
  cv_.notify_one();
}

void Ft8Monitor::worker_loop() {
  for (;;) {
    Task task;
    {
      std::unique_lock<std::mutex> lock(mutex_);
      cv_.wait(lock, [&]() { return stop_ || !queue_.empty(); });
      if (stop_) break;
      task = std::move(queue_.front());
      queue_.pop_front();
    }
    auto decodes = decode(task.samples, task.slot_utc_ms, task.sample_rate);
    if (!decodes.empty() && sink_ && enabled_.load()) sink_(decodes);
  }
  if (on_worker_exit_) on_worker_exit_();
}

std::vector<Ft8Decode> Ft8Monitor::decode(std::vector<float> const& slot, long long slot_utc_ms, int sample_rate) {
  std::vector<Ft8Decode> out;

  monitor_config_t cfg;
  cfg.f_min = 100.0f;
  cfg.f_max = 3100.0f;  // the JS8 waterfall's width
  cfg.sample_rate = sample_rate;
  cfg.time_osr = 2;
  cfg.freq_osr = 2;
  cfg.protocol = FTX_PROTOCOL_FT8;

  monitor_t mon;
  monitor_init(&mon, &cfg);
  for (std::size_t pos = 0; pos + static_cast<std::size_t>(mon.block_size) <= slot.size(); pos += mon.block_size) {
    monitor_process(&mon, slot.data() + pos);
  }

  candidate_t candidates[kMaxCandidates];
  int const count = ft8_find_sync(&mon.wf, kMaxCandidates, candidates, kMinScore);

  ftx_callsign_hash_interface_t hash_if = {lookup_hash, save_hash};
  std::vector<ftx_message_t> seen;

  for (int i = 0; i < count; ++i) {
    candidate_t const& cand = candidates[i];
    ftx_message_t message;
    decode_status_t status;
    if (!ft8_decode(&mon.wf, &cand, kLdpcIterations, &message, &status)) continue;

    bool duplicate = false;
    for (auto const& m : seen) {
      if (m.hash == message.hash && std::memcmp(m.payload, message.payload, sizeof(message.payload)) == 0) {
        duplicate = true;
        break;
      }
    }
    if (duplicate) continue;
    seen.push_back(message);

    char text[FTX_MAX_MESSAGE_LENGTH + 16] = {0};
    if (ftx_message_decode(&message, &hash_if, text) != FTX_MESSAGE_RC_OK || text[0] == '\0') continue;

    // Transmitting station: the second callsign of standard and nonstandard-call messages
    std::string call_de;
    char to[16] = {0}, de[16] = {0}, extra[24] = {0};
    switch (ftx_message_get_type(&message)) {
      case FTX_MESSAGE_TYPE_STANDARD:
        if (ftx_message_decode_std(&message, &hash_if, to, de, extra) == FTX_MESSAGE_RC_OK) call_de = de;
        break;
      case FTX_MESSAGE_TYPE_NONSTD_CALL:
        if (ftx_message_decode_nonstd(&message, &hash_if, to, de, extra) == FTX_MESSAGE_RC_OK) call_de = de;
        break;
      default:
        break;
    }

    Ft8Decode d;
    d.slot_utc_ms = slot_utc_ms;
    d.freq_hz = (static_cast<float>(mon.min_bin) + cand.freq_offset +
                 static_cast<float>(cand.freq_sub) / static_cast<float>(mon.wf.freq_osr)) / mon.symbol_period;
    d.snr = ft8_snr(&mon.wf, &cand);
    d.text = text;
    d.call_de = call_de;
    out.push_back(std::move(d));
  }

  monitor_free(&mon);
  __android_log_print(ANDROID_LOG_INFO, "JS8Ft8", "FT8 slot %lld: %d candidates, %zu decodes",
                      slot_utc_ms, count, out.size());
  return out;
}
