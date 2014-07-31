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
package co.mitro.twofactor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

public class CryptoForBackupCodesTest {

  //test encode/decode works
  @Test
  public void testEncodeDecode() {
    byte[] a = {68, -3, 18, 109, -89, -3, -15, -29, 44, 34, -117, 11, 22, -42, -27, 6, 58, -95, 2, 54, 33, 6, -82, 7, 57, -65, 85, 94};
    byte[] b = CryptoForBackupCodes.encodedStringToBytes(CryptoForBackupCodes.digestToStringEncode(a)); //a is being first encoded and then decoded. b is set to that
    assertEquals(Arrays.toString(a), Arrays.toString(b)); //check a and b are the same
  }
  
  //tests that getting the salt back from the stored String works
  @Test
  public void testGetSalt() {
    byte[] first= {68, -3, 18, 109, -89, -3, -15, -29};
    byte[] second= {44, 34, -117, 11, 22, -42, -27, 6, 58, -95, 2, 54, 33, 6, -82, 7, 57, -65, 85, 94};
    byte[] salt = CryptoForBackupCodes.getSaltFromDigest(CryptoForBackupCodes.appendByteArrays(first, second)); //this function gets the first 8 bites from the byte[]. gets salt
    assertEquals(Arrays.toString(first), Arrays.toString(salt)); 
  }
  
  //test 2 generated salts aren't equal and length of salt
  @Test
  public void testGenerateSalt() {
    byte[] firstSalt = CryptoForBackupCodes.randSaltGen();
    byte[] secondSalt = CryptoForBackupCodes.randSaltGen();
    assertFalse(Arrays.equals(firstSalt, secondSalt)); //check that salts really are random and unique
    assertEquals(firstSalt.length, 8); //check length of salts
    assertEquals(secondSalt.length, 8);
  }
  
  //test that same input will give same output for digesting
  @Test
  public void testDigest()  {
    String backupCode1 = "123456789";
    String backupCode2 = "123456789";
    String backupCode3 = "987654321";
    byte[] salt = CryptoForBackupCodes.randSaltGen();
    byte[] otherSalt = CryptoForBackupCodes.randSaltGen();
    //backupCode1 and backupCode2 have the same value. should digest to the same String using the same salt
    assertEquals(CryptoForBackupCodes.digest(backupCode1, salt), CryptoForBackupCodes.digest(backupCode2, salt));
    //backupCode1 and backupCode3 don't have same value. shouldn't digest to the same String using the same salt
    assertTrue(!(CryptoForBackupCodes.digest(backupCode1, salt).equals(CryptoForBackupCodes.digest(backupCode3, salt))));
    //backupCode1 and backupCode2 shouldn't digest to same value with different salts
    assertFalse(Arrays.equals(salt, otherSalt));
    assertTrue(!(CryptoForBackupCodes.digest(backupCode1, salt).equals(CryptoForBackupCodes.digest(backupCode2, otherSalt))));
  }

}
