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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.util.Iterator;

import org.junit.Before;
import org.junit.Test;

import co.mitro.core.server.data.DBGroup;
import co.mitro.core.server.data.DBIdentity;
import co.mitro.core.server.data.DBServerVisibleSecret;
import co.mitro.core.server.data.RPC.EditSecretContentRequest;
import co.mitro.core.server.data.RPC.EditSecretContentRequest.SecretContent;
import co.mitro.core.servlets.MitroServlet.MitroRequestContext;

import com.google.common.collect.Maps;

public class EditSecretContentTest extends OrganizationsFixture {
  private static final String NO_ACCESS = "user does not have access";
  private static final String OMITTED = "was omitted for secret";
  private static final String NOT_VISIBLE = "not visible to groups";

  private EditSecretContentRequest request;
  private EditSecretContent servlet;
  private DBGroup testGroup2;
  private DBGroup testGroup3;
  private DBServerVisibleSecret svs;
  private DBIdentity thirdParty = null;
  private DBIdentity randomAdmin;
  @Before
  public void setUp() throws Exception {
    request = new EditSecretContentRequest();
    svs = this.createSecret(testGroup, "client", "critical", org);
    request.secretId = svs.getId();
    servlet = new EditSecretContent();
    Iterator<DBIdentity> iterator = outsiders.iterator();
    testGroup2 = this.createGroupContainingIdentity(testIdentity2);
    thirdParty = iterator.next();
    testGroup3 = this.createGroupContainingIdentity(thirdParty);
    request.groupIdToEncryptedData = Maps.newHashMap();
    Iterator<DBIdentity> adminIter = admins.iterator();
    while (adminIter.hasNext()){
      randomAdmin = adminIter.next();
      if (randomAdmin.getId() != testIdentity.getId()) {
        break;
      } else {
        randomAdmin = null;
      }
    }
    assertNotNull(randomAdmin);
  }
  private void process(DBIdentity requestor) throws Exception {
    servlet.processCommand(
        new MitroRequestContext(requestor, gson.toJson(request), manager, null));
  }

  
  private void expectFailure(DBIdentity requestor, String msg) {
    try {
      process(requestor);
      fail("expected exception");
    } catch (Throwable t) {
      System.err.println(t.getMessage());
      if (null != msg) {
        assertThat(t.getMessage(), containsString(msg));
      }
    }
  }
  
  
  @Test
  public void testEditSecret() throws Exception {
    SecretContent content = new EditSecretContentRequest.SecretContent("client", null);
    request.groupIdToEncryptedData.put(testGroup.getId(), content);
    expectFailure(testIdentity, OMITTED);
    expectFailure(randomAdmin, OMITTED);
    expectFailure(thirdParty, NO_ACCESS);
    expectFailure(testIdentity2, NO_ACCESS);
    
    request.groupIdToEncryptedData.put(org.getId(), content);
    process(testIdentity);
    process(randomAdmin);
    expectFailure(thirdParty, NO_ACCESS);
    expectFailure(testIdentity2, NO_ACCESS);
    
    content.encryptedCriticalData = "critical";
    process(testIdentity);
    process(randomAdmin);
    expectFailure(thirdParty, NO_ACCESS);
    expectFailure(testIdentity2, NO_ACCESS);
    
    // try to edit the secret without client data
    content.encryptedClientData = null;
    expectFailure(testIdentity, null);
    expectFailure(randomAdmin, null);
    expectFailure(thirdParty, null);
    expectFailure(testIdentity2, null);
    
    content.encryptedClientData = "client3";
    
    // try to edit this secret, but missing a group.
    addSecretToGroup(svs, testGroup2, "e2", "e3");
    expectFailure(testIdentity, OMITTED);
    expectFailure(testIdentity2, OMITTED);
    expectFailure(randomAdmin, OMITTED);
    expectFailure(thirdParty, NO_ACCESS);
    manager.svsDao.refresh(svs);

    // try to edit this secret correctly.
    request.groupIdToEncryptedData.put(testGroup2.getId(), content);
    process(testIdentity);
    process(randomAdmin);
    expectFailure(thirdParty, NO_ACCESS);
    process(testIdentity2);
    manager.svsDao.refresh(svs);

    // try to edit while supplying another random group
    request.groupIdToEncryptedData.put(testGroup3.getId(), content);
    expectFailure(testIdentity, NOT_VISIBLE);
    expectFailure(testIdentity2, NOT_VISIBLE);
    expectFailure(randomAdmin, NOT_VISIBLE);
    expectFailure(thirdParty, NO_ACCESS);
  }
}
