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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import co.mitro.core.accesscontrol.AuthenticatedDB;
import co.mitro.core.exceptions.InvalidRequestException;
import co.mitro.core.exceptions.MitroServletException;
import co.mitro.core.exceptions.PermissionException;
import co.mitro.core.exceptions.UserVisibleException;
import co.mitro.core.server.Manager;
import co.mitro.core.server.data.DBAcl;
import co.mitro.core.server.data.DBAcl.AccessLevelType;
import co.mitro.core.server.data.DBAudit;
import co.mitro.core.server.data.DBGroup;
import co.mitro.core.server.data.DBGroupSecret;
import co.mitro.core.server.data.DBIdentity;
import co.mitro.core.server.data.DBServerVisibleSecret;
import co.mitro.core.server.data.RPC;
import co.mitro.core.server.data.RPC.MitroRPC;

@WebServlet("/api/AddSecret")
public class AddSecret extends MitroServlet {
  private static final long serialVersionUID = 1L;

  protected boolean isReadOnly() {
    return false;
  }

  @Override
  protected MitroRPC processCommand(MitroRequestContext context)
      throws IOException, SQLException, MitroServletException {
    RPC.AddSecretRequest in = gson.fromJson(context.jsonRequest, RPC.AddSecretRequest.class);

    // TODO: Remove this once the client code no longer sends this id
    @SuppressWarnings("deprecation")
    String deprecatedId = in.myUserId;
    throwIfRequestorDoesNotEqualUserId(context.requestor, deprecatedId);

    DBServerVisibleSecret svs = addSecret(context.requestor, context.manager, in);
    
    RPC.AddSecretResponse out = new RPC.AddSecretResponse();
    out.secretId = svs.getId();

    return out;
  }

  private void throwIfNotAdmin(AccessLevelType accessLevel) throws MitroServletException {
    if (!DBAcl.modifyGroupSecretsAccess().contains(accessLevel)) {
      throw new PermissionException("User is not an admin for secret");
    }
  }

  public DBServerVisibleSecret addSecret(
      DBIdentity requestor, Manager mgr, RPC.AddSecretRequest in)
      throws SQLException, MitroServletException {
    @SuppressWarnings("deprecation")
    AuthenticatedDB udb = AuthenticatedDB.deprecatedNew(mgr, requestor);
    final DBGroup owningGroup = udb.getGroupForAddSecret(in.ownerGroupId);
    if (null == owningGroup) {
      throw new PermissionException("user not in group");
    }

    DBServerVisibleSecret svs;
    mgr.userState.trackGroup(owningGroup);

    if (null == in.secretId) {      
      // Creating a new secret: ensure the user is an admin
      if (!owningGroup.isTopLevelOrganization()) {
        AccessLevelType accessLevel = requestor.getAccessLevelForGroup(mgr, owningGroup);
        throwIfNotAdmin(accessLevel);
      }
      svs = new DBServerVisibleSecret();
      svs.setKing(requestor);
      mgr.svsDao.create(svs);
    } else {
      svs = mgr.svsDao.queryForId(in.secretId);
      if (svs == null) {
        throw new MitroServletException("Secret does not exist");
      }

      // Verify the user is an admin for the secret
      // Find the highest access level for this user
      AccessLevelType highestAccessLevel = requestor.getHighestAccessLevel(mgr, svs);
      throwIfNotAdmin(highestAccessLevel);
    }

    // Verify that the secret isn't already in the group
    // TODO: Use a database constraint?
    DBGroupSecret gs = mgr.getGroupSecret(owningGroup, svs);
    if (gs != null) {
      throw new InvalidRequestException("Secret is already in group");
    }

    // Create the DBGroupSecret and add the data from the client
    gs = new DBGroupSecret();
    gs.setClientVisibleDataEncrypted(in.encryptedClientData);
    gs.setCriticalDataEncrypted(in.encryptedCriticalData);
    gs.setGroup(owningGroup);
    gs.setServerVisibleSecret(svs);
    gs.setSignatureOfGroupIdAndSecretId("TODO");
    mgr.groupSecretDao.create(gs);
    mgr.svsDao.refresh(svs);
    Set<DBServerVisibleSecret> secrets = ImmutableSet.of(svs);
    preventMutipleOrgSecretOwnership(mgr, secrets);
    
    mgr.addAuditLog(DBAudit.ACTION.ADD_SECRET, null, null, owningGroup, svs, null);

    
    
    return svs;
  }

  static void preventMutipleOrgSecretOwnership(Manager mgr,
      Set<DBServerVisibleSecret> secrets)
      throws SQLException, UserVisibleException {
    // ensure that a secret is not shared with multiple organizations.
    Integer seenOrg = null;
    Set<DBGroup> seenGroups = Sets.newHashSet();
    for (DBServerVisibleSecret serverSecret : secrets) {
      for (DBGroupSecret groupSecret : serverSecret.getGroupSecrets()) {
        DBGroup g = groupSecret.getGroup();
        if (seenGroups.add(g)) {
          // unseen group
          for (DBAcl acl : g.getAcls()) {
            Integer thisOrg = acl.getMemberGroupIdAsInteger();
            if (seenOrg != null && thisOrg != null) {
              if (seenOrg.intValue() != thisOrg.intValue()) {
                throw new UserVisibleException("You cannot add a secret to more than one organization.");
              }
            }
            if (thisOrg != null) {
              seenOrg = thisOrg;
            }
          }
        }
      }
    }
  }
}
