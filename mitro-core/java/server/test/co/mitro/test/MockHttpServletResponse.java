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

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

public class MockHttpServletResponse implements HttpServletResponse {
  private static final String CONTENT_TYPE_HEADER = "Content-Type";

  private StringWriter output = new StringWriter();
  private boolean committed = false;
  private int status = SC_OK;
  private final HashMap<String, String> headers = new HashMap<String, String>();
  private String contentType = null;

  private void throwIfCommitted() {
    if (committed) {
      throw new IllegalStateException("request is already committed");
    }
  }

  /** Marks this response as committed, throwing an exception if it is already committed. */
  private void commitAndResetOutput() {
    throwIfCommitted();
    committed = true;
    output = new StringWriter();
  }

  public String getOutput() {
    return output.toString();
  }

  @Override
  public void flushBuffer() throws IOException {
    // TODO Auto-generated method stub

  }

  @Override
  public int getBufferSize() {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public String getCharacterEncoding() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String getContentType() {
    return contentType;
  }

  @Override
  public Locale getLocale() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public ServletOutputStream getOutputStream() throws IOException {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public PrintWriter getWriter() throws IOException {
    return new PrintWriter(output);
  }

  @Override
  public boolean isCommitted() {
    return committed;
  }

  @Override
  public void reset() {
    // TODO Auto-generated method stub

  }

  @Override
  public void resetBuffer() {
    // TODO Auto-generated method stub

  }

  @Override
  public void setBufferSize(int arg0) {
    // TODO Auto-generated method stub

  }

  @Override
  public void setCharacterEncoding(String charset) {
    // TODO Auto-generated method stub

  }

  @Override
  public void setContentLength(int arg0) {
    // TODO Auto-generated method stub

  }

  @Override
  public void setContentType(String type) {
    setHeader(CONTENT_TYPE_HEADER, type);
  }

  @Override
  public void setLocale(Locale arg0) {
    // TODO Auto-generated method stub

  }

  @Override
  public void addCookie(Cookie arg0) {
    // TODO Auto-generated method stub

  }

  @Override
  public void addDateHeader(String arg0, long arg1) {
    // TODO Auto-generated method stub

  }

  @Override
  public void addHeader(String arg0, String arg1) {
    throw new UnsupportedOperationException("TODO: implement");
  }

  @Override
  public void addIntHeader(String arg0, int arg1) {
    // TODO Auto-generated method stub

  }

  @Override
  public boolean containsHeader(String arg0) {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public String encodeRedirectURL(String arg0) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  @Deprecated
  public String encodeRedirectUrl(String arg0) {
    throw new UnsupportedOperationException("deprecated");
  }

  @Override
  public String encodeURL(String arg0) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  @Deprecated
  public String encodeUrl(String arg0) {
    throw new UnsupportedOperationException("deprecated");
  }

  @Override
  public String getHeader(String name) {
    return headers.get(name);
  }

  @Override
  public Collection<String> getHeaderNames() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Collection<String> getHeaders(String arg0) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public int getStatus() {
    return status;
  }

  @Override
  public void sendError(int arg0) throws IOException {
    commitAndResetOutput();
    throw new RuntimeException("unimplemented");
  }

  @Override
  public void sendError(int sc, String msg) throws IOException {
    commitAndResetOutput();
    status = sc;
    // Set output to the error message
    // real implementations will do other things (e.g. render it as HTML)
    output.write(msg);
  }

  @Override
  public void sendRedirect(String location) throws IOException {
    // setHeader must come first to avoid throwing IllegalStateException
    // TODO: Resolve redirects to an absolute URL like Jetty (http://host/path)
    setHeader("Location", location);
    commitAndResetOutput();
    status = SC_MOVED_TEMPORARILY;
  }

  @Override
  public void setDateHeader(String arg0, long arg1) {
    // TODO Auto-generated method stub

  }

  @Override
  public void setHeader(String name, String value) {
    throwIfCommitted();
    if (name.equals(CONTENT_TYPE_HEADER)) {
      contentType = value;
    }
    headers.put(name, value);
  }

  @Override
  public void setIntHeader(String arg0, int arg1) {
    // TODO Auto-generated method stub

  }

  @Override
  public void setStatus(int sc) {
    throwIfCommitted();
    status = sc;
  }

  @Override
  @Deprecated
  public void setStatus(int arg0, String arg1) {
    throw new UnsupportedOperationException("deprecated");
  }
}
