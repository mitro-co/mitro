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
#include <keyczar/openssl/aes.h>

#include <keyczar/base/logging.h>
#include <keyczar/crypto_factory.h>
#include <keyczar/rand_impl.h>

namespace keyczar {

namespace openssl {

AESOpenSSL::~AESOpenSSL() {
}

// static
AESOpenSSL* AESOpenSSL::Create(CipherMode::Type cipher_mode,
                               const std::string& key) {
  // Only CBC mode is currently supported
  if (cipher_mode != CipherMode::CBC) {
    NOTREACHED();
    return NULL;
  }

  int key_length = key.length() * 8;
  switch (key_length) {
    case 128:
      return new AESOpenSSL(EVP_aes_128_cbc, key);
    case 192:
      return new AESOpenSSL(EVP_aes_192_cbc, key);
    case 256:
      return new AESOpenSSL(EVP_aes_256_cbc, key);
    default:
      NOTREACHED();
  }
  return NULL;
}

// static
AESOpenSSL* AESOpenSSL::GenerateKey(CipherMode::Type cipher_mode, int size) {
  RandImpl* rand_impl = CryptoFactory::Rand();
  if (rand_impl == NULL)
    return NULL;

  base::ScopedSafeString key(new std::string());
  if (!rand_impl->RandBytes(size / 8, key.get()))
    return NULL;
  CHECK_EQ(static_cast<int>(key->size()), size / 8);

  return AESOpenSSL::Create(cipher_mode, *key);
}

bool AESOpenSSL::Encrypt(const std::string& plaintext, std::string* ciphertext,
                         std::string* iv) const {
  if (!EncryptInit(iv))
    return false;

  if (!EncryptUpdate(plaintext, ciphertext))
    return false;

  if (!EncryptFinal(ciphertext))
    return false;

  return true;
}

bool AESOpenSSL::EncryptInit(std::string* iv) const {
  if (evp_cipher_ == NULL || iv == NULL)
    return false;

  RandImpl* rand_impl = CryptoFactory::Rand();
  if (rand_impl == NULL)
    return false;

  // Generate a random IV
  // Note: OpenSSL only takes the 16 first bytes of the iv string for the
  // AES ciphers (128, 192, 256).
  if (!rand_impl->RandBytes(EVP_CIPHER_iv_length(evp_cipher_()), iv))
    return false;

  bool init = CipherInit(*iv, true, encryption_context_.get());
  if (!init)
    EncryptContextCleanup();
  return init;
}

bool AESOpenSSL::EncryptUpdate(const std::string& plaintext,
                               std::string* ciphertext) const {
  bool update = CipherUpdate(plaintext, ciphertext, encryption_context_.get());
  if (!update)
    EncryptContextCleanup();
  return update;
}

bool AESOpenSSL::EncryptFinal(std::string* ciphertext) const {
  bool final = CipherFinal(ciphertext, encryption_context_.get());
  EncryptContextCleanup();
  return final;
}

bool AESOpenSSL::Decrypt(const std::string& iv, const std::string& ciphertext,
                         std::string* plaintext) const {
  if (!DecryptInit(iv))
    return false;

  if (!DecryptUpdate(ciphertext, plaintext))
    return false;

  if (!DecryptFinal(plaintext))
    return false;

  return true;
}

bool AESOpenSSL::DecryptInit(const std::string& iv) const {
  bool init = CipherInit(iv, false, decryption_context_.get());
  if (!init)
    DecryptContextCleanup();
  return init;
}

bool AESOpenSSL::DecryptUpdate(const std::string& ciphertext,
                               std::string* plaintext) const {
  bool update = CipherUpdate(ciphertext, plaintext, decryption_context_.get());
  if (!update)
    DecryptContextCleanup();
  return update;
}

bool AESOpenSSL::DecryptFinal(std::string* plaintext) const {
  bool final = CipherFinal(plaintext, decryption_context_.get());
  DecryptContextCleanup();
  return final;
}

AESOpenSSL::AESOpenSSL(const EVP_CIPHER* (*evp_cipher)(),
                       const std::string& key)
    : evp_cipher_(evp_cipher), key_(new std::string(key)),
      encryption_context_(EVP_CIPHER_CTX_new()),
      decryption_context_(EVP_CIPHER_CTX_new()),
      engine_(NULL) {
  CHECK(EVP_CIPHER_key_length(evp_cipher()) == static_cast<int>(key_->size()));
}

void AESOpenSSL::EncryptContextCleanup() const {
  EVP_CIPHER_CTX_cleanup(encryption_context_.get());
}

void AESOpenSSL::DecryptContextCleanup() const {
  EVP_CIPHER_CTX_cleanup(decryption_context_.get());
}

bool AESOpenSSL::CipherInit(const std::string& iv, bool encrypt,
                            EVP_CIPHER_CTX* context) const {
  int do_encrypt = 0;
  if (encrypt)
    do_encrypt = 1;

  if (context == NULL || evp_cipher_ == NULL)
    return false;

  if (EVP_CipherInit_ex(context,
                        evp_cipher_(),
                        engine_,
                        reinterpret_cast<unsigned char*>(
                            const_cast<char*>(key_->data())),
                        reinterpret_cast<unsigned char*>(
                            const_cast<char*>(iv.data())),
                        do_encrypt) != 1)
    return false;
  return true;
}

bool AESOpenSSL::CipherUpdate(const std::string& in_data, std::string* out_data,
                              EVP_CIPHER_CTX* context) const {
  if (out_data == NULL || context == NULL || evp_cipher_ == NULL)
    return false;

  int current_size = out_data->size();
  base::STLStringResizeUninitialized(out_data, current_size + in_data.length() +
                                     evp_cipher_()->block_size);

  int out_data_len = 0;
  if (EVP_CipherUpdate(context,
                       reinterpret_cast<unsigned char*>(
                           base::string_as_array(out_data) + current_size),
                       &out_data_len,
                       reinterpret_cast<unsigned char*>(
                           const_cast<char*>(in_data.data())),
                       in_data.length()) != 1)
    return false;

  CHECK_LT(static_cast<uint32>(out_data_len),
           in_data.length() + evp_cipher_()->block_size);
  out_data->resize(current_size + out_data_len);
  return true;
}

bool AESOpenSSL::CipherFinal(std::string* out_data,
                             EVP_CIPHER_CTX* context) const {
  if (out_data == NULL || context == NULL || evp_cipher_ == NULL)
    return false;

  int current_size = out_data->size();
  base::STLStringResizeUninitialized(out_data,
                                     current_size + evp_cipher_()->block_size);

  int out_data_len = 0;
  if (EVP_CipherFinal_ex(context,
                         reinterpret_cast<unsigned char*>(
                             base::string_as_array(out_data) + current_size),
                         &out_data_len) != 1)
    return false;

  CHECK_LE(out_data_len, evp_cipher_()->block_size);
  out_data->resize(current_size + out_data_len);
  return true;
}

int AESOpenSSL::GetKeySize() const {
  int key_length = static_cast<int>(key_->size());
  CHECK_GE(evp_cipher_ && key_length, EVP_CIPHER_iv_length(evp_cipher_()));
  return key_length;
}

}  // namespace openssl

}  // namespace keyczar
