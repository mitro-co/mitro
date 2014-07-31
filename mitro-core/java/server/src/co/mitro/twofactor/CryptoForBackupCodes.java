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

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.SQLException;

import org.apache.commons.codec.binary.Base64;

import co.mitro.core.server.Manager;
import co.mitro.core.server.data.DBIdentity;

import com.google.common.base.Charsets;

public class CryptoForBackupCodes {
  public static final int NUM_BACKUP_CODES = 10;
  public static final int BACKUP_CODE_LENGTH = 9;
  // TODO: compute MAX_BACKUP from BACKUP_CODE_LENGTH
  public static final int MAX_BACKUP = 1000000000;
  private static final int SALT_LENGTH = 8; //the web site recommended 8 byte salt or more
  private static final SecureRandom randomGenerator = new SecureRandom();

  //random salt generation
  public static byte[] randSaltGen() {
    byte[] salt = new byte[SALT_LENGTH];
    randomGenerator.nextBytes(salt);
    return salt;
  }

  //appending 2 byte[]. when putting salt and code together, put salt first. When putting undigested salt and digest together, put salt first.
  public static byte[] appendByteArrays(byte[] first, byte[] second) {
    byte[] combination = new byte[first.length + second.length];
    System.arraycopy(first, 0, combination, 0, first.length);
    System.arraycopy(second, 0, combination, first.length, second.length);
    return combination; //works. isn't problem
  }

  //for when I change the code into byte format before digest
  public static byte[] stringToByte(String code) {
    byte[] newCodeInBytes = null;
    try {
      newCodeInBytes = code.getBytes("UTF-8");
    } catch (UnsupportedEncodingException e) {
    }
    return newCodeInBytes;
  }

  //digesting the salt+code byte array. This has to be iterated at least 1000 times
  public static byte[] digestSaltCode(byte[] saltCode)  {
    MessageDigest md;
    try {
      md = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      // TODO Auto-generated catch block
      throw new RuntimeException(e); 
    }
    md.update(saltCode);
    return md.digest();
  }

  //for saving into database
  public static String digestToStringEncode(byte[] saltCode) {
    String toStore = new String(Base64.encodeBase64(saltCode), Charsets.UTF_8);
    //assert toBytes = Base64.decode(saltCode.getBytes()) is toStore
    return toStore;
  }

  //for decoding what's stored in the database to be compared to digested code user puts in
  public static byte[] encodedStringToBytes(String saltCode) {
    byte[] toBytes = null;
    byte[] bytes = null;
    try {
      bytes = saltCode.getBytes("UTF-8");
    } catch (UnsupportedEncodingException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    toBytes = Base64.decodeBase64(bytes);

    return toBytes;
  }

  //retrieve the salt from the database digest (after it has been converted back into a byte[] of course, using encodedStringToBytes above)
  public static byte[] getSaltFromDigest(byte[] digest) { //only method that isn't quite on point. when works, can take out the randSalt static final ini TempVerify
    byte[] salt = new byte[SALT_LENGTH];
    for (int i = 0; i < SALT_LENGTH; i++)
      salt[i] = digest[i];
    return salt;
  }

  // Checks if codeString is a valid backup code and, if so, resets the backup code so it cannot be reused.
  // Returns true if a backup code was used and false otherwise.
  // WARNING: does not commit the reset to the db.  It is the caller's responsibility to commit.
  // TODO: Commit this update in a separate transaction.
  public static boolean tryBackupCode(String secret, String codeString, DBIdentity identity, Manager manager)
      throws SQLException {
    if (codeString.length() == CryptoForBackupCodes.BACKUP_CODE_LENGTH) {
      for (int i = 0; i < CryptoForBackupCodes.NUM_BACKUP_CODES; i++) {
        String backupCode = identity.getBackup(i);
        if (backupCode != null) {
          byte[] salt = CryptoForBackupCodes.getSaltFromDigest(
              CryptoForBackupCodes.encodedStringToBytes(backupCode));
          String digestedCode = CryptoForBackupCodes.digest(codeString, salt);

          if (digestedCode.equals(backupCode)) {
            identity.setBackup(i, null);
            manager.identityDao.update(identity);
            return true;
          }
        }
      }
    }
    return false;
  }

  public static String digest(String backupCode, byte[] salt){
    byte[] backupCodeBytes = CryptoForBackupCodes.stringToByte(backupCode);
    byte[] appendedSaltByte = CryptoForBackupCodes.appendByteArrays(salt, backupCodeBytes);
    byte[] digestedSaltCode = appendedSaltByte;
    for (int i = 0; i<1000; i++) {
      digestedSaltCode = CryptoForBackupCodes.digestSaltCode(digestedSaltCode);
    }
    byte[] undigestedSaltAndDigested = CryptoForBackupCodes.appendByteArrays(salt, digestedSaltCode);
    String digestedTogether = CryptoForBackupCodes.digestToStringEncode(undigestedSaltAndDigested);
    return digestedTogether;
  }

  public static String generateBackupCode() {
    return String.format("%0" + BACKUP_CODE_LENGTH + "d",
        randomGenerator.nextInt(MAX_BACKUP));
  }

  public static String[] generateBackupCodesForUser(DBIdentity identity) {
    String[] backups = new String[NUM_BACKUP_CODES];
    String[] digestedBackups = new String[NUM_BACKUP_CODES];
    for (int i = 0; i < backups.length; i++) {
      backups[i] = generateBackupCode();
      byte[] randSalt = CryptoForBackupCodes.randSaltGen();
      digestedBackups[i] = CryptoForBackupCodes.digest(backups[i], randSalt);
      identity.setBackup(i, digestedBackups[i]);
    }
    return backups;
  }
}
