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
package co.mitro.twofactor;

import static org.junit.Assert.*;

import org.junit.Test;

public class QRGeneratorTest {
  @Test
  public void makeTotpUrl() {
    String output = QRGenerator.makeTotpUrl("\u00e9xample issuer", "\u00e9xample user", "secret");
    assertEquals("otpauth://totp/%C3%A9xample+issuer:%C3%A9xample+user?secret=secret&issuer=%C3%A9xample+issuer", output);

    assertIllegalArgument("", "user", "secret");
    assertIllegalArgument("issuer", "", "secret");
    assertIllegalArgument("issuer", "user", "");
    // no colons
    assertIllegalArgument("iss:uer", "user", "secret");
    assertIllegalArgument("issuer", "us:er", "secret");
  }

  private static void assertIllegalArgument(String issuer, String accountName, String secret) {
    try {
      QRGenerator.makeTotpUrl(issuer, accountName, secret);
      fail("expected exception");
    } catch (IllegalArgumentException ignored) {
    }
  }
}
