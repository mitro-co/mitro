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
#include <keyczar/rsa_private_key.h>

#include <keyczar/base/base64w.h>
#include <keyczar/base/logging.h>
#include <keyczar/base/values.h>
#include <keyczar/crypto_factory.h>
#include <keyczar/key_type.h>
#include <keyczar/key_util.h>

namespace keyczar {

// static
RSAPrivateKey* RSAPrivateKey::CreateFromValue(const Value& root_key) {
  if (!root_key.IsType(Value::TYPE_DICTIONARY))
    return NULL;
  const DictionaryValue* private_key = static_cast<const DictionaryValue*>(
      &root_key);

  RSAImpl::RSAIntermediateKey intermediate_key;

  if (!util::SafeDeserializeString(*private_key, "privateExponent",
                                   &intermediate_key.d))
    return NULL;
  if (!util::SafeDeserializeString(*private_key, "primeP", &intermediate_key.p))
    return NULL;
  if (!util::SafeDeserializeString(*private_key, "primeQ", &intermediate_key.q))
    return NULL;
  if (!util::SafeDeserializeString(*private_key, "primeExponentP",
                                   &intermediate_key.dmp1))
    return NULL;
  if (!util::SafeDeserializeString(*private_key, "primeExponentQ",
                                   &intermediate_key.dmq1))
    return NULL;
  if (!util::SafeDeserializeString(*private_key, "crtCoefficient",
                                   &intermediate_key.iqmp))
    return NULL;

  int size;
  if (!private_key->GetInteger("size", &size))
    return NULL;

  DictionaryValue* public_key = NULL;
  if (!private_key->GetDictionary("publicKey", &public_key))
    return NULL;

  if (public_key == NULL)
    return NULL;

  if (!util::DeserializeString(*public_key, "modulus", &intermediate_key.n))
    return NULL;
  if (!util::DeserializeString(*public_key, "publicExponent",
                               &intermediate_key.e))
    return NULL;

  int size_public;
  if (!public_key->GetInteger("size", &size_public))
    return NULL;

  scoped_ptr<RSAImpl> rsa_private_key_impl(
      CryptoFactory::CreatePrivateRSA(intermediate_key));
  if (rsa_private_key_impl.get() == NULL)
    return NULL;

  // Check the provided size is valid.
  if (size != size_public || size != rsa_private_key_impl->Size() ||
      !KeyType::IsValidCipherSize(KeyType::RSA_PRIV, size))
    return NULL;

  scoped_ptr<RSAImpl> rsa_public_key_impl(
      CryptoFactory::CreatePublicRSA(intermediate_key));
  if (rsa_public_key_impl.get() == NULL)
    return NULL;

  RSAPublicKey* rsa_public_key = new RSAPublicKey(rsa_public_key_impl.release(),
                                                  size);
  if (rsa_public_key == NULL)
    return NULL;

  return new RSAPrivateKey(rsa_private_key_impl.release(),
                           rsa_public_key,
                           size);
}

// static
RSAPrivateKey* RSAPrivateKey::GenerateKey(int size) {
  if (!KeyType::IsValidCipherSize(KeyType::RSA_PRIV, size))
    return NULL;

  scoped_ptr<RSAImpl> rsa_private_key_impl(
      CryptoFactory::GeneratePrivateRSA(size));
  if (rsa_private_key_impl.get() == NULL)
    return NULL;

  RSAImpl::RSAIntermediateKey intermediate_public_key;
  if (!rsa_private_key_impl->GetPublicAttributes(&intermediate_public_key))
     return NULL;

  scoped_ptr<RSAImpl> rsa_public_key_impl(
      CryptoFactory::CreatePublicRSA(intermediate_public_key));
  if (rsa_public_key_impl.get() == NULL)
    return NULL;

  RSAPublicKey* rsa_public_key = new RSAPublicKey(rsa_public_key_impl.release(),
                                                  size);
  if (rsa_public_key == NULL)
    return NULL;

  return new RSAPrivateKey(rsa_private_key_impl.release(),
                           rsa_public_key,
                           size);
}

// static
RSAPrivateKey* RSAPrivateKey::CreateFromPEMPrivateKey(
    const std::string& filename, const std::string* passphrase) {
  scoped_ptr<RSAImpl> rsa_private_key_impl(
      CryptoFactory::CreatePrivateRSAFromPEMPrivateKey(filename, passphrase));
  if (rsa_private_key_impl.get() == NULL)
    return NULL;

  const int size = rsa_private_key_impl->Size();
  if (!KeyType::IsValidCipherSize(KeyType::RSA_PRIV, size))
    return NULL;

  RSAImpl::RSAIntermediateKey intermediate_public_key;
  if (!rsa_private_key_impl->GetPublicAttributes(&intermediate_public_key))
     return NULL;

  scoped_ptr<RSAImpl> rsa_public_key_impl(
      CryptoFactory::CreatePublicRSA(intermediate_public_key));
  if (rsa_public_key_impl.get() == NULL)
    return NULL;

  RSAPublicKey* rsa_public_key = new RSAPublicKey(rsa_public_key_impl.release(),
                                                  size);
  if (rsa_public_key == NULL)
    return NULL;

  return new RSAPrivateKey(rsa_private_key_impl.release(),
                           rsa_public_key,
                           size);
}

Value* RSAPrivateKey::GetValue() const {
  scoped_ptr<DictionaryValue> private_key(new DictionaryValue);
  if (private_key.get() == NULL)
    return NULL;

  RSAImpl::RSAIntermediateKey intermediate_key;
  if (!rsa_impl()->GetAttributes(&intermediate_key))
    return NULL;

  if (!util::SafeSerializeString(intermediate_key.d, "privateExponent",
                                 private_key.get()))
    return NULL;
  if (!util::SafeSerializeString(intermediate_key.p, "primeP",
                                 private_key.get()))
    return NULL;
  if (!util::SafeSerializeString(intermediate_key.q, "primeQ",
                                 private_key.get()))
    return NULL;
  if (!util::SafeSerializeString(intermediate_key.dmp1, "primeExponentP",
                                 private_key.get()))
    return NULL;
  if (!util::SafeSerializeString(intermediate_key.dmq1, "primeExponentQ",
                                 private_key.get()))
    return NULL;
  if (!util::SafeSerializeString(intermediate_key.iqmp, "crtCoefficient",
                                 private_key.get()))
    return NULL;

  if (!private_key->SetInteger("size", size()))
    return NULL;

  Value* public_key_value = public_key()->GetValue();
  if (public_key_value == NULL)
    return NULL;

  if (!private_key->Set("publicKey", public_key_value))
    return NULL;

  return private_key.release();
}

bool RSAPrivateKey::ExportPrivateKey(const std::string& filename,
                                     const std::string* passphrase) const {
  if (rsa_impl() == NULL)
    return false;
  return rsa_impl()->ExportPrivateKey(filename, passphrase);
}

bool RSAPrivateKey::Sign(const std::string& data,
                         std::string* signature) const {
  if (rsa_impl() == NULL || signature == NULL)
    return false;

  MessageDigestImpl* digest_impl = CryptoFactory::SHAFromFFCIFCSize(size());
  if (digest_impl == NULL)
    return false;

  std::string message_digest;
  if (!digest_impl->Digest(data, &message_digest))
    return false;

  return rsa_impl()->Sign(digest_impl->digest_algorithm(),
                          message_digest, signature);
}

bool RSAPrivateKey::Decrypt(const std::string& ciphertext,
                            std::string* plaintext) const {
  if (rsa_impl() == NULL || plaintext == NULL)
    return false;

  return rsa_impl()->Decrypt(ciphertext.substr(Key::GetHeaderSize()),
                             plaintext);
}

}  // namespace keyczar
