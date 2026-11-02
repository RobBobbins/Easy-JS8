#include <cstdio>
#include <string>
#include "js8core/protocol/varicode.hpp"
using namespace js8core::protocol::varicode;
int main(int c, char** v) {
  if (c == 4) { std::printf("%s %s
", checksum16(v[1]).c_str(), checksum32(v[1]).c_str()); return 0; }
  if (c == 2) {
    int n = 0;
    auto f = pack_compound_message(v[1], &n);
    std::printf("C[%s] n=%d\n", f.c_str(), n);
    return 0;
  }
  std::string to, cmd, num; bool comp = false; int n = 0;
  auto f = pack_directed_message(v[1], v[2], &to, &comp, &cmd, &num, &n);
  std::printf("[%s] to=%s comp=%d cmd=%s n=%d\n", f.c_str(), to.c_str(), comp, cmd.c_str(), n);
  std::uint8_t t = 0;
  for (auto const& p : unpack_directed_message(f, &t)) std::printf(" <%s>", p.c_str());
  std::printf("\n");
}
