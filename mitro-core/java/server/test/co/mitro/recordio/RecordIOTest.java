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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

import org.junit.Before;
import org.junit.Test;

 
public class RecordIOTest {
  public ByteArrayOutputStream os;

  @Before
  public void setUp() {
    os = new ByteArrayOutputStream(); 
  }
  
  @Test
  public void testEmpty() throws IOException {
    RecordWriter writer = new RecordWriter(os, null);
    writer.close();
    
    byte [] data = os.toByteArray();
    
    InputStream is = (new ByteArrayInputStream(data));
    RecordReader reader = RecordReader.MakeFromStream(is);
    byte[] dc = reader.read();
    assertEquals(null, dc);
  }


  @Test
  public void testSimple() throws IOException {
    RecordWriter writer = new RecordWriter(os, null);
    writer.write("hello".getBytes());
    writer.write("there".getBytes());
    writer.write("how".getBytes());
    writer.write(new byte[0]);
    writer.close();
    
    byte [] data = os.toByteArray();
    
    InputStream is = (new ByteArrayInputStream(data));
    RecordReader reader = RecordReader.MakeFromStream(is);
    byte[] dc;
    dc = reader.read();
    assertEquals(new String(dc), "hello");
    dc = reader.read();
    assertEquals(new String(dc), "there");
    dc = reader.read();
    assertEquals(new String(dc), "how");
    dc = reader.read();
    assertEquals(0, dc.length);
    dc = reader.read();
    assertEquals(null, dc);
  }
  
  @Test
  public void testLots() throws IOException {
    // Verify writing a few 1 MB chunks; no need to do more than a few
    // NOTE: REPEAT_COUNT = 10 os.toByteArray() rarely fails with OutOfMemoryError
    final int REPEAT_COUNT = 5;
    byte[] data = new byte[1<<20];
    try (RecordWriter writer = new RecordWriter(os, null)) {
      for (byte i = 0; i < REPEAT_COUNT; ++i) {
        for (int j = 0; j < data.length; ++j) {
          data[j] = i;
        }
        writer.write(data);
      }
    }        
    byte[] buf = os.toByteArray();
    
    InputStream is = (new ByteArrayInputStream(buf));
    RecordReader reader = RecordReader.MakeFromStream(is);
    for (byte i = 0; i < REPEAT_COUNT; ++i) {
      for (int j = 0; j < data.length; ++j) {
        data[j] = i;
      }
      System.out.println("reading " + i);
      byte[] dc = reader.read();
      assertArrayEquals(data, dc);
    }
    assertEquals(null, reader.read());
    try {
      assertEquals(null, reader.read());
      fail("expected exception");
    } catch (EOFException e) {}
  }

  private static class ByteArrayOutputClosedProxy extends ByteArrayOutputStream {
    private boolean isClosed = false;
    @Override
    public void close() throws IOException {
      isClosed = true;
      super.close();
    }

    public boolean isClosed() {
      return isClosed;
    }
  }

  @Test
  public void openCloseFlush() throws IOException {
    ByteArrayOutputClosedProxy output = new ByteArrayOutputClosedProxy();
    ByteArrayOutputClosedProxy pointers = new ByteArrayOutputClosedProxy();
    assertEquals(0, output.size());
    RecordWriter writer = new RecordWriter(output, pointers);
    assertEquals(0, output.size());
    assertEquals(0, pointers.size());

    writer.write(new byte[]{0x00});
    // writes (int32 header version)(int32 type)(int32 length)(data)
    assertEquals(4 + 4 + 4 + 1, output.size());
    assertEquals(16, pointers.size());

    assertFalse(output.isClosed());
    assertFalse(pointers.isClosed());
    writer.close();
    assertTrue(output.isClosed());
    assertTrue(pointers.isClosed());
    // writes (int32 footer)
    assertEquals(13 + 4, output.size());
    assertEquals(16, pointers.size());
  }
}
