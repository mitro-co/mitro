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
package co.mitro.metrics;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.PortUnreachableException;
import java.net.SocketException;

import org.junit.Before;
import org.junit.Test;

public class UDPBatchStatsDClientTest {
  private MockDatagramSocket mockSocket;
  private UDPBatchStatsDClient statsd;
  private BatchStatsDClient.Batch batch;

  private static class MockDatagramSocket extends DatagramSocket {
    public int sendCount;
    public boolean throwUnreachable;

    public MockDatagramSocket() throws SocketException {
      super();
    }

    @Override
    public void send(DatagramPacket p) throws IOException {
      sendCount += 1;
      if (throwUnreachable) {
        throw new PortUnreachableException("mock exception");
      }
    }
  }

  @Before
  public void setUp() throws Exception {
    mockSocket = new MockDatagramSocket();
    statsd = new UDPBatchStatsDClient(mockSocket);
    batch = new BatchStatsDClient.Batch();
  }

  @Test
  public void testEmptySend() throws IOException {
    statsd.sendStatistics(batch);
    assertEquals(0, mockSocket.sendCount);
  }

  @Test
  public void testSingleSend() throws IOException {
    batch.count("foo", 1);
    statsd.sendStatistics(batch);
    assertEquals(1, mockSocket.sendCount);
  }

  @Test
  public void testTwoSends() throws IOException {
    batch.count("foo", 1);
    assertEquals(1, batch.getPackets().size());
    batch.count("bar", 1);
    assertEquals(2, batch.getPackets().size());
    statsd.sendStatistics(batch);
    assertEquals(2, mockSocket.sendCount);
  }

  @Test
  public void testUnreachableSend() throws IOException, InterruptedException {
    // On Linux UDP sockets can eventually throw PortUnreachableException
    // it seems like it always happens on the second send for localhost, but I don't trust that
    // we silently ignore this error

    // really be sure we ignore this exception
    batch.count("foo", 1);
    mockSocket.throwUnreachable = true;
    statsd.sendStatistics(batch);

    // pick a port that should be always unused
    statsd = new UDPBatchStatsDClient("localhost", 1);

    for (int i = 0; i < 50; i++) {
      statsd.sendStatistics(batch);
    }
  }
}
