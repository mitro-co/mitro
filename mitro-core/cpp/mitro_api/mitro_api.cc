#include "mitro_api/mitro_api.h"

#include <map>
#include <string>
#include <vector>

#include <keyczar/base/base64w.h>
#include <keyczar/crypto_factory.h>
#include <keyczar/keyczar.h>
#include <thrift/protocol/TSimpleJSONProtocol.h>
#include <thrift/transport/TBufferTransports.h>
#include <thrift/Thrift.h>

#include "base/bind.h"
#include "base/logging.h"
#include "base/memory/scoped_ptr.h"
#include "base/stl_util.h"
#include "base/strings/string_number_conversions.h"
#include "base/strings/stringprintf.h"
#include "keyczar_json/json_keyset_reader.h"
#include "mitro_api/mitro_api_types.h"
#include "net/http_client.h"
#include "net/uri.h"

using apache::thrift::protocol::TSimpleJSONProtocol;
using apache::thrift::transport::TMemoryBuffer;
using apache::thrift::TStruct;
using base::Bind;
using base::Callback;
using base::StringPrintf;
using mitro::MitroPrivateKey;
using net::HttpClient;
using net::HttpHeaders;
using net::HttpRequestCallback;
using net::HttpResponse;
using std::map;
using std::string;
using std::vector;

namespace mitro_api {

const char* MitroApiClient::kProtocol = "https";
const char* MitroApiClient::kDefaultHost = "www.mitro.co";
const char* MitroApiClient::kApiBasePath = "mitro-core/api";

MitroApiClient::MitroApiClient(HttpClient* http_client)
    : host_(kDefaultHost),
      http_client_(http_client),
      transport_(new TMemoryBuffer(kTransportBufferSize)),
      protocol_(new TSimpleJSONProtocol(transport_)) {
}

/* static */
string MitroApiClient::GenerateDeviceID() {
  string device_id;
  CHECK(keyczar::CryptoFactory::Rand()->RandBytes(kDeviceIDSize, &device_id));

  string device_id_string;
  CHECK(keyczar::base::Base64WEncode(device_id, &device_id_string));

  return device_id_string;
}

const string& MitroApiClient::GetDeviceID() {
  if (device_id_.empty()) {
    device_id_ = GenerateDeviceID();
  }

  return device_id_;
}

string MitroApiClient::ThriftStructToJsonString(const TStruct& message) {
  // TODO: an exception could be thrown here.
  // It looks like none of these are exceptions that we should handle
  // e.g. bad_alloc, strings larger than 2^31.
  message.write(protocol_.get());
  string json_string = transport_.get()->getBufferAsString();
  transport_->resetBuffer();
  return json_string;
}

bool MitroApiClient::JsonStringToThriftStruct(const std::string& s,
                                              TStruct* message,
                                              MitroApiError* error) {
  bool result = true;

  try {
    transport_.get()->write(reinterpret_cast<const uint8_t*>(s.data()),
                            s.size());
    message->read(protocol_.get());
  } catch (apache::thrift::TException e) {
    LOG(ERROR) << e.what();
    error->SetMessage(e.what());
    result = false;

    // TODO: json parser should reset it's own state on exception.
    protocol_.reset(new TSimpleJSONProtocol(transport_));
  }

  transport_->resetBuffer();

  return result;
}

void MitroApiClient::PostRequest(const string& endpoint,
                                 const TStruct& request,
                                 const HttpRequestCallback& callback) {
  const string& data = ThriftStructToJsonString(request);
  LOG(INFO) << "request: " << data;

  string url = net::BuildUri(kProtocol, GetHost(), kApiBasePath + endpoint);
  HttpHeaders headers;
  http_client_->Post(url, headers, data, callback);
}

void MitroApiClient::MakeRequest(const string& endpoint,
                                 const TStruct& request,
                                 const HttpRequestCallback& callback) {
  SignedRequest request_wrapper;

  string request_string = ThriftStructToJsonString(request);
  request_wrapper.set_clientIdentifier(GetClientID());
  request_wrapper.set_identity(GetUsername());
  request_wrapper.set_request(request_string);

  if (endpoint != "/GetMyPrivateKey") {
    CHECK(user_private_key_.get() != NULL);
    // TODO: Can signing fail?
    string signature;
    CHECK(user_private_key_->Sign(request_string, &signature));
    request_wrapper.set_signature(signature);
  }

  LOG(INFO) << endpoint << " " << request_string;
  PostRequest(endpoint, request_wrapper, callback);
}

bool MitroApiClient::ParseResponse(const HttpResponse& response,
                                   TStruct* message,
                                   MitroApiError* error) {
  //LOG(INFO) << "response: " << response.GetBody();

  if (!response.IsOk()) {
    error->SetMessage(response.GetError().GetMessage());
    return false;
  }

  MitroException exception;
  if (!JsonStringToThriftStruct(response.GetBody(), &exception, error)) {
    return false;
  }

  if (exception.has_exceptionType()) {
    error->SetMessage(exception.get_userVisibleError());
    error->SetExceptionType(exception.get_exceptionType());
    return false;
  }

  if (!JsonStringToThriftStruct(response.GetBody(), message, error)) {
    return false;
  }

  return true;
}

void MitroApiClient::Login(const string& username,
                           const string& password,
                           const string& two_factor_auth_code,
                           const string& login_token,
                           const string& login_token_signature,
                           const string& encrypted_private_key,
                           bool save_private_key,
                           const LoginCallback& callback) {
  if (IsLoggedIn()) {
    Logout();
  }

  SetUsername(username);

  GetMyPrivateKeyRequest request;
  request.set_userId(GetUsername());
  request.set_deviceId(GetDeviceID());
  request.set_twoFactorCode(two_factor_auth_code);
  request.set_loginToken(login_token);
  request.set_loginTokenSignature(login_token_signature);

  HttpRequestCallback request_callback =
      Bind(&MitroApiClient::OnGetMyPrivateKey,
           base::Unretained(this),
           password,
           encrypted_private_key,
           save_private_key,
           callback);

  MakeRequest("/GetMyPrivateKey", request, request_callback);
}

void MitroApiClient::Logout() {
  username_ = "";
  user_private_key_.reset();

  key_cache_.Clear();
  decryption_cache_.clear();
}

void MitroApiClient::OnGetMyPrivateKey(const string& password,
                                       const string& encrypted_private_key,
                                       bool save_private_key,
                                       const LoginCallback& callback,
                                       const HttpResponse& response) {
  MitroApiError error;
  GetMyPrivateKeyResponse private_key_response;

  if (ParseResponse(response, &private_key_response, &error)) {
    if (!encrypted_private_key.empty() &&
        private_key_response.has_deviceKeyString()) {
      mitro::JsonKeysetReader reader;
      reader.ReadKeyString(private_key_response.get_deviceKeyString());
      scoped_ptr<keyczar::Keyczar> crypter(keyczar::Crypter::Read(reader));
      string private_key_string = crypter->Decrypt(encrypted_private_key);

      user_private_key_.reset(new MitroPrivateKey);
      if (!user_private_key_->Read(private_key_string)) {
        user_private_key_.reset();
        error.SetMessage("Error decrypting private key");
      }
    } else if (private_key_response.has_encryptedPrivateKey()) {
      user_private_key_.reset(new MitroPrivateKey);

      if (!user_private_key_->ReadEncrypted(
              private_key_response.get_encryptedPrivateKey(), password)) {
        user_private_key_.reset();
        error.SetMessage("Invalid password");
      } else if (save_private_key) {
        GetDeviceKeyCallback device_key_callback =
            Bind(&MitroApiClient::OnGetMyPrivateKeyDeviceKeyCallback,
                 base::Unretained(this),
                 callback,
                 private_key_response.get_unsignedLoginToken());
        GetDeviceKey(device_key_callback);
        return;
      }
    } else {
      error.SetMessage("Missing encrypted private key");
    }
  } else if (error.GetMessage().empty()) {
    error.SetMessage("Unknown error parsing private key response");
  }

  if (error.GetMessage().empty()) {
    const string& login_token = private_key_response.get_unsignedLoginToken();
    FinishGetMyPrivateKey(callback, login_token, "", NULL);
  } else {
    FinishGetMyPrivateKey(callback, "", "", &error);
  }
}

void MitroApiClient::OnGetMyPrivateKeyDeviceKeyCallback(
    const LoginCallback& callback,
    const string& login_token,
    const string& device_key_string,
    MitroApiError* error) {
  if (error == NULL) {
    mitro::MitroPrivateKey device_key;

    MitroApiError device_key_error;
    if (!device_key.Read(device_key_string)) {
      device_key_error.SetMessage("Error reading device key");
      FinishGetMyPrivateKey(callback, login_token, "", &device_key_error);
    }

    // TODO: serialize and encrypt user private key using device string after
    // json keyczar writer is done.
  }

  FinishGetMyPrivateKey(callback, login_token, "", error);
}

void MitroApiClient::FinishGetMyPrivateKey(
      const LoginCallback& callback,
      const string& login_token,
      const string& encrypted_private_key,
      MitroApiError* error) {
  if (error != NULL) {
    callback.Run(username_, "", "", "", error);
    return;
  }

  string login_token_signature;
  // TODO: Can signing fail?
  CHECK(user_private_key_->Sign(login_token, &login_token_signature));

  callback.Run(username_, login_token, login_token_signature, encrypted_private_key, NULL);
}

void MitroApiClient::GetDeviceKey(const GetDeviceKeyCallback& callback) {
  GetMyDeviceKeyRequest request;
  request.set_deviceId(GetDeviceID());

  HttpRequestCallback request_callback =
      Bind(&MitroApiClient::OnGetMyDeviceKey, base::Unretained(this), callback);

  MakeRequest("/GetMyDeviceKey", request, request_callback);
}

void MitroApiClient::OnGetMyDeviceKey(const GetDeviceKeyCallback& callback,
                                      const HttpResponse& response) {
  MitroApiError error;
  GetMyDeviceKeyResponse device_key_response;

  if (!ParseResponse(response, &device_key_response, &error)) {
    callback.Run("", &error);
    return;
  }

  string device_key_string = device_key_response.get_deviceKeyString();
  LOG(INFO) << "device key: " << device_key_string;

  callback.Run(device_key_string, NULL);
}

void MitroApiClient::GetSecretsList(const GetSecretsListCallback& callback) {
  ListMySecretsAndGroupKeysRequest request;

  request.set_deviceId(GetDeviceID());

  HttpRequestCallback request_callback =
      Bind(&MitroApiClient::OnGetSecretsList,
           base::Unretained(this),
           callback);

  MakeRequest("/ListMySecretsAndGroupKeys", request, request_callback);
}

void MitroApiClient::OnGetSecretsList(const GetSecretsListCallback& callback,
                                      const HttpResponse& response) {
  MitroApiError error;
  ListMySecretsAndGroupKeysResponse secrets_list_response;

  if (!ParseResponse(response, &secrets_list_response, &error)) {
    callback.Run(secrets_list_response, &error);
    return;
  }

  callback.Run(secrets_list_response, NULL);
}

void MitroApiClient::GetSecret(int secret_id,
                               int group_id,
                               bool include_critical_data,
                               GetSecretCallback& callback) {
  GetSecretRequest request;

  request.set_deviceId(GetDeviceID());
  request.set_userId(GetUsername());
  request.set_secretId(secret_id);
  request.set_groupId(group_id);
  request.set_includeCriticalData(include_critical_data);

  HttpRequestCallback request_callback =
      Bind(&MitroApiClient::OnGetSecret,
           base::Unretained(this),
           callback);

  MakeRequest("/GetSecret", request, request_callback);
}

void MitroApiClient::OnGetSecret(const GetSecretCallback& callback,
                                 const net::HttpResponse& response) {
  Secret empty_secret;
  MitroApiError error;
  GetSecretResponse secret_response;

  if (!ParseResponse(response, &secret_response, &error)) {
    callback.Run(empty_secret, &error);
    return;
  }

  callback.Run(secret_response.get_secret(), NULL);
}

int MitroApiClient::GetUserGroupIdFromSecretsListResponse(
    const ListMySecretsAndGroupKeysResponse& secrets_list_response) {
  map<string, GroupInfo>::const_iterator group_iter;
  map<string, GroupInfo>::const_iterator group_end_iter =
      secrets_list_response.get_groups().end();

  for (group_iter = secrets_list_response.get_groups().begin();
       group_iter != group_end_iter;
       ++group_iter) {
      if (group_iter->second.get_name().empty()) {
        return group_iter->second.get_groupId();
      }
  }

  return -1;
}

bool MitroApiClient::DecryptUsingCache(const MitroPrivateKey& key,
                                       const std::string& encrypted_data,
                                       std::string* decrypted_data) {
  DecryptionCache::const_iterator iter = decryption_cache_.find(encrypted_data);
  if (iter == decryption_cache_.end()) {
    if (!key.Decrypt(encrypted_data, decrypted_data)) {
      return false;
    }
    decryption_cache_.insert(
        std::pair<string, string>(encrypted_data, *decrypted_data));
  } else {
    *decrypted_data = iter->second;
  }

  return true;
}

MitroPrivateKey* MitroApiClient::GetDecryptionKeyForSecret(
    const Secret& secret,
    const map<string, GroupInfo>& groups,
    MitroApiError* error) {
  if (!secret.has_groupIdPath()) {
    error->SetMessage("Secret does not contain group id path");
    return NULL;
  }

  MitroPrivateKey* decryption_key = user_private_key_.get();
  vector<int>::const_iterator group_id_iter;

  for (group_id_iter = secret.get_groupIdPath().begin();
       group_id_iter != secret.get_groupIdPath().end();
       ++group_id_iter) {
    map<string, GroupInfo>::const_iterator group_iter;
    group_iter = groups.find(base::IntToString(*group_id_iter));

    if (group_iter == groups.end()) {
      error->SetMessage(StringPrintf("Group %d not found", *group_id_iter));
      return NULL;
    }

    string encrypted_group_key = group_iter->second.get_encryptedPrivateKey(); 

    MitroPrivateKey* next_decryption_key = key_cache_.Find(encrypted_group_key);
    if (next_decryption_key == NULL) {
      string group_key_string;
      if (!DecryptUsingCache(*decryption_key,
                             encrypted_group_key,
                             &group_key_string)) {
        error->SetMessage("Error decrypting group key");
        return NULL;
      }

      MitroPrivateKey* group_key = new MitroPrivateKey;
      if (!group_key->Read(group_key_string)) {
        delete group_key;
        error->SetMessage("Error reading group key");
        return NULL;
      }

      key_cache_.Insert(encrypted_group_key, group_key);
      next_decryption_key = group_key;
    }
    decryption_key = next_decryption_key;
  }

  return decryption_key;
}

bool MitroApiClient::DecryptSecret(Secret* secret,
                                   const map<string, GroupInfo>& groups,
                                   MitroApiError* error) {
  MitroPrivateKey* decryption_key =
      GetDecryptionKeyForSecret(*secret, groups, error);
  if (decryption_key == NULL) {
    // Error message set inside of GetDecryptionKeyForSecret.
    return false;
  }

  if (secret->has_encryptedClientData()) {
    string client_data_string;
    if (!DecryptUsingCache(*decryption_key,
                           secret->get_encryptedClientData(),
                           &client_data_string)) {
      error->SetMessage("Error decrypting secret client data");
      return false;
    }

    if (!JsonStringToThriftStruct(client_data_string,
                                  secret->mutable_clientData(),
                                  error)) {
      error->SetMessage("Error parsing secret client data");
      return false;
    }
  }

  if (secret->has_encryptedCriticalData()) {
    string critical_data_string;
    if (!decryption_key->Decrypt(secret->get_encryptedCriticalData(),
                                 &critical_data_string)) {
      error->SetMessage("Error decrypting secret critical data");
      return false;
    }

    if (!JsonStringToThriftStruct(critical_data_string,
                                  secret->mutable_criticalData(),
                                  error)) {
      error->SetMessage("Error parsing secret critical data");
      return false;
    }
  }

  return true;
}

MitroApiClient::KeyCache::~KeyCache() {
  STLDeleteValues(&cache_);
}

void MitroApiClient::KeyCache::Insert(const string& encrypted_key_string,
                                      MitroPrivateKey* key) {
  CHECK(Find(encrypted_key_string) == NULL);
  cache_.insert(std::pair<string, MitroPrivateKey*>(encrypted_key_string, key));
}

MitroPrivateKey* MitroApiClient::KeyCache::Find(
    const string& encrypted_key_string) const {
  KeyMap::const_iterator key_iter = cache_.find(encrypted_key_string);
  return key_iter == cache_.end() ? NULL : key_iter->second;
}

}  // namespace mitro_api
