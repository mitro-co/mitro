/*******************************************************************************
 * Copyright (c) 2013 Lectorius, Inc.
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
package co.mitro.core.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;

import co.mitro.core.server.ManagerFactory.ConnectionMode;

public class ManagerFactoryTest {
  // testdb2 to ensure it's different from memorydbfixture
  private final static String MEMORY_DB_URL = "jdbc:h2:mem:testdb2;DB_CLOSE_DELAY=-1";
  private static final long POLL_MS = 50;

  private FakeAbortManagerPool pool;
  private ManagerFactory factory;

  private static final class FakeAbortManagerPool implements ManagerPool {
    // All methods use the real pool except abortExpiredTransactions
    Manager.Pool pool = new Manager.Pool();
    private boolean abortCalled;

    @Override
    public synchronized int abortExpiredTransactions() {
      abortCalled = true;
      notify();
      return 0;
    }

    public synchronized boolean getCalled() {
      return abortCalled;
    }

    public synchronized boolean waitAndResetCalled(long timeoutMs) throws InterruptedException {
      long now = System.currentTimeMillis();
      long timeoutDeadline = now + timeoutMs;
      while (!abortCalled && now < timeoutDeadline) {
        wait(timeoutDeadline - now);
        now = System.currentTimeMillis();
      }

      boolean called = abortCalled;
      abortCalled = false;
      return called;
    }

    @Override
    public void addManagerToPool(Manager m) {
      pool.addManagerToPool(m);
    }

    @Override
    public Manager getManagerFromUniqueId(String transactionId) {
      return pool.getManagerFromUniqueId(transactionId);
    }

    @Override
    public void removeManager(Manager manager) {
      pool.removeManager(manager);
    }
  }

  @Before
  public void setUp() {
    pool = new FakeAbortManagerPool();
    factory = new ManagerFactory(
        MEMORY_DB_URL, pool, POLL_MS, TimeUnit.MILLISECONDS, ConnectionMode.READ_WRITE);
  }

  @Test
  public void testCreateClose() throws SQLException {
    Manager m = factory.newManager();
    Manager m2 = factory.newManager();
    assertTrue(m != m2);
    assertTrue(m.getTransactionId() != m2.getTransactionId());

    assertEquals(m, factory.getTransaction(m.getTransactionId()));
    assertEquals(m2, factory.getTransaction(m2.getTransactionId()));

    m.close();
    assertEquals(null, factory.getTransaction(m.getTransactionId()));
    assertFalse(m.getConnectionSource().isOpen());
    assertEquals(m2, factory.getTransaction(m2.getTransactionId()));
  }

  @Test
  public void testAutoClosable() throws SQLException {
    String transactionId = null;
    try (Manager m = factory.newManager()) {
      transactionId = m.getTransactionId();
      assertEquals(m, pool.getManagerFromUniqueId(transactionId));
    }

    // auto-closing the manager should remove it from the pool
    assertNull(pool.getManagerFromUniqueId(transactionId));
  }

  @Test(timeout=POLL_MS*10)
  public void testAbortPolling() throws InterruptedException {
    // no polling: it doesn't get called
    assertFalse(pool.getCalled());
    assertFalse(pool.waitAndResetCalled(POLL_MS*2));

    // start polling: it gets called twice
    factory.lifeCycleStarting(null);
    assertTrue(pool.waitAndResetCalled(POLL_MS*2));
    assertFalse(pool.getCalled());
    assertTrue(pool.waitAndResetCalled(POLL_MS*2));

    // stop polling: stops getting called
    factory.lifeCycleStopping(null);
    assertFalse(pool.waitAndResetCalled(POLL_MS*2));
  }
}
