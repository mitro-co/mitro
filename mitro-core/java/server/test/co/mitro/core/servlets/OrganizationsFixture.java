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
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.junit.Before;

import co.mitro.core.server.data.DBAcl;
import co.mitro.core.server.data.DBAcl.CyclicGroupError;
import co.mitro.core.server.data.DBGroup;
import co.mitro.core.server.data.DBIdentity;
import co.mitro.core.server.data.DBServerVisibleSecret;
import co.mitro.core.server.data.RPC.GetOrganizationStateRequest;
import co.mitro.core.server.data.RPC.GetOrganizationStateResponse;

public class OrganizationsFixture extends MemoryDBFixture {

  protected GetOrganizationStateRequest gosRequest;
  protected GetOrganizationStateResponse gosResponse;
  protected GetOrganizationState gosServlet;
  private Set<DBIdentity> allIdentities;
  protected Set<DBIdentity> members;
  protected Set<DBIdentity> admins;
  protected Set<DBIdentity> outsiders;
  protected DBGroup org;
  protected DBServerVisibleSecret orphanedOrgSecret;
  protected DBServerVisibleSecret orgSecret;


  @Before
  public void orgFixtureSetUp() throws Exception,
      IOException {
    gosRequest = new GetOrganizationStateRequest();
    gosResponse = new GetOrganizationStateResponse();
    gosServlet = new GetOrganizationState();
    
    allIdentities = new HashSet<>();
    members = new HashSet<>();
    admins = new HashSet<>();
    outsiders = new HashSet<>();
  
    for (int i=0; i < 20; ++i) {
      DBIdentity newUser;
      allIdentities.add(newUser = this.createIdentity("user_" + i + "@example.com"));
      switch (i % 3) {
      case 0:
        admins.add(newUser);
        break;
      case 1:
        members.add(newUser);
        break;
      case 2:
        outsiders.add(newUser);
        break;
      default:
        assert(false);
      }
    }
    admins.add(testIdentity);
    org = createOrganization(testIdentity, "org1", admins, members);
    gosRequest.orgId = org.getId();
    
    // TODO: Not actually orphaned?
    orphanedOrgSecret = createSecret(testGroup, "clientVisibleEncrypted", "criticalDataEncrypted", org);
  
    // get a private group for a member; put a secret in that group
    DBIdentity member = members.iterator().next();
    DBGroup secretGroup = getPrivateOrgGroup(org, member);
    orgSecret = createSecret(secretGroup, "cve", "cde", org);
    manager.commitTransaction();
  }

  public DBGroup createSyncedGroup(String groupName, String scope, Collection<DBIdentity> users, DBGroup org) throws CyclicGroupError, SQLException {
    DBGroup group = new DBGroup();
    group.setName(groupName);
    group.setPublicKeyString("fake_public_key");
    group.setScope(scope);
    manager.groupDao.create(group);
    
    for (DBIdentity u : users) {
      addToGroup(u, group, DBAcl.AccessLevelType.MODIFY_SECRETS_BUT_NOT_MEMBERSHIP);
    }
    DBAcl acl = new DBAcl();
    acl.setMemberGroup(org);
    acl.setGroup(group);
    acl.setGroupKeyEncryptedForMe(ENCRYPTED_GROUP_KEY);
    acl.setLevel(DBAcl.AccessLevelType.ADMIN);
    manager.aclDao.create(acl);
    return group;
  }


}