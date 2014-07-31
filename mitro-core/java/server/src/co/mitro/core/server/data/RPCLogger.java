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

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import co.mitro.recordio.JsonRecordWriter;

public class RPCLogger {
  private static final Logger logger = LoggerFactory.getLogger(RPCLogger.class);
  private static final int LOG_TIME_HOURS = 12;

  private static JsonRecordWriter rpcLogger = null;
  private static String prefix = null;
  private static Timer rotator;

  /** Not constructible: All members are static (should we change that?). */
  private RPCLogger() {}

  public static synchronized void stopLogging() throws IOException {
    try {
      if (rotator != null) {
        rotator.cancel();
      }
    } finally {
      if (rpcLogger != null) {
        rpcLogger.close();
      }
      rpcLogger = null;
      rotator = null;
      prefix = null;
    }
  }

  public static synchronized void startLoggingWithPrefix(String prefix) throws IOException {
    Preconditions.checkNotNull(prefix);
    Preconditions.checkState(RPCLogger.prefix == null, "RPCLogger already started");

    RPCLogger.prefix = prefix;
    // this thread runs as a daemon, so it doesn't stop the JVM from exiting
    rotator = new Timer(true);
    
    // run the log cycler immediately so that it can create the 1st log file. 
    rotator.schedule(new LogRotator(), 0, TimeUnit.HOURS.toMillis(LOG_TIME_HOURS));
  }

  private static synchronized void closeAndSetNewJsonLogger() throws IOException {
    if (rpcLogger != null) {
      rpcLogger.close();
      rpcLogger = null;
    }
    // prefix can be null if we do start/stop very quickly (e.g. unit tests)
    if (prefix != null) {
      rpcLogger = JsonRecordWriter.MakeFromFilePrefix(prefix);
    }
  }

  public static synchronized void log(Object rpc, Object logMetadata) {
    if (null != rpcLogger) {
      rpcLogger.writeJson(rpc, logMetadata);
    }
  }

  public final static class LogRotator extends TimerTask {
    @Override
    public void run() {
      try {
        logger.info("Cycling log file");
        RPCLogger.closeAndSetNewJsonLogger();
      } catch (IOException e) {
        logger.error("Unknown error cycling log file: ", e); 
      }
    }
  }
}