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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletResponse;

import com.google.gson.Gson;

public final class Util {
  // not constructible
  private Util() {}

  // http://www.whatwg.org/specs/web-apps/current-work/multipage/states-of-the-type-attribute.html#e-mail-state-(type=email)
  private static final Pattern VALID_EMAIL = Pattern.compile(
      "^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$");
  private static final Gson gson = new Gson();

  /** Content-Type header for JSON with charset=UTF-8 so Jetty serializes Unicode correctly. */
  public static final String JSON_CONTENT_TYPE = "application/json; charset=UTF-8";

  public static final String CORS_ALLOW_ORIGIN_HEADER = "Access-Control-Allow-Origin";
  public static final String CORS_ALLOW_METHODS_HEADER = "Access-Control-Allow-Methods";
  public static final String CORS_ALLOW_HEADERS_HEADER = "Access-Control-Allow-Headers";
  public static final String CORS_MAX_AGE_HEADER = "Access-Control-Max-Age";

  /** Returns true if address looks like a valid email address. Uses the HTML5 regex. */
  public static boolean isEmailAddress(String address) {
    Matcher matcher = VALID_EMAIL.matcher(address);
    return matcher.matches();
  }

  // TODO: Use this throughout Mitro!
  /** Returns the "normalized" version of address. Currently this just lowercases the address. */
  public static String normalizeEmailAddress(String address) {
    // TODO: Is this a sane policy? Strictly speaking this is a violation of the RFCs
    assert isEmailAddress(address);
    return address.toLowerCase();
  }

  /**
   * Writes gsonValue as JSON to response, serializing it using Gson. This sets the content type
   * correctly, so Unicode characters get serialized in the right format.
   */
  public static void writeJsonResponse(Object gsonValue, HttpServletResponse response)
      throws IOException {
    // By default, JSON should be interpreted as UTF-8, but servlets default to ISO-8859-1:
    // http://wiki.apache.org/tomcat/FAQ/CharacterEncoding#Q1
    response.setContentType(JSON_CONTENT_TYPE);
    response.getWriter().write(gson.toJson(gsonValue));
  }

  /**
   * Sets response headers to permit cross-origin requests. See:
   * http://www.w3.org/TR/cors/#access-control-allow-origin-response-header
   */
  public static void allowCrossOriginRequests(HttpServletResponse response) {
    // must be *: Accessed by both mitroweb.com and by the extension
    // Firefox: GET ServerRejects failing due to Origin: resource://mitro-login-manager-at-jetpack
    // POST requests are sent without Origin, possibly because they are from a "window" context?
    // https://developer.mozilla.org/en-US/Add-ons/Extension_Frequently_Asked_Questions#I_cannot_initiate_an_XMLHttpRequest_from_my_extension
    // To be safe: permit all origins
    response.setHeader(CORS_ALLOW_ORIGIN_HEADER, "*");
    response.setHeader(CORS_ALLOW_METHODS_HEADER, "POST, GET, OPTIONS");
    response.setHeader(CORS_ALLOW_HEADERS_HEADER, "*");
    response.setHeader(CORS_MAX_AGE_HEADER, "3600");
  }

  // Url encode a set of key=value params.
  // For example the input params {a: b, c: d&e} would be encoded as a=b&c=d%26e.
  public static String urlEncode(Map<String, String> params) {
    assert(params != null);

    StringBuilder paramsBuilder = new StringBuilder();
    boolean firstParam = true;

    for (Map.Entry<String, String> entry : params.entrySet()) {
      if (firstParam) {
        firstParam = false;
      } else {
        paramsBuilder.append("&");
      }

      try {
        paramsBuilder.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
        paramsBuilder.append("=");
        paramsBuilder.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
      } catch (UnsupportedEncodingException e) {
        // Should never fail: : UTF-8 is required to be supported by the JVM.
        throw new RuntimeException(e);
      }
    }
    return paramsBuilder.toString();
  }

  // Build a url from host, path, and query params.
  // Host includes the scheme and optional port e.g. http://example.com:8000.
  // Query params is optional (pass params=null).
  public static String buildUrl(String host, String path, Map<String, String> params) {
    assert(host != null);
    assert(path != null);

    StringBuilder url = new StringBuilder();
    url.append(host);
    url.append(path);

    if (params != null && !params.isEmpty()) {
      url.append("?");
      url.append(urlEncode(params));
    }
    return url.toString();
  }
}
