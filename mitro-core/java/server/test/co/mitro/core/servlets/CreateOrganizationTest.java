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
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import co.mitro.core.accesscontrol.AuthenticatedDB;
import co.mitro.core.exceptions.MitroServletException;
import co.mitro.core.exceptions.SendableException;
import co.mitro.core.exceptions.UserVisibleException;
import co.mitro.core.server.data.DBGroup;
import co.mitro.core.server.data.RPC;
import co.mitro.core.server.data.RPC.CreateOrganizationRequest;
import co.mitro.core.server.data.RPC.CreateOrganizationRequest.PrivateGroupKeys;
import co.mitro.core.server.data.RPC.CreateOrganizationResponse;
import co.mitro.core.servlets.MitroServlet.MitroRequestContext;

import com.google.common.collect.ImmutableMap;

public class CreateOrganizationTest extends MemoryDBFixture {
  private CreateOrganizationRequest request;
  private CreateOrganization servlet;
  private AuthenticatedDB db;

  @SuppressWarnings("deprecation")
  @Before
  public void setUp() {
    servlet = new CreateOrganization(managerFactory, keyFactory);

    request = new CreateOrganizationRequest();
    request.name = "hello";
    request.publicKey = "org public key";

    request.adminEncryptedKeys = new HashMap<>();
    request.adminEncryptedKeys.put(testIdentity.getName(), "org key for user");

    request.memberGroupKeys = new HashMap<>();
    addMemberToRequest(testIdentity.getName());

    db = AuthenticatedDB.deprecatedNew(manager, testIdentity);
  }

  private void addMemberToRequest(String identityEmail) {
    PrivateGroupKeys keys = new PrivateGroupKeys();
    keys.publicKey = "public key for group";
    keys.keyEncryptedForUser = "group key for user";
    keys.keyEncryptedForOrganization = "group key for org";
    request.memberGroupKeys.put(identityEmail, keys);
  }

  private void expectMessage(String substring) {
    String errorMessage = CreateOrganization.validateRequest(request);
    assertThat(errorMessage, containsString(substring));
  }

  @Test
  public void requestErrorMessage() {
    // empty request = not valid
    request = new RPC.CreateOrganizationRequest();
    expectMessage("name cannot be empty");
    request.name = "";
    expectMessage("name cannot be empty");
    request.name = "hello";

    expectMessage("publicKey cannot be empty");
    request.publicKey = "";
    expectMessage("publicKey cannot be empty");
    request.publicKey = "pubkey";

    expectMessage("adminEncryptedKeys cannot be empty");
    request.adminEncryptedKeys = new HashMap<>();
    expectMessage("adminEncryptedKeys cannot be empty");
    request.adminEncryptedKeys.put("identity", "fake key");

    expectMessage("memberGroupKeys cannot be empty");
    request.memberGroupKeys = new HashMap<>();
    expectMessage("memberGroupKeys cannot be empty");
    request.memberGroupKeys.put("identity2", null);
    expectMessage("each admin must be a member");
    request.memberGroupKeys.put("identity", null);
    assertEquals(null, CreateOrganization.validateRequest(request));
  }

  @Test
  public void createOrg() throws Exception {
    assertEquals(0, db.getOrganizations().size());

    request.name = "";
    expectExceptionType(SendableException.class, "name cannot be empty");
    assertEquals(1, manager.auditDao.countOf());  // audit log rollback

    request.name = "org";
    makeRequest();
    assertEquals(2, manager.auditDao.countOf());

    Set<DBGroup> orgs = db.getOrganizations();
    assertEquals(1, orgs.size());
    DBGroup g = orgs.iterator().next();
    assertEquals("org", g.getName());
    assertEquals(DBGroup.Type.TOP_LEVEL_ORGANIZATION, g.getType());

    manager.commitTransaction();

    // execute the request again: duplicate organization error
    expectException("duplicate organization name");

    // create a different org with this user as a member
    request.name = "second org";
    request.adminEncryptedKeys.clear();
    request.adminEncryptedKeys.put(testIdentity2.getName(), "admin keys for testIdentity2");
    addMemberToRequest(testIdentity2.getName());
    addMemberToRequest(testIdentity.getName());
    
    // users can now be in more than one organization
    servlet.processCommand(new MitroRequestContext(
        testIdentity2, gson.toJson(request), manager, null));

  }

  @Test
  public void createOrgWithAdditionalMembers() throws Exception {
    // add testIdentity2 as a member
    addMemberToRequest(testIdentity2.getName());
    makeRequest();
    assertEquals(1, db.getOrganizations().size());

    @SuppressWarnings("deprecation")
    AuthenticatedDB db2 = AuthenticatedDB.deprecatedNew(manager, testIdentity2);
    assertEquals(1, db2.getOrganizations().size());
  }

  @Test
  public void createOrgBadMembers() throws Exception {
    // add a non-existent user as a member
    addMemberToRequest("bad@example.com");
    expectException("acl must apply to exactly one identity or group");
  }

  @Test
  public void createOrgRequestorNotAdmin() throws Exception {
    // make testIdentity2 an admin instead of testIdentity; must fail
    request.adminEncryptedKeys = ImmutableMap.of(testIdentity2.getName(), "private key");
    request.memberGroupKeys.put(
        testIdentity2.getName(), request.memberGroupKeys.get(testIdentity.getName()));
    expectException("does not have permission to modify group");
  }

  private void expectException(String errorSubstring) throws IOException, SQLException {
    expectExceptionType(MitroServletException.class, errorSubstring);
  }

  private <T> void expectExceptionType(
      Class<T> exceptionClass, String errorSubstring) throws IOException, SQLException {
    try {
      makeRequest();
      fail("expected exception");
    } catch (MitroServletException e) {
      assertThat(e, instanceOf(exceptionClass));
      assertThat(e.getMessage(), containsString(errorSubstring));
    } finally {
      manager.rollbackTransaction();
    }
  }

  public CreateOrganizationResponse makeRequest()
      throws IOException, SQLException, MitroServletException {
    CreateOrganizationResponse response = (CreateOrganizationResponse) servlet.processCommand(
        new MitroRequestContext(testIdentity, gson.toJson(request), manager, null));
    return response;
  }
}
