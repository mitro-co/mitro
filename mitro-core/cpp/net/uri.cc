#include "net/uri.h"

#include "base/strings/stringprintf.h"

namespace net {

std::string BuildUri(const std::string& protocol,
                     const std::string& host,
                     const std::string& path) {
  return base::StringPrintf("%s://%s/%s",
                            protocol.c_str(), host.c_str(), path.c_str());
}

}  // namespace net
