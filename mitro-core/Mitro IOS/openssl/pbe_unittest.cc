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
#include <openssl/opensslv.h>

#include <string>

#include <testing/gtest/include/gtest/gtest.h>

#include <keyczar/base/scoped_ptr.h>
#include <keyczar/keyczar_test.h>
#include <keyczar/openssl/pbe.h>
#include <keyczar/pbe_impl.h>

namespace keyczar {

namespace openssl {

class PBEOpenSSLTest : public KeyczarTest {
};

TEST_F(PBEOpenSSLTest, AES_HMAC_SHA1) {
  const int iteration_count = 4096;
  const std::string password("foobar");

  scoped_ptr<PBEOpenSSL> pbe(PBEOpenSSL::Create(PBEImpl::AES128,
                                                PBEImpl::HMAC_SHA1,
                                                iteration_count,
                                                password));
  ASSERT_TRUE(pbe.get());

  std::string iv, salt, ciphertext;
  EXPECT_TRUE(pbe->Encrypt(input_data_, &ciphertext, &salt, &iv));
  EXPECT_GE(iv.size(), 16);
  EXPECT_GE(salt.size(), 8);

  std::string decrypted;
  EXPECT_TRUE(pbe->Decrypt(salt, iv, ciphertext, &decrypted));
  EXPECT_EQ(input_data_, decrypted);
}

#if OPENSSL_VERSION_NUMBER >= 0x10000000L
TEST_F(PBEOpenSSLTest, AES_HMAC_SHA256) {
  const int iteration_count = 4096;
  const std::string password("foobar");

  scoped_ptr<PBEOpenSSL> pbe(PBEOpenSSL::Create(PBEImpl::AES128,
                                                PBEImpl::HMAC_SHA256,
                                                iteration_count,
                                                password));
  ASSERT_TRUE(pbe.get());

  std::string iv, salt, ciphertext;
  EXPECT_TRUE(pbe->Encrypt(input_data_, &ciphertext, &salt, &iv));
  EXPECT_GE(iv.size(), 16);
  EXPECT_GE(salt.size(), 8);

  std::string decrypted;
  EXPECT_TRUE(pbe->Decrypt(salt, iv, ciphertext, &decrypted));
  EXPECT_EQ(input_data_, decrypted);
}
#endif

}  // namespace openssl

}  // namespace keyczar
