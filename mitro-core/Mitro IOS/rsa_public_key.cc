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
#include <keyczar/rsa_public_key.h>

#include <keyczar/base/base64w.h>
#include <keyczar/base/logging.h>
#include <keyczar/base/values.h>
#include <keyczar/crypto_factory.h>
#include <keyczar/key_type.h>
#include <keyczar/key_util.h>
#include <keyczar/message_digest_impl.h>

namespace keyczar {

// static
RSAPublicKey* RSAPublicKey::CreateFromValue(const Value& root_key) {
  if (!root_key.IsType(Value::TYPE_DICTIONARY))
    return NULL;
  const DictionaryValue* public_key = static_cast<const DictionaryValue*>(
      &root_key);

  RSAImpl::RSAIntermediateKey intermediate_key;

  if (!util::DeserializeString(*public_key, "modulus", &intermediate_key.n))
    return NULL;
  if (!util::DeserializeString(*public_key, "publicExponent",
                               &intermediate_key.e))
    return NULL;

  int size;
  if (!public_key->GetInteger("size", &size))
    return NULL;

  scoped_ptr<RSAImpl> rsa_public_key_impl(
      CryptoFactory::CreatePublicRSA(intermediate_key));
  if (rsa_public_key_impl.get() == NULL)
    return NULL;

  // Check the provided size is valid.
  if (size != rsa_public_key_impl->Size() ||
      !KeyType::IsValidCipherSize(KeyType::RSA_PUB, size))
    return NULL;

  return new RSAPublicKey(rsa_public_key_impl.release(), size);
}

Value* RSAPublicKey::GetValue() const {
  scoped_ptr<DictionaryValue> public_key(new DictionaryValue);
  if (public_key.get() == NULL)
    return NULL;

  RSAImpl::RSAIntermediateKey intermediate_key;
  if (!rsa_impl()->GetPublicAttributes(&intermediate_key))
    return NULL;

  if (!util::SerializeString(intermediate_key.n, "modulus", public_key.get()))
    return NULL;
  if (!util::SerializeString(intermediate_key.e, "publicExponent",
                             public_key.get()))
    return NULL;

  if (!public_key->SetInteger("size", size()))
    return NULL;

  return public_key.release();
}

bool RSAPublicKey::Hash(std::string* hash) const {
  if (hash == NULL)
    return false;

  RSAImpl::RSAIntermediateKey key;
  if (!rsa_impl()->GetPublicAttributes(&key))
    return false;

  // Builds a message digest based on public attributes
  MessageDigestImpl* digest_impl = CryptoFactory::SHA1();
  if (digest_impl == NULL)
    return false;

  digest_impl->Init();
  AddToHash(key.n, *digest_impl);
  AddToHash(key.e, *digest_impl);
  std::string full_hash;
  digest_impl->Final(&full_hash);
  CHECK_LE(Key::GetHashSize(), static_cast<int>(full_hash.length()));

  base::Base64WEncode(full_hash.substr(0, Key::GetHashSize()), hash);
  return true;
}

bool RSAPublicKey::Verify(const std::string& data,
                          const std::string& signature) const {
  if (rsa_impl() == NULL)
    return false;

  MessageDigestImpl* digest_impl = CryptoFactory::SHAFromFFCIFCSize(size());
  if (digest_impl == NULL)
    return false;

  std::string message_digest;
  if (!digest_impl->Digest(data, &message_digest))
    return false;

  return rsa_impl()->Verify(digest_impl->digest_algorithm(),
                            message_digest, signature);
}

bool RSAPublicKey::Encrypt(const std::string& plaintext,
                           std::string* ciphertext) const {
  if (rsa_impl() == NULL || ciphertext == NULL)
    return false;

  std::string header;
  if (!Header(&header))
    return false;

  std::string encrypted;
  if (!rsa_impl()->Encrypt(plaintext, &encrypted))
    return false;

  ciphertext->assign(header + encrypted);
  return true;
}

}  // namespace keyczar
