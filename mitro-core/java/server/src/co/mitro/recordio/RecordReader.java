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

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import co.mitro.recordio.RecordWriter.Header;
import co.mitro.recordio.RecordWriter.RecordType;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;

public class RecordReader implements AutoCloseable {
  // protocol specification:
  // big endian
  // record type:   int
  // record length: int
  //   - value: byte buffer

  private final DataInputStream is;

  public RecordReader(InputStream input) throws IOException {
    Preconditions.checkNotNull(input);
    is = new DataInputStream(input);

    int version = Header.MakeFromStream(is).getVersion();
    assert version == 0;
  }

  public static RecordReader MakeFromStream(InputStream is) throws IOException {
    return new RecordReader(is);
  }
  
  // returns null if EOF
  public synchronized byte[] read() throws IOException {
    return read(Sets.immutableEnumSet(RecordType.TYPE.DATA_COMPRESSED,RecordType.TYPE.DATA_UNCOMPRESSED));
  }

  // returns null if EOF
  public synchronized byte[] read(Set<RecordType.TYPE> typesToRead) throws IOException {
    byte[] rval = null;
    RecordType.TYPE type = RecordType.read(is);
    if (!typesToRead.contains(type) && type != RecordType.TYPE.FILE_FOOTER) {
      assert(false);
      return rval;
    } else if (RecordType.TYPE.FILE_FOOTER == type) {
      this.close();
      return rval;
    } 

    int recLen = is.readInt();
    rval = new byte[recLen];
    is.readFully(rval, 0, recLen);
    return rval;
  }
  
  @Override
  public synchronized void close() throws IOException {
    this.is.close();
  }
}
