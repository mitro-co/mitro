/*******************************************************************************
 * Copyright (c) 2013 Lectorius, Inc.
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
/**
 * 
 */
package co.mitro.core.servlets;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.annotation.WebServlet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.mitro.core.accesscontrol.AuthenticatedDB;
import co.mitro.core.exceptions.MitroServletException;
import co.mitro.core.server.data.DBGroup;
import co.mitro.core.server.data.DBIdentity;
import co.mitro.core.server.data.DBPendingGroup;
import co.mitro.core.server.data.RPC;
import co.mitro.core.server.data.RPC.AddPendingGroupRequest.MemberList;
import co.mitro.core.server.data.RPC.EditGroupRequest;
import co.mitro.core.server.data.RPC.GetPendingGroupApprovalsRequest;
import co.mitro.core.server.data.RPC.GetPendingGroupApprovalsResponse.PendingGroupApproval;
import co.mitro.core.server.data.RPC.GroupDiff;
import co.mitro.core.server.data.RPC.GroupDiff.GroupModificationType;
import co.mitro.core.server.data.RPC.MitroRPC;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * @author vijayp
 * 
 */
@WebServlet("/api/GetPendingGroups")
public class GetPendingGroupApprovals extends MitroServlet {

  /**
   * 
   */
  private static final long serialVersionUID = 7257988672701779890L;
  private static final Logger logger = LoggerFactory
      .getLogger(GetPendingGroupApprovals.class);

  /*
   * (non-Javadoc)
   * 
   * @see
   * co.mitro.core.servlets.MitroServlet#processCommand(co.mitro.core.server
   * .data.DBIdentity, java.lang.String, co.mitro.core.server.Manager)
   */
  @SuppressWarnings("deprecation")
  @Override
  protected MitroRPC processCommand(MitroRequestContext context) throws IOException, SQLException, MitroServletException {
    GetPendingGroupApprovalsRequest in = gson.fromJson(context.jsonRequest,
        GetPendingGroupApprovalsRequest.class);
    RPC.GetPendingGroupApprovalsResponse out = new RPC.GetPendingGroupApprovalsResponse();
    out.pendingAdditionsAndModifications = Lists.newArrayList();
    out.pendingDeletions = Lists.newArrayList();

    out.diffs = Collections.emptyMap();
    out.newOrgMembers = Collections.emptyList();
    out.deletedOrgMembers = Collections.emptyList();
    
    AuthenticatedDB userDb = AuthenticatedDB.deprecatedNew(context.manager, context.requestor);
    Set<DBGroup> orgs = userDb.getOrganizations();

    if (orgs.isEmpty()) {
      logger.warn("ignoring request from user who is not a member of any organizations");
      return out;
    } else {
      assert orgs.size() == 1;
    }
    
    final DBGroup org = orgs.iterator().next();
    assert (userDb.isOrganizationAdmin(org.getId()));
    
    List<DBPendingGroup> pendingDBGroups = context.manager.pendingGroupDao.queryForEq(
        DBPendingGroup.OWNING_ORG, org.getId());
    Map<String, PendingGroupApproval> pendingGroups = Maps.newHashMap();
    String nonce = null;
    String scope = null;
    for (DBPendingGroup databasePendingGroup : pendingDBGroups) {
      PendingGroupApproval pendingGroupOutput = new PendingGroupApproval();
      if (nonce == null) {
        nonce = databasePendingGroup.getSyncNonce();
      } else {
        assert (nonce.equals(databasePendingGroup.getSyncNonce())) : "only one nonce per sync data allowed " + nonce + " " + databasePendingGroup.getSyncNonce();
      }

      // TODO: support multiple scopes per org by merging groups from multiple scopes.
      if (scope == null) {
        scope = databasePendingGroup.getScope();
      } else {
        assert scope.equals(databasePendingGroup.getScope()) : "non-unique scope detected: " + scope + ", " + databasePendingGroup.getScope();
      }
      databasePendingGroup.fillPendingGroup(pendingGroupOutput);
      pendingGroups.put(pendingGroupOutput.groupName, pendingGroupOutput);
    }
    out.syncNonce = nonce;

    Map<String, DBGroup> existingGroups = Maps.newHashMap();
    Map<String, GroupDiff> diffs = Maps.newHashMap();
    Map<String, MemberList> groupNameToMemberListMap = Maps.newHashMap();
    AddPendingGroupServlet.calculatePendingGroupDiffs(context, pendingGroups.values(), org, existingGroups,
        diffs, groupNameToMemberListMap, scope);

    for (String groupName : Sets.union(pendingGroups.keySet(), diffs.keySet())) {
      if (pendingGroups.containsKey(groupName)) {
        if (!diffs.containsKey(groupName)) {
          continue;
        }
        final PendingGroupApproval groupApprovalOut = pendingGroups.get(groupName);
        final GroupDiff gd = diffs.get(groupName);
        assert (!gd.groupModification.equals(GroupModificationType.IS_DELETED));
        if (gd.groupModification.equals(GroupModificationType.IS_NEW)) {
          groupApprovalOut.matchedGroup = null;
        } else {
          final DBGroup matchedGroup = existingGroups.get(groupName);
          groupApprovalOut.matchedGroup = new EditGroupRequest();
          GetGroup.fillEditGroupRequest(context.manager, matchedGroup, groupApprovalOut.matchedGroup, in.deviceId);
        }
        out.pendingAdditionsAndModifications.add(groupApprovalOut);
      } else {
        assert (diffs.containsKey(groupName));
        final GroupDiff gd = diffs.get(groupName);
        assert (gd.groupModification.equals(GroupModificationType.IS_DELETED)); // otherwise it should have been handled above.
        EditGroupRequest groupToDelete = new EditGroupRequest();
        GetGroup.fillEditGroupRequest(context.manager, existingGroups.get(gd.groupName), groupToDelete, in.deviceId);
        out.pendingDeletions.add(groupToDelete);
      }
    }
    
    if (!diffs.isEmpty()) {
      // TODO: eventually pull this out of a special ALL group that ought to be synced.
      Set<String> allUserNames = Sets.newHashSet();
      for (MemberList ml : groupNameToMemberListMap.values()) {
        allUserNames.addAll(ml.memberList);
      }
      // see which users we need to add to the org.
      Set<Integer> orgUserIds = MutateOrganization.getMemberIdsAndPrivateGroupIdsForOrg(context.manager, org).keySet();
      Set<String> existingOrgMembers = DBIdentity.getUserNamesFromIds(context.manager, orgUserIds);

      out.newOrgMembers = Lists.newArrayList(Sets.difference(allUserNames, existingOrgMembers));

      // TODO: if none of the groups that are synced is an ALL group, 
      // i.e. we're syncing a subset of the org, this will suggest 
      // deleting most org members, which is not good.  We need a better 
      // solution for this.
      out.deletedOrgMembers = Lists.newArrayList(Sets.difference(existingOrgMembers, allUserNames));
    } else {
      out.deletedOrgMembers = Collections.emptyList();
      out.newOrgMembers = Collections.emptyList();
    }
    out.diffs = diffs;
    out.orgId = org.getId();
    assert (out.diffs.size() == (out.pendingAdditionsAndModifications.size() + out.pendingDeletions.size()));
    return out;
  }

}
