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
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sends a batch of statistics in a single message. Different statsd implementations will have
 * different limits for the maximum packet size. dogstatsd's maximum is 1024, so that is what
 * we use here.
 */
public interface BatchStatsDClient {
  /** As defined by dogstatsd: https://github.com/DataDog/dd-agent/blob/master/dogstatsd.py */
  public static final int MAX_PACKET_LENGTH = 1024;
  public static final class Batch implements StatsDClient {
    private final byte[] packetBuffer = new byte[MAX_PACKET_LENGTH];
    private int packetLength = 0;
    private final ArrayList<byte[]> packets = new ArrayList<byte[]>();

    private static final Pattern INVALID_NAME_CHARS = Pattern.compile("\\||:|\n");
    private final void validateName(String name) {
      Matcher m = INVALID_NAME_CHARS.matcher(name);
      if (m.find()) {
        char illegalChar = name.charAt(m.start());
        String out = Character.toString(illegalChar);;
        if (illegalChar == '\n') {
          out = "\\n";
        }
        throw new IllegalArgumentException("name contains invalid character: " + out);
      }
    }

    private void addStatistic(String name, String value, char type) {
      // statsd format: https://github.com/b/statsd_spec
      // http://docs.datadoghq.com/guides/dogstatsd/#datagram-format
      validateName(name);

      StringBuilder builder = new StringBuilder(name);
      builder.append(':');
      builder.append(value);
      builder.append('|');
      builder.append(type);
      builder.append('\n');

      // convert to UTF-8; append to current packet
      byte[] bytes = toUtf8(builder.toString());
      if (bytes.length > MAX_PACKET_LENGTH) {
        throw new IllegalArgumentException("name/value too long for max packet length ("
            + bytes.length + " > " + MAX_PACKET_LENGTH + ")");
      }
      if (bytes.length + packetLength > MAX_PACKET_LENGTH) {
        allocateNewPacket();
      }

      // Append the value to the packet
      assert bytes.length + packetLength <= MAX_PACKET_LENGTH;
      System.arraycopy(bytes, 0, packetBuffer, packetLength, bytes.length);
      packetLength += bytes.length;
    }

    private void allocateNewPacket() {
      assert 0 < packetLength && packetLength <= MAX_PACKET_LENGTH;
      byte[] packet = new byte[packetLength];
      System.arraycopy(packetBuffer, 0, packet, 0, packetLength);
      packets.add(packet);
      packetLength = 0;
    }

    private byte[] toUtf8(String s) {
      try {
        return s.getBytes("UTF-8");
      } catch (UnsupportedEncodingException e) {
        throw new RuntimeException("UTF-8 is required by the JRE; unexpected exception", e);
      }
    }

    @Override
    public void count(String name, long delta) {
      // counter format: name:value|c
      addStatistic(name, Long.toString(delta, 10), 'c');
      validateName(name);
    }

    @Override
    public void recordGauge(String name, long value) {
      addStatistic(name, Long.toString(value), 'g');
    }

    @Override
    public void recordGauge(String name, double value) {
      addStatistic(name, Double.toString(value), 'g');
    }

    @Override
    public void recordExecutionTime(String aspect, int timeInMs) {
      throw new UnsupportedOperationException("TODO");
    }

    public List<byte[]> getPackets() {
      if (packetLength > 0) {
        allocateNewPacket();
      }

      assert packetLength == 0;
      return Collections.unmodifiableList(packets);
    }

    /**
     * Returns new number of statistics recorded in this batch. Slow because it parses the packets.
     */
    public int getNumStats() {
      if (packetLength > 0) {
        allocateNewPacket();
      }

      // count newline characters in each packet
      int count = 0;
      for (byte[] packet : getPackets()) {
        for (int i = 0; i < packet.length; i++) {
          if (packet[i] == '\n') {
            count += 1;
          }
        }
      }
      return count;
    }
  }

  /**
   * Attempts to send statistics to statsd. It should only throw exceptions for truly unusual
   * errors, since UDP is unreliable, so there is no way to check that statsd really is processing
   * the messages. For example, we ignore PortUnreachableException, since applications can't rely
   * on it: it isn't thrown on some operating systems, or ports/ICMP may be firewalled.
   */
  public void sendStatistics(Batch statistics) throws IOException;
}
