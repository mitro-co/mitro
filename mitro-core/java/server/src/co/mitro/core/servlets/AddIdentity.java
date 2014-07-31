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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.mitro.core.crypto.KeyInterfaces.KeyFactory;
import co.mitro.core.exceptions.InvalidRequestException;
import co.mitro.core.exceptions.MitroServletException;
import co.mitro.core.exceptions.UserExistsException;
import co.mitro.core.server.ManagerFactory;
import co.mitro.core.server.data.DBAcl;
import co.mitro.core.server.data.DBAudit;
import co.mitro.core.server.data.DBEmailQueue;
import co.mitro.core.server.data.DBIdentity;
import co.mitro.core.server.data.RPC;
import co.mitro.core.server.data.RPC.AddGroupRequest;
import co.mitro.core.server.data.RPC.AddGroupResponse;
import co.mitro.core.server.data.RPC.MitroRPC;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;


@WebServlet("/api/AddIdentity")
public class AddIdentity extends MitroServlet {
  private static final long serialVersionUID = 1L;
  private static final Logger logger = LoggerFactory.getLogger(AddIdentity.class);
  /**
   * If false, new users are not verified. Set to true to disable verification.
   * TODO: Fix regression tests and remove this flag.
   * TODO: This is not thread-safe.
   */
  public static boolean defaultVerifiedState = false;

  public AddIdentity(ManagerFactory managerFactory, KeyFactory keyFactory) {
    super(managerFactory, keyFactory);
  }

  protected boolean isReadOnly() {
    return false;
  }

  @Override
  protected MitroRPC processCommand(MitroRequestContext context)
      throws IOException, SQLException, MitroServletException {
    // The requestor's signature is verified by MitroServlet.doPost()
    
    RPC.AddIdentityRequest in = gson.fromJson(context.jsonRequest, RPC.AddIdentityRequest.class);
    DBIdentity iden = new DBIdentity();
    
    if (null != DBIdentity.getIdentityForUserName(context.manager, in.userId)) {
      throw new UserExistsException(in.userId);
    }

    if (!Util.isEmailAddress(in.userId)) {
      throw new InvalidRequestException(
          String.format("Address '%s' is not a valid email address", in.userId));
    }

    iden.setVerified(defaultVerifiedState);
    iden.setName(in.userId);
    iden.setEncryptedPrivateKeyString(in.encryptedPrivateKey);
    iden.setPublicKeyString(in.publicKey);
    iden.setAnalyticsId(in.analyticsId);
    DBIdentity.createUserInDb(context.manager, iden);

    // send the validation email
    DBEmailQueue email =
        DBEmailQueue.makeAddressVerification(iden.getName(), iden.getVerificationUid(), context.requestServerUrl);
    context.manager.emailDao.create(email);
    if (defaultVerifiedState) {
      logger.warn("Email verification disabled for user: {}", iden.getName());
    }

    RPC.AddIdentityResponse out = new RPC.AddIdentityResponse();
    out.verified = iden.isVerified();
    out.unsignedLoginToken = GetMyPrivateKey.makeLoginTokenString(iden, null, in.deviceId);
    context.manager.addAuditLog(DBAudit.ACTION.CREATE_IDENTITY, iden, null, null, null, null);
    
    if (!Strings.isNullOrEmpty(in.groupKeyEncryptedForMe) && !Strings.isNullOrEmpty(in.groupPublicKey)) {
      AddGroupRequest rqst = new AddGroupRequest();
      rqst.autoDelete = false;
      rqst.publicKey = in.groupPublicKey;
      rqst.signatureString = "TODO"; // this mimics what the js code does.
      rqst.name = ""; // this cannot be null.
      rqst.acls = Lists.newArrayList();
      AddGroupRequest.ACL acl = new AddGroupRequest.ACL();
      acl.groupKeyEncryptedForMe = in.groupKeyEncryptedForMe;
      acl.level = DBAcl.AccessLevelType.ADMIN;
      acl.memberIdentity = iden.getName();
      acl.myPublicKey = iden.getPublicKeyString();
      rqst.acls.add(acl);
      AddGroupResponse rsp = (AddGroupResponse) new AddGroup().processCommand(new MitroRequestContext(iden, gson.toJson(rqst), context.manager, context.platform));
      out.privateGroupId = rsp.groupId;
    }
    
    // authorize this device
    assert(!Strings.isNullOrEmpty(in.deviceId));
    GetMyDeviceKey.maybeGetOrCreateDeviceKey(context.manager, iden, in.deviceId, false, context.platform);
    return out;
  }
}
