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
package co.mitro.core.servlets;

import java.sql.SQLException;
import java.util.List;

import javax.servlet.annotation.WebServlet;

import co.mitro.core.exceptions.InvalidRequestException;
import co.mitro.core.exceptions.MitroServletException;
import co.mitro.core.server.data.DBAudit;
import co.mitro.core.server.data.DBDeviceSpecificInfo;
import co.mitro.core.server.data.RPC;
import co.mitro.core.server.data.RPC.MitroRPC;

import com.google.common.collect.Lists;


@WebServlet("/api/EditEncryptedPrivateKey")
public class EditEncryptedPrivateKey extends MitroServlet {
  private static final long serialVersionUID = 1L;
  private static final int MIN_KEY_LENGTH = 500;
  private static final int MAX_KEY_LENGTH = 50000;

  @Override
  protected boolean isReadOnly() {
    return false;
  }

  @Override
  protected MitroRPC processCommand(MitroRequestContext context)
      throws SQLException, MitroServletException {
    RPC.EditEncryptedPrivateKeyRequest in = gson.fromJson(context.jsonRequest,
        RPC.EditEncryptedPrivateKeyRequest.class);
    
    // this will throw an exception if the person has 2fa enabled but the token/signature isn't provided or is incorrect
    CheckTwoFactorRequired.checkTwoFactorSecret(context, in);

    // TODO: Remove this once the client code no longer sends this id
    @SuppressWarnings("deprecation")
    String deprecatedId = in.userId;
    throwIfRequestorDoesNotEqualUserId(context.requestor, deprecatedId);

    if (in.encryptedPrivateKey == null || in.encryptedPrivateKey.length() < MIN_KEY_LENGTH
        || in.encryptedPrivateKey.length() > MAX_KEY_LENGTH) {
      throw new InvalidRequestException("invalid key");
    }

    assert (in.deviceId != null);
    // invalidate all existing devices except this one
    // TODO: More efficient using DeleteBuilder, but more complicated
    List<DBDeviceSpecificInfo> devices = context.manager.deviceSpecificDao.queryForEq(
        DBDeviceSpecificInfo.IDENTITY_FIELD_NAME, context.requestor);
    List<DBDeviceSpecificInfo> devicesToRemove = Lists.newArrayList();
    for (DBDeviceSpecificInfo dsi : devices) {
      if (!dsi.getDeviceId().equals(in.deviceId)) {
        devicesToRemove.add(dsi);
      }
    }
    // verify we found the current device in the list
    assert devicesToRemove.size() == devices.size() - 1;
    context.manager.deviceSpecificDao.delete(devicesToRemove);

    context.requestor.setChangePasswordOnNextLogin(false);
    context.requestor.setEncryptedPrivateKeyString(in.encryptedPrivateKey);
    context.manager.identityDao.update(context.requestor);
    context.manager.addAuditLog(DBAudit.ACTION.EDIT_ENCRYPTED_PRIVATE_KEY, null, null, null, null, null);
    RPC.EditEncryptedPrivateKeyResponse out = new RPC.EditEncryptedPrivateKeyResponse();
    return out;
  }
}
