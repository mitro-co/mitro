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
#include <keyczar/openssl/ecdsa.h>

namespace keyczar {

namespace openssl {

class ECDSAOpenSSLTest : public KeyczarTest {
};

TEST(ECDSAOpenSSL, CreateKeyAndCompare) {
  scoped_ptr<ECDSAOpenSSL> ecdsa_generated(
      ECDSAOpenSSL::GenerateKey(ECDSAImpl::PRIME192V1));
  ASSERT_TRUE(ecdsa_generated.get());

  ECDSAImpl::ECDSAIntermediateKey intermediate_key;
  ASSERT_TRUE(ecdsa_generated->GetAttributes(&intermediate_key));
  scoped_ptr<ECDSAOpenSSL> ecdsa_created(ECDSAOpenSSL::Create(intermediate_key,
                                                              true));
  ASSERT_TRUE(ecdsa_created.get());
  EXPECT_TRUE(ecdsa_generated->Equals(*ecdsa_created));

  ECDSAImpl::ECDSAIntermediateKey intermediate_public_key;
  ASSERT_TRUE(ecdsa_generated->GetPublicAttributes(&intermediate_public_key));
  scoped_ptr<ECDSAOpenSSL> ecdsa_public(ECDSAOpenSSL::Create(
                                            intermediate_public_key, false));
  ASSERT_TRUE(ecdsa_public.get());
  ecdsa_generated->private_key_ = false;
  EXPECT_TRUE(ecdsa_generated->Equals(*ecdsa_public));
}

TEST_F(ECDSAOpenSSLTest, GenerateKeyAndSign) {
  MessageDigestOpenSSL digest(MessageDigestImpl::SHA256);
  std::string message_digest;
  EXPECT_TRUE(digest.Digest(input_data_, &message_digest));

  scoped_ptr<ECDSAOpenSSL> ecdsa(
      ECDSAOpenSSL::GenerateKey(ECDSAImpl::PRIME256V1));
  ASSERT_TRUE(ecdsa.get());

  std::string signed_message_digest;
  EXPECT_TRUE(ecdsa->Sign(message_digest, &signed_message_digest));
  EXPECT_TRUE(ecdsa->Verify(message_digest, signed_message_digest));
}

TEST_F(ECDSAOpenSSLTest, ExportPrivateKey) {
  scoped_ptr<ECDSAOpenSSL> ecdsa(
      ECDSAOpenSSL::GenerateKey(ECDSAImpl::PRIME192V1));
  ASSERT_TRUE(ecdsa.get());

  const FilePath pem_file = temp_path_.Append("ecdsa_priv.pem");
  const std::string passphrase("cartman");

  EXPECT_TRUE(ecdsa->ExportPrivateKey(pem_file.value(), &passphrase));
  EXPECT_TRUE(base::PathExists(pem_file));

  scoped_ptr<ECDSAOpenSSL> ecdsa_imported(ECDSAOpenSSL::CreateFromPEMPrivateKey(
                                              pem_file.value(), &passphrase));
  ASSERT_TRUE(ecdsa_imported.get());
  EXPECT_TRUE(ecdsa->Equals(*ecdsa_imported));
}

// Keys were created with these commands:
//
// openssl ecparam -out ec_param.pem -name prime256v1
// openssl ecparam -in ec_param.pem -genkey -out ec_priv.pem
// openssl ecparam -in ec_param.pem -genkey | openssl ec -aes256 -out
//    ec_priv_encrypted.pem
//    with 'cartman' as passphrase
TEST_F(ECDSAOpenSSLTest, CreateFromPEMPrivateKey) {
  const FilePath ec_pem = data_path_.Append("ec_pem");

  scoped_ptr<ECDSAOpenSSL> ecdsa(ECDSAOpenSSL::CreateFromPEMPrivateKey(
                                     ec_pem.Append("ec_priv.pem").value(),
                                     NULL));
  EXPECT_TRUE(ecdsa.get());

  const std::string passphrase("cartman");
  ecdsa.reset(ECDSAOpenSSL::CreateFromPEMPrivateKey(
                  ec_pem.Append("ec_priv_encrypted.pem").value(),
                  &passphrase));
  EXPECT_TRUE(ecdsa.get());
}

}  // namespace openssl

}  // namespace keyczar
