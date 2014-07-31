// Copyright 2011 Google Inc. All Rights Reserved.
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
#include <iostream>

#include <keyczar/base/base64w.h>
#include <keyczar/keyczar_test.h>
#include <keyczar/session.h>
#include <testing/gtest/include/gtest/gtest.h>

namespace keyczar {

class SessionTest : public KeyczarTest {
 protected:
  virtual void SetUp() {
    KeyczarTest::SetUp();  // Sets up paths and data.
  }

  // Utility methods
  SignedSessionEncrypter* BuildEnc(
      const std::string& enc_type = "aes",
      const std::string& sig_type = "hmac") const {
    FilePath enc_path = data_path_.Append(enc_type);
    FilePath sig_path = data_path_.Append(sig_type);

    return SignedSessionEncrypter::NewSessionEncrypter(
        Encrypter::Read(enc_path), Signer::Read(sig_path));
  }

  SignedSessionDecrypter* BuildDec(
      const std::string& session_material,
      const std::string& enc_type = "aes",
      const std::string& sig_type = "hmac") const {
    FilePath enc_path = data_path_.Append(enc_type);
    FilePath sig_path = data_path_.Append(sig_type);

    return SignedSessionDecrypter::NewSessionDecrypter(
        Crypter::Read(enc_path), Verifier::Read(sig_path), session_material);
  }

  // Common test methods
  void RoundTripWithSpecificKeys(const std::string& encrypt_path,
                                 const std::string& sign_path) const;
};

TEST_F(SessionTest, RoundTripWithVariousKeyTypes) {
  const int kNumEncAlgs = 2;
  std::string enc_algs[kNumEncAlgs] = { "aes", "rsa" };
  const int kNumSigAlgs = 4;
  std::string sig_algs[kNumSigAlgs] = { "hmac", "dsa", "rsa-sign", "ecdsa"};

  for (int enc_iter = 0; enc_iter < kNumEncAlgs; ++enc_iter)
    for (int sig_iter = 0; sig_iter < kNumSigAlgs; ++sig_iter)
      RoundTripWithSpecificKeys(enc_algs[enc_iter], sig_algs[sig_iter]);
}

TEST_F(SessionTest, ConstructWithBadCrypters) {
  FilePath enc_path = data_path_.Append("aes");
  FilePath sig_path = data_path_.Append("hmac");

  scoped_ptr<SignedSessionEncrypter> encrypter(
      SignedSessionEncrypter::NewSessionEncrypter(
          NULL, Signer::Read(sig_path)));
  ASSERT_FALSE(encrypter.get());

  scoped_ptr<SignedSessionDecrypter> decrypter(
      SignedSessionDecrypter::NewSessionDecrypter(
          NULL, Verifier::Read(sig_path), ""));
  ASSERT_FALSE(decrypter.get());

  encrypter.reset(
      SignedSessionEncrypter::NewSessionEncrypter(
          Encrypter::Read(enc_path), NULL));
  ASSERT_FALSE(encrypter.get());

  decrypter.reset(
      SignedSessionDecrypter::NewSessionDecrypter(
          Crypter::Read(enc_path), NULL, ""));
  ASSERT_FALSE(decrypter.get());
}

TEST_F(SessionTest, ConstructDecrypterWithBadSession) {
  scoped_ptr<SignedSessionDecrypter> decrypter(BuildDec("garbage"));
  ASSERT_FALSE(decrypter.get());
}

TEST_F(SessionTest, DecryptWithWrongKeys) {
  scoped_ptr<SignedSessionEncrypter> encrypter(BuildEnc());
  ASSERT_TRUE(encrypter.get());

  std::string session_material = encrypter->EncryptedSessionBlob();
  ASSERT_GT(session_material.size(), 0);

  std::string ciphertext = encrypter->SessionEncrypt(input_data_);
  ASSERT_GT(ciphertext.size(), 0);

  // Construction should fail if wrong decryption key given.
  scoped_ptr<SignedSessionDecrypter> decrypter(
      BuildDec(session_material, "rsa", "hmac"));
  ASSERT_FALSE(decrypter.get());

  // Decryption should fail if wrong verification key given.
  decrypter.reset(BuildDec(session_material, "aes", "dsa"));
  ASSERT_TRUE(decrypter.get());

  std::string plaintext = decrypter->SessionDecrypt(ciphertext);
  ASSERT_EQ(plaintext.size(), 0);
}

TEST_F(SessionTest, DecryptWithWrongSession) {
  scoped_ptr<SignedSessionEncrypter> encrypter(BuildEnc());
  ASSERT_TRUE(encrypter.get());

  // Encrypt some data with session.
  std::string ciphertext = encrypter->SessionEncrypt(input_data_);
  ASSERT_GT(ciphertext.size(), 0);

  // Create new session encrypter (and hence new session).
  encrypter.reset(BuildEnc());
  ASSERT_TRUE(encrypter.get());

  std::string session_material = encrypter->EncryptedSessionBlob();
  ASSERT_GT(session_material.size(), 0);

  scoped_ptr<SignedSessionDecrypter> decrypter(BuildDec(session_material));
  ASSERT_TRUE(decrypter.get());

  // Decryption should fail; wrong session key.
  std::string plaintext = decrypter->SessionDecrypt(ciphertext);
  ASSERT_EQ(plaintext.size(), 0);
}

TEST_F(SessionTest, Base64Encoding) {
  scoped_ptr<SignedSessionEncrypter> encrypter(BuildEnc());
  ASSERT_TRUE(encrypter.get());

  std::string session_material = encrypter->EncryptedSessionBlob();
  ASSERT_GT(session_material.size(), 0);

  std::string ciphertext = encrypter->SessionEncrypt(input_data_);
  ASSERT_GT(ciphertext.size(), 0);

  std::string binary_ciphertext;
  // Decoding will fail with high probability if ciphertext is binary.
  ASSERT_TRUE(base::Base64WDecode(ciphertext, &binary_ciphertext));

  scoped_ptr<SignedSessionDecrypter> decrypter(BuildDec(session_material));

  // Should be able to decrypt original with decrypter w/default encoding.
  std::string plaintext = decrypter->SessionDecrypt(ciphertext);
  ASSERT_EQ(input_data_, plaintext);

  // Should be able to decrypt decoded with decrypter set to binary.
  decrypter->set_encoding(Keyczar::NO_ENCODING);
  plaintext = decrypter->SessionDecrypt(binary_ciphertext);
  ASSERT_EQ(input_data_, plaintext);
}

TEST_F(SessionTest, BinaryEncoding) {
  scoped_ptr<SignedSessionEncrypter> encrypter(BuildEnc());
  ASSERT_TRUE(encrypter.get());

  std::string session_material = encrypter->EncryptedSessionBlob();
  ASSERT_GT(session_material.size(), 0);

  encrypter->set_encoding(Keyczar::NO_ENCODING);
  std::string binary_ciphertext = encrypter->SessionEncrypt(input_data_);
  ASSERT_GT(binary_ciphertext.size(), 0);

  scoped_ptr<SignedSessionDecrypter> decrypter(BuildDec(session_material));

  decrypter->set_encoding(Keyczar::NO_ENCODING);
  std::string plaintext = decrypter->SessionDecrypt(binary_ciphertext);
  ASSERT_EQ(input_data_, plaintext);

  // Should be able to decrypt decoded with decrypter set to Base64W.
  std::string encoded_ciphertext;
  ASSERT_TRUE(base::Base64WEncode(binary_ciphertext, &encoded_ciphertext));

  decrypter->set_encoding(Keyczar::BASE64W);
  plaintext = decrypter->SessionDecrypt(encoded_ciphertext);
  ASSERT_EQ(input_data_, plaintext);
}

void SessionTest::RoundTripWithSpecificKeys(
    const std::string& enc_type,
    const std::string& sig_type) const {
  scoped_ptr<SignedSessionEncrypter> encrypter(BuildEnc(enc_type, sig_type));
  ASSERT_TRUE(encrypter.get());

  std::string session_material = encrypter->EncryptedSessionBlob();
  ASSERT_GT(session_material.size(), 0);

  // Session material should be Base64W-encoded.  Decoding will fail with
  // high probability if it's not.
  std::string decoded_session;
  ASSERT_TRUE(base::Base64WDecode(session_material, &decoded_session));

  std::string ciphertext = encrypter->SessionEncrypt(input_data_);
  ASSERT_GT(ciphertext.size(), 0);

  scoped_ptr<SignedSessionDecrypter> decrypter(
      BuildDec(session_material, enc_type, sig_type));
  ASSERT_TRUE(decrypter.get());

  std::string plaintext = decrypter->SessionDecrypt(ciphertext);
  ASSERT_EQ(input_data_, plaintext);
}

} // namespace keyzcar
