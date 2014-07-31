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
package co.mitro.core.util;

import java.security.SecureRandom;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Random {
  private static int fillCharRange(char start, char endInclusive, char[] output, int index) {
    for (char c = start; c <= endInclusive; c++) {
      output[index] = c;
      index += 1;
    }
    return index;
  }

  protected static char[] makeAlphaNumChars() {
    char[] output = new char[26+26+10];
    int index = fillCharRange('a', 'z', output, 0);
    index = fillCharRange('A', 'Z', output, index);
    index = fillCharRange('0', '9', output, index);
    assert index == output.length;
    return output;
  }

  private static final char[] ALPHANUM = makeAlphaNumChars();

  // caches SecureRandom objects because they are expensive
  private static final ConcurrentLinkedQueue<SecureRandom> RNG_QUEUE =
      new ConcurrentLinkedQueue<SecureRandom>();

  /** Returns a secure random password with numChars alpha numeric characters. */
  public static String makeRandomAlphanumericString(int numChars) {
    SecureRandom rng = RNG_QUEUE.poll();
    if (rng == null) {
      // automatically seeded on first use
      rng = new SecureRandom();
    }

    StringBuilder output = new StringBuilder(numChars);
    while (output.length() != numChars) {
      // nextInt()'s algorithm is unbiased, so this will select an unbiased char from ALPHANUM
      int index = rng.nextInt(ALPHANUM.length);
      output.append(ALPHANUM[index]);
    }

    RNG_QUEUE.add(rng);
    return output.toString();
  }
}
