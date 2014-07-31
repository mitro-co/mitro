/*******************************************************************************
 * Copyright (c) 2013 Lectorius, Inc.
 * Authors:
 * Vijay Pandurangan (vijayp@mitro.co)
 * Evan Jones (ej@mitro.co)
 * Adam Hilss (ahilss@mitro.co)
 *
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *     
 *     You can contact the authors at inbound@mitro.co.
 *******************************************************************************/
package co.mitro.keyczar;

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import org.keyczar.enums.CipherMode;
import org.keyczar.exceptions.Base64DecodingException;
import org.keyczar.exceptions.KeyczarException;
import org.keyczar.interfaces.KeyczarReader;
import org.keyczar.util.Base64Coder;

import com.google.gson.Gson;

public class KeyczarPBEReader implements KeyczarReader {
  private final KeyczarReader reader;
  private final String passphrase;

  // PBKDF2 NIST approved standard key derivation function
  private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA1";

  // PBKDF2 RFC 2898 recommends at least 8 bytes (64 bits) of salt
  // http://tools.ietf.org/html/rfc2898#section-4
  // but NIST recommends at least 16 bytes (128 bits; see Section 5.1)
  // http://csrc.nist.gov/publications/nistpubs/800-132/nist-sp800-132.pdf
  static final int SALT_BYTES = 16;

  // NIST suggests count to be 1000 as a minimum, but that seems poor.
  // 4 GPUs can do 3M attempts/second with 1000 iterations. See:
  // http://blog.agilebits.com/2013/04/16/1password-hashcat-strong-master-passwords/
  // 10000 iterations; 7 mixed case random letters = 9 days to crack
  // 10000 iterations; 9 mixed case random letters = 193 years days to crack
  // C++ Keyczar uses 4096 iterations by default (crypto_factory.cc)
  @SuppressWarnings("unused")
  private static final int MIN_ITERATION_COUNT = 10000;

  // We use 50000 iterations by default to increase brute force difficulty
  // 7 mixed case random letters = 41 days to crack
  // 9 mixed case random letters = 867 years to crack
  static final int DEFAULT_ITERATION_COUNT = 50000;

  // PBKDF2 key derivation function
  public static byte[] pbkdf2(PBEKeySpec keySpec) {
    try {
      SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
      SecretKey key = factory.generateSecret(keySpec);
      return key.getEncoded();
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("Unexpected: unsupported key derivation function?", e);
    } catch (InvalidKeySpecException e) {
      throw new IllegalArgumentException("Invalid keySpec", e);
    }
  }

  private static final String AES_ALGORITHM = "AES";

  /**
   * Reads encrypted key files from the given reader and decrypts them
   * with the given crypter.
   *
   * @param reader The reader to read files from.
   * @param crypter The crypter to decrypt keys with.
   */
  public KeyczarPBEReader(KeyczarReader reader, String passphrase) {
    this.reader = reader;
    this.passphrase = passphrase;
  }

  String decryptKey(String encryptedKeyData) throws KeyczarException {
    // Parse the metadata
    PBEKeyczarKey pbeMetadata = parsePBEMetadata(encryptedKeyData);

    // generate the key
    PBEKeySpec spec = keySpecFromJson(pbeMetadata, passphrase);
    byte[] keyBytes = pbkdf2(spec);
      SecretKeySpec key = new SecretKeySpec(keyBytes, AES_ALGORITHM);

      // Decrypt the data
      IvParameterSpec iv = new IvParameterSpec(Base64Coder.decodeWebSafe(pbeMetadata.iv));
      try {
      Cipher decryptingCipher = Cipher.getInstance(CipherMode.CBC.getMode());
      decryptingCipher.init(Cipher.DECRYPT_MODE, key, iv);

      byte[] encryptedKey = Base64Coder.decodeWebSafe(pbeMetadata.key);
      byte[] decrypted = decryptingCipher.doFinal(encryptedKey);
      return new String(decrypted, "UTF-8");
    } catch (java.security.GeneralSecurityException e) {
      throw new KeyczarException("Error decrypting PBE key", e);
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException("Should never occur");
    }
  }

  @Override
  public String getKey() throws KeyczarException {
    return decryptKey(reader.getKey());
  }

  @Override
  public String getKey(int version) throws KeyczarException {
    return decryptKey((reader.getKey(version)));
  }

  @Override
  public String getMetadata() throws KeyczarException {
    String originalMetadata = reader.getMetadata();
    // GenericKeyczar throws an exception if it sees encrypted: true
    // hack around this
    return originalMetadata.replaceFirst("\"encrypted\":\\s*true", "\"encrypted\":false");
  }

  static final class PBEKeyczarKey {
    public String cipher;
    public String hmac;
    public int iterationCount;
    public String iv;
    public String key;
    public String salt;
  }

  static String encryptKey(String key, String password) throws KeyczarException {
    PBEKeyczarKey pbeKey = new PBEKeyczarKey();
    pbeKey.cipher = PBE_CIPHER;
    pbeKey.hmac = PBE_HMAC;
    pbeKey.iterationCount = DEFAULT_ITERATION_COUNT;
    // TODO: Figure out how to remove this base64 encoding round trip?
    byte[] salt = new byte[SALT_BYTES];
    org.keyczar.util.Util.rand(salt);
    pbeKey.salt = Base64Coder.encodeWebSafe(salt);

    PBEKeySpec spec = keySpecFromJson(pbeKey, password);
    byte[] derivedKeyBytes = pbkdf2(spec);
    SecretKeySpec derivedKey = new SecretKeySpec(derivedKeyBytes, AES_ALGORITHM);

    // Decrypt the data
    byte[] ivBytes = new byte[PBE_AES_KEY_BYTES];
    org.keyczar.util.Util.rand(ivBytes);
    pbeKey.iv = Base64Coder.encodeWebSafe(ivBytes);
    IvParameterSpec iv = new IvParameterSpec(ivBytes);
    try {
      Cipher encryptingCipher = Cipher.getInstance(CipherMode.CBC.getMode());
      encryptingCipher.init(Cipher.ENCRYPT_MODE, derivedKey, iv);

      byte[] keyBytes = key.getBytes("UTF-8");
      byte[] encrypted = encryptingCipher.doFinal(keyBytes);


      pbeKey.key = Base64Coder.encodeWebSafe(encrypted);
    } catch (java.security.GeneralSecurityException e) {
      throw new KeyczarException("Error encrypting key", e);
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException("Should never occur");
    }

    Gson gson = new Gson();
    return gson.toJson(pbeKey);
  }

  static PBEKeySpec keySpecFromJson(PBEKeyczarKey pbeKey, String password) {
    try {
      byte[] saltBytes = Base64Coder.decodeWebSafe(pbeKey.salt);
      char[] passwordChars = password.toCharArray();
      // keyLength is in bits
      return new PBEKeySpec(passwordChars, saltBytes, pbeKey.iterationCount, PBE_AES_KEY_BYTES*8);
    } catch (Base64DecodingException e) {
      throw new RuntimeException(e);
    }
  }

  private static final String PBE_CIPHER = "AES128";
  // TODO: Look this up from the cipher type
  static final int PBE_AES_KEY_BYTES = 16;
  private static final String PBE_HMAC = "HMAC_SHA1";

  static PBEKeyczarKey parsePBEMetadata(String json) {
    Gson gson = new Gson();
    PBEKeyczarKey pbeKey = gson.fromJson(json, PBEKeyczarKey.class);

    assert pbeKey.cipher.equals(PBE_CIPHER);
    assert pbeKey.hmac.equals(PBE_HMAC);
    assert pbeKey.iterationCount > 0;
    return pbeKey;
  }
}
