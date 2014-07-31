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
#ifndef KEYCZAR_RW_KEYSET_FILE_READER_H_
#define KEYCZAR_RW_KEYSET_FILE_READER_H_

#include <string>

#include <keyczar/base/basictypes.h>
#include <keyczar/base/file_path.h>
#include <keyczar/base/stl_util-inl.h>
#include <keyczar/base/values.h>
#include <keyczar/rw/keyset_reader.h>

namespace keyczar {
namespace rw {

// A class reading key sets from JSON structured files.
class KeysetJSONFileReader : public KeysetReader {
 public:
  // |dirname| is the string path of the keyset to read.
  explicit KeysetJSONFileReader(const std::string& dirname);

  // |dirname| is the FilePath of the keyset to read.
  explicit KeysetJSONFileReader(const FilePath& dirname);

  // Read the metadata. The caller takes ownership of the returned value.
  virtual Value* ReadMetadata() const;

  // Read the key |version|. The caller takes ownership of the returned value.
  virtual Value* ReadKey(int version) const;

  FilePath dirname() const { return dirname_; }

 private:
  const FilePath dirname_;
  const FilePath metadata_basename_;

  DISALLOW_COPY_AND_ASSIGN(KeysetJSONFileReader);
};

// A class reading PBE (Password-based encryption) key sets from JSON
// structured files.
class KeysetPBEJSONFileReader : public KeysetJSONFileReader {
 public:
  // |dirname| is the string path of the keyset to read. Read keys are
  // |password| protected.
  KeysetPBEJSONFileReader(const std::string& dirname,
                          const std::string& password);

  // |dirname| is the FilePath of the keyset to read. Read keys are
  // |password| protected.
  KeysetPBEJSONFileReader(const FilePath& dirname,
                          const std::string& password);

  // Read the key |version| with |password_|. The caller takes ownership
  // of the returned value.
  virtual Value* ReadKey(int version) const;

  // Read the key |version| with |password|. The caller takes
  // ownership of the returned value.
  Value* ReadPBEKey(int version, const std::string& password) const;

 private:
  const base::ScopedSafeString password_;

  DISALLOW_COPY_AND_ASSIGN(KeysetPBEJSONFileReader);
};

}  // namespace rw
}  // namespace keyczar

#endif  // KEYCZAR_RW_KEYSET_FILE_READER_H_
