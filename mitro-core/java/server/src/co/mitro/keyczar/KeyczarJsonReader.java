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

import org.keyczar.KeyMetadata;
import org.keyczar.exceptions.KeyczarException;
import org.keyczar.interfaces.KeyczarReader;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Reads a Keyczar key serialized to a JSON string. */
public class KeyczarJsonReader implements KeyczarReader {
	private final JsonObject parsed;

	public KeyczarJsonReader(String json) {
		JsonParser parser = new JsonParser();
		parsed = parser.parse(json).getAsJsonObject();
	}

	@Override
	public String getKey(int version) throws KeyczarException {
		return parsed.get(Integer.toString(version)).getAsString();
	}

	@Override
	public String getKey() throws KeyczarException {
	    KeyMetadata metadata = KeyMetadata.read(getMetadata());
		
	    return getKey(metadata.getPrimaryVersion().getVersionNumber());
	}

	@Override
	public String getMetadata() throws KeyczarException {
		return parsed.get("meta").getAsString();
	}
}
