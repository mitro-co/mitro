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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import co.mitro.core.server.Manager;
import co.mitro.core.server.data.RPC.AuditAction;
import co.mitro.core.server.data.RPC.Secret;
import co.mitro.core.servlets.ListMySecretsAndGroupKeys.AdminAccess;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.j256.ormlite.dao.ForeignCollection;
import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.field.ForeignCollectionField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "secrets")
public class DBServerVisibleSecret {
  // for QueryBuilder to be able to find the fields
  // TODO: Remove entirely? Now effectively unused
  public static final String HOSTNAME_FIELD_NAME = "hostname";
  public static final String GROUPS_FIELD_NAME = "groups_secrets";
  public static final String KING_FIELD_NAME = "king";
  public static final String VIEWABLE_FIELD_NAME = "is_viewable";

  public static void fillRecentAuditActions(Manager manager, Collection<? extends Secret> secrets) throws SQLException {
    Map<Integer, Secret> secretIdToSecret = Maps.newTreeMap();
    
    for (Secret s : secrets) {
      secretIdToSecret.put(s.secretId, s);
    }
    if (secretIdToSecret.isEmpty()) {
      return;
    }

    String getRecentActions = 
        "SELECT target_sv_secret, MAX(\"timestampMs\"), action, identity.name " +
        "FROM audit " +
        "JOIN identity ON (uid = identity.id) " +
        "WHERE target_sv_secret IN (" + Joiner.on(',').join(secretIdToSecret.keySet()) +") " +
        "      AND action IN ('GET_SECRET_WITH_CRITICAL', 'ADD_SECRET') " + 
        "GROUP BY target_sv_secret, action, identity.name " +
        "ORDER BY max(\"timestampMs\") DESC";
    
    // A query that looks more like this should be faster and not return duplicates, but for some reason takes way longer:
    /* select t1.* from audit as t1 
           LEFT OUTER JOIN audit as t2 ON (t1.target_sv_secret = t2.target_sv_secret) 
                    and ((t1."timestampMs" < t2."timestampMs")) 
           WHERE t2.target_sv_secret IS NULL 
                 AND t1.target_sv_secret in (188,338);
     */
    // Alternatively we should use DISTINCT ON() but that is unsupported on H2.
    List<String[]> actionsResults = Lists.newArrayList(manager.identityDao.queryRaw(getRecentActions));
    resultLoop:
    for (String[] cols : actionsResults) {
      int secretId =  Integer.parseInt(cols[0], 10);
      final Secret secret = secretIdToSecret.get(secretId);
      final DBAudit.ACTION action = DBAudit.ACTION.valueOf(cols[2]);
      switch (action) {
        case GET_SECRET_WITH_CRITICAL:
          secret.lastAccessed = (null == secret.lastAccessed) ? newAuditAction(cols) : secret.lastAccessed;
          if (secret.lastModified != null) {
            // This currently doesn't do anything because add secret is always the oldest operation
            // but when we add EDIT_SECRET tracking this will be a pretty big optimization win.
            break resultLoop;
          }
          break;
        case ADD_SECRET:
          // TODO: we should really be using EDIT_SECRET here as well, but those
          // data are not properly set by the actions.
          secret.lastModified = (null == secret.lastModified) ? newAuditAction(cols) : secret.lastModified;
          if (secret.lastAccessed != null) {
            break resultLoop;
          }
          break;
        default:
          assert (false) : "invalid result from query";
      }
    }
  }

  protected static AuditAction newAuditAction(String[] cols) {
    AuditAction rval = new AuditAction();
    long timestampMs = Long.parseLong(cols[1], 10);
    rval.timestampSec = (int)TimeUnit.MILLISECONDS.toSeconds(timestampMs);
    rval.userName = cols[3];
    return rval;
  }
  
  
  @DatabaseField(generatedId = true)
  private int id;

  @DatabaseField(columnName = HOSTNAME_FIELD_NAME, canBeNull = false, dataType=DataType.LONG_STRING)
  private String hostname;

  @DatabaseField(canBeNull=true, foreign=true, columnName=KING_FIELD_NAME)
  private DBIdentity king;
  
  @DatabaseField(canBeNull=false, columnName=VIEWABLE_FIELD_NAME)
  private boolean isViewable=true;
  
  @ForeignCollectionField(columnName=GROUPS_FIELD_NAME, eager=true)
  private ForeignCollection<DBGroupSecret> groupSecrets;

  public DBIdentity getKing() {
    return king;
  }

  public void setKing(DBIdentity king) {
    this.king = king;
  }

  public boolean isViewable() {
    return isViewable;
  }

  public void setViewable(boolean isViewable) {
    this.isViewable = isViewable;
  }

  
  public static class InvariantException extends Exception {
    private static final long serialVersionUID = 1L;

    public InvariantException(String obj) {
      super(obj);
    }
  };
  
  public Set<Integer> getAllUserIdsWithAccess(Manager manager, Collection<DBAcl.AccessLevelType> accessLevels, AdminAccess useOrgAdminPrivileges) throws SQLException {
    Set<Integer> rval = Sets.newHashSet();
    assert (this.getId() > 0);
    boolean queryGroups = (useOrgAdminPrivileges == AdminAccess.IGNORE_ACCESS_VIA_TOPLEVEL_GROUPS);
    String getUserIds = 
        "SELECT identity.id, acl.level"
        + " FROM identity, group_secret, acl " + (queryGroups ? ", groups " : "") 
        + " WHERE acl.member_identity = identity.id " 
        + "      AND group_secret.\"serverVisibleSecret_id\" = " + this.getId()
        + "      AND acl.group_id = group_secret.group_id ";
    if (queryGroups) {
      getUserIds += 
          "      AND groups.id = acl.group_id "
        + "      AND (groups.type IS NULL OR groups.type != 'TOP_LEVEL_ORGANIZATION') ";
    }
    
    List<String[]> userIdsResults = Lists.newArrayList(manager.svsDao.queryRaw(getUserIds));
    for (String[] cols : userIdsResults) {
      DBAcl.AccessLevelType level = DBAcl.AccessLevelType.valueOf(cols[1]);
      if (accessLevels.contains(level)) {
        rval.add(Integer.parseInt(cols[0], 10));
      }
    }
    return rval;
  }
  
  /**
   * Verifies that this secret has at least one identity that has administrative privileges.
   */
  public void verifyHasAdministrator(Manager manager) throws InvariantException, SQLException {
    for (DBGroupSecret gs : getGroupSecrets()) {
      Set<DBIdentity> theseUsers = Sets.newHashSet();
      Set<DBGroup> theseGroups = Sets.newHashSet();
      gs.getGroup().addTransitiveGroupsAndUsers(
          manager, DBAcl.modifyGroupSecretsAccess(),
          theseUsers, theseGroups);
      if (!theseUsers.isEmpty()) {
        return;
      }
    }

    throw new InvariantException("secret " + getId() + " is orphaned");
  }

  public ForeignCollection<DBGroupSecret> getGroupSecrets() {
    return groupSecrets;
  }

  public void setGroupSecrets(ForeignCollection<DBGroupSecret> groupSecrets) {
    this.groupSecrets = groupSecrets;
  }

  public DBServerVisibleSecret() {
    this.hostname="";
  }

  public void setId(int id) {
    this.id = id;
  }

  public int getId() {
    return id;
  }
}
