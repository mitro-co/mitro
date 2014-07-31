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
#include <keyczar/rw/keyset_file_writer.h>

#include <keyczar/base/file_util.h>
#include <keyczar/base/json_value_serializer.h>
#include <keyczar/base/scoped_ptr.h>
#include <keyczar/base/string_util.h>
#include <keyczar/crypto_factory.h>
#include <keyczar/key_util.h>
#include <keyczar/pbe_impl.h>

namespace {

bool WriteJSONFile(const FilePath& file, const Value& root) {
  keyczar::base::JSONFileValueSerializer json_serializer(file);
  return json_serializer.Serialize(root);
}

}  // namespace

namespace keyczar {
namespace rw {

static DictionaryValue* EncryptPBEKey(const Value& key,
                                      const std::string& password) {
  scoped_ptr<PBEImpl> pbe_impl(CryptoFactory::CreateNewPBE(password));
  if (pbe_impl.get() == NULL)
    return NULL;

  std::string json_key_string;
  base::JSONStringValueSerializer json_serializer(&json_key_string);
  if (!json_serializer.Serialize(key))
    return NULL;

  std::string iv, salt, encrypted_key;
  if (!pbe_impl->Encrypt(json_key_string, &encrypted_key, &salt, &iv))
    return NULL;

  scoped_ptr<DictionaryValue> pbe_value(new DictionaryValue);
  if (pbe_value.get() == NULL)
    return NULL;

  if (!pbe_value->SetString("cipher", pbe_impl->cipher_algorithm_name()))
    return NULL;

  if (!pbe_value->SetString("hmac", pbe_impl->hmac_algorithm_name()))
    return NULL;

  if (!pbe_value->SetInteger("iterationCount", pbe_impl->iteration_count()))
    return NULL;

  if (!util::SerializeString(salt, "salt", pbe_value.get()))
    return NULL;

  if (!util::SerializeString(iv, "iv", pbe_value.get()))
    return NULL;

  if (!util::SerializeString(encrypted_key, "key", pbe_value.get()))
    return NULL;

  return pbe_value.release();
}

KeysetJSONFileWriter::KeysetJSONFileWriter(const std::string& dirname)
    : dirname_(dirname), metadata_basename_("meta") {
  CHECK(base::PathExists(dirname_));
}

KeysetJSONFileWriter::KeysetJSONFileWriter(const FilePath& dirname)
    : dirname_(dirname), metadata_basename_("meta") {
  CHECK(base::PathExists(dirname_));
}

bool KeysetJSONFileWriter::WriteMetadata(const Value& metadata) const {
  if (!base::PathExists(dirname_))
    return false;
  FilePath metadata_file = dirname_.Append(metadata_basename_);
  return WriteJSONFile(metadata_file, metadata);
}

bool KeysetJSONFileWriter::WriteKey(const Value& key, int version) const {
  if (!base::PathExists(dirname_))
    return false;
  FilePath key_file = dirname_.Append(FilePath(IntToString(version)));
  return WriteJSONFile(key_file, key);
}

KeysetPBEJSONFileWriter::KeysetPBEJSONFileWriter(const std::string& dirname,
                                                 const std::string& password)
    : KeysetJSONFileWriter(dirname), password_(new std::string(password)) {
}

KeysetPBEJSONFileWriter::KeysetPBEJSONFileWriter(const FilePath& dirname,
                                                 const std::string& password)
    : KeysetJSONFileWriter(dirname), password_(new std::string(password)) {
}

bool KeysetPBEJSONFileWriter::WriteKey(const Value& key, int version) const {
  return WritePBEKey(key, version, *password_);
}

bool KeysetPBEJSONFileWriter::WritePBEKey(const Value& key, int version,
                                          const std::string& password) const {
  scoped_ptr<DictionaryValue> pbe_value(EncryptPBEKey(key, password));
  if (pbe_value.get() == NULL)
    return false;

  return KeysetJSONFileWriter::WriteKey(*pbe_value, version);
}

}  // namespace rw
}  // namespace keyczar
