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
package co.mitro.core.server.data;
import co.mitro.core.server.data.RPC.GetPendingGroupApprovalsResponse.PendingGroupApproval;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;


@DatabaseTable(tableName="pending_group")
public class DBPendingGroup {
  /** Required by OrmLite to load objects from DB. */
  @Deprecated
  public DBPendingGroup() {}

  public DBPendingGroup(DBIdentity requestor, 
      String groupName, String scope, String memberListJson, String signature,
      DBGroup owningOrg) {
    assert (null != owningOrg);
    assert (null != requestor);
    this.groupName = groupName;
    this.scope = scope;
    this.owningOrg = owningOrg;
    this.creator = requestor;
    this.memberEmailsJsonString = memberListJson;
    this.jsonSignature = signature;
    this.timestampUtcSec = (int)(System.currentTimeMillis()/1000.0);
  }

  public static final String GROUP_NAME_FIELD_NAME = "group_name";
  public static final String CREATOR_NAME="creator";
  public static final String OWNING_ORG="owning_org";
  public static final String SCOPE_NAME="scope";
  public static final String SYNC_NONCE = "sync_nonce";

  /** scope when synchronizing "no groups" from upstream. */
  // TODO: probably should set the source for a set of users/groups, not per group
  // then the "no groups" source would have a value (the upstream source!)
  public static final String SCOPE_NO_GROUPS = "nogroups:";

  @DatabaseField(generatedId=true)
  private int id;
  
  @DatabaseField(foreign = true,columnName=CREATOR_NAME, canBeNull=false)
  private DBIdentity creator;
  
  @DatabaseField(columnName=GROUP_NAME_FIELD_NAME, canBeNull=false)
  private String groupName;

  @DatabaseField(columnName=SCOPE_NAME, canBeNull=false)
  private String scope;
  
  @DatabaseField(dataType=DataType.LONG_STRING)
  private String jsonSignature;
  
  @DatabaseField(dataType=DataType.LONG_STRING, canBeNull=false)
  private String memberEmailsJsonString;

  @DatabaseField(foreign = true, columnName=OWNING_ORG)
  private DBGroup owningOrg;
  
  /** This is a GUID to prevent race conditions between user view & sync actions */
  @DatabaseField(columnName=SYNC_NONCE, canBeNull=false)
  private String syncNonce;
  
  @DatabaseField
  Integer timestampUtcSec;

  public int getId() {
    return id;
  }

  public DBIdentity getCreator() {
    return creator;
  }

  public String getGroupName() {
    return groupName;
  }

  public String getScope() {
    return scope;
  }

  public String getJsonSignature() {
    return jsonSignature;
  }

  public String getMemberEmailsJsonString() {
    return memberEmailsJsonString;
  }

  public Integer getTimestampUtcSec() {
    return timestampUtcSec;
  }

  public void fillPendingGroup(PendingGroupApproval pg) {
    pg.groupName = getGroupName();
    pg.scope = getScope();
    pg.memberListJson = getMemberEmailsJsonString();
    pg.signature = getJsonSignature();
  }

  public DBGroup getOwningOrg() {
    return owningOrg;
  }

  public void setOwningOrg(DBGroup owningOrg) {
    this.owningOrg = owningOrg;
  }

  public String getSyncNonce() {
    return syncNonce;
  }

  public void setSyncNonce(String syncNonce) {
    this.syncNonce = syncNonce;
  }
};
