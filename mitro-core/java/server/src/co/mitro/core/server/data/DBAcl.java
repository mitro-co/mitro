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
import java.sql.SQLException;
import java.util.Collection;
import java.util.Set;

import co.mitro.core.exceptions.MitroServletException;
import co.mitro.core.server.Manager;

import com.google.common.collect.Sets;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;


@DatabaseTable(tableName="acl")
public class DBAcl implements UserListInterface {
  public static void getMemberGroupsAndIdentities(
      Collection<? extends DBAcl> acls,
      Set<Integer> groupIds,
      Set<Integer> userIds) throws MitroServletException {
    for (DBAcl a : acls) {
      // Warning: these may not be "complete" objects, but they will have id set
      DBGroup g = a.memberGroup;
      DBIdentity i = a.memberIdentity;
      if (null != g) {
        boolean added = groupIds.add(g.getId());
        if (!added) {
          throw new MitroServletException("duplicates in ACL");
        }
      } else if (null != i) {
        boolean added = userIds.add(i.getId());
        if (!added) {
          throw new MitroServletException("duplicates in ACL");
        }
      } else {
        assert (false);
      }
    }
  }
      
      
  public static class CyclicGroupError extends Exception {
    private static final long serialVersionUID = 1L;
  };

  private void checkCycles() throws CyclicGroupError {
    // TODO: this should ensure that nested groups do not contain cycles.
  }

  public static final String GROUP_SECRET_FIELD_NAME = "group_secret";
  public static final String MEMBER_IDENTITY_FIELD_NAME = "member_identity";
  public static final String MEMBER_GROUP_FIELD_NAME = "group_identity";

  public static enum AccessLevelType {
    ADMIN, READONLY, MODIFY_SECRETS_BUT_NOT_MEMBERSHIP;

    /**
     * Returns true if this access level is higher (more permissive) than other. 
     */
    public boolean isHigher(AccessLevelType other) {
      if (other == null) {
        throw new NullPointerException("other AccessLevelType cannot be null");
      }

      switch (this) {
      case ADMIN:
        return other == MODIFY_SECRETS_BUT_NOT_MEMBERSHIP || other == READONLY;

      case MODIFY_SECRETS_BUT_NOT_MEMBERSHIP:
        return other == READONLY;

      case READONLY:
        return false;

      default:
        throw new IllegalArgumentException("Bug: unsupported access level: " + this);
      }
    }

    /** Returns true if this access level can edit secrets. */
    public boolean canEditSecret() {
      return modifyGroupSecretsAccess().contains(this);
    }
  };
  
  public static Set<AccessLevelType> allAccessTypes() {
    return Sets.immutableEnumSet(AccessLevelType.ADMIN, AccessLevelType.READONLY, AccessLevelType.MODIFY_SECRETS_BUT_NOT_MEMBERSHIP);
  }

  public static Set<AccessLevelType> adminAccess() {
    return Sets.immutableEnumSet(AccessLevelType.ADMIN);
  }
  
  public static Set<AccessLevelType> modifyGroupSecretsAccess() {
    return Sets.immutableEnumSet(AccessLevelType.ADMIN, AccessLevelType.MODIFY_SECRETS_BUT_NOT_MEMBERSHIP);
  }
  
  @DatabaseField(generatedId=true)
  private int id;
  
  @DatabaseField(canBeNull=false)
  private AccessLevelType level;

  @DatabaseField(dataType=DataType.LONG_STRING, canBeNull=false)
  private String groupKeyEncryptedForMe;
  
  /**
   * @return the groupKeyEncryptedForMe
   */
  public String getGroupKeyEncryptedForMe() {
    return groupKeyEncryptedForMe;
  }

  /**
   * @param groupKeyEncryptedForMe the groupKeyEncryptedForMe to set
   */
  public void setGroupKeyEncryptedForMe(String groupKeyEncryptedForMe) {
    this.groupKeyEncryptedForMe = groupKeyEncryptedForMe;
  }

  public static final String GROUP_ID_FIELD_NAME = "group_id";
  // TODO: Add index=true? Currently the production table is too small so Postgres won't use it.
  // would eliminate serializability conflicts between concurrent Begin/AddGroup/End operations.
  @DatabaseField(foreign=true, columnName=GROUP_ID_FIELD_NAME, canBeNull=false)
  private DBGroup group;

  @DatabaseField(foreign = true, columnName = MEMBER_GROUP_FIELD_NAME, canBeNull=true)
  private DBGroup memberGroup;

  @DatabaseField(foreign = true, columnName = MEMBER_IDENTITY_FIELD_NAME, canBeNull=true)
  private DBIdentity memberIdentity;

  /* (non-Javadoc)
   * @see co.mitro.core.server.data.UserList#addTransitiveGroupsAndUsers(co.mitro.core.server.data.DBAcl.AccessLevelType, java.util.Set, java.util.Set)
   */
  @Override
  public void addTransitiveGroupsAndUsers(
      Manager manager,
      Set<DBAcl.AccessLevelType> types,
      Set<DBIdentity> allUsers,
      Set<DBGroup> allGroups) throws SQLException {
    if (!types.contains(this.getLevel())) {
      return;
    }
    if (null != memberGroup) {
      // memberGroup is a member of group; recurse into group
      loadGroup(manager.groupDao);
      assert group.getName() != null;
      group.addTransitiveGroupsAndUsers(manager, types, allUsers, allGroups);
    } else {
      loadMemberIdentity(manager.identityDao);
      assert memberIdentity.getName() != null;
      memberIdentity.addTransitiveGroupsAndUsers(manager, types, allUsers, allGroups);
    }
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

  /**
   * @return the level
   */
  public AccessLevelType getLevel() {
    return level;
  }

  /**
   * @param level the level to set
   */
  public void setLevel(AccessLevelType level) {
    this.level = level;
  }

  /** Refreshes the group object from the database and returns it. */
  public DBGroup loadGroup(Dao<DBGroup, Integer> dao) throws SQLException {
    dao.refresh(group);
    return group;
  }

  /** Returns an incomplete DBGroup with only the id set. */
  public DBGroup getGroupId() {
    return group;
  }

  /**
   * @param group the group to set
   * @throws CyclicGroupError 
   */
  public void setGroup(DBGroup group) throws CyclicGroupError {
    this.group = group;
    checkCycles();
  }

  public DBIdentity loadMemberIdentity(Dao<DBIdentity, Integer> dao) throws SQLException {
    dao.refresh(memberIdentity);
    return memberIdentity;
  }

  /**
   * Returns incomplete DBIdentity of a member group. Use getMemberIdentityIdAsInteger() instead.  
   */
  public DBIdentity getMemberIdentityId() {
    return memberIdentity;
  }
  
  public Integer getMemberIdentityIdAsInteger() {
    return memberIdentity == null ? null : memberIdentity.getId();
  }

  /**
   * @param memberIdentity the memberIdentity to set
   */
  public void setMemberIdentity(DBIdentity memberIdentity) {
    this.memberIdentity = memberIdentity;
  }
  
  /**
   * returns the fully-loaded group.
   */
  public DBGroup loadMemberGroup(Dao<DBGroup, Integer> dao) throws SQLException {
    dao.refresh(memberGroup);
    return memberGroup;
  }

  /**
   * Returns incomplete DBGroup of a member group. Use getMemberGroupIdAsInteger() instead.  
   */
  public DBGroup getMemberGroupId() {
    return memberGroup;
  }
  
  public Integer getMemberGroupIdAsInteger() {
    return memberGroup == null ? null : memberGroup.getId();
  }

  /**
   * @param memberGroup the memberGroup to set
   * @throws CyclicGroupError 
   */
  public void setMemberGroup(DBGroup memberGroup) throws CyclicGroupError {
    DBGroup oldGroup = this.memberGroup;
    this.memberGroup = memberGroup;
    try {
      checkCycles();
    } catch (CyclicGroupError e) {
      // I'm not sure if this is necessary, but let's leave this object in a 
      // sane state
      this.memberGroup = oldGroup;
      throw e;
    }
  }

};
