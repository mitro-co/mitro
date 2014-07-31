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

import static org.junit.Assert.*;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.SQLException;
import java.util.Set;

import org.junit.Test;

import co.mitro.core.server.data.DBAcl.AccessLevelType;
import co.mitro.core.server.data.DBAcl.CyclicGroupError;
import co.mitro.core.servlets.MemoryDBFixture;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

public class DBAclTest extends MemoryDBFixture {
  @Test
  public void testAddTransitiveGroupsAndUsers() throws CyclicGroupError, SQLException {
    DBIdentity id2 = new DBIdentity();
    id2.setName("id2@example.com");
    
    DBIdentity.createUserInDb(manager, id2);

    // Create g2, containing id2 and a member of testGroup
    DBGroup g2 = createGroupContainingIdentity(id2);

    DBAcl acl = new DBAcl();
    acl.setGroup(testGroup);
    acl.setMemberGroup(g2);
    acl.setLevel(DBAcl.AccessLevelType.ADMIN);
    acl.setGroupKeyEncryptedForMe("encrypted group key");
    manager.aclDao.create(acl);

    acl = manager.aclDao.queryForId(acl.getId());
    DBGroup returnedGroup = acl.loadMemberGroup(manager.groupDao);
    //DBGroup returnedGroup = acl.getMemberGroupId();
    Set<Integer> returnedGroupMembers = Sets.newHashSet();
    returnedGroup.putDirectUsersIntoSet(returnedGroupMembers, DBAcl.adminAccess());
    assertEquals(returnedGroup.getId(), g2.getId());
    assertEquals(1, g2.getAcls().size());
    assertEquals(1, returnedGroup.getAcls().size());
    
    assertEquals(1, returnedGroupMembers.size());
    
    Set<DBIdentity> allUsers = Sets.newHashSet();
    Set<DBGroup> allGroups = Sets.newHashSet();
    acl.addTransitiveGroupsAndUsers(
        manager, ImmutableSet.of(DBAcl.AccessLevelType.ADMIN), allUsers, allGroups);
    // TODO: is this the right direction? Should it be getting id2 and g2?
    assertArrayEquals(new DBGroup[]{testGroup}, allGroups.toArray());
    assertArrayEquals(new DBIdentity[]{testIdentity}, allUsers.toArray());
  }

  @Test
  public void accessLevelIsGreater() {
    assertFalse(AccessLevelType.ADMIN.isHigher(AccessLevelType.ADMIN));
    assertTrue(AccessLevelType.ADMIN.isHigher(AccessLevelType.MODIFY_SECRETS_BUT_NOT_MEMBERSHIP));
    assertTrue(AccessLevelType.ADMIN.isHigher(AccessLevelType.READONLY));

    assertFalse(AccessLevelType.MODIFY_SECRETS_BUT_NOT_MEMBERSHIP.isHigher(AccessLevelType.ADMIN));
    assertFalse(AccessLevelType.MODIFY_SECRETS_BUT_NOT_MEMBERSHIP.isHigher(AccessLevelType.MODIFY_SECRETS_BUT_NOT_MEMBERSHIP));
    assertTrue(AccessLevelType.MODIFY_SECRETS_BUT_NOT_MEMBERSHIP.isHigher(AccessLevelType.READONLY));

    assertFalse(AccessLevelType.READONLY.isHigher(AccessLevelType.ADMIN));
    assertFalse(AccessLevelType.READONLY.isHigher(AccessLevelType.MODIFY_SECRETS_BUT_NOT_MEMBERSHIP));
    assertFalse(AccessLevelType.READONLY.isHigher(AccessLevelType.READONLY));

    try {
      assertFalse(AccessLevelType.ADMIN.isHigher(null));
      fail("expected exception");
    } catch (NullPointerException ignored) {}
  }
}
