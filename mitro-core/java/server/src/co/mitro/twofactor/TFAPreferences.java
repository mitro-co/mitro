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
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

import javax.servlet.annotation.WebServlet;

import co.mitro.core.crypto.KeyInterfaces.KeyFactory;
import co.mitro.core.server.Manager;
import co.mitro.core.server.ManagerFactory;
import co.mitro.core.server.data.DBIdentity;

@WebServlet("/TwoFactorAuth/TFAPreferences")
public class TFAPreferences extends UserSignedTwoFactorServlet {
  private static final long serialVersionUID = 1L;
  
  public TFAPreferences(ManagerFactory managerFactory, KeyFactory keyFactory) {
    super(managerFactory, keyFactory, HttpMethod.GET);
  }

  public static class TemplateData {
    public String user;
    public boolean enabledTwoFactorAuth;
    public String encodedUser;
    public String token;
    public String signature;
    public String date;
    public int numAvailableBackups;
    public String errorMessage;

    public TemplateData(DBIdentity identity,
        String username,
        String token, String signature, String date,
        int numAvailableBackups, String errorMessage) {
      this.user = username;
      this.enabledTwoFactorAuth = identity.isTwoFactorAuthEnabled();
      this.token = token;
      this.signature = signature;
      this.date = date;
      this.errorMessage = errorMessage;
      this.numAvailableBackups = numAvailableBackups;
    }
  }

  public static class BackupsTemplateData {
    public String[] backupCodes;
    public boolean resetBackupCodes;

    public BackupsTemplateData(String[] backups, boolean resetBackupCodes) {
      this.backupCodes = Arrays.copyOf(backups, backups.length);
      this.resetBackupCodes = resetBackupCodes;
    }
  }

  @Override
  protected void processRequest(TwoFactorRequestContext context)
      throws IOException, SQLException {
    TwoFactorRequestParams params = context.params;
    DBIdentity identity = context.identity;

    DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy");
    Date date = new Date(identity.getEnabledTFAMs());
    String dateEnabled = dateFormat.format(date);

    int numAvailableBackups = identity.getNumAvailableBackups();
    String error = null;

    boolean disableTwoFactor = context.request.getParameter("disable") != null;
    boolean resetBackupCodes = context.request.getParameter("reset") != null;

    if (disableTwoFactor || resetBackupCodes) {
      Manager manager = context.manager;
      String secret = identity.getTwoFactorSecret();
      long t = System.currentTimeMillis();

      error = verifyCode(params.codeString, secret, t, identity, manager);
      if (error == null) {
        if (disableTwoFactor) {
          for (int i = 0; i < CryptoForBackupCodes.NUM_BACKUP_CODES; i++) {
            identity.setBackup(i, null);
          }
          identity.setTwoFactorSecret(null);
          manager.identityDao.update(identity);
          manager.commitTransaction();
        } else if (resetBackupCodes) {
          String[] newBackups = CryptoForBackupCodes.generateBackupCodesForUser(identity);
          manager.identityDao.update(identity);
          manager.commitTransaction();

          BackupsTemplateData templateData = new BackupsTemplateData(newBackups, resetBackupCodes);
          renderTemplate("Backups.mustache", templateData, context.response);
          return;
        }
      }
    }
    renderTemplate(
        "TFAPreferences.mustache",
        new TemplateData(identity, params.username, params.token, params.signature,
            dateEnabled, numAvailableBackups, error),
        context.response);
  }
}
