// Copyright 2011 Google Inc. All Rights Reserved.  (This file is -*-C++-*-)
//
//
// Author: Shawn Willden (swillden@google.com)
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include <keyczar/keyczar.h>

namespace keyczar {

class Session;
class SessionTest;

// A SignedSessionEncrypter is used to sign and encrypt data using a session
// key.  The usage flow is as follows:
//
// 1. Load an Encrypter and a Signer, and pass them to
//    NewSessionEncrypter to create a SignedSessionEncrypter.  This
//    will create session key.
//
// 2. Call EncyryptedSessionBlob() to retrieve the encrypted and
//    base64-encoded session data.  This must be provided to the
//    SignedSessionDecrypter.
//
// 3. Call SessionEncrypt() to encrypt the data payload with the session key
//    and sign it with the Signer.  The output is an AttachedSign blob
//    containing both data and signature.
//
// Step 3 may be repeated multiple times.  The same session key and
// nonce will be used for all encryptions, so it's critical that it
// not be called multiple times with the same plaintext, because the
// output will be identical.
//
// This class is threadsafe, except for the methods that update encoding and
// compression, assuming the provided Encrypter and Signer are not used
// outside of the SignedSessionEncrypter.
class SignedSessionEncrypter {
 public:
  // Creates a new SignedSessionEncrypter, returning NULL on failure.  The
  // caller owns the return value.  The SignedSessionEncrypter takes
  // ownership of |encrypter| and |signer|, even when creation fails.
  static SignedSessionEncrypter* NewSessionEncrypter(
      Encrypter* encrypter,
      Signer* signer,
      int session_key_bits);

  static SignedSessionEncrypter* NewSessionEncrypter(
      Encrypter* encrypter,
      Signer* signer) {
    return NewSessionEncrypter(encrypter, signer, 128);
  }

  ~SignedSessionEncrypter();

  // Retrieve the encoded and encrypted session key and nonce and place it in
  // |session_material| as a Base64W-encoded string (always encoded
  // regardless of the current encoding setting).
  bool EncryptedSessionBlob(std::string* session_material) const;

  // Return the encoded and encrypted session key and nonce.
  std::string EncryptedSessionBlob() const;

  // Encrypts plaintext with the session key and signs it with the Signer.
  // If successful, places the signed ciphertext in |signed_ciphertext| and
  // returns true.  Otherwise, returns false and does not modify
  // |signed_ciphertext|.
  bool SessionEncrypt(const std::string& plaintext,
                      std::string* signed_ciphertext) const ;

  // Encrypts plaintext with the session key and signs it with the Signer.
  // If successful, returns the signed ciphertext.  Otherwise, returns an
  // empty string.
  std::string SessionEncrypt(const std::string& plaintext) const;

  // Returns the current encoding algorithm set.  Default is Base64W
  // encoding, unless modified by set_encoding() below.  This is true
  // regardless of the encoding setting of the provided Encrypter or Signer.
  Keyczar::Encoding encoding() const { return signer_->encoding(); }

  // Replaces the current encoding algorithm by |encoding|
  void set_encoding(Keyczar::Encoding encoding) {
    signer_->set_encoding(encoding);
  }

  // Returns the current compression algorithm.  Default is no compression,
  // unless modified by set_compression() below.  This is true regardless of
  // the compression setting of the provided Encrypter or Signer.
  Keyczar::Compression compression() const { return signer_->compression(); }

  void set_compression(Keyczar::Compression compression) {
    encrypter_->set_compression(compression);
    signer_->set_compression(compression);
  }

private:
  explicit SignedSessionEncrypter(Encrypter* encrypter,
                                  Signer* signer,
                                  const Session* session)
      : encrypter_(encrypter), signer_(signer), session_(session) {
    encrypter_->set_encoding(Keyczar::BASE64W);
    signer_->set_encoding(Keyczar::BASE64W);
    encrypter_->set_compression(Keyczar::NO_COMPRESSION);
    signer_->set_compression(Keyczar::NO_COMPRESSION);
  }

  scoped_ptr<Encrypter> encrypter_;
  scoped_ptr<Signer> signer_;
  const Session* session_; // raw because scoped_ptr requires full definition

  DISALLOW_COPY_AND_ASSIGN(SignedSessionEncrypter);
};

// A SignedSessionDecryper is used to verify and decrypt data that was
// encrypted by a SignedSessionEncrypter.  The usage flow is as follows:
//
// 1.  Load a Crypter and a Verifier, using the correct decryption and
//     verification keys, and pass them and the session material
//     (Base64W-encoded) to NewSessionEncrypter to create a
//     SignedSessionDecryper.
//
// 2.  Call SessionDecrypt with the encrypted and signed ciphertext.
//
// The const methods of this class are threadsafe, assuming the provided
// Verifier is not used outside of the SignedSessionDecrypter.
class SignedSessionDecrypter {
 public:
  // Creates a new SignedSessionDecrypter, returning NULL on failure.  The
  // caller owns the return value.  The SignedSessionDecrypter takes
  // ownership of |crypter| and |verifier|, even if creation fails.
  static SignedSessionDecrypter* NewSessionDecrypter(
      Crypter* crypter,
      Verifier* verifier,
      const std::string& session_material);

  ~SignedSessionDecrypter();

  // Verifies and decrypts |ciphertext|, placing the result in |plaintext|.
  // If there is an error or if verification fails, this method will return
  // false.
  bool SessionDecrypt(const std::string& ciphertext,
                      std::string* plaintext) const;

  // Verifies and decrypts |ciphertext|, returning the result.  If there is
  // an error or if verification fails, this method will return an empty
  // string.
  std::string SessionDecrypt(const std::string& ciphertext) const;

  // Returns the current encoding algorithm set.  Default is Base64W
  // encoding, unless modified by set_encoding() below.  This is true
  // regardless of the encoding setting of the provided Crypter or Verifier.
  Keyczar::Encoding encoding() const { return verifier_->encoding(); }

  // Replaces the current encoding algorithm by |encoding|
  void set_encoding(Keyczar::Encoding encoding) {
    verifier_->set_encoding(encoding);
  }

  // Returns the current compression algorithm.  Default is no compression,
  // unless modified by set_compression() below.  This is true regardless of
  // the compression setting of the provided Crypter or Verifier.
  Keyczar::Compression compression() const { return verifier_->compression(); }

  // Replaces the current compression algorithm by |compression|.
  void set_compression(Keyczar::Compression compression) {
    verifier_->set_compression(compression);
  };

private:
  SignedSessionDecrypter(Verifier* verifier,
                         const Session* session)
      : verifier_(verifier), session_(session) {
    verifier_->set_encoding(Keyczar::BASE64W);
    verifier_->set_compression(Keyczar::NO_COMPRESSION);
  }

  scoped_ptr<Verifier> verifier_;
  const Session* session_; // raw because scoped_ptr requires full definition
};

} // namespace keyczar
