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
package co.mitro.test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.Principal;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.servlet.AsyncContext;
import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;

public class MockHttpServletRequest implements HttpServletRequest {
  /** Returned by getScheme() and getRequestURL() */
  private static final String SCHEME = "https";
  private static final int PORT = 443;
  private static final String REMOTE_ADDR = "127.0.0.1";
  private static final String SERVLET_PATH = "/servlet";

  private MockServletInputStream inputStream;
  private final HashMap<String, String[]> parameterMap = new HashMap<String, String[]>();
  private final MockSession session = new MockSession();
  private String hostname = "localhost";
  private String path = "/somepath";
  private String method = "GET";
  private final List<Cookie> cookies = new LinkedList<>(); 
  
  public void setRequestBody(byte[] body) {
    inputStream = new MockServletInputStream(body);
  }

  public void setParameter(String key, String value) {
    parameterMap.put(key, new String[]{value});
  }

  @Override
  public AsyncContext getAsyncContext() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Object getAttribute(String arg0) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Enumeration<String> getAttributeNames() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String getCharacterEncoding() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public int getContentLength() {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public String getContentType() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public DispatcherType getDispatcherType() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public ServletInputStream getInputStream() throws IOException {
    return inputStream;
  }

  @Override
  public String getLocalAddr() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String getLocalName() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public int getLocalPort() {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public Locale getLocale() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Enumeration<Locale> getLocales() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String getParameter(String name) {
    String[] values = parameterMap.get(name);
    if (values == null) {
      return null;
    }
    return values[0];
  }

  @Override
  public Map<String, String[]> getParameterMap() {
    return Collections.unmodifiableMap(parameterMap);
  }

  @Override
  public Enumeration<String> getParameterNames() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String[] getParameterValues(String arg0) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String getProtocol() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public BufferedReader getReader() throws IOException {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  @Deprecated
  public String getRealPath(String arg0) {
    throw new UnsupportedOperationException("deprecated");
  }

  @Override
  public String getRemoteAddr() {
    return REMOTE_ADDR;
  }

  @Override
  public String getRemoteHost() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public int getRemotePort() {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public RequestDispatcher getRequestDispatcher(String arg0) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String getScheme() {
    return SCHEME;
  }

  @Override
  public String getServerName() {
    return hostname;
  }

  @Override
  public int getServerPort() {
    return PORT;
  }

  @Override
  public ServletContext getServletContext() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public boolean isAsyncStarted() {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public boolean isAsyncSupported() {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public boolean isSecure() {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public void removeAttribute(String arg0) {
    // TODO Auto-generated method stub

  }

  @Override
  public void setAttribute(String arg0, Object arg1) {
    // TODO Auto-generated method stub

  }

  @Override
  public void setCharacterEncoding(String arg0)
      throws UnsupportedEncodingException {
    // TODO Auto-generated method stub

  }

  @Override
  public AsyncContext startAsync() throws IllegalStateException {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public AsyncContext startAsync(ServletRequest arg0, ServletResponse arg1)
      throws IllegalStateException {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public boolean authenticate(HttpServletResponse arg0) throws IOException,
      ServletException {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public String getAuthType() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String getContextPath() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Cookie[] getCookies() {
    Cookie[] rval = new Cookie[cookies.size()];
    return cookies.toArray(rval);
  }
  
  public void addCookie(String key, String value) {
    cookies.add(new Cookie(key, value));
  }

  public void clearCookies() {
    cookies.clear();
  }

  @Override
  public long getDateHeader(String arg0) {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public String getHeader(String arg0) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Enumeration<String> getHeaderNames() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Enumeration<String> getHeaders(String arg0) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public int getIntHeader(String arg0) {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public String getMethod() {
    return method;
  }

  public void setMethod(String method) {
    assert method.equals("GET") || method.equals("POST");
    this.method = method;
  }

  @Override
  public Part getPart(String arg0) throws IOException, ServletException {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Collection<Part> getParts() throws IOException, ServletException {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String getPathInfo() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String getPathTranslated() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String getQueryString() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String getRemoteUser() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String getRequestURI() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public StringBuffer getRequestURL() {
    StringBuffer result = new StringBuffer(SCHEME + "://");
    result.append(hostname).append('/').append(path);
    return result;
  }

  @Override
  public String getRequestedSessionId() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String getServletPath() {
    return SERVLET_PATH;
  }

  @Override
  public MockSession getSession() {
    return session;
  }

  @Override
  public HttpSession getSession(boolean arg0) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Principal getUserPrincipal() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public boolean isRequestedSessionIdFromCookie() {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public boolean isRequestedSessionIdFromURL() {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  @Deprecated
  public boolean isRequestedSessionIdFromUrl() {
    throw new UnsupportedOperationException("deprecated");
  }

  @Override
  public boolean isRequestedSessionIdValid() {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public boolean isUserInRole(String arg0) {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public void login(String arg0, String arg1) throws ServletException {
    // TODO Auto-generated method stub

  }

  @Override
  public void logout() throws ServletException {
    // TODO Auto-generated method stub

  }

}
