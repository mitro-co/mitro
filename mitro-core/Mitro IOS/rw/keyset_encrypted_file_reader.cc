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
#include <keyczar/rw/keyset_encrypted_file_reader.h>

#include <keyczar/base/file_util.h>
#include <keyczar/base/json_value_serializer.h>
#include <keyczar/base/logging.h>
#include <keyczar/base/string_util.h>
#include <keyczar/base/values.h>
#include <keyczar/keyczar.h>

namespace keyczar {
namespace rw {

KeysetEncryptedJSONFileReader::KeysetEncryptedJSONFileReader(
    const std::string& dirname, Crypter* crypter)
    : KeysetJSONFileReader(dirname), crypter_(crypter) {
}

KeysetEncryptedJSONFileReader::KeysetEncryptedJSONFileReader(
    const FilePath& dirname, Crypter* crypter)
    : KeysetJSONFileReader(dirname), crypter_(crypter) {
}

Value* KeysetEncryptedJSONFileReader::ReadKey(int version) const {
  if (crypter_.get() == NULL)
    return NULL;

  const FilePath key_file_path = dirname().Append(IntToString(version));
  if (!base::PathExists(key_file_path))
    return NULL;

  std::string encrypted_json;
  if (!base::ReadFileToString(key_file_path, &encrypted_json))
    return NULL;

  std::string decrypted_json;
  if (!crypter_->Decrypt(encrypted_json, &decrypted_json))
    return NULL;

  base::JSONStringValueSerializer serializer(decrypted_json);

  std::string error_message;
  scoped_ptr<Value> root(serializer.Deserialize(&error_message));
  if (root.get() == NULL) {
    LOG(ERROR) << error_message;
    return NULL;
  }
  return root.release();
}

}  // namespace rw
}  // namespace keyczar
