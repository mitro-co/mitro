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
import static org.junit.Assert.*;

import java.sql.SQLException;
import java.util.Iterator;

import org.junit.Before;
import org.junit.Test;

import co.mitro.core.crypto.KeyInterfaces.CryptoError;
import co.mitro.core.crypto.KeyInterfaces.PrivateKeyInterface;
import co.mitro.core.server.data.DBGroup;
import co.mitro.core.server.data.DBGroupSecret;
import co.mitro.core.server.data.DBIdentity;
import co.mitro.core.server.data.DBServerVisibleSecret;
import co.mitro.core.server.data.RPC.UpdateSecretFromAgentRequest;
import co.mitro.core.server.data.RPC.UpdateSecretFromAgentRequest.UserData;
import co.mitro.core.server.data.RPC.UpdateSecretFromAgentRequest.UserData.CriticalData;
import co.mitro.core.servlets.MitroServlet.MitroRequestContext;

public class UpdateSecretFromAgentTest extends OrganizationsFixture {

  private UserData userdata;
  private UpdateSecretFromAgent servlet;
  @SuppressWarnings("unused")
  private DBGroup testGroup2;
  @SuppressWarnings("unused")
  private DBGroup testGroup3;
  private DBServerVisibleSecret svs;
  private DBIdentity thirdParty = null;
  private DBIdentity randomAdmin;
  private UpdateSecretFromAgentRequest request;
  private PrivateKeyInterface randomAdminKey;
  private PrivateKeyInterface thirdPartyKey;
  private static final String OLD = "old";
  private static final String NEW = "new";
  
  
  @Before
  public void setUp() throws Exception {
    svs = this.createSecret(testGroup, "client", "critical", org);
    userdata = new UserData();
    userdata.secretId = svs.getId();
    userdata.criticalData = new CriticalData();
    userdata.criticalData.oldPassword = OLD;
    userdata.criticalData.password = NEW;
    userdata.criticalData.note = null;
    request = new UpdateSecretFromAgentRequest();
    servlet = new UpdateSecretFromAgent(managerFactory, keyFactory);
    
    Iterator<DBIdentity> iterator = outsiders.iterator();

    testGroup2 = this.createGroupContainingIdentity(testIdentity2);
    thirdParty = iterator.next();
    testGroup3 = this.createGroupContainingIdentity(thirdParty);
    Iterator<DBIdentity> adminIter = admins.iterator();
    while (adminIter.hasNext()){
      randomAdmin = adminIter.next();
      if (randomAdmin.getId() != testIdentity.getId()) {
        break;
      } else {
        randomAdmin = null;
      }
    }
    randomAdminKey = keyFactory.generate();
    randomAdmin.setPublicKeyString(randomAdminKey.exportPublicKey().toString());
    manager.identityDao.update(randomAdmin);
    
    thirdPartyKey = keyFactory.generate();
    thirdParty.setPublicKeyString(thirdPartyKey.exportPublicKey().toString());
    manager.identityDao.update(thirdParty);
    
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
      if (null != msg) {
        assertThat(t.getMessage(), containsString(msg));
      }
    }
  }
  
  @Test
  public void editSecretDataOK() throws Exception {
    userdata.userId = randomAdmin.getName();
    fillRequestWithSignature(randomAdminKey);
    process(null);
    verifyCriticalData();
  }
  @Test
  public void editSecretDataOKWithRequestor() throws Exception {
    userdata.userId = randomAdmin.getName();
    fillRequestWithSignature(randomAdminKey);
    process(testIdentity);
    //TODO: this is fragile
    verifyCriticalData();
  }
  
  @Test
  public void editSecretDataWithBadSignature() throws Exception {
    userdata.userId = randomAdmin.getName();
    fillRequestWithSignature(thirdPartyKey);
    expectFailure(null, "did not sign");
    expectFailure(testIdentity, "did not sign");
  }

  @Test
  public void editSecretDataWithUserWithNoAccess() throws Exception {
    userdata.userId = thirdParty.getName();
    fillRequestWithSignature(thirdPartyKey);
    expectFailure(null, "does not have access");
    expectFailure(testIdentity, "does not have access");
    
  }

  @Test
  public void editSecretDataWithBadDomainUser() throws Exception {
    DBIdentity exampleOrgIdentity = createIdentity("user2@example.org", null);
    PrivateKeyInterface exampleOrgIdentityKey = keyFactory.generate();
    exampleOrgIdentity.setPublicKeyString(exampleOrgIdentityKey.exportPublicKey().toString());
    DBGroup domain2Group = createGroupContainingIdentity(exampleOrgIdentity);
    addSecretToGroup(svs, domain2Group, "client", "critical");
    
    userdata.userId = exampleOrgIdentity.getName();
    fillRequestWithSignature(exampleOrgIdentityKey);
    expectFailure(null, "permitted domains list");
    expectFailure(testIdentity, "permitted domains list");
  }

  private void verifyCriticalData() throws SQLException, CryptoError {
    // get the server visible secret
    DBServerVisibleSecret svs = manager.svsDao.queryForId(userdata.secretId); 
    for (DBGroupSecret gs : svs.getGroupSecrets()) {
      PrivateKeyInterface key = groupToPrivateKeyMap.get(gs.getGroup().getId());
      CriticalData actual = gson.fromJson(key.decrypt(gs.getCriticalDataEncrypted()), CriticalData.class); 
      assertEquals(actual, userdata.criticalData);
      assertEquals(actual.password, NEW);
      // TODO: fix memorydbfixture to actually encrypt these data
      assertEquals(gs.getClientVisibleDataEncrypted(), "client");
    } 
  }
  private void fillRequestWithSignature(PrivateKeyInterface key) throws CryptoError {
    request.dataFromUserSignature = null;
    request.dataFromUser = gson.toJson(userdata);
    if (null != key) {
      request.dataFromUserSignature = key.sign(request.dataFromUser);
    }
  }
  
}
