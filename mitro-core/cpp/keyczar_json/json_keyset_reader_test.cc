#include <gtest/gtest.h>
#include <keyczar/keyczar.h>

#include "json_keyset_reader.h"

using namespace mitro;

TEST(JsonKeysetReaderTest, BadInput) {
  mitro::JsonKeysetReader reader;
  ASSERT_FALSE(reader.ReadKeyString("not json"));
  ASSERT_FALSE(reader.ReadKeyString("[]"));
  ASSERT_FALSE(reader.ReadKeyString("{}"));
  ASSERT_FALSE(reader.ReadKeyString("{\"meta\":42}"));
  ASSERT_TRUE(reader.ReadKeyString("{\"meta\":\"\", \"2\":null}"));

  ASSERT_EQ(NULL, reader.ReadMetadata());
  ASSERT_EQ(NULL, reader.ReadKey(1));
  ASSERT_EQ(NULL, reader.ReadKey(2));
}

static keyczar::Crypter* ExportAndReRead(const keyczar::Keyczar& keyczar) {
  std::string exported = mitro::SerializeKeysetToJson(*keyczar.keyset());

  mitro::JsonKeysetReader exported_reader;
  if (!exported_reader.ReadKeyString(exported)) {
    return NULL;
  }
  return keyczar::Crypter::Read(exported_reader);
}

static const char KEY[] = "{\"meta\":\"{\\\"name\\\":\\\"Key\\\",\\\"purpose\\\":\\\"DECRYPT_AND_ENCRYPT\\\",\\\"type\\\":\\\"RSA_PRIV\\\",\\\"versions\\\":[{\\\"exportable\\\":false,\\\"status\\\":\\\"PRIMARY\\\",\\\"versionNumber\\\":1}],\\\"encrypted\\\":false}\",\"1\":\"{\\\"publicKey\\\":{\\\"modulus\\\":\\\"AIHxWE7519j3ryp3S9KpmIMpZcXmnvPJ79IzkWivdfvSChRpab2RM21VVq7UtL-jiwhxVm6kwy_GEp9CUjA1m1VWWTrBgB7QbcRqwa-jXSvP2qehqO5sNRtrN2woFKlXekZWTjy_hEc4eawyuJ5YCF2QaZORe5_vn6rNMQwh3f2obOdJwtCK0i7eAl2-ZhGIwnkVxdjBnZBjMiyjkd3aX9QiqpQO0xJzuptGsghO44cswtlUmDZW71RjMROw-jyNC-HL0sWEiPjW7ne2txmW9MiYcAjsfURBXLYV0jferEISpbs4EulNnNvTY5Fy6lLSPsH71NcZQ0dzPy0WeJgaYulU5za1gCBY0voLQ3tEhjoKzUHNaU5UdAAr7-UF2od8pkMJN-H8bMNcWuvX9puqzyWhLuhkNeF9QHR2hsoOcE0-pKY2exv3dzel1wJlSvb2mQu5I6glIA2EwqB_Pe1T2po-8pdD5x5WdN0K3BUr_2RS5T7GsnJIV3yNaEHM_vKyRfJCNv9VaogfvVrAC8ZrqSxsrpccnMDb54Y8V8c-sK3gzS29JGsMTohyvEp9Oibtz2bu7D-f1ITqI013QmHyVh_vhBc8cxEGf84GQyDmZex7ycvhFLSA9IkN0u4sXh0yMZLS74Nd9ZJqPqEInFsBE-YcSeo2outVQmJB6oI3HtM_\\\",\\\"publicExponent\\\":\\\"AQAB\\\",\\\"size\\\":4096},\\\"privateExponent\\\":\\\"ddQGMp8FJh36fXRtkdntjMnmGlz0N7YePCo8qYOpZa5hK38mvhnDW1nt31zLhZbjxMUZDf1jbJecXUODSWnir7A7-zZpWge_8UKo3P4yxz6UVDsX9xKx2WoQeIejrKbBHrS1_wnfBcHlOstHc3X6J_WCdqiOREnCK9wipJC1PoUSM2xENlwbGRqBZMX4wd-UEL44iEOQ_9OjNK0h4N1a1f6VVOpYZTwBszmYyEXYtcNV8EJwP533KTtEzklFr9BqPNZZCngSyCoFVRKe15p13suzk7P2JwVp70a8N6M36jYEiE7hfyK8oOz_NW3Ro0Y4WChmKKIkys5rNBRTSAFtPDTqZgmjyqjXAYP0VrRN3UBWzUxXJb5t-QcclcLpbZe2cFzQJLZvc7sb9YWmoQ8weVYpXbi5cEcSzLjJML1I8HQTFaOfQsHtsyDcBj8MgCmsS5I3LjD03l9b9AO4sbJ7REynYMn4CQuQ2hV0Do_xVAWykwke4wNVp_YHYZlcKpPkXhPko4laCnlxhBFes4lfQu2Wn_Dcbg2EshhnSm-1w__rL600Wl_L6cAtd06fV8ZJpDYbgkciY5eybCHnv3xGnKJhhPJtjR8BbvI8Y9EYiU2ADFeyvxP6EUDv6osa93j8VGy-k_pbPMu5lSPdTWSraf-wtc4XzxWJQ-iAdcvK-CE\\\",\\\"primeP\\\":\\\"APOIPPXUHy1YD3WYpqqDK5fSCEIXmMXsI8b--doxqQQV_cwBkuXs7yy_ab5mkibiXCvlyHEdcbjmJ3V7GWAnShpQ1w-FBoif9hHT6N_C1q8cqbR3zlbuesNJRMRhVMh_Krfrc-n7kiyMTL4-kKTAfKTqXsntvYKv9FBInoLVEEUILsaKO39QE4JvCLrFVGVhNz9qP646BwtfqDYRWPgsSip5nasCQXs0JW1ffsFN48IApjnj-RPC-mwfF5RapIMAV6qFP0Y_jqTKtwbXH_K4TePRGKgYp6dgLl6F0LnA9QLR3A1v6zPNrdQwMS7mmAWqWdUkSBDa6WrIVcUvY2kHuA8\\\",\\\"primeQ\\\":\\\"AIiYY-WvSSwPD23yFl253mUKgqxEhcxFet6YX0olKg82Mr6e6t1BqYTfieYO0Ba4QnUWuVZWh4A1DDq5J5ZOFobnp4I7LwDX5dUZHLRrQ0vxRi7teWM1glnEOw5-pScN6ul3ii5wGCbig0EBUiTqjLhdEF2H2WRMp0ciKfCeReb_UVnYzUU-BGgunxDzSJrLJ6xvcXLAjfYZpkA8KEclLOEnYR0zOAKfA1_UbQN-inVSpr2j-arRruj6rZTmZgih28nIb0GkRKB6dNypJx3vrquTJgbXYPlU67lqTGB50DCQTeJ7OAcTkWpeiokNlFiMQv45yH4BvocFW2TyKgjOgdE\\\",\\\"primeExponentP\\\":\\\"AOKodjRGTQ4i26CThutEUhpnfRAmbiYn9dSME3ckqS4IWcK55ELjFmgLbxuq03QlUav5nWjKAsIYU7lf7Oo1Jx7_BmqHYOFgH-HP3PoFmVI51ykEKKN4KgH_-2TbQdGpqdSr66JmZlHX8sVN7cmo7VmAWOCPyMYNhdIDBKS4MQBO8VoP5fhESyQmu3U5m6cPqB_5f3NkxpGrYh-QXcH9VrnZnwk-fDty-TKmoo6_M6-ocw2GRJhD7FcdSRDmnB7g_rbSdWWoWda-ZUKUjkOVjcjvH4e0CrYZKo8G9JEuKduJ_sjy1XHNiG_oup3wOKVtO1bJAdnOJVkhQ9LWnCtw57k\\\",\\\"primeExponentQ\\\":\\\"bnPbg6UD5C6haWQylcNwg__FvosCLjWYr1pC42-93OMUkXNrs1IfQ6SGm5MqzNReWNPxNlFt7Ev5AZsq2a13Jcs4dmRpE6OA45oUzgdWOZh2CghY-dIQ_4lfv87EuRnV9MeG4xodTYQoYm4xAEzxUTtfumHZdfNN6IVFqJ4zJCEcpSxcgDsv0Rax5UD0Wscf452R_RqbMH95IcQDRdRXwBDZxITgK65tShY5uHOgflJBJmPrxScc6qOZrSfJJf3L_hSnckrke2fKC4x0Zw6jDXZFb47FiZgTrLC55eFaaYqmkBbXLVGzanHcUAq9BAUXPv92kf3TFNdOGgh_2PMzoQ\\\",\\\"crtCoefficient\\\":\\\"AJ-menwUEK289CBTEOBxI5HJHBJu4LpZKHxgkab9anPqO4F4-_ACJ9rdcMNxyQg-maJxJfc-W4ax5pnA415bxPXRjsLQBe0nThNgvAxGyvj1nNEacvQjpisSSbqiecP7fM3q0wjuTcfYyLpI-WEW4G9LjJMUA81yrkFNrnaHFOwqzH22Tb-4zgnkY3pwIIBZqjBIzXUzBGhsx9BoprMJkjbWz21Ktwg9EBhB-T3w7ZDKoobt7_igdJ3Y0c-3qMndzBAiq77ZeESbWsVM-p36dqzjdQIix-tWbo4HJevCCa0Svk808RSMP4xqVoQkSi0_L-i-M7o0PLQAeDroeSccdcU\\\",\\\"size\\\":4096}\"}";
TEST(JsonKeysetReaderTest, Valid) {
  mitro::JsonKeysetReader reader;
  ASSERT_TRUE(reader.ReadKeyString(KEY));

  scoped_ptr<Value> metadata(reader.ReadMetadata());
  ASSERT_TRUE(metadata != NULL);
  scoped_ptr<Value> key(reader.ReadKey(1));
  ASSERT_TRUE(key != NULL);

  scoped_ptr<keyczar::Keyczar> crypter(keyczar::Crypter::Read(reader));
  ASSERT_TRUE(crypter != NULL);

  const std::string input = "Secret message";
  std::string ciphertext;

  ASSERT_TRUE(crypter->Encrypt(input, &ciphertext));

  std::string decrypted_input;
  ASSERT_TRUE(crypter->Decrypt(ciphertext, &decrypted_input));
  ASSERT_EQ(input, decrypted_input);

  scoped_ptr<keyczar::Crypter> roundtripped(ExportAndReRead(*crypter));
  decrypted_input.clear();
  ASSERT_TRUE(roundtripped->Decrypt(ciphertext, &decrypted_input));
  ASSERT_EQ(input, decrypted_input);
}

static const char PBE_KEY[] = "{\"meta\":\"{\\\"name\\\":\\\"Key\\\",\\\"purpose\\\":\\\"DECRYPT_AND_ENCRYPT\\\",\\\"type\\\":\\\"RSA_PRIV\\\",\\\"versions\\\":[{\\\"exportable\\\":false,\\\"status\\\":\\\"PRIMARY\\\",\\\"versionNumber\\\":1}],\\\"encrypted\\\":true}\",\"1\":\"{\\\"cipher\\\":\\\"AES128\\\",\\\"hmac\\\":\\\"HMAC_SHA1\\\",\\\"iterationCount\\\":50000,\\\"iv\\\":\\\"1NghSaj2SJW_0jr8jNvbAw\\\",\\\"key\\\":\\\"6C8wppVPZ3oJFm04iUYuwU2jUFoGNNxk8a8tw1DqP2g-hMCSZ002mqGhr-Ka2A557oo8xnbCUKN-ZZsfB38oa3MN-Y8Ajqz1H6Lk4RYUix-RlS2NgvHFeEvRnxASxTc8pW1ltojllDkSca5D3NNdue6sqE7xwhO7SwMRji7IHWamEC6qTF2likF4j8oBYs9EoLI5sOf2AuF2yVQwPF3EdHdDL7eR-dm_bt1ULr624yNkD-8HJg7Kdt0G75LtfrIP3cB6K3oQB06eVknhN98YnT6cBpp3XwElMptAW2lyRjSyVTPBlSMQKVscDDPYZroKIOpYUIVxFHd6Ve5D6XXMFT6ouejwzOQN1ZDEbbfwsTKOT3AsH36wtXR_dejJAmSo6VXBXI0_aHbQpdLU8XVtHIgZzV_LZn5gjRWVuFDUMx300LFF6TiSI8vJtK12U4_8AHjs2l7cQq9dY75-TVjb05Iqti_mvjSGb5GYeq8bwlgmTxe3xTBkvSJPqmIB05UhCsMhJVfXSgfSAm_KCAfDdFxxeZUQjEGAO_d2JBEaBZdeiF8Wp7mhienipEcQ_xbim3y0nZZRQx2iLrC-MA_o7wVSwjKaeLFKVTtyDdzBlB1J1fMpIo1ubS8bMRIGzdJs9Rtd-a273LKj8ay3M1V6hA_OzRXqNYqpIAS3ebGgAdZFxzo2jq03jEQjXeifMzyWX3Cjkw99b0aJ4_fPn1-GS2raaCzrF7-erU7ZeXubfW5wuKvY7ucGUiazPz1eMEWR9M1d6ozLb3U_ErFK5T3qn2xH5B8w2l8RnZWBjh50KpaBB4VbR8Aj0XZ4blmlsfebXhQn4J_YeqE3ZHEgsqO_QDnhx_eP7TWj1wf8nu8hrONddODfE0dvpUno_TVVUJuBrl0HiLXrsrhNp_BBKh2c8ZwVjFpArijC8XbQrEbFJt2dXyIZwbbv8Sy5-nLd8ZcBn4M11vMJcRzqTgm6OzgMrEd8xHO68F3a4qqMZ_3kGRT4CP2llwJRMugjod7GcuUmpvvGnIcPCbfUupdvr81-6WhOtpTPsd9ssXmUkzINOV9xDmna_F_yyIVY9zMCTxJwOg9EZFyLVs0kTSbZ79mxzHn_wBwBWmnWf2fyCGaylI7QduE-aEjfpSvJ7AiSiVl0zN5vZl2stOyAaW8VlXT2MoDujImY5Bhj9NMW5Y6e_PwqEWmI9FiOEs64_BMDxWeazCQhsTEJvAEL0NEHGm2JepDkgXCEvt8RWm3H56z1JqIEcDG1uFQbRyI-6yrEiwaqbwVV7IAIRRDFmxvTjw6ZRG__z5zw0GbP-vWSwwYaIc14HY5QA3Bh8gllyR4BDDTfQi20GgHErzw7asUjp6qAaji4p9x42kjaFnucQw39TR8tsCIRCyHdLXtg6eIwtFFjJTVMrwNy5VZFnn4Ubp0em2weChy3ETErjtcqUXqp5LS-K-yXIvK_iOuYLiFmq6fF9vQ1ZF5vTAYE8liyOquqkimyH0bL-rkMQdfyyLU5yK-nMK4dcSksTs196xiLZIyys4AQGeAZp7r31nlP5Dat_qr4Wmlr31bhK6k6-yhknRWlv6FLBUv4SroSsWHgYqIFdj_dCHshacwdA8yZtht_0Uhy4BO6apGpKyePSOEJqc8lj-5sGo1fUrlSuRlSOlP3XQzYolieqhlG0tEyL6-EW5cMZX5FGVVBlwX2Imx22U_iELbJPRvVOe1WUNVuTX2ccoeKVKKU-4i5NFqU6JFCjD3pxLQf0DdQMzezudJphn9Y9oGMcc-1j9nKJ31F85rv2_IEkrkxDIuQV3h4Zk_4-KMEmnsbHW4DEp-qiJ5qgCVwVZTU3jE5zwxAfk-L-8QXeaHvUjy99trPiC2_xHlzm-X9cqJbcrtC0shtt8S2WaWikLOiKswWO7hu7lxT1HhiirXzY2V0A-PwRDdNCghXb4AKI1Wr_kOzWh66fjK3kSPT7Jl75iFbEDm3qElHb24rOTtHK-RpYgBUSTIufJor0LIwNBTeBbXxJuOqAipxQ5IQdsdCejUkEjgpUyxscV55ih2MrXK_J3C9MF2kjRU9qaFDXFEpFBVJFuYkYoc4NXWJOQREGunaVKpFunTNCIseZ36uo6qJfb1XA6vtBOV3nj_9bMgdkUxOKnHJd_BhpxkVqnTM_fB0HXWQnwX3fdbLE67nSfhh5WhFHMF-IF38Xv02zE5zb6s9Nvb4nI3a2-849zs5RemjISQNicsACcs9MJmwF1_2P9R_yGhPJ5zwni7BvLAVFmajb5iPgvs94CQktCprAWERBRoONRRGMQ54oAFoqkkoYD11qORzUptignQZvu4cN7DIis3aPJR0odkiRK-VnzxUtiK7OwPmolDTSxrw7bolFQN3bVKgbTLS0hfAxPep-PtNihN_-u3mOaKk4Hk14gHNIRCWw_DSKlXen2HI4E7Xu84Wyaf1h3ug4sqR5lQkc37y-c19umyVKBFjP9ZOEdWNVLIJi7P1Rwp1lQP2KB4d4tNPrr96o6wyA5aSJc-8Ky_cojB1uGZ2T_pR3mvNLmL06EwY_PVyPV07BIwSF5aUClOvxgAhTvhU-Lzu0dWtG1imGOwSWgIfPqKZBKe6ScxMbRJSIv0loeekJeJ-0N10Meo2Q34YyIUr175WzH0fc9KpSotoITgzthFnURZ_Iu3hYgjwYaQwTvcn674gOBlKvzWU4WXskq_19uNb0YXzB_gVKBLw-mFbtHBqXUCobCqMDYDe6TGT8KI6hJEPO47-hi3wni4LLzp0PHxJj3nEyYjGPkntcmLVVZc54C6zBBIqGOa_hDsXd48PhSm6Bv9NrxhEjgZRXcA0TsACB9d6TzrnOK9EJcbsoEfLd73_JHtai8Iu6_P5kNLubyHkKrWYGujxazkrk_A9BHmj4W_QdF-NA860rI2vsA_TvBVO45h1L5TL4mJ6TKuIDWinCRuh0yzc8l7P_KOIsfoIErdHYuDhn7uGsREAuqhKqU1VnLiYoxFhqr_ToxoepuCn0PjErtetut4uzh7KXVeGiJUE7BswQ2-kL2HNV-TPTpPpwxVUrHj0YeGT-Qb0g9-XYHnmWJYtJrfEzS0N-B-ui1xAoCRpicHiJyPfmCEExhMoCs9qjDrhJDVQvp2egmnuU0fMg8duPdAhL-oeCMcwW5WB8_4h36f9M2GLEwZMWzqIIHcydzfOeAqP2B9BWHkGbyS4hk_TfX6VeO7qtFwKXJCbNF7Gtyp4UfmSX0EEG-oNHycp9bqJvwwK9glGsLMsCSPOxeD3E0oJ1EokrHRKnb2ZsWEDyUEcOTkPf1TOmer3uRu-_ztBfdd0QkM3Totv71pmbsidZQR3SjWKp3AK33Z5WY9ntNbLe2JVEvhuVgYXR9CKl6HM_eazMmT62zTobKHkQbwxfzNGj1ktLICQICrSoEsi23D5jAbMtfy4fdNao9qwrm17FeEZ-PDuysr74Wkdf_k37pQ-d9Ffelq7nXrauytnjlvtVa6Dl6jJEEpQimT2_dqFRoK0SL1EZsLUJfEFW8BwQSMprc4PtQjz6veYnNWUxY7F9fnSXxT4gu93QAYHhJk_wyURKCmEqzWLGRaMB-gCklt81WW2gUHidfV66bBwc5QmshosNaZzD_3uzCJ5rFp32pxHWJ_DUps0FiPnAU0gbQvSsbO9Wz_exTKKe6IJprz5tnMUcwaGzUiRk7I9VEHWgOBDoeDzlMsFl5SdQwAldZA1s9TJuE2IVdRdHTCfLm52Jj9ehmIzASTZ30k80NUDHC3uExjYbF8-SnlbyIAi8lidmumQ2P7ye5pjEfZc-gKEH53kP1PU-7zZKMTl3iNZpKJlMx2lTPSmLh_HtGgM7f3yrE-EvaQIdDccdH6mztc5PXKFG4_PGpwrqWCDSn_vgwEjbryOER-m_PbNhf8K56kSAhCD8g-Oy6cKRCpbPS3CnUEoncA1dXjz0Jf17GMexFFwEh-bIRo3L-EBAVgKRetVAXxU1ZTUzQnB5-WWNoazBwMsT9HLCWFQDwZ-0YWF7f5gRSXuKuR96R7tvIi3FyUe86cObJCTeHFHmDWZ62elY5sOEG7JPxxDW1bajqtrlj6EsHAqlBC__tolvKGv0Jh6avoeZLoPNS_CO6e0gZKIJ3BEDJsOtDXUsnFeXRBPASE37c6mTkfauAUtp9bLRFcATW0AUYs-iDdq-4_JTyN1T-wIWJGrfNXBuPHG0q3zKOnNMSnlkDd16ErYl65S8aLYCwILYu1kAn74jWcyk0vcI1A4es03KOk2ciG8zBbdbr9HlLxpdGyzB7WbxKbYEXMS9ry3vVW8FwAD9t4FLBH91sqNP5CufX8ECT3worxGWQ-79ES2\\\",\\\"salt\\\":\\\"j55zwpZjzXJjmSkXYGn-Cg\\\"}\"}";

static const char PASSWORD[] = "foopassword";

TEST(JsonKeysetReaderTest, PasswordProtected) {
  mitro::JsonKeysetReader json_reader;
  ASSERT_TRUE(json_reader.ReadKeyString(PBE_KEY));
  mitro::PBEKeysetReader pbe_reader(&json_reader, PASSWORD);

  scoped_ptr<Value> metadata(pbe_reader.ReadMetadata());
  ASSERT_TRUE(metadata != NULL);
  scoped_ptr<Value> key(pbe_reader.ReadKey(1));
  ASSERT_TRUE(key != NULL);

  scoped_ptr<keyczar::Keyczar> crypter(keyczar::Crypter::Read(pbe_reader));
  ASSERT_TRUE(crypter != NULL);

  const std::string input = "Secret message";
  std::string ciphertext;

  ASSERT_TRUE(crypter->Encrypt(input, &ciphertext));

  std::string decrypted_input;
  ASSERT_TRUE(crypter->Decrypt(ciphertext, &decrypted_input));
  ASSERT_EQ(input, decrypted_input);

  scoped_ptr<keyczar::Crypter> roundtripped(ExportAndReRead(*crypter));
  decrypted_input.clear();
  ASSERT_TRUE(roundtripped->Decrypt(ciphertext, &decrypted_input));
  ASSERT_EQ(input, decrypted_input);
}

TEST(JsonKeysetReaderTest, BadPasswordProtected) {
  mitro::JsonKeysetReader json_reader;
  ASSERT_TRUE(json_reader.ReadKeyString(PBE_KEY));
  mitro::PBEKeysetReader pbe_reader(&json_reader, "wrong password");
  scoped_ptr<keyczar::Keyczar> crypter(keyczar::Crypter::Read(pbe_reader));
  ASSERT_TRUE(crypter == NULL);
}

TEST(JsonUtilsTest, ParseJsonToDictionary) {
  ASSERT_TRUE(ParseJsonToDictionary("") == NULL);
  ASSERT_TRUE(ParseJsonToDictionary("5") == NULL);
  ASSERT_TRUE(ParseJsonToDictionary("\"str value\"") == NULL);
  ASSERT_TRUE(ParseJsonToDictionary("[]") == NULL);
  ASSERT_TRUE(ParseJsonToDictionary("true") == NULL);
  ASSERT_TRUE(ParseJsonToDictionary("null") == NULL);

  scoped_ptr<DictionaryValue> root(ParseJsonToDictionary("{\"b\": true, \"a\": 42}"));
  int a = 0;
  ASSERT_TRUE(root->GetInteger("a", &a));
  ASSERT_EQ(42, a);
  bool b = false;
  ASSERT_TRUE(root->GetBoolean("b", &b));
  ASSERT_TRUE(b);
}

TEST(JsonUtilsTest, SerializeJsonValue) {
  std::string output("hello world");
  StringValue str_value("json string");
  ASSERT_TRUE(SerializeJsonValue(str_value, &output));
  ASSERT_EQ("\"json string\"", output);

  // TODO: Test serialization failures? No idea how
  ASSERT_DEATH(SerializeJsonValue(str_value, NULL), "");
}
