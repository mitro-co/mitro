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
#include <openssl/rand.h>

#include <string>

#include <testing/gtest/include/gtest/gtest.h>

#include <keyczar/base/scoped_ptr.h>
#include <keyczar/cipher_mode.h>
#include <keyczar/keyczar_test.h>
#include <keyczar/openssl/aes.h>

namespace keyczar {

namespace openssl {

class AESOpenSSLTest : public KeyczarTest {
};

TEST_F(AESOpenSSLTest, EncryptAndDecrypt) {
  std::string iv, encrypted, decrypted;

  for (int s = 16; s <= 32; s += 8) {
    encrypted.clear();
    decrypted.clear();

    unsigned char key_buffer[s];
    EXPECT_TRUE(RAND_bytes(key_buffer, s));
    std::string key(reinterpret_cast<char*>(key_buffer), s);

    scoped_ptr<AESOpenSSL> aes(AESOpenSSL::Create(CipherMode::CBC, key));
    ASSERT_TRUE(aes.get());

    EXPECT_TRUE(aes->Encrypt(input_data_, &encrypted, &iv));
    EXPECT_EQ(iv.length(), 16);

    EXPECT_TRUE(aes->Decrypt(iv, encrypted, &decrypted));
    EXPECT_EQ(input_data_, decrypted);

    encrypted.clear();
    decrypted.clear();

    EXPECT_TRUE(aes->EncryptInit(&iv));
    EXPECT_EQ(iv.length(), 16);
    EXPECT_TRUE(aes->EncryptUpdate(input_data_.substr(0, 5), &encrypted));
    EXPECT_TRUE(aes->EncryptUpdate(input_data_.substr(5), &encrypted));
    EXPECT_TRUE(aes->EncryptFinal(&encrypted));

    EXPECT_TRUE(aes->DecryptInit(iv));
    EXPECT_TRUE(aes->DecryptUpdate(encrypted.substr(0, 5), &decrypted));
    EXPECT_TRUE(aes->DecryptUpdate(encrypted.substr(5), &decrypted));
    EXPECT_TRUE(aes->DecryptFinal(&decrypted));
    EXPECT_EQ(iv.length(), 16);

    EXPECT_EQ(input_data_, decrypted);
  }
}

}  // namespace openssl

}  // namespace keyczar
