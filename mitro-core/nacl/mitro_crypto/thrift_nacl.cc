#include <boost/shared_ptr.hpp>

#include "ppapi/cpp/instance.h"
#include "ppapi/cpp/module.h"
#include "ppapi/cpp/var.h"
#include "ppapi/cpp/var_dictionary.h"

#include "thrift_nacl.h"

namespace thrift_nacl {

typedef std::map<std::string, MessageHandler> MessageHandlerMap;

MessageHandlerMap& GetMessageHandlerMap() {
  static MessageHandlerMap message_handler_map;
  return message_handler_map;
}

bool RegisterMessageHandler(const std::string& message_type,
                            MessageHandler handler) {
  GetMessageHandlerMap().insert(
      MessageHandlerMap::value_type(message_type, handler));
  return true;
}

bool GetMessageHandler(const std::string& message_type,
                       MessageHandler* message_handler) {
  const MessageHandlerMap& message_handler_map = GetMessageHandlerMap();
  MessageHandlerMap::const_iterator iter =
      message_handler_map.find(message_type);

  if (iter == message_handler_map.end()) {
    return false;
  }
  *message_handler = iter->second;
  return true;
}

class ThriftNaClInstance : public pp::Instance {
 public:
  // The constructor creates the plugin-side instance.
  // @param[in] instance the handle to the browser-side plugin instance.
  explicit ThriftNaClInstance(PP_Instance instance) : pp::Instance(instance) {}
  virtual ~ThriftNaClInstance() {}

  // Handler for messages coming in from the browser via postMessage().
  // @param[in] var_message The message posted by the browser.
  virtual void HandleMessage(const pp::Var& var_message) {
    pp::VarDictionary var_response;

    std::string message_id;
    std::string message_type;
    MessageHandler message_handler;
    boost::shared_ptr<pp::Var> in;

    if (!ParseMessage(var_message, &message_id, &message_type, &in)) {
      pp::VarDictionary error_var;
      error_var.Set(pp::Var("type"), pp::Var("invalid_message"));
      error_var.Set(pp::Var("message"), pp::Var("Invalid message")); 
      var_response.Set(pp::Var("error"), error_var); 
    } else {
      // Response message id is set to match the request message id.
      var_response.Set(pp::Var("id"), pp::Var(message_id));

      if (!GetMessageHandler(message_type, &message_handler)) {
        pp::VarDictionary error_var;
        error_var.Set(pp::Var("type"), pp::Var("unknown_message_type"));
        std::string error_message = "Unknown message type: " + message_type; 
        error_var.Set(pp::Var("message"), pp::Var(error_message)); 
        var_response.Set(pp::Var("error"), error_var); 
      } else {
        boost::shared_ptr<const pp::Var> out;
        boost::shared_ptr<const pp::Var> error;

        if (!message_handler(in, &out, &error)) {
          var_response.Set(pp::Var("error"), *error);
        } else {
          var_response.Set(pp::Var("data"), *out);
        }
      }
    }

    PostMessage(var_response);
  }

 private:
  // Parse an incoming message from js.
  //
  // Valid messages have 3 required fields: id, type, and data.
  // Returns true and sets message_id, message_type, and data iff the message
  // is valid.
  static bool ParseMessage(const pp::Var& var_message,
                           std::string* message_id,
                           std::string* message_type,
                           boost::shared_ptr<pp::Var>* data) {
    if (!var_message.is_dictionary()) {
      return false;
    }

    const pp::VarDictionary* var_dict =
        static_cast<const pp::VarDictionary*>(&var_message);
    if (!var_dict->HasKey("type")) {
      return false;
    }

    pp::Var var_id = var_dict->Get("id");
    if (!var_id.is_string()) {
      return false;
    }

    *message_id = var_id.AsString();

    pp::Var var_type = var_dict->Get("type");
    if (!var_type.is_string()) {
      return false;
    }

    *message_type = var_type.AsString();

    if (!var_dict->HasKey("data")) {
      return false;
    }

    // Using pp_var increments the reference count of pp_var instead of
    // performing a deep copy if pp::Var was used.
    data->reset(new pp::Var(var_dict->Get("data").pp_var()));
    if (!(*data)->is_dictionary()) {
      return false;
    }

    return true;
  }
};

// The Module class.  The browser calls the CreateInstance() method to create
// an instance of your NaCl module on the web page.  The browser creates a new
// instance for each <embed> tag with type="application/x-pnacl".
class ThriftNaClModule : public pp::Module {
 public:
  ThriftNaClModule() : pp::Module() {}
  virtual ~ThriftNaClModule() {}

  // Create and return a ThriftNaClInstance object.
  // @param[in] instance The browser-side instance.
  // @return the plugin-side instance.
  virtual pp::Instance* CreateInstance(PP_Instance instance) {
    return new ThriftNaClInstance(instance);
  }
};

} // namespace thrift_nacl

namespace pp {
// Factory function called by the browser when the module is first loaded.
Module* CreateModule() {
  return new thrift_nacl::ThriftNaClModule();
}
}  // namespace pp
