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
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.mitro.core.server.Manager;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.SelectArg;
import com.j256.ormlite.table.DatabaseTable;


@DatabaseTable(tableName="future_alert")
public class DBFutureAlert {
  
  public static final String ALERT_TYPE = "alert_type";
  public static final String USER_ID_TO_ALERT = "user_id_to_alert";
  public static final String AFFECTED_SECRET_ID = "affected_secret_id";
  public static final String INACTIVE = "inactive";
  public static final String NEXT_CHECK_TIMESTAMP_MS = "next_check_timestamp_ms";

  private static final Logger logger = LoggerFactory.getLogger(DBFutureAlert.class);
  public enum AlertPredicate {
    EMAIL_ON_CREATE_IDENTITY,
    EMAIL_ON_NEW_USER_INACTIVE,
    EMAIL_TARGET_USER_ON_SHARES,
    EMAIL_ON_FIRST_SECRET,
    
  }

	@DatabaseField(generatedId=true, columnName="id")
  public int id;
  
  @DatabaseField(canBeNull=false, columnName=ALERT_TYPE) 
  public AlertPredicate alertType;
  
  @DatabaseField(canBeNull=false, columnName=USER_ID_TO_ALERT)
  public int userIdToAlert;
  
  @DatabaseField(canBeNull=false, columnName="transaction_id")
  public String transactionId;
  
  @DatabaseField(canBeNull=false, columnName="enqueue_timestamp_ms")
  public long enqueueTimestampMs = System.currentTimeMillis();

  @DatabaseField(canBeNull=false, columnName=NEXT_CHECK_TIMESTAMP_MS)
  public long nextCheckTimestampMs = 0;
  
  @DatabaseField(canBeNull=false, columnName=INACTIVE)
  public boolean inactive = false;

  // TODO: this should accept user to alert as a param 
  public static void addNew(Manager manager,
      AlertPredicate alertType, DBProcessedAudit audit, DBIdentity userToAlert, long minTimestampMs) throws SQLException {
    // These must be unique on (secretId, userId, alertType)  
    // e.g. If I share, then unshare, then re-share something 
    // with a user, only one of these events should ever be added.
    
    QueryBuilder<DBFutureAlert,Integer> builder = manager.futureAlertDao.queryBuilder();
    builder.where().eq(INACTIVE, false)
        .and().eq(ALERT_TYPE, new SelectArg(alertType))
        .and().eq(USER_ID_TO_ALERT, userToAlert.getId());
    
    builder.setCountOf(true);
    if (manager.futureAlertDao.countOf(builder.prepare()) > 0) {
      logger.info("Trying to add new alert for {}, {}. Existing alert already exists. Ignoring",
          alertType, userToAlert.getId());
    } else {
      logger.info("Inserting future alert for {}, {}.",
          alertType, userToAlert.getId());
      DBFutureAlert a = new DBFutureAlert();
      a.userIdToAlert = userToAlert.getId();
      a.transactionId = audit.getTransactionId();
      a.enqueueTimestampMs = minTimestampMs;
      a.alertType = alertType;
      manager.futureAlertDao.create(a);
    }
  }

};
