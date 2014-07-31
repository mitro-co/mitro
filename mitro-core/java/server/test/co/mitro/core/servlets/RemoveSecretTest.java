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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import co.mitro.analysis.AuditLogProcessor;
import co.mitro.core.exceptions.MitroServletException;
import co.mitro.core.server.data.DBAcl.AccessLevelType;
import co.mitro.core.server.data.DBAcl.CyclicGroupError;
import co.mitro.core.server.data.DBGroup;
import co.mitro.core.server.data.DBGroupSecret;
import co.mitro.core.server.data.DBIdentity;
import co.mitro.core.server.data.DBServerVisibleSecret;
import co.mitro.core.server.data.RPC;
import co.mitro.core.server.data.RPC.MitroRPC;
import co.mitro.core.servlets.MitroServlet.MitroRequestContext;

import com.google.common.collect.ImmutableList;

public class RemoveSecretTest extends MemoryDBFixture {
  private DBServerVisibleSecret secret;
  private RPC.RemoveSecretRequest request;
  private RemoveSecret servlet;

  @Before
  public void setUp() throws SQLException {
    secret = createSecret(testGroup, "client", "critical", null);

    request = new RPC.RemoveSecretRequest();
    request.secretId = secret.getId();
    request.groupId = testGroup.getId();
    servlet = new RemoveSecret();
  }

  @Test
  public void testRemoveFromLastGroup() throws Exception {
    // remove secret from testGroup: deleted!
    removeSecret(testIdentity);
    assertNull(manager.svsDao.queryForId(secret.getId()));
    manager.setRequestor(testIdentity, "device");
    assertEquals(true, hasAudit(manager, AuditLogProcessor.ActionType.REVOKED_ACCESS_TO, testIdentity, testIdentity, secret.getId()));
  }


  @Test
  public void testRemoveFromSingleGroup() throws Exception {
    // add secret to testGroup and group2
    DBGroup g2 = createGroupContainingIdentity(testIdentity);
    addSecretToGroup(secret, g2, "client", "critical");

    // remove secret from testGroup: still in g2
    removeSecret(testIdentity);
    assertNotNull(manager.getGroupSecret(g2, secret));
    assertNotNull(manager.svsDao.queryForId(secret.getId()));
  }

  @Test
  public void deleteSecretAsOrgAdmin() throws IOException, SQLException, MitroServletException, CyclicGroupError {
    List<DBIdentity> admins = ImmutableList.of(testIdentity2);
    List<DBIdentity> members = ImmutableList.of();
    DBGroup topLevelOrganization = createOrganization(
        testIdentity2, "Organization", admins, members);
    DBGroup g2 = createGroupContainingIdentity(testIdentity);
    addOrgToGroup(manager, topLevelOrganization, g2, AccessLevelType.ADMIN);
    DBServerVisibleSecret newSecret = createSecret(g2, "client", "critical", null);
    request.secretId = newSecret.getId();
    request.groupId = g2.getId();
    // we should be able to delete the secret as the admin.
    removeSecret(testIdentity2);
  }
  
  @Test
  public void testRemoveFromAllGroups() throws Exception {
    // add secret to testGroup and group2
    DBGroup g2 = createGroupContainingIdentity(testIdentity);
    addSecretToGroup(secret, g2, "client", "critical");

    DBGroupSecret matchObject = new DBGroupSecret();
    matchObject.setServerVisibleSecret(secret);
    assertEquals(2, manager.groupSecretDao.queryForMatchingArgs(matchObject).size());

    // remove secret from all groups: deletes the secret
    request.groupId = null;
    removeSecret(testIdentity);
    assertEquals(0, manager.groupSecretDao.queryForMatchingArgs(matchObject).size());
    assertNull(manager.svsDao.queryForId(secret.getId()));
  }

  @Test
  public void testNoSecret() throws Exception {
    request.secretId += 1;
    assertRemoveException("does not exist", testIdentity);
  }

  @Test
  public void testWrongGroup() throws Exception {
    request.groupId += 1;
    assertRemoveException("not in group", testIdentity);
  }

  @Test
  public void testRemoveFromNonMemberGroup() throws Exception {
    // testIdentity2: not permitted to remove secret from testGroup (does not have access)
    assertRemoveException("does not have access", testIdentity2);

    // Create group for testIdentity2, adding secret
    DBGroup id2Group = createGroupContainingIdentity(testIdentity2);
    addSecretToGroup(secret, id2Group, "client", "critical");

    // remove secret from testGroup, as testIdentity2
    // happens when user1 shares a secret with id2Group, and user2 in id2Group shares with user3
    // this causes user2 to remove user1, creating a group with (user1, user3)
    removeSecret(testIdentity2);
  }

  @SuppressWarnings("deprecation")
  @Test
  public void testDeprecatedUserId() throws IOException, SQLException, MitroServletException {
    request.myUserId = testIdentity.getName();
    removeSecret(testIdentity);

    // sign the request as testIdentity2, but the request itself specifies testIdentity
    // protects against spoofing (signing with one key, but pretending to be someone else)
    request.myUserId = testIdentity2.getName();
    assertRemoveException("does not match rpc requestor", testIdentity);
  }

  @Test
  public void removeFromAutodeleteGroup() throws Exception {
    // make testGroup an autodelete group.
    testGroup.setAutoDelete(true);
    manager.groupDao.update(testGroup);

    // share secret with another group and give testIdentity2 access
    DBGroup group2 = createGroupContainingIdentity(testIdentity2);
    addSecretToGroup(secret, group2, "client", "critical");

    // testIdentity2 removes the secret from testGroup
    removeSecret(testIdentity2);
    // testGroup should be deleted
    assertNull(manager.groupDao.queryForId(testGroup.getId()));

    // the secret still exists: it was only removed from the one group
    assertNotNull(manager.svsDao.queryForId(secret.getId()));
  }

  @Test
  public void removeFromOrganization() throws Exception {
    DBGroup organization = createOrganization(
        testIdentity, "org", ImmutableList.of(testIdentity), ImmutableList.of(testIdentity2));

    // put a secret in a private organization group
    DBGroup privateMemberGroup = getPrivateOrgGroup(organization, testIdentity2);
    secret = createSecret(privateMemberGroup, "client", "critical", organization);

    // remove the secret from the org only: not permitted
    request.secretId = secret.getId();
    request.groupId = organization.getId();
    assertRemoveException("cannot remove secrets from organizations", testIdentity);
    assertRemoveException("cannot remove secrets from organizations", testIdentity2);

    // delete the secret completely
    request.secretId = secret.getId();
    request.groupId = null;
    // This is now permitted, but we should because testIdentity has admin access,
    // but we should require a special flag in the future
    // assertRemoveException("does not have access", testIdentity);
    // permitted as testIdentity2
    removeSecret(testIdentity2);

    // the secret still exists, but only as part of the organization
    manager.svsDao.refresh(secret);
    assertEquals(1, secret.getGroupSecrets().size());
    assertEquals(organization, secret.getGroupSecrets().iterator().next().getGroup());

    // now fails as testIdentity2
    assertRemoveException("does not have access", testIdentity2);
  }

  private void assertRemoveException(String expectedSubstring, DBIdentity identity)
      throws IOException, SQLException {
    try {
      removeSecret(identity);
      fail("expected exception");
    } catch (MitroServletException e) {
      assertThat(e.getMessage(), containsString(expectedSubstring));
    }
  }

  private void removeSecret(DBIdentity identity)
      throws IOException, SQLException, MitroServletException {
    MitroRPC response = servlet.processCommand(
        new MitroRequestContext(identity, gson.toJson(request), manager, null));
    assertNotNull(response);
  }
}
