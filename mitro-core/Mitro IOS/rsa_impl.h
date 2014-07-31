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
#ifndef KEYCZAR_RSA_IMPL_H_
#define KEYCZAR_RSA_IMPL_H_

#include <string>

#include <keyczar/base/basictypes.h>
#include <keyczar/base/stl_util-inl.h>
#include <keyczar/message_digest_impl.h>

namespace keyczar {

// Cryptographic RSA interface.
class RSAImpl {
 public:
  // This structure will be used for retrieving in a generic way the values
  // of these fields from the concrete implementations.
  struct RSAIntermediateKey {
    std::string n;     // public modulus
    std::string e;     // public exponent
    std::string d;     // private exponent
    std::string p;     // secret prime factor
    std::string q;     // secret prime factor
    std::string dmp1;  // d mod (p-1
    std::string dmq1;  // d mod (q-1)
    std::string iqmp;  // q^-1 mod p

    ~RSAIntermediateKey() {
      base::STLStringMemErase(&d);
      base::STLStringMemErase(&p);
      base::STLStringMemErase(&q);
      base::STLStringMemErase(&dmp1);
      base::STLStringMemErase(&dmq1);
      base::STLStringMemErase(&iqmp);
    }
  };

  RSAImpl() {}
  virtual ~RSAImpl() {}

  virtual bool ExportPrivateKey(const std::string& filename,
                                const std::string* passphrase) const = 0;

  // Through this method the concrete implementation copies all its internal
  // private and public fields into |key|. This function returns true on
  // success.
  virtual bool GetAttributes(RSAIntermediateKey* key) = 0;

  // In this case only public attributes are copied into |key|.
  virtual bool GetPublicAttributes(RSAIntermediateKey* key) = 0;

  virtual bool Sign(const MessageDigestImpl::DigestAlgorithm digest_algorithm,
                    const std::string& message,
                    std::string* signature) const = 0;

  virtual bool Verify(const MessageDigestImpl::DigestAlgorithm digest_algorithm,
                      const std::string& message,
                      const std::string& signature) const = 0;

  virtual bool Encrypt(const std::string& data,
                       std::string* encrypted) const = 0;

  virtual bool Decrypt(const std::string& encrypted,
                       std::string* data) const = 0;

  // Returns the key size in bits.
  virtual int Size() const = 0;

 private:
  DISALLOW_COPY_AND_ASSIGN(RSAImpl);
};

}  // namespace keyczar

#endif  // KEYCZAR_RSA_IMPL_H_
