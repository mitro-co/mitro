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
import java.util.Set;

import co.mitro.core.server.Manager;

import com.google.common.collect.Sets;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "username")
public class DBUserName {
  public static final String IDENTITY_FIELD_NAME = "identity";
  public static final String EMAIL_FIELD_NAME = "email";
  
  @DatabaseField(generatedId = true)
  private int id;
  
  @DatabaseField(columnName=IDENTITY_FIELD_NAME, foreign=true, canBeNull=false)
  private DBIdentity identity;
  
  @DatabaseField(columnName=EMAIL_FIELD_NAME, unique=true, canBeNull=false)
  private String email;
  
  public int getId() {
    return id;
  }

  public static void addAlias(String aliasEmail, Manager mgr,
      DBIdentity identity) throws SQLException {
    mgr.userNameDao.create(new DBUserName(identity, aliasEmail));
  }

  public DBUserName() {}
  
  /** 
   * create a default alias for the primary account name
   * @param identity identity which already exists in the DB (i.e. id != 0)
   */
  public DBUserName(DBIdentity identity) {
    assert (identity.getId() != 0);
    this.identity = identity;
    this.email = identity.getName();
  }
  
  /**
   * create an alias for an existing identity
   * @param identity identity which already exists in the DB (i.e. id != 0)
   */
  public DBUserName(DBIdentity identity, String name) {
    this.identity = identity;
    this.email = name;
  }
  
  public static Set<String> getAliasesForIdentity(Manager mgr, DBIdentity identity) throws SQLException {
    Set<String> rval = Sets.newHashSet();
    for (DBUserName u : mgr.userNameDao.queryForEq(DBUserName.IDENTITY_FIELD_NAME, identity)) {
      rval.add(u.email);
    }
    return rval;
  }

  
}