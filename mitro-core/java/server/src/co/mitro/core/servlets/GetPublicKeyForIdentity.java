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

import java.sql.SQLException;
import java.util.Set;

import javax.servlet.annotation.WebServlet;

import co.mitro.core.accesscontrol.AuthenticatedDB;
import co.mitro.core.crypto.KeyInterfaces.CryptoError;
import co.mitro.core.crypto.KeyInterfaces.KeyFactory;
import co.mitro.core.exceptions.MitroServletException;
import co.mitro.core.exceptions.TooManyAccountsException;
import co.mitro.core.exceptions.UserVisibleException;
import co.mitro.core.server.ManagerFactory;
import co.mitro.core.server.data.DBAcl.CyclicGroupError;
import co.mitro.core.server.data.DBGroup;
import co.mitro.core.server.data.DBIdentity;
import co.mitro.core.server.data.DBUserName;
import co.mitro.core.server.data.RPC;
import co.mitro.core.server.data.RPC.MitroRPC;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;


/**
 * Returns a map of <uid -> public keys> for a list of identities.
 */
@WebServlet("/api/GetPublicKeyForIdentity")
public class GetPublicKeyForIdentity extends MitroServlet {
	private static final long serialVersionUID = 1L;

  public GetPublicKeyForIdentity(ManagerFactory managerFactory, KeyFactory keyFactory) {
    super(managerFactory, keyFactory);
  }

  @Override
  protected MitroRPC processCommand(MitroRequestContext context)
      throws SQLException, MitroServletException {
    RPC.GetPublicKeysForIdentityRequest in = gson.fromJson(
        context.jsonRequest,
        RPC.GetPublicKeysForIdentityRequest.class); 

    RPC.GetPublicKeyForIdentityResponse out = new RPC.GetPublicKeyForIdentityResponse();
    out.userIdToPublicKey = Maps.newTreeMap();
    out.missingUsers = Lists.newLinkedList();
    Set<String> userIdSet = Sets.newHashSet(in.userIds);
    
    for (DBIdentity i : DBIdentity.getUsersFromNames(context.manager, in.userIds)) {
      Set<String> aliases = DBUserName.getAliasesForIdentity(context.manager, i);
      Set<String> matchingAliasesForThisUser = Sets.intersection(userIdSet, aliases).immutableCopy();
      if (matchingAliasesForThisUser.size() > 1) {
        throw new UserVisibleException("The following emails are aliases and cannot"
            +" be included simultaneously: "
            + Joiner.on(",").join(matchingAliasesForThisUser));
      }
      for (String email : matchingAliasesForThisUser) {
        out.userIdToPublicKey.put(email, i.getPublicKeyString());
        userIdSet.remove(email);
      }
    }

    if (!userIdSet.isEmpty()) {
      assert in.addMissingUsers : "addMissingUsers must be true.";

      if (!context.isGroupSyncRequest() &&
          !isPermittedToGetMissingUsers(context.requestor.getName(), userIdSet.size())) {
        throw new TooManyAccountsException(Integer.toString(in.userIds.size()));
      }

      try {
        for (String newUser : userIdSet) {
          DBIdentity identity = InviteNewUser.inviteNewUser(keyFactory, context.manager, context.requestor, newUser);
          out.userIdToPublicKey.put(newUser, identity.getPublicKeyString());
        }
      } catch (CryptoError|CyclicGroupError e) {
        throw new MitroServletException(e);
      }
    }
    @SuppressWarnings("deprecation")
    AuthenticatedDB udb = AuthenticatedDB.deprecatedNew(context.manager, context.requestor);
    if (null != in.groupIds && !in.groupIds.isEmpty()) {
      for (Integer gid : in.groupIds) {
        DBGroup group = udb.getGroupForAddSecret(gid);
        assert (group != null) : "Invalid permissions";
        out.groupIdToPublicKey.put(group.getId(), group.getPublicKeyString());
      }
    }
    return out;
  }
}
