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
package co.mitro.core.servlets;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.junit.Before;
import org.junit.Test;
import org.keyczar.exceptions.KeyczarException;

import co.mitro.core.exceptions.MitroServletException;
import co.mitro.core.server.data.DBIdentity;
import co.mitro.core.server.data.RPC;
import co.mitro.test.MockHttpServletRequest;
import co.mitro.test.MockHttpServletResponse;
import co.mitro.twofactor.TwoFactorSigningService;

public class VerifyDeviceServletTest extends MemoryDBFixture {
  private MockHttpServletRequest request;
  private MockHttpServletResponse response;
  private VerifyDeviceServlet servlet;
  private final static String PLATFORM = "TEST";
  @Before
  public void setUp() {
    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();
    servlet = new VerifyDeviceServlet(managerFactory, keyFactory);
  }

  private void setToken(DBIdentity identity, String deviceId) throws KeyczarException {
    request.setParameter("token", GetMyPrivateKey.makeLoginTokenString(
        identity, "extensionId", deviceId));
    request.setParameter("token_signature",
        TwoFactorSigningService.signToken(request.getParameter("token")));
  }

  private void assertDoGetSuccess(String deviceId) throws SQLException, MitroServletException,
      ServletException, IOException {
    // deviceId not registered before
    assertNull(GetMyDeviceKey.maybeGetClientKeyForLogin(manager, testIdentity, deviceId, PLATFORM));

    servlet.doGet(request, response);
    assertEquals(HttpServletResponse.SC_FOUND, response.getStatus());
    assertEquals(VerifyDeviceServlet.SUCCESS_DESTINATION, response.getHeader("Location"));

    // deviceId is valid
    assertTrue(GetMyDeviceKey.maybeGetClientKeyForLogin(
        manager, testIdentity, deviceId, PLATFORM).length() > 0);
  }

  @Test
  public void mismatchedUser() throws Exception {
    // user parameter doesn't match the user in the token: error
    request.setParameter("user", testIdentity2.getName());
    setToken(testIdentity, "deviceId2");

    // deviceId2 not registered before
    assertNull(GetMyDeviceKey.maybeGetClientKeyForLogin(manager, testIdentity, "deviceId2", PLATFORM));
    assertNull(GetMyDeviceKey.maybeGetClientKeyForLogin(manager, testIdentity2, "deviceId2", PLATFORM));
    try {
      servlet.doGet(request, response);
      fail("expected exception");
    } catch (ServletException ignored) {}

    // deviceId2 still not registered
    assertNull(GetMyDeviceKey.maybeGetClientKeyForLogin(manager, testIdentity, "deviceId2", PLATFORM));
    assertNull(GetMyDeviceKey.maybeGetClientKeyForLogin(manager, testIdentity2, "deviceId2", PLATFORM));
  }

  @Test
  public void success() throws Exception {
    setToken(testIdentity, "deviceId3");
    assertDoGetSuccess("deviceId3");
  }

  @Test
  public void successWithUser() throws Exception {
    // user parameter is optional (for backwards compatibility for now)
    request.setParameter("user", testIdentity.getName());
    setToken(testIdentity, "deviceId");
    assertDoGetSuccess("deviceId");
  }

  @Test
  public void verifyDeviceVerifiesEmail() throws Exception {
    testIdentity.setVerified(false);
    manager.identityDao.update(testIdentity);
    manager.commitTransaction();

    setToken(testIdentity, "deviceId4");
    assertDoGetSuccess("deviceId4");

    manager.identityDao.refresh(testIdentity);
    assertTrue(testIdentity.isVerified());
  }

  @Test
  public void timeoutError() throws Exception {
    setExpiredToken();

    servlet.doGet(request, response);
    assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatus());
    assertThat(response.getOutput(), containsString("link is too old"));
    assertThat(response.getOutput(), not(containsString("link is not valid")));

    // An invalid signature AND expired token reports the same error to the user
    setExpiredToken();
    request.setParameter("token_signature", "bad signature");

    servlet.doGet(request, response);
    assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatus());
    assertThat(response.getOutput(), containsString("link is too old"));
    assertThat(response.getOutput(), not(containsString("link is not valid")));
  }

  /** Sets a correctly signed but expired token on request. */
  private void setExpiredToken() throws KeyczarException {
    // create a token and modify the timestamp to be a timeout
    RPC.LoginToken lt = new RPC.LoginToken();
    lt.email = testIdentity.getName();
    lt.extensionId = "extensionId";
    lt.timestampMs = System.currentTimeMillis() - VerifyDeviceServlet.VALIDITY_TIMEOUT_MS;
    lt.deviceId = "deviceId";
    request.setParameter("token", gson.toJson(lt));
    request.setParameter("token_signature",
        TwoFactorSigningService.signToken(request.getParameter("token")));
  }

  @Test
  public void badSignatureError() throws Exception {
    setToken(testIdentity, "deviceId6");
    request.setParameter("token_signature", "bad signature");

    servlet.doGet(request, response);
    assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatus());
    assertThat(response.getOutput(), not(containsString("link is too old")));
    assertThat(response.getOutput(), containsString("link is not valid"));
  }
}
