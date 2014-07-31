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
import java.math.BigInteger;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import javax.servlet.annotation.WebServlet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.mitro.core.accesscontrol.AuthenticatedDB;
import co.mitro.core.exceptions.MitroServletException;
import co.mitro.core.server.data.DBAcl;
import co.mitro.core.server.data.DBGroup;
import co.mitro.core.server.data.DBIdentity;
import co.mitro.core.server.data.DBPendingGroup;
import co.mitro.core.server.data.RPC.AddPendingGroupRequest;
import co.mitro.core.server.data.RPC.AddPendingGroupRequest.AdminInfo;
import co.mitro.core.server.data.RPC.AddPendingGroupRequest.MemberList;
import co.mitro.core.server.data.RPC.AddPendingGroupRequest.PendingGroup;
import co.mitro.core.server.data.RPC.AddPendingGroupResponse;
import co.mitro.core.server.data.RPC.GroupDiff;
import co.mitro.core.server.data.RPC.GroupDiff.GroupModificationType;
import co.mitro.core.server.data.RPC.MitroRPC;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.j256.ormlite.stmt.DeleteBuilder;
import com.j256.ormlite.stmt.SelectArg;

@WebServlet("/api/internal/AddPendingGroups")
public class AddPendingGroupServlet extends MitroServlet {
  private static final long serialVersionUID = -6743381126604749211L;
  private static final Logger logger = LoggerFactory.getLogger(AddPendingGroupServlet.class);
  private static final SecureRandom secureRng = new SecureRandom(); 
  
  @Override
  protected MitroRPC processCommand(MitroRequestContext context) throws IOException, SQLException, MitroServletException {
    AddPendingGroupRequest in = gson.fromJson(context.jsonRequest,
        AddPendingGroupRequest.class);
    
    /** This is generated once per push per scope. Used to prevent race conditions*/
    final String nonce = new BigInteger(128, secureRng).toString(32);

    assert (in.pendingGroups != null);
    assert (in.adminInfo != null);
    final AdminInfo adminInfo = in.adminInfo;
    assert (adminInfo.domainAdminEmail != null);
    DBIdentity domainAdmin = DBIdentity.getIdentityForUserName(context.manager, adminInfo.domainAdminEmail);
    if (domainAdmin == null) {
      throw new MitroServletException("unknown user:" + adminInfo.domainAdminEmail);
    }
    if (Strings.isNullOrEmpty(in.scope)) {
      throw new MitroServletException("scope must not be null or empty");
    }

    @SuppressWarnings("deprecation")
    AuthenticatedDB syncAdminDb = AuthenticatedDB.deprecatedNew(context.manager, domainAdmin);
    Set<DBGroup> orgs = syncAdminDb.getOrganizations();
    if (orgs.isEmpty()) {
      throw new MitroServletException("no org:" + adminInfo.domainAdminEmail);
    } else if (orgs.size() > 1) {
      throw new MitroServletException("user " + adminInfo.domainAdminEmail + 
          " is an admin of multiple orgs. aborting");
    }
    DBGroup org = orgs.iterator().next();
    Map<String, DBGroup> existingGroups = Maps.newHashMap();
    Map<String, GroupDiff> diffs = Maps.newHashMap();
    Map<String, MemberList> pendingGroupMap = Maps.newHashMap();
    Map<String, PendingGroup> inMap = Maps.newHashMap();
    for (PendingGroup pg : in.pendingGroups) {
      inMap.put(pg.groupName, pg);
    }

    calculatePendingGroupDiffs(context, in.pendingGroups, org, existingGroups,
        diffs, pendingGroupMap, in.scope);

    // send email if there are no existing sync requests.
    boolean alreadySentEmail = (null != context.manager.pendingGroupDao.queryBuilder().where().eq(
        DBPendingGroup.SCOPE_NAME, new SelectArg(in.scope)).queryForFirst());
    if (!(alreadySentEmail || diffs.isEmpty())) {
      // TODO SEND EMAIL;
    }

    // TODO: send email!
    DeleteBuilder<DBPendingGroup, Integer> deleter = context.manager.pendingGroupDao.deleteBuilder();
    deleter.where().eq(DBPendingGroup.SCOPE_NAME, in.scope);
    deleter.delete();

    if (!diffs.isEmpty()) {
      // if there are _any_ diffs, we have to add everything.
      for (String groupName : pendingGroupMap.keySet()) {
        PendingGroup pg = inMap.get(groupName);
        DBPendingGroup dbg = new DBPendingGroup(context.requestor, groupName, in.scope, pg.memberListJson, pg.signature, org);
        dbg.setSyncNonce(nonce);
        context.manager.pendingGroupDao.create(dbg);
      }
    }
    
    AddPendingGroupResponse out = new AddPendingGroupResponse();
    out.syncNonce = nonce;
    out.diffs = diffs;
    return out;
  }

  /**
   * Too many inout parameters.
   * TODO: change this to use a parameter object
   * @param context in
   * @param pendingGroupsList in
   * @param org in
   * @param existingGroups in/out
   * @param diffs in/out
   * @param pendingGroupMap in/out
   * @return the scope
   */
  public static void calculatePendingGroupDiffs(MitroRequestContext context,
      Collection<? extends PendingGroup> pendingGroupsList, DBGroup org,
      Map<String, DBGroup> existingGroups, Map<String, GroupDiff> diffs,
      Map<String, MemberList> pendingGroupMap, String scope) throws MitroServletException,
      SQLException {
    assert scope != null || pendingGroupsList.isEmpty();
    for (PendingGroup pg : pendingGroupsList) {
      AddPendingGroupRequest.MemberList ml = gson.fromJson(pg.memberListJson,
          AddPendingGroupRequest.MemberList.class);
      assert (null != ml) : "you cannot have a null MemberList";
      for (String user : ml.memberList) {
        assert Util.isEmailAddress(user) : "invalid email: '" + user + "'";
      }
      pendingGroupMap.put(pg.groupName, ml);
    }
    
    for (DBGroup g : org.getAllOrgGroups(context.manager)) {
      if (scope != null && scope.equals(g.getScope())) {
        existingGroups.put(g.getName(), g);
      }
    }
    
    Set<String> allGroupNames = Sets.union(pendingGroupMap.keySet(), existingGroups.keySet());
    for (String groupName : allGroupNames) {
      diffGroupPair(context, diffs, groupName, existingGroups.get(groupName), pendingGroupMap.get(groupName));
    }
  }

  private static void diffGroupPair(MitroRequestContext context, Map<String, GroupDiff> diffs,
      String groupName, DBGroup existing, MemberList pending)
      throws SQLException {
    GroupDiff gd = new GroupDiff();
    gd.groupName = groupName;
    Set<String> syncSourceMembers = null;
    Set<String> existingUsers = null;
    if (existing != null) {
      Set<Integer> userIds = Sets.newHashSet();
      existing.putDirectUsersIntoSet(userIds, DBAcl.modifyGroupSecretsAccess());
      // TODO: this will pull in users n*m times where n is number of users and m
      //       is number of groups the user is in.
      existingUsers = DBIdentity.getUserNamesFromIds(context.manager, userIds);
    }
    if (pending != null) {
      syncSourceMembers = Sets.newHashSet(pending.memberList);
    }
    assert (syncSourceMembers != null || existingUsers != null);
    if (syncSourceMembers == null) {
      gd.groupModification  = GroupModificationType.IS_DELETED;
      syncSourceMembers = Collections.emptySet();
    } else if (existingUsers == null) {
      gd.groupModification  = GroupModificationType.IS_NEW;
      existingUsers = Collections.emptySet();
    }
    gd.deletedUsers = Lists.newArrayList(Sets.difference(existingUsers, syncSourceMembers));
    gd.newUsers = Lists.newArrayList(Sets.difference(syncSourceMembers, existingUsers));
    if (gd.groupModification.equals(GroupModificationType.IS_UNCHANGED) &&
          !(gd.deletedUsers.isEmpty() && gd.newUsers.isEmpty())) {
      gd.groupModification = GroupModificationType.MEMBERSHIP_MODIFIED;
    }
    if (gd.isDifferent()) {
      // TODO: move this outside to make sure this doesn't have so many mutable parameters.
      diffs.put(groupName, gd);
    }
  }
}
