#include "keyczar_session.h"

#include <limits>

#include <keyczar/aes_key.h>
#include <keyczar/base/base64w.h>
#include <keyczar/base/logging.h>
#include <keyczar/base/ref_counted.h>
#include <keyczar/base/scoped_ptr.h>
#include <keyczar/base/values.h>
#include <keyczar/cipher_mode.h>
#include <keyczar/crypto_factory.h>
#include <keyczar/key_type.h>
#include <keyczar/keyczar.h>
#include <keyczar/rand_impl.h>

using keyczar::AESKey;
using keyczar::CipherMode;
using keyczar::Crypter;
using keyczar::Keyczar;
using keyczar::KeyType;
using std::string;

namespace mitro {

static char* WriteBigEndian(char* location, int32_t value) {
  location[0] = (value >> 24) & 0xff;
  location[1] = (value >> 16) & 0xff;
  location[2] = (value >> 8) & 0xff;
  location[3] = value & 0xff;
  location += 4;
  return location;
}

static bool ReadBigEndian(const char* location, const char* end, int32_t* output) {
  if (location + 4 > end) {
    return false;
  }

  *output = ((location[3] & 0xff) << 0) |
      ((location[2] & 0xff) << 8) |
      ((location[1] & 0xff) << 16) |
      ((location[0] & 0xff) << 24);
  return true;
}

string LenPrefixPack(const string* const strings[], size_t num_strings) {
  // TODO: Handle overflow?
  CHECK(num_strings < (size_t) std::numeric_limits<int32_t>::max());

  // Count an int for each input array, and one for the number of arrays
  int32_t output_size = (1 + num_strings) * 4;
  for (size_t i = 0; i < num_strings; i++) {
    output_size += strings[i]->size();
  }
  CHECK(output_size > 0);

  string result(output_size, '\x00');
  char* data = const_cast<char*>(result.data());

  // write total number of arrays
  data = WriteBigEndian(data, (int32_t) num_strings);

  // write each array
  for (size_t i = 0; i < num_strings; i++) {
    // write bytes length, then the bytes
    data = WriteBigEndian(data, (int32_t) strings[i]->size());
    memcpy(data, strings[i]->data(), strings[i]->size());
    data += strings[i]->size();
  }
  CHECK(data == result.data() + output_size);
  return result;
}

bool PairPrefixUnpack(const string& data, string* v1, string* v2) {
  // read number of arrays
  const char* data_bytes = data.data();
  const char* end = data.data() + data.size();
  int32_t num_byte_strings = 0;
  if (!ReadBigEndian(data_bytes, end, &num_byte_strings)) {
    return false;
  }
  data_bytes += 4;
  if (num_byte_strings != 2) {
    return false;
  }

  // read v1
  int32_t length = 0;
  if (!ReadBigEndian(data_bytes, end, &length)) {
    return false;
  }
  data_bytes += 4;
  if (!(0 <= length && length <= (end - data_bytes))) {
    return false;
  }
  v1->assign(data_bytes, length);
  data_bytes += length;

  // read v2
  if (!ReadBigEndian(data_bytes, end, &length)) {
    return false;
  }
  data_bytes += 4;
    if (!(0 <= length && length <= (end - data_bytes))) {
    return false;
  }
  v2->assign(data_bytes, length);
  data_bytes += length;

  CHECK(data_bytes == data.data() + data.size());
  return true;
}

static const char AES_BYTES_PROPERTY[] = "aesKeyString";
static const char HMAC_BYTES_PROPERTY[] = "hmacKey.hmacKeyString";
static const char HMAC_SIZE_PROPERTY[] = "hmacKey.size";
static const char HMAC_DIGEST_PROPERTY[] = "hmacKey.digest";
static const char HMAC_SHA1[] = "SHA1";

static bool GetStringAndBase64Decode(const DictionaryValue& dict, const string key,
    string* output) {
  string encoded;
  if (!dict.GetString(key, &encoded) || !keyczar::base::Base64WDecode(encoded, output)) {
    return false;
  }
  return true;
}

string PackAESKey(const AESKey& key) {
  // disgusting hack: read key bits out of JSON.
  // TODO: Push a patch upstream to avoid this grossness
  scoped_ptr<const Value> json(key.GetValue());
  CHECK(json->IsType(Value::TYPE_DICTIONARY));
  const DictionaryValue* dict = (DictionaryValue*) json.get();

  // load the AES key bytes
  string aes_bytes;
  if (!GetStringAndBase64Decode(*dict, AES_BYTES_PROPERTY, &aes_bytes)) {
    return string();
  }
  CHECK(aes_bytes.size() == (size_t) key.size()/8);

  // load the HMAC key
  string hmac_bytes;
  if (!GetStringAndBase64Decode(*dict, HMAC_BYTES_PROPERTY, &hmac_bytes)) {
    return string();
  }

  // When building without the --compat switch, this is required
  // string digest;
  // if (!dict->GetString(HMAC_DIGEST_PROPERTY, &digest) || digest != HMAC_SHA1) {
  //   return string();
  // }

  // pack them together
  const string* VALUES[] = {&aes_bytes, &hmac_bytes};
  return LenPrefixPack(VALUES, sizeof(VALUES)/sizeof(*VALUES));
}

keyczar::AESKey* UnpackAESKey(const string& data) {
  // pull out the data
  string aes_bytes;
  string hmac_bytes;
  if (!PairPrefixUnpack(data, &aes_bytes, &hmac_bytes)) {
    return NULL;
  }

  // disgusting hack: assemble a JSON object to load the key
  DictionaryValue dict;
  dict.SetString("mode", CipherMode::GetNameFromType(CipherMode::CBC));

  string aes_encoded;
  if (!keyczar::base::Base64WEncode(aes_bytes, &aes_encoded)) {
    return NULL;
  }
  dict.SetString(AES_BYTES_PROPERTY, aes_encoded);
  dict.SetInteger("size", aes_bytes.size()*8);

  string hmac_encoded;
  if (!keyczar::base::Base64WEncode(hmac_bytes, &hmac_encoded)) {
    return NULL;
  }
  dict.SetString(HMAC_BYTES_PROPERTY, hmac_encoded);
  dict.SetInteger(HMAC_SIZE_PROPERTY, hmac_bytes.size()*8);
  // When building without the --compat switch, this is required
  // dict.SetString(HMAC_DIGEST_PROPERTY, HMAC_SHA1);

  return AESKey::CreateFromValue(dict);
}

bool EncryptSession(const Keyczar& crypter, const string& data, string* output) {
  CHECK(crypter.encoding() == Keyczar::NO_ENCODING);

  // Generate a key
  static const int DEFAULT_SESSION_BITS = keyczar::KeyType::CipherSizes(keyczar::KeyType::AES)[0];
  scoped_refptr<keyczar::AESKey> key(keyczar::AESKey::GenerateKey(DEFAULT_SESSION_BITS));
  string session_material = PackAESKey(*key);

  // encrypt the session material
  string encrypted_session;
  if (!crypter.Encrypt(session_material, &encrypted_session)) {
    return false;
  }

  // encrypt data with the session; plain AESKey doesn't do base64
  string ciphertext;
  if (!key->Encrypt(data, &ciphertext)) {
    return false;
  }

  // Pack everything together
  const string* values[] = {&encrypted_session, &ciphertext};
  string result = LenPrefixPack(values, sizeof(values)/sizeof(*values));

  // Base64 encode
  return keyczar::base::Base64WEncode(result, output);
}

bool DecryptSession(const Keyczar& crypter, const string& data, string* output) {
  CHECK(crypter.encoding() == Keyczar::NO_ENCODING);

  // Base64 decode
  string decoded;
  if (!keyczar::base::Base64WDecode(data, &decoded)) {
    return false;
  }

  // Unpack the session material and ciphertext
  string encrypted_session;
  string ciphertext;
  if (!PairPrefixUnpack(decoded, &encrypted_session, &ciphertext)) {
    return false;
  }

  // Decrypt the session and load the session key
  string session_material;
  if (!crypter.Decrypt(encrypted_session, &session_material)) {
    return false;
  }
  scoped_refptr<AESKey> key(UnpackAESKey(session_material));
  if (key == NULL) {
    return false;
  }

  // Decrypt data with plain AESKey; no base64
  return key->Decrypt(ciphertext, output);
}

}  // namespace mitro


