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

import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import co.mitro.core.crypto.KeyInterfaces.CryptoError;
import co.mitro.core.crypto.KeyInterfaces.KeyFactory;
import co.mitro.core.crypto.KeyInterfaces.PublicKeyInterface;
import co.mitro.core.exceptions.InvalidRequestException;
import co.mitro.core.exceptions.MitroServletException;
import co.mitro.core.exceptions.PermissionException;
import co.mitro.core.server.Manager;
import co.mitro.core.server.data.DBAcl;
import co.mitro.core.server.data.DBAcl.AccessLevelType;
import co.mitro.core.server.data.DBGroup;
import co.mitro.core.server.data.DBGroupSecret;
import co.mitro.core.server.data.DBIdentity;
import co.mitro.core.server.data.DBServerVisibleSecret;
import co.mitro.core.server.data.RPC.SignedRequest;
import co.mitro.core.servlets.ListMySecretsAndGroupKeys.AdminAccess;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.Where;

/**
 * Provides access to data at the access level of the user making the request. This is intended
 * to be the single location where all access control checks will be enforced. This code must
 * be reviewed carefully, but it should be very difficult for callers to introduce access control
 * errors.
 */
public class AuthenticatedDB {
  private final Manager manager;
  private final DBIdentity identity;

  private AuthenticatedDB(Manager manager, DBIdentity identity) {
    Preconditions.checkNotNull(manager);
    Preconditions.checkNotNull(identity);
    this.manager = manager;
    this.identity = identity;
  }

  /** Returns a new AuthenticatedDb, if the request is valid. */
  public static AuthenticatedDB newFromRequest(
      Manager manager, KeyFactory keyFactory, SignedRequest request)
      throws SQLException, MitroServletException {
    if (request.identity == null) {
      return null;
    }

    DBIdentity identity = DBIdentity.getIdentityForUserName(manager, request.identity);
    if (identity == null) {
      return null;
    }

    // the identity exists: check the request!
    try {
      PublicKeyInterface key = keyFactory.loadPublicKey(identity.getPublicKeyString());
      if (!key.verify(request.request, request.signature)) {
        throw new MitroServletException(
            "failed to verify signature for identity " + identity.getName());
      }
    } catch (CryptoError e) {
      throw new MitroServletException("failed to load key for identity " + identity.getName(), e);
    }

    return new AuthenticatedDB(manager, identity);
  }

  /** Hack for compatibility with existing code. We should only use {@link #newFromRequest}. */
  @Deprecated
  public static AuthenticatedDB deprecatedNew(
      Manager manager, DBIdentity identity) {
    return new AuthenticatedDB(manager, identity);
  }

  public boolean isOrganizationAdmin(int orgId) throws MitroServletException {
    try {
      DBGroup group = getDirectlyAccessibleGroup(orgId);
      if (null == group) {
        return false;
      }
      assert (group.isTopLevelOrganization());
      return true;
    } catch (SQLException e) {
      throw new MitroServletException(e);
    }
  }
  
  public DBGroup getOrganizationAsAdmin(int orgId) throws MitroServletException  {
    DBGroup orgGroup;
    try {
      orgGroup = getDirectlyAccessibleGroup(orgId);
    } catch (SQLException e) {
      throw new MitroServletException(e);
    }
    if (null == orgGroup || !orgGroup.isTopLevelOrganization()) {
      throw new MitroServletException("Not org or no access");
    }
    return orgGroup;
  }

  /**
   * Returns the DBGroup for orgId if it is an organization and the user is a member.
   * @throws MitroServletException if orgId does not exist, is not an organization, or the user
   *    is not a member.
   */
  public DBGroup getOrganizationAsMember(int orgId) throws MitroServletException, SQLException {
    Set<DBGroup> orgs = getOrganizations();
    for (DBGroup org : orgs) {
      if (org.getId() == orgId) {
        return org;
      }
    }

    // TODO: Should this and getOrganizationAsAdmin return null on error instead?
    throw new MitroServletException("Not org or no access");
  }

  public DBGroup getGroupAsUserOrOrgAdmin(int groupId) throws SQLException, MitroServletException {
    final DBGroup rval = getDirectlyAccessibleGroup(groupId);
    if (rval != null) {
      return rval;
    } 
    // try to get the group indirectly.
    final DBGroup actualGroup = manager.groupDao.queryForId(groupId);
    for (DBAcl acl : actualGroup.getAcls()) {
      Integer memberGroup = acl.getMemberGroupIdAsInteger();
      if (memberGroup != null) {
        if (isOrganizationAdmin(memberGroup)) {
          return actualGroup;
        }
      }
    }
    return null;
  }

  public DBGroup getGroupForAddSecret(int groupId) throws SQLException, MitroServletException {
    // if we are in the group: we can add to the group
    try {
      return getGroupOrOrg(groupId);
    } catch (PermissionException pe) {
      // check if we have access as an admin of the organization this group
      // we might still get access to this secret due to being a member of an org that this group belongs to.
      // This works for org admins as well.
      DBGroup rval = manager.groupDao.queryForId(groupId);
      Set<DBGroup> orgs = this.getOrganizations();
      Set<Integer> orgIds = Sets.newHashSet();
      for (DBGroup org: orgs) {
        orgIds.add(org.getId());
      }
      if (rval != null) {
        for (DBAcl acl : rval.getAcls()) {
          if (orgIds.contains(acl.getMemberGroupIdAsInteger())) {
            return rval;
          }
        }

        // permit access if requestor is an admin of a SECRET in this group, so they can edit it.
        // TODO: Add an EditSecret API and forbid access if requestor is not in group!

        // find all secrets in group
        boolean isAdminForSecretInGroup = false;
        boolean isOrgAdminForSecretInGroup = false;

        for (DBGroupSecret secretInGroup : rval.getGroupSecrets()) {
          DBServerVisibleSecret secret = secretInGroup.getServerVisibleSecret();
          // test secret to see if requestor can edit it
          AccessLevelType accessLevel = identity.getHighestAccessLevel(manager, secret);
          if (accessLevel != null && accessLevel.canEditSecret()) {
            // the requestor is an editor for secret, which is in group
            isAdminForSecretInGroup = true;
            break;
          } else {
            if (null != getSecretAsOrgAdmin(secret.getId())) {
              isOrgAdminForSecretInGroup = true;
              break;
            }
          }
        }

        if (isAdminForSecretInGroup || isOrgAdminForSecretInGroup) {
          return rval;
        }
      }

      throw new MitroServletException("User should not be able to see group: " + groupId, pe);
    }
  }

  /**
   * 
   * This returns a group OR org that a user has access to. The group is returned
   * IFF (the user is a member of the group) OR (the group is a top-level org of which the user is a member)
   * @param groupId group id
   * @return the group if it's accessible and it exists
   * @throws PermissionException the user does not have the ability to access the org or the org does not exist.
   */
  public DBGroup getGroupOrOrg(int groupId) throws PermissionException, SQLException {
    DBGroup orgGroup = getDirectlyAccessibleGroup(groupId);
    if (null != orgGroup) {
      // direct membership
      return orgGroup;
    }
    orgGroup = manager.groupDao.queryForId(groupId);
    if (orgGroup != null && orgGroup.isTopLevelOrganization() && getOrganizations().contains(orgGroup)) {
      // access via org membership
      return orgGroup;
    }
    throw new PermissionException("group does not exist or user not in group or not member of org (" + groupId + ")");
  }
  
  /** Returns the group represented by groupId, if this identity has direct access to it. */
  private DBGroup getDirectlyAccessibleGroup(int groupId) throws SQLException {
    // Query for acls referencing (groupId, requesting identity)
    List<DBGroup> groups = getDirectlyAccessibleGroups(ImmutableList.of(groupId));
    assert(groups.size() == 0 || groups.size() == 1);
    if (groups.size() == 0) {
      return null;
    } else {
      assert groups.size() == 1;
      assert groups.get(0) != null;
      return groups.get(0);
    }
  }

  /**
   * Saves a new group with new acls, if this user has permission and the group and ACLs
   * are valid. This will throw an exception if the group and ACLs already exist.
   */
  public void saveNewGroupWithAcls(DBGroup group, List<DBAcl> acls)
      throws InvalidRequestException, SQLException {
    // verify that the acls actually apply correctly to group
    validateGroupAcls(group, acls);

    // The requestor must be able to edit secrets in the new group
    Set<Integer> groupAdmins = getFirstLevelUsers(acls, DBAcl.modifyGroupSecretsAccess());
    if (!groupAdmins.contains(identity.getId())) {
      // We cannot allow a group to be created where the requestor cannot modify
      // group secrets.
      throw new InvalidRequestException("Cannot create group where requestor " +
          identity.getName() + " does not have permission to modify group secrets");
    }

    // save the group and the acls
    manager.groupDao.create(group);
    for (DBAcl acl : acls) {
      manager.aclDao.create(acl);
    }
    
  }

  /** Returns the organizations the requestor can access. */
  public Set<DBGroup> getOrganizations() throws SQLException {
    HashSet<DBGroup> organizations = new HashSet<>();

    for (DBGroup group : getAllDirectlyAccessibleGroups()) {
      if (group.isPrivateUserGroup()) {
        // private groups must either be owned by only the user or belong to an organization
        for (DBAcl acl : group.getAcls()) {
          assert DBAcl.modifyGroupSecretsAccess().contains(acl.getLevel());
          if (acl.getMemberGroupId() != null) {
            // any group members of private groups must be organizations
            DBGroup organization = acl.loadMemberGroup(manager.groupDao);
            assert organization.isTopLevelOrganization();
            organizations.add(organization);
          } else {
            assert acl.getMemberIdentityId().getId() == identity.getId();
          }
        }
      }
    }

    return organizations;
  }

  public DBIdentity getIdentity(String name) throws SQLException {
    // TODO: Move this method here?
    return DBIdentity.getIdentityForUserName(manager, name);
  }

  /** Returns the secret if the requestor has regular user access. */
  public DBServerVisibleSecret getSecretAsUser(int secretId) throws SQLException {
    return getSecretAsUser(secretId, DBAcl.allAccessTypes());
  }
  public DBServerVisibleSecret getSecretAsUser(int secretId, Collection<DBAcl.AccessLevelType> accessLevels) 
      throws SQLException {
    DBServerVisibleSecret rval = manager.svsDao.queryForId(secretId);
    if (rval == null) {
      return rval;
    }
    // check permissions.
    if (rval.getAllUserIdsWithAccess(manager, accessLevels, AdminAccess.IGNORE_ACCESS_VIA_TOPLEVEL_GROUPS).contains(this.identity.getId())) {
      return rval;
    }
    return null;
  }
  
  public DBServerVisibleSecret getSecretAsUserOrAdmin(int secretId, Collection<DBAcl.AccessLevelType> accessLevels) throws SQLException {
    DBServerVisibleSecret rval = getSecretAsUser(secretId, accessLevels);
    if (rval == null) {
      rval = getSecretAsOrgAdmin(secretId);
    }
    return rval;
  }
  
  /** Returns the secret if the requestor has admin access. */
  public DBServerVisibleSecret getSecretAsOrgAdmin(int secretId) throws SQLException {
    Set<DBGroup> orgs = getOrganizations();
    if (orgs.isEmpty()) {
      return null;
    }
    Set<Integer> groupIds = Sets.newHashSet();
    for (DBGroup org : orgs) {
      org = getDirectlyAccessibleGroup(org.getId());
      if (org == null) {
        // you are not an org admin
        continue;
      }
      groupIds.add(org.getId());
      for (DBGroup g : org.getAllOrgGroups(manager)) {
        groupIds.add(g.getId());
      }

    }
    if (groupIds.isEmpty()) {
      return null;
    }
    // if the secret is shared with any of the groups, return it
     List<DBGroupSecret> groupSecrets = manager.groupSecretDao.queryBuilder().where().eq(DBGroupSecret.SVS_ID_NAME, secretId)
        .and().in(DBGroupSecret.GROUP_ID_NAME, groupIds).query();
     if (groupSecrets.isEmpty()) {
       return null;
     }
     // any access path will do.
     return groupSecrets.get(0).getServerVisibleSecret();
  }
  /**
   * Throws an exception if the ACL list is not valid:
   * - must not be empty
   * - must apply to targetGroup.
   * - members must be an identity OR a group, not both.
   * - identities must already exist in the database.
   * - groups must be accessible by this user and exist.
   * - no duplicate users or groups
   */
  private void validateGroupAcls(DBGroup targetGroup, List<DBAcl> acls)
      throws InvalidRequestException, SQLException {
    if (acls.isEmpty()) {
      throw new InvalidRequestException("acls must not be empty");
    }

    HashSet<Integer> uniqueMemberIdentities = new HashSet<>();
    HashSet<Integer> uniqueMemberGroups = new HashSet<>();
    for (DBAcl acl : acls) {
      // must apply to targetGroup
      if (acl.getGroupId().getId() != targetGroup.getId()) {
        throw new InvalidRequestException("acl applies to the wrong group");
      }

      boolean isIdentityAcl = acl.getMemberIdentityId() != null;
      boolean isGroupAcl = acl.getMemberGroupId() != null;
      if (!(isIdentityAcl ^ isGroupAcl)) {
        throw new InvalidRequestException("acl must apply to exactly one identity or group; " +
            "specifies identity: " + isIdentityAcl + "; specifies group: " + isGroupAcl);
      }

      if (isIdentityAcl) {
        assert !isGroupAcl;
        if (!uniqueMemberIdentities.add(acl.getMemberIdentityId().getId())) {
          throw new InvalidRequestException(
              "duplicate identity: " + acl.getMemberIdentityId().getId());
        }
      } else {
        assert !isIdentityAcl;
        if (!uniqueMemberGroups.add(acl.getMemberGroupId().getId())) {
          throw new InvalidRequestException("duplicate group: " + acl.getMemberGroupId().getId());
        }
      }
    }

    if (!uniqueMemberIdentities.isEmpty()) {
      // verify all referenced identities exist
      if (DBIdentity.getUserNamesFromIds(manager, uniqueMemberIdentities).size() != uniqueMemberIdentities.size()) {
        throw new InvalidRequestException("some identities do not exist");
      }
    }

    if (!uniqueMemberGroups.isEmpty()) {
      // verifies all referenced groups are accessible
      QueryBuilder<DBGroup, Integer> groupQuery = getDirectlyAccessibleGroupsQuery(uniqueMemberGroups);
      if (groupQuery.countOf() != uniqueMemberGroups.size()) {
        throw new InvalidRequestException("some groups are not accessible");
      }
    }
  }

  /** Returns all groups where requestor is a direct member. */
  private List<DBGroup> getAllDirectlyAccessibleGroups() throws SQLException {
    QueryBuilder<DBGroup, Integer> groupQuery = getDirectlyAccessibleGroupsQuery(null);
    return groupQuery.query();
  }

  /** Returns the DBGroups for groupIds if they are directly accessible (at any level). */
  private List<DBGroup> getDirectlyAccessibleGroups(Collection<Integer> groupIds) throws SQLException {
    if (groupIds.isEmpty()) {
      // Postgres can't parse empty IN: "columnName" IN ()
      return Collections.emptyList();
    }

    QueryBuilder<DBGroup, Integer> groupQuery = getDirectlyAccessibleGroupsQuery(groupIds);
    return manager.groupDao.query(groupQuery.prepare());
  }

  private QueryBuilder<DBGroup, Integer> getDirectlyAccessibleGroupsQuery(
      Collection<Integer> optionalGroupIdFilter) throws SQLException {
    return getDirectlyAccessibleGroupsQuery(manager, optionalGroupIdFilter, identity.getId(), null);
  }
  
  /**
   * Returns a query for groups where requestor is a direct member, optionally filtered to 
   * only groups in optionalGroupIdFilter.
   */
  public static QueryBuilder<DBGroup, Integer> getDirectlyAccessibleGroupsQuery(
      Manager manager,
      Collection<Integer> optionalGroupIdFilter, Integer memberIdentityId, 
      Integer memberGroupId) throws SQLException {

    assert (null == memberGroupId ^ null == memberIdentityId) : 
      "set either identity or group but not both";
    
    // get ACLs matching (group=*, member=requesting identity)
    QueryBuilder<DBAcl, Integer> aclQuery = manager.aclDao.queryBuilder();
    Where<DBAcl, Integer> whereClause = null;
    if (memberIdentityId != null) { 
      whereClause = aclQuery.where().eq(DBAcl.MEMBER_IDENTITY_FIELD_NAME, memberIdentityId);
    } else {
      whereClause = aclQuery.where().eq(DBAcl.MEMBER_GROUP_FIELD_NAME, memberGroupId);
    }
    if (optionalGroupIdFilter != null) {
      // get ACLs matching (group_id in optionalGroupIdFilter, member_id=requesting identity)
      // Postgres/SQL doesn't support empty IN queries
      assert !optionalGroupIdFilter.isEmpty();
      whereClause.and().in(DBAcl.GROUP_ID_FIELD_NAME, optionalGroupIdFilter);
    }

    // return the actual groups themselves
    // NOTE: OrmLite's .join() just takes the "first" field, which is fragile!
    aclQuery.selectColumns(DBAcl.GROUP_ID_FIELD_NAME);
    QueryBuilder<DBGroup, Integer> groupQuery = manager.groupDao.queryBuilder();
    groupQuery.where().in(DBGroup.ID_FIELD_NAME, aclQuery);
    return groupQuery;
  }

  
  /**
   * Returns users included in acls at a level in includedAccessLevels, loading one level of group
   * members. Throws if the requestor is not a member of one of the referenced groups.
   */
  private Set<Integer> getFirstLevelUsers(List<DBAcl> acls,
      Set<AccessLevelType> includedAccessLevels) throws SQLException, InvalidRequestException {
    HashSet<Integer> users = new HashSet<Integer>();
    HashSet<Integer> groups = new HashSet<Integer>();
    for (DBAcl acl : acls) {
      if (includedAccessLevels.contains(acl.getLevel())) {
        if (null != acl.getMemberIdentityId()) {
          users.add(acl.getMemberIdentityId().getId());
        } else {
          // must be a member group
          groups.add(acl.getMemberGroupId().getId());
        }
      }
    }

    // get users from all accessible groups
    List<DBGroup> accessibleGroups = getDirectlyAccessibleGroups(groups);
    if (accessibleGroups.size() != groups.size()) {
      throw new InvalidRequestException("not all groups are accessible");
    }
    for (DBGroup accessibleGroup : accessibleGroups) {
      accessibleGroup.putDirectUsersIntoSet(users, includedAccessLevels);
    }

    return users;
  }

  public DBServerVisibleSecret getServerSecretForViewOrEdit(int secretId) throws SQLException, MitroServletException {
    DBServerVisibleSecret svs = getSecretAsOrgAdmin(secretId);
    if (svs == null) {
      return null;
    }
    
    // we may have been granted access to the secret via a non-admin path. 
    // only return the secret if we have access via org admin only.
    for (DBGroupSecret gs : svs.getGroupSecrets()) {
      DBGroup group = gs.getGroup();
      manager.groupDao.refresh(group);
      if (group.isTopLevelOrganization() && isOrganizationAdmin(group.getId())) {
        return svs;
      }
    }
    return null;
  }
}
