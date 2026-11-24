#pragma once

#include <array>
#include <cstddef>
#include <cstdint>
#include <string>

namespace js8core::decoder {

// Maps a six-bit JS8 alphabet character to its word value (0-63), or 0xFF when
// the character is not part of the alphabet.
std::uint8_t word_for(char c);

// CRC-12 used by the JS8 message layer: polynomial 0xC06 over the 11-byte
// block (which already contains the twelve trailing zero bits), final XOR 42.
std::uint16_t crc12(std::uint8_t const* data, std::size_t size);

bool crc_ok(std::array<std::int8_t, 87> const& payload);

// Reconstructs the twelve-character frame text from a CRC-valid payload.
std::string decode_message(std::array<std::int8_t, 87> const& payload);

// Sum-product belief propagation over the 174/87 LDPC code. Returns the number
// of hard-decision disagreements for a valid codeword, or -1 when decoding
// does not converge.
// With `tone_scores` (8 tone log-likelihoods for each of the 58 data symbols,
// tone number = the symbol's 3 bits, first bit highest), every round after
// the first rebuilds each bit's reading from the tone scores, each tone
// weighted by the decoder's current belief in the other two bits of its
// label (iterative demapping: Valenti and Cheng 2005; Ferrett and Valenti
// 2017). `llr` must be the same reading with equal beliefs; hard-decision
// disagreements are still counted against it.
int belief_propagation(std::array<float, 174> const& llr,
                       std::array<std::int8_t, 87>& payload,
                       std::array<std::int8_t, 174>& codeword,
                       int max_iterations = 60, bool early_abort = true,
                       std::array<float, 8 * 58> const* tone_scores = nullptr);

// Ordered statistics decoding of the 174/87 code with up to `order` (0 to 2)
// flipped reliable bits. Always returns the closest candidate found: the
// number of bits that disagree with the hard decisions, or -1 on failure.
// `discrepancy` is the sum of |llr| over those bits. The caller must check
// the CRC and judge `discrepancy` before trusting the result.
// With `filtered` and order 2, it also tries 3 and 4 flips, fully costing
// only candidates that pass the Wu and Hadjicostis preprocessing rules.
// `runner_up`, when given, receives the discrepancy of the second-closest
// candidate found (Forney's test compares the best answer with it).
// `order_by`, when given, picks the reliable positions and their starting
// bit values in place of `llr`; candidates are still scored against `llr`
// (Fossorier, IEEE JSAC 19(5), 2001). With `order_by`, the 4-flip search is
// skipped (see ldpc.cpp).
int ordered_statistics_decode(std::array<float, 174> const& llr,
                              std::array<std::int8_t, 87>& payload,
                              std::array<std::int8_t, 174>& codeword,
                              int order, float* discrepancy,
                              bool filtered = false,
                              float* runner_up = nullptr,
                              std::array<float, 174> const* order_by = nullptr);

// A few rounds of belief propagation from `llr`, with the check messages
// multiplied by `feedback` (below 1), giving each bit's updated value in
// `refined`. Used to order the last-try search (Zhang, Yue, Li, Vucetic,
// arXiv 2306.00443, 2023).
// With `tone_scores` (as for belief_propagation), every round after the first
// rebuilds the bit readings from the tone scores, each tone weighted by the
// damped check beliefs in its other two bits (iterative demapping).
void refine_reliabilities(std::array<float, 174> const& llr, int rounds,
                          float feedback, std::array<float, 174>& refined,
                          std::array<float, 8 * 58> const* tone_scores = nullptr);

// True when the generator parity matrix has a one at (row, column).
bool parity_bit(std::size_t row, std::size_t column);

}  // namespace js8core::decoder
