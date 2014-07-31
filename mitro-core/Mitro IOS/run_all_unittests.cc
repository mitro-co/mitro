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
#include <testing/gtest/include/gtest/gtest.h>

#include <keyczar/crypto_factory.h>

int main(int argc, char** argv) {
  testing::InitGoogleTest(&argc, argv);

  // Before any cryptographic operation initializes the random engine
  // (seeding...). However this step is useless under Linux with OpenSSL.
  keyczar::CryptoFactory::Rand();

  return RUN_ALL_TESTS();
}
