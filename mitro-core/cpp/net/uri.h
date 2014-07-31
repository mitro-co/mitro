#ifndef NET_URI_H_
#define NET_URI_H_

#include <string>

namespace net {

std::string BuildUri(const std::string& protocol,
                     const std::string& host,
                     const std::string& path);

}  // namespace net

#endif  // NET_URI_H_
