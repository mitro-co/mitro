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

import java.io.IOException;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;

/**
 * Publishes metrics to an Etsy StatsD server. All values are reported as gauges, because
 * typically statsd is used with another aggregation system that can compute rates, averages,
 * etc.
 *
 * Based on GraphiteReporter.
 *
 * Metrics input type -> StatsD output type
 * Gauge -> guage
 * Counter -> guage
 * Meter -> guage (.count)
 * Histogram -> guage "name.mean" (mean)
 *           -> guage "name.p95" (95th percentile)
 * Timer -> both the Meter and the Histogram
 */
public class StatsDReporter extends ScheduledReporter {
  /**
   * Returns a new {@link Builder} for {@link StatsDReporter}.
   *
   * @param registry the registry to report
   * @return a {@link Builder} instance for a {@link StatsDReporter}
   */
  public static Builder forRegistry(MetricRegistry registry) {
    return new Builder(registry);
  }

  /**
   * A builder for {@link StatsDReporter} instances. Defaults to the
   * default clock, converting rates to events/second, converting durations to milliseconds, and
   * not filtering metrics.
   */
  public static class Builder {
    private final MetricRegistry registry;
    private String prefix;
    private TimeUnit rateUnit;
    private TimeUnit durationUnit;
    private MetricFilter filter;

    private Builder(MetricRegistry registry) {
      this.registry = registry;
      this.prefix = null;
      this.rateUnit = TimeUnit.SECONDS;
      this.durationUnit = TimeUnit.MILLISECONDS;
      this.filter = MetricFilter.ALL;
    }

    /**
     * Prefix all metric names with the given string.
     *
     * @param prefix the prefix for all metric names
     * @return {@code this}
     */
    public Builder prefixedWith(String prefix) {
        this.prefix = prefix;
        return this;
    }

    /**
     * Convert rates to the given time unit.
     *
     * @param rateUnit a unit of time
     * @return {@code this}
     */
    public Builder convertRatesTo(TimeUnit rateUnit) {
      this.rateUnit = rateUnit;
      return this;
    }

    /**
     * Convert durations to the given time unit.
     *
     * @param durationUnit a unit of time
     * @return {@code this}
     */
    public Builder convertDurationsTo(TimeUnit durationUnit) {
      this.durationUnit = durationUnit;
      return this;
    }

    /**
     * Only report metrics which match the given filter.
     *
     * @param filter a {@link MetricFilter}
     * @return {@code this}
     */
    public Builder filter(MetricFilter filter) {
      this.filter = filter;
      return this;
    }

    /**
     * Builds a {@link StatsDReporter} with the given properties, sending metrics using the
     * given {@link Graphite} client.
     *
     * @param graphite a {@link Graphite} client
     * @return a {@link StatsDReporter}
     */
    public StatsDReporter build(BatchStatsDClient statsd) {
      return new StatsDReporter(registry, statsd, prefix, rateUnit, durationUnit, filter);
    }
  }

  private static final Logger logger = LoggerFactory.getLogger(StatsDReporter.class);

  private final BatchStatsDClient statsd;
  private final String prefix;

  private StatsDReporter(MetricRegistry registry, BatchStatsDClient statsd,
      String prefix, TimeUnit rateUnit, TimeUnit durationUnit, MetricFilter filter) {
    super(registry, "statsd-reporter", filter, rateUnit, durationUnit);
    this.statsd = statsd;
    this.prefix = prefix;
  }

  @Override
  @SuppressWarnings("rawtypes")
  public void report(SortedMap<String, Gauge> gauges, SortedMap<String, Counter> counters,
      SortedMap<String, Histogram> histograms, SortedMap<String, Meter> meters,
      SortedMap<String, Timer> timers) {
    BatchStatsDClient.Batch batch = new BatchStatsDClient.Batch();
    for (Map.Entry<String, Counter> entry : counters.entrySet()) {
      String name = prefix(entry.getKey());
      batch.recordGauge(name, entry.getValue().getCount());
    }

    for (Map.Entry<String, Gauge> entry : gauges.entrySet()) {
      String name = prefix(entry.getKey());
      final DoubleOrLong value = DoubleOrLong.make(entry.getValue().getValue());
      if (value != null) {
        if (value.isLong) {
          batch.recordGauge(name, value.longValue);
        } else {
          batch.recordGauge(name, value.doubleValue);
        }
      }
    }

    for (Map.Entry<String, Meter> meter : meters.entrySet()) {
      String name = prefix(meter.getKey());
      batch.recordGauge(name, meter.getValue().getCount());
    }

    for (Map.Entry<String, Histogram> histogram : histograms.entrySet()) {
      String name = prefix(histogram.getKey());
      // Export the mean and 95th percentile
      // TODO: Export other measures if we will find them useful?
      Snapshot snapshot = histogram.getValue().getSnapshot();
      recordSnapshot(batch, name, snapshot, false);
    }

    for (Map.Entry<String, Timer> timer : timers.entrySet()) {
      String name = prefix(timer.getKey());
      // Export the count and the histogram as milliseconds
      Snapshot snapshot = timer.getValue().getSnapshot();
      batch.recordGauge(name + ".count", timer.getValue().getCount());
      recordSnapshot(batch, name, snapshot, true);
    }

    try {
      statsd.sendStatistics(batch);

      int count = gauges.size() + counters.size() + histograms.size() + meters.size() +
          timers.size();
      logger.debug("reported {} statistics", count);
    } catch (IOException e) {
      logger.warn("Unable to report to StatsD", e);
    }
  }

  private String prefix(String... components) {
    return MetricRegistry.name(prefix, components);
  }

  private void recordGaugeWithConversion(
      BatchStatsDClient.Batch batch, String name, double value, boolean isDuration) {
    if (isDuration) value = convertDuration(value);
    batch.recordGauge(name, value);
  }

  private void recordSnapshot(
      BatchStatsDClient.Batch batch, String name, Snapshot snapshot, boolean isDuration) {
    recordGaugeWithConversion(batch, name + ".mean", snapshot.getMean(), isDuration);
    recordGaugeWithConversion(batch, name + ".p95", snapshot.get95thPercentile(), isDuration);
  }

  private static final class DoubleOrLong {
    public final boolean isLong;
    public final long longValue;
    public final double doubleValue;

    public static DoubleOrLong make(long longValue) {
      return new DoubleOrLong(true, longValue, 0.0);
    }

    public static DoubleOrLong make(double doubleValue) {
      return new DoubleOrLong(false, 0, doubleValue);
    }

    public static DoubleOrLong make(Object o) {
      if (o instanceof Float) {
        return make(((Float) o).doubleValue());
      } else if (o instanceof Double) {
        return make(((Double) o).doubleValue());
      } else if (o instanceof Byte) {
        return make(((Byte) o).longValue());
      } else if (o instanceof Short) {
        return make(((Short) o).longValue());
      } else if (o instanceof Integer) {
        return make(((Integer) o).longValue());
      } else if (o instanceof Long) {
        return make(((Long) o).longValue());
      }
      return null;
    }

    public DoubleOrLong(boolean isLong, long longValue, double doubleValue) {
      if (isLong) assert doubleValue == 0.0;
      else assert longValue == 0;
      this.isLong = isLong;
      this.longValue = longValue;
      this.doubleValue = doubleValue;
    }
  }
}
