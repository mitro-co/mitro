#include <string>

#include "gtest/gtest.h"

#include "protobuf_json.h"
#include "test.pb.h"

using std::string;

TEST(ProtobufJSONTest, ReadWriteTest) {
  test::PersonMessage* person = new test::PersonMessage;

  person->set_name("John");
  person->set_weight(160.0);

  person->mutable_birthday()->set_day(1);
  person->mutable_birthday()->set_month(1);
  person->mutable_birthday()->set_year(1970);

  person->add_phone(5551212);

  person->add_test()->set_s("hi");

  string output = protobuf_json::SerializeProtobufToJSONString(*person);

  test::PersonMessage* person2 = new test::PersonMessage;
  bool retval = protobuf_json::ParseProtobufFromJSONString(output, person2);

  string output2 = protobuf_json::SerializeProtobufToJSONString(*person2);

  ASSERT_EQ(output, output2);
}
