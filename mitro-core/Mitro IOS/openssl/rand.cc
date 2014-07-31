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
#include <keyczar/openssl/rand.h>

#include <keyczar/base/build_config.h>
#include <keyczar/base/logging.h>
#include <keyczar/openssl/util.h>

namespace keyczar {

namespace openssl {

bool RandOpenSSL::Init() {
#if defined(OS_LINUX) || defined(OS_BSD) || defined(OS_MACOSX)
  // It seems that on Linux and *BSD seeding is made transparently, see:
  // http://www.openssl.org/docs/crypto/RAND_add.html#DESCRIPTION
#else
  // Appropriate seeding might be needed on others architectures.
  NOTIMPLEMENTED();
  return false;
#endif
  is_initialized_ = true;
  return true;
}

bool RandOpenSSL::RandBytes(int num, std::string* bytes) const {
  if (bytes == NULL)
    return false;

  unsigned char buffer[num];

  if (RAND_bytes(buffer, num) != 1) {
    PrintOSSLErrors();
    return false;
  }

  bytes->assign(reinterpret_cast<char*>(buffer), num);
  return true;
}

}  // namespace openssl

}  // namespace keyczar
