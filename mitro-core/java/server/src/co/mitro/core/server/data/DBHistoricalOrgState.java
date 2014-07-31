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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import co.mitro.core.server.data.RPC.GetOrganizationStateResponse;
import co.mitro.core.server.data.RPC.ListMySecretsAndGroupKeysResponse.GroupInfo;
import co.mitro.core.server.data.RPC.Secret;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;

public class DBHistoricalOrgState {

  @DatabaseField(generatedId = true)
  public int id;
  
  @DatabaseField(columnName="org_id")
  public int orgId;
  @DatabaseField(columnName="org_name", dataType=DataType.LONG_STRING)
  public String orgName;
  @DatabaseField(columnName="num_members")
  public int numMembers;
  @DatabaseField(columnName="num_admins")
  public int numAdmins;
  @DatabaseField(columnName="num_groups")
  public int numGroups;
  @DatabaseField(columnName="num_secrets")
  public int numSecrets;
  
  @DatabaseField(columnName="timestamp_ms")
  public long timestampMs;
  
  public DBHistoricalOrgState() {}
  public DBHistoricalOrgState(GetOrganizationStateResponse resp, int orgId, long timestampMs) {
    this.orgId = orgId;
    this.timestampMs = timestampMs;
    orgName = resp.groups.get(orgId).name;
    admins = resp.admins;
    members = resp.members;
    groups = resp.groups.values();
    secrets = new ArrayList<Secret>();
    secrets.addAll(resp.orgSecretsToPath.values());
    secrets.addAll(resp.orphanedSecretsToPath.values());
    numAdmins = admins.size();
    numMembers = members.size();
    numSecrets = secrets.size();
    numGroups = groups.size();
  }

  // these are not persisted
  public List<Secret> secrets;
  public Collection<GroupInfo> groups;
  public Collection<String> admins;
  public Collection<String> members;
  
}