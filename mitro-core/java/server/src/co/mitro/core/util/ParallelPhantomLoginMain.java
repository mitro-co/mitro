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

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Charsets;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class ParallelPhantomLoginMain {
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
  private static final JsonParser JSON_PARSER = new JsonParser();
  public final static void safePrintln(String s) {
    synchronized (System.out) {
      System.out.println(s);
    }
  }
  static final Map<String, Map<String, String>> newData = new HashMap<>();
  static Map<String, Map<String, String>> oldData = new HashMap<>();
  static synchronized final void addFlattenedSiteData(String key, Map<String, String> data) {
    newData.put(key, data);
    if (oldData.containsKey(key)) {
      MapDifference<String, String> differences = Maps.difference(data, oldData.get(key));
      if (differences.areEqual()) {
        safePrintln("data for " + key + " has not changed.");
        return;
      } else {
        safePrintln("data for " + key + " differs:");
        for (String s : differences.entriesOnlyOnLeft().keySet()) {
          safePrintln("\tNew key " + s);
        }
        for (String s : differences.entriesOnlyOnRight().keySet()) {
          safePrintln("\tMissing key " + s);
        }
        for (String s : differences.entriesDiffering().keySet()) {
          safePrintln("\tKey[" + key + "]"
              + " old:" + differences.entriesDiffering().get(s).rightValue()
              + " new:" + differences.entriesDiffering().get(s).leftValue());
        }
      }
    } else {
      safePrintln("no old data for " + key);
    }
  }
  
  
  public static final class RunPhantom implements Runnable {
    private String phantomLocation;
    private String urlToExamine;
    public RunPhantom(String loc, String url) {
      phantomLocation = loc;
      urlToExamine = url;
    }
    public static Map<String, String> flattenToMap(JsonElement json, String prefix, Map<String,String> out) {
      if (json.isJsonObject()) {
        for (Map.Entry<String, JsonElement> i : ((JsonObject)json).entrySet()) {
          flattenToMap(i.getValue(), prefix + '|' + i.getKey(), out);
        }
      } else {
        out.put(prefix, json.getAsString());
      }
      return out;
    }

    
    @Override
    public void run() {
      ProcessBuilder pb = new ProcessBuilder("/usr/local/bin/phantomjs", phantomLocation, urlToExamine, "u", "p");
      try {
        Process p = pb.start();
        p.getOutputStream().close();
        p.waitFor();
        String formData = CharStreams.toString(new InputStreamReader(p.getInputStream(), "UTF-8" ));
        
        // Form data looks like this:
        /*
         * ""
         * {"id":"ssoform","passwordField":{"id":"Pword","itemNo":1,"maxlength":16,"name":"Pword","pointer":"","type":"password","value":""},"submitField":{"class":"btn btn-primary","id":"login-btn","itemNo":2,"pointer":"","type":"button","value":"Log in"},"usernameField":{"id":"username","itemNo":0,"name":"Userid","pointer":"","type":"text","value":""}}
         */
        JsonElement elem = JSON_PARSER.parse(formData);
        if (elem.isJsonObject()) {
          JsonObject data = (JsonObject) elem;
          Map<String, String> values = flattenToMap(data, urlToExamine, new HashMap<String, String>());
          addFlattenedSiteData(urlToExamine, values);
        } else {
          safePrintln("unknown data for " + urlToExamine + ": " + formData);
          addFlattenedSiteData(urlToExamine, new HashMap<String, String>());
        }
      } catch (InterruptedException | IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    
  }
  @SuppressWarnings("unchecked")
  public static void main(String[] args) throws IOException, InterruptedException {
    if (args.length != 3 && args.length != 4) {
      
      System.err.println("args: phantom_script_location urlFile");
      System.err.println();
      System.err.println("e.g.: java -ea -cp build/mitrocore.jar co.mitro.core.util.ParallelPhantomLoginMain 20 /Users/vijayp/src/mitro-co/browser-ext/login/phantom-test/guess_form.js /tmp/urls [old_file]");
      return;
    }
    String oldDatafile = (args.length == 4) ? args[3] : null;
    String urlFile = args[2];
    String jsFile = args[1];
    if (oldDatafile != null) {
      try (Reader reader = new FileReader(oldDatafile)) {
        oldData = GSON.fromJson(reader, oldData.getClass());
      }
    } else {
      System.err.println("WARNING! not using old data for comparison");
    }
    int numThreads = Integer.parseInt(args[0]);
    long runtime = System.currentTimeMillis();
    ExecutorService pool = Executors.newFixedThreadPool(numThreads);
    for (String u : Files.readLines(new File(urlFile), Charsets.UTF_8)) {
      pool.execute(new RunPhantom(jsFile, u));
    }
    pool.shutdown();
    pool.awaitTermination(1, TimeUnit.DAYS);
    System.out.println("DONE");
    try (Writer writer = new FileWriter("run." + runtime + ".json")) {
      GSON.toJson(newData, writer);
    }
    
    
    
  }

}
