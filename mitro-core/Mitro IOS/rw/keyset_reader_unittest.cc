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

#include <keyczar/base/file_path.h>
#include <keyczar/base/file_util.h>
#include <keyczar/base/values.h>
#include <keyczar/keyczar.h>
#include <keyczar/keyczar_test.h>
#include <keyczar/rw/keyset_encrypted_file_reader.h>
#include <keyczar/rw/keyset_encrypted_file_writer.h>
#include <keyczar/rw/keyset_file_reader.h>
#include <keyczar/rw/keyset_file_writer.h>

namespace keyczar {
namespace rw {

class KeysetReaderTest : public KeyczarTest {
};

TEST_F(KeysetReaderTest, ReadValidJSON) {
  FilePath path = data_path_.Append("aes");

  KeysetJSONFileReader reader(path.value());

  ASSERT_TRUE(base::PathExists(path.Append("meta")));
  scoped_ptr<Value> metadata_value(reader.ReadMetadata());
  ASSERT_TRUE(metadata_value.get());

  ASSERT_TRUE(base::PathExists(path.Append("1")));
  scoped_ptr<Value> key1_value(reader.ReadKey(1));
  ASSERT_TRUE(key1_value.get());

  ASSERT_TRUE(base::PathExists(path.Append("2")));
  scoped_ptr<Value> key2_value(reader.ReadKey(2));
  ASSERT_TRUE(key2_value.get());

  ASSERT_FALSE(base::PathExists(path.Append("3")));
  scoped_ptr<Value> key3_value(reader.ReadKey(3));
  ASSERT_FALSE(key3_value.get());
}

TEST_F(KeysetReaderTest, ReadAndWriteToPBEJSON) {
  const std::string password("cartman");
  const FilePath path = data_path_.Append("aes");
  KeysetJSONFileReader reader(path.value());

  {
    // Use PBE JSON writer
    KeysetPBEJSONFileWriter writer(temp_path_, password);

    // Reads and writes key 1 (before metadata to test that
    // it works even if metadata is not yet completely valid).
    scoped_ptr<Value> key1_value(reader.ReadKey(1));
    ASSERT_TRUE(key1_value.get());
    EXPECT_TRUE(writer.WriteKey(*key1_value, 1));

    // Reads and writes metadata
    scoped_ptr<Value> metadata_value(reader.ReadMetadata());
    ASSERT_TRUE(metadata_value.get());
    EXPECT_TRUE(writer.WriteMetadata(*metadata_value));

    // Reads and writes key 2
    scoped_ptr<Value> key2_value(reader.ReadKey(2));
    ASSERT_TRUE(key2_value.get());
    EXPECT_TRUE(writer.WriteKey(*key2_value, 2));

    // Writes key 1 (overwrite)
    EXPECT_TRUE(writer.WriteKey(*key1_value, 1));
  }

  {
    // Use PBE JSON reader
    KeysetPBEJSONFileReader pbe_reader(temp_path_, password);

    scoped_ptr<Value> pbe_key1_value(pbe_reader.ReadKey(1));
    scoped_ptr<Value> key1_value(reader.ReadKey(1));
    ASSERT_TRUE(pbe_key1_value.get() && key1_value.get());
    EXPECT_TRUE(key1_value->Equals(pbe_key1_value.get()));

    scoped_ptr<Value> pbe_metadata_value(pbe_reader.ReadMetadata());
    scoped_ptr<Value> metadata_value(reader.ReadMetadata());
    ASSERT_TRUE(pbe_metadata_value.get() && metadata_value.get());
    EXPECT_TRUE(metadata_value->Equals(pbe_metadata_value.get()));

    scoped_ptr<Value> pbe_key2_value(pbe_reader.ReadKey(2));
    scoped_ptr<Value> key2_value(reader.ReadKey(2));
    ASSERT_TRUE(pbe_key2_value.get() && key2_value.get());
    EXPECT_TRUE(key2_value->Equals(pbe_key2_value.get()));
  }
}

}  // namespace rw
}  // namespace keyczar
