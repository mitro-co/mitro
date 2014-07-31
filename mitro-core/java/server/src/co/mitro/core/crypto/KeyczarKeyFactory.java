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
package co.mitro.core.crypto;

import java.io.File;
import java.io.IOException;

import org.keyczar.Crypter;
import org.keyczar.DefaultKeyType;
import org.keyczar.Encrypter;
import org.keyczar.GenericKeyczar;
import org.keyczar.Signer;
import org.keyczar.Verifier;
import org.keyczar.enums.KeyPurpose;
import org.keyczar.exceptions.KeyczarException;
import org.keyczar.interfaces.KeyczarReader;

import co.mitro.core.crypto.KeyInterfaces.CryptoError;
import co.mitro.core.crypto.KeyInterfaces.KeyFactory;
import co.mitro.core.crypto.KeyInterfaces.PrivateKeyInterface;
import co.mitro.core.crypto.KeyInterfaces.PublicKeyInterface;
import co.mitro.keyczar.JsonWriter;
import co.mitro.keyczar.KeyczarJsonReader;
import co.mitro.keyczar.KeyczarPBEReader;
import co.mitro.keyczar.Util;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.gson.Gson;

/**
 * Implements KeyFactory using Keyczar. Each key is actually a pair for encryption and signatures.
 */
public class KeyczarKeyFactory implements KeyFactory {
  // Gson object is thread-safe, assuming custom serializers are thread safe
  private static final Gson gson = new Gson();

  public static abstract class KeyczarBaseKey {
    protected final GenericKeyczar encryptionKey;
    protected final GenericKeyczar signingKey;

    public KeyczarBaseKey(KeyczarReader encryptionReader, KeyczarReader signingReader)
        throws KeyczarException {
      this.encryptionKey = new GenericKeyczar(encryptionReader);
      this.signingKey = new GenericKeyczar(signingReader);
    }

    @Override
    public String toString() {
      SerializedKey key = new SerializedKey();
      key.encryption = JsonWriter.toString(encryptionKey);
      key.signing = JsonWriter.toString(signingKey);
      return gson.toJson(key);
    }
  }

  public static final class KeyczarPublicKey extends KeyczarBaseKey implements PublicKeyInterface {
    private final Encrypter encrypter;
    private final Verifier verifier;

    public KeyczarPublicKey(KeyczarReader encryptionReader, KeyczarReader signingReader)
        throws KeyczarException {
      super(encryptionReader, signingReader);
      encrypter = new Encrypter(encryptionReader);
      verifier = new Verifier(signingReader);
    }

    @Override
    public boolean verify(String message, String signature) throws CryptoError {
      try {
        return verifier.verify(message, signature);
      } catch (KeyczarException e) {
        throw new CryptoError(e);
      } catch (ArrayIndexOutOfBoundsException e) {
        throw new CryptoError(e);
      }
    }

    @Override
    public String encrypt(String plaintext) throws CryptoError {
      try {
        return Util.encryptWithSession(encrypter, plaintext);
      } catch (KeyczarException e) {
        throw new CryptoError(e);
      }
    }
  }

  /** Size of keys for RSA. Keyczar defaults to 4096 which is too slow in JavaScript. */
  public static final int RSA_KEY_SIZE = 2048;

  private static final class SerializedKey {
    public String encryption;
    public String signing;
  }

  private static final class SerializedReader {
    public final KeyczarJsonReader encryptionReader;
    public final KeyczarJsonReader signingReader;

    public SerializedReader(SerializedKey key) {
      encryptionReader = new KeyczarJsonReader(key.encryption);
      signingReader = new KeyczarJsonReader(key.signing);
    }

    public static SerializedReader loadJson(String serializedKey) {
      SerializedKey key = gson.fromJson(serializedKey, SerializedKey.class);
      return new SerializedReader(key);
    }
  }

  @Override
  public PublicKeyInterface loadPublicKey(String serializedKey) throws CryptoError {
    try {
      SerializedReader key = SerializedReader.loadJson(serializedKey);
      return new KeyczarPublicKey(key.encryptionReader, key.signingReader);
    } catch (KeyczarException e) {
      throw new CryptoError(e);
    }
  }

  @Override
  public PrivateKeyInterface loadPrivateKey(String serializedKey) throws CryptoError {
    try {
      SerializedReader key = SerializedReader.loadJson(serializedKey);
      return new KeyczarPrivateKey(key.encryptionReader, key.signingReader);
    } catch (KeyczarException e) {
      throw new CryptoError(e);
    }
  }

  @Override
  public PrivateKeyInterface loadEncryptedPrivateKey(String serializedKey, String password)
      throws CryptoError {
    try {
      SerializedReader key = SerializedReader.loadJson(serializedKey);
      KeyczarPBEReader encryptedEncryption = new KeyczarPBEReader(key.encryptionReader, password);
      KeyczarPBEReader encryptedSigning = new KeyczarPBEReader(key.signingReader, password);
      return new KeyczarPrivateKey(encryptedEncryption, encryptedSigning);
    } catch (KeyczarException e) {
      throw new CryptoError(e);
    }
  }

  @Override
  public PrivateKeyInterface generate() throws CryptoError {
    try {
      KeyczarReader encryptionKey = Util.generateKeyczarReader(
          DefaultKeyType.RSA_PRIV, KeyPurpose.DECRYPT_AND_ENCRYPT, RSA_KEY_SIZE);
      KeyczarReader signingKey = Util.generateKeyczarReader(
          DefaultKeyType.RSA_PRIV, KeyPurpose.SIGN_AND_VERIFY, RSA_KEY_SIZE);
      return new KeyczarPrivateKey(encryptionKey, signingKey);
    } catch (KeyczarException e) {
      throw new KeyInterfaces.CryptoError(e);
    }
  }

  private static final class KeyczarPrivateKey
      extends KeyczarBaseKey implements PrivateKeyInterface {
    private final Crypter crypter;
    private final Signer signer;

    public KeyczarPrivateKey(KeyczarReader encryptionReader, KeyczarReader signingReader)
        throws KeyczarException {
      super(encryptionReader, signingReader);

      crypter = new Crypter(encryptionReader);
      signer = new Signer(signingReader);
    }

    @Override
    public boolean verify(String message, String signature) throws CryptoError {
      try {
        return signer.verify(message, signature);
      } catch (KeyczarException e) {
        throw new CryptoError(e);
      }
    }

    @Override
    public String sign(String message) throws CryptoError {
      try {
        return signer.sign(message);
      } catch (KeyczarException e) {
        throw new CryptoError(e);
      }
    }

    @Override
    public PublicKeyInterface exportPublicKey() throws CryptoError {
      try {
        KeyczarReader publicEncrypt = Util.exportPublicKeys(encryptionKey);
        KeyczarReader publicSigning = Util.exportPublicKeys(signingKey);
        return new KeyczarPublicKey(publicEncrypt, publicSigning);
      } catch (KeyczarException e) {
        throw new CryptoError(e);
      }
    }

    @Override
    public String exportEncrypted(String password) throws CryptoError {
      SerializedKey key = new SerializedKey();
      StringBuilder out = new StringBuilder();
      JsonWriter.writeEncrypted(encryptionKey, password, out);
      key.encryption = out.toString();

      out = new StringBuilder();
      JsonWriter.writeEncrypted(signingKey, password, out);

      key.signing = out.toString();
      return gson.toJson(key);
    }

    @Override
    public String encrypt(String plaintext) throws CryptoError {
      try {
        return Util.encryptWithSession(crypter, plaintext);
      } catch (KeyczarException e) {
        throw new CryptoError(e);
      }
    }

    @Override
    public String decrypt(String message) throws CryptoError {
      try {
        return Util.decryptWithSession(crypter, message);
      } catch (KeyczarException e) {
        throw new CryptoError(e);
      }
    }
  }

  /** Test program to load a key from disk and decrypt a file. */
  public static void main(String[] arguments) throws IOException, CryptoError {
    String privateKeyPath = arguments[0];
    String password = arguments[1];
    String encryptedPath = arguments[2];

    String privateKeyData = Files.toString(new File(privateKeyPath), Charsets.UTF_8);
    KeyczarKeyFactory keyFactory = new KeyczarKeyFactory();
    PrivateKeyInterface key = keyFactory.loadEncryptedPrivateKey(privateKeyData, password);

    String encryptedData = Files.toString(new File(encryptedPath), Charsets.UTF_8);
    System.out.println(key.decrypt(encryptedData));
  }
}
