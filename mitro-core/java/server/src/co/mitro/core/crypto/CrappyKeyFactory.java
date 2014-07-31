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

import java.util.Random;

import co.mitro.core.crypto.KeyInterfaces.CryptoError;
import co.mitro.core.crypto.KeyInterfaces.PrivateKeyInterface;
import co.mitro.core.crypto.KeyInterfaces.PublicKeyInterface;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

public class CrappyKeyFactory implements KeyInterfaces.KeyFactory {
  private static final int MAX_VALUE = 100;
  private static final Gson gson = new Gson();

  private static int hash(String s) {
    int output = 0;
    for (int i = 0; i < s.length(); i++) {
      output = ((output << 5) - output) + s.charAt(i);
    }
    return output;
  }

  private static final class CrappyMessage {
    String message;
    int crypto;
  }

  private static final class CrappySerialized {
    String type;
    int key;
    String password;
  }

  private static enum KeyType {
    PUBLIC("PUB"),
    PRIVATE("PRV"),
    ENCRYPTED("PAS");

    public final String value;

    private KeyType(String value) {
      this.value = value;
    }
  }

  public static class CrappyPublicKey implements KeyInterfaces.PublicKeyInterface {
    protected final int publicKeyValue;

    public CrappyPublicKey(int publicKeyValue) {
      this.publicKeyValue = publicKeyValue;
      assert this.publicKeyValue < MAX_VALUE;
    }

    protected String toJson(KeyType type, String password) {
      CrappySerialized key = new CrappySerialized();
      key.type = type.value;
      key.key = publicKeyValue;
      key.password = password;
      return gson.toJson(key);
    }

    @Override
    public String toString() {
      return toJson(KeyType.PUBLIC, null);
    }

    @Override
    public boolean verify(String message, String signature) throws CryptoError {
      int h = hash(message);
      h += publicKeyValue;
      JsonParser parser = new JsonParser();
      try {
        JsonElement e = parser.parse(signature);
        return e.getAsInt() == h;
      } catch (NumberFormatException e) {
        return false;
      }
    }

    @Override
    public String encrypt(String plaintext) throws CryptoError {
      CrappyMessage message = new CrappyMessage();
      message.message = plaintext;
      message.crypto = MAX_VALUE - publicKeyValue;
      return gson.toJson(message);
    }
  }

  public static class CrappyPrivateKey extends CrappyPublicKey implements PrivateKeyInterface {
    public CrappyPrivateKey(int publicKeyValue) {
      super(publicKeyValue);
    }

    @Override
    public String toString() {
      return toJson(KeyType.PRIVATE, null);
    }

    @Override
    public String sign(String message) {
      int h = hash(message);
      h += publicKeyValue;
      return gson.toJson(h);
    }

    @Override
    public KeyInterfaces.PublicKeyInterface exportPublicKey() {
      return new CrappyPublicKey(publicKeyValue);
    }

    @Override
    public String decrypt(String message) throws CryptoError {
      CrappyMessage struct = gson.fromJson(message, CrappyMessage.class);

      if (struct.crypto != MAX_VALUE - publicKeyValue) {
        throw new CryptoError("Wrong key? Message: " + struct.crypto + " key: " + publicKeyValue);
      }
      return struct.message;
    }

    @Override
    public String exportEncrypted(String password) throws CryptoError {
      return toJson(KeyType.ENCRYPTED, password);
    }
  }

  @Override
  public PublicKeyInterface loadPublicKey(String serializedKey) throws CryptoError {
    CrappySerialized key = gson.fromJson(serializedKey, CrappySerialized.class);
    if (!key.type.equals(KeyType.PUBLIC.value)) {
      throw new CryptoError("Bad key type: " + key.type);
    }
    return new CrappyPublicKey(key.key);
  }

  @Override
  public PrivateKeyInterface loadPrivateKey(String serializedKey) throws CryptoError {
    CrappySerialized key = gson.fromJson(serializedKey, CrappySerialized.class);
    if (!key.type.equals(KeyType.PRIVATE.value)) {
      throw new CryptoError("Bad key type: " + key.type);
    }
    return new CrappyPrivateKey(key.key);
  }

  @Override
  public PrivateKeyInterface loadEncryptedPrivateKey(String serializedKey, String password)
      throws CryptoError {
    CrappySerialized key = gson.fromJson(serializedKey, CrappySerialized.class);
    if (!key.type.equals(KeyType.ENCRYPTED.value)) {
      throw new CryptoError("Bad key type: " + key.type);
    }
    if (!key.password.equals(password)) {
      throw new CryptoError("Invalid password");
    }
    return new CrappyPrivateKey(key.key);
  }

  @Override
  public PrivateKeyInterface generate() throws CryptoError {
    int publicKeyValue = new Random().nextInt(MAX_VALUE - 1) + 1;
    return new CrappyPrivateKey(publicKeyValue);
  }
}
