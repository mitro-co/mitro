#ifndef MITROKEY__
#define MITROKEY__

#include <string>

#include <keyczar/base/scoped_ptr.h>

namespace keyczar {
  class Keyczar;

  namespace rw {
    class KeysetReader;
  }
}

namespace mitro {

// A function that reads a Keyczar object from a KeysetReader.
typedef keyczar::Keyczar* (*ReaderFunction)(const keyczar::rw::KeysetReader&);

class KeyPair {
public:
  bool Read(const std::string& input, const std::string* password, ReaderFunction encryption_reader,
      ReaderFunction signing_reader);

  // Returns this key serialized to JSON.
  std::string ToJson() const;

  // Takes ownership of encryption and signing.
  void Assign(keyczar::Keyczar* encryption, keyczar::Keyczar* signing);

  scoped_ptr<keyczar::Keyczar> encryption_;
  scoped_ptr<keyczar::Keyczar> signing_;
};


class MitroPublicKey {
public:
  MitroPublicKey();
  ~MitroPublicKey();

  // Reads a public key from input. Returns NULL on failure.
  static MitroPublicKey* ReadNew(const std::string& input);
  static MitroPublicKey* ReadNewFromKeyset(const keyczar::rw::KeysetReader& encryption,
      const keyczar::rw::KeysetReader& signing);

  // Returns this key serialized to JSON.
  std::string ToJson() const {
    return keys_.ToJson();
  }

  // DEPRECATED. Reads a public key from input. Returns true on success.
  bool Read(const std::string& input);

  // Encrypts input into output (base64 encoded).
  bool Encrypt(const std::string& input, std::string* output) const;

  // Returns true if signature is valid for input.
  bool Verify(const std::string& input, const std::string& signature) const;

private:
  KeyPair keys_;
};

class MitroPrivateKey {
public:
  MitroPrivateKey();
  ~MitroPrivateKey();

  // Reads a private key from input. Returns NULL on failure.
  static MitroPrivateKey* ReadNew(const std::string& input) {
    return ReadMaybeEncrypted(input, NULL);
  }

  // Reads a private key from input, decrypting with password. Returns NULL on failure.
  static MitroPrivateKey* ReadNewEncrypted(const std::string& input, const std::string& password) {
    return ReadMaybeEncrypted(input, &password);
  }

  // DEPRECATED. Reads a private key from input. Returns true on success.
  bool Read(const std::string& input) {
    return DeprecatedReadMaybeEncrypted(input, NULL);
  }

  // DEPRECATED. Reads a private key from input, decrypting with password. Returns true on success.
  bool ReadEncrypted(const std::string& input, const std::string& password) {
    return DeprecatedReadMaybeEncrypted(input, &password);
  }

  // Returns a new MitroPublicKey for this private key.
  MitroPublicKey* ExportPublicKey() const;

  // Serializes this key to JSON, encrypted with encrypting_key.
  std::string ToJsonEncrypted(const MitroPublicKey& encrypting_key) const;

  // Decrypts encrypted_key with this key, returning a new MitroPrivateKey.
  MitroPrivateKey* DecryptPrivateKey(const std::string& encrypted_key) const;

  // Generates a new private key. Returns NULL on failure.
  static MitroPrivateKey* Generate();

  // Encrypts input into output (base64 encoded).
  bool Encrypt(const std::string& input, std::string* output) const;

  // Decrypts encrypted input into output.
  bool Decrypt(const std::string& input, std::string* output) const;

  // Signs input into signature (base64 encoded).
  bool Sign(const std::string& input, std::string* signature) const;

  // Returns true if signature is valid for input.
  bool Verify(const std::string& input, const std::string& signature) const;

private:
  static MitroPrivateKey* ReadMaybeEncrypted(
      const std::string& input, const std::string* password);
  bool DeprecatedReadMaybeEncrypted(
      const std::string& input, const std::string* password);

  KeyPair keys_;
};

}  // namespace mitro
#endif
