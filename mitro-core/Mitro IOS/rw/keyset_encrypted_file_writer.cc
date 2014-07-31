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
#include <keyczar/rw/keyset_encrypted_file_writer.h>

#include <keyczar/base/file_util.h>
#include <keyczar/base/json_value_serializer.h>
#include <keyczar/base/string_util.h>
#include <keyczar/base/values.h>
#include <keyczar/keyczar.h>

namespace keyczar {
namespace rw {

KeysetEncryptedJSONFileWriter::KeysetEncryptedJSONFileWriter(
    const std::string& dirname, Encrypter* encrypter)
    : KeysetJSONFileWriter(dirname), encrypter_(encrypter) {
}

KeysetEncryptedJSONFileWriter::KeysetEncryptedJSONFileWriter(
    const FilePath& dirname, Encrypter* encrypter)
    : KeysetJSONFileWriter(dirname), encrypter_(encrypter) {
}

bool KeysetEncryptedJSONFileWriter::WriteKey(const Value& key,
                                             int version) const {
  if (encrypter_.get() == NULL)
    return false;

  if (!base::PathExists(dirname()))
    return false;

  const FilePath key_file_path = dirname().Append(IntToString(version));

  std::string json_string;
  base::JSONStringValueSerializer serializer(&json_string);
  serializer.set_pretty_print(true);
  if (!serializer.Serialize(key))
    return false;

  std::string encrypted_json;
  if (!encrypter_->Encrypt(json_string, &encrypted_json))
    return false;

  int data_size = static_cast<int>(encrypted_json.length());
  if (!base::WriteStringToFile(key_file_path, encrypted_json))
    return false;
  return true;
}

}  // namespace rw
}  // namespace keyczar
