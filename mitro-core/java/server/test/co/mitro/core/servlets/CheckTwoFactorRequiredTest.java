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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.sql.SQLException;

import org.junit.Before;
import org.junit.Test;
import org.keyczar.exceptions.KeyczarException;

import co.mitro.core.exceptions.DoTwoFactorAuthException;
import co.mitro.core.server.data.RPC;
import co.mitro.core.servlets.MitroServlet.MitroRequestContext;
import co.mitro.twofactor.TwoFactorSigningService;

public class CheckTwoFactorRequiredTest extends MemoryDBFixture {
  MitroRequestContext testContext;
  RPC.TwoFactorAuthRequest testRequest;
  CheckTwoFactorRequired servlet;

  @Before
  public void setup() throws KeyczarException, SQLException {
    servlet = new CheckTwoFactorRequired(managerFactory, keyFactory);

    String testToken1 = GetMyPrivateKey.makeLoginTokenString(testIdentity, "extensionID", DEVICE_ID);
    RPC.LoginToken tokenInGson = gson.fromJson(testToken1, RPC.LoginToken.class);
    tokenInGson.twoFactorAuthVerified = true;
    testRequest = new RPC.TwoFactorAuthRequest();
    testRequest.tfaToken = gson.toJson(tokenInGson);
    testRequest.tfaSignature = TwoFactorSigningService.signToken(testRequest.tfaToken);

    testIdentity.setTwoFactorSecret("12313123");
    testContext = new MitroRequestContext(testIdentity, testRequest.tfaToken, manager, "url");
  }

  @Test
  public void testCheckTwoFactorSecret() throws Exception {
    assertTrue(CheckTwoFactorRequired.checkTwoFactorSecret(testContext, testRequest));
  }

  @Test(expected=DoTwoFactorAuthException.class)
  public void testCheckTwoFactorSecretFail() throws DoTwoFactorAuthException {
    testRequest.tfaSignature += "A";
    CheckTwoFactorRequired.checkTwoFactorSecret(testContext, testRequest);
  }

  @Test
  public void testProcessCommandWithTwoFactor() throws Exception {
    RPC.CheckTwoFactorRequiredResponse response =
        (RPC.CheckTwoFactorRequiredResponse) servlet.processCommand(testContext);
    assertThat(response.twoFactorUrl, containsString("url/mitro-core/TwoFactorAuth?token="));
  }

  @Test
  public void testProcessCommandNoTwoFactor() throws Exception {
    testContext = new MitroRequestContext(testIdentity2, testRequest.tfaToken, manager, "url");
    RPC.CheckTwoFactorRequiredResponse response =
        (RPC.CheckTwoFactorRequiredResponse) servlet.processCommand(testContext);
    assertNull(response.twoFactorUrl);
  }
}
