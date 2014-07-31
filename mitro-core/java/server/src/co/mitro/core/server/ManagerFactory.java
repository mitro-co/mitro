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

import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.util.component.LifeCycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.mitro.analysis.AuditLogProcessor;

import com.j256.ormlite.jdbc.JdbcConnectionSource;

/**
 * Creates Managers for a database. Contains a ManagerPool to manage long running transactions,
 * and will periodically clean up idle transactions.
 *
 * TODO: Eventually this should actually be used to create managers!
 */
public class ManagerFactory implements LifeCycle.Listener {
  private static final Logger logger = LoggerFactory.getLogger(ManagerFactory.class);
  public static final long IDLE_TXN_POLL_SECONDS = 60;
  private static final int NUMBER_THREADS = 1;

  private final ExecutorService logProcessor = 
      Executors.newFixedThreadPool(NUMBER_THREADS, new DaemonThreadFactory());

  /** Creates daemon threads so the Exector does not stop application shutdown. */
  public static final class DaemonThreadFactory implements ThreadFactory {
    // used to create threads with the same names as the default
    private final ThreadFactory defaultFactory = Executors.defaultThreadFactory();

    @Override
    public Thread newThread(Runnable r) {
      Thread t = defaultFactory.newThread(r);
      t.setDaemon(true);
      return t;
    }
  }

  private static final class BackgroundAuditProcessor extends AuditLogProcessor implements Runnable {
    private String transactionId;
    public BackgroundAuditProcessor(String transactionId) {
      this.transactionId = transactionId;
    }
    @Override
    public void run() {
      try (Manager mgr = getInstance().newManager()) {
        // do NOT remove this disableAuditLogs() call.  Otherwise, we will go in an endless loop
        // (the manager used by the transaction processor will trigger another txn complete call)
        mgr.disableAuditLogs();
        int count = putActionsForTransactionId(mgr, transactionId);
        logger.info("processed audit logs for transaction {} and created {} actions", transactionId, count);
      } catch (SQLException e) {
        logger.error("unknown error processing logs", e);
      }
    }
    
  }
  
  // TODO: this may not need to be synchronized.
  public synchronized void transactionComplete(String txnId) {
    logProcessor.execute(new BackgroundAuditProcessor(txnId));
  }
  
  // TODO: Remove this; Inject ManagerFactory everywhere instead
  public static String DATABASE_URL = "jdbc:postgresql://localhost:5432/mitro";

  // TODO: Replace this by injecting the dependency where needed
  private static ManagerFactory INSTANCE = new ManagerFactory();

  public static ManagerFactory getInstance() {
	  return INSTANCE;
  }

  // TODO: Figure out a better way to inject this
  public static void setDatabaseUrlForTest(String databaseUrl) {
    DATABASE_URL = databaseUrl;
    // recreate the DB with the same mode as the previous one (in case it was switched!)
    unsafeRecreateSingleton(INSTANCE.mode);
  }

  // For testing; remove this! It is thread unsafe
  public static void unsafeRecreateSingleton(ConnectionMode mode) {
	  INSTANCE = new ManagerFactory(ManagerFactory.DATABASE_URL, new Manager.Pool(),
	      IDLE_TXN_POLL_SECONDS, TimeUnit.SECONDS, mode);
  }

  private final String databaseUrl;
  private final ManagerPool pool;
  private final long pollMs;
  private final ConnectionMode mode;
  private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

  public enum ConnectionMode {
    READ_WRITE, READ_ONLY
  }

  /**
   * Creates Managers connected to databaseUrl.
   *
   * @param databaseUrl JDBC URL of the database.
   * @param pool ManagerPool used to pool connections.
   * @param pollPeriod time that the pool will be checked for expired connections.
   * @param unit time unit for pollPeriod.
   * @param mode determines if the Manager can be used for writes or not.
   */
  public ManagerFactory(String databaseUrl, ManagerPool pool, long pollPeriod, TimeUnit unit, ConnectionMode mode) {
    this.databaseUrl = databaseUrl;
    this.pool = pool;
    this.pollMs = unit.toMillis(pollPeriod);
    this.mode = mode;
    tryCreateTables();
  }

  /**
   * Creates a ManagerFactory with default arguments.
   */
  public ManagerFactory() {
    this(ManagerFactory.DATABASE_URL, new Manager.Pool(), IDLE_TXN_POLL_SECONDS, TimeUnit.SECONDS,
        ConnectionMode.READ_WRITE);
  }

  private void tryCreateTables() {
    try {
      JdbcConnectionSource connectionSource = new JdbcConnectionSource(databaseUrl);
      try {
        Manager.createTablesIfNotExists(connectionSource);
      } finally {
        connectionSource.closeQuietly();
      }
    } catch (SQLException e) {
      // TODO: Don't just ignore this?
      logger.error("Exception while creating tables:", e);
    }
  }

  /**
   * Returns a new manager, registered with the connection pool.
   *
   * TODO: Rethink the Manager/Factory/Pool interfaces; it is hard to use
   * pooled Managers correctly: technically, this should be locked/unlocked
   * when used, since the Pool periodically checks for idle managers.
   */
  public Manager newManager() throws SQLException {
    // TODO: Use a different DB for the auditConnection?
    JdbcConnectionSource connection = new JdbcConnectionSource(databaseUrl);
    // we use a separate connection so it is a different transaction
    JdbcConnectionSource auditConnection = new JdbcConnectionSource(databaseUrl);

    // TODO: support creating unpooled connections?
    Manager m = new Manager(pool, connection, auditConnection, mode);
    pool.addManagerToPool(m);
    return m;
  }

  public Manager getTransaction(String transactionId) {
    return pool.getManagerFromUniqueId(transactionId);
  }

  @Override
  public void lifeCycleFailure(LifeCycle event, Throwable arg1) {
  }

  @Override
  public void lifeCycleStarted(LifeCycle event) {
  }

  @Override
  public void lifeCycleStarting(LifeCycle event) {
    executor.scheduleAtFixedRate(new Runnable() {
      @Override
      public void run() {
        pool.abortExpiredTransactions();
      }
    }, pollMs, pollMs, TimeUnit.MILLISECONDS);
  }

  @Override
  public void lifeCycleStopped(LifeCycle event) {
  }

  @Override
  public void lifeCycleStopping(LifeCycle event) {
    executor.shutdown();
    try {
      boolean terminated = executor.awaitTermination(1, TimeUnit.SECONDS);
      if (!terminated) {
        logger.warn("timed out waiting for scheduled task to stop");
      }
    } catch (InterruptedException e) {
      logger.warn("interrupted while waiting for scheduled task", e);
    }
  }
}
