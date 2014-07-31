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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.sql.SQLException;

import org.junit.Test;

import co.mitro.core.exceptions.MitroServletException;
import co.mitro.core.servlets.MemoryDBFixture;

public class DBGroupSecretTest extends MemoryDBFixture {
  @Test
  public void addToRpcSecretWithHiddenGroup() throws SQLException, MitroServletException {
    DBServerVisibleSecret secret = createSecret(
        testGroup, "client visible", "critical", null);
    DBGroupSecret groupSecret = manager.getGroupSecret(testGroup, secret);

    testGroup.setAutoDelete(true);
    manager.groupDao.update(testGroup);

    // Must load the DBGroupSecret to get related objects (eager fetching)
    manager.commitTransaction();
    groupSecret = manager.groupSecretDao.queryForId(groupSecret.getId());

    RPC.Secret rpcSecret = new RPC.Secret();
    groupSecret.addToRpcSecret(
        manager, rpcSecret, DBGroupSecret.CRITICAL.NO_CRITICAL_DATA, testIdentity.getName());
    assertEquals("client visible", rpcSecret.encryptedClientData);
    assertEquals(null, rpcSecret.encryptedCriticalData);
    assertEquals(1, rpcSecret.hiddenGroups.size());
    assertEquals(testGroup.getId(), rpcSecret.hiddenGroups.get(0).intValue());
    assertArrayEquals(new String[]{testIdentity.getName()}, rpcSecret.users.toArray());
    assertEquals(0, rpcSecret.groupNames.size());
  }

  @Test
  public void testAddToRpcSecretInGroup() throws SQLException, MitroServletException {
    DBServerVisibleSecret secret = createSecret(testGroup, "client", "critical", null);
    DBGroupSecret groupSecret = manager.getGroupSecret(testGroup, secret);

    RPC.Secret rpcSecret = new RPC.Secret();
    groupSecret.addToRpcSecret(
        manager, rpcSecret, DBGroupSecret.CRITICAL.NO_CRITICAL_DATA, testIdentity.getName());
    assertEquals(0, rpcSecret.users.size());
    assertEquals(0, rpcSecret.hiddenGroups.size());
    assertEquals(1, rpcSecret.groupNames.size());
    assertEquals(testGroup.getName(), rpcSecret.groupNames.get(testGroup.getId()));
  }
}
