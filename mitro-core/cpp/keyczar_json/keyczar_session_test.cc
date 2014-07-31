#include <gtest/gtest.h>
#include <keyczar/keyczar.h>
// #include <keyczar/key_type.h>
#include <keyczar/aes_key.h>

// #include <keyczar/base/file_util.h>

#include "json_keyset_reader.h"
#include "keyczar_session.h"

using std::string;

namespace mitro {

static const char MESSAGE[] = "hello this is a message";
static const char KEY[] = "{\"meta\":\"{\\\"name\\\":\\\"Key\\\",\\\"purpose\\\":\\\"DECRYPT_AND_ENCRYPT\\\",\\\"type\\\":\\\"RSA_PRIV\\\",\\\"versions\\\":[{\\\"exportable\\\":false,\\\"status\\\":\\\"PRIMARY\\\",\\\"versionNumber\\\":1}],\\\"encrypted\\\":false}\",\"1\":\"{\\\"publicKey\\\":{\\\"modulus\\\":\\\"AIHxWE7519j3ryp3S9KpmIMpZcXmnvPJ79IzkWivdfvSChRpab2RM21VVq7UtL-jiwhxVm6kwy_GEp9CUjA1m1VWWTrBgB7QbcRqwa-jXSvP2qehqO5sNRtrN2woFKlXekZWTjy_hEc4eawyuJ5YCF2QaZORe5_vn6rNMQwh3f2obOdJwtCK0i7eAl2-ZhGIwnkVxdjBnZBjMiyjkd3aX9QiqpQO0xJzuptGsghO44cswtlUmDZW71RjMROw-jyNC-HL0sWEiPjW7ne2txmW9MiYcAjsfURBXLYV0jferEISpbs4EulNnNvTY5Fy6lLSPsH71NcZQ0dzPy0WeJgaYulU5za1gCBY0voLQ3tEhjoKzUHNaU5UdAAr7-UF2od8pkMJN-H8bMNcWuvX9puqzyWhLuhkNeF9QHR2hsoOcE0-pKY2exv3dzel1wJlSvb2mQu5I6glIA2EwqB_Pe1T2po-8pdD5x5WdN0K3BUr_2RS5T7GsnJIV3yNaEHM_vKyRfJCNv9VaogfvVrAC8ZrqSxsrpccnMDb54Y8V8c-sK3gzS29JGsMTohyvEp9Oibtz2bu7D-f1ITqI013QmHyVh_vhBc8cxEGf84GQyDmZex7ycvhFLSA9IkN0u4sXh0yMZLS74Nd9ZJqPqEInFsBE-YcSeo2outVQmJB6oI3HtM_\\\",\\\"publicExponent\\\":\\\"AQAB\\\",\\\"size\\\":4096},\\\"privateExponent\\\":\\\"ddQGMp8FJh36fXRtkdntjMnmGlz0N7YePCo8qYOpZa5hK38mvhnDW1nt31zLhZbjxMUZDf1jbJecXUODSWnir7A7-zZpWge_8UKo3P4yxz6UVDsX9xKx2WoQeIejrKbBHrS1_wnfBcHlOstHc3X6J_WCdqiOREnCK9wipJC1PoUSM2xENlwbGRqBZMX4wd-UEL44iEOQ_9OjNK0h4N1a1f6VVOpYZTwBszmYyEXYtcNV8EJwP533KTtEzklFr9BqPNZZCngSyCoFVRKe15p13suzk7P2JwVp70a8N6M36jYEiE7hfyK8oOz_NW3Ro0Y4WChmKKIkys5rNBRTSAFtPDTqZgmjyqjXAYP0VrRN3UBWzUxXJb5t-QcclcLpbZe2cFzQJLZvc7sb9YWmoQ8weVYpXbi5cEcSzLjJML1I8HQTFaOfQsHtsyDcBj8MgCmsS5I3LjD03l9b9AO4sbJ7REynYMn4CQuQ2hV0Do_xVAWykwke4wNVp_YHYZlcKpPkXhPko4laCnlxhBFes4lfQu2Wn_Dcbg2EshhnSm-1w__rL600Wl_L6cAtd06fV8ZJpDYbgkciY5eybCHnv3xGnKJhhPJtjR8BbvI8Y9EYiU2ADFeyvxP6EUDv6osa93j8VGy-k_pbPMu5lSPdTWSraf-wtc4XzxWJQ-iAdcvK-CE\\\",\\\"primeP\\\":\\\"APOIPPXUHy1YD3WYpqqDK5fSCEIXmMXsI8b--doxqQQV_cwBkuXs7yy_ab5mkibiXCvlyHEdcbjmJ3V7GWAnShpQ1w-FBoif9hHT6N_C1q8cqbR3zlbuesNJRMRhVMh_Krfrc-n7kiyMTL4-kKTAfKTqXsntvYKv9FBInoLVEEUILsaKO39QE4JvCLrFVGVhNz9qP646BwtfqDYRWPgsSip5nasCQXs0JW1ffsFN48IApjnj-RPC-mwfF5RapIMAV6qFP0Y_jqTKtwbXH_K4TePRGKgYp6dgLl6F0LnA9QLR3A1v6zPNrdQwMS7mmAWqWdUkSBDa6WrIVcUvY2kHuA8\\\",\\\"primeQ\\\":\\\"AIiYY-WvSSwPD23yFl253mUKgqxEhcxFet6YX0olKg82Mr6e6t1BqYTfieYO0Ba4QnUWuVZWh4A1DDq5J5ZOFobnp4I7LwDX5dUZHLRrQ0vxRi7teWM1glnEOw5-pScN6ul3ii5wGCbig0EBUiTqjLhdEF2H2WRMp0ciKfCeReb_UVnYzUU-BGgunxDzSJrLJ6xvcXLAjfYZpkA8KEclLOEnYR0zOAKfA1_UbQN-inVSpr2j-arRruj6rZTmZgih28nIb0GkRKB6dNypJx3vrquTJgbXYPlU67lqTGB50DCQTeJ7OAcTkWpeiokNlFiMQv45yH4BvocFW2TyKgjOgdE\\\",\\\"primeExponentP\\\":\\\"AOKodjRGTQ4i26CThutEUhpnfRAmbiYn9dSME3ckqS4IWcK55ELjFmgLbxuq03QlUav5nWjKAsIYU7lf7Oo1Jx7_BmqHYOFgH-HP3PoFmVI51ykEKKN4KgH_-2TbQdGpqdSr66JmZlHX8sVN7cmo7VmAWOCPyMYNhdIDBKS4MQBO8VoP5fhESyQmu3U5m6cPqB_5f3NkxpGrYh-QXcH9VrnZnwk-fDty-TKmoo6_M6-ocw2GRJhD7FcdSRDmnB7g_rbSdWWoWda-ZUKUjkOVjcjvH4e0CrYZKo8G9JEuKduJ_sjy1XHNiG_oup3wOKVtO1bJAdnOJVkhQ9LWnCtw57k\\\",\\\"primeExponentQ\\\":\\\"bnPbg6UD5C6haWQylcNwg__FvosCLjWYr1pC42-93OMUkXNrs1IfQ6SGm5MqzNReWNPxNlFt7Ev5AZsq2a13Jcs4dmRpE6OA45oUzgdWOZh2CghY-dIQ_4lfv87EuRnV9MeG4xodTYQoYm4xAEzxUTtfumHZdfNN6IVFqJ4zJCEcpSxcgDsv0Rax5UD0Wscf452R_RqbMH95IcQDRdRXwBDZxITgK65tShY5uHOgflJBJmPrxScc6qOZrSfJJf3L_hSnckrke2fKC4x0Zw6jDXZFb47FiZgTrLC55eFaaYqmkBbXLVGzanHcUAq9BAUXPv92kf3TFNdOGgh_2PMzoQ\\\",\\\"crtCoefficient\\\":\\\"AJ-menwUEK289CBTEOBxI5HJHBJu4LpZKHxgkab9anPqO4F4-_ACJ9rdcMNxyQg-maJxJfc-W4ax5pnA415bxPXRjsLQBe0nThNgvAxGyvj1nNEacvQjpisSSbqiecP7fM3q0wjuTcfYyLpI-WEW4G9LjJMUA81yrkFNrnaHFOwqzH22Tb-4zgnkY3pwIIBZqjBIzXUzBGhsx9BoprMJkjbWz21Ktwg9EBhB-T3w7ZDKoobt7_igdJ3Y0c-3qMndzBAiq77ZeESbWsVM-p36dqzjdQIix-tWbo4HJevCCa0Svk808RSMP4xqVoQkSi0_L-i-M7o0PLQAeDroeSccdcU\\\",\\\"size\\\":4096}\"}";

TEST(KeyczarSessionTest, LenPrefixPackTest) {
  const string EMPTY;
  const string* values[] = {&EMPTY, &EMPTY};
  ASSERT_EQ(string(4, '\x00'), LenPrefixPack(values, 0));

  const char ONE_RAW[] = "\x00\x00\x00\x01\x00\x00\x00\x00";
  ASSERT_EQ(string(ONE_RAW, sizeof(ONE_RAW)-1), LenPrefixPack(values, 1));

  const char TWO_RAW[] = "\x00\x00\x00\x02\x00\x00\x00\x00\x00\x00\x00\x00";
  ASSERT_EQ(string(TWO_RAW, sizeof(TWO_RAW)-1), LenPrefixPack(values, 2));

  const string A("a");
  const string BC("bc");
  const string* v2[] = {&A, &BC};
  const char THREE_RAW[] = "\x00\x00\x00\x02\x00\x00\x00\x01" "a" "\x00\x00\x00\x02" "bc";
  string out = LenPrefixPack(v2, 2);
  ASSERT_EQ(string(THREE_RAW, sizeof(THREE_RAW)-1), out);

  string value1;
  string value2;
  PairPrefixUnpack(out, &value1, &value2);
  ASSERT_EQ(A, value1);
  ASSERT_EQ(BC, value2);

  // long enough to trigger signed/unsigned errors
  string manychars(128, 'a');
  const string* v3[] = {&EMPTY, &manychars};
  out = LenPrefixPack(v3, 2);

  ASSERT_TRUE(PairPrefixUnpack(out, &value1, &value2));
  ASSERT_EQ(EMPTY, value1);
  ASSERT_EQ(manychars, value2);
}

TEST(KeyczarSessionTest, PackKey) {
  // generate an AES key
  static const int DEFAULT_SESSION_BITS = keyczar::KeyType::CipherSizes(keyczar::KeyType::AES)[0];
  scoped_refptr<keyczar::AESKey> key(keyczar::AESKey::GenerateKey(DEFAULT_SESSION_BITS));

  // encrypt some data
  std::string encrypted;
  ASSERT_TRUE(key->Encrypt(MESSAGE, &encrypted));

  // pack and unpack the key
  std::string packedKey = PackAESKey(*key);
  ASSERT_GT(packedKey.size(), 0);
  scoped_refptr<keyczar::AESKey> key2(UnpackAESKey(packedKey));
  ASSERT_TRUE(key2 != NULL);

  // decrypt
  std::string decrypted;
  ASSERT_TRUE(key->Decrypt(encrypted, &decrypted));
}

TEST(KeyczarSessionTest, RoundTrip) {
  mitro::JsonKeysetReader reader;
  ASSERT_TRUE(reader.ReadKeyString(KEY));
  scoped_ptr<keyczar::Keyczar> crypter(keyczar::Crypter::Read(reader));

  // make a message longer than raw RSA can handle
  std::string input;
  for (int i = 0; i < 100; i++) {
    input.append(MESSAGE);
  }
  std::string ciphertext;
  ASSERT_FALSE(crypter->Encrypt(input, &ciphertext));

  // Use the session encrypter
  crypter->set_encoding(keyczar::Keyczar::NO_ENCODING);
  ASSERT_TRUE(EncryptSession(*crypter, input, &ciphertext));
  std::string decrypted_input;
  ASSERT_TRUE(DecryptSession(*crypter, ciphertext, &decrypted_input));
  ASSERT_EQ(input, decrypted_input);
}

}
