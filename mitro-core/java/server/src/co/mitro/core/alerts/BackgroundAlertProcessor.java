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
package co.mitro.core.alerts;

import java.util.Collection;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.mitro.core.server.Manager;
import co.mitro.core.server.ManagerFactory;
import co.mitro.core.server.data.DBFutureAlert;

import com.j256.ormlite.stmt.QueryBuilder;


public class BackgroundAlertProcessor implements Runnable {
  private static final int NUMBER_THREADS = 1;
  private static final ScheduledExecutorService ALERT_PROCESSOR = 
      Executors.newScheduledThreadPool(NUMBER_THREADS, new ManagerFactory.DaemonThreadFactory());
  private static final Logger logger = LoggerFactory.getLogger(BackgroundAlertProcessor.class);
  private static final int INITIAL_DELAY_SEC = 0; 
  private static final int BETWEEN_RUNS_DELAY_SEC = 60;
  private ManagerFactory factory;
  private int totalProcessed = 0;

  public BackgroundAlertProcessor(ManagerFactory mf) {
    factory = mf;
  }

  public static final void startBackgroundService(ManagerFactory mf) {
    BackgroundAlertProcessor background = new BackgroundAlertProcessor(mf);
    ALERT_PROCESSOR.scheduleWithFixedDelay(background, INITIAL_DELAY_SEC, BETWEEN_RUNS_DELAY_SEC, TimeUnit.SECONDS);
  }

  @Override
  public void run() {
    int numProcessed = 0;
    logger.info("starting periodic alert check; cumulative total = {} events", totalProcessed);
    try (Manager mgr = factory.newManager()) {
      // try to find all pending alerts.
      long now = System.currentTimeMillis();
      QueryBuilder<DBFutureAlert,Integer> builder = mgr.futureAlertDao.queryBuilder();
      builder.where().eq(DBFutureAlert.INACTIVE, false).and().lt(DBFutureAlert.NEXT_CHECK_TIMESTAMP_MS, now);
      Collection<DBFutureAlert> alerts = mgr.futureAlertDao.query(builder.prepare());
      for (DBFutureAlert dbAlert : alerts) {
        try {
          EmailAlertManager.getInstance().processFutureAlert(mgr, dbAlert);
          ++numProcessed;
          ++totalProcessed;
        } catch (Throwable e) {
          
          // we should do something cleaner, but really, there should be no exceptions here
          // Maybe Evan is right, and exceptions are stupid.
          logger.error("!!!!! Exception trying to process alert, ignoring", e);
          dbAlert.inactive = true;
          mgr.futureAlertDao.update(dbAlert);
        }
      }
    } catch (Throwable e) {
      logger.error("Exception processing alerts", e);
    }
    logger.info("periodic alert check complete. This time: {}. Cumulative: {}", numProcessed, totalProcessed);
  }

  
}
