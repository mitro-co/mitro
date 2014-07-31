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

import javax.servlet.annotation.WebServlet;

import co.mitro.core.crypto.KeyInterfaces.KeyFactory;
import co.mitro.core.server.ManagerFactory;

@WebServlet("/TwoFactorAuth/NewUser")
public class NewUser extends UserSignedTwoFactorServlet {
  private final static long serialVersionUID = 1L;

  public NewUser(ManagerFactory managerFactory, KeyFactory keyFactory) {
    super(managerFactory, keyFactory, HttpMethod.POST);
  }

  public static class TemplateData {
    public String username;
    public String encodedUsername;
    public String secret;
    public String encodedSecret;
    public String token;
    public String signature;
    public String errorMessage;

    public TemplateData(String username, String secret, String token, String signature, String errorMessage)
        throws UnsupportedEncodingException {
      this.username = username;
      this.encodedUsername = URLEncoder.encode(username, "UTF-8");
      this.secret = secret;
      this.encodedSecret = URLEncoder.encode(secret, "UTF-8");
      this.token = token;
      this.signature = signature;
      this.errorMessage = errorMessage;
    }
  }

  @Override
  protected void processRequest(TwoFactorRequestContext context)
      throws IOException {
    TwoFactorRequestParams params = context.params;
    String secret = TwoFactorSecretGenerator.generateSecretKey();
    renderTemplate("NewUser.mustache",
        new TemplateData(params.username, secret, params.token, params.signature, ""),
        context.response);
  }
}
