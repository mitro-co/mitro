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

import java.util.Date;

import co.mitro.analysis.AuditLogProcessor.ActionType;

import com.google.common.base.Preconditions;
import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "processedaudit")
public class DBProcessedAudit {
  public static final String AFFECTED_USER_FIELD_NAME = "affected_user";
  public static final String TRANSACTION_ID_FIELD_NAME = "transaction_id";
  public static final String ACTOR_FIELD_NAME = "actor";
  public static final String ACTION_FIELD_NAME = "action";
  public static final String TIMESTAMP_FIELD_NAME = "timestamp_ms";
  public static final String AFFECTED_SECRET_FIELD_NAME = "affected_secret";
  public static final String AFFECTED_GROUP_FIELD_NAME = "affected_group";
  public static final String SOURCE_IP_FIELD_NAME = "source_ip";
  public static final String DEVICE_ID_NAME = "device_id";

  public int getId() {
    return id;
  }

  public DBIdentity getActor() {
    return actor;
  }

  public DBIdentity getAffectedUser() {
    return affectedUser;
  }

  public Integer getAffectedSecret() {
    return affectedSecret;
  }

  public DBGroup getAffectedGroup() {
    return affectedGroup;
  }

  public ActionType getAction() {
    return action;
  }

  public long getTimestampMs() {
    return timestampMs;
  }

  public String getSourceIp() {
    return sourceIp;
  }

  @DatabaseField(generatedId = true)
  private int id;
  
  @DatabaseField(columnName=ACTOR_FIELD_NAME, foreign=true, canBeNull=false, index=true)
  private DBIdentity actor;
  
  @DatabaseField(columnName="actor_name", dataType=DataType.LONG_STRING)
  private String actorName;
  
  @DatabaseField(columnName=AFFECTED_USER_FIELD_NAME, foreign=true, canBeNull=true, index=true)
  private DBIdentity affectedUser;
  
  @DatabaseField(columnName="affected_user_name", dataType=DataType.LONG_STRING)
  private String affectedUserName;
  
  @DatabaseField(columnName=AFFECTED_SECRET_FIELD_NAME, canBeNull=true, index=true)
  private Integer affectedSecret;

  @DatabaseField(columnName=AFFECTED_GROUP_FIELD_NAME, foreign=true, canBeNull=true, index=true)
  private DBGroup affectedGroup;
  
  @DatabaseField(columnName=ACTION_FIELD_NAME, canBeNull=false)
  private ActionType action;
  
  @DatabaseField(columnName=TIMESTAMP_FIELD_NAME)
  private long timestampMs;
  
  @DatabaseField(columnName=TRANSACTION_ID_FIELD_NAME, dataType=DataType.LONG_STRING)
  private String transactionId;

  @DatabaseField(columnName = SOURCE_IP_FIELD_NAME)
  private String sourceIp;
  
  @DatabaseField(columnName = DEVICE_ID_NAME)
  private String deviceId;

  @Override
  public String toString() {
    // DateFormat is not thread-safe and cannot be cached
    String timeString = DBEmailQueue.getUtcIsoFormat().format(new Date(timestampMs));
    return actorName + " " + action.toString() + " " + affectedUserName + " @ " + timeString;
  }

  /** Required by ORMLite but creates an uninitialized object. */
  @Deprecated
  DBProcessedAudit() {}

  public enum GrantOrRevoke {GRANT, REVOKE};
  public DBProcessedAudit(DBIdentity actor, DBIdentity affectedUser, int secretId, GrantOrRevoke action, String txnId, String sourceIp, String deviceId) {
    this.action = (action == GrantOrRevoke.GRANT) ? ActionType.GRANTED_ACCESS_TO : ActionType.REVOKED_ACCESS_TO;
    this.actor = actor;
    this.affectedUser = affectedUser;
    this.actorName = actor.getName();
    this.affectedUserName = affectedUser.getName();
    this.affectedSecret = secretId;
    this.timestampMs = System.currentTimeMillis();
    this.transactionId = txnId;
    this.sourceIp = sourceIp;
    this.deviceId = deviceId;
  }

  public DBProcessedAudit(ActionType action, DBAudit audit) {
    this.action = Preconditions.checkNotNull(action);
    this.actor = audit.getUser();
    this.affectedUser = audit.getTargetUser();
    this.sourceIp = audit.getSourceIp();
    this.deviceId = audit.getDeviceId();
    
    // a bit of a hack, but when A invites B, and we have an action type of 
    // INVITED_BY, we need to swap actor and object.
    if (action == ActionType.INVITED_BY_USER) {
      final DBIdentity tmp = actor;
      actor = affectedUser;
      affectedUser = tmp;
    }

    if (affectedUser != null) {
      this.affectedUserName = Preconditions.checkNotNull(this.affectedUser.getName());
    }

    this.actorName = Preconditions.checkNotNull(audit.getUser().getName());
    this.timestampMs = audit.getTimestampMs();
    this.transactionId = audit.getTransactionId();
  }

  public void setAffectedSecret(DBServerVisibleSecret affectedSecret) {
    this.affectedSecret = Preconditions.checkNotNull(affectedSecret).getId();
  }
  public void setAffectedSecret(int secretId) {
    affectedSecret = secretId;
  }

  public void setAffectedGroup(DBGroup affectedGroup) {
    this.affectedGroup = affectedGroup;
  }

  public String getTransactionId() {
    return transactionId;
  }

  public String getActorName() {
    return actorName;
  }

  public String getAffectedUserName() {
    // TODO Auto-generated method stub
    return affectedUserName;
  }
}
