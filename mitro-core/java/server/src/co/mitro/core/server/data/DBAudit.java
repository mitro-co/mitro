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
import java.util.List;
import java.util.Set;

import co.mitro.core.server.Manager;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "audit")
public class DBAudit {
  public static final String USER_FIELD_NAME = "uid";
  public static final String TARGET_USER_FIELD_NAME = "target_uid";
  public static final String TARGET_GROUP_FIELD_NAME = "target_group";
  public static final String TARGET_SV_SECRET_FIELD_NAME = "target_sv_secret";
  public static final String SOURCE_IP_FIELD_NAME = "source_ip";
  public static final String SERVER_IP_FIELD_NAME = "server_ip";
  public static final String TRANSACTION_ID_FIELD_NAME = "transaction_id";
  public static final String ACTION_FIELD_NAME = "action";
  public static final String TIMESTAMP_FIELD_NAME = "timestampMs";
  public static final String DEVICE_ID_NAME = "device_id";
  public static final String OPERATION_NAME = "operation";

  public static enum ACTION {
       ADD_SECRET,
       ADD_GROUP,
       AUTHORIZE_NEW_DEVICE,
       TFA_SUCCESS,
       TFA_FAIL,
       MODIFY_GROUP,
       REMOVE_SECRET,
       EDIT_SECRET,
       EDIT_SECRET_CONTENT,
       LOGIN,
       GET_GROUP,
       GET_SECRET_WITH_CRITICAL,
       GET_SECRET_WITHOUT_CRITICAL,
       LIST_SECRETS,
       CREATE_IDENTITY,
       EDIT_ENCRYPTED_PRIVATE_KEY,
       GET_PRIVATE_KEY,
       INVITE_NEW_USER,
       ADD_PENDING_GROUP_SYNC,
       GET_PENDING_GROUP_SYNC,
       REMOVE_PENDING_GROUP_SYNC,
       TRANSACTION_BEGIN,
       TRANSACTION_COMMIT,
       TRANSACTION_ROLLBACK,
       TRANSACTION_TERMINATE,
       TRANSACTION_IDLE_AND_LOCKED,
       CREATE_ORGANIZATION,
       MUTATE_ORGANIZATION,
  };
  public static final Set<ACTION> UNCOMMITTED_TRANSACTIONS = Sets.immutableEnumSet(ACTION.TRANSACTION_ROLLBACK, ACTION.TRANSACTION_TERMINATE);
  public static final Set<ACTION> TRANSACTION_ACTIONS = Sets.immutableEnumSet(ACTION.TRANSACTION_BEGIN, 
      ACTION.TRANSACTION_COMMIT, ACTION.TRANSACTION_IDLE_AND_LOCKED, ACTION.TRANSACTION_ROLLBACK, 
      ACTION.TRANSACTION_TERMINATE);

  public static final ImmutableSet<ACTION> SECRET_ACTION_TYPES = ImmutableSet.of(
      DBAudit.ACTION.ADD_SECRET,
      DBAudit.ACTION.REMOVE_SECRET,
      DBAudit.ACTION.EDIT_SECRET,
      DBAudit.ACTION.GET_SECRET_WITH_CRITICAL
  );
  
  public static List<DBAudit> getAllActionsByUser(Manager mgr, DBIdentity user)
      throws SQLException {
    return mgr.auditDao.queryForEq(DBAudit.USER_FIELD_NAME, user);
  }

  @DatabaseField(generatedId = true)
  private int id;

  @DatabaseField(columnName = SOURCE_IP_FIELD_NAME)
  private String sourceIp;

  @DatabaseField(columnName = TRANSACTION_ID_FIELD_NAME)
  private String transactionId;

  @DatabaseField(columnName = SERVER_IP_FIELD_NAME)
  private String serverIp;

  @DatabaseField(columnName = ACTION_FIELD_NAME)
  private ACTION action;

  @DatabaseField(foreign = true, columnName = USER_FIELD_NAME, index=true, foreignAutoRefresh=true)
  private DBIdentity user;

  @DatabaseField(foreign = true, columnName = TARGET_USER_FIELD_NAME, foreignAutoRefresh=true)
  private DBIdentity targetUser;

  @DatabaseField(foreign = true, columnName = TARGET_GROUP_FIELD_NAME)
  private DBGroup targetGroup;

  /**
   * @return the deviceId
   */
  public String getDeviceId() {
    return deviceId;
  }

  /**
   * @param deviceId the deviceId to set
   */
  public void setDeviceId(String deviceId) {
    this.deviceId = deviceId;
  }

  @DatabaseField(foreign = true, columnName = TARGET_SV_SECRET_FIELD_NAME, index=true)
  private DBServerVisibleSecret targetSVS;

  @DatabaseField(dataType = DataType.LONG_STRING)
  private String note;

  @DatabaseField(columnName = TIMESTAMP_FIELD_NAME)
  private Long timestampMs;

  @DatabaseField(columnName = DEVICE_ID_NAME)
  private String deviceId;

  
  @DatabaseField(columnName = OPERATION_NAME)
  private String operationName;
  
  public String getOperationName() {
    return operationName;
  }

  /**
   * @return the sourceIp
   */
  public String getSourceIp() {
    return sourceIp;
  }

  /**
   * @param sourceIp
   *          the sourceIp to set
   */
  public void setSourceIp(String sourceIp) {
    this.sourceIp = sourceIp;
  }

  /**
   * @return the serverIp
   */
  public String getServerIp() {
    return serverIp;
  }

  /**
   * @param serverIp
   *          the serverIp to set
   */
  public void setServerIp(String serverIp) {
    this.serverIp = serverIp;
  }

  /**
   * @return the user
   */
  public DBIdentity getUser() {
    return user;
  }

  /**
   * @param user
   *          the user to set
   */
  public void setUser(DBIdentity user) {
    this.user = user;
  }

  /**
   * @return the targetUser
   */
  public DBIdentity getTargetUser() {
    return targetUser;
  }

  /**
   * @param targetUser
   *          the targetUser to set
   */
  public void setTargetUser(DBIdentity targetUser) {
    this.targetUser = targetUser;
  }

  /**
   * @return the targetGroup
   */
  public DBGroup getTargetGroup() {
    return targetGroup;
  }

  /**
   * @param targetGroup
   *          the targetGroup to set
   */
  public void setTargetGroup(DBGroup targetGroup) {
    this.targetGroup = targetGroup;
  }

  /**
   * @return the targetSVS
   */
  public DBServerVisibleSecret getTargetSVS() {
    return targetSVS;
  }

  /**
   * @param targetSVS
   *          the targetSVS to set
   */
  public void setTargetSVS(DBServerVisibleSecret targetSVS) {
    this.targetSVS = targetSVS;
  }

  /**
   * @return the note
   */
  public String getNote() {
    return note;
  }

  /**
   * @param note
   *          the note to set
   */
  public void setNote(String note) {
    this.note = note;
  }

  /**
   * @return the timestampMs
   */
  public Long getTimestampMs() {
    return timestampMs;
  }

  /**
   * @param timestampMs
   *          the timestampMs to set
   */
  public void setTimestampMs(Long timestampMs) {
    this.timestampMs = timestampMs;
  }

  /**
   * @return the id
   */
  public int getId() {
    return id;
  }

  public void setAction(ACTION action2) {
    action = action2;
  }

  public ACTION getAction() {
    return action;
  }

  /**
   * @return the transactionId
   */
  public String getTransactionId() {
    return transactionId;
  }

  /**
   * @param transactionId
   *          the transactionId to set
   */
  public void setTransactionId(String transactionId) {
    this.transactionId = transactionId;
  }

  public void setOperationName(String operationName) {
    this.operationName = operationName;
    
  }
}
