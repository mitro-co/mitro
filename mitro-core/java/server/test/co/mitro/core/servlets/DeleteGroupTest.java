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
import static org.junit.Assert.fail;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import co.mitro.core.exceptions.MitroServletException;
import co.mitro.core.server.data.DBAcl;
import co.mitro.core.server.data.DBAcl.AccessLevelType;
import co.mitro.core.server.data.DBAcl.CyclicGroupError;
import co.mitro.core.server.data.DBGroup;
import co.mitro.core.server.data.DBIdentity;
import co.mitro.core.server.data.RPC;
import co.mitro.core.servlets.MitroServlet.MitroRequestContext;

import com.google.common.collect.ImmutableList;

public class DeleteGroupTest extends MemoryDBFixture {
  private DeleteGroup servlet;

  @Before
  public void setUp() throws SQLException {
    servlet = new DeleteGroup();
  }

  @Test
  public void removeGroupWithMembers() throws Exception {
    addToGroup(testIdentity2, testGroup, AccessLevelType.READONLY);
    deleteGroupAsUser(testGroup, testIdentity);
    assertNull(manager.groupDao.queryForId(testGroup.getId()));
  }

  // TODO: Fix this immediately
  @Test
  @Ignore
  public void cannotDeleteWithoutAdmin() throws Exception {
    DBAcl acl = addToGroup(testIdentity2, testGroup, AccessLevelType.READONLY);
    deleteGroupAsUser(testGroup, testIdentity2);
    fail("expected exception");
    acl.setLevel(AccessLevelType.MODIFY_SECRETS_BUT_NOT_MEMBERSHIP);
    manager.aclDao.update(acl);
    deleteGroupAsUser(testGroup, testIdentity2);
    fail("expected exception");
  }

  @Test
  public void notMember() throws Exception {
    // testIdentity2 can't remove testGroup: it does not have access
    expectException(testGroup, testIdentity2, "does not have permission");
  }

  @Test
  public void privateUserGroup() throws Exception {
    // "legacy" untagged private user group
    testGroup.setName("");
    manager.groupDao.update(testGroup);
    assertTrue(testGroup.isPrivateUserGroup());
    expectException(testGroup, testIdentity, "cannot delete private group");

    // new style tagged private user group
    testGroup.setType(DBGroup.Type.PRIVATE);
    manager.groupDao.update(testGroup);
    assertTrue(testGroup.isPrivateUserGroup());
    expectException(testGroup, testIdentity, "cannot delete private group");
  }

  @Test
  public void deleteNonExistentGroup() throws Exception {
    int groupId = testGroup.getId() + 10000;
    assertNull(manager.groupDao.queryForId(groupId));

    try {
      deleteGroupIdAsUser(groupId, testIdentity);
      fail("expected exception");
    } catch (MitroServletException e) {
      assertThat(e.getMessage(), containsString("does not exist"));
    }
  }

  @Test
  public void deleteAutoDeleteGroup() throws Exception {
    // we should never remove autodelete groups through the public API
    testGroup.setAutoDelete(true);
    manager.groupDao.update(testGroup);
    expectException(testGroup, testIdentity2, "cannot delete autodelete");
  }

  @Test
  public void tryRemoveGroupWithSecret() throws Exception{
    // add a secret to testGroup
    createSecret(testGroup, "client", "critical", null);
    expectException(testGroup, testIdentity, "Cannot delete a team with secrets");
  }

  @Test
  public void deleteTopLevelOrg() throws IOException, SQLException, MitroServletException {
    List<DBIdentity> admins = ImmutableList.of(testIdentity);
    List<DBIdentity> members = ImmutableList.of(testIdentity2);
    DBGroup topLevelOrganization = createOrganization(
        testIdentity, "Organization", admins, members);

    // no one can delete the top level organization group
    expectException(topLevelOrganization, testIdentity2, "permission to access");
    expectException(topLevelOrganization, testIdentity, "cannot delete organization");
  }

  @Test
  public void deleteOrgGroupAsAdmin() throws IOException, SQLException, MitroServletException, CyclicGroupError {
    List<DBIdentity> admins = ImmutableList.of(testIdentity2);
    List<DBIdentity> members = ImmutableList.of();
    DBGroup topLevelOrganization = createOrganization(
        testIdentity2, "Organization", admins, members);
    addOrgToGroup(manager, topLevelOrganization, testGroup, AccessLevelType.ADMIN);
    // no one can delete the top level organization group
    deleteGroupAsUser(testGroup, testIdentity2);
  }
  private void expectException(DBGroup group, DBIdentity identity, String exceptionSubstring)
      throws IOException, SQLException {
    try {
      deleteGroupAsUser(group, identity);
      fail("expected exception");
    } catch (MitroServletException e) {
      assertThat(e.getMessage(), containsString(exceptionSubstring));
    }
  }

  private void deleteGroupAsUser(DBGroup group, DBIdentity identity)
      throws IOException, SQLException, MitroServletException {
    deleteGroupIdAsUser(group.getId(), identity);
  }

  private void deleteGroupIdAsUser(int groupId, DBIdentity identity)
      throws IOException, SQLException, MitroServletException {
    RPC.DeleteGroupRequest request = new RPC.DeleteGroupRequest();
    request.groupId = groupId;
    servlet.processCommand(new MitroRequestContext(identity, gson.toJson(request), manager, null));
  }
}
