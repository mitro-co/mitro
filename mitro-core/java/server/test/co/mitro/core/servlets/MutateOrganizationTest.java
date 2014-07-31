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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import co.mitro.core.accesscontrol.AuthenticatedDB;
import co.mitro.core.exceptions.MitroServletException;
import co.mitro.core.exceptions.UserVisibleException;
import co.mitro.core.server.data.DBAcl;
import co.mitro.core.server.data.DBAcl.CyclicGroupError;
import co.mitro.core.server.data.DBGroup;
import co.mitro.core.server.data.DBGroupSecret;
import co.mitro.core.server.data.DBIdentity;
import co.mitro.core.server.data.DBServerVisibleSecret;
import co.mitro.core.server.data.RPC.CreateOrganizationRequest.PrivateGroupKeys;
import co.mitro.core.server.data.RPC.MutateOrganizationRequest;
import co.mitro.core.server.data.RPC.MutateOrganizationResponse;
import co.mitro.core.servlets.MitroServlet.MitroRequestContext;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.j256.ormlite.stmt.DeleteBuilder;

public class MutateOrganizationTest extends OrganizationsFixture {
  
  protected MutateOrganizationResponse resp;
  protected MutateOrganizationRequest rqst;
  protected MutateOrganization mutate;
  
  // NB: The @Before methods of superclasses will be run before those of the current class. 
  // No other ordering is defined.
  @Before 
  public void mutateSetup() {
    rqst = new MutateOrganizationRequest();
    rqst.promotedMemberEncryptedKeys = Maps.newHashMap();
    rqst.orgId = org.getId();
    rqst.newMemberGroupKeys = Maps.newHashMap();
    mutate = new MutateOrganization(managerFactory, keyFactory);
  }

  private void expectException(DBIdentity identity, String substr) throws IOException, SQLException {
    try {
      getMutateResponse(identity);
      fail("expected exception");
    } catch (MitroServletException expected) {
      if (null != substr) {
        assertThat(expected.getMessage().toLowerCase(), containsString(substr.toLowerCase()));
      }
    }
  }
  
  private void getMutateResponse(DBIdentity identity) throws MitroServletException, IOException, SQLException {
    resp = (MutateOrganizationResponse) mutate.processCommand(
        new MitroRequestContext(identity, gson.toJson(rqst), manager, null));
  }

  @Test
  public void testAdminRemovalFailures() throws SQLException, MitroServletException, IOException {

    // bad admin removal requests
    // try to remove a non-existent user 
    rqst.adminsToDemote = ImmutableList.of("unknown_user@example.com");
    expectException(testIdentity, "are not admins and could not be deleted");
    
    // try to remove a non-existent user and a real one
    rqst.adminsToDemote = ImmutableList.of("unknown_user@example.com", testIdentity.getName());
    expectException(testIdentity, "are not admins and could not be deleted");

    // try to remove a member who isn't an admin
    rqst.adminsToDemote = ImmutableList.of(testIdentity2.getName());
    expectException(testIdentity, "are not admins and could not be deleted");

    // try to remove a member who isn't an admin
    rqst.adminsToDemote = ImmutableList.of("unkonwn", testIdentity2.getName());
    expectException(testIdentity, "are not admins and could not be deleted");
  }
  
  @Test
  public void testMemberRemovalFailures() throws SQLException, MitroServletException, IOException {    
    // bad member removal requests
    // try to remove a non-existent user 
    rqst.membersToRemove = ImmutableList.of("unknown_user@example.com");
    expectException(testIdentity, "Invalid members to remove");
    
    // try to remove a non-existent user and a real one
    rqst.membersToRemove = ImmutableList.of("unknown_user@example.com", testIdentity2.getName());
    expectException(testIdentity, "Invalid members to remove");

    // try to remove a member is an admin without removing him as an admin
    rqst.membersToRemove = ImmutableList.of(testIdentity.getName());
    expectException(testIdentity, "Cannot remove members who are admins");

    rqst.membersToRemove = ImmutableList.of(testIdentity.getName(), testIdentity2.getName());
    expectException(testIdentity, "Cannot remove members who are admins");

    rqst.membersToRemove = ImmutableList.of("unknown", testIdentity.getName());
    expectException(testIdentity, "Invalid members to remove");
  }

  @Test
  public void removeAndPromoteMember() throws IOException, SQLException {
    // remove a member and promote them at the same time
    DBIdentity member = members.iterator().next();
    rqst.membersToRemove = ImmutableList.of(member.getName());
    rqst.promotedMemberEncryptedKeys.put(member.getName(), "org key encrypted for identity");
    expectException(testIdentity, "Cannot add admins without them being members");
  }

  @Test
  public void testUnprivilegedRemovalFailures() throws SQLException, MitroServletException, IOException {    
    // bad member removal requests
    // try to remove a non-existent user 
    rqst.membersToRemove = ImmutableList.of(testIdentity2.getName());
    expectException(testIdentity2, "no access");
    
    rqst.membersToRemove = ImmutableList.of("moo");
    expectException(testIdentity2, "no access");
  }
  
  @Test
  public void testRemoveAllAdminsFailure() throws SQLException, MitroServletException, IOException {
    rqst.adminsToDemote = Lists.newArrayList();
    for (DBIdentity a : admins) {
      rqst.adminsToDemote.add(a.getName());
    }

    // must be user visible
    try {
      getMutateResponse(testIdentity);
      fail("expected exception");
    } catch (UserVisibleException expected) {
      assertThat(expected.getUserVisibleMessage(), containsString("all admins"));
    }
  }
  
  @Test
  public void testAddAdminButNotUserFailure() throws SQLException, MitroServletException, IOException {
    rqst.promotedMemberEncryptedKeys.put(((DBIdentity)outsiders.toArray()[0]).getName(), "org key for admin");
    expectException(testIdentity, "cannot add admins without");
  }

  @Test
  public void testDemoteAdmins() throws SQLException, MitroServletException, IOException {
    rqst.adminsToDemote = ImmutableList.of(testIdentity.getName());
    getMutateResponse(testIdentity);
    admins.remove(testIdentity);
    
    // confusingly admins and members are exclusive -- they have the same meaning as the JS code.
    members.add(testIdentity);
    assertTrue(members.contains(testIdentity));
    checkAll();
  }
  
  @Test
  public void testPromoteUsers() throws SQLException, MitroServletException, IOException {
    DBIdentity member = members.iterator().next();
    rqst.promotedMemberEncryptedKeys.put(member.getName(), "org key for admin");
    getMutateResponse(testIdentity);
    admins.add(member);
    checkAll();
  }
  @Test
  public void testAddMember() throws SQLException, MitroServletException, IOException {
    DBIdentity newMember = outsiders.iterator().next();
    addNewMemberToRequest(newMember.getName());
    getMutateResponse(testIdentity);
    members.add(newMember);
    checkAll();
  }

  private void addNewMemberToRequest(String emailAddress) {
    PrivateGroupKeys memberKeys = new PrivateGroupKeys();
    memberKeys.publicKey = "pub key";
    memberKeys.keyEncryptedForUser = "userkey";
    memberKeys.keyEncryptedForOrganization = "orgkey";
    rqst.newMemberGroupKeys.put(emailAddress, memberKeys);
  }

  @Test
  public void testReAddMemberFailure() throws SQLException, MitroServletException, IOException {
    DBIdentity existingMember = members.iterator().next();
    addNewMemberToRequest(existingMember.getName());
    expectException(testIdentity, "duplicate members");
  }

  @Test
  public void testReAddAdminFailure() throws SQLException, MitroServletException, IOException {
    DBIdentity existingAdmin = admins.iterator().next();
    rqst.promotedMemberEncryptedKeys.put(existingAdmin.getName(), "org key for admin");
    expectException(testIdentity, "duplicate admins");
  }

  @Test
  public void testAddMemberAndAdmin() throws SQLException, MitroServletException, IOException {
    DBIdentity newMemberAndAdmin = outsiders.iterator().next();
    addNewMemberToRequest(newMemberAndAdmin.getName());
    rqst.promotedMemberEncryptedKeys.put(newMemberAndAdmin.getName(), "org key for admin");

    getMutateResponse(testIdentity);
    admins.add(newMemberAndAdmin);
    members.add(newMemberAndAdmin);
    checkAll();
  }

  @Test
  public void testMemberWithOtherGroupRemoval() throws SQLException, MitroServletException, IOException, CyclicGroupError {
    DBGroup namedOrgGroup = createGroupContainingIdentity(testIdentity);
    DBAcl acl = addToGroup(testIdentity2, namedOrgGroup, DBAcl.AccessLevelType.ADMIN);
    addOrgToGroup(manager, org, namedOrgGroup, DBAcl.AccessLevelType.ADMIN);
    rqst.adminsToDemote = Lists.newArrayList();
    rqst.membersToRemove = Lists.newArrayList();
    rqst.adminsToDemote.add(testIdentity.getName());
    rqst.membersToRemove.add(testIdentity.getName());
    getMutateResponse(testIdentity);
    
    // the named group must not have been deleted
    boolean found = false;
    for (DBGroup g : org.getAllOrgGroups(manager)) {
      if (g.getId() == namedOrgGroup.getId()) {
        found = true;
        break;
      }
    }
    assertTrue(found);
    
    // the user must not have access to the group
    namedOrgGroup = manager.groupDao.queryForId(namedOrgGroup.getId());
    for (DBAcl a : namedOrgGroup.getAcls()) {
      if (a.getMemberIdentityIdAsInteger() != null) {
        assertFalse(a.getMemberIdentityIdAsInteger().intValue() == testIdentity.getId());
      }
    }
    admins.remove(testIdentity);
    members.remove(testIdentity);
    checkAll();
  }

  @Test
  public void testMemberRemovePrivateGroup() throws SQLException, MitroServletException, IOException, CyclicGroupError {
    DBIdentity member = members.iterator().next();

    // find this member's private group
    DBGroup privateMemberGroup = getPrivateOrgGroup(org, member);
    assertEquals(2, privateMemberGroup.getAcls().size());
    // add a secret to this member's private group
    DBServerVisibleSecret secret = createSecret(privateMemberGroup, "client", "critical", org);

    // remove member from the organization
    rqst.membersToRemove = Lists.newArrayList(member.getName());
    getMutateResponse(testIdentity);

    // member's private group no longer exists
    assertNull(manager.groupDao.queryForId(privateMemberGroup.getId()));
    // the secret exists but only has 1 group secret (org membership for orphaned secret)
    secret = manager.svsDao.queryForId(secret.getId());
    assertEquals(1, secret.getGroupSecrets().size());
  }

  @Test
  public void emptyOrgMemberRemove() throws SQLException, MitroServletException, IOException, CyclicGroupError {
    // remove all secrets from the organization
    DeleteBuilder<DBGroupSecret, Integer> deleteBuilder = manager.groupSecretDao.deleteBuilder();
    deleteBuilder.where().in(DBGroupSecret.SVS_ID_NAME, orgSecret.getId(), orphanedOrgSecret.getId());
    assertEquals(4, deleteBuilder.delete());

    // remove member from an "empty" organization
    // had a bug where we attempted an IN query on an empty list
    rqst.membersToRemove = new ArrayList<>();
    rqst.membersToRemove.add(members.iterator().next().getName());
    getMutateResponse(testIdentity);
  }

  @SuppressWarnings("deprecation")
  @Test
  public void testMemberRemoveAccess() throws SQLException, MitroServletException, IOException, CyclicGroupError {
    Iterator<DBIdentity> it = members.iterator();
    DBIdentity member1 = it.next();
    DBIdentity member2 = it.next();

    // namedNonOrgGroup contains member and testIdentity2
    DBGroup namedNonOrgGroup = createGroupContainingIdentity(member1);
    addToGroup(testIdentity2, namedNonOrgGroup, DBAcl.AccessLevelType.ADMIN);

    // namedOrgGroup contains member1 and member2
    DBGroup namedOrgGroup = createGroupContainingIdentity(member1);
    addToGroup(member2, namedOrgGroup, DBAcl.AccessLevelType.ADMIN);
    addOrgToGroup(manager, org, namedOrgGroup, DBAcl.AccessLevelType.ADMIN);

    // Add org secret to both groups
    addSecretToGroup(orgSecret, namedOrgGroup, "client", "critical");
    addSecretToGroup(orgSecret, namedNonOrgGroup, "client", "critical");

    // all three have access to the secret
    AuthenticatedDB member1Db = AuthenticatedDB.deprecatedNew(manager, member1);
    AuthenticatedDB member2Db = AuthenticatedDB.deprecatedNew(manager, member2);
    AuthenticatedDB testIdentity2Db = AuthenticatedDB.deprecatedNew(manager, testIdentity2);
    assertEquals(orgSecret.getId(), member1Db.getSecretAsUser(orgSecret.getId()).getId());
    assertEquals(orgSecret.getId(), member2Db.getSecretAsUser(orgSecret.getId()).getId());
    assertEquals(orgSecret.getId(), testIdentity2Db.getSecretAsUser(orgSecret.getId()).getId());

    // remove member1 from the organization
    rqst.adminsToDemote = Lists.newArrayList();
    rqst.membersToRemove = Lists.newArrayList();
    rqst.adminsToDemote = Lists.newArrayList();
    rqst.membersToRemove.add(member1.getName());
    getMutateResponse(testIdentity);

    // member1 no longer has access. member2 has access via namedOrgGroup. testIdentity2 loses access
    assertNull(member1Db.getSecretAsUser(orgSecret.getId()));
    assertEquals(orgSecret.getId(), member2Db.getSecretAsUser(orgSecret.getId()).getId());
    assertNull(testIdentity2Db.getSecretAsUser(orgSecret.getId()));
  }

  private void checkAdmins() throws SQLException {
    Collection<DBAcl> acls = org.getAcls();
    Set<DBIdentity> actualAdmins = Sets.newHashSet();
    assertEquals(admins.size(), acls.size());
    for (DBAcl a : acls) {
      DBIdentity i = a.loadMemberIdentity(manager.identityDao);
      actualAdmins.add(i);
    }
    assertTrue((Sets.symmetricDifference(actualAdmins, admins)).isEmpty());
  }

  private void checkMembers() throws SQLException {
    Set<Integer> actualMemberIds = MutateOrganization.getMemberIdsAndPrivateGroupIdsForOrg(manager, org).keySet();
    Set<Integer> expectedMemberIds = Sets.newHashSet();
    for (DBIdentity i : members) {
      expectedMemberIds.add(i.getId());
    }
    for (DBIdentity i : admins) {
      expectedMemberIds.add(i.getId());
    }
    assertTrue((Sets.symmetricDifference(actualMemberIds, expectedMemberIds)).isEmpty());
  }

  public void checkAll() throws SQLException {
    checkAdmins();
    checkMembers();
  }
}
