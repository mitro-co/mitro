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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.servlet.annotation.WebServlet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.mitro.core.accesscontrol.AuthenticatedDB;
import co.mitro.core.exceptions.DoEmailVerificationException;
import co.mitro.core.exceptions.MitroServletException;
import co.mitro.core.server.Manager;
import co.mitro.core.server.data.DBAudit;
import co.mitro.core.server.data.DBGroup;
import co.mitro.core.server.data.DBServerVisibleSecret;
import co.mitro.core.server.data.RPC;
import co.mitro.core.server.data.RPC.ListMySecretsAndGroupKeysResponse;
import co.mitro.core.server.data.RPC.ListMySecretsAndGroupKeysResponse.GroupInfo;
import co.mitro.core.server.data.RPC.ListMySecretsAndGroupKeysResponse.SecretToPath;
import co.mitro.core.server.data.RPC.MitroRPC;

import com.google.common.base.Joiner;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.common.collect.TreeMultimap;
import com.j256.ormlite.dao.RawRowMapper;


@WebServlet("/api/ListMySecretsAndGroupKeys")
public class ListMySecretsAndGroupKeys extends MitroServlet {
  private static final long serialVersionUID = 1L;
  private static final Logger logger = LoggerFactory
      .getLogger(ListMySecretsAndGroupKeys.class);

  @Override
  protected MitroRPC processCommand(MitroRequestContext context) throws IOException, SQLException, MitroServletException {
    //Gson gson = new GsonBuilder().setPrettyPrinting().create();
    //MitroRPC oldOut = oldProcessCommand(context);
    RPC.ListMySecretsAndGroupKeysRequest in = gson.fromJson(context.jsonRequest,
        RPC.ListMySecretsAndGroupKeysRequest.class);
    // TODO: Remove this once the client code no longer sends this id
    @SuppressWarnings("deprecation")
    String deprecatedId = in.myUserId;
    throwIfRequestorDoesNotEqualUserId(context.requestor, deprecatedId);

    ListMySecretsAndGroupKeysResponse response = executeWithoutAuditLog(context);
    context.manager.addAuditLog(
        DBAudit.ACTION.LIST_SECRETS, context.requestor, null, null, null, null);
    return response;
  }
  
  public static ListMySecretsAndGroupKeysResponse executeWithoutAuditLog(MitroRequestContext context) throws SQLException,
      MitroServletException, DoEmailVerificationException {
    ListMySecretsAndGroupKeysResponse out = new RPC.ListMySecretsAndGroupKeysResponse();
    Stopwatch stopwatch = Stopwatch.createStarted();
    out.myUserId = context.requestor.getName();
    @SuppressWarnings("deprecation")
    AuthenticatedDB userDb = AuthenticatedDB.deprecatedNew(context.manager, context.requestor);
    
    
    out.groups = Maps.newHashMap();
    out.organizations = Maps.newHashMap();
    Set<String> users = Sets.newHashSet();
    Set<Integer> groupIds = getGroupsUsersAndOrgsFromRawStatement(context,
        context.requestor.getId(), null, out.groups, out.organizations, users, null);
    for (GroupInfo gi : out.organizations.values()) {
      if (gi.isTopLevelOrg) {
        if (userDb.isOrganizationAdmin(gi.groupId)) {
          gi.isRequestorAnOrgAdmin = true;
        } else {
          gi.encryptedPrivateKey = null;
          gi.isRequestorAnOrgAdmin = false;
        }
      }
    }
    
    out.autocompleteUsers = Lists.newArrayList(users);
    out.secretToPath = Maps.newHashMap();
    getSecretInfo(context, AdminAccess.IGNORE_ACCESS_VIA_TOPLEVEL_GROUPS, out.secretToPath, groupIds, null, IncludeAuditLogInfo.NO_AUDIT_LOG_INFO);

    logger.info("{} elapsed: {} ms:", out.myUserId, stopwatch.elapsed(TimeUnit.MILLISECONDS));
    return out;
  }
  
  static enum IncludeAuditLogInfo {INCLUDE_AUDIT_LOG_INFO, NO_AUDIT_LOG_INFO};
  
  static final String IS_TOPLEVEL_ORG_QUERY_PIECE = "(groups.type = 'TOP_LEVEL_ORGANIZATION' AND groups.type IS NOT NULL)";
  public static enum AdminAccess {IGNORE_ACCESS_VIA_TOPLEVEL_GROUPS, FORCE_ACCESS_VIA_TOPLEVEL_GROUPS};

  /**
   * This gets all secrets accessible from  any of the groups provided
   * @param context request context
   * @param topLevel Should we include secrets that we have access to only via a top level group? 
   * @param stopwatch used for timing things (maybe we should elminate this?)
   * @param secretToPath output value: returns a map of secret ID to secret + list of group ids used to access the secret
   * @param groupIds list of groups to search for secrets
   * @param secretIdsToExclude any secrets we should not include in the response, if not null. 
   * @param auditLogInfo Should we include audit log info in the responses?
   * @return a set of secret ids we've examined
   * @throws MitroServletException
   * @throws SQLException
   * @throws DoEmailVerificationException WTF? 
   */
  static Set<Integer> getSecretInfo(MitroRequestContext context,
      AdminAccess topLevel,
      Map<Integer, SecretToPath> secretToPath, Set<Integer> groupIds, Set<Integer> secretIdsToExclude, IncludeAuditLogInfo auditLogInfo)
      throws MitroServletException, SQLException, DoEmailVerificationException {
    Set<Integer> rval = Sets.newHashSet();
    final class GetSecretsResultType {
      int relatedSecretId;
      int ownSecretId;
      boolean autoDelete;
      int groupId;
      Integer aclMemberId;
      Integer aclGroupId;
      String aclMemberEmail;
      String aclGroupName;
      int secretId;
      String encryptedClientData;
      boolean isViewable;
      String kingName;
      boolean isTopLevel;
    }

    String groupIdString = Joiner.on(",").join(groupIds);
    if (groupIds.isEmpty()) {
      // this should never happen; probably only in the regression test.
      throw new MitroServletException("User without any groups; WTF?");
    }
    
    String getSecretsStatement = 
        "SELECT DISTINCT " +
          "all_secret.id as related_secret_id,  " +
          "one_secret.id as initial_secret_id, " +
          "groups.\"autoDelete\", " +
          "groups.id as group_id, " +
          "acl.member_identity," +
          "acl.group_identity, " + 
          "identity.name, " +
          "secrets.id AS secretId, " + 
          "all_secret.\"clientVisibleDataEncrypted\" as encrypted_client_data, " +
          "org_group.name as org_group_name, " +
          "secrets.is_viewable as is_viewable, " +
          "king_identity.name, " +
          IS_TOPLEVEL_ORG_QUERY_PIECE + " as is_org " +
        "FROM " +
          "group_secret as one_secret, " +
          "group_secret as all_secret, " +
          "secrets " + 
          "LEFT OUTER JOIN " +
          "identity AS king_identity ON secrets.king = king_identity.id, " +
          "groups, " +
          "acl " +
        "LEFT OUTER JOIN "+
          "identity on acl.member_identity = identity.id " +
        "LEFT OUTER JOIN " +
          "groups as org_group on acl.group_identity = org_group.id " +
        "WHERE " +
          "all_secret.\"serverVisibleSecret_id\" = one_secret.\"serverVisibleSecret_id\" " +
          "AND " +
          "all_secret.group_id = groups.id " +
          "AND " +
          "acl.group_id = all_secret.group_id " +
          "AND " +
          "secrets.id  = all_secret.\"serverVisibleSecret_id\" " +
          "AND " +
          "one_secret.group_id in (" + groupIdString + ")";

    if (topLevel.equals(AdminAccess.IGNORE_ACCESS_VIA_TOPLEVEL_GROUPS)){
      getSecretsStatement += " AND NOT " + IS_TOPLEVEL_ORG_QUERY_PIECE;
    } else if (topLevel.equals(AdminAccess.FORCE_ACCESS_VIA_TOPLEVEL_GROUPS)) {
      // SQL hacking: order by sorts false before true. You have to check null explicitly.
      // ensuring that top level orgs appear before other groups ensures that
      // the secret is always mapped to top level orgs.
      getSecretsStatement += " ORDER BY " + IS_TOPLEVEL_ORG_QUERY_PIECE + " DESC"; 
    }
    
    List<GetSecretsResultType> results = Lists.newArrayList(context.manager.groupDao.queryRaw(
        getSecretsStatement, new RawRowMapper<GetSecretsResultType>() {
      public GetSecretsResultType mapRow(String[] columnNames,
          String[] resultColumns) {
        GetSecretsResultType rval = new GetSecretsResultType();
        rval.relatedSecretId = Integer.parseInt(resultColumns[0]);
        rval.ownSecretId = Integer.parseInt(resultColumns[1]);
        rval.autoDelete = resultColumns[2].toLowerCase().startsWith("t");
        rval.groupId = Integer.parseInt(resultColumns[3]);
        rval.aclMemberId = resultColumns[4] == null ? null :  Integer.parseInt(resultColumns[4]);
        rval.aclGroupId = resultColumns[5] == null ? null : Integer.parseInt(resultColumns[5]);
        rval.aclMemberEmail = resultColumns[6];
        rval.secretId = Integer.parseInt(resultColumns[7]);
        rval.encryptedClientData = resultColumns[8];
        rval.aclGroupName = resultColumns[9];
        rval.isViewable = resultColumns[10].toLowerCase().startsWith("t");
        rval.kingName = resultColumns[11];
        rval.isTopLevel = resultColumns[12].toLowerCase().startsWith("t");
        return rval;
      }
        }));

    // assume there are no nested groups
    // TODO: solve this properly later.
    // NB: These records are in no particular order, so it must build the secret data 
    //     structure incrementally, storing partial state
    SetMultimap<Integer, String> secretToUserSet = TreeMultimap.create();
    for (GetSecretsResultType result : results) {
      if (secretIdsToExclude != null && secretIdsToExclude.contains(result.secretId)) {
        continue;
      }
      rval.add(result.secretId);
      SecretToPath thisSecret = secretToPath.get(result.secretId);
      if (null == thisSecret) {
        // if we force access via toplevel groups, only add secret ids for toplevel groups.
        // the ordering of the secrets will ensure we get the full access paths, etc.
        if (topLevel.equals(AdminAccess.FORCE_ACCESS_VIA_TOPLEVEL_GROUPS) && !result.isTopLevel) {
          // ignoring personal secret
          continue;
        }
        thisSecret = new SecretToPath();
        secretToPath.put(result.secretId, thisSecret);
        thisSecret.secretId = result.secretId;
        // TODO: Get icons and titles in a way that doesn't destroy privacy
//        thisSecret.icons = Manager.getOldJsonData().getIcons(thisSecret.hostname);
//        thisSecret.title = Manager.getOldJsonData().getTitle(thisSecret.hostname);
        thisSecret.king = result.kingName;
        thisSecret.isViewable = result.isViewable;
        thisSecret.owningOrgId = null;
      }

      if (null != result.aclGroupId) {
        // this ACL has a nested group.
        // examine this to find the owning organization
        // NB: this owning org MAY NOT be visible above.
        assert (thisSecret.owningOrgId == null ||
                  thisSecret.owningOrgId.equals(result.aclGroupId))
                  : "Secret in more than one org! " + thisSecret.owningOrgId + " != "  + result.aclGroupId;
        thisSecret.owningOrgId = result.aclGroupId;
        thisSecret.owningOrgName = result.aclGroupName;
        continue;
      }

      if (result.autoDelete) {
        secretToUserSet.put(result.secretId, result.aclMemberEmail);
        thisSecret.hiddenGroupsSet.add(result.groupId);
      } else {
        thisSecret.visibleGroupsSet.add(result.groupId);
      }

      if (result.aclMemberId == context.requestor.getId() && groupIds.contains(result.groupId)) {
        thisSecret.groupIdPath = Lists.newArrayList(result.groupId);
        thisSecret.encryptedClientData = result.encryptedClientData;
        thisSecret.encryptedCriticalData = null;
        thisSecret.secretId = result.secretId;
      }
    }

    for (SecretToPath secret : secretToPath.values()) {
      secret.users = Lists.newArrayList(secretToUserSet.get(secret.secretId));
      secret.groups = Lists.newArrayList(secret.visibleGroupsSet);
      secret.hiddenGroups = Lists.newArrayList(secret.hiddenGroupsSet);
    }
    
    if (auditLogInfo.equals(IncludeAuditLogInfo.INCLUDE_AUDIT_LOG_INFO)) { 
      DBServerVisibleSecret.fillRecentAuditActions(context.manager, secretToPath.values());
    }

    return rval;
  }
  
  
  /**
   * 
   * Get all visible users and groups for a specific user or group.
   * You may specify groupIdentity or userIdentity, but not both and not neither.
   * @param context 
   * @param userIdentity return all groups to which userIdentity has access.
   * @param groupIdentity return all groups to which groupIdentity has access. 
   * @param groups outval: a map of group id to RPC::ListMySecretsResponse::GroupInfo. All groups
   * @param organizations outval: a map of org id to RPC::ListMySecretsResponse::GroupInfo. All orgs (currently exactly 1)
   * @param users outval: a set of all users in all groups. 
   * @param nonAutoDeleteUsers outval: a set of all users in non-autodelete groups. Useful for finding org memberships.
   * @return a set of group IDs 
   * @throws SQLException
   */
  static Set<Integer> getGroupsUsersAndOrgsFromRawStatement(
        MitroRequestContext context, Integer userIdentity, Integer groupIdentity,
        Map<Integer, GroupInfo> groups, Map<Integer, GroupInfo> organizations,
        Set<String> users, Set<String> nonAutoDeleteUsers) throws SQLException {
    
    assert (groupIdentity == null) ^ (userIdentity == null) : " exactly 1 of (group, user) must be set";
    

    // This code fetches all users in all groups that the user has access to.
    // results look like this:
    /*
     *
              name        | autoDelete |         name         | group_id |          type          | name 
      --------------------+------------+----------------------+----------+------------------------+------
       vijays             | f          | vijayp@gmail.com     |     2663 |                        | 
       vijays             | f          | vijayp@lectorius.com |     2663 |                        | 
       hidden group 163   | t          | vijayp@gmail.com     |     3384 |                        | 
       hidden group 163   | t          | vijayp@lectorius.com |     3384 |                        | 
       hidden group 16878 | t          | vijayp@lectorius.com |     4471 |                        | 
       ORG1               | f          | vijayp@lectorius.com |     5603 | TOP_LEVEL_ORGANIZATION | 
                          | f          |                      |     5604 | PRIVATE                | ORG1
                          | f          | vijayp@lectorius.com |     5604 | PRIVATE                | 
       hidden group 19283 | t          | vijayp@gmail.com     |     5608 |                        | 
       hidden group 19283 | t          | vijayp@lectorius.com |     5608 |                        | 
     */
    String getGroupsAndUsersStatement = 
        "SELECT DISTINCT groups.name as group_name, groups.\"autoDelete\", other.\"groupKeyEncryptedForMe\", "+
            "ident.name, my.group_id, groups.type, org_group.name  as org_group_name, "+
            "org_group.id as org_group_id, org_group.type as org_group_type " +
        "FROM groups, acl as my " +
        "JOIN acl AS other ON my.group_id=other.group_id " +
        "LEFT OUTER JOIN identity AS ident ON ident.id = other.member_identity " +
        "LEFT OUTER JOIN groups as org_group on org_group.id = other.group_identity " + 
        "WHERE (" +
          (userIdentity != null ? 
            ("my.member_identity = "+ userIdentity) : 
            ("(my.group_identity = " + groupIdentity + 
                " OR (my.group_identity IS NULL AND groups.id = " + groupIdentity + "))")
          ) + " AND groups.id = other.group_id)";
    
      
    final class GetGroupsAndUsersResult {
      String groupName;
      boolean autoDelete;
      String groupKeyEncrypted;
      String userEmail;
      int groupId;
      DBGroup.Type type;
      String owningOrgName;
      Integer orgGroupId;
      DBGroup.Type owningType;
    }
    Set<Integer> groupIds = Sets.newHashSet();
    List<GetGroupsAndUsersResult> userGroupResults = Lists.newArrayList(context.manager.groupDao.queryRaw(
        getGroupsAndUsersStatement, new RawRowMapper<GetGroupsAndUsersResult>() {
      public GetGroupsAndUsersResult mapRow(String[] columnNames,
          String[] resultColumns) {
        /*
        for (int i=0; i<9; ++i) {
          System.out.print(resultColumns[i] + '\t');
        }
        System.out.print('\n');
        */
        
        GetGroupsAndUsersResult rval = new GetGroupsAndUsersResult();
        rval.groupName = resultColumns[0];
        rval.autoDelete = resultColumns[1].toLowerCase().startsWith("t");
        rval.groupKeyEncrypted = resultColumns[2];
        rval.userEmail = resultColumns[3];
        rval.groupId = Integer.parseInt(resultColumns[4]);
        rval.type = (null == resultColumns[5]) ? null : DBGroup.Type.valueOf(resultColumns[5]);
        rval.owningOrgName = resultColumns[6];
        rval.orgGroupId = null == resultColumns[7] ? null : Integer.parseInt(resultColumns[7]);
        
        rval.owningType = (null == resultColumns[8]) ? null : DBGroup.Type.valueOf(resultColumns[8]);
        if (null == rval.userEmail) {
          assert (
              rval.owningOrgName != null 
              && rval.owningType.equals(DBGroup.Type.TOP_LEVEL_ORGANIZATION)) 
              : "parent group must be top level orgs with names";
        }
        
        return rval;
      }
      }));
    

    // NB: These records are in no particular order, so it must build the group data 
    //     structure incrementally, storing partial state
    SetMultimap<Integer, String> groupIdToUserSet = TreeMultimap.create();
    for (GetGroupsAndUsersResult result : userGroupResults) {
      GroupInfo gi = groups.get(result.groupId);
      if (null == gi) {
        gi = new GroupInfo();
        // could this possibly be a private group?
        gi.autoDelete = result.autoDelete;
        gi.isOrgPrivateGroup = DBGroup.isPrivateUserGroup(result.type, result.autoDelete, result.groupName) && !gi.autoDelete;
        gi.isNonOrgPrivateGroup = gi.isOrgPrivateGroup;
        gi.encryptedPrivateKey = null; // this is filled in later.
        gi.groupId = result.groupId;
        gi.name = result.groupName;
        gi.isTopLevelOrg = (result.type != null) && result.type.equals(DBGroup.Type.TOP_LEVEL_ORGANIZATION);
        if (gi.isTopLevelOrg /* TODO special case for admin GET */) {
          // do not send encrypted private keys to members of the organization
          gi.encryptedPrivateKey = null;
          organizations.put(gi.groupId, gi);
        } else {
          groupIds.add(result.groupId);
        }
        groups.put(result.groupId, gi);
      }

      if (gi.owningOrgId == null && result.orgGroupId != null) {
        gi.owningOrgId = result.orgGroupId;
      }

      if (gi.owningOrgName == null && result.owningOrgName != null) {
        gi.owningOrgName = result.owningOrgName;
      }

      if (null != result.userEmail) {
        groupIdToUserSet.put(result.groupId, result.userEmail);
      }

      if (null == result.userEmail) {
        // nested groups; this means that this has an org parent.
        // This can no longer be an nonorg private group
        assert null != result.owningOrgName : "ACL without a user must have an owning org.";
        gi.isNonOrgPrivateGroup = false;

        // TODO: if we ever support different kinds of nested groups,
        //       we might want to set orgprivategroup to false also
      } else if (result.userEmail.equals(context.requestor.getName())) {
        gi.encryptedPrivateKey = result.groupKeyEncrypted;
      } /*else {
        // this group has another user in it, so it can't be any kind of private group
        gi.isOrgPrivateGroup = false;
        gi.isNonOrgPrivateGroup = false;
      }*/
      if (null != result.userEmail) {
        if (null != users) {
          users.add(result.userEmail);
        }
        if (!gi.autoDelete && null != nonAutoDeleteUsers) {
          nonAutoDeleteUsers.add(result.userEmail);
        }
      }
    }

    for (GroupInfo g : groups.values()) {
      if (g.owningOrgId == null) {
        g.isOrgPrivateGroup = false;
      }
      g.users = Lists.newArrayList(groupIdToUserSet.get(g.groupId));
    }
    return groupIds;
  }

}
