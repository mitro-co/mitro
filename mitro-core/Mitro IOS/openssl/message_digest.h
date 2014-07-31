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
#ifndef KEYCZAR_OPENSSL_MESSAGE_DIGEST_H_
#define KEYCZAR_OPENSSL_MESSAGE_DIGEST_H_

#include <openssl/evp.h>

#include <string>

#include <keyczar/base/basictypes.h>
#include <keyczar/base/scoped_ptr.h>
#include <keyczar/message_digest_impl.h>
#include <keyczar/openssl/util.h>

namespace keyczar {

namespace openssl {

class MessageDigestOpenSSL : public MessageDigestImpl {
 public:
  explicit MessageDigestOpenSSL(DigestAlgorithm algorithm);

  virtual bool Init();

  virtual bool Update(const std::string& data);

  virtual bool Final(std::string* digest);

  virtual int Size() const;

 private:
  typedef scoped_ptr_malloc<
    EVP_MD_CTX, openssl::OSSLDestroyer<
    EVP_MD_CTX, EVP_MD_CTX_destroy> > ScopedEVPMDCtx;

  // Message digest context.
  ScopedEVPMDCtx context_;

  // Hash function.
  const EVP_MD* (*evp_md_)();

  // The caller keeps ownership over the engine. Note: the use of a true engine
  // is currently not supported.
  ENGINE* engine_;

  DISALLOW_COPY_AND_ASSIGN(MessageDigestOpenSSL);
};

}  // namespace openssl

}  // namespace keyczar

#endif  // KEYCZAR_OPENSSL_MESSAGE_DIGEST_H_
