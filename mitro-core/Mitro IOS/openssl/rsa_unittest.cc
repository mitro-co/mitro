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

#include <keyczar/base/file_path.h>
#include <keyczar/base/file_util.h>
#include <keyczar/base/scoped_ptr.h>
#include <keyczar/keyczar_test.h>
#include <keyczar/openssl/message_digest.h>
#include <keyczar/openssl/rsa.h>

namespace keyczar {

namespace openssl {

class RSAOpenSSLTest : public KeyczarTest {
};

TEST_F(RSAOpenSSLTest, GenerateKeyAndEncrypt) {
  int size = 1024;
  scoped_ptr<RSAOpenSSL> rsa(RSAOpenSSL::GenerateKey(size));
  ASSERT_TRUE(rsa.get());

  std::string encrypted_data;
  EXPECT_TRUE(rsa->Encrypt(input_data_, &encrypted_data));
  EXPECT_EQ(static_cast<int>(encrypted_data.length()), size / 8);
  std::string decrypted_data;
  EXPECT_TRUE(rsa->Decrypt(encrypted_data, &decrypted_data));
  EXPECT_EQ(input_data_, decrypted_data);
}

TEST(RSAOpenSSL, CreateKeyAndCompare) {
  int size = 1024;
  scoped_ptr<RSAOpenSSL> rsa_generated(RSAOpenSSL::GenerateKey(size));
  ASSERT_TRUE(rsa_generated.get());
  EXPECT_EQ(rsa_generated->Size(), size);

  RSAImpl::RSAIntermediateKey intermediate_key;
  ASSERT_TRUE(rsa_generated->GetAttributes(&intermediate_key));
  scoped_ptr<RSAOpenSSL> rsa_created(RSAOpenSSL::Create(intermediate_key,
                                                        true));
  ASSERT_TRUE(rsa_created.get());
  EXPECT_TRUE(rsa_generated->Equals(*rsa_created));

  RSAImpl::RSAIntermediateKey intermediate_public_key;
  ASSERT_TRUE(rsa_generated->GetPublicAttributes(&intermediate_public_key));
  scoped_ptr<RSAOpenSSL> rsa_public(RSAOpenSSL::Create(intermediate_public_key,
                                                       false));
  ASSERT_TRUE(rsa_public.get());
  rsa_generated->private_key_ = false;
  EXPECT_TRUE(rsa_generated->Equals(*rsa_public));
}

TEST_F(RSAOpenSSLTest, GenerateKeyAndSign) {
  MessageDigestOpenSSL digest(MessageDigestImpl::SHA1);
  std::string message_digest;
  EXPECT_TRUE(digest.Digest(input_data_, &message_digest));

  int size = 1024;
  scoped_ptr<RSAOpenSSL> rsa(RSAOpenSSL::GenerateKey(size));
  ASSERT_TRUE(rsa.get());
  EXPECT_EQ(rsa->Size(), size);

  std::string signed_message_digest;
  EXPECT_TRUE(rsa->Sign(MessageDigestImpl::SHA1,
                        message_digest, &signed_message_digest));
  EXPECT_EQ(static_cast<int>(signed_message_digest.length()), size / 8);
  EXPECT_TRUE(rsa->Verify(MessageDigestImpl::SHA1,
                          message_digest, signed_message_digest));
}

TEST_F(RSAOpenSSLTest, ExportPrivateKey) {
  int size = 2048;
  scoped_ptr<RSAOpenSSL> rsa(RSAOpenSSL::GenerateKey(size));
  ASSERT_TRUE(rsa.get());
  EXPECT_EQ(rsa->Size(), size);

  const FilePath pem_file = temp_path_.Append("rsa_priv.pem");
  const std::string passphrase("cartman");

  EXPECT_TRUE(rsa->ExportPrivateKey(pem_file.value(), &passphrase));
  EXPECT_TRUE(base::PathExists(pem_file));

  scoped_ptr<RSAOpenSSL> rsa_imported(RSAOpenSSL::CreateFromPEMPrivateKey(
                                          pem_file.value(), &passphrase));
  ASSERT_TRUE(rsa_imported.get());
  EXPECT_TRUE(rsa->Equals(*rsa_imported));
}

// Keys were created with these commands:
//
//  openssl genrsa -f4 -out rsa_priv.pem 2048
//  openssl genrsa -f4 -out rsa_priv_wrong_size.pem 128
//  openssl genrsa -aes256 -f4 -out rsa_priv_encrypted.pem 2048
//    with 'cartman' as passphrase
TEST_F(RSAOpenSSLTest, CreateFromPEMPrivateKey) {
  const FilePath rsa_pem = data_path_.Append("rsa_pem");

  scoped_ptr<RSAOpenSSL> rsa(RSAOpenSSL::CreateFromPEMPrivateKey(
                                 rsa_pem.Append("rsa_priv.pem").value(),
                                 NULL));
  EXPECT_TRUE(rsa.get());

  const std::string passphrase("cartman");
  rsa.reset(RSAOpenSSL::CreateFromPEMPrivateKey(
                rsa_pem.Append("rsa_priv_encrypted.pem").value(),
                &passphrase));
  EXPECT_TRUE(rsa.get());

  // rsa.reset(RSAOpenSSL::CreateFromPEMKey(
  //              rsa_pem.Append("rsa_priv_encrypted.pem").value(),
  //              NULL));
  // EXPECT_TRUE(rsa.get());
}

}  // namespace openssl

}  // namespace keyczar
