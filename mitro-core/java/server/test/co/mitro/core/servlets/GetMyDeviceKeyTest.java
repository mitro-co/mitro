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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;

import co.mitro.core.exceptions.DoEmailVerificationException;
import co.mitro.core.server.data.RPC.GetMyDeviceKeyRequest;
import co.mitro.core.server.data.RPC.GetMyDeviceKeyResponse;
import co.mitro.core.server.data.RPC.GetMyPrivateKeyResponse;

public class GetMyDeviceKeyTest extends MemoryDBFixture {
  private GetMyDeviceKey servlet;

  @Before
  public void setUp() {
    servlet = new GetMyDeviceKey();
  }

  @Test
  public void testDeviceKey() throws Exception {
    GetMyDeviceKeyRequest request = new GetMyDeviceKeyRequest();
    // send a request from an authenticated user to get a device key
    request.deviceId = "Device1";
    MitroServlet.MitroRequestContext params = 
        new MitroServlet.MitroRequestContext(testIdentity, gson.toJson(request), manager, null);
    GetMyDeviceKeyResponse response = (GetMyDeviceKeyResponse)servlet.processCommand(params);
    assertNotNull(response.deviceKeyString);

    // TODO: Merge these tests into the same class
    GetMyPrivateKey getPrivateKeyServlet = new GetMyPrivateKey(managerFactory, keyFactory);
    // try to get this key without a login token. This should fail to return the key we set earlier
    GetMyPrivateKeyResponse keyResponse = GetMyPrivateKeyTest.tryLogin(
        getPrivateKeyServlet, testIdentity, manager, "Device1", null, null, null, false);
    assertNull(keyResponse.deviceKeyString);

    // try to get this key with a login token. Should succeed
    keyResponse = GetMyPrivateKeyTest.tryLogin(getPrivateKeyServlet, testIdentity, manager,
        "Device1", testIdentityLoginToken, testIdentityLoginTokenSignature, null, false);
    assertEquals(response.deviceKeyString, keyResponse.deviceKeyString);

    // try to get this key with a login token but for a previously unknown device
    // this should result in a request to do email verification
    try {
      GetMyPrivateKeyTest.tryLogin(getPrivateKeyServlet, testIdentity, manager, "Device2",
          testIdentityLoginToken, testIdentityLoginTokenSignature, null, false);
      fail("This should have thrown an exception");
    } catch (DoEmailVerificationException e) {
      ;
    }
    
    authorizeIdentityForDevice(testIdentity, "Device2");
    
    // after authorizing device2, we should get a different key
    String key = GetMyPrivateKeyTest.tryLogin(getPrivateKeyServlet, testIdentity, manager,
        "Device2", testIdentityLoginToken, testIdentityLoginTokenSignature, null, false).deviceKeyString;
    // make sure this is a new key
    assert (!key.equals(response.deviceKeyString));
  }
}
