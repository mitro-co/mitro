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

import co.mitro.core.servlets.Util;

import com.google.common.base.Preconditions;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName="mitro_access_email")
public class DBMitroAccessEmail {
  public static final String EMAIL_FIELD_NAME = "email";
  public static final String TIMESTAMP_FIELD_NAME = "timestamp_ms";
  
  @DatabaseField(generatedId=true, canBeNull=false)
  private int id;

  @DatabaseField(columnName=EMAIL_FIELD_NAME, canBeNull=false, index=true)
  private String email;
  
  @DatabaseField(columnName=TIMESTAMP_FIELD_NAME, canBeNull=false)
  private long timestamp;
  
  public DBMitroAccessEmail() {
    timestamp = System.currentTimeMillis();
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    Preconditions.checkArgument(Util.isEmailAddress(email));
    this.email = email;
  }
}
