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
package co.mitro.core.util;

import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import co.mitro.recordio.JsonRecordReader;

import com.google.common.base.Strings;
import com.google.common.util.concurrent.AtomicLongMap;

public class RpcLogReader {
  public final static class Span {
    public long minTime = Long.MAX_VALUE;
    public long maxTime = Long.MIN_VALUE;
    public Span() {
    };
    public Span(long ts) {
      addTime(ts);
    }
    public void addTime(long ts) {
      minTime = java.lang.Math.min(minTime, ts);
      maxTime = java.lang.Math.max(maxTime, ts);
    }
    public long duration() {
      return maxTime - minTime;
    }
  }
  public static void main(String[] args) throws IOException {
    AtomicLongMap<String> counter = AtomicLongMap.<String>create();
    Map<String, Span> txnLength = new HashMap<>();
    Span duration = new Span();
    
    for (int i = 0; i < args.length; ++i) {
      String filename = args[i];
      System.err.println("Reading file: " + filename);
      JsonRecordReader rr = JsonRecordReader.MakeFromFilename(filename);
      JsonRecordReader.JsonLog log;
      try {
        while (null != (log = rr.readJson())) {
          counter.incrementAndGet(log.metadata.endpoint);
          duration.addTime(log.metadata.timestamp);
          if (log.metadata.endpoint.endsWith("BeignTransaction") || log.payload.implicitBeginTransaction) {
            txnLength.put((String)((Map)log.metadata.response).get("transactionId"), new Span(log.metadata.timestamp));
          } else if (! Strings.isNullOrEmpty(log.payload.transactionId)) {
            txnLength.get(log.payload.transactionId).addTime(log.metadata.timestamp);
          }
        }
      } catch (EOFException e) {
        System.err.println("unexpected end of file; skipping");
      }
    }
    System.out.println("total duration: " + duration.duration());
    for (String k : counter.asMap().keySet()) {
      System.out.println(k + ": " + counter.get(k));
    }
    List<Long> times = new ArrayList<>();
    
    
    for (Span s : txnLength.values()) {
      times.add(s.duration());
    }
    Collections.sort(times);
    double meanTime = 0;
    for (Long l : times) {
      meanTime += l;
    }
    
    meanTime /= txnLength.size();
    double stdDev = 0;
    for (Long l : times) {
      stdDev += Math.pow((l - meanTime), 2);
    }
    stdDev /= txnLength.size();
    stdDev = Math.pow(stdDev, 0.5);
    
    // percentiles
    long PERCENTILES = 10;
    for (int i = 0; i <= PERCENTILES; i += 1) {
      System.out.println(
          "percentile " + i * PERCENTILES  + ": "
          + times.get((int) ((times.size()-1) * i / PERCENTILES)));
    }
    
    
    
    
    
    System.out.println("write txns:");
    System.out.println("num: " + txnLength.size() + ", mean:" + meanTime + ", stddev:" + stdDev);
    
  }

}
