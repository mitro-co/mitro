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
package co.mitro.analysis;

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

import co.mitro.core.server.Manager;
import co.mitro.core.server.ManagerFactory;
import co.mitro.core.server.data.DBAcl.CyclicGroupError;
import co.mitro.core.servlets.MemoryDBFixture;

import com.google.common.collect.Lists;
import com.j256.ormlite.jdbc.JdbcConnectionSource;
import com.j256.ormlite.support.DatabaseConnection;

public class StatsGeneratorTest extends MemoryDBFixture {
  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  @Before
  public void setUp() throws SQLException, CyclicGroupError {
    // MemoryDBFixture doesn't create a "private group" for this identity
    createGroupContainingIdentity(testIdentity2);
  }

  @Test
  public void outputToFile() throws Exception {
    tempFolder.newFolder("users");
    tempFolder.newFolder("orgs");
    StatsGenerator.Snapshot output =
        StatsGenerator.generateStatistics(tempFolder.getRoot().toString(), manager);
    assertEquals(2, output.userStateObjects.size());
    assertEquals(0, output.orgStateObjects.size());
  }

  @Test
  public void noFileOutput() throws Exception {
    StatsGenerator.Snapshot output = StatsGenerator.generateStatistics(null, manager);
    assertEquals(2, output.userStateObjects.size());
    assertEquals(0, output.orgStateObjects.size());
  }

  @Test
  public void readOnlyAuditLog() throws Exception {
    // create an organization to test that path
    createOrganization(testIdentity, testIdentity.getName(), 
        Lists.newArrayList(testIdentity), Lists.newArrayList(testIdentity));

    // this must be a disk db for reopening as read-only to work (it seems)
    // Another alternative would be to create a read-only user, but I can't get that to work either
    final String PATH = tempFolder.newFile().getAbsolutePath();
    final String OTHER_DB_URL = "jdbc:h2:file:" + PATH + ";DATABASE_TO_UPPER=FALSE";
    // create a ManagerFactory just to create the tables (yuck)
    new ManagerFactory(OTHER_DB_URL, new Manager.Pool(), ManagerFactory.IDLE_TXN_POLL_SECONDS, TimeUnit.SECONDS, ManagerFactory.ConnectionMode.READ_WRITE);

    // create a read-only connection for the audit log
    JdbcConnectionSource readOnlyConnection = new JdbcConnectionSource(
        OTHER_DB_URL + ";ACCESS_MODE_DATA=r");
    try {
      readOnlyConnection.getReadWriteConnection().executeStatement(
          "UPDATE email_queue SET arg_string='';", DatabaseConnection.DEFAULT_RESULT_FLAGS);
      fail("expected exception");
    } catch (JdbcSQLException e) {
      assertThat(e.getMessage(), CoreMatchers.containsString("database is read only"));
    }
    Manager readOnlyAuditManager = new Manager(
        new Manager.Pool(),
        (JdbcConnectionSource) manager.identityDao.getConnectionSource(),
        readOnlyConnection,
        ManagerFactory.ConnectionMode.READ_WRITE);

    assertEquals(1, manager.auditDao.countOf());
    StatsGenerator.Snapshot output = StatsGenerator.generateStatistics(null, readOnlyAuditManager);
    assertEquals(1, manager.auditDao.countOf());
    assertEquals(2, output.userStateObjects.size());
    assertEquals(1, output.orgStateObjects.size());
  }
}
