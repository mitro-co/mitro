#ifndef MESSAGE_HANDLER_H_
#define MESSAGE_HANDLER_H_

#include <boost/shared_ptr.hpp>

#include "ppapi/cpp/var.h"
#include "thrift/protocol/TNativeClientProtocol.h"
#include "thrift_nacl_types.h"

#include <map>
#include <string>

namespace thrift_nacl {

typedef bool (*MessageHandler)(const boost::shared_ptr<pp::Var>& in,
                               boost::shared_ptr<const pp::Var>* out,
                               boost::shared_ptr<const pp::Var>* error);

bool RegisterMessageHandler(const std::string& message_type,
                            MessageHandler handler);

}  // namespace thrift_nacl

#define MAKE_HANDLER_WRAPPER(handler, in_type, out_type, error_type) \
bool handler##Wrapper(const boost::shared_ptr<pp::Var>& in, \
                      boost::shared_ptr<const pp::Var>* out, \
                      boost::shared_ptr<const pp::Var>* err) { \
  boost::shared_ptr<apache::thrift::protocol::TNativeClientProtocol> protocol(\
      new apache::thrift::protocol::TNativeClientProtocol()); \
  in_type request; \
  out_type response; \
  error_type error; \
\
  protocol->setVar(in); \
  request.read(protocol.get()); \
\
  if (handler(request, &response, &error)) { \
    response.write(protocol.get()); \
    *out = protocol->getVar(); \
    return true; \
  } else { \
    error.write(protocol.get()); \
    *err = protocol->getVar(); \
    return false; \
  } \
}

#define REGISTER_MESSAGE_HANDLER_FULL(message_type, handler, in_type, out_type, error_type) \
MAKE_HANDLER_WRAPPER(handler, in_type, out_type, error_type) \
static bool handler##_result = thrift_nacl::RegisterMessageHandler( \
  std::string(message_type), handler##Wrapper);

#define REGISTER_MESSAGE_HANDLER(message_type, handler) \
REGISTER_MESSAGE_HANDLER_FULL(message_type, handler, handler##Request, handler##Response, ThriftNaClError)

#endif  // MESSAGE_HANDLER_H_
