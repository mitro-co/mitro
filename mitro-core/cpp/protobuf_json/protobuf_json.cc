#include "protobuf_json.h"

#include <vector>

#include <base/json/json_reader.h>
#include <base/json/json_writer.h>
#include <base/logging.h>
#include <base/values.h>

using google::protobuf::Descriptor;
using google::protobuf::FieldDescriptor;
using google::protobuf::Message;
using google::protobuf::Reflection;
using base::JSONReader;
using base::JSONWriter;
using base::scoped_ptr;
using std::string;
using std::vector;

namespace protobuf_json {

Value* CreateValueFromMessage(const Message& message);

#define GET_FIELD_VALUE(type) \
  (field.is_repeated() ? reflection.GetRepeated##type(message, &field, index) \
                       : reflection.Get##type(message, &field))

Value* CreateValueFromField(const Message& message,
                            const Reflection& reflection,
                            const FieldDescriptor& field,
                            int index) {
  Value* value = NULL;

  switch (field.cpp_type()) {
    case FieldDescriptor::CPPTYPE_INT32:
      value = Value::CreateIntegerValue(GET_FIELD_VALUE(Int32));
      break;
    case FieldDescriptor::CPPTYPE_INT64:
      value = Value::CreateDoubleValue(GET_FIELD_VALUE(Int64));
      break;
    case FieldDescriptor::CPPTYPE_UINT32:
      value = Value::CreateDoubleValue(GET_FIELD_VALUE(UInt32));
      break;
    case FieldDescriptor::CPPTYPE_UINT64:
      value = Value::CreateDoubleValue(GET_FIELD_VALUE(UInt64));;
      break;
    case FieldDescriptor::CPPTYPE_FLOAT:
      value = Value::CreateDoubleValue(GET_FIELD_VALUE(Float));
      break;
    case FieldDescriptor::CPPTYPE_DOUBLE:
      value = Value::CreateDoubleValue(GET_FIELD_VALUE(Double));
      break;
    case FieldDescriptor::CPPTYPE_BOOL:
      value = Value::CreateBooleanValue(GET_FIELD_VALUE(Bool));
      break;
    case FieldDescriptor::CPPTYPE_ENUM:
      value = Value::CreateStringValue(GET_FIELD_VALUE(Enum)->name());
      break;
    case FieldDescriptor::CPPTYPE_STRING:
      value = Value::CreateStringValue(GET_FIELD_VALUE(String));
      break;
    case FieldDescriptor::CPPTYPE_MESSAGE:
      value = CreateValueFromMessage(GET_FIELD_VALUE(Message));
      break;
    default:
      NOTREACHED();
      break;
  }
  return value;
}

Value* CreateValueFromRepeatedField(const Message& message,
                                    const Reflection& reflection,
                                    const FieldDescriptor& field) {
  ListValue* list_value = new ListValue;
  int size = reflection.FieldSize(message, &field);

  for (int i = 0; i < size; ++i) {
    list_value->Append(CreateValueFromField(message, reflection, field, i));
  }
  return list_value;   
}

Value* CreateValueFromMessage(const Message& message) {
  const Reflection* reflection = message.GetReflection();
  const Descriptor* descriptor = message.GetDescriptor();
  int num_fields = descriptor->field_count();

  DictionaryValue* dict_value = new DictionaryValue;

  for (int i = 0; i < num_fields; ++i) {
    const FieldDescriptor* field = descriptor->field(i);
    Value* field_value = NULL;

    // Always print repeated fields, even if there are no elements.
    if (field->is_repeated()) {
      field_value = CreateValueFromRepeatedField(message, *reflection, *field);
    } else if (reflection->HasField(message, field)) {
      field_value = CreateValueFromField(message, *reflection, *field, 0);
    }

    // Empty, non-repeated fields are not output.
    if (field_value != NULL) {
      dict_value->Set(field->name(), field_value);  
    }
  }

  return dict_value;
}

void PrintFields(const Message& message) {
  const Reflection* reflection = message.GetReflection();
  const Descriptor* descriptor = message.GetDescriptor();

  for (int i = 0; i < descriptor->field_count(); ++i) {
    const FieldDescriptor* field = descriptor->field(i);

    if (field->is_repeated()) {
      printf("%s: %d\n", field->name().c_str(),
                         reflection->FieldSize(message, field));
    } else {
      printf("%s: %d\n", field->name().c_str(),
                         reflection->HasField(message, field));
    }
  }
}

std::string SerializeProtobufToJSONString(const Message& message) {
  string json_string;
  scoped_ptr<Value> root(CreateValueFromMessage(message));
  JSONWriter::WriteWithOptions(root.get(), 
                               JSONWriter::OPTIONS_PRETTY_PRINT,
                               &json_string);
  return json_string;
}

Value::Type kCppTypeToValueType[] = {
  static_cast<Value::Type>(-1), // undefined
  Value::TYPE_INTEGER,    // CPPTYPE_INT32
  Value::TYPE_INTEGER,    // CPPTYPE_INT64
  Value::TYPE_INTEGER,    // CPPTYPE_UINT32
  Value::TYPE_INTEGER,    // CPPTYPE_UINT64
  Value::TYPE_INTEGER,    // CPPTYPE_DOUBLE
  Value::TYPE_INTEGER,    // CPPTYPE_FLOAT
  Value::TYPE_BINARY,     // CPPTYPE_BOOL
  Value::TYPE_STRING,     // CPPTYPE_ENUM  TODO: Maybe should be TYPE_INTEGER?
  Value::TYPE_STRING,     // CPPTYPE_STRING
  Value::TYPE_DICTIONARY  // CPPTYPE_MESSAGE
};

bool IsValueCompatibleWithField(const FieldDescriptor& field,
                                const Value& value) {
  Value::Type expected_type = kCppTypeToValueType[field.cpp_type()];

  if (value.GetType() == expected_type) {
    return true;
  } else if (expected_type == Value::TYPE_INTEGER &&
             value.GetType() == Value::TYPE_DOUBLE) {
    // TODO: verify value is a whole number.
    return true;
  } else { 
    return false;
  }
}

bool ParseProtobufFromValue(const Value& value, Message* message);
bool ParseRepeatedFieldFromValue(const Value& value, Message* message);

#define DEFINE_GET_VALUE(cpp_type, value_type) \
cpp_type Get##value_type##Value(const Value& value) { \
  cpp_type cpp_value; \
  CHECK(value.GetAs##value_type(&cpp_value)); \
  return cpp_value; \
}

DEFINE_GET_VALUE(int, Integer)
DEFINE_GET_VALUE(double, Double)
DEFINE_GET_VALUE(bool, Boolean)
DEFINE_GET_VALUE(string, String)

#define GET_NUMBER_VALUE(value) \
  (value.GetType() == Value::TYPE_DOUBLE ? GetDoubleValue(value) \
                                         : GetIntegerValue(value))

#define SET_FIELD_VALUE(type, func) \
  if (field->is_repeated()) { \
    reflection->Add##type(message, field, func(value)); \
  } else { \
    reflection->Set##type(message, field, func(value)); \
  }

bool ParseFieldFromValue(const string& key,
                         const Value& value,
                         Message* message) {
  const Reflection* reflection = message->GetReflection();
  const Descriptor* descriptor = message->GetDescriptor();
  const FieldDescriptor* field = descriptor->FindFieldByName(key);

  if (value.GetType() == Value::TYPE_NULL) {
    return true;
  }

  if (!IsValueCompatibleWithField(*field, value)) {
    LOG(INFO) << "Value of wrong type for field '" << key << "'";
    LOG(INFO) << "Type: " << value.GetType();
    return false;
  }
  LOG(INFO) << key << " " << value.GetType() << " " << field->cpp_type();

  switch (field->cpp_type()) {
    case FieldDescriptor::CPPTYPE_INT32:
      SET_FIELD_VALUE(Int32, GET_NUMBER_VALUE)
      break;
    case FieldDescriptor::CPPTYPE_INT64:
      SET_FIELD_VALUE(Int64, GET_NUMBER_VALUE)
      break;
    // TODO: verify that these are nonnegative values.
    case FieldDescriptor::CPPTYPE_UINT32:
      SET_FIELD_VALUE(UInt32, GET_NUMBER_VALUE)
      break;
    case FieldDescriptor::CPPTYPE_UINT64:
      SET_FIELD_VALUE(UInt64, GET_NUMBER_VALUE)
      break;
    case FieldDescriptor::CPPTYPE_FLOAT:
      SET_FIELD_VALUE(Float, GET_NUMBER_VALUE)
      break;
    case FieldDescriptor::CPPTYPE_DOUBLE:
      SET_FIELD_VALUE(Double, GET_NUMBER_VALUE)
      break;
    case FieldDescriptor::CPPTYPE_BOOL:
      SET_FIELD_VALUE(Bool, GetBooleanValue)
      reflection->SetBool(message, field, GetBooleanValue(value));
      break;
    case FieldDescriptor::CPPTYPE_ENUM:
      // TODO
      NOTREACHED();
      break;
    case FieldDescriptor::CPPTYPE_STRING:
      SET_FIELD_VALUE(String, GetStringValue)
      break;
    case FieldDescriptor::CPPTYPE_MESSAGE: {
      Message* field_message;
      if (field->is_repeated()) {
        field_message = reflection->AddMessage(message, field);
      } else {
        field_message = reflection->MutableMessage(message, field);
      }
      return ParseProtobufFromValue(value, field_message);
      break;
    }
    default:
      NOTREACHED();
      break;
  }
  
  return true;
}

bool ParseRepeatedFieldFromValue(const string& key, const Value& value, Message* message) {
  if (!value.IsType(Value::TYPE_LIST)) {
    LOG(INFO) << "Error parsing JSON: Value is not a list.";
    return false;
  }

  const ListValue* list_value = dynamic_cast<const ListValue*>(&value);

  Value* field_value;
  ListValue::const_iterator iter;
  for (iter = list_value->begin(); iter != list_value->end(); ++iter) {
    ParseFieldFromValue(key, **iter, message); 
  }

  return true;
}

bool ParseProtobufFromValue(const Value& value, Message* message) {
  if (!value.IsType(Value::TYPE_DICTIONARY)) {
    LOG(INFO) << "Error parsing JSON: Value is not a dictionary.";
    return false;
  }

  const DictionaryValue* dict_value = dynamic_cast<const DictionaryValue*>(&value);
  CHECK(dict_value != NULL);

  for (DictionaryValue::Iterator iter(*dict_value);
       !iter.IsAtEnd();
       iter.Advance()) {
    const string& key = iter.key();
    const Value& field_value = iter.value();

    const Descriptor* descriptor = message->GetDescriptor();
    const FieldDescriptor* field = descriptor->FindFieldByName(key);

    if (field) {
      if (field->is_repeated()) {
        if (!ParseRepeatedFieldFromValue(key, field_value, message)) {
          return false;
        }
      } else {
        if (!ParseFieldFromValue(key, field_value, message)) {
          return false;
        }
      }
    } else {
      // Ignore unknown fields.
      LOG(INFO) << "Unknown field: " << key;
    }
  }

  return true;
}

bool ParseProtobufFromJSONString(const string& json_string, Message* message) {
  int options = base::JSON_ALLOW_TRAILING_COMMAS;
  int error_code;
  string error_message;
  scoped_ptr<Value> root(JSONReader::ReadAndReturnError(json_string,
                                                        options,
                                                        &error_code,
                                                        &error_message));

  if (root.get() == NULL) {
    LOG(INFO) << "Error parsing JSON: " << error_message;
    return false;
  }

  return ParseProtobufFromValue(*root, message);
}

}  // namespace protobuf_json
