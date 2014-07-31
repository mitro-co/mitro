#include "net/http_client.h"

#include <string>

#include "base/bind.h"
#include "base/logging.h"
#include "base/mac/mac_util.h"
#include "gtest/gtest.h"
#include "net/http_client.h"
#include "net/uri.h"

using net::HttpClient;
using net::HttpError;
using net::HttpHeaders;
using net::HttpResponse;

void GetRequestCallback(const HttpResponse& response) {
  ASSERT_TRUE(response.IsOk());
  ASSERT_EQ(200, response.GetStatusCode());
  ASSERT_FALSE(response.GetBody().empty());

  HttpHeaders::Iterator iter(response.GetHeaders());
  while (iter.GetNext()) {
    LOG(INFO) << iter.name() << ": " << iter.value();
  }

  base::StopRunLoop();
}

TEST(HTTPClientTest, GetRequestTest) {
  HttpClient http_client;
  HttpHeaders headers;

  http_client.Get("http://www.google.com/", headers, base::Bind(&GetRequestCallback));

  base::StartRunLoop();
}

void BadURLCallback(const HttpResponse& response) {
  ASSERT_FALSE(response.IsOk());
  ASSERT_NE(HttpError::OK, response.GetError().GetCode());
  
  base::StopRunLoop();
}

TEST(HTTPClientTest, BadURLTest) {
  HttpClient http_client;
  HttpHeaders headers;

  http_client.Get("/", headers, base::Bind(&BadURLCallback));

  base::StartRunLoop();
}

void UnknownHostCallback(const HttpResponse& response) {
  ASSERT_FALSE(response.IsOk());
  ASSERT_NE(HttpError::OK, response.GetError().GetCode());
  
  base::StopRunLoop();
}

TEST(HTTPClientTest, UnknownHostTest) {
  HttpClient http_client;
  HttpHeaders headers;

  http_client.Get("http://bjqqxyucpmck.ca/", headers, base::Bind(&UnknownHostCallback));

  base::StartRunLoop();
}
