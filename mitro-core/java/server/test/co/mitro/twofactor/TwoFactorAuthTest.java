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

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.junit.Before;
import org.junit.Test;

import co.mitro.test.MockHttpServletRequest;
import co.mitro.test.MockHttpServletResponse;

public class TwoFactorAuthTest extends TwoFactorTests {
  TwoFactorAuth servlet;

  @Before
  public void setup() {
    servlet = new TwoFactorAuth(managerFactory, keyFactory);
  }

  public MockHttpServletResponse testDoGet(String token, String signature)
      throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    request.setParameter("token", token);
    request.setParameter("signature", signature);
    servlet.doGet(request, response);
    return response;
  }

  @Test
  public void testDoGetSuccess() throws ServletException, IOException {
    MockHttpServletResponse response =
        testDoGet(twoFactorData.testToken, twoFactorData.testSignature);
    //should let you sign in if you token/signature is correct
    assertEquals(HttpServletResponse.SC_OK, response.getStatus());
    assertThat(response.getOutput(), containsString("Sign In"));
  }

  @Test(expected=ServletException.class)
  public void testDoGetFailure() throws ServletException, IOException {
    // bad signature (fails validation)
    testDoGet(twoFactorData.testToken, twoFactorData.testSignature + "A");
  }

  @Test
  public void testEmailHarvesting() throws ServletException, IOException {
    doEmailHarvestingTest(servlet);
  }
}
