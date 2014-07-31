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

import javax.servlet.annotation.WebServlet;

import co.mitro.core.accesscontrol.AuthenticatedDB;
import co.mitro.core.exceptions.MitroServletException;
import co.mitro.core.exceptions.PermissionException;
import co.mitro.core.exceptions.UserVisibleException;
import co.mitro.core.server.Manager;
import co.mitro.core.server.data.DBGroup;
import co.mitro.core.server.data.DBIdentity;
import co.mitro.core.server.data.RPC;
import co.mitro.core.server.data.RPC.MitroRPC;


@WebServlet("/api/DeleteGroup")
public class DeleteGroup extends MitroServlet {
  private static final long serialVersionUID = 1L;

  @Override
  protected MitroRPC processCommand(MitroRequestContext context) throws IOException, SQLException, MitroServletException {
    RPC.DeleteGroupRequest in = gson.fromJson(context.jsonRequest,
        RPC.DeleteGroupRequest.class);

    DBGroup group = context.manager.groupDao.queryForId(in.groupId);
    if (group == null) {
      throw new MitroServletException("Group id " + in.groupId + " does not exist");
    }

    // the RPC API should never get called for autodelete groups
    if (group.isAutoDelete()) {
      throw new MitroServletException("cannot delete autodelete groups");
    }

    deleteGroup(context.requestor, context.manager, group);
    RPC.DeleteGroupResponse out = new RPC.DeleteGroupResponse();
    return out;
  }

  protected static void deleteGroup(DBIdentity requestor,
      Manager mgr, DBGroup groupToDelete) throws SQLException,
      MitroServletException {
    // any user might remove the last secret from an autodelete group; in that case this gets called
    if (!groupToDelete.isAutoDelete()) {
      @SuppressWarnings("deprecation")
      AuthenticatedDB userDb = AuthenticatedDB.deprecatedNew(mgr, requestor);
      if (userDb.getGroupAsUserOrOrgAdmin(groupToDelete.getId()) == null) {
        throw new PermissionException("user does not have permission to access group");
      }
    }

    if (!groupToDelete.getGroupSecrets().isEmpty()) {
      throw new UserVisibleException("Cannot delete a team with secrets. " +
          "Please remove all secrets from this team and try again.");
    }

    if (groupToDelete.isPrivateUserGroup()) {
      throw new MitroServletException("cannot delete private group id " + groupToDelete.getId());
    }

    if (groupToDelete.isTopLevelOrganization()) {
      throw new MitroServletException("cannot delete organization");
    }

    mgr.aclDao.delete(groupToDelete.getAcls());
    //TODO: this does not work with nested groups properly.
    // with nested groups, we really need to make sure this group
    // isn't the admin for another group (I think this is right, right?)
    mgr.groupDao.delete(groupToDelete);
  }
}
