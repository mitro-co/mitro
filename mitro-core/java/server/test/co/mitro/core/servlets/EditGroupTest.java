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
import static org.junit.Assert.fail;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import co.mitro.core.exceptions.MitroServletException;
import co.mitro.core.server.data.DBAcl;
import co.mitro.core.server.data.DBAcl.AccessLevelType;
import co.mitro.core.server.data.DBAcl.CyclicGroupError;
import co.mitro.core.server.data.DBGroup;
import co.mitro.core.server.data.DBGroupSecret;
import co.mitro.core.server.data.DBIdentity;
import co.mitro.core.server.data.DBServerVisibleSecret;
import co.mitro.core.server.data.RPC;
import co.mitro.core.server.data.RPC.AddGroupRequest.ACL;
import co.mitro.core.server.data.RPC.AddGroupResponse;
import co.mitro.core.server.data.RPC.EditGroupRequest;
import co.mitro.core.servlets.MitroServlet.MitroRequestContext;

public class EditGroupTest extends OrganizationsFixture {
  private EditGroupRequest request;
  private EditGroup servlet;

  @Before
  public void setUp() throws SQLException {
    request = createEditGroupRequest(testGroup);
    servlet = new EditGroup();
  }
  
  @Test
  public void testGetOrgAdminsForGroup() throws SQLException, CyclicGroupError {
    DBGroup groupForTest = createGroupContainingIdentity(testIdentity);

    DBAcl acl = new DBAcl();
    acl.setGroup(testGroup);
    acl.setMemberGroup(groupForTest);
    acl.setLevel(DBAcl.AccessLevelType.ADMIN);
    acl.setGroupKeyEncryptedForMe("encrypted group key");
    manager.aclDao.create(acl);
    assertEquals(1, manager.groupDao.refresh(testGroup));
    assertEquals(2, testGroup.getAcls().size());
    Set<Integer> orgAdmins = EditGroup.getOrgAdminsForGroup(manager, testGroup);
    assertEquals(1, orgAdmins.size());
    assertEquals(testIdentity.getId(), orgAdmins.iterator().next().intValue());
  }
  

  /** Returns a valid "do nothing" EditGroupRequest for group. */
  private EditGroupRequest createEditGroupRequest(DBGroup group) throws SQLException {
    EditGroupRequest r = new EditGroupRequest();
    r.groupId = group.getId();
    r.name = group.getName();
    r.autoDelete = group.isAutoDelete();
    r.publicKey = group.getPublicKeyString();
    r.acls = new ArrayList<>();
    for (DBAcl acl : group.getAcls()) {
      RPC.AddGroupRequest.ACL rpcAcl = new RPC.AddGroupRequest.ACL();
      rpcAcl.level = acl.getLevel();
      rpcAcl.groupKeyEncryptedForMe = acl.getGroupKeyEncryptedForMe();
      rpcAcl.memberIdentity = acl.loadMemberIdentity(manager.identityDao).getName();
      rpcAcl.myPublicKey = testIdentity.getPublicKeyString();

      assert rpcAcl.memberIdentity != null;
      r.acls.add(rpcAcl);
    }

    r.secrets = new ArrayList<>();
    for (DBGroupSecret groupSecret : group.getGroupSecrets()) {
      RPC.Secret rpcSecret = new RPC.Secret();
      rpcSecret.encryptedClientData = "client";
      rpcSecret.encryptedCriticalData = "critical";
      rpcSecret.secretId = groupSecret.getServerVisibleSecret().getId();
      r.secrets.add(rpcSecret);
    }
    return r;
  }

  private AddGroupResponse processCommand(DBIdentity identity)
      throws IOException, SQLException, MitroServletException {
    return (AddGroupResponse) servlet.processCommand(
        new MitroRequestContext(identity, gson.toJson(request), manager, null));
  }

  @Test
  public void testEditNoOpWithAcl() throws SQLException, IOException, MitroServletException, CyclicGroupError {
    DBGroup newGroup = createGroupContainingIdentity(testIdentity); 
    request.groupId = newGroup.getId();
    request.secrets = null;
    AddGroupResponse response = processCommand(testIdentity);
    assertEquals(newGroup.getId(), response.groupId);
    request = createEditGroupRequest(newGroup);
    
    
    // Same thing should work with empty secrets
    request.secrets = new ArrayList<>();
    response = processCommand(testIdentity);
    assertEquals(newGroup.getId(), response.groupId);
    
    // Add a secret to the group: empty secrets now is an incorrect request
    createSecret(newGroup, "client", "critical", null);
    try {
      processCommand(testIdentity);
      fail("expected exception");
    } catch (MitroServletException e) {
      assertThat(e.getMessage(), containsString("cannot change the number of secrets"));
    }
  }

  @Test
  public void testEditNoOpWithoutAcl() throws SQLException, IOException, MitroServletException {
    request.acls = null;
    AddGroupResponse response = processCommand(testIdentity2);
    assertEquals(testGroup.getId(), response.groupId);

    request.acls = Collections.emptyList();
    response = processCommand(testIdentity2);
    assertEquals(testGroup.getId(), response.groupId);
  }

  @Test
  public void testRemoveUserFromGroup() throws Exception {
    // only contains testIdentity
    DBGroup privateGroup = createGroupContainingIdentity(testIdentity);
    // Add testIdentity2 to testGroup
    addToGroup(testIdentity2, testGroup, AccessLevelType.ADMIN);
    // Add a secret to the group
    DBServerVisibleSecret svs = createSecret(testGroup, "client", "critical", null);
    addSecretToGroup(svs, privateGroup, "client", "critical");

    // create the edit request then remove testIdentity from testGroup
    request = createEditGroupRequest(testGroup);
    int testIdentityIndex = -1;
    for (int i = 0; i < request.acls.size(); i++) {
      if (request.acls.get(i).memberIdentity.equals(testIdentity.getName())) {
        testIdentityIndex = i;
        break;
      }
    }
    request.acls.remove(testIdentityIndex);

    // request should succeed (at one point failed due to "orphaned" secret:
    // testIdentity can only edit privateGroup, testIdentity2 can only edit testGroup
    AddGroupResponse response = processCommand(testIdentity2);
    assertEquals(testGroup.getId(), response.groupId);
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
    
    manager.groupDao.refresh(group);
    // turn group into an org2 group.
    request = createEditGroupRequest(group);
    ACL orgAcl = new ACL();
    orgAcl.memberGroup = org2.getId();
    orgAcl.groupKeyEncryptedForMe = "asdf";
    orgAcl.memberIdentity = null;
    orgAcl.level = AccessLevelType.ADMIN;
    request.acls.add(orgAcl);
    try {
      processCommand(org2Admin);
      fail("expected exception");
    } catch (MitroServletException e) {
      if (!e.getMessage().contains("secret to more than one organization")) throw e;
    }
  }
  
  
}
