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
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import co.mitro.core.exceptions.MitroServletException;
import co.mitro.core.server.data.DBAcl.CyclicGroupError;
import co.mitro.core.server.data.DBGroup;
import co.mitro.core.server.data.DBIdentity;
import co.mitro.core.server.data.RPC.AddPendingGroupRequest;
import co.mitro.core.server.data.RPC.AddPendingGroupRequest.AdminInfo;
import co.mitro.core.server.data.RPC.AddPendingGroupRequest.MemberList;
import co.mitro.core.server.data.RPC.AddPendingGroupRequest.PendingGroup;
import co.mitro.core.server.data.RPC.AddPendingGroupResponse;
import co.mitro.core.server.data.RPC.GetPendingGroupApprovalsRequest;
import co.mitro.core.server.data.RPC.GetPendingGroupApprovalsResponse;
import co.mitro.core.server.data.RPC.GroupDiff;
import co.mitro.core.server.data.RPC.GroupDiff.GroupModificationType;
import co.mitro.core.servlets.MitroServlet.MitroRequestContext;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class AddAndGetPendingGroupsTest extends OrganizationsFixture {
  private static final String NEW_USERNAME_1 = "nu34@example.com";
  private static final String SCOPE1 = "scope1";
  private static final String OLDGROUP2 = "oldgroup2";
  private static final String OLDGROUP1 = "oldgroup1";
  protected AddPendingGroupResponse resp;
  protected AddPendingGroupRequest rqst;
  protected AddPendingGroupServlet servlet;
  protected GetPendingGroupApprovalsResponse getresp;
  protected GetPendingGroupApprovalsRequest getrqst;
  protected GetPendingGroupApprovals getservlet;
  
  @Before 
  public void addPendingGroupsSetup() {
    rqst = new AddPendingGroupRequest();
    servlet = new AddPendingGroupServlet();
    getrqst = new GetPendingGroupApprovalsRequest();
    getservlet = new GetPendingGroupApprovals();
    getrqst.scope = SCOPE1;
  }

  private void expectException(DBIdentity identity, String substr) throws IOException, SQLException {
    try {
      getAddPendingResponse(identity);
      fail("expected exception");
    } catch (MitroServletException|AssertionError expected) {
      if (null != substr) {
        assertThat(expected.getMessage().toLowerCase(), containsString(substr.toLowerCase()));
      }
    }
  }

  private void expectExceptionForGet(DBIdentity identity, String substr) throws IOException, SQLException {
    try {
      getGetPendingResponse(identity);
      fail("expected exception");
    } catch (MitroServletException|AssertionError expected) {
      if (null != substr) {
        assertThat(expected.getMessage().toLowerCase(), containsString(substr.toLowerCase()));
      }
    }
  }
  
  private void getAddPendingResponse(DBIdentity identity) throws MitroServletException, IOException, SQLException {
    resp = (AddPendingGroupResponse)servlet.processCommand(
        new MitroRequestContext(identity, gson.toJson(rqst), manager, null));
  }

  private void getGetPendingResponse(DBIdentity identity) throws MitroServletException, IOException, SQLException {
    getresp = (GetPendingGroupApprovalsResponse) getservlet.processCommand(
        new MitroRequestContext(identity, gson.toJson(getrqst), manager, null));
  }

  @Test
  public void testPreconditionFailures() throws IOException, SQLException {
    rqst.pendingGroups = null;
    expectException(testIdentity2, null);
    
    rqst.pendingGroups = Lists.newArrayList();
    expectException(testIdentity2, null);
    rqst.pendingGroups.add(new PendingGroup());
    expectException(testIdentity2, null);
    rqst.adminInfo = new AdminInfo();
    expectException(testIdentity2, null);
    
    rqst.adminInfo.domainAdminEmail = "unknown@example.com";
    expectException(testIdentity2, "unknown user");
    
    rqst.adminInfo.domainAdminEmail = testIdentity.getName();
    expectException(testIdentity2, "scope must not be null");
    rqst.scope = SCOPE1;
    expectException(testIdentity2, "null MemberList");
  }
  
  private static String makeMemberList(List<String> members, String groupName){
    MemberList ml = new MemberList();
    ml.memberList = Lists.newArrayList(members);
    ml.groupName = groupName;
    return gson.toJson(ml);
  }
  
  private static void compareDiffs(Map<String, GroupDiff> a, Map<String, GroupDiff> b) {
    Map<String, GroupDiff> sortedA = Maps.newTreeMap();
    sortedA.putAll(a);
    Map<String, GroupDiff> sortedB = Maps.newTreeMap();
    sortedB.putAll(b);
    assertEquals(gson.toJson(sortedA), gson.toJson(sortedB));
    return;
  }

  @Test
  public void testAddingGroupsToOrg() throws MitroServletException, IOException, SQLException {
    rqst.pendingGroups = Lists.newArrayList();
    String groupName = "new group1"; 
    List<String> userNames = ImmutableList.of(NEW_USERNAME_1);
    setAdminInfo();
    addPendingGroupToRequest(groupName, userNames);
    getAddPendingResponse(testIdentity2);
    //private groups shouldn't be permitted
    assertEquals(1, resp.diffs.size());
    assertTrue(resp.diffs.containsKey(groupName));    
    assertEquals(GroupModificationType.IS_NEW, resp.diffs.get(groupName).groupModification);
    assertEquals(1, resp.diffs.get(groupName).newUsers.size());
    assertEquals(NEW_USERNAME_1, resp.diffs.get(groupName).newUsers.get(0));
    
    getGetPendingResponse(testIdentity);
    compareDiffs(resp.diffs, getresp.diffs);
    assertTrue(getresp.pendingDeletions.isEmpty());
    assertEquals(1, getresp.pendingAdditionsAndModifications.size());
    assertEquals(null, getresp.pendingAdditionsAndModifications.get(0).matchedGroup);
    assertEquals(resp.syncNonce, getresp.syncNonce);
    
    String oldNonce = resp.syncNonce;
    getAddPendingResponse(testIdentity2);
    assertTrue(resp.diffs.containsKey(groupName));
    assertEquals(GroupModificationType.IS_NEW, resp.diffs.get(groupName).groupModification);
    assertTrue(resp.diffs.get(groupName).deletedUsers.isEmpty());
    assertEquals(1, resp.diffs.get(groupName).newUsers.size());
    assertEquals(NEW_USERNAME_1, resp.diffs.get(groupName).newUsers.get(0));
    assertFalse(oldNonce.equals(resp.syncNonce));

    getGetPendingResponse(testIdentity);
    
    // all non-synced org users will be listed for delete.
    assertTrue(getresp.deletedOrgMembers.size() == admins.size() + members.size());
    assertTrue(getresp.newOrgMembers.contains(NEW_USERNAME_1));
    compareDiffs(resp.diffs, getresp.diffs);
    assertTrue(getresp.pendingDeletions.isEmpty());
    assertEquals(1, getresp.pendingAdditionsAndModifications.size());
    assertEquals(null, getresp.pendingAdditionsAndModifications.get(0).matchedGroup);
    assertEquals(resp.syncNonce, getresp.syncNonce);
  }

  private void setAdminInfo() {
    rqst.adminInfo = new AdminInfo();
    rqst.adminInfo.domainAdminEmail = testIdentity.getName();
    rqst.scope = SCOPE1;
  }

  private void addPendingGroupToRequest(String groupName, List<String> userNames)
      throws MitroServletException, IOException, SQLException {
    PendingGroup pg = new PendingGroup();
    pg.groupName = groupName;
    pg.memberListJson = makeMemberList(userNames, groupName);
    rqst.pendingGroups.add(pg);
    getAddPendingResponse(testIdentity2);
  }

  @Test
  public void testRemovingGroupsFromOrg() throws CyclicGroupError, SQLException, MitroServletException, IOException {
    DBGroup toDelete = createSyncedGroup(OLDGROUP1, SCOPE1, members, org);
    createSyncedGroup(OLDGROUP2, SCOPE1, members, org);
    
    rqst.pendingGroups = Lists.newArrayList();
    String groupName = OLDGROUP2; 
    List<String> userNames = Lists.newArrayList();
    for (DBIdentity i : members) {
      userNames.add(i.getName());
    }
    setAdminInfo();
    addPendingGroupToRequest(groupName, userNames);

    getAddPendingResponse(testIdentity2);

    //private groups shouldn't be permitted
    assertEquals(1, resp.diffs.size());
    
    assertFalse(resp.diffs.containsKey(OLDGROUP2));    
    assertTrue(resp.diffs.containsKey(OLDGROUP1));
    assertEquals(GroupModificationType.IS_DELETED, resp.diffs.get(OLDGROUP1).groupModification);
    assertTrue(resp.diffs.get(OLDGROUP1).newUsers.isEmpty());
    assertEquals(members.size(), resp.diffs.get(OLDGROUP1).deletedUsers.size());

    getGetPendingResponse(testIdentity);
        
    compareDiffs(resp.diffs, getresp.diffs);
    assertFalse(getresp.pendingDeletions.isEmpty());
    assertEquals(1, getresp.pendingDeletions.size());
    assertEquals(toDelete.getId(), getresp.pendingDeletions.get(0).groupId);
    assertEquals(resp.syncNonce, getresp.syncNonce);

    // test that non-admins can't get pending groups.
    expectExceptionForGet(testIdentity2, null);
  }
  
  @Test
  public void testNoOp() throws MitroServletException, IOException, SQLException, CyclicGroupError {
    createSyncedGroup(OLDGROUP2, SCOPE1, members, org);
    rqst.pendingGroups = Lists.newArrayList();
    String groupName = OLDGROUP2; 
    List<String> userNames = Lists.newArrayList();
    for (DBIdentity i : members) {
      userNames.add(i.getName());
    }
    setAdminInfo();
    addPendingGroupToRequest(groupName, userNames);
    getAddPendingResponse(testIdentity2);
    assertEquals(0, resp.diffs.size());

    // TODO: test failure when using testIdentity2
    getGetPendingResponse(testIdentity);
    compareDiffs(resp.diffs, getresp.diffs);
    assertTrue(getresp.pendingDeletions.isEmpty());
    assertTrue(getresp.pendingAdditionsAndModifications.isEmpty());
    assertTrue(getresp.deletedOrgMembers.isEmpty());
    assertTrue(getresp.newOrgMembers.isEmpty());

    // TODO noops have no nonce; this is weird.
    //assertEquals(resp.syncNonce, getresp.syncNonce);
  }
  
  @Test
  public void testModifyingGroupsFromOrg() throws CyclicGroupError, SQLException, MitroServletException, IOException {
    DBGroup oldGroup = createSyncedGroup(OLDGROUP2, SCOPE1, members, org);
    rqst.pendingGroups = Lists.newArrayList();
    String groupName = OLDGROUP2; 
    List<String> userNames = Lists.newArrayList();
    int i = 0;
    int NUM_MEMBERS_TO_ADD = 5;
    for (DBIdentity user : members) {
      userNames.add(user.getName());
      if (++i >= NUM_MEMBERS_TO_ADD) {
        break;
      }
    }
    userNames.add("anon@anon.com");
    // TODO add an unchanged group and check that it's still there.
    setAdminInfo();
    addPendingGroupToRequest(groupName, userNames);
    getAddPendingResponse(testIdentity2);
    assertEquals(1, resp.diffs.size());
    
    assertFalse(resp.diffs.containsKey(OLDGROUP1));    
    assertTrue(resp.diffs.containsKey(OLDGROUP2));
    assertEquals(GroupModificationType.MEMBERSHIP_MODIFIED, resp.diffs.get(OLDGROUP2).groupModification);
    assertEquals("anon@anon.com", resp.diffs.get(OLDGROUP2).newUsers.get(0));
    assertEquals(members.size() - (userNames.size() - 1), resp.diffs.get(OLDGROUP2).deletedUsers.size());
    getGetPendingResponse(testIdentity);
    
    compareDiffs(resp.diffs, getresp.diffs);
    assertTrue(getresp.pendingDeletions.isEmpty());
    assertEquals(1, getresp.pendingAdditionsAndModifications.size());
    assertEquals(oldGroup.getId(), getresp.pendingAdditionsAndModifications.get(0).matchedGroup.groupId);
    assertFalse(getresp.pendingAdditionsAndModifications.get(0).memberListJson.isEmpty());
    assertEquals(resp.syncNonce, getresp.syncNonce);
    assertEquals(getresp.orgId, org.getId());
    
    // all non-synced org users will be listed for delete.
    assertEquals((admins.size() + members.size() - NUM_MEMBERS_TO_ADD), getresp.deletedOrgMembers.size());
    assertEquals(1, getresp.newOrgMembers.size());
    assertTrue(getresp.newOrgMembers.contains("anon@anon.com"));
  }

  @Test
  public void testAddNoGroups() throws MitroServletException, IOException, SQLException, CyclicGroupError {
    // push a sync request that has no groups. This should work: a domain can
    // have no groups, or could have a group then have them all removed
    // TODO: Test that removing all groups causes them to get deleted
    setAdminInfo();
    rqst.pendingGroups = new ArrayList<>();
    getAddPendingResponse(testIdentity);
  }
}
