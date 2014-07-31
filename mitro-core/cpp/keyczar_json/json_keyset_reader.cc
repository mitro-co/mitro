#include "keyczar_json/json_keyset_reader.h"

#include <keyczar/base/json_value_serializer.h>
#include <keyczar/base/logging.h>
#include <keyczar/base/string_util.h>
#include <keyczar/crypto_factory.h>
#include <keyczar/key_util.h>
#include <keyczar/keyset.h>
#include <keyczar/pbe_impl.h>

namespace mitro {

static const char META_KEY[] = "meta";

// Returns a parsed Value from json, or NULL on failure.
Value* ParseJsonToValue(const std::string& json) {
  keyczar::base::JSONStringValueSerializer json_serializer(json);
  std::string error;
  scoped_ptr<Value> root(json_serializer.Deserialize(&error));
  if (root.get() == NULL) {
    LOG(ERROR) << "Error parsing JSON: " << error;
    return NULL;
  }
  return root.release();
}

// Returns a parsed Value from the JSON string stored in json_value. The
// caller owns the returned value.
Value* ParseJsonString(const Value& value) {
  std::string json_string;
  bool success = value.GetAsString(&json_string);
  if (!success) {
    LOG(ERROR) << "Failed to get string from JSON value";
    return NULL;
  }
  return ParseJsonToValue(json_string);
}

DictionaryValue* ParseJsonToDictionary(const std::string& json) {
  scoped_ptr<Value> root(ParseJsonToValue(json));
  if (root.get() == NULL) {
    return NULL;
  }

  if (!root->IsType(Value::TYPE_DICTIONARY)) {
    LOG(ERROR) << "root object must be a dictionary: " << root->GetType();
    return NULL;
  }

  return (DictionaryValue*) root.release();
}

Value* JsonKeysetReader::ReadMetadata() const {
  // we check for meta in ReadKeyString; this should be true!
  Value* out_value = NULL;
  bool success = key_dict_->Get(META_KEY, &out_value);
  CHECK(success);
  CHECK(out_value->IsType(Value::TYPE_STRING));

  // meta is a string we need to parse
  return ParseJsonString(*out_value);
}

Value* JsonKeysetReader::ReadKey(int version) const {
  assert(version > 0);
  std::string version_string = StringPrintf("%d", version);
  Value* out_value = NULL;
  if (!key_dict_->Get(version_string, &out_value)) {
    LOG(ERROR) << "key version " << version_string << " does not exist";
    return NULL;
  }
  if (!out_value->IsType(Value::TYPE_STRING)) {
    LOG(ERROR) << "key version " << version_string << " is not a string: " << out_value->GetType();
    return NULL;
  }

  return ParseJsonString(*out_value);
}

bool JsonKeysetReader::ReadKeyString(const std::string& json_string) {
  CHECK(key_dict_ == NULL);
  scoped_ptr<DictionaryValue> root(ParseJsonToDictionary(json_string));
  if (root.get() == NULL) {
    return false;
  }

  // Check that "meta" exists
  Value* out_value = NULL;
  if (!root->Get(META_KEY, &out_value)) {
    LOG(ERROR) << "missing required key \"" << META_KEY << "\" in keyset";
    return false;
  }
  if (!out_value->IsType(Value::TYPE_STRING)) {
    LOG(ERROR) << META_KEY << " must be a string: " << out_value->GetType();
    return false;
  }
  // TODO: More validation?

  key_dict_.swap(root);
  return true;
}

PBEKeysetReader::PBEKeysetReader(const KeysetReader* keyset, const std::string& password) :
    keyset_(keyset),
    password_(password) {
  CHECK(keyset_ != NULL);
}

// Copied from keyset_file_reader.cc in Keyczar because it is static
// TODO: Patch upstream to make this visible!
static Value* DecryptPBEKey(const DictionaryValue& pbe_value,
                            const std::string& password) {
  // using keyczar::PBEImpl;
  using namespace keyczar;

  std::string cipher_string;
  if (!pbe_value.GetString("cipher", &cipher_string))
    return NULL;
  const PBEImpl::CipherAlgorithm cipher = PBEImpl::GetCipher(cipher_string);
  if (cipher == PBEImpl::UNDEF_CIPHER)
    return NULL;

  std::string hmac_string;
  if (!pbe_value.GetString("hmac", &hmac_string))
    return NULL;
  const PBEImpl::HMACAlgorithm hmac = PBEImpl::GetHMAC(hmac_string);
  if (hmac == PBEImpl::UNDEF_HMAC)
    return NULL;

  int iteration_count;
  if (!pbe_value.GetInteger("iterationCount", &iteration_count))
    return NULL;

  std::string salt;
  if (!util::DeserializeString(pbe_value, "salt", &salt))
    return NULL;

  std::string iv;
  if (!util::DeserializeString(pbe_value, "iv", &iv))
    return NULL;

  std::string encrypted_key;
  if (!util::DeserializeString(pbe_value, "key", &encrypted_key))
    return NULL;

  scoped_ptr<PBEImpl> pbe_impl(
    CryptoFactory::CreatePBE(cipher, hmac, iteration_count, password));
  if (pbe_impl.get() == NULL)
    return NULL;

  std::string decrypted_key;
  if (!pbe_impl->Decrypt(salt, iv, encrypted_key, &decrypted_key))
    return NULL;

  return ParseJsonToValue(decrypted_key);
}


Value* PBEKeysetReader::ReadKey(int version) const {
  scoped_ptr<Value> encrypted_key(keyset_->ReadKey(version));
  if (encrypted_key == NULL) {
    return NULL;
  }

  if (!encrypted_key->IsType(Value::TYPE_DICTIONARY)) {
    LOG(ERROR) << "root of JSON key must be a dictionary: " << encrypted_key->GetType();
    return NULL;
  }

  return DecryptPBEKey(*(DictionaryValue*) encrypted_key.get(), password_);
}

Value* PBEKeysetReader::ReadMetadata() const {
  return keyset_->ReadMetadata();
}

bool SerializeJsonValue(const Value& value, std::string* output) {
  keyczar::base::JSONStringValueSerializer serializer(output);
  if (!serializer.Serialize(value)) {
    LOG(ERROR) << "Error serializing JSON value";
    output->clear();
    return false;
  }
  return true;
}

// Returns a JSON string for the Value in value. The
// caller owns the returned value.
StringValue* SerializeToJsonString(const Value& value) {
  std::string output;
  if (!SerializeJsonValue(value, &output)) {
    return NULL;
  }
  return new StringValue(output);
}

std::string SerializeKeysetToJson(const keyczar::Keyset& keyset) {
  // export_public=false, same as in KeysetWriter when generating new keys
  scoped_ptr<Value> metadata_value(keyset.metadata()->GetValue(false));
  DictionaryValue root;
  root.Set(META_KEY, SerializeToJsonString(*metadata_value));

  for (keyczar::Keyset::const_iterator it = keyset.Begin(); it != keyset.End(); ++it) {
    std::string key_string = IntToString(it->first);
    const keyczar::Key* key = keyset.GetKey(it->first);
    scoped_ptr<Value> key_value(key->GetValue());
    root.Set(key_string, SerializeToJsonString(*key_value));
  }

  std::string output;
  if (!SerializeJsonValue(root, &output)) {
    LOG(ERROR) << "Error serializing keyset";
  }
  return output;
}

}  // namespace mitro
