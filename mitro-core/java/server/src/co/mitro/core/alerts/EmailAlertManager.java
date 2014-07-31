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
import java.util.HashSet;
import java.util.Set;

import co.mitro.core.server.Manager;
import co.mitro.core.server.data.DBFutureAlert;
import co.mitro.core.server.data.DBFutureAlert.AlertPredicate;
import co.mitro.core.server.data.DBProcessedAudit;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

public class EmailAlertManager {

  Multimap<AlertPredicate, BaseAlerter> alertTypeToAlerter = Multimaps.synchronizedMultimap(ArrayListMultimap.<AlertPredicate, BaseAlerter>create());
  Set<BaseAlerter> allAlerters = Collections.synchronizedSet(new HashSet<BaseAlerter>());
  public void registerAlerter(BaseAlerter alerter) {
    alertTypeToAlerter.put(alerter.getMatchingAlertType(), alerter);
    allAlerters.add(alerter);
  }
  
  
  public void createFutureAlertsFromAudits(Manager manager,
      Collection<DBProcessedAudit> actions, long minTimestampMs) throws SQLException {
    for (BaseAlerter alerter : allAlerters) {
      alerter.createNewDBFutureAlertsForProcessedAudits(manager, actions, minTimestampMs);
    }
  }
  
  public void processFutureAlert(Manager mgr, DBFutureAlert alert) throws SQLException {
    for (BaseAlerter alerter : alertTypeToAlerter.get(alert.alertType)) {
      alerter.processAlert(mgr, alert);
      if (alert.inactive) {
        break;
      }
    }
  }
  
  private static EmailAlertManager instance = new EmailAlertManager();
  public static EmailAlertManager getInstance() {
    return instance;
  }

}
