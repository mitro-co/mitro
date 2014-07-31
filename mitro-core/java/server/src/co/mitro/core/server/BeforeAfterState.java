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
package co.mitro.core.server;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import co.mitro.core.exceptions.DoEmailVerificationException;
import co.mitro.core.exceptions.MitroServletException;
import co.mitro.core.server.data.DBAcl;
import co.mitro.core.server.data.DBGroup;
import co.mitro.core.server.data.DBIdentity;
import co.mitro.core.server.data.DBServerVisibleSecret;
import co.mitro.core.servlets.ListMySecretsAndGroupKeys.AdminAccess;

import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

public final class BeforeAfterState {
  final Multimap<Integer, Integer> userIdToSecretIds = ArrayListMultimap.create();
  private Manager manager;
  public final class UserDiff {
    public String userName;
    public Set<Integer> newSecrets;
    public Set<Integer> removedSecrets;
  }
    
  public BeforeAfterState(Manager mgr) {this.manager = mgr;};
  public void trackGroup(DBGroup g) throws DoEmailVerificationException, SQLException, MitroServletException {
    // get all users in the group
    Set<Integer> groupUsers = Sets.newHashSet();
    
    // TODO: optimize this:
    g.putDirectUsersIntoSet(groupUsers, DBAcl.allAccessTypes());
    trackUsers(groupUsers);
  }
  
  public void trackSecret(DBServerVisibleSecret svs) throws DoEmailVerificationException, SQLException, MitroServletException {
    trackUsers(svs.getAllUserIdsWithAccess(manager, DBAcl.allAccessTypes(), AdminAccess.IGNORE_ACCESS_VIA_TOPLEVEL_GROUPS));
  }
  
  private void addUserDataToSet(Set<Integer> userIds, Multimap<Integer, Integer> userToSecret) throws SQLException {
    if (userIds.isEmpty()) return;
    
    String getAccessibleSecrets = "SELECT identity.id, group_secret.\"serverVisibleSecret_id\" "
        + " FROM group_secret "
        + "      JOIN acl ON (acl.group_id = group_secret.group_id) "
        + "      JOIN identity ON (identity.id = acl.member_identity) "
        + " WHERE identity.id IN (" + Joiner.on(',').join(userIds) + ')';
    List<String[]> secretsResults = Lists.newArrayList(manager.identityDao.queryRaw(getAccessibleSecrets));
    for (String[] cols : secretsResults) {
      int userId = Integer.parseInt(cols[0], 10);
      int secretId = Integer.parseInt(cols[1], 10);
      userToSecret.put(userId, secretId);
    }
  }
  
  public void trackUsers(Set<Integer> userIds) throws SQLException {
    // don't query users that we already have.
    Set<Integer> usersToQuery = Sets.difference(userIds, userIdToSecretIds.keySet());
    addUserDataToSet(usersToQuery, this.userIdToSecretIds);
  }

  public Map<DBIdentity, UserDiff> diffState() throws SQLException, MitroServletException {
    // get the new state
    Multimap<Integer, Integer> newState = ArrayListMultimap.create();
    addUserDataToSet(userIdToSecretIds.keySet(), newState);
    
    Map<DBIdentity, UserDiff> rval = Maps.newHashMap();
    for (Integer uid : userIdToSecretIds.keySet()) {
      Set<Integer> preSecrets = Sets.newHashSet(userIdToSecretIds.get(uid));
      Set<Integer> postSecrets = Sets.newHashSet(newState.get(uid));
      UserDiff ud = new UserDiff();
      ud.removedSecrets = Sets.difference(preSecrets, postSecrets);
      ud.newSecrets = Sets.difference(postSecrets, preSecrets);
      if (ud.removedSecrets.isEmpty() && ud.newSecrets.isEmpty()) {
        continue;
      }

      // TODO: optimize this to one query instead of n queries.
      DBIdentity id = manager.identityDao.queryForId(uid);
      ud.userName = id.getName();
      rval.put(id, ud);
    }
    return rval;
  }
}