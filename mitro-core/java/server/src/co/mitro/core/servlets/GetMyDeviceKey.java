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
package co.mitro.core.servlets;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import javax.servlet.annotation.WebServlet;

import org.keyczar.DefaultKeyType;
import org.keyczar.GenericKeyczar;
import org.keyczar.enums.KeyPurpose;
import org.keyczar.exceptions.KeyczarException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.mitro.core.exceptions.MitroServletException;
import co.mitro.core.server.Manager;
import co.mitro.core.server.data.DBDeviceSpecificInfo;
import co.mitro.core.server.data.DBIdentity;
import co.mitro.core.server.data.RPC;
import co.mitro.core.server.data.RPC.MitroRPC;
import co.mitro.keyczar.JsonWriter;
import co.mitro.keyczar.Util;

import com.google.common.collect.ImmutableMap;

/**
 * Servlet used by the client to retrieve an AES key which 
 * it should use to encrypt stuff on local disk.
 */ 
@WebServlet("/api/GetMyDeviceKey")
public class GetMyDeviceKey extends MitroServlet {
  private static final Logger logger = LoggerFactory.getLogger(GetMyDeviceKey.class);
  private static final long serialVersionUID = 1L;

  @Override
  protected MitroRPC processCommand(MitroRequestContext context) throws IOException, SQLException, MitroServletException {
    RPC.GetMyDeviceKeyRequest in = gson.fromJson(context.jsonRequest,
        RPC.GetMyDeviceKeyRequest.class);
    
    RPC.GetMyDeviceKeyResponse out = new RPC.GetMyDeviceKeyResponse();
    
    out.deviceKeyString = maybeGetOrCreateDeviceKey(
        context.manager,
        context.requestor, 
        in.deviceId, false, context.platform);
    return out;
  }

  protected static String maybeGetOrCreateDeviceKey(
      Manager manager, DBIdentity requestor, String deviceId, 
      boolean forLogin, String platform)
      throws SQLException, MitroServletException {
    // give the client a key!
    // search DB 
    if (deviceId == null) {
      return null;
    }

    List<DBDeviceSpecificInfo> devInfos = null;
    try {
       // Note: queryForFieldValuesArgs escapes using SelectArg.
       devInfos = manager.deviceSpecificDao.queryForFieldValuesArgs(
          ImmutableMap.of(
              DBDeviceSpecificInfo.DEVICE_ID_NAME, deviceId,
              DBDeviceSpecificInfo.IDENTITY_FIELD_NAME, requestor
              ));
    } catch (Exception e) {
      logger.error("ignoring weird error probably utf-8", e);
    }

    DBDeviceSpecificInfo devInfo;
    boolean keyInDb = !(null == devInfos || devInfos.isEmpty());
    if (keyInDb) {
      assert (devInfos.size() == 1);
      devInfo = devInfos.get(0);
      devInfo.setLastUseSec(System.currentTimeMillis() / 1000.0); 
      
      if (forLogin) {
        // TODO: check expiration
      }
      // TODO: this will break operations on the replica.
      // TODO: we should have a way of easily detecting if we are on the replica or not.
      // enable this when we check expiration, above.
      // manager.deviceSpecificDao.update(devInfo);
      //
      
    } else if (!keyInDb && !forLogin) {
      devInfo = new DBDeviceSpecificInfo();
      devInfo.setLastUseSec(System.currentTimeMillis()/1000.0);
      devInfo.setUser(requestor);
      devInfo.setPlatform(platform);
      // generate an AES key
      GenericKeyczar key;
      try {
        key = Util.createKey(DefaultKeyType.AES, KeyPurpose.DECRYPT_AND_ENCRYPT);
      } catch (KeyczarException e) {
        throw new MitroServletException(e);
      }
      devInfo.setClientLocalStorageKey(JsonWriter.toString(key));
      try {
        devInfo.setDeviceId(deviceId);
        manager.deviceSpecificDao.create(devInfo);
      } catch (Exception e) {
        logger.error("ignoring weird error probably utf-8", e);
      }
    } else {
      // no key in db and they want to log in. Sadly, we can't provide the key!
      devInfo = null;
    }
    return (null == devInfo) ? null : devInfo.getClientLocalStorageKey();
  }
  public static String maybeGetClientKeyForLogin(
      Manager manager, DBIdentity identity, String deviceId, String platform) throws SQLException, MitroServletException {
    return maybeGetOrCreateDeviceKey(manager, identity, deviceId, true, platform);
  }

}
