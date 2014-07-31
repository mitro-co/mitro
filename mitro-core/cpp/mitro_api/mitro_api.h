#ifndef MITRO_API_MITRO_API_H_
#define MITRO_API_MITRO_API_H_

#include <string>
#include <vector>

#include <boost/shared_ptr.hpp>

#include "base/basictypes.h"
#include "base/callback.h"
#include "keyczar_json/mitrokey.h"
#include "net/http_client.h"

namespace apache {
namespace thrift {
class TStruct;

namespace transport {
class TMemoryBuffer;
}
namespace protocol {
class TProtocol;
}
}
}

namespace mitro_api {

class GroupInfo;
class ListMySecretsAndGroupKeysResponse;
class SecretClientData;
class Secret;

class MitroApiError {
 public:
  MitroApiError() {}
  explicit MitroApiError(const std::string& message) : message_(message) {}
  explicit MitroApiError(const MitroApiError& error)
      : message_(error.message_),
        exception_type_(error.exception_type_) {
  }
  ~MitroApiError() {}

  inline const std::string& GetMessage() const { return message_; }
  inline void SetMessage(const std::string& message) { message_ = message; }

  inline const std::string& GetExceptionType() const { return exception_type_; }
  inline void SetExceptionType(const std::string& exception_type) {
    exception_type_ = exception_type;
  }

 private:
  std::string message_;
  std::string exception_type_;

  DISALLOW_ASSIGN(MitroApiError);
};

typedef base::Callback<void(const std::string& username,
                            const std::string& login_token,
                            const std::string& login_token_signature,
                            const std::string& encrypted_private_key,
                            MitroApiError* error)> LoginCallback;
typedef base::Callback<void(const std::string&, MitroApiError*)> GetDeviceKeyCallback;
typedef base::Callback<void(const ListMySecretsAndGroupKeysResponse&, MitroApiError*)> GetSecretsListCallback;
typedef base::Callback<void(const Secret&, MitroApiError*)> GetSecretCallback;

class MitroApiClient {
 public:
  MitroApiClient(net::HttpClient* http_client);
  virtual ~MitroApiClient() {}

  const std::string& GetHost() const { return host_; }
  void SetHost(const std::string& host) { host_ = host; } 

  const std::string& GetClientID() const { return client_id_; }
  void SetClientID(const std::string& client_id) { client_id_ = client_id; }

  const std::string& GetDeviceID();
  void SetDeviceID(const std::string& device_id) { device_id_ = device_id; }

  const std::string& GetUsername() const { return username_; }

  bool IsLoggedIn() const { return user_private_key_.get() != NULL; }
  void Login(const std::string& username,
             const std::string& password,
             const std::string& two_factor_auth_code,
             const std::string& login_token,
             const std::string& login_token_signature,
             const std::string& encrypted_private_key,
             bool save_private_key,
             const LoginCallback& callback);
  void Logout();

  void GetDeviceKey(const GetDeviceKeyCallback& callback);

  void GetSecretsList(const GetSecretsListCallback& callback);

  void GetSecret(int secret_id,
                 int group_id,
                 bool include_critical_data,
                 GetSecretCallback& callback);

  // Decrypts secret data.
  //
  // Returns true on success.
  // Returns false and sets error on failure.
  bool DecryptSecret(Secret* secret,
                     const std::map<std::string, GroupInfo>& groups,
                     MitroApiError* error);

 private:
  static std::string GenerateDeviceID();

  void SetUsername(const std::string& username) { username_ = username; }

  std::string ThriftStructToJsonString(const apache::thrift::TStruct& message);
  bool JsonStringToThriftStruct(const std::string& s,
                                apache::thrift::TStruct* message,
                                MitroApiError* error);

  void PostRequest(const std::string& endpoint,
                   const apache::thrift::TStruct& request,
                   const net::HttpRequestCallback& callback);
  void MakeRequest(const std::string& endpoint,
                   const apache::thrift::TStruct& request,
                   const net::HttpRequestCallback& callback);

  bool ParseResponse(const net::HttpResponse& response,
                     apache::thrift::TStruct* message,
                     MitroApiError* error);

  void OnGetMyPrivateKey(const std::string& password,
                         const std::string& encrypted_private_key,
                         bool save_private_key,
                         const LoginCallback& callback,
                         const net::HttpResponse& response);
  void OnGetMyPrivateKeyDeviceKeyCallback(const LoginCallback& callback,
                                          const std::string& login_token,
                                          const std::string& device_key_string,
                                          MitroApiError* error);
  void FinishGetMyPrivateKey(const LoginCallback& callback,
                             const std::string& login_token,
                             const std::string& encrypted_private_key,
                             MitroApiError* error);

  void OnGetMyDeviceKey(const GetDeviceKeyCallback& callback,
                        const net::HttpResponse& response);

  // Callback for the GetSecretsList request.
  void OnGetSecretsList(const GetSecretsListCallback& callback,
                        const net::HttpResponse& response);

  void OnGetSecret(const GetSecretCallback& callback,
                   const net::HttpResponse& response);

  int GetUserGroupIdFromSecretsListResponse(
      const ListMySecretsAndGroupKeysResponse& secrets_list_response);

  bool DecryptUsingCache(const mitro::MitroPrivateKey& key,
                         const std::string& encrypted_data,
                         std::string* decrypted_data);

  // Decrypts the chain of group keys.
  //
  // Returns the key that can be used to decrypt the secret data on success.
  // Returns NULL and sets error on failure.
  mitro::MitroPrivateKey* GetDecryptionKeyForSecret(
      const Secret& secret,
      const std::map<std::string, GroupInfo>& groups,
      MitroApiError* error);

  static const char* kProtocol;
  static const char* kDefaultHost;
  static const char* kApiBasePath;

  static const int kTransportBufferSize = 1 << 16;
  static const int kDeviceIDSize = 20;

  net::HttpClient* http_client_;
  boost::shared_ptr<apache::thrift::transport::TMemoryBuffer> transport_;
  boost::shared_ptr<apache::thrift::protocol::TProtocol> protocol_;

  // Shared data i.e. data that persists across users. 
  std::string host_;
  std::string client_id_;
  std::string device_id_;
  bool save_private_key_;

  // User data i.e. data that is cleared on logout.
  std::string username_;
  base::scoped_ptr<mitro::MitroPrivateKey> user_private_key_;

  // Cache of decrypted private keys. This reduces expensive crypto operations.
  class KeyCache {
   public:
    KeyCache() {}
    ~KeyCache();

    void Clear() { cache_.clear(); }
    // WARNING! KeyCache takes ownshership of the inserted key.
    void Insert(const std::string& encrypted_key_string,
                mitro::MitroPrivateKey* key);
    mitro::MitroPrivateKey* Find(const std::string& encrypted_key_string) const;

   private:
    typedef std::map<std::string, mitro::MitroPrivateKey*> KeyMap;
    KeyMap cache_;

    DISALLOW_COPY_AND_ASSIGN(KeyCache);
  };

  KeyCache key_cache_;

  typedef std::map<std::string, std::string> DecryptionCache;
  DecryptionCache decryption_cache_;

  DISALLOW_COPY_AND_ASSIGN(MitroApiClient);

  friend class MitroApiClientTest;
};

}  // namespace mitro_api

#endif  // MITRO_API_MITRO_API_H_
