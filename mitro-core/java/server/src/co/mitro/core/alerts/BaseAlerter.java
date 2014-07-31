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
/**
 * 
 */
package co.mitro.core.alerts;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.j256.ormlite.stmt.QueryBuilder;

import co.mitro.analysis.AuditLogProcessor.ActionType;
import co.mitro.core.server.Manager;
import co.mitro.core.server.data.DBFutureAlert;
import co.mitro.core.server.data.DBIdentity;
import co.mitro.core.server.data.DBProcessedAudit;

/**
 * This is the base class for Alert Predicates.
 * Adding a new Predicate? You must update the map in DBFutureAlert for
 * things to work properly.
 * @author vijayp
 *
 */
public abstract class BaseAlerter {
  private static final Logger logger = LoggerFactory.getLogger(BaseAlerter.class);
  private static final long TIME_FUDGE_FACTOR_MS = 10;
  
  protected abstract DBFutureAlert.AlertPredicate getMatchingAlertType();
  
  public abstract int createNewDBFutureAlertsForProcessedAudits(Manager mgr, 
      Collection<DBProcessedAudit> audits, long minTimestampMs) throws SQLException;
  
  public static final class AlerterContext {
    public AlerterContext(Manager mgr, DBFutureAlert alert) throws SQLException {
      manager = mgr;
      dbAlertObject = alert;
      userToAlert = mgr.identityDao.queryForId(dbAlertObject.userIdToAlert);
    }
    public Manager manager;
    public DBFutureAlert dbAlertObject;
    public DBIdentity userToAlert;
    
  }
  
  protected enum AlertUserType {ACTOR, OBJECT}
  protected static QueryBuilder<DBProcessedAudit, Integer> getBuilder(AlerterContext context, Collection<ActionType> actions,
      AlertUserType userType) throws SQLException {
    QueryBuilder<DBProcessedAudit,Integer> builder = context.manager.processedAuditDao.queryBuilder();
    builder.where().eq(
        (userType == AlertUserType.ACTOR) ? DBProcessedAudit.ACTOR_FIELD_NAME : DBProcessedAudit.AFFECTED_USER_FIELD_NAME, 
            context.userToAlert)
    .and().in(DBProcessedAudit.ACTION_FIELD_NAME, Manager.makeSelectArgsFromList(actions))
    .and().gt(DBProcessedAudit.TIMESTAMP_FIELD_NAME, context.dbAlertObject.enqueueTimestampMs - TIME_FUDGE_FACTOR_MS);
    return builder;
  }
  
  /**
   * Process the loaded alert. Return true if the predicate was met
   * and the action was completed. The alert object will be marked as 
   * inactive.
   */
  protected abstract boolean internalProcess(AlerterContext ac) throws SQLException;
  
  /**
   * Process an future alert. Sets the object to be inactive iff 
   * the action has been completed.
   * @throws SQLException
   */
  final public boolean processAlert(Manager mgr, DBFutureAlert futureAlert) throws SQLException {
    assert (getMatchingAlertType() == futureAlert.alertType);
    AlerterContext ac = new AlerterContext(mgr, futureAlert);
    long oldTime = ac.dbAlertObject.nextCheckTimestampMs;
    if (internalProcess(ac)) {
      logger.info("completed processing for db alert: {}", futureAlert.id);
      futureAlert.inactive = true;
      if (ac.dbAlertObject.nextCheckTimestampMs == oldTime) {
        logger.warn("Warning: email alerter did not update next check time. using default");
        ac.dbAlertObject.nextCheckTimestampMs += DEFAULT_NEXT_CHECK_DELAY;
      }
    } 
    mgr.futureAlertDao.update(futureAlert);
    mgr.commitTransaction();
    return futureAlert.inactive;
  }
  
  private static final long DEFAULT_NEXT_CHECK_DELAY = TimeUnit.HOURS.toMillis(1);
}
