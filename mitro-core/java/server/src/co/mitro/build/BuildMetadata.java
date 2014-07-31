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
package co.mitro.build;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

/**
 * Provides information about the build. This reads a properties file that
 * should be created by ant.
 */
public class BuildMetadata {
  private static final String RESOURCE_PATH = "/build.properties";
  private static final Logger logger = LoggerFactory.getLogger(BuildMetadata.class);
  private static final BuildMetadata SINGLETON;

  private static final BuildMetadata UNKNOWN_BUILD =
      new BuildMetadata("unknown", "unknown", "1970-01-01T00:00:00Z");

  static {
    BuildMetadata parsed = tryParseResource();
    if (parsed == null) {
      parsed = UNKNOWN_BUILD;
    }
    SINGLETON = parsed;
  }

  public static BuildMetadata get() {
    return SINGLETON;
  }

  /** Hash of the commit of the build. */
  public final String commit;
  /** Output of git describe for the build. */
  public final String describe;
  /** Time */
  public final String time;

  private BuildMetadata(String commit, String describe, String time) {
    Preconditions.checkArgument(!Strings.isNullOrEmpty(commit));
    Preconditions.checkArgument(!Strings.isNullOrEmpty(describe));
    Preconditions.checkArgument(!Strings.isNullOrEmpty(time));
    this.commit = commit;
    this.describe = describe;
    this.time = time;
  }

  private static BuildMetadata tryParseResource() {
    BuildMetadata result = null;
    InputStream stream = BuildMetadata.class.getResourceAsStream(RESOURCE_PATH);
    if (stream != null) {
      try {
        Properties properties = new Properties();
        properties.load(stream);

        String commit = properties.getProperty("gitCommit");
        if (commit == null) {
          commit = UNKNOWN_BUILD.commit;
        }
        String describe = properties.getProperty("gitDescribe");
        if (describe == null) {
          describe = UNKNOWN_BUILD.describe;
        }
        String time = properties.getProperty("buildTime");
        if (time == null) {
          time = UNKNOWN_BUILD.time;
        }

        result = new BuildMetadata(commit, describe, time);
      } catch (IOException e) {
        logger.warn("exception when loading build metadata from " + RESOURCE_PATH, e);
      } finally {
        try {
          stream.close();
        } catch (IOException e) {
          logger.warn("could not close input stream", e);
        }
      }
    } else {
      logger.info("no build metadata found at resource path {}", RESOURCE_PATH);
    }
    return result;
  }

  public static void main(String[] arguments) {
    BuildMetadata metadata = BuildMetadata.get();
    if (metadata == null) {
      System.err.println("Error: Could not parse build metadata");
      System.exit(1);
      return;
    }

    System.out.println("commit: " + metadata.commit);
    System.out.println("build time: " + metadata.time);
  }
}
