#include <stdio.h>
#include <stdlib.h>
#include <time.h>

#include <limits>
#include <vector>
#include <string>

#include <boost/shared_ptr.hpp>
#include <boost/scoped_ptr.hpp>
#include <gtest/gtest.h>
#include <thrift/protocol/TNativeClientProtocol.h>
#include <thrift/transport/TBufferTransports.h>

#include "ppapi/cpp/var.h"
#include "ppapi/cpp/var_dictionary.h"
#include "ppapi/cpp/module.h"
#include "ppapi_simple/ps_main.h"

#include "test_types.h"

using apache::thrift::protocol::TNativeClientProtocol;
using apache::thrift::protocol::TProtocolException;
using boost::scoped_ptr;
using boost::shared_ptr;
using pp::Var;
using pp::VarArray;
using pp::VarDictionary;
using std::string;

double RandomDouble() {
  return rand() / (RAND_MAX + 1.0);
}

int RandomInt(int min, int max) {
  return static_cast<int>(RandomDouble() * (max - min)) + min;
}

void CreateRandomBinaryString(int length, string* s) {
  s->resize(length);

  for (int i = 0; i < s->size(); ++i) {
    (*s)[i] = static_cast<uint8_t>(RandomInt(0, 256));
  }
}

shared_ptr<Var> CreateBooleanVar(const string& key, bool value) {
  VarDictionary* object = new VarDictionary();
  object->Set(Var(key), Var(value));
  return shared_ptr<Var>(object);
}

shared_ptr<Var> CreateIntegerVar(const string& key, int32_t value) {
  VarDictionary* object = new VarDictionary();
  object->Set(Var(key), Var(value));
  return shared_ptr<Var>(object);
}

shared_ptr<Var> CreateDoubleVar(const string& key, double value) {
  VarDictionary* object = new VarDictionary();
  object->Set(Var(key), Var(value));
  return shared_ptr<Var>(object);
}

shared_ptr<Var> CreateStringVar(const string& key, const string& value) {
  VarDictionary* object = new VarDictionary();
  object->Set(Var(key), Var(value));
  return shared_ptr<Var>(object);
}


Person* CreateTestPerson() {
  Person* person = new Person();
  person->set_name("John");
  person->set_weight(160.0);

  person->mutable_birthday()->set_month(1);
  person->mutable_birthday()->set_day(1);
  person->mutable_birthday()->set_year(1970);

  (*person->mutable_dict())["a"] = 1;
  (*person->mutable_dict())["b"] = 2;

  String obj;
  obj.set_s("test");
  (*person->mutable_objdict())["c"] = obj;
  (*person->mutable_objdict())["d"] = obj;

  person->mutable_objlist()->push_back(obj);
  person->mutable_objlist()->push_back(obj);

  person->mutable_params()->insert("p1");
  person->mutable_params()->insert("p2");

  return person;
}

TEST(ThriftNaclTest, BoolTest) {
  shared_ptr<TNativeClientProtocol> protocol(new TNativeClientProtocol());
  shared_ptr<Boolean> boolean(new Boolean());

  shared_ptr<const Var> bool_var = CreateBooleanVar("value", true);
  protocol->setVar(bool_var);
  boolean->read(protocol.get());
  ASSERT_TRUE(boolean->get_value());

  bool_var = CreateBooleanVar("value", false);
  protocol->setVar(bool_var);
  boolean->read(protocol.get());
  ASSERT_FALSE(boolean->get_value());

  boolean->set_value(true);
  boolean->write(protocol.get());
  scoped_ptr<Boolean> boolean2(new Boolean());
  boolean2->read(protocol.get());
  ASSERT_TRUE(*boolean == *boolean2);

  // TODO: Reenable when exceptions working
/*
  // Test invalid type
  bool_var = CreateStringVar("value", "value");
  protocol->setVar(bool_var);
  ASSERT_THROW(number->read(protocol.get()), TProtocolException);
*/
}

#define INT_TEST(bits) \
TEST(ThriftNaclTest, Int##bits##Test) { \
  shared_ptr<TNativeClientProtocol> protocol(new TNativeClientProtocol()); \
  shared_ptr<Number> number(new Number()); \
  shared_ptr<const Var> int_var; \
\
  int##bits##_t min_value = std::numeric_limits<int##bits##_t>::min(); \
  int##bits##_t max_value = std::numeric_limits<int##bits##_t>::max(); \
\
  int##bits##_t test_values[] = {0, min_value, max_value}; \
  int num_tests = sizeof(test_values) / sizeof(test_values[0]); \
\
  /* Test reading from integer values. */ \
  for (int i = 0; i < num_tests; i++) { \
    int_var = CreateIntegerVar("i"#bits"_value", test_values[i]); \
    protocol->setVar(int_var); \
    number->read(protocol.get()); \
    ASSERT_EQ(test_values[i], number->get_i##bits##_value()); \
  } \
\
  /* Test reading from double values. */ \
  for (int i = 0; i < num_tests; i++) { \
    int_var = CreateDoubleVar("i"#bits"_value", test_values[i]); \
    protocol->setVar(int_var); \
    number->read(protocol.get()); \
    ASSERT_EQ(test_values[i], number->get_i##bits##_value()); \
  } \
 \
  number->set_i##bits##_value(RandomInt(-100, 100)); \
  number->write(protocol.get()); \
  scoped_ptr<Number> number2(new Number()); \
  number2->read(protocol.get()); \
  ASSERT_TRUE(*number == *number2); \
}
  // TODO: Reenable when exceptions working
/*
  // Test integer overflow
  shared_ptr<Var> int_var = CreateDoubleVar("i##bits##_value", min_value - 1.0);
  protocol->setVar(int_var);
  ASSERT_THROW(number->read(protocol.get()), TProtocolException);

  int_var = CreateDoubleVar("i##bits##_value", max_value + 1.0);
  protocol->setVar(int_var);
  ASSERT_THROW(number->read(protocol.get()), TProtocolException);

  // Test invalid type
  int_var = CreateStringVar("i##bits##_value", "value");
  protocol->setVar(int_var);
  ASSERT_THROW(number->read(protocol.get()), TProtocolException);
*/

INT_TEST(8)
INT_TEST(16)
INT_TEST(32)

TEST(ThriftNaclTest, Int64Test) { \
  shared_ptr<TNativeClientProtocol> protocol(new TNativeClientProtocol());
  shared_ptr<Number> number(new Number());
  shared_ptr<const Var> int_var;

  int32_t test_values[] = {0, -100, 100};
  int num_tests = sizeof(test_values) / sizeof(test_values[0]);

  /* Test reading from integer values. */
  for (int i = 0; i < num_tests; i++) {
    int_var = CreateIntegerVar("i64_value", test_values[i]);
    protocol->setVar(int_var);
    number->read(protocol.get());
    ASSERT_EQ(test_values[i], number->get_i64_value());
  }

  int64_t min_value = -(1LL << 53);
  int64_t max_value = 1LL << 53;

  int64_t test_values2[] = {0, 100};
  int num_tests2 = sizeof(test_values2) / sizeof(test_values2[0]);

  /* Test reading from double values. */
  for (int i = 0; i < num_tests2; i++) {
    int_var = CreateDoubleVar("i64_value", test_values2[i]);
    protocol->setVar(int_var);
    number->read(protocol.get());
    ASSERT_EQ(test_values2[i], number->get_i64_value());
  }

  number->set_i64_value(RandomInt(-100, 100));
  number->write(protocol.get());
  scoped_ptr<Number> number2(new Number());
  number2->read(protocol.get());
  ASSERT_TRUE(*number == *number2);
}

TEST(ThriftNaclTest, DoubleTest) {
  shared_ptr<TNativeClientProtocol> protocol(new TNativeClientProtocol());
  shared_ptr<Number> number(new Number());
  shared_ptr<const Var> dbl_var;

  double test_values[] = {0.0, -100.0, 100.0};
  int num_tests = sizeof(test_values) / sizeof(test_values[0]);

  /* Test reading from integer values. */
  for (int i = 0; i < num_tests; i++) {
    dbl_var = CreateIntegerVar("double_value", test_values[i]);
    protocol->setVar(dbl_var);
    number->read(protocol.get());
    ASSERT_EQ(test_values[i], number->get_double_value());
  }

  /* Test reading from double values. */
  for (int i = 0; i < num_tests; i++) {
    dbl_var = CreateDoubleVar("double_value", test_values[i]);
    protocol->setVar(dbl_var);
    number->read(protocol.get());
    ASSERT_EQ(test_values[i], number->get_double_value());
  }

  number->set_double_value(RandomDouble());
  number->write(protocol.get());
  scoped_ptr<Number> number2(new Number());
  number2->read(protocol.get());
  ASSERT_TRUE(*number == *number2);
}

TEST(ThriftNaclTest, StringTest) {
  shared_ptr<TNativeClientProtocol> protocol(new TNativeClientProtocol());
  shared_ptr<String> obj(new String());
  shared_ptr<const Var> str_var;

  const char* test_values[] = {"", "test", "test unicod\u00e9"};
  int num_tests = sizeof(test_values) / sizeof(test_values[0]);

  for (int i = 0; i < num_tests; i++) {
    str_var = CreateStringVar("s", string(test_values[i]));
    protocol->setVar(str_var);
    obj->read(protocol.get());
    ASSERT_EQ(test_values[i], obj->get_s());
  }

  obj->set_s("test");
  obj->write(protocol.get());
  scoped_ptr<String> obj2(new String());
  obj2->read(protocol.get());
  ASSERT_TRUE(*obj == *obj2);
}

TEST(ThriftNaclTest, Base64Test) {
  for (int i = 0; i < 1000; i++) {
    shared_ptr<TNativeClientProtocol> protocol(new TNativeClientProtocol());
    shared_ptr<BinaryData> data(new BinaryData());
    string s;
    CreateRandomBinaryString(RandomInt(0, 20), &s);
    data->set_data(s);
    data->write(protocol.get());

    shared_ptr<const Var> data_var(protocol->getVar());
    shared_ptr<TNativeClientProtocol> protocol2(new TNativeClientProtocol(data_var));
    scoped_ptr<BinaryData> data2(new BinaryData());
    data2->read(protocol2.get());
    string s2 = data2->get_data();
    ASSERT_TRUE(s == s2);
  }
}

TEST(ThriftNaclTest, UndefinedTest) {
  shared_ptr<TNativeClientProtocol> protocol(new TNativeClientProtocol());
  shared_ptr<Number> number(new Number());

  shared_ptr<VarDictionary> null_var(new VarDictionary());
  null_var->Set(Var("i8_value"), Var());
  null_var->Set(Var("i32_value"), Var(100));
  protocol->setVar(null_var);
  number->read(protocol.get());

  ASSERT_FALSE(number->has_i8_value());
  ASSERT_EQ(100, number->get_i32_value());
}

TEST(ThriftNaclTest, NullTest) {
  shared_ptr<TNativeClientProtocol> protocol(new TNativeClientProtocol());
  shared_ptr<Number> number(new Number());

  shared_ptr<VarDictionary> null_var(new VarDictionary());
  null_var->Set(Var("i8_value"), Var(Var::Null()));
  null_var->Set(Var("i32_value"), Var(100));
  protocol->setVar(null_var);
  number->read(protocol.get());

  ASSERT_FALSE(number->has_i8_value());
  ASSERT_EQ(100, number->get_i32_value());
}

TEST(ThriftNaclTest, UnknownFieldTest) {
  shared_ptr<TNativeClientProtocol> protocol(new TNativeClientProtocol());
  shared_ptr<Boolean> boolean(new Boolean());

  shared_ptr<VarDictionary> bool_var(new VarDictionary());
  bool_var->Set(Var("key"), Var(true));
  bool_var->Set(Var("value"), Var(true));
  protocol->setVar(bool_var);
  boolean->read(protocol.get());

  ASSERT_TRUE(boolean->get_value());
}

TEST(ThriftNaclTest, WriteTest) {
  shared_ptr<TNativeClientProtocol> protocol(new TNativeClientProtocol());
  shared_ptr<Person> person(CreateTestPerson());
  person->write(protocol.get());

  shared_ptr<const Var> person_var(protocol->getVar());
  ASSERT_TRUE(person_var->is_dictionary());
  const VarDictionary* person_dict = static_cast<const VarDictionary*>(person_var.get());

  ASSERT_TRUE(person_dict->HasKey(Var("name")));
  ASSERT_TRUE(person_dict->Get(Var("name")).is_string());
  ASSERT_EQ(person->get_name(), person_dict->Get(Var("name")).AsString());

  ASSERT_TRUE(person_dict->HasKey(Var("weight")));
  ASSERT_TRUE(person_dict->Get(Var("weight")).is_double());
  ASSERT_EQ(person->get_weight(), person_dict->Get(Var("weight")).AsDouble());

  ASSERT_TRUE(person_dict->HasKey(Var("birthday")));
  const Var& birthday_var = person_dict->Get(Var("birthday"));
  ASSERT_TRUE(birthday_var.is_dictionary());
  const VarDictionary* birthday_dict = static_cast<const VarDictionary*>(&birthday_var);

  ASSERT_TRUE(birthday_dict->HasKey(Var("month")));
  ASSERT_TRUE(birthday_dict->Get(Var("month")).is_int());
  ASSERT_EQ(person->get_birthday().get_month(),
            birthday_dict->Get(Var("month")).AsInt());

  ASSERT_TRUE(birthday_dict->HasKey(Var("day")));
  ASSERT_TRUE(birthday_dict->Get(Var("day")).is_int());
  ASSERT_EQ(person->get_birthday().get_day(),
            birthday_dict->Get(Var("day")).AsInt());

  ASSERT_TRUE(birthday_dict->HasKey(Var("year")));
  ASSERT_TRUE(birthday_dict->Get(Var("year")).is_int());
  ASSERT_EQ(person->get_birthday().get_year(),
            birthday_dict->Get(Var("year")).AsInt());
  
  ASSERT_TRUE(person_dict->HasKey(Var("dict")));
  const Var& dict_var = person_dict->Get(Var("dict"));
  ASSERT_TRUE(dict_var.is_dictionary());
  const VarDictionary* dict_dict = static_cast<const VarDictionary*>(&dict_var);

  const std::map<std::string, int32_t>& dict = person->get_dict();
  std::map<std::string, int32_t>::const_iterator dict_iter;
  for (dict_iter = dict.begin(); dict_iter != dict.end(); ++dict_iter) {
    ASSERT_TRUE(dict_dict->HasKey(Var(dict_iter->first)));
    ASSERT_TRUE(dict_dict->Get(Var(dict_iter->first)).is_int());
    ASSERT_EQ(dict_iter->second, dict_dict->Get(Var(dict_iter->first)).AsInt());
  }

  ASSERT_TRUE(person_dict->HasKey(Var("objlist")));
  const Var& objlist_var = person_dict->Get(Var("objlist"));
  ASSERT_TRUE(objlist_var.is_array());
  const VarArray* objlist_array = static_cast<const VarArray*>(&objlist_var);

  const std::vector<String>& objlist = person->get_objlist();
  ASSERT_EQ(objlist.size(), objlist_array->GetLength());

  for (int i = 0; i < objlist.size(); ++i) {
    const Var& value_var = objlist_array->Get(i);
    ASSERT_TRUE(value_var.is_dictionary());
    const VarDictionary* value_dict = static_cast<const VarDictionary*>(&value_var);
    ASSERT_TRUE(value_dict->HasKey(Var("s")));
    ASSERT_TRUE(value_dict->Get(Var("s")).is_string());
    ASSERT_EQ(objlist[i].get_s(), value_dict->Get(Var("s")).AsString());
  }
  
  ASSERT_TRUE(person_dict->HasKey(Var("objdict")));
  const Var& objdict_var = person_dict->Get(Var("objdict"));
  ASSERT_TRUE(objdict_var.is_dictionary());
  const VarDictionary* objdict_dict = static_cast<const VarDictionary*>(&objdict_var);

  const std::map<std::string, String>& objdict = person->get_objdict();
  std::map<std::string, String>::const_iterator objdict_iter;
  for (objdict_iter = objdict.begin(); objdict_iter != objdict.end(); ++objdict_iter) {
    ASSERT_TRUE(objdict_dict->HasKey(Var(objdict_iter->first)));
    const Var& value_var = objdict_dict->Get(Var(objdict_iter->first));
    ASSERT_TRUE(value_var.is_dictionary());
    const VarDictionary* value_dict = static_cast<const VarDictionary*>(&value_var);
    
    ASSERT_TRUE(value_dict->HasKey(Var("s")));
    ASSERT_TRUE(value_dict->Get(Var("s")).is_string());
    ASSERT_EQ(objdict_iter->second.get_s(),
              value_dict->Get(Var("s")).AsString());
  }

  ASSERT_TRUE(person_dict->HasKey(Var("params")));
  const Var& params_var = person_dict->Get(Var("params"));
  ASSERT_TRUE(params_var.is_array());
  const VarArray* params_array = static_cast<const VarArray*>(&params_var);

  const std::set<std::string>& params = person->get_params();
  ASSERT_EQ(params.size(), params_array->GetLength());

  int i;
  std::set<std::string>::const_iterator params_iter;
  for (i = 0, params_iter = params.begin();
       params_iter != params.end();
       ++i, ++params_iter) {
    const Var& value_var = params_array->Get(i);
    ASSERT_TRUE(value_var.is_string());
    ASSERT_EQ(*params_iter, value_var.AsString());
  }
}

TEST(ThriftNaclTest, ReadTest) {
  shared_ptr<TNativeClientProtocol> protocol(new TNativeClientProtocol());
  shared_ptr<Person> person(CreateTestPerson());
  person->write(protocol.get());

  shared_ptr<const Var> person_var(protocol->getVar());
  shared_ptr<TNativeClientProtocol> protocol2(new TNativeClientProtocol(person_var));
  scoped_ptr<Person> person2(new Person());
  person2->read(protocol2.get());

  ASSERT_TRUE(*person == *person2);
}

int test_main(int argc, char* argv[]) {
  srand(time(NULL));
  ::testing::InitGoogleTest(&argc, argv);
  return RUN_ALL_TESTS();
}

/*
 * Register the function to call once the Instance Stringect is initialized.
 * see: pappi_simple/ps_main.h
 */
PPAPI_SIMPLE_REGISTER_MAIN(test_main)
