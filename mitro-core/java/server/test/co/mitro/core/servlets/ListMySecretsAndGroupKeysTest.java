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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.sql.SQLException;

import org.junit.Before;
import org.junit.Test;

import co.mitro.core.exceptions.MitroServletException;
import co.mitro.core.server.data.DBAcl.CyclicGroupError;
import co.mitro.core.server.data.DBGroup.Type;
import co.mitro.core.server.data.DBGroup;
import co.mitro.core.server.data.DBIdentity;
import co.mitro.core.server.data.DBServerVisibleSecret;
import co.mitro.core.server.data.RPC;
import co.mitro.core.server.data.RPC.ListMySecretsAndGroupKeysRequest;
import co.mitro.core.server.data.RPC.ListMySecretsAndGroupKeysResponse.GroupInfo;
import co.mitro.core.servlets.MitroServlet.MitroRequestContext;

public class ListMySecretsAndGroupKeysTest extends OrganizationsFixture {
  private ListMySecretsAndGroupKeysRequest request;
  private ListMySecretsAndGroupKeys servlet;
  private RPC.ListMySecretsAndGroupKeysResponse response;

  @Before
  public void setUp() {
    request = new RPC.ListMySecretsAndGroupKeysRequest();
    servlet = new ListMySecretsAndGroupKeys();
    response = null;
  }

  /** Calls servlet.processCommand using request, storing the return value in response. */
  private void listMySecretsAndGroups(DBIdentity requestor)
      throws IOException, SQLException, MitroServletException {
    response = (RPC.ListMySecretsAndGroupKeysResponse)
        servlet.processCommand(new MitroRequestContext(requestor, gson.toJson(request), manager, null));
  }

  @Test
  public void testSingleSecretAutoDeleteGroup()
      throws IOException, SQLException, MitroServletException, CyclicGroupError {
    // Make testGroup an auto-delete group
    testGroup.setAutoDelete(true);
    manager.groupDao.update(testGroup);

    // Create a server visible secret in this hidden group and list it
    final String clientData = "client visible";
    final String criticalData = "critical";
    DBServerVisibleSecret secret = createSecret(testGroup, clientData, criticalData, null);

    listMySecretsAndGroups(testIdentity);
    assertEquals(3, response.groups.size());
    assertEquals(testGroup.getName(), response.groups.get(testGroup.getId()).name);
    assertEquals(2, response.secretToPath.size());
    assertEquals(clientData, response.secretToPath.get(secret.getId()).encryptedClientData);
    assertEquals(null, response.secretToPath.get(secret.getId()).encryptedCriticalData);
  }


  private void verifyGroupFlags() throws IOException, SQLException,
      MitroServletException {
    listMySecretsAndGroups(testIdentity);
    assertEquals(4, response.groups.size());
    assertEquals(testGroup.getName(), response.groups.get(testGroup.getId()).name);
    assertEquals(1, response.secretToPath.size());
    int orgpvt = 0;
    int pvt = 0;
    int toplevel = 0;
    System.out.println(gson.toJson(response.groups.values()));
    for (GroupInfo g : response.groups.values()) {
      if (g.isNonOrgPrivateGroup) {
        ++pvt;
      } 
      if (g.isOrgPrivateGroup) {
        ++orgpvt;
      } 
      if (g.isTopLevelOrg) {
        ++toplevel;
      }
    }
    assertEquals(1, pvt);
    assertEquals(1, orgpvt);
    assertEquals(1, toplevel);
  }
  @Test
  public void testOldStylePrivateUserGroup() throws SQLException, CyclicGroupError, IOException, MitroServletException {
    DBGroup pg = createGroupContainingIdentity(testIdentity);
    pg.setAutoDelete(false);
    assertNull(pg.getType());
    pg.setName("");
    manager.groupDao.update(pg);
    verifyGroupFlags();
  }

  @Test
  public void testNewStylePrivateUserGroup() throws SQLException, CyclicGroupError, IOException, MitroServletException {
    DBGroup pg = createGroupContainingIdentity(testIdentity);
    pg.setAutoDelete(false);
    pg.setName("");
    pg.setPrivateGroup();
    assertTrue(null != pg.getType());
    manager.groupDao.update(pg);
    verifyGroupFlags();
  }

  
  @SuppressWarnings("deprecation")
  @Test
  public void testDeprecatedUserId() throws IOException, SQLException, MitroServletException, CyclicGroupError {
    // specify the user id; this works
    request.myUserId = testIdentity.getName();
    listMySecretsAndGroups(testIdentity);
    assertEquals(3, response.groups.size());
    assertEquals(testGroup.getName(), response.groups.get(testGroup.getId()).name);
    assertEquals(1, response.secretToPath.size());
    try {
      // sign the request as testIdentity2, but the request itself specifies testIdentity
      // protects against spoofing (signing with one key, but pretending to be someone else)
      listMySecretsAndGroups(testIdentity2);
      fail("expected exception");
    } catch (MitroServletException e) {
      assertTrue(e.getMessage().contains("does not match rpc requestor"));
    }
  }
  
  
  
}
