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
#include <keyczar/openssl/hmac.h>

namespace keyczar {

namespace openssl {

TEST(HMACOpenSSL, SHA1) {
  // Python code used for obtaining the reference hmac:
  // >>> import hmac
  // >>> import hashlib
  // >>> import base64
  // >>> a = hmac.new("my secret key my secret key","hello world!",hashlib.sha1)
  // >>> base64.urlsafe_b64encode(a.digest())
  // 'gdRgzs51Fb4yfmUM4J50aNNkLMI='

  const std::string message("hello world!");
  const std::string key("my secret key my secret key");
  const std::string digest("gdRgzs51Fb4yfmUM4J50aNNkLMI");

  scoped_ptr<HMACOpenSSL> hmac;

  hmac.reset(HMACOpenSSL::Create(HMACImpl::SHA1, key.substr(0, 19)));
  ASSERT_FALSE(hmac.get());

  hmac.reset(HMACOpenSSL::Create(HMACImpl::SHA1, key));
  ASSERT_TRUE(hmac.get());

  // Method 1
  std::string value_1;
  EXPECT_TRUE(hmac->Digest(message, &value_1));

  // Method 2
  std::string value_2;
  EXPECT_TRUE(hmac->Init());
  EXPECT_TRUE(hmac->Update(message.substr(0, 2)));
  EXPECT_TRUE(hmac->Update(message.substr(2)));
  EXPECT_TRUE(hmac->Final(&value_2));

  EXPECT_EQ(value_1, value_2);

  std::string value_1_encoded;
  base::Base64WEncode(value_1, &value_1_encoded);
  EXPECT_EQ(value_1_encoded, digest);
}

}  // namespace openssl

}  // namespace keyczar
