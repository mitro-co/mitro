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
import java.util.Set;

import javax.servlet.annotation.WebServlet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.mitro.core.accesscontrol.AuthenticatedDB;
import co.mitro.core.exceptions.MitroServletException;
import co.mitro.core.server.data.DBAcl;
import co.mitro.core.server.data.DBAudit;
import co.mitro.core.server.data.DBGroup;
import co.mitro.core.server.data.DBGroupSecret;
import co.mitro.core.server.data.DBServerVisibleSecret;
import co.mitro.core.server.data.RPC;
import co.mitro.core.server.data.RPC.MitroRPC;

import com.google.common.collect.Sets;


@WebServlet("/api/RemoveSecret")
public class RemoveSecret extends MitroServlet {
  private static final long serialVersionUID = 1L;
  @SuppressWarnings("unused")
  private static final Logger logger = LoggerFactory.getLogger(RemoveSecret.class);

  @Override
  protected boolean isReadOnly() {
    return false;
  }

  @Override
  protected MitroRPC processCommand(MitroRequestContext context)
      throws IOException, SQLException, MitroServletException {
    RPC.RemoveSecretRequest in = gson.fromJson(
        context.jsonRequest,
        RPC.RemoveSecretRequest.class);

    // TODO: Remove this once the client code no longer sends this id
    @SuppressWarnings("deprecation")
    String deprecatedId = in.myUserId;
    throwIfRequestorDoesNotEqualUserId(context.requestor, deprecatedId);

    @SuppressWarnings("deprecation")
    AuthenticatedDB userDb = AuthenticatedDB.deprecatedNew(context.manager, context.requestor);
    // get the secret, if we have access
    DBServerVisibleSecret svs = userDb.getSecretAsUserOrAdmin(in.secretId, DBAcl.modifyGroupSecretsAccess());
    if (svs == null) {
      throw new MitroServletException(
          "secret " + in.secretId + " does not exist or requestor does not have access");
    }
    context.manager.userState.trackSecret(svs);

    // Authorized: do the delete(s)
    int deleteCount = 0;
    Set<DBGroup> affectedAutoDeleteGroups = Sets.newHashSet();
    for (DBGroupSecret gs : svs.getGroupSecrets()) {
      // cannot remove secrets from organizations
      DBGroup secretGroup = gs.getGroup();
      if (secretGroup.isTopLevelOrganization()) {
        if (null != in.groupId && in.groupId == secretGroup.getId()) {
          // specifically trying to remove this entry: error
          throw new MitroServletException("cannot remove secrets from organizations");
        }
        // ignored: creating an "orphaned" secret
        continue;
      }

      if (null == in.groupId || in.groupId == secretGroup.getId()) {
        if (secretGroup.isAutoDelete()) {
          affectedAutoDeleteGroups.add(secretGroup);
        }

        context.manager.groupSecretDao.delete(gs);
        context.manager.addAuditLog(
            DBAudit.ACTION.REMOVE_SECRET, null, null, secretGroup, svs, null);
        deleteCount += 1;
      }
    }

    // for any autodelete group that we touched, delete the group if this was 
    // its last secret:
    for (DBGroup g : affectedAutoDeleteGroups) {
      context.manager.groupDao.refresh(g);
      if (g.getGroupSecrets().isEmpty()) {
        DeleteGroup.deleteGroup(context.requestor, context.manager, g);
      }
    }
    
    if (deleteCount == 0) {
      throw new MitroServletException("secret " + svs.getId() + " is not in group " + in.groupId);
    }

    if (deleteCount == svs.getGroupSecrets().size()) {
      // deleted the secret from all its groups: delete the secret
      DBGroupSecret matchObject = new DBGroupSecret();
      matchObject.setServerVisibleSecret(svs);
      assert context.manager.groupSecretDao.queryForMatchingArgs(matchObject).size() == 0;

      context.manager.svsDao.delete(svs);
      context.manager.addAuditLog(DBAudit.ACTION.REMOVE_SECRET, null, null, null, svs, null);
    }

    RPC.RemoveSecretResponse out = new RPC.RemoveSecretResponse();
    return out;
  }
}
