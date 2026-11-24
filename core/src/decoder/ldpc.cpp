#include "ldpc.hpp"

#include <algorithm>
#include <array>
#include <bit>
#include <cmath>
#include <cstddef>
#include <cstdint>
#include <limits>
#include <numeric>
#include <string>
#include <vector>

#include "decoder_tables.hpp"

namespace js8core::decoder {
namespace {

constexpr std::uint8_t kInvalidWord = 0xFF;

constexpr std::array<std::uint8_t, 256> build_word_table() {
  std::array<std::uint8_t, 256> table{};
  // Manual fill: std::array::fill is not constexpr in older NDK libc++.
  for (std::size_t i = 0; i < table.size(); ++i) table[i] = kInvalidWord;
  for (std::size_t i = 0; i < data::kAlphabet.size(); ++i) {
    table[static_cast<std::uint8_t>(data::kAlphabet[i])] =
        static_cast<std::uint8_t>(i);
  }
  return table;
}

constexpr auto kWordTable = build_word_table();

constexpr int hex_digit(char c) {
  if (c >= '0' && c <= '9') return c - '0';
  if (c >= 'a' && c <= 'f') return c - 'a' + 10;
  if (c >= 'A' && c <= 'F') return c - 'A' + 10;
  return 0;
}

// The generator parity rows are stored as hex text. Expand them into a packed
// bit matrix once at compile time.
constexpr std::size_t kParityWords =
    (data::kPayloadBits * data::kPayloadBits + 63) / 64;

constexpr std::array<std::uint64_t, kParityWords> build_parity_matrix() {
  std::array<std::uint64_t, kParityWords> matrix{};
  for (std::size_t row = 0; row < data::kPayloadBits; ++row) {
    std::size_t column = 0;
    for (char const c : data::kParityRows[row]) {
      int const value = hex_digit(c);
      for (int const mask : {8, 4, 2, 1}) {
        if (column >= data::kPayloadBits) break;
        if ((value & mask) != 0) {
          std::size_t const index = row * data::kPayloadBits + column;
          matrix[index / 64] |= (std::uint64_t{1} << (index % 64));
        }
        ++column;
      }
    }
  }
  return matrix;
}

constexpr auto kParityMatrix = build_parity_matrix();

constexpr int kMaxCheckDegree = 7;
constexpr int kMaxBitDegree = 3;

}  // namespace

std::uint8_t word_for(char c) {
  return kWordTable[static_cast<std::uint8_t>(c)];
}

std::uint16_t crc12(std::uint8_t const* data, std::size_t size) {
  std::uint16_t remainder = 0;
  for (std::size_t i = 0; i < size; ++i) {
    for (int bit = 7; bit >= 0; --bit) {
      bool const feedback = (remainder & 0x0800u) != 0;
      remainder = static_cast<std::uint16_t>(
          ((remainder << 1) | ((data[i] >> bit) & 1u)) & 0x0FFFu);
      if (feedback) remainder ^= 0x0C06u;
    }
  }
  return static_cast<std::uint16_t>(remainder ^ 42u);
}

bool crc_ok(std::array<std::int8_t, 87> const& payload) {
  std::array<std::uint8_t, 11> bytes{};
  for (std::size_t i = 0; i < payload.size(); ++i) {
    if (payload[i] != 0) {
      bytes[i / 8] |= static_cast<std::uint8_t>(1u << (7 - (i % 8)));
    }
  }

  std::uint16_t const received =
      static_cast<std::uint16_t>(((bytes[9] & 0x1Fu) << 7) | (bytes[10] >> 1));

  bytes[9] &= 0xE0u;
  bytes[10] = 0;

  return received == crc12(bytes.data(), bytes.size());
}

std::string decode_message(std::array<std::int8_t, 87> const& payload) {
  if (!crc_ok(payload)) return {};

  std::string text;
  text.reserve(data::kFrameCharacters);
  for (int word = 0; word < data::kFrameCharacters; ++word) {
    std::uint8_t value = 0;
    for (int bit = 0; bit < 6; ++bit) {
      value = static_cast<std::uint8_t>(
          (value << 1) | (payload[word * 6 + bit] != 0 ? 1u : 0u));
    }
    text.push_back(data::kAlphabet[value]);
  }
  return text;
}

int belief_propagation(std::array<float, 174> const& llr,
                       std::array<std::int8_t, 87>& payload,
                       std::array<std::int8_t, 174>& codeword,
                       int max_iterations, bool early_abort,
                       std::array<float, 8 * 58> const* tone_scores) {
  using namespace data;

  // Bit readings used this round; rebuilt from tone_scores when given.
  std::array<float, kCodeLength> channel = llr;

  // Messages travelling from bits to checks, and from checks to bits.
  std::array<std::array<float, kMaxBitDegree>, kCodeLength> bit_to_check{};
  std::array<std::array<float, kMaxCheckDegree>, kParityBits> check_to_bit{};
  std::array<std::array<float, kMaxCheckDegree>, kParityBits> check_tanh{};
  std::array<float, kCodeLength> posterior{};
  std::array<int, kParityBits> syndrome{};

  int stalled = 0;
  int previous_failures = 0;

  // Damping: every message, in both directions, keeps this share of its value
  // from the round before (messages start at zero). Curbs round-to-round
  // swings; 0.1 measured best on JS8's code (0.5 loses decodes).
  constexpr float kDamping = 0.1f;

  for (int iteration = 0; iteration <= max_iterations; ++iteration) {
    // Iterative demapping: the decoder's belief in each bit (the sum of its
    // check messages) weights the tones carrying that bit. A bit's own
    // belief is then removed from its reading: every tone on the bit's "1"
    // side carries it, so subtracting it once is exact.
    if (tone_scores != nullptr && iteration > 0) {
      for (int symbol = 0; symbol < 58; ++symbol) {
        std::array<float, 3> belief{};
        for (int b = 0; b < 3; ++b) {
          for (int k = 0; k < kMaxBitDegree; ++k) {
            belief[static_cast<std::size_t>(b)] +=
                bit_to_check[static_cast<std::size_t>(3 * symbol + b)]
                            [static_cast<std::size_t>(k)];
          }
        }
        std::array<float, 8> weight{};
        float top = -std::numeric_limits<float>::infinity();
        for (int tone = 0; tone < 8; ++tone) {
          float value = (*tone_scores)[static_cast<std::size_t>(8 * symbol + tone)];
          for (int b = 0; b < 3; ++b) {
            if ((tone >> (2 - b)) & 1) value += belief[static_cast<std::size_t>(b)];
          }
          weight[static_cast<std::size_t>(tone)] = value;
          top = std::max(top, value);
        }
        for (auto& value : weight) value = std::exp(value - top);
        for (int b = 0; b < 3; ++b) {
          float one = 0.0f;
          float zero = 0.0f;
          for (int tone = 0; tone < 8; ++tone) {
            ((tone >> (2 - b)) & 1 ? one : zero) += weight[static_cast<std::size_t>(tone)];
          }
          channel[static_cast<std::size_t>(3 * symbol + b)] = std::clamp(
              std::log((one + 1e-30f) / (zero + 1e-30f)) -
                  belief[static_cast<std::size_t>(b)],
              -20.0f, 20.0f);
        }
      }
    }

    // Posterior log-likelihood ratio for every coded bit.
    for (int bit = 0; bit < kCodeLength; ++bit) {
      float value = channel[static_cast<std::size_t>(bit)];
      for (int k = 0; k < kMaxBitDegree; ++k) {
        value += bit_to_check[static_cast<std::size_t>(bit)]
                              [static_cast<std::size_t>(k)];
      }
      posterior[static_cast<std::size_t>(bit)] = value;
    }

    for (int bit = 0; bit < kCodeLength; ++bit) {
      codeword[static_cast<std::size_t>(bit)] =
          posterior[static_cast<std::size_t>(bit)] > 0.0f ? 1 : 0;
    }

    int failed_checks = 0;
    for (int check = 0; check < kParityBits; ++check) {
      CheckNode const& node = kCheckNodes[static_cast<std::size_t>(check)];
      int parity = 0;
      for (int j = 0; j < node.count; ++j) {
        parity += codeword[static_cast<std::size_t>(node.bits[j])];
      }
      syndrome[static_cast<std::size_t>(check)] = parity;
      if ((parity & 1) != 0) ++failed_checks;
    }

    if (failed_checks == 0) {
      std::copy(codeword.begin() + kParityBits, codeword.end(),
                payload.begin());

      int errors = 0;
      for (int bit = 0; bit < kCodeLength; ++bit) {
        float const sign = (2 * codeword[static_cast<std::size_t>(bit)] - 1);
        if (sign * llr[static_cast<std::size_t>(bit)] < 0.0f) ++errors;
      }
      return errors;
    }

    if (iteration > 0) {
      int const delta = failed_checks - previous_failures;
      stalled = delta < 0 ? 0 : stalled + 1;
      if (early_abort && stalled >= 5 && iteration >= 10 && failed_checks > 15) return -1;
    }
    previous_failures = failed_checks;

    // Bit-to-check messages: posterior minus the incoming check message.
    for (int check = 0; check < kParityBits; ++check) {
      CheckNode const& node = kCheckNodes[static_cast<std::size_t>(check)];
      for (int j = 0; j < node.count; ++j) {
        int const bit = node.bits[j];
        float message = posterior[static_cast<std::size_t>(bit)];
        for (int k = 0; k < kMaxBitDegree; ++k) {
          if (kBitChecks[static_cast<std::size_t>(bit)]
                       [static_cast<std::size_t>(k)] == check) {
            message -= bit_to_check[static_cast<std::size_t>(bit)]
                                    [static_cast<std::size_t>(k)];
          }
        }
        float& sent = check_to_bit[static_cast<std::size_t>(check)]
                                  [static_cast<std::size_t>(j)];
        sent = kDamping * sent + (1.0f - kDamping) * message;
      }
    }

    for (int check = 0; check < kParityBits; ++check) {
      CheckNode const& node = kCheckNodes[static_cast<std::size_t>(check)];
      for (int j = 0; j < node.count; ++j) {
        check_tanh[static_cast<std::size_t>(check)]
                  [static_cast<std::size_t>(j)] = std::tanh(
                      -check_to_bit[static_cast<std::size_t>(check)]
                                   [static_cast<std::size_t>(j)] /
                      2.0f);
      }
    }

    // Check-to-bit messages: product of the other incoming tanh terms.
    for (int bit = 0; bit < kCodeLength; ++bit) {
      for (int k = 0; k < kMaxBitDegree; ++k) {
        int const check =
            kBitChecks[static_cast<std::size_t>(bit)]
                      [static_cast<std::size_t>(k)];
        CheckNode const& node = kCheckNodes[static_cast<std::size_t>(check)];
        float product = 1.0f;
        for (int j = 0; j < node.count; ++j) {
          if (node.bits[j] != bit) {
            product *= check_tanh[static_cast<std::size_t>(check)]
                                 [static_cast<std::size_t>(j)];
          }
        }
        // Saturated LLRs can yield tanh == +/-1 exactly. atanh(+/-1)
        // produces infinities which poison subsequent message passes.
        constexpr float kTanhLimit = 1.0f - 1.0e-6f;
        product = std::clamp(product, -kTanhLimit, kTanhLimit);
        float& sent = bit_to_check[static_cast<std::size_t>(bit)]
                                  [static_cast<std::size_t>(k)];
        sent = kDamping * sent + (1.0f - kDamping) * (2.0f * std::atanh(-product));
      }
    }
  }

  return -1;
}

bool parity_bit(std::size_t row, std::size_t column) {
  std::size_t const index = row * data::kPayloadBits + column;
  return ((kParityMatrix[index / 64] >> (index % 64)) & 1u) != 0;
}

// A 174-bit codeword row as three 64-bit words. Replaces std::bitset so that
// a candidate's cost can visit only the bits that differ (in increasing
// position order, so the sum is the same as a bit-by-bit loop) instead of
// testing all 174. Measured 2026-11-19: scoring took about 60% of ordered
// statistics decoding time on a crowded Fast band.
class CodewordBits {
 public:
  void set(std::size_t k) { word_[k / 64] |= std::uint64_t{1} << (k % 64); }
  bool test(std::size_t k) const { return ((word_[k / 64] >> (k % 64)) & 1u) != 0; }
  CodewordBits& operator^=(CodewordBits const& other) {
    for (std::size_t w = 0; w < kWords; ++w) word_[w] ^= other.word_[w];
    return *this;
  }
  friend CodewordBits operator^(CodewordBits a, CodewordBits const& b) { return a ^= b; }
  friend bool operator==(CodewordBits const& a, CodewordBits const& b) { return a.word_ == b.word_; }
  // Calls visit(k) for every set bit k, lowest first.
  template <typename Visit>
  void for_each_set(Visit&& visit) const {
    for (std::size_t w = 0; w < kWords; ++w) {
      for (std::uint64_t bits = word_[w]; bits != 0; bits &= bits - 1) {
        visit(w * 64 + static_cast<std::size_t>(std::countr_zero(bits)));
      }
    }
  }

 private:
  static constexpr std::size_t kWords = (data::kCodeLength + 63) / 64;
  std::array<std::uint64_t, kWords> word_{};
};

// Ordered statistics decoding (see Lin and Costello, Error Control Coding,
// 2nd ed., 2004, as cited by Franke, Somerville and Taylor, QEX July/August
// 2020). The generator rows
// are reduced over the most reliable independent positions; candidate
// codewords re-encode the hard decisions there with up to `order` flips, and
// the candidate with the smallest discrepancy (sum of |llr| over bits that
// disagree with the hard decisions) wins.
int ordered_statistics_decode(std::array<float, 174> const& llr,
                              std::array<std::int8_t, 87>& payload,
                              std::array<std::int8_t, 174>& codeword,
                              int order, float* discrepancy, bool filtered,
                              float* runner_up,
                              std::array<float, 174> const* order_by) {
  using namespace data;
  using Row = CodewordBits;
  auto const& reliability = order_by ? *order_by : llr;

  // Generator row r: message bit r contributes to parity bit p when
  // parity_bit(p, r), and to its own systematic position 87 + r.
  static std::array<Row, kPayloadBits> const generator = [] {
    std::array<Row, kPayloadBits> rows{};
    for (std::size_t r = 0; r < kPayloadBits; ++r) {
      for (std::size_t p = 0; p < kPayloadBits; ++p) {
        if (parity_bit(p, r)) rows[r].set(p);
      }
      rows[r].set(kPayloadBits + r);
    }
    return rows;
  }();

  std::array<int, kCodeLength> by_reliability{};
  std::iota(by_reliability.begin(), by_reliability.end(), 0);
  std::stable_sort(by_reliability.begin(), by_reliability.end(), [&reliability](int a, int b) {
    return std::abs(reliability[static_cast<std::size_t>(a)]) >
           std::abs(reliability[static_cast<std::size_t>(b)]);
  });

  // `hard`: the starting bit values the search flips from. `received`: the
  // hard decisions candidates are scored against. The same without order_by.
  Row hard;
  Row received;
  for (std::size_t k = 0; k < kCodeLength; ++k) {
    if (reliability[k] > 0.0f) hard.set(k);
    if (llr[k] > 0.0f) received.set(k);
  }

  // Gauss-Jordan elimination, taking pivot positions in reliability order.
  std::array<Row, kPayloadBits> rows = generator;
  std::array<int, kPayloadBits> pivot{};
  std::size_t rank = 0;
  for (int position : by_reliability) {
    if (rank == kPayloadBits) break;
    std::size_t const column = static_cast<std::size_t>(position);
    std::size_t found = rank;
    while (found < kPayloadBits && !rows[found].test(column)) ++found;
    if (found == kPayloadBits) continue;
    std::swap(rows[rank], rows[found]);
    for (std::size_t r = 0; r < kPayloadBits; ++r) {
      if (r != rank && rows[r].test(column)) rows[r] ^= rows[rank];
    }
    pivot[rank] = position;
    ++rank;
  }
  if (rank != kPayloadBits) return -1;

  // Order 0: re-encode the hard decisions on the pivot positions.
  Row base;
  for (std::size_t r = 0; r < kPayloadBits; ++r) {
    if (hard.test(static_cast<std::size_t>(pivot[r]))) base ^= rows[r];
  }

  auto cost = [&llr, &received](Row const& candidate) {
    float total = 0.0f;
    (candidate ^ received).for_each_set([&](std::size_t k) { total += std::abs(llr[k]); });
    return total;
  };

  Row best = base;
  float best_cost = cost(base);
  float second_cost = std::numeric_limits<float>::max();
  auto consider = [&](Row const& candidate, float c) {
    if (candidate == best) return;
    if (c < best_cost) {
      second_cost = best_cost;
      best_cost = c;
      best = candidate;
    } else if (c < second_cost) {
      second_cost = c;
    }
  };
  if (order >= 1) {
    for (std::size_t a = 0; a < kPayloadBits; ++a) {
      Row const one = base ^ rows[a];
      float const c1 = cost(one);
      consider(one, c1);
      if (order < 2) continue;
      for (std::size_t b = a + 1; b < kPayloadBits; ++b) {
        Row const two = one ^ rows[b];
        float const c2 = cost(two);
        consider(two, c2);
      }
    }
  }

  // Preprocessing rules of Wu and Hadjicostis (IEEE Trans. Inf. Theory 53(1),
  // 2007, section III, Theorem 2): candidates with order + 1 flips are fully
  // costed only if they disagree with the hard decisions on at most theta of
  // the pivot positions plus the t most reliable other positions; candidates
  // with order + 2 flips only if they disagree on at most 1 of the tau most
  // reliable other positions. Settings from Theorem 2 for K = R = 87, w = 2:
  // t = R / 2 = 43, theta = 2 (w + 3) = 10, tau = 3 log2(K / w) = 16.
  if (filtered && order == 2) {
    constexpr int kT = 43;
    constexpr int kTheta = 10;
    constexpr int kTau = 16;

    // The most reliable non-pivot positions, in reliability order.
    Row is_pivot;
    for (int p : pivot) is_pivot.set(static_cast<std::size_t>(p));
    std::array<int, kT> reliable{};
    int found = 0;
    for (int position : by_reliability) {
      if (found == kT) break;
      if (!is_pivot.test(static_cast<std::size_t>(position))) reliable[found++] = position;
    }

    // Each row and the order-0 mismatch, packed onto those positions: bit i
    // is position reliable[i]. The low kTau bits are the tau most reliable.
    auto pack = [&reliable](Row const& row) {
      std::uint64_t packed = 0;
      for (int i = 0; i < kT; ++i) {
        if (row.test(static_cast<std::size_t>(reliable[i]))) packed |= std::uint64_t{1} << i;
      }
      return packed;
    };
    std::array<std::uint64_t, kPayloadBits> packed_row{};
    for (std::size_t r = 0; r < kPayloadBits; ++r) packed_row[r] = pack(rows[r]);
    std::uint64_t const packed_mismatch = pack(base ^ hard);

    // Order 3, rule 1: three pivot disagreements plus at most theta - 3 on
    // the t positions.
    for (std::size_t a = 0; a < kPayloadBits; ++a) {
      for (std::size_t b = a + 1; b < kPayloadBits; ++b) {
        std::uint64_t const ab = packed_mismatch ^ packed_row[a] ^ packed_row[b];
        for (std::size_t c = b + 1; c < kPayloadBits; ++c) {
          if (std::popcount(ab ^ packed_row[c]) > kTheta - 3) continue;
          Row const three = base ^ rows[a] ^ rows[b] ^ rows[c];
          consider(three, cost(three));
        }
      }
    }

    // Order 4, rule 2, skipped when the search is ordered by refined values
    // (order_by): measured 2026-11-19 on a crowded band, it then scores about
    // 18 times as many candidates (the refined starting values agree with more
    // of the reliable positions), for about 9 more messages in 2,975 (Zhang et
    // al. 2023: a refined order m - 1 search matches a plain order m).
    if (order_by == nullptr) {
      // Order 4, rule 2: pair sums matched through a table keyed by their tau
      // bits; each set {a < b < c < e} is found once as (a, b) with (c, e).
      std::uint32_t constexpr kTauMask = (1u << kTau) - 1u;
      std::vector<std::uint32_t> bucket_start((1u << kTau) + 1u, 0);
      std::vector<std::uint16_t> pair_c;
      std::vector<std::uint16_t> pair_e;
      std::size_t const pairs = kPayloadBits * (kPayloadBits - 1) / 2;
      std::vector<std::uint32_t> pair_key;
      pair_key.reserve(pairs);
      for (std::size_t c = 0; c < kPayloadBits; ++c) {
        for (std::size_t e = c + 1; e < kPayloadBits; ++e) {
          std::uint32_t const key = static_cast<std::uint32_t>((packed_row[c] ^ packed_row[e]) & kTauMask);
          pair_key.push_back(key);
          ++bucket_start[key + 1];
        }
      }
      for (std::size_t k = 1; k < bucket_start.size(); ++k) bucket_start[k] += bucket_start[k - 1];
      pair_c.resize(pairs);
      pair_e.resize(pairs);
      {
        std::vector<std::uint32_t> fill(bucket_start.begin(), bucket_start.end() - 1);
        std::size_t index = 0;
        for (std::size_t c = 0; c < kPayloadBits; ++c) {
          for (std::size_t e = c + 1; e < kPayloadBits; ++e) {
            std::uint32_t const slot = fill[pair_key[index++]]++;
            pair_c[slot] = static_cast<std::uint16_t>(c);
            pair_e[slot] = static_cast<std::uint16_t>(e);
          }
        }
      }
      for (std::size_t a = 0; a < kPayloadBits; ++a) {
        for (std::size_t b = a + 1; b < kPayloadBits; ++b) {
          std::uint32_t const ab = static_cast<std::uint32_t>(
              (packed_mismatch ^ packed_row[a] ^ packed_row[b]) & kTauMask);
          for (int flip = -1; flip < kTau; ++flip) {
            std::uint32_t const key = flip < 0 ? ab : ab ^ (1u << flip);
            for (std::uint32_t s = bucket_start[key]; s < bucket_start[key + 1]; ++s) {
              if (pair_c[s] <= b) continue;
              Row const four = base ^ rows[a] ^ rows[b] ^ rows[pair_c[s]] ^ rows[pair_e[s]];
              consider(four, cost(four));
            }
          }
        }
      }
    }
  }

  int errors = 0;
  for (std::size_t k = 0; k < kCodeLength; ++k) {
    codeword[k] = best.test(k) ? 1 : 0;
    if (best.test(k) != received.test(k)) ++errors;
  }
  std::copy(codeword.begin() + kParityBits, codeword.end(), payload.begin());
  if (discrepancy) *discrepancy = best_cost;
  if (runner_up) *runner_up = second_cost;
  return errors;
}

void refine_reliabilities(std::array<float, 174> const& llr, int rounds,
                          float feedback, std::array<float, 174>& refined,
                          std::array<float, 8 * 58> const* tone_scores) {
  using namespace data;

  // Bit readings used this round; rebuilt from tone_scores when given.
  std::array<float, kCodeLength> channel = llr;

  // incoming[bit][k]: the message from the bit's k-th check to the bit.
  std::array<std::array<float, kMaxBitDegree>, kCodeLength> incoming{};
  std::array<std::array<float, kMaxCheckDegree>, kParityBits> check_tanh{};

  for (int round = 0; round < rounds; ++round) {
    // Iterative demapping, as in belief_propagation, with each bit's belief
    // the damped sum of its check messages.
    if (tone_scores != nullptr && round > 0) {
      for (int symbol = 0; symbol < 58; ++symbol) {
        std::array<float, 3> belief{};
        for (int b = 0; b < 3; ++b) {
          for (float message : incoming[static_cast<std::size_t>(3 * symbol + b)]) {
            belief[static_cast<std::size_t>(b)] += message;
          }
          belief[static_cast<std::size_t>(b)] *= feedback;
        }
        std::array<float, 8> weight{};
        float top = -std::numeric_limits<float>::infinity();
        for (int tone = 0; tone < 8; ++tone) {
          float value = (*tone_scores)[static_cast<std::size_t>(8 * symbol + tone)];
          for (int b = 0; b < 3; ++b) {
            if ((tone >> (2 - b)) & 1) value += belief[static_cast<std::size_t>(b)];
          }
          weight[static_cast<std::size_t>(tone)] = value;
          top = std::max(top, value);
        }
        for (auto& value : weight) value = std::exp(value - top);
        for (int b = 0; b < 3; ++b) {
          float one = 0.0f;
          float zero = 0.0f;
          for (int tone = 0; tone < 8; ++tone) {
            ((tone >> (2 - b)) & 1 ? one : zero) += weight[static_cast<std::size_t>(tone)];
          }
          channel[static_cast<std::size_t>(3 * symbol + b)] = std::clamp(
              std::log((one + 1e-30f) / (zero + 1e-30f)) -
                  belief[static_cast<std::size_t>(b)],
              -20.0f, 20.0f);
        }
      }
    }

    // Bit-to-check messages: the bit's own value plus the scaled messages
    // from its other checks.
    for (int check = 0; check < kParityBits; ++check) {
      CheckNode const& node = kCheckNodes[static_cast<std::size_t>(check)];
      for (int j = 0; j < node.count; ++j) {
        std::size_t const bit = static_cast<std::size_t>(node.bits[j]);
        float others = 0.0f;
        for (int k = 0; k < kMaxBitDegree; ++k) {
          if (kBitChecks[bit][static_cast<std::size_t>(k)] != check) {
            others += incoming[bit][static_cast<std::size_t>(k)];
          }
        }
        check_tanh[static_cast<std::size_t>(check)][static_cast<std::size_t>(j)] =
            std::tanh(-(channel[bit] + feedback * others) / 2.0f);
      }
    }

    // Check-to-bit messages: product of the other incoming tanh terms.
    for (int bit = 0; bit < kCodeLength; ++bit) {
      for (int k = 0; k < kMaxBitDegree; ++k) {
        int const check = kBitChecks[static_cast<std::size_t>(bit)][static_cast<std::size_t>(k)];
        CheckNode const& node = kCheckNodes[static_cast<std::size_t>(check)];
        float product = 1.0f;
        for (int j = 0; j < node.count; ++j) {
          if (node.bits[j] != bit) {
            product *= check_tanh[static_cast<std::size_t>(check)][static_cast<std::size_t>(j)];
          }
        }
        constexpr float kTanhLimit = 1.0f - 1.0e-6f;
        product = std::clamp(product, -kTanhLimit, kTanhLimit);
        incoming[static_cast<std::size_t>(bit)][static_cast<std::size_t>(k)] =
            2.0f * std::atanh(-product);
      }
    }
  }

  for (std::size_t bit = 0; bit < kCodeLength; ++bit) {
    float sum = 0.0f;
    for (float message : incoming[bit]) sum += message;
    refined[bit] = channel[bit] + feedback * sum;
  }
}

}  // namespace js8core::decoder
