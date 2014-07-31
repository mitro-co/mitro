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

import static org.junit.Assert.*;

import java.sql.SQLException;

import org.junit.Test;

import co.mitro.analysis.AuditLogProcessor;
import co.mitro.core.servlets.MemoryDBFixture;

public class DBProcessedAuditTest extends MemoryDBFixture {
  @Test
  public void testConstructorAndToString() throws SQLException {
    // previously it was possible to have .toString() throw NullPointerException
    // the constructor should not permit an incorrectly initialized object to be created
    DBAudit audit = new DBAudit();
    audit.setUser(testIdentity);
    audit.setTimestampMs(1392069651764L);
    audit.setTransactionId("foofoo");
    manager.auditDao.create(audit);

    audit = manager.auditDao.queryForId(audit.getId());

    DBProcessedAudit processed = new DBProcessedAudit(
        AuditLogProcessor.ActionType.CREATE_GROUP, audit);
    assertEquals(testIdentity.getName() + " CREATE_GROUP null @ 2014-02-10T22:00:51.764Z",
        processed.toString());
  }

  @Test
  public void deletedIdentity() throws SQLException {
    DBAudit audit = new DBAudit();
    audit.setUser(testIdentity);
    audit.setTimestampMs(1392069651764L);
    audit.setTransactionId("foofoo");
    manager.auditDao.create(audit);
    manager.identityDao.delete(testIdentity);
    manager.commitTransaction();

    // auditDao is a different transaction: must commit before querying
    audit = manager.auditDao.queryForId(audit.getId());

    // This throws NPE because testIdentity no longer exists. Should we handle this differently?
    try {
      new DBProcessedAudit(AuditLogProcessor.ActionType.CREATE_GROUP, audit);
      fail("expected exception");
    } catch (NullPointerException ignored) {
    }
  }
}
