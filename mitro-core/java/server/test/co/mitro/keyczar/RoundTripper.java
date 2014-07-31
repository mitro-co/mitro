/*******************************************************************************
 * Copyright (c) 2013, 2014 Lectorius, Inc.
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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import org.keyczar.Crypter;
import org.keyczar.DefaultKeyType;
import org.keyczar.Encrypter;
import org.keyczar.GenericKeyczar;
import org.keyczar.KeyMetadata;
import org.keyczar.Signer;
import org.keyczar.Verifier;
import org.keyczar.enums.KeyPurpose;
import org.keyczar.exceptions.KeyczarException;
import org.keyczar.interfaces.KeyczarReader;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

/* Script that tries all message lengths, and generates keys both in JS and Java.

#!/bin/sh
set -e
JAVA="java -ea -cp java/server/lib/guava-14.0.1.jar:java/server/lib/keyczar-0.71f-040513.jar:java/server/lib/gson-2.2.4.jar:java/server/lib/log4j-1.2.17.jar:bin"

mkdir -p testwtf
MESSAGE=''
for i in `seq 471`; do
    rm -f testwtf/*
    echo "Generating keys with Java"
    $JAVA co.mitro.keyczar.RoundTripper encrypt testwtf "$MESSAGE"
    node ~/keyczarjs/roundtripper.js roundtrip testwtf
    $JAVA co.mitro.keyczar.RoundTripper decrypt testwtf "$MESSAGE"

    rm -f testwtf/*
    echo "Generting keys with JS"
    node ~/keyczarjs/roundtripper.js encrypt testwtf "$MESSAGE"
    $JAVA co.mitro.keyczar.RoundTripper roundtrip testwtf
    node ~/keyczarjs/roundtripper.js decrypt testwtf "$MESSAGE"

    echo $i
    echo
    MESSAGE="${MESSAGE}A"
done
*/

/**
 * Verifies interoperability with other implementations.
 *
 * encrypt mode:
 * - Generate private key.
 * - Write encrypted message.
 *
 * Other implementation:
 * - Decrypt message with private key.
 * - Re-encrypt message with public key.
 *
 * decrypt mode:
 * - Decrypt the re-encrypted message and verify
 */
public class RoundTripper {
  private static GenericKeyczar createKey(DefaultKeyType type, KeyPurpose purpose, String outpath) throws KeyczarException {
    // Generate a key and write it out
    System.out.println("generating " + type + " key into " + outpath);
    GenericKeyczar key = Util.createKey(type, purpose);
    Util.writeJsonToPath(key, outpath);
    return key;
  }

  private static void encrypt(String keyPath, String message, String outpath) throws KeyczarException, IOException {
    Encrypter key = new Encrypter(Util.readJsonFromPath(keyPath));
    System.out.println("encrypting message length " + message.length());
    String output = key.encrypt(message);

    Files.write(output, new File(outpath), Charsets.UTF_8);
  }

  private static void sign(String keyPath, String message, String outpath) throws KeyczarException, IOException {
    Signer key = new Signer(Util.readJsonFromPath(keyPath));
    System.out.println("signing message length " + message.length());
    String output = key.sign(message);

    Files.write(output, new File(outpath), Charsets.UTF_8);
  }

  public static void verify(String keyPath, String message, String signaturePath) throws KeyczarException {
    // Read the key, possibly decrypting using a password
    KeyczarReader reader = Util.readJsonFromPath(keyPath);
    Verifier key = new Verifier(reader);
    String signature = Util.readFile(signaturePath);

    System.out.println("verifying signature on message length " + message.length());
    if (!key.verify(message, signature)) {
      System.err.println("Signature could not be verified!\n");
      System.exit(1);
    }
  }

  public static String decrypt(String keyPath, String encryptedPath, String expectedMessage,
      DefaultKeyType expectedType, String keyPassword) throws KeyczarException {
    // Read the key, possibly decrypting using a password
    KeyczarReader reader = Util.readJsonFromPath(keyPath);
    if (keyPassword != null) {
      reader = new KeyczarPBEReader(reader, keyPassword);
    }

    KeyMetadata metadata = KeyMetadata.read(reader.getMetadata());
    if (metadata.getType() != expectedType) {
      throw new RuntimeException("Unexpected key type: " + metadata.getType());
    }

    Crypter key = new Crypter(reader);
    String data = Util.readFile(encryptedPath);
    String output = key.decrypt(data);

    if (expectedMessage != null) {
      if (output.equals(expectedMessage)) {
        System.out.println(encryptedPath + " decrypts successfully");
      } else {
        System.err.println("Decryption does not match?\n" + output);
        System.exit(1);
      }
    }
    return output;
  }

  private static final String MESSAGE = "Hello this is a longish message from Java";
  private static final String PASSWORD = "foopassword";

  private static void encryptSession(String keyPath, String outPath, String message) throws KeyczarException {
    KeyczarReader reader = Util.readJsonFromPath(keyPath);
    String output = Util.encryptWithSession(new Encrypter(reader), message);
    try {
      FileOutputStream out = new FileOutputStream(outPath);
      byte[] data = output.getBytes("UTF-8");
      out.write(data);
      out.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static String decryptSession(String keyPath, String inPath, String expectedMessage) throws KeyczarException {
    KeyczarReader reader = Util.readJsonFromPath(keyPath);
    String input = Util.readFile(inPath);
    String output = Util.decryptWithSession(new Crypter(reader), input);

    if (expectedMessage != null && !output.equals(expectedMessage)) {
      System.err.println("Session decryption does not match?\n" + output);
      System.exit(1);
    }
    return output;
  }

  private static String makeLonger(String inputMessage) {
    StringBuilder longMessage = new StringBuilder(inputMessage);
    // protect against zero length input
    if (longMessage.length() == 0) {
      longMessage.append(0x00);
    }

    while (longMessage.length() < 1000) {
      longMessage.append(longMessage);
    }
    return longMessage.toString();
  }

  public static void main(String[] args) throws KeyczarException, IOException {
    if (args.length != 2 && args.length != 3) {
      System.err.println("RoundTripper (mode) (in/out directory) [message to en/decrypt]");
      System.exit(1);
    }

    String mode = args[0];
    String dirpath = args[1];
    String message = MESSAGE;
    if (args.length == 3) {
      message = args[2];
    }

    if (mode.equals("encrypt")) {
      GenericKeyczar privateKey = createKey(DefaultKeyType.RSA_PRIV, KeyPurpose.DECRYPT_AND_ENCRYPT, dirpath + "/privatekey.json");

      // Export/use the public key
      KeyczarReader publicKeyReader = Util.exportPublicKeys(privateKey);
      Util.writeJsonToPath(new GenericKeyczar(publicKeyReader), dirpath + "/publickey.json");
      encrypt(dirpath + "/publickey.json", message, dirpath + "/publickey_encrypted");

      // Write the encrypted key
      FileOutputStream output = new FileOutputStream(dirpath + "/privatekey_encrypted.json");
      BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(output, "UTF-8"));
      JsonWriter.writeEncrypted(privateKey, PASSWORD, writer);
      writer.close();

      // Create a symmetric key
      createKey(DefaultKeyType.AES, KeyPurpose.DECRYPT_AND_ENCRYPT, dirpath + "/symmetric.json");
      encrypt(dirpath + "/symmetric.json", message, dirpath + "/symmetric_encrypted");

      // Encrypt with session data
      encryptSession(dirpath + "/publickey.json", dirpath + "/publickey_session", makeLonger(message));

      // Create a signing key
      GenericKeyczar privateSignKey = createKey(DefaultKeyType.RSA_PRIV, KeyPurpose.SIGN_AND_VERIFY, dirpath + "/privatekey_sign.json");

      // Export the public key; sign with the private key
      KeyczarReader publicSignKeyReader = Util.exportPublicKeys(privateSignKey);
      Util.writeJsonToPath(new GenericKeyczar(publicSignKeyReader), dirpath + "/publickey_sign.json");
      sign(dirpath + "/privatekey_sign.json", message, dirpath + "/privatekey_sign");
    } else if (mode.equals("decrypt")) {
      decrypt(dirpath + "/privatekey.json", dirpath + "/publickey_reencrypted", message, DefaultKeyType.RSA_PRIV, null);
      decrypt(dirpath + "/symmetric.json", dirpath + "/symmetric_reencrypted", message, DefaultKeyType.AES, null);

      String output = decryptSession(dirpath + "/privatekey.json", dirpath + "/publickey_session_reencrypted", makeLonger(message));

      // verify the session signature
      verify(dirpath + "/publickey_sign.json", output, dirpath + "/publickey_session_sign");
    } else if (mode.equals("roundtrip")) {
      // re-encrypt the message
      String output = decrypt(dirpath + "/privatekey.json", dirpath + "/publickey_encrypted", null, DefaultKeyType.RSA_PRIV, null);
      encrypt(dirpath + "/publickey.json", output, dirpath + "/publickey_reencrypted");

      // Decrypt with the encrypted key
      String output2 = decrypt(dirpath + "/privatekey_encrypted.json",
          dirpath + "/publickey_encrypted", null, DefaultKeyType.RSA_PRIV, PASSWORD);
      if (!output2.equals(output)) {
        System.err.println("encrypted private key did not work?");
        System.exit(1);
      }

      output = decrypt(dirpath + "/symmetric.json", dirpath + "/symmetric_encrypted", null, DefaultKeyType.AES, null);
      encrypt(dirpath + "/symmetric.json", output, dirpath + "/symmetric_reencrypted");

      // Verify the signature
      verify(dirpath + "/publickey_sign.json", output, dirpath + "/privatekey_sign");

      output = decryptSession(dirpath + "/privatekey.json", dirpath + "/publickey_session", null);
      encryptSession(dirpath + "/publickey.json", dirpath + "/publickey_session_reencrypted", output);

      // sign the session output
      sign(dirpath + "/privatekey_sign.json", output, dirpath + "/publickey_session_sign");
    } else {
      System.err.println("Mode must be encrypt, decrypt, or roundtrip\n");
      System.exit(1);
    }
  }
}
