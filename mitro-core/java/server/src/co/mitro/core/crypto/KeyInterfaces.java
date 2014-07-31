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

public class KeyInterfaces {
  public static class CryptoError extends Exception {
    private static final long serialVersionUID = 1L;

    public CryptoError() {
      super();
    }

    public CryptoError(Throwable cause) {
      super(cause);
    }

    public CryptoError(String string) {
      super(string);
    }
  };

  /**
   * Loads, generates, and decrypts keys.
   */
  public static interface KeyFactory {
    /**
     * Returns the public key serialized in serializedKey.
     */
    PublicKeyInterface loadPublicKey(String serializedKey) throws CryptoError;

    /**
     * Returns the private key stored in seralizedKey.
     */
    PrivateKeyInterface loadPrivateKey(String serializedKey) throws CryptoError;

    /**
     * Decrypts serializedKey using password, returning a new PrivateKey object.
     */
    PrivateKeyInterface loadEncryptedPrivateKey(String serializedKey, String password)
        throws CryptoError;

    /**
     * Returns a new PrivateKey.
     */
    PrivateKeyInterface generate() throws CryptoError;
  }

  public static interface PublicKeyInterface {
    /** Returns this public key as as string. */
    public String toString();

    /** Returns true if the signature is valid for this message. */
    public boolean verify(String message, String signature) throws CryptoError;

    /** Returns plaintext encrypted with this key. */
    public String encrypt(String plaintext) throws CryptoError;
  }

  public static interface PrivateKeyInterface extends PublicKeyInterface {
    /** Returns a signature for this message. */
    public String sign(String message) throws CryptoError;

    /** Returns this private key as a string. */
    public String toString();

    /** Returns this private key as an encrypted string. */
    public String exportEncrypted(String password) throws CryptoError;

    /** Returns the public key for this private key. */
    public PublicKeyInterface exportPublicKey() throws CryptoError;

    /** Returns the message decrypted with this key. */
    public String decrypt(String message) throws CryptoError;
  }
}
