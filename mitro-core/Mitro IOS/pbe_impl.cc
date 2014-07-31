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
#include <keyczar/pbe_impl.h>

#include <keyczar/base/logging.h>

namespace keyczar {

// static
PBEImpl::CipherAlgorithm PBEImpl::GetCipher(const std::string& name) {
  if (name == "AES128")
    return AES128;

  NOTREACHED();
  return UNDEF_CIPHER;
}

// static
PBEImpl::HMACAlgorithm PBEImpl::GetHMAC(const std::string& name) {
  if (name == "HMAC_SHA1")
    return HMAC_SHA1;
  if (name == "HMAC_SHA256")
    return HMAC_SHA256;

  NOTREACHED();
  return UNDEF_HMAC;
}

std::string PBEImpl::cipher_algorithm_name() const {
  switch (cipher_algorithm_) {
    case AES128:
      return std::string("AES128");
    default:
      NOTREACHED();
  }
  return std::string("");
}

std::string PBEImpl::hmac_algorithm_name() const {
  switch (hmac_algorithm_) {
    case HMAC_SHA1:
      return std::string("HMAC_SHA1");
    case HMAC_SHA256:
      return std::string("HMAC_SHA256");
    default:
      NOTREACHED();
  }
  return std::string("");
}

}  // namespace keyczar
