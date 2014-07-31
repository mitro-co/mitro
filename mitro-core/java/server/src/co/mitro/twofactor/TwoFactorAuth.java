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

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;

import co.mitro.core.crypto.KeyInterfaces.KeyFactory;
import co.mitro.core.server.ManagerFactory;

import com.google.common.base.Strings;

@WebServlet("/TwoFactorAuth")
public class TwoFactorAuth extends ServerSignedTwoFactorServlet {
  private static final long serialVersionUID = 1L;

  public TwoFactorAuth(ManagerFactory managerFactory, KeyFactory keyFactory) {
    super(managerFactory, keyFactory, HttpMethod.GET);
    // Crash on startup if secrets aren't loaded
    TwoFactorSigningService.checkInitialized();
  }

  public static class TemplateData {
    public boolean enabledTwoFactorAuth;
    public boolean changePassword;
    public String token;
    public String signature;
    public String errorMessage;

    public TemplateData(boolean enabledTwoFactorAuth, boolean changePassword, String token, String signature, String errorMessage) {
      this.changePassword = changePassword;
      this.enabledTwoFactorAuth = enabledTwoFactorAuth;
      this.token = token;
      this.signature = signature;
      this.errorMessage = errorMessage;
    }
  }

  public static boolean isPasswordChangeRequired(HttpServletRequest request) {
    return !Strings.isNullOrEmpty(request.getParameter("changePassword")) ;
  }

  protected void processRequest(TwoFactorRequestContext context) throws IOException {
    String token = context.params.token;
    String signature = context.params.signature;
    boolean twoFactorEnabled = context.identity.isTwoFactorAuthEnabled();
    boolean changePasswordRequired = isPasswordChangeRequired(context.request);

    TemplateData templateData = new TemplateData(twoFactorEnabled, changePasswordRequired, token, signature, "");
    renderTemplate("TwoFactorAuth.mustache", templateData, context.response);
  }
}
