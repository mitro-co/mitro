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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import co.mitro.test.MockHttpServletResponse;

public class UtilTest {
  @Test(expected=NullPointerException.class)
  public void testIsEmailAddressNull() {
    Util.isEmailAddress(null);
  }

  @Test
  public void testIsEmailAddress() {
    String badAddresses[] = {
        "",
        " \t",
        "example.com",
        "@example.com",
        "user@",
        " close@example.com",
        "close @example.com",
        "close@ example.com",
        "close@example.com ",
    };

    for (String address : badAddresses) {
      assertFalse(address, Util.isEmailAddress(address));
    }

    String goodAddresses[] = {
        "good@example.com",
        "good@example",
        "Good@Example.com",
    };

    for (String address : goodAddresses) {
      assertTrue(address, Util.isEmailAddress(address));
    }
  }

  @Test
  public void testNormalize() {
    assertEquals("a@example.com", Util.normalizeEmailAddress("a@example.com"));
    assertEquals("abc@example.com", Util.normalizeEmailAddress("AbC@Example.Com"));
  }

  @Test
  public void testAllowCrossOriginRequests() {
    MockHttpServletResponse mockResponse = new MockHttpServletResponse();
    mockResponse.setHeader(Util.CORS_ALLOW_ORIGIN_HEADER, "null");
    Util.allowCrossOriginRequests(mockResponse);
    assertEquals("*", mockResponse.getHeader(Util.CORS_ALLOW_ORIGIN_HEADER));
  }

  @Test
  public void testUrlEncode() {
    Map<String, String> params = new HashMap<String, String>();
    assertEquals("", Util.urlEncode(params));

    params.put("a", "b&?=");
    assertEquals("a=b%26%3F%3D", Util.urlEncode(params));

    params.put("c", "d");
    String result = Util.urlEncode(params);
    assertTrue(result.equals("a=b%26%3F%3D&c=d") || result.equals("c=d&a=b%26%3F%3D"));
  }

  @Test
  public void testBuildUrl() {
    assertEquals("http://example.com", Util.buildUrl("http://example.com", "", null));
    assertEquals("http://example.com/", Util.buildUrl("http://example.com", "/", null));
    assertEquals("http://example.com/test", Util.buildUrl("http://example.com", "/test", null));
    assertEquals("http://example.com/", Util.buildUrl("http://example.com", "/", Collections.<String,String>emptyMap()));

    Map<String, String> queryParams = new HashMap<String, String>();
    queryParams.put("a", "b");
    assertEquals("http://example.com/test?a=b", Util.buildUrl("http://example.com", "/test", queryParams));

    queryParams.put("c", "d");
    String url = Util.buildUrl("http://example.com", "/test", queryParams);
    // query param order is undefined
    assertTrue(url.equals("http://example.com/test?a=b&c=d") || url.equals("http://example.com/test?c=d&a=b"));
  }
}
