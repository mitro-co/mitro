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
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.keyczar.AesKey;
import org.keyczar.Crypter;
import org.keyczar.DefaultKeyType;
import org.keyczar.GenericKeyczar;
import org.keyczar.HmacKey;
import org.keyczar.KeyMetadata;
import org.keyczar.KeyVersion;
import org.keyczar.KeyczarKey;
import org.keyczar.enums.KeyPurpose;
import org.keyczar.enums.KeyStatus;
import org.keyczar.exceptions.KeyczarException;
import org.keyczar.interfaces.KeyczarReader;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

public class JsonWriterTest {
  // Basically a copy of ImportedKeyReader which does not have public constructors
  public static final class StaticKeyReader implements KeyczarReader {
    private final KeyMetadata metadata;
    private final List<KeyczarKey> keys;

    StaticKeyReader(AesKey key) {
      this.metadata = new KeyMetadata(
              "Imported AES", KeyPurpose.DECRYPT_AND_ENCRYPT, DefaultKeyType.AES);
      KeyVersion version = new KeyVersion(0, KeyStatus.PRIMARY, false);
      this.metadata.addVersion(version);
      this.keys = new ArrayList<KeyczarKey>();
      this.keys.add(key);
    }

    @Override
    public String getKey() throws KeyczarException {
    KeyMetadata metadata = KeyMetadata.read(getMetadata());
    return getKey(metadata.getPrimaryVersion().getVersionNumber());
    }

    @Override
    public String getKey(int version) {
      return keys.get(version).toString();
    }

    @Override
    public String getMetadata() {
      return metadata.toString();
    }
  }

  public static GenericKeyczar makeKey() throws KeyczarException {
    HmacKey hmacKey = new HmacKey(
        new byte[DefaultKeyType.HMAC_SHA1.getAcceptableSizes().get(0)/8]);
    AesKey aesKey = new AesKey(
        new byte[DefaultKeyType.AES.getAcceptableSizes().get(0)/8], hmacKey);
    StaticKeyReader reader = new StaticKeyReader(aesKey);
    GenericKeyczar keyczar = new GenericKeyczar(reader);
    return keyczar;
  }

  @Test
  public void testWriteKey() throws KeyczarException {
    // Create a key with zero value
    GenericKeyczar keyczar = makeKey();

    StringBuilder builder = new StringBuilder();
    JsonWriter.write(keyczar, builder);
    String serialized = builder.toString();

    String substr = "\"0\":\"{\\\"aesKeyString\\\":\\\"AAAAAAAAAA";
    assertTrue(serialized.contains(substr));
  }

  @Test
  public void testWriteEncrypted() throws KeyczarException {
    GenericKeyczar keyczar = makeKey();

    final String PASSWORD = "foopassword";
    StringBuilder builder = new StringBuilder();
    JsonWriter.writeEncrypted(keyczar, PASSWORD, builder);
    String serialized = builder.toString();

    JsonParser parser = new JsonParser();
    JsonElement element = parser.parse(serialized);
    String metadata = element.getAsJsonObject().getAsJsonPrimitive("meta").getAsString();
    element = parser.parse(metadata);
    JsonPrimitive p = element.getAsJsonObject().getAsJsonPrimitive("encrypted");
    assertTrue(p.getAsBoolean());

    KeyczarReader reader = new KeyczarJsonReader(serialized);
    KeyczarPBEReader pbeReader = new KeyczarPBEReader(reader, PASSWORD);
    Crypter c = new Crypter(pbeReader);
    assertEquals("hello", c.decrypt(c.encrypt("hello")));
  }
}
