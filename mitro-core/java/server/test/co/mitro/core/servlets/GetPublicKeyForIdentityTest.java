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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.sql.SQLException;

import org.junit.Test;

import co.mitro.core.exceptions.MitroServletException;
import co.mitro.core.exceptions.UserVisibleException;
import co.mitro.core.server.data.DBIdentity;
import co.mitro.core.server.data.DBUserName;
import co.mitro.core.server.data.RPC.GetPublicKeysForIdentityRequest;
import co.mitro.core.server.data.RPC.MitroRPC;
import co.mitro.core.servlets.MitroServlet.MitroRequestContext;

import com.google.common.collect.ImmutableList;

public class GetPublicKeyForIdentityTest extends MemoryDBFixture {
  private static final String NEW_EMAIL_ADDRESS = "unknownUser@example.com";
  GetPublicKeyForIdentity servlet = new GetPublicKeyForIdentity(managerFactory, keyFactory);
  GetPublicKeysForIdentityRequest request = new GetPublicKeysForIdentityRequest();
  
  @Test 
  public void testAdding() throws SQLException, MitroServletException {
    request.userIds = ImmutableList.of(NEW_EMAIL_ADDRESS, testIdentity.getName());
    try {
      servlet.processCommand(
          new MitroRequestContext(testIdentity, gson.toJson(request), manager, null));
      assertTrue(false);
    } catch (AssertionError e) {
      assertTrue (e.getMessage().contains("Missing"));
    }
    request.addMissingUsers = true;
    servlet.processCommand(
        new MitroRequestContext(testIdentity, gson.toJson(request), manager, null));
    DBIdentity ident = DBIdentity.getIdentityForUserName(manager, NEW_EMAIL_ADDRESS);
    assertNotNull(ident);    
  }
  
  @Test 
  public void testSimpleAlias() throws SQLException, MitroServletException {
    final String ALIAS_EMAIL = "testIdentity+alias@example.com"; 
    DBUserName.addAlias(ALIAS_EMAIL, manager, testIdentity);
    request.userIds = ImmutableList.of(testIdentity.getName());
    MitroRPC response = servlet.processCommand(
        new MitroRequestContext(testIdentity, gson.toJson(request), manager, null));
    assertNotNull(response);
    request.userIds = ImmutableList.of(ALIAS_EMAIL);
    response = servlet.processCommand(
        new MitroRequestContext(testIdentity, gson.toJson(request), manager, null));
    assertNotNull(response);
    
    request.userIds = ImmutableList.of(ALIAS_EMAIL, testIdentity.getName());
    try {
      response = servlet.processCommand(
          new MitroRequestContext(testIdentity, gson.toJson(request), manager, null));
      assertTrue(false);
    } catch (UserVisibleException e) {
      assertTrue (e.getUserVisibleMessage().contains("aliases and cannot be included"));
    }
    
    
  }
}
