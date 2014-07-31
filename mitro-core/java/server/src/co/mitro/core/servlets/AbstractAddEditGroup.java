/*******************************************************************************
 * Copyright (c) 2013 Lectorius, Inc.
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

import java.sql.SQLException;

import co.mitro.core.exceptions.InvalidRequestException;
import co.mitro.core.exceptions.MitroServletException;
import co.mitro.core.server.Manager;
import co.mitro.core.server.data.DBAcl;
import co.mitro.core.server.data.DBAcl.CyclicGroupError;
import co.mitro.core.server.data.DBGroup;
import co.mitro.core.server.data.DBIdentity;
import co.mitro.core.server.data.RPC;
import co.mitro.core.server.data.RPC.AddGroupRequest;

public abstract class AbstractAddEditGroup extends MitroServlet {
  private static final long serialVersionUID = -590496905921831595L;

  @Override
  protected boolean isReadOnly() {
    return false;
  }

  public static RPC.AddGroupResponse addEditGroupCommand(Manager mgr, RPC.AddGroupRequest in,
      DBGroup theGroup) throws MitroServletException,
      SQLException {

    if (null == in.acls || in.acls.isEmpty()) {
      throw new InvalidRequestException("no acls");
    }
    theGroup.setName(in.name);
    theGroup.setAutoDelete(in.autoDelete);
    theGroup.setPublicKeyString(in.publicKey);
    theGroup.setSignatureString(in.signatureString);
    if (theGroup.getId() == 0) {
      mgr.groupDao.create(theGroup);
    } else {
      mgr.groupDao.update(theGroup);
    }

    try {
      for (AddGroupRequest.ACL acl : in.acls) {
        DBAcl newAcl = new DBAcl();
        newAcl.setGroup(theGroup);
        newAcl.setGroupKeyEncryptedForMe(acl.groupKeyEncryptedForMe);
        newAcl.setLevel(acl.level);
        if (null != acl.memberGroup) {
          DBGroup mg = mgr.groupDao.queryForId(acl.memberGroup);
          assert null != mg;
          newAcl.setMemberGroup(mg);
        } else if (null != acl.memberIdentity) {
          final DBIdentity mi = DBIdentity.getIdentityForUserName(mgr, acl.memberIdentity);
          newAcl.setMemberIdentity(mi);
        } else {
          // TODO: fix this
          throw new InvalidRequestException("must supply group or identity");
        }
        mgr.aclDao.create(newAcl);
      }
    } catch (CyclicGroupError e) {
      throw new MitroServletException(e);
    }

    RPC.AddGroupResponse out = new RPC.AddGroupResponse();
    out.groupId = theGroup.getId();
    return out;
  }

}