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
package co.mitro.core.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.postgresql.util.PSQLException;
import org.postgresql.util.PSQLState;

import co.mitro.analysis.AuditLogProcessor;
import co.mitro.core.server.ManagerFactory.ConnectionMode;
import co.mitro.core.server.data.DBAudit;
import co.mitro.core.server.data.DBIdentity;
import co.mitro.core.server.data.DBProcessedAudit;

import com.codahale.metrics.Clock;

public class ManagerTest {
  private final static String DATABASE_URL = "jdbc:h2:mem:memtest;DB_CLOSE_DELAY=-1";

  private static final class FakeClock extends Clock {
    public long currentTick = System.nanoTime();

    @Override
    public long getTick() {
      return currentTick;
    }
  }

  private FakeClock clock;
  private Manager.Pool pool;
  private ManagerFactory managerFactory;

  private Manager manager;
  private String id1;

  private Manager manager2;
  private String id2;

  @Before
  public void setUp() throws SQLException {
    clock = new FakeClock();
    pool = new Manager.Pool(clock);
    managerFactory = new ManagerFactory(DATABASE_URL, pool, ManagerFactory.IDLE_TXN_POLL_SECONDS,
        TimeUnit.SECONDS, ConnectionMode.READ_WRITE);

    manager = managerFactory.newManager();
    id1 = manager.getTransactionId();
    assertEquals(manager, pool.getManagerFromUniqueId(id1));

    manager2 = managerFactory.newManager();
    id2 = manager2.getTransactionId();
    assertEquals(manager2, pool.getManagerFromUniqueId(id2));
  }

  @After
  public void tearDown() {
    if (manager.aclDao.getConnectionSource().isOpen()) {
      manager.close();
    }
    if (manager2.aclDao.getConnectionSource().isOpen()) {
      manager2.close();
    }
  }

  @Test
  public void testPoolGetClose() throws SQLException {
    assertEquals(manager2, pool.getManagerFromUniqueId(id2));
    assertEquals(manager, pool.getManagerFromUniqueId(id1));
    assertEquals(null, pool.getManagerFromUniqueId(""));

    // close connection 2
    manager2.close();
    assertEquals(null, pool.getManagerFromUniqueId(id2));
    assertEquals(manager, pool.getManagerFromUniqueId(id1));

    // close/remove connection 2 again: exception
    try {
      pool.removeManager(manager2);
      fail("expected exception");
    } catch (AssertionError e) {}
    try {
      manager2.close();
      fail("expected exception");
    } catch (AssertionError e) {}
  }

  @Test
  public void testExpiration() {
    // get m1
    long t0 = clock.currentTick;
    assertEquals(manager, pool.getManagerFromUniqueId(id1));

    // get m2 later
    clock.currentTick += 10;
    long t10 = clock.currentTick;
    assertEquals(manager2, pool.getManagerFromUniqueId(id2));

    // check for expiration nearly at the expiration time
    clock.currentTick = t0 + Manager.Pool.ABORT_NS - 1;
    assertEquals(0, pool.abortExpiredTransactions());
    // fetch m1
    assertEquals(manager, pool.getManagerFromUniqueId(id1));

    // check for expiration at exactly the expiration time
    clock.currentTick = t10 + Manager.Pool.ABORT_NS;
    assertEquals(1, pool.abortExpiredTransactions());

    assertNull(pool.getManagerFromUniqueId(id2));
    assertFalse(manager2.getConnectionSource().isOpen());
  }

  /** Locks a java.util.concurrent.locks.Lock in a new thread. */
  private static final class LockInOtherThread implements Runnable {
    private final Lock lock;
    private final Thread t = new Thread(this);
    private final CountDownLatch acquiredLock = new CountDownLatch(1);
    private final CountDownLatch shouldUnlock = new CountDownLatch(1);

    public LockInOtherThread(Lock lock) {
      this.lock = lock;
      t.start();
      try {
        acquiredLock.await();
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }

    public void unlock() {
      shouldUnlock.countDown();
      try {
        t.join();
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }

    public void run() {
      lock.lock();
      acquiredLock.countDown();
      try {
        shouldUnlock.await();
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
      lock.unlock();
    }
  }

  @Test
  public void testExpirationLocked() {
    LockInOtherThread l = new LockInOtherThread(manager.lock);
    try {
      assertEquals(0, pool.abortExpiredTransactions());
      clock.currentTick += Manager.Pool.ABORT_NS;
      assertEquals(1, pool.abortExpiredTransactions());

      assertEquals(manager, pool.getManagerFromUniqueId(id1));
    } finally {
      l.unlock();
    }

    clock.currentTick += Manager.Pool.ABORT_NS;
    assertEquals(1, pool.abortExpiredTransactions());
  }

  @Test
  public void testExpireAllWrapAround() {
    // managers are expired when times are in the far future and with long wraparound
    clock.currentTick = Long.MAX_VALUE;
    pool.getManagerFromUniqueId(id1);
    pool.getManagerFromUniqueId(id2);
    clock.currentTick = -100000;
    assertEquals(2, pool.abortExpiredTransactions());
    assertEquals(null, pool.getManagerFromUniqueId(id1));
    assertEquals(null, pool.getManagerFromUniqueId(id2));

    assertFalse(manager.getConnectionSource().isOpen());
    assertFalse(manager2.getConnectionSource().isOpen());
  }

  @Test
  public void testExtractPSQLException() {
    PSQLException psqlException = new PSQLException("fake error", PSQLState.UNEXPECTED_ERROR);
    // passing in the exception itself works
    assertEquals(psqlException, Manager.extractPSQLException(psqlException));

    // wrapping the exception in a SQLException (as done by ORMLite) works
    SQLException wrap1 = new SQLException("wrapper", psqlException);
    assertEquals(psqlException, Manager.extractPSQLException(wrap1));

    // ORMLite can also double wrap the exception
    SQLException wrap2 = new SQLException("double", wrap1);
    assertEquals(psqlException, Manager.extractPSQLException(wrap2));

    // SQLException with some other kind of exception: null
    SQLException other = new SQLException("other", new RuntimeException("cause"));
    assertNull(Manager.extractPSQLException(other));
    
    Throwable t = new Throwable("hello", psqlException);
    assertEquals(psqlException, Manager.extractPSQLException(t));
  }

  @Test
  public void unquotedSqlIdentifier() {
    String tenDigits = "1234567890";
    String fiftyDigits = tenDigits + tenDigits + tenDigits + tenDigits + tenDigits;
    String sixtyThree = "_234567890" + fiftyDigits + "012";
    assertEquals(63, sixtyThree.length());

    String[] badIdentifiers = {
        "",
        "A",
        "0",
        " ",
        "\n",
        sixtyThree + "0",
        "hello'quote",
        "hello''quote",
        "hello\"quote",
        "hello\"\"quote",
        "hellOoo",
        "hel.oo",
        "h\u00e9llo",
        "hello ",
        " hello",
        "\nhello",
        "hello\n",
        "hello\r\n",
        "hello\r",
        "hello\na",
        "hello world",
        "hello\nworld",
        "hello\tworld",
        "valid\nTHIS IS AWESOME",
    };

    String[] goodIdentifiers = {
        "a",
        "_",
        sixtyThree,
        "_0_",
        "hello",
        "hello9",
        "hello_9",
        "hello_world",
    };

    for (String bad : badIdentifiers) {
      assertFalse("identifier returned true: " + bad, Manager.isUnquotedSqlIdentifier(bad));
    }

    for (String good : goodIdentifiers) {
      assertTrue("identifier returned false: " + good, Manager.isUnquotedSqlIdentifier(good));
    }
  }

  @Test
  public void auditCommitedInAnotherTransaction() throws SQLException {
    long beforeCount = manager.auditDao.countOf();
    DBIdentity user = null;
    manager.addAuditLog(DBAudit.ACTION.ADD_GROUP, user, null, null, null, null);
    assertEquals(beforeCount + 1, manager.auditDao.countOf());
    manager.rollbackTransaction();
    assertEquals(beforeCount + 2, manager.auditDao.countOf());
  }

  @Test
  public void processedAuditSameTransaction() throws SQLException {
    long beforeCount = manager.processedAuditDao.countOf();

    DBIdentity user = new DBIdentity();
    user.setName("hello@example.com");
    DBAudit audit = new DBAudit();
    audit.setUser(user);
    audit.setTimestampMs(1392069651764L);
    audit.setTransactionId("foofoo");
    DBProcessedAudit processed = new DBProcessedAudit(AuditLogProcessor.ActionType.CREATE_GROUP, audit);
    manager.processedAuditDao.create(processed);

    assertEquals(beforeCount + 1, manager.processedAuditDao.countOf());
    manager.rollbackTransaction();
    assertEquals(beforeCount, manager.processedAuditDao.countOf());
  }
}
