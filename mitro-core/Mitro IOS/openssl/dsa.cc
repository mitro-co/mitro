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
#include <keyczar/openssl/dsa.h>

#include <openssl/bn.h>
#include <openssl/objects.h>
#include <openssl/pem.h>
#include <string.h>

#include <keyczar/base/file_util.h>
#include <keyczar/base/logging.h>
#include <keyczar/base/stl_util-inl.h>

namespace keyczar {

namespace openssl {

// static
DSAOpenSSL* DSAOpenSSL::Create(const DSAIntermediateKey& key,
                               bool private_key) {
  ScopedDSAKey dsa_key(DSA_new());
  if (dsa_key.get() == NULL) {
    PrintOSSLErrors();
    return NULL;
  }

  // p
  dsa_key->p = BN_bin2bn(reinterpret_cast<unsigned char*>(
                             const_cast<char*>(key.p.data())),
                         key.p.length(), NULL);
  if (dsa_key->p == NULL)
    return NULL;

  // q
  dsa_key->q = BN_bin2bn(reinterpret_cast<unsigned char*>(
                             const_cast<char*>(key.q.data())),
                         key.q.length(), NULL);
  if (dsa_key->q == NULL)
    return NULL;

  // g
  dsa_key->g = BN_bin2bn(reinterpret_cast<unsigned char*>(
                             const_cast<char*>(key.g.data())),
                         key.g.length(), NULL);
  if (dsa_key->g == NULL)
    return NULL;

  // pub_key
  dsa_key->pub_key = BN_bin2bn(reinterpret_cast<unsigned char*>(
                             const_cast<char*>(key.y.data())),
                         key.y.length(), NULL);
  if (dsa_key->pub_key == NULL)
    return NULL;

  if (!private_key)
    return new DSAOpenSSL(dsa_key.release(), private_key);

  // priv_key
  dsa_key->priv_key = BN_bin2bn(reinterpret_cast<unsigned char*>(
                             const_cast<char*>(key.x.data())),
                         key.x.length(), NULL);
  if (dsa_key->priv_key == NULL)
    return NULL;

  return new DSAOpenSSL(dsa_key.release(), private_key);
}

// static
DSAOpenSSL* DSAOpenSSL::GenerateKey(int size) {
  ScopedDSAKey dsa_key(DSA_new());
  if (dsa_key.get() == NULL) {
    PrintOSSLErrors();
    return NULL;
  }

  if (!DSA_generate_parameters_ex(dsa_key.get(), size, NULL, 0, NULL,
                                  NULL, NULL)) {
    PrintOSSLErrors();
    return NULL;
  }

  if (DSA_generate_key(dsa_key.get()) != 1) {
    PrintOSSLErrors();
    return NULL;
  }

  return new DSAOpenSSL(dsa_key.release(),
                        true /* private_key */);
}

// static
DSAOpenSSL* DSAOpenSSL::CreateFromPEMPrivateKey(const std::string& filename,
                                                const std::string* passphrase) {
  // Load the disk based private key.
  ScopedEVPPKey evp_pkey(ReadPEMPrivateKeyFromFile(filename, passphrase));
  if (evp_pkey.get() == NULL) {
    PrintOSSLErrors();
    return NULL;
  }

  if (evp_pkey->pkey.dsa == NULL) {
    LOG(ERROR) << "Invalid DSA private key";
    return NULL;
  }

  // Duplicate the DSA key component.
  ScopedDSAKey dsa_key(EVP_PKEY_get1_DSA(evp_pkey.get()));
  if (dsa_key.get() == NULL) {
    PrintOSSLErrors();
    return NULL;
  }

  return new DSAOpenSSL(dsa_key.release(),
                        true /* private_key */);
}

bool DSAOpenSSL::ExportPrivateKey(const std::string& filename,
                                  const std::string* passphrase) const {
  if (key_.get() == NULL || !private_key_)
    return false;

  ScopedEVPPKey evp_key(EVP_PKEY_new());
  if (evp_key.get() == NULL)
    return false;

  if (!EVP_PKEY_set1_DSA(evp_key.get(), key_.get()))
    return false;

  return WritePEMPrivateKeyToFile(evp_key.get(), filename, passphrase);
}

bool DSAOpenSSL::GetAttributes(DSAIntermediateKey* key) {
  if (key == NULL || key_.get() == NULL)
    return false;

  if (!private_key_ || !key_->priv_key)
    return false;

  if (!GetPublicAttributes(key))
    return false;

  // priv_key (x)
  int num_priv_key = BN_num_bytes(key_->priv_key);
  unsigned char priv_key[num_priv_key + 1];
  // Set the MSB to 0 to be compatible with Java implementation.
  priv_key[0] = 0;
  if (BN_bn2bin(key_->priv_key, priv_key + 1) != num_priv_key) {
    PrintOSSLErrors();
    return false;
  }
  key->x.assign(reinterpret_cast<char*>(priv_key), num_priv_key + 1);
  memset(priv_key, 0, num_priv_key + 1);

  return true;
}

bool DSAOpenSSL::GetPublicAttributes(DSAIntermediateKey* key) {
  if (key == NULL || key_.get() == NULL)
    return false;

  if (!key_->p || !key_->q || !key_->g || !key_->pub_key)
    return false;

  // p
  int num_p = BN_num_bytes(key_->p);
  unsigned char p[num_p + 1];
  // Set the MSB to 0 to be compatible with Java implementation.
  p[0] = 0;
  if (BN_bn2bin(key_->p, p + 1) != num_p) {
    PrintOSSLErrors();
    return false;
  }
  key->p.assign(reinterpret_cast<char*>(p), num_p + 1);

  // q
  int num_q = BN_num_bytes(key_->q);
  unsigned char q[num_q + 1];
  // Set the MSB to 0 to be compatible with Java implementation.
  q[0] = 0;
  if (BN_bn2bin(key_->q, q + 1) != num_q) {
    PrintOSSLErrors();
    return false;
  }
  key->q.assign(reinterpret_cast<char*>(q), num_q + 1);

  // g
  int num_g = BN_num_bytes(key_->g);
  unsigned char g[num_g + 1];
  // Set the MSB to 0 to be compatible with Java implementation.
  g[0] = 0;
  if (BN_bn2bin(key_->g, g + 1) != num_g) {
    PrintOSSLErrors();
    return false;
  }
  key->g.assign(reinterpret_cast<char*>(g), num_g + 1);

  // pub_key (y)
  int num_pub_key = BN_num_bytes(key_->pub_key);
  unsigned char pub_key[num_pub_key + 1];
  // Set the MSB to 0 to be compatible with Java implementation.
  pub_key[0] = 0;
  if (BN_bn2bin(key_->pub_key, pub_key + 1) != num_pub_key) {
    PrintOSSLErrors();
    return false;
  }
  key->y.assign(reinterpret_cast<char*>(pub_key), num_pub_key + 1);

  return true;
}

bool DSAOpenSSL::Sign(const std::string& message_digest,
                      std::string* signature) const {
  if (key_.get() == NULL || signature == NULL || !private_key_)
    return false;

  uint32 dsa_size = DSA_size(key_.get());
  base::STLStringResizeUninitialized(signature, dsa_size);

  uint32 signature_length = 0;
  if (DSA_sign(0,
               reinterpret_cast<unsigned char*>(
                   const_cast<char*>(message_digest.data())),
               message_digest.length(),
               reinterpret_cast<unsigned char*>(
                   base::string_as_array(signature)),
               &signature_length,
               key_.get()) != 1) {
    PrintOSSLErrors();
    return false;
  }

  CHECK_LE(signature_length, dsa_size);
  signature->resize(signature_length);
  return true;
}

bool DSAOpenSSL::Verify(const std::string& message_digest,
                        const std::string& signature) const {
  if (key_.get() == NULL)
    return false;

  int ret_val = 0;
  ret_val = DSA_verify(0,
                       reinterpret_cast<unsigned char*>(
                           const_cast<char*>(message_digest.data())),
                       message_digest.length(),
                       reinterpret_cast<unsigned char*>(
                           const_cast<char*>(signature.data())),
                       signature.length(),
                       key_.get());
  if (ret_val == 1)
    return true;
  if (ret_val == -1)
    PrintOSSLErrors();
  return false;
}

int DSAOpenSSL::Size() const {
  if (key_.get() == NULL)
    return 0;

  // TODO(seb): This method is not bullet proof see man BN_num_bits, but
  // currently there is no such method in openssl, DSA_size() returns
  // the signature size not the key size.
  return BN_num_bytes(key_->p) * 8;
}

bool DSAOpenSSL::Equals(const DSAOpenSSL& rhs) const {
  if (key_.get() == NULL || private_key() != rhs.private_key())
    return false;

  const DSA* key_rhs = rhs.key();

  // Compares public components.
  if (BN_cmp(key_->p, key_rhs->p) != 0 || BN_cmp(key_->q, key_rhs->q) != 0 ||
      BN_cmp(key_->g, key_rhs->g) != 0 ||
      BN_cmp(key_->pub_key, key_rhs->pub_key) != 0)
    return false;

  if (!private_key())
    return true;

  // Compares private components.
  if (BN_cmp(key_->priv_key, key_rhs->priv_key) != 0)
    return false;

  return true;
}

}  // namespace openssl

}  // namespace keyczar
