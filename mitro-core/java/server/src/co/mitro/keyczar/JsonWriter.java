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

import java.lang.reflect.Type;

import org.keyczar.GenericKeyczar;
import org.keyczar.KeyVersion;
import org.keyczar.KeyczarFileReader;
import org.keyczar.exceptions.KeyczarException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

public class JsonWriter {
  public static final class KeyczarSerializer implements
      JsonSerializer<GenericKeyczar> {
    // Everything must be final so this is thread-safe
    private final String password;

    public KeyczarSerializer(String password) {
      this.password = password;
    }

    @Override
    public JsonElement serialize(GenericKeyczar keyczar, Type typeOfKey,
        JsonSerializationContext context) {
      // key.toString() returns the serialized metadata
      // this is a bit gross: JSON embedded in JSON, but it makes it
      // easier to integrate
      JsonObject obj = new JsonObject();

      // hack to set encrypted = true if needed
      String metadataString = keyczar.toString();
      if (password != null && !keyczar.getMetadata().isEncrypted()) {
        String out = metadataString.replaceFirst("\"encrypted\":false", "\"encrypted\":true");
        assert !out.equals(metadataString);
        metadataString = out;
      }
      obj.addProperty("meta", metadataString);

      for (KeyVersion version : keyczar.getVersions()) {
        String keyData = keyczar.getKey(version).toString();

        if (password != null) {
          try {
            keyData = KeyczarPBEReader.encryptKey(keyData, password);
          } catch (KeyczarException e) {
            throw new RuntimeException(e);
          }
        }

        obj.addProperty(Integer.toString(version.getVersionNumber()),
            keyData);
      }

      return obj;
    }
  }

  private static final Gson gson;
  static {
    // gson itself is thread-safe. Our KeyczarSerializer must be as well.
    KeyczarSerializer serializer = new KeyczarSerializer(null);
    gson = new GsonBuilder().registerTypeAdapter(
        GenericKeyczar.class, serializer).create();
  }

  /** Writes input Keyczar key as a JSON string to output. */
  public static void write(GenericKeyczar input, Appendable output) {
    gson.toJson(input, output);
  }

  /** Returns input Keyczar key as a JSON string. */
  public static String toString(GenericKeyczar input) {
    return gson.toJson(input);
  }

  public static void writeEncrypted(GenericKeyczar input, String password, Appendable output) {
    KeyczarSerializer serializer = new KeyczarSerializer(password);

    Gson gson = new GsonBuilder().registerTypeAdapter(
        GenericKeyczar.class, serializer).create();
    gson.toJson(input, output);
  }

  public static void main(String[] arguments) throws KeyczarException {
    if (arguments.length != 1) {
      System.err.println("JsonWriter (input key path)");
      System.err.println("  Reads a key and writes to stdout as JSON");
      System.exit(1);
    }

    GenericKeyczar keyczar = new GenericKeyczar(new KeyczarFileReader(arguments[0]));
    write(keyczar, System.out);
  }
}
