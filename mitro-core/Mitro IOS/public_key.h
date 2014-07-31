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
#ifndef KEYCZAR_PUBLIC_KEY_H_
#define KEYCZAR_PUBLIC_KEY_H_

#include <keyczar/base/basictypes.h>
#include <keyczar/key.h>

namespace keyczar {

// Abstract public key class.
class PublicKey : public Key {
 public:
  explicit PublicKey(int size) : Key(size) {}

  virtual ~PublicKey() = 0;

 private:
  DISALLOW_COPY_AND_ASSIGN(PublicKey);
};

}  // namespace keyczar

#endif  // KEYCZAR_PUBLIC_KEY_H_
