// Copyright 2011 Google Inc. All Rights Reserved.
//
// Author: Shawn Willden (swillden@google.com)
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

#include <iostream>

#include <keyczar/session.h>

#include <keyczar/aes_key.h>
#include <keyczar/base/base64w.h>
#include <keyczar/base/json_reader.h>
#include <keyczar/base/json_writer.h>
#include <keyczar/base/values.h>
#include <keyczar/crypto_factory.h>
#include <keyczar/rand_impl.h>

namespace keyczar {

const int NONCE_SIZE = 16;

class Session {
 public:
  static Session* Parse(const std::string& json_string);
  static Session* Generate(int session_key_bits);

  const Value* GetValue() const;
  bool Encrypt(const std::string& plaintext, std::string* ciphertext) const;
  bool Decrypt(const std::string& ciphertext, std::string* plaintext) const;
  const std::string& Nonce() const { return nonce_; }

 private:
  Session(Key* session_key, const std::string& nonce)
      : session_key_(session_key), nonce_(nonce) {
  }

  scoped_refptr<Key> session_key_;
  std::string nonce_;
};

Session* Session::Generate(int session_key_bits) {
  scoped_ptr<AESKey> session_key(AESKey::GenerateKey(session_key_bits));
  std::string nonce;
  const RandImpl* rand = CryptoFactory::Rand();
  if (session_key.get()
      && rand != NULL
      && rand->RandBytes(NONCE_SIZE, &nonce))
    return new Session(session_key.release(), nonce);
  return NULL;
}

Session* Session::Parse(const std::string& json_string) {
  std::string error_out;
  scoped_ptr<const Value> session_json(
      base::JSONReader::Read(json_string, false /* allow_trailing_comma */));

  if (session_json.get() == NULL
      || !session_json->IsType(Value::TYPE_DICTIONARY))
    return NULL;

  const DictionaryValue* session_dict
      = static_cast<const DictionaryValue*>(session_json.get());

  std::string encoded_nonce;
  std::string nonce;
  Value* key_json;
  if (!session_dict->GetString("nonce", &encoded_nonce)
      || !base::Base64WDecode(encoded_nonce, &nonce)
      || !session_dict->Get("key", &key_json))
    return NULL;

  scoped_ptr<AESKey> session_key(AESKey::CreateFromValue(*key_json));
  if (session_key.get() == NULL)
    return NULL;

  return new Session(session_key.release(), nonce);
}

const Value* Session::GetValue() const {
  std::string encoded_nonce;
  scoped_ptr<DictionaryValue> dict(new DictionaryValue);
  if (dict.get()
      && dict->Set("key", session_key_->GetValue())
      && base::Base64WEncode(nonce_, &encoded_nonce)
      && dict->SetString("nonce", encoded_nonce))
    return dict.release();
  return NULL;
}

bool Session::Encrypt(const std::string& plaintext,
                      std::string* ciphertext) const {
  return session_key_.get() != NULL
      && session_key_->Encrypt(plaintext, ciphertext);
}

bool Session::Decrypt(const std::string& ciphertext,
                      std::string* plaintext) const {
  return session_key_.get() != NULL
      && session_key_->Decrypt(ciphertext, plaintext);
}

SignedSessionEncrypter::~SignedSessionEncrypter() {
  delete session_;
}

bool SignedSessionEncrypter::EncryptedSessionBlob(
    std::string* session_material) const {
  scoped_ptr<const Value> json_value(session_->GetValue());
  if (json_value.get() == NULL)
    return false;

  std::string json_string;
  base::JSONWriter::Write(json_value.get(), false, &json_string);

  return encrypter_->Encrypt(json_string, session_material);
}

std::string SignedSessionEncrypter::EncryptedSessionBlob() const {
  std::string blob;
  if (!EncryptedSessionBlob(&blob))
    return "";
  return blob;
}

bool SignedSessionEncrypter::SessionEncrypt(
    const std::string& plaintext,
    std::string* signed_ciphertext) const {
  std::string ciphertext;
  if (!session_->Encrypt(plaintext, &ciphertext))
    return false;
  return signer_->AttachedSign(ciphertext, session_->Nonce(),
                               signed_ciphertext);
}

std::string SignedSessionEncrypter::SessionEncrypt(
    const std::string& plaintext) const {
  std::string signed_ciphertext;
  if (!SessionEncrypt(plaintext, &signed_ciphertext))
    return "";
  return signed_ciphertext;
}

// static
SignedSessionEncrypter* SignedSessionEncrypter::NewSessionEncrypter(
    Encrypter* encrypter,
    Signer* signer,
    int session_key_bits) {
  scoped_ptr<Encrypter> encrypter_p(encrypter);
  scoped_ptr<Signer> signer_p(signer);

  scoped_ptr<Session> session(Session::Generate(session_key_bits));
  if (encrypter_p.get() != NULL
      && signer_p.get() != NULL
      && session.get() != NULL) {
    encrypter_p->set_encoding(Keyczar::BASE64W);
    return new SignedSessionEncrypter(encrypter_p.release(),
                                      signer_p.release(),
                                      session.release());
  }
  return NULL;
}

SignedSessionDecrypter::~SignedSessionDecrypter() {
  delete session_;
}

bool SignedSessionDecrypter::SessionDecrypt(const std::string& ciphertext,
                                            std::string* plaintext) const {
  const std::string& nonce = session_->Nonce();
  std::string encrypted_data;
  return verifier_->AttachedVerify(ciphertext, nonce, &encrypted_data)
      && session_->Decrypt(encrypted_data, plaintext);
}

std::string SignedSessionDecrypter::SessionDecrypt(
    const std::string& ciphertext) const {
  std::string plaintext;
  if (!SessionDecrypt(ciphertext, &plaintext))
    return "";
  return plaintext;
}

//static
SignedSessionDecrypter* SignedSessionDecrypter::NewSessionDecrypter(
    Crypter* crypter,
    Verifier* verifier,
    const std::string& session_material) {
  if (crypter == NULL || verifier == NULL)
    return NULL;

  scoped_ptr<Crypter> crypter_p(crypter);
  scoped_ptr<Verifier> verifier_p(verifier);

  std::string decrypted_session;
  crypter_p->set_encoding(Keyczar::BASE64W);
  if (!crypter_p->Decrypt(session_material, &decrypted_session))
    return NULL;

  scoped_ptr<Session> session(Session::Parse(decrypted_session));
  if (session.get() == NULL)
    return NULL;
  return new SignedSessionDecrypter(verifier_p.release(), session.release());
}

} // namespace keyczar
