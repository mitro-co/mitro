// Copyright 2009 Sebastien Martini (seb@dbzteam.org)
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
#include <keyczar/openssl/ecdsa.h>

#include <openssl/bn.h>
#include <openssl/ec.h>
#include <openssl/objects.h>
#include <openssl/pem.h>
#include <string.h>

#include <keyczar/base/file_util.h>
#include <keyczar/base/logging.h>
#include <keyczar/base/stl_util-inl.h>

namespace {

typedef scoped_ptr_malloc<EC_GROUP,
                          keyczar::openssl::OSSLDestroyer<
                              EC_GROUP, EC_GROUP_free> > ScopedECGroup;

static std::string CurveToOpenSSLName(keyczar::ECDSAImpl::Curve curve) {
  switch (curve) {
    case keyczar::ECDSAImpl::PRIME192V1:
      return std::string("prime192v1");
    case keyczar::ECDSAImpl::SECP224R1:
      return std::string("secp224r1");
    case keyczar::ECDSAImpl::PRIME256V1:
      return std::string("prime256v1");
    case keyczar::ECDSAImpl::SECP384R1:
      return std::string("secp384r1");
    default:
      NOTREACHED();
  }
  return std::string("");
}

static keyczar::ECDSAImpl::Curve OpenSSLNidToCurve(int nid) {
  switch (nid) {
    case NID_X9_62_prime192v1:
      return keyczar::ECDSAImpl::PRIME192V1;
    case NID_secp224r1:
      return keyczar::ECDSAImpl::SECP224R1;
    case NID_X9_62_prime256v1:
      return keyczar::ECDSAImpl::PRIME256V1;
    case NID_secp384r1:
      return keyczar::ECDSAImpl::SECP384R1;
    default:
      NOTREACHED();
  }
  return keyczar::ECDSAImpl::UNDEF;
}

}  // namespace

namespace keyczar {

namespace openssl {

// static
ECDSAOpenSSL* ECDSAOpenSSL::Create(const ECDSAIntermediateKey& key,
                                   bool private_key) {
  ScopedECKey ecdsa_key(EC_KEY_new());
  if (ecdsa_key.get() == NULL) {
    PrintOSSLErrors();
    return NULL;
  }

  // named_curve
  std::string named_curve = CurveToOpenSSLName(key.curve);
  if (named_curve.empty())
    return NULL;

  int nid = OBJ_sn2nid(named_curve.c_str());
  if (nid == 0) {
    PrintOSSLErrors();
    return NULL;
  }

  ScopedECGroup group(EC_GROUP_new_by_curve_name(nid));
  if (group.get() == NULL) {
    PrintOSSLErrors();
    return NULL;
  }

  // Make sure this group has this flag set. This flag is needed for exporting
  // this key with the ASN1 OID information which in turn is needed when the
  // key is loaded back to get its curve name.
  EC_GROUP_set_asn1_flag(group.get(), OPENSSL_EC_NAMED_CURVE);

  // group is duplicated by this function
  if (EC_KEY_set_group(ecdsa_key.get(), group.get()) == 0) {
    PrintOSSLErrors();
    return NULL;
  }
  group.reset();

  // public_key
  EC_KEY* key_tmp = ecdsa_key.get();
  const unsigned char* public_key_bytes = reinterpret_cast<unsigned char*>(
      const_cast<char*>(key.public_key.data()));
  if (!o2i_ECPublicKey(&key_tmp, &public_key_bytes, key.public_key.length())) {
    PrintOSSLErrors();
    return NULL;
  }

  if (!private_key)
    return new ECDSAOpenSSL(ecdsa_key.release(), private_key);

  // private_key
  ScopedSecretBIGNUM private_key_bn;
  private_key_bn.reset(BN_bin2bn(reinterpret_cast<unsigned char*>(
                                     const_cast<char*>(key.private_key.data())),
                                 key.private_key.length(),
                                 private_key_bn.get()));
  if (private_key_bn.get() == NULL) {
    PrintOSSLErrors();
    return NULL;
  }

  // private_key_bn is duplicated by this function
  if (!EC_KEY_set_private_key(ecdsa_key.get(), private_key_bn.get())) {
    PrintOSSLErrors();
    return NULL;
  }
  private_key_bn.reset();

  if (!EC_KEY_check_key(ecdsa_key.get()) ||
      EC_GROUP_check(EC_KEY_get0_group(ecdsa_key.get()), NULL) != 1) {
    PrintOSSLErrors();
    return NULL;
  }

  return new ECDSAOpenSSL(ecdsa_key.release(), private_key);
}

// static
ECDSAOpenSSL* ECDSAOpenSSL::GenerateKey(ECDSAImpl::Curve curve) {
  ScopedECKey ecdsa_key(EC_KEY_new());
  if (ecdsa_key.get() == NULL) {
    PrintOSSLErrors();
    return NULL;
  }

  // named_curve
  std::string named_curve = CurveToOpenSSLName(curve);
  if (named_curve.empty())
    return NULL;

  int nid = OBJ_sn2nid(named_curve.c_str());
  if (nid == 0) {
    PrintOSSLErrors();
    return NULL;
  }

  ScopedECGroup group(EC_GROUP_new_by_curve_name(nid));
  if (group.get() == NULL) {
    PrintOSSLErrors();
    return NULL;
  }

  // Make sure this group has this flag set. This flag is needed for exporting
  // this key with the ASN1 OID information which in turn is needed when the
  // key is loaded back to get its curve name.
  EC_GROUP_set_asn1_flag(group.get(), OPENSSL_EC_NAMED_CURVE);

  // group is duplicated by this function
  if (EC_KEY_set_group(ecdsa_key.get(), group.get()) == 0) {
    PrintOSSLErrors();
    return NULL;
  }
  group.reset();

  // Generate the private key
  if (EC_KEY_generate_key(ecdsa_key.get()) != 1) {
    PrintOSSLErrors();
    return NULL;
  }

  // Last checks
  if (EC_KEY_check_key(ecdsa_key.get()) != 1 ||
      EC_GROUP_check(EC_KEY_get0_group(ecdsa_key.get()), NULL) != 1) {
    PrintOSSLErrors();
    return NULL;
  }

  return new ECDSAOpenSSL(ecdsa_key.release(),
                          true /* private_key */);
}

// static
ECDSAOpenSSL* ECDSAOpenSSL::CreateFromPEMPrivateKey(
    const std::string& filename, const std::string* passphrase) {
  // Load the disk based private key.
  ScopedEVPPKey evp_pkey(ReadPEMPrivateKeyFromFile(filename, passphrase));
  if (evp_pkey.get() == NULL) {
    PrintOSSLErrors();
    return NULL;
  }

  if (evp_pkey->pkey.ec == NULL) {
    LOG(ERROR) << "Invalid EC private key";
    return NULL;
  }

  // Duplicate the EC key component.
  ScopedECKey ecdsa_key(EVP_PKEY_get1_EC_KEY(evp_pkey.get()));
  if (ecdsa_key.get() == NULL) {
    PrintOSSLErrors();
    return NULL;
  }

  if (EC_KEY_check_key(ecdsa_key.get()) != 1 ||
      EC_GROUP_check(EC_KEY_get0_group(ecdsa_key.get()), NULL) != 1 ||
      EC_GROUP_get_asn1_flag(EC_KEY_get0_group(
                                 ecdsa_key.get())) != OPENSSL_EC_NAMED_CURVE) {
    PrintOSSLErrors();
    return NULL;
  }

  return new ECDSAOpenSSL(ecdsa_key.release(),
                          true /* private_key */);
}

bool ECDSAOpenSSL::ExportPrivateKey(const std::string& filename,
                                    const std::string* passphrase) const {
  if (key_.get() == NULL || !private_key_)
    return false;

  ScopedEVPPKey evp_key(EVP_PKEY_new());
  if (evp_key.get() == NULL)
    return false;

  if (!EVP_PKEY_set1_EC_KEY(evp_key.get(), key_.get()))
    return false;

  return WritePEMPrivateKeyToFile(evp_key.get(), filename, passphrase);
}

bool ECDSAOpenSSL::GetAttributes(ECDSAIntermediateKey* key) {
  if (key == NULL || key_.get() == NULL)
    return false;

  if (!private_key_)
    return false;

  if (!GetPublicAttributes(key))
    return false;

  // private_key
  if (!EC_KEY_get0_private_key(key_.get()))
    return false;
  int private_key_len = BN_num_bytes(EC_KEY_get0_private_key(key_.get()));

  unsigned char private_key[private_key_len];
  if (BN_bn2bin(EC_KEY_get0_private_key(key_.get()),
                private_key) != private_key_len) {
    PrintOSSLErrors();
    return false;
  }
  key->private_key.assign(reinterpret_cast<char*>(private_key),
                          private_key_len);
  memset(private_key, 0, private_key_len);

  return true;
}

bool ECDSAOpenSSL::GetPublicAttributes(ECDSAIntermediateKey* key) {
  if (key == NULL || key_.get() == NULL)
    return false;

  // named_curve
  int nid = EC_GROUP_get_curve_name(EC_KEY_get0_group(key_.get()));
  if (nid == 0) {
    PrintOSSLErrors();
    return false;
  }
  key->curve = OpenSSLNidToCurve(nid);

  // public_key
  unsigned char* public_key_buffer = NULL;
  int public_key_buffer_len = 0;

  public_key_buffer_len = i2o_ECPublicKey(key_.get(), &public_key_buffer);
  if (public_key_buffer_len == 0) {
    PrintOSSLErrors();
    if (public_key_buffer != NULL)
      free(public_key_buffer);
    return false;
  }
  key->public_key.assign(reinterpret_cast<char*>(public_key_buffer),
                         public_key_buffer_len);
  free(public_key_buffer);

  return true;
}

bool ECDSAOpenSSL::Sign(const std::string& message_digest,
                        std::string* signature) const {
  if (key_.get() == NULL || signature == NULL || !private_key_)
    return false;

  uint32 ecdsa_size = ECDSA_size(key_.get());
  base::STLStringResizeUninitialized(signature, ecdsa_size);

  uint32 signature_length = 0;
  if (ECDSA_sign(0,
                 reinterpret_cast<unsigned char*>(
                     const_cast<char*>(message_digest.data())),
                 message_digest.length(),
                 reinterpret_cast<unsigned char*>(
                     base::string_as_array(signature)),
                 &signature_length,
                 key_.get()) != 1) {
    PrintOSSLErrors();
    return false;
  }

  CHECK_LE(signature_length, ecdsa_size);
  signature->resize(signature_length);
  return true;
}

bool ECDSAOpenSSL::Verify(const std::string& message_digest,
                          const std::string& signature) const {
  if (key_.get() == NULL)
    return false;

  int ret_val = 0;
  ret_val = ECDSA_verify(0,
                         reinterpret_cast<unsigned char*>(
                             const_cast<char*>(message_digest.data())),
                         message_digest.length(),
                         reinterpret_cast<unsigned char*>(
                             const_cast<char*>(signature.data())),
                         signature.length(),
                         key_.get());
  if (ret_val == 1)
    return true;
  if (ret_val == -1)
    PrintOSSLErrors();
  return false;
}

int ECDSAOpenSSL::Size() const {
  if (key_.get() == NULL)
    return 0;

  // In this case it is important to retrun the exact size in bits, because
  // this value will be used by the signing method to truncate the message
  // digest to the key size.
  return BN_num_bits(EC_KEY_get0_private_key(key_.get()));
}

bool ECDSAOpenSSL::Equals(const ECDSAOpenSSL& rhs) const {
  if (key_.get() == NULL || private_key() != rhs.private_key())
    return false;

  const EC_KEY* key_rhs = rhs.key();

  // Compares public components.
  if (EC_GROUP_cmp(EC_KEY_get0_group(key_.get()),
                   EC_KEY_get0_group(key_rhs), NULL) != 0)
    return false;

  unsigned char* pk_buf_lhs = NULL;
  unsigned char* pk_buf_rhs = NULL;
  int pk_buf_len_lhs = 0, pk_buf_len_rhs = 0;
  bool pub_key_eq = true;

  pk_buf_len_lhs = i2o_ECPublicKey(key_.get(), &pk_buf_lhs);
  pk_buf_len_rhs = i2o_ECPublicKey(const_cast<EC_KEY*>(key_rhs), &pk_buf_rhs);
  if (!pk_buf_lhs || !pk_buf_rhs || pk_buf_len_lhs != pk_buf_len_rhs ||
      pk_buf_len_lhs == 0 ||
      memcmp(pk_buf_lhs, pk_buf_rhs, pk_buf_len_lhs) != 0) {
    pub_key_eq = false;
  }
  if (pk_buf_lhs != NULL)
    free(pk_buf_lhs);
  if (pk_buf_rhs != NULL)
    free(pk_buf_rhs);

  if (!pub_key_eq)
    return false;

  if (!private_key())
    return true;

  // Compares private components.
  if (BN_cmp(EC_KEY_get0_private_key(key_.get()),
             EC_KEY_get0_private_key(key_rhs)) != 0)
    return false;

  return true;
}

}  // namespace openssl

}  // namespace keyczar
