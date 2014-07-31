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
#ifndef KEYCZAR_PRIVATE_KEY_H_
#define KEYCZAR_PRIVATE_KEY_H_

#include <string>

#include <keyczar/base/basictypes.h>
#include <keyczar/base/ref_counted.h>
#include <keyczar/key.h>

namespace keyczar {

class PublicKey;

// Abstract private key class. Each private key is associated to its public key.
// Leaf private key classes will access their public key through this class.
class PrivateKey : public Key {
 public:
  PrivateKey(PublicKey* public_key, int size);

  virtual ~PrivateKey() = 0;

  virtual Value* GetPublicKeyValue() const;

  virtual bool Hash(std::string* hash) const;

  virtual bool Verify(const std::string& data,
                      const std::string& signature) const;

  virtual bool Encrypt(const std::string& plaintext,
                       std::string* ciphertext) const;

  // The caller doesn't take ownership over the returned PublicKey object.
  const PublicKey* public_key() const { return public_key_.get(); }

 private:
  scoped_refptr<PublicKey> public_key_;

  DISALLOW_COPY_AND_ASSIGN(PrivateKey);
};

}  // namespace keyczar

#endif  // KEYCZAR_PRIVATE_KEY_H_
