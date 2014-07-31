#include <stdio.h>

#include <vector>
#include <string>

#include <boost/shared_ptr.hpp>
#include <boost/scoped_ptr.hpp>
#include <gtest/gtest.h>
#include <thrift/protocol/TSimpleJSONProtocol.h>
#include <thrift/transport/TBufferTransports.h>

#include "test_types.h"

using apache::thrift::protocol::TSimpleJSONProtocol;
using apache::thrift::protocol::TProtocol;
using apache::thrift::transport::TMemoryBuffer;
using apache::thrift::transport::TTransport;
using boost::scoped_ptr;
using boost::shared_ptr;
using std::string;

TEST(ThriftJsonTest, ReadWriteTest) {
  shared_ptr<TMemoryBuffer> transport(new TMemoryBuffer(1 << 20));
  shared_ptr<TProtocol> protocol(new TSimpleJSONProtocol(transport));

  scoped_ptr<Person> person1(new Person);
  person1->set_name("John");
  person1->set_weight(160);

  person1->mutable_birthday()->set_month(1);
  person1->mutable_birthday()->set_day(1);
  person1->mutable_birthday()->set_year(1970);

  (*person1->mutable_dict())["a"] = 1;
  (*person1->mutable_dict())["b"] = 2;

  Obj obj;
  obj.set_s("test");
  (*person1->mutable_objdict())["c"] = obj;
  (*person1->mutable_objdict())["d"] = obj;

  person1->mutable_objlist()->push_back(obj);
  person1->mutable_objlist()->push_back(obj);

  person1->mutable_params()->push_back("p1");
  person1->mutable_params()->push_back("p2");

  person1->write(protocol.get());

  string result = transport->getBufferAsString();
  printf("%s\n", result.c_str());

  shared_ptr<TProtocol> protocol2(new TSimpleJSONProtocol(transport));
  scoped_ptr<Person> person2(new Person);
  person2->read(protocol2.get());

  shared_ptr<TMemoryBuffer> transport2(new TMemoryBuffer(1 << 20));
  shared_ptr<TProtocol> protocol3(new TSimpleJSONProtocol(transport2));
  person2->write(protocol3.get());
  string result2 = transport2->getBufferAsString();
  printf("%s\n", result2.c_str());

  ASSERT_TRUE(*person1 == *person2);
}

TEST(ThriftJsonTest, NullTest) {
  const string kMessage = "{\"name\":null}";

  shared_ptr<TMemoryBuffer> transport(new TMemoryBuffer(1 << 20));
  shared_ptr<TProtocol> protocol(new TSimpleJSONProtocol(transport));

  transport->write(reinterpret_cast<const uint8_t*>(kMessage.c_str()),
                   kMessage.size());

  scoped_ptr<Person> person(new Person);
  ASSERT_TRUE(person->read(protocol.get()));
  ASSERT_FALSE(person->has_name());
}
