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
package co.mitro.twofactor;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;

import org.keyczar.exceptions.KeyczarException;

import co.mitro.core.crypto.KeyInterfaces.KeyFactory;
import co.mitro.core.server.Manager;
import co.mitro.core.server.ManagerFactory;
import co.mitro.core.server.data.DBIdentity;
import co.mitro.core.server.data.RPC;

import com.google.common.base.Strings;
import com.google.gson.Gson;

@WebServlet("/TwoFactorAuth/Verify")
public class Verify extends ServerSignedTwoFactorServlet {
  private static final long serialVersionUID = 1L;
  private static final Gson gson = new Gson();

  public Verify(ManagerFactory managerFactory, KeyFactory keyFactory) {
    super(managerFactory, keyFactory, HttpMethod.POST);
    // Crash on startup if secrets aren't loaded
    TwoFactorSigningService.checkInitialized();
  }

  @Override
  protected void processRequest(TwoFactorRequestContext context)
      throws IOException, SQLException, ServletException {
    TwoFactorRequestParams params = context.params;
    DBIdentity identity = context.identity;
    Manager manager = context.manager;
    String secret = identity.getTwoFactorSecret();
    long t = System.currentTimeMillis();

    String error = verifyCode(params.codeString, secret, t, identity, manager);
    if (error == null) {
      identity.setLastAuthMs(t);
      context.manager.identityDao.update(identity);
      context.manager.commitTransaction();

      RPC.LoginToken loginToken = gson.fromJson(params.token, RPC.LoginToken.class);
      loginToken.twoFactorAuthVerified = true;
      assert (!Strings.isNullOrEmpty(loginToken.extensionId));
      String redirectPath;
      if (TwoFactorAuth.isPasswordChangeRequired(context.request)) {
        redirectPath = "change-password.html";
      } else {
        redirectPath = "popup.html";
      }
      context.response.sendRedirect(makeRedirectUrl(loginToken, redirectPath));
    } else {
      boolean changePasswordRequired = TwoFactorAuth.isPasswordChangeRequired(context.request);
      TwoFactorAuth.TemplateData templateData =
          new TwoFactorAuth.TemplateData(true, changePasswordRequired, params.token, params.signature, error);
      renderTemplate("TwoFactorAuth.mustache", templateData, context.response);
    }
  }
  
  public String makeRedirectUrl(RPC.LoginToken in, String location) throws ServletException {
    try {
      String newToken = gson.toJson(in);
      return "chrome-extension://"
      + in.extensionId
      + "/html/"
      + location
      + "#token="
      + URLEncoder.encode(newToken, "UTF-8") + "&token_signature="
      + URLEncoder.encode(TwoFactorSigningService.signToken(newToken), "UTF-8") + "&u="
      + URLEncoder.encode(in.email, "UTF-8");
    } catch (UnsupportedEncodingException|KeyczarException e) {
      throw new ServletException("Internal server error");
    }
  }
}
