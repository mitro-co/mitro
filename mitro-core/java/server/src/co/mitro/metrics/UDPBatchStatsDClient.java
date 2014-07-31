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

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.PortUnreachableException;
import java.net.SocketException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UDPBatchStatsDClient implements BatchStatsDClient {
  private static final Logger logger = LoggerFactory.getLogger(UDPBatchStatsDClient.class);

  private final DatagramSocket clientSocket;

  public UDPBatchStatsDClient(DatagramSocket clientSocket) {
    this.clientSocket = clientSocket;
  }

  public UDPBatchStatsDClient(String host, int port) throws SocketException {
    clientSocket = new DatagramSocket();
    clientSocket.connect(new InetSocketAddress(host, port));
  }

  @Override
  public void sendStatistics(Batch batch) throws IOException {
    for (byte[] packet : batch.getPackets()) {
      // TODO: Re-use p?
      DatagramPacket p = new DatagramPacket(packet, packet.length);
      // TODO: might block if sending buffer fills? investigate? Move to background thread?
      try {
        clientSocket.send(p);
      } catch (PortUnreachableException ignored) {
        logger.debug("ignoring PortUnreachableException; no statsd running on {}?",
            clientSocket.getRemoteSocketAddress());
      }
    }

    if (logger.isDebugEnabled()) {
      logger.debug("sent {} packets containing {} statistics",
          batch.getPackets().size(), batch.getNumStats());
    }
  }
}
