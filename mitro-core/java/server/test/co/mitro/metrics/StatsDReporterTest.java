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

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SlidingWindowReservoir;
import com.codahale.metrics.Timer;
import com.google.common.collect.Maps;

public class StatsDReporterTest {
  private MetricRegistry metrics;
  private MockBatchStatsDClient statsd;
  private StatsDReporter reporter;

  @SuppressWarnings("rawtypes")
  private SortedMap<String, Gauge> gauges;
  private SortedMap<String, Counter> counters;
  private SortedMap<String, Histogram> histograms;
  private SortedMap<String, Meter> meters;
  private SortedMap<String, Timer> timers;

  private static final class MockBatchStatsDClient implements BatchStatsDClient {
    public Batch lastSend;

    @Override
    public void sendStatistics(Batch collection) throws IOException {
      lastSend = collection;
    }
  }

  @Before
  public void setUp() throws Exception {
    metrics = new MetricRegistry();
    statsd = new MockBatchStatsDClient();
    reporter = StatsDReporter.forRegistry(metrics).build(statsd);

    gauges = Maps.newTreeMap();
    counters = Maps.newTreeMap();
    histograms = Maps.newTreeMap();
    meters = Maps.newTreeMap();
    timers = Maps.newTreeMap();
  }

  /** 2^53 is the largest integer that can be exactly stored in a double. */
  private static final long MAX_DOUBLE_INT = (1L<<53);

  private void assertPacketEquals(String expected) {
    try {
      assertEquals(1, statsd.lastSend.getPackets().size());
      String packet = new String(statsd.lastSend.getPackets().get(0), "UTF-8");
      assertEquals(expected, packet);
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void testReportCounters() {
    // Metrics Counters are actually statsd gauges (maintained by the client)
    Counter c = new Counter();
    c.inc(45);
    counters.put("counter", c);
    reporter.report(gauges, counters, histograms, meters, timers);
    assertPacketEquals("counter:45|g\n");

    c.dec(3);
    reporter.report(gauges, counters, histograms, meters, timers);
    assertPacketEquals("counter:42|g\n");

    c.dec(42);
    c.inc(MAX_DOUBLE_INT + 1);
    reporter.report(gauges, counters, histograms, meters, timers);
    assertPacketEquals(String.format("counter:%d|g\n", MAX_DOUBLE_INT + 1));
  }

  @Test
  public void testReportGauges() {
    Gauge<Double> g = new Gauge<Double>() {
      @Override
      public Double getValue() {
        return 3.14;
      }
    };

    gauges.put("gauge", g);
    reporter.report(gauges, counters, histograms, meters, timers);
    assertPacketEquals("gauge:3.14|g\n");

    Gauge<Long> g2 = new Gauge<Long>() {
      @Override
      public Long getValue() {
        return MAX_DOUBLE_INT + 1;
      }
    };
    gauges.put("gauge", g2);
    reporter.report(gauges, counters, histograms, meters, timers);
    assertPacketEquals(String.format("gauge:%d|g\n", MAX_DOUBLE_INT + 1));
  }

  @Test
  public void testReportMeters() {
    Meter m = new Meter();
    m.mark();
    m.mark();

    meters.put("meter", m);
    reporter.report(gauges, counters, histograms, meters, timers);
    assertPacketEquals("meter:2|g\n");
  }

  @Test
  public void testReportHistograms() {
    Histogram h = new Histogram(new SlidingWindowReservoir(10));
    h.update(1000);
    h.update(2000);
    h.update(2000);

    histograms.put("histogram", h);
    reporter.report(gauges, counters, histograms, meters, timers);
    assertPacketEquals("histogram.mean:1666.6666666666667|g\nhistogram.p95:2000.0|g\n");
  }

  @Test
  public void testReportTimers() {
    Timer t = new Timer();
    t.update(1000, TimeUnit.MILLISECONDS);
    t.update(2000, TimeUnit.MILLISECONDS);
    t.update(2, TimeUnit.SECONDS);

    timers.put("timer", t);
    reporter.report(gauges, counters, histograms, meters, timers);
    assertPacketEquals("timer.count:3|g\ntimer.mean:1666.6666666666667|g\ntimer.p95:2000.0|g\n");
  }

  @Test
  public void testPrefix() {
    reporter = StatsDReporter.forRegistry(metrics).prefixedWith("prefix").build(statsd);

    Meter m = new Meter();
    m.mark();
    meters.put("meter", m);
    reporter.report(gauges, counters, histograms, meters, timers);
    assertPacketEquals("prefix.meter:1|g\n");
  }

  @Test
  public void testDurationConversion() {
    reporter = StatsDReporter.forRegistry(metrics).convertDurationsTo(TimeUnit.NANOSECONDS)
        .build(statsd);

    Timer t = new Timer();
    t.update(1, TimeUnit.MILLISECONDS);
    timers.put("timer", t);
    reporter.report(gauges, counters, histograms, meters, timers);
    assertPacketEquals("timer.count:1|g\ntimer.mean:1000000.0|g\ntimer.p95:1000000.0|g\n");
  }
}
