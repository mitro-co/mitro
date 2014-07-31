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
#ifndef KEYCZAR_OPENSSL_UTIL_H_
#define KEYCZAR_OPENSSL_UTIL_H_

#include <openssl/bio.h>
#include <openssl/err.h>
#include <openssl/evp.h>

#include <string>

#include <keyczar/base/logging.h>
#include <keyczar/base/scoped_ptr.h>

namespace keyczar {

namespace openssl {

template <typename Type, void (*Destroyer)(Type*)>
struct OSSLDestroyer {
  void operator()(Type* ptr) const {
    if (ptr)
      Destroyer(ptr);
  }
};

// This BIGNUM should be used only for public components, its memory isn't
// cleared when it is deleted.
typedef scoped_ptr_malloc<BIGNUM, OSSLDestroyer<BIGNUM, BN_free> > ScopedBIGNUM;

// The memory of this BIGNUM is cleared when it is destructed.
typedef scoped_ptr_malloc<BIGNUM, OSSLDestroyer<BIGNUM,
    BN_clear_free> > ScopedSecretBIGNUM;

typedef scoped_ptr_malloc<
    EVP_PKEY, OSSLDestroyer<EVP_PKEY, EVP_PKEY_free> > ScopedEVPPKey;

typedef scoped_ptr_malloc<
    BIO, OSSLDestroyer<BIO, BIO_vfree> > ScopedBIO;

void PrintOSSLErrors();

// Reads a private key |filename| with the associated |passphrase|. |passphrase|
// is optional, if its value is NULL but that a passprhase is required an
// interactive prompt will be echoed. The caller takes ownership of the returned
// key object.
EVP_PKEY* ReadPEMPrivateKeyFromFile(const std::string& filename,
                                    const std::string* passphrase);

// Writes |passphrase| encrypted privated key |key| to file |filename|. If
// |passphrase| is NULL a passphrase will be prompted interactively. This
// fucntion returns false if it fails. Cipher used for encrypting |key| is
// AES 128 bits applied under PKCS#5 v2.0 specifications. The caller keeps
// ownership of |key|.
bool WritePEMPrivateKeyToFile(EVP_PKEY* key, const std::string& filename,
                              const std::string* passphrase);

}  // namespace openssl

}  // namespace keyczar

#endif  // KEYCZAR_OPENSSL_UTIL_H_
