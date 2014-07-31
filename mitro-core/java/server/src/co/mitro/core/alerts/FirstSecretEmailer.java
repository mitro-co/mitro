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
import java.util.Collections;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.mitro.analysis.AuditLogProcessor.ActionType;
import co.mitro.core.server.Manager;
import co.mitro.core.server.data.DBEmailQueue;
import co.mitro.core.server.data.DBFutureAlert;
import co.mitro.core.server.data.DBFutureAlert.AlertPredicate;
import co.mitro.core.server.data.DBProcessedAudit;

import com.google.common.collect.ImmutableList;
import com.j256.ormlite.stmt.QueryBuilder;

public class FirstSecretEmailer extends BaseEmailAlerter {
  
  private static final Logger logger = LoggerFactory.getLogger(FirstSecretEmailer.class);

  @Override
  protected boolean internalProcess(AlerterContext context) throws SQLException {
    QueryBuilder<DBProcessedAudit,Integer> builder = getBuilder(context,
        ImmutableList.of(ActionType.CREATE_SECRET),
        AlertUserType.ACTOR);
    builder.setCountOf(true);
    if (context.manager.processedAuditDao.countOf(builder.prepare()) > 0) {
      enqueueEmail(context, Collections.<String,String>emptyMap(), DBEmailQueue.Type.MANDRILL_SAVE_FIRST_SECRET);
      return true;
    }
    return false;
  }

  @Override
  protected AlertPredicate getMatchingAlertType() {
    return AlertPredicate.EMAIL_ON_FIRST_SECRET;
  }

  @Override
  public int createNewDBFutureAlertsForProcessedAudits(
      Manager mgr,
      Collection<DBProcessedAudit> audits, long minTimestampMs) throws SQLException {
    int count = 0;
    for (DBProcessedAudit action : audits) {
      if (action.getAction() == ActionType.SIGNUP) {
        DBFutureAlert.addNew(mgr, AlertPredicate.EMAIL_ON_FIRST_SECRET, action, action.getActor(), minTimestampMs);
        ++count;
      }
    }
    return count;
  }
}
