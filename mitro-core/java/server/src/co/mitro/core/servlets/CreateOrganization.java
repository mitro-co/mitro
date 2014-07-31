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
import java.util.Map;
import java.util.Set;

import javax.servlet.annotation.WebServlet;

import co.mitro.core.accesscontrol.AuthenticatedDB;
import co.mitro.core.crypto.KeyInterfaces.KeyFactory;
import co.mitro.core.exceptions.InvalidRequestException;
import co.mitro.core.exceptions.MitroServletException;
import co.mitro.core.exceptions.UserVisibleException;
import co.mitro.core.server.Manager;
import co.mitro.core.server.ManagerFactory;
import co.mitro.core.server.data.DBAcl;
import co.mitro.core.server.data.DBAcl.CyclicGroupError;
import co.mitro.core.server.data.DBAudit;
import co.mitro.core.server.data.DBGroup;
import co.mitro.core.server.data.DBIdentity;
import co.mitro.core.server.data.RPC;
import co.mitro.core.server.data.RPC.CreateOrganizationRequest.PrivateGroupKeys;
import co.mitro.core.server.data.RPC.CreateOrganizationResponse;
import co.mitro.core.server.data.RPC.MitroRPC;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;


@WebServlet("/api/CreateOrganization")
public class CreateOrganization extends MitroServlet {
  private static final long serialVersionUID = 1L;

  public CreateOrganization(ManagerFactory managerFactory, KeyFactory keyFactory) {
    super(managerFactory, keyFactory);
  }

  /** Returns an error message if request is not valid. Checks basic request formatting. */
  static String validateRequest(RPC.CreateOrganizationRequest request) {
    if (Strings.isNullOrEmpty(request.name)) {
      return "name cannot be empty";
    }
    if (Strings.isNullOrEmpty(request.publicKey)) {
      return "publicKey cannot be empty";
    }
    if (request.adminEncryptedKeys == null || request.adminEncryptedKeys.isEmpty()) {
      return "adminEncryptedKeys cannot be empty";
    }
    if (request.memberGroupKeys == null || request.memberGroupKeys.isEmpty()) {
      return "memberGroupKeys cannot be empty";
    }

    // ensure that every admin is also a member
    SetView<String> adminsNotInMembers = Sets.difference(
        request.adminEncryptedKeys.keySet(), request.memberGroupKeys.keySet());
    if (!adminsNotInMembers.isEmpty()) {
      return "each admin must be a member";
    }
    return null;
  }

  @Override
  protected MitroRPC processCommand(MitroRequestContext context)
      throws IOException, SQLException, MitroServletException {
    try {
      RPC.CreateOrganizationRequest request = gson.fromJson(context.jsonRequest,
          RPC.CreateOrganizationRequest.class);

      String errorMessage = validateRequest(request);
      if (errorMessage != null) {
        // some errors shouldn't be shown, but "name cannot be empty" should
        throw new UserVisibleException(errorMessage);
      }

      // this user must not already have an organization with this name.
      @SuppressWarnings("deprecation")
      AuthenticatedDB userDb = AuthenticatedDB.deprecatedNew(context.manager, context.requestor);
      Set<DBGroup> organizations = userDb.getOrganizations();
      for (DBGroup g : organizations) {
        if (g.getName().equals(request.name)) {
          throw new InvalidRequestException("duplicate organization name");
        }
      }

      // Build the organization's group (contains admins)
      DBGroup organizationGroup = new DBGroup();
      organizationGroup.setType(DBGroup.Type.TOP_LEVEL_ORGANIZATION);
      organizationGroup.setName(request.name);
      organizationGroup.setAutoDelete(false);
      organizationGroup.setPublicKeyString(request.publicKey);
      organizationGroup.setScope(null);
      organizationGroup.setSignatureString(null);

      // Add all admins to the ACL (AuthenicatedDB.saveNewGroupWithAcls checks duplicates)
      List<DBAcl> organizationAcls = makeAdminAclsForOrganization(userDb, organizationGroup, request.adminEncryptedKeys);

      // actually create the organization
      userDb.saveNewGroupWithAcls(organizationGroup, organizationAcls);
      context.manager.groupDao.update(organizationGroup);

      // create private groups for each member
      addMembersToOrganization(userDb, organizationGroup, request.memberGroupKeys, context.manager);

      String auditNote = request.memberGroupKeys.size() + " total members";
      context.manager.addAuditLog(
          DBAudit.ACTION.CREATE_ORGANIZATION, null, null, organizationGroup, null, auditNote);
      CreateOrganizationResponse out = new RPC.CreateOrganizationResponse();
      out.organizationGroupId = organizationGroup.getId();
      return out;
    } catch (CyclicGroupError e) {
      throw new MitroServletException(e);
    }
  }

  @SuppressWarnings("deprecation")
  static void addMembersToOrganization(AuthenticatedDB userDb,
      DBGroup organizationGroup, Map<String, PrivateGroupKeys> memberGroupKeys, Manager manager)
      throws SQLException, CyclicGroupError, MitroServletException {
    for (Map.Entry<String, PrivateGroupKeys> memberEntry : memberGroupKeys.entrySet()) {
      PrivateGroupKeys privateGroupKeys = memberEntry.getValue();
      DBGroup memberGroup = new DBGroup();
      memberGroup.setPrivateGroup();
      memberGroup.setPublicKeyString(privateGroupKeys.publicKey);
      memberGroup.setScope(null);
      memberGroup.setSignatureString(null);
      assert (!Strings.isNullOrEmpty(memberEntry.getKey()));

      DBIdentity memberIdentity = userDb.getIdentity(memberEntry.getKey());

      // create the user's ACL
      DBAcl userAcl = new DBAcl();
      userAcl.setGroup(memberGroup);
      userAcl.setLevel(DBAcl.AccessLevelType.MODIFY_SECRETS_BUT_NOT_MEMBERSHIP);
      userAcl.setMemberIdentity(memberIdentity);
      userAcl.setGroupKeyEncryptedForMe(privateGroupKeys.keyEncryptedForUser);

      // create the ACL granting the organization access
      DBAcl organizationAcl = new DBAcl();
      organizationAcl.setGroup(memberGroup);
      organizationAcl.setLevel(DBAcl.AccessLevelType.ADMIN);
      organizationAcl.setMemberGroup(organizationGroup);
      organizationAcl.setGroupKeyEncryptedForMe(privateGroupKeys.keyEncryptedForOrganization);
      userDb.saveNewGroupWithAcls(memberGroup, ImmutableList.of(userAcl, organizationAcl));
    }
  }

  static List<DBAcl> makeAdminAclsForOrganization(AuthenticatedDB userDb,
      DBGroup organizationGroup, Map<String, String> adminEncryptedKeys) throws SQLException, CyclicGroupError {
    List<DBAcl> organizationAcls = Lists.newArrayList();
    for (Map.Entry<String, String> adminEntry : adminEncryptedKeys.entrySet()) {
      DBIdentity identity = userDb.getIdentity(adminEntry.getKey());
      String keyEncryptedForUser = adminEntry.getValue();

      DBAcl acl = new DBAcl();
      acl.setGroup(organizationGroup);
      acl.setLevel(DBAcl.AccessLevelType.ADMIN);
      acl.setMemberIdentity(identity);
      acl.setGroupKeyEncryptedForMe(keyEncryptedForUser);
      organizationAcls.add(acl);
    }
    return organizationAcls;
  }
}
