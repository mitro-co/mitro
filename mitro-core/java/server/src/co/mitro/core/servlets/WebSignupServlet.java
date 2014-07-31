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

import java.net.URI;
import java.net.URLEncoder;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import co.mitro.core.server.Manager;
import co.mitro.core.server.ManagerFactory;
import co.mitro.core.server.data.DBSignup;

import com.google.common.base.Strings;
import com.google.gson.Gson;

@javax.servlet.annotation.WebServlet("/WebSignup")
public class WebSignupServlet extends MitroWebServlet {
  private static final long serialVersionUID = 1L;
  
  protected static final Gson gson = new Gson();
  
  public static final String WEB_SERVER_SCHEME = "https";
  public static final String WEB_SERVER_HOST = "www.mitro.co";
  public static final int WEB_SERVER_PORT = 443;
  public static final String INSTALL_PATH = "/static/html/install.html";

  @Override
  protected void handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {
    String email = request.getParameter("email");
    String browser = request.getParameter("browser");
    String userAgent = request.getParameter("user_agent");
    String trackingData = request.getParameter("tracking_data");

    if (Strings.isNullOrEmpty(email)) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid arguments");
      return;
    }

    DBSignup signup = new DBSignup();
    signup.setEmail(email);
    signup.setBrowser(browser);
    signup.setUserAgent(userAgent);
    signup.setTrackingData(trackingData);

    try (Manager mgr = ManagerFactory.getInstance().newManager()) {
      // Note: multiple signup records from the same email address are permitted.
      // They may be signing up with different browsers.
      mgr.signupDao.create(signup);
      mgr.commitTransaction();
    }

    String fragment = "";
    if (browser.equals("chrome")) {
      fragment = "#u=" + URLEncoder.encode(email, "UTF-8");
    }

    URI uri = new URI(WEB_SERVER_SCHEME, null, WEB_SERVER_HOST, WEB_SERVER_PORT, INSTALL_PATH, null, null);
    String redirectLocation = uri.toASCIIString() + fragment;
    response.sendRedirect(redirectLocation);
  }
}
