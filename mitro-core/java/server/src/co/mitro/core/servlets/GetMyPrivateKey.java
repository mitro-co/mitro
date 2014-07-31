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

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.sql.SQLException;

import javax.servlet.annotation.WebServlet;

import org.keyczar.exceptions.KeyczarException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.mitro.core.crypto.KeyInterfaces.CryptoError;
import co.mitro.core.crypto.KeyInterfaces.KeyFactory;
import co.mitro.core.crypto.KeyInterfaces.PublicKeyInterface;
import co.mitro.core.exceptions.DoEmailVerificationException;
import co.mitro.core.exceptions.DoTwoFactorAuthException;
import co.mitro.core.exceptions.MitroServletException;
import co.mitro.core.server.ManagerFactory;
import co.mitro.core.server.data.DBAudit;
import co.mitro.core.server.data.DBEmailQueue;
import co.mitro.core.server.data.DBIdentity;
import co.mitro.core.server.data.RPC;
import co.mitro.core.server.data.RPC.GetMyPrivateKeyRequest;
import co.mitro.core.server.data.RPC.MitroRPC;
import co.mitro.twofactor.CryptoForBackupCodes;
import co.mitro.twofactor.TwoFactorCodeChecker;
import co.mitro.twofactor.TwoFactorSigningService;

import com.google.common.base.Strings;


@WebServlet("/api/GetMyPrivateKey")
public class GetMyPrivateKey extends MitroServlet {
  private static final Logger logger = LoggerFactory.getLogger(MitroServlet.class);
  private static final long serialVersionUID = 1L;

  public static final String DEMO_ACCOUNT = "mitro.demo@gmail.com";
  public static boolean doEmailVerification = true;

  public GetMyPrivateKey(ManagerFactory mf, KeyFactory keyFactory) {
    super(mf, keyFactory);
    // Crash on startup if secrets aren't loaded
    TwoFactorSigningService.checkInitialized();
  }

  @Override
  protected MitroRPC processCommand(MitroRequestContext context) throws MitroServletException, DoTwoFactorAuthException, DoEmailVerificationException, SQLException {
    RPC.GetMyPrivateKeyRequest in = gson.fromJson(context.jsonRequest,
        RPC.GetMyPrivateKeyRequest.class);
    boolean okToProvideKey = false;
    DBIdentity identity = null; 
    String deviceKeyString = null;
    boolean twoFactorRequired = true;
    try {
      identity = DBIdentity.getIdentityForUserName(context.manager, in.userId);
      if (null == identity) {
        // Throw this so we don't leak info about which email accounts are valid.
        throw new DoEmailVerificationException();
      }
      twoFactorRequired = identity.isTwoFactorAuthEnabled();
      deviceKeyString = GetMyDeviceKey.maybeGetClientKeyForLogin(context.manager, identity, in.deviceId, context.platform);
    } catch (SQLException | MitroServletException e) {
      throw new DoEmailVerificationException(e);  
    }

    // see if we know about this device. If not, we make the user log in by clicking on an email.
    if (null == deviceKeyString) {
      if (identity.getChangePasswordOnNextLogin()) {
        // Hack for invited users: Auto-register devices
        // Previously we limited this to 1, but users who abandoned it and tried later ran into
        // weird auto-login loops. Easier to permit multiple devices until they change password?
        // TODO: Verify a token/nonce to ensure the user came from the invite email?
        deviceKeyString = GetMyDeviceKey.maybeGetOrCreateDeviceKey(
            context.manager, identity, in.deviceId, false, context.platform);
      } else if (!identity.getName().equals(DEMO_ACCOUNT) && doEmailVerification) {
        if (in.automatic) {
          // The device is unknown but the user has a token, and the device is automatically logging in. 
          // this used to cause a login loop, and we should not send an email
          logger.info("Not actually sending email for {} because it's an automatic login", identity.getName());
          throw new DoEmailVerificationException();
        }
        // we need to do an email verification now.
        sendEmailAndThrow(in, context, identity);
      }
    } 

    if (!Strings.isNullOrEmpty(in.loginToken)) {
      // TODO: this is because the crypto code will throw a NullPointerException
      // if you pass in an empty signature.
      if (null == in.loginTokenSignature) {
        in.loginTokenSignature = "";
      }
      
      RPC.LoginToken signedToken = gson.fromJson(in.loginToken,
          RPC.LoginToken.class);
      if (!signedToken.email.equals(identity.getName())) {
        //throw new MitroServletException("mismatched token");
        throw new DoEmailVerificationException();
      }
      if (!signedToken.twoFactorAuthVerified) {
        // this should be signed by the user.
        try {
          PublicKeyInterface key = keyFactory.loadPublicKey(identity
              .getPublicKeyString());
          if (!key.verify(in.loginToken, in.loginTokenSignature)) {
            //throw new MitroServletException("invalid token");
            throw new DoEmailVerificationException();
          }
          okToProvideKey = true;
        } catch (CryptoError e) {
          // crypto errors mean the signature or key is not valid
          throw new DoEmailVerificationException(e);
        }
      } else { // signed token is a tfa token
        // 2fa was used! verify key and signature
        if (!TwoFactorSigningService.verifySignatureAndTimestamp(
            in.loginToken, in.loginTokenSignature, signedToken.timestampMs)) {
          throw new MitroServletException("invalid 2fa token");
        }
        okToProvideKey = true;
      }
    } // end if(token)

    if (!okToProvideKey) {
      // we don't have a token, so this is a new login. We need to verify email and maybe TFA
      // we know about the device
      if (twoFactorRequired) {
        boolean twoFactorCodeCorrect = false;
        if (!Strings.isNullOrEmpty(in.twoFactorCode)) {
          long twoFactorCode = Long.parseLong(in.twoFactorCode);
          twoFactorCodeCorrect = TwoFactorCodeChecker.checkCode(
              identity.getTwoFactorSecret(), twoFactorCode, System.currentTimeMillis())
              || CryptoForBackupCodes.tryBackupCode(identity.getTwoFactorSecret(),
                  in.twoFactorCode, identity, context.manager);
        }
        if (!twoFactorCodeCorrect) {
          sendToTFAAndThrow(context, in, identity);
        } else {
          okToProvideKey = true;
        }
      }
    }
    
    
    RPC.GetMyPrivateKeyResponse out = new RPC.GetMyPrivateKeyResponse();
    out.myUserId = identity.getName();
    out.encryptedPrivateKey = identity.getEncryptedPrivateKeyString();
    out.changePasswordOnNextLogin = identity.getChangePasswordOnNextLogin();
    out.verified = identity.isVerified();
    out.unsignedLoginToken = makeLoginTokenString(identity, in.extensionId, in.deviceId);

    if (!okToProvideKey) {
      // in this case we have an authorized device but no token. 
      // We provide the encrypted key but not the device key.
      out.deviceKeyString = null;
    } else {
      out.deviceKeyString = deviceKeyString;
    }
    
    try {
      context.manager.addAuditLog(DBAudit.ACTION.GET_PRIVATE_KEY, identity, null, null, null,
          null);
    } catch (SQLException e) {
      throw new DoEmailVerificationException(e);
    }
    
    // authorize this device if it has not yet been authorized.
    assert(!Strings.isNullOrEmpty(in.deviceId));
    if (deviceKeyString == null) {
      GetMyDeviceKey.maybeGetOrCreateDeviceKey(context.manager, identity, in.deviceId, false, context.platform);
    }
    return out;
  }

  private void sendToTFAAndThrow(MitroRequestContext context, 
      GetMyPrivateKeyRequest in,
      DBIdentity identity) throws MitroServletException {
    // make token check password
    String token = makeLoginTokenString(identity, in.extensionId, in.deviceId);
    try {
      String tokenSignature = TwoFactorSigningService.signToken(token);
      throw new DoTwoFactorAuthException(
          context.requestServerUrl +
          "/mitro-core/TwoFactorAuth?token="
              + URLEncoder.encode(token, "UTF-8") + "&signature="
              + URLEncoder.encode(tokenSignature, "UTF-8"));
    } catch (KeyczarException e) {
      throw new MitroServletException(e);
    } catch (UnsupportedEncodingException e) {
      // this will never happen: UTF-8 must be supported by all JVMs
      throw new MitroServletException(e);
    }
  }

  private void sendEmailAndThrow(GetMyPrivateKeyRequest in, MitroRequestContext context, DBIdentity identity) throws MitroServletException {
    try {
      String token = makeLoginTokenString(identity, in.extensionId, in.deviceId);
      String tokenSignature = TwoFactorSigningService.signToken(token);

      if (!context.manager.isReadOnly()) {
        DBEmailQueue email = DBEmailQueue.makeNewDeviceVerification(identity.getName(), token, tokenSignature, context.platform, context.requestServerUrl);
        context.manager.emailDao.create(email);
        context.manager.commitTransaction();
      }
      // TODO: throw ReadOnlyServerException if isReadOnly, after we don't retry on the secondary!
      logger.info("Forcing user {} to verify device {}", identity.getName(), in.deviceId);
      throw new DoEmailVerificationException();
    } catch (SQLException|KeyczarException e) {
      throw new MitroServletException(e);
    }
  }

  public static String makeLoginTokenString(DBIdentity identity, String extensionId, String deviceId) {
    // TODO: add device id in here.
    RPC.LoginToken lt = new RPC.LoginToken();
    lt.email = identity.getName();
    lt.extensionId = extensionId;
    lt.timestampMs = System.currentTimeMillis();
    lt.deviceId = deviceId;
    return gson.toJson(lt);
  }

}
