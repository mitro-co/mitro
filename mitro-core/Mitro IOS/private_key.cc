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
#include <keyczar/private_key.h>

#include <keyczar/base/values.h>
#include <keyczar/public_key.h>

namespace keyczar {

PrivateKey::PrivateKey(PublicKey* public_key, int size)
    : Key(size), public_key_(public_key) {}

PrivateKey::~PrivateKey() {}

Value* PrivateKey::GetPublicKeyValue() const {
  return public_key()->GetValue();
}

bool PrivateKey::Hash(std::string* hash) const {
  if (public_key() == NULL || hash == NULL)
    return false;
  return public_key()->Hash(hash);
}

bool PrivateKey::Verify(const std::string& data,
                        const std::string& signature) const {
  if (public_key() == NULL)
    return false;

  return public_key()->Verify(data, signature);
}

bool PrivateKey::Encrypt(const std::string& plaintext,
                         std::string* ciphertext) const {
  if (public_key() == NULL)
    return false;

  return public_key()->Encrypt(plaintext, ciphertext);
}

}  // namespace keyczar
