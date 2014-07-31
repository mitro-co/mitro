// This file contains message handlers for mitro crypto.
//
// Each handler takes a dictionary of input parameters, a dictionary of output
// parameters, and a dictionary for returning an error object.
//
// Handlers return true to indicate success and false on error.
// On success, the output dictionary is valid and the error object should
// be ignored.  On failure, the error object is valid and the output
// dictionary is ignored.

#include "keyczar_json/mitrokey.h"

#include "mitro_crypto_types.h"
#include "thrift_nacl.h"

namespace mitro_crypto {

bool LoadPrivateKeyFromJson(const LoadPrivateKeyFromJsonRequest& request,
                            LoadPrivateKeyFromJsonResponse* response,
                            MitroCryptoError* error) {
  mitro::MitroPrivateKey private_key;

  if (private_key.ReadEncrypted(request.get_encrypted_key(),
                                request.get_password())) {
    response->set_result("success");
  }  else {
    response->set_result("failure");
  }

  return true;
}

bool TestMessage(const TestMessageRequest& request,
                 TestMessageResponse* response,
                 MitroCryptoError* error) {
  if (request.has_return_error() && request.get_return_error()) {
    error->set_message("TestMessage error");
    return false;
  } else {
    response->set_result("success");
    return true;
  }
}

REGISTER_MESSAGE_HANDLER("loadPrivateKeyFromJson", LoadPrivateKeyFromJson)
REGISTER_MESSAGE_HANDLER("testMessage", TestMessage)

}  // namespace mitro_crypto
