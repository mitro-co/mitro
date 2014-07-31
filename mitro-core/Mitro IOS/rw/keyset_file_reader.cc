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
#include <keyczar/rw/keyset_file_reader.h>

#include <keyczar/base/file_util.h>
#include <keyczar/base/json_value_serializer.h>
#include <keyczar/base/scoped_ptr.h>
#include <keyczar/base/string_util.h>
#include <keyczar/crypto_factory.h>
#include <keyczar/key_util.h>
#include <keyczar/pbe_impl.h>

namespace {

Value* ReadJSONFile(const FilePath& file) {
  keyczar::base::JSONFileValueSerializer json_serializer(file);
  std::string error;
  scoped_ptr<Value> root(json_serializer.Deserialize(&error));
  if (root.get() == NULL) {
    LOG(ERROR) << error;
    return NULL;
  }
  return root.release();
}

}  // namespace

namespace keyczar {
namespace rw {

static Value* DecryptPBEKey(const DictionaryValue& pbe_value,
                            const std::string& password) {
  std::string cipher_string;
  if (!pbe_value.GetString("cipher", &cipher_string))
    return NULL;
  const PBEImpl::CipherAlgorithm cipher = PBEImpl::GetCipher(cipher_string);
  if (cipher == PBEImpl::UNDEF_CIPHER)
    return NULL;

  std::string hmac_string;
  if (!pbe_value.GetString("hmac", &hmac_string))
    return NULL;
  const PBEImpl::HMACAlgorithm hmac = PBEImpl::GetHMAC(hmac_string);
  if (hmac == PBEImpl::UNDEF_HMAC)
    return NULL;

  int iteration_count;
  if (!pbe_value.GetInteger("iterationCount", &iteration_count))
    return NULL;

  std::string salt;
  if (!util::DeserializeString(pbe_value, "salt", &salt))
    return NULL;

  std::string iv;
  if (!util::DeserializeString(pbe_value, "iv", &iv))
    return NULL;

  std::string encrypted_key;
  if (!util::DeserializeString(pbe_value, "key", &encrypted_key))
    return NULL;

  scoped_ptr<PBEImpl> pbe_impl(
    CryptoFactory::CreatePBE(cipher, hmac, iteration_count, password));
  if (pbe_impl.get() == NULL)
    return NULL;

  std::string decrypted_key;
  if (!pbe_impl->Decrypt(salt, iv, encrypted_key, &decrypted_key))
    return NULL;

  base::JSONStringValueSerializer json_serializer(decrypted_key);
  std::string error;
  scoped_ptr<Value> key_value(json_serializer.Deserialize(&error));
  if (key_value.get() == NULL) {
    LOG(ERROR) << error;
    return NULL;
  }
  return key_value.release();
}

KeysetJSONFileReader::KeysetJSONFileReader(const std::string& dirname)
    : dirname_(dirname), metadata_basename_("meta") {
  CHECK(base::PathExists(dirname_));
}

KeysetJSONFileReader::KeysetJSONFileReader(const FilePath& dirname)
    : dirname_(dirname), metadata_basename_("meta") {
  CHECK(base::PathExists(dirname_));
}

Value* KeysetJSONFileReader::ReadMetadata() const {
  FilePath metadata_file = dirname_.Append(metadata_basename_);
  if (!base::PathExists(metadata_file))
    return NULL;
  return ReadJSONFile(metadata_file);
}

Value* KeysetJSONFileReader::ReadKey(int version) const {
  FilePath key_file = dirname_.Append(FilePath(IntToString(version)));
  if (!base::PathExists(key_file))
    return NULL;
  return ReadJSONFile(key_file);
}

KeysetPBEJSONFileReader::KeysetPBEJSONFileReader(const std::string& dirname,
                                                 const std::string& password)
    : KeysetJSONFileReader(dirname), password_(new std::string(password)) {
}

KeysetPBEJSONFileReader::KeysetPBEJSONFileReader(const FilePath& dirname,
                                                 const std::string& password)
    : KeysetJSONFileReader(dirname), password_(new std::string(password)) {
}

Value* KeysetPBEJSONFileReader::ReadKey(int version) const {
  return ReadPBEKey(version, *password_);
}

Value* KeysetPBEJSONFileReader::ReadPBEKey(int version,
                                           const std::string& password) const {
  scoped_ptr<DictionaryValue> pbe_value(
      static_cast<DictionaryValue*>(KeysetJSONFileReader::ReadKey(version)));
  if (pbe_value.get() == NULL)
    return NULL;
  return DecryptPBEKey(*pbe_value, password);
}

}  // namespace rw
}  // namespace keyczar
