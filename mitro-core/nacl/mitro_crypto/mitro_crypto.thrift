struct MitroCryptoError {
  1: optional string type;
  2: optional string message;
}

struct LoadPrivateKeyFromJsonRequest {
  1: required string encrypted_key;
  2: required string password;
}

struct LoadPrivateKeyFromJsonResponse {
  1: optional string result;
}

struct TestMessageRequest {
  1: optional bool return_error;
}

struct TestMessageResponse {
  1: optional string result;
}
