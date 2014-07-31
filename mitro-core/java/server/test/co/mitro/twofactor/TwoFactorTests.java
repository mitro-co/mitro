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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.binary.Base32;
import org.junit.Before;

import co.mitro.core.servlets.GetMyPrivateKey;
import co.mitro.core.servlets.MemoryDBFixture;
import co.mitro.test.MockHttpServletRequest;
import co.mitro.test.MockHttpServletResponse;
import co.mitro.twofactor.TwoFactorServlet.HttpMethod;
import co.mitro.twofactor.UserSignedTwoFactorServlet.TokenData;

import com.google.gson.Gson;


/** Test fixture for two-factor authentication tests. See {@link #twoFactorTestSetup()}. */
public class TwoFactorTests extends MemoryDBFixture {
  /** Two-factor auth data for testIdentity. See {@link #twoFactorTestSetup()}. */
  protected TwoFactorTestData twoFactorData;

  protected static final class TwoFactorTestData {
    // secret of same length as real one.
    public final String testSecret = "L7W5Q63HY4EBYZTY";
    public final String redirectUrl = "chrome-extension://iifdnkifekacffipjffcjgiljadlhfkc";
    public String testToken;
    public String testSignature;
    public String validTimeCode;
    public String backupCode;
  }

  /** Enables two-factor auth for testIdentity, storing parameters in twoFactorData. */
  @Before
  public void twoFactorTestSetup() throws Exception {
    twoFactorData = new TwoFactorTestData();

    // set secret in DB means two factor auth is enabled
    testIdentity.setTwoFactorSecret(twoFactorData.testSecret);

    // create testToken and sign it
    twoFactorData.testToken =
        GetMyPrivateKey.makeLoginTokenString(testIdentity, twoFactorData.redirectUrl, null);
    twoFactorData.testSignature = TwoFactorSigningService.signToken(twoFactorData.testToken);

    // create code as if the google authenticator had
    Base32 codec = new Base32();
    byte[] decodedKey = codec.decode(twoFactorData.testSecret);
    long t = (System.currentTimeMillis() / 1000L) / 30L;
    twoFactorData.validTimeCode = Integer.toString(TwoFactorCodeChecker.computeHash(decodedKey, t));
    twoFactorData.backupCode = "123456789";
    byte[] salt = CryptoForBackupCodes.randSaltGen();
    testIdentity.setBackup(0, CryptoForBackupCodes.digest(twoFactorData.backupCode, salt));

    manager.identityDao.update(testIdentity);
    manager.commitTransaction();
  }

  protected String makeTokenString(String email) {
    TokenData token = new TokenData();
    token.email = email;
    // a fake nonce that works for testing
    token.nonce = "123456";
    return gson.toJson(token);
  }

  protected Exception makeRequestAndCatchException(TwoFactorServlet servlet, HttpServletRequest request, HttpServletResponse response) {
    try {
      if (servlet.getMethod() == HttpMethod.POST) {
        servlet.doPost(request, response);
      } else {
        servlet.doGet(request, response);
      }
    } catch (Exception e) {
      return e;
    }
    return null;
  }

  // Verify an identical response regardless of whether the email has a Mitro account or not.
  protected void doEmailHarvestingTest(TwoFactorServlet servlet) throws ServletException, IOException {
    replaceDefaultManagerDbForTest();
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    UserSignedTwoFactorServlet.TokenData token = new TokenData();
    token.email = "invalid@example.com";

    request.setParameter("token", new Gson().toJson(token));
    request.setParameter("signature", "FAKE_SIGNATURE");
    request.setParameter("secret", "FAKE_SECRET");
    request.setParameter("code", "123456");

    Exception e1 = makeRequestAndCatchException(servlet, request, response);
    assertNotNull(e1);

    token.email = this.testIdentity.getName();
    request.setParameter("token", new Gson().toJson(token));

    Exception e2 = makeRequestAndCatchException(servlet, request, response);
    assertNotNull(e2);

    assertTrue(e1.getClass().equals(e2.getClass()));
    assertEquals(e1.getMessage(), e2.getMessage());
  }
}
