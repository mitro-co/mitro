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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Lists;

import co.mitro.core.exceptions.MitroServletException;
import co.mitro.core.server.data.DBAcl;
import co.mitro.core.server.data.DBAcl.AccessLevelType;
import co.mitro.core.server.data.DBAcl.CyclicGroupError;
import co.mitro.core.server.data.DBAudit;
import co.mitro.core.server.data.DBGroup;
import co.mitro.core.server.data.DBGroupSecret;
import co.mitro.core.server.data.DBGroupSecret.CRITICAL;
import co.mitro.core.server.data.RPC.EditSecretRequest;
import co.mitro.core.server.data.DBIdentity;
import co.mitro.core.server.data.DBServerVisibleSecret;
import co.mitro.core.server.data.RPC;
import co.mitro.core.servlets.MitroServlet.MitroRequestContext;

public class GetSecretTest extends OrganizationsFixture {

  private int uniqueIds = 1000;
  private GetSecret servlet;
  private RPC.GetSecretRequest request;
  /** Secret that is part of testGroup (testIdentity has ADMIN access). */
  private DBServerVisibleSecret testServerVisibleSecret;

  /**
   * Returns an id not shared in the DB. Catches bugs where we used ids from the wrong table.
   */
  private int getUniqueId() {
    uniqueIds += 1;
    return uniqueIds;
  }

  private RPC.GetSecretResponse processCommand(DBIdentity requestor)
      throws IOException, SQLException, MitroServletException {
    RPC.GetSecretResponse rval = (RPC.GetSecretResponse)
        servlet.processCommand(new MitroRequestContext(requestor, gson.toJson(request), manager, null));
    assertTrue(rval.encryptedGroupKey != null);
    return rval;
  }

  @Before
  public void setUp() throws SQLException { 
    servlet = new GetSecret();
    request = new RPC.GetSecretRequest();
    testServerVisibleSecret = createSecret(testGroup, "group1", "critical1", null);
  }

  
  @Test
  public void testLimitedDisplay() throws IOException, SQLException, CyclicGroupError, MitroServletException {
    // TODO: test edit secret 
    testServerVisibleSecret.setViewable(false);
    manager.svsDao.update(testServerVisibleSecret);
    // fetch secret via group 1
    request.groupId = testGroup.getId();
    request.secretId = testServerVisibleSecret.getId();
    request.includeCriticalData = "display";
    addToGroup(manager, this.testIdentity2, testGroup, AccessLevelType.ADMIN);

    try {
      servlet.processCommand(new MitroRequestContext(testIdentity, gson.toJson(request), manager, null));
      fail("expected exception");
    } catch (MitroServletException e) {
      assertTrue(e.getMessage().contains("you do not have permission to view the password"));
    }

    EditSecret editSecretServlet = new EditSecret();
    EditSecretRequest esRequest = new EditSecretRequest();
    esRequest.isViewable = true;
    esRequest.secretId = testServerVisibleSecret.getId();

    
    // add the secret to the org.
    addOrgToGroup(manager, org, testGroup, AccessLevelType.ADMIN);
    addSecretToGroup(testServerVisibleSecret, org, "", "");
    assertEquals(true, org.isTopLevelOrganization());
    
    // TODO:
    // this line should be removed once the buggy DBAcl::addTransitiveGroupsAndUsers is fixed
    request.groupId = org.getId();
    //
    
    // admin should now be able to view the secret.
    servlet.processCommand(new MitroRequestContext(admins.iterator().next(), gson.toJson(request), manager, null));

    // ti2 cannot view the secret, or edit it.
    try {
      servlet.processCommand(new MitroRequestContext(testIdentity2, gson.toJson(request), manager, null));
      fail("expected exception");
    } catch (MitroServletException e) {
      assertTrue(e.getMessage().contains("you do not have permission to view the password"));
    }

    try {
      editSecretServlet.processCommand(new MitroRequestContext(testIdentity2, gson.toJson(esRequest), manager, null));
      fail("expected exception");
    } catch (MitroServletException e) {
      assertTrue(e.getMessage().contains("could not access secret for udpate"));
    }
    // testidentity1 should be able to edit the secret
    editSecretServlet.processCommand(new MitroRequestContext(testIdentity, gson.toJson(esRequest), manager, null));
    request.groupId = testGroup.getId();
    
    // now ti2 should be able to view the secret
    servlet.processCommand(new MitroRequestContext(testIdentity2, gson.toJson(request), manager, null));

    
  }
  
  @Test
  public void testSingleSecretMultipleGroups() throws Exception {
    // create a secret in two groups, with totally unique ids
    // hack testGroup to change its id (we had a bug where we used the wrong ids)
    manager.groupDao.updateId(testGroup, getUniqueId());
    addToGroup(testIdentity, testGroup, AccessLevelType.ADMIN);
    DBGroup g2 = new DBGroup();
    g2.setName("g2");
    g2.setId(getUniqueId());
    g2.setPublicKeyString("public key");
    manager.groupDao.create(g2);
    addToGroup(testIdentity, g2, AccessLevelType.ADMIN);

    DBServerVisibleSecret secret = createSecret(testGroup, "group1", "critical1", null);
    // hack ids to be unique (previously had a bug where we used wrong ids)
    DBGroupSecret g1Secret = manager.getGroupSecret(testGroup, secret);
    manager.svsDao.updateId(secret, getUniqueId());
    manager.svsDao.refresh(secret);
    g1Secret.setServerVisibleSecret(secret);
    manager.groupSecretDao.update(g1Secret);
    manager.groupSecretDao.updateId(g1Secret, getUniqueId());

    // also put the secret in group 2
    DBGroupSecret g2Secret = addSecretToGroup(secret, g2, "group2", "critical2");
    manager.groupSecretDao.updateId(g2Secret, getUniqueId());

    // fetch secret via group 1
    request.groupId = testGroup.getId();
    request.secretId = secret.getId();
    request.includeCriticalData = CRITICAL.INCLUDE_CRITICAL_DATA.getClientString();

    manager.setRequestor(testIdentity, null);
    RPC.GetSecretResponse response = processCommand(testIdentity);
    
    
    assertEquals("group1", response.secret.encryptedClientData);
    assertEquals("critical1", response.secret.encryptedCriticalData);
    assertEquals(secret.getId(), response.secret.secretId);
    request.groupId = null;

    response = processCommand(testIdentity);
    
    // either order is possible
    assertTrue("group1".equals(response.secret.encryptedClientData) || "group2".equals(response.secret.encryptedClientData));
    assertEquals(secret.getId(), response.secret.secretId);

    
    // fetch secret via group 2
    request.groupId = g2.getId();
    response = processCommand(testIdentity);
    assertEquals("critical2", response.secret.encryptedCriticalData);
    List<DBAudit> audits = DBAudit.getAllActionsByUser(manager, testIdentity);
    assertEquals(3, audits.size());
    assertEquals(audits.get(0).getAction(), DBAudit.ACTION.GET_SECRET_WITH_CRITICAL);
    assertEquals(audits.get(0).getTargetSVS().getId(), secret.getId());
    assertEquals(audits.get(1).getAction(), DBAudit.ACTION.GET_SECRET_WITH_CRITICAL);
    assertEquals(audits.get(1).getTargetSVS().getId(), secret.getId());

    // fetch secret via non-existent group
    request.groupId = uniqueIds + 50;
    try {
      servlet.processCommand(new MitroRequestContext(testIdentity, gson.toJson(request), manager, null));
      fail("expected exception");
    } catch (MitroServletException e) {
      assertTrue(e.getMessage().contains("does not exist"));
    }
  }

  @SuppressWarnings("deprecation")
  @Test
  public void deprecatedUserId() throws Exception {
    // mismatched inner and outer identities
    request.userId = testIdentity2.getName();
    request.groupId = testGroup.getId();
    request.secretId = testServerVisibleSecret.getId();

    try {
      processCommand(testIdentity);
      fail("expected exception");
    } catch (MitroServletException e) {
      assertThat(e.getMessage(), containsString("User ID does not match"));
    }

    // correct userId works
    request.userId = testIdentity.getName();
    RPC.GetSecretResponse response = processCommand(testIdentity);
    assertEquals("group1", response.secret.encryptedClientData);
  }

  // Can only access secrets if you have permission. Verifies iSEC-MITRO-WAPT-2013-2
  @Test
  public void secretAccessControl() throws Exception {
    // requesting secret as testIdentity2 doesn't work: no access!
    request.groupId = testGroup.getId();
    request.secretId = testServerVisibleSecret.getId();
    try {
      processCommand(testIdentity2);
      fail("expected exception");
    } catch (MitroServletException e) {
      assertThat(e.getMessage(), containsString("does not have permission"));
    }
    
    try {
      request.groupId = null;
      processCommand(testIdentity2);
      fail("expected exception");
    } catch (MitroServletException e) {
      assertThat(e.getMessage(), containsString("you do not have access"));
    }

    // secret is not in group2
    DBGroup group2 = createGroupContainingIdentity(testIdentity2);
    request.groupId = group2.getId();
    try {
      processCommand(testIdentity2);
      fail("expected exception");
    } catch (MitroServletException e) {
      assertThat(e.getMessage(), containsString("does not exist in group"));
    }

    // bad group
    request.groupId = getUniqueId();
    try {
      processCommand(testIdentity);
      fail("expected exception");
    } catch (MitroServletException e) {
      assertThat(e.getMessage(), containsString("does not exist in group"));
    }

    // bad secret id
    request.groupId = testGroup.getId();
    request.secretId = getUniqueId();
    try {
      processCommand(testIdentity2);
      fail("expected exception");
    } catch (MitroServletException e) {
      assertThat(e.getMessage(), containsString("Invalid secret"));
    }
  }

  // Can access individual secrets but not shared secrets if account is not verified
  @Test
  public void unverifiedIdentity() throws Exception {
    testIdentity.setVerified(false);
    manager.identityDao.update(testIdentity);

    // works: only shared with testIdentity
    request.groupId = testGroup.getId();
    request.secretId = testServerVisibleSecret.getId();
    RPC.GetSecretResponse response = processCommand(testIdentity);
    assertEquals("group1", response.secret.encryptedClientData);

    // fails: shared secret
    addToGroup(testIdentity2, testGroup, AccessLevelType.ADMIN);
    try {
      processCommand(testIdentity);
      fail("expected exception");
    } catch (MitroServletException e) {
      assertThat(e.getMessage(), containsString("unverified"));
    }

    // Remove testIdentity2, add the secret to a group containing testIdentity2
    boolean found = true;
    for (DBAcl acl : testGroup.getAcls()) {
      if (acl.getMemberIdentityId().getId() == testIdentity2.getId()) {
        found = true;
        manager.aclDao.delete(acl);
        break;
      }
    }
    assertTrue(found);

    DBGroup testId2Group = createGroupContainingIdentity(testIdentity2);
    addSecretToGroup(testServerVisibleSecret, testId2Group, "client", "critical");

    // should not work: shared!
    try {
      processCommand(testIdentity);
      fail("expected exception");
    } catch (MitroServletException e) {
      assertThat(e.getMessage(), containsString("unverified"));
    }
  }

  @Test
  public void testOrgGroupFirst() throws Exception {
    // get a secret via an org group, where the org ACL appears before the identity
    // at one point this caused a NullPointerException
    // the bug was order dependent, so the ACL on orgGroup is in order:
    //   testIdentity, organization, user2
    // we fetch both user2 and user1 so hopefully one will trigger it
    Iterator<DBIdentity> iterator = outsiders.iterator();
    DBIdentity user1 = iterator.next();
    DBIdentity user2 = iterator.next();
    DBGroup org = createOrganization(
        user1, "org", Lists.newArrayList(user1), Lists.newArrayList(user2));
    DBGroup orgGroup = createGroupContainingIdentity(user1);
    addOrgToGroup(manager, org, orgGroup, AccessLevelType.ADMIN);
    addToGroup(user2, orgGroup, AccessLevelType.ADMIN);

    addSecretToGroup(testServerVisibleSecret, orgGroup, "client", "critical");
    request.secretId = testServerVisibleSecret.getId();
    processCommand(user2);
    processCommand(user1);
  }
}
