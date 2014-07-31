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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.mitro.analysis.AuditLogProcessor.ActionType;
import co.mitro.core.server.Manager;
import co.mitro.core.server.data.DBFutureAlert;
import co.mitro.core.server.data.DBFutureAlert.AlertPredicate;
import co.mitro.core.server.data.DBProcessedAudit;

import com.google.common.base.Joiner;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.j256.ormlite.stmt.QueryBuilder;

/**
 * This sends out e-mails whenever secrets are shared with a user.
 * It currently aggregates all secrets shared with a particular user
 * (Not yet TODO! It cancels shares when access to a particular secret is revoked,
 * or when the user has logged into the secret) 
 *
 */
public class SecretsSharedWithUserEmailer extends BaseEmailAlerter {
  private static final Logger logger = LoggerFactory.getLogger(SecretsSharedWithUserEmailer.class);
  
  private static final long MIN_TIME_AFTER_SHARE_MS= TimeUnit.MINUTES.toMillis(2);
  private static final Set<ActionType> IMPORTANT_ACTIONS = Sets.immutableEnumSet(ActionType.GRANTED_ACCESS_TO);
    
  private static final Joiner JOINER = Joiner.on(", ");
  

  @Override
  public int createNewDBFutureAlertsForProcessedAudits(
      Manager mgr,
      Collection<DBProcessedAudit> audits, long minTimestampMs) throws SQLException {
    int count = 0;
    for (DBProcessedAudit action : audits) {
      if (action.getAction() == ActionType.GRANTED_ACCESS_TO) {
        // TODO: making these unique per target here will be more efficient
        // but it's done in addnew anyway
        DBFutureAlert.addNew(mgr, AlertPredicate.EMAIL_TARGET_USER_ON_SHARES, action, action.getAffectedUser(), minTimestampMs);
        ++count;
      }
    }
    return count;
  }
  
  @Override
  protected AlertPredicate getMatchingAlertType() {
    return AlertPredicate.EMAIL_TARGET_USER_ON_SHARES;
  }

  @Override
  protected boolean internalProcess(AlerterContext ac) throws SQLException {

    // if we haven't wanted at least min_time, fail.
    ac.dbAlertObject.nextCheckTimestampMs = Math.max(ac.dbAlertObject.nextCheckTimestampMs,
        ac.dbAlertObject.enqueueTimestampMs + MIN_TIME_AFTER_SHARE_MS);
    if (System.currentTimeMillis() < ac.dbAlertObject.nextCheckTimestampMs) {
      return false;
    }
    
    // get all the grant actions for this user
    QueryBuilder<DBProcessedAudit,Integer> builder = getBuilder(ac, IMPORTANT_ACTIONS, AlertUserType.OBJECT);
    
    // this orders them descending by timestamp.
    builder.orderBy(DBProcessedAudit.TIMESTAMP_FIELD_NAME, false);
    List<DBProcessedAudit> matchingAudits = ac.manager.processedAuditDao.query(builder.prepare());
    assert (!matchingAudits.isEmpty()) : "this cannot be!";
    
    // first element is most recent event.
    if (matchingAudits.get(0).getTimestampMs() + MIN_TIME_AFTER_SHARE_MS > System.currentTimeMillis()) {
      // not enough time has transpired.
      ac.dbAlertObject.nextCheckTimestampMs = matchingAudits.get(0).getTimestampMs() + MIN_TIME_AFTER_SHARE_MS;
      return false;
    } 
    
    Set<String> sharingUsers = Sets.newHashSet();
    int numSharedSecrets = 0;
    for (DBProcessedAudit audit : matchingAudits) {
      sharingUsers.add(audit.getActorName());
      if (null != audit.getAffectedSecret()) {
        numSharedSecrets += 1;
      }
    }
    
    // TODO: query the DB for processed audit logs showing that the user actually
    // logged into that secret, then cancel email for those secrets.
    // no such actions have happened. we should trigger the alert.
    Map<String, String> emailParams = Maps.newHashMap();
    emailParams.put("fullname", JOINER.join(sharingUsers));
    // TODO: fix the user name
    emailParams.put("username", "UNKNOWN");
    emailParams.put("numsecrets", Integer.toString(numSharedSecrets));
    // TODO: this should probably point to the specific secret or something?
    emailParams.put("secreturl", "https://www.mitro.co");
    logger.info("ignoring share email about ", emailParams.get("fullname"));
      //enqueueEmail(ac, emailParams, DBEmailQueue.Type.MANDRILL_SHARE_SECRET);
    
    return true;
  }
}
