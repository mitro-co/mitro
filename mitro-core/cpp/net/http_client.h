#ifndef NET_HTTP_CLIENT_H_
#define NET_HTTP_CLIENT_H_

#include <string>
#include <map>

#include "base/basictypes.h"
#include "base/callback.h"
#include "base/memory/scoped_ptr.h"

namespace net {

// HttpError represents an internal error from an Http request.  It should
// not be confused with the Http status code. 
class HttpError {
 public:
  enum Code {
    OK,
    UNKNOWN
  };

  HttpError() : code_(OK) {}
  HttpError(const HttpError& error)
      : code_(error.code_), message_(error.message_) {}
  HttpError(Code code, const std::string& message)
      : code_(code), message_(message) {}
  ~HttpError() {}

  inline Code GetCode() const { return code_; } 
  inline const std::string& GetMessage() const { return message_; }

 private:
  Code code_;
  std::string message_;
};

typedef std::map<std::string, std::string> HttpHeaderMap;

class HttpHeaders {
 public:
  HttpHeaders() {}
  explicit HttpHeaders(const HttpHeaderMap& headers);
  explicit HttpHeaders(const HttpHeaders& headers);
  ~HttpHeaders() {}

  // Returns the header value or NULL if the header does not exist.
  const std::string* Get(const std::string& name) const;

  // Sets a header, replacing any existing header with the same key.
  // TODO: Implement method for adding multiple headers with the same key.
  void Set(const std::string& name, const std::string& value);

  class Iterator {
   public:
    explicit Iterator(const HttpHeaders& headers);
    ~Iterator() {}

    // Advances the iterator to the next header, if any.  Returns true if there
    // is a next header.  Use name() and value() methods to access the resultant
    // header name and value.
    bool GetNext();

    // These two accessors are only valid if GetNext() returned true.
    inline const std::string& name() const { return cur_->first; }
    inline const std::string& value() const { return cur_->second; }

   private:
    bool started_;
    HttpHeaderMap::const_iterator cur_;
    const HttpHeaderMap::const_iterator end_;

    DISALLOW_COPY_AND_ASSIGN(Iterator);
  };

 private:
  HttpHeaderMap headers_;  

  DISALLOW_ASSIGN(HttpHeaders);   
};

class HttpRequest {
 public:
  HttpRequest(const std::string& url,
              const std::string& method,
              const HttpHeaders& headers,
              const std::string& body);
  HttpRequest(const HttpRequest& request);
  ~HttpRequest() {}

  inline const std::string& GetURL() const { return url_; }
  inline const std::string& GetMethod() const { return method_; }
  inline const HttpHeaders& GetHeaders() const { return headers_; }
  inline const std::string& GetBody() const { return body_; }

 private:
  std::string url_;
  std::string method_;
  HttpHeaders headers_;
  std::string body_;

  DISALLOW_ASSIGN(HttpRequest);
};

class HttpResponse {
 public:
  HttpResponse(const HttpRequest& request);
  ~HttpResponse() {}

  // Call IsOk() before accessing any other methods to check that the response
  // is valid.  If IsOk() returns false, call GetError for information about
  // why the request failed. 
  inline bool IsOk() const { return error_.GetCode() == HttpError::OK; }

  // Get the internal error when the request failed.
  inline const HttpError& GetError() const { return error_; }
  inline void SetError(const HttpError& error) { error_ = error; }

  inline const HttpRequest& GetRequest() const { return request_; }

  inline int GetStatusCode() const { return status_code_; }
  inline void SetStatusCode(int status_code) { status_code_ = status_code; }

  inline const std::string& GetBody() const { return body_; }
  inline void SetBody(const std::string& body) { body_ = body; }

  inline const HttpHeaders& GetHeaders() const { return headers_; }
  inline HttpHeaders* GetMutableHeaders() { return &headers_; }

 private:
  HttpRequest request_;
  int status_code_;
  std::string body_;
  HttpHeaders headers_;
  HttpError error_;

  DISALLOW_COPY_AND_ASSIGN(HttpResponse); 
};

typedef base::Callback<void(const HttpResponse&)> HttpRequestCallback;

class HttpClient {
 public:
  HttpClient() {}
  virtual ~HttpClient() {}

  virtual bool MakeRequest(const HttpRequest& request,
                           const HttpRequestCallback& callback);

  virtual bool Get(const std::string& url,
                   const HttpHeaders& headers,
                   const HttpRequestCallback& callback);
  virtual bool Post(const std::string& url,
                    const HttpHeaders& headers,
                    const std::string& body,
                    const HttpRequestCallback& callback);

 private:
  DISALLOW_COPY_AND_ASSIGN(HttpClient);   
};

}  // namespace net

#endif  // NET_HTTP_CLIENT_H_
