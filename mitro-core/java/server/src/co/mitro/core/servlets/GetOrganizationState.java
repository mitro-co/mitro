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
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.servlet.annotation.WebServlet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.mitro.core.accesscontrol.AuthenticatedDB;
import co.mitro.core.exceptions.MitroServletException;
import co.mitro.core.server.data.DBAcl;
import co.mitro.core.server.data.DBGroup;
import co.mitro.core.server.data.DBIdentity;
import co.mitro.core.server.data.RPC.GetOrganizationStateRequest;
import co.mitro.core.server.data.RPC.GetOrganizationStateResponse;
import co.mitro.core.server.data.RPC.ListMySecretsAndGroupKeysResponse.GroupInfo;
import co.mitro.core.server.data.RPC.ListMySecretsAndGroupKeysResponse.SecretToPath;
import co.mitro.core.server.data.RPC.MitroRPC;
import co.mitro.core.servlets.ListMySecretsAndGroupKeys.AdminAccess;
import co.mitro.core.servlets.ListMySecretsAndGroupKeys.IncludeAuditLogInfo;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

@WebServlet("/api/GetOrganizationState")
public class GetOrganizationState extends MitroServlet {
  private static final long serialVersionUID = 1L;
  private static final Logger logger = LoggerFactory
      .getLogger(GetOrganizationState.class);

  @Override
  protected MitroRPC processCommand(MitroRequestContext context) throws IOException, SQLException, MitroServletException {
    GetOrganizationStateRequest in = gson.fromJson(context.jsonRequest,
        GetOrganizationStateRequest.class);
    return doOperation(context, in.orgId);
  }

  public static GetOrganizationStateResponse doOperation(MitroRequestContext context, int orgId)
      throws MitroServletException, SQLException {
    Stopwatch stopwatch = Stopwatch.createStarted();
    GetOrganizationStateResponse out = new GetOrganizationStateResponse();

    @SuppressWarnings("deprecation")
    AuthenticatedDB userDb = AuthenticatedDB.deprecatedNew(context.manager, context.requestor);
    DBGroup org = userDb.getOrganizationAsMember(orgId);
    assert(null != org);

    Set<String> users = Sets.newHashSet();
    Set<Integer> groupIds = ListMySecretsAndGroupKeys.getGroupsUsersAndOrgsFromRawStatement(context,
        null, org.getId(), out.groups, out.organizations, null, users);
    
    // prevent users who are memebers of org groups but not members of the org from being returned as members.
    Set<String> orgMembers = DBIdentity.getUserNamesFromIds(context.manager, 
        MutateOrganization.getMemberIdsAndPrivateGroupIdsForOrg(context.manager, org).keySet());
    out.members = Lists.newArrayList(Sets.intersection(orgMembers, users));

    Set<Integer> orgAdmins = Sets.newHashSet();
    org.putDirectUsersIntoSet(orgAdmins, DBAcl.adminAccess());
    out.admins = Lists.newArrayList(DBIdentity.getUserNamesFromIds(context.manager, orgAdmins));

    // all users get a list of THEIR secrets
    if (userDb.isOrganizationAdmin(orgId)) {
      // if user is admin: get list of secrets
      // TODO: audit log is super slow; this should move to its own API call?
      final IncludeAuditLogInfo GET_AUDIT = IncludeAuditLogInfo.NO_AUDIT_LOG_INFO;
      groupIds.add(org.getId());
      ListMySecretsAndGroupKeys.getSecretInfo(context,
          AdminAccess.FORCE_ACCESS_VIA_TOPLEVEL_GROUPS, out.orgSecretsToPath,
          groupIds, null, GET_AUDIT);
      
      // any org secrets with no users, no hidden groups and only the org group are orphaned.
      out.orphanedSecretsToPath = Maps.newHashMap();
      for (Iterator<Entry<Integer, SecretToPath>> iter = out.orgSecretsToPath.entrySet().iterator();
          iter.hasNext(); ) {
        Entry<Integer, SecretToPath> entry = iter.next(); 
        SecretToPath stp = entry.getValue();
        if (stp.users.isEmpty() && stp.hiddenGroups.isEmpty() && stp.groups.size() == 1) {
          // this is an orphaned secret
          assert (stp.groups.get(0) == org.getId());
          out.orphanedSecretsToPath.put(entry.getKey(), entry.getValue());
          // remove orphaned secrets from regular org secrets
          iter.remove();
        }
      }
    } else {
      // these variables are not filled; set to null so the caller doesn't rely on them
      out.orgSecretsToPath = null;
      out.orphanedSecretsToPath = null;
      out.organizations = null;

      // Remove private data from groups: membership; encrypted keys
      for (GroupInfo group : out.groups.values()) {
        group.users = null;
        group.encryptedPrivateKey = null;
      }
    }

    logger.info("{} elapsed: {} ms:", context.requestor, stopwatch.elapsed(TimeUnit.MILLISECONDS));
    return out;
  }
}
