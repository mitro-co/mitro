// Copyright 2009 Sebastien Martini (seb@dbzteam.org)
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
#include <string>

#include <testing/gtest/include/gtest/gtest.h>

#include <keyczar/base/base64w.h>
#include <keyczar/base/logging.h>
#include <keyczar/base/ref_counted.h>
#include <keyczar/base/file_path.h>
#include <keyczar/base/file_util.h>
#include <keyczar/base/scoped_ptr.h>
#include <keyczar/base/values.h>
#include <keyczar/key_type.h>
#include <keyczar/keyczar_test.h>
#include <keyczar/openssl/rsa.h>
#include <keyczar/rsa_private_key.h>
#include <keyczar/rsa_public_key.h>
#include <keyczar/rw/keyset_file_reader.h>
#include <keyczar/rw/keyset_file_writer.h>

namespace keyczar {

class RSATest : public KeyczarTest {
 protected:
  // TODO(seb): add a dynamic_cast for each new rsa implementation.
  bool Equals(const RSAPrivateKey& lhs, const RSAPrivateKey& rhs) {
    const openssl::RSAOpenSSL* lhs_impl = dynamic_cast<openssl::RSAOpenSSL*>(
        lhs.rsa_impl());
    const openssl::RSAOpenSSL* rhs_impl = dynamic_cast<openssl::RSAOpenSSL*>(
        rhs.rsa_impl());
    if (!lhs_impl || !rhs_impl)
      return false;

    return lhs_impl->Equals(*rhs_impl);
  }

  // TODO(seb): add a dynamic_cast for each new rsa implementation.
  bool Equals(const RSAPublicKey& lhs, const RSAPublicKey& rhs) {
    const openssl::RSAOpenSSL* lhs_impl = dynamic_cast<openssl::RSAOpenSSL*>(
        lhs.rsa_impl());
    const openssl::RSAOpenSSL* rhs_impl = dynamic_cast<openssl::RSAOpenSSL*>(
        rhs.rsa_impl());
    if (!lhs_impl || !rhs_impl)
      return false;

    return lhs_impl->Equals(*rhs_impl);
  }

  // Loads private key from JSON file.
  scoped_refptr<RSAPrivateKey> LoadRSAPrivateKey(const FilePath& path,
                                                 int key_version) {
    rw::KeysetJSONFileReader reader(path);
    scoped_ptr<Value> value(reader.ReadKey(key_version));
    EXPECT_NE(static_cast<Value*>(NULL), value.get());
    scoped_refptr<RSAPrivateKey> private_key(
        RSAPrivateKey::CreateFromValue(*value));
    CHECK(private_key);
    return private_key;
  }

  // Loads public key from JSON file.
  scoped_refptr<RSAPublicKey> LoadRSAPublicKey(const FilePath& path,
                                               int key_version) {
    rw::KeysetJSONFileReader reader(path);
    scoped_ptr<Value> value(reader.ReadKey(key_version));
    EXPECT_NE(static_cast<Value*>(NULL), value.get());
    scoped_refptr<RSAPublicKey> public_key(RSAPublicKey::CreateFromValue(
                                               *value));
    CHECK(public_key);
    return public_key;
  }
};

TEST_F(RSATest, GeneratePrivateKeyAndPublicEncrypt) {
  const std::vector<int> sizes = KeyType::CipherSizes(KeyType::RSA_PRIV);
  scoped_refptr<RSAPrivateKey> private_key;

  for (std::vector<int>::const_iterator iter = sizes.begin();
       iter != sizes.end(); ++iter) {
    // Generates a new private key
    private_key = RSAPrivateKey::GenerateKey(*iter);
    ASSERT_TRUE(private_key.get());

    // Attempts to encrypt and decrypt input data.
    std::string encrypted_data;
    EXPECT_TRUE(private_key->Encrypt(input_data_, &encrypted_data));
    EXPECT_EQ(static_cast<int>(encrypted_data.length()),
              Key::GetHeaderSize() + *iter / 8);
    std::string decrypted_data;
    EXPECT_TRUE(private_key->Decrypt(encrypted_data, &decrypted_data));
    EXPECT_EQ(input_data_, decrypted_data);
  }
}

TEST_F(RSATest, GeneratePrivateKeyAndPrivateSign) {
  const std::vector<int> sizes = KeyType::CipherSizes(KeyType::RSA_PRIV);
  scoped_refptr<RSAPrivateKey> private_key;

  for (std::vector<int>::const_iterator iter = sizes.begin();
       iter != sizes.end(); ++iter) {
    // Generates a new private key.
    private_key = RSAPrivateKey::GenerateKey(*iter);
    ASSERT_TRUE(private_key.get());

    // Attempts to sign and verify input data.
    std::string signature;
    EXPECT_TRUE(private_key->Sign(input_data_, &signature));
    EXPECT_EQ(static_cast<int>(signature.length()), *iter / 8);
    EXPECT_TRUE(private_key->Verify(input_data_, signature));
  }
}

TEST_F(RSATest, LoadPrivateKey) {
  FilePath rsa_path = data_path_.Append("rsa");
  scoped_refptr<RSAPrivateKey> private_key = LoadRSAPrivateKey(rsa_path, 1);

  // Attempts to encrypt and decrypt input data.
  std::string encrypted_data;
  EXPECT_TRUE(private_key->Encrypt(input_data_, &encrypted_data));
  std::string decrypted_data;
  EXPECT_TRUE(private_key->Decrypt(encrypted_data, &decrypted_data));
  EXPECT_EQ(input_data_, decrypted_data);
}

TEST_F(RSATest, LoadPublicKey) {
  FilePath rsa_private_path = data_path_.Append("rsa-sign");
  scoped_refptr<RSAPrivateKey> private_key = LoadRSAPrivateKey(rsa_private_path,
                                                               1);

  // Attempts to sign data with this private key.
  std::string signature;
  EXPECT_TRUE(private_key->Sign(input_data_, &signature));

  // Loads the associated public key
  FilePath rsa_public_path = data_path_.Append("rsa-sign.public");
  scoped_refptr<RSAPublicKey> public_key = LoadRSAPublicKey(rsa_public_path, 1);

  // Attempts to verify the signature with this public key.
  EXPECT_TRUE(public_key->Verify(input_data_, signature));
}

// Steps:
// 1- Loads private key
// 2- Dumps private key to temporary file
// 3- Loads this temporary file
// 3- Signs some data with this key
// 4- Then, exports the public part into a second temporary file
// 5- Loads this file and checks that the signature is valid
TEST_F(RSATest, LoadPrivateKeyDumpAndExport) {
  {
    FilePath rsa_path = data_path_.Append("rsa-sign");
    scoped_refptr<RSAPrivateKey> private_key = LoadRSAPrivateKey(rsa_path, 1);

    // Dumps private key into temporary path
    rw::KeysetJSONFileWriter writer(temp_path_);
    EXPECT_TRUE(writer.WriteKey(*private_key->GetValue(), 1));
    ASSERT_TRUE(base::PathExists(temp_path_.Append("1")));
  }

  std::string signature;

  {
    // Loads the dumped key
    scoped_refptr<RSAPrivateKey> private_key = LoadRSAPrivateKey(temp_path_, 1);
    ASSERT_TRUE(private_key);

    // Attempts to sign data
    EXPECT_TRUE(private_key->Sign(input_data_, &signature));

    // Exports public key
    rw::KeysetJSONFileWriter writer(temp_path_);
    scoped_ptr<Value> private_key_value(private_key->GetPublicKeyValue());
    ASSERT_TRUE(private_key_value.get());
    EXPECT_TRUE(writer.WriteKey(*private_key_value, 2));
    ASSERT_TRUE(base::PathExists(temp_path_.Append("2")));
  }

  {
    // Loads public key
    scoped_refptr<RSAPublicKey> public_key = LoadRSAPublicKey(temp_path_, 2);
    ASSERT_TRUE(public_key);

    // Checks the signature
    EXPECT_TRUE(public_key->Verify(input_data_, signature));
  }
}

TEST_F(RSATest, CompareOutputHeader) {
  const FilePath rsa_path = data_path_.Append("rsa");
  scoped_refptr<RSAPrivateKey> private_key = LoadRSAPrivateKey(rsa_path, 1);

  // Loads the encrypted data file and retrieve the output header
  std::string b64w_encrypted_data;
  EXPECT_TRUE(base::ReadFileToString(rsa_path.Append("1.out"),
                                     &b64w_encrypted_data));
  std::string encrypted_data;
  EXPECT_TRUE(base::Base64WDecode(b64w_encrypted_data, &encrypted_data));
  std::string header = encrypted_data.substr(0, Key::GetHeaderSize());

  // Compares headers
  std::string key_header;
  EXPECT_TRUE(private_key->Header(&key_header));
  EXPECT_EQ(header, key_header);
}

TEST_F(RSATest, CompareDecrypt) {
  const FilePath rsa_path = data_path_.Append("rsa");
  scoped_refptr<RSAPrivateKey> private_key = LoadRSAPrivateKey(rsa_path, 1);

  // Try to decrypt corresponding data file
  std::string b64w_encrypted_data;
  EXPECT_TRUE(base::ReadFileToString(rsa_path.Append("1.out"),
                                     &b64w_encrypted_data));
  std::string encrypted_data;
  EXPECT_TRUE(base::Base64WDecode(b64w_encrypted_data, &encrypted_data));
  std::string decrypted_data;
  EXPECT_TRUE(private_key->Decrypt(encrypted_data, &decrypted_data));

  // Compares clear texts
  EXPECT_EQ(decrypted_data, input_data_);
}

TEST_F(RSATest, VerifyEncodedSignature) {
  const FilePath rsa_sign_pub_path = data_path_.Append("rsa-sign.public");
  scoped_refptr<RSAPublicKey> public_key = LoadRSAPublicKey(rsa_sign_pub_path,
                                                            2);

  // Try to verify the signature file
  std::string b64w_signature;
  FilePath signature_file = data_path_.Append("rsa-sign");
  signature_file = signature_file.Append("2.out");
  EXPECT_TRUE(base::ReadFileToString(signature_file,
                                     &b64w_signature));
  std::string signature;
  EXPECT_TRUE(base::Base64WDecode(b64w_signature, &signature));

  // Checks signature
  input_data_.push_back(Key::GetVersionByte());
  EXPECT_TRUE(public_key->Verify(input_data_,
                                 signature.substr(
                                     Key::GetHeaderSize())));
}

TEST_F(RSATest, CompareOriginalAndDumpedPrivateKey) {
  const FilePath rsa_path = data_path_.Append("rsa");
  scoped_refptr<RSAPrivateKey> original_key = LoadRSAPrivateKey(rsa_path, 1);

  // Dumps private key into temporary path
  rw::KeysetJSONFileWriter writer(temp_path_);
  EXPECT_TRUE(writer.WriteKey(*original_key->GetValue(), 1));
  ASSERT_TRUE(base::PathExists(temp_path_.Append("1")));

  // Loads the dumped key
  scoped_refptr<RSAPrivateKey> dumped_key = LoadRSAPrivateKey(temp_path_, 1);
  ASSERT_TRUE(dumped_key);

  // Expect to be the equals
  EXPECT_TRUE(Equals(*original_key, *dumped_key));
}

TEST_F(RSATest, CompareOriginalAndExportedPublicKey) {
  const FilePath rsa_path = data_path_.Append("rsa-sign");
  scoped_refptr<RSAPrivateKey> private_key = LoadRSAPrivateKey(rsa_path, 1);

  // Exports public key into temporary path
  rw::KeysetJSONFileWriter writer(temp_path_);
  scoped_ptr<Value> private_key_value(private_key->GetPublicKeyValue());
  ASSERT_TRUE(private_key_value.get());
  EXPECT_TRUE(writer.WriteKey(*private_key_value, 1));
  ASSERT_TRUE(base::PathExists(temp_path_.Append("1")));

  // Loads orginal public key
  const FilePath rsa_path_pub = data_path_.Append("rsa-sign.public");
  scoped_refptr<RSAPublicKey> public_key = LoadRSAPublicKey(rsa_path_pub, 1);
  ASSERT_TRUE(public_key);

  // Loads the dumped key
  scoped_refptr<RSAPublicKey> dumped_key = LoadRSAPublicKey(temp_path_, 1);
  ASSERT_TRUE(dumped_key);

  // Expected to be the equals
  EXPECT_TRUE(Equals(*public_key, *dumped_key));
}

TEST_F(RSATest, LoadPEMPrivateKey) {
  const FilePath rsa_pem_path = data_path_.Append("rsa_pem");
  scoped_refptr<RSAPrivateKey> private_key;

  const FilePath invalid_key = rsa_pem_path.Append(
      "rsa_priv_wrong_size.pem");
  private_key = RSAPrivateKey::CreateFromPEMPrivateKey(invalid_key.value(),
                                                       NULL);
  EXPECT_FALSE(private_key);

  const FilePath simple_key = rsa_pem_path.Append("rsa_priv.pem");
  private_key = RSAPrivateKey::CreateFromPEMPrivateKey(simple_key.value(),
                                                       NULL);
  EXPECT_TRUE(private_key);

  const std::string passphrase("cartman");
  const FilePath protected_key = rsa_pem_path.Append(
      "rsa_priv_encrypted.pem");
  private_key = RSAPrivateKey::CreateFromPEMPrivateKey(protected_key.value(),
                                                       &passphrase);
  EXPECT_TRUE(private_key);

  // Attempts to encrypt and decrypt input data.
  std::string encrypted_data;
  EXPECT_TRUE(private_key->Encrypt(input_data_, &encrypted_data));
  std::string decrypted_data;
  EXPECT_TRUE(private_key->Decrypt(encrypted_data, &decrypted_data));
  EXPECT_EQ(input_data_, decrypted_data);
}

TEST_F(RSATest, ExportAndImportPrivateKey) {
  const FilePath pem = temp_path_.Append("rsa.pem");
  const std::string password("cartman");

  scoped_refptr<RSAPrivateKey> private_key = RSAPrivateKey::GenerateKey(2048);
  ASSERT_TRUE(private_key.get());

  // Exports private key
  EXPECT_TRUE(private_key->ExportPrivateKey(pem.value(), &password));

  // Reloads private key
  scoped_refptr<RSAPrivateKey> imported_key =
      RSAPrivateKey::CreateFromPEMPrivateKey(pem.value(), &password);
  ASSERT_TRUE(imported_key.get());

  // Sign data and verify
  std::string signature;
  EXPECT_TRUE(imported_key->Sign(input_data_, &signature));
  EXPECT_TRUE(imported_key->Verify(input_data_, signature));
}

}  // namespace keyczar
