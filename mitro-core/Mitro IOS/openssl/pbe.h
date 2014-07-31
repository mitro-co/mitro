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
#ifndef KEYCZAR_OPENSSL_PBE_H_
#define KEYCZAR_OPENSSL_PBE_H_

#include <string>

#include <openssl/evp.h>
#include <openssl/x509.h>

#include <keyczar/base/basictypes.h>
#include <keyczar/base/stl_util-inl.h>
#include <keyczar/pbe_impl.h>

namespace keyczar {

namespace openssl {

// Limitations: current shipped versions of OpenSSL 0.9.x have some limitations
//              in PKCS5 v2.0 implementation:
// - do not support hmac other than sha1 hmac (like e.g. NID_hmacWithSHA256)
// - do not provide PKCS5_pbe2_set_iv() (needed for setting user's iv and prf)

// OpenSSL password based encryption concrete implementation. Uses OpenSSL's
// PKCS5 v2.0 implementation.
class PBEOpenSSL : public PBEImpl {
 public:
  virtual ~PBEOpenSSL();

  // Returns true if hmac sha256 is available on this system (through its
  // linked crypto lib).
  static bool HasPRFHMACSHA256();

  // |hmac_algorithm| is restricted to HMAC_SHA1 for OpenSSL versions < 1.0.0.
  // If |password| is empty (empty string), a password will be interactively
  // prompted on stdin at creation.
  static PBEOpenSSL* Create(CipherAlgorithm cipher_algorithm,
                            HMACAlgorithm hmac_algorithm,
                            int iteration_count, const std::string& password);

  // Encrypts |plaintext| into |ciphertext| with a key derived from |password_|.
  // Corresponding IV and salt used are respectively copied into |iv| and
  // |salt|. If this method fails it returns false.
  virtual bool Encrypt(const std::string& plaintext, std::string* ciphertext,
                       std::string* salt, std::string* iv) const;

  // Recovers |plaintext| from |ciphertext| using |salt|, |iv| and |password_|.
  // If this method fails it returns false.
  virtual bool Decrypt(const std::string& salt, const std::string& iv,
                       const std::string& ciphertext,
                       std::string* plaintext) const;

 private:
  // A cipher's key is derived from |password|. |prf_nid| is a pseudo-random
  // function's identifier, |iteration_count| the number of times |prf_nid|
  // is applied and |evp_cipher| is the cipher used to encrypt the data with
  // the derived key.
  PBEOpenSSL(CipherAlgorithm cipher_algorithm,
             const EVP_CIPHER* (*evp_cipher)(),
             HMACAlgorithm hmac_algorithm, int prf_nid,
             int iteration_count, const std::string& password);

  // In an ideal world we would not have to call this method. But since
  // OpenSSL versions < 1.0.0 do not provide PKCS5_pbe2_set_iv() we have
  // to do it ourself. This method is meant to replace and insert |iv| and
  // |prf_nid_| into |pbe|.
  bool OverwriteParams(const std::string& iv, X509_ALGOR* pbe) const;

  // Encryption's cipher.
  const EVP_CIPHER* (*evp_cipher_)();

  // Pseudo-random function's identifier.
  const int prf_nid_;

  const base::ScopedSafeString password_;

  DISALLOW_COPY_AND_ASSIGN(PBEOpenSSL);
};

}  // namespace openssl

}  // namespace keyczar

#endif  // KEYCZAR_OPENSSL_PBE_H_

