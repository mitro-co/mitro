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
#ifndef KEYCZAR_RW_KEYSET_WRITER_H_
#define KEYCZAR_RW_KEYSET_WRITER_H_

#include <keyczar/base/basictypes.h>
#include <keyczar/base/values.h>
#include <keyczar/key.h>
#include <keyczar/keyset.h>
#include <keyczar/keyset_metadata.h>

namespace keyczar {
namespace rw {

// Abstract Writer class. This class is also implemented as an Observer
// for being notified by the class Keyset when the metadata or the keys
// have to be written.
class KeysetWriter : public Keyset::Observer {
 public:
  KeysetWriter() {}
  virtual ~KeysetWriter() {}

  // Abstract method for writing |metadata|.
  virtual bool WriteMetadata(const Value& metadata) const = 0;

  // Abstract method for writing |key| of number |version|.
  virtual bool WriteKey(const Value& key, int version) const = 0;

  // This method is called each time the metadata is updated and should be
  // written back.
  virtual void OnUpdatedKeysetMetadata(const KeysetMetadata& key_metadata);

  // This method is called when a new key is added to the key set. This key
  // must also be written with its writer.
  virtual void OnNewKey(const Key& key, int version_number);

  // This method is called when a key is revoked. Currently this function
  // does nothing but the key |version_number| could be deleted from its
  // support by its writer as well.
  virtual void OnRevokedKey(int version_number);

 private:
  DISALLOW_COPY_AND_ASSIGN(KeysetWriter);
};

}  // namespace rw
}  // namespace keyczar

#endif  // KEYCZAR_RW_KEYSET_WRITER_H_
