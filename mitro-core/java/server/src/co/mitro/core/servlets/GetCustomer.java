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

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import co.mitro.core.server.Manager;
import co.mitro.core.server.ManagerFactory;
import co.mitro.core.server.data.DBGroup;
import co.mitro.core.server.data.DBStripeCustomer;
import co.mitro.core.server.data.RPC.GetCustomerResponse;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.j256.ormlite.stmt.SelectArg;
import com.stripe.model.Plan;

@javax.servlet.annotation.WebServlet("/GetCustomer")
public class GetCustomer extends MitroWebServlet {

  private static final long serialVersionUID = 1L;

  protected static final Gson gson = new Gson();

  @Override
  protected void handleRequest(HttpServletRequest request,
      HttpServletResponse response) throws Exception {
    String customerToken = request.getParameter("customer_token");

    if (Strings.isNullOrEmpty(customerToken)) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing customer_token parameter");
      return;
    }

    try (Manager mgr = ManagerFactory.getInstance().newManager()) {
      List<DBStripeCustomer> resultSet = mgr.stripeCustomerDao.queryForEq("token",  new SelectArg(customerToken));
      assert(resultSet.size() == 1);
      DBStripeCustomer stripeCustomer = resultSet.get(0);

      DBGroup org = mgr.groupDao.queryForId(stripeCustomer.getOrgId());
      assert(null != org);

      Plan plan = Plan.retrieve(SubmitPayment.MITRO_PRO_PLAN_ID, SubmitPayment.STRIPE_API_KEY);

      GetCustomerResponse out = new GetCustomerResponse();
      out.orgName = org.getName();
      out.numUsers = stripeCustomer.getNumUsers();
      out.planName = plan.getName();
      out.planUnitCost = plan.getAmount();

      response.getWriter().write(gson.toJson(out));
    }
  }

}
