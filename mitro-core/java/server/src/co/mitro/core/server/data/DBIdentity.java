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

import java.math.BigInteger;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Set;

import co.mitro.core.server.Manager;
import co.mitro.core.server.data.DBAcl.AccessLevelType;
import co.mitro.core.servlets.EditGroup;
import co.mitro.twofactor.CryptoForBackupCodes;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "identity")
public class DBIdentity implements UserListInterface {
  // for QueryBuilder to be able to find the fields
  final static private SecureRandom randomNumberGenerator = new SecureRandom();
  public static final String NAME_FIELD_NAME = "name";
  public static final String ID_NAME = "id";
  public static final String VERIFIED_NAME = "verified";
  public static final String VERIFICATION_UID_NAME = "verification_uid";
  public static final String ANALYTICS_ID_NAME = "analytics_id";
  public static final String REFERRER_NAME = "referrer";
  public static final String GUID_COOKIE_NAME = "guid_cookie";
  

  /**
   * If true, the user's verification code is their email address (for testing).
   */
  // TODO: Not thread safe!
  public static boolean kUseEmailAsVerificationForTestsOnly = false;
  
  public static Collection<DBIdentity> getUsersFromNames(Manager mgr, Collection<String> userNames) throws SQLException {
    if (userNames.isEmpty()) {
      return Collections.emptyList();
    } else {
      // Find all identities in the DB that correspond to any of the user names provided.
      QueryBuilder<DBUserName, Integer> userNameQb = mgr.userNameDao.queryBuilder();
      userNameQb.where().in(DBUserName.EMAIL_FIELD_NAME, Manager.makeSelectArgsFromList(userNames));
      QueryBuilder<DBIdentity, Integer> identityQb = mgr.identityDao.queryBuilder();
      return identityQb.join(userNameQb).query();
    }
  }

  public static Set<Integer> getUserIdsFromNames(Manager mgr, Collection<String> userNames) throws SQLException {
    if (userNames.isEmpty()) {
      return Collections.emptySet();
    } else {
      Set<Integer> userIds = Sets.newHashSet();
      for (DBIdentity i : getUsersFromNames(mgr, userNames)) {
        userIds.add(i.getId());
      }
      return userIds;
    }
  }  
  
  public static Set<String> getUserNamesFromIds(Manager mgr, Collection<Integer> userIds) throws SQLException {
    if (userIds.isEmpty()) {
      return Collections.emptySet();
    } else {
      Set<String> rval = Sets.newHashSet();
      for (DBIdentity i : mgr.identityDao.queryBuilder().where().in(DBIdentity.ID_NAME, Manager.makeSelectArgsFromList(userIds)).query()) {
        rval.add(i.getName());
      }
      return rval;
    }
  }
  
  /**
   * @return the isVerified
   */
  public boolean isVerified() {
    return isVerified;
  }

  /**
   * @param isVerified the isVerified to set
   */
  public void setVerified(boolean isVerified) {
    this.isVerified = isVerified;
  }

  @DatabaseField(generatedId = true)
  private int id;
  
  @DatabaseField(columnName=VERIFICATION_UID_NAME)
  private String verificationUid;

  @DatabaseField(columnName=ANALYTICS_ID_NAME, canBeNull = true, dataType=DataType.LONG_STRING)
  private String analyticsId;
  
  @DatabaseField(columnName=VERIFIED_NAME, canBeNull=false)
  private boolean isVerified = true;
  
  @DatabaseField(columnName = NAME_FIELD_NAME, canBeNull = false, unique=true)
  private String name;

  @DatabaseField(dataType=DataType.LONG_STRING)
  private String publicKeyString;
  
  @DatabaseField(dataType=DataType.LONG_STRING)
  private String encryptedPrivateKeyString;
  
  @DatabaseField(dataType=DataType.LONG_STRING)
  private String keyserverSignatureOfIdAndKey;

  @DatabaseField(columnName="force_password_change", canBeNull=false)
  private boolean changePasswordOnNextLogin = false;
  
  @DatabaseField()
  private String twoFactorSecret;
  
  @DatabaseField()
  private String backup1;
  
  @DatabaseField()
  private String backup2;
  
  @DatabaseField()
  private String backup3;
  
  @DatabaseField()
  private String backup4;
  
  @DatabaseField()
  private String backup5;
  
  @DatabaseField()
  private String backup6;
  
  @DatabaseField()
  private String backup7;
  
  @DatabaseField()
  private String backup8;
  
  @DatabaseField()
  private String backup9;
  
  @DatabaseField()
  private String backup10;
  
  @DatabaseField()
  private long lastAuthMs;
  
  // Number of backups is now calculated dynamically.
  @Deprecated
  @DatabaseField()
  private int numAvailableBackups;
  
  @DatabaseField()
  private long enabledTFAMs;
  
  
  @DatabaseField(columnName=REFERRER_NAME, dataType=DataType.LONG_STRING)
  private String referrer;
  
  @DatabaseField(columnName=GUID_COOKIE_NAME, dataType=DataType.LONG_STRING)
  private String guidCookie;
  
  public String toString() {
    return "userid:" + Integer.toString(getId()) + "; name:" + getName();
  }
  
  private void addPathToRval(Set<DBGroup> seenGroups, List<DBGroup> position,
      List<List<DBGroup>> rval, Deque<List<DBGroup>> toProcess,
      final DBGroup group) {
    assert group.getName() != null;
    boolean added = seenGroups.add(group);
    if (!added) {
      return;
    }

    position.add(group);
    List<DBGroup> immutablePosition = Lists.newArrayList(position); 
    rval.add(immutablePosition);
    toProcess.push(immutablePosition);
    position.remove(position.size()-1);
  }

  @Deprecated
  public List<List<DBGroup> > ListAccessibleGroupsBF(Manager manager) throws SQLException {
    Set<DBGroup> seenGroups = Sets.newHashSet();
    return ListAccessibleGroupsBF(manager, seenGroups);
  }  

  /** BUG: traverses nested groups in the *opposite* order; See DBIdentityTest. */
  @Deprecated
  public List<List<DBGroup> > ListAccessibleGroupsBF(Manager manager, Set<DBGroup> seenGroups) 
      throws SQLException {
    
    List<DBGroup> position = Lists.newArrayList();
    //need to do a BFS
    //first, find all groups I belong to.
    List<List<DBGroup> > rval = Lists.newLinkedList();
    List<DBAcl> myacls = manager.aclDao.queryForFieldValuesArgs(
        ImmutableMap.of(
            DBAcl.MEMBER_IDENTITY_FIELD_NAME, (Object) getId()
            ));
    Deque<List<DBGroup> > toProcess = Queues.newArrayDeque();
    
    for (DBAcl acl : myacls) {
      assert acl.getGroupId().getName() == null;
      final DBGroup group = acl.loadGroup(manager.groupDao);
      addPathToRval(seenGroups, position, rval, toProcess, group);
    }

    while (!toProcess.isEmpty()) {
      position = toProcess.pop();
      final DBGroup processedGroup = position.get(position.size()-1);

      // get subgroups
      // TODO: Push filter for memberGroupId != null into the DB
      for (DBAcl acl : processedGroup.getAcls()) {
        final DBGroup newGroup = acl.getMemberGroupId();
        if (null == newGroup) {
          continue;
        }

        assert newGroup.getName() == null;
        manager.groupDao.refresh(newGroup);
        addPathToRval(seenGroups, position, rval, toProcess, newGroup);
      }
    }    
    return rval;
  }

  
  /**
   * @return the id
   */
  public int getId() {
    return id;
  }

  /**
   * @param id the id to set
   */
  public void setId(int id) {
    this.id = id;
  }
  

  public String getTwoFactorSecret() {
    return twoFactorSecret;
  }

  public boolean isTwoFactorAuthEnabled() {
    return getTwoFactorSecret() != null;
  }

  public void setBackup(int i, String newBackup){
    switch (i) {
      case 0: backup1 = newBackup;
              break;
      case 1: backup2 = newBackup;
              break;  
      case 2: backup3 = newBackup;
              break;
      case 3: backup4 = newBackup;
              break;
      case 4: backup5 = newBackup;
              break;
      case 5: backup6 = newBackup;
              break;
      case 6: backup7 = newBackup;
              break;
      case 7: backup8 = newBackup;
              break;
      case 8: backup9 = newBackup;
              break;
      case 9: backup10 = newBackup;
              break;
      default:throw new IllegalArgumentException("invalid index:" + i);
    }   
  }

  public int getNumAvailableBackups() {
    int numBackups = 0;
    for (int i = 0; i < CryptoForBackupCodes.NUM_BACKUP_CODES; ++i) {
      if (getBackup(i) != null) {
        ++numBackups;
      }
    }
    return numBackups;
  }
  
  public String getBackup(int i) {
    switch (i) {
      case 0: return backup1;
      case 1: return backup2;
      case 2: return backup3;
      case 3: return backup4;
      case 4: return backup5;
      case 5: return backup6;
      case 6: return backup7;
      case 7: return backup8;
      case 8: return backup9;
      case 9: return backup10;
      default:throw new IllegalArgumentException("invalid index:" + i);
    }
  }
  
  public long getLastAuthMs() {
    return lastAuthMs;
  }

  /**
   * @param twoFactorSecret the twoFactorSecret to set
   */
  public void setTwoFactorSecret(String twoFactorSecret) {
    this.twoFactorSecret = twoFactorSecret;
  }

  /**
   * @return the name
   */
  public String getName() {
    return name;
  }

  /**
   * @param name the name to set
   */
  public void setName(String name) {
    this.name = name;
    if (kUseEmailAsVerificationForTestsOnly) {
      this.verificationUid = name;
    }
  }

  /**
   * @return the publicKeyString
   */
  public String getPublicKeyString() {
    return publicKeyString;
  }

  /**
   * @param publicKeyString the publicKeyString to set
   */
  public void setPublicKeyString(String publicKeyString) {
    this.publicKeyString = publicKeyString;
  }

  /**
   * @return the encryptedPrivateKeyString
   */
  public String getEncryptedPrivateKeyString() {
    return encryptedPrivateKeyString;
  }

  /**
   * @param encryptedPrivateKeyString the encryptedPrivateKeyString to set
   */
  public void setEncryptedPrivateKeyString(String encryptedPrivateKeyString) {
    this.encryptedPrivateKeyString = encryptedPrivateKeyString;
  }

  /**
   * @return the keyserverSignatureOfIdAndKey
   */
  public String getKeyserverSignatureOfIdAndKey() {
    return keyserverSignatureOfIdAndKey;
  }

  /**
   * @param keyserverSignatureOfIdAndKey the keyserverSignatureOfIdAndKey to set
   */
  public void setKeyserverSignatureOfIdAndKey(String keyserverSignatureOfIdAndKey) {
    this.keyserverSignatureOfIdAndKey = keyserverSignatureOfIdAndKey;
  }

  /**
   * @return the verificationUid
   */
  public String getVerificationUid() {
    return verificationUid;
  }

  public DBIdentity() {
     verificationUid = new BigInteger(256, randomNumberGenerator).toString(Character.MAX_RADIX);
  }

  @Override
  public int hashCode() {
    if (name == null) { 
      throw new NullPointerException("incomplete object added");
    }
    return name.hashCode();
    //return Integer.valueOf(getId()).hashCode();
  }

  @Override
  public boolean equals(Object right) {
    if (right == null || right.getClass() != getClass()) {
      return false;
    }
    if (name == null || ((DBIdentity)right).name == null) {
      return id == ((DBIdentity)right).id; 
    } 
    return name.equals(((DBIdentity) right).name);
  }

  @Override
  public void addTransitiveGroupsAndUsers(Manager manager, Set<AccessLevelType> type,
      Set<DBIdentity> allUsers, Set<DBGroup> allGroups)  {
    allUsers.add(this);
  }
  
  public void setLastAuthMs(long t){
    this.lastAuthMs = t;
  }
  
  public void setEnabledTFAMs(long t) {
    this.enabledTFAMs = t;
  }
  
  public long getEnabledTFAMs() {
    return this.enabledTFAMs;
  }

  /**
   * @return the changePasswordOnNextLogin
   */
  public boolean getChangePasswordOnNextLogin() {
    return changePasswordOnNextLogin;
  }

  /**
   * @param changePasswordOnNextLogin the changePasswordOnNextLogin to set
   */
  public void setChangePasswordOnNextLogin(boolean changePasswordOnNextLogin) {
    this.changePasswordOnNextLogin = changePasswordOnNextLogin;
  }

  /**
   * @return the analyticsId
   */
  public String getAnalyticsId() {
    return analyticsId;
  }
  
  /**
   * @param analyticsId the analyticsId to set
   */
  public void setAnalyticsId(String analyticsId) {
    this.analyticsId = analyticsId;
  }

  /**
   * Returns this identity's highest access level for secret, considering all access paths.
   * @throws SQLException 
   */
  public AccessLevelType getHighestAccessLevel(Manager manager, DBServerVisibleSecret secret) throws SQLException {
    AccessLevelType highestAccessLevel = null;
    for (DBGroupSecret groupSecret : secret.getGroupSecrets()) {
      AccessLevelType level = getAccessLevelForGroup(manager, groupSecret.getGroup());
      if (level != null && (highestAccessLevel == null || level.isHigher(highestAccessLevel))) {
        highestAccessLevel = level;
      }
    }
    return highestAccessLevel;
  }

  /** Returns this identity's access level for group. 
   * @throws SQLException */
  public AccessLevelType getAccessLevelForGroup(Manager manager, DBGroup group) throws SQLException {
    // TODO: this allows org admins to access secrets. This should be restricted to admin-only operations
    Set<Integer> orgAdmins = EditGroup.getOrgAdminsForGroup(manager, group);
    if (orgAdmins.contains(getId())) {
      return DBAcl.AccessLevelType.ADMIN;
    }
    
    for (DBAcl acl : group.getAcls()) {
      Integer id = acl.getMemberIdentityIdAsInteger();
      if (id == null) {
        continue;
      }
      if (getId() == id.intValue()) {
        // this is an ACL for this identity: record the highest level
        return acl.getLevel();
      }
    }
    return null;
  }
  
  /** Returns the identity for the email address in userName, or null if not found. */
  public static DBIdentity getIdentityForUserName(Manager mgr, String userName) throws SQLException {
    Preconditions.checkNotNull(userName);
    
    Collection<DBIdentity> matches = getUsersFromNames(mgr, ImmutableList.of(userName));
    if (matches.size() == 0) {
      return null;
    } else {
      assert matches.size() == 1;
      return matches.iterator().next();
    }
  }

  public String getGuidCookie() {
    return guidCookie;
  }

  public void setGuidCookie(String guidCookie) {
    this.guidCookie = guidCookie;
  }

  public String getReferrer() {
    return referrer;
  }

  public void setReferrer(String referrer) {
    this.referrer = referrer;
  }

  public static void createUserInDb(Manager manager, DBIdentity iden) throws SQLException {
    manager.identityDao.create(iden);
    manager.identityDao.refresh(iden);
    manager.userNameDao.create(new DBUserName(iden));
  }
}
