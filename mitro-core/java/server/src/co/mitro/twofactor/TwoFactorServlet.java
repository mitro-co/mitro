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
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.mitro.core.crypto.KeyInterfaces.KeyFactory;
import co.mitro.core.server.Manager;
import co.mitro.core.server.ManagerFactory;
import co.mitro.core.server.Templates.ResourceMustacheFactory;
import co.mitro.core.server.data.DBIdentity;

import com.github.mustachejava.Mustache;
import com.google.common.base.Strings;

public abstract class TwoFactorServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;
  // TODO: Maybe this should use server.Templates instead of this static factory?
  private static final ResourceMustacheFactory MUSTACHE_FACTORY = new ResourceMustacheFactory();
  private static final Logger logger = LoggerFactory.getLogger(TwoFactorServlet.class);

  protected final ManagerFactory managerFactory;
  protected final KeyFactory keyFactory;

  public enum HttpMethod {GET, POST}
  protected final HttpMethod method;

  public static final String INCORRECT_CODE_ERROR_MESSAGE =
      "The verification code you entered was incorrect or expired";
  public static final String INVALID_CODE_ERROR_MESSAGE =
      "Invalid verification code";
  public static final String MISSING_CODE_ERROR_MESSAGE =
      "Missing verification code";

  public static class TwoFactorRequestParams {
    public String token;
    public String signature;
    public String username;
    public String codeString;
    public Long timestampMs = null;

    public TwoFactorRequestParams(String token, String signature, String username, String codeString, Long timestampMs) {
      this.token = token;
      this.signature = signature;
      this.username = username;
      this.codeString = codeString;
      this.timestampMs = timestampMs;
    }
  }

  public static class TwoFactorRequestContext {
    public HttpServletRequest request;
    public HttpServletResponse response;
    public TwoFactorRequestParams params;
    public Manager manager;
    public DBIdentity identity;

    TwoFactorRequestContext(HttpServletRequest request, HttpServletResponse response,
        TwoFactorRequestParams params, Manager manager, DBIdentity identity) {
      this.request = request;
      this.response = response;
      this.params = params;
      this.manager = manager;
      this.identity = identity;
    }
  }

  public TwoFactorServlet(ManagerFactory managerFactory, KeyFactory keyFactory, HttpMethod method) {
    this.managerFactory = managerFactory;
    this.keyFactory = keyFactory;
    this.method = method;
  }

  public HttpMethod getMethod() {
    return method;
  }

  public static void preventCaching(HttpServletResponse response) {
    response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate"); // HTTP 1.1.
    response.setHeader("Pragma", "no-cache"); // HTTP 1.0.
    response.setDateHeader("Expires", 0); // Proxies.
  }

  public static void renderTemplate(String templateName, Object b, HttpServletResponse response)
      throws IOException {
    Mustache mustache = MUSTACHE_FACTORY.compilePackageRelative(
        TwoFactorServlet.class, templateName);
    mustache.execute(response.getWriter(), b);
  }

  // Try codeString as both a TFA code and a backup code.
  // Returns null if verification succeeded and an error string on failure.
  // IMPORTANT: caller must commit transaction on successful verification.
  // This is required to reset the backup code, if one was used.
  public String verifyCode(String codeString, String secret, long time, DBIdentity identity, Manager manager)
      throws SQLException {
    if (!Strings.isNullOrEmpty(codeString)) {
      try {
        long code = Long.parseLong(codeString);

        if (TwoFactorCodeChecker.checkCode(secret, code, time) ||
            CryptoForBackupCodes.tryBackupCode(secret, codeString, identity, manager)) {
          return null;
        } else {
          logger.info("invalid code '{}' for user {}", codeString, identity.getName());
          return INCORRECT_CODE_ERROR_MESSAGE;
        }
      } catch (NumberFormatException e) {
        logger.info("invalid number '{}' for user {}", codeString, identity.getName());
        return INVALID_CODE_ERROR_MESSAGE;
      }
    } else {
      return MISSING_CODE_ERROR_MESSAGE;
    }
  }

  abstract protected TwoFactorRequestParams parseRequestParams(HttpServletRequest request);

  abstract protected void processRequest(TwoFactorRequestContext context) throws IOException, SQLException, ServletException;

  abstract protected boolean verifyRequest(TwoFactorRequestContext context);

  protected void doCommon(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    TwoFactorRequestParams params = parseRequestParams(request);

    try (Manager mgr = managerFactory.newManager()) {
      DBIdentity identity = DBIdentity.getIdentityForUserName(mgr, params.username);
      TwoFactorRequestContext context =
          new TwoFactorRequestContext(request, response, params, mgr, identity);

      if (identity == null || !verifyRequest(context)) {
        throw new ServletException("Invalid identity or signature");
      }

      processRequest(context);
    } catch (SQLException e) {
      throw new ServletException("Internal server error");
    }
  }

  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    if (method == HttpMethod.GET) {
      preventCaching(response);
      doCommon(request, response);
    } else {
      super.doGet(request, response);
    }
  }

  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    if (method == HttpMethod.POST) {
      doCommon(request, response);
    } else {
      super.doPost(request, response);
    }
  }
}
