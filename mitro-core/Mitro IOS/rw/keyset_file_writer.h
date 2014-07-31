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
#ifndef KEYCZAR_RW_KEYSET_FILE_WRITER_H_
#define KEYCZAR_RW_KEYSET_FILE_WRITER_H_

#include <string>

#include <keyczar/base/basictypes.h>
#include <keyczar/base/file_path.h>
#include <keyczar/base/stl_util-inl.h>
#include <keyczar/base/values.h>
#include <keyczar/rw/keyset_writer.h>

namespace keyczar {
namespace rw {

// Concrete class writing key sets to JSON to structured files.
class KeysetJSONFileWriter : public KeysetWriter {
 public:
  // |dirname| is the string path of the keyset to write.
  explicit KeysetJSONFileWriter(const std::string& dirname);

  // |dirname| is the FilePath of the keyset to write.
  explicit KeysetJSONFileWriter(const FilePath& dirname);

  // Writes |metadata| to file 'meta' inside the keyset directory.
  virtual bool WriteMetadata(const Value& metadata) const;

  // Writes |key| to file |version| inside the keyset directory.
  virtual bool WriteKey(const Value& key, int version) const;

  FilePath dirname() const { return dirname_; }

 private:
  const FilePath dirname_;
  const FilePath metadata_basename_;

  DISALLOW_COPY_AND_ASSIGN(KeysetJSONFileWriter);
};

// Concrete class writing PBE (Password-based encryption) key sets to JSON
// to structured files.
class KeysetPBEJSONFileWriter : public KeysetJSONFileWriter {
 public:
  // |dirname| is the string path of the keyset to write. Dumped keys are
  // |password| protected.
  KeysetPBEJSONFileWriter(const std::string& dirname,
                          const std::string& password);

  // |dirname| is the FilePath of the keyset to write Dumped keys are
  // |password| protected.
  KeysetPBEJSONFileWriter(const FilePath& dirname,
                          const std::string& password);

  // Writes |key| to file |version| inside keyset directory. Uses |password_|
  // for key encryption.
  virtual bool WriteKey(const Value& key, int version) const;

  // Writes |key| to file |version| inside keyset directory. Uses |password|
  // for key encryption.
  bool WritePBEKey(const Value& key, int version,
                   const std::string& password) const;

 private:
  const base::ScopedSafeString password_;

  DISALLOW_COPY_AND_ASSIGN(KeysetPBEJSONFileWriter);
};

}  // namespace rw
}  // namespace keyczar

#endif  // KEYCZAR_RW_KEYSET_FILE_WRITER_H_
