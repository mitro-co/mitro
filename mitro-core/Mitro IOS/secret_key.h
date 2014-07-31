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
#ifndef KEYCZAR_SECRET_KEY_H_
#define KEYCZAR_SECRET_KEY_H_

#include <keyczar/base/basictypes.h>
#include <keyczar/key.h>
#include <keyczar/hmac_key.h>

namespace keyczar {

// Abstract secret key class.
class SecretKey : public Key {
 public:
  explicit SecretKey(int size, HMACKey* hmac_key)
      : Key(size), hmac_key_(hmac_key) {}

  virtual ~SecretKey() = 0;

 protected:
  // The caller doesn't take ownership over the returned HMACKey object.
  const HMACKey* hmac_key() const { return hmac_key_.get(); }

 private:
  scoped_refptr<HMACKey> hmac_key_;

  DISALLOW_COPY_AND_ASSIGN(SecretKey);
};

}  // namespace keyczar

#endif  // KEYCZAR_SECRET_KEY_H_
