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
/**
 * 
 */
package co.mitro.core.servlets;

import java.io.IOException;
import java.sql.SQLException;

import javax.servlet.annotation.WebServlet;

import co.mitro.core.exceptions.MitroServletException;
import co.mitro.core.server.data.DBAudit;
import co.mitro.core.server.data.DBPendingGroup;
import co.mitro.core.server.data.RPC;
import co.mitro.core.server.data.RPC.MitroRPC;

import com.j256.ormlite.stmt.DeleteBuilder;
import com.j256.ormlite.stmt.SelectArg;

/**
 * @author vijayp
 * 
 */
@WebServlet("/api/RemovePendingGroupApprovals")
public class RemoveAllPendingGroupApprovalsForScope extends MitroServlet {

  /**
   * 
   */
  private static final long serialVersionUID = 7257988672701779890L;

  /*
   * (non-Javadoc)
   * 
   * @see
   * co.mitro.core.servlets.MitroServlet#processCommand(co.mitro.core.server
   * .data.DBIdentity, java.lang.String, co.mitro.core.server.Manager)
   */
  @Override
  protected MitroRPC processCommand(MitroRequestContext context) throws IOException, SQLException, MitroServletException {
    RPC.RemoveAllPendingGroupApprovalsForScopeRequest in = gson.fromJson(context.jsonRequest, RPC.RemoveAllPendingGroupApprovalsForScopeRequest.class);
    
    // TODO: verify auth
    
    DeleteBuilder<DBPendingGroup, Integer> deleter = context.manager.pendingGroupDao.deleteBuilder();
    deleter.where().eq(DBPendingGroup.SCOPE_NAME, new SelectArg(in.scope));
    deleter.delete();
    
    context.manager.addAuditLog(DBAudit.ACTION.REMOVE_PENDING_GROUP_SYNC, context.requestor, null, /*group*/null, null, in.scope);
    return new MitroRPC();
  }
}
