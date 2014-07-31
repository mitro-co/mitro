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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Set;

import org.junit.Test;

import com.google.common.collect.Sets;

public class RandomTest {
  @Test
  public void testMakeCharRange() {
    char[] range = Random.makeAlphaNumChars();
    Set<Character> s = Sets.newHashSet();
    for (char c : range) {
      boolean added = s.add(c);
      assertTrue("duplicate char in range: " + c, added);
    }
    assertEquals(range.length, s.size());
    assertEquals(26 + 26 + 10, s.size());

    char[] EXPECTED = {'a', 'e', 'z', 'A', 'J', 'Z', '0', '8', '9'};
    for (char c : EXPECTED) {
      assertTrue(s.contains(c));
    }

    // values before and after the ranges, and a few others
    char[] NOT_EXPECTED = {'/', ':', '@', '[', '`', '{', '.', '!'};
    for (char c : NOT_EXPECTED) {
      assertFalse(s.contains(c));
    }
  }

  @Test
  public void testMakeRandomAlphanumericString() {
    String pw = Random.makeRandomAlphanumericString(12);
    assertEquals(12, pw.length());
  }
}
