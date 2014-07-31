#include "net/http_client.h"

#include <Foundation/Foundation.h>
#include <string>

#include "base/logging.h"
#include "base/strings/sys_string_conversions.h"

using base::SysNSStringToUTF8;
using base::SysUTF8ToNSString;
using net::HttpError;
using net::HttpHeaders;
using net::HttpRequest;
using net::HttpRequestCallback;
using net::HttpResponse;

@interface MitroURLConnectionDelegate : NSObject<NSURLConnectionDelegate> {
  HttpResponse* _response;
  HttpRequestCallback _callback;
  NSMutableData* _responseData;
  NSArray* _trustedHosts;
}

- (id)initWithRequest:(const HttpRequest*)request
             callback:(HttpRequestCallback)callback;
@end

@implementation MitroURLConnectionDelegate

- (id) initWithRequest:(const HttpRequest*)request
              callback:(HttpRequestCallback)callback {
  self = [super init];

  if (self) {
    _response = new HttpResponse(*request);
    _callback = callback;
    _responseData = [[NSMutableData alloc] init];
    _trustedHosts = [[NSArray alloc] initWithObjects:@"localhost", nil];
  }
  return self;
}

- (void)dealloc {
  delete _response;
  [_responseData release];
  [_trustedHosts release];
  [super dealloc];
}

- (void)connection:(NSURLConnection*)connection didReceiveData:(NSData*)data {
  DLOG(INFO) << "received data";
  [_responseData appendData:data];
}

- (void)connection:(NSURLConnection*)connection 
    didReceiveResponse:(NSURLResponse*)response {
  DLOG(INFO) << "received response";

  NSHTTPURLResponse* httpResponse = (NSHTTPURLResponse*) response;

  _response->SetStatusCode([httpResponse statusCode]); 

  NSDictionary* headersDict = [httpResponse allHeaderFields];
  HttpHeaders* headers = _response->GetMutableHeaders();

  for (NSString* name in headersDict) {
    NSString* value = [headersDict objectForKey:name];
    headers->Set(SysNSStringToUTF8(name), SysNSStringToUTF8(value));
  }
}

- (void)connectionDidFinishLoading:(NSURLConnection*)connection {
  DLOG(INFO) << "request finished";

  NSString* str = [[[NSString alloc]initWithData:_responseData
                                    encoding:NSUTF8StringEncoding] autorelease];
  _response->SetBody(SysNSStringToUTF8(str));
  _callback.Run(*_response);
}

- (void)connection:(NSURLConnection*)connection
    didFailWithError:(NSError*)error {
    std::string error_message = SysNSStringToUTF8([error localizedDescription]);
    LOG(ERROR) << "Error making request: " << error_message;

    _response->SetError(HttpError(HttpError::UNKNOWN, error_message));
    _callback.Run(*_response);
}

// Dev server does not supply an acceptable SSL certificate so we need to
// whitelist localhost as a trusted host.
#ifndef NDEBUG

- (BOOL)connection:(NSURLConnection *)connection canAuthenticateAgainstProtectionSpace:(NSURLProtectionSpace *)protectionSpace {
  return [protectionSpace.authenticationMethod isEqualToString:NSURLAuthenticationMethodServerTrust];
}

- (void)connection:(NSURLConnection *)connection didReceiveAuthenticationChallenge:(NSURLAuthenticationChallenge *)challenge {
  if ([challenge.protectionSpace.authenticationMethod isEqualToString:NSURLAuthenticationMethodServerTrust]) {
    if ([_trustedHosts containsObject:challenge.protectionSpace.host]) {
      [challenge.sender useCredential:[NSURLCredential credentialForTrust:challenge.protectionSpace.serverTrust] forAuthenticationChallenge:challenge];
    }
  }

  [challenge.sender continueWithoutCredentialForAuthenticationChallenge:challenge];
}

#endif  // NDEBUG

@end

namespace net {

// Creates an NSURLRequest from an HttpRequest.
// The returned object is autoreleased.
NSURLRequest* CreateURLRequestWithHttpRequest(const HttpRequest& http_request) {
  NSMutableURLRequest* urlRequest =
      [[[NSMutableURLRequest alloc] init] autorelease];

  // SysUTF8ToNSSTring returns autoreleased NSString.
  NSString* url = SysUTF8ToNSString(http_request.GetURL());
  NSString* method = SysUTF8ToNSString(http_request.GetMethod());
  NSString* data = SysUTF8ToNSString(http_request.GetBody());

  [urlRequest setURL:[NSURL URLWithString:url]];
  [urlRequest setHTTPMethod:method];
  [urlRequest setHTTPBody:[data dataUsingEncoding:NSUTF8StringEncoding]];

  HttpHeaders::Iterator iter(http_request.GetHeaders());
  while (iter.GetNext()) {
    NSString* name = SysUTF8ToNSString(iter.name());
    NSString* value = SysUTF8ToNSString(iter.value());
    [urlRequest addValue:value forHTTPHeaderField:name];
  }

  return urlRequest;
}

bool HttpClient::MakeRequest(const HttpRequest& request,
                             const HttpRequestCallback& callback) {
  NSURLRequest* urlRequest = CreateURLRequestWithHttpRequest(request);
  
  MitroURLConnectionDelegate* delegate = 
      [[[MitroURLConnectionDelegate alloc] initWithRequest:&request
                                                callback:callback] autorelease];
  NSURLConnection* connection = 
    [[[NSURLConnection alloc] initWithRequest:urlRequest
                                     delegate:delegate
                             startImmediately:YES] autorelease];
  if (connection == NULL) {
    HttpResponse response(request);
    response.SetError(HttpError(HttpError::UNKNOWN,
                                "Error initializing connection"));
    callback.Run(response);
  } else {
    DLOG(INFO) << "request started";
  }

  return true;
}

}
