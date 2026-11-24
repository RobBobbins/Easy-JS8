#pragma once

#include <array>
#include <cstddef>
#include <cstdint>
#include <string_view>

#include "js8core/protocol/costas.hpp"

// The generator rows and sparse parity checks encode the fixed JS8 wire
// format, along with the message alphabet and submode constants. The inverse
// check-node adjacency is derived below rather than copied separately.
// The algorithms consuming these values are implemented in ldpc.cpp and
// legacy_decoder.cpp.

namespace js8core::decoder::data {

// Code and frame geometry.
inline constexpr int kCodeLength = 174;      // coded bits
inline constexpr int kPayloadBits = 87;      // message plus CRC
inline constexpr int kParityBits = 87;       // check equations
inline constexpr int kDataSymbols = 58;      // data tones per frame
inline constexpr int kSyncSymbols = 21;      // three Costas blocks
inline constexpr int kChannelSymbols = 79;   // sync + data
inline constexpr int kFrameCharacters = 12;  // six-bit words in a frame
inline constexpr int kStepsPerSymbol = 4;    // quarter-symbol spectra
inline constexpr int kOversample = 2;        // FFT span per symbol
inline constexpr int kFilterLength = 1400;   // subtraction filter taps (Turbo, mode 8)
inline constexpr int kFilterSymbols = 3;     // subtraction window in symbols (Normal, Fast, Slow)
inline constexpr int kToneRows = 8;          // candidate tone bins
inline constexpr int kComplexCapacity = 3200;
inline constexpr float kMinSync = 1.5f;
inline constexpr int kFreqSearchSteps = 5;   // +/- half-Hz steps
inline constexpr std::size_t kMaxCandidates = 300;
inline constexpr float kTau = 6.283185307179586476925286766559f;

// Noise-baseline polynomial fit.
inline constexpr int kBaselineDegree = 5;
inline constexpr int kBaselineSamplePercent = 10;
inline constexpr int kBaselineMinHz = 500;
inline constexpr int kBaselineMaxHz = 2500;

// Six-bit message alphabet, in word order.
inline constexpr std::string_view kAlphabet =
    "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-+";

// For each coded bit, the three check equations that include it.
inline constexpr std::array<std::array<int, 3>, kCodeLength> kBitChecks = {{
        { 0, 24, 68}, { 1,  4, 72}, { 2, 31, 67}, { 3, 50, 60}, { 5, 62, 69}, { 6, 32, 78},
        { 7, 49, 85}, { 8, 36, 42}, { 9, 40, 64}, {10, 13, 63}, {11, 74, 76}, {12, 22, 80},
        {14, 15, 81}, {16, 55, 65}, {17, 52, 59}, {18, 30, 51}, {19, 66, 83}, {20, 28, 71},
        {21, 23, 43}, {25, 34, 75}, {26, 35, 37}, {27, 39, 41}, {29, 53, 54}, {33, 48, 86},
        {38, 56, 57}, {44, 73, 82}, {45, 61, 79}, {46, 47, 84}, {58, 70, 77}, { 0, 49, 52},
        { 1, 46, 83}, { 2, 24, 78}, { 3,  5, 13}, { 4,  6, 79}, { 7, 33, 54}, { 8, 35, 68},
        { 9, 42, 82}, {10, 22, 73}, {11, 16, 43}, {12, 56, 75}, {14, 26, 55}, {15, 27, 28},
        {17, 18, 58}, {19, 39, 62}, {20, 34, 51}, {21, 53, 63}, {23, 61, 77}, {25, 31, 76},
        {29, 71, 84}, {30, 64, 86}, {32, 38, 50}, {36, 47, 74}, {37, 69, 70}, {40, 41, 67},
        {44, 66, 85}, {45, 80, 81}, {48, 65, 72}, {57, 59, 65}, {60, 64, 84}, { 0, 13, 20},
        { 1, 12, 58}, { 2, 66, 81}, { 3, 31, 72}, { 4, 35, 53}, { 5, 42, 45}, { 6, 27, 74},
        { 7, 32, 70}, { 8, 48, 75}, { 9, 57, 63}, {10, 47, 67}, {11, 18, 44}, {14, 49, 60},
        {15, 21, 25}, {16, 71, 79}, {17, 39, 54}, {19, 34, 50}, {22, 24, 33}, {23, 62, 86},
        {26, 38, 73}, {28, 77, 82}, {29, 69, 76}, {30, 68, 83}, {21, 36, 85}, {37, 40, 80},
        {41, 43, 56}, {46, 52, 61}, {51, 55, 78}, {59, 74, 80}, { 0, 38, 76}, { 1, 15, 40},
        { 2, 30, 53}, { 3, 35, 77}, { 4, 44, 64}, { 5, 56, 84}, { 6, 13, 48}, { 7, 20, 45},
        { 8, 14, 71}, { 9, 19, 61}, {10, 16, 70}, {11, 33, 46}, {12, 67, 85}, {17, 22, 42},
        {18, 63, 72}, {23, 47, 78}, {24, 69, 82}, {25, 79, 86}, {26, 31, 39}, {27, 55, 68},
        {28, 62, 65}, {29, 41, 49}, {32, 36, 81}, {34, 59, 73}, {37, 54, 83}, {43, 51, 60},
        {50, 52, 71}, {57, 58, 66}, {46, 55, 75}, { 0, 18, 36}, { 1, 60, 74}, { 2,  7, 65},
        { 3, 59, 83}, { 4, 33, 38}, { 5, 25, 52}, { 6, 31, 56}, { 8, 51, 66}, { 9, 11, 14},
        {10, 50, 68}, {12, 13, 64}, {15, 30, 42}, {16, 19, 35}, {17, 79, 85}, {20, 47, 58},
        {21, 39, 45}, {22, 32, 61}, {23, 29, 73}, {24, 41, 63}, {26, 48, 84}, {27, 37, 72},
        {28, 43, 80}, {34, 67, 69}, {40, 62, 75}, {44, 48, 70}, {49, 57, 86}, {47, 53, 82},
        {12, 54, 78}, {76, 77, 81}, { 0,  1, 23}, { 2,  5, 74}, { 3, 55, 86}, { 4, 43, 52},
        { 6, 49, 82}, { 7,  9, 27}, { 8, 54, 61}, {10, 28, 66}, {11, 32, 39}, {13, 15, 19},
        {14, 34, 72}, {16, 30, 38}, {17, 35, 56}, {18, 45, 75}, {20, 41, 83}, {21, 33, 58},
        {22, 25, 60}, {24, 59, 64}, {26, 63, 79}, {29, 36, 65}, {31, 44, 71}, {37, 50, 85},
        {40, 76, 78}, {42, 55, 67}, {46, 73, 81}, {39, 51, 77}, {53, 60, 70}, {45, 57, 68}
    }};

struct CheckNode {
  int count;
  std::array<int, 7> bits;
};

// For each check equation, the coded bits that participate.
// Derive the inverse parity-check adjacency directly from the canonical
// bit-to-check protocol matrix. The on-air LDPC code is unchanged; the second
// precomputed copy of its graph no longer needs to be copied or maintained.
consteval std::array<CheckNode, kParityBits> derive_check_nodes() {
  std::array<CheckNode, kParityBits> nodes{};
  for (int bit = 0; bit < kCodeLength; ++bit) {
    for (int check : kBitChecks[static_cast<std::size_t>(bit)]) {
      auto& node = nodes[static_cast<std::size_t>(check)];
      if (node.count >= static_cast<int>(node.bits.size())) {
        throw "JS8 parity check degree exceeds the node capacity";
      }
      node.bits[static_cast<std::size_t>(node.count++)] = bit;
    }
  }
  return nodes;
}
inline constexpr auto kCheckNodes = derive_check_nodes();

consteval bool check_ldpc_graph() {
  for (auto const& node : kCheckNodes)
    if (node.count < 5 || node.count > 7) return false;
  return true;
}
static_assert(check_ldpc_graph(), "invalid JS8 LDPC graph");


// Generator parity rows: 87 rows of 87 bits, packed as 22 hex digits each.
// The final bit of the last nibble is unused.
inline constexpr std::array<std::string_view, kPayloadBits> kParityRows = {
        "23bba830e23b6b6f50982e", "1f8e55da218c5df3309052", "ca7b3217cd92bd59a5ae20",
        "56f78313537d0f4382964e", "6be396b5e2e819e373340c", "293548a138858328af4210",
        "cb6c6afcdc28bb3f7c6e86", "3f2a86f5c5bd225c961150", "849dd2d63673481860f62c",
        "56cdaec6e7ae14b43feeee", "04ef5cfa3766ba778f45a4", "c525ae4bd4f627320a3974",
        "41fd9520b2e4abeb2f989c", "7fb36c24085a34d8c1dbc4", "40fc3e44bb7d2bb2756e44",
        "d38ab0a1d2e52a8ec3bc76", "3d0f929ef3949bd84d4734", "45d3814f504064f80549ae",
        "f14dbf263825d0bd04b05e", "db714f8f64e8ac7af1a76e", "8d0274de71e7c1a8055eb0",
        "51f81573dd4049b082de14", "d8f937f31822e57c562370", "b6537f417e61d1a7085336",
        "ecbd7c73b9cd34c3720c8a", "3d188ea477f6fa41317a4e", "1ac4672b549cd6dba79bcc",
        "a377253773ea678367c3f6", "0dbd816fba1543f721dc72", "ca4186dd44c3121565cf5c",
        "29c29dba9c545e267762fe", "1616d78018d0b4745ca0f2", "fe37802941d66dde02b99c",
        "a9fa8e50bcb032c85e3304", "83f640f1a48a8ebc0443ea", "3776af54ccfbae916afde6",
        "a8fc906976c35669e79ce0", "f08a91fb2e1f78290619a8", "cc9da55fe046d0cb3a770c",
        "d36d662a69ae24b74dcbd8", "40907b01280f03c0323946", "d037db825175d851f3af00",
        "1bf1490607c54032660ede", "0af7723161ec223080be86", "eca9afa0f6b01d92305edc",
        "7a8dec79a51e8ac5388022", "9059dfa2bb20ef7ef73ad4", "6abb212d9739dfc02580f2",
        "f6ad4824b87c80ebfce466", "d747bfc5fd65ef70fbd9bc", "612f63acc025b6ab476f7c",
        "05209a0abb530b9e7e34b0", "45b7ab6242b77474d9f11a", "6c280d2a0523d9c4bc5946",
        "f1627701a2d692fd9449e6", "8d9071b7e7a6a2eed6965e", "bf4f56e073271f6ab4bf80",
        "c0fc3ec4fb7d2bb2756644", "57da6d13cb96a7689b2790", "a9fa2eefa6f8796a355772",
        "164cc861bdd803c547f2ac", "cc6de59755420925f90ed2", "a0c0033a52ab6299802fd2",
        "b274db8abd3c6f396ea356", "97d4169cb33e7435718d90", "81cfc6f18c35b1e1f17114",
        "481a2a0df8a23583f82d6c", "081c29a10d468ccdbcecb6", "2c4142bf42b01e71076acc",
        "a6573f3dc8b16c9d19f746", "c87af9a5d5206abca532a8", "012dee2198eba82b19a1da",
        "b1ca4ea2e3d173bad4379c", "b33ec97be83ce413f9acc8", "5b0f7742bca86b8012609a",
        "37d8e0af9258b9e8c5f9b2", "35ad3fb0faeb5f1b0c30dc", "6114e08483043fd3f38a8a",
        "cd921fdf59e882683763f6", "95e45ecd0135aca9d6e6ae", "2e547dd7a05f6597aac516",
        "14cd0f642fc0c5fe3a65ca", "3a0a1dfd7eee29c2e827e0", "c8b5dffc335095dcdcaf2a",
        "3dd01a59d86310743ec752", "8abdb889efbe39a510a118", "3f231f212055371cf3e2a2"
};

// Per-submode signal profile. The template parameters are all functional
// protocol values: symbol rate, frame length, downsampling geometry, and the
// search/taper constants that define each JS8 speed level.
template <int EventMode, int BitIndex, protocol::CostasType CostasKind,
          int SymbolSamples, int TxSeconds, int DownSamplesPerSymbol,
          int TaperLength, int SearchRadius, int StartTenths,
          int BaselineTenths, int AzNumerator, int AzDenominator>
struct ModeProfile {
  static constexpr int event_mode = EventMode;
  static constexpr int bit_index = BitIndex;
  static constexpr auto costas_type = CostasKind;

  static constexpr int nsps = SymbolSamples;
  static constexpr int tx_seconds = TxSeconds;
  static constexpr int downsample_sps = DownSamplesPerSymbol;
  static constexpr int taper_span = TaperLength;
  static constexpr int search_radius = SearchRadius;
  static constexpr float start_seconds = StartTenths / 10.0f;
  static constexpr float baseline_offset = BaselineTenths / 10.0f;
  static constexpr float az =
      (12000.0f / nsps) * AzNumerator / AzDenominator;

  static constexpr int nmax = tx_seconds * 12000;
  // Subtraction window in samples: 3 symbols at Normal, Fast and Slow (best
  // measured on crowded sets); Turbo keeps 1,400 (already 2.33 symbols, and
  // shorter or longer measured no better); mode 8 is untested and keeps 1,400.
  static constexpr int filter_length =
      (event_mode == 0 || event_mode == 1 || event_mode == 4)
          ? kFilterSymbols * nsps
          : kFilterLength;
  static constexpr int fft_span = nsps * kOversample;
  static constexpr int time_step = nsps / kStepsPerSymbol;
  static constexpr int spectra = nmax / time_step - 3;
  static constexpr int downsample_factor = nsps / downsample_sps;
  static constexpr int quarter_symbol = downsample_sps / 4;
  static constexpr int ds_fft_span = nsps * taper_span;
  static constexpr int ds_fft_out = ds_fft_span / downsample_factor;
  static constexpr int payload_samples = kChannelSymbols * downsample_sps;
  static constexpr float step_seconds = time_step / 12000.0f;
  static constexpr int start_step =
      static_cast<int>(start_seconds / step_seconds);
  static constexpr float bin_hz = 12000.0f / fft_span;
};

using ProfileA = ModeProfile<0, 0, protocol::CostasType::Original,
                              1920, 15, 32, 100, 62, 5, 400, 64, 100>;
using ProfileB = ModeProfile<1, 1, protocol::CostasType::Modified,
                              1200, 10, 20, 100, 144, 2, 390, 8, 10>;
using ProfileC = ModeProfile<2, 2, protocol::CostasType::Modified,
                              600, 6, 12, 120, 172, 1, 380, 6, 10>;
using ProfileE = ModeProfile<4, 3, protocol::CostasType::Modified,
                              3840, 30, 32, 94, 32, 5, 420, 64, 100>;
using ProfileI = ModeProfile<8, 4, protocol::CostasType::Modified,
                              384, 4, 12, 125, 250, 1, 360, 64, 100>;

}  // namespace js8core::decoder::data
