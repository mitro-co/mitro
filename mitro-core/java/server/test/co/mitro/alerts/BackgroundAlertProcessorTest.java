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
package co.mitro.alerts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

import org.h2.jdbc.JdbcSQLException;
import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import co.mitro.analysis.AuditLogProcessor.ActionType;
import co.mitro.core.alerts.BackgroundAlertProcessor;
import co.mitro.core.alerts.EmailAlertManager;
import co.mitro.core.alerts.NewUserEmailer;
import co.mitro.core.server.Manager;
import co.mitro.core.server.ManagerFactory;
import co.mitro.core.server.data.DBAcl.CyclicGroupError;
import co.mitro.core.server.data.DBFutureAlert.AlertPredicate;
import co.mitro.core.server.data.DBAudit;
import co.mitro.core.server.data.DBFutureAlert;
import co.mitro.core.server.data.DBProcessedAudit;
import co.mitro.core.servlets.MemoryDBFixture;

import com.google.common.collect.Lists;
import com.j256.ormlite.jdbc.JdbcConnectionSource;
import com.j256.ormlite.support.DatabaseConnection;

public class BackgroundAlertProcessorTest extends MemoryDBFixture {
  BackgroundAlertProcessor bap;

  @Before
  public void setUp() throws SQLException, CyclicGroupError {
    EmailAlertManager.getInstance().registerAlerter(new NewUserEmailer());
    bap = new BackgroundAlertProcessor(managerFactory);
  }

  @Test
  public void simpleTest() throws SQLException {
    DBAudit audit = new DBAudit();
    audit.setUser(testIdentity);
    audit.setTargetUser(null);
    audit.setTimestampMs(1000L);
    audit.setTransactionId("txn1");
    audit.setDeviceId("device1");
    audit.setSourceIp("source ip 1");
    DBProcessedAudit pa = new DBProcessedAudit(ActionType.SIGNUP, audit);
    manager.processedAuditDao.create(pa);
    manager.commitTransaction();
    assertEquals(1, manager.processedAuditDao.queryForAll().size());
    
    DBFutureAlert fa = new DBFutureAlert();
    fa.alertType = AlertPredicate.EMAIL_ON_CREATE_IDENTITY;
    fa.enqueueTimestampMs = 1000L;
    fa.inactive = false;
    fa.nextCheckTimestampMs = 1000L;
    fa.userIdToAlert = testIdentity.getId();
    fa.transactionId = "txn1";
    manager.futureAlertDao.create(fa);
    manager.commitTransaction();
    manager.futureAlertDao.refresh(fa);
    assertEquals(1, manager.futureAlertDao.queryForAll().size());
    assertEquals(false, manager.futureAlertDao.queryForAll().get(0).inactive);
    bap.run();
    assertEquals(1, manager.futureAlertDao.queryForAll().size());
    assertEquals(true, manager.futureAlertDao.queryForAll().get(0).inactive);
  }
}
