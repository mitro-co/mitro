#include <time.h>
#include <string>
#include <vector>

#include <gtest/gtest.h>

#include "base/bind.h"
#include "base/callback.h"
#include "base/mac/mac_util.h"
#include "base/logging.h"
#include "mitro_api/mitro_api.h"
#include "mitro_api/mitro_api_types.h"
#include "net/http_client.h"

using base::Callback;
using mitro_api::MitroApiClient;
using mitro_api::MitroApiError;
using net::HttpClient;
using std::string;
using std::vector;

const char* kMitroHost = "localhost:8443";
const char* kUsername = "z@z";
const char* kPassword = "UJ85tzyL";
const char* kDeviceId = "UJ85tzyLvZgT4vzWp6fENE/smm4=";

class MitroApiClientTest : public testing::Test {
 protected:
  MitroApiClientTest()
      : api_client_(&http_client_) {
  }

  void OnLogin(const string& username,
               const string& login_token,
               const string& login_token_signature,
               const string& encrypted_private_key,
               MitroApiError* error) {
    ASSERT_EQ(NULL, error);
    base::StopRunLoop();

    LOG(INFO) << "logged in as " << api_client_.GetUsername();
  }

  virtual void SetUp() {
    api_client_.SetHost(kMitroHost);
    api_client_.SetDeviceID(kDeviceId);

    mitro_api::LoginCallback callback =
        base::Bind(&MitroApiClientTest::OnLogin, base::Unretained(this));
    api_client_.Login(kUsername, kPassword, "",  "", "", "", false, callback);
    base::StartRunLoop();
  }

  HttpClient http_client_;
  MitroApiClient api_client_;
};

void OnLogin(const string& username,
             const string& login_token,
             const string& login_token_signature,
             const string& encrypted_private_key,
             MitroApiError* error) {
  base::StopRunLoop();

  if (error != NULL) {
    LOG(INFO) << error->GetMessage();
  }

  ASSERT_EQ(NULL, error);
  LOG(INFO) << "exit OnGetMyPrivateKey";
}

TEST_F(MitroApiClientTest, LoginTest) {
  mitro_api::LoginCallback callback = base::Bind(&::OnLogin);
  api_client_.Login(kUsername, kPassword, "",  "", "", "", false, callback);

  base::StartRunLoop();
}

void OnGetMyDeviceKey(const string& device_key_string,
                      MitroApiError* error) {
  base::StopRunLoop();
  ASSERT_EQ(NULL, error);
  LOG(INFO) << "exit OnGetMyDeviceKey";
}

TEST_F(MitroApiClientTest, GetMyDeviceKeyTest) {
  mitro_api::GetDeviceKeyCallback callback = base::Bind(&OnGetMyDeviceKey);
  api_client_.GetDeviceKey(callback);

  base::StartRunLoop();
}

void OnGetSecretsList(
    const mitro_api::ListMySecretsAndGroupKeysResponse& secrets,
    MitroApiError* error) {
  base::StopRunLoop();
  ASSERT_EQ(NULL, error);
  LOG(INFO) << "exit OnGetSecretsList";
}

TEST_F(MitroApiClientTest, GetSecretsListTest) {
  mitro_api::GetSecretsListCallback callback = base::Bind(&OnGetSecretsList);
  api_client_.GetSecretsList(callback);

  base::StartRunLoop();
}

void OnGetSecret(const mitro_api::Secret& secret, MitroApiError* error) {
  base::StopRunLoop();

  ASSERT_EQ(NULL, error);
  ASSERT_TRUE(secret.has_encryptedCriticalData());
  LOG(INFO) << "exit OnGetMyPrivateKey";
}

TEST_F(MitroApiClientTest, GetSecretTest) {
  mitro_api::GetSecretCallback callback = base::Bind(&OnGetSecret);
  api_client_.GetSecret(1, 1, true, callback);

  base::StartRunLoop();
}
