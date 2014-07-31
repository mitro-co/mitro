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
package co.mitro.analysis;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.mitro.core.server.Main;
import co.mitro.core.server.Manager;
import co.mitro.core.server.ManagerFactory;
import co.mitro.core.server.ManagerFactory.ConnectionMode;

public class DumpStateMain {
  private static final Logger logger = LoggerFactory.getLogger(DumpStateMain.class);

  public static void main(String[] args) throws Exception {
    Main.exitIfAssertionsDisabled();
    if (args.length != 1) {
      System.err.println("You must specify a target directory, in which both users/ and orgs/ must already exist");
      System.exit(-1);
    }
    String outDir = args[0];

    String databaseUrl = ManagerFactory.DATABASE_URL;
    String databaseProperty = System.getProperty("dburl");
    if (databaseProperty != null) {
      logger.info("Setting database URL: {}", databaseProperty);
      databaseUrl = databaseProperty;
    }

    ManagerFactory managerFactory = new ManagerFactory(databaseUrl, new Manager.Pool(),
        ManagerFactory.IDLE_TXN_POLL_SECONDS, TimeUnit.SECONDS, ConnectionMode.READ_WRITE);
    try (Manager manager = managerFactory.newManager()) {
      StatsGenerator.generateStatistics(outDir, manager);
    }
  }
}
