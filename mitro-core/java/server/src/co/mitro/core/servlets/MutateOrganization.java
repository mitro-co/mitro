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

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.annotation.WebServlet;

import co.mitro.core.accesscontrol.AuthenticatedDB;
import co.mitro.core.crypto.KeyInterfaces.KeyFactory;
import co.mitro.core.exceptions.MitroServletException;
import co.mitro.core.exceptions.UserVisibleException;
import co.mitro.core.server.Manager;
import co.mitro.core.server.ManagerFactory;
import co.mitro.core.server.data.DBAcl;
import co.mitro.core.server.data.DBAcl.CyclicGroupError;
import co.mitro.core.server.data.DBAudit;
import co.mitro.core.server.data.DBGroup;
import co.mitro.core.server.data.DBGroupSecret;
import co.mitro.core.server.data.DBIdentity;
import co.mitro.core.server.data.RPC;
import co.mitro.core.server.data.RPC.ListMySecretsAndGroupKeysResponse.SecretToPath;
import co.mitro.core.server.data.RPC.MitroRPC;
import co.mitro.core.server.data.RPC.MutateOrganizationResponse;
import co.mitro.core.servlets.ListMySecretsAndGroupKeys.AdminAccess;
import co.mitro.core.servlets.ListMySecretsAndGroupKeys.IncludeAuditLogInfo;

import com.google.common.base.Joiner;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.j256.ormlite.stmt.DeleteBuilder;


@WebServlet("/api/MutateOrganization")
public class MutateOrganization extends MitroServlet {
  private static final long serialVersionUID = 1L;
  private static final Joiner COMMA_JOINER = Joiner.on(",");

  public MutateOrganization(ManagerFactory managerFactory, KeyFactory keyFactory) {
    super(managerFactory, keyFactory);
  }
 
  @Override
  protected MitroRPC processCommand(MitroRequestContext context)
      throws IOException, SQLException, MitroServletException {
    try {
      RPC.MutateOrganizationRequest in = gson.fromJson(context.jsonRequest,
          RPC.MutateOrganizationRequest.class);

      in.promotedMemberEncryptedKeys = MitroServlet.createMapIfNull(in.promotedMemberEncryptedKeys);
      in.newMemberGroupKeys = MitroServlet.createMapIfNull(in.newMemberGroupKeys);
      in.adminsToDemote = MitroServlet.uniquifyCollection(in.adminsToDemote);
      in.membersToRemove = MitroServlet.uniquifyCollection(in.membersToRemove);
      

      @SuppressWarnings("deprecation")
      AuthenticatedDB userDb = AuthenticatedDB.deprecatedNew(context.manager, context.requestor);
      DBGroup org = userDb.getOrganizationAsAdmin(in.orgId);
      
      Set<String> adminsToDemote = Sets.newHashSet(in.adminsToDemote);
      Collection<DBAcl> aclsToRemove = new HashSet<>();
      Set<Integer> existingAdmins = new HashSet<>();
      for (DBAcl acl : org.getAcls()) {
        DBIdentity u = acl.loadMemberIdentity(context.manager.identityDao);
        assert (u != null); // toplevel groups should not have group members.
        if (adminsToDemote.contains(u.getName())) {
          aclsToRemove.add(acl);
          adminsToDemote.remove(u.getName());
        } else {
          existingAdmins.add(u.getId());
        }
      }
      // check for an attempt to promote members who are already admins.
      Set<Integer> duplicateAdmins = Sets.intersection(existingAdmins,
          DBIdentity.getUserIdsFromNames(context.manager, in.promotedMemberEncryptedKeys.keySet()));
      if (!duplicateAdmins.isEmpty()) {
        throw new MitroServletException("Operation would create duplicate admins: " +
            COMMA_JOINER.join(duplicateAdmins));
      }
      
      if (!adminsToDemote.isEmpty()) {
        throw new MitroServletException("The following users are not admins and could not be deleted:" + 
            COMMA_JOINER.join(adminsToDemote));
      }
      if (existingAdmins.isEmpty() && in.promotedMemberEncryptedKeys.isEmpty()) {
        throw new UserVisibleException("You cannot remove all admins from an organization");
      }
      
      // delete ACLs for the admin user on the group. This maybe should be using common code?
      context.manager.aclDao.delete(aclsToRemove);
      Map<Integer, Integer> currentMemberIdsToGroupIds =
          getMemberIdsAndPrivateGroupIdsForOrg(context.manager, org);

      // Promoted members (new admins) must be members after all changes
      Set<String> currentMembers =
          DBIdentity.getUserNamesFromIds(context.manager, currentMemberIdsToGroupIds.keySet());
      Set<String> membersAfterChanges = Sets.difference(
          Sets.union(currentMembers, in.newMemberGroupKeys.keySet()),
          new HashSet<>(in.membersToRemove));
      Set<String> nonMemberAdmins = Sets.difference(in.promotedMemberEncryptedKeys.keySet(),
          membersAfterChanges);
      if (!nonMemberAdmins.isEmpty()) {
        throw new MitroServletException("Cannot add admins without them being members: "
            + COMMA_JOINER.join(nonMemberAdmins));
      }

      // check for duplicate users
      Set<Integer> duplicateMembers = Sets.intersection(currentMemberIdsToGroupIds.keySet(),
          DBIdentity.getUserIdsFromNames(context.manager, in.newMemberGroupKeys.keySet()));
      if (!duplicateMembers.isEmpty()) {
        throw new MitroServletException("Operation would create duplicate members: "
            + COMMA_JOINER.join(duplicateMembers));
      }
      
      // delete all the private groups. This might orphan secrets, which is the intended result.
      Set<Integer> memberIdsToRemove = DBIdentity.getUserIdsFromNames(context.manager, in.membersToRemove);
      if (memberIdsToRemove.size() != in.membersToRemove.size()) {
        throw new MitroServletException("Invalid members to remove.");
      }
      
      Set<Integer> illegalRemovals = Sets.intersection(existingAdmins, memberIdsToRemove);
      if (!illegalRemovals.isEmpty()) {
        throw new MitroServletException("Cannot remove members who are admins:"
            + COMMA_JOINER.join(illegalRemovals));
      }
      Set<Integer> nonOrgUsers =  Sets.difference(memberIdsToRemove, currentMemberIdsToGroupIds.keySet());
      if (!nonOrgUsers.isEmpty()) {
        throw new MitroServletException("The following users are not members and cannot be removed:" 
            + COMMA_JOINER.join(nonOrgUsers));
      }
      Set<Integer> deleteGroupIds =
          Sets.newHashSet(Maps.filterKeys(currentMemberIdsToGroupIds, Predicates.in(memberIdsToRemove)).values());
      if (!deleteGroupIds.isEmpty()) {
        context.manager.groupDao.deleteIds(deleteGroupIds);
        DeleteBuilder<DBAcl, Integer> deleter = context.manager.aclDao.deleteBuilder();
        deleter.where().in(DBAcl.GROUP_ID_FIELD_NAME, deleteGroupIds);
        deleter.delete();

        DeleteBuilder<DBGroupSecret, Integer> gsDeleter = context.manager.groupSecretDao.deleteBuilder();
        gsDeleter.where().in(DBGroupSecret.GROUP_ID_NAME, deleteGroupIds);
        gsDeleter.delete();
      }
      
      // Remove the user from all org-owned group to which he belongs.
      // Note: if the user has access to an org-owned secret via a non-org-owned group, 
      // he will retain access. 
      Set<Integer> allOrgGroupIds = Sets.newHashSet();
      for (DBGroup g : org.getAllOrgGroups(context.manager)) {
        allOrgGroupIds.add(g.getId());
      }
      if (!memberIdsToRemove.isEmpty()) {
        if (!allOrgGroupIds.isEmpty()) {
          // Remove users from organization-owned groups (named or otherwise)
          DeleteBuilder<DBAcl, Integer> deleter = context.manager.aclDao.deleteBuilder();
          deleter.where().in(DBAcl.MEMBER_IDENTITY_FIELD_NAME, memberIdsToRemove)
              .and().in(DBAcl.GROUP_ID_FIELD_NAME, allOrgGroupIds);
          deleter.delete();
        }

        // Remove users from any org-owned secrets (e.g. via non-org private or named groups)
        HashMap<Integer, SecretToPath> orgSecretsToPath = new HashMap<>();
        ListMySecretsAndGroupKeys.getSecretInfo(context,
             AdminAccess.FORCE_ACCESS_VIA_TOPLEVEL_GROUPS, orgSecretsToPath,
             ImmutableSet.of(org.getId()), null, IncludeAuditLogInfo.NO_AUDIT_LOG_INFO);
        if (orgSecretsToPath.size() > 0) {
          // Delete any group secret giving these users access to org secrets
          // strange side effect: personal teams may be mysteriously removed from org secrets
          // TODO: Potential bug: removing the last personal team will "orphan" the secret
          String groupSecretDelete = String.format("DELETE FROM group_secret WHERE id IN ("
              + "SELECT group_secret.id FROM group_secret, acl WHERE "
              + "  group_secret.\"serverVisibleSecret_id\" IN (%s) AND "
              + "  group_secret.group_id = acl.group_id AND acl.member_identity IN (%s))",
              COMMA_JOINER.join(orgSecretsToPath.keySet()), COMMA_JOINER.join(memberIdsToRemove));
          context.manager.groupSecretDao.executeRaw(groupSecretDelete);
        }
      }

      List<DBAcl> organizationAcls = CreateOrganization.makeAdminAclsForOrganization(userDb, org, in.promotedMemberEncryptedKeys);
      
      // TODO: move to authdb?
      for (DBAcl acl : organizationAcls) {
        context.manager.aclDao.create(acl);
      }

      // create private groups for each new member
      CreateOrganization.addMembersToOrganization(userDb, org, in.newMemberGroupKeys, context.manager);
      
      context.manager.addAuditLog(
          DBAudit.ACTION.MUTATE_ORGANIZATION, null, null, org, null, "");
      
      MutateOrganizationResponse out = new RPC.MutateOrganizationResponse();
      // TODO: validate the group?
      return out;
      
    } catch (CyclicGroupError e) {
      throw new MitroServletException(e);
    }
  }

  public static Map<Integer, Integer> getMemberIdsAndPrivateGroupIdsForOrg(
      Manager manager, DBGroup org) throws SQLException {
    String getMembersAndPrivateGroupsQuery = 
        "SELECT " +
            // MAX is safe to use here since we require a group membership and a count of 2, so 
            // there is only one possible value.
            "MAX(counter.member_identity), counter.group_id " +
        "FROM acl as pvtgroup " +
        "JOIN acl as counter ON (counter.group_id = pvtgroup.group_id) " +
        "JOIN groups ON (counter.group_id = groups.id) " +
        "WHERE pvtgroup.group_identity = " + org.getId() + 
            " AND groups.type = 'PRIVATE' " +
        "GROUP BY counter.group_id " + 
        "HAVING count(counter.group_id) = 2";
    Map<Integer, Integer> rval = Maps.newHashMap();
    List<String[]> membersAndPrivateGroupsResults =
        Lists.newArrayList(manager.groupDao.queryRaw(getMembersAndPrivateGroupsQuery));
    for (String[] cols : membersAndPrivateGroupsResults) {
      rval.put(Integer.parseInt(cols[0], 10), Integer.parseInt(cols[1], 10));
    }
    return rval;
  }
  
}
