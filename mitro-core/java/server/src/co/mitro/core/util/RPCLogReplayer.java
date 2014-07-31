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
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import co.mitro.core.server.data.RPC.SignedRequest;
import co.mitro.recordio.JsonRecordReader;

import com.google.common.base.Strings;
import com.google.gson.Gson;

public class RPCLogReplayer {
  public final static Gson GSON = new Gson();  
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
  public final static class SendQuery implements Runnable {
    private SignedRequest request;
    private String url;
    public SendQuery(Request request) {
      this.request = request.request; 
      this.url = "http://127.0.0.1:8080/mitro-core" + request.endpoint;
    }

    @Override
    public void run() { 
      try (CloseableHttpClient client = HttpClients.createDefault()) {

        System.out.println("sending request to " + url);
        HttpPost post = new HttpPost(url);
        StringEntity entity = new StringEntity(GSON.toJson(request), "UTF-8");
        post.setEntity(entity);
        try (CloseableHttpResponse response = client.execute(post)) {
          System.out.println("done: " + response.getStatusLine());
          
        }
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
  }
  
  private static final Random RNG = new Random();
  
  private static double nextExp(double lambda) {
    double u;
    do {
        u = RNG.nextDouble();
    } while (0 == u); 
    return (-Math.log(u)) / lambda;
  }
  private static final class Request {
    public Request(String endpoint, SignedRequest rqst) {
      this.endpoint = endpoint;
      this.request = rqst;
    }
    SignedRequest request;
    String endpoint;
  }
  public static void main(String[] args) throws IOException, InterruptedException, KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException {
 
    List<Request> requests = new ArrayList<>();
    ExecutorService executor = Executors.newFixedThreadPool(5);
    for (int i = 0; i < args.length; ++i) {
      String filename = args[i];
      System.err.println("Reading file: " + filename);
      JsonRecordReader rr = JsonRecordReader.MakeFromFilename(filename);
      JsonRecordReader.JsonLog log;
      try {
        while (null != (log = rr.readJson())) {
          if (Strings.isNullOrEmpty(log.payload.transactionId) && !log.payload.implicitBeginTransaction) {
            // read only transaction
            requests.add(new Request(log.metadata.endpoint, log.payload));
          }
        }
      } catch (EOFException e) {
        System.err.println("unexpected end of file; skipping");
      }
    }
    
    // run the simulation for a while.
    long scaling = 1000;
    double requestsPerMs = 353. / 9805199;
    long START_MS = 0;
    // run for 20 min
    long END_MS = 20*60*1000;
    long now = START_MS;
    int count = 0;
    while (now < END_MS) {
      double toSleep = nextExp(requestsPerMs * scaling);
      now += toSleep;
      ++count;
      Thread.sleep((long)toSleep);
      executor.execute(new SendQuery(requests.get(RNG.nextInt(requests.size()))));
      System.out.println("count: " + count + "\t time:" + now + "\t rate:" + (double)count/now);
    }
    executor.awaitTermination(1, TimeUnit.MINUTES);
    
  }
  
  
  
}
  