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
import java.util.Set;

import javax.servlet.annotation.WebServlet;

import co.mitro.core.accesscontrol.AuthenticatedDB;
import co.mitro.core.exceptions.MitroServletException;
import co.mitro.core.exceptions.PermissionException;
import co.mitro.core.exceptions.UnverifiedUserException;
import co.mitro.core.server.data.DBAcl;
import co.mitro.core.server.data.DBGroup;
import co.mitro.core.server.data.DBGroupSecret;
import co.mitro.core.server.data.DBGroupSecret.CRITICAL;
import co.mitro.core.server.data.DBIdentity;
import co.mitro.core.server.data.DBServerVisibleSecret;
import co.mitro.core.server.data.RPC;
import co.mitro.core.server.data.RPC.GetSecretResponse;
import co.mitro.core.server.data.RPC.MitroRPC;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;


@WebServlet("/api/GetSecret")
public class GetSecret extends MitroServlet {
	private static final long serialVersionUID = 1L;
	private static Set<String> viewOnlyPlatforms = ImmutableSet.of("ANDROID", "IOS");
	private boolean criticalRequestOK(AuthenticatedDB udb, DBServerVisibleSecret svs, CRITICAL requestedAccess, MitroRequestContext context) throws SQLException, MitroServletException {
	  if (svs.isViewable() || (CRITICAL.NO_CRITICAL_DATA == requestedAccess)) {
	    return true;
	  }
	  if (CRITICAL.INCLUDE_CRITICAL_DATA_FOR_DISPLAY == requestedAccess) {
	    return (null != udb.getServerSecretForViewOrEdit(svs.getId()));
	  }
	  if (CRITICAL.INCLUDE_CRITICAL_DATA == requestedAccess) {
	    if (viewOnlyPlatforms.contains(context.platform)) {
	      // these platforms use the include_critical even for display, so we need to reject their requests.
	      return (null != udb.getServerSecretForViewOrEdit(svs.getId()));
	    }
	  }
	  return true;
	  
	}

	@Override
  protected MitroRPC processCommand(MitroRequestContext context)
      throws IOException, SQLException, MitroServletException {
    RPC.GetSecretRequest in = gson.fromJson(
        context.jsonRequest,
        RPC.GetSecretRequest.class);

    // TODO: Remove this once the client code no longer sends this id
    @SuppressWarnings("deprecation")
    String deprecatedId = in.userId;
    throwIfRequestorDoesNotEqualUserId(context.requestor, deprecatedId);
    @SuppressWarnings("deprecation")
    AuthenticatedDB udb = AuthenticatedDB.deprecatedNew(context.manager, context.requestor);
 
    DBServerVisibleSecret svs = null;
    svs = context.manager.svsDao.queryForId(in.secretId);
    if (svs == null) {
      throw new MitroServletException("Invalid secret: " + in.secretId);
    }
    
    CRITICAL includeCritical = CRITICAL.fromClientString(in.includeCriticalData);
    if (!criticalRequestOK(udb, svs, includeCritical, context)) {
      throw new PermissionException("you do not have permission to view the password");
    }
    DBGroupSecret secret = null;
    if (null == in.groupId) {
      // Find any group we share with the secret
      String getGroupSecretIdQuery = "SELECT group_secret.id FROM group_secret, acl WHERE "
          + " acl.group_id = group_secret.group_id and acl.member_identity =  " + context.requestor.getId() 
          + " AND \"serverVisibleSecret_id\" = "+ in.secretId + " LIMIT 1";
      
      List<String[]> groupSecretResults =
          Lists.newArrayList(context.manager.groupSecretDao.queryRaw(getGroupSecretIdQuery));
      for (String[] row : groupSecretResults) {
        secret = context.manager.groupSecretDao.queryForId(Integer.parseInt(row[0], 10));
      }
      if (null == secret) {
        throw new MitroServletException("you do not have access to secret " + in.secretId);
      }
    } else {
      for (DBGroupSecret gs : svs.getGroupSecrets()) {
        if (gs.getGroup().getId() == in.groupId) {
          secret = gs;
          break;
        }
      }
      if (null == secret) {
        throw new MitroServletException("secret id " + in.secretId + " does not exist in group " +
            in.groupId + " (perhaps you have the wrong group?)");
      }
    }
    
    // can the user actually access this group?
    // TODO: Create a data access API that forces ACL checks; similar code in AddSecret.
    Set<DBIdentity> users = Sets.newHashSet();
    Set<DBGroup> groups = Sets.newHashSet();
    DBGroup g = secret.getGroup();
    context.manager.groupDao.refresh(g);
    // List users and groups in *this specific* group; not all with access to the secret
    g.addTransitiveGroupsAndUsers(context.manager, DBAcl.allAccessTypes(), users, groups);

    if (!users.contains(context.requestor)) {
      throw new MitroServletException("user does not have permission to access group");
    }

    if (!context.requestor.isVerified()) {
      // unverified users cannot access secret if shared with users or groups
      boolean forbidAccess = users.size() > 1;
      if (!forbidAccess) {
        // If the secret is shared with multiple groups, forbid access. The secret could be shared
        // with me, and a group containing only me, but whatever.
        // TODO: Make this a database COUNT query
        int secretGroupCount = context.manager.groupSecretDao.queryForEq(
            DBGroupSecret.SVS_ID_NAME, svs.getId()).size();
        forbidAccess = secretGroupCount > 1;
      }

      if (forbidAccess) {
        throw new UnverifiedUserException("cannot list this secret because your account " + context.requestor.getName() + " is unverified and it is shared with others");
      }
    }

    GetSecretResponse out = new RPC.GetSecretResponse();
    out.groupId = g.getId();
    for (DBAcl acl : g.getAcls()) {
      if (acl.getMemberIdentityIdAsInteger() != null && acl.getMemberIdentityIdAsInteger() == context.requestor.getId()) {
        out.encryptedGroupKey = acl.getGroupKeyEncryptedForMe();
        break;
      }
    }
    assert (out.encryptedGroupKey != null);
    secret.addToRpcSecret(
        context.manager,
        out.secret,
        includeCritical,
        context.requestor.getName());
    
    out.secret.canEditServerSecret = (null != udb.getServerSecretForViewOrEdit(svs.getId()));

    return out;
  }
}
