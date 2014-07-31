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

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "device_specific")
public class DBDeviceSpecificInfo {
  // for QueryBuilder to be able to find the fields
  public static final String IDENTITY_FIELD_NAME = "user";
  public static final String DEVICE_ID_NAME = "device";
  public static final String CLIENT_LOCAL_STORAGE_KEY_NAME = "client_local_storage_key";  
  public static final String LAST_USE_SEC_NAME = "last_use_sec";
  public static final String PLATFORM_NAME = "platform";
  
  /**
   * @param string the clientLocalStorageKey to set
   */
  public void setClientLocalStorageKey(String string) {
    this.clientLocalStorageKey = string;
  }

  /**
   * @return the id
   */
  public int getId() {
    return id;
  }

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

  /**
   * @return the clientLocalStorageKey
   */
  public String getClientLocalStorageKey() {
    return clientLocalStorageKey;
  }

  /**
   * @return the user
   */
  public DBIdentity getUser() {
    return user;
  }

  /**
   * @param user the user to set
   */
  public void setUser(DBIdentity user) {
    this.user = user;
  }

  /**
   * @return the lastUseSec
   */
  public double getLastUseSec() {
    return lastUseSec;
  }

  /**
   * @param lastUseSec the lastUseSec to set
   */
  public void setLastUseSec(double lastUseSec) {
    this.lastUseSec = lastUseSec;
  } 

  public String getPlatform() {
    return platform;
  }

  public void setPlatform(String platform) {
    this.platform = platform;
  }


  @DatabaseField(generatedId = true)
  private int id;
  
  @DatabaseField(columnName=DEVICE_ID_NAME, canBeNull=false, index=true)
  private String deviceId;
  
  @DatabaseField(columnName=PLATFORM_NAME, canBeNull=true, index=false)
  private String platform;
  
  @DatabaseField(columnName=CLIENT_LOCAL_STORAGE_KEY_NAME, dataType=DataType.LONG_STRING)
  private String clientLocalStorageKey;
  
  @DatabaseField(columnName=LAST_USE_SEC_NAME)
  private double lastUseSec;
  
  @DatabaseField(foreign = true, columnName = IDENTITY_FIELD_NAME, canBeNull=false)
  private DBIdentity user;

}
