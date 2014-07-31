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

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


import co.mitro.core.server.data.RPC.ListMySecretsAndGroupKeysResponse;
import co.mitro.core.server.data.RPC.ListMySecretsAndGroupKeysResponse.GroupInfo;
import co.mitro.core.server.data.RPC.ListMySecretsAndGroupKeysResponse.SecretToPath;
import co.mitro.core.server.data.RPC.Secret;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;

public class DBHistoricalUserState {
  @DatabaseField(generatedId = true)
  public int id;

  @DatabaseField(columnName="user_id", dataType=DataType.LONG_STRING)
  public String userId;
  @DatabaseField(columnName="referrer_url", dataType=DataType.LONG_STRING)
  public String referrerUrl;
  @DatabaseField(columnName="referrer_domain", dataType=DataType.LONG_STRING)
  public String referrerDomain;
  @DatabaseField(columnName="num_visible_users")
  public int numVisibleUsers;
  @DatabaseField(columnName="num_groups")
  public int numGroups;
  @DatabaseField(columnName="num_organizations")
  public int numOrganizations;
  @DatabaseField(columnName="num_secrets")
  public int numSecrets;
  @DatabaseField(columnName="num_shared_secrets")
  public int numSharedSecrets;
  @DatabaseField(columnName="num_visible_users_in_same_domain")
  public int numVisibleUsersInSameDomain;

  @DatabaseField(columnName="timestamp_ms")
  public long timestampMs;

  public Collection<String> visibleUsers;
  public Collection<GroupInfo> groups;
  public Collection<GroupInfo> organizations;
  public Collection<SecretToPath> secrets;
  
  public DBHistoricalUserState() {};
  
  
  public DBHistoricalUserState(ListMySecretsAndGroupKeysResponse resp, Map<Integer, GroupInfo> orgIdToOrg, long timestampMs) {
    this.userId = resp.myUserId;
    this.timestampMs = timestampMs;
    // sort the users
    this.visibleUsers = Sets.newTreeSet(resp.autocompleteUsers);
    numVisibleUsersInSameDomain = 0;
    String myDomain = userId.split("@")[1];
    for (String u : visibleUsers) {
      if (myDomain.equals(u.split("@")[1])) {
        ++numVisibleUsersInSameDomain;
      }
    }
    
    this.organizations = Lists.newArrayList();
    
    this.secrets = resp.secretToPath.values();
    this.groups = resp.groups.values();
    numGroups = groups.size();
    Set<Integer> myPrivateGroups = Sets.newHashSet();
    Set<Integer> seenOrgs = Sets.newHashSet();
    for (GroupInfo gi : groups) {
      if (gi.isNonOrgPrivateGroup || gi.isOrgPrivateGroup) {
        myPrivateGroups.add(gi.groupId);
      }
      if (gi.owningOrgId != null && seenOrgs.add(gi.owningOrgId)) {
        organizations.add(orgIdToOrg.get(gi.owningOrgId));
      }
      if (gi.isTopLevelOrg && seenOrgs.add(gi.groupId)) {
        organizations.add(orgIdToOrg.get(gi.groupId));
      }
    }
    numOrganizations = organizations.size();
    numSecrets = secrets.size();
    numVisibleUsers = visibleUsers.size();
    Set<Integer> sharedSecrets = new HashSet<Integer>();
    for (Secret s : secrets) {
      // the user should be excluded from this list.
      Set<String> usersExcludingMe = Sets.difference(Sets.newHashSet(s.users), ImmutableSet.of(userId));
      Set<Integer> groupsExcludingMe = Sets.difference(Sets.newHashSet(s.groups), myPrivateGroups);
      if (!(usersExcludingMe.isEmpty() && groupsExcludingMe.isEmpty())) {
        sharedSecrets.add(s.secretId);
      }
    }
    numSharedSecrets = sharedSecrets.size();
  }


  public Set<Integer> getSecretIds() {
    Set<Integer> secretIds = Sets.newTreeSet();
    for (Secret s : secrets) {
      secretIds.add(s.secretId);
    }
    return secretIds;
    
  }
}