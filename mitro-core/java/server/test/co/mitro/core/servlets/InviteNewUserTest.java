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

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import co.mitro.core.crypto.CrappyKeyFactory;
import co.mitro.core.crypto.KeyInterfaces;
import co.mitro.core.crypto.KeyInterfaces.CryptoError;
import co.mitro.core.exceptions.MitroServletException;
import co.mitro.core.exceptions.UserVisibleException;
import co.mitro.core.server.data.DBAcl;
import co.mitro.core.server.data.DBEmailQueue;
import co.mitro.core.server.data.DBGroup;
import co.mitro.core.server.data.DBIdentity;
import co.mitro.core.server.data.RPC.InviteNewUserRequest;
import co.mitro.core.server.data.RPC.InviteNewUserResponse;
import co.mitro.core.servlets.MitroServlet.MitroRequestContext;

import com.google.common.collect.ImmutableList;

public class InviteNewUserTest extends MemoryDBFixture {
  private KeyInterfaces.KeyFactory keyFactory;
  private InviteNewUser servlet;
  private DBIdentity sender;

  @Before
  public void setUp() {
    sender = new DBIdentity();
    sender.setName("sender@exmaple.com");

    keyFactory = new CrappyKeyFactory();
    servlet = new InviteNewUser(managerFactory, keyFactory);
  }

  @Test(expected=SQLException.class)
  public void testAlreadyExists() throws Exception {
    servlet.inviteNewUser(manager, sender, testIdentity.getName());
  }

  @Test
  public void testBadEmailAddress() throws Exception {
    try {
      servlet.inviteNewUser(manager, sender, "@notanaddr.com");
    } catch (UserVisibleException e) {
      assertThat(e.getUserVisibleMessage(), containsString("Invalid email address"));
    }
  }

  @Test
  public void testSingle() throws Exception {
    // invite a new user
    final String email = "newuser@example.com";
    DBIdentity identity = servlet.inviteNewUser(manager, sender, email);

    // check for the queued email
    List<DBEmailQueue> emails = manager.emailDao.queryForAll();
    assertEquals(1, emails.size());
    assertEquals(DBEmailQueue.Type.INVITE, emails.get(0).getType());
    assertEquals(null, emails.get(0).getAttemptedTime());

    String[] args = emails.get(0).getArguments();
    assertEquals(sender.getName(), args[0]);
    assertEquals(email, args[1]);
    final String password = args[2];
    assertEquals(InviteNewUser.GENERATED_PASSWORD_LENGTH, password.length());

    // check the DB for the user and the email
    DBIdentity user = DBIdentity.getIdentityForUserName(manager, email);
    assertEquals(email, user.getName());
    assertTrue(user.getChangePasswordOnNextLogin());
    assertNotNull(
        keyFactory.loadEncryptedPrivateKey(user.getEncryptedPrivateKeyString(), password));
    assertEquals(identity, user);
    assertNotNull(keyFactory.loadPublicKey(identity.getPublicKeyString()));

    // Check that the user has a private group
    @SuppressWarnings("deprecation")
    List<List<DBGroup>> groups = user.ListAccessibleGroupsBF(manager);
    assertEquals(1, groups.size());
    DBGroup g = groups.get(0).get(0);
    assertEquals(false, g.isAutoDelete());
    assertEquals("", g.getName());

    DBAcl[] acls = g.getAcls().toArray(new DBAcl[0]);
    assertEquals(1, acls.length);
    assertEquals(user.getId(), acls[0].getMemberIdentityId().getId());
  }

  @Test
  public void testRequest() throws SQLException, CryptoError, MitroServletException, IOException {
    final String u1 = "1@example.com";
    final String u2 = "2@example.com";
    InviteNewUserRequest request = new InviteNewUserRequest();
    request.emailAddresses = ImmutableList.of(u1, u2);

    InviteNewUserResponse response = (InviteNewUserResponse)
        servlet.processCommand(new MitroRequestContext(testIdentity, gson.toJson(request), manager, null));

    assertEquals(2, response.publicKeys.size());
    assertNotNull(keyFactory.loadPublicKey(response.publicKeys.get(u1)));
    assertNotNull(keyFactory.loadPublicKey(response.publicKeys.get(u2)));

    // 2 emails
    assertEquals(2, manager.emailDao.countOf());
  }

}
