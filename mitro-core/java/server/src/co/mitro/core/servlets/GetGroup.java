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
import java.util.Collections;

import javax.servlet.annotation.WebServlet;

import co.mitro.core.accesscontrol.AuthenticatedDB;
import co.mitro.core.exceptions.InvalidRequestException;
import co.mitro.core.exceptions.MitroServletException;
import co.mitro.core.exceptions.PermissionException;
import co.mitro.core.server.Manager;
import co.mitro.core.server.data.DBAcl;
import co.mitro.core.server.data.DBAudit;
import co.mitro.core.server.data.DBGroup;
import co.mitro.core.server.data.DBGroupSecret;
import co.mitro.core.server.data.DBGroupSecret.CRITICAL;
import co.mitro.core.server.data.DBIdentity;
import co.mitro.core.server.data.RPC;
import co.mitro.core.server.data.RPC.GetGroupResponse;
import co.mitro.core.server.data.RPC.MitroRPC;

import com.google.common.collect.Lists;


@WebServlet("/api/GetGroup")
public class GetGroup extends MitroServlet {
	private static final long serialVersionUID = 1L;

  @Override
  protected MitroRPC processCommand(MitroRequestContext context)
      throws IOException, SQLException, MitroServletException {
    RPC.GetGroupRequest in = gson.fromJson(context.jsonRequest, RPC.GetGroupRequest.class); 
    final DBGroup group = context.manager.groupDao.queryForId(in.groupId);
    
    // TODO: Remove this once the client code no longer sends this id
    @SuppressWarnings("deprecation")
    String deprecatedId = in.userId;
    throwIfRequestorDoesNotEqualUserId(context.requestor, deprecatedId);
    @SuppressWarnings("deprecation")
    AuthenticatedDB userDb = AuthenticatedDB.deprecatedNew(context.manager, context.requestor);
    // check if requestor is a member of group (has full access)
    
    boolean hasFullAccess = false;
    if (null != userDb.getGroupAsUserOrOrgAdmin(group.getId())) {
      hasFullAccess = true;
    } else {
      // throws if we don't have access
      try {
        DBGroup result = userDb.getGroupForAddSecret(group.getId());
        assert result.getId() == group.getId();
      } catch (MitroServletException e) {
        // preserve behavior by throwing PermissionException
        if (e.getMessage().startsWith("User should not be able to see group")) {
          throw new PermissionException("user does not have permission to access group");
        }
      }
    }

    RPC.GetGroupResponse out = new RPC.GetGroupResponse();
    fillEditGroupRequest(context.manager, group, out, null);
    out.secrets = Lists.newArrayList();
    if (!hasFullAccess) {
      // don't reveal encrypted group keys to a limited access requestor
      out.acls = Collections.emptyList();
    } else if (null != group.getGroupSecrets()) {
      assert hasFullAccess;
      for (DBGroupSecret gs : group.getGroupSecrets()) {
        RPC.Secret secret = new RPC.Secret();
        
        gs.addToRpcSecret(context.manager, secret,
            in.includeCriticalData ? CRITICAL.INCLUDE_CRITICAL_DATA : CRITICAL.NO_CRITICAL_DATA,
            context.requestor.getName());
        out.secrets.add(secret);
      }
    }
    return out;
  }

  protected static void fillEditGroupRequest(Manager mgr, 
      final DBGroup group, RPC.EditGroupRequest out, String deviceId) throws SQLException,
      MitroServletException {
    out.groupId = group.getId();
    out.name = group.getName();
    out.scope = group.getScope(); 
    out.autoDelete = group.isAutoDelete();
    out.publicKey = group.getPublicKeyString();
    out.signatureString = group.getSignatureString();
    out.acls = Lists.newArrayList();
    assert out.name != null;
    mgr.addAuditLog(DBAudit.ACTION.GET_GROUP, null, null, group, null, null);
    for (DBAcl acl : group.getAcls()) {
      GetGroupResponse.ACL out_acl = new GetGroupResponse.ACL();
      // TODO: Don't return groupKeyEncrypted if it isn't actually for the user!
      out_acl.groupKeyEncryptedForMe = acl.getGroupKeyEncryptedForMe();
      assert out_acl.groupKeyEncryptedForMe != null;
      out_acl.level = acl.getLevel();
      if (null != acl.getMemberGroupId()) {
        DBGroup memberGroup = acl.loadMemberGroup(mgr.groupDao);
        out_acl.myPublicKey = memberGroup.getPublicKeyString();
        out_acl.memberGroup = memberGroup.getId();
      } else if (null != acl.getMemberIdentityId()) {
        DBIdentity memberIdentity = acl.loadMemberIdentity(mgr.identityDao);
        out_acl.myPublicKey = memberIdentity.getPublicKeyString();
        out_acl.memberIdentity = memberIdentity.getName();
      } else {
        throw new InvalidRequestException("Invalid ACL: both memberGroup and memberIdentity are null");
      }
      out.acls.add(out_acl);
    }
  }
}
