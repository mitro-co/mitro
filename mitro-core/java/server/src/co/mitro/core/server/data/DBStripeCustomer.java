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
package co.mitro.core.server.data;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName="stripe_customer")
public class DBStripeCustomer {
  @DatabaseField(generatedId=true, canBeNull=false)
  private int id;

  @DatabaseField(columnName="token", canBeNull=true)
  private String token;

  @DatabaseField(columnName="org_id", canBeNull=true)
  private int orgId;

  @DatabaseField(columnName="stripe_customer_id", canBeNull=true)
  private String stripeCustomerId;

  @DatabaseField(columnName="num_users")
  private int numUsers;

  public int getOrgId() {
    return orgId;
  }

  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
  }

  public void setOrgId(int orgId) {
    this.orgId = orgId;
  }

  public String getStripeCustomerId() {
    return stripeCustomerId;
  }

  public void setStripeCustomerId(String stripeCustomerId) {
    this.stripeCustomerId = stripeCustomerId;
  }

  public int getNumUsers() {
    return numUsers;
  }

  public void setNumUsers(int numUsers) {
    this.numUsers = numUsers;
  }
}
