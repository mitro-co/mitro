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
package co.mitro.core.servlets;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Set;

import javax.servlet.annotation.WebServlet;

import co.mitro.core.exceptions.InvalidRequestException;
import co.mitro.core.exceptions.MitroServletException;
import co.mitro.core.server.data.DBAcl;
import co.mitro.core.server.data.DBAudit;
import co.mitro.core.server.data.DBGroup;
import co.mitro.core.server.data.RPC;
import co.mitro.core.server.data.RPC.MitroRPC;

import com.google.common.base.Joiner;
import com.google.common.collect.Sets;

/**
 * Servlet implementation class TestServlet
 */
@WebServlet("/api/AddGroup")
public class AddGroup extends AbstractAddEditGroup {
  private static final long serialVersionUID = 1L;

  @Override
  protected MitroRPC processCommand(MitroRequestContext context) throws IOException, SQLException, MitroServletException {
    RPC.AddGroupRequest in = gson.fromJson(context.jsonRequest,
        RPC.AddGroupRequest.class);

    // TODO: verify that the user has the ability to edit this group
    // DBIdentity me = getIdentityForUserId(mgr, in.myUserId);
    // if (!me.equals(requestor)) {
    // throw new MitroServletException("User ID does not match rpc requestor");
    // }

    DBGroup newGroup = new DBGroup();
    if (null != in.scope) {
      newGroup.setScope(in.scope);
    }
    // TODO: this is a bit ugly
    MitroRPC out = addEditGroupCommand(context.manager, in, newGroup);
    context.manager.groupDao.refresh(newGroup);

    // check permissions on the group.
    Set<Integer> permittedUsers = Sets.newHashSet();
    newGroup.putDirectUsersIntoSet(permittedUsers,
        DBAcl.modifyGroupSecretsAccess());
    if (!permittedUsers.contains(context.requestor.getId())) {
      // We cannot allow a group to be created where the requestor cannot modify
      // group secrets.
      throw new InvalidRequestException(
          "Cannot create group where requestor"
              + context.requestor.getName()
              + ";"
              + context.requestor.getId()
              + " does not have permission to modify group secrets. Permitted users:"
              + Joiner.on(",").join(permittedUsers));
    }

    context.manager.addAuditLog(DBAudit.ACTION.ADD_GROUP, null, null, newGroup, null, null);
    return out;

  }
}
