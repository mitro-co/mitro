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
package co.mitro.migration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;

import co.mitro.core.server.Main;
import co.mitro.core.server.Manager;
import co.mitro.core.server.ManagerFactory;
import co.mitro.core.server.data.DBIdentity;
import co.mitro.core.server.data.DBUserName;

public class CreateUserNamesMain {
  public static void main(String[] args) throws IOException, SQLException {
    Main.exitIfAssertionsDisabled();
    BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in,
        StandardCharsets.UTF_8));
    System.out.println("Should we create alias records (y/n)? ");
    if (stdin.readLine().toLowerCase().startsWith("y")) {
      try (Manager mgr = ManagerFactory.getInstance().newManager()) {
        // assert that the table is empty
        if (!mgr.userNameDao.queryForAll().isEmpty()) {
          System.err.println("ERROR: user name table is not empty! Not proceeding");
          System.exit(-1);
        }
        for (DBIdentity identity : mgr.identityDao.queryForAll()) {
          mgr.userNameDao.create(new DBUserName(identity));
        }
        mgr.commitTransaction();
        System.out.println("Done.");
      }
    } else {
      System.out.println("OK, bye.");
    }
  }
}
