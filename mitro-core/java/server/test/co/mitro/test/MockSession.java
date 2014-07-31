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

import java.util.Enumeration;
import java.util.HashMap;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;

public class MockSession implements HttpSession {
  private final HashMap<String, Object> attributes =  new HashMap<String, Object>();

  @Override
  public Object getAttribute(String name) {
    return attributes.get(name);
  }

  @Override
  public Enumeration<String> getAttributeNames() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public long getCreationTime() {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public String getId() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public long getLastAccessedTime() {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public int getMaxInactiveInterval() {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public ServletContext getServletContext() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  @Deprecated
  public javax.servlet.http.HttpSessionContext getSessionContext() {
    throw new UnsupportedOperationException("deprecated");
  }

  @Override
  @Deprecated
  public Object getValue(String arg0) {
    throw new UnsupportedOperationException("deprecated");
  }

  @Override
  @Deprecated
  public String[] getValueNames() {
    throw new UnsupportedOperationException("deprecated");
  }

  @Override
  public void invalidate() {
    // TODO Auto-generated method stub

  }

  @Override
  public boolean isNew() {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  @Deprecated
  public void putValue(String arg0, Object arg1) {
    throw new UnsupportedOperationException("deprecated");
  }

  @Override
  public void removeAttribute(String arg0) {
    // TODO Auto-generated method stub

  }

  @Override
  @Deprecated
  public void removeValue(String arg0) {
    throw new UnsupportedOperationException("deprecated");
  }

  @Override
  public void setAttribute(String name, Object value) {
    attributes.put(name, value);
  }

  @Override
  public void setMaxInactiveInterval(int arg0) {
    // TODO Auto-generated method stub

  }

}
