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
import static org.junit.Assert.fail;

import java.io.IOException;
import java.sql.SQLException;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import co.mitro.analysis.AuditLogProcessor.ActionType;
import co.mitro.core.exceptions.MitroServletException;
import co.mitro.core.server.data.DBAcl;
import co.mitro.core.server.data.DBGroup;
import co.mitro.core.server.data.DBIdentity;
import co.mitro.core.server.data.RPC;
import co.mitro.core.server.data.RPC.MitroRPC;
import co.mitro.core.servlets.MitroServlet.MitroRequestContext;

public class GetAuditLogTest extends OrganizationsFixture {
  private GetAuditLog servlet;
  private RPC.GetAuditLogRequest request;

  protected int addSecret(DBIdentity requestor, int groupId)
      throws IOException, SQLException, MitroServletException {
    RPC.AddSecretRequest request = new RPC.AddSecretRequest();
    request.ownerGroupId = groupId;
    request.encryptedClientData = "client";
    request.encryptedCriticalData = "critical";

    manager.setRequestor(requestor, null);
    AddSecret servlet = new AddSecret();
    MitroRPC output = servlet.processCommand(new MitroRequestContext(requestor, gson.toJson(request), manager, null));
    RPC.AddSecretResponse response = (RPC.AddSecretResponse) output;

    return response.secretId;
  }

  // don't use the enum value to ensure the enum stuff works
  private static final String INCLUDE_CRITICAL_DATA_STRING = "true";
  protected void accessSecret(DBIdentity requestor, DBGroup group, int secretId)
      throws MitroServletException, IOException, SQLException {
    RPC.GetSecretRequest request = new RPC.GetSecretRequest();
    request.groupId = group.getId();
    request.secretId = secretId;
    request.includeCriticalData = INCLUDE_CRITICAL_DATA_STRING;

    manager.setRequestor(requestor, null);
    GetSecret servlet = new GetSecret();
    servlet.processCommand(new MitroRequestContext(requestor, gson.toJson(request), manager, null));
  }

  protected RPC.GetAuditLogResponse getAuditLog(DBIdentity identity) throws IOException, SQLException, MitroServletException {
    MitroRPC output = servlet.processCommand(new MitroRequestContext(identity, gson.toJson(request), manager, null));
    return (RPC.GetAuditLogResponse) output;
  }

  private void expectException(DBIdentity identity) throws IOException, SQLException {
    try {
      getAuditLog(identity);
      fail("expected exception");
    } catch (MitroServletException expected) {
      assertThat(expected.getMessage(), containsString("no access"));
    }
  }

  @Before
  public void setUp() throws Exception {
    servlet = new GetAuditLog();

    request = new RPC.GetAuditLogRequest();
    request.limit = 100L;
    request.offset = 0L;
  }

  @Ignore @Test
  public void testSingleUserAuditLog() throws MitroServletException, IOException, SQLException, InterruptedException {
    Integer secretId = addSecret(testIdentity, testIdentity.getId());

    RPC.GetAuditLogResponse response = getAuditLog(testIdentity);

    assertEquals(1, response.events.size());
    assertEquals(testIdentity.getId(), response.events.get(0).userId);
    assertEquals(ActionType.CREATE_SECRET, response.events.get(0).action);
    assertEquals(secretId, response.events.get(0).secretId);

    accessSecret(testIdentity, testGroup, secretId);

    response = getAuditLog(testIdentity);

    assertEquals(2, response.events.size());
    assertEquals(testIdentity.getId(), response.events.get(1).userId);
    assertEquals(ActionType.CREATE_SECRET, response.events.get(1).action);
    assertEquals(secretId, response.events.get(1).secretId);

    assertEquals(testIdentity.getId(), response.events.get(0).userId);
    assertEquals(ActionType.GET_SECRET_CRITICAL_DATA_FOR_EDIT, response.events.get(0).action);
    assertEquals(secretId, response.events.get(0).secretId);
  }

  @Ignore @Test
  public void testMultipleUserAuditLog() throws Exception {
    addToGroup(testIdentity2, testGroup, DBAcl.AccessLevelType.ADMIN);

    Integer secretId = addSecret(testIdentity, testIdentity.getId());
    accessSecret(testIdentity, testGroup, secretId);

    RPC.GetAuditLogResponse response = getAuditLog(testIdentity);

    assertEquals(2, response.events.size());
    assertEquals(testIdentity.getId(), response.events.get(1).userId);
    assertEquals(ActionType.CREATE_SECRET, response.events.get(1).action);
    assertEquals(secretId, response.events.get(1).secretId);

    assertEquals(testIdentity.getId(), response.events.get(0).userId);
    assertEquals(ActionType.GET_SECRET_CRITICAL_DATA_FOR_EDIT, response.events.get(0).action);
    assertEquals(secretId, response.events.get(0).secretId);

    // Check that actions are visible to a second user that has access to the secret.
    response = getAuditLog(testIdentity2);

    assertEquals(2, response.events.size());
    assertEquals(testIdentity.getId(), response.events.get(1).userId);
    assertEquals(ActionType.CREATE_SECRET, response.events.get(1).action);
    assertEquals(secretId, response.events.get(1).secretId);

    assertEquals(testIdentity.getId(), response.events.get(0).userId);
    assertEquals(ActionType.GET_SECRET_CRITICAL_DATA_FOR_EDIT, response.events.get(0).action);
    assertEquals(secretId, response.events.get(0).secretId);
  }

  @Ignore @Test
  public void testOrgAuditLog() throws Exception {
    DBIdentity orgMember = members.iterator().next();
    DBIdentity orgAdmin = admins.iterator().next();

    Integer secretId = addSecret(orgAdmin, org.getId());

    // Verify that org admin doesn't see events in personal audit log.
    RPC.GetAuditLogResponse response = getAuditLog(orgAdmin);
    assertEquals(0, response.events.size());

    // Check that regular org member cannot access audit log for org.
    request.orgId = org.getId();
    expectException(orgMember);

    // Check that org admin sees events in org audit log.
    response = getAuditLog(orgAdmin);
    assertEquals(1, response.events.size());
    assertEquals(orgAdmin.getId(), response.events.get(0).userId);
    assertEquals(ActionType.CREATE_SECRET, response.events.get(0).action);
    assertEquals(secretId, response.events.get(0).secretId);
  }

  @Ignore @Test
  public void testTimestampQuery() throws Exception {
    int secretId = addSecret(testIdentity, testIdentity.getId());

    Thread.sleep(10);
    Long t1 = System.currentTimeMillis();

    accessSecret(testIdentity, testGroup, secretId);

    Thread.sleep(10);
    Long t2 = System.currentTimeMillis();

    accessSecret(testIdentity, testGroup, secretId);

    request.startTimeMs = null;
    request.endTimeMs = null;
    RPC.GetAuditLogResponse response = getAuditLog(testIdentity);
    assertEquals(3, response.events.size());

    request.endTimeMs = t1;
    response = getAuditLog(testIdentity);
    assertEquals(1, response.events.size());

    request.startTimeMs = t1;
    request.endTimeMs = t2 - 1;
    response = getAuditLog(testIdentity);
    assertEquals(1, response.events.size());

    request.startTimeMs = t2;
    request.endTimeMs = null;
    response = getAuditLog(testIdentity);
    assertEquals(1, response.events.size());
  }
}
