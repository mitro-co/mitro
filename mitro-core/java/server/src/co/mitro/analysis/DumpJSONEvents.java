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
package co.mitro.analysis;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.gson.Gson;
import com.j256.ormlite.support.DatabaseConnection;

import co.mitro.core.server.Main;
import co.mitro.core.server.Manager;
import co.mitro.core.server.ManagerFactory;
import co.mitro.core.server.data.DBProcessedAudit;

public class DumpJSONEvents {
  private static final Logger logger = LoggerFactory.getLogger(DumpJSONEvents.class);
  private static Table<String, Integer, String> uniqueMapper = HashBasedTable.create();
  private static SecureRandom random = new SecureRandom();
  private static Gson gson = new Gson();
  public static String map(String type, Integer value) {
    if (null == value) {
      return null;
    }
    String rval = uniqueMapper.get(type, value);
    if (rval == null) {
      rval = new BigInteger(130, random).toString(32);
      uniqueMapper.put(type, value, rval);
    }
    return rval;
  }
  public static final class OutputDatum {
    public static final class OutputProperties {
      String ip;
      String actor_code;
      String secret_code;
      String group_code;
      String affected_user_code;
      long time;
      String distinct_id;
      String transaction_id;
    }
    String event;
    OutputProperties properties = new OutputProperties();
    
    public OutputDatum(DBProcessedAudit audit) {
      event = audit.getAction().toString();
      properties.ip = audit.getSourceIp();
      properties.distinct_id = properties.actor_code = map("user", audit.getActor().getId());
      properties.affected_user_code = map("user", audit.getAffectedUser() != null ? audit.getAffectedUser().getId() : null);
      properties.secret_code = map("secret", audit.getAffectedSecret());
      properties.group_code = map("group", audit.getAffectedGroup() != null ? audit.getAffectedGroup().getId(): null);
      properties.time = audit.getTimestampMs()/1000;
      properties.transaction_id = audit.getTransactionId();
      if ("SIGNUP".equals(event)) {
        // special case for mixpanel
        event = "$signup";
      }
    }
  }
  private static void writeData(String dataFile) throws IOException {
    try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(Paths.get(dataFile).toFile()));) {
      oos.writeObject(uniqueMapper);
    }
  }
  
  @SuppressWarnings("unchecked")
  private static void readData(String dataFile) throws FileNotFoundException, IOException, ClassNotFoundException {
    try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(Paths.get(dataFile).toFile()));) {
      uniqueMapper = (Table<String, Integer, String>) ois.readObject();
      logger.info("Wrote data..." );
    }
  }

  public static void main(String[] args) throws SQLException, IOException, ClassNotFoundException {
    Main.exitIfAssertionsDisabled();
    try {
      readData(args[0]);
    } catch (FileNotFoundException e) {
      logger.warn("Could not locate mapper. Starting new mapping");
    }
    ManagerFactory.unsafeRecreateSingleton(ManagerFactory.ConnectionMode.READ_ONLY);

    try (Manager mgr = ManagerFactory.getInstance().newManager()) {
      // pull some audit logs and emit them.
      for (DBProcessedAudit audit : mgr.processedAuditDao.queryBuilder().query()) {
        OutputDatum od = new OutputDatum(audit);
        System.out.println(gson.toJson(od));
      }
    }
    writeData(args[0]);
    
  }
};
