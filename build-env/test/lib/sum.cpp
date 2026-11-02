#include <cstdio>
#include <string>
#include "js8core/protocol/varicode.hpp"
using namespace js8core::protocol::varicode;
int main(int c, char** v) {
  std::printf("%s %s\n", checksum16(v[1]).c_str(), checksum32(v[1]).c_str());
  return 0;
}
