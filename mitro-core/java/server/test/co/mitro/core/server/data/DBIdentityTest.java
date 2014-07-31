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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.SQLException;
import java.util.List;

import org.junit.Test;

import com.google.common.collect.ImmutableList;

import co.mitro.core.server.data.DBAcl.AccessLevelType;
import co.mitro.core.server.data.DBAcl.CyclicGroupError;
import co.mitro.core.servlets.GetPublicKeyForIdentity;
import co.mitro.core.servlets.MemoryDBFixture;

public class DBIdentityTest extends MemoryDBFixture {
  @Test
  public void testNullNameHashCode() {
    DBIdentity id = new DBIdentity();
    // calling hashCode should crash but toString shouldn't crash
    try {
      id.hashCode();
      fail("expected exception");
    } catch (NullPointerException e) {
      ;
    }
    String out = id.toString();
    assertTrue(out != "");
  }

  @Test
  public void testConstraints() throws SQLException {
    DBIdentity id = new DBIdentity();
    try {
      DBIdentity.createUserInDb(manager, id);
      fail("expected exception");
    } catch (SQLException e) {
    }

    id.setName("foo");
    id.setChangePasswordOnNextLogin(true);
    DBIdentity.createUserInDb(manager, id);

    // Duplicate names
    DBIdentity id2 = new DBIdentity();
    id2.setName(id.getName());
    try {
      DBIdentity.createUserInDb(manager, id2);
      fail("expected exception");
    } catch (SQLException e) {
    }

    // verify that changePassword is saved
    id2 = manager.identityDao.queryForId(id.getId());
    assertTrue(id2.getChangePasswordOnNextLogin());
  }
  
  @Test
  public void testSetAndGetBackup(){
    DBIdentity id = new DBIdentity();
    try {
      DBIdentity.createUserInDb(manager, id);
      fail("expected exception");
    } catch (SQLException e) {
    }
    String[] backups = new String[]{"0", "1", "2", "3", "4"};
    for (int i = 0; i < 5; i++)
      id.setBackup(i, backups[i]);
    assertTrue(id.getBackup(0).equals(backups[0]) && id.getBackup(1).equals(backups[1]) && id.getBackup(2).equals(backups[2]) && id.getBackup(3).equals(backups[3]) && id.getBackup(4).equals(backups[4]));
    
  }

  @SuppressWarnings("deprecation")
  @Test
  public void listAccessibleGroupsBF() throws SQLException, CyclicGroupError {
    // testIdentity is part of testGroup
    // testIdentity2 is part of testGroup2
    DBGroup testGroup2 = createGroupContainingIdentity(testIdentity2);
    // make testGroup an admin member of testGroup2
    DBAcl acl = new DBAcl();
    acl.setGroup(testGroup2);
    acl.setMemberGroup(testGroup);
    acl.setGroupKeyEncryptedForMe(ENCRYPTED_GROUP_KEY);
    acl.setLevel(AccessLevelType.ADMIN);
    manager.aclDao.create(acl);

    // testIdentity can access secrets in 2 groups: testGroup and testGroup2
    List<List<DBGroup>> groups = testIdentity.ListAccessibleGroupsBF(manager);
    // BUG: currently returns only testGroup! (should be 2!!!)
    assertEquals(1, groups.size());
    // testIdentity2 can access secrets in 1 group: testGroup2
    groups = testIdentity2.ListAccessibleGroupsBF(manager);
    // BUG: currently returns testGroup2 and testGroup2 (should be 1!!!)
    assertEquals(2, groups.size());
  }
  @Test
  public void testGetUsersFromNames() throws SQLException {
    List<String> userNames = ImmutableList.of("Test'User_.Name@getmitro.co");
    assertTrue(DBIdentity.getUsersFromNames(manager, userNames).isEmpty());
  }
  
  @Test 
  public void testGetIdentityForUserName() throws SQLException {
    DBIdentity iden = DBIdentity.getIdentityForUserName(manager, testIdentity.getName());
    assertEquals(testIdentity.getName(), iden.getName());
  }

}
