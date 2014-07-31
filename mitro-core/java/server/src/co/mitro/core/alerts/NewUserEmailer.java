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
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.mitro.analysis.AuditLogProcessor.ActionType;
import co.mitro.core.server.Manager;
import co.mitro.core.server.data.DBFutureAlert;
import co.mitro.core.server.data.DBFutureAlert.AlertPredicate;
import co.mitro.core.server.data.DBProcessedAudit;

public class NewUserEmailer extends BaseEmailAlerter {
  private static final Logger logger = LoggerFactory.getLogger(NewUserEmailer.class);

  @Override
  public int createNewDBFutureAlertsForProcessedAudits(
      Manager mgr,
      Collection<DBProcessedAudit> audits, long minTimestampMs) throws SQLException {
    int count = 0;
    for (DBProcessedAudit action : audits) {
      if (action.getAction() == ActionType.SIGNUP) {
        DBFutureAlert.addNew(mgr, AlertPredicate.EMAIL_ON_CREATE_IDENTITY, action, action.getActor(), minTimestampMs);
        ++count;
      }
    }
    return count;
  }
  @Override
  protected AlertPredicate getMatchingAlertType() {
    // TODO Auto-generated method stub
    return AlertPredicate.EMAIL_ON_CREATE_IDENTITY;
  }

  @Override
  protected boolean internalProcess(AlerterContext ac) throws SQLException {
    if (ac.userToAlert == null) {
      logger.info("new user has disappeared!");
    } else {
      // TODO: implement message
      logger.info("Should send new user welcome email to user " + ac.userToAlert.getName());   
    }
    return true;
  }
}
