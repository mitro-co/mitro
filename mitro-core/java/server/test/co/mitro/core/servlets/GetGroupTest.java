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
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.sql.SQLException;

import org.junit.Before;
import org.junit.Test;

import co.mitro.core.exceptions.MitroServletException;
import co.mitro.core.exceptions.PermissionException;
import co.mitro.core.server.data.DBAcl;
import co.mitro.core.server.data.DBAcl.AccessLevelType;
import co.mitro.core.server.data.DBAcl.CyclicGroupError;
import co.mitro.core.server.data.DBGroup;
import co.mitro.core.server.data.DBIdentity;
import co.mitro.core.server.data.DBServerVisibleSecret;
import co.mitro.core.server.data.RPC;
import co.mitro.core.server.data.RPC.GetGroupRequest;
import co.mitro.core.servlets.MitroServlet.MitroRequestContext;

public class GetGroupTest extends MemoryDBFixture {
  private GetGroupRequest request;
  private GetGroup servlet;

  @Before
  public void setUp() {
    request = new RPC.GetGroupRequest();
    request.groupId = testGroup.getId();
    servlet = new GetGroup();
  }

  private RPC.GetGroupResponse processCommand(DBIdentity identity) throws IOException,
      SQLException, MitroServletException {
    RPC.GetGroupResponse out = (RPC.GetGroupResponse) servlet.processCommand(
        new MitroRequestContext(identity, gson.toJson(request), manager, null));
    return out;
  }

  @Test
  public void test() throws IOException, SQLException, MitroServletException, CyclicGroupError {
    RPC.GetGroupResponse out = processCommand(testIdentity);
    assertEquals(false, out.autoDelete);
    assertEquals(testGroup.getName(), out.name);
    assertEquals(1, out.acls.size());
    assertEquals(ENCRYPTED_GROUP_KEY, out.acls.get(0).groupKeyEncryptedForMe);
    assertEquals(testIdentity.getPublicKeyString(), out.acls.get(0).myPublicKey);

    // test mid-level permissions: modify_secrets permission should be able to get the group
    DBGroup newGroup = createGroupContainingIdentity(testIdentity2);
    DBAcl a = newGroup.getAcls().iterator().next();
    assertEquals(a.getMemberIdentityId().getId(), testIdentity2.getId());
    a.setLevel(DBAcl.AccessLevelType.MODIFY_SECRETS_BUT_NOT_MEMBERSHIP);
    manager.aclDao.update(a);
    request.groupId = newGroup.getId();
    out = processCommand(testIdentity2);

    request.groupId = testGroup.getId();
    
    // Add a new group as a member of testGroup
    DBGroup g2 = new DBGroup();
    g2.setName("g2");
    g2.setPublicKeyString("g2 pubkey");
    manager.groupDao.create(g2);

    DBAcl acl = new DBAcl();
    acl.setGroup(testGroup);
    acl.setMemberGroup(g2);
    acl.setGroupKeyEncryptedForMe("encrypted");
    acl.setLevel(DBAcl.AccessLevelType.ADMIN);
    manager.aclDao.create(acl);

    // Get the ACL for Test
    out = processCommand(testIdentity);
    assertEquals(testGroup.getName(), out.name);
    assertEquals(2, out.acls.size());
    assertEquals(testIdentity.getPublicKeyString(), out.acls.get(0).myPublicKey);
    assertEquals(g2.getPublicKeyString(), out.acls.get(1).myPublicKey);

    // TODO: Test adding another identity to the group
    // testIdentity should not get the encryptedGroupKey for that user
  }

  @Test
  public void indirectAccess() throws Exception {
    // testIdentity and testIdentity2 are both in group g, sharing a secret
    DBGroup g = createGroupContainingIdentity(testIdentity);
    DBAcl id2Acl = addToGroup(testIdentity2, g, AccessLevelType.ADMIN);
    DBServerVisibleSecret s = createSecret(g, "client",  "critical", null);

    // only testIdentity can access testGroup
    try {
      processCommand(testIdentity2);
      fail("expected exception");
    } catch (PermissionException e) {
      assertThat(e.getMessage(), containsString("permission to access group"));
    }

    // add secret to testGroup; testIdentity2 must have access to testGroup to edit this secret
    addSecretToGroup(s, testGroup, "client", "critical");
    RPC.GetGroupResponse out = processCommand(testIdentity2);
    assertEquals(false, out.autoDelete);
    assertEquals(testGroup.getName(), out.name);
    assertEquals(testGroup.getPublicKeyString(), out.publicKey);
    // secrets and ACLs are NOT returned for this limited request
    assertEquals(0, out.acls.size());
    assertEquals(0, out.secrets.size());

    // still get group as a modify secrets admin
    id2Acl.setLevel(AccessLevelType.MODIFY_SECRETS_BUT_NOT_MEMBERSHIP);
    manager.aclDao.update(id2Acl);
    assertNotNull(processCommand(testIdentity2));

    // make id2 a read-only member (can't edit the secret); no longer can get the group!
    id2Acl.setLevel(AccessLevelType.READONLY);
    manager.aclDao.update(id2Acl);
    try {
      processCommand(testIdentity2);
      fail("expected exception");
    } catch (PermissionException e) {
      assertThat(e.getMessage(), containsString("permission to access group"));
    }
  }
}
