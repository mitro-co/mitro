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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
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

import co.mitro.core.crypto.KeyInterfaces.CryptoError;
import co.mitro.core.server.data.DBIdentity;
import co.mitro.test.MockHttpServletRequest;
import co.mitro.test.MockHttpServletResponse;
import co.mitro.twofactor.UserSignedTwoFactorServlet.TokenData;

public class TFAPreferencesTest extends TwoFactorTests {
  TFAPreferences servlet;

  @Before
  public void setup() throws InvalidKeyException, NoSuchAlgorithmException,
      KeyczarException, SQLException {
    servlet = new TFAPreferences(managerFactory, keyFactory);
  }

  public MockHttpServletResponse testDoGet(String code, boolean tryDisable,
      boolean wrongTokenSignature, boolean tryEnable) throws ServletException,
      IOException, CryptoError {
    replaceDefaultManagerDbForTest();
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    String testToken;
    String testSignature;
    if (wrongTokenSignature) {
      //create incorrect token/signature
      testToken = "{}";
      testSignature = "wrong";
    } else {
      //create correct token/signature
      TokenData fakeToken = new TokenData();
      fakeToken.email = testIdentity.getName();
      fakeToken.nonce = "123456";
      testToken = gson.toJson(fakeToken);
      testSignature = testIdentityKey.sign(testToken);
    }
    if (tryDisable) {
      //set parameters to try to disable
      request.setParameter("disable", "true");
      request.setParameter("code", code);
    }
    //set rest of parameters
    request.setParameter("token", testToken);
    request.setParameter("signature", testSignature);
    servlet.doGet(request, response);
    return response;
  }

  //verify if 2fa is enabled
  @Test
  public void testDoGetIsEnabled() throws ServletException, IOException,
      InvalidKeyException, NoSuchAlgorithmException, SQLException,
      KeyczarException, CryptoError {
    MockHttpServletResponse response = testDoGet(null, false, false, false);
    assertThat(response.getOutput(), containsString("Enabled"));
    testIdentity = DBIdentity.getIdentityForUserName(manager, testIdentity.getName());
    assertTrue(testIdentity.getTwoFactorSecret() != null);
  }
    
  //fail trying to disable 2fa
  @Test
  public void testDoGetFailDisable() throws ServletException, IOException, CryptoError, SQLException {
    //set code to an incorrect code
    String code = "987654321";
    assertTrue(testIdentity.getTwoFactorSecret() != null);
    MockHttpServletResponse response = testDoGet(code, true, false, false);
    assertThat(response.getOutput(),
        containsString("The verification code you entered was incorrect"));
    testIdentity = DBIdentity.getIdentityForUserName(manager, testIdentity.getName());
    assertTrue(testIdentity.getTwoFactorSecret() != null);
  }
  
  //disable 2fa successfully
  @Test
  public void testDoGetSuccessDisable() throws ServletException, IOException, CryptoError, SQLException {
    //set code correctly
    assertTrue(testIdentity.getTwoFactorSecret().length() > 0);
    MockHttpServletResponse response = testDoGet(
        twoFactorData.validTimeCode, true, false, false);
    assertThat(response.getOutput(), containsString("Two Factor Authentication Disabled"));
    testIdentity = DBIdentity.getIdentityForUserName(manager, testIdentity.getName());
    assertNull(testIdentity.getTwoFactorSecret());
  }
  
  //verify 2fa is disabled
  @Test
  public void testDoGetIsDisabled() throws SQLException, CryptoError,
      ServletException, IOException, InvalidKeyException,
      NoSuchAlgorithmException {
    testIdentity.setTwoFactorSecret(null); //set secret to null to ensure 2fa is disabled. the existence of a secret determines whether 2fa is enabled
    manager.identityDao.update(testIdentity);
    manager.commitTransaction();
    MockHttpServletResponse response = testDoGet(null, false, false, false);
    assertThat(response.getOutput(),
        containsString("Two Factor Authentication Disabled"));
    testIdentity = DBIdentity.getIdentityForUserName(manager, testIdentity.getName());
    assertFalse(testIdentity.getTwoFactorSecret() != null);
  }

  //check that wrong token throws an exception
  @Test(expected=NullPointerException.class)
  public void testDoGetWrongToken() throws ServletException, IOException, CryptoError {
    testDoGet(null, false, true, false);
  }

  @Test
  public void testEmailHarvesting() throws ServletException, IOException {
    doEmailHarvestingTest(servlet);
  }
}
