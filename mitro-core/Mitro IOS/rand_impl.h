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
#ifndef KEYCZAR_RAND_IMPL_H_
#define KEYCZAR_RAND_IMPL_H_

#include <string>

#include <keyczar/base/basictypes.h>

namespace keyczar {

// Cryptographic rand interface.
class RandImpl {
 public:
  RandImpl() {}
  virtual ~RandImpl() {}

  // This function must initialize the random engine according to its
  // underlying implementation and its architecture. This function returns
  // true if the engine is correctly set up in a working state. This function
  // must be called before any call to the others function members, and before
  // calls to any cryptographic operation using random data.
  virtual bool Init() = 0;

  // Returns true if Init() was already called successfully.
  virtual bool is_initialized() const = 0;

  // Fills |bytes| with |num| random bytes and returns true.
  virtual bool RandBytes(int num, std::string* bytes) const = 0;

 private:
  DISALLOW_COPY_AND_ASSIGN(RandImpl);
};

}  // namespace keyczar

#endif  // KEYCZAR_RAND_IMPL_H_
