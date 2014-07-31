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
#include <keyczar/openssl/message_digest.h>

#include <keyczar/base/logging.h>

namespace keyczar {

namespace openssl {

MessageDigestOpenSSL::MessageDigestOpenSSL(DigestAlgorithm digest_algorithm)
    : MessageDigestImpl(digest_algorithm), context_(EVP_MD_CTX_create()),
      evp_md_(NULL), engine_(NULL) {
  switch (digest_algorithm) {
    case SHA1:
      evp_md_ = EVP_sha1;
      break;
    case SHA224:
      evp_md_ = EVP_sha224;
      break;
    case SHA256:
      evp_md_ = EVP_sha256;
      break;
    case SHA384:
      evp_md_ = EVP_sha384;
      break;
    case SHA512:
      evp_md_ = EVP_sha512;
      break;
    default:
      NOTREACHED();
  }
}

bool MessageDigestOpenSSL::Init() {
  if (context_.get() == NULL || evp_md_ == NULL)
    return false;

  if (!EVP_DigestInit_ex(context_.get(), evp_md_(), engine_))
    return false;

  return true;
}

bool MessageDigestOpenSSL::Update(const std::string& data) {
  if (context_.get() == NULL)
    return false;

  if (!EVP_DigestUpdate(context_.get(), data.data(), data.length()))
    return false;

  return true;
}

bool MessageDigestOpenSSL::Final(std::string* digest) {
  if (digest == NULL || context_.get() == NULL)
    return false;

  unsigned char md_buffer[EVP_MAX_MD_SIZE];
  uint32 md_len = 0;
  if (!EVP_DigestFinal_ex(context_.get(), md_buffer, &md_len))
    return false;

  digest->assign(reinterpret_cast<char*>(md_buffer), md_len);
  return true;
}

int MessageDigestOpenSSL::Size() const {
  if (evp_md_ == NULL)
    return 0;
  return EVP_MD_size(evp_md_());
}

}  // namespace openssl

}  // namespace keyczar
