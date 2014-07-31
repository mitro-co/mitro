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

import java.util.Date;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName="issues")
public class DBIssue {
  public DBIssue() {
    creationDate = new Date();
  }

  @DatabaseField(generatedId=true)
  private int id;

  @DatabaseField(dataType=DataType.LONG_STRING)
  private String url;
    
  @DatabaseField
  private String type;

  /**
   * @return the logs
   */
  public String getLogs() {
    return logs;
  }

  /**
   * @param logs the logs to set
   */
  public void setLogs(String logs) {
    this.logs = logs;
  }

  @DatabaseField(dataType=DataType.LONG_STRING)
  private String logs;

  @DatabaseField(dataType=DataType.LONG_STRING)
  private String description;
  
  @DatabaseField(dataType=DataType.LONG_STRING)
  private String email;

  // TODO: change this to dataType=DataType.DATE_LONG to avoid timezone troubles
  @DatabaseField
  private Date creationDate;

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public Date getCreationDate() {
    return creationDate;
  }

  public void setCreationDate(Date creationDate) {
    this.creationDate = creationDate;
  }

  public int getId() {
    return id;
  }
}
