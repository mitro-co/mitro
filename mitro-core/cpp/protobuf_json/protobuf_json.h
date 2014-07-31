#ifndef PROTOBUF_JSON_H_
#define PROTOBUF_JSON_H_

#include <string>

#include <google/protobuf/message.h>

namespace protobuf_json {

std::string SerializeProtobufToJSONString(const google::protobuf::Message& message);
bool ParseProtobufFromJSONString(const std::string& json_string,
                                 google::protobuf::Message* message);


}  // namespace protobuf_json

#endif  // PROTOBUF_JSON_H_
