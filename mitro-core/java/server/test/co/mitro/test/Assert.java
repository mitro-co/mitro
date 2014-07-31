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
package co.mitro.test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.util.concurrent.Callable;

public final class Assert {
  /** Not constructible. */
  private Assert() {}

  /**
   * Asserts that runnable throws an exception of type, with message containing substring
   * (case insensitive).
   *
   * This is an attempt to factor-out this common test code, but callables are annoying so it
   * isn't clear if this is better than the duplication. JUnit ExpectedException rules help,
   * but then you can only have one exception per test.
   */
  public static void assertExceptionMessage(
      String expectedMessageSubstring, Class<? extends Throwable> type, Callable<Void> callable) {
    try {
      callable.call();
      fail("expected exception");
    } catch (Throwable expected) {
      assertThat(expected, instanceOf(type));
      assertThat(expected.getMessage().toLowerCase(),
          containsString(expectedMessageSubstring.toLowerCase()));
    }
  }
}
