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
#include <keyczar/openssl/dsa.h>

namespace keyczar {

namespace openssl {

class DSAOpenSSLTest : public KeyczarTest {
};

TEST(DSAOpenSSL, CreateKeyAndCompare) {
  int size = 1024;
  scoped_ptr<DSAOpenSSL> dsa_generated(DSAOpenSSL::GenerateKey(size));
  ASSERT_TRUE(dsa_generated.get());
  EXPECT_EQ(dsa_generated->Size(), size);

  DSAImpl::DSAIntermediateKey intermediate_key;
  ASSERT_TRUE(dsa_generated->GetAttributes(&intermediate_key));
  scoped_ptr<DSAOpenSSL> dsa_created(DSAOpenSSL::Create(intermediate_key,
                                                        true));
  ASSERT_TRUE(dsa_created.get());
  EXPECT_TRUE(dsa_generated->Equals(*dsa_created));

  DSAImpl::DSAIntermediateKey intermediate_public_key;
  ASSERT_TRUE(dsa_generated->GetPublicAttributes(&intermediate_public_key));
  scoped_ptr<DSAOpenSSL> dsa_public(DSAOpenSSL::Create(intermediate_public_key,
                                                       false));
  ASSERT_TRUE(dsa_public.get());
  dsa_generated->private_key_ = false;
  EXPECT_TRUE(dsa_generated->Equals(*dsa_public));
}

TEST_F(DSAOpenSSLTest, GenerateKeyAndSign) {
  MessageDigestOpenSSL digest(MessageDigestImpl::SHA1);
  std::string message_digest;
  EXPECT_TRUE(digest.Digest(input_data_, &message_digest));

  int size = 1024;
  scoped_ptr<DSAOpenSSL> dsa(DSAOpenSSL::GenerateKey(size));
  ASSERT_TRUE(dsa.get());
  EXPECT_EQ(dsa->Size(), size);

  std::string signed_message_digest;
  EXPECT_TRUE(dsa->Sign(message_digest, &signed_message_digest));
  EXPECT_TRUE(dsa->Verify(message_digest, signed_message_digest));
}

TEST_F(DSAOpenSSLTest, ExportPrivateKey) {
  int size = 2048;
  scoped_ptr<DSAOpenSSL> dsa(DSAOpenSSL::GenerateKey(size));
  ASSERT_TRUE(dsa.get());
  EXPECT_EQ(dsa->Size(), size);

  const FilePath pem_file = temp_path_.Append("dsa_priv.pem");
  const std::string passphrase("cartman");

  EXPECT_TRUE(dsa->ExportPrivateKey(pem_file.value(), &passphrase));
  EXPECT_TRUE(base::PathExists(pem_file));

  scoped_ptr<DSAOpenSSL> dsa_imported(DSAOpenSSL::CreateFromPEMPrivateKey(
                                          pem_file.value(), &passphrase));
  ASSERT_TRUE(dsa_imported.get());
  EXPECT_TRUE(dsa->Equals(*dsa_imported));
}

// Keys were created with these commands:
//
//  openssl dsaparam -genkey 1024 -out dsaparam
//  openssl gendsa -out dsa_priv.pem dsaparam
//  openssl gendsa -aes256 -out dsa_priv_encrypted.pem dsaparam
//      with 'cartman' as passphrase
TEST_F(DSAOpenSSLTest, CreateFromPEMPrivateKey) {
  const FilePath dsa_pem = data_path_.Append("dsa_pem");

  scoped_ptr<DSAOpenSSL> dsa(DSAOpenSSL::CreateFromPEMPrivateKey(
                                 dsa_pem.Append("dsa_priv.pem").value(),
                                 NULL));
  EXPECT_TRUE(dsa.get());

  const std::string passphrase("cartman");
  dsa.reset(DSAOpenSSL::CreateFromPEMPrivateKey(
                dsa_pem.Append("dsa_priv_encrypted.pem").value(),
                &passphrase));
  EXPECT_TRUE(dsa.get());
}

}  // namespace openssl

}  // namespace keyczar
