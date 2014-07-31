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
package co.mitro.core.servlets;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.regex.PatternSyntaxException;

import javax.servlet.http.HttpServletResponse;

import org.junit.Test;

import co.mitro.test.MockHttpServletRequest;
import co.mitro.test.MockHttpServletResponse;

import com.google.gson.JsonSyntaxException;

public class ServerRejectsServletTest {
  @Test(expected=JsonSyntaxException.class)
  public void testInvalidJson() {
    ServerRejectsServlet.setServerHintsJson("bad JSON");
  }

  @Test(expected=JsonSyntaxException.class)
  public void testNotAnArray() {
    ServerRejectsServlet.setServerHintsJson("{}");
  }

  @Test
  public void testEmpty() {
    ServerRejectsServlet.setServerHintsJson("[]");
  }

  @Test(expected=JsonSyntaxException.class)
  public void testCommentsNotPermitted() {
    ServerRejectsServlet.setServerHintsJson("[//comment\n]");
  }

  @Test(expected=PatternSyntaxException.class)
  public void testInvalidRegexp() {
    ServerRejectsServlet.setServerHintsJson("[{\"regex\": \"*\"}]");
  }

  @Test(expected=NullPointerException.class)
  public void testMissingReject() {
    ServerRejectsServlet.setServerHintsJson("[{\"regex\": \".*\"}]");
  }

  @Test(expected=AssertionError.class)
  public void testEmptySubmitId() {
    ServerRejectsServlet.setServerHintsJson(
        "[{\"regex\": \".*\", \"additional_submit_button_ids\": [null]}]");
  }

  @Test(expected=NullPointerException.class)
  public void testEmptyReject() {
    ServerRejectsServlet.setServerHintsJson("[{\"regex\": \".*\", \"reject\": {}}]");
  }

  @Test(expected=AssertionError.class)
  public void testEmptyAndRule() {
    ServerRejectsServlet.setServerHintsJson(
        "[{\"regex\": \".*\", \"reject\": {\"login_submit\": [[]]}}]");
  }

  @Test(expected=AssertionError.class)
  public void testBadAttribute() {
    ServerRejectsServlet.setServerHintsJson(
        "[{\"regex\": \".*\", \"reject\": {\"login_submit\": [[{\"attributeName\": \"\"}]]}}]");
  }

  @Test
  public void testValidRegexMatch() {
    ServerRejectsServlet.setServerHintsJson(
        "[{\"regex\": \".*\", \"reject\": {\"login_submit\": [], \"submit\": [], \"password\": [[{\"attributeName\": \"name\", \"regexMatch\": \"[abc]+\"}]], \"username\": [], \"form\": []}}]");
  }

  @Test
  public void testValidEmptyReject() {
    ServerRejectsServlet.setServerHintsJson("[{\"regex\": \".*\", \"reject\": {" +
        "\"login_submit\": [], \"submit\": [], \"password\": [], \"username\": []," +
        "\"form\": []}}]");
  }

  @Test
  public void testValidAllowEmptyUsername() {
    ServerRejectsServlet.setServerHintsJson("[{\"regex\": \".*\", \"allow_empty_username\":true," +
        "\"reject\": {\"login_submit\": [], \"submit\": [], \"password\": [], \"username\": []," +
        "\"form\": []}}]");
  }

  @Test(expected=JsonSyntaxException.class)
  public void testInvalidAllowEmptyUsername() {
    ServerRejectsServlet.setServerHintsJson("[{\"regex\": \".*\", \"allow_empty_username\":42," +
        "\"reject\": {\"login_submit\": [], \"submit\": [], \"password\": [], \"username\": []," +
        "\"form\": []}}]");
  }

  @Test
  public void testEmptyPasswordUsernameSelector() {
    try {
      ServerRejectsServlet.setServerHintsJson("[{\"regex\": \".*\", \"reject\": {" +
          "\"login_submit\": [], \"submit\": [], \"password\": [], \"username\": []," +
          "\"form\": []}, \"empty_password_username_selector\": \"\"}]");
      fail("expected exception");
    } catch (AssertionError e) {
      assertThat(e.getMessage(), containsString("empty_password_username_selector"));
    }

    try {
      ServerRejectsServlet.setServerHintsJson("[{\"regex\": \".*\", \"reject\": {" +
          "\"login_submit\": [], \"submit\": [], \"password\": [], \"username\": []," +
          "\"form\": []}, \"empty_password_username_selector\": \"hello\"}]");
      fail("expected exception");
    } catch (AssertionError e) {
      assertThat(e.getMessage(), containsString("empty_password_username_selector"));
    }

    ServerRejectsServlet.setServerHintsJson("[{\"regex\": \".*\", \"reject\": {" +
        "\"login_submit\": [], \"submit\": [], \"password\": [], \"username\": []," +
        "\"form\": []}, \"allow_empty_username\": true, \"empty_password_username_selector\": \"hello\"}]");
  }

  @Test
  public void allowsCrossOriginRequests() throws IOException {
    ServerRejectsServlet servlet = new ServerRejectsServlet();
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    servlet.doGet(request, response);
    assertEquals(HttpServletResponse.SC_OK, response.getStatus());
    assertEquals("*", response.getHeader(Util.CORS_ALLOW_ORIGIN_HEADER));
  }
}
