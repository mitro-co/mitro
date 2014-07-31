#include "keyczar_json/mitrokey.h"

#include <tr1/unordered_map>

#include <keyczar/base/stl_util-inl.h>

#include <keyczar/base/logging.h>
#include <keyczar/keyczar.h>
#include <keyczar/rw/keyset_writer.h>

#include "keyczar_json/json_keyset_reader.h"
#include "keyczar_json/keyczar_session.h"

using keyczar::Crypter;
using keyczar::KeyPurpose;
using keyczar::Keyset;
using keyczar::KeysetMetadata;
using keyczar::KeyStatus;
using keyczar::KeyType;
using keyczar::Signer;

namespace mitro {

static const char ENCRYPTION_KEY[] = "encryption";
static const char SIGNING_KEY[] = "signing";

static const KeyType::Type DEFAULT_KEY_TYPE = KeyType::RSA_PRIV;

static keyczar::Keyczar* ReadCrypter(const keyczar::rw::KeysetReader& reader) {
  return keyczar::Crypter::Read(reader);
}

static keyczar::Keyczar* ReadSigner(const keyczar::rw::KeysetReader& reader) {
  return keyczar::Signer::Read(reader);
}

static keyczar::Keyczar* ReadEncrypter(const keyczar::rw::KeysetReader& reader) {
  return keyczar::Encrypter::Read(reader);
}

static keyczar::Keyczar* ReadVerifier(const keyczar::rw::KeysetReader& reader) {
  return keyczar::Verifier::Read(reader);
}

static keyczar::Keyczar* ReadKeyczarFromJson(const DictionaryValue& dict, const std::string& key,
    const std::string* password, ReaderFunction reader_function) {
  // Read the key
  std::string data;
  if (!dict.GetString(key, &data)) {
    LOG(ERROR) << "could not read string \"" << key << "\" in MitroKey";
    return NULL;
  }

  JsonKeysetReader json_reader;
  if (!json_reader.ReadKeyString(data)) {
    LOG(ERROR) << "error parsing key from JSON: " << key;
    return NULL;
  }

  const keyczar::rw::KeysetReader* reader = &json_reader;
  scoped_ptr<PBEKeysetReader> pbe_reader;
  if (password != NULL) {
    pbe_reader.reset(new PBEKeysetReader(&json_reader, *password));
    reader = pbe_reader.get();
  }

  return reader_function(*reader);
}

bool KeyPair::Read(const std::string& input, const std::string* password, ReaderFunction encryption_reader,
    ReaderFunction signing_reader) {
  CHECK(encryption_ == NULL);
  CHECK(signing_ == NULL);

  scoped_ptr<DictionaryValue> root(ParseJsonToDictionary(input));
  if (root == NULL) {
    return false;
  }

  scoped_ptr<keyczar::Keyczar> encryption(
      ReadKeyczarFromJson(*root, ENCRYPTION_KEY, password, encryption_reader));
  if (encryption == NULL) {
    LOG(ERROR) << "error reading encryption key";
    return false;
  }

  scoped_ptr<keyczar::Keyczar> signing(
      ReadKeyczarFromJson(*root, SIGNING_KEY, password, signing_reader));
  if (signing == NULL) {
    LOG(ERROR) << "error reading signing key";
    return false;
  }

  Assign(encryption.release(), signing.release());
  return true;
}

std::string KeyPair::ToJson() const {
  DictionaryValue root;
  std::string encryption = mitro::SerializeKeysetToJson(*encryption_->keyset());
  root.SetString(ENCRYPTION_KEY, encryption);
  std::string signing = mitro::SerializeKeysetToJson(*signing_->keyset());
  root.SetString(SIGNING_KEY, signing);

  std::string output;
  bool success = SerializeJsonValue(root, &output);
  CHECK(success);
  return output;
}

void KeyPair::Assign(keyczar::Keyczar* encryption, keyczar::Keyczar* signing) {
  CHECK(encryption_ == NULL);
  CHECK(signing_ == NULL);
  CHECK(encryption->keyset()->metadata()->key_purpose() == KeyPurpose::ENCRYPT ||
      encryption->keyset()->metadata()->key_purpose() == KeyPurpose::DECRYPT_AND_ENCRYPT);
  CHECK(signing->keyset()->metadata()->key_purpose() == KeyPurpose::VERIFY ||
      signing->keyset()->metadata()->key_purpose() == KeyPurpose::SIGN_AND_VERIFY);

  encryption_.reset(encryption);
  signing_.reset(signing);

  // turn off base64 encoding for the encrypter: we use sessions
  encryption_->set_encoding(keyczar::Keyczar::NO_ENCODING);
}


MitroPublicKey::MitroPublicKey() {}
MitroPublicKey::~MitroPublicKey() {}

MitroPublicKey* MitroPublicKey::ReadNew(const std::string& input) {
  scoped_ptr<MitroPublicKey> key(new MitroPublicKey());
  if (!key->keys_.Read(input, NULL, &ReadEncrypter, &ReadVerifier)) {
    return NULL;
  }
  return key.release();
}

bool MitroPublicKey::Read(const std::string& input) {
  return keys_.Read(input, NULL, &ReadEncrypter, &ReadVerifier);
}

MitroPublicKey* MitroPublicKey::ReadNewFromKeyset(const keyczar::rw::KeysetReader& encryption,
    const keyczar::rw::KeysetReader& signing) {
  scoped_ptr<MitroPublicKey> key(new MitroPublicKey());

  scoped_ptr<keyczar::Encrypter> encrypter(keyczar::Encrypter::Read(encryption));
  scoped_ptr<keyczar::Verifier> verifier(keyczar::Verifier::Read(signing));

  key->keys_.Assign(encrypter.release(), verifier.release());
  return key.release();
}

bool MitroPublicKey::Encrypt(const std::string& input, std::string* output) const {
  return EncryptSession(*keys_.encryption_, input, output);
}

bool MitroPublicKey::Verify(const std::string& input, const std::string& signature) const {
  return keys_.signing_->Verify(input, signature);
}


MitroPrivateKey::MitroPrivateKey() {}
MitroPrivateKey::~MitroPrivateKey() {}

MitroPrivateKey* MitroPrivateKey::ReadMaybeEncrypted(
    const std::string& input, const std::string* password) {
  scoped_ptr<MitroPrivateKey> key(new MitroPrivateKey());
  if (!key->keys_.Read(input, password, &ReadCrypter, &ReadSigner)) {
    return NULL;
  }
  return key.release();
}

bool MitroPrivateKey::DeprecatedReadMaybeEncrypted(
    const std::string& input, const std::string* password) {
  scoped_ptr<MitroPrivateKey> key(MitroPrivateKey::ReadMaybeEncrypted(input, password));
  if (key == NULL) {
    return false;
  }

  // transfer ownership
  keys_.encryption_.swap(key->keys_.encryption_);
  keys_.signing_.swap(key->keys_.signing_);
  return true;
}

static Keyset* generatePrivateKeyset(KeyPurpose::Type key_purpose) {
  static const char TEMP_KEYSET_NAME[] = "";
  KeysetMetadata* metadata = new KeysetMetadata(
      TEMP_KEYSET_NAME, DEFAULT_KEY_TYPE, key_purpose, false, 1);

  scoped_ptr<Keyset> keyset(new Keyset());
  keyset->set_metadata(metadata);
  if (keyset->GenerateDefaultKeySize(KeyStatus::PRIMARY) == 0) {
    return NULL;
  }

  return keyset.release();
}

MitroPrivateKey* MitroPrivateKey::Generate() {
  scoped_ptr<Keyset> encryption_keyset(generatePrivateKeyset(KeyPurpose::DECRYPT_AND_ENCRYPT));
  if (encryption_keyset == NULL) {
    return NULL;
  }
  scoped_ptr<Keyset> signing_keyset(generatePrivateKeyset(KeyPurpose::SIGN_AND_VERIFY));
  if (signing_keyset == NULL) {
    return NULL;
  }

  scoped_ptr<MitroPrivateKey> key(new MitroPrivateKey());
  key->keys_.Assign(new Crypter(encryption_keyset.release()), new Signer(signing_keyset.release()));
  return key.release();
}


// Used to export a public key in memory, without serializing it.
class MemoryKeysetReaderWriter : public keyczar::rw::KeysetWriter, public keyczar::rw::KeysetReader {
 public:
  MemoryKeysetReaderWriter() {}
  virtual ~MemoryKeysetReaderWriter() {
    keyczar::base::STLDeleteValues(&keys_);
  }

  virtual bool WriteMetadata(const Value& metadata) const {
    CHECK(metadata_ == NULL);
    mutable_this()->metadata_.reset(metadata.DeepCopy());
    return true;
  }

  virtual bool WriteKey(const Value& key, int version) const {
    std::pair<KeyMap::iterator, bool> result =
        mutable_this()->keys_.insert(KeyMap::value_type(version, NULL));
    // means key already exists; shouldn't happen
    CHECK(result.second);
    CHECK(result.first->second == NULL);
    result.first->second = key.DeepCopy();
    return true;
  }

  virtual void OnUpdatedKeysetMetadata(const KeysetMetadata& key_metadata __attribute__((__unused__))) {
    CHECK(false);
  }

  virtual void OnNewKey(const keyczar::Key& key __attribute__((__unused__)), int version_number __attribute__((__unused__))) {
    CHECK(false);
  }

  virtual void OnRevokedKey(int version_number __attribute__((__unused__))) {
    CHECK(false);
  }

  virtual Value* ReadMetadata() const {
    return metadata_->DeepCopy();
  }

  virtual Value* ReadKey(int version) const {
    KeyMap::const_iterator it = keys_.find(version);
    if (it == keys_.end()) {
      return NULL;
    }
    return it->second->DeepCopy();
  }

 private:
  MemoryKeysetReaderWriter* mutable_this() const {
    // Hack for Keyczar's crappy KeysetWriter interface that is incorrectly const
    return const_cast<MemoryKeysetReaderWriter*>(this);
  }

  scoped_ptr<Value> metadata_;
  typedef std::tr1::unordered_map<int, Value*> KeyMap;
  KeyMap keys_;

  DISALLOW_COPY_AND_ASSIGN(MemoryKeysetReaderWriter);
};


MitroPublicKey* MitroPrivateKey::ExportPublicKey() const {
  if (keys_.encryption_ == NULL || keys_.signing_ == NULL) {
    return NULL;
  }

  MemoryKeysetReaderWriter encryption_writer;
  keys_.encryption_->keyset()->PublicKeyExport(encryption_writer);
  MemoryKeysetReaderWriter signing_writer;
  keys_.signing_->keyset()->PublicKeyExport(signing_writer);

  return MitroPublicKey::ReadNewFromKeyset(encryption_writer, signing_writer);
}

MitroPrivateKey* MitroPrivateKey::DecryptPrivateKey(const std::string& input) const {
  std::string decrypted_key;
  bool success = Decrypt(input, &decrypted_key);
  if (!success) {
    return NULL;
  }

  return ReadNew(decrypted_key);
}

std::string MitroPrivateKey::ToJsonEncrypted(const MitroPublicKey& encrypting_key) const {
  std::string unencrypted = keys_.ToJson();
  std::string encrypted_key;
  bool success = encrypting_key.Encrypt(unencrypted, &encrypted_key);
  CHECK(success);
  return encrypted_key;
}

bool MitroPrivateKey::Encrypt(const std::string& input, std::string* output) const {
  return EncryptSession(*keys_.encryption_, input, output);
}

bool MitroPrivateKey::Decrypt(const std::string& input, std::string* output) const {
  return DecryptSession(*keys_.encryption_, input, output);
}

bool MitroPrivateKey::Sign(const std::string& input, std::string* signature) const {
  return keys_.signing_->Sign(input, signature);
}

bool MitroPrivateKey::Verify(const std::string& input, const std::string& signature) const {
  return keys_.signing_->Verify(input, signature);
}

}  // namespace mitro
