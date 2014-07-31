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
package co.mitro.core.alerts;

import java.sql.SQLException;
import java.util.Map;

import co.mitro.core.server.data.DBEmailQueue;

public abstract class BaseEmailAlerter extends BaseAlerter {

  static boolean isEmailPermitted(String email) {
    return true;
  }

  protected void enqueueEmail(AlerterContext context, 
      Map<String, String> emailParams, DBEmailQueue.Type type) throws SQLException {
    assert (type != null);
    String recipientEmail = context.userToAlert.getName();

    DBEmailQueue eq = DBEmailQueue.makeMandrillEmail(recipientEmail, emailParams, type);

    if (isEmailPermitted(recipientEmail)) {
      // TODO: we should probably catch an sql exception here and handle it properly.
      context.manager.emailDao.create(eq);
    }
  }
}
