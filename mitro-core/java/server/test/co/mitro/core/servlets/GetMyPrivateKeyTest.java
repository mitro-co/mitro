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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.sql.SQLException;

import org.junit.Before;
import org.junit.Test;

import co.mitro.core.exceptions.DoEmailVerificationException;
import co.mitro.core.exceptions.DoTwoFactorAuthException;
import co.mitro.core.exceptions.MitroServletException;
import co.mitro.core.server.Manager;
import co.mitro.core.server.data.DBDeviceSpecificInfo;
import co.mitro.core.server.data.DBIdentity;
import co.mitro.core.server.data.RPC.GetMyPrivateKeyRequest;
import co.mitro.core.server.data.RPC.GetMyPrivateKeyResponse;
import co.mitro.core.servlets.MitroServlet.MitroRequestContext;
import co.mitro.twofactor.TwoFactorTests;

public class GetMyPrivateKeyTest extends TwoFactorTests {
  GetMyPrivateKey servlet;

  @Before
  public void setUp() throws SQLException {
    servlet = new GetMyPrivateKey(managerFactory, keyFactory);
    testIdentity2.setEncryptedPrivateKeyString("encrypted private key");
    manager.identityDao.update(testIdentity2);
  }

  // TODO: Merge GetMyPrivateKeyTest and GetMyDeviceKeyTest, make this private
  public static GetMyPrivateKeyResponse tryLogin(GetMyPrivateKey servlet, DBIdentity identity,
      Manager manager, String deviceId, String loginToken, String loginTokenSignature,
      String twoFactorCode, boolean automatic) throws IOException, SQLException, MitroServletException {
    GetMyPrivateKeyRequest request = new GetMyPrivateKeyRequest();
    request.userId = identity.getName();
    request.deviceId = deviceId;
    request.loginToken = loginToken;
    request.loginTokenSignature = loginTokenSignature;
    request.twoFactorCode = twoFactorCode;
    request.automatic = automatic;
    return (GetMyPrivateKeyResponse) servlet.processCommand(
        new MitroRequestContext(null, gson.toJson(request), manager, null));
  }

  private GetMyPrivateKeyResponse tryLogin(DBIdentity identity, Manager manager,
      String deviceId, String loginToken, String loginTokenSignature, String twoFactorCode, boolean automatic)
      throws IOException, SQLException, MitroServletException {
    return tryLogin(servlet, identity, manager, deviceId, loginToken, loginTokenSignature,
        twoFactorCode, automatic);
  }

  @Test
  public void simpleLoginWithoutTwoFactorAuth() throws Exception {
    // returns the private key but no device key
    GetMyPrivateKeyResponse response = tryLogin(testIdentity2, manager, DEVICE_ID, null, null, null, false);
    assertEquals(testIdentity2.getEncryptedPrivateKeyString(), response.encryptedPrivateKey);
    assertFalse(response.changePasswordOnNextLogin);
    assertThat(response.unsignedLoginToken, containsString(testIdentity2.getName()));
    assertTrue(response.verified);
    assertNull(response.deviceKeyString);

    String deprecatedMyId = response.myUserId;
    assertEquals(testIdentity2.getName(), deprecatedMyId);
  }

  @Test
  public void invitedUserLogin() throws Exception {
    // Change password on login means invited user; we must give them the private key
    // TODO: we should probably pass some signed token from the invitation email?
    testIdentity2.setChangePasswordOnNextLogin(true);
    // delete existing devices for testIdentity2
    manager.deviceSpecificDao.delete(manager.deviceSpecificDao.queryForEq(
        DBDeviceSpecificInfo.IDENTITY_FIELD_NAME, testIdentity2.getId()));
    manager.identityDao.update(testIdentity2);

    // returns the private key but no device key
    GetMyPrivateKeyResponse response = tryLogin(testIdentity2, manager, "newdevice", null, null, null, false);
    assertEquals(testIdentity2.getEncryptedPrivateKeyString(), response.encryptedPrivateKey);
    assertTrue(response.changePasswordOnNextLogin);
    assertThat(response.unsignedLoginToken, containsString(testIdentity2.getName()));
    assertTrue(response.verified);
    assertNull(response.deviceKeyString);

    // this also works for a second new device
    // TODO: Limit this to N devices? Previously was limited to 1, but I suspect some users
    // ran into trouble (e.g. click on one, don't change password, click on another, get error)
    tryLogin(testIdentity2, manager, "anotherdevice", null, null, null, false);

    // Reset the "change password" flag; next request still returns the key because the device is registered
    testIdentity2.setChangePasswordOnNextLogin(false);
    manager.identityDao.update(testIdentity2);
    response = tryLogin(testIdentity2, manager, "newdevice", null, null, null, false);
    assertEquals(testIdentity2.getEncryptedPrivateKeyString(), response.encryptedPrivateKey);
  }

  @Test
  public void checkNoEmailOnAutomaticLoginAttempt() throws Exception {
    long old = manager.emailDao.countOf();
    // try authenticating with an unknown device, non automatic. 
    // it should send an email
    try {
      tryLogin(testIdentity, manager,
          "Device1", testIdentityLoginToken, testIdentityLoginTokenSignature, null, false);
      fail("should have thrown");
    } catch (DoEmailVerificationException e) {
      assertEquals(++old, manager.emailDao.countOf());
    }
    try {
      tryLogin(testIdentity, manager,
          "Device1", testIdentityLoginToken, testIdentityLoginTokenSignature, null, true);
      fail("should have thrown");
    } catch (DoEmailVerificationException e) {
      // no new email sent here.
      assertEquals(old, manager.emailDao.countOf());
    }
  }

  // TODO: Merge with simpleLoginWithoutTwoFactorAuth
  @Test
  public void loginWithoutTwoFactorAuth() throws Exception {
    try {
      tryLogin(testIdentity2, manager, null, null, null, null, false);
      fail("should have thrown");
    } catch (DoEmailVerificationException e) {
      ;
    }
    try {
      tryLogin(testIdentity2, manager, "dev2", null, null, null, false);
      fail("should have thrown");
    } catch (DoEmailVerificationException e) {
      ;
    }
    authorizeIdentityForDevice(testIdentity2, "dev2");
    tryLogin(testIdentity2, manager, "dev2", null, null, null, false);
  }

  private GetMyPrivateKeyResponse getPrivateKeyWith2FA(String code)
      throws IOException, SQLException, MitroServletException {
    return tryLogin(testIdentity, manager, DEVICE_ID, null, null, code, false);
  }

  @Test
  public void twoFactorLoginWithCorrectCode() throws Exception {
    assertNotNull(twoFactorData.validTimeCode);
    GetMyPrivateKeyResponse response = getPrivateKeyWith2FA(twoFactorData.validTimeCode);
    assertEquals(testIdentity.getEncryptedPrivateKeyString(), response.encryptedPrivateKey);
  }

  @Test(expected=DoTwoFactorAuthException.class)
  public void twoFactorLoginWithIncorrectCode() throws Exception {
    // TODO: 7 chars so this cannot equal a valid code or backup code
    final String BADCODE = "0123456";
    // fails with DoTwoFactorAuthException
    getPrivateKeyWith2FA(BADCODE);
  }

  @Test(expected=DoTwoFactorAuthException.class)
  public void twoFactorLoginWithoutCode() throws Exception {
    // fails with DoTwoFactorAuthException
    tryLogin(testIdentity, manager, DEVICE_ID, null, null, null, false);
  }
}
