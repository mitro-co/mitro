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
package co.mitro.metrics;

/**
 * Sends metrics to an Etsy statsd server. It is inspired by the Tim Group's implementation
 * (BSD license): https://github.com/youdevise/java-statsd-client/
 */
public interface StatsDClient {
  /**
   * Adjusts the named counter by delta. The current counter value is maintained by statsd.
   *
   * @param name the name of the counter to adjust
   * @param delta the amount to adjust the counter by
   */
  public void count(String name, long delta);

  /**
   * Records the latest fixed value for the named gauge.
   *
   * @param name the name of the gauge
   * @param value the new reading of the gauge
   */
  public void recordGauge(String name, long value);

  /**
   * Same as {@link #recordGauge(String, long)} but for doubles. Datadog supports doubles,
   * but some statsd implementations may not.
   *
   * @see #recordGauge(String, long)
   */
  public void recordGauge(String name, double value);

  /**
   * Records an execution time in milliseconds for the named operation.
   *
   * @param name the name of the timed operation
   * @param timeInMs the time in milliseconds
   */
  public void recordExecutionTime(String name, int timeInMs);
}
