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
#ifndef KEYCZAR_RSA_PUBLIC_KEY_H_
#define KEYCZAR_RSA_PUBLIC_KEY_H_

#include <string>

#include <keyczar/base/basictypes.h>
#include <keyczar/base/scoped_ptr.h>
#include <keyczar/base/values.h>
#include <keyczar/public_key.h>

namespace keyczar {

class RSAImpl;

class RSAPublicKey : public PublicKey {
 public:
  // Takes ownership of |rsa_impl|. The best way to instanciate this class
  // is to call the factory method CreateFromValue.
  RSAPublicKey(RSAImpl* rsa_impl, int size)
      : PublicKey(size), rsa_impl_(rsa_impl) {}

  // Creates a key from |root_key|. The caller takes ownership of the returned
  // Key.
  static RSAPublicKey* CreateFromValue(const Value& root_key);

  // The caller takes ownership of the returned Value.
  virtual Value* GetValue() const;

  virtual bool Hash(std::string* hash) const;

  virtual bool Verify(const std::string& data,
                      const std::string& signature) const;

  virtual bool Encrypt(const std::string& plaintext,
                       std::string* ciphertext) const;

 private:
  friend class RSATest;

  // The caller doesn't take ownership over the returned object.
  RSAImpl* rsa_impl() const { return rsa_impl_.get(); }

  scoped_ptr<RSAImpl> rsa_impl_;

  DISALLOW_COPY_AND_ASSIGN(RSAPublicKey);
};

}  // namespace keyczar

#endif  // KEYCZAR_RSA_PUBLIC_KEY_H_
