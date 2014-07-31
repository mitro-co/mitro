#ifndef MITRO_JSON_KEYSET_READER__
#define MITRO_JSON_KEYSET_READER__

#include <keyczar/rw/keyset_reader.h>

namespace keyczar {
class Keyset;
}

namespace mitro {

// Reads Keyczar keysets from a JSON string.
class JsonKeysetReader : public keyczar::rw::KeysetReader {
public:
  // Creates a new reader.
  JsonKeysetReader() {}
  virtual ~JsonKeysetReader() {}

  virtual Value* ReadMetadata() const;
  virtual Value* ReadKey(int version) const;

  // Reads the keyset from json_string, returning true if successful.
  bool ReadKeyString(const std::string& json_string);

private:
  scoped_ptr<DictionaryValue> key_dict_;

  DISALLOW_COPY_AND_ASSIGN(JsonKeysetReader);
};

// Reads password-based encryption (PBE) key sets.
class PBEKeysetReader : public keyczar::rw::KeysetReader {
public:
  // Reads keys from keyset, decrypting them using password. Does NOT own keyset.
  PBEKeysetReader(const KeysetReader* keyset, const std::string& password);
  virtual ~PBEKeysetReader() {}

  virtual Value* ReadKey(int version) const;
  virtual Value* ReadMetadata() const;

 private:
  const KeysetReader* keyset_;
  const std::string password_;

  DISALLOW_COPY_AND_ASSIGN(PBEKeysetReader);
};

// Serializes keyset to a JSON string.
std::string SerializeKeysetToJson(const keyczar::Keyset& keyset);

// Returns a DictionaryValue parsed from json, or NULL on failure. Caller owns the return value.
DictionaryValue* ParseJsonToDictionary(const std::string& json);

// Serializes value to output, returning true if successful. The value in output is replaced.
bool SerializeJsonValue(const Value& value, std::string* output);

}  // namespace mitro
#endif
