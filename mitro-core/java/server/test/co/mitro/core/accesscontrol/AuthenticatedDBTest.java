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
package co.mitro.core.accesscontrol;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import co.mitro.core.exceptions.InvalidRequestException;
import co.mitro.core.exceptions.MitroServletException;
import co.mitro.core.exceptions.PermissionException;
import co.mitro.core.server.data.DBAcl;
import co.mitro.core.server.data.DBAcl.AccessLevelType;
import co.mitro.core.server.data.DBAcl.CyclicGroupError;
import co.mitro.core.server.data.DBGroup;
import co.mitro.core.server.data.DBIdentity;
import co.mitro.core.server.data.DBServerVisibleSecret;
import co.mitro.core.servlets.MemoryDBFixture;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;


public class AuthenticatedDBTest extends MemoryDBFixture {
  private AuthenticatedDB testIdentityDb;
  private AuthenticatedDB testIdentity2Db;
  private AuthenticatedDB testIdentity3Db;

  private DBGroup group;
  private List<DBAcl> acls;
  private DBGroup org;
  private DBIdentity testIdentity3;

  @SuppressWarnings("deprecation")
  @Before
  public void setUp() throws CyclicGroupError, IOException, SQLException, MitroServletException {
    testIdentityDb = AuthenticatedDB.deprecatedNew(manager, testIdentity);
    testIdentity2Db = AuthenticatedDB.deprecatedNew(manager, testIdentity2);
    testIdentity3 = createIdentity("user3@example.com", null);
    testIdentity3Db = AuthenticatedDB.deprecatedNew(manager, testIdentity3);

    // create an unsaved group, with a single ACL giving access to testIdentity
    group = new DBGroup();
    group.setName("group");
    group.setPublicKeyString("public key");
    acls = new ArrayList<>();
    DBAcl acl = makeAcl(testIdentity, group, AccessLevelType.ADMIN);
    acls.add(acl);

    // create org with testIdentity as an admin, and testIdentity2 as a member
    org = this.createOrganization(
        testIdentity, "org", Lists.newArrayList(testIdentity), Lists.newArrayList(testIdentity2));
  }
  
  @Test
  public void getOrganizationAsAdminSuccess() throws MitroServletException {
    assertEquals(testIdentityDb.getOrganizationAsAdmin(org.getId()).getId(), org.getId());
  }
  
  @Test
  public void getOrganizationAsAdminFail() {
    // not an admin
    try {
      testIdentity2Db.getOrganizationAsAdmin(org.getId());
      fail("exception expected");
    } catch (MitroServletException expected) {
      assertThat(expected.getMessage(), containsString("no access"));
    }

    // not part of the organization
    try {
      testIdentity3Db.getOrganizationAsAdmin(org.getId());
      fail("exception expected");
    } catch (MitroServletException expected) {
      assertThat(expected.getMessage(), containsString("no access"));
    }
  }

  @Test
  public void getOrganizationAsMemberSuccess() throws MitroServletException, SQLException {
    assertEquals(testIdentityDb.getOrganizationAsMember(org.getId()).getId(), org.getId());
    assertEquals(testIdentity2Db.getOrganizationAsMember(org.getId()).getId(), org.getId());
  }

  @Test
  public void getOrganizationAsMemberFail() throws SQLException {
    // not part of the organization
    try {
      testIdentity3Db.getOrganizationAsMember(org.getId());
      fail("exception expected");
    } catch (MitroServletException expected) {
      assertThat(expected.getMessage(), containsString("no access"));
    }
  }

  @Test
  public void getGroupOrOrgSuccess() throws MitroServletException, SQLException {
    assertEquals(testIdentityDb.getGroupOrOrg(org.getId()).getId(), org.getId());
    assertEquals(testIdentityDb.getGroupOrOrg(testGroup.getId()).getId(), testGroup.getId());
    assertEquals(testIdentity2Db.getGroupOrOrg(org.getId()).getId(), org.getId());
  }

  @Test
  public void getGroupAsUserOrOrgAdmin() throws SQLException, MitroServletException, CyclicGroupError {
    assertEquals(null, testIdentity3Db.getGroupAsUserOrOrgAdmin(testGroup.getId()));
    assertEquals(testGroup.getId(), testIdentityDb.getGroupAsUserOrOrgAdmin(testGroup.getId()).getId());

    // create a new org group.
    DBGroup newGroup = createGroupContainingIdentity(testIdentity2);
    addOrgToGroup(manager, org, newGroup, AccessLevelType.ADMIN);

    // testIdentity does not have direct access to the group.
    try {
      testIdentityDb.getGroupOrOrg(newGroup.getId());
      fail("exception expected");
    } catch (MitroServletException expected) {
      assertThat(expected.getMessage(), containsString("not in group"));
    }
    // testIdentity does have access to group indirectly.
    assertEquals(newGroup.getId(), testIdentityDb.getGroupAsUserOrOrgAdmin(newGroup.getId()).getId());

    // organization member can NOT remove the top level group
    assertEquals(null, testIdentity2Db.getGroupAsUserOrOrgAdmin(org.getId()));
  }

  @Test
  public void getGroupForAddSecret() throws SQLException, MitroServletException, CyclicGroupError {
    // organization member can add secrets to the top level group
    // this is the key difference from getGroupAsUserOrOrgAdmin
    assertEquals(org.getId(), testIdentity2Db.getGroupForAddSecret(org.getId()).getId());

    // verify identity2 can access a new org group (it is a member)
    DBGroup identity2OrgGroup = createGroupContainingIdentity(testIdentity2);
    addOrgToGroup(manager, org, identity2OrgGroup, AccessLevelType.ADMIN);
    assertEquals(identity2OrgGroup.getId(), testIdentity2Db.getGroupForAddSecret(identity2OrgGroup.getId()).getId());

    // identity2 can add secrets to any groups in the org (even if not a member)
    // TODO: This should work, but doesn't yet. Implement this and "shared org outsiders"
    DBGroup identity1OrgGroup = createGroupContainingIdentity(testIdentity);
    addOrgToGroup(manager, org, identity1OrgGroup, AccessLevelType.ADMIN);
    assertEquals(identity1OrgGroup.getId(), testIdentity2Db.getGroupForAddSecret(identity1OrgGroup.getId()).getId());
  }

  @Test
  public void getGroupForAddSecretAsOutsider() throws MitroServletException, SQLException, CyclicGroupError {
    // Create a group with id1 and id3
    DBGroup id1PrivateGroup = getPrivateOrgGroup(org, testIdentity);
    DBServerVisibleSecret secret = createSecret(id1PrivateGroup, "client", "critical", org); 
    DBGroup id1AndId3Group = createGroupContainingIdentity(testIdentity);
    addToGroup(testIdentity3, id1AndId3Group, AccessLevelType.ADMIN);

    // no access to the org: not a member
    try {
      testIdentity3Db.getGroupForAddSecret(org.getId());
      fail("expected exception");
    } catch (MitroServletException e) {
      assertThat(e.getMessage(), containsString("should not be able to see group"));
    }

    // share the secret
    addSecretToGroup(secret, id1AndId3Group, "client id3", "critical id3");
    assertEquals(org, testIdentity3Db.getGroupForAddSecret(org.getId()));
  }

  @Test
  public void getGroupForAddSecretFailures() throws SQLException, MitroServletException {
    // does not exist (previously caused an NPE)
    try {
      testIdentity2Db.getGroupForAddSecret(-1);
      fail("expected exception");
    } catch (MitroServletException e) {
      assertThat(e.getMessage(), containsString("should not be able to see group"));
    }
  }

  @Test
  public void getGroupOrOrgFailure() throws SQLException, PermissionException {
    try {
      testIdentity3Db.getGroupOrOrg(org.getId());
      fail("exception expected");
    } catch (MitroServletException expected) {
      assertThat(expected.getMessage(), containsString("not member of org"));
    }

    try {
      testIdentity3Db.getGroupOrOrg(testGroup.getId());
      fail("exception expected");
    } catch (MitroServletException expected) {
      assertThat(expected.getMessage(), containsString("not in group"));
    }
  }

  @Test
  public void createAdminSuccess() throws Exception {
    testIdentityDb.saveNewGroupWithAcls(group, acls);
  }

  @Test
  public void createModifySecretsSuccess() throws Exception {
    acls.get(0).setLevel(AccessLevelType.MODIFY_SECRETS_BUT_NOT_MEMBERSHIP);
    testIdentityDb.saveNewGroupWithAcls(group, acls);
  }

  @Test
  public void createGroupSuccess() throws Exception {
    acls.get(0).setMemberIdentity(null);
    acls.get(0).setMemberGroup(testGroup);
    testIdentityDb.saveNewGroupWithAcls(group, acls);
  }

  @Test
  public void createGroupInvalidArgs() throws Exception {
    // null arguments
    DBGroup originalGroup = group;
    List<DBAcl> originalAcls = acls;

    group = null;
    acls = originalAcls;
    expectNPE();
    group = originalGroup;
    acls = null;
    expectNPE();

    // null ACL
    acls = Lists.newArrayList((DBAcl) null);
    expectNPE();

    // empty ACLs
    acls = new ArrayList<>();
    expectInvalidRequest("acls must not be empty");
    acls = originalAcls;

    // acl for a null group
    acls.get(0).setGroup(null);
    expectNPE();
    acls.get(0).setGroup(group);

    // acl is not the right level
    acls.get(0).setLevel(AccessLevelType.READONLY);
    expectInvalidRequest("does not have permission to modify");
    acls.get(0).setLevel(AccessLevelType.ADMIN);

    // Add an ACL that doesn't reference anything
    DBAcl acl = makeAcl(testIdentity, group, AccessLevelType.ADMIN);
    acl.setMemberIdentity(null);
    acl.setMemberGroup(null);
    acls.add(acl);
    expectInvalidRequest("must apply to exactly one identity or group");

    // Add an ACL that references both a group and identity
    acl.setMemberIdentity(testIdentity2);
    acl.setMemberGroup(testGroup);
    expectInvalidRequest("must apply to exactly one identity or group");

    // 2 ACLs specifying the same identity
    acl.setMemberIdentity(testIdentity);
    acl.setMemberGroup(null);
    expectInvalidRequest("duplicate identity");

    // 2 ACLs specifying the same groups
    acls.get(0).setMemberIdentity(null);
    acls.get(0).setMemberGroup(testGroup);
    acls.get(1).setMemberIdentity(null);
    acls.get(1).setMemberGroup(testGroup);
    expectInvalidRequest("duplicate group");
  }

  @Test
  public void createGroupMissingReferences() throws Exception {
    // ACL references a user that doesn't exist
    // previously we only checked users with ADMIN access; must check all users
    DBAcl acl = makeAcl(testIdentity, group, AccessLevelType.READONLY);
    DBIdentity unsavedIdentity = new DBIdentity();
    unsavedIdentity.setId(100000);
    acl.setMemberIdentity(unsavedIdentity);
    acls.add(acl);
    expectInvalidRequest("some identities do not exist");
    acl.setLevel(AccessLevelType.ADMIN);
    expectInvalidRequest("some identities do not exist");

    // ACL references a group that doesn't exist
    DBGroup unsavedGroup = new DBGroup();
    unsavedGroup.setId(100000);
    acl.setMemberIdentity(null);
    acl.setMemberGroup(unsavedGroup);
    expectInvalidRequest("some groups are not accessible");
    // again doesn't matter about the access level
    acl.setLevel(AccessLevelType.READONLY);
    expectInvalidRequest("some groups are not accessible");

    // testIdentity does not have access to this group
    DBGroup otherGroup = createGroupContainingIdentity(testIdentity2);
    manager.groupDao.create(otherGroup);
    acl.setMemberIdentity(null);
    acl.setMemberGroup(otherGroup);
    expectInvalidRequest("some groups are not accessible");
  }

  @Test
  public void getSecretNoOrganization() throws SQLException, CyclicGroupError {
    // regular secrets, not part of an organization
    DBServerVisibleSecret id1Secret = createSecret(testGroup, "client", "critical", null);
    DBGroup group2 = createGroupContainingIdentity(testIdentity2);
    DBServerVisibleSecret id2Secret = createSecret(group2, "client", "critical", null);

    // testIdentity can't access id2Secret, but can access id1Secret
    assertNull(testIdentityDb.getSecretAsUser(id2Secret.getId()));
    assertNotNull(testIdentityDb.getSecretAsUser(id1Secret.getId()));
  }

  @Test
  public void getSecretOrganization() throws SQLException, CyclicGroupError {
    DBGroup id1PrivateGroup = getPrivateOrgGroup(org, testIdentity);
    DBServerVisibleSecret id1Secret = createSecret(
        id1PrivateGroup, "client", "critical", org);
    DBGroup id2PrivateGroup = getPrivateOrgGroup(org, testIdentity2);
    DBServerVisibleSecret id2Secret = createSecret(
        id2PrivateGroup, "client", "critical", org);

    // testIdentity can't access id2Secret, but can access id1Secret
    assertNull(testIdentityDb.getSecretAsUser(id2Secret.getId()));
    assertNotNull(testIdentityDb.getSecretAsUser(id1Secret.getId()));

    assertNotNull(testIdentity2Db.getSecretAsUser(id2Secret.getId()));
    assertNull(testIdentity2Db.getSecretAsUser(id1Secret.getId()));
  }

  @Test
  public void getSecretAsOrgAdmin() throws IOException, SQLException, MitroServletException, CyclicGroupError {
    List<DBIdentity> admins = ImmutableList.of(testIdentity3);
    List<DBIdentity> members = ImmutableList.of();
    DBGroup topLevelOrganization = createOrganization(
        testIdentity3, "Organization", admins, members);
    DBGroup g2 = createGroupContainingIdentity(testIdentity);
    addOrgToGroup(manager, topLevelOrganization, g2, AccessLevelType.ADMIN);

    DBServerVisibleSecret newSecret = createSecret(g2, "client", "critical", null);
    assertEquals(newSecret.getId(), testIdentity3Db.getSecretAsOrgAdmin(newSecret.getId()).getId());
    assertEquals(null, testIdentity2Db.getSecretAsOrgAdmin(newSecret.getId()));

  }

  
  
  private void expectInvalidRequest(String expectedSubstring)
      throws SQLException {
    expectException(InvalidRequestException.class, expectedSubstring);
  }

  private void expectNPE() {
    expectException(NullPointerException.class, null);
  }

  private void expectException(Class<? extends Exception> expectedType, String expectedSubstring) {
    try {
      testIdentityDb.saveNewGroupWithAcls(group, acls);
      fail("expected exception");
    } catch (Exception e) {
      assertThat(e, instanceOf(expectedType));
      if (expectedSubstring != null) {
        assertThat(e.getMessage(), containsString(expectedSubstring));
      }
    }
  }
}
