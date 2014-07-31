/*******************************************************************************
 * Copyright (c) 2013 Lectorius, Inc.
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

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;

import co.mitro.core.server.Manager;
import co.mitro.core.server.ManagerFactory;
import co.mitro.core.server.data.DBIdentity;


@WebServlet("/user/VerifyAccount")
public class VerifyAccountServlet extends HttpServlet {
  private static final Logger logger = LoggerFactory.getLogger(VerifyAccountServlet.class);
  private static final long serialVersionUID = 1L;
  /** Destination where successful requests are redirected. */
  public static final String SUCCESS_DESTINATION = "/verified.html";

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    String user = request.getParameter("user");
    String code = request.getParameter("code");

    boolean haveAllParameters = !Strings.isNullOrEmpty(user) && !Strings.isNullOrEmpty(code);
    if (!haveAllParameters || request.getParameterMap().size() != 2) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid arguments");
      return;
    }

    try (Manager mgr = ManagerFactory.getInstance().newManager()) {
      DBIdentity u = DBIdentity.getIdentityForUserName(mgr, user);
      if (u != null && code.equals(u.getVerificationUid())) {
        u.setVerified(true);
        mgr.identityDao.update(u);
        logger.info("Successfully verified address {} code {}", u.getName(), code);
        mgr.commitTransaction();

        // TODO: The server probably should return the HTML output directly?
        response.sendRedirect(SUCCESS_DESTINATION);
      } else {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid code");

        String expected = null;
        if (u != null) expected = u.getVerificationUid();
        logger.warn("Address validation failed for identity {}: got {} expected {}",
            user, code, expected);
      }
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }
}
