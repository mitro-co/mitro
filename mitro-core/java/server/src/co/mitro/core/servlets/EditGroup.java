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
import java.util.List;
import java.util.Set;

import javax.servlet.annotation.WebServlet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.mitro.core.exceptions.InvalidRequestException;
import co.mitro.core.exceptions.MitroServletException;
import co.mitro.core.exceptions.PermissionException;
import co.mitro.core.server.Manager;
import co.mitro.core.server.data.DBAcl;
import co.mitro.core.server.data.DBAudit;
import co.mitro.core.server.data.DBGroup;
import co.mitro.core.server.data.DBGroupSecret;
import co.mitro.core.server.data.DBServerVisibleSecret;
import co.mitro.core.server.data.DBServerVisibleSecret.InvariantException;
import co.mitro.core.server.data.RPC;
import co.mitro.core.server.data.RPC.AddGroupResponse;
import co.mitro.core.server.data.RPC.MitroRPC;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;


@WebServlet("/api/EditGroup")
public class EditGroup extends AbstractAddEditGroup {
	private static final long serialVersionUID = 1L;
	private static final Logger logger = LoggerFactory.getLogger(EditGroup.class);

  @Override
  protected MitroRPC processCommand(MitroRequestContext context) throws IOException, SQLException, MitroServletException {
    RPC.EditGroupRequest in = gson.fromJson(context.jsonRequest,
        RPC.EditGroupRequest.class); 

    // TODO: Remove this after client JS for editing secrets is improved
    // for now, editing a secret causes a client to do getgroup/editgroup with no changes
    if (in.acls == null || in.acls.isEmpty()) {
      logger.info("ignoring empty EditGroup");
      AddGroupResponse response = new AddGroupResponse();
      response.groupId = in.groupId;
      return response;
    }

    DBGroup existingGroup = context.manager.groupDao.queryForId(in.groupId);
    context.manager.userState.trackGroup(existingGroup);
    
    // TODO: this is a hack and allows org admins unfettered control on groups. 
    //       This is bad for the following reasons:
    //          - the org admin operation should be different, so that an accident 
    //            can't cause a user to use his admin powers to do bad things
    //
    //          - this will allow synced groups to be manually changed by an admin
    //            which is bad.
    Set<Integer> oldPermittedUsersForMembershipChange = getOrgAdminsForGroup(context.manager, existingGroup);
    Set<Integer> oldPermittedUsersForSecretRewrite = Sets.newHashSet(oldPermittedUsersForMembershipChange);

    existingGroup.putDirectUsersIntoSet(oldPermittedUsersForMembershipChange, DBAcl.adminAccess());
    existingGroup.putDirectUsersIntoSet(oldPermittedUsersForSecretRewrite, DBAcl.modifyGroupSecretsAccess());

    assert oldPermittedUsersForSecretRewrite.containsAll(oldPermittedUsersForMembershipChange);

    // eliminate existing ACLs. These will be recreated later.
    Set<Integer> oldGroups = Sets.newHashSet();
    Set<Integer> oldUsers = Sets.newHashSet();
    DBAcl.getMemberGroupsAndIdentities(existingGroup.getAcls(), oldGroups, oldUsers);
    context.manager.aclDao.delete(existingGroup.getAcls());
    
    AddGroupResponse rval = addEditGroupCommand(context.manager, in, existingGroup);
    existingGroup = context.manager.groupDao.queryForId(in.groupId);
    Set<Integer> newGroups = Sets.newHashSet();
    Set<Integer> newUsers = Sets.newHashSet();
    DBAcl.getMemberGroupsAndIdentities(existingGroup.getAcls(), newGroups, newUsers);

    if (!oldGroups.equals(newGroups) || !oldUsers.equals(newUsers)) {
      if (!oldPermittedUsersForMembershipChange.contains(context.requestor.getId())) {
        throw new PermissionException("User does not have permission to modify group membership");
      }
    }
    
    oldGroups.removeAll(newGroups);
    oldUsers.removeAll(newUsers);
    
    context.manager.addAuditLog(DBAudit.ACTION.MODIFY_GROUP, null, null, existingGroup, null, null);
    
    if (null == in.secrets) {
      if (!oldGroups.isEmpty() || !oldUsers.isEmpty()) {
        throw new InvalidRequestException("removal of members requires rewriting secrets");
      }
    } else {
      // secrets are modified if and only if secrets is set in the request.
      // ensure that the user has permission for this operation
      if (!oldPermittedUsersForSecretRewrite.contains(context.requestor.getId())) {
        throw new PermissionException("User does not have permission to modify group");
      }
    
      DBGroup updatedGroup = context.manager.groupDao.queryForId(in.groupId);
      List<DBGroupSecret> groupSecrets = Lists.newArrayList(updatedGroup.getGroupSecrets());
      if (groupSecrets.size() != in.secrets.size()) {
        throw new MitroServletException("you cannot change the number of secrets using EditGroup " + groupSecrets.size() + " vs " + in.secrets.size());
      }
      for (int i = 0; i < in.secrets.size(); ++i) {
        if (groupSecrets.get(i).getServerVisibleSecret().getId() != in.secrets.get(i).secretId) {
          logger.error("secret index {} id does not match. DB: {} request: {}",
              i, groupSecrets.get(i).getServerVisibleSecret().getId(), in.secrets.get(i).secretId);
          throw new MitroServletException("Order of secrets may not be changed");
        }
        
        // we only need to check for orphaning secrets here because 
        // this can only happen when users are removed, and that should
        // always result in new secret data being set.
        try {
          groupSecrets.get(i).getServerVisibleSecret().verifyHasAdministrator(context.manager);
        } catch (InvariantException e) {
          // this operation cannot be allowed
          throw new MitroServletException(e);
        }
        
        // TODO: verify signature
        groupSecrets.get(i).setClientVisibleDataEncrypted(in.secrets.get(i).encryptedClientData);
        groupSecrets.get(i).setCriticalDataEncrypted(in.secrets.get(i).encryptedCriticalData);
        context.manager.groupSecretDao.update(groupSecrets.get(i));
      }
    }
    
    // test to ensure that making a group an org group does not result in secrets being owned by multiple orgs.
    context.manager.groupDao.refresh(existingGroup);
    Set<DBServerVisibleSecret> secrets = Sets.newHashSet();
    
    for (DBGroupSecret gs : existingGroup.getGroupSecrets()) {
      secrets.add(gs.getServerVisibleSecret());
    }
    AddSecret.preventMutipleOrgSecretOwnership(context.manager, secrets);
    return rval;
  }

  // TODO: this should be merged (?) with AuthenticatedDb::getGroupAsUserOrOrgAdmin
  public static Set<Integer> getOrgAdminsForGroup(Manager manager, DBGroup existingGroup) throws SQLException {
    // is this group part of an org?
    Integer orgId = null;
    for (DBAcl acl : existingGroup.getAcls()) {
      orgId = acl.getMemberGroupIdAsInteger();
      if (orgId != null) {
        break;
      }
    }
    Set<Integer> orgAdmins = Sets.newHashSet();
    if (orgId != null) {
      DBGroup org = manager.groupDao.queryForId(orgId);
      assert (org != null);
      org.putDirectUsersIntoSet(orgAdmins, DBAcl.adminAccess());
    }
    return orgAdmins;
  }
}
