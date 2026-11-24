#pragma once

// Callsign hints for the decoder: the operator's own callsign and the
// stations heard recently, as 28-bit packed callsigns (see DecodeState).

#include <algorithm>
#include <chrono>
#include <cstddef>
#include <cstdint>
#include <deque>
#include <mutex>
#include <string>
#include <utility>

#include "js8core/decoder_state.hpp"
#include "js8core/engine.hpp"
#include "js8core/protocol/varicode.hpp"

namespace js8core {

class CallsignHints {
 public:
  using Clock = std::chrono::steady_clock;

  static constexpr int kFrameData = 4;
  static constexpr std::size_t kMaxHeard = 16;
  // Packed callsigns at or above this value stand for groups and
  // placeholders (such as @ALLCALL or a compound callsign), not stations.
  static constexpr std::uint32_t kFirstGroupCall = 37u * 36u * 10u * 27u * 27u * 27u;

  static bool valid_call(std::uint32_t packed) {
    return packed != 0 && packed < kFirstGroupCall;
  }

  // Own callsign. Empty, or one that does not fit a directed frame, turns
  // the own-callsign hints off.
  void set_own(std::string const& callsign) {
    std::uint32_t packed = 0;
    if (!callsign.empty() && !protocol::varicode::is_compound_callsign(callsign)) {
      packed = protocol::varicode::pack_callsign(callsign, nullptr);
    }
    std::lock_guard<std::mutex> lock(mutex_);
    own_ = valid_call(packed) ? packed : 0;
  }

  // Remembers the sending station of a decoded frame: the from callsign of a
  // directed frame, or the callsign of a heartbeat, CQ or compound frame.
  void note(events::Decoded const& decoded, Clock::time_point now) {
    if ((decoded.type & kFrameData) != 0 || decoded.data.size() < 12) return;
    std::uint8_t rem = 0;
    std::uint64_t const value = protocol::varicode::unpack72bits(decoded.data, &rem);
    int const flag = static_cast<int>(value >> 61);
    std::uint32_t packed = 0;
    if (flag == 3) {
      packed = static_cast<std::uint32_t>((value >> 33) & 0x0FFFFFFFu);
    } else if (flag <= 2) {
      std::uint8_t type = 0;
      std::uint16_t number = 0;
      std::uint8_t bits3 = 0;
      auto const fields =
          protocol::varicode::unpack_compound_frame(decoded.data, &type, &number, &bits3);
      if (fields.empty() || fields.front().empty() ||
          protocol::varicode::is_compound_callsign(fields.front())) {
        return;
      }
      packed = protocol::varicode::pack_callsign(fields.front(), nullptr);
    }
    if (!valid_call(packed)) return;

    std::lock_guard<std::mutex> lock(mutex_);
    std::erase_if(heard_, [&](auto const& entry) { return entry.first == packed; });
    heard_.emplace_front(packed, now);
  }

  // Copies the hints into a decode task: own callsign, then up to kMaxHeard
  // stations heard within Js8Engine::kHintHeardMinutes, newest first.
  void fill(DecodeState& task, Clock::time_point now) {
    auto const cutoff = now - std::chrono::minutes(Js8Engine::kHintHeardMinutes);
    std::lock_guard<std::mutex> lock(mutex_);
    while (!heard_.empty() && heard_.back().second < cutoff) heard_.pop_back();
    task.hint_own_call = own_;
    task.hint_heard_calls.clear();
    for (auto const& entry : heard_) {
      if (task.hint_heard_calls.size() == kMaxHeard) break;
      task.hint_heard_calls.push_back(entry.first);
    }
  }

 private:
  std::mutex mutex_;
  std::uint32_t own_ = 0;
  // Newest first.
  std::deque<std::pair<std::uint32_t, Clock::time_point>> heard_;
};

}  // namespace js8core
