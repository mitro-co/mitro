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
package co.mitro.core.servlets;

import static org.junit.Assert.fail;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;

import co.mitro.analysis.AuditLogProcessor.ActionType;
import co.mitro.core.crypto.CrappyKeyFactory;
import co.mitro.core.crypto.KeyInterfaces;
import co.mitro.core.crypto.KeyInterfaces.CryptoError;
import co.mitro.core.crypto.KeyInterfaces.PrivateKeyInterface;
import co.mitro.core.exceptions.MitroServletException;
import co.mitro.core.server.Manager;
import co.mitro.core.server.ManagerFactory;
import co.mitro.core.server.ManagerFactory.ConnectionMode;
import co.mitro.core.server.SecretsBundle;
import co.mitro.core.server.data.DBAcl;
import co.mitro.core.server.data.DBAcl.AccessLevelType;
import co.mitro.core.server.data.DBAcl.CyclicGroupError;
import co.mitro.core.server.data.DBGroup;
import co.mitro.core.server.data.DBGroupSecret;
import co.mitro.core.server.data.DBIdentity;
import co.mitro.core.server.data.DBProcessedAudit;
import co.mitro.core.server.data.DBServerVisibleSecret;
import co.mitro.core.server.data.RPC.CreateOrganizationRequest;
import co.mitro.core.server.data.RPC.CreateOrganizationRequest.PrivateGroupKeys;
import co.mitro.core.server.data.RPC.CreateOrganizationResponse;
import co.mitro.core.servlets.MitroServlet.MitroRequestContext;
import co.mitro.twofactor.TwoFactorSigningService;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.j256.ormlite.jdbc.JdbcConnectionSource;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.support.DatabaseConnection;

/** Creates a Manager that uses H2, the in-memory DB. */
public class MemoryDBFixture {
  protected static final KeyInterfaces.KeyFactory keyFactory = new CrappyKeyFactory();
  // DATABASE_TO_UPPER must be disabled in order to deal with mixed case table names (ormlite bug probably)
  // (add TRACE_LEVEL_SYSTEM_OUT=2 for query logging).
  private final static String DATABASE_URL = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE";
  protected static final String DEVICE_ID = "DEVICE1";
  protected static final Gson gson = new Gson();
  protected static final String ENCRYPTED_GROUP_KEY = "encrypted group key";
  protected ManagerFactory managerFactory;
  protected Manager manager;

  /** A member of testGroup when the test starts. */
  protected DBIdentity testIdentity;
  protected KeyInterfaces.PrivateKeyInterface testIdentityKey;
  /** Not a member of any groups when the test starts. */
  protected DBIdentity testIdentity2;
  protected String testIdentityLoginToken;
  /** Contains a single member, testIdentity, when the test starts. */
  protected DBGroup testGroup;

  protected String testIdentityLoginTokenSignature;

  protected Map<Integer, PrivateKeyInterface> groupToPrivateKeyMap;
  
  @Before
  public void memorySetUp() throws SQLException, CyclicGroupError, CryptoError, MitroServletException {
    // Create a fake SecretsBundle
    TwoFactorSigningService.initialize(SecretsBundle.generateForTest());
    groupToPrivateKeyMap = Maps.newHashMap();
    JdbcConnectionSource connectionSource = new JdbcConnectionSource(DATABASE_URL);
    connectionSource.getReadWriteConnection().executeStatement(
        "DROP ALL OBJECTS", DatabaseConnection.DEFAULT_RESULT_FLAGS);
    managerFactory = new ManagerFactory(DATABASE_URL, new Manager.Pool(),
        ManagerFactory.IDLE_TXN_POLL_SECONDS, TimeUnit.SECONDS, ConnectionMode.READ_WRITE);
    manager = managerFactory.newManager();
    testIdentityKey = keyFactory.generate();
    testIdentity = createIdentity("user@example.com", testIdentityKey);
    testIdentityLoginToken = GetMyPrivateKey.makeLoginTokenString(testIdentity, null, null);
    testIdentityLoginTokenSignature = testIdentityKey.sign(testIdentityLoginToken);

    testIdentity2 = createIdentity("user2@example.com", null);
       
    testGroup = createGroupContainingIdentity(testIdentity);
    manager.commitTransaction();

    // remove the audit log that commit writes so that tests start with an empty log
    connectionSource.getReadWriteConnection().executeStatement(
        "DELETE FROM audit;", DatabaseConnection.DEFAULT_RESULT_FLAGS);
    connectionSource.getReadWriteConnection().commit(null);
  }

  protected DBIdentity createIdentity(String name) throws SQLException, MitroServletException {
    return createIdentity(name, null);
  }

  protected void authorizeIdentityForDefaultDevice(DBIdentity thisIdentity) throws SQLException, MitroServletException {
    // authorize this identity for the common device id.
    authorizeIdentityForDevice(thisIdentity, DEVICE_ID);
  }
  protected void authorizeIdentityForDevice(DBIdentity thisIdentity, String deviceId) throws SQLException, MitroServletException {
    // authorize this identity for the common device id.
    GetMyDeviceKey.maybeGetOrCreateDeviceKey(manager, thisIdentity, deviceId, false, null);
  }

  protected DBGroup createOrganization(DBIdentity actor, String name, Iterable<DBIdentity> admins, Iterable<DBIdentity> members) throws IOException, SQLException, MitroServletException {
    CreateOrganization servlet = new CreateOrganization(managerFactory, keyFactory);
    CreateOrganizationRequest request = new CreateOrganizationRequest();
    request.name = name;
    request.adminEncryptedKeys = Maps.newHashMap();
    request.memberGroupKeys = Maps.newHashMap();
    for (DBIdentity admin : admins) {
      request.adminEncryptedKeys.put(admin.getName(), "key for " + admin.getName());
      addMemberKeys(request, admin);
    }
    for (DBIdentity user : members) {
      addMemberKeys(request, user);
    }
    PrivateKeyInterface key;

    try {
      key = keyFactory.generate();
      request.publicKey = key.exportPublicKey().toString();
    } catch(CryptoError e) {
      throw new MitroServletException(e);
    }
    
    CreateOrganizationResponse out = (CreateOrganizationResponse) servlet.processCommand(new MitroRequestContext(actor, gson.toJson(request), manager, null));
    groupToPrivateKeyMap.put(out.organizationGroupId, key);
    return manager.groupDao.queryForId(out.organizationGroupId);
  }

  private static void addMemberKeys(CreateOrganizationRequest request,
      DBIdentity user) {
    PrivateGroupKeys pgk = new PrivateGroupKeys();
    pgk.publicKey = "public key for " + user.getName();
    pgk.keyEncryptedForUser = "key encrypted for user:" + user.getName();
    pgk.keyEncryptedForOrganization = "key encrypted for org:" + user.getName();
    request.memberGroupKeys.put(user.getName(), pgk);
  }
  
  protected DBIdentity createIdentity(String name, PrivateKeyInterface key) throws SQLException, MitroServletException {
    DBIdentity identity = new DBIdentity();
    identity.setName(name);
    if (null == key) {
      identity.setPublicKeyString("identity public key " + identity.getName());
    } else {
      try {
        identity.setPublicKeyString(key.exportPublicKey().toString());
      } catch (CryptoError e) {
        // TODO Auto-generated catch block
        throw new RuntimeException(e);
      }
    }
    DBIdentity.createUserInDb(manager, identity);

    // authorize this identity for the main device id
    this.authorizeIdentityForDefaultDevice(identity);

    return identity;
  }

  @After
  public void memoryTearDown() {
    if (manager != null && manager.getConnectionSource() != null) {
      manager.getConnectionSource().closeQuietly();
    }
  }

  protected DBGroup createGroupContainingIdentity(DBIdentity identity)
      throws SQLException, CyclicGroupError {
    return createGroupContainingIdentity(manager, identity);
  }

  protected DBAcl addToGroup(DBIdentity identity, DBGroup group, AccessLevelType accessLevel)
      throws CyclicGroupError, SQLException {
    return addToGroup(manager, identity, group, accessLevel);
  }

  public DBGroup createGroupContainingIdentity(Manager manager, DBIdentity identity)
      throws SQLException, CyclicGroupError {
    DBGroup group = new DBGroup();
    group.setName("hello");
    group.setPublicKeyString("temp");
    manager.groupDao.create(group);
    setKeyForGroup(group);
    manager.groupDao.update(group);

    addToGroup(manager, identity, group, DBAcl.AccessLevelType.ADMIN);
    manager.groupDao.refresh(group);
    return group;
  }

  // TODO: this exists only for MitroServletTest. This needs to be refactored.   
  public static DBGroup createGroupContainingIdentityStatic(Manager manager, DBIdentity identity)
      throws SQLException, CyclicGroupError {
    DBGroup group = new DBGroup();
    group.setName("hello");
    group.setPublicKeyString("public key strings");
    manager.groupDao.create(group);
    addToGroup(manager, identity, group, DBAcl.AccessLevelType.ADMIN);
    manager.groupDao.refresh(group);
    return group;
  }

  private void setKeyForGroup(DBGroup group) {
    try {
      PrivateKeyInterface key = keyFactory.generate();
      group.setPublicKeyString(key.exportPublicKey().toString());
      groupToPrivateKeyMap.put(group.getId(), key);
    } catch (CryptoError e) {
      throw new RuntimeException(e);
    }
  }

  public static DBAcl addToGroup(Manager manager, DBIdentity identity, DBGroup group,
      AccessLevelType accessLevel) throws CyclicGroupError, SQLException {
    DBAcl acl = makeAcl(identity, group, accessLevel);
    manager.aclDao.create(acl);
    return acl;
  }

  public static DBAcl makeAcl(DBGroup memberGroup, DBGroup group, AccessLevelType accessLevel)
      throws CyclicGroupError {
    DBAcl acl = makeAcl(group, accessLevel);
    acl.setMemberGroup(memberGroup);
    return acl;
  }

  public static DBAcl makeAcl(DBIdentity identity, DBGroup group, AccessLevelType accessLevel)
      throws CyclicGroupError {
    DBAcl acl = makeAcl(group, accessLevel);
    acl.setMemberIdentity(identity);
    return acl;
  }
  public static DBAcl makeAcl(DBGroup group, AccessLevelType accessLevel)
      throws CyclicGroupError {
    DBAcl acl = new DBAcl();
    acl.setGroup(group);
    acl.setGroupKeyEncryptedForMe(ENCRYPTED_GROUP_KEY);
    acl.setLevel(accessLevel);
    return acl;
  }


  /** Creates a new secret in group, sharing it with an org. 
   * @param org org with which to share or null*/
  protected DBServerVisibleSecret createSecret(DBGroup group, String clientVisibleEncrypted, 
      String criticalDataEncrypted, DBGroup org)
      throws SQLException {
    DBServerVisibleSecret secret = new DBServerVisibleSecret();
    manager.svsDao.create(secret);
    addSecretToGroup(secret, group, clientVisibleEncrypted, criticalDataEncrypted);
    if (org != null) {
      assert(org.isTopLevelOrganization());
      addSecretToGroup(secret, org, clientVisibleEncrypted, criticalDataEncrypted);
    }
    return secret;
  }

  protected DBGroupSecret addSecretToGroup(DBServerVisibleSecret secret, DBGroup group,
      String clientVisibleEncrypted, String criticalDataEncrypted)
      throws SQLException {
    DBGroupSecret groupSecret = new DBGroupSecret();
    groupSecret.setServerVisibleSecret(secret);
    groupSecret.setGroup(group);
    groupSecret.setClientVisibleDataEncrypted(clientVisibleEncrypted);
    groupSecret.setCriticalDataEncrypted(criticalDataEncrypted);
    manager.groupSecretDao.create(groupSecret);
    return groupSecret;
  }

  // TODO: Remove after we inject this dependency everywhere
  protected void replaceDefaultManagerDbForTest() {
    ManagerFactory.setDatabaseUrlForTest(DATABASE_URL);
  }

  protected DBGroup getPrivateOrgGroup(DBGroup organization, DBIdentity member)
      throws SQLException {
    // get all of member's groups
    QueryBuilder<DBAcl, Integer> directMemberQuery =
        manager.aclDao.queryBuilder().selectColumns(DBAcl.GROUP_ID_FIELD_NAME);
    directMemberQuery.where().eq(DBAcl.MEMBER_IDENTITY_FIELD_NAME, member.getId());

    // from those groups, get the groups that are also part of organization
    QueryBuilder<DBAcl, Integer> query = manager.aclDao.queryBuilder();
    query.where().in(DBAcl.GROUP_ID_FIELD_NAME, directMemberQuery).and()
        .eq(DBAcl.MEMBER_GROUP_FIELD_NAME, organization.getId());

    // find the private group from the list organization groups this user belongs to
    for (DBAcl acl : query.query()) {
      DBGroup group = acl.loadGroup(manager.groupDao);
      if (group.isPrivateUserGroup()) {
        return group;
      }
    }

    fail("did not find member's private organization group");
    return null;
  }
  public static DBAcl addOrgToGroup(Manager manager, DBGroup org, DBGroup group,
      AccessLevelType accessLevel) throws CyclicGroupError, SQLException {
    DBAcl acl = makeAcl(org, group, accessLevel);
    manager.aclDao.create(acl);
    return acl;
  }

  protected boolean hasAudit(Manager manager, ActionType action,
      DBIdentity actor, DBIdentity object, int secretId) throws SQLException {
    String txnId = manager.getTransactionId();
    manager.commitTransaction();
    List<DBProcessedAudit> audits = manager.processedAuditDao.queryForFieldValues(
        ImmutableMap.of(
            DBProcessedAudit.ACTION_FIELD_NAME, action,
            DBProcessedAudit.TRANSACTION_ID_FIELD_NAME, (Object)txnId,
            DBProcessedAudit.ACTOR_FIELD_NAME, actor,
            DBProcessedAudit.AFFECTED_SECRET_FIELD_NAME, secretId,
            DBProcessedAudit.AFFECTED_USER_FIELD_NAME, object
            ));
    
    if (audits.isEmpty()) {
      return false;
    }
    for (DBProcessedAudit audit : audits) {
      if (actor != null) {
        assertEquals(actor.getName(), audit.getActorName());
      }
      if (object != null) {
        assertEquals(object.getName(), audit.getAffectedUserName());
      }
    }
    return true;
  }

}

