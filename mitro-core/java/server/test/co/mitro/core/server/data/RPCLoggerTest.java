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

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.containsString;

import java.io.IOException;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class RPCLoggerTest {
  @Rule
  public TemporaryFolder folder = new TemporaryFolder();

  @After
  public void tearDown() throws IOException {
    RPCLogger.stopLogging();
  }

  @Test
  public void shutdownWithoutStart() throws IOException {
    // Main.createJetty had a shutdown hook that stopped logging ... even if it wasn't started
    RPCLogger.stopLogging();
  }

  @Test
  public void doubleStart() throws IOException {
    String prefix = folder.getRoot().toString() + "/file_prefix";
    RPCLogger.startLoggingWithPrefix(prefix);

    // calling start a second time should not work
    try {
      RPCLogger.startLoggingWithPrefix(prefix);
      fail("expected exception");
    } catch (IllegalStateException e) {
      assertThat(e.getMessage(), containsString("already started"));
    }
  }

  private static int countNonDaemonThreads() {
    Thread[] threads = new Thread[Thread.activeCount()*2];
    int threadCount = Thread.enumerate(threads);
    assertTrue(threadCount < threads.length);

    int nonDaemonCount = 0;
    for (int i = 0; i < threadCount; i++) {
      if (!threads[i].isDaemon()) {
        nonDaemonCount += 1;
      }
    }
    return nonDaemonCount;
  }

  @Test
  public void runsAsDaemonThread() throws IOException {
    int nonDaemonBefore = countNonDaemonThreads();
    int totalBefore = Thread.activeCount();
    RPCLogger.startLoggingWithPrefix(folder.getRoot().toString() + "/prefix");
    assertEquals(nonDaemonBefore, countNonDaemonThreads());
    assertEquals(totalBefore + 1, Thread.activeCount());

    RPCLogger.stopLogging();
    assertEquals(nonDaemonBefore, countNonDaemonThreads());
  }

  @Test(expected=NullPointerException.class)
  public void badPrefix() throws Exception {
    RPCLogger.startLoggingWithPrefix(null);
  }
}
