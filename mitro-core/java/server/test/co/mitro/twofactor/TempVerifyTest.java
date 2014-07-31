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

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import javax.servlet.ServletException;

import org.junit.Before;
import org.junit.Test;

import co.mitro.core.crypto.KeyInterfaces.CryptoError;
import co.mitro.test.MockHttpServletRequest;
import co.mitro.test.MockHttpServletResponse;

public class TempVerifyTest extends TwoFactorTests {
  TempVerify servlet;

  @Before
  public void setUp() {
    servlet = new TempVerify(managerFactory, keyFactory);
  }

  // generates 2 random 9-digit backup codes, asserts they are not equal
  @Test
  public void testgenerateBackupCodes() {
    String a = CryptoForBackupCodes.generateBackupCode();
    String b = CryptoForBackupCodes.generateBackupCode();
    assertTrue(!(a.equals(b)));
  }

  @Test
  public void testDoPostSuccess() throws CryptoError, ServletException, IOException {
    String output = testDoPost(twoFactorData.validTimeCode);
    assertThat(output, containsString("successfully"));
    // TODO: Check DB state?
  }

  @Test
  public void testDoPostFailure() throws CryptoError, ServletException, IOException {
    // create incorrect code
    final String wrongCode = "0123456789";
    String output = testDoPost(wrongCode);
    assertThat(output, containsString("incorrect or expired"));
  }

  public String testDoPost(String code) throws CryptoError, ServletException,
      IOException {
    replaceDefaultManagerDbForTest();
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    // create token and sign it
    String testToken = makeTokenString(testIdentity.getName());
    String testSignature = testIdentityKey.sign(testToken);
    //set needed parameters
    request.setParameter("token", testToken);
    request.setParameter("signature", testSignature);
    request.setParameter("code", code);
    request.setParameter("secret", twoFactorData.testSecret);
    servlet.doPost(request, response);
    return response.getOutput();
  }

  @Test
  public void testEmailHarvesting() throws ServletException, IOException {
    doEmailHarvestingTest(servlet);
  }
}
