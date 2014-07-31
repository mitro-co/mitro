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

import java.math.BigInteger;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

import org.postgresql.util.PSQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.mitro.core.exceptions.MitroServletException;
import co.mitro.core.server.BeforeAfterState.UserDiff;
import co.mitro.core.server.ManagerFactory.ConnectionMode;
import co.mitro.core.server.data.DBAcl;
import co.mitro.core.server.data.DBAudit;
import co.mitro.core.server.data.DBDeviceSpecificInfo;
import co.mitro.core.server.data.DBEmailQueue;
import co.mitro.core.server.data.DBEmailRecord;
import co.mitro.core.server.data.DBFutureAlert;
import co.mitro.core.server.data.DBGroup;
import co.mitro.core.server.data.DBGroupSecret;
import co.mitro.core.server.data.DBHistoricalOrgState;
import co.mitro.core.server.data.DBHistoricalUserState;
import co.mitro.core.server.data.DBIdentity;
import co.mitro.core.server.data.DBIssue;
import co.mitro.core.server.data.DBMitroAccessEmail;
import co.mitro.core.server.data.DBPendingGroup;
import co.mitro.core.server.data.DBProcessedAudit;
import co.mitro.core.server.data.DBProcessedAudit.GrantOrRevoke;
import co.mitro.core.server.data.DBServerVisibleSecret;
import co.mitro.core.server.data.DBSignup;
import co.mitro.core.server.data.DBStripeCustomer;
import co.mitro.core.server.data.DBUserName;
import co.mitro.core.server.data.OldJsonData;

import com.codahale.metrics.Clock;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.db.PostgresDatabaseType;
import com.j256.ormlite.jdbc.JdbcConnectionSource;
import com.j256.ormlite.stmt.SelectArg;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.support.DatabaseConnection;
import com.j256.ormlite.table.TableUtils;

public class Manager implements AutoCloseable {
  final static private SecureRandom randomNumberGenerator = new SecureRandom();
  
  private static final Logger logger = LoggerFactory.getLogger(Manager.class);

  // All the DB objects; used to create tables
  private static final Class<?>[] DATA_CLASSES = { DBGroup.class, DBAcl.class,
      DBGroupSecret.class, DBServerVisibleSecret.class, DBIdentity.class,
      DBIssue.class, DBEmailQueue.class, DBAudit.class, DBPendingGroup.class,
      DBDeviceSpecificInfo.class, DBSignup.class,
      DBHistoricalOrgState.class, DBHistoricalUserState.class, DBProcessedAudit.class,
      DBUserName.class, DBEmailRecord.class, DBFutureAlert.class,
      DBStripeCustomer.class, DBMitroAccessEmail.class };

  public final Lock lock = new ReentrantLock();
  public final BeforeAfterState userState = new BeforeAfterState(this);
  private final ConnectionMode mode;
  private final ConnectionSource connectionSource;
  private final ConnectionSource auditConnectionSource;
  /** Publicly visible transaction id. */
  private final String transactionId = new BigInteger(256, randomNumberGenerator).toString(Character.MAX_RADIX);

  /** ManagerPool that stores this Manager, for AutoCloseable. */
  private final ManagerPool sourcePool;

  public Dao<DBGroup, Integer> groupDao;
  public Dao<DBAcl, Integer> aclDao;
  public Dao<DBIdentity, Integer> identityDao;
  public Dao<DBServerVisibleSecret, Integer> svsDao;
  public Dao<DBGroupSecret, Integer> groupSecretDao;
  public Dao<DBIssue, Integer> issueDao;
  public Dao<DBEmailQueue, Integer> emailDao;
  public Dao<DBAudit, Integer> auditDao;
  public Dao<DBPendingGroup, Integer> pendingGroupDao;
  public Dao<DBDeviceSpecificInfo, Integer> deviceSpecificDao;
  public Dao<DBSignup, Integer> signupDao;
  public Dao<DBHistoricalOrgState, Integer> historicalOrgDao;
  public Dao<DBHistoricalUserState, Integer> historicalUserDao;
  public Dao<DBProcessedAudit, Integer> processedAuditDao;
  public Dao<DBUserName, Integer> userNameDao;
  public Dao<DBEmailRecord, Integer> emailRecordDao;
  public Dao<DBFutureAlert, Integer> futureAlertDao;
  public Dao<DBStripeCustomer, Integer> stripeCustomerDao;
  public Dao<DBMitroAccessEmail, Integer> mitroAccessEmailDao;


  /**
   * This generates a list of SelectArgs which must be used when 
   * building queries, especially with Strings. Strings are otherwise not escaped, 
   * even when used with .in() or .eq(). This can result in an SQL injection
   * vulnerability.
   *  
   * See: http://ormlite.com/javadoc/ormlite-core/doc-files/ormlite_3.html#Select-Arguments
   * 
   * @param items A Collection of any specific type of Object.
   * @return A list of SelectArgs that can be used in place of the List of Objects.
   */
  public static List<SelectArg> makeSelectArgsFromList(
      Collection<? extends Object> items) {
    List<SelectArg> args = new ArrayList<SelectArg>(items.size());
    for (Object item : items) {
      args.add(new SelectArg(item));
    }
    return args;
  }
  
  
  /**
   * Stores in-progress transactions with a unique id. Aborts them after a timeout.
   */
  public static class Pool implements ManagerPool {
    /** Stores (manager, access time) pairs to simplify locking. */
    private static final class TimedManager {
      public final Manager manager;
      public long lastAccessedTick;

      public TimedManager(Manager manager, long tick) {
        this.manager = manager;
        lastAccessedTick = tick;
      }
    }

    private HashMap<String, TimedManager> uniqueIdManagerMap = new HashMap<String, TimedManager>();
    /** Timeout clock, makes timed expiration testable. */
    private final Clock clock;

    /** Abort the transaction if not accessed within this time (relative to clock). */
    static final long ABORT_NS = TimeUnit.MINUTES.toNanos(2);

    public Pool() {
      this(Clock.defaultClock());
    }

    public Pool(Clock clock) {
      this.clock = clock;
    }

    @Override
    public synchronized Manager getManagerFromUniqueId(String uniqueId) {
      TimedManager timed = uniqueIdManagerMap.get(uniqueId);
      if (timed == null) return null;

      // update access time and return the manager
      timed.lastAccessedTick = clock.getTick();
      return timed.manager;
    }

    @Override
    public synchronized void addManagerToPool(Manager m) {
      TimedManager timed = new TimedManager(m, clock.getTick());
      TimedManager previousValue = uniqueIdManagerMap.put(m.getTransactionId(), timed);
      assert previousValue == null;
    }

    @Override
    public synchronized void removeManager(Manager manager) {
      TimedManager timed = uniqueIdManagerMap.remove(manager.getTransactionId());
      assert timed != null;
    }

    /** Aborts any transactions that have been idle for longer than ABORT_NS. */
    @Override
    public synchronized int abortExpiredTransactions() {
      logger.debug("checking for idle transactions to be aborted");
      long now = clock.getTick();

      // add expired entries to a separate set to avoid invalidating iterators
      List<TimedManager> expired = Lists.newArrayList();
      for (Map.Entry<String, TimedManager> e : uniqueIdManagerMap.entrySet()) {
        // delta might be negative in case of multi-core sync weirdness
        // http://stas-blogspot.blogspot.com/2012/02/what-is-behind-systemnanotime.html
        long delta = now - e.getValue().lastAccessedTick;
        if (delta >= ABORT_NS) {
          expired.add(e.getValue());
        }
      }

      // Iterate over a separate set to avoid "invalid iterator" problems
      int count = 0;
      for (TimedManager timed : expired) {
        long delta = now - timed.lastAccessedTick;
        Manager m = timed.manager;
        if (timed.manager.lock.tryLock()) {
          try {
            logger.info("closing idle manager (id={}; idle time={} ns)", m.getTransactionId(), delta);
            try {
              m.addAuditLog(DBAudit.ACTION.TRANSACTION_TERMINATE, null, null, null, null, null);
            } catch (SQLException e) {
              logger.warn("failed to add audit log entry:", e);
            }

            // Manager.close calls Pool.removeManager()
            m.close();
            assert !uniqueIdManagerMap.containsKey(m.getTransactionId());

            count += 1;
          } finally {
            m.lock.unlock();
          }
        } else {
          logger.warn("idle manager is locked; not closing (id={}; idle time={} ns)",
              m.getTransactionId(), delta);
          // TODO:
          // m.addAuditLog(DBAudit.ACTION.TRANSACTION_IDLE_AND_LOCKED, null, null, null, null, null);
        }
      }

      return count;
    }
  };

  /**
   * Returns true if e is a Postgres "already exists" error message. OrmLite wraps Postgres's
   * errors in its own SQLException, so you need to pass the result of e.getCause() in here.
   */
  private static boolean isPostgresAlreadyExistsError(SQLException e) {
    PSQLException p = extractPSQLException(e);
    // Postgres's message: ERROR: relation "groups_id_seq" already exists
    // boolean result = p.getMessage().contains("exists");
    return (null != p) && (p.getSQLState().equals(POSTGRES_DUPLICATE_TABLE_SQLSTATE));
  }

  /**
   * Returns true if e is a Postgres "read-only" error message.
   */
  private static boolean isPostgresReadOnlyError(SQLException e) {
    PSQLException p = extractPSQLException(e);
    return (null != p) && (p.getSQLState().equals(POSTGRES_READONLY_SQLSTATE));
  }

  /**
   * Extracts a PSQLException from an ORMLite wrapped SQLException.  
   * This is necessary in order to pull out sqlstate data
   * @param e: a SQLException object (hopefully) containing a wrapped PSQLException
   * @return the PSQLException that's contained or NULL if none is contained.
   */
  public static PSQLException extractPSQLException(Throwable exception) {
    while (exception != null) {
      if (exception instanceof PSQLException) {
        return (PSQLException) exception;
      }
      exception = exception.getCause();
    }
    return null;
  }

  /** See http://www.postgresql.org/docs/current/static/errcodes-appendix.html */
  private static final String POSTGRES_DUPLICATE_TABLE_SQLSTATE = "42P07";
  private static final String POSTGRES_READONLY_SQLSTATE = "25006";

  // TODO: Move into ManagerFactory
  static void createTablesIfNotExists(ConnectionSource connectionSource)
      throws SQLException {
    assert connectionSource.getReadWriteConnection().isAutoCommit();
    for (Class<?> c : DATA_CLASSES) {
      // Create each table, but ignore the "already exists" errors.
      // TODO: This won't create missing secondary indexes!
      try {
        TableUtils.createTable(connectionSource, c);
      } catch (SQLException e) {
        if (! (isPostgresAlreadyExistsError(e) || isPostgresReadOnlyError(e))) {
          // unexpected error or a different database?
          throw e;
        }
      }
    }
    
    /*
     * WARNING! from the manual:
     * In general, a unique constraint is violated when there are two
     * or more rows in the table where the values of all of the columns included
     * in the constraint are equal. However, null values are not considered
     * equal in this comparison. That means even in the presence of a unique
     * constraint it is possible to store duplicate rows that contain a null
     * value in at least one of the constrained columns. This behavior conforms
     * to the SQL standard, but we have heard that other SQL databases may not
     * follow this rule. So be careful when developing applications that are
     * intended to be portable.
     */
    // TODO: Figure out a less ugly way to do this? OrmLite uniqueCombo?
    createConstraintOnPostgres(connectionSource, "groups_name_scope",
        "ALTER TABLE groups ADD CONSTRAINT groups_name_scope UNIQUE (name, scope)");

    // group_id first because we query on it (all secrets in group)
    createConstraintOnPostgres(connectionSource, "group_secret_svs_group",
        "ALTER TABLE group_secret ADD CONSTRAINT group_secret_svs_group UNIQUE " +
            "(group_id, \"serverVisibleSecret_id\")");
  }

  // first char = letter or underscore
  // others = letter, underscore, digits
  // max length = 63 (on Postgres?)
  // identifiers without quotes are case insensitive, so we require lower case
  // theoretically this can include accented characters (!), but we require ascii
  private static final Pattern SQL_UNQUOTED_ID = Pattern.compile("^[a-z_][a-z0-9_]{0,62}$");

  /**
   * Returns true if identifier is a valid SQL identifier to be used unquoted. See:
   * http://www.postgresql.org/docs/devel/static/sql-syntax-lexical.html#SQL-SYNTAX-IDENTIFIERS
   */
  public static boolean isUnquotedSqlIdentifier(String identifier) {
    return SQL_UNQUOTED_ID.matcher(identifier).matches();
  }

  /**
   * If using Postgres and constaintName doesn't exist, it executes constraintStatement to
   * create it.
   * @return true if a constraint is created.
   */
  private static boolean createConstraintOnPostgres(
      ConnectionSource connectionSource, String constraintName, String constraintStatement) throws SQLException {
    Preconditions.checkArgument(isUnquotedSqlIdentifier(constraintName),
        "invalid constraint name: %s", constraintName);

    if (! (connectionSource.getDatabaseType() instanceof PostgresDatabaseType)) {
      logger.warn("Not creating constraint {}: DB is not Postgres", constraintName);
      return false;
    }

    // Creating constraint can return "already exists" error OR SQLState 42501: must be owner
    // of relation groups (if not owner), so we check for existence this way instead
    // TODO: This is slightly dangerous; build the query in a better way?
    String queryString =
        "SELECT count(*) FROM pg_constraint WHERE conname = '" + constraintName + "';";
    long constraintCount = connectionSource.getReadOnlyConnection().queryForLong(
          queryString);
    if (constraintCount != 0) {
      assert constraintCount == 1;
      return false;
    }

    logger.info("Creating constraint {}", constraintName);
    connectionSource.getReadWriteConnection().executeStatement(
        constraintStatement,
        DatabaseConnection.DEFAULT_RESULT_FLAGS);
    assert connectionSource.getReadOnlyConnection().queryForLong(queryString) == 1;
    return true;
  }

  public Manager(ManagerPool pool, JdbcConnectionSource connectionSource,
      JdbcConnectionSource auditConnectionSource, ManagerFactory.ConnectionMode mode)
          throws SQLException {
    this.mode = mode;
    this.sourcePool = pool;
    this.connectionSource = connectionSource;
    this.auditConnectionSource = null != auditConnectionSource ? auditConnectionSource
        : connectionSource;

    connectionSource.getReadWriteConnection().setAutoCommit(false);
    if (connectionSource.getDatabaseType() instanceof PostgresDatabaseType) {
      String type = "serializable";
      if (mode == ConnectionMode.READ_ONLY) {
        type = "repeatable read read only";
      }

      // this sets the default for all future transactions on this session:
      connectionSource.getReadWriteConnection().executeStatement(
          "set session characteristics as transaction isolation level " + type
              + ";", DatabaseConnection.DEFAULT_RESULT_FLAGS);

      // the documentation is unclear on whether the above affects the current
      // transaction,
      // so set it on the current transaction anyway.
      connectionSource.getReadWriteConnection().executeStatement(
          "set transaction isolation level " + type + ";",
          DatabaseConnection.DEFAULT_RESULT_FLAGS);
    } else {
      ; // we are not setting isolation mode
    }

    groupDao = createDao(DBGroup.class, this.connectionSource);
    aclDao = createDao(DBAcl.class, this.connectionSource);
    identityDao = createDao(DBIdentity.class, this.connectionSource);
    svsDao = createDao(DBServerVisibleSecret.class, this.connectionSource);
    groupSecretDao = createDao(DBGroupSecret.class, this.connectionSource);
    issueDao = createDao(DBIssue.class, this.connectionSource);
    emailDao = createDao(DBEmailQueue.class, this.connectionSource);
    futureAlertDao = createDao(DBFutureAlert.class, this.connectionSource);
    auditDao = createDao(DBAudit.class, this.auditConnectionSource);
    pendingGroupDao = createDao(DBPendingGroup.class, connectionSource);
    deviceSpecificDao = createDao(DBDeviceSpecificInfo.class, connectionSource);
    signupDao = createDao(DBSignup.class, this.connectionSource);
    historicalOrgDao = createDao(DBHistoricalOrgState.class, this.connectionSource);
    historicalUserDao = createDao(DBHistoricalUserState.class, this.connectionSource);
    processedAuditDao = createDao(DBProcessedAudit.class, this.connectionSource);
    userNameDao = createDao(DBUserName.class, this.connectionSource);
    emailRecordDao = createDao(DBEmailRecord.class, this.connectionSource);
    stripeCustomerDao = createDao(DBStripeCustomer.class, this.connectionSource);
    mitroAccessEmailDao = createDao(DBMitroAccessEmail.class, this.connectionSource);
  }

  private <D extends Dao<T, ?>, T> D createDao(Class<T> clazz,
      ConnectionSource connectionSource) throws SQLException {
    assert Arrays.asList(DATA_CLASSES).contains(clazz) : "Class not in DATA_CLASSES: "
        + clazz.getName();
    return DaoManager.createDao(connectionSource, clazz);
  }

  @SuppressWarnings("unused")
  public void enableCaches() throws SQLException {
    // Enable the object cache, which makes eager fetching significantly faster
    // TODO: This may have bad effects when combined with transactions!
    // Currently we throw out the Manager at the end of the transaction, so this
    // is okay
    // TODO: Revisit after we disable eager fetching on everything.
    if (false) {
      Dao<?, ?>[] daos = { groupDao, aclDao, identityDao, svsDao,
          groupSecretDao };
      for (Dao<?, ?> dao : daos) {
        dao.setObjectCache(true);
      }
    }
  }

  public void commitTransaction() throws SQLException {
    try {
      // figure out what has changed. if anything
      Map<DBIdentity, UserDiff> userChanges = userState.diffState();
      // TODO: enqueue emails here.
      for (DBIdentity user : userChanges.keySet()) {
        UserDiff ud = userChanges.get(user);
        for (Integer secretId : ud.newSecrets) {
          processedAuditDao.create(new DBProcessedAudit(this.requestor, user, secretId, GrantOrRevoke.GRANT, this.transactionId, this.ipString, this.deviceId));
        }
        for (Integer secretId : ud.removedSecrets) {
          Preconditions.checkNotNull(requestor);
          Preconditions.checkNotNull(user);
          processedAuditDao.create(new DBProcessedAudit(this.requestor, user, secretId, GrantOrRevoke.REVOKE, this.transactionId, this.ipString, this.deviceId));
        }
        
        if (!ud.newSecrets.isEmpty()) {
          logger.info("{} gained access to {} secrets", ud.userName, ud.newSecrets.size());
        }
        if (!ud.removedSecrets.isEmpty()) {
          logger.info("{} lost access to {} secrets", ud.userName, ud.removedSecrets.size());          
        }
      }
    
    } catch (MitroServletException e) {
      logger.error("unkonwn error creating log lines", e);
    } finally {    
      addAuditLog(DBAudit.ACTION.TRANSACTION_COMMIT, this.requestor, null, null, null, null);
      connectionSource.getReadWriteConnection().commit(null);
    }
  }

  public void rollbackTransaction() throws SQLException {
    logger.info("Rolling back transaction");
    addAuditLog(DBAudit.ACTION.TRANSACTION_ROLLBACK, this.requestor, null, null, null, null);
    connectionSource.getReadWriteConnection().rollback(null);
  }

  public ConnectionSource getConnectionSource() {
    return this.connectionSource;
  }

  public String getTransactionId() {
    return transactionId;
  }

  public boolean isReadOnly() {
    return mode == ConnectionMode.READ_ONLY;
  }

  /**
   * Returns the DBGroupSecret for (group, secret) or null if it does not exist.
   * You can pass "new" values if avoid querying for the group and secret in
   * advance. We use the types to avoid accidentally screwing up the id order.
   */
  public DBGroupSecret getGroupSecret(DBGroup group,
      DBServerVisibleSecret secret) throws SQLException {
    List<DBGroupSecret> secrets = groupSecretDao
        .queryForFieldValues(ImmutableMap.of(DBGroupSecret.SVS_ID_NAME,
            (Object) secret.getId(), DBGroupSecret.GROUP_ID_NAME, group.getId()));
    if (secrets.size() == 0) {
      return null;
    } else {
      assert secrets.size() == 1;
      return secrets.get(0);
    }
  }

  @Override
  public void close() {
    final String txnId = transactionId;
    sourcePool.removeManager(this);

    if (this.connectionSource != this.auditConnectionSource) {
      this.auditConnectionSource.closeQuietly();
    }
    this.connectionSource.closeQuietly();

    // HACK: Unregister Daos from OrmLite's stupid internal cache to avoid a memory leak
    // We need to unregister ALL tables; creating a Dao creates Daos for any related objects.
    // TODO: the docs suggest reusing a single JdbcPooledConnectionSource, which might work
    // (reusing a plain JdbcConnectionSource will not because it is a single connection)
    ConnectionSource[] sources = {connectionSource, auditConnectionSource};
    for (ConnectionSource source : sources) {
      // Get all the daos for all data types
      ArrayList<Dao<?, ?>> daos = new ArrayList<Dao<?, ?>>(DATA_CLASSES.length);
      for (Class<?> clazz : DATA_CLASSES) {
        // Get the cached Dao from the DaoManager, then remove it
        try {
          daos.add(DaoManager.createDao(source, clazz));
        } catch (SQLException e) {
          throw new RuntimeException("Unexpected SQLException", e);
        }
      }

      // Unregister them all to avoid leaks due to related tables
      for (Dao<?, ?> dao : daos) {
        DaoManager.unregisterDao(source, dao);
      }
    }
    
    // do NOT remove this writeAuditLogs check.  Otherwise, we will go in an endless loop
    // (the manager used by the transaction processor will trigger another txn complete)
    if (shouldWriteAuditLogs()) {
      ManagerFactory.getInstance().transactionComplete(txnId);
    }
  }

  private String ipString = "unknown";
  private DBIdentity requestor = null;
  private String deviceId = null;
  private String operationName = null;

  private boolean writeAuditLogs = true;
  public void setUserIp(String ipString) {
    // TODO Auto-generated method stub
    this.ipString = ipString;
  }

  /**
   * Set the requestor for audit logs. All accesses will be logged as from this user.
   * TODO: Remove this now that servlets are passed the requestor?
   */
  public void setRequestor(DBIdentity requestor, String deviceId) throws MitroServletException {
    this.deviceId = deviceId; 
    if (this.requestor != null && !requestor.equals(this.requestor)) {
      // transactions may not be shared between users.
      throw new MitroServletException(
          "Transactions may not be shared between users");
    }
    this.requestor = requestor;
  }
  
  

  /**
   * This sets the name of the high level operation name (e.g. sharesite), 
   * which is passed from the client.
   * @param op the name of the high-level operation, passed from the client.
   */
  public void setOperationName(String op) {
    this.operationName = op;
  }
  
  public String getOperationName() {
    return operationName;
  }

  public void disableAuditLogs() {
    this.writeAuditLogs = false;
  }
  
  public void enableAuditLogs() {
    this.writeAuditLogs = true;
  }

  public boolean shouldWriteAuditLogs() {
    return writeAuditLogs && mode != ConnectionMode.READ_ONLY; 
  }
  
  public void addAuditLog(DBAudit.ACTION action, DBIdentity user,
      DBIdentity targetUser, DBGroup targetGroup,
      DBServerVisibleSecret targetSVS, String note) throws SQLException {
    // do not create audit logs if we are on a read only connection or they
    // are otherwise disabled
    if (!shouldWriteAuditLogs()) {
      return;
    }
    DBAudit audit = new DBAudit();
    audit.setAction(action);
    audit.setSourceIp(ipString);
    audit.setNote(note);
    audit.setTargetGroup(targetGroup);
    audit.setTargetSVS(targetSVS);
    audit.setTargetUser(targetUser);
    audit.setUser(user != null ? user : this.requestor);
    audit.setTimestampMs(System.currentTimeMillis());
    audit.setTransactionId(transactionId);
    audit.setDeviceId(this.deviceId);
    audit.setOperationName(operationName);
    auditDao.create(audit);

  }

  private static OldJsonData ojd = new OldJsonData();

  public static void setOldJsonData(OldJsonData ojd) {
    Manager.ojd = ojd;
  }

  public static OldJsonData getOldJsonData() {
    // TODO Auto-generated method stub
    return ojd;
  }
}
