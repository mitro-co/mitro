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
package co.mitro.core.server;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;

import org.keyczar.DefaultKeyType;
import org.keyczar.KeyczarFileReader;
import org.keyczar.Signer;
import org.keyczar.enums.KeyPurpose;
import org.keyczar.exceptions.KeyczarException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.mitro.keyczar.Util;

/** Contains all secrets for the Mitro Core server. */
public class SecretsBundle {
  private static final Logger logger = LoggerFactory.getLogger(SecretsBundle.class);
  private static final String SIGNING_RELATIVE_PATH = "sign_keyczar";

  private final Signer signingKey;

  /** Loads secrets from path. */
  public SecretsBundle(String path) {
    String subPathString = new File(path, SIGNING_RELATIVE_PATH).getPath();
    logger.info("loading signing key from {}", subPathString);
    try {
      signingKey = new Signer(new KeyczarFileReader(subPathString));
    } catch (KeyczarException e) {
      throw new RuntimeException("Unable to load signing key", e);
    }
  }

  private SecretsBundle(Signer signingKey) {
    this.signingKey = checkNotNull(signingKey);
  }

  /** Returns the signature for input, using the signing key. */
  public String signToken(String input) throws KeyczarException {
    return signingKey.sign(input);
  }

  /**
   * Returns true if signature is valid for data, using the signing key. Keyczar throws exceptions
   * for many cases of malformed signatures, but this instead returns false.
   */
  public boolean verifyToken(String data, String signature) {
    if (signature.length() == 0) {
      // throws ArrayIndexOutOfBoundsException with current Keyczar
      return false;
    }

    try {
      return signingKey.verify(data, signature);
    } catch (KeyczarException e) {
      // thrown if input length, version, or key doesn't match.
      return false;
    }
  }

  /** Returns a new SecretsBundle with random test secrets. */
  public static SecretsBundle generateForTest() {
    try {
      Signer signer = new Signer(Util.generateKeyczarReader(
          DefaultKeyType.HMAC_SHA1, KeyPurpose.SIGN_AND_VERIFY));
      return new SecretsBundle(signer);
    } catch (KeyczarException e) {
      throw new RuntimeException("Error generating signing key", e);
    }
  }

  /** Signs a string using a SecretBundle. Used to debug a token signature verification error. */
  public static void main(String[] arguments) throws KeyczarException {
    if (arguments.length != 2) {
      System.err.println("SecretsBundle (path) (string to sign)");
      System.exit(1);
    }
    String secretsPath = arguments[0];
    String data = arguments[1];

    System.out.println("Signing string: " + data);
    SecretsBundle secrets = new SecretsBundle(secretsPath);
    String signature = secrets.signToken(data);
    System.out.println("Signature: " + signature);
  }
}
