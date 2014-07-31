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

import static co.mitro.test.Assert.assertExceptionMessage;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Callable;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import co.mitro.core.crypto.KeyInterfaces.PrivateKeyInterface;
import co.mitro.core.exceptions.InvalidRequestException;
import co.mitro.core.exceptions.MitroServletException;
import co.mitro.core.exceptions.UserExistsException;
import co.mitro.core.server.data.DBAcl;
import co.mitro.core.server.data.DBEmailQueue;
import co.mitro.core.server.data.DBGroup;
import co.mitro.core.server.data.DBIdentity;
import co.mitro.core.server.data.RPC;
import co.mitro.core.servlets.MitroServlet.MitroRequestContext;
import co.mitro.test.MockHttpServletRequest;
import co.mitro.test.MockHttpServletResponse;

import com.google.common.collect.Lists;

public class AddIdentityTest extends MemoryDBFixture {
  private AddIdentity servlet;
  private RPC.AddIdentityRequest request;

  private int counter = 42;
  private String getUniqueEmail() {
    String out = "user" + counter + "@example.com";
    counter += 1;
    return out;
  }

  @Before
  public void setUp() {
    // Required for testing signature verification; doPost creates the Manager directly
    replaceDefaultManagerDbForTest();
    servlet = new AddIdentity(managerFactory, keyFactory);

    request = new RPC.AddIdentityRequest();
    request.encryptedPrivateKey = "private key";
    request.publicKey = "pub key";
    request.deviceId = DEVICE_ID;
  }

  @Test(expected=UserExistsException.class)
  public void testAlreadyExists() throws IOException, SQLException, MitroServletException {
    // this identity already exists: expected to fail
    request.userId = testIdentity.getName();
    servlet.processCommand(new MitroRequestContext(null, gson.toJson(request), manager, null));
  }

  @Test
  public void testSuccess() throws IOException, SQLException, MitroServletException {
    request.userId = getUniqueEmail();
    RPC.AddIdentityResponse r = runAndVerifyAddIdentity();
    assertEquals(null, r.privateGroupId);
  }

  @Test
  public void testAutoGroupCreation() throws IOException, SQLException, MitroServletException {
    request.userId = getUniqueEmail();
    request.groupKeyEncryptedForMe = "groupKeyForMe";
    request.groupPublicKey = "group public key";
    RPC.AddIdentityResponse r = runAndVerifyAddIdentity();
    assertNotNull(r.privateGroupId);
    DBGroup group = manager.groupDao.queryForId(r.privateGroupId);
    
    assertEquals("", group.getName());
    List<DBAcl> acl = Lists.newArrayList(group.getAcls());
    assertEquals(request.groupPublicKey, group.getPublicKeyString());
    assertEquals(false, group.isAutoDelete());
    assertEquals(1, acl.size());
    assertEquals(request.groupKeyEncryptedForMe, acl.get(0).getGroupKeyEncryptedForMe());
    assertEquals(DBAcl.AccessLevelType.ADMIN, acl.get(0).getLevel());
    DBIdentity i = acl.get(0).getMemberIdentityId();
    manager.identityDao.refresh(i);
    assertEquals(request.userId, i.getName());
  }

  @Test
  public void testBadEmails() throws Exception {
    Callable<Void> tryAdd = new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        runAndVerifyAddIdentity();
        return null;
      }
    };

    request.userId = "someone@example.com ";
    assertExceptionMessage("not a valid email address", InvalidRequestException.class, tryAdd);
  }

  @Test
  @Ignore
  public void testEmailsAreNormalized() throws Exception {
    // TODO: Normalize email addresses throughout the entire system!
    request.userId = "Capitalized@Example.com";
    servlet.processCommand(new MitroRequestContext(null, gson.toJson(request), manager, null));

    // user is created with the normalized address
    assertNull(DBIdentity.getIdentityForUserName(manager, request.userId));
    String normalized = Util.normalizeEmailAddress(request.userId);
    DBIdentity i = DBIdentity.getIdentityForUserName(manager, normalized);
    assertEquals(normalized, i.getName());
  }

  protected RPC.AddIdentityResponse runAndVerifyAddIdentity()
      throws IOException, SQLException, MitroServletException {
    RPC.MitroRPC response = servlet.processCommand(new MitroRequestContext(null, gson.toJson(request), manager, null));
    RPC.AddIdentityResponse r = (RPC.AddIdentityResponse) response;
    assertFalse(r.verified);
    
    DBIdentity i = DBIdentity.getIdentityForUserName(manager, request.userId);
    assertFalse(i.isVerified());
    
    // Check the validation message
    List<DBEmailQueue> emails = manager.emailDao.queryForAll();
    assertEquals(1, emails.size());
    assertEquals(DBEmailQueue.Type.ADDRESS_VERIFICATION, emails.get(0).getType());
    assertEquals(request.userId, emails.get(0).getArguments()[0]);
    assertEquals(i.getVerificationUid(), emails.get(0).getArguments()[1]);
    return r;
  }
  
  
  @Test
  public void testSignatureVerification() throws Exception {
    // Failed request: signature does not verify
    request.userId = getUniqueEmail();
    RPC.SignedRequest r = new RPC.SignedRequest();
    r.identity = request.userId;
    r.request = gson.toJson(request);

    MockHttpServletRequest httpRequest = new MockHttpServletRequest();
    httpRequest.setRequestBody(gson.toJson(r).getBytes("UTF-8"));
    MockHttpServletResponse httpResponse = new MockHttpServletResponse();
    servlet.doPost(httpRequest, httpResponse);

    RPC.MitroException exception =
        gson.fromJson(httpResponse.getOutput(), RPC.MitroException.class);
    // // DO NOT REMOVE! old versions of extensions expect reasons to have size == 1.
    assertEquals(1, exception.reasons.size());
    assertThat(exception.userVisibleError, containsString("Error"));

    // successful request: valid signature
    PrivateKeyInterface key = keyFactory.generate();
    request.publicKey = key.exportPublicKey().toString();
    r.request = gson.toJson(request);
    r.signature = key.sign(r.request);

    httpRequest.setRequestBody(gson.toJson(r).getBytes("UTF-8"));
    httpResponse = new MockHttpServletResponse();
    servlet.doPost(httpRequest, httpResponse);

    RPC.AddIdentityResponse r2 =
        gson.fromJson(httpResponse.getOutput(), RPC.AddIdentityResponse.class);
    assertFalse(r2.verified);

    DBIdentity i = DBIdentity.getIdentityForUserName(manager, request.userId);
    assertNotNull(i);

    // verify the login token
    RPC.LoginToken lt = gson.fromJson(r2.unsignedLoginToken,
        RPC.LoginToken.class);
    assertEquals(lt.email, i.getName());
    assertEquals(lt.email, request.userId);
    
    // make sure this token can be used to log in again.
    RPC.GetMyPrivateKeyRequest pvtRequest = new RPC.GetMyPrivateKeyRequest();
    
    // 1. Test null signature
    pvtRequest.loginToken = r2.unsignedLoginToken;
    pvtRequest.userId = request.userId;
    processLoginFailure(pvtRequest);

    // 1. Test bad signature
    pvtRequest.loginToken = r2.unsignedLoginToken;
    pvtRequest.loginTokenSignature = key.sign(r2.unsignedLoginToken) + "bad";
    pvtRequest.userId = request.userId;
    processLoginFailure(pvtRequest);

    // 1. Test bad token
    pvtRequest.loginToken = r2.unsignedLoginToken;
    pvtRequest.loginToken = pvtRequest.loginToken.replace(request.userId, "adifferentuser@example.com");
    pvtRequest.loginTokenSignature = key.sign(pvtRequest.loginToken);
    pvtRequest.userId = request.userId;
    processLoginFailure(pvtRequest);
 
    // finally, do this correctly.
    pvtRequest.loginToken = r2.unsignedLoginToken;
    pvtRequest.loginTokenSignature = key.sign(pvtRequest.loginToken);
    pvtRequest.userId = request.userId;
    pvtRequest.deviceId = DEVICE_ID;
    GetMyPrivateKey pvtServlet = new GetMyPrivateKey(managerFactory, keyFactory);
    pvtServlet.processCommand(new MitroRequestContext(null, gson.toJson(pvtRequest), manager, null));
  }

  protected void processLoginFailure(RPC.GetMyPrivateKeyRequest pvtRequest)
      throws IOException, SQLException {
    try {
      GetMyPrivateKey pvtServlet = new GetMyPrivateKey(managerFactory, keyFactory);
      pvtServlet.processCommand(new MitroRequestContext(null, gson.toJson(pvtRequest), manager, null));
      fail("this should have thrown an exception");
    } catch (MitroServletException ignored) {
      ;
    }
  }
}
