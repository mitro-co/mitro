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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import co.mitro.core.exceptions.MitroServletException;
import co.mitro.core.server.data.DBAcl.AccessLevelType;
import co.mitro.core.server.data.DBGroup;
import co.mitro.core.server.data.DBIdentity;
import co.mitro.core.server.data.DBServerVisibleSecret;
import co.mitro.core.server.data.RPC;
import co.mitro.core.server.data.RPC.GetOrganizationStateResponse;
import co.mitro.core.server.data.RPC.ListMySecretsAndGroupKeysResponse.GroupInfo;
import co.mitro.core.servlets.MitroServlet.MitroRequestContext;

import com.google.common.collect.Sets;
import com.j256.ormlite.support.DatabaseConnection;

public class GetOrganizationStateTest extends OrganizationsFixture {
  
  private void insertAddSecretAuditLog(DBServerVisibleSecret secret, DBIdentity user, int timestamp)
      throws SQLException {
    manager.auditDao.getConnectionSource().getReadWriteConnection().executeStatement(
        "INSERT into audit (uid, target_sv_secret, \"timestampMs\", action) VALUES("
        + user.getId() + ", " + secret.getId() + ", " + timestamp + ", 'ADD_SECRET');",
        DatabaseConnection.DEFAULT_RESULT_FLAGS);
  }
  
  @Test
  public void testGetOrgData() throws SQLException, MitroServletException, IOException {
    // Must be a multiple of 1000:
    final int orphanedSecretTimestamp  = 2000;
    insertAddSecretAuditLog(orgSecret, testIdentity, 0);
    insertAddSecretAuditLog(orphanedOrgSecret, testIdentity2, orphanedSecretTimestamp);
    getOrgStateResponse(testIdentity);

    assertEquals(admins.size() + members.size(), gosResponse.members.size());
    Set<String> emails = Sets.newHashSet();
    final Set<String> adminEmails = Sets.newHashSet();
    for (DBIdentity i : members) {
      emails.add(i.getName());
    }
    for (DBIdentity i : admins) {
      emails.add(i.getName());
      adminEmails.add(i.getName());
    }
    List<String> sortedEmails = new ArrayList<>(emails);
    Collections.sort(sortedEmails);
    Collections.sort(gosResponse.members);
    assertEquals(sortedEmails, gosResponse.members);
    assertEquals(emails.size() + 1, gosResponse.groups.size());
    assertEquals(1, gosResponse.organizations.size());
    for (GroupInfo g: gosResponse.groups.values()) {
      assertFalse(g.autoDelete);
      assertFalse(g.isNonOrgPrivateGroup);
      if (g.isTopLevelOrg) {
        assertFalse(g.isOrgPrivateGroup);
        assertEquals(adminEmails, Sets.newHashSet(g.users));
        assertEquals(gosRequest.orgId, g.groupId);
      } else {
        assertTrue(g.isOrgPrivateGroup);
        assertEquals(org.getId(), g.owningOrgId.intValue());
        assertEquals(1, g.users.size());
        // make sure each user appears exactly once
        assertTrue(sortedEmails.remove(g.users.get(0)));
        // TODO: check that ONLY the requestor's private group has encryptedPrivateKey.
      }
    }
    assertTrue(sortedEmails.isEmpty());
    assertEquals(2, gosResponse.orgSecretsToPath.size());
    assertNotNull(gosResponse.orgSecretsToPath.get(orgSecret.getId()));
    assertEquals(0, gosResponse.orphanedSecretsToPath.size());

    for (RPC.Secret s : gosResponse.orgSecretsToPath.values()) {
      // TODO: Disabled due to slowness. See comment in GetOrganizationState
//      assertNotNull(s.lastModified);
//      assertEquals(testIdentity.getName(), s.lastModified.userName);
//      assertEquals(0, s.lastModified.timestampSec);
      assertNull(s.lastModified);
    }
    for (RPC.Secret s : gosResponse.orphanedSecretsToPath.values()) {
//      assertNotNull(s.lastModified);
//      assertEquals(orphanedSecretTimestamp/1000, s.lastModified.timestampSec);
//      assertEquals(testIdentity2.getName(), s.lastModified.userName);
      assertNull(s.lastModified);
    }
  }

  @Test
  public void getAsMember() throws Exception {
    // Originally getOrgState was forbidden to regular users.
    // Now used to get list of members and groups.
    DBIdentity member = members.iterator().next();
    getOrgStateResponse(member);

    // doesn't return secrets for non-admins (not needed)
    assertNull(gosResponse.orgSecretsToPath);
    assertNull(gosResponse.orphanedSecretsToPath);
    assertNull(gosResponse.organizations);

    assertEquals(8, gosResponse.admins.size());
    assertTrue(gosResponse.admins.contains(testIdentity.getName()));
    assertFalse(gosResponse.admins.contains(member.getName()));

    // members contains both admins and regular members
    assertEquals(15, gosResponse.members.size());
    assertTrue(gosResponse.members.contains(testIdentity.getName()));
    assertTrue(gosResponse.members.contains(member.getName()));

    assertEquals(16, gosResponse.groups.size());
    for (GroupInfo group : gosResponse.groups.values()) {
      assertNull(group.users);
      assertNull(group.encryptedPrivateKey);
      assertFalse(group.isNonOrgPrivateGroup);
      assertFalse(group.isRequestorAnOrgAdmin);
    }
  }

  @Test
  public void orgGroupDontMakeMembers() throws Exception {
    // Add a named group to the organization. The members are not automatically part of the org
    DBGroup namedGroup = createGroupContainingIdentity(testIdentity2);
    addOrgToGroup(manager, org, namedGroup, AccessLevelType.ADMIN);

    // testIdentity2 still can't call getorgstate
    expectException(testIdentity2);

    getOrgStateResponse(testIdentity);
    assertFalse(gosResponse.members.contains(testIdentity2.getName()));
  }

  @Test
  public void invalidGets() throws Exception {
    // non-member
    expectException(testIdentity2);
    
    // get non-org group
    gosRequest.orgId = testGroup.getId();
    expectException(testIdentity2);
    
    // get non-existent group
    gosRequest.orgId = -1;
    expectException(testIdentity2);
  }

  private void expectException(DBIdentity identity) throws IOException, SQLException {
    try {
      getOrgStateResponse(identity);
      fail("expected exception");
    } catch (MitroServletException expected) {
      assertThat(expected.getMessage(), containsString("no access"));
    }
  }

  private void getOrgStateResponse(DBIdentity identity) throws IOException, SQLException,
      MitroServletException {
    gosResponse = (GetOrganizationStateResponse) gosServlet.processCommand(
        new MitroRequestContext(identity, gson.toJson(gosRequest), manager, null));
  }
}
