#include "net/http_client.h"

#include <string>

#include "base/logging.h"

using std::string;

namespace net {

HttpHeaders::HttpHeaders(const HttpHeaderMap& headers)
    : headers_(headers) {
}

HttpHeaders::HttpHeaders(const HttpHeaders& headers)
    : headers_(headers.headers_) {
}

const string* HttpHeaders::Get(const string& key) const {
  HttpHeaderMap::const_iterator iter = headers_.find(key);
  return iter == headers_.end() ? NULL : &iter->second;
}

void HttpHeaders::Set(const string& key, const string& value) {
  headers_.insert(std::pair<string, string>(key, value));
}

HttpHeaders::Iterator::Iterator(const HttpHeaders& headers)
    : started_(false),
      cur_(headers.headers_.begin()),
      end_(headers.headers_.end()) {
}

bool HttpHeaders::Iterator::GetNext() {
  if (!started_) {
    started_ = true;
    return cur_ != end_;
  }

  if (cur_ == end_)
    return false;

  ++cur_;
  return cur_ != end_;
}

HttpRequest::HttpRequest(const string& url,
                         const string& method,
                         const HttpHeaders& headers,
                         const string& body)
    : url_(url), method_(method), headers_(headers), body_(body) {
}

HttpRequest::HttpRequest(const HttpRequest& request)
    : url_(request.url_),
      method_(request.method_),
      headers_(request.headers_),
      body_(request.body_) {
}

HttpResponse::HttpResponse(const HttpRequest& request)
    : request_(request),
      status_code_(599) {
}

bool HttpClient::Get(const std::string& url,
                     const HttpHeaders& headers,
                     const HttpRequestCallback& callback) {
  return MakeRequest(HttpRequest(url, "GET", headers, ""), callback);
}

bool HttpClient::Post(const std::string& url,
                      const HttpHeaders& headers,
                      const std::string& body,
                      const HttpRequestCallback& callback) {
  return MakeRequest(HttpRequest(url, "POST", headers, body), callback);
}

}  // namespace net
