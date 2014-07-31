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
#include <keyczar/openssl/hmac.h>

#include <keyczar/base/logging.h>
#include <keyczar/crypto_factory.h>
#include <keyczar/rand_impl.h>

namespace keyczar {

namespace openssl {

HMACOpenSSL::~HMACOpenSSL() {
  HMAC_CTX_cleanup(&context_);
}

// static
HMACOpenSSL* HMACOpenSSL::Create(DigestAlgorithm digest_algorithm,
                                 const std::string& key) {
  const EVP_MD* (*evp_md)();

  switch (digest_algorithm) {
    case HMACImpl::SHA1:
      evp_md = EVP_sha1;
      break;
    case HMACImpl::SHA224:
      evp_md = EVP_sha224;
      break;
    case HMACImpl::SHA256:
      evp_md = EVP_sha256;
      break;
    case HMACImpl::SHA384:
      evp_md = EVP_sha384;
      break;
    case HMACImpl::SHA512:
      evp_md = EVP_sha512;
      break;
    default:
      NOTREACHED();
      evp_md = NULL;
  }

  if (evp_md == NULL)
    return NULL;

  if (key.length() < static_cast<uint32>(EVP_MD_size(evp_md()))) {
    LOG(ERROR) << "HMAC key size must at least be equal to its output length";
    return NULL;
  }

  return new HMACOpenSSL(evp_md, key);
}

// static
HMACOpenSSL* HMACOpenSSL::GenerateKey(DigestAlgorithm digest_algorithm,
                                      int size) {
  RandImpl* rand_impl = CryptoFactory::Rand();
  if (rand_impl == NULL)
    return NULL;

  base::ScopedSafeString key(new std::string());
  if (!rand_impl->RandBytes(size / 8, key.get()))
    return NULL;
  CHECK_EQ(static_cast<int>(key->size()), size / 8);

  return HMACOpenSSL::Create(digest_algorithm, *key);
}

bool HMACOpenSSL::Init() {
  if (evp_md_ == NULL)
    return false;

  // TODO(seb): for backward compatibility the return value avalaible in the
  // most recent versions of openssl is currently ignored.
  HMAC_Init_ex(&context_,
               reinterpret_cast<unsigned char*>(
                   const_cast<char*>(key_->data())),
               key_->size(),
               evp_md_(),
               engine_);
  return true;
}

bool HMACOpenSSL::Update(const std::string& data) {
  // TODO(seb): for backward compatibility the return value avalaible in the
  // most recent versions of openssl is currently ignored.
  HMAC_Update(&context_,
              reinterpret_cast<unsigned char*>(
                  const_cast<char*>(data.data())),
              data.length());
  return true;
}

bool HMACOpenSSL::Final(std::string* digest) {
  if (digest == NULL)
    return false;

  unsigned char md_buffer[EVP_MAX_MD_SIZE];
  uint32 md_len = 0;

  // TODO(seb): for backward compatibility the return value avalaible in the
  // most recent versions of openssl is currently ignored.
  HMAC_Final(&context_, md_buffer, &md_len);
  digest->assign(reinterpret_cast<char*>(md_buffer), md_len);
  return true;
}

HMACOpenSSL::HMACOpenSSL(const EVP_MD* (*evp_md)(), const std::string& key)
    : evp_md_(evp_md), key_(new std::string(key)), engine_(NULL) {
  // Initializes the hmac context.
  HMAC_CTX_init(&context_);
}

}  // namespace openssl

}  // namespace keyczar
