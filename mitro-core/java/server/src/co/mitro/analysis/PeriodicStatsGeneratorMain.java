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

import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.mitro.core.exceptions.MitroServletException;
import co.mitro.core.server.Main;
import co.mitro.core.server.Manager;
import co.mitro.core.server.ManagerFactory;
import co.mitro.core.server.data.DBHistoricalOrgState;
import co.mitro.core.server.data.DBHistoricalUserState;

import com.google.common.base.Stopwatch;
import com.j256.ormlite.support.DatabaseConnection;

public class PeriodicStatsGeneratorMain {
  private static final String STATS_DIRECTORY = "/home/analytics";
  private static final Logger logger = LoggerFactory.getLogger(PeriodicStatsGeneratorMain.class);
  public final static class StatsTask extends StatsGenerator implements Runnable {
    @Override
    public void run() {
      Stopwatch watch  = Stopwatch.createStarted();
      watch.start();
      try (Manager mgr = ManagerFactory.getInstance().newManager()) {
        mgr.disableAuditLogs();
        mgr.identityDao.getConnectionSource().getReadWriteConnection().executeStatement(
            "set transaction isolation level REPEATABLE READ;",
            DatabaseConnection.DEFAULT_RESULT_FLAGS);
        logger.info("starting stats generation run");

        StatsGenerator.Snapshot snapshot = StatsGenerator.generateStatistics(STATS_DIRECTORY, mgr);
        logger.info("Have data on {} orgs and {} users",
            snapshot.orgStateObjects.size(), snapshot.userStateObjects.size());
        for (DBHistoricalOrgState org : snapshot.orgStateObjects) {
          mgr.historicalOrgDao.create(org);
        }
        for (DBHistoricalUserState user : snapshot.userStateObjects) {
          mgr.historicalUserDao.create(user);
        }
        logger.info("Successfully updated historical tables. Commiting transaction ...");
        mgr.commitTransaction();
        logger.info("Done (elapsed time = {}ms)", watch.elapsed(TimeUnit.MILLISECONDS));
      } catch (SQLException | IOException | MitroServletException e) {
        logger.error("unknown error", e);
      }
    }
    
  }

  public static void main(String[] args) throws Exception {
    Main.exitIfAssertionsDisabled();

    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    scheduler.scheduleAtFixedRate(new StatsTask(), 0, 6, TimeUnit.HOURS);
  }
}
