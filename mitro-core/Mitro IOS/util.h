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
#ifndef KEYCZAR_UTIL_H_
#define KEYCZAR_UTIL_H_

#include <string>

#include <keyczar/base/basictypes.h>

namespace keyczar {

namespace util {

// An string comparison that is safe from timing attacks. If two strings are
// of equal length, this code will always check all elements, rather than
// exiting once it encounters a differing character.
bool SafeStringEquals(const std::string& s1, const std::string& s2);

// Converts a 32-bit integer to a big-endian byte array in a string
std::string Int32ToByteString(int32 num);

// Converts a four-byte big-endian byte array at a specified offset in
// a specified string to an int32.  Returns false if four bytes are
// not available.
bool ByteStringToInt32(const std::string& str, int offset, int32* num);

}  // namespace util

}  // namespace keyczar

#endif  // KEYCZAR_UTIL_H_
