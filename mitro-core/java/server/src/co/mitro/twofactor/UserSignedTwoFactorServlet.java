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

import javax.servlet.http.HttpServletRequest;

import co.mitro.core.crypto.KeyInterfaces.CryptoError;
import co.mitro.core.crypto.KeyInterfaces.KeyFactory;
import co.mitro.core.crypto.KeyInterfaces.PublicKeyInterface;
import co.mitro.core.server.ManagerFactory;

import com.google.gson.Gson;

abstract public class UserSignedTwoFactorServlet extends TwoFactorServlet {
  private static final long serialVersionUID = 1L;

  public static class TokenData {
    public String email;
    public String nonce;
  }

  public UserSignedTwoFactorServlet(ManagerFactory managerFactory, KeyFactory keyFactory, HttpMethod method) {
    super(managerFactory, keyFactory, method);
  }

  @Override
  protected TwoFactorRequestParams parseRequestParams(HttpServletRequest request) {
    String token = request.getParameter("token");
    String signature = request.getParameter("signature");
    String codeString = request.getParameter("code");
    TokenData tokenData = new Gson().fromJson(token, TokenData.class);
    return new TwoFactorRequestParams(token, signature, tokenData.email, codeString, null);
  }

  @Override
  protected boolean verifyRequest(TwoFactorRequestContext context) {
    assert context.identity != null;
    boolean requestVerified = false;
    try {
      PublicKeyInterface key = keyFactory.loadPublicKey(context.identity.getPublicKeyString());
      requestVerified = key.verify(context.params.token, context.params.signature);
    } catch (CryptoError e1) {
    }
    return requestVerified;
  }
}
