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

import co.mitro.core.accesscontrol.AuthenticatedDB;
import co.mitro.core.server.Manager;
import co.mitro.core.server.data.DBAcl.AccessLevelType;

import com.google.common.base.Preconditions;
import com.j256.ormlite.dao.ForeignCollection;
import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.field.ForeignCollectionField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "groups")
public class DBGroup implements UserListInterface {
  public static final String ID_FIELD_NAME = "id";
  public static final String NAME_FIELD_NAME = "name";
  public static final String ACL_FIELD_NAME = "acl";
  public static final String GROUP_SECRET_FIELD_NAME = "group_secret";
  public static final String SCOPE_FIELD_NAME = "scope";
  public static final String TYPE_FIELD_NAME = "type";

  public DBGroup(String name) {
    this.name = name;
  }

  public DBGroup() {
  }

  public static Collection<DBGroup> getAllOrganizations(Manager manager) throws SQLException {
    return manager.groupDao.queryForEq(TYPE_FIELD_NAME, Type.TOP_LEVEL_ORGANIZATION);
  }
  
  public Collection<DBGroup> getAllOrgGroups(Manager manager) throws SQLException {
    assert (isTopLevelOrganization());
    // fetch all groups for scope from the DB.
    return AuthenticatedDB.getDirectlyAccessibleGroupsQuery(manager, 
            null, null, getId())
        .query();    
  }
  @DatabaseField(generatedId=true, columnName=ID_FIELD_NAME, canBeNull=false)
  private int id;

  @DatabaseField
  private boolean autoDelete = false;

  public static enum Type {
    /** A user's private group. */
    PRIVATE,

    /** Autodelete group used for an ad-hoc ACL. */
    AUTODELETE,

    /** A named team created by a user or organization. */
    NAMED_TEAM,

    /** The top level group representing an organization. */
    TOP_LEVEL_ORGANIZATION, 
    
  }

  @DatabaseField(columnName = TYPE_FIELD_NAME)
  private Type type;

  /**
   * @return the autoDelete
   */
  public boolean isAutoDelete() {
    return autoDelete;
  }

  /**
   * @param autoDelete
   *          the autoDelete to set
   */
  public void setAutoDelete(boolean autoDelete) {
    this.autoDelete = autoDelete;
  }

  @DatabaseField(columnName = NAME_FIELD_NAME, canBeNull = false)
  private String name;

  @DatabaseField(columnName = SCOPE_FIELD_NAME)
  private String scope;

  /**
   * @return the scope
   */
  public String getScope() {
    return scope;
  }

  /**
   * @param scope
   *          the scope to set
   */
  public void setScope(String scope) {
    this.scope = scope;
  }

  @DatabaseField(dataType = DataType.LONG_STRING, canBeNull=false)
  private String publicKeyString;

  @DatabaseField(dataType = DataType.LONG_STRING)
  private String signatureString;

  @ForeignCollectionField(columnName = ACL_FIELD_NAME)
  private ForeignCollection<DBAcl> acls;

  // ordered because GetGroup/EditGroup depends on the order of secrets
  @ForeignCollectionField(columnName = GROUP_SECRET_FIELD_NAME,
      orderColumnName=DBGroupSecret.SVS_ID_NAME)
  private ForeignCollection<DBGroupSecret> groupSecrets;

  @Override
  public int hashCode() {
    return Integer.valueOf(id).hashCode();
  }

  public boolean equals(Object right) {
    if (right == null || right.getClass() != getClass()) {
      return false;
    }
    return ((DBGroup) right).id == id;
  }

  public Type getType() {
    return type;
  }
  
  public void putDirectUsersIntoSet(Set<Integer> myUsers,
      Set<AccessLevelType> accessLevels) {
    for (DBAcl acl : getAcls()) {
      if ((null == accessLevels || accessLevels.contains(acl.getLevel()))
          && null != acl.getMemberIdentityId()) {
        myUsers.add(acl.getMemberIdentityId().getId());
      }
    }
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * co.mitro.core.server.data.UserList#addTransitiveGroupsAndUsers(co.mitro
   * .core.server.data.DBAcl.AccessLevelType, java.util.Set, java.util.Set)
   */
  @Override
  public void addTransitiveGroupsAndUsers(Manager manager,
      Set<DBAcl.AccessLevelType> types, Set<DBIdentity> allUsers,
      Set<DBGroup> allGroups) throws SQLException {
    boolean added = allGroups.add(this);
    if (!added) {
      return;
    }
    
    for (DBAcl acl : getAcls()) {
      acl.addTransitiveGroupsAndUsers(manager, types, allUsers, allGroups);
    }
  }

  // getters and setters:
  /**
   * @return the id
   */
  public int getId() {
    return id;
  }

  /**
   * @param id
   *          the id to set
   */
  public void setId(int id) {
    this.id = id;
  }

  /**
   * @return the name
   */
  public String getName() {
    return name;
  }

  /**
   * @param name
   *          the name to set
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * @return the publicKeyString
   */
  public String getPublicKeyString() {
    return publicKeyString;
  }

  /**
   * @param publicKeyString
   *          the publicKeyString to set
   */
  public void setPublicKeyString(String publicKeyString) {
    this.publicKeyString = publicKeyString;
  }

  /**
   * @return the signatureString
   */
  public String getSignatureString() {
    return signatureString;
  }

  /**
   * @param signatureString
   *          the signatureString to set
   */
  public void setSignatureString(String signatureString) {
    this.signatureString = signatureString;
  }

  /**
   * @return the acls
   */
  public ForeignCollection<DBAcl> getAcls() {
    return acls;
  }

  /**
   * @param acls
   *          the acls to set
   */
  public void setAcls(ForeignCollection<DBAcl> acls) {
    this.acls = acls;
  }

  /**
   * @return the groupSecrets
   */
  public ForeignCollection<DBGroupSecret> getGroupSecrets() {
    return groupSecrets;
  }

  /**
   * @param groupSecrets
   *          the groupSecrets to set
   */
  public void setGroupSecrets(ForeignCollection<DBGroupSecret> groupSecrets) {
    this.groupSecrets = groupSecrets;
  }

  /** Marks this group as a private group. TODO: Only use type(). */
  public void setPrivateGroup() {
    assert type == null;
    type = Type.PRIVATE;
    name = "";
    autoDelete = false;
  }

  public void setType(Type type) {
    Preconditions.checkNotNull(type);
    this.type = type;
  }

  public static boolean isPrivateUserGroup(Type type, boolean autoDelete, String name) {
    if (type != null) {
      return Type.PRIVATE.equals(type);
     }

     // TODO: Remove once .type is mandatory non-null
     if (name.equals("") && !autoDelete) {
       return true;
     }
     return false;
  }
  
  /** Returns true if this is a user's private group (personal or organization). */
  public boolean isPrivateUserGroup() {
    return isPrivateUserGroup(this.type, this.autoDelete, this.name);
  }

  public boolean isTopLevelOrganization() {
    return Type.TOP_LEVEL_ORGANIZATION.equals(type);
  }
  
  public String toString() {
    return this.name + " (" + this.id +") " + this.getType();
  }
};
