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
import java.io.StringWriter;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.mitro.core.crypto.KeyInterfaces.KeyFactory;
import co.mitro.core.exceptions.MitroServletException;
import co.mitro.core.server.Manager;
import co.mitro.core.server.ManagerFactory;
import co.mitro.core.server.Templates;
import co.mitro.core.server.data.DBAudit;
import co.mitro.core.server.data.DBIdentity;
import co.mitro.core.server.data.RPC;
import co.mitro.twofactor.TwoFactorSigningService;

import com.github.mustachejava.Mustache;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;


@WebServlet("/user/VerifyDevice")
public class VerifyDeviceServlet extends HttpServlet {
  private static final Gson gson = new Gson();
  private static final Logger logger = LoggerFactory.getLogger(VerifyDeviceServlet.class);
  private static final long serialVersionUID = 1L;
  /** Destination where successful requests are redirected. */
  public static final String SUCCESS_DESTINATION = "/verified-device.html";
  /** How long a device token is valid, which must permit long email delays. */
  static final long VALIDITY_TIMEOUT_MS = TimeUnit.HOURS.toMillis(12);

  private ManagerFactory managerFactory;
  private Mustache errorPageTemplate;
  private Mustache errorMessageTemplate;

  public VerifyDeviceServlet(ManagerFactory managerFactory, KeyFactory keyFactory) {
    this.managerFactory = managerFactory;
    // Crash on startup if secrets aren't loaded
    TwoFactorSigningService.checkInitialized();
    errorPageTemplate = Templates.compile("error.mustache");
    errorMessageTemplate = Templates.compile("verifydeviceservlet.mustache");
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    String token = request.getParameter("token");
    String tokenSignature = request.getParameter("token_signature");
    RPC.LoginToken signedToken = gson.fromJson(token,
        RPC.LoginToken.class);

    // TODO: Remove user parameter: it isn't signed so it can't be trusted!
    if (request.getParameter("user") != null) {
      if (!request.getParameter("user").equals(signedToken.email)) {
        throw new ServletException("Invalid parameters");
      }
    }

    boolean correctSignature = TwoFactorSigningService.verifySignature(token, tokenSignature);
    // TODO: even with the signature we should check that it came from the past?
    boolean correctTimeStamp = TwoFactorSigningService.verifyTimestamp(
        signedToken.timestampMs,  VALIDITY_TIMEOUT_MS);
    if (!(correctSignature && correctTimeStamp)) {
      // both could be wrong, but incorrect signatures are more "important" (shouldn't happen)
      if (!correctSignature) {
        logger.error("Invalid signature for token: {} signature: {}", token, tokenSignature);
      } else {
        assert !correctTimeStamp;
        logger.warn("Token timed out. timestampMs: {} expired time: {} now: {} ",
            signedToken.timestampMs, signedToken.timestampMs + VALIDITY_TIMEOUT_MS,
            System.currentTimeMillis());
      }

      // Generate the error message
      ImmutableMap<String, Boolean> template = ImmutableMap.of(
          "correctTimestamp", correctTimeStamp,
          "correctSignature", correctSignature);
      StringWriter writer = new StringWriter();
      errorMessageTemplate.execute(writer, template);
      String errorMessage = writer.toString();

      // Render the error page with the message
      ImmutableMap<String, String> templatePage = ImmutableMap.of(
          "errorMessage", errorMessage);
      errorPageTemplate.execute(response.getWriter(), templatePage);
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return;
    }

    try (Manager mgr = managerFactory.newManager()) {
      DBIdentity u = DBIdentity.getIdentityForUserName(mgr, signedToken.email);
      mgr.setOperationName("VERIFY_DEVICE");
      String deviceKey = GetMyDeviceKey.maybeGetOrCreateDeviceKey(
          mgr, u, signedToken.deviceId, false, "UNKNOWN");
      if (deviceKey == null) {
        logger.error("Failed for device {} user {}: get/create device key failed",
            signedToken.deviceId, u.getName());
        throw new MitroServletException("failed to verify device");
      }

      if (!u.isVerified()) {
        mgr.setRequestor(u, signedToken.deviceId);
        logger.info("marking user {} as verified", u.getName());
        mgr.addAuditLog(DBAudit.ACTION.AUTHORIZE_NEW_DEVICE, u, null, null, null,
            null);
        u.setVerified(true);
        mgr.identityDao.update(u);
      }
      mgr.commitTransaction();
      logger.info("accepting device {} for user {}", signedToken.deviceId, u.getName());
      response.sendRedirect(SUCCESS_DESTINATION);
    } catch (SQLException|MitroServletException e) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid token");      
      throw new ServletException(e);
    }
  }
}
