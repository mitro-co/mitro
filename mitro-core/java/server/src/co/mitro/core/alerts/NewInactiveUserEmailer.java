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
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;
import com.j256.ormlite.stmt.QueryBuilder;

import co.mitro.analysis.AuditLogProcessor.ActionType;
import co.mitro.core.server.Manager;
import co.mitro.core.server.data.DBFutureAlert;
import co.mitro.core.server.data.DBFutureAlert.AlertPredicate;
import co.mitro.core.server.data.DBProcessedAudit;

public class NewInactiveUserEmailer extends BaseEmailAlerter {
  private static final Logger logger = LoggerFactory.getLogger(BaseEmailAlerter.class);

  
  // TODO: this stuff should probably be abstracted out:
  private static final Set<ActionType> CANCEL_ALERT_ACTIONS = Sets.immutableEnumSet(
      ActionType.GRANTED_ACCESS_TO,
      ActionType.CREATE_SECRET);
  private static final long TIME_TO_WAIT_MS = TimeUnit.DAYS.toMillis(5);
  
  @Override
  protected boolean internalProcess(AlerterContext context) throws SQLException {
    // get all actions that user did after joining.
    if (System.currentTimeMillis() < TIME_TO_WAIT_MS + context.dbAlertObject.enqueueTimestampMs) {
      context.dbAlertObject.nextCheckTimestampMs = context.dbAlertObject.enqueueTimestampMs + TIME_TO_WAIT_MS;
      return false;
    }
    QueryBuilder<DBProcessedAudit,Integer> builder = getBuilder(context, CANCEL_ALERT_ACTIONS, AlertUserType.ACTOR);

    // for some reason limit(int) is deprecated, WTF.
    builder.limit(1L);
    builder.setCountOf(true);
    
    if (context.manager.processedAuditDao.countOf(builder.prepare()) == 0) {
      // no such actions have happened. we should trigger the alert.
      logger.warn("should send unimplemented email for idle user");
      // TODO: send idle user email
    }
    return true;
  }
  @Override
  protected AlertPredicate getMatchingAlertType() {
    return AlertPredicate.EMAIL_ON_NEW_USER_INACTIVE;
  }

  @Override
  public int createNewDBFutureAlertsForProcessedAudits(
      Manager mgr,
      Collection<DBProcessedAudit> audits, long minTimestampMs) throws SQLException {
    int count = 0;
    for (DBProcessedAudit action : audits) {
      if (action.getAction() == ActionType.SIGNUP) {
        DBFutureAlert.addNew(mgr, AlertPredicate.EMAIL_ON_NEW_USER_INACTIVE, action, action.getActor(), minTimestampMs);
        ++count;
      }
    }
    return count;
  }
}
