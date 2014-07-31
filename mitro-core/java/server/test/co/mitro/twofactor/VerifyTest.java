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
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;

import javax.servlet.ServletException;

import org.junit.Before;
import org.junit.Test;
import org.keyczar.exceptions.KeyczarException;

import co.mitro.test.MockHttpServletRequest;
import co.mitro.test.MockHttpServletResponse;

public class VerifyTest extends TwoFactorTests {
  Verify servlet;

  @Before
  public void setupVerifyTest() {
    servlet = new Verify(managerFactory, keyFactory);
  }

  @Test
  public void testDoPostWithCode() throws ServletException, IOException {
    // test doPost with a valid time generated code
    testDoPost(twoFactorData.validTimeCode);
  }

  @Test
  public void testDoPostWithBackup() throws ServletException, IOException {
    // test doPost with a backup code
    testDoPost(twoFactorData.backupCode);
  }

  // testWrongCode
  @Test
  public void testDoPostWrongCode() throws SQLException, KeyczarException,
      InvalidKeyException, NoSuchAlgorithmException, ServletException,
      IOException {
    //create a wrong code
    String wrongCode = "1234567890";
    boolean failure = false;
    try {
      testDoPost(wrongCode);// testDoPost with a wrong code
    } catch (AssertionError e) {
      failure = true;// when code is wrong, failure becomes true, which is what
                     // we want
    }
    assertTrue(failure);
  }

  // used by testDoPostWithCode() and testDoPostWithBackup(), with different
  // code parameters
  public void testDoPost(String code) throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    // sets token and signature to the generated testToken and testSignature.
    // sets code to the given parameter.
    request.setParameter("token", twoFactorData.testToken);
    request.setParameter("signature", twoFactorData.testSignature);
    request.setParameter("code", code);
    servlet.doPost(request, response);
    // checks that the servlet redirected to the redirect String because the
    // code was correct.
    // Checks that "true" is in the URL because the token passed in should have
    // parameter twoFactorAuthVerified set to true
    assertThat(response.getHeader("Location"), containsString(twoFactorData.redirectUrl));
    assertThat(response.getHeader("Location"), containsString("true"));
  }

  @Test
  public void testEmailHarvesting() throws ServletException, IOException {
    doEmailHarvestingTest(servlet);
  }
}
