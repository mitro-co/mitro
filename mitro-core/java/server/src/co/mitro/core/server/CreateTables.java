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
package co.mitro.core.server;

import java.sql.SQLException;

import com.j256.ormlite.jdbc.JdbcConnectionSource;

/**
 * Command-line tool to create database tables. Used to diff production/development schemas.
 */
public class CreateTables {
  public static void main(String[] args) throws Exception {
    if (args.length != 1) {
      System.err.println("CreateTables (database name)");
      System.exit(1);
    }
    String databaseName = args[0];

    int exitCode = 0;

    String databaseUrl = ManagerFactory.DATABASE_URL.replace("/mitro", "/" + databaseName);
    JdbcConnectionSource connection = new JdbcConnectionSource(databaseUrl);
    try {
      Manager.createTablesIfNotExists(connection);
    } catch (SQLException e) {
      exitCode = 1;
      System.err.println("Failed: Some exception was thrown:");
      e.printStackTrace(System.err);
    } finally {
      connection.close();
    }
    System.exit(exitCode);
  }
}
