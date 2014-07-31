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
#include <keyczar/util.h>

#include <keyczar/base/basictypes.h>

namespace keyczar {

namespace util {

bool SafeStringEquals(const std::string& s1, const std::string& s2) {
  if (s1.length() != s2.length())
    return false;

  int result = 0;
  for (uint32 i = 0; i < s1.length(); ++i)
    result |= s1[i] ^ s2[i];
  return result == 0;
}

std::string Int32ToByteString(int32 num) {
  unsigned char byte_array[sizeof(num)];
  for (int i = 0; i < sizeof(num); ++i) {
    unsigned char current_byte = (num >> ((i & 7) << 3)) & 0xFF;
    byte_array[sizeof(num) - i - 1] = current_byte;
  }
  return std::string(reinterpret_cast<char*>(byte_array), sizeof(num));
}

bool ByteStringToInt32(const std::string& str, int offset, int32* num) {
  if (offset + sizeof(*num) > str.size())
    false;

  const unsigned char* bytes =
      reinterpret_cast<const unsigned char*>(str.c_str()) + offset;

  *num = 0;
  for (int i = 0; i < sizeof(*num); ++i)
    *num = (*num << 8) | *bytes++;

  return true;
}

}  // namespace util

}  // namespace keyczar
