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
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.annotation.WebServlet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.mitro.core.accesscontrol.AuthenticatedDB;
import co.mitro.core.crypto.KeyInterfaces.CryptoError;
import co.mitro.core.crypto.KeyInterfaces.KeyFactory;
import co.mitro.core.crypto.KeyInterfaces.PublicKeyInterface;
import co.mitro.core.exceptions.MitroServletException;
import co.mitro.core.server.ManagerFactory;
import co.mitro.core.server.data.DBAcl;
import co.mitro.core.server.data.DBAudit;
import co.mitro.core.server.data.DBGroup;
import co.mitro.core.server.data.DBGroupSecret;
import co.mitro.core.server.data.DBIdentity;
import co.mitro.core.server.data.DBServerVisibleSecret;
import co.mitro.core.server.data.RPC;
import co.mitro.core.server.data.RPC.EditSecretContentRequest.SecretContent;
import co.mitro.core.server.data.RPC.MitroRPC;
import co.mitro.core.servlets.EditSecretContent.EmptyClientData;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;


@WebServlet("/api/internal/UpdateSecretFromAgent")
public class UpdateSecretFromAgent extends MitroServlet {
  private static final long serialVersionUID = 1L;
  private static final Logger logger = LoggerFactory.getLogger(UpdateSecretFromAgent.class);
  private static final Set<String> PERMITTED_USER_ENDINGS = ImmutableSet.of("@mitro.co", "@example.com", "@lectorius.com");

  public UpdateSecretFromAgent(ManagerFactory managerFactory, KeyFactory keyFactory) {
    super(managerFactory, keyFactory);
  }
  
  @Override
  protected MitroRPC processCommand(MitroRequestContext context) throws SQLException, MitroServletException {
    RPC.UpdateSecretFromAgentRequest in = gson.fromJson(context.jsonRequest,
        RPC.UpdateSecretFromAgentRequest.class);
    
    // first off, extract the signed string.
    RPC.UpdateSecretFromAgentRequest.UserData userData = gson.fromJson(in.dataFromUser, RPC.UpdateSecretFromAgentRequest.UserData.class);
    // get the user id that this is supposed to reference
    DBIdentity actingUser = DBIdentity.getIdentityForUserName(context.manager, userData.userId);

    // Check if this user is permitted to delegate access
    List<String> pieces = Splitter.on('@').splitToList(actingUser.getName());
    assert pieces.size() == 2;
    boolean okToProceed = false;
    for (String suffix : PERMITTED_USER_ENDINGS) {
      if (actingUser.getName().endsWith(suffix)) {
        okToProceed = true;
        break;
      }
    }
    if (!okToProceed) {
      throw new MitroServletException("User " + actingUser.getName() + " is not in the permitted domains list.");
    }
    
    try {
      // verify the signature
      if (!keyFactory.loadPublicKey(actingUser.getPublicKeyString()).verify(in.dataFromUser, in.dataFromUserSignature)) {
        throw new MitroServletException("User " + actingUser.getName() + " did not sign the message");
      };
      // glorius hack
      @SuppressWarnings("deprecation")
      AuthenticatedDB udb = AuthenticatedDB.deprecatedNew(context.manager, actingUser);
      DBServerVisibleSecret svs = udb.getSecretAsUserOrAdmin(userData.secretId, DBAcl.modifyGroupSecretsAccess());
      if (null == svs) {
        throw new MitroServletException("user does not have access to secret");
      }
      Map<Integer, SecretContent> groupIdToData = Maps.newHashMap();
      
      for (DBGroupSecret gs : svs.getGroupSecrets()) {
        DBGroup group = gs.getGroup();
        PublicKeyInterface groupKey = keyFactory.loadPublicKey(group.getPublicKeyString());
        
        SecretContent sc = new SecretContent(null, groupKey.encrypt(gson.toJson(userData.criticalData))); 
        groupIdToData.put(group.getId(), sc);
      }

      logger.info("password change agent: update from user {}", actingUser.getName());
      EditSecretContent.setEncryptedDataForSVS(context, groupIdToData, svs, EmptyClientData.ALLOW_EMPTY_CLIENT_DATA);
      context.manager.addAuditLog(DBAudit.ACTION.EDIT_SECRET_CONTENT, null, null, null, svs, null);
      return new MitroRPC();
    } catch (CryptoError e) {
      throw new MitroServletException(e);
    }
  } 
    
}
