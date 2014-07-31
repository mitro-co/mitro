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
import java.net.URLEncoder;
import java.sql.SQLException;

import javax.servlet.annotation.WebServlet;

import org.keyczar.exceptions.KeyczarException;

import co.mitro.core.crypto.KeyInterfaces.KeyFactory;
import co.mitro.core.exceptions.DoTwoFactorAuthException;
import co.mitro.core.exceptions.MitroServletException;
import co.mitro.core.server.ManagerFactory;
import co.mitro.core.server.data.RPC;
import co.mitro.core.server.data.RPC.MitroRPC;
import co.mitro.twofactor.TwoFactorSigningService;

import com.google.common.base.Strings;

@WebServlet("/api/ChangePwdTwoFactorRequired")
public class CheckTwoFactorRequired extends MitroServlet {
  private static final long serialVersionUID = 1L;

  public CheckTwoFactorRequired(ManagerFactory mf, KeyFactory keyFactory) {
    super(mf, keyFactory);
    // Crash on startup if secrets aren't loaded
    TwoFactorSigningService.checkInitialized();
  }

  // this either returns an object with either an null URL or a URL that goes to
  // the TwoFactorAuth website to log in.
  @Override
  protected MitroRPC processCommand(MitroRequestContext context)
      throws IOException, SQLException, MitroServletException {
    RPC.CheckTwoFactorRequiredRequest in = gson.fromJson(context.jsonRequest,
        RPC.CheckTwoFactorRequiredRequest.class);
    String url = null;
    // url stays null if 2fa isn't enabled. else, changes to 2fa login page
    if (context.requestor.isTwoFactorAuthEnabled()) {
      String token = GetMyPrivateKey.makeLoginTokenString(context.requestor,
          in.extensionId, in.deviceId);
      String signedToken;
      try {
        signedToken = TwoFactorSigningService.signToken(token);
      } catch (KeyczarException e) {
        throw new MitroServletException(e);
      }
      url = context.requestServerUrl + "/mitro-core/TwoFactorAuth?token="
          + URLEncoder.encode(token, "UTF-8") + "&signature="
          + URLEncoder.encode(signedToken, "UTF-8");
    }
    RPC.CheckTwoFactorRequiredResponse out = new RPC.CheckTwoFactorRequiredResponse();
    out.twoFactorUrl = url;
    return out;
  }

  // this will throw an exception if the person has 2fa enabled but the
  // token/signature isn't provided or is incorrect or old
  public static boolean checkTwoFactorSecret(MitroRequestContext context,
      RPC.TwoFactorAuthRequest in) throws DoTwoFactorAuthException {
    if (context.requestor.isTwoFactorAuthEnabled()) {
      boolean tokenExists = !Strings.isNullOrEmpty(in.tfaToken);
      boolean signatureExists = !Strings.isNullOrEmpty(in.tfaSignature);
      if (!tokenExists || !signatureExists) {
        throw new DoTwoFactorAuthException("");
      }

      RPC.LoginToken tokenInGson = gson.fromJson(in.tfaToken, RPC.LoginToken.class);
      boolean twoFactorVerified = tokenInGson.twoFactorAuthVerified;
      if (!twoFactorVerified) {
        throw new DoTwoFactorAuthException("");
      } else if (!TwoFactorSigningService.verifySignatureAndTimestamp(in.tfaToken, in.tfaSignature, tokenInGson.timestampMs)) {
        throw new DoTwoFactorAuthException("");
      } else {
        return true;
      }
    }
    return false;
  }
}
