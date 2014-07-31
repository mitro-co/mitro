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
import java.util.HashMap;
import java.util.Set;

import co.mitro.core.exceptions.MitroServletException;
import co.mitro.core.server.Manager;
import co.mitro.core.server.data.RPC.ListMySecretsAndGroupKeysResponse.GroupInfo;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;


@DatabaseTable(tableName="group_secret")
public class DBGroupSecret {
  @DatabaseField(generatedId=true)
  private int id;
  
  @DatabaseField
  private long versionId;

  // TODO: Remove foreignAutoRefresh: causes unneeded queries in ListMySecretsAndGroups
  static public final String GROUP_ID_NAME = "group_id";
  @DatabaseField(foreign = true, foreignAutoRefresh = true, columnName = GROUP_ID_NAME, canBeNull=false)
  private DBGroup group;

  // TODO: Change to underscores?
  // TODO: Remove foreignAutoRefresh: causes unneeded queries in ListMySecretsAndGroups
  static public final String SVS_ID_NAME = "serverVisibleSecret_id";
  @DatabaseField(foreign = true, foreignAutoRefresh = true, columnName = SVS_ID_NAME, canBeNull=false)
  private DBServerVisibleSecret serverVisibleSecret;

  // TODO: should this be byte[]?
  @DatabaseField(dataType=DataType.LONG_STRING, canBeNull=false)
  private String clientVisibleDataEncrypted;
  
  @DatabaseField(dataType=DataType.LONG_STRING, canBeNull=false)
  private String criticalDataEncrypted;
  
  @DatabaseField(dataType=DataType.LONG_STRING)
  private String signatureOfGroupIdAndSecretId; 
  
  public enum CRITICAL {
    INCLUDE_CRITICAL_DATA("true"),
    NO_CRITICAL_DATA("false"), 
    INCLUDE_CRITICAL_DATA_FOR_DISPLAY("display");

    private String clientString;
    
    private CRITICAL(String clientString) {
      this.clientString = clientString;
    }
    public String getClientString() {
      return clientString;
    }
    
    public static final CRITICAL fromClientString(String includeCriticalData) {
      for (CRITICAL c : values()) {
        if (c.getClientString().equals(includeCriticalData)) {
          return c;
        }
      }
      throw new IllegalArgumentException(includeCriticalData);
    }
  }

  /**
   * Adds all data about this to the RPC message secret, including encrypted data and ACLs.
   * Includes the critical data if critical ==  INCLUDE_CRITICAL_DATA.
   */
  public void addToRpcSecret(Manager manager, RPC.Secret secret, CRITICAL critical, String userId)
      throws MitroServletException, SQLException {
    DBAudit.ACTION type = critical == CRITICAL.INCLUDE_CRITICAL_DATA ? DBAudit.ACTION.GET_SECRET_WITH_CRITICAL : DBAudit.ACTION.GET_SECRET_WITHOUT_CRITICAL;
    secret.encryptedClientData = getClientVisibleDataEncrypted();
    Set<Integer> groups = Sets.newHashSet();
    Set<Integer> hiddenGroups = Sets.newHashSet();
    Set<String> users = Sets.newTreeSet();
    secret.groupIdToPublicKeyMap = new HashMap<>();
    secret.groupNames = new HashMap<>();
    secret.groupMap = new HashMap<>();
    // TODO: This performs many loads of ServerVisibleSecret, GroupSecret, and Group
    // can we avoid these? Do fewer? It makes ListMySecretsAndGroups slow
    final DBServerVisibleSecret svs = getServerVisibleSecret();
    secret.king = svs.getKing() == null ? null : svs.getKing().getName();
    secret.isViewable = svs.isViewable();

    for (DBGroupSecret dbGroupSecret : svs.getGroupSecrets()) {
      DBGroup group = dbGroupSecret.getGroup();
      secret.groupIdToPublicKeyMap.put(group.getId(), group.getPublicKeyString());
      if (dbGroupSecret.getGroup().isAutoDelete()) {
        hiddenGroups.add(dbGroupSecret.getGroup().getId());
        // in this case we should add the users
        for (DBAcl acl : dbGroupSecret.getGroup().getAcls()) {
          if (null != acl.getMemberIdentityId()) {
            DBIdentity identity = acl.loadMemberIdentity(manager.identityDao);
            users.add(identity.getName());
            continue;
          } 
        }
        
      } else {
        groups.add(group.getId());
        // add group names so we can display groups even if we don't have access to them
        // TODO: Should we only return this from GetSecret?
        
        secret.groupNames.put(group.getId(), group.getName());

        GroupInfo groupInfo = new GroupInfo();
        groupInfo.groupId = group.getId();
        groupInfo.name = group.getName();
        groupInfo.autoDelete = group.isAutoDelete();
        groupInfo.isTopLevelOrg = group.isTopLevelOrganization();
        secret.groupMap.put(group.getId(), groupInfo);
        
        if (null == secret.owningOrgId && groupInfo.isTopLevelOrg) {
          secret.owningOrgId = groupInfo.groupId;
        }
        groupInfo.isOrgPrivateGroup = group.isPrivateUserGroup() && (groupInfo.owningOrgId != null);
        groupInfo.isNonOrgPrivateGroup = group.isPrivateUserGroup() && (groupInfo.owningOrgId == null);
      }
    }
    secret.hiddenGroups = Lists.newLinkedList(hiddenGroups);
    secret.users = Lists.newLinkedList(users);
    secret.groups = Lists.newLinkedList(groups);
    manager.addAuditLog(type, null, null, null, this.getServerVisibleSecret(), null);
    if (CRITICAL.INCLUDE_CRITICAL_DATA_FOR_DISPLAY == critical || CRITICAL.INCLUDE_CRITICAL_DATA == critical) {
      secret.encryptedCriticalData = getCriticalDataEncrypted();
    } else {
      secret.encryptedCriticalData = null;
    }
    secret.secretId = getServerVisibleSecret().getId();
    // TODO: Figure out how to get icons and titles for sites that doesn't destroy privacy
//    secret.icons = Manager.getOldJsonData().getIcons(secret.hostname);
//    secret.title = Manager.getOldJsonData().getTitle(secret.hostname);
  }

  public String getClientVisibleDataEncrypted() {
    return clientVisibleDataEncrypted;
  }

  public void setClientVisibleDataEncrypted(String clientVisibleDataEncrypted) {
    this.clientVisibleDataEncrypted = clientVisibleDataEncrypted;
  }

  public String getCriticalDataEncrypted() {
    return criticalDataEncrypted;
  }

  public void setCriticalDataEncrypted(String criticalDataEncrypted) {
    this.criticalDataEncrypted = criticalDataEncrypted;
  }

  public String getSignatureOfGroupIdAndSecretId() {
    return signatureOfGroupIdAndSecretId;
  }

  public void setSignatureOfGroupIdAndSecretId(
      String signatureOfGroupIdAndSecretId) {
    this.signatureOfGroupIdAndSecretId = signatureOfGroupIdAndSecretId;
  }

  public int getId() {
    return id;
  }

  public void setId(int id) {
    this.id = id;
  }

  public long getVersionId() {
    return versionId;
  }

  public void setVersionId(long versionId) {
    this.versionId = versionId;
  }

  public DBGroup getGroup() {
    return group;
  }

  public void setGroup(DBGroup group) {
    this.group = group;
  }

  public DBServerVisibleSecret getServerVisibleSecret() {
    return serverVisibleSecret;
  }

  public void setServerVisibleSecret(DBServerVisibleSecret serverVisibleSecret) {
    this.serverVisibleSecret = serverVisibleSecret;
  }
}
