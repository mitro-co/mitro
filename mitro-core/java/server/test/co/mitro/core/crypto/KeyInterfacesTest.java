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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Arrays;

import org.junit.Test;
import org.keyczar.exceptions.Base64DecodingException;
import org.keyczar.exceptions.KeyNotFoundException;
import org.keyczar.exceptions.KeyczarException;
import org.keyczar.exceptions.ShortSignatureException;
import org.keyczar.util.Base64Coder;

import co.mitro.core.crypto.KeyInterfaces.CryptoError;
import co.mitro.core.crypto.KeyInterfaces.KeyFactory;
import co.mitro.core.crypto.KeyInterfaces.PrivateKeyInterface;
import co.mitro.core.crypto.KeyInterfaces.PublicKeyInterface;

import com.google.common.io.CharStreams;

public class KeyInterfacesTest {
  private static final String makeLong(String in) {
    StringBuilder out = new StringBuilder(in);
    if (out.length() == 0) {
      out.append('a');
    }
    while (out.length() < 1024) {
      out.append(out);
    }
    return out.toString();
  }

  public void testGenericCrypto(KeyFactory keyFactory) throws CryptoError {
    PrivateKeyInterface privateKey = keyFactory.generate();
    final String MESSAGE = "world";
    String signature = privateKey.sign(MESSAGE);

    PublicKeyInterface publicKey = privateKey.exportPublicKey();
    assertTrue(publicKey.verify(MESSAGE, signature));
    assertTrue(privateKey.verify(MESSAGE, signature));

    // modified message or signature fails
    assertFalse(publicKey.verify(MESSAGE + "a", signature));

    char diffLastChar = signature.charAt(signature.length() - 1);
    diffLastChar = (diffLastChar == '0') ? '1' : '0';
    String modifiedSignature = signature.substring(0, signature.length()-1) + diffLastChar;

    // either an exception or fail verification
    assertFalse(publicKey.verify(MESSAGE, modifiedSignature));

    // we should either get an exception or this should fail
    try {
      boolean result = publicKey.verify(MESSAGE, signature + "A");
      assertFalse(result);
    } catch (CryptoError e) {}

    // verification with a different key should fail (exception or false)
    PrivateKeyInterface privateKey2 = privateKey;
    // in a loop because CrappyCrypto has 1/100 probability of generating the same key
    while (privateKey2.toString().equals(privateKey.toString())) {
      privateKey2 = keyFactory.generate();
    }
    PublicKeyInterface publicKey2 = privateKey2.exportPublicKey();
    try {
      boolean result = publicKey2.verify(MESSAGE, signature);
      assertFalse(result);
    } catch (CryptoError e) {}

    // test encryption with long messages (sessions!)
    String longMessage = makeLong(MESSAGE);
    assertEquals(longMessage, privateKey.decrypt(publicKey.encrypt(longMessage)));
    assertEquals(longMessage, privateKey2.decrypt(publicKey2.encrypt(longMessage)));

    // Verify that round tripping the key to/from string works
    PrivateKeyInterface roundTripped = keyFactory.loadPrivateKey(privateKey.toString());
    assertEquals("message", roundTripped.decrypt(privateKey.encrypt("message")));

    // Verify that exporting private keys works
    String exported = privateKey.exportEncrypted("password");
    String exported2 = privateKey.exportEncrypted("password2");
    assertTrue(!exported.equals(exported2));
    assertTrue(!exported.equals(privateKey.toString()));

    roundTripped = keyFactory.loadEncryptedPrivateKey(exported2, "password2");
    assertEquals("message", roundTripped.decrypt(privateKey.encrypt("message")));
  }

  public static String loadResource(String path) throws IOException {
    Reader reader = new InputStreamReader(
        KeyInterfacesTest.class.getResourceAsStream(path), "UTF-8");
    return CharStreams.toString(reader);
  }

  public static PrivateKeyInterface loadTestKey() {
    try {
      KeyczarKeyFactory keyFactory = new KeyczarKeyFactory();
      PrivateKeyInterface privateKey = keyFactory.loadPrivateKey(loadResource("privatekey.json"));
      return privateKey;
    } catch (IOException e) {
      throw new RuntimeException(e);
    } catch (CryptoError e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void testKeyczar() throws CryptoError, IOException {
    KeyczarKeyFactory keyFactory = new KeyczarKeyFactory();
    testGenericCrypto(keyFactory);
    PrecomputingKeyczarKeyFactory pkf = new PrecomputingKeyczarKeyFactory();
    testGenericCrypto(pkf);
    
    // verify Unicode round-tripping to/from JavaScript
    PublicKeyInterface publicKey = keyFactory.loadPublicKey(loadResource("publickey.json"));
    String unicodeMessage = loadResource("utf8.txt");
    String signature = loadResource("utf8_signature.txt");
    assertTrue(publicKey.verify(unicodeMessage, signature));

    PrivateKeyInterface privateKey = loadTestKey();
    String sig2 = privateKey.sign(unicodeMessage);
    assertEquals(signature, sig2);

    String encrypted = loadResource("utf8_encrypted.txt");
    assertEquals(unicodeMessage, privateKey.decrypt(encrypted));

    // verify we can load encrypted keys from javascript and that it works
    String encryptedKey = loadResource("privatekey_encrypted.json");
    privateKey = keyFactory.loadEncryptedPrivateKey(encryptedKey, "hellopass");
    assertEquals("message", privateKey.decrypt(privateKey.encrypt("message")));
    assertTrue(privateKey.verify("message", privateKey.sign("message")));
  }

  @Test
  public void testCrappy() throws CryptoError, IOException {
    CrappyKeyFactory keyFactory = new CrappyKeyFactory();
    testGenericCrypto(keyFactory);

    // Keys and values from Javascript
    PublicKeyInterface publicKey = keyFactory.loadPublicKey("{\"type\":\"PUB\",\"key\":\"8\"}");
    assertTrue(publicKey.verify("this is a longish string wtf", "397913811"));
  }

  @Test
  public void testKeyczarExceptions() throws CryptoError, IOException, Base64DecodingException {
    // Keyczar throws a bunch of runtime exceptions under "error" conditions; check what happens
    // TODO: Possibly some of these should be upstream bugs?

    KeyFactory keyFactory = new KeyczarKeyFactory();
    PrivateKeyInterface privateKey = keyFactory.loadPrivateKey(loadResource("privatekey.json"));
    try {
      privateKey.verify("message", null);
      fail("expected exception");
    } catch (NullPointerException ignored) {}

    try {
      privateKey.verify(null, "signature");
      fail("expected exception");
    } catch (NullPointerException ignored) {}

    // verifying with an empty signature = ArrayIndexOutOfBounds
    try {
      privateKey.verify("message", "");
      fail("expected exception");
    } catch (ArrayIndexOutOfBoundsException ignored) {}

    // Checked exceptions: maybe these should return false: signature is invalid?
    try {
      privateKey.verify("message", "A");
      fail("expected exception");
    } catch (CryptoError ignored) {
      assertTrue(ignored.getCause() instanceof Base64DecodingException);
    }

    try {
      privateKey.verify("message", "AA");
      fail("expected exception");
    } catch (CryptoError ignored) {
      assertTrue(ignored.getCause() instanceof ShortSignatureException);
    }

    // wrong key: KeyNotFoundException (happens when we AddIdentity twice)
    try {
      privateKey.verify("message", "AAAAAAA");
      fail("expected exception");
    } catch (CryptoError ignored) {
      assertTrue(ignored.getCause() instanceof KeyNotFoundException);
    }

    String valid = privateKey.sign("message");
    byte[] decoded = Base64Coder.decodeWebSafe(valid);

    // too short
    try {
      byte[] tooShort = Arrays.copyOf(decoded, decoded.length-1);
      privateKey.verify("message", Base64Coder.encodeWebSafe(tooShort));
      fail("expected exception");
    } catch (CryptoError ignored) {
      assertTrue(ignored.getCause() instanceof KeyczarException);
      assertTrue(ignored.getCause().getCause() instanceof java.security.GeneralSecurityException);
    }

    // too long
    try {
      byte[] tooLong = Arrays.copyOf(decoded, decoded.length+1);
      privateKey.verify("message", Base64Coder.encodeWebSafe(tooLong));
      fail("expected exception");
    } catch (CryptoError ignored) {
      assertTrue(ignored.getCause() instanceof KeyczarException);
      assertTrue(ignored.getCause().getCause() instanceof java.security.GeneralSecurityException);
    }

    // actual true/false values
    decoded[decoded.length-1] += 1;
    assertFalse(privateKey.verify("message", Base64Coder.encodeWebSafe(decoded)));
    decoded[decoded.length-1] -= 1;
    assertTrue(privateKey.verify("message", Base64Coder.encodeWebSafe(decoded)));
    assertFalse(privateKey.verify("messag", Base64Coder.encodeWebSafe(decoded)));
  }
}
