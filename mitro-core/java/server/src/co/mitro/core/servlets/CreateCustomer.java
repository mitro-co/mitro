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

import java.security.SecureRandom;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.keyczar.util.Base64Coder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.mitro.core.server.Manager;
import co.mitro.core.server.ManagerFactory;
import co.mitro.core.server.data.DBGroup;
import co.mitro.core.server.data.DBStripeCustomer;

import com.google.common.base.Strings;

@javax.servlet.annotation.WebServlet("/CreateCustomer")
public class CreateCustomer extends MitroWebServlet {

  private static Logger logger = LoggerFactory.getLogger(CreateCustomer.class);

  private static final long serialVersionUID = 1L;

  private static final String PASSWORD = "cynif!gbg5OzHgmK";

  private static final int TOKEN_LENGTH = 24;
  private static final SecureRandom RANDOM = new SecureRandom();

  protected static String createToken() {
    byte[] token = new byte[TOKEN_LENGTH];
    RANDOM.nextBytes(token);
    return Base64Coder.encodeWebSafe(token);
  }

  @Override
  protected void handleRequest(HttpServletRequest request,
      HttpServletResponse response) throws Exception {

    String password = request.getParameter("password");
    if (!password.equals(PASSWORD)) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return;
    }

    String orgIdString = request.getParameter("org_id");
    Integer orgId;

    if (Strings.isNullOrEmpty(orgIdString)) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing org_id parameter");
      return;
    }

    try {
      orgId = new Integer(Integer.parseInt(orgIdString, 10));
    } catch (NumberFormatException e) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid org_id parameter");
      return;
    }

    try (Manager mgr = ManagerFactory.getInstance().newManager()) {
      if (!mgr.stripeCustomerDao.queryForEq("org_id", orgId).isEmpty()) {
        response.getWriter().write("Customer already created");
        return;
      }

      DBGroup org = mgr.groupDao.queryForId(orgId);
      assert(null != org);

      int numMembers = MutateOrganization.getMemberIdsAndPrivateGroupIdsForOrg(mgr, org).keySet().size();
      logger.info("Creating payment for org {} of {} members", org.getName(), numMembers);

      DBStripeCustomer customer = new DBStripeCustomer();
      customer.setOrgId(orgId);
      customer.setToken(createToken());
      customer.setNumUsers(numMembers);

      mgr.stripeCustomerDao.create(customer);
      mgr.commitTransaction();

      response.getWriter().write(customer.getToken());
    }
  }
}
