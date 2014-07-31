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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.keyczar.Crypter;
import org.keyczar.KeyMetadata;
import org.keyczar.enums.KeyPurpose;
import org.keyczar.exceptions.KeyczarException;
import org.keyczar.interfaces.KeyczarReader;

public class KeyczarJsonReaderTest {
	private static final String JSON_KEY =
			"{\"meta\":\"{\\\"name\\\":\\\"Imported AES\\\",\\\"purpose\\\":\\\"DECRYPT_AND_ENCRYPT\\\"," +
			"\\\"type\\\":\\\"AES\\\",\\\"versions\\\":[{\\\"exportable\\\":false," + 
			"\\\"status\\\":\\\"PRIMARY\\\",\\\"versionNumber\\\":0}],\\\"encrypted\\\":false}\"," +
			"\"0\":\"{\\\"aesKeyString\\\":\\\"AAAAAAAAAAAAAAAAAAAAAA\\\",\\\"hmacKey\\\":" +
			"{\\\"hmacKeyString\\\":\\\"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\\\"," +
			"\\\"size\\\":256},\\\"mode\\\":\\\"CBC\\\",\\\"size\\\":128}\"}";

	@Test
	public void testSimple() throws KeyczarException {
		KeyczarReader reader = new KeyczarJsonReader(JSON_KEY);
		KeyMetadata metadata = KeyMetadata.read(reader.getMetadata());
		assertEquals(0, metadata.getPrimaryVersion().getVersionNumber());
		assertEquals(KeyPurpose.DECRYPT_AND_ENCRYPT, metadata.getPurpose());
		assertEquals("Imported AES", metadata.getName());
		assertEquals(1, metadata.getVersions().size());
		assertEquals(0, metadata.getVersions().get(0).getVersionNumber());
		assertFalse(metadata.getVersions().get(0).isExportable());

		Crypter crypter = new Crypter(reader);
		String plaintext = "hello world";
		String encrypted = crypter.encrypt(plaintext);
		assertTrue(!encrypted.equals(plaintext));

		String decrypted = crypter.decrypt(encrypted);
		assertEquals(plaintext, decrypted);

		// TODO: Add an old version of a key; test decrypting with it
	}
}
