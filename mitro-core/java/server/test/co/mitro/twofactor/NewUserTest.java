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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.junit.Before;
import org.junit.Test;
import org.keyczar.exceptions.KeyczarException;

import co.mitro.core.crypto.KeyInterfaces.CryptoError;
import co.mitro.test.MockHttpServletRequest;
import co.mitro.test.MockHttpServletResponse;
import co.mitro.twofactor.UserSignedTwoFactorServlet.TokenData;

public class NewUserTest extends TwoFactorTests {
  private NewUser servlet;
  private String tokenString;

  @Before
  public void setUp() throws InvalidKeyException, NoSuchAlgorithmException,
      KeyczarException, SQLException {
    servlet = new NewUser(managerFactory, keyFactory);
    TokenData token = new UserSignedTwoFactorServlet.TokenData();
    token.email = testIdentity.getName();
    token.nonce = "123456";
    tokenString = gson.toJson(token);
  }

  @Test
  public void testGenerateSecretKey() {
    String v1 = TwoFactorSecretGenerator.generateSecretKey();
    String v2 = TwoFactorSecretGenerator.generateSecretKey();
    assertTrue(!v1.equals(v2));
  }

  @Test(expected=ServletException.class)
  public void testDoPostFailure() throws ServletException, IOException, CryptoError {
    // create incorrect signature
    String badSignature = testIdentityKey.sign("WRONG");
    testDoPost(badSignature, tokenString);
  }

  @Test
  public void testDoPostSuccess() throws ServletException, IOException, CryptoError {
    // create correct token and sign it
    String tokenSignature = testIdentityKey.sign(tokenString);
    MockHttpServletResponse response = testDoPost(tokenSignature, tokenString);
    assertEquals(HttpServletResponse.SC_OK, response.getStatus());
    // assert that the template was rendered
    assertThat(response.getOutput(),
        containsString("Once you have scanned the barcode, enter the 6-digit code below"));
  }

  public MockHttpServletResponse testDoPost(String signature, String token)
      throws ServletException, IOException, CryptoError {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    // set needed parameters
    request.setParameter("token", token);
    request.setParameter("signature", signature);
    servlet.doPost(request, response);
    return response;
  }

  @Test
  public void testEmailHarvesting() throws ServletException, IOException {
    doEmailHarvestingTest(servlet);
  }
}
