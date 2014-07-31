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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.mitro.core.server.Manager;
import co.mitro.core.server.ManagerFactory;
import co.mitro.core.server.data.DBGroup;
import co.mitro.core.server.data.DBStripeCustomer;

import com.google.common.base.Strings;
import com.j256.ormlite.stmt.SelectArg;
import com.stripe.Stripe;
import com.stripe.model.Customer;
import com.stripe.model.CustomerSubscriptionCollection;

@javax.servlet.annotation.WebServlet("/SubmitPayment")
public class SubmitPayment extends MitroWebServlet {

  private static final long serialVersionUID = 1L;
  private static Logger logger = LoggerFactory.getLogger(SubmitPayment.class);

  public static String STRIPE_API_KEY = "REDACTED";
  public static String MITRO_PRO_PLAN_ID = "pro";

  @Override
  protected void handleRequest(HttpServletRequest request,
      HttpServletResponse response) throws Exception {
      String customerToken = request.getParameter("customer_token");
      String cardToken = request.getParameter("cc_token");

      if (Strings.isNullOrEmpty(customerToken)) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing customer_token parameter");
        return;
      }
      if (Strings.isNullOrEmpty(cardToken)) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing card_token parameter");
        return;
      }
 
      try (Manager mgr = ManagerFactory.getInstance().newManager()) {
        List<DBStripeCustomer> resultSet = mgr.stripeCustomerDao.queryForEq("token",  new SelectArg(customerToken));
        assert(resultSet.size() == 1);
        DBStripeCustomer stripeCustomer = resultSet.get(0);

        DBGroup org = mgr.groupDao.queryForId(stripeCustomer.getOrgId());
        assert(null != org);

        Stripe.apiKey = STRIPE_API_KEY;
        Customer customer;

        if (Strings.isNullOrEmpty(stripeCustomer.getStripeCustomerId())) {
          Map<String, Object> customerParams = new HashMap<String, Object>();
          customerParams.put("card", cardToken);
          customerParams.put("description", org.getName());

          customer = Customer.create(customerParams);

          // Associate the stripe customer with org id
          stripeCustomer.setStripeCustomerId(customer.getId());
          mgr.stripeCustomerDao.update(stripeCustomer);
          mgr.commitTransaction();
        } else {
          // throws exception if customer not found.
          customer = Customer.retrieve(stripeCustomer.getStripeCustomerId());
        }

        assert(customer != null);

        // TODO: allow updating subscription.
        if (!customer.getSubscriptions().all(Collections.<String,Object>emptyMap()).getData().isEmpty()) {
          response.getWriter().write("Already subscribed to Mitro Pro");
          return;
        }

        // Warning: don't combine with Customer.create()
        // It is possible to subscribe to a plan during customer creation, but at the risk of charging the
        // customer twice if commit the stripe customer id fails.
        Map<String, Object> subscriptionParams = new HashMap<String, Object>();
        subscriptionParams.put("plan", MITRO_PRO_PLAN_ID);
        subscriptionParams.put("quantity", stripeCustomer.getNumUsers());
        // throws exception if subscribe fails
        customer.createSubscription(subscriptionParams);

        logger.info("Payment submitted for {}", org.getName());
        response.getWriter().write("Payment successful!");
      }
  }
}
