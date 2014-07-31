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

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;

import javax.servlet.ServletException;

import org.junit.Before;
import org.junit.Test;
import org.keyczar.exceptions.KeyczarException;

import co.mitro.core.crypto.KeyInterfaces.CryptoError;
import co.mitro.test.MockHttpServletRequest;
import co.mitro.test.MockHttpServletResponse;

public class BackupsTest extends TwoFactorTests {
  private TFAPreferences servlet;
  private String oldBackup;

  @Before
  public void setupBackupsTest() throws CryptoError {
    servlet = new TFAPreferences(managerFactory, keyFactory);
    oldBackup = testIdentity.getBackup(0);
  }

  //to be used to check correct and incorrect codes
  public MockHttpServletResponse testDoGet(String code)
      throws ServletException, IOException, SQLException, CryptoError {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    request.setParameter("token", twoFactorData.testToken);
    // the token for the backup servlet is signed with the *user's* key
    request.setParameter("signature", testIdentityKey.sign(twoFactorData.testToken));
    request.setParameter("code", code);
    request.setParameter("secret", twoFactorData.testSecret);
    request.setParameter("reset", "true");
    servlet.doGet(request, response);

    manager.identityDao.refresh(testIdentity);
    return response;
  }

  @Test
  public void testDoGetSuccess() throws ServletException, IOException,
      InvalidKeyException, NoSuchAlgorithmException, SQLException,
      KeyczarException, CryptoError {
    MockHttpServletResponse response = testDoGet(twoFactorData.validTimeCode);
    // check that the token worked and that the correct page was rendered.
    assertThat(response.getOutput(),
        containsString("Your one-time backup emergency codes are below."));

    // asserts that the backup code is now different
    assertTrue(!testIdentity.getBackup(0).equals(oldBackup));
  }

  // test wrong code. make sure it fails
  @Test
  public void testDoGetWrongCode() throws ServletException, IOException,
      InvalidKeyException, NoSuchAlgorithmException, SQLException,
      KeyczarException, CryptoError {
    // create incorrect code
    String wrongCode = "123456";
    MockHttpServletResponse response = testDoGet(wrongCode);
    assertThat(response.getOutput(),
        containsString(TwoFactorServlet.INCORRECT_CODE_ERROR_MESSAGE));

    // asserts that the oldBackup is the same as the newBackup, that because it
    // failed it didn't change.
    assertEquals(oldBackup, testIdentity.getBackup(0));
  }

  @Test
  public void testEmailHarvesting() throws ServletException, IOException {
    doEmailHarvestingTest(servlet);
  }
}
