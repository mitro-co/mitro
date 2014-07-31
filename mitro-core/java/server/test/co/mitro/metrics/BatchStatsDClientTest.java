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
package co.mitro.metrics;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.UnsupportedEncodingException;

import org.junit.Before;
import org.junit.Test;

public class BatchStatsDClientTest {
  private BatchStatsDClient.Batch b;

  @Before
  public void setUp() {
    b = new BatchStatsDClient.Batch();
  }

  private void assertPacket(String expected) {
    try {
      byte[] output = expected.getBytes("UTF-8");
      assertEquals(1, b.getPackets().size());
      assertArrayEquals(output, b.getPackets().get(0));
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void testCounters() throws UnsupportedEncodingException {
    assertEquals(0, b.getNumStats());
    assertEquals(0, b.getPackets().size());

    b.count("h��llo.world", -5);
    long v = ((long) Integer.MAX_VALUE) + 1000;
    b.count("baz.bar", v);

    assertEquals(2, b.getNumStats());
    assertPacket(String.format("h��llo.world:-5|c\nbaz.bar:%d|c\n", v));
  }

  @Test
  public void testGauges() {
    b.recordGauge("baz", -5);
    b.recordGauge("bar", 3.14);
    assertPacket("baz:-5|g\nbar:3.14|g\n");
  }

  private void checkMessage(IllegalArgumentException e, String name) {
    assertTrue("name=" + name + " bad exception message: " + e.getMessage(),
        e.getMessage().contains("invalid character"));
  }

  @Test
  public void testBadNames() {
    String[] names = {
        "hello|bar",
        "hello\nbar",
        "hello:bar",
    };

    for (final String name : names) {
      try {
        b.count(name, 1);
        fail("expected exception");
      } catch (IllegalArgumentException e) {
        checkMessage(e, name);
      }

      try {
        b.recordGauge(name, 1);
        fail("expected exception");
      } catch (IllegalArgumentException e) {
        checkMessage(e, name);
      }
    }

    assertEquals(0, b.getPackets().size());
  }

  @Test
  public void testBigPacket() {
    StringBuilder nameBuilder = new StringBuilder();
    for (int i = 0; i < 64; i++) {
      // two bytes per char
      nameBuilder.append('\u00e9');
    }

    String name = nameBuilder.toString();
    for (int i = 0; i < 8; i++) {
      b.recordGauge(name, i);
    }

    assertEquals(2, b.getPackets().size());
    assertEquals(8, b.getNumStats());
  }

  @Test(expected=IllegalArgumentException.class)
  public void testNameTooLong() {
    StringBuilder nameBuilder = new StringBuilder();
    for (int i = 0; i < BatchStatsDClient.MAX_PACKET_LENGTH; i++) {
      nameBuilder.append('a');
    }

    b.count(nameBuilder.toString(), 1);
  }
}
