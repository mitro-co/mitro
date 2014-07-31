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

import static org.junit.Assert.assertEquals;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import co.mitro.core.server.data.DBAcl;
import co.mitro.core.server.data.DBAcl.AccessLevelType;
import co.mitro.core.server.data.DBAcl.CyclicGroupError;
import co.mitro.core.server.data.DBGroup;
import co.mitro.core.server.data.DBIdentity;
import co.mitro.core.servlets.MemoryDBFixture;

import com.google.common.collect.Sets;

public class DBGroupTest extends MemoryDBFixture {
  private static final String FAKE_PUBLIC_KEY = "public key";

  private DBAcl makeAclForGroup(DBGroup owner, AccessLevelType level) throws CyclicGroupError {
    DBAcl newAcl = new DBAcl();
    newAcl.setGroup(owner);
    newAcl.setLevel(level);
    newAcl.setGroupKeyEncryptedForMe("encrypted group key");
    return newAcl;
  }

  private void addGroupToGroup(DBGroup owner, DBGroup newGroup, AccessLevelType level) throws CyclicGroupError, SQLException {
    DBAcl newAcl = makeAclForGroup(owner, level);
    newAcl.setMemberGroup(newGroup);
    manager.aclDao.create(newAcl);
    // TODO: verify that the group is not already in the list.
  }

  private void addIdentityToGroup(DBGroup owner, DBIdentity newIdentity, AccessLevelType level) throws CyclicGroupError, SQLException {
    DBAcl newAcl = makeAclForGroup(owner, level);
    newAcl.setMemberIdentity(newIdentity);
    manager.aclDao.create(newAcl);
    // TODO: verify that the group is not already in the list.
  }
  
  @Test
  public  void testAGroupHierarchy() throws SQLException, CyclicGroupError {
    // This function makes:

    //       a
    //      / \
    //     /   \
    //     b    d
    //    / \
    //   c   d 
    DBGroup a = makeGroupWithDummyKey("a");
    DBGroup b = makeGroupWithDummyKey("b");
    DBGroup c = makeGroupWithDummyKey("c");
    DBGroup d = makeGroupWithDummyKey("d");
    addGroupToGroup(a,b, AccessLevelType.READONLY);
    addGroupToGroup(b,c, AccessLevelType.READONLY);
    addGroupToGroup(a,d, AccessLevelType.READONLY);
    addGroupToGroup(b,d, AccessLevelType.READONLY);
    
    
    DBIdentity iden = new DBIdentity();
    iden.setName("me@example.com");
    DBIdentity.createUserInDb(manager, iden);

    addIdentityToGroup(a, iden, AccessLevelType.READONLY);
    Set<DBGroup> seenGroups = Sets.newHashSet();
    @SuppressWarnings("deprecation")
    List<List<DBGroup> > paths = iden.ListAccessibleGroupsBF(manager, seenGroups);
    
    assertEquals(4, paths.size());
    
    List<DBGroup> ptr = paths.get(0); //a
    assertEquals(1, ptr.size());
    assertEquals(ptr.get(0).getName(), "a");

    ptr = paths.get(1); // a->b
    assertEquals(2, ptr.size());
    assertEquals(ptr.get(0).getName(), "a");
    assertEquals(ptr.get(1).getName(), "b");    
    
    ptr = paths.get(2); // a->d
    assertEquals(2, ptr.size());
    assertEquals(ptr.get(0).getName(), "a");
    assertEquals(ptr.get(1).getName(), "d");    
    
    ptr = paths.get(3); // a->b->c
    assertEquals(3, ptr.size());
    assertEquals(ptr.get(0).getName(), "a");
    assertEquals(ptr.get(1).getName(), "b");    
    assertEquals(ptr.get(2).getName(), "c");    
  }

  private DBGroup makeGroupWithDummyKey(String groupName) throws SQLException {
    DBGroup group = new DBGroup();
    group.setName(groupName);
    group.setPublicKeyString(FAKE_PUBLIC_KEY);
    manager.groupDao.create(group);
    return group;
  }

  @Test
  public void test() throws SQLException, CyclicGroupError {
    DBIdentity iden = new DBIdentity();
    iden.setName("me@example.com");
    DBIdentity.createUserInDb(manager, iden);

    DBGroup group = makeGroupWithDummyKey("groupname");
    
    DBGroup groupResult = manager.groupDao.queryForId(group.getId());
    assertEquals(0, groupResult.getAcls().size());
    assertEquals(0, groupResult.getGroupSecrets().size());
    assertEquals("groupname", groupResult.getName());
    assertEquals(FAKE_PUBLIC_KEY, groupResult.getPublicKeyString());
    assertEquals(null, groupResult.getSignatureString());

    // add an ACL
    DBAcl acl = makeAclForGroup(group, DBAcl.AccessLevelType.ADMIN);
    manager.aclDao.create(acl); // TODO: this should not be allowed -- ACL should have group or user

    DBAcl acl2 = makeAclForGroup(group, DBAcl.AccessLevelType.ADMIN);
    acl2.setMemberIdentity(iden);
    manager.aclDao.create(acl2);

    groupResult = manager.groupDao.queryForId(group.getId());
    DBAcl[] acls = groupResult.getAcls().toArray(new DBAcl[0]);
    assertEquals(2, acls.length);
    assertEquals(acl.getId(), acls[0].getId());
    assertEquals(groupResult.getId(), acls[0].getGroupId().getId());
    assertEquals(iden.getId(), acls[1].getMemberIdentityId().getId());
  }
};
