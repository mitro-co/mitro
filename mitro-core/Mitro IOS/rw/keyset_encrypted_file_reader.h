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
#ifndef KEYCZAR_RW_KEYSET_ENCRYPTED_FILE_READER_H_
#define KEYCZAR_RW_KEYSET_ENCRYPTED_FILE_READER_H_

#include <string>

#include <keyczar/base/basictypes.h>
#include <keyczar/base/file_path.h>
#include <keyczar/base/scoped_ptr.h>
#include <keyczar/base/values.h>
#include <keyczar/rw/keyset_file_reader.h>

namespace keyczar {

class Crypter;

namespace rw {

// A JSON reader for reading encrypted keys from 'encrypted' keysets.
// In these keysets only the metadata is not encrypted.
// It requires an appropriate Crypter instance for decrypting them.
class KeysetEncryptedJSONFileReader : public KeysetJSONFileReader {
 public:
  // |dirname| is the path of the encrypted JSON keyset and |crypter| is
  // the Crypter instance used for decrypting ecnrypted keys. This class
  // takes ownership of |crypter|.
  KeysetEncryptedJSONFileReader(const std::string& dirname, Crypter* crypter);

  KeysetEncryptedJSONFileReader(const FilePath& dirname, Crypter* crypter);

  // This function transparently decrypts the key |version| and returns its
  // unencrypted value.
  virtual Value* ReadKey(int version) const;

 private:
  scoped_ptr<Crypter> crypter_;

  DISALLOW_COPY_AND_ASSIGN(KeysetEncryptedJSONFileReader);
};

}  // namespace rw
}  // namespace keyczar

#endif  // KEYCZAR_RW_KEYSET_ENCRYPTED_FILE_READER_H_
