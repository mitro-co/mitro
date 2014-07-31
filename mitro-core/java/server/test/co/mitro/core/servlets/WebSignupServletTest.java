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

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.sql.SQLException;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Before;
import org.junit.Test;

import co.mitro.core.server.data.DBSignup;
import co.mitro.core.util.Random;
import co.mitro.test.MockHttpServletRequest;
import co.mitro.test.MockHttpServletResponse;

public class WebSignupServletTest extends MemoryDBFixture {
  private WebSignupServlet servlet;
  
  private HttpServletRequest createSignupRequest(String email,
      String userAgent, String browser, String trackingData) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setParameter("email",  email);
    request.setParameter("user_agent",  userAgent);
    request.setParameter("browser",  browser);
    request.setParameter("tracking_data", trackingData);
    
    return request;
  }
  
  private String createRandomEmailAddress() {
    StringBuilder sb = new StringBuilder();
    sb.append(Random.makeRandomAlphanumericString(8));
    sb.append("@");
    sb.append(Random.makeRandomAlphanumericString(8));
    return sb.toString();
  }
  
  @Before
  public void setUp() {
    replaceDefaultManagerDbForTest();
    servlet = new WebSignupServlet();
  }
  
  @Test
  public void testSignup() throws IOException, SQLException, URISyntaxException {
    String email = "foo+" + createRandomEmailAddress();
    String browser = "chrome";
    String userAgent = "Chrome";
    String trackingData = "glcid=123";
    
    HttpServletRequest request = createSignupRequest(email, userAgent, browser, trackingData);
    HttpServletResponse response = new MockHttpServletResponse();
    servlet.doPost(request, response);
    assertEquals(HttpServletResponse.SC_MOVED_TEMPORARILY, response.getStatus());

    List<DBSignup> signups = manager.signupDao.queryForEq("email", email);
    assertEquals(1, signups.size());
    
    DBSignup signup = signups.get(0);
    assertEquals(email, signup.getEmail());
    assertEquals(browser, signup.getBrowser());
    assertEquals(userAgent, signup.getUserAgent());
    assertEquals(trackingData, signup.getTrackingData());

    // Parse the fragment
    String fragment = response.getHeader("Location").split("#", 2)[1];
    fragment = URLDecoder.decode(fragment, "UTF-8");
    assertEquals("u=" + email, fragment);
  }
  
  @Test
  public void testMissingEmail() throws IOException, SQLException {
    String email = "";
    String browser = "chrome";
    String userAgent = "Chrome";
    String trackingData = "glcid=123";
    
    HttpServletRequest request = createSignupRequest(email, userAgent, browser, trackingData);
    HttpServletResponse response = new MockHttpServletResponse();
    servlet.doPost(request, response);
    assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatus());
  }
}
