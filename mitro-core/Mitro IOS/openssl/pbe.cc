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
#include <keyczar/openssl/pbe.h>

#include <openssl/asn1.h>
#include <openssl/hmac.h>
#include <openssl/opensslv.h>
#include <openssl/pkcs12.h>
#include <string.h>

#include <keyczar/base/logging.h>
#include <keyczar/base/scoped_ptr.h>
#include <keyczar/crypto_factory.h>
#include <keyczar/openssl/util.h>
#include <keyczar/rand_impl.h>


namespace {

static const int kPasswordBufferSize = 1024;

typedef scoped_ptr_malloc<X509_ALGOR,
                          keyczar::openssl::OSSLDestroyer<
                            X509_ALGOR, X509_ALGOR_free> > ScopedX509Algor;

typedef scoped_ptr_malloc<PBE2PARAM,
                          keyczar::openssl::OSSLDestroyer<
                            PBE2PARAM, PBE2PARAM_free> > ScopedPBE2Param;

typedef scoped_ptr_malloc<PBKDF2PARAM,
                          keyczar::openssl::OSSLDestroyer<
                            PBKDF2PARAM, PBKDF2PARAM_free> > ScopedPBKDF2Param;

/* Written by Dr Stephen N Henson (steve@openssl.org) for the OpenSSL
 * project 2000.
 */
/* ====================================================================
 * Copyright (c) 2000 The OpenSSL Project.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. All advertising materials mentioning features or use of this
 *    software must display the following acknowledgment:
 *    "This product includes software developed by the OpenSSL Project
 *    for use in the OpenSSL Toolkit. (http://www.OpenSSL.org/)"
 *
 * 4. The names "OpenSSL Toolkit" and "OpenSSL Project" must not be used to
 *    endorse or promote products derived from this software without
 *    prior written permission. For written permission, please contact
 *    licensing@OpenSSL.org.
 *
 * 5. Products derived from this software may not be called "OpenSSL"
 *    nor may "OpenSSL" appear in their names without prior written
 *    permission of the OpenSSL Project.
 *
 * 6. Redistributions of any form whatsoever must retain the following
 *    acknowledgment:
 *    "This product includes software developed by the OpenSSL Project
 *    for use in the OpenSSL Toolkit (http://www.OpenSSL.org/)"
 *
 * THIS SOFTWARE IS PROVIDED BY THE OpenSSL PROJECT ``AS IS'' AND ANY
 * EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE OpenSSL PROJECT OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 * ====================================================================
 *
 * This product includes cryptographic software written by Eric Young
 * (eay@cryptsoft.com).  This product includes software written by Tim
 * Hudson (tjh@cryptsoft.com).
 *
 */
// This function was copied from OpenSSL 1.0.0 crypto/asn1/x_algor.c
static int X_X509_ALGOR_set0(X509_ALGOR *alg, ASN1_OBJECT *aobj, int ptype,
                             void *pval) {
  if (!alg)
    return 0;
  if (ptype != V_ASN1_UNDEF) {
    if (alg->parameter == NULL)
      alg->parameter = ASN1_TYPE_new();
    if (alg->parameter == NULL)
      return 0;
  }
  if (alg) {
    if (alg->algorithm)
      ASN1_OBJECT_free(alg->algorithm);
    alg->algorithm = aobj;
  }
  if (ptype == 0)
    return 1;
  if (ptype == V_ASN1_UNDEF) {
    if (alg->parameter) {
      ASN1_TYPE_free(alg->parameter);
      alg->parameter = NULL;
    }
  } else {
    ASN1_TYPE_set(alg->parameter, ptype, pval);
  }
  return 1;
}

}  // namespace

namespace keyczar {
namespace openssl {

PBEOpenSSL::~PBEOpenSSL() {
  // Cleanup loaded symbols
  EVP_cleanup();
}

// static
bool PBEOpenSSL::HasPRFHMACSHA256() {
#if OPENSSL_VERSION_NUMBER >= 0x10000000L
  return true;
#else
  return false;
#endif
}

// static
PBEOpenSSL* PBEOpenSSL::Create(CipherAlgorithm cipher_algorithm,
                               HMACAlgorithm hmac_algorithm,
                               int iteration_count,
                               const std::string& password) {
  const EVP_CIPHER* (*evp_cipher)();

  switch (cipher_algorithm) {
    case PBEImpl::AES128:
      evp_cipher = EVP_aes_128_cbc;
      break;
    default:
      NOTREACHED();
      return NULL;
  }

  int prf_nid = 0;
  switch (hmac_algorithm) {
    case PBEImpl::HMAC_SHA1:
      prf_nid = NID_hmacWithSHA1;
      break;
#if OPENSSL_VERSION_NUMBER >= 0x10000000L
    case PBEImpl::HMAC_SHA256:
      prf_nid = NID_hmacWithSHA256;
      break;
#endif
    default:
      NOTREACHED();
      return NULL;
  }

  if (iteration_count < PKCS5_DEFAULT_ITER)
    return NULL;

  if (!password.empty())
    return new PBEOpenSSL(cipher_algorithm, evp_cipher, hmac_algorithm,
                          prf_nid, iteration_count, password);

  // There is no password so prompt it interactively
  char password_buffer[kPasswordBufferSize];
  if (EVP_read_pw_string(password_buffer, kPasswordBufferSize,
                         "Enter PBE password:", 0) != 0) {
    memset(password_buffer, 0, kPasswordBufferSize);
    PrintOSSLErrors();
    return NULL;
  }
  base::ScopedSafeString in_password(new std::string(password_buffer));
  memset(password_buffer, 0, kPasswordBufferSize);
  return new PBEOpenSSL(cipher_algorithm, evp_cipher, hmac_algorithm,
                        prf_nid, iteration_count, *in_password);
}

bool PBEOpenSSL::Encrypt(const std::string& plaintext, std::string* ciphertext,
                         std::string* salt, std::string* iv) const {
  if (ciphertext == NULL || salt == NULL || iv == NULL || evp_cipher_ == NULL ||
      password_.get() == NULL)
    return false;

  RandImpl* rand_impl = CryptoFactory::Rand();
  if (rand_impl == NULL)
    return false;

  // Generate random salt
  const int salt_len = 16;
  CHECK_GE(salt_len, PKCS5_SALT_LEN);
  if (!rand_impl->RandBytes(salt_len, salt))
    return false;

  // Generate random IV
  if (!rand_impl->RandBytes(EVP_CIPHER_iv_length(evp_cipher_()), iv))
    return false;

  // Set PBE2 parameters
#if OPENSSL_VERSION_NUMBER >= 0x10000000L
  ScopedX509Algor pbe(PKCS5_pbe2_set_iv(evp_cipher_(),
                                        iteration_count(),
                                        reinterpret_cast<unsigned char*>(
                                            const_cast<char*>(salt->data())),
                                        salt->size(),
                                        reinterpret_cast<unsigned char*>(
                                            const_cast<char*>(iv->data())),
                                        prf_nid_));
  if (pbe.get() == NULL) {
    PrintOSSLErrors();
    return false;
  }
#else
  ScopedX509Algor pbe(PKCS5_pbe2_set(evp_cipher_(),
                                     iteration_count(),
                                     reinterpret_cast<unsigned char*>(
                                         const_cast<char*>(salt->data())),
                                     salt->size()));
  if (pbe.get() == NULL) {
    PrintOSSLErrors();
    return false;
  }

  // Overwrite IV and PRF parameters
  if (!OverwriteParams(*iv, pbe.get()))
    return false;
#endif

  scoped_ptr_malloc<unsigned char> ct_buffer(
      reinterpret_cast<unsigned char*>(malloc(plaintext.size() +
                                              evp_cipher_()->block_size)));
  unsigned char *p_ct_buffer = ct_buffer.get();

  int ciphertext_len = 0;
  if (PKCS12_pbe_crypt(pbe.get(),
                       password_->c_str(),
                       password_->size(),
                       reinterpret_cast<unsigned char*>(
                           const_cast<char*>(plaintext.data())),
                       plaintext.size(),
                       &p_ct_buffer,
                       &ciphertext_len,
                       1) == NULL) {
    PrintOSSLErrors();
    return false;
  }

  ciphertext->assign(reinterpret_cast<char*>(p_ct_buffer), ciphertext_len);
  return true;
}

bool PBEOpenSSL::Decrypt(const std::string& salt, const std::string& iv,
                         const std::string& ciphertext,
                         std::string* plaintext) const {
  if (plaintext == NULL || evp_cipher_ == NULL || password_.get() == NULL)
    return false;

  RandImpl* rand_impl = CryptoFactory::Rand();
  if (rand_impl == NULL)
    return false;

  // Set PBE2 parameters
#if OPENSSL_VERSION_NUMBER >= 0x10000000L
  ScopedX509Algor pbe(PKCS5_pbe2_set_iv(evp_cipher_(),
                                        iteration_count(),
                                        reinterpret_cast<unsigned char*>(
                                            const_cast<char*>(salt.data())),
                                        salt.size(),
                                        reinterpret_cast<unsigned char*>(
                                            const_cast<char*>(iv.data())),
                                        prf_nid_));
  if (pbe.get() == NULL) {
    PrintOSSLErrors();
    return false;
  }
#else
  ScopedX509Algor pbe(PKCS5_pbe2_set(evp_cipher_(),
                                     iteration_count(),
                                     reinterpret_cast<unsigned char*>(
                                         const_cast<char*>(salt.data())),
                                     salt.size()));
  if (pbe.get() == NULL) {
    PrintOSSLErrors();
    return false;
  }

  // Overwrite IV and PRF parameters
  if (!OverwriteParams(iv, pbe.get()))
    return false;
#endif

  scoped_ptr_malloc<unsigned char> pt_buffer(
      reinterpret_cast<unsigned char*>(malloc(ciphertext.size() +
                                              evp_cipher_()->block_size)));
  unsigned char *p_pt_buffer = pt_buffer.get();

  int plaintext_len = 0;
  if (PKCS12_pbe_crypt(pbe.get(),
                       password_->c_str(),
                       password_->size(),
                       reinterpret_cast<unsigned char*>(
                           const_cast<char*>(ciphertext.data())),
                       ciphertext.size(),
                       &p_pt_buffer,
                       &plaintext_len,
                       0) == NULL) {
    PrintOSSLErrors();
    return false;
  }

  plaintext->assign(reinterpret_cast<char*>(p_pt_buffer), plaintext_len);
  return true;
}

PBEOpenSSL::PBEOpenSSL(CipherAlgorithm cipher_algorithm,
                       const EVP_CIPHER* (*evp_cipher)(),
                       HMACAlgorithm hmac_algorithm, int prf_nid,
                       int iteration_count, const std::string& password)
    : PBEImpl(cipher_algorithm, hmac_algorithm, iteration_count),
      evp_cipher_(evp_cipher), prf_nid_(prf_nid),
      password_(new std::string(password)) {
  // Need ciphers and digests defines
  OpenSSL_add_all_algorithms();
}

bool PBEOpenSSL::OverwriteParams(const std::string& iv, X509_ALGOR* pbe) const {
  if (pbe == NULL || evp_cipher_ == NULL)
    return false;

  // Get parameters
  const unsigned char* pbuf_pbe2 = pbe->parameter->value.sequence->data;
  int plen_pbe2 = pbe->parameter->value.sequence->length;
  ScopedPBE2Param pbe2_param(d2i_PBE2PARAM(NULL, &pbuf_pbe2, plen_pbe2));
  if (pbe2_param.get() == NULL) {
    PrintOSSLErrors();
    return false;
  }

  // Override iv
  if (pbe2_param->encryption->parameter == NULL)
    return false;
  ASN1_TYPE_free(pbe2_param->encryption->parameter);
  pbe2_param->encryption->parameter = ASN1_TYPE_new();
  if (pbe2_param->encryption->parameter == NULL) {
    PrintOSSLErrors();
    return false;
  }

  EVP_CIPHER_CTX ctx;
  EVP_CIPHER_CTX_init(&ctx);
  EVP_CipherInit_ex(&ctx,
                    evp_cipher_(),
                    NULL,
                    NULL,
                    reinterpret_cast<unsigned char*>(
                        const_cast<char*>(iv.data())),
                    0);
  if (EVP_CIPHER_param_to_asn1(&ctx, pbe2_param->encryption->parameter) < 0) {
    PrintOSSLErrors();
    return false;
  }
  EVP_CIPHER_CTX_cleanup(&ctx);

  // Override prf
  if (pbe2_param->keyfunc == NULL ||
      pbe2_param->keyfunc->parameter == NULL)
    return false;

  const unsigned char* pbuf_pbkdf2 =
      pbe2_param->keyfunc->parameter->value.sequence->data;
  int plen_pbkdf2 = pbe2_param->keyfunc->parameter->value.sequence->length;
  ScopedPBKDF2Param pbkdf2_param(d2i_PBKDF2PARAM(NULL, &pbuf_pbkdf2,
                                                 plen_pbkdf2));
  if (pbkdf2_param.get() == NULL) {
    PrintOSSLErrors();
    return false;
  }

  if (pbkdf2_param->prf != NULL)
    X509_ALGOR_free(pbkdf2_param->prf);

  pbkdf2_param->prf = X509_ALGOR_new();
  if (pbkdf2_param->prf == NULL) {
    PrintOSSLErrors();
    return false;
  }

  if (X_X509_ALGOR_set0(pbkdf2_param->prf, OBJ_nid2obj(prf_nid_),
                        V_ASN1_NULL, NULL) != 1)  {
    PrintOSSLErrors();
    return false;
  }

  if (!ASN1_item_pack(pbkdf2_param.get(), ASN1_ITEM_rptr(PBKDF2PARAM),
                      &pbe2_param->keyfunc->parameter->value.sequence)) {
    PrintOSSLErrors();
    return false;
  }

  // Finally repack parameters
  if (!ASN1_item_pack(pbe2_param.get(), ASN1_ITEM_rptr(PBE2PARAM),
                      &pbe->parameter->value.sequence)) {
    PrintOSSLErrors();
    return false;
  }

  return true;
}

}  // namespace openssl
}  // namespace keyczar
