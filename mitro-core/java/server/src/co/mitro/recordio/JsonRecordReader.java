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
package co.mitro.recordio;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.mitro.core.server.data.RPC.LogMetadata;
import co.mitro.core.server.data.RPC.SignedRequest;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;

public class JsonRecordReader extends RecordReader {
  private static final Logger logger = LoggerFactory.getLogger(JsonRecordReader.class);
  private static final Gson gson = new Gson();
  
  public static JsonRecordReader MakeFromFilename(String fn) throws IOException {
    Preconditions.checkNotNull(fn);
    InputStream is = new FileInputStream(fn);
    return new JsonRecordReader(is);
  }

  public JsonRecordReader(InputStream is) throws IOException {
    super(is);
  }

  public static class JsonLog {
    // TODO: this should be generic
    public SignedRequest payload;
    public LogMetadata metadata;
  }

  public synchronized JsonLog readJson() throws IOException {
    byte[] buffer = read();
    if (buffer == null || buffer.length == 0) {
      return null;
    }
    return gson.fromJson(new String(buffer, "UTF-8"), JsonLog.class);
  }
}
