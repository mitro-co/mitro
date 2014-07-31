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
#ifndef KEYCZAR_AES_KEY_H_
#define KEYCZAR_AES_KEY_H_

#include <string>

#include <keyczar/base/basictypes.h>
#include <keyczar/base/scoped_ptr.h>
#include <keyczar/base/values.h>
#include <keyczar/cipher_mode.h>
#include <keyczar/secret_key.h>
#include <keyczar/aes_impl.h>
#include <keyczar/hmac_key.h>

namespace keyczar {

// This class represents an AES key. It manages a concrete key of abstract type
// AESImpl. Its concrete implementation depends on the crypto underlying library
// used e.g. OpenSSL. This key also has a CipherMode and an HMACKey aggregated
// to it.
class AESKey : public SecretKey {
 public:
  // Takes ownership of |aes_impl|, |cipher_mode| and |hmac_key|.
  AESKey(AESImpl* aes_impl, CipherMode::Type cipher_mode, int size,
         HMACKey* hmac_key)
      : SecretKey(size, hmac_key), cipher_mode_(cipher_mode),
        aes_impl_(aes_impl) {}

  virtual ~AESKey() {}

  // Creates a key from |root_key|. The caller takes ownership of the returned
  // Key.
  static AESKey* CreateFromValue(const Value& root_key);

  // Generates a |size| bits key. The caller takes ownership of the returned
  // Key.
  static AESKey* GenerateKey(int size);

  // Returns the main data structures of this key. The caller takes ownership
  // of the returned Value.
  virtual Value* GetValue() const;

  // Returns the hash value of this key.
  virtual bool Hash(std::string* hash) const;

  // Returns the buggy hash value of this key, if any.
  virtual bool BuggyHash(std::string* hash) const;

  virtual bool Encrypt(const std::string& plaintext,
                       std::string* ciphertext) const;

  virtual bool Decrypt(const std::string& ciphertext,
                       std::string* plaintext) const;

 private:
  FRIEND_TEST(AESTest, GenerateKeyDumpAndCompare);

  bool ComputeHash(std::string* hash, bool buggy) const;

  // The caller doesn't take ownership over the returned AESKey object.
  AESImpl* aes_impl() const { return aes_impl_.get(); }

  CipherMode::Type cipher_mode_;

  scoped_ptr<AESImpl> aes_impl_;

  DISALLOW_COPY_AND_ASSIGN(AESKey);
};

}  // namespace keyczar

#endif  // KEYCZAR_AES_KEY_H_
