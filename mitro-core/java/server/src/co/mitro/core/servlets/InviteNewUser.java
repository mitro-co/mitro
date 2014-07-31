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

import co.mitro.core.crypto.KeyInterfaces.CryptoError;
import co.mitro.core.crypto.KeyInterfaces.KeyFactory;
import co.mitro.core.crypto.KeyInterfaces.PrivateKeyInterface;
import co.mitro.core.crypto.KeyInterfaces.PublicKeyInterface;
import co.mitro.core.exceptions.MitroServletException;
import co.mitro.core.exceptions.UserVisibleException;
import co.mitro.core.server.Manager;
import co.mitro.core.server.ManagerFactory;
import co.mitro.core.server.data.DBAcl;
import co.mitro.core.server.data.DBAcl.CyclicGroupError;
import co.mitro.core.server.data.DBAudit;
import co.mitro.core.server.data.DBEmailQueue;
import co.mitro.core.server.data.DBGroup;
import co.mitro.core.server.data.DBIdentity;
import co.mitro.core.server.data.RPC.InviteNewUserRequest;
import co.mitro.core.server.data.RPC.InviteNewUserResponse;
import co.mitro.core.server.data.RPC.MitroRPC;
import co.mitro.core.util.Random;

import com.google.common.collect.Maps;

@WebServlet("/api/InviteNewUsers")
public class InviteNewUser extends MitroServlet {
  private static final Logger logger = LoggerFactory.getLogger(InviteNewUser.class);
  private static final long serialVersionUID = 1L;
  // 12 chars = basically impossible to brute force
  // http://blog.agilebits.com/2013/04/16/1password-hashcat-strong-master-passwords/
  static final int GENERATED_PASSWORD_LENGTH = 12;

  // Allow dependencies to be injected for testing
  public InviteNewUser(ManagerFactory managerFactory, KeyFactory keyFactory) {
    super(managerFactory, keyFactory);
  }

  @Override
  protected MitroRPC processCommand(
      MitroRequestContext context)
      throws IOException, SQLException, MitroServletException {
    InviteNewUserRequest request = gson.fromJson(context.jsonRequest, InviteNewUserRequest.class);
    InviteNewUserResponse response = new InviteNewUserResponse();
    response.publicKeys = Maps.newHashMap();

    try {
      for (String address : request.emailAddresses) {
        DBIdentity invitee = inviteNewUser(context.manager, context.requestor, address);
        response.publicKeys.put(address, invitee.getPublicKeyString());
      }
    } catch (CryptoError|CyclicGroupError e) {
      throw new MitroServletException(e);
    }

    return response;
  }

  protected DBIdentity inviteNewUser(Manager manager, DBIdentity requestor, String emailAddress)
      throws CryptoError, SQLException, MitroServletException, CyclicGroupError {
    return InviteNewUser.inviteNewUser(keyFactory, manager, requestor, emailAddress);
  }

  /**
   * Creates a new identity for a user that doesn't exist, generating a key and password.
   * Queues an email to the user with their temporary password.
   */
  public static DBIdentity inviteNewUser(
      KeyFactory keyFactory, Manager manager, DBIdentity requestor, String emailAddress)
      throws CryptoError, SQLException, MitroServletException, CyclicGroupError {
    if (!Util.isEmailAddress(emailAddress)) {
      throw new UserVisibleException("Invalid email address: " + emailAddress);
    }
    final String reqEmail = requestor.getName();
    assert Util.isEmailAddress(reqEmail);

    // generate a key
    logger.debug("generating key for address {}", emailAddress);
    PrivateKeyInterface privateKey = keyFactory.generate();
    PublicKeyInterface publicKey = privateKey.exportPublicKey();

    // generate a password
    String generatedPassword = Random.makeRandomAlphanumericString(GENERATED_PASSWORD_LENGTH);

    // Create the identity; we rely on the unique name constraint
    DBIdentity identity = new DBIdentity();
    
    // identites that are invited are by default verified by email
    identity.setVerified(true);
    identity.setName(emailAddress);
    identity.setChangePasswordOnNextLogin(true);
    identity.setPublicKeyString(publicKey.toString());
    identity.setEncryptedPrivateKeyString(privateKey.exportEncrypted(generatedPassword));
    DBIdentity.createUserInDb(manager, identity);
    manager.identityDao.refresh(identity);
    // Create a private group
    PrivateKeyInterface groupKey = keyFactory.generate();
    PublicKeyInterface publicGroupKey = groupKey.exportPublicKey();

    DBGroup privateGroup = new DBGroup();
    privateGroup.setName("");
    privateGroup.setPublicKeyString(publicGroupKey.toString());
    privateGroup.setAutoDelete(false);
    manager.groupDao.create(privateGroup);
    manager.groupDao.refresh(privateGroup);

    // Encrypt the key for the user
    DBAcl acl = new DBAcl();
    acl.setGroup(privateGroup);
    acl.setMemberIdentity(identity);
    acl.setLevel(DBAcl.AccessLevelType.ADMIN);
    acl.setGroupKeyEncryptedForMe(publicKey.encrypt(groupKey.toString()));
    manager.aclDao.create(acl);
    manager.addAuditLog(DBAudit.ACTION.INVITE_NEW_USER, requestor, identity, privateGroup, null, null);
    // send an email
    DBEmailQueue email =
        DBEmailQueue.makeInvitation(reqEmail, emailAddress, generatedPassword);
    manager.emailDao.create(email);

    return identity;
  }
}
