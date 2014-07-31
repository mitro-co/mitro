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
import java.util.Map;

import javax.servlet.annotation.WebServlet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.mitro.core.accesscontrol.AuthenticatedDB;
import co.mitro.core.exceptions.MitroServletException;
import co.mitro.core.server.data.DBAcl;
import co.mitro.core.server.data.DBAudit;
import co.mitro.core.server.data.DBGroupSecret;
import co.mitro.core.server.data.DBServerVisibleSecret;
import co.mitro.core.server.data.RPC;
import co.mitro.core.server.data.RPC.EditSecretContentRequest.SecretContent;
import co.mitro.core.server.data.RPC.MitroRPC;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;


@WebServlet("/api/EditSecretContent")
public class EditSecretContent extends MitroServlet {
  private static final long serialVersionUID = 1L;
  private static final Logger logger = LoggerFactory.getLogger(EditSecretContent.class);

  @Override
  protected MitroRPC processCommand(MitroRequestContext context) throws SQLException, MitroServletException {
    RPC.EditSecretContentRequest in = gson.fromJson(context.jsonRequest,
        RPC.EditSecretContentRequest.class); 

    @SuppressWarnings("deprecation")
    AuthenticatedDB udb = AuthenticatedDB.deprecatedNew(context.manager, context.requestor);
    DBServerVisibleSecret svs = udb.getSecretAsUserOrAdmin(in.secretId, DBAcl.modifyGroupSecretsAccess());
    if (null == svs) {
      throw new MitroServletException("user does not have access to secret");
    }
    setEncryptedDataForSVS(context, in.groupIdToEncryptedData, svs, EmptyClientData.DISALLOW_EMPTY_CLIENT_DATA);
    context.manager.addAuditLog(DBAudit.ACTION.EDIT_SECRET_CONTENT, null, null, null, svs, null);

    return new MitroRPC();
  }

  public static enum EmptyClientData {ALLOW_EMPTY_CLIENT_DATA, DISALLOW_EMPTY_CLIENT_DATA};
  
  static void setEncryptedDataForSVS(MitroRequestContext context,
      Map<Integer, SecretContent> groupIdToEncryptedData,
      DBServerVisibleSecret svs, EmptyClientData allowEmptyClientData) throws MitroServletException, SQLException {
    // find all the group secrets for these groups. TODO: optimize.
    for (DBGroupSecret gs : svs.getGroupSecrets()) {
      int groupId = gs.getGroup().getId();
      SecretContent newContent = groupIdToEncryptedData.get(groupId);
      if (newContent == null) {
        throw new MitroServletException("group:" + groupId + " was omitted for secret:" + svs.getId());
      }
      if (allowEmptyClientData == EmptyClientData.DISALLOW_EMPTY_CLIENT_DATA) {
        Preconditions.checkNotNull(Strings.emptyToNull(newContent.encryptedClientData));
      }
      if (!Strings.isNullOrEmpty(newContent.encryptedClientData)) {
        gs.setClientVisibleDataEncrypted(newContent.encryptedClientData);
      }
      if (!Strings.isNullOrEmpty(newContent.encryptedCriticalData)) {
        gs.setCriticalDataEncrypted(newContent.encryptedCriticalData);
      }
      context.manager.groupSecretDao.update(gs);
      groupIdToEncryptedData.remove(groupId);
    }
    if (!groupIdToEncryptedData.isEmpty()) {
      throw new MitroServletException("secret: " + svs.getId() + " is not visible to groups:" +
          Joiner.on(",").join(groupIdToEncryptedData.keySet()));
    }
  } 
    
}
