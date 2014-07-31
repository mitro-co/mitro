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
package co.mitro.twofactor;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;

import javax.servlet.annotation.WebServlet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.mitro.core.crypto.KeyInterfaces.KeyFactory;
import co.mitro.core.server.Manager;
import co.mitro.core.server.ManagerFactory;
import co.mitro.core.server.data.DBIdentity;

@WebServlet("/TwoFactorAuth/TempVerify")
public class TempVerify extends UserSignedTwoFactorServlet {
  private static final long serialVersionUID = 1L;
  private static final Logger logger = LoggerFactory.getLogger(TempVerify.class);

  public TempVerify(ManagerFactory managerFactory, KeyFactory keyFactory) {
    super(managerFactory, keyFactory, HttpMethod.POST);
  }

  public static class TemplateData {
    public String[] backupCodes;

    public TemplateData(String[] backups) {
      this.backupCodes = Arrays.copyOf(backups, backups.length);
    }
  }

  @Override
  protected void processRequest(TwoFactorRequestContext context)
      throws IOException, SQLException {
    TwoFactorRequestParams params = context.params;
    DBIdentity identity = context.identity;
    Manager manager = context.manager;
    String secret = context.request.getParameter("secret");
    long t = System.currentTimeMillis();

    String error = verifyCode(params.codeString, secret, t, identity, manager);
    boolean verifiedCode = error == null;
    logger.info("Enabling 2FA for user {} code verified: {}", identity.getName(), verifiedCode);
    if (verifiedCode) {
      String[] backups = CryptoForBackupCodes.generateBackupCodesForUser(identity);
      identity.setTwoFactorSecret(secret);
      identity.setEnabledTFAMs(t);
      identity.setLastAuthMs(t);
      context.manager.identityDao.update(identity);
      context.manager.commitTransaction();

      TemplateData templateData = new TemplateData(backups);
      renderTemplate("Backups.mustache", templateData, context.response);
    } else {
      NewUser.TemplateData templateData =
          new NewUser.TemplateData(params.username, secret, params.token, params.signature, error);
      renderTemplate("NewUser.mustache", templateData, context.response);
    }
  }
}
