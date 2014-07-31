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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.keyczar.exceptions.KeyczarException;

import co.mitro.core.exceptions.DoTwoFactorAuthException;
import co.mitro.core.exceptions.InvalidRequestException;
import co.mitro.core.exceptions.MitroServletException;
import co.mitro.core.server.data.DBDeviceSpecificInfo;
import co.mitro.core.server.data.DBIdentity;
import co.mitro.core.server.data.RPC;
import co.mitro.core.servlets.MitroServlet.MitroRequestContext;
import co.mitro.twofactor.TwoFactorSigningService;
import co.mitro.twofactor.TwoFactorTests;

import com.google.gson.Gson;

public class EditEncryptedPrivateKeyTest extends TwoFactorTests {
  private static final Gson gson = new Gson();

  private EditEncryptedPrivateKey servlet;
  private RPC.EditEncryptedPrivateKeyRequest testReq;

  @Before
  public void setup() throws InvalidKeyException, NoSuchAlgorithmException, KeyczarException, SQLException {
    servlet = new EditEncryptedPrivateKey();
    testReq = new RPC.EditEncryptedPrivateKeyRequest();
  }
  
  //TO be used by the other tests
  public void testProcessCommand(DBIdentity identity)
      throws InvalidKeyException, NoSuchAlgorithmException, KeyczarException,
      SQLException, MitroServletException {
    testReq.deviceId = DEVICE_ID;

    //this is using a made up new private key
    testReq.encryptedPrivateKey ="ASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALFASDKJHAFKJSDHFSSDKJALSKDJALF";
    String testRequest = gson.toJson(testReq);
    MitroRequestContext testContext = new MitroRequestContext(identity,
        testRequest, manager, null);
    servlet.processCommand(testContext);
  }

  // 2fa is enabled, but there is no token ("buggy" edit request from extension)
  @Test(expected=DoTwoFactorAuthException.class)
  public void testTwoFactorEnabledNotVerified() throws InvalidKeyException,
      NoSuchAlgorithmException, KeyczarException, SQLException, MitroServletException {
    // extension should check if 2FA is enabled, but we had a bug where this didn't happen
    testReq.encryptedPrivateKey = "some encrypted key";
    testProcessCommand(testIdentity);
  }

  // 2fa enabled, but this is a login token, not a 2FA token
  @Test(expected=DoTwoFactorAuthException.class)
  public void twoFactorLoginTokenNotTwoFactorToken() throws InvalidKeyException,
      NoSuchAlgorithmException, KeyczarException, SQLException, MitroServletException {
    testReq.tfaSignature = twoFactorData.testSignature;
    testReq.tfaToken = twoFactorData.testToken;
    testProcessCommand(testIdentity);
  }

  //successful. with 2fa enabled.
  @Test
  public void testTwoFactorEnabledVerified() throws InvalidKeyException, NoSuchAlgorithmException, KeyczarException, SQLException, MitroServletException {
    RPC.LoginToken useToken = gson.fromJson(twoFactorData.testToken, RPC.LoginToken.class);
    assertFalse(useToken.twoFactorAuthVerified);
    useToken.twoFactorAuthVerified = true;
    testReq.tfaToken = gson.toJson(useToken);
    testReq.tfaSignature = TwoFactorSigningService.signToken(testReq.tfaToken);
    testProcessCommand(testIdentity);
  }

  //successful because 2fa is disabled and the correct parameters are passed in
  @Test
  public void testTwoFactorDisabledSuccess() throws KeyczarException, InvalidKeyException, NoSuchAlgorithmException, SQLException, MitroServletException {
    testProcessCommand(testIdentity2);
  }
  
  //throws InvalidRequestException because it is passing in the incorrect name
  @SuppressWarnings("deprecation")
  @Test(expected=InvalidRequestException.class)
  public void testDeprecatedUserId() throws KeyczarException, InvalidKeyException, NoSuchAlgorithmException, SQLException, MitroServletException {
    testReq.userId = testIdentity.getName(); //wrong name
    testProcessCommand(testIdentity2);
  }

  @Test
  public void testChangePasswordInvalidateDevices() throws Exception {
    // Create extra devices for this user
    for (int i = 0; i < 3; i++) {
      authorizeIdentityForDevice(testIdentity2, "extra_device" + i);
    }
    manager.commitTransaction();
    List<DBDeviceSpecificInfo> devices = manager.deviceSpecificDao.queryForEq(
        DBDeviceSpecificInfo.IDENTITY_FIELD_NAME, testIdentity2);
    assertEquals(4, devices.size());

    // servlet checks that key is at least 500 chars
    StringBuilder fakeKey = new StringBuilder();
    for (int i = 0; i < 500; i++) {
      fakeKey.append('k');
    }

    EditEncryptedPrivateKey servlet = new EditEncryptedPrivateKey();
    testReq.deviceId = DEVICE_ID;
    testReq.encryptedPrivateKey = fakeKey.toString();
    MitroRequestContext context = new MitroRequestContext(
        testIdentity2, gson.toJson(testReq), manager, null);
    RPC.EditEncryptedPrivateKeyResponse response =
        (RPC.EditEncryptedPrivateKeyResponse) servlet.processCommand(context);
    assertNotNull(response);

    devices = manager.deviceSpecificDao.queryForEq(
        DBDeviceSpecificInfo.IDENTITY_FIELD_NAME, testIdentity2);
    assertEquals(1, devices.size());
    assertEquals(DEVICE_ID, devices.get(0).getDeviceId());
  }
}
