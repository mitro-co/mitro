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
package co.mitro.recordio;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;

public class JsonRecordWriter extends RecordWriter {
  private static final Logger logger = LoggerFactory.getLogger(JsonRecordWriter.class);
  private static final Gson gson = new Gson();
  private static final String POINTERS_SUFFIX = ".pointers";

  public static JsonRecordWriter MakeFromFilePrefix(String prefix) {
    Preconditions.checkNotNull(prefix);

    long time = System.nanoTime();
    String filename = prefix + '.' + Long.toString(time);
    String pointers = filename + POINTERS_SUFFIX;

    FileOutputStream recordsFile = null;
    FileOutputStream pointersFile = null;
    try {
      recordsFile = new FileOutputStream(filename);
      pointersFile = new FileOutputStream(pointers);
    } catch (FileNotFoundException e) {
      logger.error("Error opening log or pointers file {}", filename, e);
      tryClose(recordsFile);
      tryClose(pointersFile);
      return null;
    }

    return new JsonRecordWriter(recordsFile, pointersFile);
  }

  private static void tryClose(FileOutputStream stream) {
    if (stream != null) {
      try {
        stream.close();
      } catch (IOException e) {
        logger.warn("error closing output stream", e);
      }
    }
  }
  
  public JsonRecordWriter(OutputStream os, OutputStream pointerOs) {
    super(os, pointerOs);
  }

  static class JsonLog {
    JsonLog(Object payload, Object metadata) {
      this.payload = payload;
      this.metadata = metadata;
    }
    public Object payload;
    public Object metadata;
  }
  private volatile boolean loggedIoException = false;
  public synchronized void writeJson(Object payload, Object metadata) {
    JsonLog jl = new JsonLog(payload, metadata);
    // TODO: write directly to the writer
    try {
      write(gson.toJson(jl).getBytes("UTF-8"));
      loggedIoException = false;
    } catch (IOException e) {
      if (!loggedIoException) {
        loggedIoException = true;
        logger.error("error writing record.  Ignoring subsequent errors.", e);
      }
    }
  }
}
