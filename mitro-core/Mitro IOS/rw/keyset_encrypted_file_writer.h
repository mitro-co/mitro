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
#ifndef KEYCZAR_RW_KEYSET_ENCRYPTED_FILE_WRITER_H_
#define KEYCZAR_RW_KEYSET_ENCRYPTED_FILE_WRITER_H_

#include <string>

#include <keyczar/base/basictypes.h>
#include <keyczar/base/file_path.h>
#include <keyczar/base/scoped_ptr.h>
#include <keyczar/base/values.h>
#include <keyczar/rw/keyset_file_writer.h>

namespace keyczar {

class Encrypter;

namespace rw {

// A JSON writer for writing encrypted keys.
class KeysetEncryptedJSONFileWriter : public KeysetJSONFileWriter {
 public:
  // |dirname| is the path of the encrypted JSON keyset and |encrypter| is the
  // Encrypter instance used for encrypting keys. This class takes ownership
  // of |encrypter|.
  KeysetEncryptedJSONFileWriter(const std::string& dirname,
                                Encrypter* encrypter);

  KeysetEncryptedJSONFileWriter(const FilePath& dirname, Encrypter* encrypter);

  // Transparently encrypts |key| and writes it to |version| inside the keyset
  // path. Returns true on success.
  virtual bool WriteKey(const Value& key, int version) const;

 private:
  scoped_ptr<Encrypter> encrypter_;

  DISALLOW_COPY_AND_ASSIGN(KeysetEncryptedJSONFileWriter);
};

}  // namespace rw
}  // namespace keyczar

#endif  // KEYCZAR_RW_KEYSET_ENCRYPTED_FILE_WRITER_H_

