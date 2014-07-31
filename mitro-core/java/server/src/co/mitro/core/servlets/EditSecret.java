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

import javax.servlet.annotation.WebServlet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.mitro.core.accesscontrol.AuthenticatedDB;
import co.mitro.core.exceptions.MitroServletException;
import co.mitro.core.server.data.DBServerVisibleSecret;
import co.mitro.core.server.data.RPC;
import co.mitro.core.server.data.RPC.MitroRPC;


@WebServlet("/api/EditSecret")
public class EditSecret extends AbstractAddEditGroup {
  private static final long serialVersionUID = 1L;
  private static final Logger logger = LoggerFactory.getLogger(EditSecret.class);

  @Override
  protected MitroRPC processCommand(MitroRequestContext context) throws SQLException, MitroServletException {
    RPC.EditSecretRequest in = gson.fromJson(context.jsonRequest,
        RPC.EditSecretRequest.class); 
    @SuppressWarnings("deprecation")
    AuthenticatedDB udb = AuthenticatedDB.deprecatedNew(context.manager, context.requestor);
    
    DBServerVisibleSecret svs = udb.getServerSecretForViewOrEdit(in.secretId);
    if (svs == null) {
      throw new MitroServletException("could not access secret for udpate");
    }
    svs.setViewable(in.isViewable);
    context.manager.svsDao.update(svs);
    
    // TODO: add audit.
    return new RPC.EditSecretResponse();
  }
}
