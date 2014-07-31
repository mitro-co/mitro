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
package co.mitro.core.server;

import static org.junit.Assert.*;

import org.junit.Test;
import org.keyczar.exceptions.KeyczarException;

public class SecretsBundleTest {
  @Test
  public void verifyBadSignature() throws KeyczarException {
    SecretsBundle secrets = SecretsBundle.generateForTest();

    final String TOKEN = "token";
    String signature = secrets.signToken(TOKEN);
    assertTrue(secrets.verifyToken(TOKEN, signature));
    assertFalse(secrets.verifyToken(TOKEN, signature + "A"));
    // Base64DecodingException
    assertFalse(secrets.verifyToken(TOKEN, signature.substring(0, signature.length()-1)));
    assertFalse(secrets.verifyToken(TOKEN, signature.substring(0, signature.length()-2)));
    // ArrayIndexOutOfBoundsException
    assertFalse(secrets.verifyToken(TOKEN, ""));

    // change the first byte: version exception
    assert signature.charAt(0) == 'A';
    assertFalse(secrets.verifyToken(TOKEN, 'B' + signature.substring(1, signature.length())));
  }
}
