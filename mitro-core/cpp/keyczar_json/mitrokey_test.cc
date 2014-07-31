#include <gtest/gtest.h>

#include <keyczar/base/file_util.h>
#include <keyczar/base/logging.h>

#include "keyczar_json/mitrokey.h"

using namespace mitro;

static const char MESSAGE[] = "hello this is a message";
static const char PASSWORD[] = "hellopass";

static const char PRIVATE_KEY_PATH[] = "privatekey.json";
static const char PUBLIC_KEY_PATH[] = "publickey.json";
static const char PRIVATE_KEY_ENCRYPTED_PATH[] = "privatekey_encrypted.json";

void checkInterop(const MitroPrivateKey& key, const MitroPublicKey& publicKey) {
  std::string encrypted;
  ASSERT_TRUE(publicKey.Encrypt(MESSAGE, &encrypted));
  std::string output;
  ASSERT_TRUE(key.Decrypt(encrypted, &output));
  ASSERT_EQ(MESSAGE, output);

  std::string signature;
  ASSERT_TRUE(key.Sign(MESSAGE, &signature));
  ASSERT_TRUE(publicKey.Verify(MESSAGE, signature));
}

void checkEncryptingAndDecrypting(const MitroPrivateKey& key) {
  // Check encrypting data
  std::string encrypted;
  ASSERT_TRUE(key.Encrypt(MESSAGE, &encrypted));
  std::string output;
  ASSERT_TRUE(key.Decrypt(encrypted, &output));
  ASSERT_EQ(MESSAGE, output);

  // Check signing data
  std::string signature;
  ASSERT_TRUE(key.Sign(MESSAGE, &signature));
  ASSERT_TRUE(key.Verify(MESSAGE, signature));
}

std::string ReadFileOrDie(const char* path) {
  std::string output;
  bool success = keyczar::base::ReadFileToString(path, &output);
  CHECK(success);
  return output;
}

class MitroKeyTest : public testing::Test {
public:
  MitroKeyTest() {
    key_data_ = ReadFileOrDie(PRIVATE_KEY_PATH);
    key_.reset(MitroPrivateKey::ReadNew(key_data_));
    ASSERT_TRUE(key_ != NULL);

    public_key_data_ = ReadFileOrDie(PUBLIC_KEY_PATH);
    public_key_.reset(MitroPublicKey::ReadNew(public_key_data_));
    ASSERT_TRUE(public_key_ != NULL);

    password_key_data_ = ReadFileOrDie(PRIVATE_KEY_ENCRYPTED_PATH);
  }

  std::string key_data_;
  scoped_ptr<MitroPrivateKey> key_;
  std::string public_key_data_;
  scoped_ptr<MitroPublicKey> public_key_;
  std::string password_key_data_;
};

TEST_F(MitroKeyTest, DeprecatedReadPublicPrivate) {
  // Read a private key without a password
  mitro::MitroPrivateKey key;
  ASSERT_TRUE(key.Read(key_data_));

  // Load the public key
  mitro::MitroPublicKey pubkey;
  ASSERT_TRUE(pubkey.Read(public_key_data_));

  checkInterop(key, pubkey);
  checkEncryptingAndDecrypting(key);
}

TEST_F(MitroKeyTest, DeprecatedPasswordPrivateKey) {
  // Read a password-protected private key, without the password
  mitro::MitroPrivateKey key;
  ASSERT_FALSE(key.Read(password_key_data_));

  // Read it with the password: success!
  ASSERT_TRUE(key.ReadEncrypted(password_key_data_, PASSWORD));

  checkEncryptingAndDecrypting(key);
}

TEST_F(MitroKeyTest, PublicPrivateKey) {
  // Check interoperability
  checkInterop(*key_, *public_key_);

  // Check the private key by itself
  checkEncryptingAndDecrypting(*key_);

  // encrypt the key with another key (here actually itself)
  std::string encrypted_key = key_->ToJsonEncrypted(*public_key_);
  scoped_ptr<MitroPrivateKey> key2(key_->DecryptPrivateKey(encrypted_key));
  scoped_ptr<MitroPublicKey> public_key2(key2->ExportPublicKey());
  checkEncryptingAndDecrypting(*key2);
  checkInterop(*key2, *public_key_);
  checkInterop(*key2, *public_key2);
  checkInterop(*key_, *public_key2);
}

TEST_F(MitroKeyTest, DecryptPrivateKeyErrors) {
  // bad input data
  ASSERT_TRUE(key_->DecryptPrivateKey("") == NULL);
  ASSERT_TRUE(key_->DecryptPrivateKey("{}") == NULL);
  ASSERT_TRUE(key_->DecryptPrivateKey(key_data_) == NULL);
  ASSERT_TRUE(key_->DecryptPrivateKey(password_key_data_) == NULL);

  // Encrypt the key, decrypt with wrong key
}

TEST_F(MitroKeyTest, PasswordPrivateKey) {
  // Read a password-protected private key, without the password
  scoped_ptr<MitroPrivateKey> key(MitroPrivateKey::ReadNew(password_key_data_));
  ASSERT_TRUE(key == NULL);

  // Read it with the password: success!
  key.reset(MitroPrivateKey::ReadNewEncrypted(password_key_data_, PASSWORD));
  ASSERT_TRUE(key != NULL);

  // verify the key works
  checkEncryptingAndDecrypting(*key);
}

TEST(MitroKey, ExportPublicKeyNull) {
  mitro::MitroPrivateKey key;
  ASSERT_EQ(NULL, key.ExportPublicKey());
}

static const char COMPAT_MESSAGE[] = "TEST";
static const char COMPAT_PASSWORD[] = "UJ85tzyL";

// Test compatibility with the js version of keyczar.
// We supply a string that has been encrypted with the js implementation.
// Using the same private key, check to make sure it can be decrypted.
TEST(MitroKey, CompatibilityTest) {
  mitro::MitroPrivateKey key;
  std::string encrypted_key = ReadFileOrDie("compatibility_privatekey_encrypted.json");
  ASSERT_TRUE(key.ReadEncrypted(encrypted_key, COMPAT_PASSWORD));

  std::string data = ReadFileOrDie("compatibility_data.txt");

  // ReadFileToString seems to add a '\n'.
  if (!data.empty() && data[data.size() - 1] == '\n') {
    data = std::string(data, 0, data.size() - 1);
  }

  std::string message;
  ASSERT_TRUE(key.Decrypt(data, &message));

  ASSERT_EQ(COMPAT_MESSAGE, message);
}

TEST(MitroKey, Generate) {
  scoped_ptr<MitroPrivateKey> key(MitroPrivateKey::Generate());
  checkEncryptingAndDecrypting(*key);

  // export the public key and test interop
  scoped_ptr<mitro::MitroPublicKey> public_key(key->ExportPublicKey());
  checkInterop(*key, *public_key);

  // Export to JSON and re-read it
  std::string public_string = public_key->ToJson();
  scoped_ptr<MitroPublicKey> public_key2(MitroPublicKey::ReadNew(public_string));
  checkInterop(*key, *public_key2);
}
