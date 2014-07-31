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
package co.mitro.core.server.data;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.sql.SQLException;

import org.junit.Test;

import co.mitro.core.server.data.DBAcl.AccessLevelType;
import co.mitro.core.server.data.DBAcl.CyclicGroupError;
import co.mitro.core.server.data.DBServerVisibleSecret.InvariantException;
import co.mitro.core.servlets.MemoryDBFixture;

public class DBServerVisibleSecretTest extends MemoryDBFixture {
  @Test
  public void testVerifyHasAdministrator() throws SQLException, InvariantException, CyclicGroupError {
    DBServerVisibleSecret secret = new DBServerVisibleSecret();
    manager.svsDao.create(secret);
    // TODO: Isn't this an ORMLite bug?
    manager.svsDao.refresh(secret);

    // secret without any owners
    assertEquals(0, secret.getGroupSecrets().size());
    try {
      secret.verifyHasAdministrator(manager);
      fail("expected exception");
    } catch (InvariantException e) {
      assertThat(e.getMessage(), containsString("orphaned"));
    }

    // Add the secret to a group: not orphaned
    addSecretToGroup(secret, testGroup, "client", "critical");
    manager.svsDao.refresh(secret);
    secret.verifyHasAdministrator(manager);

    // Create g2 without any members. Add Secret s2 in group g2
    DBGroup g2 = new DBGroup("g2");
    g2.setPublicKeyString("public key");
    manager.groupDao.create(g2);

    DBServerVisibleSecret s2 = createSecret(g2, "client", "critical", null);

    // Fails: no members in g2!
    // Re-query the object to get a "clean" object from ORMLite
    s2 = manager.svsDao.queryForId(s2.getId());
    try {
      s2.verifyHasAdministrator(manager);
      fail("expected exception");
    } catch (InvariantException expected) {
      assertThat(expected.getMessage(), containsString("orphaned"));
    }

    // g2 is a member of testGroup
    DBAcl acl = new DBAcl();
    acl.setGroup(testGroup);
    acl.setMemberGroup(g2);
    acl.setLevel(DBAcl.AccessLevelType.ADMIN);
    acl.setGroupKeyEncryptedForMe("encrypted group key");
    manager.aclDao.create(acl);

    // Re-query the object to get a "clean" object from ORMLite
    s2 = manager.svsDao.queryForId(s2.getId());
    try {
      // BUG: I think this probably should pass: s2 has admins in testGroup
      s2.verifyHasAdministrator(manager);
      fail("expected exception");
    } catch (InvariantException e) {
      assertThat(e.getMessage(), containsString("orphaned"));
    }
  }

  @Test
  public void testVerifyHasAdministratorAccessLevels() throws SQLException, InvariantException, CyclicGroupError {
    // Create a new group with testIdentity that can edit secrets but not membership
    createGroupContainingIdentity(testIdentity);
    DBGroup group = new DBGroup();
    group.setName("hello");
    group.setPublicKeyString("public key");
    manager.groupDao.create(group);
    DBAcl acl = addToGroup(testIdentity, group, AccessLevelType.MODIFY_SECRETS_BUT_NOT_MEMBERSHIP);

    // Add a secret to the group: not orphaned
    DBServerVisibleSecret secret = createSecret(group, "client", "critical", null);
    manager.svsDao.refresh(secret);

    secret.verifyHasAdministrator(manager);

    // Change access level to read only: should fail (no administrators)
    acl.setLevel(AccessLevelType.READONLY);
    manager.aclDao.update(acl);
    manager.svsDao.refresh(secret);
    try {
      secret.verifyHasAdministrator(manager);
      fail("expected exception");
    } catch (InvariantException expected) {
      assertThat(expected.getMessage(), containsString("orphaned"));
    }
  }
};
