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
/**
 * 
 */
package co.mitro.core.crypto;

import java.io.File;import java.nio.charset.Charset;
import java.util.List;


import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.gson.Gson;

/**
 * @author vijayp
 *
 */
public class GenerateKeysToFile {

  /**
   * @param args
   */
  public static void main(String[] args) throws Exception {
    // TODO Auto-generated method stub
    long start = System.currentTimeMillis();
    if (args.length < 2) {
      throw new Exception ("Need 2 args: numKeys outputFileName");
    }
    KeyczarKeyFactory kz = new KeyczarKeyFactory();
    final int numKeys = Integer.valueOf(args[0]);
    
    List<String> keys = Lists.newLinkedList();
    for (int i = 0; i < numKeys; ++i) {
      System.out.println("generating key " + i);
      keys.add(kz.generate().toString());
    }
    Gson gson = new Gson();
    String out = gson.toJson(keys);
    Files.write(out, new File(args[1]), Charset.forName("UTF-8"));
    
    System.out.println("done in " + (System.currentTimeMillis() - start) + " ms.");
    
  }

}
