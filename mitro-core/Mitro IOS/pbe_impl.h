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
#ifndef KEYCZAR_PBE_IMPL_H_
#define KEYCZAR_PBE_IMPL_H_

#include <string>

#include <keyczar/base/basictypes.h>
#include <keyczar/base/build_config.h>

namespace keyczar {

// Cryptographic PBE interface.
class PBEImpl {
 public:
  enum CipherAlgorithm {
    UNDEF_CIPHER,
    AES128,
  };

  // Although HMAC_SHA256 is specified in this abstract interface, a concrete
  // implementation might not be able to accept this algorithm and will return
  // an error. For instance openssl/pbe.cc only supports HMAC_SHA1 for versions
  // of OpenSSL < 1.0.0
  enum HMACAlgorithm {
    UNDEF_HMAC,
    HMAC_SHA1,
    HMAC_SHA256,
  };

  static CipherAlgorithm GetCipher(const std::string& name);

  static HMACAlgorithm GetHMAC(const std::string& name);

  PBEImpl(CipherAlgorithm cipher_algorithm, HMACAlgorithm hmac_algorithm,
          int iteration_count)
      : cipher_algorithm_(cipher_algorithm),
        hmac_algorithm_(hmac_algorithm),
        iteration_count_(iteration_count) {}

  virtual ~PBEImpl() {}

  virtual bool Encrypt(const std::string& plaintext, std::string* ciphertext,
                       std::string* salt, std::string* iv) const = 0;

  virtual bool Decrypt(const std::string& salt, const std::string& iv,
                       const std::string& ciphertext,
                       std::string* plaintext) const = 0;

  CipherAlgorithm cipher_algorithm() const { return cipher_algorithm_; }

  std::string cipher_algorithm_name() const;

  HMACAlgorithm hmac_algorithm() const { return hmac_algorithm_; }

  std::string hmac_algorithm_name() const;

  int iteration_count() const { return iteration_count_; }

 private:
  const CipherAlgorithm cipher_algorithm_;

  const HMACAlgorithm hmac_algorithm_;

  const int iteration_count_;

  DISALLOW_COPY_AND_ASSIGN(PBEImpl);
};

}  // namespace keyczar

#endif  // KEYCZAR_PBE_IMPL_H_
