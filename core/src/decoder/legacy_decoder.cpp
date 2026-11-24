#include "js8core/decoder.hpp"

#include <algorithm>
#include <array>
#include <cmath>
#include <complex>
#include <cstddef>
#include <cstdint>
#include <functional>
#include <limits>
#include <memory>
#include <numeric>
#include <optional>
#include <stdexcept>
#include <string>
#include <tuple>
#include <unordered_map>
#include <utility>
#include <vector>

#include <Eigen/Dense>
#define POCKETFFT_CACHE_SIZE 1000000
#include "pocketfft/pocketfft_hdronly.h"

#include "js8core/compat/numbers.hpp"
#include "js8core/protocol/constants.hpp"
#include "js8core/protocol/costas.hpp"

#include "decoder_tables.hpp"
#include "ldpc.hpp"

namespace js8core {

using dec_data = DecodeState;
using EventEmitter = std::function<void(events::Variant const&)>;
using namespace decoder::data;

namespace {

using namespace decoder::data;

using shape_t = std::vector<std::size_t>;
using stride_t = std::vector<std::ptrdiff_t>;

constexpr std::ptrdiff_t kComplexStride =
    static_cast<std::ptrdiff_t>(sizeof(std::complex<float>));
constexpr std::ptrdiff_t kFloatStride = static_cast<std::ptrdiff_t>(sizeof(float));
constexpr std::complex<float> kZero{0.0f, 0.0f};

// Ordered statistics decoding limits, set on synthetic Normal-speed signals
// and noise-only audio (2026-11-18): order 2, at least 9 of 21 Costas tones
// matching, and a discrepancy of at most 6.5% of the total |LLR|. The
// last-try passes (8 to 10) also search 3 and 4 flips through the
// preprocessing rules (ldpc.cpp).
constexpr int kOsdOrder = 2;
constexpr int kOsdMinSyncMatches = 9;
// Without deep decoding, the 3-tone last try (pass 10) needs one more Costas
// tone. On 2026-11-19 test sets (fading, fade-free, 60 overlapping signals
// per file) it gave 2 right and 2 wrong messages at exactly 9, while the
// 1-tone last try gave 21 right and none wrong at 9. Four events only.
constexpr int kBlockOsdMinSyncMatches = 10;
constexpr float kOsdMaxDiscrepancy = 0.065f;
// A last-try answer must also beat the runner-up candidate by at least 0.25%
// of the total |LLR| (Forney's test, approximated with the runner-up).
// Measured 2026-11-19 on six test sets (fading, fade-free, 60 overlapping
// signals per file; two seeds each): 0.2% gave about 41% fewer last-try
// results reaching the CRC check (each a 1 in 4,096 chance of a wrong
// message) for 1.6% fewer messages. With the refined search below, 0.25%
// gives more messages and fewer such results than the decoder before it at
// 0.2% (Normal 2,966 against 2,907 messages, 2,867 against 3,159 results;
// Deep 3,108 against 3,061, 4,425 against 5,068).
constexpr float kOsdMinRunnerUpGap = 0.0025f;
// A last-try answer (passes 8 to 11) is refused when a different frame was
// already reported within 3 Hz and 0.05 s of it in this period: there it is
// the leftover of that frame's subtraction read as a new frame. The same frame
// decoded again at its own spot is still accepted, because its second
// subtraction helps the decodes after it. Measured 2026-11-23 on real-style
// traffic (research note, S131 and S132): on the 48 sets of seed 2101 it
// removed the 9 wrong frames the 3-symbol subtraction window added and
// changed none of 393,902 right frames; on 24 fresh sets (seed 2102) wrong
// frames 14 to 8 (Deep 53 to 35), none of 226,539 right frames changed
// (Deep 231,257).
constexpr float kLastTryNearHz = 3.0f;
constexpr float kLastTryNearSeconds = 0.05f;
// The last try orders its search by a short, damped re-run of belief
// propagation on the same evidence instead of the evidence alone (Zhang, Yue,
// Li, Vucetic, arXiv 2306.00443, 2023): floor(girth / 4) + 1 = 3 rounds for
// this code's girth of 8. Candidates are still scored against the evidence.
constexpr int kRefineRounds = 3;
constexpr float kRefineFeedback = 0.6f;
// Fast fading (frequency spread near or above the 6.25 Hz tone spacing)
// spreads each tone's power onto its neighbours, and JS8 maps bits straight
// to tone numbers (no Gray code), so a misread neighbour costs up to 3 bits.
// The spread reading scores each tone by its own power and its neighbours',
// weighted t / (1 + t) / noise with t measured on the 21 known sync tones (the
// standard detector for a Rayleigh-faded tone of known spread; WSJT-X's Q65
// fast-fading decoder uses the same form). Bits are summed over all 8 tones.
// Pass 7 tries belief propagation with iterative demapping on it for every
// candidate that failed the 3-tone reading (2026-11-20). Pass 11 is a last
// try on it, run only when the sync tones show spread (neighbour signal at
// least kSpreadMinShare of the centre's) and kSpreadMinSync sync tones are
// read right by the spread reading. Measured 2026-11-19 and 2026-11-20, see
// the research note.
constexpr float kSpreadMinShare = 0.2f;
constexpr int kSpreadMinSync = 9;
// Deep only: pass 11's search is ordered with iterative demapping when a
// symbol has at least this many samples (tones 10 Hz apart or closer). On
// 10 Hz fading (2026-11-20) it added 9 of 587 messages at Normal speed and 2
// at Fast, for 5.6% and 1.4% more wrong-message chances there; at Turbo
// (tones 20 Hz apart) it added none.
constexpr int kSpreadDemapMinSymbolSamples = 1200;
// The plain spread reading's tone scores are multiplied by this factor
// (2026-11-22): 1.4 in Normal mode, 2.5 in Deep. Measured against known bits,
// the reading was 1.4 times too timid on fade-free audio (the factor at which
// it carries the most information; Wu, El-Khamy, Lee and Kang, IEEE WCNC
// 2013), and belief propagation with iterative demapping decodes more with a
// bolder reading still. Normal mode at 1.4: 26,402 against 26,049 messages of
// 53,286 sent over all four speeds (+1.4%), crowded-band time +2%. At 2.5:
// +2.1%, wrong messages 3 against 4, crowded-band time +11%, so 2.5 is for
// Deep only (Normal speed +2.0%, Fast, Turbo and Slow +1.0% to +1.1%, the
// same wrong messages, crowded-band time +9%). The wide spread reading is not
// scaled: at 1.4 and above the 10 Hz fading sets lost messages. See the
// research note, section 13.
constexpr double kSpreadBoldness = 1.4;
constexpr double kSpreadBoldnessDeep = 2.5;

// Wide spread reading (2026-11-20), tones 6.25 Hz apart or closer (Normal and
// Slow): the signal is cut out again kWideExtraTones wider on each side, and
// each tone is scored from the bins up to kWideReach tones away, weighted
// t / (1 + t) / noise with t measured on the sync tones and the noise from
// bins at least kWideReach + 2 tones away. It replaces the spread reading
// when its own neighbour share is at least kWideMinShare. On 10 Hz fading it
// added 33% to 38% more messages, with fewer wrong-message chances; at Fast
// (tones 10 Hz apart) it lost messages. See the research note.
//
// The reach is the same in Hz at Normal and Slow, 12.5 Hz each side: 2 tones
// at Normal, 4 at Slow (tones 3.125 Hz apart). WSJT-X's Q65 likewise sizes its
// fast-fading window from spread times symbol time. With reach 2 at Slow, 10 Hz
// fading decoded nothing; reach 4 decoded 51 of 800 on fresh sets with no loss
// elsewhere (2026-11-19). At Normal speed, reach 4 was worse than 2.
constexpr int kWideMinSymbolSamples = 1920;
constexpr int kWideLongSymbolSamples = 3840;
constexpr double kWideMinShare = 0.2;
template <typename Mode>
inline constexpr int kWideReach = Mode::nsps >= kWideLongSymbolSamples ? 4 : 2;
template <typename Mode>
inline constexpr int kWideExtraTones = kWideReach<Mode> + 2;
template <typename Mode>
inline constexpr int kWideLow = -1 - kWideExtraTones<Mode>;          // lowest bin, tones
template <typename Mode>
inline constexpr int kWideHigh = kToneRows + kWideExtraTones<Mode>;  // highest bin, tones
template <typename Mode>
inline constexpr int kWideBins = kWideHigh<Mode> - kWideLow<Mode> + 1;

// Fit smoothing and weak-sync rescue (2026-11-20), Normal and Slow speeds
// (kWideMinSymbolSamples). Under fast fading a sync tone's power spreads onto
// its neighbours, so the time and frequency fit, which scores each sync tone
// in one bin, goes wrong, and the 6-sync-tone check drops signals whose fit
// is right. Smoothing: the fit's score is summed over the whole sync pattern
// shifted by -2 to +2 tones, weighted 1, 4, 6, 4, 1 (2 passes of WSJT-X's
// 1-2-1 filter, smo121, as its Q65 decoder smooths before sync). Normal mode
// redoes the fit smoothed when the sync score 1 tone either side is at least
// kFitSmoothShare of the score at the fitted point. Deep instead decodes each
// candidate with the plain fit first and, if that fails, once more with the
// smoothed fit, as WSJT-X's Q65 decoder retries fits and keeps the first
// decode that passes the CRC. Rescue: a candidate with 6 or fewer sync tones
// read right goes on if the spread reading reads at least kWeakSyncMinSpread
// right. Measured on 10 Hz fading: Normal mode +11% at Normal speed and
// 49 -> 68 of 800 at Slow, with no losses; Deep also +5.9% on ordinary sets,
// at 1.9 times the wrong-message chances and 1.6 times the crowded-band time.
// See the research note.
constexpr int kFitSmoothReach = 2;
constexpr std::array<double, 2 * kFitSmoothReach + 1> kFitSmoothWeights{
    0.0625, 0.25, 0.375, 0.25, 0.0625};
constexpr double kFitSmoothShare = 0.5;
constexpr int kWeakSyncMinSpread = 7;

// Frame read length (2026-11-22). The channel holds ds_fft_out samples, but
// the decoder read only payload_samples (79 symbols) counted from the start of
// the period. A frame starts start_seconds plus its time offset into the
// period, so its last symbols read as silence: at Normal speed and a 0.3 s
// offset, 5 of the 21 sync tones; above about 0.62 s, data tones too. WSJT-X
// FT8 (ft8b, sync8d) and JS8Call-improved v3.0.3 read to 2,812 samples (its
// file-level NP2, used at every speed), capped here at the channel length.
// Normal, Fast and Turbo only. Measured on the test sets: messages +4.5%
// (Normal speed), +5.1% (Fast), +17.2% (Turbo), real-style traffic +1.1%;
// wrong-message chances 4% to 43% fewer. Slow gained 0.7% for 15% to 20% more
// wrong-message chances, so Slow, and mode I (not tested), keep the old
// length. See the research note, section 13.
constexpr int kFrameReadSamples = 2812;
template <typename Mode>
inline constexpr int kReadSamples =
    (Mode::event_mode == 0 || Mode::event_mode == 1 || Mode::event_mode == 2)
        ? (kFrameReadSamples < Mode::ds_fft_out ? kFrameReadSamples : Mode::ds_fft_out)
        : Mode::payload_samples;

// Callsign hints (a priori decoding): limits on the hinted retry of a weak
// signal. Same Costas limit as ordered statistics decoding. Discrepancy limit
// set on 528 synthetic Normal-speed files of directed frames plus 96
// noise-only files, 16 heard stations (2026-11-18): all 158 right CRC-valid
// hint results scored at most 0.2, the one wrong result 0.246. At 0.12, 148
// of the 158 right results pass and no wrong one.
constexpr float kHintMaxDiscrepancy = 0.12f;
constexpr std::size_t kHintMaxHeard = 16;

// Largest number of data symbols per block for noncoherent block detection
// (8^3 = 512 tone sequences). Blocks of 2 and 3 are read.
constexpr int kBlockSymbols = 3;

// Kahan-compensated accumulator. The Nuttall window is very sensitive to
// summation order; plain float accumulation shifts the coefficients enough to
// matter during correlation.
class Accumulator {
 public:
  explicit Accumulator(float initial = 0.0f)
      : value_(initial), correction_(0.0f) {}

  Accumulator& operator=(float value) {
    value_ = value;
    correction_ = 0.0f;
    return *this;
  }

  Accumulator& operator+=(float term) {
    float const adjusted = term - correction_;
    float const next = value_ + adjusted;
    correction_ = (next - value_) - adjusted;
    value_ = next;
    return *this;
  }

  operator float() const { return value_; }

 private:
  float value_;
  float correction_;
};

// One synchronization candidate: absolute frequency, time offset in seconds,
// and normalized correlation score.
struct Candidate {
  float frequency;
  float offset;
  float score;
};

// A successfully decoded frame, keyed by type and text so that duplicate
// detections can be merged.
struct Frame {
  int type;
  std::string text;

  bool operator==(Frame const&) const noexcept = default;

  struct Hash {
    std::size_t operator()(Frame const& frame) const noexcept {
      std::size_t const a = std::hash<int>{}(frame.type);
      std::size_t const b = std::hash<std::string>{}(frame.text);
      return a ^ (b + 0x9e3779b9u + (a << 6) + (a >> 2));
    }
  };

  using Map = std::unordered_map<Frame, int, Hash>;
};

// Per-submode decoder. All buffers are sized from the ModeProfile so that one
// template instantiation handles each JS8 speed level.
template <typename Mode>
class ChannelDecoder {
 public:
  ChannelDecoder() { initialize(); }

  std::size_t operator()(dec_data const& data, int position, int size,
                         EventEmitter emit);

 private:
  using Spectrum = std::array<std::complex<float>, Mode::fft_span / 2 + 1>;
  using Baseband = std::array<std::complex<float>, Mode::ds_fft_span / 2 + 1>;

  // --- Initialization -------------------------------------------------------

  void initialize();
  void build_window();
  void build_sync_waveforms();
  void build_subtraction_filter();

  // --- Signal conditioning --------------------------------------------------

  void prepare_baseband();
  void extract_channel(float frequency);
  // Power in bins kWideLow to kWideHigh (tones) for every channel symbol,
  // from a cut-out kWideExtraTones wider each side than extract_channel's,
  // with the same time and frequency fine-tuning. Index symbol * kWideBins +
  // bin - kWideLow.
  void extract_wide_energy(float frequency, float delta, int best_time,
                           std::vector<float>& energy) const;
  void compute_baseline(int first, int last);
  float evaluate_baseline(float x) const;

  // --- Synchronization and demodulation ------------------------------------

  std::vector<Candidate> find_candidates(int low_hz, int high_hz);
  float alignment_score(int offset, float frequency_delta);
  // Set for Deep's second attempt at a candidate (see kFitSmoothShare).
  bool smooth_fit_ = false;
  // The first attempt's fit, so Deep's second attempt can skip a fit that
  // lands in the same place.
  int first_fit_time_ = 0;
  float first_fit_delta_ = 0.0f;
  // Frames reported so far in this period, with their position, for the
  // last-try position check (see kLastTryNearHz).
  struct Reported {
    float frequency;
    float offset;
    std::string text;
  };
  std::vector<Reported> reported_;
  // True when a frame with different text was reported within
  // kLastTryNearHz and kLastTryNearSeconds, and no frame with this text was.
  bool different_frame_near(float frequency, float offset, std::string const& text) const {
    bool near = false;
    for (auto const& frame : reported_) {
      if (std::abs(frequency - frame.frequency) > kLastTryNearHz ||
          std::abs(offset - frame.offset) > kLastTryNearSeconds) {
        continue;
      }
      if (frame.text == text) return false;
      near = true;
    }
    return near;
  }
  std::optional<Frame> attempt_decode(bool sync_stats, bool subtract,
                                      float& frequency, float& offset,
                                      int& hard_errors, float& snr,
                                      int& rescue_budget, EventEmitter emit);

  // --- Signal reconstruction -----------------------------------------------

  std::vector<std::complex<float>> make_reference(std::array<int, kChannelSymbols> const& tones,
                                                  float frequency);
  void subtract_reference(std::vector<std::complex<float>> const& reference,
                          float offset);

  // --- Callsign hints -------------------------------------------------------

  void build_hints(dec_data const& data);

  // Payload bits each hint fixes: 0 or 1, or -1 where the hint says nothing.
  std::vector<std::array<std::int8_t, kPayloadBits>> hints_;

  // Deep decoding (DecodeParams::deep) for the current decode.
  bool deep_ = false;

  // --- Buffers --------------------------------------------------------------

  std::array<float, Mode::fft_span> window_{};
  std::array<std::array<std::array<std::complex<float>, Mode::downsample_sps>, 7>, 3>
      sync_waveforms_{};
  alignas(64) std::array<std::complex<float>, Mode::downsample_sps> symbol_fft_{};
  alignas(64) std::array<std::complex<float>, Mode::nmax> subtract_filter_{};
  alignas(64) std::array<std::complex<float>, Mode::nmax> subtract_work_{};
  alignas(64) std::array<std::complex<float>, Mode::ds_fft_span / 2 + 1> baseband_spectrum_{};
  alignas(64) std::array<std::complex<float>, Mode::fft_span / 2 + 1> spectrum_{};
  alignas(64) std::array<std::complex<float>, kComplexCapacity> channel_{};
  std::array<float, Mode::nmax> samples_{};
  std::vector<float> baseband_input_;

  std::array<std::array<float, Mode::spectra>, Mode::nsps> spectra_{};
  std::array<float, Mode::nsps> average_spectrum_{};
  std::array<float, Mode::fft_span> windowed_{};

  // Baseline fit.
  using Points = Eigen::Matrix<double, 6, 2>;
  using Vandermonde = Eigen::Matrix<double, 6, 6>;
  using Coefficients = Eigen::Vector<double, 6>;

  Points points_{};
  Vandermonde vandermonde_{};
  Coefficients coefficients_{};

  inline static const auto& kCostas = protocol::costas(Mode::costas_type);

  inline static const auto kTaper = [] {
    std::array<std::array<float, Mode::taper_span + 1>, 2> taper{};
    constexpr float pi = js8core::compat::numbers::pi_v<float>;
    for (int i = 0; i <= Mode::taper_span; ++i) {
      float const c = std::cos(i * pi / Mode::taper_span);
      taper[0][static_cast<std::size_t>(i)] = 0.5f * (1.0f - c);  // head
      taper[1][static_cast<std::size_t>(i)] = 0.5f * (1.0f + c);  // tail
    }
    return taper;
  }();

  inline static const auto kBaselineNodes = [] {
    std::array<double, 6> nodes{};
    constexpr double slice = js8core::compat::numbers::pi / (2.0 * 6);
    for (std::size_t i = 0; i < nodes.size(); ++i) {
      nodes[i] = 0.5 * (1.0 - std::cos(slice * (2.0 * i + 1.0)));
    }
    return nodes;
  }();
};

// ---------------------------------------------------------------------------
// Initialization
// ---------------------------------------------------------------------------

template <typename Mode>
void ChannelDecoder<Mode>::initialize() {
  baseband_input_.assign(Mode::ds_fft_span, 0.0f);
  build_window();
  build_sync_waveforms();
  build_subtraction_filter();
}

template <typename Mode>
void ChannelDecoder<Mode>::build_window() {
  constexpr float a0 = 0.3635819f;
  constexpr float a1 = -0.4891775f;
  constexpr float a2 = 0.1365995f;
  constexpr float a3 = -0.0106411f;
  float const pi = 4.0f * std::atan(1.0f);

  float total = 0.0f;
  for (std::size_t i = 0; i < window_.size(); ++i) {
    Accumulator value(a0);
    value += a1 * std::cos(2.0f * pi * i / window_.size());
    value += a2 * std::cos(4.0f * pi * i / window_.size());
    value += a3 * std::cos(6.0f * pi * i / window_.size());
    window_[i] = value;
    total += value;
  }

  float const scale = static_cast<float>(window_.size()) / (300.0f * total);
  for (float& value : window_) value *= scale;
}

template <typename Mode>
void ChannelDecoder<Mode>::build_sync_waveforms() {
  for (int block = 0; block < 3; ++block) {
    for (int column = 0; column < 7; ++column) {
      float const step =
          kTau * static_cast<float>(kCostas[block][column]) / Mode::downsample_sps;
      float phase = 0.0f;
      for (int i = 0; i < Mode::downsample_sps; ++i) {
        sync_waveforms_[static_cast<std::size_t>(block)]
                       [static_cast<std::size_t>(column)]
                       [static_cast<std::size_t>(i)] = std::polar(1.0f, phase);
        phase = std::fmod(phase + step, kTau);
      }
    }
  }
}

template <typename Mode>
void ChannelDecoder<Mode>::build_subtraction_filter() {
  constexpr int radius = Mode::filter_length / 2;
  const float pi = 4.0f * std::atan(1.0f);

  // A periodic FFT filter is indexed by signed time delay modulo N. Lags
  // below zero belong at the *end* of the N-point array, not after the
  // positive taps. Otherwise the filter has a spurious ~700-sample delay,
  // which ruins phase-aware subtraction of overlapping signals.
  subtract_filter_.fill(kZero);
  float tap_sum = 0.0f;
  for (int lag = -radius; lag <= radius; ++lag) {
    const float amplitude = std::cos(pi * lag / Mode::filter_length);
    const float weight = amplitude * amplitude;
    tap_sum += weight;
    const auto sample = static_cast<std::size_t>(
        lag < 0 ? Mode::nmax + lag : lag);
    subtract_filter_[sample] = {weight, 0.0f};
  }
  for (auto& tap : subtract_filter_) {
    tap *= (1.0f / tap_sum);
  }

  // Pair a pre-normalized frequency response with an unscaled inverse FFT.
  pocketfft::c2c<float>(shape_t{static_cast<std::size_t>(Mode::nmax)},
                        stride_t{kComplexStride}, stride_t{kComplexStride},
                        shape_t{0}, true, subtract_filter_.data(),
                        subtract_filter_.data(), 1.0f / Mode::nmax, 1);
}

// ---------------------------------------------------------------------------
// Signal conditioning
// ---------------------------------------------------------------------------

template <typename Mode>
void ChannelDecoder<Mode>::prepare_baseband() {
  std::fill(baseband_input_.begin(), baseband_input_.end(), 0.0f);
  std::copy(samples_.begin(), samples_.end(), baseband_input_.begin());

  pocketfft::r2c<float>(shape_t{static_cast<std::size_t>(Mode::ds_fft_span)},
                        stride_t{kFloatStride}, stride_t{kComplexStride},
                        shape_t{0}, true, baseband_input_.data(),
                        baseband_spectrum_.data(), 1.0f, 1);
}

template <typename Mode>
void ChannelDecoder<Mode>::extract_channel(float frequency) {
  constexpr float resolution = 12000.0f / Mode::ds_fft_span;
  constexpr float baud = 12000.0f / Mode::nsps;

  float const top_hz = frequency + 8.5f * baud;
  float const bottom_hz = frequency - 1.5f * baud;
  int const center = static_cast<int>(std::round(frequency / resolution));
  int const top = std::min(static_cast<int>(std::round(top_hz / resolution)),
                           Mode::ds_fft_span / 2);
  int const bottom = std::max(0, static_cast<int>(std::round(bottom_hz / resolution)));

  std::size_t const taper_count = Mode::taper_span + 1;
  std::size_t const width = static_cast<std::size_t>(top - bottom + 1);

  std::fill_n(channel_.begin(), Mode::ds_fft_out, kZero);
  std::copy(baseband_spectrum_.begin() + bottom,
            baseband_spectrum_.begin() + bottom + static_cast<std::ptrdiff_t>(width),
            channel_.begin());

  auto head = channel_.begin();
  auto tail = channel_.begin() + static_cast<std::ptrdiff_t>(width);

  for (std::size_t i = 0; i < taper_count; ++i) {
    head[i] *= kTaper[0][i];
    tail[-static_cast<std::ptrdiff_t>(taper_count) + static_cast<std::ptrdiff_t>(i)] *=
        kTaper[1][i];
  }

  int const rotation = center - bottom;
  std::rotate(channel_.begin(), channel_.begin() + rotation,
              channel_.begin() + Mode::ds_fft_out);

  // The inverse DFT must be *unscaled*, matching FFTW_BACKWARD in the
  // desktop reference. The sample-amplitude normalization immediately below
  // already accounts for the input/output FFT lengths. Applying pocketfft's
  // optional 1/N factor here as well attenuates the channel twice, collapsing
  // every decoded SNR to its -60 dB reporting floor.
  pocketfft::c2c<float>(shape_t{static_cast<std::size_t>(Mode::ds_fft_out)},
                        stride_t{kComplexStride}, stride_t{kComplexStride},
                        shape_t{0}, false, channel_.data(), channel_.data(),
                        1.0f, 1);

  float const scale =
      1.0f / std::sqrt(static_cast<float>(Mode::ds_fft_span) * Mode::ds_fft_out);
  for (int i = 0; i < Mode::ds_fft_out; ++i) {
    channel_[static_cast<std::size_t>(i)] *= scale;
  }
}

template <typename Mode>
float ChannelDecoder<Mode>::evaluate_baseline(float x) const {
  double value = 0.0;
  double power = 1.0;
  for (int i = 0; i < 3; ++i) {
    value += (coefficients_(2 * i) + coefficients_(2 * i + 1) * x) * power;
    power *= x * x;
  }
  return static_cast<float>(value);
}

template <typename Mode>
void ChannelDecoder<Mode>::compute_baseline(int first, int last) {
  constexpr std::size_t bottom =
      static_cast<std::size_t>(kBaselineMinHz / Mode::bin_hz + 0.5f);
  constexpr std::size_t top =
      static_cast<std::size_t>(kBaselineMaxHz / Mode::bin_hz + 0.5f);
  constexpr std::size_t span = top - bottom + 1;
  constexpr std::size_t arm = span / (2 * kBaselineNodes.size());

  auto data = average_spectrum_.begin() + static_cast<std::ptrdiff_t>(bottom);
  auto end = data + static_cast<std::ptrdiff_t>(span);

  std::transform(data, end, data, [](float value) {
    return 10.0f * std::log10(std::max(value, 1.0e-30f));
  });

  for (std::size_t i = 0; i < kBaselineNodes.size(); ++i) {
    double const node = static_cast<double>(span) * kBaselineNodes[i];
    auto base = data + static_cast<std::ptrdiff_t>(std::round(node));
    auto from = std::clamp(base - static_cast<std::ptrdiff_t>(arm), data, end);
    auto to = std::clamp(base + static_cast<std::ptrdiff_t>(arm), data, end);
    std::vector<float> window(from, to);
    std::size_t const pick = window.size() * kBaselineSamplePercent / 100;
    std::nth_element(window.begin(),
                     window.begin() + static_cast<std::ptrdiff_t>(pick),
                     window.end());
    points_.row(static_cast<Eigen::Index>(i)) << node,
        window[static_cast<std::size_t>(pick)];
  }

  Eigen::VectorXd x = points_.col(0);
  Eigen::VectorXd y = points_.col(1);

  vandermonde_.col(0).setOnes();
  for (Eigen::Index i = 1; i < vandermonde_.cols(); ++i) {
    vandermonde_.col(i) = vandermonde_.col(i - 1).cwiseProduct(x);
  }
  coefficients_ = vandermonde_.colPivHouseholderQr().solve(y);

  float const last_index = static_cast<float>(span - 1);
  average_spectrum_.fill(0.0f);
  for (int i = first; i <= last; ++i) {
    float const mapped = (i - first) * last_index / static_cast<float>(last - first);
    average_spectrum_[static_cast<std::size_t>(i)] =
        evaluate_baseline(mapped) + 0.65f;
  }
}

// ---------------------------------------------------------------------------
// Synchronization and demodulation
// ---------------------------------------------------------------------------

template <typename Mode>
float ChannelDecoder<Mode>::alignment_score(int offset, float frequency_delta) {
  constexpr float down_rate = 12000.0f / Mode::downsample_factor;
  constexpr float phase_step = kTau / down_rate;

  std::array<std::complex<float>, Mode::downsample_sps> rotation;
  if (frequency_delta != 0.0f) {
    float const step = phase_step * frequency_delta;
    float phase = 0.0f;
    for (int i = 0; i < Mode::downsample_sps; ++i) {
      rotation[static_cast<std::size_t>(i)] = std::polar(1.0f, phase);
      phase = std::fmod(phase + step, kTau);
      if (phase < 0.0f) phase += kTau;
    }
  } else {
    rotation.fill(std::complex<float>{1.0f, 0.0f});
  }

  float score = 0.0f;
  for (int block = 0; block < 3; ++block) {
    for (int column = 0; column < 7; ++column) {
      int const start =
          36 * block * Mode::downsample_sps + offset + column * Mode::downsample_sps;
      if (start < 0 || start + Mode::downsample_sps > kReadSamples<Mode>) {
        continue;
      }

      std::complex<float> sum{0.0f, 0.0f};
      for (int i = 0; i < Mode::downsample_sps; ++i) {
        auto const& wave =
            sync_waveforms_[static_cast<std::size_t>(block)]
                           [static_cast<std::size_t>(column)]
                           [static_cast<std::size_t>(i)];
        sum += channel_[static_cast<std::size_t>(start + i)] *
               std::conj(rotation[static_cast<std::size_t>(i)] * wave);
      }
      score += std::norm(sum);
    }
  }
  return score;
}

template <typename Mode>
std::vector<Candidate> ChannelDecoder<Mode>::find_candidates(int low_hz,
                                                             int high_hz) {
  // Invalid or inverted bands must not reach the polynomial baseline fit or
  // be turned into negative/huge vector allocation sizes.
  if (low_hz >= high_hz || high_hz <= 0 || low_hz > 4910) return {};
  average_spectrum_.fill(0.0f);

  for (int j = 0; j < Mode::spectra; ++j) {
    int const start = j * Mode::time_step;
    int const stop = start + Mode::fft_span;
    if (stop > Mode::nmax) break;

    for (int i = 0; i < Mode::fft_span; ++i) {
      windowed_[static_cast<std::size_t>(i)] =
          samples_[static_cast<std::size_t>(start + i)] * window_[static_cast<std::size_t>(i)];
    }

    pocketfft::r2c<float>(shape_t{static_cast<std::size_t>(Mode::fft_span)},
                          stride_t{kFloatStride}, stride_t{kComplexStride},
                          shape_t{0}, true, windowed_.data(), spectrum_.data(),
                          1.0f, 1);

    for (int i = 0; i < Mode::nsps; ++i) {
      float const power = std::norm(spectrum_[static_cast<std::size_t>(i)]);
      spectra_[static_cast<std::size_t>(i)][static_cast<std::size_t>(j)] = power;
      average_spectrum_[static_cast<std::size_t>(i)] += power;
    }
  }

  int const requested_width = high_hz - low_hz;
  if (low_hz < 100) {
    low_hz = 100;
    if (requested_width < 100) high_hz = low_hz + requested_width;
  }
  if (high_hz > 4910) {
    high_hz = 4910;
    if (requested_width < 100) low_hz = high_hz - requested_width;
  }

  int const first = std::max(0, static_cast<int>(std::round(low_hz / Mode::bin_hz)));
  int const last = static_cast<int>(std::round(high_hz / Mode::bin_hz));
  // Even a positive-width Hz interval can collapse to a single FFT bin
  // after rounding. The baseline fit divides by (last - first).
  if (first >= last || last + kOversample * (kToneRows - 1) >= Mode::nsps)
    return {};

  compute_baseline(first, last);

  std::vector<Candidate> entries;
  entries.reserve(static_cast<std::size_t>(last - first + 1));

  for (int bin = first; bin <= last; ++bin) {
    float best = -std::numeric_limits<float>::infinity();
    int best_offset = -Mode::search_radius;

    for (int delta = -Mode::search_radius; delta <= Mode::search_radius; ++delta) {
      std::array<std::array<float, 3>, 2> sums{};

      for (int block = 0; block < 3; ++block) {
        for (int column = 0; column < 7; ++column) {
          int const time = delta + Mode::start_step + kStepsPerSymbol * column +
                           block * 36 * kStepsPerSymbol;
          if (time < 0 || time >= Mode::spectra) continue;

          sums[0][static_cast<std::size_t>(block)] +=
              spectra_[static_cast<std::size_t>(bin + kOversample * kCostas[block][column])]
                      [static_cast<std::size_t>(time)];

          for (int tone = 0; tone < 7; ++tone) {
            sums[1][static_cast<std::size_t>(block)] +=
                spectra_[static_cast<std::size_t>(bin + kOversample * tone)]
                        [static_cast<std::size_t>(time)];
          }
        }
      }

      auto const ratio = [&sums](int from, int to) {
        float signal = 0.0f;
        float total = 0.0f;
        for (int i = from; i <= to; ++i) {
          signal += sums[0][static_cast<std::size_t>(i)];
          total += sums[1][static_cast<std::size_t>(i)];
        }
        return signal / ((total - signal) / 6.0f);
      };

      float const score = std::max({ratio(0, 2), ratio(0, 1), ratio(1, 2)});
      if (std::isfinite(score) && score > best) {
        best = score;
        best_offset = delta;
      }
    }

    // The spectra fill the whole 2-symbol FFT span with audio under a window
    // that peaks one symbol in, so a tone scores best when the spectrum
    // starts 2 steps (half a symbol) before it. Counting from the true step
    // (start_step plus those 2) keeps the candidate time on the signal's
    // start instead of 1.5 steps early.
    entries.push_back(Candidate{
        Mode::bin_hz * bin,
        (best_offset + Mode::start_step + 2) * Mode::step_seconds - Mode::start_seconds,
        best});
  }

  if (entries.empty()) return {};

  std::vector<float> scores;
  scores.reserve(entries.size());
  for (auto const& entry : entries) scores.push_back(entry.score);

  std::size_t const rank = scores.size() * 4 / 10;
  std::nth_element(scores.begin(), scores.begin() + static_cast<std::ptrdiff_t>(rank),
                   scores.end());
  float const reference = scores[rank];
  if (!std::isfinite(reference) || reference <= 1.0e-12f) return {};

  for (auto& entry : entries) entry.score /= reference;

  std::vector<std::size_t> order(entries.size());
  std::iota(order.begin(), order.end(), 0);
  std::stable_sort(order.begin(), order.end(), [&entries](std::size_t a, std::size_t b) {
    return entries[a].score > entries[b].score;
  });

  std::vector<char> removed(entries.size(), 0);
  std::vector<Candidate> selected;

  for (std::size_t index : order) {
    if (removed[index]) continue;

    Candidate const& candidate = entries[index];
    if (!std::isfinite(candidate.score) || candidate.score < kMinSync) break;

    selected.push_back(candidate);
    if (selected.size() >= kMaxCandidates) break;

    for (std::size_t other = 0; other < entries.size(); ++other) {
      if (!removed[other] &&
          std::abs(entries[other].frequency - candidate.frequency) <= Mode::az) {
        removed[other] = 1;
      }
    }
  }

  return selected;
}

template <typename Mode>
void ChannelDecoder<Mode>::extract_wide_energy(float frequency, float delta,
                                               int best_time,
                                               std::vector<float>& energy) const {
  constexpr float resolution = 12000.0f / Mode::ds_fft_span;
  constexpr float baud = 12000.0f / Mode::nsps;
  constexpr float down_rate = 12000.0f / Mode::downsample_factor;

  // Cut-out and tapers as in extract_channel, wider on both sides.
  int const center = static_cast<int>(std::round(frequency / resolution));
  int const top = std::min(
      static_cast<int>(std::round((frequency + (8.5f + kWideExtraTones<Mode>) * baud) / resolution)),
      Mode::ds_fft_span / 2);
  int const bottom = std::max(
      0, static_cast<int>(std::round((frequency - (1.5f + kWideExtraTones<Mode>) * baud) / resolution)));
  std::size_t const taper_count = Mode::taper_span + 1;
  std::size_t const width = static_cast<std::size_t>(top - bottom + 1);

  std::vector<std::complex<float>> wide(static_cast<std::size_t>(Mode::ds_fft_out), kZero);
  std::copy(baseband_spectrum_.begin() + bottom,
            baseband_spectrum_.begin() + bottom + static_cast<std::ptrdiff_t>(width),
            wide.begin());
  for (std::size_t i = 0; i < taper_count; ++i) {
    wide[i] *= kTaper[0][i];
    wide[width - taper_count + i] *= kTaper[1][i];
  }
  std::rotate(wide.begin(), wide.begin() + (center - bottom),
              wide.begin() + Mode::ds_fft_out);
  pocketfft::c2c<float>(shape_t{static_cast<std::size_t>(Mode::ds_fft_out)},
                        stride_t{kComplexStride}, stride_t{kComplexStride},
                        shape_t{0}, false, wide.data(), wide.data(), 1.0f, 1);

  // Same scale as extract_channel, then the same frequency correction as
  // attempt_decode applies to channel_.
  float const scale =
      1.0f / std::sqrt(static_cast<float>(Mode::ds_fft_span) * Mode::ds_fft_out);
  std::complex<float> const rotation_step = std::polar(1.0f, -delta * (kTau / down_rate));
  std::complex<float> rotation{1.0f, 0.0f};
  for (int i = 0; i < Mode::ds_fft_out; ++i) {
    wide[static_cast<std::size_t>(i)] *= scale;
    if (i < kReadSamples<Mode>) {
      rotation *= rotation_step;
      wide[static_cast<std::size_t>(i)] *= rotation;
    }
  }

  energy.assign(static_cast<std::size_t>(kChannelSymbols * kWideBins<Mode>), 0.0f);
  std::vector<std::complex<float>> symbol(static_cast<std::size_t>(Mode::downsample_sps));
  for (int index = 0; index < kChannelSymbols; ++index) {
    int const start = best_time + index * Mode::downsample_sps;
    std::fill(symbol.begin(), symbol.end(), kZero);
    if (start >= 0 && start + Mode::downsample_sps <= kReadSamples<Mode>) {
      std::copy(wide.begin() + start, wide.begin() + start + Mode::downsample_sps,
                symbol.begin());
    }
    pocketfft::c2c<float>(shape_t{static_cast<std::size_t>(Mode::downsample_sps)},
                          stride_t{kComplexStride}, stride_t{kComplexStride},
                          shape_t{0}, true, symbol.data(), symbol.data(), 1.0f, 1);
    for (int bin = kWideLow<Mode>; bin <= kWideHigh<Mode>; ++bin) {
      energy[static_cast<std::size_t>(index * kWideBins<Mode> + bin - kWideLow<Mode>)] = std::norm(
          symbol[static_cast<std::size_t>((bin + Mode::downsample_sps) % Mode::downsample_sps)] /
          1000.0f);
    }
  }
}

template <typename Mode>
std::optional<Frame> ChannelDecoder<Mode>::attempt_decode(
    bool sync_stats, bool subtract, float& frequency, float& offset,
    int& hard_errors, float& snr, int& rescue_budget, EventEmitter emit) {
  constexpr float frequency_resolution = 12000.0f / Mode::fft_span;
  constexpr float down_rate = 12000.0f / Mode::downsample_factor;
  constexpr float down_period = 1.0f / down_rate;

  int const bin = static_cast<int>(std::round(frequency / frequency_resolution));
  float const linear_base =
      std::pow(10.0f, 0.1f * (average_spectrum_[static_cast<std::size_t>(bin)] -
                              Mode::baseline_offset));

  extract_channel(frequency);

  int const start_origin =
      static_cast<int>(std::round((offset + Mode::start_seconds) * down_rate));
  int origin = start_origin;
  float best_score = 0.0f;
  int best_time = 0;
  float refined_offset = 0.0f;
  float best_delta = 0.0f;
  constexpr float tone_hz = 12000.0f / Mode::nsps;

  // Sync score for the fit, optionally smoothed across frequency (see
  // kFitSmoothShare).
  auto fit_score = [&](int at, float delta, bool smooth) -> float {
    if (!smooth) return alignment_score(at, delta);
    double total = 0.0;
    for (int k = -kFitSmoothReach; k <= kFitSmoothReach; ++k) {
      total += kFitSmoothWeights[static_cast<std::size_t>(k + kFitSmoothReach)] *
               alignment_score(at, delta + k * tone_hz);
    }
    return static_cast<float>(total);
  };

  // Time, then frequency, then time again at the corrected frequency over
  // half the first window (as WSJT-X does in ft8b).
  auto fit = [&](bool smooth) {
    origin = start_origin;
    best_score = 0.0f;
    best_time = 0;
    for (int candidate = origin - Mode::quarter_symbol;
         candidate <= origin + Mode::quarter_symbol; ++candidate) {
      float const score = fit_score(candidate, 0.0f, smooth);
      if (score > best_score) {
        best_score = score;
        best_time = candidate;
      }
    }

    refined_offset = best_time * down_period;
    origin = static_cast<int>(std::round(refined_offset * down_rate));
    best_score = 0.0f;
    best_delta = 0.0f;

    for (int step = -kFreqSearchSteps; step <= kFreqSearchSteps; ++step) {
      float const delta = step * 0.5f;
      float const score = fit_score(origin, delta, smooth);
      if (score > best_score) {
        best_score = score;
        best_delta = delta;
      }
    }

    float best_retime = 0.0f;
    int retime = origin;
    for (int candidate = origin - Mode::quarter_symbol / 2;
         candidate <= origin + Mode::quarter_symbol / 2; ++candidate) {
      float const score = fit_score(candidate, best_delta, smooth);
      if (score > best_retime) {
        best_retime = score;
        retime = candidate;
      }
    }
    best_time = retime;
    refined_offset = best_time * down_period;
    origin = static_cast<int>(std::round(refined_offset * down_rate));
  };

  constexpr bool smooth_speed = Mode::nsps >= kWideMinSymbolSamples;
  fit(smooth_speed && smooth_fit_);
  if (smooth_speed && !deep_ && !smooth_fit_) {
    float const centre = alignment_score(origin, best_delta);
    float const side = 0.5f * (alignment_score(origin, best_delta + tone_hz) +
                               alignment_score(origin, best_delta - tone_hz));
    double const share = centre > 0.0f ? side / centre : 0.0;
    if (share >= kFitSmoothShare) fit(true);
  }
  // Deep's second attempt: a smoothed fit that lands on the first attempt's
  // time and frequency would repeat that failed attempt exactly, so stop.
  if (smooth_fit_) {
    if (best_time == first_fit_time_ && best_delta == first_fit_delta_) return std::nullopt;
  } else {
    first_fit_time_ = best_time;
    first_fit_delta_ = best_delta;
  }

  float const phase_step = -best_delta * (kTau / down_rate);
  std::complex<float> const rotation_step = std::polar(1.0f, phase_step);
  std::complex<float> rotation{1.0f, 0.0f};
  for (int i = 0; i < kReadSamples<Mode>; ++i) {
    rotation *= rotation_step;
    channel_[static_cast<std::size_t>(i)] *= rotation;
  }

  offset = refined_offset;
  frequency += best_delta;
  float const sync = alignment_score(origin, 0.0f);

  std::array<std::array<float, kChannelSymbols>, kToneRows> tone_power{};
  std::array<std::array<std::complex<float>, kChannelSymbols>, kToneRows> tone_value{};
  // Power just below tone 0 and just above tone 7, for the spread reading.
  std::array<std::array<float, kChannelSymbols>, 2> edge_power{};

  for (int symbol = 0; symbol < kChannelSymbols; ++symbol) {
    int const start = best_time + symbol * Mode::downsample_sps;
    symbol_fft_.fill(kZero);
    if (start >= 0 && start + Mode::downsample_sps <= kReadSamples<Mode>) {
      std::copy(channel_.begin() + start,
                channel_.begin() + start + Mode::downsample_sps,
                symbol_fft_.begin());
    }

    pocketfft::c2c<float>(shape_t{static_cast<std::size_t>(Mode::downsample_sps)},
                          stride_t{kComplexStride}, stride_t{kComplexStride},
                          shape_t{0}, true, symbol_fft_.data(),
                          symbol_fft_.data(), 1.0f, 1);

    for (int row = 0; row < kToneRows; ++row) {
      tone_power[static_cast<std::size_t>(row)][static_cast<std::size_t>(symbol)] =
          std::abs(symbol_fft_[static_cast<std::size_t>(row)]) / 1000.0f;
      tone_value[static_cast<std::size_t>(row)][static_cast<std::size_t>(symbol)] =
          symbol_fft_[static_cast<std::size_t>(row)] / 1000.0f;
    }
    edge_power[0][static_cast<std::size_t>(symbol)] =
        std::norm(symbol_fft_[Mode::downsample_sps - 1] / 1000.0f);
    edge_power[1][static_cast<std::size_t>(symbol)] =
        std::norm(symbol_fft_[kToneRows] / 1000.0f);
  }

  int sync_matches = 0;
  for (std::size_t block = 0; block < kCostas.size(); ++block) {
    std::size_t const base = block * 36;
    for (std::size_t column = 0; column < 7; ++column) {
      std::size_t const position = base + column;
      int best_row = 0;
      for (int row = 1; row < kToneRows; ++row) {
        if (tone_power[static_cast<std::size_t>(row)][position] >
            tone_power[static_cast<std::size_t>(best_row)][position]) {
          best_row = row;
        }
      }
      if (kCostas[block][column] == best_row) ++sync_matches;
    }
  }

  // 6 or fewer: dropped, except at Normal and Slow speeds, where the spread
  // reading decides just before the decode passes (see kWeakSyncMinSpread).
  bool const weak_sync = sync_matches <= 6;
  if (weak_sync && !smooth_speed) return std::nullopt;

  if (sync_stats && !weak_sync) {
    events::SyncState event;
    event.kind = events::SyncState::Kind::Candidate;
    event.mode = Mode::event_mode;
    event.frequency = frequency;
    event.dt = offset;
    event.sync.candidate = sync_matches;
    emit(event);
  }

  std::array<std::array<float, kDataSymbols>, kToneRows> data_power{};
  for (int row = 0; row < kToneRows; ++row) {
    std::copy(tone_power[static_cast<std::size_t>(row)].begin() + 7,
              tone_power[static_cast<std::size_t>(row)].begin() + 36,
              data_power[static_cast<std::size_t>(row)].begin());
    std::copy(tone_power[static_cast<std::size_t>(row)].begin() + 43,
              tone_power[static_cast<std::size_t>(row)].begin() + 72,
              data_power[static_cast<std::size_t>(row)].begin() + 29);
  }

  std::array<float, 3 * kDataSymbols> amplitude_llr{};
  std::array<float, 3 * kDataSymbols> logarithmic_llr{};

  for (int symbol = 0; symbol < kDataSymbols; ++symbol) {
    std::array<float, kToneRows> power{};
    for (int row = 0; row < kToneRows; ++row) {
      power[static_cast<std::size_t>(row)] =
          data_power[static_cast<std::size_t>(row)][static_cast<std::size_t>(symbol)];
    }

    std::size_t const bit0 = static_cast<std::size_t>(3 * symbol);
    std::size_t const bit1 = bit0 + 1;
    std::size_t const bit2 = bit0 + 2;

    amplitude_llr[bit0] = std::max({power[4], power[5], power[6], power[7]}) -
                          std::max({power[0], power[1], power[2], power[3]});
    amplitude_llr[bit1] = std::max({power[2], power[3], power[6], power[7]}) -
                          std::max({power[0], power[1], power[4], power[5]});
    amplitude_llr[bit2] = std::max({power[1], power[3], power[5], power[7]}) -
                          std::max({power[0], power[2], power[4], power[6]});

    for (float& value : power) value = std::log(value + 1e-32f);

    logarithmic_llr[bit0] = std::max({power[4], power[5], power[6], power[7]}) -
                            std::max({power[0], power[1], power[2], power[3]});
    logarithmic_llr[bit1] = std::max({power[2], power[3], power[6], power[7]}) -
                            std::max({power[0], power[1], power[4], power[5]});
    logarithmic_llr[bit2] = std::max({power[1], power[3], power[5], power[7]}) -
                            std::max({power[0], power[2], power[4], power[6]});
  }

  // Readings are scaled to unit spread, then multiplied by this factor.
  // Deep uses 6.0: measured 2026-11-19, it decodes a few more messages on
  // fading and crowded sets, loses none and adds no wrong messages, but a
  // crowded band takes about 1.4 times as long, so Normal keeps 2.83.
  float const scale = deep_ ? 6.0f : 2.83f;
  auto normalize = [scale](auto& values) {
    float sum = 0.0f;
    float squares = 0.0f;
    for (float value : values) {
      sum += value;
      squares += value * value;
    }
    float const mean = sum / values.size();
    float const mean_square = squares / values.size();
    float const variance = mean_square - mean * mean;
    float const sigma = std::sqrt(variance > 0.0f ? variance : mean_square);

    if (!std::isfinite(sigma) || sigma <= 1.0e-12f) {
      values.fill(0.0f);
      return;
    }
    for (float& value : values) value = (value / sigma) * scale;
  };

  normalize(amplitude_llr);
  normalize(logarithmic_llr);
  // Later erasure passes modify amplitude_llr. Preserve the full evidence
  // for one optional stronger BP run after the standard decode sequence.
  auto const pristine_amplitude_llr = amplitude_llr;

  std::array<std::int8_t, kPayloadBits> payload{};
  std::array<std::int8_t, kCodeLength> codeword{};

  // Noncoherent block detection over groups of consecutive data symbols
  // (Franke, Somerville and Taylor, QEX July/August 2020). JS8 tones are
  // phase-continuous, so within a block the complex tone values of
  // neighbouring symbols add in phase for the transmitted tone sequence;
  // only the magnitude of each sum is used, so blocks need no common phase.
  // Each bit's metric is the best |sum| over tone sequences with that bit set
  // minus the best with it clear. Built only when needed, for blocks of 3
  // tones (block_llr) and of 2 tones (block2_llr). The paper reads blocks of
  // 1, 2 and 3 tones and notes that shorter blocks hold up better on fading.
  std::array<float, 3 * kDataSymbols> block_llr{};
  std::array<float, 3 * kDataSymbols> block2_llr{};
  auto build_block_llr = [&](int block_symbols, std::array<float, 3 * kDataSymbols>& out) {
    for (int block = 0; block < 2; ++block) {
      int const first_symbol = block == 0 ? 7 : 43;
      int const first_data = block * 29;
      for (int group = 0; group < 29; group += block_symbols) {
        int const n = std::min(block_symbols, 29 - group);
        int const bits = 3 * n;
        std::array<float, 3 * kBlockSymbols> best_one{};
        std::array<float, 3 * kBlockSymbols> best_zero{};
        best_one.fill(-1.0f);
        best_zero.fill(-1.0f);
        for (int sequence = 0; sequence < (1 << bits); ++sequence) {
          std::complex<float> sum{0.0f, 0.0f};
          for (int k = 0; k < n; ++k) {
            int const tone = (sequence >> (3 * (n - 1 - k))) & 7;
            sum += tone_value[static_cast<std::size_t>(tone)]
                             [static_cast<std::size_t>(first_symbol + group + k)];
          }
          float const metric = std::abs(sum);
          for (int bit = 0; bit < bits; ++bit) {
            auto& best = ((sequence >> (bits - 1 - bit)) & 1) ? best_one : best_zero;
            best[static_cast<std::size_t>(bit)] =
                std::max(best[static_cast<std::size_t>(bit)], metric);
          }
        }
        for (int bit = 0; bit < bits; ++bit) {
          out[static_cast<std::size_t>(3 * (first_data + group) + bit)] =
              best_one[static_cast<std::size_t>(bit)] - best_zero[static_cast<std::size_t>(bit)];
        }
      }
    }
    normalize(out);
  };

  // Reports a CRC-valid payload: sync event, frame text, subtraction and SNR.
  auto finish = [&]() -> std::optional<Frame> {
    if (sync_stats) {
      events::SyncState event;
      event.kind = events::SyncState::Kind::Decoded;
      event.mode = Mode::event_mode;
      event.frequency = frequency;
      event.dt = refined_offset;
      event.sync.decoded = sync;
      emit(event);
    }

    std::string const text = decoder::decode_message(payload);
    int const type = (payload[72] << 2) | (payload[73] << 1) | payload[74];

    std::array<int, kChannelSymbols> tones{};
    legacy_encode(type, kCostas, text.data(), tones.data());

    if (subtract) subtract_reference(make_reference(tones, frequency), refined_offset);

    float signal_power = 0.0f;
    for (std::size_t i = 0; i < tones.size(); ++i) {
      float const value =
          tone_power[static_cast<std::size_t>(tones[i])][i];
      signal_power += value * value;
    }

    snr = std::max(10.0f * std::log10(std::max(signal_power / linear_base - 1.0f,
                                               1.259e-10f)) - 32.0f,
                   -60.0f);

    return Frame{type, text};
  };

  // Passes 1-5: belief propagation on the standard metrics. Pass 6: retired
  // (see below). Pass 7: belief propagation on the 3-tone block metrics, then
  // on the spread reading with iterative demapping. Passes 8-10:
  // ordered statistics decoding on the standard, 2-tone and 3-tone metrics,
  // as a last resort. Pass 9 runs only with deep decoding: on 400 noise-only
  // files (2026-11-19) it added 32 results reaching the CRC check to the 66 of
  // passes 8 and 10, about 1.5 times the chance of a wrong message. Belief
  // propagation (passes 1-7) brought no noise result to the CRC check.
  // Spread reading (see kSpreadMinShare), built once when first needed.
  std::array<float, 3 * kDataSymbols> spread_llr{};
  std::array<float, kToneRows * kDataSymbols> spread_scores{};
  bool spread_ready = false;
  float spread_share = 0.0f;
  int spread_sync = 0;
  bool spread_built = false;
  auto build_spread = [&]() {
    if (spread_built) return;
    spread_built = true;
    auto energy = [&](int symbol, int tone) -> float {
      auto const s = static_cast<std::size_t>(symbol);
      if (tone < 0) return edge_power[0][s];
      if (tone >= kToneRows) return edge_power[1][s];
      return std::norm(tone_value[static_cast<std::size_t>(tone)][s]);
    };
    double centre = 0.0, side = 0.0, rest = 0.0;
    int rest_count = 0;
    for (std::size_t block = 0; block < kCostas.size(); ++block) {
      for (int column = 0; column < 7; ++column) {
        int const symbol = static_cast<int>(block) * 36 + column;
        int const tone = kCostas[block][static_cast<std::size_t>(column)];
        centre += energy(symbol, tone);
        side += 0.5 * (energy(symbol, tone - 1) + energy(symbol, tone + 1));
        for (int other = 0; other < kToneRows; ++other) {
          if (std::abs(other - tone) > 1) {
            rest += energy(symbol, other);
            ++rest_count;
          }
        }
      }
    }
    double const noise = rest_count ? rest / rest_count : 0.0;
    if (noise <= 0.0) return;
    double const t0 = std::max(0.0, centre / 21.0 - noise) / noise;
    double const t1 = std::max(0.0, side / 21.0 - noise) / noise;
    spread_share = t0 > 0.0 ? static_cast<float>(t1 / t0) : 0.0f;
    double const bold = deep_ ? kSpreadBoldnessDeep : kSpreadBoldness;
    double const w0 = bold * t0 / (1.0 + t0) / noise;
    double const w1 = bold * t1 / (1.0 + t1) / noise;

    // Wide spread reading (see kWideMinSymbolSamples).
    bool wide = false;
    std::vector<float> wide_energy;
    std::array<double, kWideReach<Mode> + 1> wide_weight{};
    if constexpr (Mode::nsps >= kWideMinSymbolSamples) {
      extract_wide_energy(frequency - best_delta, best_delta, best_time, wide_energy);
      auto at = [&](int symbol, int bin) -> double {
        return wide_energy[static_cast<std::size_t>(symbol * kWideBins<Mode> + bin - kWideLow<Mode>)];
      };
      std::array<double, kWideReach<Mode> + 1> sums{};
      double far = 0.0;
      int far_count = 0;
      for (std::size_t block = 0; block < kCostas.size(); ++block) {
        for (int column = 0; column < 7; ++column) {
          int const symbol = static_cast<int>(block) * 36 + column;
          int const tone = kCostas[block][static_cast<std::size_t>(column)];
          sums[0] += at(symbol, tone);
          for (int k = 1; k <= kWideReach<Mode>; ++k) {
            sums[static_cast<std::size_t>(k)] +=
                0.5 * (at(symbol, tone - k) + at(symbol, tone + k));
          }
          for (int bin = kWideLow<Mode>; bin <= kWideHigh<Mode>; ++bin) {
            if (std::abs(bin - tone) >= kWideReach<Mode> + 2) {
              far += at(symbol, bin);
              ++far_count;
            }
          }
        }
      }
      double const wide_noise = far_count ? far / far_count : 0.0;
      if (wide_noise > 0.0) {
        std::array<double, kWideReach<Mode> + 1> t{};
        for (int k = 0; k <= kWideReach<Mode>; ++k) {
          t[static_cast<std::size_t>(k)] =
              std::max(0.0, sums[static_cast<std::size_t>(k)] / 21.0 - wide_noise) / wide_noise;
          wide_weight[static_cast<std::size_t>(k)] =
              t[static_cast<std::size_t>(k)] / (1.0 + t[static_cast<std::size_t>(k)]) / wide_noise;
        }
        wide = t[0] > 0.0 && t[1] / t[0] >= kWideMinShare;
      }
    }

    auto scores = [&](int symbol, std::array<double, kToneRows>& score) {
      for (int tone = 0; tone < kToneRows; ++tone) {
        if (wide) {
          auto bin = [&](int offset) -> double {
            return wide_energy[static_cast<std::size_t>(symbol * kWideBins<Mode> + tone + offset - kWideLow<Mode>)];
          };
          double value = wide_weight[0] * bin(0);
          for (int k = 1; k <= kWideReach<Mode>; ++k) {
            value += wide_weight[static_cast<std::size_t>(k)] * (bin(-k) + bin(k));
          }
          score[static_cast<std::size_t>(tone)] = value;
          continue;
        }
        score[static_cast<std::size_t>(tone)] =
            w0 * energy(symbol, tone) +
            w1 * (energy(symbol, tone - 1) + energy(symbol, tone + 1));
      }
    };
    std::array<double, kToneRows> score{};
    for (std::size_t block = 0; block < kCostas.size(); ++block) {
      for (int column = 0; column < 7; ++column) {
        scores(static_cast<int>(block) * 36 + column, score);
        auto const best = std::max_element(score.begin(), score.end()) - score.begin();
        if (best == kCostas[block][static_cast<std::size_t>(column)]) ++spread_sync;
      }
    }
    for (int data = 0; data < kDataSymbols; ++data) {
      scores(data < 29 ? 7 + data : 43 + (data - 29), score);
      double const top = *std::max_element(score.begin(), score.end());
      for (int tone = 0; tone < kToneRows; ++tone) {
        spread_scores[static_cast<std::size_t>(kToneRows * data + tone)] =
            static_cast<float>(score[static_cast<std::size_t>(tone)] - top);
      }
      for (int bit = 0; bit < 3; ++bit) {
        double one = 0.0, zero = 0.0;
        for (int tone = 0; tone < kToneRows; ++tone) {
          double const weight = std::exp(score[static_cast<std::size_t>(tone)] - top);
          ((tone >> (2 - bit)) & 1 ? one : zero) += weight;
        }
        spread_llr[static_cast<std::size_t>(3 * data + bit)] = static_cast<float>(
            std::clamp(std::log((one + 1e-300) / (zero + 1e-300)), -20.0, 20.0));
      }
    }
    spread_ready = true;
  };

  if (weak_sync) {
    build_spread();
    if (!spread_ready || spread_sync < kWeakSyncMinSpread) return std::nullopt;
    if (sync_stats) {
      events::SyncState event;
      event.kind = events::SyncState::Kind::Candidate;
      event.mode = Mode::event_mode;
      event.frequency = frequency;
      event.dt = offset;
      event.sync.candidate = sync_matches;
      emit(event);
    }
  }

  for (int pass = 1; pass <= 11; ++pass) {
    std::array<float, 3 * kDataSymbols> rescue_llr{};
    if (pass == 9 && !deep_) continue;
    if (pass >= 8) {
      // Ordered statistics decoding always returns its closest codeword, so
      // accept it only with good Costas sync and a small discrepancy
      // relative to the total evidence; the CRC check below still applies.
      // On noise-only audio these two limits pass about 3% of attempts
      // before the CRC. Pass 11 uses the spread reading's own sync count.
      if (pass <= 10 && sync_matches < kOsdMinSyncMatches) continue;
      if (pass == 10 && !deep_ && sync_matches < kBlockOsdMinSyncMatches) continue;
      if (pass == 11) {
        build_spread();
        if (spread_share < kSpreadMinShare || spread_sync < kSpreadMinSync) continue;
      }
      if (pass == 9) build_block_llr(2, block2_llr);
      auto const& evidence_llr = pass == 8 ? pristine_amplitude_llr
                                 : pass == 9 ? block2_llr
                                 : pass == 10 ? block_llr : spread_llr;
      float discrepancy = 0.0f;
      float runner_up = 0.0f;
      float evidence = 0.0f;
      for (float value : evidence_llr) evidence += std::abs(value);
      std::array<float, 3 * kDataSymbols> refined{};
      if (kRefineRounds > 0) {
        // Deep, pass 11, tones 10 Hz apart or closer (not Turbo): the
        // ordering rounds also redo the tone reading (iterative demapping).
        // Candidates are still scored against the plain spread reading.
        bool const demap = deep_ && pass == 11 && Mode::nsps >= kSpreadDemapMinSymbolSamples;
        decoder::refine_reliabilities(evidence_llr, kRefineRounds, kRefineFeedback, refined,
                                      demap ? &spread_scores : nullptr);
      }
      hard_errors = decoder::ordered_statistics_decode(
          evidence_llr, payload, codeword, kOsdOrder, &discrepancy, true, &runner_up,
          kRefineRounds > 0 ? &refined : nullptr);
      if (hard_errors < 0 || evidence <= 0.0f ||
          discrepancy > kOsdMaxDiscrepancy * evidence) {
        continue;
      }
      if (runner_up - discrepancy < kOsdMinRunnerUpGap * evidence) continue;
    } else if (pass == 6) {
      // Retired 2026-11-20: belief propagation on the 2-tone reading added 4
      // of 3,132 messages once pass 7's iterative demapping was in (research
      // note, section 5). The 2-tone reading is still built for pass 9.
      continue;
    } else if (pass == 7) {
      build_block_llr(3, block_llr);
      hard_errors = decoder::belief_propagation(block_llr, payload, codeword);
      if (hard_errors < 0) {
        // Iterative demapping on the spread reading, for every candidate:
        // with no spread its neighbour weight is near zero and it reads each
        // tone on its own.
        build_spread();
        if (spread_ready) {
          hard_errors = decoder::belief_propagation(spread_llr, payload, codeword, 60, true,
                                                    &spread_scores);
        }
      }
    } else if (pass == 5) {
      // Rare weak candidates may need more iterations after the early-stall
      // heuristic fires. Bound that work per receive frame and require solid
      // fine Costas synchronization rather than lowering sync thresholds.
      if (sync_matches < 9 || rescue_budget <= 0) continue;
      --rescue_budget;
      rescue_llr = pristine_amplitude_llr;
      for (auto& value : rescue_llr) value *= 1.5f;
    } else if (pass == 3) {
      std::fill(amplitude_llr.begin(), amplitude_llr.begin() + 24, 0.0f);
    } else if (pass == 4) {
      std::fill(amplitude_llr.begin() + 24, amplitude_llr.begin() + 48, 0.0f);
    }

    if (pass <= 5) {
      auto const& llr = (pass == 5) ? rescue_llr
                        : (pass == 2) ? logarithmic_llr : amplitude_llr;
      hard_errors = pass == 5
          ? decoder::belief_propagation(llr, payload, codeword, 150, false)
          : decoder::belief_propagation(llr, payload, codeword);
    }
    snr = -99.0f;

    if (std::all_of(codeword.begin(), codeword.end(),
                    [](std::int8_t bit) { return bit == 0; })) {
      continue;
    }

    // Ordered statistics results were judged by their own limits above.
    bool const acceptable = pass >= 8 ? hard_errors >= 0 :
        hard_errors >= 0 && hard_errors < 60 &&
        !(sync < 2.0f && hard_errors > 35) &&
        !(pass > 2 && hard_errors > 39) &&
        !(pass == 4 && hard_errors > 30);

    if (!acceptable) {
      hard_errors = -1;
      continue;
    }

    if (!decoder::crc_ok(payload)) continue;
    if (pass >= 8 && different_frame_near(frequency, offset, decoder::decode_message(payload))) {
      continue;
    }

    return finish();
  }

  // Callsign hints: retry a weak signal that failed every pass above as a
  // directed frame whose callsigns are known (see DecodeState). Each hint
  // fixes its bits as certain, then belief propagation and ordered
  // statistics decoding solve the remaining bits. A result must keep the
  // hinted bits, pass the CRC and stay within the discrepancy limit measured
  // against the received evidence.
  if (hints_.empty() || sync_matches < kOsdMinSyncMatches) return std::nullopt;

  using Evidence = std::array<float, 3 * kDataSymbols>;
  for (Evidence const* evidence_llr :
       {static_cast<Evidence const*>(&pristine_amplitude_llr),
        static_cast<Evidence const*>(&block_llr)}) {
    float evidence = 0.0f;
    float strongest = 0.0f;
    for (float value : *evidence_llr) {
      evidence += std::abs(value);
      strongest = std::max(strongest, std::abs(value));
    }
    if (evidence <= 0.0f) continue;
    float const certain = 4.0f * strongest + 10.0f;

    for (auto const& hint : hints_) {
      auto hinted_llr = *evidence_llr;
      for (std::size_t bit = 0; bit < kPayloadBits; ++bit) {
        if (hint[bit] < 0) continue;
        hinted_llr[kPayloadBits + bit] = hint[bit] ? certain : -certain;
      }

      // The hinted bits must survive, and the frame must be a well-formed
      // directed frame: its 6-bit number field (payload 66-71) is 0 unless
      // the 5-bit command (payload 59-63) is SNR (25) or HEARTBEAT SNR (29).
      auto keeps_hint = [&] {
        for (std::size_t bit = 0; bit < kPayloadBits; ++bit) {
          if (hint[bit] >= 0 && payload[bit] != hint[bit]) return false;
        }
        int command = 0;
        for (std::size_t bit = 59; bit < 64; ++bit) command = (command << 1) | payload[bit];
        int number = 0;
        for (std::size_t bit = 66; bit < 72; ++bit) number = (number << 1) | payload[bit];
        return number == 0 || command == 25 || command == 29;
      };
      auto within_limit = [&] {
        float discrepancy = 0.0f;
        hard_errors = 0;
        for (std::size_t i = 0; i < kCodeLength; ++i) {
          if (((*evidence_llr)[i] > 0.0f) != (codeword[i] != 0)) {
            discrepancy += std::abs((*evidence_llr)[i]);
            ++hard_errors;
          }
        }
        return discrepancy <= kHintMaxDiscrepancy * evidence;
      };

      // Belief propagation is tried on the standard metrics only; on the
      // block-detection metrics it found nothing that ordered statistics
      // decoding missed.
      bool const block = evidence_llr == &block_llr;
      for (int method = block ? 1 : 0; method < 2; ++method) {
        float unused = 0.0f;
        int const result = method == 0
            ? decoder::belief_propagation(hinted_llr, payload, codeword)
            : decoder::ordered_statistics_decode(hinted_llr, payload, codeword,
                                                 kOsdOrder, &unused);
        if (result < 0 || !keeps_hint() || !decoder::crc_ok(payload) ||
            !within_limit()) {
          continue;
        }
        return finish();
      }
    }
  }

  hard_errors = -1;
  return std::nullopt;
}

template <typename Mode>
void ChannelDecoder<Mode>::build_hints(dec_data const& data) {
  hints_.clear();
  std::uint32_t const own = data.hint_own_call & 0x0FFFFFFFu;

  std::vector<std::uint32_t> heard;
  for (std::uint32_t call : data.hint_heard_calls) {
    call &= 0x0FFFFFFFu;
    if (call == 0 || call == own) continue;
    if (std::find(heard.begin(), heard.end(), call) != heard.end()) continue;
    heard.push_back(call);
    if (heard.size() == kHintMaxHeard) break;
  }

  // Directed frame: 3-bit frame type 3, then the 28-bit from and to
  // callsigns, most significant bit first. It always opens a transmission:
  // first-frame flag set (payload 74), data flag clear (payload 72).
  auto directed = [&](std::uint32_t from, std::uint32_t to) {
    std::array<std::int8_t, kPayloadBits> hint;
    hint.fill(-1);
    hint[0] = 0;
    hint[1] = 1;
    hint[2] = 1;
    hint[72] = 0;
    hint[74] = 1;
    auto put = [&](std::size_t first, std::uint32_t call) {
      for (std::size_t i = 0; i < 28; ++i) {
        hint[first + i] = static_cast<std::int8_t>((call >> (27 - i)) & 1u);
      }
    };
    if (from != 0) put(3, from);
    if (to != 0) put(31, to);
    hints_.push_back(hint);
  };

  // Most specific first: heard station to me, anyone to me, heard station to
  // anyone.
  if (own != 0) {
    for (std::uint32_t call : heard) directed(call, own);
    directed(0, own);
  }
  for (std::uint32_t call : heard) directed(call, 0);
}

// ---------------------------------------------------------------------------
// Signal reconstruction
// ---------------------------------------------------------------------------

template <typename Mode>
std::vector<std::complex<float>> ChannelDecoder<Mode>::make_reference(
    std::array<int, kChannelSymbols> const& tones, float frequency) {
  float const carrier = kTau * frequency / 12000.0f;
  float phase = 0.0f;

  std::vector<std::complex<float>> reference;
  reference.reserve(kChannelSymbols * Mode::nsps);

  for (int symbol = 0; symbol < kChannelSymbols; ++symbol) {
    float const step = carrier + kTau * static_cast<float>(tones[static_cast<std::size_t>(symbol)]) /
                                      Mode::nsps;
    for (int i = 0; i < Mode::nsps; ++i) {
      reference.push_back(std::polar(1.0f, phase));
      phase = std::fmod(phase + step, kTau);
    }
  }
  return reference;
}

template <typename Mode>
void ChannelDecoder<Mode>::subtract_reference(
    std::vector<std::complex<float>> const& reference, float offset) {
  int const start = static_cast<int>(offset * 12000.0f);
  std::size_t const reference_start =
      start < 0 ? static_cast<std::size_t>(-start) : 0;
  std::size_t const signal_start = start > 0 ? static_cast<std::size_t>(start) : 0;

  if (reference_start >= reference.size() || signal_start >= samples_.size()) return;

  std::size_t const count =
      std::min(reference.size() - reference_start, samples_.size() - signal_start);

  for (std::size_t i = 0; i < count; ++i) {
    subtract_work_[i] = samples_[signal_start + i] * std::conj(reference[reference_start + i]);
  }
  std::fill(subtract_work_.begin() + static_cast<std::ptrdiff_t>(count),
            subtract_work_.end(), kZero);

  pocketfft::c2c<float>(shape_t{static_cast<std::size_t>(Mode::nmax)},
                        stride_t{kComplexStride}, stride_t{kComplexStride},
                        shape_t{0}, true, subtract_work_.data(),
                        subtract_work_.data(), 1.0f, 1);

  for (std::size_t i = 0; i < subtract_work_.size(); ++i) {
    subtract_work_[i] *= subtract_filter_[i];
  }

  // The frequency-domain subtraction filter is already normalized by
  // 1/N when it is constructed. As with FFTW_BACKWARD, the inverse here is
  // unscaled; normalizing again would leave only 1/N of the cancellation
  // signal and mask weaker stations during later decode passes.
  pocketfft::c2c<float>(shape_t{static_cast<std::size_t>(Mode::nmax)},
                        stride_t{kComplexStride}, stride_t{kComplexStride},
                        shape_t{0}, false, subtract_work_.data(),
                        subtract_work_.data(), 1.0f, 1);

  for (std::size_t i = 0; i < count; ++i) {
    samples_[signal_start + i] -=
        2.0f * std::real(subtract_work_[i] * reference[reference_start + i]);
  }
}

// ---------------------------------------------------------------------------
// Frame entry point
// ---------------------------------------------------------------------------

template <typename Mode>
std::size_t ChannelDecoder<Mode>::operator()(dec_data const& data, int position,
                                             int size, EventEmitter emit) {
  int const pos = std::max(0, position);
  int const count = std::clamp(size, 0, Mode::nmax);
  if (pos >= kJs8NtMax * kJs8RxSampleRate || count == 0) return 0;

  if (data.params.syncStats) emit(events::SyncStart{pos, count});

  samples_.fill(0.0f);

  int const available = kJs8NtMax * kJs8RxSampleRate - pos;
  if (available < count) {
    int const first = available;
    int const second = count - first;

    for (int i = 0; i < first; ++i) {
      samples_[static_cast<std::size_t>(i)] =
          static_cast<float>(data.samples[static_cast<std::size_t>(pos + i)]);
    }
    for (int i = 0; i < second; ++i) {
      samples_[static_cast<std::size_t>(first + i)] =
          static_cast<float>(data.samples[static_cast<std::size_t>(i)]);
    }
  } else {
    for (int i = 0; i < count; ++i) {
      samples_[static_cast<std::size_t>(i)] =
          static_cast<float>(data.samples[static_cast<std::size_t>(pos + i)]);
    }
  }

  Frame::Map decoded;
  reported_.clear();
  int rescue_budget = 32;
  build_hints(data);
  deep_ = data.params.deep;

  for (int pass = 1; pass <= 3; ++pass) {
    auto candidates = find_candidates(data.params.nfa, data.params.nfb);
    if (candidates.empty()) break;

    int const preferred = data.params.nfqso;
    std::sort(candidates.begin(), candidates.end(),
              [preferred](Candidate const& a, Candidate const& b) {
                float const da = std::abs(a.frequency - preferred);
                float const db = std::abs(b.frequency - preferred);
                if (da < 10.0f && db >= 10.0f) return true;
                if (db < 10.0f && da >= 10.0f) return false;
                return std::tie(da, a.frequency) < std::tie(db, b.frequency);
              });

    prepare_baseband();

    bool const subtract = pass < 3;
    bool improved = false;

    for (auto candidate : candidates) {
      float frequency = candidate.frequency;
      float offset = candidate.offset;
      int hard_errors = -1;
      float snr = 0.0f;

      auto frame = attempt_decode(data.params.syncStats, subtract, frequency,
                                  offset, hard_errors, snr, rescue_budget, emit);
      // Deep, Normal and Slow speeds: a candidate that fails is decoded once
      // more with the smoothed fit (see kFitSmoothShare).
      if (!frame && deep_ && Mode::nsps >= kWideMinSymbolSamples) {
        frequency = candidate.frequency;
        offset = candidate.offset;
        hard_errors = -1;
        snr = 0.0f;
        smooth_fit_ = true;
        frame = attempt_decode(data.params.syncStats, subtract, frequency,
                               offset, hard_errors, snr, rescue_budget, emit);
        smooth_fit_ = false;
      }
      if (!frame) continue;

      int const rounded_snr = static_cast<int>(std::round(snr));
      auto [slot, inserted] = decoded.try_emplace(std::move(*frame), rounded_snr);
      if (!inserted && slot->second >= rounded_snr) continue;

      improved = true;
      if (!inserted) slot->second = rounded_snr;

      emit(events::Decoded{data.params.utc,
                           rounded_snr,
                           offset - Mode::start_seconds,
                           frequency,
                           slot->first.text,
                           slot->first.type,
                           1.0f - hard_errors / 60.0f,
                           Mode::event_mode});
      reported_.push_back({frequency, offset, slot->first.text});
    }

    if (!improved) break;
  }

  return decoded.size();
}

}  // namespace

std::size_t legacy_decode(DecodeState const& state,
                          std::function<void(events::Variant const&)> emit_fn) {
  static thread_local std::unique_ptr<ChannelDecoder<ProfileA>> decoder_a;
  static thread_local std::unique_ptr<ChannelDecoder<ProfileB>> decoder_b;
  static thread_local std::unique_ptr<ChannelDecoder<ProfileC>> decoder_c;
  static thread_local std::unique_ptr<ChannelDecoder<ProfileE>> decoder_e;
  static thread_local std::unique_ptr<ChannelDecoder<ProfileI>> decoder_i;

  auto emit = [&](events::Variant const& event) {
    if (emit_fn) emit_fn(event);
  };

  auto run = [&](auto& decoder, int position, int size) {
    using Decoder =
        typename std::remove_reference_t<decltype(decoder)>::element_type;
    if (!decoder) decoder = std::make_unique<Decoder>();
    return (*decoder)(state, position, size, emit);
  };

  int const submodes = state.params.nsubmodes;
  std::size_t total = 0;

  emit(events::DecodeStarted{submodes});

  if ((submodes & (1 << ProfileI::bit_index)) != 0) {
    total += run(decoder_i, state.params.kposI, state.params.kszI);
  }
  if ((submodes & (1 << ProfileE::bit_index)) != 0) {
    total += run(decoder_e, state.params.kposE, state.params.kszE);
  }
  if ((submodes & (1 << ProfileC::bit_index)) != 0) {
    total += run(decoder_c, state.params.kposC, state.params.kszC);
  }
  if ((submodes & (1 << ProfileB::bit_index)) != 0) {
    total += run(decoder_b, state.params.kposB, state.params.kszB);
  }
  if ((submodes & (1 << ProfileA::bit_index)) != 0) {
    total += run(decoder_a, state.params.kposA, state.params.kszA);
  }

  emit(events::DecodeFinished{total});
  return total;
}

void legacy_encode(int const type, protocol::CostasArray const& costas,
                   char const* const message, int* const tones) {
  std::array<std::uint8_t, 11> bytes{};

  for (int i = 0, j = 0; i < kFrameCharacters; i += 4, j += 3) {
    std::uint32_t const word = (static_cast<std::uint32_t>(decoder::word_for(message[i])) << 18) |
                               (static_cast<std::uint32_t>(decoder::word_for(message[i + 1])) << 12) |
                               (static_cast<std::uint32_t>(decoder::word_for(message[i + 2])) << 6) |
                               static_cast<std::uint32_t>(decoder::word_for(message[i + 3]));

    bytes[static_cast<std::size_t>(j)] = static_cast<std::uint8_t>(word >> 16);
    bytes[static_cast<std::size_t>(j + 1)] = static_cast<std::uint8_t>(word >> 8);
    bytes[static_cast<std::size_t>(j + 2)] = static_cast<std::uint8_t>(word);
  }

  for (int i = 0; i < kFrameCharacters; ++i) {
    if (decoder::word_for(message[i]) == 0xFF) {
      throw std::runtime_error("Invalid character in message");
    }
  }

  bytes[9] = static_cast<std::uint8_t>((type & 0x7) << 5);
  std::uint16_t const crc = decoder::crc12(bytes.data(), bytes.size());
  bytes[9] |= static_cast<std::uint8_t>((crc >> 7) & 0x1F);
  bytes[10] = static_cast<std::uint8_t>((crc & 0x7F) << 1);

  int* costas_out = tones;
  int* parity_out = tones + 7;
  int* data_out = tones + 43;

  for (auto const& array : costas) {
    std::copy(array.begin(), array.end(), costas_out);
    costas_out += 36;
  }

  std::size_t bit_count = 0;
  std::size_t byte_index = 0;
  std::uint8_t byte_mask = 0x80;
  std::uint8_t parity_word = 0;
  std::uint8_t data_word = 0;

  for (std::size_t i = 0; i < kPayloadBits; ++i) {
    bool parity = false;
    for (std::size_t j = 0; j < kPayloadBits; ++j) {
      bool const message_bit =
          (bytes[j / 8] & (1u << (7 - (j % 8)))) != 0;
      parity ^= decoder::parity_bit(i, j) && message_bit;
    }

    parity_word = static_cast<std::uint8_t>((parity_word << 1) | (parity ? 1u : 0u));
    data_word = static_cast<std::uint8_t>(
        (data_word << 1) | ((bytes[byte_index] & byte_mask) != 0 ? 1u : 0u));
    byte_mask = byte_mask == 1 ? (++byte_index, 0x80) : static_cast<std::uint8_t>(byte_mask >> 1);

    if (++bit_count == 3) {
      *parity_out++ = parity_word;
      *data_out++ = data_word;
      parity_word = 0;
      data_word = 0;
      bit_count = 0;
    }
  }
}

}  // namespace js8core
