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

import static com.google.common.base.Preconditions.checkNotNull;

import org.keyczar.exceptions.KeyczarException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.mitro.core.server.SecretsBundle;

public class TwoFactorSigningService {
  public static final int TIME_FOR_2FA = 600000;

  private static final Logger logger = LoggerFactory.getLogger(TwoFactorAuth.class);

  private static SecretsBundle secrets;

  public static void initialize(SecretsBundle secrets) {
    if (TwoFactorSigningService.secrets != null) {
      logger.warn("re-setting secrets; this should not happen in production");
    }
    TwoFactorSigningService.secrets = checkNotNull(secrets);
  }

  public static boolean isInitialized() {
    return secrets != null;
  }

  public static void checkInitialized() {
    if (!isInitialized()) {
      throw new RuntimeException("Error: secrets not loaded; call TwoFactorSigningService.initialize()");
    }
  }

  public static String signToken(String input) throws KeyczarException {
    checkInitialized();
    return secrets.signToken(input);
  }

  public static boolean verifyTimestamp(Long timestampMs, long expirationMs) {
    return timestampMs != null && System.currentTimeMillis() - timestampMs < expirationMs;
  }

  public static boolean verifySignature(String data, String signature) {
    checkInitialized();
    return data != null && signature != null && secrets.verifyToken(data, signature);
  }

  public static boolean verifySignatureAndTimestamp(String data, String signature, Long timestampMs) {
    return verifySignature(data, signature) && verifyTimestamp(timestampMs, TIME_FOR_2FA);
  }
}
