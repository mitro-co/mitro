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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import co.mitro.recordio.RecordWriter.RecordType.TYPE;

public class RecordWriter implements AutoCloseable {
  
  // protocol specification:
  // big endian
  // record type:   int
  // record length: int
  //   - value: byte buffer
  public static class Header {
    private int version = 0;
    /**
     * @return the version
     */
    public int getVersion() {
      return version;
    }
    public void write(DataOutputStream os) throws IOException {
      os.writeInt(version);
    }
    public static Header MakeFromStream(DataInputStream is) throws IOException {
      Header h = new Header();
      h.version = is.readInt();
      return h;
    }
  }
  
  public static class Pointers {
    private static final int LOG_EVERY_BYTES = 1<<17; // one pointer every 128k bytes

    long numRecords = 0;
    long lastOffset = 0;
    DataOutputStream footerStream = null;
    
    public Pointers(OutputStream footerOutput) {
      
      footerStream = footerOutput != null ? new DataOutputStream(footerOutput) : null;
    }
    
    void addRecord(long offset) throws IOException {
      ++numRecords;
      if (numRecords == 1 || (offset - lastOffset) > LOG_EVERY_BYTES) {
        lastOffset = offset;
        if (null != footerStream) {
          writePointerRecord(footerStream, numRecords, lastOffset);
        }
      }
    }
    
    private void writePointerRecord(DataOutputStream os, long key, long lastOffset)
        throws IOException {
      os.writeLong(key);
      os.writeLong(lastOffset);
      footerStream.flush();
    }

    public void close() throws IOException {
      if (footerStream != null) {
        footerStream.close();
      }
    }
  }
  
  public static class RecordType {
    public enum TYPE {
      FILE_FOOTER,
      REVERSE_POINTER,
      DATA_UNCOMPRESSED,
      DATA_COMPRESSED
    }
    public static void write(DataOutputStream os, TYPE type) throws IOException {
      os.writeInt(type.ordinal());
    }
    public static TYPE read(DataInputStream is) throws IOException {
      int ord = is.readInt();
      return TYPE.values()[ord];
    }
  }
  protected DataOutputStream os;
  private boolean initialized = false;
  private Pointers footer; 
  private long offset = 0;
  public long bytesWritten() {
    return offset;
  }
  
  public RecordWriter(OutputStream os, OutputStream pointerOutput) {
    this.os = new DataOutputStream(os);
    footer = new Pointers(pointerOutput);
  }
  
  public synchronized void write(byte[] data) throws IOException {
    write(data, RecordType.TYPE.DATA_UNCOMPRESSED);
  }

  public synchronized void write(byte[] data, TYPE recordType) throws IOException {
    initialize();
    footer.addRecord(offset);
    int recLen = data.length;
    offset += recLen + 4;
    RecordType.write(os, recordType);
    os.writeInt(recLen);
    os.write(data);
    os.flush();
  }

  protected void initialize() throws IOException {
    if (!initialized) {
      (new Header()).write(os);
      offset += 4;
      initialized = true;
    }
  }
  
  @Override
  public synchronized void close() throws IOException {
    initialize();
    RecordType.write(os, RecordType.TYPE.FILE_FOOTER);
    this.os.close();
    footer.close();
  }
}
