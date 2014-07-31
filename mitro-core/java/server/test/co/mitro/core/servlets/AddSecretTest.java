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
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import co.mitro.analysis.AuditLogProcessor;
import co.mitro.core.exceptions.MitroServletException;
import co.mitro.core.server.data.DBAcl;
import co.mitro.core.server.data.DBAcl.AccessLevelType;
import co.mitro.core.server.data.DBAcl.CyclicGroupError;
import co.mitro.core.server.data.DBAudit;
import co.mitro.core.server.data.DBGroup;
import co.mitro.core.server.data.DBGroupSecret;
import co.mitro.core.server.data.DBIdentity;
import co.mitro.core.server.data.DBServerVisibleSecret;
import co.mitro.core.server.data.RPC;
import co.mitro.core.server.data.RPC.AddSecretResponse;
import co.mitro.core.servlets.MitroServlet.MitroRequestContext;

public class AddSecretTest extends OrganizationsFixture {
  private AddSecret servlet;
  private RPC.AddSecretRequest request;

  @Before
  public void setUp() throws SQLException, CyclicGroupError {
    servlet = new AddSecret();

    request = new RPC.AddSecretRequest();
    request.ownerGroupId = testGroup.getId();
    request.encryptedClientData = "client";
    request.encryptedCriticalData = "critical";
  }

  @Test
  public void testAddSecret()
      throws IOException, SQLException, MitroServletException, CyclicGroupError {
    manager.setRequestor(testIdentity, null);
    RPC.AddSecretResponse response = processCommand(testIdentity);

    DBServerVisibleSecret secret = manager.svsDao.queryForId(response.secretId);

    DBGroupSecret groupSecret = manager.getGroupSecret(testGroup, secret);
    assertEquals(request.encryptedClientData, groupSecret.getClientVisibleDataEncrypted());
    assertEquals(request.encryptedCriticalData, groupSecret.getCriticalDataEncrypted());

    // Add the secret to a new group
    DBGroup second = createGroupContainingIdentity(testIdentity);
    request.secretId = response.secretId;
    request.ownerGroupId = second.getId();
    request.encryptedClientData = "fooclient";
    request.encryptedCriticalData = "foocritical";
    response = processCommand(testIdentity);
    assertEquals((int) request.secretId, response.secretId);

    List<DBAudit> audits = DBAudit.getAllActionsByUser(manager, testIdentity);
    assertEquals(audits.size(), 2);
    assertEquals(audits.get(0).getAction(), DBAudit.ACTION.ADD_SECRET);
    assertEquals(audits.get(0).getTargetGroup(), testGroup);
    assertEquals(audits.get(1).getAction(), DBAudit.ACTION.ADD_SECRET);
    assertEquals(audits.get(1).getTargetGroup(), second);

    DBGroupSecret s2 = manager.getGroupSecret(second, secret);
    assertTrue(s2.getId() != groupSecret.getId());
    assertEquals("fooclient", s2.getClientVisibleDataEncrypted());
    assertEquals("foocritical", s2.getCriticalDataEncrypted());

    // Repeating the request fails
    processCommandWithException(testIdentity, "Secret is already in group");
  }

  @SuppressWarnings("deprecation")
  @Test
  public void deprecatedMyUserId()
      throws IOException, SQLException, MitroServletException {

    // wrong user id fails
    request.myUserId = testIdentity2.getName();
    processCommandWithException(testIdentity, "User ID does not match rpc requestor");

    // correct user id works
    request.myUserId = testIdentity.getName();
    RPC.AddSecretResponse response = processCommand(testIdentity);
    DBServerVisibleSecret svs = manager.svsDao.queryForId(response.secretId);
    assertEquals(svs.getId(), response.secretId);
  }

  @Test
  public void addWithoutAdminAccessSecret() throws Exception {
    // testIdentity can't access this secret!
    DBGroup id2Group = createGroupContainingIdentity(testIdentity2);
    DBServerVisibleSecret svs = createSecret(id2Group, "client", "critical", null);

    // Add id2 to a new group as admin, and testIdentity as READONLY
    DBGroup bothIdentities = createGroupContainingIdentity(testIdentity2);
    DBAcl testIdentityAcl = addToGroup(testIdentity, bothIdentities, AccessLevelType.READONLY);

    // testIdentity doesn't have access to this secret (only part of id2Group)
    request.ownerGroupId = bothIdentities.getId();
    request.secretId = svs.getId();
    processCommandWithException(testIdentity, "User is not an admin");
    // testIdentity can't add new secrets to this group (not admin)
    request.secretId = null;
    processCommandWithException(testIdentity, "User is not an admin");

    // Set testIdentity's access level to ADMIN: works
    testIdentityAcl.setLevel(AccessLevelType.ADMIN);
    manager.aclDao.update(testIdentityAcl);
    AddSecretResponse resp = processCommand(testIdentity);
    manager.setRequestor(testIdentity, "device");
    hasAudit(manager, AuditLogProcessor.ActionType.GRANTED_ACCESS_TO, testIdentity, testIdentity2, resp.secretId);
  }

  @Test
  public void addWithMultipleGroups() throws Exception {
    // Groups: testGroup (testIdentity); id2Group (testIdentity2)
    // Create a secret that belongs to both id2Group and testGroup (both identities are admins)
    // had a null bug access control checking groups when the requestor is not a member
    DBGroup id2Group = createGroupContainingIdentity(testIdentity2);
    DBServerVisibleSecret svs = createSecret(id2Group, "client", "critical", null);
    addSecretToGroup(svs, testGroup, "client", "critical");

    // testIdentity successfully adds this secret to a new group
    DBGroup testIdentityGroup2 = createGroupContainingIdentity(testIdentity);
    request.ownerGroupId = testIdentityGroup2.getId();
    request.secretId = svs.getId();
    AddSecretResponse resp = processCommand(testIdentity);
    assertEquals(svs.getId(), resp.secretId);
    manager.setRequestor(testIdentity, "device");
    // both users already had access to this secret before, so no changes should have been written
    assertEquals(false, 
        hasAudit(manager, AuditLogProcessor.ActionType.GRANTED_ACCESS_TO, testIdentity, testIdentity, resp.secretId));
    assertEquals(false, 
        hasAudit(manager, AuditLogProcessor.ActionType.GRANTED_ACCESS_TO, testIdentity, testIdentity2, resp.secretId));

    // testIdentity2 successfully adds this secret to a new group
    DBGroup testIdentity2Group2 = createGroupContainingIdentity(testIdentity2);
    request.ownerGroupId = testIdentity2Group2.getId();
    request.secretId = svs.getId();
    assertEquals(svs.getId(), processCommand(testIdentity2).secretId);
  }

  @Test
  public void testAddSecretBadSecretId()
      throws IOException, SQLException, MitroServletException {
    request.secretId = 99;
    processCommandWithException(testIdentity, "Secret does not exist");
  }
  
  @Test
  public void testAddSecretToMultipleOrgs()
      throws IOException, SQLException, MitroServletException, CyclicGroupError {

    // share the secret with a user.
    DBIdentity org2Admin = outsiders.iterator().next();
    DBGroup group = createGroupContainingIdentity(org2Admin);
    addSecretToGroup(orgSecret, group, "1", "2");

    
    // create a new org
    DBGroup org2 = createOrganization(org2Admin, "org2", outsiders, outsiders);

    // try to share the secret (in org1) with org2 user
    DBGroup userGroup = getPrivateOrgGroup(org2, org2Admin);
    // try to add the org secret to this group; should fail.
    request.secretId = orgSecret.getId();
    request.ownerGroupId = userGroup.getId();
    request.encryptedClientData = "1";
    request.encryptedCriticalData = "2";
    
    processCommandWithException(org2Admin, "You cannot add a secret to more than one organization.");
  }
  
  @Test
  public void testNotInGroup()
      throws IOException, SQLException, MitroServletException {
    DBGroup g = new DBGroup();
    g.setName("name");
    g.setPublicKeyString("public key");
    manager.groupDao.create(g);

    request.ownerGroupId = g.getId();
    processCommandWithException(testIdentity, "ser should not be able to see group");
  }

  private void processCommandWithException(DBIdentity requestor, String exceptionMessage)
      throws IOException, SQLException {
    try {
      processCommand(requestor);
      fail("expected exception");
    } catch (MitroServletException e) {
      assertThat(e.getMessage(), containsString(exceptionMessage));
    }
  }

  private RPC.AddSecretResponse processCommand(DBIdentity identity) throws IOException, SQLException,
      MitroServletException {
    return (RPC.AddSecretResponse) servlet.processCommand(new MitroRequestContext(
        identity, gson.toJson(request), manager, null));
  }
}
