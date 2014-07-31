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

import static org.junit.Assert.assertEquals;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.keyczar.Crypter;
import org.keyczar.DefaultKeyType;
import org.keyczar.Encrypter;
import org.keyczar.GenericKeyczar;
import org.keyczar.enums.KeyPurpose;
import org.keyczar.exceptions.KeyczarException;
import org.keyczar.interfaces.KeyczarReader;

public class UtilTest {
	private static final String MESSAGE = "hello world";

	@Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();	    

	@Test
	public void testCreateExportKey() throws KeyczarException {
		// create the key, export the public key; 1024 bits is the smallest size
		GenericKeyczar keyczar = Util.createKey(
		    DefaultKeyType.RSA_PRIV, KeyPurpose.DECRYPT_AND_ENCRYPT, 1024);
		KeyczarReader publicKeyReader = Util.exportPublicKeys(keyczar);
		Encrypter encrypter = new Encrypter(publicKeyReader);

		// test that it works
		String ciphertext = encrypter.encrypt(MESSAGE);
		Crypter crypter = new Crypter(Util.readerFromKeyczar(keyczar));
		String decrypted = crypter.decrypt(ciphertext);
		assertEquals(MESSAGE, decrypted);

		// test a session
		StringBuilder longMessage = new StringBuilder("hello message ");
		while (longMessage.length() < 500) {
			longMessage.append(longMessage);
		}

		ciphertext = Util.encryptWithSession(encrypter, longMessage.toString());
		assertEquals(longMessage.toString(), Util.decryptWithSession(crypter, ciphertext));
	}

  @Test
  public void testGenerateKeyczarReader() throws KeyczarException {
    KeyczarReader reader = Util.generateKeyczarReader(DefaultKeyType.AES, KeyPurpose.DECRYPT_AND_ENCRYPT);
    Crypter crypter = new Crypter(reader);

    // test that it works
    String ciphertext = crypter.encrypt(MESSAGE);
    String decrypted = crypter.decrypt(ciphertext);
    assertEquals(MESSAGE, decrypted);
  }

	@Test
	public void testWriteReadSymmetricKey() throws KeyczarException {
		GenericKeyczar keyczar = Util.createKey(DefaultKeyType.AES, KeyPurpose.DECRYPT_AND_ENCRYPT);

    String path = tempFolder.getRoot().getAbsolutePath() + "/out.json";
    Util.writeJsonToPath(keyczar, path);
    
    Crypter roundtripped = new Crypter(Util.readJsonFromPath(path));
		verifyKeyCompatibility(keyczar, roundtripped);
	}

  protected void verifyKeyCompatibility(GenericKeyczar keyczar,
      Crypter roundtripped) throws KeyczarException {
    
    String ciphertext = roundtripped.encrypt(MESSAGE);
		Crypter original = new Crypter(Util.readerFromKeyczar(keyczar));
		String decrypted = original.decrypt(ciphertext);
		assertEquals(MESSAGE, decrypted);

    ciphertext = original.encrypt(MESSAGE);
    decrypted = roundtripped.decrypt(ciphertext);
    assertEquals(MESSAGE, decrypted);
    
  }

  @Test
  public void testSymmetricKeyToFromJson() throws KeyczarException {
    GenericKeyczar keyczar = Util.createKey(DefaultKeyType.AES, KeyPurpose.DECRYPT_AND_ENCRYPT);
    String json = JsonWriter.toString(keyczar);
    Crypter roundtripped = Util.crypterFromJson(json);
    verifyKeyCompatibility(keyczar, roundtripped);
  }
}
